"""Narrow, read-only transport for four intended-public beta assets; never handles signing keys."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request

CONFIG = Path(__file__).with_name('beta-config.json')
MAX_APK = 200 * 1024 * 1024
MAX_SIDECAR = 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def allowed(config):
    return {config['apkName'], 'BETA_MANIFEST.json', 'SHA256SUMS.txt', 'SIGNING_CERTIFICATE_SHA256.txt'}


def validate_url(url):
    require(isinstance(url, str) and not any(ord(c) < 33 or ord(c) == 127 for c in url), 'Invalid asset URL')
    parsed = urllib.parse.urlsplit(url)
    require(parsed.scheme == 'https' and parsed.hostname == 'release-assets.githubusercontent.com', 'Asset URL must use the release-assets host')
    require(parsed.username is None and parsed.password is None and parsed.port in (None, 443) and not parsed.fragment, 'Invalid asset URL authority')
    require(parsed.path.startswith('/github-production-release-asset/'), 'Invalid asset URL path')
    return url


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_descriptor(data, config, source):
    require(isinstance(data, dict) and set(data) == {'sourceRevision', 'releaseId', 'tag', 'assets'}, 'Invalid asset descriptor')
    require(data.get('sourceRevision') == source, 'Asset descriptor source mismatch')
    require(data.get('releaseId') == config['releaseId'] and data.get('tag') == config['tag'], 'Asset descriptor release mismatch')
    assets = data.get('assets')
    require(isinstance(assets, dict) and set(assets) == allowed(config), 'Exactly four named public assets are required')
    for name, asset in assets.items():
        require(isinstance(asset, dict) and set(asset) == {'url', 'size', 'sha256'}, 'Invalid asset metadata')
        validate_url(asset.get('url'))
        limit = MAX_APK if name == config['apkName'] else MAX_SIDECAR
        require(type(asset.get('size')) is int and 0 < asset['size'] <= limit, 'Invalid or excessive asset size')
        require(isinstance(asset.get('sha256'), str) and re.fullmatch('[0-9a-f]{64}', asset['sha256']) is not None, 'Invalid asset digest')
    return assets


def download_assets(data, config, source, directory, opener=None):
    assets = validate_descriptor(data, config, source)
    opener = opener or urllib.request.build_opener(NoRedirect())
    directory.mkdir(parents=True, exist_ok=True)
    for asset in assets.values():
        # Workflow inputs are observable event data. Mask before use; never retain them as evidence.
        print('::add-mask::' + asset['url'].replace('%', '%25'), flush=True)
    for name, asset in assets.items():
        destination = directory / name
        require(not destination.exists(), 'Refusing to overwrite an existing beta asset')
        digest = hashlib.sha256()
        size = 0
        try:
            with opener.open(urllib.request.Request(asset['url']), timeout=60) as response:
                require(response.status == 200, 'Asset download did not return HTTP 200')
                with destination.open('xb') as output:
                    while True:
                        chunk = response.read(min(65536, asset['size'] - size + 1))
                        if not chunk:
                            break
                        size += len(chunk)
                        require(size <= asset['size'], 'Asset exceeds its declared size')
                        digest.update(chunk)
                        output.write(chunk)
            require(size == asset['size'] and digest.hexdigest() == asset['sha256'], 'Asset size or digest mismatch')
        except Exception:
            destination.unlink(missing_ok=True)
            # HTTP errors can contain bearer URLs; do not expose their repr/traceback.
            raise ValueError('Public beta asset download failed; URLs may have expired') from None
    print('Downloaded exactly four public beta assets; source, signature and APK checks follow')


def dispatch_input():
    event_path = os.environ.get('GITHUB_EVENT_PATH')
    if not event_path:
        return ''
    event = json.loads(Path(event_path).read_text(encoding='utf-8'))
    return event.get('inputs', {}).get('beta_asset_urls', '')


def gh_json(*args):
    result = subprocess.run(['gh', *args], capture_output=True, text=True, check=False)
    require(result.returncode == 0, 'GitHub metadata request failed')
    return json.loads(result.stdout)


def resolve_descriptor(config, source, repo):
    release = gh_json('api', f'repos/{repo}/releases/{config["releaseId"]}')
    require(release.get('id') == config['releaseId'] and release.get('tag_name') == config['tag'], 'Unexpected draft release')
    require(release.get('draft') is True and release.get('prerelease') is True, 'Only an unpublished prerelease may be dispatched')
    require(release.get('target_commitish') == source, 'Draft source mismatch')
    assets = release.get('assets', [])
    require(len(assets) == 4 and {a.get('name') for a in assets} == allowed(config), 'Draft public asset list mismatch')
    token_result = subprocess.run(['gh', 'auth', 'token', '--hostname', 'github.com'], capture_output=True, text=True, check=False)
    require(token_result.returncode == 0 and bool(token_result.stdout.strip()), 'Owner GitHub authentication unavailable')
    token = token_result.stdout.strip()
    descriptor = {'sourceRevision': source, 'releaseId': config['releaseId'], 'tag': config['tag'], 'assets': {}}
    opener = urllib.request.build_opener(NoRedirect())
    try:
        for asset in assets:
            require(asset.get('state') == 'uploaded' and type(asset.get('id')) is int and asset['id'] > 0, 'Incomplete draft asset')
            digest = asset.get('digest', '')
            require(isinstance(digest, str) and re.fullmatch('sha256:[0-9a-f]{64}', digest) is not None, 'GitHub asset digest unavailable')
            request = urllib.request.Request(f'https://api.github.com/repos/{repo}/releases/assets/{asset["id"]}', headers={
                'Authorization': 'Bearer ' + token, 'Accept': 'application/octet-stream', 'User-Agent': 'Atomic-beta-owner-dispatch'})
            location = None
            try:
                with opener.open(request, timeout=60):
                    pass
            except urllib.error.HTTPError as error:
                try:
                    require(error.code == 302, 'Asset API did not provide an HTTP 302 download capability')
                    location = error.headers.get('Location')
                finally:
                    error.close()
            require(location is not None, 'Asset API returned a direct body; no narrow download capability is available')
            validate_url(location)
            descriptor['assets'][asset['name']] = {'url': location, 'size': asset['size'], 'sha256': digest[7:]}
    finally:
        token = None
        token_result = None
    validate_descriptor(descriptor, config, source)
    return descriptor


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('has-input', 'fetch', 'dispatch'))
    parser.add_argument('--repo', default='DevBehindYou/Atomic-File-Manager')
    args = parser.parse_args()
    config = json.loads(CONFIG.read_text(encoding='utf-8-sig'))
    if args.mode == 'has-input':
        return 0 if dispatch_input() else 1
    if args.mode == 'fetch':
        require(bool(dispatch_input()), 'Missing temporary public asset descriptor')
        download_assets(json.loads(dispatch_input()), config, os.environ['GITHUB_SHA'], Path('beta-smoke'))
    else:
        require(re.fullmatch('[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repo) is not None, 'Invalid repository')
        source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        require(re.fullmatch('[0-9a-f]{40}', source) is not None, 'Invalid source SHA')
        branch = gh_json('api', f'repos/{args.repo}/branches/codex%2Fbeta-0.2.0')
        require(branch['commit']['sha'] == source, 'Published beta branch differs from this source')
        descriptor = resolve_descriptor(config, source, args.repo)
        # Pass capabilities through stdin, not argv/files. Owner token never enters workflow input.
        result = subprocess.run(['gh', 'workflow', 'run', 'ci.yml', '--repo', args.repo, '--ref', 'codex/beta-0.2.0', '--json'],
                                input=json.dumps({'beta_asset_urls': json.dumps(descriptor, separators=(',', ':'))}),
                                capture_output=True, text=True, check=False)
        require(result.returncode == 0, 'Manual beta dispatch failed')
        print('Dispatched read-only signed-beta runtime at ' + source + '; inspect Actions for the result')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, OSError, json.JSONDecodeError):
        raise SystemExit('Beta artifact transport refused the request; check source, asset metadata or fresh download capabilities') from None
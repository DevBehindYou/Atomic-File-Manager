"""Verify the explicit public beta artifact set; never reads signing credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--config', type=Path, default=Path('scripts/release/beta-config.json'))
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding='utf-8-sig'))
    directory = args.directory
    manifest = json.loads((directory / 'BETA_MANIFEST.json').read_text(encoding='utf-8-sig'))
    require(re.fullmatch(r'[0-9a-f]{40}', args.source_sha) is not None, 'Invalid expected source SHA')
    require(manifest['sourceRevision'] == args.source_sha, 'APK provenance does not match this source')
    expected_tree = subprocess.check_output(['git', 'rev-parse', f'{args.source_sha}:app'], text=True).strip()
    require(manifest['appTree'] == expected_tree, 'APK app tree does not match this source')
    for field in ('tag', 'versionName', 'versionCode', 'applicationId', 'minSdk', 'apkName', 'certificateSha256'):
        require(manifest[field] == config[field], f'Unexpected beta manifest field: {field}')
    apk_name = config['apkName']
    require(Path(apk_name).name == apk_name and apk_name.endswith('.apk'), 'Unsafe APK asset name')
    apk = directory / apk_name
    require(hashlib.sha256(apk.read_bytes()).hexdigest().upper() == manifest['apkSha256'], 'APK hash mismatch')
    require(manifest['minified'] is True, 'Shipping APK must be a minified release build')
    checksums = {}
    for line in (directory / 'SHA256SUMS.txt').read_text(encoding='utf-8-sig').splitlines():
        match = re.fullmatch(r'([0-9A-Fa-f]{64})  ([A-Za-z0-9_.-]+)', line)
        require(match is not None, 'Malformed public checksum entry')
        digest, name = match.groups()
        require(name not in checksums, 'Duplicate public checksum entry')
        checksums[name] = digest.upper()
    require(set(checksums) == {apk_name, 'BETA_MANIFEST.json', 'SIGNING_CERTIFICATE_SHA256.txt'}, 'Unexpected public asset list')
    for name, digest in checksums.items():
        require(hashlib.sha256((directory / name).read_bytes()).hexdigest().upper() == digest, f'Hash mismatch: {name}')
    cert = (directory / 'SIGNING_CERTIFICATE_SHA256.txt').read_text(encoding='utf-8-sig').strip()
    require(cert == config['certificateSha256'], 'Wrong public certificate asset')
    report = (directory / 'apksigner.txt').read_text(encoding='utf-8-sig')
    certificates = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', report)
    require(len(certificates) == 1 and certificates[0].upper() == cert, 'APK is not signed by the dedicated release key')
    badging = (directory / 'badging.txt').read_text(encoding='utf-8-sig')
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.MULTILINE)
    require(package is not None, 'Missing APK package metadata')
    require(package.group(1) == config['applicationId'], 'Wrong APK application ID')
    require(int(package.group(2)) == config['versionCode'], 'Wrong APK version code')
    require(package.group(3) == config['versionName'], 'Wrong APK version name')
    require(f"sdkVersion:'{config['minSdk']}'" in badging, 'Wrong minimum SDK')
    require('application-debuggable' not in badging, 'Refusing a debuggable APK')
    require('application-testOnly' not in badging, 'Refusing a test-only APK')
    print(f'PASS: {config["tag"]}, source {args.source_sha}, dedicated signature, version, hashes and release manifest')


if __name__ == '__main__':
    main()

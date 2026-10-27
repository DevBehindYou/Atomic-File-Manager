"""Transport rejection and bounded-download tests; no network or credentials."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import urllib.error

spec = importlib.util.spec_from_file_location('transport', Path(__file__).with_name('beta_transport.py'))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)


class Response(io.BytesIO):
    status = 200


class Opener:
    def __init__(self, payloads):
        self.payloads = iter(payloads)
        self.requests = []

    def open(self, request, timeout):
        self.requests.append(request)
        return Response(next(self.payloads))


class TransportTest(unittest.TestCase):
    def setUp(self):
        self.config = json.loads(t.CONFIG.read_text(encoding='utf-8-sig'))
        self.source = '1' * 40
        self.payloads = [b'public test ' + bytes([i]) for i in range(4)]
        self.data = {'sourceRevision': self.source, 'releaseId': self.config['releaseId'], 'tag': self.config['tag'], 'assets': {}}
        for i, (name, payload) in enumerate(zip(sorted(t.allowed(self.config)), self.payloads)):
            self.data['assets'][name] = {'url': f'https://release-assets.githubusercontent.com/github-production-release-asset/test{i}?sig=synthetic',
                                         'size': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()}

    def reject(self, change):
        data = copy.deepcopy(self.data)
        change(data)
        with self.assertRaises(ValueError):
            t.validate_descriptor(data, self.config, self.source)

    def test_accepts_exact_four_public_assets_and_binds_source(self):
        self.assertEqual(set(t.validate_descriptor(self.data, self.config, self.source)), t.allowed(self.config))
        self.reject(lambda d: d.update(sourceRevision='2' * 40))
        self.reject(lambda d: d.update(releaseId=0))
        self.reject(lambda d: d.update(tag='wrong'))

    def test_rejects_missing_or_extra_assets(self):
        self.reject(lambda d: d['assets'].pop(next(iter(d['assets']))))
        self.reject(lambda d: d['assets'].update({'private-backup.zip': next(iter(d['assets'].values()))}))

    def test_rejects_wrong_scheme_host_authority_path_and_controls(self):
        for url in ['http://release-assets.githubusercontent.com/github-production-release-asset/a',
                    'https://evil.test/github-production-release-asset/a',
                    'https://release-assets.githubusercontent.com.evil.test/github-production-release-asset/a',
                    'https://user:password@release-assets.githubusercontent.com/github-production-release-asset/a',
                    'https://release-assets.githubusercontent.com:444/github-production-release-asset/a',
                    'https://release-assets.githubusercontent.com/github-production-release-asset/a#fragment',
                    'https://release-assets.githubusercontent.com/other/a',
                    'https://release-assets.githubusercontent.com/github-production-release-asset/a\nspoof']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                t.validate_url(url)

    def test_rejects_non_integer_zero_oversize_and_bad_digest(self):
        name = next(n for n in self.data['assets'] if not n.endswith('.apk'))
        for size in [0, -1, True, '1', t.MAX_SIDECAR + 1]:
            with self.subTest(size=size):
                self.reject(lambda d: d['assets'][name].update(size=size))
        self.reject(lambda d: d['assets'][self.config['apkName']].update(size=t.MAX_APK + 1))
        self.reject(lambda d: d['assets'][name].update(sha256='not-a-digest'))

    def test_downloads_exact_bytes_without_authorization_header_or_url_evidence(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()) as output:
            opener = Opener(self.payloads)
            t.download_assets(self.data, self.config, self.source, Path(directory), opener)
            self.assertEqual(set(p.name for p in Path(directory).iterdir()), set(self.data['assets']))
            for name, expected in zip(self.data['assets'], self.payloads):
                self.assertEqual((Path(directory) / name).read_bytes(), expected)
            self.assertEqual(len(opener.requests), 4)
            self.assertTrue(all(r.get_header('Authorization') is None for r in opener.requests))
            self.assertEqual(output.getvalue().count('::add-mask::'), 4)

    def test_rejects_declared_size_overrun_and_removes_partial(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(ValueError, 'download failed'):
                t.download_assets(self.data, self.config, self.source, Path(directory), Opener([self.payloads[0] + b'extra']))
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_rejects_truncation_and_wrong_hash(self):
        for payload in [b'short', b'x' * len(self.payloads[0])]:
            with self.subTest(payload=payload), tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(ValueError, 'download failed'):
                    t.download_assets(self.data, self.config, self.source, Path(directory), Opener([payload]))
                self.assertEqual(list(Path(directory).iterdir()), [])

    def test_expired_or_redirected_url_fails_without_leaking_bearer_url(self):
        url = next(iter(self.data['assets'].values()))['url']
        for status in [302, 403]:
            opener = mock.Mock()
            opener.open.side_effect = urllib.error.HTTPError(url, status, 'do not log this URL', {}, None)
            with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(ValueError) as error:
                    t.download_assets(self.data, self.config, self.source, Path(directory), opener)
                self.assertNotIn(url, str(error.exception))
                self.assertNotIn('sig=', str(error.exception))
                self.assertEqual(list(Path(directory).iterdir()), [])

    def test_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            name = next(iter(self.data['assets']))
            (Path(directory) / name).write_bytes(b'preserve')
            with self.assertRaisesRegex(ValueError, 'overwrite'):
                t.download_assets(self.data, self.config, self.source, Path(directory), Opener(self.payloads))
            self.assertEqual((Path(directory) / name).read_bytes(), b'preserve')

    def test_owner_resolver_rejects_direct_body_instead_of_passing_owner_token(self):
        release = {'id': self.config['releaseId'], 'tag_name': self.config['tag'], 'target_commitish': self.source,
                   'draft': True, 'prerelease': True, 'assets': []}
        for i, (name, asset) in enumerate(self.data['assets'].items()):
            release['assets'].append({'id': i + 1, 'name': name, 'state': 'uploaded', 'size': asset['size'], 'digest': 'sha256:' + asset['sha256']})
        with mock.patch.object(t, 'gh_json', return_value=release), mock.patch.object(t.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout='OWNER_TOKEN')), \
                mock.patch.object(t.urllib.request, 'build_opener', return_value=Opener([b'direct body'])):
            with self.assertRaisesRegex(ValueError, 'direct body'):
                t.resolve_descriptor(self.config, self.source, 'owner/repository')

    def test_owner_resolver_returns_only_asset_capabilities_from_actual302(self):
        release = {'id': self.config['releaseId'], 'tag_name': self.config['tag'], 'target_commitish': self.source,
                   'draft': True, 'prerelease': True, 'assets': []}
        responses = []
        for i, (name, asset) in enumerate(self.data['assets'].items()):
            release['assets'].append({'id': i + 1, 'name': name, 'state': 'uploaded', 'size': asset['size'], 'digest': 'sha256:' + asset['sha256']})
            responses.append(urllib.error.HTTPError('https://api.github.com/asset', 302, 'redirect', {'Location': asset['url']}, None))
        opener = mock.Mock()
        opener.open.side_effect = responses
        with mock.patch.object(t, 'gh_json', return_value=release), mock.patch.object(t.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout='OWNER_TOKEN')), \
                mock.patch.object(t.urllib.request, 'build_opener', return_value=opener):
            descriptor = t.resolve_descriptor(self.config, self.source, 'owner/repository')
        self.assertEqual(descriptor, self.data)
        self.assertNotIn('OWNER_TOKEN', json.dumps(descriptor))
        self.assertEqual(opener.open.call_count, 4)
    def test_owner_resolver_rejects_published_or_wrong_source_before_token(self):
        for change in [{'draft': False}, {'target_commitish': '0' * 40}]:
            release = {'id': self.config['releaseId'], 'tag_name': self.config['tag'], 'target_commitish': self.source, 'draft': True, 'prerelease': True}
            release.update(change)
            with mock.patch.object(t, 'gh_json', return_value=release), mock.patch.object(t.subprocess, 'run') as token:
                with self.assertRaises(ValueError):
                    t.resolve_descriptor(self.config, self.source, 'owner/repository')
                token.assert_not_called()


if __name__ == '__main__':
    unittest.main()
"""Exercise coordinator validation and leases using only a disposable empty Git repo."""

import importlib.util
import json
import subprocess
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('coordinator_validation', HERE / 'validate.py')
validation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validation)


class QueueValidationTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((validation.DOCS / 'tasks.json').read_text(encoding='utf-8-sig'))

    def test_current_queue(self):
        validation.validate(self.data)

    def test_missing_task_rejected(self):
        self.data['tasks'].pop()
        with self.assertRaises(AssertionError):
            validation.validate(self.data)

    def test_future_dependency_rejected(self):
        self.data['tasks'][0]['dependencies'] = ['C050']
        with self.assertRaises(AssertionError):
            validation.validate(self.data)

    def test_premature_final_acceptance_rejected(self):
        task = self.data['tasks'][-1]
        task.update(status='accepted', pr='test-only', evidence=['test-only'])
        with self.assertRaises(AssertionError):
            validation.validate(self.data)


class LeaseTest(unittest.TestCase):
    def setUp(self):
        self.fixture = tempfile.TemporaryDirectory(prefix='atomic-coordinator-test-')
        self.repo = Path(self.fixture.name).resolve()
        # Verify the recursively cleaned fixture is the unique temp directory we own.
        assert self.repo.parent == Path(tempfile.gettempdir()).resolve()
        assert self.repo.name.startswith('atomic-coordinator-test-')
        self.addCleanup(self.fixture.cleanup)
        subprocess.run(['git', 'init', '--quiet', str(self.repo)], check=True, capture_output=True)
        (self.repo / 'nested').mkdir()

    def call(self, *args, repo=None):
        return subprocess.run(
            ['powershell', '-NoProfile', '-NonInteractive', '-File', str(HERE / 'lease.ps1'),
             '-Repository', str(repo or self.repo), *args],
            capture_output=True, text=True, timeout=30)

    def acquire(self, resource='host-gradle', repo=None):
        return self.call('-Action', 'acquire', '-Resource', resource,
                         '-Owner', 'Claude Code', '-Task', 'test-only', repo=repo)

    def test_cross_directory_exclusion_and_token_release(self):
        first = self.acquire()
        self.assertEqual(first.returncode, 0, first.stderr)
        token = json.loads(first.stdout)['token']
        duplicate = self.acquire(repo=self.repo / 'nested')
        self.assertNotEqual(duplicate.returncode, 0)
        wrong = self.call('-Action', 'release', '-Resource', 'host-gradle', '-Token', 'wrong')
        self.assertNotEqual(wrong.returncode, 0)
        status = self.call('-Action', 'status')
        self.assertEqual(len(json.loads(status.stdout)), 1)
        release = self.call('-Action', 'release', '-Resource', 'host-gradle', '-Token', token)
        self.assertEqual(release.returncode, 0, release.stderr)
        self.assertEqual(json.loads(self.call('-Action', 'status').stdout), [])

    def test_concurrent_acquisition_has_one_winner(self):
        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _: self.acquire(), range(2)))
        self.assertEqual(sum(result.returncode == 0 for result in results), 1)

    def test_invalid_resource_cannot_escape_directory(self):
        result = self.acquire('../escape')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.repo / '.git/escape.json').exists())

    def test_distinct_resources_can_coexist(self):
        self.assertEqual(self.acquire('host-gradle').returncode, 0)
        self.assertEqual(self.acquire('phone-adb').returncode, 0)
        self.assertEqual(len(json.loads(self.call('-Action', 'status').stdout)), 2)


if __name__ == '__main__':
    unittest.main()

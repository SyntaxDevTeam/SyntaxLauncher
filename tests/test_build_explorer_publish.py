import gzip
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('publisher', 'scripts/publish_build_explorer.py')
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.env = {'GITHUB_RUN_ID': '123', 'GITHUB_RUN_NUMBER': '4', 'GITHUB_SHA': 'a' * 40,
                    'GITHUB_REF': 'refs/heads/main', 'GITHUB_REF_NAME': 'main'}
        for platform in publisher.PLATFORMS:
            (self.directory / f'syntaxlauncher-{platform}.tar.gz').write_bytes(gzip.compress(b'test fixture'))

    def test_three_platforms_share_release_and_include_integrity(self):
        releases = publisher.prepare(self.directory, self.env)
        self.assertEqual(3, len(releases))
        self.assertEqual({123}, {metadata['id'] for _, metadata in releases})
        for path, metadata in releases:
            self.assertEqual('other', metadata['platform_family'])
            self.assertEqual('snapshot', metadata['channel'])
            self.assertEqual(publisher.hashlib.sha256(path.read_bytes()).hexdigest(), metadata['sha256'])
            self.assertEqual(path.stat().st_size, metadata['size'])

    def test_release_tag(self):
        self.env['GITHUB_REF'] = 'refs/tags/v0.3.0'
        self.assertEqual('release', publisher.prepare(self.directory, self.env)[0][1]['channel'])
        self.assertEqual('0.3.0', publisher.prepare(self.directory, self.env)[0][1]['version'])

    def test_missing_platform_rejects_whole_batch(self):
        next(self.directory.iterdir()).unlink()
        with self.assertRaises(ValueError):
            publisher.prepare(self.directory, self.env)

    def test_untrusted_branch_and_invalid_format_rejected(self):
        self.env['GITHUB_REF'] = 'refs/pull/10/merge'
        with self.assertRaises(ValueError):
            publisher.prepare(self.directory, self.env)
        self.env['GITHUB_REF'] = 'refs/heads/main'
        next(self.directory.iterdir()).write_bytes(b'not gzip')
        with self.assertRaises(ValueError):
            publisher.prepare(self.directory, self.env)

    def test_size_limit(self):
        path = next(self.directory.iterdir())
        with path.open('wb') as stream:
            stream.truncate(publisher.MAX_BYTES + 1)
        with self.assertRaises(ValueError):
            publisher.prepare(self.directory, self.env)


if __name__ == '__main__':
    unittest.main()

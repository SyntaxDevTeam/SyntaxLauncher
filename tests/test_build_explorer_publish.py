import gzip
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path('scripts').resolve()))

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
        self.version_file = self.directory / 'gradle.properties'
        self.version_file.write_text('launcherVersion=0.3.0-dev\n')
        for platform in publisher.PLATFORMS:
            (self.directory / f'syntaxlauncher-{platform}.tar.gz').write_bytes(gzip.compress(b'test fixture'))

    def publication(self):
        return publisher.prepare(self.directory, self.env, version_file=self.version_file)

    def test_three_platforms_share_release_and_include_integrity(self):
        releases = self.publication()
        self.assertEqual(3, len(releases))
        self.assertEqual({123}, {metadata['id'] for _, metadata in releases})
        self.assertEqual({'0.3.0-dev'}, {metadata['version'] for _, metadata in releases})
        for path, metadata in releases:
            self.assertEqual('other', metadata['platform_family'])
            self.assertEqual('snapshot', metadata['channel'])
            self.assertEqual(publisher.hashlib.sha256(path.read_bytes()).hexdigest(), metadata['sha256'])
            self.assertEqual(path.stat().st_size, metadata['size'])

    def test_release_tag(self):
        self.version_file.write_text('launcherVersion=0.3.0\n')
        self.env['GITHUB_REF'] = 'refs/tags/v0.3.0'
        self.assertEqual('release', self.publication()[0][1]['channel'])
        self.assertEqual('0.3.0', self.publication()[0][1]['version'])

    def test_release_tag_must_match_canonical_version(self):
        self.env['GITHUB_REF'] = 'refs/tags/v0.3.1'
        with self.assertRaises(ValueError):
            self.publication()

    def test_missing_platform_rejects_whole_batch(self):
        next(self.directory.glob('syntaxlauncher-*.tar.gz')).unlink()
        with self.assertRaises(ValueError):
            self.publication()

    def test_untrusted_branch_and_invalid_format_rejected(self):
        self.env['GITHUB_REF'] = 'refs/pull/10/merge'
        with self.assertRaises(ValueError):
            self.publication()
        self.env['GITHUB_REF'] = 'refs/heads/main'
        next(self.directory.glob('syntaxlauncher-*.tar.gz')).write_bytes(b'not gzip')
        with self.assertRaises(ValueError):
            self.publication()

    def test_size_limit(self):
        path = next(self.directory.glob('syntaxlauncher-*.tar.gz'))
        with path.open('wb') as stream:
            stream.truncate(publisher.MAX_BYTES + 1)
        with self.assertRaises(ValueError):
            self.publication()


if __name__ == '__main__':
    unittest.main()

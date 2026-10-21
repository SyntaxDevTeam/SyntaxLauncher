import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from publish_preview import PLATFORMS, preview_tag, publish


class PreviewPublishingTest(unittest.TestCase):
    def test_identity_keeps_canonical_version_and_reruns_are_distinct(self):
        self.assertEqual('dev-42.2-v0.6.0', preview_tag('dev', '0.6.0', '42', '2'))
        self.assertEqual('snapshot-42.1-v0.6.0-rc.1', preview_tag('snapshot', '0.6.0-rc.1', '42', '1'))
        for channel, run, attempt in [('stable', '1', '1'), ('dev', '0', '1'), ('dev', '1', '01')]:
            with self.assertRaises(ValueError):
                preview_tag(channel, '0.6.0', run, attempt)

    def test_nothing_published_until_all_assets_exist_and_publish_is_last(self):
        with tempfile.TemporaryDirectory() as folder, patch('publish_preview.subprocess.run') as command:
            assets = Path(folder)
            with self.assertRaises(ValueError):
                publish('dev', '0.6.0', '42', '1', 'a' * 40, assets)
            command.assert_not_called()
            for platform in PLATFORMS:
                for name in (f'syntaxlauncher-update-{platform}.zip', f'syntaxlauncher-{platform}.tar.gz'):
                    (assets / name).write_bytes(b'verified')
            publish('snapshot', '0.6.0', '42', '1', 'a' * 40, assets)
            calls = [call.args[0] for call in command.call_args_list]
            self.assertIn(f'sha={"a" * 40}', calls[0])
            self.assertIn('--verify-tag', calls[1])
            calls = calls[1:]
            self.assertIn('--draft', calls[0])
            self.assertIn('--prerelease', calls[0])
            self.assertIn('--latest=false', calls[0])
            self.assertEqual('upload', calls[1][2])
            self.assertEqual(10, len(calls[1]))
            self.assertIn('--draft=false', calls[2])
            self.assertIn('--latest=false', calls[2])
            self.assertNotIn('--clobber', calls[1])


if __name__ == '__main__':
    unittest.main()

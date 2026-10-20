import tempfile
from pathlib import Path
import unittest

from launcher_version import read_launcher_version, validate_git_ref


class LauncherVersionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.properties = Path(self.temp.name) / "gradle.properties"

    def test_reads_single_property_and_checks_matching_release_tag(self):
        self.properties.write_text("org.gradle.caching=true\nlauncherVersion=0.5.0\n")
        self.assertEqual("0.5.0", read_launcher_version(self.properties))
        self.assertEqual("0.5.0", validate_git_ref("0.5.0", "refs/tags/v0.5.0"))
        self.assertEqual("0.5.0", validate_git_ref("0.5.0", "refs/heads/main"))

    def test_mismatched_tags_rejected(self):
        for ref in ("refs/tags/v0.5.1", "refs/tags/v0.4.0", "refs/tags/release-0.5.0"):
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                validate_git_ref("0.5.0", ref)

    def test_missing_duplicate_and_invalid_values_rejected(self):
        for content in (
            "org.gradle.caching=true\n",
            "launcherVersion=0.5.0\nlauncherVersion=0.6.0\n",
            "launcherVersion=0.05.0\n",
            "launcherVersion=0.5.0-beta.01\n",
            "launcherVersion=\n",
        ):
            with self.subTest(content=content), self.assertRaises(ValueError):
                self.properties.write_text(content)
                read_launcher_version(self.properties)


if __name__ == "__main__":
    unittest.main()

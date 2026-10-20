import unittest
from auto_release import eligible
from launcher_version import parse_launcher_version


class AutoReleaseTest(unittest.TestCase):
    def test_publishes_stable_increments_and_promotions(self):
        self.assertTrue(eligible("0.3.0", "0.4.0"))
        self.assertTrue(eligible("0.3.0-dev", "0.4.0"))
        self.assertTrue(eligible("0.3.0-rc.1", "0.3.0"))
        self.assertTrue(eligible("0.4.0-SNAPSHOT.42", "0.4.0"))

    def test_ignores_unchanged_development_downgraded_or_untracked_versions(self):
        for previous, current in [
            ("0.3.0", "0.3.0"),
            ("0.3.0-dev", "0.3.0-dev"),
            ("0.3.0", "0.4.0-dev"),
            ("0.4.0", "0.3.0"),
            ("0.5.0-dev", "0.4.0"),
            (None, "0.4.0"),
        ]:
            with self.subTest(previous=previous, current=current):
                self.assertFalse(eligible(previous, current))

    def test_historical_parse_uses_canonical_validator(self):
        self.assertEqual("0.3.0-dev", parse_launcher_version("launcherVersion=0.3.0-dev\n"))
        with self.assertRaises(ValueError):
            parse_launcher_version("launcherVersion=0.3.0\nlauncherVersion=0.4.0\n")


if __name__ == "__main__":
    unittest.main()

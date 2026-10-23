import unittest

from release_readiness import audit, markdown, read_product_version


ROADMAP = """# Intro

# v0.9 — Graphics

- [x] Shader packs.
- [x] OptiFine.

# v0.10 — Modpacks

- [x] Import.
- [ ] P0 — Native acceptance.

# v1.0 — Stable Release

- [ ] P0 / BLOCKED — Minecraft AppID approval.
- [ ] Code signing.
"""


class ReleaseReadinessTest(unittest.TestCase):
    def test_completed_scope_is_separate_from_open_and_blocked_work(self):
        items = audit(ROADMAP)
        self.assertEqual(["v0.9", "v0.10", "v1.0"], [i.version for i in items])
        self.assertTrue(items[0].ready)
        self.assertFalse(items[1].ready)
        self.assertEqual((1, 1, 1), (items[1].completed, items[1].open, items[1].blockers))
        self.assertEqual(1, items[2].blockers)

    def test_missing_and_duplicate_milestones_fail(self):
        with self.assertRaises(ValueError):
            audit("No milestones")
        with self.assertRaises(ValueError):
            audit("# v0.1 — Intro\n# v0.1 — Duplicate")

    def test_product_version_is_not_derived_from_roadmap(self):
        self.assertEqual("0.7.1", read_product_version(
            "org.gradle.jvmargs=-Xmx2g\nlauncherVersion=0.7.1\n"))
        with self.assertRaises(ValueError):
            read_product_version("launcherVersion=0.7.1\nlauncherVersion=0.9.0\n")

    def test_markdown_has_explicit_acceptance_caveat(self):
        report = markdown(audit(ROADMAP), "0.7.1")
        self.assertIn("third-party approval", report)
        self.assertIn("v0.10", report)
        self.assertIn("0.7.1", report)


if __name__ == "__main__":
    unittest.main()

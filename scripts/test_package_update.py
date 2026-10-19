import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from package_update import package


class PackageUpdateTest(unittest.TestCase):
    def test_manifest_records_hash_size_and_executable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            executable = root / "app/SyntaxLauncher/bin/SyntaxLauncher"
            executable.parent.mkdir(parents=True)
            executable.write_bytes(b"fixture")
            archive = root / "update.zip"
            package(root / "app", archive, "0.3.1", "linux-x64")
            with zipfile.ZipFile(archive) as payload:
                manifest = json.loads(payload.read("syntaxlauncher-update.json"))
                entry = next(e for e in manifest["entries"] if e["kind"] == "file")
                self.assertEqual(entry["sha256"], hashlib.sha256(b"fixture").hexdigest())
                self.assertTrue(entry["executable"])
                self.assertEqual(entry["size"], 7)
                self.assertEqual(manifest["version"], "0.3.1")

    def test_missing_executable_and_escaping_link_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                package(root, root / "update.zip", "0.3.1", "linux-x64")
            executable = root / "app/SyntaxLauncher/bin/SyntaxLauncher"
            executable.parent.mkdir(parents=True)
            executable.write_bytes(b"fixture")
            try:
                (executable.parent / "escape").symlink_to(root)
            except OSError:
                self.skipTest("Symbolic links unavailable")
            with self.assertRaises(ValueError):
                package(root / "app", root / "update.zip", "0.3.1", "linux-x64")
            self.assertFalse((root / "update.zip").exists())


if __name__ == "__main__":
    unittest.main()

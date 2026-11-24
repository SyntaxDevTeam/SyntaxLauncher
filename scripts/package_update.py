#!/usr/bin/env python3
"""Create the launcher's schema-1 update ZIP from a Compose portable app image.

Only stdlib is needed. Symlinks and executable bits are described explicitly in
its SHA-256-verified manifest, independent of ZIP platform permission behavior.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import zipfile

EXECUTABLES = {
    "linux-x64": "SyntaxLauncher/bin/SyntaxLauncher",
    "windows-x64": "SyntaxLauncher/SyntaxLauncher.exe",
    "macos-arm64": "SyntaxLauncher.app/Contents/MacOS/SyntaxLauncher",
}
MANIFEST = "syntaxlauncher-update.json"


def package(source: Path, output: Path, version: str, platform: str):
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[A-Za-z0-9.-]+)?(?:\+[A-Za-z0-9.-]+)?", version):
        raise ValueError("version must be a semantic version")
    source = source.resolve(strict=True)
    executable = EXECUTABLES[platform]
    if not (source / executable).is_file():
        raise ValueError(f"Missing portable application executable: {executable}")
    entries = []
    output.parent.mkdir(parents=True, exist_ok=True)
    partial = output.with_suffix(output.suffix + ".part")
    try:
        with zipfile.ZipFile(partial, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            # rglob does not follow symlink directories; links are separate manifest entries.
            for path in sorted(source.rglob("*")):
                name = path.relative_to(source).as_posix()
                if path.is_symlink():
                    target = os.readlink(path)
                    if Path(target).is_absolute() or not path.resolve(strict=True).is_relative_to(source):
                        raise ValueError(f"Symlink escapes app image: {name}")
                    entries.append(dict(path=name, kind="symlink", target=target))
                elif path.is_dir():
                    entries.append(dict(path=name, kind="directory"))
                elif path.is_file():
                    digest = hashlib.sha256()
                    with path.open("rb") as data:
                        for chunk in iter(lambda: data.read(1024 * 1024), b""):
                            digest.update(chunk)
                    entries.append(dict(path=name, kind="file", size=path.stat().st_size,
                                        sha256=digest.hexdigest(),
                                        executable=bool(path.stat().st_mode & 0o111) or name == executable))
                    archive.write(path, name)
                else:
                    raise ValueError(f"Unsupported app image entry: {name}")
            archive.writestr(MANIFEST, json.dumps(dict(schemaVersion=1, version=version,
                              platform=platform, executable=executable, entries=entries), separators=(",", ":")))
        partial.replace(output)
    finally:
        partial.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--platform", required=True, choices=EXECUTABLES)
    args = parser.parse_args()
    package(args.source, args.output, args.version, args.platform)

#!/usr/bin/env python3
"""Canonical SyntaxLauncher product version and Git release-tag guard."""
import argparse
from pathlib import Path
import re

VERSION_FILE = Path(__file__).resolve().parent.parent / "gradle.properties"
SEMVER = re.compile(
    r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
)


def read_launcher_version(path=VERSION_FILE):
    """Fail closed on absent/duplicated/invalid Gradle properties."""
    properties = Path(path).read_text(encoding="utf-8").splitlines()
    matches = [line.split("=", 1)[1].strip() for line in properties
               if re.match(r"^\s*launcherVersion\s*=", line)]
    if len(matches) != 1:
        raise ValueError("gradle.properties must contain exactly one launcherVersion=... entry")
    version = matches[0]
    match = SEMVER.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid launcherVersion SemVer: {version}")
    prerelease = match.group(4)
    if prerelease and any(len(part) > 1 and part.startswith("0") and part.isdecimal()
                          for part in prerelease.split(".")):
        raise ValueError("Numeric SemVer prerelease identifiers must not have leading zeroes")
    return version


def validate_git_ref(version, ref):
    if ref.startswith("refs/tags/"):
        if ref != "refs/tags/v" + version:
            raise ValueError(
                f"Release tag {ref!r} must match gradle.properties launcherVersion={version!r}"
            )
    return version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--github-ref", default="", help="Optional GitHub ref for tag/version validation")
    args = parser.parse_args()
    print(validate_git_ref(read_launcher_version(), args.github_ref))


if __name__ == "__main__":
    main()

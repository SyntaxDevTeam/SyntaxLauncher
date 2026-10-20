#!/usr/bin/env python3
"""Detect an intentional stable-version increment on the main branch."""
import argparse
from pathlib import Path
import re
import subprocess

from launcher_version import SEMVER, parse_launcher_version, read_launcher_version

STABLE = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
COMMIT = re.compile(r"^[0-9a-fA-F]{40}$")


def eligible(previous: str | None, current: str) -> bool:
    if not previous or previous == current or not STABLE.fullmatch(current):
        return False
    old_match = SEMVER.fullmatch(previous)
    if old_match is None:
        raise ValueError(f"Invalid previous product SemVer: {previous}")
    old_parts = tuple(int(part) for part in old_match.groups()[:3])
    new_parts = tuple(int(part) for part in STABLE.fullmatch(current).groups())
    return new_parts > old_parts or (new_parts == old_parts and not STABLE.fullmatch(previous))


def previous_version(commit: str) -> str | None:
    if not commit or commit == "0" * 40:
        return None
    if not COMMIT.fullmatch(commit):
        raise ValueError("Expected 40-character Git commit SHA for previous main revision")
    result = subprocess.run(
        ["git", "show", f"{commit}:gradle.properties"],
        capture_output=True, text=True, check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"Cannot inspect previous main version: {result.stderr.strip()}")
    return parse_launcher_version(result.stdout)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", default="", help="github.event.before for main push only")
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    current = read_launcher_version()
    previous = previous_version(args.before)
    publish = eligible(previous, current)
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as out:
            out.write(f"launcher_version={current}\npublish_release={'true' if publish else 'false'}\n")
    print(f"Current version: {current}; previous: {previous or 'unknown'}; release: {publish}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Publish an immutable DEV/SNAPSHOT release only after all platform bundles pass CI."""
import argparse
from pathlib import Path
import re
import subprocess

from launcher_version import read_launcher_version

PLATFORMS = ("linux-x64", "windows-x64", "macos-arm64")


def preview_tag(channel, version, run, attempt):
    if channel not in ("dev", "snapshot"):
        raise ValueError("Only DEV and SNAPSHOT are preview channels")
    if not re.fullmatch(r"[1-9][0-9]*", run) or not re.fullmatch(r"[1-9][0-9]*", attempt):
        raise ValueError("Invalid CI build identity")
    return f"{channel}-{run}.{attempt}-v{version}"


def publish(channel, version, run, attempt, commit, assets):
    tag = preview_tag(channel, version, run, attempt)
    if not re.fullmatch(r"[a-fA-F0-9]{40}", commit):
        raise ValueError("Expected a full verified commit SHA")
    files = [assets / name for platform in PLATFORMS for name in (
        f"syntaxlauncher-update-{platform}.zip", f"syntaxlauncher-{platform}.tar.gz")]
    if any(not file.is_file() or file.stat().st_size == 0 for file in files):
        raise ValueError("All six verified platform assets are required before publishing")
    # No clobber: retries get a new GITHUB_RUN_ATTEMPT, never mutate a published build.
    # Creating the ref explicitly fails closed if a pre-existing tag points elsewhere.
    subprocess.run(["gh", "api", "--method", "POST", "repos/{owner}/{repo}/git/refs",
                    "-f", f"ref=refs/tags/{tag}", "-f", f"sha={commit}"], check=True)
    subprocess.run(["gh", "release", "create", tag, "--verify-tag", "--draft", "--prerelease",
                    "--latest=false", "--title", f"SyntaxLauncher {channel.upper()} {version} · {run}.{attempt}",
                    "--notes", f"Preview build from {commit}. Select {channel.upper()} in launcher settings."
                    f"\n<!-- syntaxlauncher-build:{run}.{attempt} -->"], check=True)
    subprocess.run(["gh", "release", "upload", tag, *(str(file) for file in files)], check=True)
    subprocess.run(["gh", "release", "edit", tag, "--draft=false", "--prerelease", "--latest=false"], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channel", required=True, choices=("dev", "snapshot"))
    parser.add_argument("--run", required=True)
    parser.add_argument("--attempt", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--assets", type=Path, required=True)
    args = parser.parse_args()
    publish(args.channel, read_launcher_version(), args.run, args.attempt, args.commit, args.assets)


if __name__ == "__main__":
    main()

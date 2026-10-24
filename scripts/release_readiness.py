#!/usr/bin/env python3
"""Evidence-first SyntaxLauncher milestone release audit.

Read-only. This tool never rewrites roadmap checkboxes or treats a successful build
as proof of native/gameplay/account-provider acceptance.
"""
from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from launcher_version import SEMVER, parse_launcher_version

ROOT = Path(__file__).resolve().parents[1]
SECTION = re.compile(r"^# (v(?:0\.\d+|1\.0))\s+—\s+(.+?)\s*$")
CHECKBOX = re.compile(r"^\s*- \[([ xX])\]\s+(.+?)\s*$")


@dataclass(frozen=True)
class MilestoneSummary:
    version: str
    title: str
    completed: int
    open: int
    blockers: int
    examples: list[str]

    @property
    def ready(self) -> bool:
        return self.completed > 0 and self.open == 0


def audit(roadmap: str, *, max_examples: int = 5) -> list[MilestoneSummary]:
    if max_examples < 0:
        raise ValueError("max_examples cannot be negative")
    result: list[MilestoneSummary] = []
    current: str | None = None
    title = ""
    done = 0
    open_count = 0
    blocked = 0
    examples: list[str] = []

    def finish() -> None:
        if current is not None:
            result.append(MilestoneSummary(current, title, done, open_count, blocked, examples.copy()))

    for line in roadmap.splitlines():
        section = SECTION.match(line)
        if section:
            finish()
            current, title = section.groups()
            done = open_count = blocked = 0
            examples = []
            continue
        if current is None:
            continue
        checkbox = CHECKBOX.match(line)
        if checkbox is None:
            continue
        checked, detail = checkbox.groups()
        if checked.lower() == "x":
            done += 1
        else:
            open_count += 1
            if any(token in detail.upper() for token in ("P0", "BLOCKED", "APPROVAL")):
                blocked += 1
            if len(examples) < max_examples:
                examples.append(detail[:190])
    finish()
    if not result:
        raise ValueError("No release milestones found in roadmap")
    if len({item.version for item in result}) != len(result):
        raise ValueError("Duplicate milestone sections in roadmap")
    return result


def markdown(items: list[MilestoneSummary], product_version: str) -> str:
    out = [
        "## SyntaxLauncher release-readiness audit",
        "",
        f"Canonical product version: `{product_version}` (not the roadmap phase).",
        "",
        "| Milestone | Completed | Open | P0/approval-related open | Checklist |",
        "| --- | ---: | ---: | ---: | --- |",
    ]
    for item in items:
        out.append(
            f"| {item.version} — {item.title.replace('|', '/')} | {item.completed} | "
            f"{item.open} | {item.blockers} | {'Ready by checklist' if item.ready else 'Open'} |"
        )
    out += [
        "",
        "**Interpretation:** A green automated build does not resolve manual tests, "
        "third-party approval, native signing or a pending security review.",
        "This report is informational and does not change release numbers or roadmap status.",
        "",
    ]
    return "\n".join(out)


def read_product_version(properties: str) -> str:
    return parse_launcher_version(properties)


def production_milestone(product_version: str) -> str | None:
    version = SEMVER.fullmatch(product_version)
    if version is None:
        raise ValueError("Invalid canonical product version")
    return "v1.0" if int(version.group(1)) >= 1 and version.group(4) is None else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roadmap", type=Path, default=ROOT / "docs/ROADMAP.md")
    parser.add_argument("--properties", type=Path, default=ROOT / "gradle.properties")
    parser.add_argument("--format", choices=["markdown", "json"], default="markdown")
    enforcement = parser.add_mutually_exclusive_group()
    enforcement.add_argument("--enforce", metavar="VERSION",
                        help="Nonzero exit if the named milestone still has open checklist items")
    enforcement.add_argument("--enforce-production", action="store_true",
                             help="Block Stable 1.x and later while v1.0 acceptance remains open; allow pre-1.0 and previews")
    args = parser.parse_args()
    items = audit(args.roadmap.read_text(encoding="utf-8"))
    version = read_product_version(args.properties.read_text(encoding="utf-8"))
    print(json.dumps({
        "productVersion": version,
        "milestones": [{**asdict(i), "ready": i.ready} for i in items],
    }, indent=2) if args.format == "json" else markdown(items, version))
    target = production_milestone(version) if args.enforce_production else args.enforce
    if target is not None:
        matching = [item for item in items if item.version == target]
        if not matching:
            parser.error(f"Unknown milestone {target}")
        return 0 if matching[0].ready else 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

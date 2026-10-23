#!/usr/bin/env python3
"""Require the complete installer set and create/check its SHA-256 inventory."""
import argparse
import hashlib
from pathlib import Path
from package_installers import INSTALLERS

CHECKSUMS = 'SHA256SUMS-installers.txt'
NAMES = tuple(name for files in INSTALLERS.values() for name in files)


def checksum_text(directory):
    lines = []
    for name in sorted(NAMES):
        file = directory / name
        if file.is_symlink() or not file.is_file() or file.stat().st_size == 0:
            raise ValueError('Missing/empty installer: ' + name)
        with file.open('rb') as stream:
            lines.append(hashlib.file_digest(stream, 'sha256').hexdigest() + '  ' + name + '\n')
    return ''.join(lines)


def verify(directory, write=False):
    text = checksum_text(directory)
    inventory = directory / CHECKSUMS
    if write:
        inventory.write_text(text, encoding='utf-8')
    elif inventory.read_text(encoding='utf-8') != text:
        raise ValueError('Installer SHA-256 inventory differs from payloads')
    return [directory / name for name in NAMES] + [inventory]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    verify(args.directory, args.write)

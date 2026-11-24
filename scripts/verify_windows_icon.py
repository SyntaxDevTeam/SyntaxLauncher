"""Verify the portable Windows launcher's embedded icon against the source ICO.

Reads PE resources directly so the same check works on every build host without
third-party packages. The MSI uses this same verified application image.
"""
import argparse
from pathlib import Path
import struct


def embedded_icons(executable: bytes) -> list[bytes]:
    def unpack(fmt, offset):
        return struct.unpack_from(fmt, executable, offset)

    if executable[:2] != b'MZ':
        raise ValueError('Not a Windows executable')
    pe, = unpack('<I', 0x3c)
    if executable[pe:pe + 4] != b'PE\0\0':
        raise ValueError('Missing PE header')
    sections, = unpack('<H', pe + 6)
    optional_size, = unpack('<H', pe + 20)
    optional = pe + 24
    magic, = unpack('<H', optional)
    if magic not in (0x10b, 0x20b):
        raise ValueError('Unknown PE optional header')
    directories = optional + (112 if magic == 0x20b else 96)
    resource_rva, resource_size = unpack('<II', directories + 16)

    def file_offset(rva, size):
        for index in range(sections):
            header = optional + optional_size + index * 40
            _, address, raw_size, raw_offset = unpack('<IIII', header + 8)
            if address <= rva and rva + size <= address + raw_size:
                offset = raw_offset + rva - address
                if offset + size <= len(executable):
                    return offset
        raise ValueError('PE resource points outside file-backed sections')

    base = file_offset(resource_rva, resource_size)

    def entries(relative):
        if relative < 0 or relative + 16 > resource_size:
            raise ValueError('Invalid PE resource directory')
        named, numbered = unpack('<HH', base + relative + 12)
        if relative + 16 + (named + numbered) * 8 > resource_size:
            raise ValueError('Truncated PE resource entries')
        return [unpack('<II', base + relative + 16 + i * 8)
                for i in range(named + numbered)]

    icons = []
    for kind, directory in entries(0):
        if kind != 3:  # RT_ICON: original bitmap/PNG bytes, without the ICO directory.
            continue
        if not directory & 0x80000000:
            raise ValueError('Missing icon directory')
        for _, language_directory in entries(directory & 0x7fffffff):
            if not language_directory & 0x80000000:
                raise ValueError('Missing icon language directory')
            for _, data in entries(language_directory & 0x7fffffff):
                if data & 0x80000000 or data + 16 > resource_size:
                    raise ValueError('Invalid icon data entry')
                rva, size = unpack('<II', base + data)
                start = file_offset(rva, size)
                icons.append(executable[start:start + size])
    return icons


def verify(executable: Path, icon: Path) -> None:
    source = icon.read_bytes()
    reserved, kind, count = struct.unpack_from('<HHH', source)
    if reserved != 0 or kind != 1 or count == 0:
        raise ValueError('Expected a nonempty ICO file')
    expected = []
    for index in range(count):
        size, offset = struct.unpack_from('<II', source, 6 + index * 16 + 8)
        if offset < 6 + count * 16 or offset + size > len(source) or size == 0:
            raise ValueError('Invalid ICO image')
        expected.append(source[offset:offset + size])
    actual = embedded_icons(executable.read_bytes())
    if sorted(actual) != sorted(expected):
        raise ValueError('Windows launcher icon differs from repository ICO')
    print(f'Windows launcher icon verified: {count} images match {icon.name}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exe', type=Path, required=True)
    parser.add_argument('--icon', type=Path, default=Path(__file__).resolve().parents[1] /
                        'launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.ico')
    args = parser.parse_args()
    verify(args.exe, args.icon)

import struct
import tempfile
import unittest
from pathlib import Path

from verify_windows_icon import verify, embedded_icons


class WindowsIconTest(unittest.TestCase):
    def test_actual_pe_resources_must_match_all_source_images(self):
        icon = Path(__file__).resolve().parents[1] / 'launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.ico'
        source = icon.read_bytes()
        count, = struct.unpack_from('<H', source, 4)
        images = []
        for index in range(count):
            size, offset = struct.unpack_from('<II', source, 14 + index * 16)
            images.append(source[offset:offset + size])
        # Minimal PE with a real resource directory: RT_ICON -> IDs -> languages -> data.
        languages = 40 + 8 * count
        data_entries = languages + 24 * count
        payload = data_entries + 16 * count
        resource = bytearray(payload + sum(map(len, images)))
        struct.pack_into('<HII', resource, 14, 1, 3, 0x80000018)
        struct.pack_into('<H', resource, 38, count)
        for index, image in enumerate(images):
            language = languages + 24 * index
            data = data_entries + 16 * index
            struct.pack_into('<II', resource, 40 + index * 8, index + 1, language | 0x80000000)
            struct.pack_into('<HII', resource, language + 14, 1, 1033, data)
            struct.pack_into('<IIII', resource, data, 4096 + payload, len(image), 0, 0)
            resource[payload:payload + len(image)] = image
            payload += len(image)
        with tempfile.TemporaryDirectory() as directory:
            executable = Path(directory) / 'launcher.exe'
            for magic, optional_size, directory_offset in [(0x10b, 224, 96), (0x20b, 240, 112)]:
                header = bytearray(512)
                header[:2] = b'MZ'
                struct.pack_into('<I', header, 0x3c, 128)
                header[128:132] = b'PE\0\0'
                struct.pack_into('<H', header, 134, 1)
                struct.pack_into('<H', header, 148, optional_size)
                struct.pack_into('<H', header, 152, magic)
                struct.pack_into('<II', header, 152 + directory_offset + 16, 4096, len(resource))
                struct.pack_into('<IIII', header, 152 + optional_size + 8, len(resource), 4096, len(resource), 512)
                executable.write_bytes(header + resource)
                verify(executable, icon)
                wrong = Path(directory) / 'wrong.ico'
                wrong.write_bytes(source[:-1] + bytes([source[-1] ^ 255]))
                with self.assertRaisesRegex(ValueError, 'differs'):
                    verify(executable, wrong)
                executable.write_bytes(header + resource[:-1])
                with self.assertRaisesRegex(ValueError, 'outside'):
                    verify(executable, icon)

    def test_non_executable_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Not a Windows'):
            embedded_icons(b'not an executable')

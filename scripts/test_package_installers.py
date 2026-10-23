import os
from pathlib import Path
import subprocess
import shutil
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from package_installers import (NS, UPGRADE_CODE, appdir, native_version, linux_release, desktop_template, validate_image, wix_source, windows)
from verify_installers import NAMES, verify
from publish_preview import PLATFORMS, publish


class InstallerTest(unittest.TestCase):
    def test_versions_use_canonical_core_without_pre_1_windows_upgrade_inversion(self):
        self.assertEqual('0.9.3', native_version('0.9.3-DEV', 'windows-x64'))
        self.assertEqual('1.0.0', native_version('1.0.0', 'windows-x64'))
        self.assertEqual('0.9.3', native_version('0.9.3-DEV', 'linux-x64'))
        self.assertEqual('1.9.3', native_version('0.9.3-DEV', 'macos-arm64'))
        for version in ['1.2', '1.2.3;evil', '256.0.0', '1.256.0', '1.0.65536']:
            with self.assertRaises(ValueError):
                native_version(version, 'windows-x64')

    def test_linux_preview_revisions_sort_before_stable_and_distinguish_builds(self):
        self.assertEqual('0.dev.42.2', linux_release('0.9.3-DEV', '42', '2'))
        self.assertEqual('0.rc.1.43.1', linux_release('0.9.3-rc.1', '43', '1'))
        self.assertEqual('1', linux_release('0.9.3', '44', '1'))
        if shutil.which('dpkg'):
            versions = ['0.9.3-' + linux_release('0.9.3-DEV', '42', '1'),
                        '0.9.3-' + linux_release('0.9.3-DEV', '43', '1'),
                        '0.9.3-' + linux_release('0.9.3', '44', '1'), '1.0.0-1']
            for older, newer in zip(versions, versions[1:]):
                subprocess.run(['dpkg', '--compare-versions', older, 'lt', newer], check=True)
        for build, attempt in [('bad', '1'), ('42', '0')]:
            with self.assertRaises(ValueError):
                linux_release('0.9.3-DEV', build, attempt)

    def test_msi_has_stable_identity_full_payload_user_scope_and_menu(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image = root / 'app image'
            (image / 'app').mkdir(parents=True)
            (image / 'SyntaxLauncher.exe').write_bytes(b'launcher')
            (image / 'app/runtime & config.cfg').write_bytes(b'config')
            documents = []
            for i in range(2):
                source = root / f'installer-{i}.wxs'
                wix_source(image, '0.9.3-DEV', source)
                documents.append(ET.parse(source).getroot())
            ns = {'w': NS}
            package = documents[0].find('w:Package', ns)
            self.assertEqual('perUser', package.attrib['Scope'])
            self.assertEqual(UPGRADE_CODE, package.attrib['UpgradeCode'])
            self.assertEqual('0.9.3', package.attrib['Version'])
            files = package.findall('.//w:File', ns)
            self.assertEqual({str(p) for p in image.rglob('*') if p.is_file()}, {f.attrib['Source'] for f in files})
            components = package.findall('.//w:Component', ns)
            self.assertEqual([(c.attrib['Id'], c.attrib['Guid']) for c in components],
                             [(c.attrib['Id'], c.attrib['Guid']) for c in documents[1].findall('.//w:Component', ns)])
            self.assertTrue(all(c.find('w:RegistryValue', ns).attrib['Root'] == 'HKCU' for c in components))
            self.assertEqual('[INSTALLFOLDER]SyntaxLauncher.exe', package.find('.//w:Shortcut', ns).attrib['Target'])
            self.assertIsNone(package.find('.//w:RemoveFile', ns))
            self.assertEqual('SAMEVERSIONINSTALLED', package.find('w:Upgrade/w:UpgradeVersion', ns).attrib['Property'])
            self.assertIn('OR Installed', package.find('w:Launch', ns).attrib['Condition'])
            self.assertEqual('afterInstallInitialize', package.find('w:MajorUpgrade', ns).attrib['Schedule'])

    def test_headless_menu_preparation_retains_native_scripts_and_rejects_unknown_templates(self):
        for kind, marker in [('deb', 'set -e'), ('rpm', '%post')]:
            script = desktop_template(marker + '\nDESKTOP_COMMANDS_INSTALL\n', kind)
            self.assertLess(script.index('mkdir -p'), script.index('DESKTOP_COMMANDS_INSTALL'))
            self.assertIn('/usr/share/desktop-directories', script)
            self.assertIn('/etc/xdg/menus/applications-merged', script)
            self.assertEqual(1, script.count('mkdir -p'))
        with self.assertRaises(ValueError):
            desktop_template('unknown future format', 'deb')

    def test_wrong_wix_major_cannot_silently_build_with_old_toolset(self):
        with patch('package_installers.subprocess.check_output', return_value='3.14.1'), patch('package_installers.run') as command:
            with self.assertRaises(ValueError):
                windows(Path('image'), '0.9.3', Path('output'), Path('work'))
            command.assert_not_called()

    @unittest.skipIf(os.name == 'nt', 'POSIX AppImage entry point')
    def test_appdir_preserves_layout_permissions_and_arguments_in_paths_with_spaces(self):
        with tempfile.TemporaryDirectory(prefix='syntax installer ') as temporary:
            root = Path(temporary)
            image = root / 'image'
            (image / 'bin').mkdir(parents=True)
            executable = image / 'bin/SyntaxLauncher'
            executable.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
            executable.chmod(0o755)
            (image / 'relative-link').symlink_to('bin/SyntaxLauncher')
            validate_image(image, 'linux-x64')
            target = root / 'AppDir'
            target.mkdir()
            appdir(image, target)
            result = subprocess.check_output([str(target / 'AppRun'), 'one two', '$no-substitution'], text=True)
            self.assertEqual('one two\n$no-substitution\n', result)
            self.assertTrue((target / '.DirIcon').is_symlink())
            self.assertEqual('syntaxlauncher.png', os.readlink(target / '.DirIcon'))
            self.assertTrue((target / 'SyntaxLauncher/relative-link').is_symlink())
            self.assertIn('Categories=Game;', (target / 'syntaxlauncher.desktop').read_text())
            (image / 'escape').symlink_to(root)
            with self.assertRaises(ValueError):
                validate_image(image, 'linux-x64')

    def test_partial_or_corrupted_installer_set_cannot_publish(self):
        with tempfile.TemporaryDirectory() as temporary, patch('publish_preview.subprocess.run') as command:
            assets = Path(temporary)
            for platform in PLATFORMS:
                for name in (f'syntaxlauncher-update-{platform}.zip', f'syntaxlauncher-{platform}.tar.gz'):
                    (assets / name).write_bytes(b'verified app')
            for name in NAMES[:-1]:
                (assets / name).write_bytes(b'installer')
            with self.assertRaises(ValueError):
                publish('dev', '0.9.3-DEV', '12', '1', 'a' * 40, assets, installers=True)
            command.assert_not_called()
            (assets / NAMES[-1]).write_bytes(b'installer')
            verify(assets, write=True)
            publish('dev', '0.9.3-DEV', '12', '1', 'a' * 40, assets, installers=True)
            upload = command.call_args_list[2].args[0]
            self.assertTrue(all(str(assets / name) in upload for name in NAMES))
            command.reset_mock()
            (assets / NAMES[0]).write_bytes(b'tampered')
            with self.assertRaises(ValueError):
                publish('dev', '0.9.3-DEV', '13', '1', 'a' * 40, assets, installers=True)
            command.assert_not_called()


if __name__ == '__main__':
    unittest.main()

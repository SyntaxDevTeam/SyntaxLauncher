#!/usr/bin/env python3
"""Package a verified Compose app image into native installers on its host OS.

The input is the same application image used for portable/update publication.
No game, account, settings or launcher-update directory is an installer payload.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

from launcher_version import parse_launcher_version, read_launcher_version

ROOT = Path(__file__).resolve().parent.parent
BRANDING = ROOT / 'launcher-desktop/src/main/resources/branding'
WIX_VERSION = '7.0.0'
UPGRADE_CODE = 'D64AD1BC-4B59-4C1C-A45C-ABBC92E21739'
INSTALLERS = {
    'linux-x64': ('syntaxlauncher-linux-x64.deb', 'syntaxlauncher-linux-x64.rpm', 'syntaxlauncher-linux-x64.AppImage'),
    'windows-x64': ('syntaxlauncher-windows-x64.msi',),
    'macos-arm64': ('syntaxlauncher-macos-arm64.dmg',),
}
APPIMAGE_TOOLS = {
    'appimagetool': ('https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage',
                     'ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0'),
    'runtime': ('https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64',
                '2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d'),
}
NS = 'http://wixtoolset.org/schemas/v4/wxs'
UI_NS = NS + '/ui'


def numeric_version(version):
    parse_launcher_version('launcherVersion=' + version)
    return version.split('-')[0].split('+')[0]


def native_version(version, platform):
    numeric = numeric_version(version)
    parts = [int(p) for p in numeric.split('.')]
    if platform == 'windows-x64' and (parts[0] > 255 or parts[1] > 255 or parts[2] > 65535):
        raise ValueError('Product version exceeds Windows Installer version limits')
    if platform == 'macos-arm64' and parts[0] == 0:
        parts[0] = 1  # Existing jpackage macOS compatibility mapping only.
    return '.'.join(map(str, parts))


def linux_release(version, build='0', attempt='1'):
    numeric_version(version)
    if not re.fullmatch(r'[0-9]+', build) or not re.fullmatch(r'[1-9][0-9]*', attempt):
        raise ValueError('Invalid package build identity')
    prerelease = version.split('+')[0].partition('-')[2]
    if not prerelease:
        return '1'
    return '0.' + prerelease.lower().replace('-', '.') + '.' + build + '.' + attempt


def validate_image(image, platform):
    image = image.resolve(strict=True)
    executable = {'windows-x64': 'SyntaxLauncher.exe', 'linux-x64': 'bin/SyntaxLauncher',
                  'macos-arm64': 'Contents/MacOS/SyntaxLauncher'}[platform]
    if not (image / executable).is_file():
        raise ValueError('Application image is missing its platform executable')
    for path in image.rglob('*'):
        if path.is_symlink():
            if platform == 'windows-x64' or not path.resolve(strict=True).is_relative_to(image):
                raise ValueError('Unsupported/escaping application image symlink')
        elif not (path.is_file() or path.is_dir()):
            raise ValueError('Unsupported application image entry')
    return image


def jpackage_tool():
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / ('jpackage.exe' if os.name == 'nt' else 'jpackage') if home else None
    return str(candidate) if candidate and candidate.is_file() else 'jpackage'


def run(command, **kwargs):
    subprocess.run([str(p) for p in command], check=True, **kwargs)


def identifier(prefix, value):
    return prefix + hashlib.sha256(value.encode()).hexdigest()[:30]


def wix_source(image, version, output):
    """Explicit components with stable IDs/HKCU key paths for a per-user MSI."""
    image = validate_image(image, 'windows-x64')
    ET.register_namespace('', NS)
    ET.register_namespace('ui', UI_NS)
    def add(parent, name, **attributes):
        return ET.SubElement(parent, '{' + NS + '}' + name, attributes)
    root = ET.Element('{' + NS + '}Wix')
    package = add(root, 'Package', Name='SyntaxLauncher', Manufacturer='SyntaxDevTeam',
                  Version=native_version(version, 'windows-x64'), UpgradeCode=UPGRADE_CODE,
                  Scope='perUser', Language='1033', InstallerVersion='500')
    add(package, 'MajorUpgrade', DowngradeErrorMessage='A newer SyntaxLauncher is already installed.',
        Schedule='afterInstallInitialize')
    upgrade = add(package, 'Upgrade', Id=UPGRADE_CODE)
    add(upgrade, 'UpgradeVersion', Minimum=native_version(version, 'windows-x64'),
        Maximum=native_version(version, 'windows-x64'), IncludeMinimum='yes', IncludeMaximum='yes',
        OnlyDetect='yes', Property='SAMEVERSIONINSTALLED')
    add(package, 'Launch', Condition='NOT SAMEVERSIONINSTALLED OR Installed',
        Message='Uninstall the previous preview of this version before installing this build. Your games and settings will remain.')
    add(package, 'MediaTemplate', EmbedCab='yes')
    add(package, 'Icon', Id='LauncherIcon', SourceFile=str(BRANDING / 'syntaxlauncher-icon.ico'))
    add(package, 'Property', Id='ARPPRODUCTICON', Value='LauncherIcon')
    add(package, 'Property', Id='ARPURLINFOABOUT', Value='https://syntaxdevteam.pl')
    add(package, 'Property', Id='ARPCOMMENTS', Value='SyntaxLauncher ' + version)
    local = add(package, 'StandardDirectory', Id='LocalAppDataFolder')
    programs = add(local, 'Directory', Id='UserPrograms', Name='Programs')
    target = add(programs, 'Directory', Id='INSTALLFOLDER', Name='SyntaxLauncher')
    directories = {'': target}
    feature = add(package, 'Feature', Id='Complete', Title='SyntaxLauncher', Level='1')
    for path in sorted(image.rglob('*')):
        relative = path.relative_to(image).as_posix()
        if path.is_dir():
            parent = path.parent.relative_to(image).as_posix()
            directories[relative] = add(directories['' if parent == '.' else parent], 'Directory',
                                        Id=identifier('D', relative), Name=path.name)
            continue
        parent = path.parent.relative_to(image).as_posix()
        component_id = identifier('C', relative)
        component = add(directories['' if parent == '.' else parent], 'Component', Id=component_id,
                        Guid=str(uuid.uuid5(uuid.UUID(UPGRADE_CODE), relative)).upper())
        add(component, 'File', Id=identifier('F', relative), Source=str(path), KeyPath='no')
        add(component, 'RegistryValue', Root='HKCU', Key='Software\\SyntaxDevTeam\\SyntaxLauncher\\Installer',
            Name=component_id, Type='integer', Value='1', KeyPath='yes')
        add(component, 'RemoveFolder', Id=identifier('R', relative), On='uninstall')
        add(feature, 'ComponentRef', Id=component_id)
    menu = add(package, 'StandardDirectory', Id='ProgramMenuFolder')
    folder = add(menu, 'Directory', Id='LauncherMenu', Name='SyntaxLauncher')
    component = add(folder, 'Component', Id='StartMenu', Guid=str(uuid.uuid5(uuid.UUID(UPGRADE_CODE), 'start-menu')).upper())
    add(component, 'Shortcut', Id='StartShortcut', Name='SyntaxLauncher', Target='[INSTALLFOLDER]SyntaxLauncher.exe',
        WorkingDirectory='INSTALLFOLDER', Icon='LauncherIcon')
    add(component, 'RemoveFolder', Id='RemoveMenu', On='uninstall')
    add(component, 'RemoveFolder', Id='RemoveUserProgramsIfEmpty', Directory='UserPrograms', On='uninstall')
    add(component, 'RegistryValue', Root='HKCU', Key='Software\\SyntaxDevTeam\\SyntaxLauncher\\Installer',
        Name='StartMenu', Type='integer', Value='1', KeyPath='yes')
    add(feature, 'ComponentRef', Id='StartMenu')
    ET.SubElement(package, '{' + UI_NS + '}WixUI', {'Id': 'WixUI_InstallDir', 'InstallDirectory': 'INSTALLFOLDER'})
    license_rtf = output.parent / 'license.rtf'
    text = (ROOT / 'LICENSE').read_text().replace('\\', '\\\\').replace('{', '\\{').replace('}', '\\}')
    license_rtf.write_text('{\\rtf1\\ansi ' + text.replace('\n', '\\par\n') + '}')
    add(package, 'WixVariable', Id='WixUILicenseRtf', Value=str(license_rtf))
    ET.indent(root)
    ET.ElementTree(root).write(output, encoding='utf-8', xml_declaration=True)


def windows(image, version, output, work):
    tool_version = subprocess.check_output(['wix', '--version'], text=True).strip()
    if not re.match(r'^7\.0\.0(?:\D|$)', tool_version):
        raise ValueError('Expected pinned WiX ' + WIX_VERSION + ', got ' + tool_version)
    source = work / 'SyntaxLauncher.wxs'
    wix_source(image, version, source)
    run(['wix', 'build', source, '-arch', 'x64', '-ext', 'WixToolset.UI.wixext/' + WIX_VERSION,
         '-intermediatefolder', work / 'obj', '-pdbtype', 'none', '-o', output / INSTALLERS['windows-x64'][0]])


def checked_tool(name, tools):
    url, expected = APPIMAGE_TOOLS[name]
    target = tools / name
    valid = False
    if target.is_file():
        with target.open('rb') as stream:
            valid = hashlib.file_digest(stream, 'sha256').hexdigest() == expected
    if not valid:
        target.unlink(missing_ok=True)
        request = urllib.request.Request(url, headers={'User-Agent': 'SyntaxLauncher-packaging'})
        with urllib.request.urlopen(request, timeout=120) as response, target.open('wb') as data:
            shutil.copyfileobj(response, data)
    with target.open('rb') as data:
        if hashlib.file_digest(data, 'sha256').hexdigest() != expected:
            target.unlink()
            raise ValueError('AppImage tool SHA-256 mismatch: ' + name)
    target.chmod(0o755)
    return target


def appdir(image, destination):
    shutil.copytree(image, destination / 'SyntaxLauncher', symlinks=True)
    (destination / 'AppRun').write_text('#!/bin/sh\nset -eu\nAPPDIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\nexec "$APPDIR/SyntaxLauncher/bin/SyntaxLauncher" "$@"\n')
    (destination / 'AppRun').chmod(0o755)
    (destination / 'syntaxlauncher.desktop').write_text('[Desktop Entry]\nType=Application\nName=SyntaxLauncher\nComment=Minecraft launcher by SyntaxDevTeam\nExec=AppRun\nIcon=syntaxlauncher\nTerminal=false\nCategories=Game;\n')
    shutil.copyfile(BRANDING / 'syntaxlauncher-icon.png', destination / 'syntaxlauncher.png')
    (destination / '.DirIcon').symlink_to('syntaxlauncher.png')
    shutil.copyfile(ROOT / 'LICENSE', destination / 'LICENSE')


def desktop_template(text, kind):
    # Minimal/headless installations may have xdg-utils but none of its target directories.
    # Keep the JDK's own maintainer script; prepare only shared standard menu directories.
    marker = 'set -e\n' if kind == 'deb' else '%post\n'
    if marker not in text:
        raise ValueError('Unrecognized jpackage desktop-integration template')
    return text.replace(marker, marker + '\nmkdir -p /usr/share/applications /usr/share/desktop-directories /etc/xdg/menus/applications-merged\n', 1)


def linux_resources(work):
    executable = Path(shutil.which(jpackage_tool()) or jpackage_tool()).resolve()
    home = Path(os.environ.get('JAVA_HOME', str(executable.parent.parent)))
    module = home / 'jmods/jdk.jpackage.jmod'
    resources = work / 'resources'
    resources.mkdir()
    templates = [('deb', 'template.postinst', 'postinst'), ('rpm', 'template.spec', 'syntaxlauncher.spec')]
    if module.is_file():
        with zipfile.ZipFile(module) as archive:
            contents = {source: archive.read('classes/jdk/jpackage/internal/resources/' + source).decode('utf-8')
                        for _, source, _ in templates}
    else:
        # Some CI JDK distributions ship the tool in lib/modules without a jmods directory.
        extracted = work / 'jdk-resources'
        run([home / 'bin/jimage', 'extract', '--include', 'glob:**/template.postinst,glob:**/template.spec',
             '--dir', extracted, home / 'lib/modules'])
        base = extracted / 'jdk.jpackage/jdk/jpackage/internal/resources'
        contents = {source: (base / source).read_text(encoding='utf-8') for _, source, _ in templates}
    for kind, source, target in templates:
        (resources / target).write_text(desktop_template(contents[source], kind), encoding='utf-8')
    return resources


def linux(image, version, output, work):
    resources = linux_resources(work)
    for kind in ('deb', 'rpm'):
        folder = work / kind
        folder.mkdir()
        command = [jpackage_tool(), '--type', kind, '--app-image', image, '--name', 'SyntaxLauncher',
                   '--app-version', native_version(version, 'linux-x64'), '--vendor', 'SyntaxDevTeam',
                   '--description', 'Minecraft launcher by SyntaxDevTeam', '--license-file', ROOT / 'LICENSE',
                   '--linux-package-name', 'syntaxlauncher', '--linux-shortcut', '--linux-menu-group', 'Game',
                   '--linux-app-release', linux_release(version, os.environ.get('GITHUB_RUN_NUMBER', '0'), os.environ.get('GITHUB_RUN_ATTEMPT', '1')),
                   '--resource-dir', resources, '--dest', folder]
        if kind == 'deb':
            command += ['--linux-deb-maintainer', 'wieszczy85@gmail.com', '--linux-app-category', 'games']
        else:
            command += ['--linux-rpm-license-type', 'MIT', '--linux-app-category', 'Amusements/Games']
        run(command)
        files = list(folder.glob('*.' + kind))
        if len(files) != 1:
            raise ValueError('Expected exactly one ' + kind + ' package')
        shutil.copyfile(files[0], output / ('syntaxlauncher-linux-x64.' + kind))
    directory = work / 'SyntaxLauncher.AppDir'
    directory.mkdir()
    appdir(image, directory)
    tools = work / 'tools'
    tools.mkdir()
    tool = checked_tool('appimagetool', tools)
    runtime = checked_tool('runtime', tools)
    run([tool, '--appimage-extract-and-run', '--runtime-file', runtime, directory,
         output / 'syntaxlauncher-linux-x64.AppImage'], env={**os.environ, 'ARCH': 'x86_64', 'VERSION': version})
    (output / 'syntaxlauncher-linux-x64.AppImage').chmod(0o755)


def macos(image, version, output, work):
    run([jpackage_tool(), '--type', 'dmg', '--app-image', image, '--name', 'SyntaxLauncher',
         '--app-version', native_version(version, 'macos-arm64'), '--vendor', 'SyntaxDevTeam',
         '--dest', work])
    files = list(work.glob('*.dmg'))
    if len(files) != 1:
        raise ValueError('Expected exactly one DMG')
    shutil.copyfile(files[0], output / INSTALLERS['macos-arm64'][0])


def package(image, platform, version, output):
    image = validate_image(image, platform)
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if any((output / name).exists() for name in INSTALLERS[platform]):
        raise ValueError('Refusing to overwrite an existing installer')
    with tempfile.TemporaryDirectory(prefix='syntaxlauncher-installer-') as temporary:
        work = Path(temporary)
        {'windows-x64': windows, 'linux-x64': linux, 'macos-arm64': macos}[platform](image, version, output, work)
    for name in INSTALLERS[platform]:
        if not (output / name).is_file() or (output / name).stat().st_size == 0:
            raise ValueError('Missing installer: ' + name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', type=Path, required=True)
    parser.add_argument('--platform', choices=INSTALLERS, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    package(args.image, args.platform, read_launcher_version(), args.output)

#!/usr/bin/env bash
set -Eeuo pipefail
trap 'echo "Installer smoke failed at line $LINENO" >&2' ERR
packages="$PWD/launcher-desktop/build/installers"
evidence="$PWD/launcher-desktop/build/installer-evidence"
mkdir -p "$evidence"
smoke_root="$(mktemp -d)"
trap 'rm -rf "$smoke_root"' EXIT
export XDG_DATA_HOME="$smoke_root/data" XDG_CONFIG_HOME="$smoke_root/config"
export XDG_CACHE_HOME="$smoke_root/cache" XDG_STATE_HOME="$smoke_root/state"
mkdir -p "$XDG_DATA_HOME/syntaxlauncher/instances/smoke/saves" "$XDG_CONFIG_HOME/syntaxlauncher"
printf 'keep-world\n' > "$XDG_DATA_HOME/syntaxlauncher/instances/smoke/saves/marker"
printf 'keep-settings\n' > "$XDG_CONFIG_HOME/syntaxlauncher/marker"
# Native startup exercises the installed runtime/Skiko/X11 without a player/game.
startup() {
    local result=0
    timeout --signal=TERM 15s xvfb-run -a "$1" > "$evidence/$2.log" 2>&1 || result=$?
    if [[ "$result" != 124 ]]; then cat "$evidence/$2.log"; return 1; fi
    if grep -Eq 'UnsatisfiedLinkError|ClassNotFoundException|Exception in thread' "$evidence/$2.log"; then
        cat "$evidence/$2.log"; return 1
    fi
}
sudo apt-get install -y "$packages/syntaxlauncher-linux-x64.deb"
test -x /opt/syntaxlauncher/bin/SyntaxLauncher || { dpkg -L syntaxlauncher; exit 1; }
desktop_entry="$(find /usr/share/applications /usr/local/share/applications -iname '*syntaxlauncher*.desktop' -print -quit 2>/dev/null || true)"
test -n "$desktop_entry"
desktop-file-validate "$desktop_entry"
metainfo="/usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml"
test -r "$metainfo"
appstreamcli validate --no-net "$metainfo"
grep -q '<project_license>MIT</project_license>' "$metainfo"
test -r /usr/share/doc/syntaxlauncher/copyright
grep -qx 'License: MIT' /usr/share/doc/syntaxlauncher/copyright
dpkg-deb -c "$packages/syntaxlauncher-linux-x64.deb" > "$evidence/deb-contents.txt"
grep -q 'usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml' "$evidence/deb-contents.txt"
grep -q 'usr/share/doc/syntaxlauncher/copyright' "$evidence/deb-contents.txt"
dpkg-deb -f "$packages/syntaxlauncher-linux-x64.deb" > "$evidence/deb-control.txt"
startup /opt/syntaxlauncher/bin/SyntaxLauncher deb-startup
sudo apt-get remove -y syntaxlauncher
test ! -e /opt/syntaxlauncher/bin/SyntaxLauncher
grep -qx keep-world "$XDG_DATA_HOME/syntaxlauncher/instances/smoke/saves/marker"
grep -qx keep-settings "$XDG_CONFIG_HOME/syntaxlauncher/marker"
# Inspect and install/remove the RPM on an actual RPM distribution, not with --nodeps.
rpm -qpi "$packages/syntaxlauncher-linux-x64.rpm" > "$evidence/rpm-metadata.txt"
grep -Eq '^License[[:space:]]*:[[:space:]]*MIT' "$evidence/rpm-metadata.txt"
rpm -qlp "$packages/syntaxlauncher-linux-x64.rpm" > "$evidence/rpm-contents.txt"
grep -q '/usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml' "$evidence/rpm-contents.txt"
docker run --rm -v "$packages:/packages:ro" fedora:44 bash -euc '
    mkdir -p /root/.local/share/syntaxlauncher/instances/smoke/saves
    echo keep-world > /root/.local/share/syntaxlauncher/instances/smoke/saves/marker
    dnf install -y /packages/syntaxlauncher-linux-x64.rpm
    test -x /opt/syntaxlauncher/bin/SyntaxLauncher
    test -r /usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml
    grep -q "<project_license>MIT</project_license>" /usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml
    dnf remove -y syntaxlauncher
    test ! -e /opt/syntaxlauncher/bin/SyntaxLauncher
    grep -qx keep-world /root/.local/share/syntaxlauncher/instances/smoke/saves/marker
' > "$evidence/fedora-rpm-lifecycle.log" 2>&1
# Extraction needs no FUSE mount and checks the payload used on other distributions.
chmod +x "$packages/syntaxlauncher-linux-x64.AppImage"
pushd "$smoke_root" >/dev/null
"$packages/syntaxlauncher-linux-x64.AppImage" --appimage-extract > "$evidence/appimage-extract.log"
test -x squashfs-root/AppRun
test -L squashfs-root/.DirIcon
test -r squashfs-root/usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml
grep -q '<project_license>MIT</project_license>' squashfs-root/usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml
appstreamcli validate --no-net squashfs-root/usr/share/metainfo/pl.syntaxdevteam.syntaxlauncher.metainfo.xml
startup "$smoke_root/squashfs-root/AppRun" appimage-startup
popd >/dev/null

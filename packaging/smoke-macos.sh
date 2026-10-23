#!/usr/bin/env bash
set -euo pipefail
image="$PWD/launcher-desktop/build/installers/syntaxlauncher-macos-arm64.dmg"
evidence="$PWD/launcher-desktop/build/installer-evidence"
mkdir -p "$evidence"
smoke_root="$(mktemp -d)"
mounted=false
cleanup() {
    if [[ "$mounted" == true ]]; then hdiutil detach "$smoke_root/mount"; fi
    rm -rf "$smoke_root"
}
trap cleanup EXIT
hdiutil verify "$image" > "$evidence/dmg-verify.log"
hdiutil attach "$image" -readonly -nobrowse -mountpoint "$smoke_root/mount" > "$evidence/dmg-attach.log"
mounted=true
test -L "$smoke_root/mount/Applications"
mkdir "$smoke_root/Applications"
ditto "$smoke_root/mount/SyntaxLauncher.app" "$smoke_root/Applications/SyntaxLauncher.app"
app="$smoke_root/Applications/SyntaxLauncher.app"
test -x "$app/Contents/MacOS/SyntaxLauncher"
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Contents/Info.plist")" = pl.syntaxdevteam.syntaxlauncher
# Copy/removal tests affect only this temporary app and preserve a separate data fixture.
printf keep-world > "$smoke_root/world-marker"
rm -rf "$app"
test "$(cat "$smoke_root/world-marker")" = keep-world

# SyntaxLauncher v0.1 — Preview testing checklist

These builds are **unverified previews**. They compile and package in CI, but a real Minecraft game launch from a clean desktop installation must still be verified before a v0.1 release candidate is declared functional.

## Download and extract

1. Open the [Build and package SyntaxLauncher Actions workflow](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/workflows/build.yml). Select the most recent successful run for `main`.
2. Download the platform artifact `syntaxlauncher-linux-x64`, `syntaxlauncher-windows-x64` or `syntaxlauncher-macos-arm64`.
3. Unzip the GitHub Actions artifact; it contains an inner `syntaxlauncher-<platform>.tar.gz`. Extract this archive to preserve native binaries, executable permissions and macOS symlinks.
4. Run SyntaxLauncher from the extracted application directory. This is a portable, **unsigned** build, not a signed installer. macOS Gatekeeper or Windows SmartScreen may request explicit user consent; do not disable system security globally.

## Local developer verification

Use JDK 21 and the committed official Gradle wrapper.

- Linux/macOS: `./gradlew :launcher-core:test :launcher-desktop:createDistributable`
- Windows PowerShell: `.\gradlew.bat :launcher-core:test :launcher-desktop:createDistributable`
- To run directly in development: `./gradlew :launcher-desktop:run`

The native app directory is generated below `launcher-desktop/build/compose/binaries/main/app/`.

## Acceptance checklist

- [ ] Start the extracted app and confirm the Minecraft version catalog loads.
- [ ] Enter a valid offline nickname and select a supported release.
- [ ] Set RAM and press Play. Confirm downloads, native extraction and automatic managed Java installation succeed.
- [ ] Verify a real Minecraft window appears, single-player launches and the game can close normally.
- [ ] Start the same game again and confirm valid cached client/libraries/assets/runtime are reused.
- [ ] Quit and reopen SyntaxLauncher; nickname, selected version, version channel and RAM should persist.
- [ ] Test cancelling a preparation download without publishing incomplete `.part` files as valid cache.
- [ ] Test the Stop Minecraft action and verify status / exit code / separate stdout and stderr logs.
- [ ] Close the launcher while Minecraft is running and confirm the game remains open (DETACH policy).
- [ ] Repeat basic tests on supported Linux x64, Windows x64 and macOS ARM64.

## Where to find logs

Minecraft stdout and stderr are stored in separate per-run directories under the launcher OS log directory:

- Linux: `~/.local/state/syntaxlauncher/logs/minecraft/` by default (respecting `XDG_STATE_HOME`).
- Windows: `%LOCALAPPDATA%\SyntaxLauncher\logs\minecraft\`.
- macOS: `~/Library/Logs/SyntaxLauncher/minecraft/`.

Only report error messages or sanitized log excerpts; **never share credentials or private data**.

## Known preview limitations

- Offline accounts are for local worlds and servers accepting offline-mode clients, not a substitute for Microsoft authentication.
- Windows/Linux/macOS packages are unsigned previews, with no native signed release installers yet.
- Mojang's officially provided Java runtime platform matrix is incomplete (notably Linux ARM64).
- Older Minecraft versions requiring virtual asset views, inherited metadata, or unavailable Java requirements can fail explicitly.
- Version-manifest offline caching, installation migration and v0.2 isolated instances remain future work.
- Official Minecraft version downloads and real gameplay have not yet been manually verified on all supported systems.

Report reproduction steps, OS/architecture, selected Minecraft version, stage/error message and relevant sanitized process logs when filing a bug.

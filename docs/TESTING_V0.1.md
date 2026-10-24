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

## Typed launch failures and native Java verification

24 October 2026: launch status now carries a stable `LaunchFailureReason`, independent of Compose and language. Classification uses actual exception types and a bounded cause chain, not class-name substrings. Nested disk/network/integrity causes remain actionable under Java-install wrappers. Core fallback text and UI advice are fixed safe strings; raw exceptions, URLs, tokens, paths and download identifiers are not shown in this launch advice. Technical diagnostics retain the existing safe failure type.

The home page translates all categories in EN/PL/DE. Error advice occupies the full action-section width, wraps without ellipsis, and retains a reachable enabled Play retry. A real Compose click through the session/coordinator verifies invalid runtime metadata, translated advice, full text and scrolling to Play at 1440×810 and 400×400 in all three languages. A coordinator regression verifies HTTP failure before process start followed by a successful retry clearing the old failure. Malformed Java-index JSON now raises `JavaRuntimeMetadataException` instead of a generic serialization/settings error.

`ManagedJavaOfficialSmokeTest` is opt-in (`SYNTAXLAUNCHER_JAVA_LIVE_SMOKE=1`). It uses official Minecraft 1.21.11 and 26.3 metadata to select actual managed Java; current references request 21 and 25. It downloads/verifies/executes the runtime, constructs a fresh manager with networking disabled to prove cache reuse, removes only the temporary runtime's manifest-backed `release` file and repairs/re-executes it. An unrelated world marker must remain unchanged. These tests concern managed Java, not a Minecraft game boot or OS trust certification.

The native CI matrix runs that test before packaging on Windows x64, Linux x64 and macOS ARM64 and uploads `managed-java-<platform>` JUnit evidence. Local Linux passed both references, offline reuse and repair. Cross-platform acceptance is closed only after the corresponding hosted jobs pass. Full clean-machine Vanilla/gameplay, process-close/native focus and the existing broader manual checklist stay independent.

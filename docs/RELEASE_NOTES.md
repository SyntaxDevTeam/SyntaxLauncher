# Development release notes — diagnostics and production preparation

These notes describe the current development change, not an announced Stable 1.0 release or a product-version bump.

- Check a selected game before or after Play: managed-file integrity, Java, loader/mod evidence and common crash categories with clear confidence labels.
- Explicitly repair game files and managed Java without starting Minecraft, deleting worlds or changing user mods/settings. Cancel and retry safely.
- Copy a local safe diagnostic summary; raw logs, account credentials, arguments and private paths are excluded.
- Settings includes bounded launcher-log access, storage paths, build identity and update channels. Launcher messages are redacted and rotated.
- Downloads preflight free space; shared JVM dependency versions are locked.
- Explicit offline player manager changes already present in the working tree are retained and verified; Microsoft launches keep official ownership/profile checks with no automatic fallback.

Microsoft live-account acceptance is deferred pending Mojang approval. Production signing/notarization, clean-machine native installs, old→new hosted updates, migrations, gameplay and focus/DPI acceptance remain final release gates. Supported packaging targets are Linux x64, Windows x64 and macOS ARM64; game/loader/graphics support retains its existing bounded matrix.

## Candidate publication process

1. Update these notes with the exact supported matrix, known limitations and final verification evidence.
2. Run the full Gradle and Python suites; inspect compact translated screens and the three-platform CI artifacts.
3. Complete TESTING_V0.12.md and the independent v1.0 gates. Record exact platform/build/game scenarios.
4. Check `python3 scripts/release_readiness.py --enforce v1.0`; do not bypass remaining gates with a version edit.
5. Only after an explicit release request, change the sole `launcherVersion` in gradle.properties. Publish through the existing version/tag-validated workflow and verify all six release assets and update handoff.

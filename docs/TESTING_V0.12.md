# v0.12 — Production hardening and release acceptance

## Implementation delivered

- Shared downloads validate hashes/lengths, retry transient failures with bounded backoff, stream bounded payloads and clean partial files after cancellation. Existing verified files are reused. Interrupted payloads restart from zero; HTTP Range resume is not promised. An admission check reserves a 16 MiB safety margin in its calculation and reports insufficient disk space before transfer (it does not reserve actual storage against concurrent writers).
- Runtime, modpack and updater installations stage privately and publish verified results. Repair reuses these services rather than deleting cache or player data wholesale.
- Existing centralized networking has 10-second connect, 120-second request and 30-second socket deadlines; individual update/artifact operations can have bounded overrides. Initial scope is direct HTTPS networking. Configurable authenticated proxies are outside initial support and are not silently claimed to work.
- Launcher logs use a redacting Logback converter, omit arbitrary throwable messages/stacks, rotate at 8 MiB, and cap archived history at seven days/64 MiB. Main resolves the platform log directory before initializing services and restricts directory access on POSIX. The local settings viewer reads at most the latest 64 KiB. Game-owned logs and startup handoff logs have separate retention.
- Settings groups build identity, update channels, account-review status, launcher log and actual data paths. Game diagnostics exposes a bounded safe support report. Existing preparation progress/cancel/retry and keyboard/focus semantics remain in use.
- Gradle locks the resolved common JVM graph. Host-specific Compose desktop/Skiko artifacts are excluded from shared locks because module names differ by OS; their versions derive from the pinned Compose distribution. Refresh intentionally with `--write-locks`, review the resulting lock diff and run the matrix CI. This does not claim byte-for-byte reproducible installers or a fully pinned remote game ecosystem.
- Existing CI builds/tests and creates portable images/update bundles on Linux x64, Windows x64 and macOS ARM64; release helpers validate canonical versions/tags, preview identity and complete release assets. [Installation/removal/data policy](INSTALLATION.md) documents first run, manual installation, update recovery, backups and uninstall.
- Updater retention policy: keep active/previous/original and orphaned images/archives for recovery; no automatic pruning in this scope. Explicit offline cleanup instructions are documented. Native signing/notarization remains a separate release gate.

## Automated verification

Run `./gradlew --offline test build :launcher-desktop:createDistributable` with cached build dependencies. Run `python3 -m unittest discover -s scripts -p 'test_*.py'` for packaging/version/publication fixtures. Existing network/download/update/instance/runtime regressions exercise integrity, cancellation, retry, staging, state continuity and failed-child recovery. New diagnostics/repair, disk-admission, safe-log and translated compact-screen tests complement these tests.

Normal tests do not require Mojang approval or Microsoft credentials. Optional official installer/modpack smoke tests are explicitly enabled separately and are not inferred from skipped results.

## Final native/release gates — not executed by deterministic tests

- [ ] Fresh installation on clean Windows x64, Linux x64 and macOS ARM64 machines.
- [ ] Actual hosted older→newer update through original shortcut, Stable/DEV/SNAPSHOT changes, offline/cancel/failed-child recovery and native permissions.
- [ ] Real pre-1.0 upgrade retaining worlds, profiles, settings and OS credentials where applicable.
- [ ] Native keyboard/focus, window controls, multiple monitors, accessibility, DPI and graphics/gameplay acceptance.
- [ ] Publisher certificates, Windows signing, macOS signing/notarization and native trust verification.
- [ ] Final candidate's hosted three-platform packages and complete publication accepted.

Microsoft approval and live premium testing are deferred by the maintainer on 23 October 2026. No approval or successful premium gameplay is implied by this implementation. Do not check the final v0.12/v1.0 native gates until evidence exists.

# v0.11 — Diagnostics and repair acceptance

## Initial implementation (23 October 2026)

This stage adds a bounded **local-only** diagnostic engine for the last attempted game and a translated crash-analysis dialog in EN/PL/DE. It is not a general support-bundle exporter and it never uploads logs. The engine checks no more than 256 KiB per log, ignores stale game logs and does not follow redirected symlink files. It reads recent stderr/stdout, latest.log and the latest recent crash report where available. **Only static finding messages and confidence levels are returned to the interface**, not user-controlled log lines, command arguments or authentication tokens.

Initial detection: Java class-file/runtime mismatch, Java executable errors, JVM heap exhaustion, mod-loader/dependency loading errors, missing classes, file/hash/ZIP corruption symptoms, GLFW/OpenGL failures and Minecraft authentication problems. A bare exit code is **not** a confirmed diagnosis.

When evidence suggests corrupt managed files or a preparation failure, the dialog offers **Verify and repair managed files**. The user must click the action explicitly. It reuses the exact game/loader/version and existing shared download/Java integrity preparation, under the instance activity lease. It must not remove personal mods, saves, configuration files or overwrite user settings. Reinstalling all mods, modifying Java allocation, deleting crash logs or repairing private user data is **not** part of this action.

## Automated

- [ ] Classify mod dependency, Java, memory, file integrity and graphics log evidence with appropriate confidence.
- [ ] Show UNKNOWN/POSSIBLE, not confirmed, for exit code 1 without recent evidence.
- [ ] Ignore stale log files and symbolic links; read no more than the bounded tail.
- [ ] Ensure token-like text in logs or exception messages never appears in serialized diagnostic findings.
- [ ] Verify repair dispatch uses the existing instance lease/preparation pipeline and no destructive file operations.
- [ ] Run full Gradle tests/build and all supported platform packages in CI.

## Manual acceptance

- [ ] Open the last attempted game's local logs via the explicit button, including after selecting a different game. The action must not upload or display raw logs.

- [ ] Crash an isolated test game with an intentionally missing mod dependency; inspect a useful diagnosis.
- [ ] Simulate damaged launcher-managed cache and use **Verify and repair managed files**, without changing worlds/mods.
- [ ] Verify stale logs are not confused with a different instance after changing selection.
- [ ] Verify action button, keyboard navigation, 400x400 viewport and translated dialog on Windows/Linux/macOS.
- [ ] Check that premium sessions cannot leak token values in game reports or screenshots.

## Still open within v0.11

Safe Java runtime details, complete installed-mod inventories, deeper crash-report parsing, a full managed-file integrity scan, optional log export after explicit privacy review, richer mod conflict diagnosis and native acceptance remain open. The UI does not claim to auto-repair every error.

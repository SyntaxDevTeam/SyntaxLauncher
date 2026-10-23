# v0.11 — Diagnostics and repair

## Scope

Home → **Game health and repair** checks the selected game. It works without a Microsoft account or Mojang AppID approval. Repair is explicit, does not start Minecraft and uses the existing preparation pipeline for Minecraft/assets/native extraction, loaders and managed Java. Existing runtime resolution validates its managed manifest/files and reinstalls invalid payloads. Worlds, user mods, options, accounts and unrelated games are not deleted. Downloads are replacements of launcher-managed artifacts only; unverified upstream library metadata is counted separately rather than claiming checksum protection.

The scan inventories planned client/libraries/natives/index/logging files and, after validating the asset index, its asset objects. Current launch evidence adds Java, main class, classpath count, loader and process exit code. Installed mod IDs/versions and advisory warnings supplement diagnosis. Bounded tails of the current launch's stdout/stderr, latest.log and up to three recent crash reports are inspected. Old reports and symlink log files are excluded. This is not exhaustive mod dependency solving or a guarantee that the game will start.

Confirmed integrity/runtime checks and probable log signatures are presented separately. Recognized signatures include memory exhaustion, unsupported Java, missing dependencies, duplicate/incompatible mods, graphics initialization and full disk. Unknown failures retain an honest unknown finding rather than guessing from exit code 1.

## Privacy

Reports are local and sent nowhere. **Copy safe report** copies an allowlisted text summary: versions, counts, finding codes/confidence and mod inventory. It never includes raw logs, account/session objects, argument values or private filesystem paths. Technical details show the same summary. The separate local launcher-log viewer is under settings; it is not automatically included in a support report. Third-party game logs remain game-owned and are not uploaded.

## Verification

- `GameDiagnosticsTest`: missing/corrupt client detection, verified replacement, managed Java resolution, world/options/mod preservation, no game process, stale/unrelated/symlink log exclusion, current OOM evidence and credential/path exclusion from exports.
- `DesktopLauncherSessionAccountsTest`: repair needs no credential vault, rejects concurrent Play, retains world data, leaves the game stopped and returns a clean subsequent scan.
- `GameHealthDialogTest`: EN/PL/DE at 400×400 and 820×700, reachable repair control, verified repair and refreshed findings.
- Existing cancellation/download/runtime/loader tests remain regression coverage for the reused repair pipeline.

Native focus/DPI, graphical game failures and a broader real-world crash corpus remain final release acceptance. No approved Microsoft login or manual Windows/macOS result is inferred from deterministic tests.

## Earlier published diagnostics

The bounded `MinecraftDiagnostics` classifier and its privacy/stale-process regressions from commits 5ca5c86 through b85cf64 are retained and reused by the expanded health scan. The previous last-failed-game entry delegates to the same health interface and guarded repair. Hosted baseline 39901800185 passed before these extensions; it is not evidence for this new revision.

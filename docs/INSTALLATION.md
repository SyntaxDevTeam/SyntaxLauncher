# Installing, updating and removing SyntaxLauncher

## Supported distribution targets

The current packaging pipeline targets **Windows x64**, **Linux x64** and **macOS ARM64**. Game Java availability depends on Mojang's runtime component for the selected game/platform. Do not infer Windows ARM64, Linux ARM64 or Intel macOS launcher support from JVM portability. Wider game/platform compatibility and clean-machine acceptance must be recorded before Stable 1.0.

Use a complete portable application archive from this project's GitHub Releases. Extract the entire archive into a directory you own; do not move just the executable out of its accompanying runtime/libraries. The launcher includes its own application runtime. It installs the separate Java version required by Minecraft automatically. Internal `syntaxlauncher-update-*.zip` assets are updater inputs, not initial-install packages.

- Windows: extract the `.tar.gz` with a suitable archive tool and open `SyntaxLauncher/SyntaxLauncher.exe`.
- Linux: extract the `.tar.gz` preserving permissions and open `SyntaxLauncher/bin/SyntaxLauncher`.
- macOS ARM64: extract the `.tar.gz` preserving the complete `SyntaxLauncher.app`, then open that application.

Release signing/notarization is still a production gate; unsigned packages may encounter native trust restrictions. Signing certificates and approved native trust behavior are not claimed. Do not disable system protections as a workaround.

## First game

Select or add an explicit offline player. Microsoft acceptance is deferred while Mojang reviews the AppID; existing Microsoft code is retained and failed authentication never silently becomes offline play. Choose Vanilla, or create a separate game with mods. Choose extras/modpacks in the catalog, Install, then Play. Advanced local imports are secondary. Offline identities cannot authenticate to servers requiring a verified Microsoft session.

Use **Game health and repair** if installation or startup fails. Repair checks/downloads launcher-managed files and Java while retaining your worlds/mods/settings. It does not start the game. Settings provides the update channel, launcher log and data locations. A safe diagnostic report can be copied manually for support; nothing is uploaded automatically.

## Updates and recovery

Keep your original shortcut/application: it is the bootstrap for newer application images. Choose Stable, DEV or SNAPSHOT in settings and restart. Older channels wait for a newer compatible version; automatic downgrades are refused. Continue lets you keep the installed app after a failed/offline check. Interrupted downloads are restarted from verified cache; partial payloads are never accepted as complete. Failed startup handoffs retain the previous verified image. See [UPDATING.md](UPDATING.md).

Old update images and downloaded archives are deliberately retained for recovery; automatic pruning is disabled. To reclaim that storage, close the launcher and detached games, back up data, and remove the `launcher-updates` directories under data/cache. Keep a complete original installation to bootstrap again. Never remove runtime/game folders merely to clear updater storage.

## Data and backups

Settings → Storage and removal shows the actual paths. Defaults:

| System | Games/data | Settings | Cache | Logs |
| --- | --- | --- | --- | --- |
| Windows | `%LOCALAPPDATA%/SyntaxLauncher` | `config` inside data | `cache` inside data | `logs` inside data |
| Linux | `${XDG_DATA_HOME:-~/.local/share}/syntaxlauncher` | `${XDG_CONFIG_HOME:-~/.config}/syntaxlauncher` | `${XDG_CACHE_HOME:-~/.cache}/syntaxlauncher` | `${XDG_STATE_HOME:-~/.local/state}/syntaxlauncher/logs` |
| macOS | `~/Library/Application Support/SyntaxLauncher` | `config` inside data | `~/Library/Caches/SyntaxLauncher` | `~/Library/Logs/SyntaxLauncher` |

Before upgrades, back up complete game instances, worlds and settings while games are stopped. Keep the original data for rollback. Refresh credentials are protected by the operating-system vault and cannot be treated as a portable backup; signing in again may be necessary. Supported formats are versioned and unknown future schemas are refused instead of overwritten. Automated migration/backward-reading coverage is not a replacement for a real upgrade acceptance test.

## Removal

Close the launcher and any detached Minecraft process. Removing the extracted application directory removes the application, while preserving user data. To remove data too, first back up worlds, remove saved Microsoft profiles through the player manager when secure storage is available, then explicitly delete the data/config/cache/log locations above. OS-vault entries require account removal; deleting directories alone does not claim to revoke a Microsoft grant. No uninstall action automatically deletes worlds. Launcher messages rotate at 8 MiB, with seven-day/64 MiB archived-log limits; game logs and startup receipt logs are retained separately and are not subject to that policy.

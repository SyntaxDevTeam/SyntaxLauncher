# Startup auto-updater

Requested early by the maintainer on 19 October 2026. This implements the self-update slice of v0.12 without advancing its remaining production or release gates.

## User behavior

Packaged SyntaxLauncher applications check for updates before composing the home screen or fetching Minecraft metadata. A small branded screen shows checking, download progress, installation and startup. “Continue with installed version” cancels checking/downloading/extraction and opens the application the user started; it is temporarily disabled during the short process handoff. Errors offer Retry and Continue. No update is mandatory.

The stable channel is the latest public, non-draft, non-prerelease GitHub Release from `SyntaxDevTeam/SyntaxLauncher`. Only a strictly newer semantic version with the correct OS/CPU update asset is eligible. Build metadata does not trigger upgrades. A 404, a release without compatible update assets, an unsupported platform or a development `gradle run` proceeds with the current application. Other network/API failures allow Continue. The release check has a six-second request timeout; large archive downloads allow fifteen minutes, with the central client's connection/socket timeouts and bounded retries.

The original shortcut/executable remains a bootstrap. Updates live beside each other in user-owned storage; the updater does not overwrite the running executable or need administrator access. A verified cached newer application can still start when GitHub is unavailable. A failed new download/install/start falls back to the verified active application when available. When the new process fails/exits/times out before its startup receipt, the active pointer remains unchanged and its child process is terminated. The original installed application always remains available through Continue. This is startup recovery, not a promise to detect crashes occurring later during normal use.

The updater becomes available only after installing a build containing it once. Older launcher binaries cannot acquire this feature by themselves. Initial/manual installs use the portable `.tar.gz` release assets; the update ZIPs are an internal protocol, not first-install archives.

## Version and platform contract

- The sole source of the SyntaxLauncher product SemVer is `launcherVersion` in the root `gradle.properties` (currently `0.3.0-dev`). Change that property when advancing or releasing the launcher; do not edit Gradle build scripts, Python publishing code or workflow versions.
- Build normally with `./gradlew :launcher-desktop:createDistributable`; all Gradle modules, generated metadata, packaging manifests and publishers resolve the same value.
- Native jpackage versions must be numeric: prerelease/build suffixes are stripped and a product major of `0` is mapped to native major `1` for macOS compatibility (e.g. `0.3.0-dev` → `1.3.0`). This is a derived packaging identifier, **not** a second product version or update comparison key.
- The generated `launcher-build.properties` is embedded in desktop resources and displayed in the header; neither runtime startup nor CI has a hardcoded fallback product version.
- Packaged-mode detection uses jpackage's `jpackage.app-path` property. Gradle/IDE runs skip automatic update execution; unsupported native platforms continue normally.

| Platform | Update asset | Fixed executable inside bundle |
| --- | --- | --- |
| Linux x64 | `syntaxlauncher-update-linux-x64.zip` | `SyntaxLauncher/bin/SyntaxLauncher` |
| Windows x64 | `syntaxlauncher-update-windows-x64.zip` | `SyntaxLauncher/SyntaxLauncher.exe` |
| macOS ARM64 | `syntaxlauncher-update-macos-arm64.zip` | `SyntaxLauncher.app/Contents/MacOS/SyntaxLauncher` |

Mutable game data, accounts, settings, Java-for-Minecraft runtimes and caches keep their existing locations. Only launcher application images and update state use the new directories:

```text
<dataDir>/launcher-updates/
  startup.lock
  state.properties                   # schemaVersion=1; active/previous; unknown keys retained
  installations/<version>-<digest>/   # immutable verified app image + manifest
<cacheDir>/launcher-updates/<digest>.zip
```

Corrupt installations can be repaired into a fresh directory without deleting the original. Complete, identical cached images are revalidated and reused. Previous/orphan images are retained; automatic disk cleanup is deliberately deferred. Unknown update-state schemas fail without rewriting them. A process lock prevents conflicting startup update transactions. Atomic stage publication and state replacement happen on the same filesystem as their destination.

## Integrity and extraction

The only accepted network source is an HTTPS asset URL under this project's GitHub release path, initially discovered from GitHub's public API. GitHub's `sha256:` asset digest is required. The shared downloader verifies SHA-256 and length before publishing its `.part` file, and verifies cached archives again. SHA-1 handling for Mojang downloads remains available.

The update ZIP has a schema-1 `syntaxlauncher-update.json` manifest carrying version, platform, fixed executable and entries. Regular files declare length, SHA-256 and executable flag. Directories and relative symbolic links are explicit manifest entries. The packaging script preserves links without following them. Extraction accepts exactly the listed regular ZIP entries, rejects duplicates (including case collisions), absolute/traversing/Windows-special paths, malformed platform/version/executable metadata, excess size and escaping/broken/cyclic links. Links are installed last, so archive writes cannot traverse an archive-controlled link. File hashes and executable permissions are restored; staged installations are published only after full verification. Limits are 2 GiB compressed, 4 GiB expanded, 50,000 entries and a 16 MiB manifest, plus a free-space preflight.

The trust anchor is HTTPS plus the project's GitHub repository/release account. SHA-256 prevents corruption/substitution relative to that trusted metadata; it is not an independent publisher signature and does not protect against compromise of that publishing account. Native release signing/notarization and live cross-platform startup acceptance remain production follow-ups.

The bootstrap starts the fixed platform executable with a single-use receipt path/nonce. The child validates that receipt destination is inside the launcher update directory, renders its content, and writes the nonce plus its own embedded version after two frames. Only a matching receipt within thirty seconds allows the parent to atomically activate that version and exit. The child skips its own duplicate update check for this handoff only; subsequent ordinary starts check normally. Future versions must retain this handoff protocol until a versioned migration is implemented.

## Publishing

GitHub Actions runs the Gradle/Python tests and builds three portable `.tar.gz` applications and three verified update ZIPs (Linux x64, Windows x64, macOS ARM64). **Only normal, non-prerelease GitHub Releases are supported**; the publishing job waits for every check and all six files.

### Automatically publish a normal GitHub Release

Change the one canonical product version in the root `gradle.properties` and push to `main`. For example, the current version is:

```properties
launcherVersion=0.6.0
```

The next release requires a forward change such as `0.6.0` → `0.7.0`. The workflow compares the canonical version at the commit before the push with the new version. A change to a higher plain SemVer `X.Y.Z` qualifies, as does promoting a development suffix to its plain version. Source-code changes without a version bump do not issue new releases.

After tests and three-platform packaging pass, CI verifies all six files, creates or validates the exact-commit `vX.Y.Z` tag, creates a draft, uploads all assets and publishes a **normal GitHub Release** as latest. CI never marks it as `prerelease`. A published release or an existing tag pointing to another commit cannot be overwritten. Tags created by `GITHUB_TOKEN` do not start a second workflow run; only the release job has `contents: write`.

Manual matching `vX.Y.Z` tag pushes remain supported. Tags with development or prerelease suffixes do not run the GitHub Release publishing job. Do not use `-SNAPSHOT`, `-dev`, `-alpha`, `-beta` or `-rc` when planning a public release.

### Version history and release acceptance

Earlier v0.4 and v0.5 development work was committed while the canonical version remained `0.3.0-dev`; therefore no GitHub Releases were created for those historical milestones. Changing this configuration does not retrospectively issue releases for old commits. The first expected automatic normal release is `v0.6.0`, provided its CI succeeds.

**Release labeling is not a claim that every roadmap feature passed manual verification.** Real gameplay, Microsoft integration, macOS/Windows acceptance, updater continuity and signing remain separate tests and open items. Because ordinary GitHub Releases enter the stable auto-update channel, maintainers should carefully review the release before advancing the version.


## Verification

```bash
./gradlew test build
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-configuration-cache :launcher-desktop:createDistributable
python3 scripts/package_update.py \
  --source launcher-desktop/build/compose/binaries/main/app \
  --platform linux-x64 --version "$(python3 scripts/launcher_version.py)" \
  --output launcher-desktop/build/updates/syntaxlauncher-update-linux-x64.zip
./gradlew -PupdateBundle=build/updates/syntaxlauncher-update-linux-x64.zip :launcher-desktop:verifyUpdateBundle
```

The explicit bundle path is relative to `launcher-desktop` for the Gradle verification task. Normal unit tests do not need a packaged application or external services. Tests cover version ordering, release filtering, wrong digest/source/platform, cache corruption, traversal/link attacks, staged repair, cancellation, process exit/timeout, active-state preservation, automatic fallback, a real Java child startup receipt, offline cached startup, rendered UI states and the actual Compose child receipt. Render evidence is under `launcher-desktop/build/reports/ui/startup-*.png`.

Before release acceptance, manually test a real old→new hosted release with writable and read-only original install locations, slow/offline networking, cancellation and failed child startup on Windows, Linux and macOS. Check file permissions/symlinks, native trust prompts, settings/accounts/instances continuity, repeated launch through the original shortcut, and retention/recovery of the prior version. Automated fixtures and local Linux package verification do not replace these gates.

## Primary references

- [GitHub latest-release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [GitHub release asset digest contract](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)
- [OpenJDK jpackage property tests](https://github.com/openjdk/jdk/blob/master/test/jdk/tools/jpackage/share/BasicTest.java)

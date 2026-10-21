# Startup auto-updater

Requested early by the maintainer on 19 October 2026. This implements the self-update slice of v0.12 without advancing its remaining production or release gates.

## User behavior

Packaged SyntaxLauncher applications check for updates before composing the home screen or fetching Minecraft metadata. A small branded screen shows checking, download progress, installation and startup. “Continue with installed version” cancels checking/downloading/extraction and opens the application the user started; it is temporarily disabled during the short process handoff. Errors offer Retry and Continue. No update is mandatory.

Select **Your setup → Launcher updates**: **Stable** (default), **SNAPSHOT** or **DEV**. The selection is saved atomically and takes effect at the next launcher start. The dialog explains preview risks and that switching channels may install an older launcher. Loading/save failures preserve existing settings and offer Retry. Minecraft's snapshot checkbox is independent of the launcher channel.

Stable uses the latest public, non-draft, non-prerelease GitHub Release from `SyntaxDevTeam/SyntaxLauncher`. Within Stable, only a newer product SemVer is eligible. DEV/SNAPSHOT use only public pre-releases with immutable tags `<channel>-<run>.<attempt>-v<canonical-version>`; builds are ordered numerically by CI run/attempt, independently of SemVer. Discovery paginates release history (100 releases per page, maximum 100 pages; exceeding the limit reports an error) so an older SNAPSHOT remains discoverable among DEV releases. Every request has a six-second timeout. Selecting a different channel permits an older product version; ordinary checks within a channel never automatically downgrade. The current CI identity prevents duplicate handoffs, including when a Stable promotion publishes identical bytes in DEV.

Only matching OS/CPU assets with a verified SHA-256 digest are eligible. A 404, missing compatible assets, unsupported platform or development `gradle run` continues with the current app. Network/API failures offer Continue; a verified cached image from the selected channel can still start offline. Archives retain fifteen-minute download timeouts and the central downloader's bounded retries.

The original shortcut/executable remains a bootstrap. Updates live beside each other in user-owned storage; the updater does not overwrite the running executable or need administrator access. A verified cached newer application can still start when GitHub is unavailable. A failed new download/install/start falls back to the verified active application when available. When the new process fails/exits/times out before its startup receipt, the active pointer remains unchanged and its child process is terminated. The original installed application always remains available through Continue. This is startup recovery, not a promise to detect crashes occurring later during normal use.

The updater becomes available only after installing a build containing it once. Older launcher binaries cannot acquire this feature by themselves. Initial/manual installs use the portable `.tar.gz` release assets; the update ZIPs are an internal protocol, not first-install archives.

## Version and platform contract

- The sole source of the SyntaxLauncher product SemVer is `launcherVersion` in the root `gradle.properties` (read the current value in that file). Change that property when advancing or releasing the launcher; do not edit Gradle build scripts, Python publishing code or workflow versions.
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
  state.properties                   # schemaVersion=1; stable active/previous plus dev./snapshot. keys
  installations/<version>-<digest>/   # immutable verified app image + manifest
<cacheDir>/launcher-updates/<digest>.zip
```

Corrupt installations can be repaired into a fresh directory without deleting the original. Complete, identical cached images are revalidated and reused. Previous/orphan images are retained; automatic disk cleanup is deliberately deferred. Unknown update-state schemas fail without rewriting them. Existing stable keys remain readable; additive `dev.active`, `dev.previous`, `snapshot.active`, `snapshot.previous` and corresponding `.build` fields isolate channel recovery. Missing `launcher_update_channel` in schema-1 preferences means Stable; the new key preserves profile/language/unknown data and unknown channel IDs fail closed. No schema bump or destructive migration is required. A process lock prevents conflicting startup update transactions. Atomic stage publication and state replacement happen on the same filesystem as their destination.

## Integrity and extraction

The only accepted network source is an HTTPS asset URL under this project's GitHub release path, initially discovered from GitHub's public API. GitHub's `sha256:` asset digest is required. The shared downloader verifies SHA-256 and length before publishing its `.part` file, and verifies cached archives again. SHA-1 handling for Mojang downloads remains available.

The update ZIP has a schema-1 `syntaxlauncher-update.json` manifest carrying version, platform, fixed executable and entries. Regular files declare length, SHA-256 and executable flag. Directories and relative symbolic links are explicit manifest entries. The packaging script preserves links without following them. Extraction accepts exactly the listed regular ZIP entries, rejects duplicates (including case collisions), absolute/traversing/Windows-special paths, malformed platform/version/executable metadata, excess size and escaping/broken/cyclic links. Links are installed last, so archive writes cannot traverse an archive-controlled link. File hashes and executable permissions are restored; staged installations are published only after full verification. Limits are 2 GiB compressed, 4 GiB expanded, 50,000 entries and a 16 MiB manifest, plus a free-space preflight.

The trust anchor is HTTPS plus the project's GitHub repository/release account. SHA-256 prevents corruption/substitution relative to that trusted metadata; it is not an independent publisher signature and does not protect against compromise of that publishing account. Native release signing/notarization and live cross-platform startup acceptance remain production follow-ups.

The bootstrap starts the fixed platform executable with a single-use receipt path/nonce. The child validates that receipt destination is inside the launcher update directory, renders its content, and writes the nonce plus its own embedded version after two frames. Only a matching receipt within thirty seconds allows the parent to atomically activate that version and exit. The child skips its own duplicate update check for this handoff only; subsequent ordinary starts check normally. Future versions must retain this handoff protocol until a versioned migration is implemented.

## Publishing

GitHub Actions runs the Gradle/Python tests and builds three portable `.tar.gz` applications and three verified update ZIPs (Linux x64, Windows x64, macOS ARM64). Stable and preview publishing jobs wait for every check and all six files. Pull requests and manual runs on other branches never publish previews.

### Automatically publish a normal GitHub Release

Change the one canonical product version in the root `gradle.properties` and push to `main`. For example, the currently declared product version is:

```properties
launcherVersion=0.7.0
```

Only after completing the v0.8 acceptance requirements should the next release be triggered by `0.7.0` → `0.8.0`. The workflow compares the canonical version at the commit before the push with the new version. A change to a higher plain SemVer `X.Y.Z` qualifies, as does promoting a development suffix to its plain version. Source-code changes without a version bump do not issue new Stable releases; successful main pushes still publish DEV.

After tests and three-platform packaging pass, CI verifies all six files, creates or validates the exact-commit `vX.Y.Z` tag, creates a draft, uploads all assets and publishes a **normal GitHub Release** as latest. The Stable job never marks it as `prerelease`. A published release or an existing tag pointing to another commit cannot be overwritten. Tags created by `GITHUB_TOKEN` do not start a second workflow run; only the release job has `contents: write`.

Manual matching `vX.Y.Z` tag pushes remain supported. Tags with development suffixes do not run the Stable publishing job. A `v...` tag must still exactly match `launcherVersion` at its commit.

### DEV and SNAPSHOT

- Every successful push to `main` publishes DEV, including stable-version bump runs. If the same run promotes Stable, the two releases use the same verified application images.
- **Actions → Build and package SyntaxLauncher → Run workflow → main → preview_channel=snapshot** publishes SNAPSHOT. `dev` is also available for a manual DEV publication. SNAPSHOT is an explicitly chosen preview, rather than a Minecraft snapshot or an automatically promoted daily build.
- `scripts/publish_preview.py` reads canonical `launcherVersion` directly, requires all six nonempty assets, creates an exact-commit immutable Git tag, creates a draft pre-release, uploads the full set, and publishes with `--prerelease --latest=false`. No force tag moves, asset clobber or replacement of published preview builds occurs. A new workflow attempt has a distinct tag. A partial failure may leave a draft/tag, which is ignored by clients.
- Product versions, manifests and startup receipts keep the canonical version unchanged. CI adds only separate `channel` and run/attempt metadata to `launcher-build.properties`. Preview tags use `dev-...`/`snapshot-...`, not `v...`; they identify CI builds without inventing a second product SemVer. Stable notes contain a CI identity marker for matching the identical build when switching channels.
- Full release history/app images are retained; automatic pruning and signing remain deferred. This feature is available locally once built; hosted publication/old-to-new acceptance must still be verified after the workflow reaches GitHub.

### Version history and release acceptance

Earlier v0.4 and v0.5 development work was committed while the canonical version remained `0.3.0-dev`; therefore no GitHub Releases were created for those historical milestones. Changing this configuration does not retrospectively issue releases for old commits. The first automated ordinary GitHub Release was `v0.6.0`. Later attempted v0.7.0 publication is blocked by GitHub authorization (see below).

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

Before release acceptance, manually test Stable → DEV → newer DEV with the same product version → SNAPSHOT → Stable, and a real old→new hosted release with writable and read-only original install locations, slow/offline networking, cancellation and failed child startup on Windows, Linux and macOS. Check file permissions/symlinks, native trust prompts, settings/accounts/instances continuity, repeated launch through the original shortcut, and retention/recovery of the prior version. Automated fixtures and local Linux package verification do not replace these gates.

## Primary references

- [GitHub latest-release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [GitHub release asset digest contract](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)
- [OpenJDK jpackage property tests](https://github.com/openjdk/jdk/blob/master/test/jdk/tools/jpackage/share/BasicTest.java)

## Publishing HTTP 403 (21 October 2026)

**Verified state:** v0.6.0 is the last published GitHub Release. It was published
on 20 October 2026, not republished by later CI. The canonical version in
`gradle.properties` is currently `0.7.0`; work on v0.8 is still undergoing
acceptance and **must not** be published under the v0.7.0 tag.

The historical v0.7.0 release candidate is pinned to:
- Git commit: `3f6b69d517dec2e04303e2c323a0290060cd96a8`
- Successful build: [run 39558146467](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39558146467)
- Published asset requirement: all three portable archives and all three update ZIPs.

The historical recovery job verifies the commit, version, tests, all platform
builds, and the six source artifacts. Its GitHub release creation fails with:
- `SYNTAXLAUNCHER_RELEASE_TOKEN`: HTTP 403, "Resource not accessible by personal access token" ([run 39592106875](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39592106875)).
- `github.token`: HTTP 403, "Resource not accessible by integration" ([run 39592695562](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39592695562)), **despite** the job logging `Contents: write`.

This is a confirmed *publication authorization failure*, not a build or
packaging failure. Do not claim an existing GitHub Release until the public
releases API shows it.

### Required GitHub account action

1. In the GitHub account that owns `SYNTAXLAUNCHER_RELEASE_TOKEN`, create
   or edit a fine-grained PAT with **resource owner `SyntaxDevTeam`**,
   repository access **`SyntaxLauncher`**, and repository permission
   **Contents: Read and write** (Metadata: Read is automatic). If required
   by organization policy, have an organization owner approve the token.
   A classic token with appropriate `repo` scope and SSO/organization
   authorization can also work, subject to organization policy.
2. Replace the value of the existing `SYNTAXLAUNCHER_RELEASE_TOKEN` secret
   in `SyntaxDevTeam/SyntaxLauncher` → Settings → Secrets and variables →
   Actions. Never paste the token into source code, logs or chat.
3. Manually run **Recover verified SyntaxLauncher v0.7.0 stable release**
   via Actions → Run workflow. It always uses the historical verified
   v0.7.0 build, never the evolving current `main` code.
4. Verify that `/releases/tag/v0.7.0` exists, `prerelease=false`,
   `draft=false` and all six assets are attached.

The recovery workflow is **manual-only** to avoid repeated failures.
Normal automatic releases still occur following a future intentional
plain SemVer increase in `gradle.properties`; publication depends on
a token authorized to create GitHub Releases. The standard build never
backfills a historical version from a newer `main` commit.

**v0.8.0:** Complete its documented acceptance gates and then set
`launcherVersion=0.8.0` in one intentional version-bump commit. Do not
mark v0.8 as available before that build and its stable release succeed.

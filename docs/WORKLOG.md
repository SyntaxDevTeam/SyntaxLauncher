# SyntaxLauncher Worklog

This file is the append-only development journal for repository-changing work.

Newest entries go first, directly below this introduction. Historical entries should not be rewritten except to correct an obvious factual error.

Every code/build/documentation/CI task must add an entry according to `AGENTS.md`.

---

## 2026-10-18 — Refine desktop hero and correct UI readability

**Milestone:** v0.1 UI polish / v0.2 shared desktop shell  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and visual desktop review pending

### Completed
- Replaced the decorative empty circle with a layered night-sky voxel landscape and enlarged branded grass-block/arrow mark.
- Added restrained gradient scrims behind text; split hero title into high-contrast white and blue lines.
- Fixed dark-on-dark labels throughout the launcher by setting app-level Material Surface content color.
- Restyled the header, launch card, Play action and instance panel with deliberate borders, spacing and the approved navy/cyan palette.
- Made the initial window size respect the user's desktop work area and added a visible scrollbar plus compact settings spacing and RAM range labels.
- Preserved profile, version, Java, memory, launch, stop and download logic.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/HeroAtmosphere.kt` — deterministic Compose voxel landscape.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — hero, header, contrast and instance sidebar.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — work-area-aware window size.
- `docs/DESIGN.md`, `README.md` — current visual design and responsive behavior.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — progress tracking and verification.

### Verification
- `./gradlew test` — NOT RUN locally (no network access to Gradle dependencies).
- `./gradlew build` — NOT RUN locally (no network access to Gradle dependencies).
- Cross-platform GitHub Actions and manual screenshot review — pending.

### Roadmap updates
- [ ] v0.1 / UI / premium hero, settings scroll and typography — pending CI plus visual accessibility acceptance.

### Follow-ups / known issues
- The first CI attempt failed on a wrong import for `clip`; corrected in follow-up commit, verification rerun initiated.
- Inspect text wrapping at compact widths and standard/high-DPI scaling.
- Verify all three portable distributions and manually review the loaded/launching/error states.

---

## 2026-10-18 — Apply blue SyntaxLauncher brand and application logo

**Milestone:** v0.1 UI / shared v0.2 desktop shell  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and GUI review pending

### Completed
- Derived a small transparent launcher PNG from the approved blue Minecraft grass block / orbital arrow artwork.
- Replaced the generic letter S in the title header with the logo, added a desktop window icon and configured Linux package icon.
- Consolidated dark navy, cobalt and cyan accent colors in Material 3 theme and hero tokens.
- Aligned the hero/background/labels/dividers, kept errors semantic and made offline status intentionally subdued.
- Documented palette roles and asset usage.

### Changed files
- `launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.png` — optimized product artwork.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxTheme.kt` — design tokens.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — icon and blue UI.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — window icon.
- `launcher-desktop/build.gradle.kts` — Linux package icon.
- `docs/DESIGN.md`, `docs/ROADMAP.md`, `README.md` — new brand rules and tracking.
- `docs/WORKLOG.md` — this entry.

### Verification
- `./gradlew test` — NOT RUN, local Gradle dependency access unavailable.
- `./gradlew build` — NOT RUN, local Gradle dependency access unavailable.
- Manual desktop review — NOT RUN, pending test builds.
- PNG Git blob SHA matched the locally generated optimized asset before integration.

### Roadmap updates
- [ ] v0.1 / UI / blue branding remains open until CI and manual screen review.

### Follow-ups / known issues
- Confirm cross-platform compilation and package startup in GitHub Actions.
- Windows and macOS installer branding would need separate ICO/ICNS assets at release time; PNG is used for the Compose window on all platforms.

---

## 2026-10-18 — Stage v0.2 instance isolation and management delivery

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Completed (planning/documentation only; implementation gates remain open)

### Completed

- Reviewed the current v0.2 repository, tests, LaunchCoordinator integration and Compose instance manager against AGENTS.md, milestone scope, design rules and previous CI evidence.
- Created a single, concrete two-track implementation plan: Track A for separate Minecraft instances (identity, isolated game storage, shared cache, legacy import and safety); Track B for instance management (creation/selection, per-instance settings, clone/trash/restore and a usable Compose workspace).
- Defined phase dependencies, P0/P1 priorities, in-code baseline versus missing behavior, acceptance tests, file/data safety invariants and intentionally deferred features.
- Identified remaining gaps without claiming they exist already: automatic import copy progress/cancellation, safety around active/detached processes, in-app trash restore, fully editable JVM settings, duplicate-name disambiguation and UI acceptance testing.
- Preserved existing implementation and recorded the real-game isolation gate separately from the previously green 4/4 CI results.

### Changed files

- `docs/INSTANCE_MANAGER_PHASES.md` — detailed two-track phased plan, criteria and completion gates.
- `docs/ROADMAP.md` — new prioritized v0.2 checklist linked to the plan.
- `README.md` — direct link to phased v0.2 plan.
- `docs/WORKLOG.md` — this entry.

### Verification

- Existing GitHub Actions run `39157681455` — historical PASS, 4/4 jobs for the current v0.2 implementation baseline; no new code was added in this change.
- `./gradlew test` — NOT RUN; documentation-only change.
- `./gradlew build` — NOT RUN; documentation-only change.
- Manual gameplay acceptance — NOT RUN; two-world isolation/clone/legacy import remain separate open gates.

### Roadmap updates

- Added track A/B scoped, ordered tasks with P0/P1 and specific practical release gates; no newly completed boxes were checked.
- Existing implemented code/test milestones retain their verified status and v0.2 remains in progress.

### Follow-ups / known issues

- Start with A2's real two-world acceptance and B2's complete instance settings editor, then harden A3/A4's non-destructive operations.
- Implement in-app restore and a dedicated Instances UI before release.
- Update the roadmap/worklog and retain red/green evidence as each implementation phase is finished.

---

## 2026-10-18 — Confirm green v0.2 build and publish manual acceptance checklist

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Verified GitHub Actions run `39157681455` at commit `7aa5605`: all four jobs PASS (core unit/integration tests, desktop compilation, Linux, Windows and macOS application distributions).
- Verified successful upload of three platform-specific preview archives and a test-results artifact.
- Recorded newly verified v0.2 repository, schema, per-instance directories and launch layout integration tasks in the canonical roadmap.
- Added explicit two-world isolation, clone/rename/trash, legacy import and restart-persistence manual testing checklist.
- Updated README with CI and v0.2 test guide links.

### Changed files
- `docs/ROADMAP.md` — checked only CI-tested individual items; manual acceptance remains open.
- `docs/WORKLOG.md` — this verification entry.
- `docs/TESTING_V0.2.md` — manual test guide.
- `README.md` — test guide and CI link.

### Verification
- GitHub Actions `39157681455` — PASS 4/4 jobs.
- `:launcher-core:test` / `:launcher-desktop:compileKotlin` — PASS in CI.
- `:launcher-desktop:createDistributable` — PASS on Linux x64, Windows x64 and macOS ARM64.
- `./gradlew build` — NOT RUN locally, full aggregate build task not requested by CI.
- Manual Minecraft game-state isolation, large-world cloning, legacy import and trash recovery — NOT RUN in this environment.

### Roadmap updates
- Tested v0.2 domain, schema storage, per-instance paths, preferences and launch layout items marked `[x]`.
- UI usability and v0.2 real-game quality gates remain `🚧` until manual validation.

### Follow-ups / known issues
- Test two instances with the same Minecraft version and separate worlds using the downloadable preview.
- Verify existing v0.1 game directory is preserved across import.
- Add an in-app trash restore path and explicit schema migration when the schema evolves.

---

## 2026-10-18 — Harden restored instance selection, clone metadata and launch tests

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- CI run `39157356664` successfully compiled the new instance core/desktop implementation and passed launcher-core tests; platform packages were still in progress.
- Prevented silently launching Legacy Vanilla if a saved selected instance cannot be found.
- Restored the selected instance's Minecraft version and RAM settings after asynchronous inventory loading.
- Preserved unknown schema-1 JSON properties while cloning an instance, in addition to updates.
- Added an integration assertion for the actual LaunchPlan working directory when a per-instance layout is supplied.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — copy unknown instance metadata during clone.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — verify copied unknown JSON field.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinatorTest.kt` — assert instance-specific working directory and shared cache layout.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — restore profile settings and reject missing-profile fallback.
- `docs/ROADMAP.md` and `docs/WORKLOG.md` — track changes and verification.

### Verification
- Prior CI verification for initial v0.2 commit: PASS (core tests + desktop Kotlin compile).
- `./gradlew test` — follow-up CI pending, not run locally.
- `./gradlew build` — follow-up CI packaging pending, not run locally.
- Manual Minecraft two-instance isolation: NOT RUN.

### Roadmap updates
- Instance launch and persistence improvements still in progress pending follow-up CI and live user testing.

### Follow-ups / known issues
- Inspect full CI results, fix any Kotlin/UI/test problems.
- Confirm world isolation, import and trash behavior in actual Minecraft sessions.

---

## 2026-10-18 — Implement first v0.2 Instance Manager vertical slice

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftInstance` with immutable UUID, schema version, display name, resolved Minecraft version, RAM and optional JVM arguments.
- Added a core repository with atomic JSON writes, unknown metadata field preservation, strict schema checks and per-profile error inventory.
- Each instance stores mutable worlds/config/resources under a private UUID-named game directory; Mojang libraries/assets/versions and Java remain in existing shared cache directories.
- Added create/list/update/rename/clone/trash and explicit non-destructive v0.1 legacy game import operations.
- Clone stages worlds and settings before publication, rejects symlink entries and never deletes the source. Delete moves instance directory into recoverable `instance-trash`.
- Added a per-call instance layout to the existing MinecraftLaunchCoordinator, preserving default legacy behavior.
- Extended desktop session's Play to use an optional instance and per-instance JVM/RAM options with the same core launch pipeline.
- Added selected-instance UUID to existing launcher preferences with a backwards-compatible optional field.
- Added a simple Compose Instance Manager dialog for selecting, creating, renaming, cloning, updating, importing v0.1 worlds and confirming trash operations.
- Added five repository test cases for isolation/shared caches, clone/restore, metadata changes/unknown fields, legacy import and invalid paths.

### Changed files

- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — domain model and storage.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — storage/isolated worlds tests.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt` — optional per-instance game layout.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStore.kt` — selected-instance UUID persistence.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — repository dependency wiring.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — per-instance game launch.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceManagerDialog.kt` — CRUD Compose dialog.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — selection/management and primary Play target.
- `docs/ROADMAP.md`, `docs/WORKLOG.md`, `README.md` — status, changelog and user notes.

### Verification

- Source review completed for instance-specific mutable game directories and non-destructive operations.
- `./gradlew test` — awaiting CI run of new tests.
- `./gradlew build` — awaiting CI compile and platform distributable matrix.
- Manual two-instance Minecraft world test — NOT RUN; user acceptance required.

### Roadmap updates

- Core, storage, management, UI and launch integration marked `🚧 IN PROGRESS` until CI and manual verification.
- v0.2 quality gates remain open; no claim that v0.2 is already a stable completed release.

### Follow-ups / known issues

- Confirm CI build/test and fix any compiler/test errors before providing preview.
- Perform real-world clone, trash, legacy import and cache-sharing acceptance tests.
- Provide in-app trash restore and schema migration when a new schema is actually introduced.
- Maintain the old v0.1 game folder until explicit import, never remove it during this milestone.

---

## 2026-10-18 — Fix Kotlin Compose when-expression compilation

**Milestone:** v0.1 — UI polish  
**Status:** Partial

- CI run 39153778139: core tests passed; desktop compilation identified trailing commas after `else` expressions in the redesigned Compose home.
- Removed invalid trailing commas from UI `when` expressions without changing launch/runtime behavior.
- Changed `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` and this worklog.
- Verification: CI compiler diagnostics reviewed; follow-up run pending, no manual visual inspection yet.
- Roadmap remains in progress until Gradle and visual smoke acceptance.

---

## 2026-10-18 — Introduce SyntaxLauncher premium-feel home UI

**Milestone:** v0.1 — Vanilla Launcher UX polish  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Replaced the original two dense configuration cards with a visually distinct hero and streamlined right-side setup rail.
- Created a minimal branded header/footer, focused mint-on-deep-slate palette, strong headline hierarchy and atmospheric gradient surface without remote image dependencies or constant animation.
- Made Play the singular prominent action and kept download progress, cancellation, runtime state and stop behavior adjacent to it.
- Kept nickname, release/snapshot channel, version selection and RAM controls with the existing persisted state, metadata validation and launch coordinator.
- Reduced technical metadata during normal use; exposed failure details and logs conditionally.
- Preserved async catalog/loading/retry handling and existing process lifecycle.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt`
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxTheme.kt`
- `docs/ROADMAP.md`
- `docs/WORKLOG.md`
- `README.md`

### Verification
- UI source reviewed for existing callback/state wiring; complete Gradle CI and actual desktop appearance/testing pending.
- No core launch pipeline changes; previous CI green on v0.1 before redesign.
- Manual multi-resolution/DPI keyboard/focus assessment pending.

### Follow-ups
- Fix any compiler errors reported by cross-platform GitHub CI.
- Review screen visually on desktop at 1080×720 and narrow/min-size windows, keyboard navigation and scaling.
- Consider logo/visual refinements only after GUI screenshots and feedback.

---

## 2026-10-18 — Confirm all three v0.1 preview artifacts and green CI

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Confirmed GitHub Actions run `39125171338` finished with all four jobs SUCCESS: core tests plus desktop compile, Linux x64, Windows x64, macOS ARM64 `createDistributable` packaging.
- Verified Actions uploaded archive-wrapped preview app images, preserving Unix executable bits/symlinks: Windows artifact `12135981958`, Linux `12136131266`, macOS `12136425435`; also test-results artifact `12135961632`.
- Confirmed official Gradle wrapper commit `03d2650`, Kotlin compile/test repairs and schema-versioned preferences sources are on main.
- Synchronized roadmap acceptance status and documented the difference between CI-verifiable packages and an actual clean-machine Minecraft launch.

### Changed files

- `docs/ROADMAP.md` — mark CI build/packaging and official Gradle wrapper complete, keep full v0.1 game-run quality gates open.
- `docs/WORKLOG.md` — this verification entry.
- `README.md` — links to the verified packaging run and preview test instructions.

### Verification

- GitHub Actions `39125171338`: 4/4 jobs PASS, `:launcher-core:test` PASS, desktop Kotlin compile PASS, Windows/Linux/macOS portable app packaging PASS.
- Official wrapper generation `39123801774`: PASS.
- Actual game launch, fresh Mojang Java install, second-run cache test and GUI smoke on users' operating systems: NOT RUN.
- Full `./gradlew build` and local `./gradlew test` not executed in this network-isolated container; equivalent CI Gradle tasks reported above are PASS.

### Roadmap updates

- CI test, package matrix, official wrapper and identified compiler/build blockers checked complete.
- Full v0.1 release acceptance remains `🚧` until a real vanilla launch, platform smoke and cache reuse are demonstrated.

### Follow-ups / known issues

- Have a tester extract a preview build and complete `docs/TESTING_V0.1.md` with real-world game launch evidence and sanitized logs.
- Address any runtime, Java-provider, account/profile or packaging issues identified during the first Minecraft launch.
- Later add signed native installers, offline catalog caching and expanded platform acceptance.

---

## 2026-10-18 — Harden portable artifacts and document v0.1 preview testing

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Confirmed GitHub Actions run `39124788473` passed core tests, desktop compile and platform Compose app-image creation on Linux and macOS; Windows job passed its build and entered cleanup.
- Changed CI to put the complete Compose app image into a platform-specific tar.gz archive before uploading GitHub Actions artifacts, retaining POSIX executable bits and macOS symlinks that upload-artifact ZIP would otherwise normalize.
- Included the JDK `jdk.crypto.ec` module explicitly in the jlinked native distribution for JSSE HTTPS/TLS elliptic curve support.
- Added v0.1 manual test guide for Windows/Linux/macOS preview artifacts, cache behavior, Java auto-install, game launch, logs, known limitations and bug reporting.
- Clarified README instructions for extraction and unsigned preview-build status.

### Changed files

- `.github/workflows/build.yml` — create permission-preserving platform archives before artifact upload.
- `launcher-desktop/build.gradle.kts` — include jdk.crypto.ec in native app runtime.
- `docs/TESTING_V0.1.md` — manual acceptance/test guide.
- `README.md` — preview artifact extraction and testing links.
- `docs/ROADMAP.md` — portability/crypto task tracking.
- `docs/WORKLOG.md` — this entry.

### Verification

- Previous CI `39124788473`: core tests PASS, desktop compile PASS, Linux/macOS image creation PASS, Windows image creation PASS; archive-format changes pending next run.
- `./gradlew test` / `./gradlew build` — not executed locally in isolated container; CI runs equivalent test/build tasks.
- Manual clean-machine vanilla Minecraft launch — NOT RUN; no complete v0.1 release acceptance claim.

### Roadmap updates

- Portable artifact archive integrity and jlink TLS module task marked `🚧 IN PROGRESS` pending final CI and manual run.
- v0.1 quality gate remains open.

### Follow-ups / known issues

- Verify all three CI tar.gz preview artifacts can be extracted and launched.
- Real Minecraft launch and second-launch cache test required.
- Add native signed installers only after portability/game acceptance.

---

## 2026-10-18 — Repair macOS jpackage version and add v0.1 settings persistence

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Parsed detailed macOS jpackage error from Actions run `39124386892`: macOS app bundle rejects app-version with leading zero in first component.
- Set the technical native packageVersion to 1.0.0 to satisfy jpackage while retaining the product milestone v0.1; documented the difference.
- Implemented `LauncherPreferencesStore` with schema 1, atomic writes, safe validation, retention of unknown property keys and rejection of future-schema files without overwriting.
- Saved v0.1 non-sensitive settings only: offline nickname, selected Minecraft version, release/snapshot filter and RAM allocation.
- Connected preferences to desktop session and Compose state restoration. Debounced writes occur outside Compose, and window close attempts to flush the last pending settings.
- Added tests for settings round-trip, forward unknown-property retention and newer-schema preservation.

### Changed files

- `launcher-desktop/build.gradle.kts` — technical macOS-compatible package version.
- `launcher-core/src/main/kotlin/.../storage/LauncherPreferencesStore.kt` — settings persistence.
- `launcher-core/src/test/kotlin/.../storage/LauncherPreferencesStoreTest.kt` — settings validation tests.
- `launcher-desktop/src/main/kotlin/.../desktop/DesktopLauncherSession.kt` — debounced saves and close flushing.
- `launcher-desktop/src/main/kotlin/.../desktop/Main.kt` — storage wiring.
- `launcher-desktop/src/main/kotlin/.../desktop/ui/SyntaxLauncherApp.kt` — startup restore and persistence.
- `docs/ROADMAP.md` — settings/packaging status.
- `docs/WORKLOG.md` — this entry.
- `README.md` — settings format and jpackage note.

### Verification

- macOS jpackage failure: CONFIRMED from workflow error log, quoting the leading-zero version restriction.
- Linux/Windows packages and core tests: PASS in previous Actions run, before these changes.
- New v0.1 settings test sources added; `./gradlew test` and `./gradlew build` not executed in network-isolated local container. New CI run pending.
- Real GUI/Minecraft manual launch: NOT RUN.

### Roadmap updates

- Preferences and macOS packaging fix now `🚧 IN PROGRESS` pending CI and real platform acceptance.
- v0.1 acceptance gates remain open until a real Minecraft launch is verified.

### Follow-ups / known issues

- Verify Gradle tests and all three portable Compose distributions succeed with the corrected macOS app version.
- Run a real new-install Minecraft start using a generated app distribution.
- Test fresh start/restart persistence and cached second launch on supported desktop platforms.
- Add reliable manifest caching and improve first-run offline/error recovery.

---

## 2026-10-18 — Confirm test and Windows/Linux packages; capture macOS jpackage logs

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Verified Actions run `39123949155` completed the core tests and desktop Kotlin compilation successfully.
- Confirmed Windows and Linux Compose `createDistributable` job success and published platform-specific portable distribution artifacts.
- Confirmed the restricted wrapper bootstrap workflow `39123801774` succeeded and committed `gradlew`, `gradlew.bat`, official `gradle-wrapper.jar` and properties to main as commit `03d2650`.
- Identified remaining macOS failure at `:launcher-desktop:createDistributable` after both core and desktop Kotlin compiled; the underlying jpackage output was written to a local build log not included in the original Actions job output.
- Updated packaging workflow to print and attach Compose/jpackage logs when packaging fails.
- Updated README to document local test/run commands and portable distribution outputs.

### Changed files

- `.github/workflows/build.yml` — jpackage diagnostic log printing and upload on failure.
- `README.md` — verified wrapper and build instructions.
- `docs/ROADMAP.md` — reflect green verify/Windows/Linux jobs, wrapper and macOS packaging pending.
- `docs/WORKLOG.md` — this entry.

### Verification

- GitHub Actions run `39123949155`: core tests PASS; desktop Kotlin compile PASS; portable Linux/Windows app creation PASS; macOS app creation FAIL at jpackage.
- Official Gradle wrapper generation workflow `39123801774`: PASS; all expected wrapper files confirmed on main.
- `./gradlew test` / `./gradlew build`: not executed locally in the network-isolated container. Actions executes equivalent Gradle test and distribution tasks.
- Actual Minecraft game start on a desktop machine: NOT RUN.
- macOS jpackage diagnostic rerun: pending new CI run.

### Roadmap updates

- CI verification and Windows/Linux packaging now source-verified passing; remaining macOS/real-game acceptance open.
- Wrapper exists; remaining quality gates not marked complete until full build and real-game verification.

### Follow-ups / known issues

- Read jpackage error log from the next macOS packaging job and implement a targeted fix.
- Complete v0.1 settings persistence and manual launch instructions.
- Verify packaged app starts Minecraft and runs on supported platforms.

---

## 2026-10-18 — Repair malformed launch integration test source

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Analyzed GitHub Actions run `39123801750`: production launcher-core and launcher-desktop code compiled, but integration test source was corrupted by a string replacement.
- Recreated `MinecraftLaunchCoordinatorTest.kt` from the last known intact revision, retaining actual Kotlin literal placeholder escaping via `\$` characters.
- Removed the malformed duplicated source fragments rather than attempting incremental repairs.

### Changed files
- `launcher-core/src/test/kotlin/.../launch/MinecraftLaunchCoordinatorTest.kt` — restore tests and valid literal Minecraft arguments.
- `docs/WORKLOG.md` — this entry.

### Verification
- Previous Actions run: production core and desktop Kotlin compiled; test source compilation failed due malformed placeholder string.
- Tests/build: awaiting full CI rerun with corrected source.
- Live Minecraft: NOT RUN.

### Roadmap updates
- CI remains IN PROGRESS pending test and packaging pass.

### Follow-ups / known issues
- Inspect JUnit results after source is compiled.
- Verify platform app package jobs.
- Verify official Gradle wrapper bootstrapped by workflow.

## 2026-10-18 — Fix desktop Ktor dependency and launch test interpolation; bootstrap wrapper

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Parsed Actions run `39123629786`: `launcher-core:compileKotlin` passed, while desktop compilation failed because `DesktopLauncherSession` references Ktor `HttpClient` and the desktop module lacked a direct dependency.
- Added explicit desktop `ktor-client-core` implementation dependency.
- Corrected two test strings in `MinecraftLaunchCoordinatorTest` to preserve literal Minecraft placeholders rather than using Kotlin variable interpolation.
- Added restricted `bootstrap-gradle-wrapper.yml` job to generate the official Gradle 9.4.0 wrapper from a pinned distribution, verify it, and commit scripts/JAR/properties back to main as the GitHub Actions bot. The change only runs on main when the workflow itself is introduced or manually dispatched.
- Kept shared GitHub Actions compile/test/portable packaging workflow separate from one-time wrapper bootstrap.

### Changed files

- `launcher-desktop/build.gradle.kts` — add Ktor core API to the desktop compile classpath.
- `launcher-core/src/test/kotlin/.../launch/MinecraftLaunchCoordinatorTest.kt` — literal placeholder escaping.
- `.github/workflows/bootstrap-gradle-wrapper.yml` — restricted official wrapper bootstrap.
- `docs/ROADMAP.md` — updated build milestones.
- `docs/WORKLOG.md` — this entry.

### Verification

- Actions `39123629786`: core compilation PASS, desktop compilation and core test source compilation FAIL with the diagnosed missing Ktor dependency/literal placeholder errors.
- `./gradlew test` and `./gradlew build`: NOT RUN locally; awaiting wrapper/bootstrap and GitHub Actions rerun.
- Native packaging/real Minecraft: NOT RUN.

### Roadmap updates

- Gradle wrapper generation in progress, all v0.1 build tests/packaging remain incomplete until successful Jobs.

### Follow-ups / known issues

- Inspect wrapper bootstrap execution and confirm official JAR is committed.
- Repair any compiler/test errors from the next full CI run.
- Verify portable artifacts for Linux, Windows and macOS.
- Add version/nickname/RAM persistence and real-Minecraft acceptance testing.

---

## 2026-10-18 — Fix Minecraft classpath Kotlin compilation

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Read GitHub Actions run `39123484537` and isolated Kotlin compilation errors at `MinecraftFilePlan.kt:58` and `:77`.
- Replaced ambiguous `MutableList<Path> += Path` with `MutableList.add(path)`, fixing Kotlin overload resolution where `Path` is also `Iterable<Path>`.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/minecraft/MinecraftFilePlan.kt` — classpath addition.
- `docs/WORKLOG.md` — this entry.

### Verification
- Actions run `39123484537`: Gradle resolved external dependencies; Kotlin core compilation failed in two identified lines.
- `./gradlew test` / `./gradlew build`: NOT RUN locally because wrapper/network access is unavailable.
- New CI run: pending.

### Roadmap updates
- No acceptance box checked; compilation, tests and package readiness remain in progress.

### Follow-ups / known issues
- Examine next compiler/test failures and fix until the CI verification stage passes.
- Verify native desktop packages and official wrapper.

## 2026-10-18 — Fix Compose dependency resolution in CI

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Read first GitHub Actions build `39123291984` failure: eleven unresolved `androidx.*` dependencies, including lifecycle, savedstate, runtime and annotations.
- Root cause was missing Google Maven in Gradle dependency and plugin repositories.
- Added `google()` to both dependency and plugin repository declarations; no speculative Kotlin or Compose version downgrade.

### Changed files

- `settings.gradle.kts` — Google Maven resolution.
- `docs/ROADMAP.md` — running build/CI progress and blocker recorded.
- `docs/WORKLOG.md` — this entry.

### Verification

- Original Actions log — FAIL: module resolution due to missing Google Maven.
- `./gradlew test` and `./gradlew build` — NOT RUN locally (no wrapper/network); subsequent GitHub Actions rerun is pending.
- Real Minecraft launch — NOT RUN.

### Roadmap updates

- CI stays `🚧 IN PROGRESS`, dependency resolution fix requires Actions verification.

### Follow-ups / known issues

- Recheck CI logs for compiler and test errors after Maven repository resolution.
- Continue platform packaging and official wrapper generation.

---

## 2026-10-18 — Introduce cross-platform build and packaging CI

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added first GitHub Actions workflow running Kotlin core tests and desktop compilation with pinned Gradle 9.4.0 / Temurin JDK 21.
- Added a Linux/Windows/macOS portable desktop distribution matrix using Compose `createDistributable`.
- Added automatic upload of zipped portable application directories and test report artifacts for GitHub Actions build runs.
- Preserved read-only workflow permissions and concurrency cancellation for duplicate runs.
- Verified Kotlin 2.4.21 and Compose 1.12.1 plugin releases exist on official plugin portals; avoided speculative version changes.
- Identified missing official Gradle wrapper as a separate reproducibility task; CI installs Gradle through a pinned trusted GitHub Action until the official wrapper jar can be generated.

### Changed files

- `.github/workflows/build.yml` — verify/tests and portable app matrix packaging workflow.
- `docs/ROADMAP.md` — build/CI and acceptance tasks.
- `docs/WORKLOG.md` — this entry.
- `README.md` — CI workflow and artifacts instructions.

### Verification

- GitHub repository check — no prior workflows or workflow runs before this change.
- Gradle plugin versions checked against official plugin registries.
- `./gradlew test` — NOT RUN: repository did not contain a wrapper; this container cannot resolve GitHub/Maven URLs.
- `./gradlew build` — NOT RUN: same blocker.
- Workflow build/packaging — pending first GitHub Actions run; no verified distribution claimed.

### Roadmap updates

- CI and platform distributions `🚧 IN PROGRESS` pending real Actions job success.
- Official Gradle wrapper generation and v0.1 real-game launch remain open.

### Follow-ups / known issues

- Inspect the first CI errors and fix compilation/test/runtime problems before marking jobs complete.
- Generate and commit the official Gradle wrapper artifacts in a build-capable environment.
- Run a full packaged-app launch and verify Minecraft gameplay.
- Test platform-specific packaging/installers and verify executable resources included by jpackage.

---

## 2026-10-18 — Wire complete Minecraft launch pipeline into Compose Desktop

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftLaunchCoordinator` in launcher-core, sequentially performing prepared version → game files/asset index/assets/natives → required managed Java → structured LaunchPlan → ProcessBuilder launch.
- The version metadata is already resolved and SHA-1 verified by the version picker before Play; pressing Play reuses this resolved metadata rather than performing an unnecessary second metadata request.
- Added explicit phases IDLE/PREPARING_GAME/PREPARING_JAVA/BUILDING_PLAN/STARTING_GAME/STARTED/FAILED/CANCELLED and a StateFlow for current detail, download counts and per-file progress.
- Mapped core MinecraftPreparationEvent, JavaRuntimeEvent and DownloadEvent into user-visible progress stages.
- Guarded parallel Play requests with a core Mutex plus session ownership; missing Java requirement is reported before starting a large game download.
- Added a narrow, dedicated `DesktopLauncherSession` owning the coroutine scope, shared HTTP lifecycle and cancellation independently of Compose recomposition.
- On close, cancel and join any preparation job, detach from a running child process, then close the Ktor client and exit the app on the Swing dispatcher.
- Updated the Compose main screen with a real enabled/disabled Play button and Stop action; nickname, Minecraft version and RAM are forwarded to the core pipeline.
- The UI now displays preparation/Java file progress, retry for metadata fetch, process running/stopped/crashed status, PID, exit code, log path, cancellation and failure summaries.
- Controls are disabled during preparation and when the game is running; existing offline-play warning and restrained dark visual identity were preserved.
- Added controlled test sources covering a synthetic Mojang client download + offline LaunchPlan + fake Java provider + local Java child-process launch, and early failure when the Java requirement is absent.
- Kept login/premium/mod features out of v0.1, and did not claim a real Minecraft launch before acceptance tests.

### Changed files

- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt` — launch orchestration and shared typed StateFlow progress.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinatorTest.kt` — controlled end-to-end-style pipeline test sources.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — coroutines, launch cancellation, process detachment and client shutdown.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — composition root / dependency wiring.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — actual Play/Stop, progress/error/status UI and control locking.
- `docs/ROADMAP.md` — v0.1 process/launch/UI tracker synchronized.
- `docs/WORKLOG.md` — this entry.
- `README.md` — project overview updated to describe the actual Compose pipeline.

### Verification

- GitHub file/API review — source interfaces for catalog, metadata, preparation, Java Manager, LaunchPlan, ProcessManager, downloader and Compose dependencies checked before integration.
- Source/static review — phase ordering, cancellation lifecycle, no Compose dependency inside launcher-core, stable session ownership and supported offline scope reviewed.
- Deterministic integration-style test sources added with Ktor MockEngine and local Java child process; actual Gradle/JUnit execution NOT RUN.
- `./gradlew test` — NOT RUN; no Gradle wrapper is committed and container DNS/dependency resolution is unavailable.
- `./gradlew build` — NOT RUN for the same reason.
- Real Minecraft/Compose GUI manual launch — NOT RUN. Play exists in the source but may require fixes after first full build and live test.

### Roadmap updates

- Compose Play, Stop, runtime/download status, progress, cancellation, control locking, process status and close lifecycle now `🚧 IN PROGRESS` pending build and live acceptance.
- UI RAM now feeds actual LaunchOptions; process and Java stages are wired to desktop through a core coordinator.
- v0.1 quality gates remain unchecked; persistence, manifest caching, first real-game launch and supported-platform checks remain open.

### Follow-ups / known issues

- Commit a Gradle wrapper, run test/build and resolve any Kotlin/Compose library API mismatches identified by a real build.
- Perform a fresh Mojang vanilla install and launch on supported desktop platforms; verify current official Java runtime metadata/downloads and stdout/stderr behavior.
- Persist nickname, selected Minecraft version and RAM, and cache version manifest with safe fallback/error recovery.
- Before v0.3 premium authentication, replace raw child-process log redirection with a token-safe logging design.

---

## 2026-10-18 — Implement Minecraft process lifecycle management

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftProcessManager` to launcher-core using the existing, already-resolved `LaunchPlan` without hardcoded Minecraft launch recipes.
- Launched native child processes with `ProcessBuilder`, the resolved Java executable, structured command arguments, and the prepared game working directory.
- Added distinct per-run Minecraft log directories under launcher logs, with separate operating-system redirected stdout/stderr files. POSIX files are created with owner-only permissions.
- Added `StateFlow<MinecraftProcessStatus>` with IDLE, STARTING, RUNNING, STOPPED, CRASHED and DETACHED states, timestamps, PID, log paths, error summary and exit code.
- Added mutex-guarded single-session ownership, rejecting accidental duplicate starts and releasing the slot after exit.
- Added natural exit monitoring through `Process.onExit()`, distinguishing code-zero exits, unexpected nonzero exits and user-requested termination.
- Added graceful termination with a configurable timeout and forced termination fallback.
- Added explicit launcher-shutdown policy: `DETACH` preserves the child and native log redirects, `TERMINATE` stops the child.
- Added typed start, duplicate, closed-manager and termination failures without logging the command line or any launch credentials.
- Added a narrow injectable process-starter seam for deterministic tests; no Compose dependency.
- Added five controlled process scenarios covering a real JDK subprocess, logs, duplicate protection, crash exit code, relaunch, forced stop, detach and startup-error recovery.

### Changed files

- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftProcessManager.kt` — process start/exit/stop/detach lifecycle, logging and typed status/errors.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftProcessManagerTest.kt` — five lifecycle test cases, including an actual local Java subprocess.
- `docs/ROADMAP.md` — precise process-management implementation status and premium-log redaction prerequisite.
- `docs/WORKLOG.md` — this entry.
- `README.md` — actual implementation status and remaining v0.1 tasks.

### Verification

- Isolated `kotlinc` compilation against installed JDK 21, coroutines JAR and minimal domain/logging stubs — PASS.
- Five executable local smoke scenarios (using an actual Java subprocess plus controlled fake processes) — PASS.
- Architecture review — PASS for Compose-independent process ownership and not printing command arguments.
- `./gradlew test` — NOT RUN; the repository still lacks a committed Gradle wrapper and this container cannot resolve github.com/Maven dependencies.
- `./gradlew build` — NOT RUN for the same infrastructure reasons.
- Clean-machine vanilla Minecraft launch — NOT RUN; Compose Play wiring is not yet implemented.
- Direct raw stdout/stderr redirection is suitable for offline v0.1; redaction must be revisited before introducing premium access tokens in v0.3.

### Roadmap updates

- All new process-start/state/log/exit-code/duplicate-protection/stop/shutdown/error/test items marked `🚧 IN PROGRESS` pending Gradle and real-game acceptance.
- Explicit v0.3 dependency added for reviewing sensitive strings in Minecraft process logs.
- Compose launch orchestration and window-close wiring remain unchecked; no fake Play control was added.
- v0.1 test/build/real-launch quality gates remain unchecked.

### Follow-ups / known issues

- Connect metadata → game preparation → managed Java → LaunchPlan → ProcessManager into the real Compose Play action.
- Map progress, errors and running/stopped/crashed states to a lightweight UI with conflict-safe controls.
- Persist offline username, selected version and RAM; cache version catalog where appropriate.
- Commit a Gradle wrapper, run full test/build, then verify a clean-machine vanilla launch and the supported OS matrix.
- Before v0.3 Microsoft authentication, change raw Minecraft process log capture to guarantee token redaction without breaking detach behavior.

---

## 2026-10-17 — Build metadata-driven Minecraft LaunchPlan

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Implemented `LaunchPlan` as the structured, process-independent output of the v0.1 launch pipeline: resolved Java runtime, working directory, main class, JVM arguments, game arguments and final argument-vector command.
- Refactored `LaunchRequest` to consume a verified `PreparedMinecraftVersion` instead of only raw version metadata, so launch planning uses the exact prepared classpath/directories rather than recalculating filesystem state.
- Added `LaunchAuthentication` as a narrow normalized session boundary. v0.1 maps an offline account to stable UUID, `accessToken=0`, legacy user type and a deterministic offline session string; v0.3 can later provide Microsoft credentials without cloning the launch engine.
- Added `MinecraftArgumentPlaceholderResolver` with explicit unresolved-placeholder failure rather than silently starting Java with invalid `${...}` values.
- Added metadata-driven substitution for `auth_player_name`, `version_name`, `game_directory`, `assets_root`, `game_assets`, `assets_index_name`, `auth_uuid`, `auth_access_token`, `auth_session`, `user_type`, `user_properties`, `version_type`, `natives_directory`, `launcher_name`, `launcher_version`, `classpath`, `library_directory`, `classpath_separator`, `auth_xuid` and `clientid`.
- Added platform-aware classpath joining (`;` on Windows, `:` elsewhere) while preserving each Java/process argument as a separate list element instead of building a shell command string.
- Added JVM memory arguments from `LaunchOptions` plus extra user JVM arguments.
- Applied already-existing Mojang OS/architecture/feature rules before resolving JVM/game placeholders.
- Added logging configuration injection by resolving the metadata logging argument `${path}` to the verified downloaded logging configuration.
- Added legacy launch fallback for versions without metadata JVM arguments: explicit `-Djava.library.path`, `-cp` and the prepared classpath while continuing to use legacy `minecraftArguments` parsing.
- Added `librariesDirectory` to `PreparedMinecraftVersion` so `${library_directory}` is sourced from preparation output rather than guessed by the launch layer.
- Added typed launch-plan failures for missing required artifacts and unknown/unresolved placeholders.
- Added deterministic tests covering modern metadata-driven plans, memory/classpath/natives/logging/offline identity substitution, legacy JVM fallback, paths containing spaces, full command ordering and explicit failure for future unknown placeholders.
- Cross-checked standard launch placeholder usage against current PrismLauncher launch/session handling and historical metadata fixtures before freezing the v0.1 mapping.
- Updated `docs/ROADMAP.md` so all launch-planning items reflect implemented-but-not-yet-fully-executed state.

### Changed files

- `launcher-core/src/main/kotlin/.../launch/LaunchModels.kt` — prepared-game request boundary plus normalized offline launch authentication.
- `launcher-core/src/main/kotlin/.../launch/LaunchPlan.kt` — structured final launch plan and typed launch-plan errors.
- `launcher-core/src/main/kotlin/.../launch/MinecraftArgumentPlaceholderResolver.kt` — strict Mojang placeholder substitution.
- `launcher-core/src/main/kotlin/.../launch/LaunchPlanBuilder.kt` — modern/legacy JVM and game argument construction, classpath, memory, logging and offline identity wiring.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftPreparationService.kt` — exposes prepared libraries directory to launch planning.
- `launcher-core/src/test/kotlin/.../launch/LaunchPlanBuilderTest.kt` — modern/legacy structured launch-plan tests.
- `launcher-core/src/test/kotlin/.../launch/MinecraftArgumentPlaceholderResolverTest.kt` — placeholder and path-with-spaces tests.
- `docs/ROADMAP.md` — synchronized launch-planning status.
- `docs/WORKLOG.md` — this entry.

### Verification

- Launch core `kotlinc` smoke compilation with minimal domain stubs — PASS on the available JDK 21 environment; the new launch classes compile as a coherent unit.
- Architecture/source review — PASS: launch construction remains in `launcher-core`, consumes prepared/runtime state, does not depend on Compose and never uses shell command concatenation.
- GitHub compare review — PASS for the intended v0.1 launch-planning files and deterministic tests.
- External launcher compatibility review — PASS for standard Mojang placeholders/session mapping patterns used by PrismLauncher; unknown future placeholders intentionally fail explicitly.
- Container repository access — BLOCKED; the execution container still cannot resolve `github.com`, preventing a real repository clone/dependency resolution.
- `./gradlew test` — NOT RUN; repository still lacks a committed Gradle wrapper and external Gradle/Maven dependency access is unavailable in the execution environment.
- `./gradlew build` — NOT RUN for the same environment/tooling limitation.
- Real Minecraft process launch — NOT RUN by design in this task; process execution is the next v0.1 stage.

### Roadmap updates

- `LaunchRequest`, `LaunchOptions`, classpath construction, placeholder substitution, Mojang argument rules, memory, assets, natives, game directory, offline identity, logging config, legacy fallback and final `LaunchPlan` are now `🚧 IN PROGRESS` with implementation/test sources present.
- The new normalized authentication seam is documented as infrastructure for the common launch pipeline, not as premature Microsoft authentication.
- Process-management items remain fully open.
- v0.1 quality gates remain unchecked until full Gradle tests/build and a real clean-machine vanilla launch pass.

### Follow-ups / known issues

- Implement `MinecraftProcessManager` around `ProcessBuilder` using `LaunchPlan.command` and `LaunchPlan.workingDirectory`.
- Capture stdout/stderr into a separate Minecraft process log and expose running/stopped/crashed state plus exit code.
- Prevent duplicate launches and define launcher shutdown behavior while Minecraft remains running.
- Wire the real end-to-end pipeline (metadata → preparation → Java → launch plan → process) into Compose and only then expose `Play`.
- Add persistence for nickname/version/RAM selections and version-manifest cache.
- Legacy asset indexes using `virtual` / `map_to_resources` still require materialized legacy asset views before claiming broad historical-version compatibility.
- Add a Gradle wrapper and execute the complete automated test/build suite in an environment with dependency access.

---

## 2026-10-17 — Implement automatic managed Mojang Java Runtime Manager

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Implemented `MojangJavaRuntimeManager` as the concrete v0.1 `JavaRuntimeManager` implementation.
- Confirmed and integrated Mojang's current official Java runtime index endpoint rather than hardcoding Minecraft-version-to-Java tables.
- Added platform mapping for Mojang runtime ids: Windows x86/x64/ARM64, Linux x86/x64, macOS x64 and macOS ARM64; unsupported official combinations fail explicitly.
- Added runtime target selection by Minecraft-declared component (`jre-legacy`, `java-runtime-*`) and release availability.
- Added runtime-index and runtime-manifest parsing for files, directories, links, executable flags and raw download descriptors.
- Added verified runtime-manifest caching using the common `DownloadManager` and Mojang-provided SHA-1/size metadata.
- Added complete staged runtime installation into launcher-managed `runtimes/` storage instead of mutating the final runtime directory during download.
- Reused the shared verified downloader for every runtime file, preserving retries, cancellation, bounded concurrency and progress events.
- Added manifest-path validation preventing absolute/path-traversal writes outside the staging root.
- Added manifest-declared directory creation, executable-bit handling and symbolic-link creation with safe target validation.
- Added macOS Java-home detection for `jre.bundle/Contents/Home`, alongside Windows `bin/java.exe` and Linux/Unix `bin/java`.
- Hardened `JavaRuntimeInspector` with a bounded `java -version` process timeout and modern/legacy Java-major parsing.
- Added pre-publication and post-publication runtime verification against the Minecraft-required Java major.
- Added atomic directory publication where supported plus a schema-versioned `.syntaxlauncher-runtime.json` completion marker.
- Added warm-start reuse before any runtime-index network call.
- Strengthened warm-start validation to verify the cached runtime manifest plus every managed runtime file by SHA-1/size, directory presence, symlink target and a working `java -version`; corrupt/incomplete installations are rejected and reinstalled automatically.
- Extended `JavaRuntime` with source/component/platform/release identity for later launch diagnostics without coupling the launch layer to Mojang DTOs.
- Added typed runtime failures for unsupported platform, metadata, manifest, installation, verification and major-version mismatch cases.
- Added runtime lifecycle events (`checking`, metadata, downloads, installation, verification and ready) for later Compose progress/status presentation.
- Added deterministic parser tests plus a `MockEngine` integration-style manager test covering initial installation, no-network warm reuse and automatic repair after deleting an installed runtime file.
- Updated README and v0.1 roadmap to match the actual implementation state.

### Changed files

- `launcher-core/src/main/kotlin/.../java/JavaRuntime.kt` — managed-runtime identity/source fields.
- `launcher-core/src/main/kotlin/.../java/JavaRuntimeManager.kt` — runtime events, probe boundary and hardened `JavaRuntimeInspector`.
- `launcher-core/src/main/kotlin/.../java/JavaRuntimeExceptions.kt` — typed runtime failures.
- `launcher-core/src/main/kotlin/.../java/MojangRuntimeMetadata.kt` — Mojang runtime index/manifest models, parser and platform mapping.
- `launcher-core/src/main/kotlin/.../java/MojangJavaRuntimeManager.kt` — discovery, download, staging, verification, publication, reuse and repair pipeline.
- `launcher-core/src/test/kotlin/.../java/MojangRuntimeMetadataParserTest.kt` — controlled runtime metadata parsing tests.
- `launcher-core/src/test/kotlin/.../java/MojangJavaRuntimeManagerTest.kt` — controlled installation/reuse/repair test using Ktor `MockEngine` and a fake runtime probe.
- `README.md` — documented implemented automatic Java runtime management.
- `docs/ROADMAP.md` — synchronized Java Runtime Manager progress and remaining platform/build acceptance work.
- `docs/WORKLOG.md` — this entry.

### Verification

- Official metadata verification — PASS: Mojang's current runtime index endpoint exposes component-specific manifests for supported platform keys and currently includes runtime components through `java-runtime-epsilon`/Java 25 where available.
- Source/architecture review — PASS for metadata-driven selection, core/UI separation, staged publication, path safety, reuse-before-network and full warm-cache integrity checks.
- Controlled tests added — parser coverage plus managed install/reuse/corruption-repair behavior using Ktor `MockEngine`; no live Mojang dependency in normal tests.
- GitHub compare review — PASS; changes remain within v0.1 Java runtime management plus required documentation.
- `./gradlew test` — NOT RUN; repository still lacks a committed Gradle wrapper and the execution container cannot resolve external Gradle/Maven/GitHub dependencies.
- `./gradlew build` — NOT RUN for the same environment/tooling limitation.
- Real Windows/Linux/macOS Mojang runtime installation — NOT RUN in this environment; roadmap items therefore remain `🚧 IN PROGRESS` rather than `[x]`.

### Roadmap updates

- Mojang runtime source/selection, managed reuse, download/install, integrity verification, executable resolution and typed errors are now marked `🚧 IN PROGRESS` with implementation present and execution acceptance pending.
- Runtime manifest/file verification and automatic repair are explicitly tracked.
- Supported runtime platform matrix finalization remains open because official Mojang component availability differs by OS/architecture/component.
- v0.1 quality gates remain unchecked until full Gradle tests/build and a real clean-machine Minecraft launch pass.

### Follow-ups / known issues

- Build final placeholder substitution and `LaunchPlan` using `PreparedMinecraftVersion` plus the resolved managed `JavaRuntime`.
- Add Minecraft process start/monitoring after launch-plan validation.
- Wire Java/download/preparation events into the lightweight Compose launch progress state and only then expose the real `Play` action.
- Add a Gradle wrapper and run the complete automated test/build suite.
- Perform real runtime-install acceptance on the supported Windows/Linux/macOS architecture matrix.
- Decide whether Linux ARM64 should remain explicitly unsupported when Mojang has no matching official runtime entry or receive a later alternate runtime-provider adapter.

---

## 2026-10-17 — Add verified download and Minecraft preparation pipeline

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added a reusable `DownloadManager` around the shared Ktor client for streamed artifact downloads.
- Added `.part` staging and atomic finalization so incomplete downloads are never published as valid launcher files.
- Added SHA-1 and expected-size validation for both new downloads and existing cached files.
- Added verified cache reuse: valid files skip the network while invalid files are replaced only after a successful new download.
- Added bounded batch concurrency using coroutines and a configurable semaphore (default parallelism: 6).
- Added structured cancellation behavior; cancelled/failed operations clean their partial files while already completed verified cache files remain reusable.
- Added bounded retry/backoff for transient network failures, HTTP 408/425/429/5xx responses and integrity/size failures.
- Added typed download failures for network, HTTP, checksum, size, filesystem and conflicting-target conditions.
- Added typed download events for batch/file start, progress, retry, completion and failure, providing the future Compose progress UI with a core-level event source.
- Added duplicate-target coalescing so identical requests for one cache artifact are executed only once and conflicting definitions fail explicitly.
- Increased the shared HTTP request timeout to better accommodate binary artifact downloads while retaining separate connect/socket limits.
- Added `MinecraftPreparationService`, which executes the existing file plan: client JAR, libraries, native archives, asset index, logging configuration and asset objects.
- Added verified asset-index loading, content-addressed asset download planning and duplicate asset-hash de-duplication.
- Added deterministic version-specific native-directory regeneration and extraction after native archives have been verified.
- Added `PreparedMinecraftVersion` as the output boundary that carries the resolved version, file plan, parsed asset index, prepared directories and download results toward the future launch planner.
- Added controlled `MockEngine` test sources for cache reuse, transient retry, failed-integrity publication safety and end-to-end Minecraft preparation from synthetic client/library/native/asset payloads.

### Changed files

- `launcher-core/src/main/kotlin/.../network/DownloadModels.kt` — download requests/results/events and retry policy.
- `launcher-core/src/main/kotlin/.../network/DownloadExceptions.kt` — typed download failures.
- `launcher-core/src/main/kotlin/.../network/DownloadIntegrityVerifier.kt` — cached-file size/SHA-1 verification.
- `launcher-core/src/main/kotlin/.../network/DownloadManager.kt` — streaming downloads, cache reuse, retries, cancellation, bounded concurrency, progress and atomic publication.
- `launcher-core/src/main/kotlin/.../network/LauncherHttpClientFactory.kt` — increased request timeout for artifact downloads.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftPreparationService.kt` — executable game-file preparation pipeline and stage events.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftFilePlan.kt` — de-duplicates asset objects by content hash.
- `launcher-core/src/test/kotlin/.../network/DownloadManagerTest.kt` — controlled cache/retry/integrity tests.
- `launcher-core/src/test/kotlin/.../minecraft/MinecraftPreparationServiceTest.kt` — controlled preparation integration test using `MockEngine` and temporary filesystem state.
- `docs/ROADMAP.md` — synchronized download/game-file preparation progress.
- `docs/WORKLOG.md` — this entry.

### Verification

- Architecture/source review — PASS for core/UI separation, shared download ownership, retry classification, partial-file lifecycle, integrity checks and preparation sequencing.
- Ktor API review — PASS against current `bodyAsChannel`/`ByteReadChannel.readAvailable` and MockEngine response APIs used by the implementation.
- GitHub compare review — PASS; implementation changes are scoped to download/preparation infrastructure and controlled tests.
- Container repository clone/build attempt — BLOCKED; execution environment cannot resolve `github.com`, so the repository/dependencies cannot be fetched into the build container.
- `./gradlew test` — NOT RUN; Gradle wrapper is still not committed and external Gradle/Maven dependency resolution is unavailable in the execution environment.
- `./gradlew build` — NOT RUN for the same tooling/network reason.
- New roadmap items therefore remain `🚧 IN PROGRESS` rather than `[x]` even where implementation/test sources are present.

### Roadmap updates

- Client/library/native/asset-index/asset-object download execution is now `🚧 IN PROGRESS` pending Gradle/integration execution.
- Hash/size validation, verified cache reuse and partial-file publication safety are now `🚧 IN PROGRESS` with controlled test sources present.
- Download cancellation, bounded concurrency, progress events, retry policy, atomic writes and typed failures are now `🚧 IN PROGRESS` pending test execution.
- Native extraction lifecycle is now connected to verified downloads and remains `🚧 IN PROGRESS` pending execution.
- Assets/natives/game-directory outputs are now exposed toward launch planning, but placeholder substitution and final `LaunchPlan` remain open.
- Compose download/progress presentation remains only partially implemented because the core event stream is not yet wired to UI state.

### Follow-ups / known issues

- Implement managed Java runtime discovery/download/install and reuse the same verified downloader for runtime artifacts.
- Build final placeholder substitution and `LaunchPlan` using the prepared classpath/assets/natives/game directories.
- Add Minecraft process start/monitoring only after Java selection and launch-plan validation exist.
- Wire preparation/download events into the lightweight Compose progress UI when a real Play flow is available.
- Add persistence for launcher selections and manifest/settings cache.
- Legacy asset indexes using `virtual` / `map_to_resources` still need materialized legacy views if those historical versions become part of the supported v0.1 matrix.
- Add a Gradle wrapper and run full `test`/`build` plus a real clean-machine Minecraft launch before completing acceptance boxes.

---

## 2026-10-16 — Resolve version metadata and plan Minecraft files

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftVersionResolver` for downloading the selected version metadata and verifying its manifest SHA-1 before parsing.
- Added domain models and DTO parsing for client download metadata, libraries, native classifiers, asset indexes, main class, modern JVM/game arguments, legacy `minecraftArguments`, Java runtime requirements and client logging configuration.
- Added normalized Windows/Linux/macOS and CPU-architecture detection for Minecraft rule evaluation.
- Added deterministic Mojang-style allow/disallow rule evaluation including OS/version/architecture and feature predicates.
- Added argument filtering/resolution for the selected platform and feature set.
- Added platform-aware library and native-classifier resolution.
- Added `MinecraftInstallationLayout` and a `MinecraftFilePlanner` that plans client, library, native, asset-index, logging-config and classpath locations without performing downloads yet.
- Added asset-index parsing plus planning of Mojang asset-object URLs and hash-prefix cache paths.
- Added safe native JAR extraction with `META-INF` filtering and Zip Slip protection.
- Added initial `JavaRuntime`, `JavaRuntimeManager` boundary and `JavaRuntimeInspector` capable of validating a local Java executable and parsing modern/legacy Java version strings.
- Added initial `LaunchOptions` and `LaunchRequest` models so the future launch builder receives explicit account/version/runtime/platform/options state.
- Connected selected-version resolution to Compose Desktop. The UI now verifies `version.json` after version selection and shows Java requirement/library count or a retryable metadata error.
- Added unit-test sources for rule evaluation, modern/legacy version metadata parsing, native resolution, asset-index parsing, file planning, Zip Slip rejection and Java version parsing.

### Changed files

- `launcher-core/src/main/kotlin/.../minecraft/MinecraftPlatform.kt` — normalized OS/architecture model.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftRule.kt` — Mojang rule model/evaluator.
- `launcher-core/src/main/kotlin/.../minecraft/ResolvedMinecraftVersion.kt` — resolved metadata domain, argument resolution and legacy tokenizer.
- `launcher-core/src/main/kotlin/.../minecraft/VersionMetadataDto.kt` — serialization DTOs for `version.json`.
- `launcher-core/src/main/kotlin/.../minecraft/VersionMetadataParser.kt` — metadata-to-domain parser.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftVersionResolver.kt` — selected-version metadata fetch and SHA-1 verification.
- `launcher-core/src/main/kotlin/.../minecraft/LibraryResolver.kt` — OS/rule/native library selection.
- `launcher-core/src/main/kotlin/.../minecraft/AssetIndex.kt` — asset-index model/parser including legacy `map_to_resources` support.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftFilePlan.kt` — download/classpath/native planning.
- `launcher-core/src/main/kotlin/.../minecraft/NativeExtractor.kt` — safe native extraction.
- `launcher-core/src/main/kotlin/.../storage/MinecraftInstallationLayout.kt` — v0.1 game/cache directory layout.
- `launcher-core/src/main/kotlin/.../java/JavaRuntime.kt` — resolved runtime model.
- `launcher-core/src/main/kotlin/.../java/JavaRuntimeManager.kt` — runtime-manager boundary and local runtime inspector.
- `launcher-core/src/main/kotlin/.../launch/LaunchModels.kt` — initial launch request/options models.
- `launcher-core/src/test/kotlin/...` — metadata/rules/files/natives/Java test sources.
- `launcher-desktop/src/main/kotlin/.../Main.kt` — creates and injects `MinecraftVersionResolver`.
- `launcher-desktop/src/main/kotlin/.../ui/SyntaxLauncherApp.kt` — resolves selected version metadata and exposes readiness/error state without adding a fake Play action.
- `docs/ROADMAP.md` — synchronized v0.1 implementation state.
- `docs/WORKLOG.md` — this entry.

### Verification

- Source review — PASS for architecture boundaries, v0.1 scope, metadata/rule semantics, path planning and Zip Slip guard.
- Container repository clone/build attempt — BLOCKED; execution environment cannot resolve `github.com`, so dependencies/repository cannot be fetched for a complete local Gradle run.
- `./gradlew test` — NOT RUN; Gradle wrapper is still not committed and external dependency access is unavailable in the execution environment.
- `./gradlew build` — NOT RUN for the same tooling/network reason.
- Roadmap items that require build/test acceptance therefore remain unchecked or `🚧 IN PROGRESS` rather than being marked complete.

### Roadmap updates

- Selected-version metadata download/parsing, Java requirement parsing, OS/architecture rules and deterministic rule evaluation are now `🚧 IN PROGRESS` pending full build/runtime verification.
- Library/native selection, asset-index parsing/planning and safe native extraction are now `🚧 IN PROGRESS`; actual downloads/integrity/cache reuse remain open.
- `JavaRuntime`, `JavaRuntimeManager`, local runtime inspection, `LaunchOptions` and `LaunchRequest` now exist but runtime installation and final launch planning remain open.
- Desktop version-metadata readiness/error handling is now implemented but remains `🚧 IN PROGRESS` pending full Compose build/manual execution.

### Follow-ups / known issues

- Implement the reusable downloader with atomic partial files, hash verification, bounded concurrency, cancellation, retry and progress reporting.
- Fetch/verify the asset index, plan its objects, and execute client/library/native/asset/log-config downloads.
- Add deterministic native extraction lifecycle after native downloads finish.
- Implement managed Java runtime discovery/download/install using a supported upstream runtime source.
- Build placeholder substitution and final `LaunchPlan`, then process execution and logging.
- Add a Gradle wrapper and perform complete `test`/`build` verification in an environment with dependency access before checking acceptance boxes.

---

## 2026-10-16 — Start v0.1 implementation foundation

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Initialized the Kotlin/Gradle project as two explicit modules: UI-independent `launcher-core` and Compose-based `launcher-desktop`.
- Configured Kotlin/JVM 21, Compose Desktop, Coroutines, Ktor Client, kotlinx.serialization, SLF4J/Logback and Kotlin/JUnit5 test dependencies.
- Added repository `.gitignore` rules for build/IDE files and launcher-managed local/runtime state.
- Added common `Account` boundary and `OfflineAccount` implementation.
- Added Minecraft-style offline username validation and deterministic `OfflinePlayer:<username>` UUID generation.
- Added Mojang version-manifest DTOs, parsing and domain models.
- Added `MinecraftVersionCatalog` backed by the official `version_manifest_v2.json` endpoint.
- Added shared Ktor HTTP client configuration with timeouts, JSON support and SyntaxLauncher User-Agent.
- Added platform-aware launcher path resolution for Windows, macOS and XDG-style Unix systems.
- Added unit-test sources for offline identity, version-manifest parsing and platform path resolution.
- Added the first lightweight Compose Desktop surface with loading/error states, offline nickname validation, release/snapshot selection, Minecraft version selection and RAM control.
- Deliberately did not expose a `Play` button before the Java/game preparation and launch pipeline exists.

### Changed files

- `.gitignore` — added build, IDE and launcher-local-state exclusions.
- `settings.gradle.kts` — initialized multi-module Gradle project and repositories.
- `build.gradle.kts` — configured Kotlin/Compose plugins and project identity.
- `gradle.properties` — configured Gradle/Kotlin defaults.
- `launcher-core/build.gradle.kts` — configured core dependencies and tests.
- `launcher-core/src/main/kotlin/.../account/*` — common/offline account model.
- `launcher-core/src/main/kotlin/.../minecraft/*` — version manifest models, parser and catalog service.
- `launcher-core/src/main/kotlin/.../network/LauncherHttpClientFactory.kt` — shared HTTP configuration.
- `launcher-core/src/main/kotlin/.../storage/LauncherPaths.kt` — platform-aware storage layout.
- `launcher-core/src/test/kotlin/...` — initial deterministic unit-test sources.
- `launcher-desktop/build.gradle.kts` — Compose Desktop module and packaging configuration.
- `launcher-desktop/src/main/kotlin/.../Main.kt` — desktop application entry point.
- `launcher-desktop/src/main/kotlin/.../ui/SyntaxLauncherApp.kt` — first v0.1 UI state/screen.
- `launcher-desktop/src/main/kotlin/.../ui/SyntaxTheme.kt` — initial lightweight dark design tokens.
- `launcher-desktop/src/main/resources/logback.xml` — initial desktop logging backend configuration.
- `README.md` — changed project status from pre-v0.1 to active v0.1 implementation and documented current code.
- `docs/ROADMAP.md` — marked implemented-but-not-yet-fully-verified v0.1 work as `🚧 IN PROGRESS` and left later work open.
- `docs/WORKLOG.md` — recorded this implementation pass.

### Verification

- Pure-core `kotlinc` smoke compilation for `Account.kt`, `OfflineAccount.kt` and `LauncherPaths.kt` — PASS on the available JDK 21 environment.
- `./gradlew test` — NOT RUN; no Gradle wrapper is committed yet, system Gradle is unavailable in the execution environment, and the container cannot resolve external Gradle/Maven downloads.
- `./gradlew build` — NOT RUN for the same environment/tooling reason.
- Manual verification — repository structure, module dependency direction, v0.1 scope and uploaded GitHub sources reviewed after commit.

### Roadmap updates

- Project-foundation items are marked `🚧 IN PROGRESS` where code/configuration exists but full Gradle verification is still pending.
- `.gitignore` is marked complete.
- Offline account model, validation and UUID tasks are marked `🚧 IN PROGRESS` pending full test execution.
- Version-manifest discovery/parsing/filtering items are marked `🚧 IN PROGRESS` pending full build/runtime verification.
- Shared HTTP infrastructure and implemented UI elements are marked `🚧 IN PROGRESS` with remaining scope noted explicitly.
- Java runtime manager, selected-version metadata resolution, game-file preparation, launch planning, process management and the v0.1 quality gates remain unchecked.

### Follow-ups / known issues

- Add a Gradle wrapper and perform the first complete `test`/`build` verification in an environment with dependency access.
- Implement selected Minecraft version metadata resolution next, including libraries/assets/natives/arguments/Java requirement parsing.
- Add safe manifest/settings persistence and cache handling.
- Implement the managed Java Runtime Manager before exposing the Play action.
- Build the launch-plan/process pipeline and then wire RAM/account/version UI state into it.

---

## 2026-10-16 — Define lightweight modern UI/UX direction

**Milestone:** Project foundation / pre-v0.1  
**Author/Agent:** ChatGPT  
**Status:** Completed

### Completed

- Established a mandatory visual direction for SyntaxLauncher: lightweight, highly modern, distinctive and visually attractive without overwhelming the user.
- Defined progressive disclosure as the main mechanism for keeping future feature growth from increasing cognitive load on primary screens.
- Defined visual-weight, hierarchy, typography, color, motion, responsiveness, accessibility and perceived-performance principles.
- Defined `Play` as the dominant primary action for the normal launcher home screen.
- Defined requirements for loading, empty, disabled and error states.
- Defined an incremental Compose design-system approach based on reusable tokens/components without prematurely building a large UI framework.
- Made the design guidelines mandatory for AI/agent contributors working on UI/UX.

### Changed files

- `docs/DESIGN.md` — added the canonical visual and interaction guidelines for SyntaxLauncher.
- `AGENTS.md` — made the design document mandatory reading for UI work and added enforceable UI/UX implementation rules.
- `docs/WORKLOG.md` — recorded this documentation change.

### Verification

- `./gradlew test` — NOT RUN; documentation-only change and the repository still has no Gradle implementation.
- `./gradlew build` — NOT RUN; documentation-only change and the repository still has no Gradle implementation.
- Manual verification — design principles reviewed against the existing staged project scope and v0.1 minimal-launcher goal.

### Roadmap updates

- No implementation checkbox changed state; this task defines a cross-cutting design requirement rather than completing a v0.1 implementation item.
- Future UI roadmap items must be implemented consistently with `docs/DESIGN.md` before they may be considered complete.

### Follow-ups / known issues

- Final brand palette, typography choice and exact component tokens should be established during implementation rather than frozen prematurely in documentation.
- The v0.1 UI should establish the visual foundation while keeping engineering priority on a reliable launch pipeline.

---

## 2026-10-16 — Establish project documentation and release plan

**Milestone:** Project foundation / pre-v0.1  
**Author/Agent:** ChatGPT  
**Status:** Completed

### Completed

- Defined the complete staged development plan from v0.1 through v1.0.
- Defined v0.1 as a deliberately minimal vanilla launcher with offline account, version selection, automatic Java runtime management and game launch.
- Defined instance management before mod support to prevent shared mutable `.minecraft` state.
- Defined official Microsoft premium authentication as v0.3 without coupling it to launch engine internals.
- Defined the staged loader plan: Fabric, NeoForge, Forge and Quilt.
- Defined local mod management followed by Modrinth dependency/update integration.
- Defined OptiFine/shaders/resource-pack handling as instance components rather than forcing everything into the loader abstraction.
- Defined modpack, diagnostics/repair, production-hardening and v1.0 stability milestones.
- Added detailed architecture rules for accounts, Minecraft metadata resolution, Java runtimes, instances, loaders, mods, downloads, diagnostics and error handling.
- Added mandatory AI/agent rules requiring roadmap updates and worklog entries on every repository-changing task.
- Added engineering/development conventions and testing/security expectations.
- Added a canonical detailed checkbox roadmap with milestone-specific quality gates.

### Changed files

- `AGENTS.md` — mandatory workflow for AI/agent contributors, roadmap discipline, worklog format, security/testing/architecture rules.
- `docs/ARCHITECTURE.md` — target architecture and boundaries for current/future launcher systems.
- `docs/ROADMAP.md` — canonical checklist from v0.1 through v1.0.
- `docs/MILESTONES.md` — release-by-release scope, exclusions and acceptance intent.
- `docs/DEVELOPMENT.md` — engineering conventions, repository organization, security, testing and development workflow.
- `docs/WORKLOG.md` — append-only development journal initialized by this entry.
- `README.md` — project entry page and documentation index (updated as part of this documentation bootstrap).

### Verification

- `./gradlew test` — NOT RUN; repository currently contains documentation only and no Gradle project yet.
- `./gradlew build` — NOT RUN; documentation-only change and the repository still has no Gradle implementation.
- Manual verification — documentation structure and cross-document milestone ordering reviewed for consistency.

### Roadmap updates

- Project roadmap created and initialized.
- No implementation milestone item is marked complete because application code has not been created yet.
- Documentation-bootstrap items are tracked separately from functional milestone completion.

### Follow-ups / known issues

- v0.1 implementation has not started.
- The exact supported desktop OS/architecture matrix should be finalized during v0.1 implementation based on chosen Java runtime distribution and Compose packaging constraints.
- Dependency versions are intentionally not frozen in documentation; Gradle files will become authoritative once the project is initialized.
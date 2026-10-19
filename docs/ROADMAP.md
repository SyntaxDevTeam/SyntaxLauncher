# SyntaxLauncher Roadmap

This file is the canonical project progress tracker.

Status rules:

- `- [ ]` — not completed
- `- [ ] 🚧 IN PROGRESS` — partially implemented, not complete
- `- [x]` — implemented and acceptance criteria satisfied

Agents and contributors MUST keep this file synchronized with actual repository state. See `AGENTS.md`.

---

## Cross-milestone desktop development ergonomics (October 2026)

- [x] Embed the semantic product version, CI run number and source SHA at build time; show exact CI build identity in the launcher header and updater without altering release-version comparisons. Local builds have an explicit non-CI label.
- [ ] 🚧 IN PROGRESS — Replace native window decorations with branded controls integrated into the existing Compose header (drag, minimize, maximize/restore, close, 8 resize grips, 400×400 minimum). Source implementation and unit coverage for resize geometry added; real native/manual acceptance on Windows, macOS and Linux X11/Wayland, DPI and small screens remains open.
- [ ] Complete manual usability checks for dragging over navigation, resizing, maximization, multi-monitor behavior and updates on all supported window managers.

---

# v0.1 — Minimal Vanilla Launcher

Goal: launch a selected vanilla Minecraft version using an offline account and an automatically resolved Java runtime.

**Current status:** 🚧 Implementation in progress. The offline identity, Mojang catalog/metadata resolver, verified game preparation, Java manager, launch planner and process manager are now composed into a desktop launch pipeline. Compose exposes a real Play action, per-stage and per-file progress, cancellation, process status/exit code/log paths and a stop action. Window close cancels ongoing preparation and detaches from an already running Minecraft process. Full Gradle build/tests, live Minecraft acceptance, persistence and manifest cache remain open.

## Project foundation

- [ ] 🚧 IN PROGRESS — Gradle Kotlin DSL project structure created; full Gradle build verification is still pending.
- [ ] 🚧 IN PROGRESS — `launcher-core` and `launcher-desktop` module boundaries created; full Gradle verification is still pending.
- [ ] 🚧 IN PROGRESS — Kotlin/JVM toolchain configured for Java 21; full Gradle verification is still pending.
- [ ] 🚧 IN PROGRESS — Compose Desktop configured in `launcher-desktop`; full Gradle verification is still pending.
- [ ] 🚧 IN PROGRESS — Coroutines dependency configured; full Gradle verification is still pending.
- [ ] 🚧 IN PROGRESS — Ktor Client dependencies configured; full Gradle verification is still pending.
- [ ] 🚧 IN PROGRESS — kotlinx.serialization configured; full Gradle verification is still pending.
- [ ] 🚧 IN PROGRESS — SLF4J API and Logback desktop backend configured; file logging/final production policy is still pending.
- [ ] 🚧 IN PROGRESS — Kotlin/JUnit5 test framework configured and test sources expanded; Gradle test execution is pending.
- [x] Add `.gitignore` for IDE/build/runtime/generated files.
- [ ] 🚧 IN PROGRESS — platform-aware data/config/cache/log/runtime path resolution and Minecraft installation layout implemented; lifecycle/integration remains pending.

## Offline account

- [ ] 🚧 IN PROGRESS — common `Account` model implemented; full project verification remains pending.
- [ ] 🚧 IN PROGRESS — `OfflineAccount` implementation added; full project verification remains pending.
- [ ] 🚧 IN PROGRESS — Minecraft-style offline username validation implemented and test source added; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — deterministic `OfflinePlayer:<username>` UUID generation implemented and test source added; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — versioned atomic preferences store now persists the offline nickname without credentials, with unknown-key retention; first full Gradle/GUI acceptance pending.
- [ ] 🚧 IN PROGRESS — offline nickname field and explanatory text are present in the v0.1 UI; final account-state integration remains pending.

## Minecraft version discovery

- [ ] 🚧 IN PROGRESS — official Mojang `version_manifest_v2.json` fetch service implemented; full Gradle/runtime verification remains pending.
- [ ] 🚧 IN PROGRESS — version manifest parser and DTO/domain mapping implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — release/snapshot/legacy type mapping implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — release filtering is implemented in the first Compose UI; full UI build/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — snapshots can be explicitly selected through the channel filter; full UI build/manual verification remains pending.
- [ ] Cache version manifest safely.
- [ ] 🚧 IN PROGRESS — version-catalog load failures produce a visible error and Retry action; typed download/runtime/launch-plan errors now exist but catalog-specific typed errors remain pending.

## Version metadata resolver

- [ ] 🚧 IN PROGRESS — selected version metadata download service implemented and wired to UI; full live/Gradle verification remains pending.
- [ ] 🚧 IN PROGRESS — version id/type parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — client download metadata parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — library/download/classifier metadata parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — asset-index metadata parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — main class parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — modern JVM/game arguments and legacy `minecraftArguments` parsing are now consumed by `LaunchPlanBuilder`; full Gradle/real-launch verification remains pending.
- [ ] 🚧 IN PROGRESS — Java requirement/component metadata parsing is implemented and consumed by the managed runtime manager; full build/live verification remains pending.
- [ ] 🚧 IN PROGRESS — OS/version/architecture and feature rules parsed and normalized; Gradle tests remain pending.
- [ ] Support metadata inheritance where required by supported versions.
- [ ] 🚧 IN PROGRESS — deterministic ordered allow/disallow rule evaluation implemented with test sources and applied before launch argument substitution; Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — representative modern/legacy metadata fixture tests added; full `./gradlew test` remains pending.

## Minecraft files

- [ ] 🚧 IN PROGRESS — `MinecraftPreparationService` now executes the planned client JAR download through the shared verified downloader; Gradle/integration execution remains pending.
- [ ] 🚧 IN PROGRESS — client JAR SHA-1 and size are verified before the `.part` file is atomically published; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — required classpath libraries are selected for the current OS/architecture/rules and planned to cache paths; execution verification remains pending.
- [ ] 🚧 IN PROGRESS — preparation service downloads missing planned libraries through bounded shared infrastructure; Gradle/integration execution remains pending.
- [ ] 🚧 IN PROGRESS — library hashes/sizes are verified by the generic integrity layer before publication; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — native classifier selection including `${arch}` replacement implemented and tested in source; Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — planned native archives are downloaded through the shared verified downloader; Gradle/integration execution remains pending.
- [ ] 🚧 IN PROGRESS — verified native archives are extracted into a regenerated version-specific natives directory with `META-INF` exclusion and Zip Slip protection; full execution remains pending.
- [ ] 🚧 IN PROGRESS — asset index is now included in the executable preparation download plan and verified before parsing; Gradle/integration execution remains pending.
- [ ] 🚧 IN PROGRESS — asset-index parser and Mojang asset-object URL/cache planner are integrated into preparation; duplicate hashes are de-duplicated.
- [ ] 🚧 IN PROGRESS — missing asset objects are planned and downloaded through the shared manager; Gradle/integration execution remains pending.
- [ ] 🚧 IN PROGRESS — verified existing client/libraries/natives/assets are reused without network access; controlled MockEngine test source covers cache reuse but Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — downloads use sibling `.part` files and publish only after successful size/hash verification; interrupted/failed partials are cleaned and never treated as valid cache.

## Download infrastructure

- [ ] 🚧 IN PROGRESS — centralized `DownloadManager` now streams all planned artifacts through the shared Ktor client; full Gradle/runtime verification remains pending.
- [ ] 🚧 IN PROGRESS — structured coroutine cancellation propagates through batches and removes partial files; dedicated cancellation test/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — batch downloads use a coroutine `Semaphore` with configurable bounded parallelism (default 6); Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — typed batch/file start, progress, retry, complete and failure events are exposed to application callers; Compose progress UI is not wired yet.
- [ ] 🚧 IN PROGRESS — retry policy handles transient network failures, HTTP 408/425/429/5xx and integrity/size failures with bounded backoff; controlled retry test source added.
- [ ] 🚧 IN PROGRESS — SHA-1 and expected-size validation is shared for downloaded and cached files; controlled integrity/cache test sources added.
- [ ] 🚧 IN PROGRESS — downloads are written to `.part` and finalized with atomic move where supported, falling back to replace move only after verification; Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — typed network, HTTP, integrity, size, filesystem and conflicting-target download errors are implemented; final UI mapping remains pending.

## Java Runtime Manager

- [ ] 🚧 IN PROGRESS — Java requirement (`component` + `majorVersion`) is parsed from Minecraft metadata and now drives runtime resolution; full Gradle/live verification remains pending.
- [ ] 🚧 IN PROGRESS — `JavaRuntime` now records executable/home/version plus source/component/platform/release identity; full build verification remains pending.
- [ ] 🚧 IN PROGRESS — `JavaRuntimeManager` is wired into `MinecraftLaunchCoordinator`; returned runtime feeds `LaunchRequest` before `ProcessBuilder`. Full live/Gradle verification is pending.
- [ ] 🚧 IN PROGRESS — compatible launcher-managed runtimes are discovered and reused before any runtime-index network call; complete manifest-backed integrity and `java -version` checks guard reuse.
- [ ] 🚧 IN PROGRESS — official Mojang runtime index is used as the distribution source and selected by the Minecraft-declared component plus detected platform; full live platform verification remains pending.
- [ ] 🚧 IN PROGRESS — missing Mojang runtime manifests/files are downloaded through the shared verified `DownloadManager`, including retry/cancellation/progress behavior; Gradle/live execution remains pending.
- [ ] 🚧 IN PROGRESS — runtime manifests and every installed file are checked by size/SHA-1; directories and symlinks are checked on reuse; corrupted/incomplete runtimes are rejected and reinstalled automatically.
- [ ] 🚧 IN PROGRESS — Windows/Linux/macOS Java executable resolution is implemented, including `bin/java.exe`, `bin/java` and macOS `jre.bundle/Contents/Home/bin/java`; full platform execution remains pending.
- [ ] 🚧 IN PROGRESS — staged and published runtimes are executed with `java -version`, parsed for modern/legacy version formats and rejected when the major version differs from Minecraft metadata.
- [ ] 🚧 IN PROGRESS — typed runtime errors distinguish unsupported platform, metadata, manifest, install, verification and Java-version mismatch failures; Compose error mapping remains pending.
- [ ] 🚧 IN PROGRESS — runtime installation uses a staging directory, safe manifest paths, manifest-declared executable flags/symlinks, a completion marker and atomic directory publication where supported.
- [ ] 🚧 IN PROGRESS — controlled MockEngine tests cover target/manifest parsing, initial installation, no-network warm reuse and automatic repair after a managed runtime file is removed; `./gradlew test` execution remains pending.
- [ ] 🚧 IN PROGRESS — runtime models/services remain entirely in `launcher-core` and independent from Compose UI; full build verification remains pending.
- [ ] Document/finalize supported runtime platform matrix; current Mojang mapping covers Windows x86/x64/ARM64, Linux x86/x64 and macOS x64/ARM64 where the requested component exists, while unsupported official combinations fail explicitly.

## Launch planning

- [ ] 🚧 IN PROGRESS — `LaunchOptions` memory bounds are now passed from the Compose RAM control into the real Play pipeline and consumed by `LaunchPlanBuilder`; Gradle/live acceptance remains pending.
- [ ] 🚧 IN PROGRESS — `LaunchRequest` now binds common `Account`, verified `PreparedMinecraftVersion`, resolved `JavaRuntime`, platform, features, options and normalized `LaunchAuthentication`; process-layer integration remains pending.
- [ ] 🚧 IN PROGRESS — classpath is assembled from prepared libraries plus client JAR and substituted with the target platform classpath separator; full Gradle/real-launch verification remains pending.
- [ ] 🚧 IN PROGRESS — Mojang placeholders are substituted explicitly for account/version/game/assets/natives/classpath/library/auth/launcher fields; unknown placeholders fail with a typed launch-plan error instead of reaching Java unresolved.
- [ ] 🚧 IN PROGRESS — Mojang rule filtering for JVM/game arguments is applied before placeholder substitution; full Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — `-Xms`/`-Xmx` are generated from `LaunchOptions` and extra JVM arguments are appended without shell-string concatenation; UI wiring remains pending.
- [ ] 🚧 IN PROGRESS — `${assets_root}`, `${assets_index_name}` and legacy `${game_assets}` values are derived from prepared asset state; legacy virtual/map-to-resources materialization remains a separate compatibility limitation.
- [ ] 🚧 IN PROGRESS — `${natives_directory}` is bound to the version-specific prepared native directory; full launch execution remains pending.
- [ ] 🚧 IN PROGRESS — `${game_directory}` is bound to the platform-aware prepared game directory; v0.2 will later replace this global v0.1 directory with instance state.
- [ ] 🚧 IN PROGRESS — offline username/UUID/session/access token/user type are normalized through `LaunchAuthentication.offline()` without hardwiring raw nickname handling throughout the builder; premium credentials can later use the same boundary.
- [ ] 🚧 IN PROGRESS — metadata logging argument `${path}` is bound to the verified downloaded logging configuration when present.
- [ ] 🚧 IN PROGRESS — legacy versions without metadata JVM arguments receive explicit natives path and classpath fallback arguments rather than a version-specific hardcoded launch recipe.
- [ ] 🚧 IN PROGRESS — structured `LaunchPlan` now directly feeds `MinecraftProcessManager` through `MinecraftLaunchCoordinator`; compilation/full real-Minecraft launch acceptance remains pending.
- [ ] 🚧 IN PROGRESS — deterministic unit-test sources cover modern placeholder construction, memory/logging/classpath/offline identity, legacy JVM fallback, arguments containing spaces and rejection of unknown placeholders; `./gradlew test` execution remains pending.

## Process management

- [ ] 🚧 IN PROGRESS — `MinecraftProcessManager` starts `LaunchPlan.command` with `ProcessBuilder` in the prepared game working directory; full Gradle and real Minecraft launch verification remain pending.
- [ ] 🚧 IN PROGRESS — each launch creates a distinct Minecraft run directory under launcher logs with separate redirected `stdout.log` and `stderr.log`; POSIX log files use owner-only permissions and native redirects survive launcher detach.
- [ ] 🚧 IN PROGRESS — `StateFlow<MinecraftProcessStatus>` exposes IDLE/STARTING/RUNNING/STOPPED/CRASHED/DETACHED, PID, start/end timestamps, log paths, actionable failure message and exit code; Compose presentation remains pending.
- [ ] 🚧 IN PROGRESS — non-zero natural exits become CRASHED and zero exits STOPPED; requested termination becomes STOPPED even when the OS exit code is non-zero; full integration verification remains pending.
- [ ] 🚧 IN PROGRESS — mutex-guarded process ownership rejects duplicate launches until the previous process exit is observed; controlled test source added.
- [ ] 🚧 IN PROGRESS — explicit `terminate(gracePeriodMillis)` first requests graceful termination and then forces termination if necessary; cancellation and OS-specific edge cases still need live acceptance.
- [ ] 🚧 IN PROGRESS — explicit `shutdown(DETACH | TERMINATE)` policy defines launcher close behavior; DETACH leaves Minecraft alive with stdout/stderr redirected to files; desktop window-close integration remains pending.
- [ ] 🚧 IN PROGRESS — typed process startup, duplicate-launch, closed-manager and forced-termination failures are defined without logging a full launch command or authentication arguments; full Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — five process-manager test scenarios cover a real JDK subprocess, separate stdout/stderr logs, duplicate protection, crashes and relaunch, force-stop, detach, and retry after startup failure; Gradle/JUnit execution remains pending.
- [ ] 🚧 IN PROGRESS — Compose observes process `StateFlow` and displays PID, exit code, errors and process log directory; Stop calls `terminate()`. Window close uses DETACH after canceling ongoing downloads; full UI lifecycle testing pending.

## v0.1 UI

- [x] Make the launcher logo respond to the measured hero/text space and original asset aspect ratio: reserve separate text/logo areas, reflow below the heading in narrow heroes, and verify 27 resize/density cases without cropping, distortion or text collisions (19 October 2026).

- [x] Match the supplied neon concept with a detailed voxel landscape, independently fitted transparent block/arrow, subtle separation shadow/halo, ink-blue panels, shared cyan vector icons, compact controls and correctly disabled Play styling. Local `test build` and Compose render review passed on 19 October 2026.
- [x] Add deterministic headless home rendering/interaction checks for 1440×810, 1024×640, 440×740, 400×400 and 150% scaling; validate nickname/Play state and access to settings by scrolling. Native keyboard/focus and other-platform acceptance remain separate open gates.

- [ ] 🚧 IN PROGRESS — reference-based cinematic scenery and independent transparent mark, Home/Instances interactions and high-contrast Play treatment now pass local build/tests and headless render review. Native desktop, keyboard/focus and cross-platform visual acceptance remain open.

- [ ] 🚧 IN PROGRESS — voxel valley, readable scrims and independently fitted blue foreground mark implemented; short/narrow layouts and 150% scaling pass deterministic render checks. Native OS/DPI acceptance remains pending.

- [ ] 🚧 IN PROGRESS — blue voxel/arrow mark integrated into desktop header/window; updated ink-blue/cyan theme and existing Linux package icon configured. Local compilation passes; package/native visual acceptance of this revision remains pending.

- [ ] 🚧 IN PROGRESS — v0.1 Compose home redesigned around a high-contrast atmospheric hero, prominent contextual Play CTA, compact profile/version/RAM settings, branded header, semantic status and progressive error details; full platform CI and manual UX/accessibility review pending.
- [ ] 🚧 IN PROGRESS — shared ink-blue/cyan roles, 18 dp panels, 9 dp controls, typography and vector components implemented; deterministic resizing/150% rendering passes. Native focus/accessibility and platform DPI evaluation still pending.

- [ ] 🚧 IN PROGRESS — offline nickname field validates username and feeds `OfflineAccount.create()` for the real Play pipeline; persistence/full UI verification remains pending.
- [ ] 🚧 IN PROGRESS — Minecraft version selector and release/snapshot filters are driven by the official Mojang catalog, with selected metadata verified asynchronously; fixture-based Compose rendering passes, native live-catalog acceptance remains pending.
- [ ] 🚧 IN PROGRESS — RAM slider feeds `LaunchOptions(maximumMemoryMiB)` on Play; persistence/full UI verification remains pending.
- [ ] 🚧 IN PROGRESS — real `Play Minecraft` action now runs prepared metadata → verified game downloads/assets/natives → managed Java → `LaunchPlanBuilder` → `MinecraftProcessManager`; live vanilla launch acceptance is pending.
- [ ] 🚧 IN PROGRESS — `MinecraftLaunchCoordinator` exposes preparation phases, per-file progress and completion counts through `StateFlow`; Compose displays status and progress and offers cancellation.
- [ ] 🚧 IN PROGRESS — catalog/metadata errors have retry actions; pipeline preparation/Java/launch errors and process crashes display human-oriented messages and log paths; detailed error mapping/manual verification remain open.
- [ ] 🚧 IN PROGRESS — nickname/version/channel/RAM controls and Play are disabled during preparation or while the game runs; duplicate launch is also guarded in the session/core.
- [ ] 🚧 IN PROGRESS — `DesktopLauncherSession` owns application-scope jobs, cancels outstanding preparation on window close, shuts down process manager with DETACH and closes the shared Ktor client; cross-platform close acceptance is pending.
- [ ] 🚧 IN PROGRESS — the UI exposes a `Stop Minecraft` action and running/stopped/crashed/exit-code indicators from ProcessManager; real-game/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — Compose restores and debounces storage of nickname, selected version, release/snapshot channel and RAM via atomic schema-versioned properties; tests and desktop acceptance pending.
- [ ] Cache version manifest to support startup when Mojang is unreachable.
- [ ] Verify launcher UI layout, long progress text and keyboard/focus behavior at supported window sizes and scaling.


## Build and CI validation

- [x] Publish all three portable applications to Build Explorer v2 using a project-scoped CI secret; main run 39290313808 passed, all three public downloads verified by SHA-256.

- [x] GitHub Actions run 39125171338 passed `:launcher-core:test` and desktop Kotlin compilation with pinned Gradle 9.4.0 and Temurin JDK 21 on Linux.
- [x] GitHub Actions run 39125171338 built Compose portable app images for Linux x64, Windows x64 and macOS ARM64, and published permission-preserving platform tar.gz preview artifacts.
- [x] Official Gradle wrapper 9.4.0 (`gradlew`, `gradlew.bat`, properties, official JAR) generated, executed and committed by a restricted bootstrap GitHub Actions workflow (03d2650).
- [x] Identified and repaired initial CI blockers: missing Google Maven repositories, Kotlin Path classpath concatenation, desktop Ktor dependency, malformed test fixtures, and macOS app-version jpackage constraint.
- [ ] 🚧 IN PROGRESS — portable artifacts have CI-proven packaging but await manual extraction, startup/operation and signed native installer packaging where appropriate.
- [ ] Validate a clean-machine vanilla launch and documented OS/architecture matrix before declaring v0.1 usable as a public release.
- [ ] Verify the packaged launcher can connect to Mojang over HTTPS using the explicitly included `jdk.crypto.ec` runtime module.

## v0.1 quality gate

- [ ] 🚧 IN PROGRESS — GitHub Actions passes `:launcher-core:test`; full `./gradlew test` on development/supported OS hosts is still pending.
- [ ] 🚧 IN PROGRESS — desktop compilation and portable packaging passed in CI; full project `./gradlew build` and local reproduction are still pending.
- [ ] Fresh launcher state can download prerequisites and start a supported vanilla Minecraft release.
- [ ] Second launch reuses valid cached data instead of redownloading everything.
- [ ] Offline identity remains stable for the same username.
- [ ] Java is selected automatically without requiring a globally installed Java when managed runtime installation succeeds.
- [ ] No premium authentication/mod/loader feature has leaked into v0.1 scope unnecessarily.

---

# v0.2 — Instance Manager

Goal: separate user installations into isolated, manageable Minecraft instances before introducing mods.

**Status:** 🚧 PREVIEW / RELEASE VALIDATION — schema-versioned instance repository, guarded CRUD, SHA-256 verified clone/import, in-app restore, dedicated adaptive Instances workspace and JVM child-process A/B isolation regression implemented. Real Minecraft gameplay/world data, interrupt and GUI visual acceptance must still be performed before v0.2 is marked complete.

**Detailed execution phases and acceptance criteria:** [INSTANCE_MANAGER_PHASES.md](INSTANCE_MANAGER_PHASES.md) — Track A (separate game instances) and Track B (instance management).

## v0.2 delivery phases — prioritized work remaining

- [ ] 🚧 IN PROGRESS — **A2 / P0 — Real-world isolation:** filesystem/repository tests plus a real Java child-process fixture now verify per-instance working directories, worlds/options isolation and shared cache location through the launch coordinator. Actual Mojang Minecraft GUI/gameplay still requires manual acceptance.
- [ ] 🚧 IN PROGRESS — **A3 / P0 — Legacy import completion:** source inspection, disk-space preflight, cancellable streamed copy, SHA-256 read-back verification and stage rollback wired to UI. Real-world large-save import and disk-full/permission acceptance pending.
- [ ] 🚧 IN PROGRESS — **A4 / P0 — Data protection:** added per-profile OS file locks across the entire mutation (not just PID preflight) to serialize separate launcher processes; protected edit/create/clone/import/trash/restore and preparation. Deterministic duplicate-repository tests added; actual detached-game and Windows filesystem acceptance remain open.
- [ ] 🚧 IN PROGRESS — **B1 / P0 — Selection UX acceptance:** Legacy Vanilla settings remain independent, UUID selections are persisted, duplicates show short UUID, and missing game directories are reported rather than recreated; manual restart UX acceptance pending.
- [ ] 🚧 IN PROGRESS — **B2 / P0 — Settings editor:** dedicated Save/Cancel editor for name, version, min/max RAM and advanced JVM arguments added; version-change/world downgrade warning and form validation included. CI and manual persistence/cancel/downgrade acceptance pending.
- [ ] 🚧 IN PROGRESS — **B3 / P0 — Safe clone/trash/restore:** in-app trash listing/restore, UUID-conflict protection and automated round-trip tests implemented; CI and real-world recovery acceptance pending. Clone/import progress and cancellation remain open.
- [ ] 🚧 IN PROGRESS — **B4 / P1 — Instance workspace:** responsive single/dual-column layout handles narrow desktop windows, search, inventory/details and operation statuses; manual focus/DPI visual review remains open.
- [ ] **Final acceptance / P0:** automated regression suite, three platform distributions, real A/B world isolation, non-destructive legacy import, clone/restore and v0.1 compatibility.



## Instance model

- [x] `MinecraftInstance` domain model with canonical UUID and validated display name; verified in core tests.
- [x] Schema-v1 instance metadata stores Minecraft version, RAM and optional JVM arguments; tested.
- [x] Instance game path derives from canonical UUID instead of user name; tested.
- [x] Five repository tests for persistence/invalid metadata, cloning, isolation and legacy import passed in CI 39157681455.

## Instance storage

- [x] Separate UUID-scoped `game` directories for mutable saves/configurations/resource packs; filesystem tests passed.
- [x] Shared versions, libraries and assets cache paths are preserved by the per-instance layout; unit assertions passed.
- [x] Atomic per-instance `instance.json` publication with explicit errors for corrupt/newer metadata; CI tests passed.
- [ ] 🚧 IN PROGRESS — schema 1 read/write supports unknown-field preservation; later schema migrations remain to be designed/tested.
- [ ] 🚧 IN PROGRESS — explicit non-destructive import copies the v0.1 game folder while leaving source untouched.
- [ ] 🚧 IN PROGRESS — cloning rejects symlinks/special files, reports preflight size and streamed copy progress, stages data atomically without reusing abandoned staging, and preserves unknown metadata; real-world tests remain open.

## Instance management

- [ ] 🚧 IN PROGRESS — create, rename, edit instance version/RAM/JVM settings, list alphabetically, clone worlds and move to recoverable trash.
- [x] Selected instance UUID persists as an optional schema-1 preferences key, compatible with older settings; CI suite passed.
- [ ] 🚧 IN PROGRESS — deletion uses `data/instance-trash/`, with in-app restore and original UUID preservation; CI/manual restore verification remains pending.

## Launch integration

- [x] Selected instance overrides the existing launch pipeline's game directory without duplicating the launch engine; integration test passed.
- [ ] 🚧 IN PROGRESS — per-instance min/max RAM and extra JVM arguments override launcher defaults when an instance is active.
- [ ] 🚧 IN PROGRESS — the v0.1 Legacy Vanilla profile remains available with its original game folder.
- [ ] Manual acceptance: launch two profiles with the same Minecraft version and confirm independent worlds/configurations.

## v0.2 UI

- [x] Add a home instance dropdown using the existing guarded selection path for Legacy Vanilla and UUID profiles; a deterministic Compose interaction test verifies profile/version/RAM display without opening the manager.

- [ ] 🚧 IN PROGRESS — instance selector/manager added to Compose home without changing primary Play CTA.
- [ ] 🚧 IN PROGRESS — create, rename, clone, explicit legacy import, dedicated per-instance settings editor and confirm delete/restore actions; cross-platform and UX acceptance pending.
- [ ] 🚧 IN PROGRESS — corrupt/missing saved instances are explicitly reported and cannot silently redirect Play to Legacy Vanilla.
- [ ] Preview UX review (window size, instance manager dialog, large folders and keyboard focus) pending.

## v0.2 quality gate

- [ ] Existing v0.1 vanilla launching remains functional after v0.2 integration.
- [ ] Two instances of the same Minecraft version keep mutable game state isolated.
- [ ] Instance clone preserves intended data/settings, including real worlds.
- [x] Instance metadata round-trip tests pass in CI 39157681455.
- [ ] Schema migration mechanism beyond v1 exists before adding incompatible fields.

---

# v0.3 — Microsoft / Premium Accounts

Goal: add official premium authentication without changing the fundamental Minecraft launch pipeline.

**Status:** 🚧 IN PROGRESS / EXTERNAL BLOCKER — Microsoft OAuth, Xbox/XSTS and Minecraft Services exchanges are implemented and mock-tested, but **a new Microsoft Entra Client ID is not automatically authorized by Minecraft Services**. The project must separately submit its own AppID for Minecraft/Mojang approval/allowlisting. Until explicit confirmation and a real `login_with_xbox` test, authenticated Minecraft Java login and full v0.3 release are **BLOCKED**. The maintainer's one-time v0.2 acceptance waiver is unrelated to this external dependency.

## External prerequisite — Minecraft Services AppID authorization

- [ ] **P0 / BLOCKED — Register SyntaxLauncher's own native/public Microsoft Entra app** and configure its authorized `http://localhost/callback` redirect. Do not reuse another launcher's Client ID or embed a client secret.
- [ ] **P0 / BLOCKED — Submit SyntaxLauncher Client ID for Minecraft/Mojang AppID approval (allowlist).** Microsoft Entra registration alone is insufficient. Developers report `HTTP 403 Invalid app registration` at `POST /authentication/login_with_xbox` even when OAuth/Xbox/XSTS work. Check the Minecraft support page `https://help.minecraft.net/hc/en-us/articles/16254801392141` and the linked current submission process. A form reported by a developer in August 2026 was `https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR-ajEQ1td1ROpz00KtS8Gd5UNVpPTkVLNFVROVQxNkdRMEtXVjNQQjdXVC4u`; verify the current form through Minecraft support before submitting.
- [ ] **P0 / BLOCKED — Obtain and record explicit approval from Mojang/Minecraft** for the project's Client ID, then perform a real successful Minecraft Services token and Java profile retrieval test.
- [ ] Keep Xbox/XSTS/Minecraft provider implementations testable with mocked HTTP responses while approval is pending; this does **not** constitute live authentication success.

## Account management

- [ ] Introduce account manager/service.
- [ ] Preserve offline account support.
- [x] Add a verified premium account type and token-safe session class; tested.
- [ ] Support multiple saved accounts.
- [ ] Allow account selection per launch or as launcher default.
- [ ] Display account type clearly.

## Delivery phases

- [ ] **P0 — Authentication foundation:** typed premium identity, nonce/state + S256 PKCE, OAuth code exchange, Xbox/XSTS/Minecraft profile verification, mocked tests.
- [ ] **P0 — Desktop sign-in:** system browser with loopback callback and CSRF validation, loading/cancel/error states, configurable registered client ID.
- [ ] **P0 — Session security:** token redaction, secure OS-backed refresh credential persistence, refresh and sign-out.
- [ ] **P0 — Launch integration:** selected verified account supplies Minecraft token without an alternate launcher pipeline.
- [ ] **P1 — Account workspace:** multiple stored offline/Microsoft accounts, selection and account identity UI.
- [ ] **Release acceptance:** approved Microsoft app registration, live sign-in/refresh and premium Minecraft game launch.

## Microsoft authentication

- [ ] 🚧 IN PROGRESS — native public-client OAuth request/code exchange and refresh implemented; localhost browser callback UI remains.
- [x] Implement S256 PKCE, cryptographic CSRF state and unique request verifier; tested.
- [ ] Never request/store Microsoft password.
- [x] Implement Xbox user-token exchange to Microsoft OAuth token, covered by MockEngine integration test.
- [x] Implement Xbox XSTS auth with Minecraft relying party, covered by MockEngine.
- [ ] 🚧 IN PROGRESS — login_with_xbox implemented with typed HTTP-403 app-registration errors; only mocked tests so far.
- [x] Fetch and parse verified Minecraft Java profile (mock-tested).
- [x] Parse Minecraft Java profile name and compact UUID (mock-tested).
- [x] Classify Xbox, XSTS, Minecraft-app-registration and missing Java profile failures without exposing tokens.

## Token lifecycle

- [ ] 🚧 IN PROGRESS — premium LaunchPlan now flags credential-bearing sessions and default process starter discards stdout/stderr rather than persisting raw tokens. Game-owned logs and crash reports still require security review.

- [ ] Refresh expired/expiring sessions automatically when possible.
- [ ] Store long-lived secrets/tokens using an appropriate secure OS mechanism.
- [ ] Redact tokens from logs and exceptions.
- [ ] Support sign-out/revoke-local-session behavior.
- [ ] Handle refresh failure by requesting authentication again.

## Launch integration

- [ ] Convert authenticated account/session to existing launch account/session inputs.
- [ ] 🚧 IN PROGRESS — coordinator accepts normalized premium LaunchAuthentication and uses standard metadata placeholder replacement; desktop selection and live verification pending.
- [x] Both account types use MinecraftLaunchCoordinator with a normalized LaunchAuthentication.

## v0.3 UI

- [ ] 🚧 IN PROGRESS — choose Offline or a validated Microsoft session in the home sidebar (memory only); persistence and full UX acceptance pending.
- [ ] 🚧 IN PROGRESS — native browser / localhost PKCE flow wired to Add Microsoft when an owned app ID is configured; real sign-in pending.
- [x] Offline profile remains independently selectable and used by the original launch path.
- [ ] Show premium profile name/avatar when available.
- [ ] 🚧 IN PROGRESS — Sign out removes selected premium session from RAM; persistent vault integration/revocation pending.

## v0.3 manual and security acceptance

- [ ] Follow [TESTING_V0.3.md](TESTING_V0.3.md), including live consent, secure persistence decision, browser cancellation and real online-mode Minecraft gameplay.

## v0.3 quality gate

- [ ] Offline launching still works.
- [ ] Premium account can authenticate through official flow.
- [ ] Premium account can launch Minecraft with authenticated session.
- [ ] Tokens do not appear in normal logs.
- [ ] Session refresh works or fails with a clear re-authentication path.

---

# v0.4 — Fabric Loader

Goal: introduce the first mod loader and validate a reusable loader architecture.

## Loader abstraction

- [ ] Introduce loader/provider abstraction only now or when Fabric implementation requires it.
- [ ] Define loader id/version models.
- [ ] Define installed loader metadata stored in instance configuration.
- [ ] Keep loader-specific resolution separate from generic Minecraft launch logic.

## Fabric

- [ ] Fetch compatible Fabric loader versions for selected Minecraft version.
- [ ] Install Fabric metadata/libraries into an instance using official metadata sources.
- [ ] Resolve Fabric launch metadata correctly.
- [ ] Launch Fabric instance through common launch pipeline.
- [ ] Support loader version selection/change.
- [ ] Validate compatibility with selected Minecraft version.

## v0.4 UI

- [ ] Loader selection during instance creation/editing.
- [ ] Vanilla remains an explicit option.
- [ ] Fabric version selector.
- [ ] Clear incompatible-version feedback.

## v0.4 quality gate

- [ ] Existing vanilla instances remain unchanged.
- [ ] Fabric installation is instance-scoped.
- [ ] Fabric instance launches without duplicated launch engine.
- [ ] Loader abstraction is usable by a future second loader without Fabric-specific assumptions in core.

---

# v0.5 — Local Mod Manager

Goal: manage local mod JARs safely before adding online repositories.

## Mod model

- [ ] Add local mod representation.
- [ ] Track enabled/disabled state.
- [ ] Track file path/hash.
- [ ] Parse available mod metadata formats relevant to supported loaders.
- [ ] Expose mod id/name/version when available.
- [ ] Detect duplicate mod ids/files where practical.

## Local mod operations

- [ ] Add/import mod JAR into instance.
- [ ] Remove mod.
- [ ] Enable/disable mod without deleting it.
- [ ] Show installed mods.
- [ ] Surface basic compatibility/dependency metadata.
- [ ] Avoid modifying mods in other instances.

## Dependency awareness

- [ ] Detect obviously missing declared dependencies where metadata provides them.
- [ ] Detect obvious declared incompatibilities/conflicts where practical.
- [ ] Present warnings before launch.
- [ ] Do not pretend local-only dependency resolution is complete when repository data is unavailable.

## v0.5 quality gate

- [ ] Mod enable/disable is reversible.
- [ ] Mod state persists per instance.
- [ ] Fabric instance can launch with locally managed mods.
- [ ] Vanilla instance behavior remains unaffected.

---

# v0.6 — Modrinth Integration

Goal: search, install, update and resolve dependencies for compatible Modrinth content.

## Modrinth client

- [ ] Add isolated Modrinth API adapter.
- [ ] Search projects.
- [ ] Fetch project/version metadata.
- [ ] Filter versions by Minecraft version.
- [ ] Filter versions by loader.
- [ ] Filter for client-side compatibility/environment where relevant.
- [ ] Respect API usage/rate-limit expectations.

## Installation

- [ ] Build installation plan before mutating instance.
- [ ] Download selected compatible version.
- [ ] Verify hashes where provided.
- [ ] Install required dependencies automatically with user-visible plan.
- [ ] Handle optional dependencies explicitly.
- [ ] Detect dependency conflicts.
- [ ] Persist Modrinth project/version identity for future updates.

## Updates

- [ ] Detect available compatible updates.
- [ ] Update one mod.
- [ ] Update all compatible mods.
- [ ] Preserve ability to roll back or avoid destructive replacement where practical.
- [ ] Clearly report mods that cannot be matched to Modrinth.

## v0.6 UI

- [ ] Modrinth search/browser.
- [ ] Compatibility-filtered install button.
- [ ] Dependency plan confirmation.
- [ ] Update indicators.
- [ ] Bulk update action.

## v0.6 quality gate

- [ ] Incompatible loader/Minecraft versions are not silently installed.
- [ ] Required dependencies are resolved deterministically or produce an actionable conflict.
- [ ] Local mods remain supported.
- [ ] Repository-specific logic does not leak into generic mod manager unnecessarily.

---

# v0.7 — NeoForge

Goal: add a second major loader and prove the loader architecture is genuinely reusable.

## NeoForge provider

- [ ] Discover compatible NeoForge versions.
- [ ] Parse/install NeoForge metadata/processors as required.
- [ ] Resolve required libraries.
- [ ] Integrate generated/processed artifacts safely.
- [ ] Store NeoForge loader state in instance metadata.
- [ ] Launch through common launch pipeline.

## Compatibility

- [ ] Filter Modrinth content for NeoForge.
- [ ] Parse relevant NeoForge mod metadata in local manager.
- [ ] Detect common incompatible loader combinations.

## v0.7 quality gate

- [ ] Fabric implementation requires no regression-causing redesign.
- [ ] NeoForge can be added through loader adapter boundaries.
- [ ] Vanilla/Fabric/NeoForge instances coexist independently.

---

# v0.8 — Forge and Quilt

Goal: broaden loader support while preserving historical-version compatibility deliberately.

## Forge

- [ ] Discover compatible Forge versions.
- [ ] Support required Forge installation metadata/processors for targeted Minecraft ranges.
- [ ] Document exact supported Minecraft/Forge range if universal support is not practical.
- [ ] Launch Forge through common pipeline.
- [ ] Integrate Forge mod metadata handling.
- [ ] Integrate Modrinth Forge filtering.

## Quilt

- [ ] Discover compatible Quilt versions.
- [ ] Install Quilt metadata/libraries.
- [ ] Launch Quilt through common pipeline.
- [ ] Parse Quilt mod metadata where relevant.
- [ ] Integrate Modrinth Quilt filtering.

## Historical compatibility

- [ ] Define policy for legacy Minecraft Java requirements.
- [ ] Test representative old/new Forge-era versions.
- [ ] Handle legacy argument/metadata differences through resolvers rather than UI hacks.

## v0.8 quality gate

- [ ] Vanilla/Fabric/NeoForge/Forge/Quilt use one launcher core.
- [ ] Loader-specific behavior stays in adapters.
- [ ] Supported version matrix is documented and tested.

---

# v0.9 — OptiFine, Shaders and Resource Packs

Goal: manage graphics-related instance content without treating every component as a generic mod loader.

## OptiFine

- [ ] Define OptiFine integration strategy compatible with licensing/distribution constraints.
- [ ] Detect compatible OptiFine versions where technically and legally possible.
- [ ] Install/integrate OptiFine without corrupting loader abstraction.
- [ ] Clearly communicate unsupported combinations.

## Shaders

- [ ] Manage shader packs per instance.
- [ ] Import/remove shader packs.
- [ ] Track enabled/selected shader where integration permits.
- [ ] Surface renderer requirements such as Iris/OptiFine where relevant.

## Resource packs

- [ ] Manage resource packs per instance.
- [ ] Import/remove packs.
- [ ] Show metadata/icon where available.
- [ ] Preserve order/selection if launcher chooses to manage it.

## v0.9 quality gate

- [ ] Graphics integrations remain instance-scoped.
- [ ] OptiFine does not become a fake generic loader abstraction that breaks other loaders.
- [ ] Existing mod management remains intact.

---

# v0.10 — Modpacks

Goal: install/export repeatable instance definitions and support a mainstream modpack format.

## Modpack model

- [ ] Define modpack installation plan.
- [ ] Map modpack manifest to instance creation.
- [ ] Preserve overrides/configs appropriately.
- [ ] Verify downloaded files.
- [ ] Keep user-created mutable data safe on update/reinstall.

## Modrinth modpacks

- [ ] Import `.mrpack`.
- [ ] Resolve Minecraft version and loader.
- [ ] Install referenced files/dependencies.
- [ ] Apply overrides.
- [ ] Create a new isolated instance.
- [ ] Report unsupported/invalid pack content clearly.

## Export

- [ ] Export compatible instance definition where feasible.
- [ ] Exclude files that must not be redistributed.
- [ ] Preserve metadata needed for deterministic reconstruction.

## v0.10 quality gate

- [ ] A supported pack installs into a new instance from scratch.
- [ ] Pack installation does not overwrite unrelated instances.
- [ ] Invalid/incomplete packs fail without leaving a falsely completed instance.

---

# v0.11 — Diagnostics and Repair

Goal: turn raw logs/exit codes into actionable launcher diagnostics.

## Evidence collection

- [ ] Collect launch plan summary without secrets.
- [ ] Capture selected Java runtime/version.
- [ ] Capture Minecraft/loader version.
- [ ] Capture installed mod inventory and versions.
- [ ] Capture process exit code.
- [ ] Parse Minecraft latest log where available.
- [ ] Parse crash reports where available.

## Diagnostic engine

- [ ] Define structured diagnostic findings.
- [ ] Detect missing Java/runtime failures.
- [ ] Detect corrupted/missing launcher-managed files.
- [ ] Detect common loader/mod dependency failures.
- [ ] Detect common incompatible mod combinations where evidence is sufficient.
- [ ] Distinguish probable cause from confirmed cause.
- [ ] Avoid claiming certainty from weak evidence.

## Repair

- [ ] Verify/repair Minecraft managed files.
- [ ] Re-download corrupt assets/libraries.
- [ ] Repair/reinstall managed runtime where safe.
- [ ] Offer mod update/disable suggestions when supported by evidence.
- [ ] Require explicit user action for destructive repairs.

## v0.11 UI

- [ ] Human-readable crash summary.
- [ ] Technical details/log access.
- [ ] Suggested actions.
- [ ] One-click safe repair actions where available.

## v0.11 quality gate

- [ ] Diagnostics never expose access/refresh tokens.
- [ ] Repair actions are scoped and explain what they will change.
- [ ] Generic `Exit code 1` is supplemented by more useful evidence when available.

---

# v0.12 — Production Hardening

Goal: prepare the launcher for stable public release.

## Self-update

**Explicit early delivery request (19 October 2026):** implement this v0.12 slice during current launcher development. Other v0.12 features and existing release gates remain unchanged. See [UPDATING.md](UPDATING.md).

- [x] Define update channel strategy: public stable GitHub Releases from this project; SemVer ordering excludes drafts/prereleases and prevents automatic downgrade.
- [x] Implement pre-home packaged startup checks, bounded network checks and Retry/Continue UI; development runs bypass execution. Mocked HTTP and Compose receipt/recovery tests pass.
- [x] Download through the shared client/downloader with SHA-256, length verification, cancellation, retries, `.part` staging and cached archive revalidation.
- [x] Verify GitHub release asset digest and per-file manifest SHA-256; reject wrong platform/version, unsafe paths/links, duplicate/extra ZIP entries and oversized packages.
- [x] Install app images beside the original, reuse verified images, repair into a fresh stage and atomically publish installation/state; a matching child startup receipt activates the image. Local Linux full-bundle extraction/revalidation passes.
- [x] Preserve active/previous state and original executable, terminate failed/timed-out children and fall back to a verified cached application; tested with a real Java child plus failure/cancellation/offline fixtures.
- [x] Generate consistent semantic build metadata, manifest-driven update ZIPs and an explicit full-bundle verification task; Python fixture tests pass.
- [ ] 🚧 IN PROGRESS — CI now runs desktop tests and packages/verifies all three update bundles, then publishes complete six-asset releases only for version tags. Hosted workflow/tag publication and native Windows/macOS acceptance are not yet executed for this change.
- [ ] Live old→new GitHub-hosted update, original-shortcut relaunch, offline/cancel/failed-child recovery, native permissions/trust and user-data continuity on Windows/Linux/macOS.
- [ ] Release signing/notarization and cache/old-version cleanup policy before production distribution; current integrity trust is the project's HTTPS GitHub publishing account.

## Reliability

- [ ] Integrity scan for launcher-managed files.
- [ ] Repair workflow for cache corruption.
- [ ] Robust download resume/retry behavior.
- [ ] Graceful cancellation.
- [ ] Network timeout tuning.
- [ ] Proxy support if required by release scope.
- [ ] Disk-space checks for large installs.
- [ ] Safe handling of interrupted installations.

## Packaging

- [ ] Windows packaging/install strategy.
- [ ] Linux packaging strategy.
- [ ] macOS packaging strategy.
- [ ] Application icon/metadata.
- [ ] First-run experience.
- [ ] Uninstall/data-retention behavior documented.

## CI/CD

- [ ] Build/test CI.
- [ ] Cross-platform build validation.
- [ ] Release artifact generation.
- [ ] Version/tag automation.
- [ ] Release notes generation/process.
- [ ] Reproducible/versioned dependency handling.

## UX polish

- [ ] Global settings screen.
- [ ] Download queue/status.
- [ ] Better empty/loading/error states.
- [ ] Accessible keyboard/focus behavior where practical.
- [ ] Consistent notifications/errors.
- [ ] Log viewer/export.

## v0.12 quality gate

- [ ] Clean installation works on supported operating systems.
- [ ] Upgrade from an earlier supported build preserves user instances/accounts/settings appropriately.
- [ ] CI produces tested release artifacts.
- [ ] Interrupted downloads/updates do not silently corrupt launcher state.

---

# v1.0 — Stable Release

Goal: declare the architecture and primary feature set stable for end users.

## Supported feature set

- [ ] Offline accounts.
- [ ] Microsoft premium accounts.
- [ ] Vanilla Minecraft versions within documented support matrix.
- [ ] Automatic Java runtime management.
- [ ] Isolated instances.
- [ ] Fabric.
- [ ] NeoForge.
- [ ] Forge.
- [ ] Quilt.
- [ ] Local mod management.
- [ ] Modrinth search/install/update/dependency resolution.
- [ ] OptiFine integration within documented compatibility limits.
- [ ] Shader management.
- [ ] Resource-pack management.
- [ ] Supported modpack import/export flow.
- [ ] Diagnostics and repair.
- [ ] Launcher self-update.
- [ ] Windows/Linux/macOS support according to published matrix.

## Documentation and release readiness

- [ ] README reflects actual v1.0 behavior.
- [ ] Architecture documentation matches code.
- [ ] User installation/use documentation exists.
- [ ] Authentication/security behavior is documented.
- [ ] Supported Minecraft/loader/platform matrix is published.
- [ ] Known limitations are documented.
- [ ] Migration path from pre-1.0 launcher state is tested/documented.
- [ ] License/third-party notices are correct.
- [ ] Release notes are prepared.

## v1.0 quality gate

- [ ] Full automated test suite passes.
- [ ] Release builds pass on supported target platforms.
- [ ] Representative vanilla and each supported loader launch successfully.
- [ ] Premium and offline account flows are verified.
- [ ] Modrinth install/update flow is verified.
- [ ] Modpack installation is verified.
- [ ] Repair workflow is verified on representative failures.
- [ ] No known critical data-loss/security defects remain open.
- [ ] `docs/WORKLOG.md` and this roadmap reflect final release state.
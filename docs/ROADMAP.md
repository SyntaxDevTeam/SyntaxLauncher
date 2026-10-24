# SyntaxLauncher Roadmap

> **Current policy — 24 October 2026:** Microsoft-authenticated Java Edition players only; gameplay requires fresh entitlement/profile verification on every Play. Legacy identity rows remain recoverable, not selectable or playable. AppID approval and real-account acceptance are pending. See [AppID compliance](APPID_COMPLIANCE.md).

This file is the canonical project progress tracker.

Status rules:

- `- [ ]` — not completed
- `- [ ] 🚧 IN PROGRESS` — partially implemented, not complete
- `- [x]` — implemented and acceptance criteria satisfied

Agents and contributors MUST keep this file synchronized with actual repository state. See `AGENTS.md`.

---

## Main-window profiles and settings — 23 October 2026

- [x] Move selected-game memory into settings with explicit Save, draft cancellation, busy guards and per-profile/legacy persistence; remove sidebar RAM and duplicate Game profiles navigation.
- [x] Place Game type below Game version; embed profile list/details/Edit in the main content region with a distinct workshop illustration and retained clone/import/trash/restore.
- [x] Add conditional in-page profile wizard, loader-filtered complete packs, verified compatibility, final-only publication, inline extras catalog and cancellation/duplicate safeguards.
- [x] Replace footer slogan with accessible website/Discord/GitHub icons targeting the requested destinations and relocate Privacy/Terms/contact/full Mojang/Microsoft disclaimer from sidebar to shared footer; verify EN/PL/DE wide/400×400 behavior.
- [x] 24 October corrections: align the independence notice with wide-footer links, show Delete and Restore directly in the profile list, fix the missing confirmation translation and keep confirmation in the main page; hide Home Modpacks in Vanilla mode.
- [x] Verify the published Windows portable EXE uses all six repository icon images; gate new Windows publication on embedded PE icon verification. Stable 0.9.2 predated Windows icon configuration; a newer portable archive replaces its original bootstrap.
- [ ] Native focus, DPI, window-manager and graphical-game acceptance of this revision remains part of final release testing.

## Current delivery — 23 October 2026

Microsoft live-account work is **deferred pending Mojang AppID approval**, per maintainer instruction. It does not block implementation of v0.11/v0.12; the final v1.0 premium criterion is not waived. v0.11 implementation and v0.12 production implementation are tracked below, with final native/signing/hosted-update acceptance kept explicitly open. See [TESTING_V0.11.md](TESTING_V0.11.md), [TESTING_V0.12.md](TESTING_V0.12.md) and [INSTALLATION.md](INSTALLATION.md).

## Microsoft-only player management and AppID readiness — 24 October 2026

- [x] Enforce authenticated-only launches in the UI, session, coordinator and launch-plan builder; no unauthenticated fallback.
- [x] Dedicated Microsoft player manager: add, select and remove saved verified identities; worlds/add-ons remain isolated and unaffected.
- [x] Cover offline launch with a locked credential vault, unknown/failed Microsoft sessions, atomic rename/collision protection, persisted selection and translated/compact UI interactions.
- [ ] Real authenticated Minecraft Java gameplay and add-on acceptance; approved-AppID sign-in/gameplay, native Windows/macOS vault and UX acceptance remain open.
- [ ] Synchronize public website policies and Entra metadata to the current Microsoft-only build; the previous review described immutable commit b915ad2, not future changes.

## Historical authenticated-only AppID review change — 22 October 2026

This section records the implementation identified by b915ad2 and its submission; live acceptance remains unverified.

- [x] Require an authenticated Microsoft Java identity and fresh ownership/profile verification before every desktop Play; reject anonymous/legacy sessions in the common coordinator before preparation. Preserve old records/worlds. Negative HTTP, cached-session, core and EN/PL/DE UI tests plus full build/Linux package pass.
- [x] Preserve original branding, add in-app independence/contact and public privacy/terms links; record current approval limitations and supersede historic offline-play requirements.
- [x] Publish four application pages on syntaxdevteam.pl, verify content/cross-links, save homepage/privacy/terms/marketing/support URLs, description, notes and original logo in Entra, remove unused default Graph User.Read and submit one renewed request. Official receipt verified on 22 October 2026; allowlisting is not claimed.
- [ ] Approved-AppID live sign-in, Java entitlement/profile checks (including supported purchase/subscription cases), refresh and gameplay; cross-platform native vault/browser acceptance remain required.

## Mandatory child-first content experience (21 October 2026)

- [x] Record the non-negotiable audience/workflow in AGENTS.md and DESIGN.md: children browse inside the launcher, choose a single item or ready-made set and Install/Play; versions, loaders, Java, renderers and required dependencies belong to the launcher, not the child. Manual files and technical plans stay advanced.
- [x] Introduce the shared one-click Mods/Textures catalog for saved games, translated search/install/status/retry/cancel controls and automatic compatible mod/resource-pack dependency selection. Resource packs are selected automatically with original game-settings backups; deterministic backend/EN/PL/DE compact/wide UI regressions pass.
- [x] Complete the same simple catalog experience for shaders with automatic renderer setup, OptiFine and ready-made modpacks; a ZIP copy, backend-only service or placeholder category does not satisfy this acceptance requirement. The v0.9 graphics scope has private maintainer acceptance; v0.10 now supplies required-client Modrinth sets with complete preparation, and the wider native/gameplay matrix remains v1.0 validation.

## Urgent build feedback — 22 October 2026

- [x] Read declared Fabric nested module/provided IDs; preserve real missing-dependency notices, show a direct review action and per-file evidence. Nested/disabled-parent regressions and full local test/build pass.
- [x] Header language → settings gear → build identity, Game profiles naming, responsive compact navigation and artwork/category accents for extras and modpacks. EN/PL/DE render tests, full local test/build and Linux app-image packaging pass; wider native checks remain below.

## Cross-milestone desktop development ergonomics (October 2026)

- [x] Replace deprecated desktop resource/dialog calls with Compose resource bitmap decoding and `DialogWindow`; align OfflineAccount copy visibility with its private constructor and remove redundant loader null checks. Full local tests/build and Linux package verification pass; native window acceptance remains separate.
- [x] Embed the semantic product version, CI run number and source SHA at build time; show exact CI build identity in the launcher header and updater without altering release-version comparisons. Local builds have an explicit non-CI label.
- [x] Canonical product SemVer propagates from root `gradle.properties` through modules, embedded metadata, native versions, updates, CI and Build Explorer; existing hosted publication and version tooling tests pass. Evidence: [C12](MILESTONE_CLOSURE.md#c12).
- [ ] 🚧 IN PROGRESS — Replace native window decorations with branded controls integrated into the existing Compose header (drag, minimize, maximize/restore, close, 8 resize grips, 400×400 minimum). Source implementation and unit coverage for resize geometry added; real native/manual acceptance on Windows, macOS and Linux X11/Wayland, DPI and small screens remains open.
- [ ] Complete manual usability checks for dragging over navigation, resizing, maximization, multi-monitor behavior and updates on all supported window managers.

---

# v0.1 — Minimal Vanilla Launcher

Goal: launch a selected vanilla Minecraft version using an offline account and an automatically resolved Java runtime.

**Current status:** implementation and automated verification reconciled on 24 October 2026; live/native release gates remain open. Historical details below are superseded where a linked closure record is present. The account identity boundary, Mojang catalog/metadata resolver, verified game preparation, Java manager, launch planner and process manager are now composed into a desktop launch pipeline. Compose exposes a real Play action, per-stage and per-file progress, cancellation, process status/exit code/log paths and a stop action. Window close cancels ongoing preparation and detaches from an already running Minecraft process. The version-list cache and local Gradle test/build verification passed on 20 October 2026; live Minecraft, native platform and other release acceptance gates remain open.

## Interface languages — explicitly requested 20 October 2026

- [x] UTF-8 English, Polish and German resource bundles for home, instance editing/management, local mods, Modrinth browsing, window actions and startup updates.
- [x] Live language picker with system-language detection, English fallback and saved per-user choice; switching does not alter instance/account/game data.
- [x] Additive schema-1 `ui_language` preference preserves legacy/unknown properties and survives ordinary profile saves and restart; future schemas are not overwritten.
- [x] Resource key/placeholder parity, Unicode, fallback, switching/restart, persistence and EN/PL/DE wide/narrow Compose render regressions pass. Home scrolling coverage now checks the entire RAM label after both nested scroll containers finish, including CI build metadata; hosted CI must confirm the correction.
- [ ] Native Windows/macOS keyboard, font and DPI acceptance of translated screens; upstream descriptions and detailed technical diagnostics retain their original language.

## Project foundation

- [x] Gradle Kotlin DSL project structure builds successfully (local `test build`, 20 October 2026).
- [x] Core and Compose desktop modules compile and test independently through the root build.
- [x] Kotlin/JVM Java 21 toolchain verified by the full local build.
- [x] Compose Desktop compilation, rendered regression tests and Linux distribution packaging pass.
- [x] Coroutines dependency and background service integration compile and pass tests.
- [x] Shared Ktor Client configuration compiles and deterministic HTTP regressions pass.
- [x] kotlinx.serialization configuration and parser regressions pass.
- [x] Configure SLF4J/Logback with rotating, redacted launcher files and documented retention; SafeLogMessageTest and production logback.xml verify the bounded logging policy. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Kotlin/JUnit5 framework executes the complete core and desktop regression suite.
- [x] Add `.gitignore` for IDE/build/runtime/generated files.
- [x] Resolve platform-aware data/config/cache/log/runtime paths and use them in desktop/core lifecycles; LauncherPathsTest and DesktopLauncherSessionAccountsTest pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).

## Offline account

- [x] Common `Account` model implemented and verified by the local core/desktop suite.
- [x] `OfflineAccount` implemented and verified by core regressions.
- [x] Minecraft-style offline username validation passes deterministic unit and rendered UI tests.
- [x] Deterministic Minecraft offline UUID generation passes the existing compatibility regressions.
- [x] Persist offline identity/preferences atomically with schema and unknown-key retention; preference, account-manager and real Compose save/restart tests pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Preserve old account records on disk only; current player manager exposes authenticated Microsoft identities. Earlier milestone test evidence is historical and does not authorize unauthenticated play.

## Minecraft version discovery

- [x] official Mojang `version_manifest_v2.json` fetch service implemented. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Version manifest parser and DTO/domain mapping verified by deterministic tests.
- [x] Release/snapshot/legacy version type mapping verified by parser tests.
- [ ] 🚧 IN PROGRESS — release filtering is implemented in the first Compose UI; full UI build/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — snapshots can be explicitly selected through the channel filter; full UI build/manual verification remains pending.
- [x] Cache version manifest safely: validate before atomic publication, preserve the last valid catalog after network/HTTP/malformed-response failures, revalidate on cache reads, and propagate cancellation. Seven deterministic regressions and local `test build` passed (20 October 2026).
- [x] Version-catalog failures use typed `VersionCatalogException`, cache fallback and visible Retry; deterministic failure regressions pass.

## Version metadata resolver

- [x] selected version metadata download service implemented and wired to UI. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Version id/type parsing verified by metadata fixture tests.
- [x] Client download metadata parsing verified by metadata fixture tests.
- [x] Library/download/classifier metadata parsing verified by metadata fixture tests.
- [x] Asset-index metadata parsing verified by metadata fixture tests.
- [x] Main-class parsing verified by metadata fixture tests.
- [ ] 🚧 IN PROGRESS — modern JVM/game arguments and legacy `minecraftArguments` parsing are now consumed by `LaunchPlanBuilder`; full Gradle/real-launch verification remains pending.
- [x] Java requirement/component metadata parsing is implemented and consumed by the managed runtime manager. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] OS/version/architecture and feature rule parsing verified by deterministic tests.
- [x] Resolve checksum-verified catalog parent metadata with bounded cycle/depth checks, child library precedence and inherited arguments/runtime/client; deterministic regressions pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Ordered allow/disallow rule evaluation and argument filtering pass deterministic regressions.
- [x] Representative modern/legacy metadata fixture tests execute successfully in the full suite.

## Minecraft files

- [x] `MinecraftPreparationService` now executes the planned client JAR download through the shared verified downloader. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] client JAR SHA-1 and size are verified before the `.part` file is atomically published. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Select OS/architecture/rule-compatible classpath libraries and cache paths; LibraryResolverTest and MinecraftPreparationServiceTest pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] preparation service downloads missing planned libraries through bounded shared infrastructure. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] library hashes/sizes are verified by the generic integrity layer before publication. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] native classifier selection including `${arch}` replacement implemented and tested in source. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] planned native archives are downloaded through the shared verified downloader. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Extract verified native archives with META-INF exclusions and path containment; NativeExtractorTest and complete preparation regressions pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] asset index is now included in the executable preparation download plan and verified before parsing. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] asset-index parser and Mojang asset-object URL/cache planner are integrated into preparation; duplicate hashes are de-duplicated. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] missing asset objects are planned and downloaded through the shared manager. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Reuse verified existing clients/libraries/natives/assets without network requests; warm-cache preparation regressions pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] downloads use sibling `.part` files and publish only after successful size/hash verification; interrupted/failed partials are cleaned and never treated as valid cache. Evidence: [C01](MILESTONE_CLOSURE.md#c01).

## Download infrastructure

- [x] centralized `DownloadManager` now streams all planned artifacts through the shared Ktor client. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Structured coroutine cancellation propagates through batches and removes active `.part` files before completion, preserving existing targets and cancelling queued requests. Deterministic single/concurrent-batch cancellation and retry regressions plus local `test build` pass (21 October 2026); native cancel-during-game-preparation acceptance remains separate.
- [x] batch downloads use a coroutine `Semaphore` with configurable bounded parallelism (default 6). Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] typed batch/file start, progress, retry, complete and failure events are exposed to application callers. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] retry policy handles transient network failures, HTTP 408/425/429/5xx and integrity/size failures with bounded backoff. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] SHA-1 and expected-size validation is shared for downloaded and cached files. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] downloads are written to `.part` and finalized with atomic move where supported, falling back to replace move only after verification. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Map typed download/HTTP/integrity/size/disk/permission/conflicting-target failures to safe EN/PL/DE launch advice; nested cause, privacy, retry and compact full-text tests pass. See [v0.1 verification](TESTING_V0.1.md#typed-launch-failures-and-native-java-verification).

## Java Runtime Manager

- [x] Java requirement (`component` + `majorVersion`) is parsed from Minecraft metadata and now drives runtime resolution. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] `JavaRuntime` now records executable/home/version plus source/component/platform/release identity. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Resolve managed Java before creating the launch request; complete coordinator and real Java-subprocess wiring regressions pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] compatible launcher-managed runtimes are discovered and reused before any runtime-index network call; complete manifest-backed integrity and `java -version` checks guard reuse. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — official Mojang runtime index is used as the distribution source and selected by the Minecraft-declared component plus detected platform; Linux Java 21/25 cold install, execution, offline reuse and controlled repair pass; the new hosted Windows/Linux/macOS managed-runtime matrix awaits its first run.
- [x] missing Mojang runtime manifests/files are downloaded through the shared verified `DownloadManager`, including retry/cancellation/progress behavior. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] runtime manifests and every installed file are checked by size/SHA-1; directories and symlinks are checked on reuse; corrupted/incomplete runtimes are rejected and reinstalled automatically. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — Windows/Linux/macOS Java executable resolution is implemented, including `bin/java.exe`, `bin/java` and macOS `jre.bundle/Contents/Home/bin/java`; a new native CI test exercises actual managed Java 21/25 executables, offline reuse and repair on all three distributed targets; local Linux passes and hosted results are pending.
- [x] staged and published runtimes are executed with `java -version`, parsed for modern/legacy version formats and rejected when the major version differs from Minecraft metadata. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Map unsupported Java platform, index/manifest, install and verification/version failures to localized launch advice. Malformed index JSON is wrapped in a typed Java metadata error; real Play/Retry and compact EN/PL/DE tests cover the UI. See [v0.1 verification](TESTING_V0.1.md#typed-launch-failures-and-native-java-verification).
- [x] runtime installation uses a staging directory, safe manifest paths, manifest-declared executable flags/symlinks, a completion marker and atomic directory publication where supported. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] controlled MockEngine tests cover target/manifest parsing, initial installation, no-network warm reuse and automatic repair after a managed runtime file is removed. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] runtime models/services remain entirely in `launcher-core` and independent from Compose UI. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Publish distribution/runtime boundaries and exact available gameplay evidence in [SUPPORT_MATRIX.md](SUPPORT_MATRIX.md); component availability and unsupported combinations fail explicitly. Native acceptance is separate.

## Launch planning

- [ ] 🚧 IN PROGRESS — `LaunchOptions` memory bounds are now passed from the Compose RAM control into the real Play pipeline and consumed by `LaunchPlanBuilder`; Gradle/live acceptance remains pending.
- [x] `LaunchRequest` now binds common `Account`, verified `PreparedMinecraftVersion`, resolved `JavaRuntime`, platform, features, options and normalized `LaunchAuthentication`. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — classpath is assembled from prepared libraries plus client JAR and substituted with the target platform classpath separator; full Gradle/real-launch verification remains pending.
- [x] Mojang placeholders are substituted explicitly for account/version/game/assets/natives/classpath/library/auth/launcher fields; unknown placeholders fail with a typed launch-plan error instead of reaching Java unresolved. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Mojang rule filtering for JVM/game arguments is applied before placeholder substitution. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] `-Xms`/`-Xmx` are generated from `LaunchOptions` and extra JVM arguments are appended without shell-string concatenation. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Bind modern and legacy asset placeholders; materialize verified virtual and per-game resources views with safe paths, cache repair and preservation of different user files. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — `${natives_directory}` is bound to the version-specific prepared native directory; full launch execution remains pending.
- [x] Bind game_directory to the selected UUID game directory, retaining separate original Legacy Vanilla storage. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] offline username/UUID/session/access token/user type are normalized through `LaunchAuthentication.offline()` without hardwiring raw nickname handling throughout the builder; premium credentials can later use the same boundary. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] metadata logging argument `${path}` is bound to the verified downloaded logging configuration when present. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] legacy versions without metadata JVM arguments receive explicit natives path and classpath fallback arguments rather than a version-specific hardcoded launch recipe. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — structured `LaunchPlan` now directly feeds `MinecraftProcessManager` through `MinecraftLaunchCoordinator`; compilation/full real-Minecraft launch acceptance remains pending.
- [x] deterministic unit-test sources cover modern placeholder construction, memory/logging/classpath/offline identity, legacy JVM fallback, arguments containing spaces and rejection of unknown placeholders. Evidence: [C01](MILESTONE_CLOSURE.md#c01).

## Process management

- [ ] 🚧 IN PROGRESS — `MinecraftProcessManager` starts `LaunchPlan.command` with `ProcessBuilder` in the prepared game working directory; full Gradle and real Minecraft launch verification remain pending.
- [x] each launch creates a distinct Minecraft run directory under launcher logs with separate redirected `stdout.log` and `stderr.log`; POSIX log files use owner-only permissions and native redirects survive launcher detach. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] `StateFlow<MinecraftProcessStatus>` exposes IDLE/STARTING/RUNNING/STOPPED/CRASHED/DETACHED, PID, start/end timestamps, log paths, actionable failure message and exit code. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Classify natural nonzero/zero exits as CRASHED/STOPPED and requested termination as STOPPED; real subprocess regressions pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] mutex-guarded process ownership rejects duplicate launches until the previous process exit is observed. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — explicit `terminate(gracePeriodMillis)` first requests graceful termination and then forces termination if necessary; cancellation and OS-specific edge cases still need live acceptance.
- [ ] 🚧 IN PROGRESS — explicit `shutdown(DETACH | TERMINATE)` policy defines launcher close behavior; DETACH leaves Minecraft alive with stdout/stderr redirected to files; desktop window-close integration remains pending.
- [x] typed process startup, duplicate-launch, closed-manager and forced-termination failures are defined without logging a full launch command or authentication arguments. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] five process-manager test scenarios cover a real JDK subprocess, separate stdout/stderr logs, duplicate protection, crashes and relaunch, force-stop, detach, and retry after startup failure. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — Compose observes process `StateFlow` and displays PID, exit code, errors and process log directory; Stop calls `terminate()`. Window close uses DETACH after canceling ongoing downloads; full UI lifecycle testing pending.

## v0.1 UI

- [x] Integrate the explicitly selected open-gateway mark into header/window/startup and Linux icon. Following maintainer feedback, keep the home Hero free of foreground branding and retain its landscape/copy only. Local test/build/Linux image and wide/compact Compose regressions cover the revised layout (22 October 2026).
- [ ] Review the final header-only branding composition with the maintainer; broader Windows/macOS package/DPI checks remain open.

- [x] Replace the old small header/window/Linux icon with a proportion-preserving export of the existing approved hero logo (20 October 2026); original hero retained, local `test build` and Compose render review passed. Native package appearance remains a separate open acceptance gate.

- [x] Make the launcher logo respond to the measured hero/text space and original asset aspect ratio: reserve separate text/logo areas, reflow below the heading in narrow heroes, and verify 27 resize/density cases without cropping, distortion or text collisions (19 October 2026).

- [x] Match the supplied neon concept with a detailed voxel landscape, independently fitted transparent block/arrow, subtle separation shadow/halo, ink-blue panels, shared cyan vector icons, compact controls and correctly disabled Play styling. Local `test build` and Compose render review passed on 19 October 2026.
- [x] Add deterministic headless home rendering/interaction checks for 1440×810, 1024×640, 440×740, 400×400 and 150% scaling; validate nickname/Play state and access to settings by scrolling. Native keyboard/focus and other-platform acceptance remain separate open gates.

- [ ] 🚧 IN PROGRESS — reference-based cinematic scenery and independent transparent mark, Home/Instances interactions and high-contrast Play treatment now pass local build/tests and headless render review. Native desktop, keyboard/focus and cross-platform visual acceptance remain open.

- [ ] 🚧 IN PROGRESS — voxel valley, readable scrims and independently fitted blue foreground mark implemented; short/narrow layouts and 150% scaling pass deterministic render checks. Native OS/DPI acceptance remains pending.

- [ ] 🚧 IN PROGRESS — blue voxel/arrow mark integrated into desktop header/window; updated ink-blue/cyan theme and existing Linux package icon configured. Local compilation passes; package/native visual acceptance of this revision remains pending.

- [ ] 🚧 IN PROGRESS — v0.1 Compose home redesigned around a high-contrast atmospheric hero, prominent contextual Play CTA, compact profile/version/RAM settings, branded header, semantic status and progressive error details; full platform CI and manual UX/accessibility review pending.
- [ ] 🚧 IN PROGRESS — shared ink-blue/cyan roles, 18 dp panels, 9 dp controls, typography and vector components implemented; deterministic resizing/150% rendering passes. Native focus/accessibility and platform DPI evaluation still pending.

- [x] The old nickname property remains readable for non-destructive compatibility but does not provide a playable identity.
- [ ] 🚧 IN PROGRESS — Minecraft version selector and release/snapshot filters are driven by the official Mojang catalog, with selected metadata verified asynchronously; fixture-based Compose rendering passes, native live-catalog acceptance remains pending.
- [ ] 🚧 IN PROGRESS — RAM slider feeds `LaunchOptions(maximumMemoryMiB)` on Play; persistence/full UI verification remains pending.
- [ ] 🚧 IN PROGRESS — real `Play Minecraft` action now runs prepared metadata → verified game downloads/assets/natives → managed Java → `LaunchPlanBuilder` → `MinecraftProcessManager`; live vanilla launch acceptance is pending.
- [ ] 🚧 IN PROGRESS — `MinecraftLaunchCoordinator` exposes preparation phases, per-file progress and completion counts through `StateFlow`; Compose displays status and progress and offers cancellation.
- [ ] 🚧 IN PROGRESS — catalog/metadata errors have retry actions; pipeline preparation/Java/launch errors and process crashes display human-oriented messages and log paths; detailed error mapping/manual verification remain open.
- [x] nickname/version/channel/RAM controls and Play are disabled during preparation or while the game runs; duplicate launch is also guarded in the session/core. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [ ] 🚧 IN PROGRESS — `DesktopLauncherSession` owns application-scope jobs, cancels outstanding preparation on window close, shuts down process manager with DETACH and closes the shared Ktor client; cross-platform close acceptance is pending.
- [ ] 🚧 IN PROGRESS — the UI exposes a `Stop Minecraft` action and running/stopped/crashed/exit-code indicators from ProcessManager; real-game/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — Compose restores and debounces storage of nickname, selected version, release/snapshot channel and RAM via atomic schema-versioned properties; tests and desktop acceptance pending.
- [x] Cache version manifest to support startup when Mojang is unreachable; desktop cache wiring and restart/offline catalog regression pass. This does not claim a fully offline Minecraft launch.
- [ ] Verify launcher UI layout, long progress text and keyboard/focus behavior at supported window sizes and scaling.


## Build and CI validation

- [x] Publish all three portable applications to Build Explorer v2 using a project-scoped CI secret; main run 39290313808 passed, all three public downloads verified by SHA-256.

- [x] GitHub Actions run 39125171338 passed `:launcher-core:test` and desktop Kotlin compilation with pinned Gradle 9.4.0 and Temurin JDK 21 on Linux.
- [x] GitHub Actions run 39125171338 built Compose portable app images for Linux x64, Windows x64 and macOS ARM64, and published permission-preserving platform tar.gz preview artifacts.
- [x] Official Gradle wrapper 9.4.0 (`gradlew`, `gradlew.bat`, properties, official JAR) generated, executed and committed by a restricted bootstrap GitHub Actions workflow (03d2650).
- [x] Identified and repaired initial CI blockers: missing Google Maven repositories, Kotlin Path classpath concatenation, desktop Ktor dependency, malformed test fixtures, and macOS app-version jpackage constraint.
- [ ] 🚧 IN PROGRESS — portable artifacts have CI-proven packaging but await manual extraction, startup/operation and signed native installer packaging where appropriate.
- [ ] Validate a clean-machine vanilla launch and documented OS/architecture matrix before declaring v0.1 usable as a public release.
- [x] Packaged Mojang HTTPS and checksum-verified metadata pass on Windows/Linux/macOS in CI 40067168228; fresh local Linux image reconfirmed after TOML changes. The shipped runtime supplies the crypto modules.

## v0.1 quality gate

- [ ] 🚧 IN PROGRESS — The complete local core/desktop suite passes (20 October 2026); remaining supported-host/native acceptance stays open.
- [ ] 🚧 IN PROGRESS — Full local `./gradlew build` and Linux `createDistributable` pass (20 October 2026); remaining native platform release acceptance stays open.
- [ ] Fresh launcher state can download prerequisites and start a supported vanilla Minecraft release.
- [ ] Second launch reuses valid cached data instead of redownloading everything.
- [x] Offline identity remains stable for the same username; deterministic UUID/account and persistence tests pass. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] Java is selected automatically without requiring a globally installed Java when managed runtime installation succeeds. Evidence: [C01](MILESTONE_CLOSURE.md#c01).
- [x] No premium authentication/mod/loader feature has leaked into v0.1 scope unnecessarily. Evidence: [C01](MILESTONE_CLOSURE.md#c01).

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
- [x] explicit non-destructive import copies the v0.1 game folder while leaving source untouched. Evidence: [C02](MILESTONE_CLOSURE.md#c02).
- [ ] 🚧 IN PROGRESS — cloning rejects symlinks/special files, reports preflight size and streamed copy progress, stages data atomically without reusing abandoned staging, and preserves unknown metadata; real-world tests remain open.

## Instance management

- [x] create, rename, edit instance version/RAM/JVM settings, list alphabetically, clone worlds and move to recoverable trash. Evidence: [C02](MILESTONE_CLOSURE.md#c02).
- [x] Selected instance UUID persists as an optional schema-1 preferences key, compatible with older settings; CI suite passed.
- [ ] 🚧 IN PROGRESS — deletion uses `data/instance-trash/`, with in-app restore and original UUID preservation; CI/manual restore verification remains pending.

## Launch integration

- [x] Selected instance overrides the existing launch pipeline's game directory without duplicating the launch engine; integration test passed.
- [x] per-instance min/max RAM and extra JVM arguments override launcher defaults when an instance is active. Evidence: [C02](MILESTONE_CLOSURE.md#c02).
- [x] the v0.1 Legacy Vanilla profile remains available with its original game folder. Evidence: [C02](MILESTONE_CLOSURE.md#c02).
- [ ] Manual acceptance: launch two profiles with the same Minecraft version and confirm independent worlds/configurations.

## v0.2 UI

- [x] Add a home instance dropdown using the existing guarded selection path for Legacy Vanilla and UUID profiles; a deterministic Compose interaction test verifies profile/version/RAM display without opening the manager.

- [x] instance selector/manager added to Compose home without changing primary Play CTA. Evidence: [C02](MILESTONE_CLOSURE.md#c02).
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

**Status:** implementation foundations verified; live acceptance deferred by maintainer pending Mojang. Historical registration/submission blockers are reconciled in [C03](MILESTONE_CLOSURE.md#c03). Microsoft OAuth, Xbox/XSTS and Minecraft Services exchanges are implemented and mock-tested, but **a new Microsoft Entra Client ID is not automatically authorized by Minecraft Services**. The project must separately submit its own AppID for Minecraft/Mojang approval/allowlisting. Until explicit confirmation and a real `login_with_xbox` test, authenticated Minecraft Java login and full v0.3 release are **BLOCKED**. The maintainer's one-time v0.2 acceptance waiver is unrelated to this external dependency.

## External prerequisite — Minecraft Services AppID authorization

- [x] Registered SyntaxLauncher’s owned public Entra app and localhost redirect; the public app configuration and submitted immutable revision are recorded in MICROSOFT_APP.md. This is registration evidence, not Minecraft allowlisting. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [x] Submitted the owned Client ID for Mojang AppID review; MICROSOFT_APP.md records the receipt and renewed b915ad2 submission. Approval remains deferred and unchecked below. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [ ] **P0 / BLOCKED — Obtain and record explicit approval from Mojang/Minecraft** for the project's Client ID, then perform a real successful Minecraft Services token and Java profile retrieval test.
- [x] Keep Xbox/XSTS/Minecraft providers testable with mocked HTTP; MicrosoftOAuthTest and MinecraftPremiumAuthenticationTest pass without claiming live authentication. Evidence: [C03](MILESTONE_CLOSURE.md#c03).

## Account management

- [ ] 🚧 IN PROGRESS — AccountManager schema-1 identity inventory, native refresh-token vault adapters and bounded browser callback helper are present with deterministic tests; desktop integration now reloads/saves identities, refreshes on Play and removes local vault credentials on sign-out; native/live premium acceptance remains open. Registration/submission records are in [MICROSOFT_APP.md](MICROSOFT_APP.md); approval is not claimed.

- [x] Introduce account manager/service and connect desktop persistence; deterministic tests pass.
- [x] Preserve old identity records without deleting associated game data; never expose them as selectable profiles.
- [x] Add a verified premium account type and token-safe session class; tested.
- [x] Support multiple saved offline/Microsoft identities; restart coverage uses an isolated test vault.
- [x] Allow saved account selection as the persistent launcher default; account-manager and translated desktop player tests pass. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [x] Display only authenticated Microsoft identities in the player selector and manager; translation and rendering tests remain acceptance gates.

## Delivery phases

- [x] Implement authentication foundation: typed identity, S256 PKCE/state, OAuth/Xbox/XSTS/Minecraft profile and entitlement exchanges with deterministic provider tests. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [ ] **P0 — Desktop sign-in:** system browser with loopback callback and CSRF validation, loading/cancel/error states, configurable registered client ID.
- [ ] **P0 — Session security:** token redaction, secure OS-backed refresh credential persistence, refresh and sign-out.
- [x] Pass the selected authenticated account through the common coordinator; DesktopLauncherSessionAccountsTest and premium coordinator regressions pass. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [x] Implement multiple saved Microsoft identities and persistent selection, with read-only recognition of historical local entries; regression verification is commit-specific.
- [ ] **Release acceptance:** approved Microsoft app registration, live sign-in/refresh and premium Minecraft game launch.

## Microsoft authentication

- [ ] 🚧 IN PROGRESS — native public-client OAuth request/code exchange and refresh implemented; localhost browser callback UI remains.
- [x] Implement S256 PKCE, cryptographic CSRF state and unique request verifier; tested.
- [x] Use the system-browser public-client flow without collecting or storing a Microsoft password; browser callback and OAuth contract tests pass. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [x] Implement Xbox user-token exchange to Microsoft OAuth token, covered by MockEngine integration test.
- [x] Implement Xbox XSTS auth with Minecraft relying party, covered by MockEngine.
- [ ] 🚧 IN PROGRESS — login_with_xbox implemented with typed HTTP-403 app-registration errors; only mocked tests so far.
- [x] Fetch and parse verified Minecraft Java profile (mock-tested).
- [x] Parse Minecraft Java profile name and compact UUID (mock-tested).
- [x] Classify Xbox, XSTS, Minecraft-app-registration and missing Java profile failures without exposing tokens.

## Token lifecycle

- [ ] 🚧 IN PROGRESS — premium LaunchPlan now flags credential-bearing sessions and default process starter discards stdout/stderr rather than persisting raw tokens. Game-owned logs and crash reports still require security review.

- [x] Refresh Microsoft sessions through the account manager, verifying unchanged identity; deterministic lifecycle tests pass. Live acceptance still awaits Mojang. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [ ] Store long-lived secrets/tokens using an appropriate secure OS mechanism.
- [ ] Redact tokens from logs and exceptions.
- [x] Remove the local refresh credential and saved identity on sign-out; preserve identity on vault removal failure. Remote grant revocation is not claimed. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [x] Refuse premium Play after refresh failure with a re-authentication path; no offline fallback. DesktopLauncherSessionAccountsTest passes. Evidence: [C03](MILESTONE_CLOSURE.md#c03).

## Launch integration

- [x] Convert verified account/session data to the existing normalized launch inputs; premium/coordinator tests pass. Evidence: [C03](MILESTONE_CLOSURE.md#c03).
- [ ] 🚧 IN PROGRESS — coordinator accepts normalized premium LaunchAuthentication and uses standard metadata placeholder replacement; desktop selection and live verification pending.
- [x] Both account types use MinecraftLaunchCoordinator with a normalized LaunchAuthentication.

## v0.3 UI

- [ ] 🚧 IN PROGRESS — choose saved Offline or Microsoft identities in the home sidebar; desktop persistence and deterministic render tests are implemented, full native/live UX acceptance pending.
- [ ] 🚧 IN PROGRESS — native browser / localhost PKCE flow wired to Add Microsoft when an owned app ID is configured; real sign-in pending.
- [x] Offline profile remains independently selectable and used by the original launch path.
- [ ] Show premium profile name/avatar when available.
- [ ] 🚧 IN PROGRESS — Sign out removes the local refresh credential and saved identity; a vault error retains the account in the UI. Native acceptance remains pending; remote Microsoft grant revocation is not claimed.

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

**Status:** maintainer additionally confirmed Linux/Windows 10 Fabric startup and world saving for Minecraft 1.21.11, 26.2 and 26.3 on 24 October; exact loader versions were not supplied. See [C04](MILESTONE_CLOSURE.md#c04). Earlier CI verified ([successful run](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39317269516): core/desktop tests,
native packages for Windows/Linux/macOS and Build Explorer publishing). The maintainer confirmed real Fabric and Quilt testing on 20 October 2026; game boot acceptance is passed. Cross-platform, fault-injection and library-integrity acceptance remain separately tracked. See [v0.4 checklist](TESTING_V0.4.md).
v0.3 official Microsoft approval is an independent external dependency and does not block
offline account tests for this milestone.

## Loader abstraction

- [x] Introduced `ModLoaderProvider` and `ModLoaderRegistry`; the second-provider extensibility quality gate remains open.
- [x] Implemented and validated loader ID/version selections in core.
- [x] Added optional per-instance loader selection in schema 1 with passing serialization regression tests.
- [x] Isolated Fabric metadata handling in its provider without duplicating the process launcher.

## Fabric

- [x] Query compatible loaders through official Fabric Meta; deterministic tests and maintainer-confirmed Fabric testing passed (20 October 2026).
- [x] Construct Fabric dependency downloads through the shared downloader; maintainer confirms a real Fabric boot (20 October 2026).
- [x] Overlay Fabric entry point/libraries/arguments on Mojang metadata; automated regressions and maintainer-confirmed game test pass. Missing profile hashes remain a separate integrity-policy item.
- [x] Feed Fabric selection into the generic launch pipeline; real Fabric launch confirmed by the maintainer (20 October 2026).
- [ ] 🚧 IN PROGRESS — Support per-instance loader version changing, cloning and Vanilla reset; manual acceptance pending.
- [ ] 🚧 IN PROGRESS — Compatibility checked on selection and again before launch; live metadata verification pending.

## v0.4 UI

- [x] Automatically adjust unsupported Minecraft/loader drafts to the newest supported game and loader in settings, creation and quick setup; explain the adjustment before Save/Create. Quilt discovery 404 is incompatibility; network failures do not trigger fallback. Deterministic provider/HTTP, EN/PL/DE Compose, cancellation regressions and local test/build pass (21 October 2026); native platform usability remains a separate open gate.

- [x] Add a child-friendly home Vanilla / With mods choice; reuse saved Fabric/Quilt games and keep Vanilla worlds unchanged. Rendered mode-switch/persistence and isolation regressions pass.
- [x] Offer first-time modded-game setup with automatic compatible Minecraft/loader selection, explicit confirmation and immediate Modrinth discovery; advanced naming/version/loader choices stay behind More options. Loading, retry, cancellation, duplicate-click protection and EN/PL/DE 400×400/440×640 setup regressions pass.
- [x] Replace disabled managed-profile version/RAM controls with a compact summary and Game settings entry; keep Play dominant and use My games rather than requiring instance terminology on the normal home screen.
- [ ] Validate the complete simplified discovery/install/Play flow on native supported platforms and observe first-time child users; no child usability or all-platform acceptance is inferred from renders.

- [ ] 🚧 IN PROGRESS — Vanilla/Fabric creation and settings controls implemented; manual desktop usability pending.
- [x] Vanilla remains explicit; metadata coexistence and profile/world preservation tests pass. Evidence: [C04](MILESTONE_CLOSURE.md#c04).
- [ ] 🚧 IN PROGRESS — Fabric version picker from API, stable version preferred; keyboard/small-window acceptance pending.
- [ ] 🚧 IN PROGRESS — Incompatible and API error feedback added; error-state acceptance pending.

## v0.4 quality gate

- [x] CI Gradle tests and build completed on Ubuntu, macOS and Windows runners, with successful Build Explorer publishing.

- [ ] Existing vanilla instances remain unchanged (serialization tests pass; manual pending).
- [ ] Fabric selection and writable game data are instance-scoped (automated coverage passes; real-world acceptance pending).
- [x] Fabric instance launches through the same coordinator; maintainer-confirmed real boot on 20 October 2026.
- [x] A second playable provider, Quilt, uses the registry/shared coordinator; deterministic profile tests and maintainer-confirmed Quilt boot pass. Forge-family processor execution remains separate.
- [x] Unchecksummed library/native cache reuse requires a URL/size-bound observed SHA-256 receipt; corruption/missing receipt forces download. Local detection does not claim upstream authenticity. Evidence: [C04](MILESTONE_CLOSURE.md#c04).
- [ ] Pass the `docs/TESTING_V0.4.md` *manual* checklist on target desktop platforms.

---

# v0.5 — Local Mod Manager

Goal: manage local mod JARs safely before adding online repositories.

**Status:** implementation and current automated CI verified; manual game/native acceptance remains open. Real Fabric and Quilt startup tests were subsequently confirmed by the maintainer on 20 October 2026. Local mod-management gameplay and native UX acceptance remain separate.
See [v0.5 test checklist](TESTING_V0.5.md).

## Mod model

- [x] Local JAR inventory records filename, size, SHA-256, enabled state and optional loader metadata; deterministic regressions pass.
- [ ] 🚧 IN PROGRESS — Enabled/disabled state persisted via per-instance filesystem rename; manual testing pending.
- [x] Track instance-local JAR hashes and filenames; restart/provenance regressions pass.
- [x] Read `fabric.mod.json` and `quilt.mod.json`; loader-specific metadata regressions pass.
- [ ] 🚧 IN PROGRESS — Expose Fabric id/name/version, declared requirements/conflicts; client verification pending.
- [x] Detect duplicate enabled mod IDs as warnings and duplicate JAR SHA-256/filename collisions during import; automated regressions pass.

## Local mod operations

- [x] Local JAR import uses private staging, preserves the source and publishes under the instance lock; deterministic tests pass.
- [ ] 🚧 IN PROGRESS — Remove JAR to a recoverable instance-local trash folder; manual acceptance pending.
- [ ] 🚧 IN PROGRESS — Enable/disable with a reversible `.jar.disabled` rename; acceptance pending.
- [ ] 🚧 IN PROGRESS — Show installed local mods and basic metadata in a scrollable dialog; UX check pending.
- [x] Warn about missing/conflicting declared Fabric dependencies; version ranges advisory only. Evidence: [C05](MILESTONE_CLOSURE.md#c05).
- [x] Local mod mutations use the instance repository cross-process activity lock and remain instance-scoped; automated regressions pass.

## Dependency awareness

- [x] Warn for missing enabled declared dependencies using local metadata; deterministic tests pass.
- [x] Warn about explicitly declared local `breaks` conflicts; advisory metadata behavior tested.
- [x] Show local dependency warnings in Mods and before Play; dependency enforcement remains outside this advisory scope.
- [x] Modrinth-backed dependency resolution remains explicitly in v0.6; v0.5 does not claim it.

## v0.5 quality gate

- [x] Mod enable/disable is reversible and preserves bytes; local regression suite passes.
- [ ] Mod state persists per instance (automated test added; real client pending).
- [ ] Fabric instance can launch with locally managed mods (real game boot deferred).
- [ ] Vanilla instance behavior remains unaffected (automated coverage added; manual pending).
- [x] Per-instance mod mutations use the activity lock; automated instance activity/lock regressions pass.
- [ ] Manual tests in `docs/TESTING_V0.5.md` pass.

---

# v0.6 — Modrinth Integration

Goal: browse popular or searched compatible mods, read details, install dependencies,
and later deliver version-aware updates.

**Status:** implemented discovery/required-dependency installation and transactional updates are verified in the current automated suite and hosted CI. Optional-dependency choices and declared managed-origin conflict guards are now tested; general dependency reconciliation and native/game acceptance remain open. The maintainer confirmed real Fabric and Quilt startup on 20 October 2026; this does not imply completion of every Modrinth workflow. See [v0.6 checklist](TESTING_V0.6.md).

## Discovery and Modrinth client

- [x] Isolated Modrinth API v2 adapter for popular mod charts and search. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Sort popular mods by Modrinth download count, not a hardcoded list. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Search projects and retrieve project body/license. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] List compatible releases and filter by exact Minecraft version and loader. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Exclude server-only versions from desktop installation. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Bounded pages, canonical product User-Agent, 15-second API deadlines and three-attempt transient/429 retry with capped Retry-After; permanent errors fail immediately with translated retryable UI. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Support installed Fabric loader now, other loader types after their own implementation. Evidence: [C06](MILESTONE_CLOSURE.md#c06).

## Installation and dependencies

- [x] Resolve transitive required Modrinth dependencies into a reviewable plan. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Include compatible files, versions, sizes and SHA-1 checksums before downloads. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Download staged JARs through the shared downloader, then atomically import the batch under an instance lock. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Reject incompatible Minecraft/loader versions, server-only files and malformed or untrusted CDN metadata. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Optional choices resolve selected branches; explicit incompatible declarations fail within a plan and against enabled managed origins before download and under the instance lock before import. Full local/version reconciliation remains separate. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Preflight duplicate filenames/mod IDs and preserve pre-existing local mods. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [ ] 🚧 IN PROGRESS — Persist instance-local project/version and verify saved SHA-256 on read; CI/manual acceptance pending.
- [ ] Evaluate version-range conflicts and reconcile already-installed dependencies rather than rejecting all duplicates.

## Updates

- [ ] 🚧 IN PROGRESS — Read-only compatible updates for managed files; CI/manual acceptance pending.
- [ ] 🚧 IN PROGRESS — Install selected compatible update with reviewed hash and prior-byte backup; CI and gameplay acceptance pending.
- [ ] 🚧 IN PROGRESS — Batch update of managed mods with no missing/different required dependency; UI/CI acceptance pending.
- [ ] 🚧 IN PROGRESS — Transactional per-instance JAR replacement, retained backup and explicit last-update rollback; CI/manual acceptance pending.
- [x] Explain unmatched local mods. Evidence: [C06](MILESTONE_CLOSURE.md#c06).

## v0.6 UI

- [x] Expose Choose mods directly from the home screen and open Modrinth discovery first for newly created modded games; the advanced manager retains the installed-mod view.
- [x] Popular mods in a loader/version-filtered browser inside Instances → Mods. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Project detail and external Modrinth page link. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Search, pagination, installation review and install confirmation. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Add an optional-dependency picker to the advanced installation review; EN/PL/DE compact interaction and install-while-replanning guards pass. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [ ] Full dependency conflict reconciliation choices for arbitrary installed local mods and version ranges; unsupported cases fail rather than replacing files silently.
- [ ] 🚧 IN PROGRESS — Manual update check/results in Mods UI; bulk/single replacement remains open.

## v0.6 quality gate

- [x] Compatible mods are installed and incompatible ones are rejected. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Required dependencies are installed correctly or yield actionable diagnostics. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Existing local mods and other instances are preserved. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] No Modrinth metadata code leaks into the generic launcher. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [x] Automated tests and multi-platform packaging pass. Evidence: [C06](MILESTONE_CLOSURE.md#c06).
- [ ] Manual discovery/read/install and actual Fabric game startup pass.

---

# v0.7 — NeoForge

**Release decision (21 October 2026):** maintainer reported tests complete and requested moving to v0.8. Full official NeoForge installer smoke, core/desktop CI and three-platform packaging succeeded in [CI 39548448140](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39548448140). Public v0.7.0 is released with a *bounded* support claim; still-open optional compatibility, interruption and undocumented gameplay matrix checks stay unchecked. No undocumented test environments/results are inferred.

Goal: add a second major loader and prove the loader architecture is genuinely reusable.

## NeoForge provider

- [x] Discover NeoForge versions through official Maven metadata; installer runtime is wired into the common pipeline (v0.7 CI 21 October 2026).
- [x] Inspect modern NeoForge V2 install_profile.json/version.json; identify client processors and libraries.
- [x] Fetch official NeoForge installer SHA-256, use verified cache and inspect V2 data; real 1.21.1/21.1.213 official CI smoke passed on 21 October 2026.
- [x] Run V2 client processors in a child JVM, stage and verify generated output libraries; real official smoke passed. Wider game/OS compatibility remains an explicit limitation.
- [x] Resolve required V2 processor libraries through verified shared download infrastructure and stage generated artifact paths; validated against official 1.21.1/21.1.213 smoke.
- [ ] 🚧 IN PROGRESS — Stage generated/processed artifacts with collision checks; interruption, concurrency and multi-version recovery matrix remain open.
- [x] Store NeoForge loader id/version using the existing instance metadata schema and expose the compatible-version selector in desktop creation/editing.
- [x] Wire processor-backed NeoForge into common coordinator after Vanilla preparation and managed Java resolution; synthetic launch and official installer CI pass.

## Compatibility

- [ ] 🚧 IN PROGRESS — Generic Modrinth adapter already filters by the instance loader; NeoForge profile/UI integration added. Live install acceptance pending.
- [x] Read Forge/NeoForge full TOML syntax with bounded depth/size/counts, multi-mod identities and client required/incompatible declarations; multiline/escaped/quoted/inline and malformed/deep fixtures pass. See [C07](MILESTONE_CLOSURE.md#c07).
- [ ] Full installed dependency version-range reconciliation remains open; TOML ranges are preserved as advisory metadata.
- [x] Detect common incompatible loader combinations. Evidence: [C07](MILESTONE_CLOSURE.md#c07).

## v0.7 quality gate

- [x] NeoForge added through adapter boundary; Fabric and Quilt compile/run regression suite remains green.
- [x] NeoForge uses generic prepared-loader adapter; no NeoForge-specific branch added to account/process layers.
- [ ] 🚧 IN PROGRESS — Per-instance isolation is implemented; explicit multi-platform mixed-loader gameplay matrix remains unrecorded.

---

# v0.8 — Forge and Quilt

**Status: accepted for the documented support scope (21 October 2026).** The maintainer confirmed private Forge startup and Forge/Quilt mod installation/startup in this chat. No OS matrix or exact Quilt/mod versions were supplied. [The acceptance record and support matrix](TESTING_V0.8.md) distinguish that confirmation, official hosted installer tests and deterministic pipeline tests. Historical Forge V1 remains unsupported; modern Forge V2 discovery now follows exact official Minecraft coordinates, with four reference installation pairs documented; acceptance does not claim universal historical or native-platform compatibility. Development now proceeds to v0.9; no release/tag or product-version change is implied.

Goal: broaden loader support while preserving historical-version compatibility deliberately.

## Forge

- [x] Discover and numerically order official modern Forge V2 versions for the exact selected Minecraft version; recheck official membership before install. No single-pair whitelist. Evidence: [C08](MILESTONE_CLOSURE.md#c08).
- [x] Download and SHA-1 verify Forge V2 installer; inspect client processors and execute official 1.21.1/52.1.20 installation smoke (GitHub Actions 39560642818; reconfirmed by 39634747655).
- [x] Official V2 reference installations cover 1.21.1/52.1.20, 1.21.11/61.2.4, 26.2/65.1.5 and 26.3/66.1.0 with Mojang-declared managed Java. Historical V1 stays excluded and tested as rejected. Evidence: [C08](MILESTONE_CLOSURE.md#c08).
- [x] Forge V2 uses common PreparedModLoaderProvider and ProcessorInstallerV2Engine; official installation smoke, synthetic full coordinator regression and maintainer-confirmed private startup pass.
- [x] Read Forge/NeoForge mod identity and explicit required/incompatible dependencies using a bounded full-syntax TOML parser, prefer instance-matching manifests and warn on wrong loader families; deterministic regressions pass. Dependency version-range solving remains outside inventory interpretation.
- [x] Forge version and loader-filtered Modrinth browser available for the supported profile; private mod installation/startup confirmed by the maintainer on 21 October 2026.

## Quilt

- [x] Quilt Meta v3 discovery through the shared registry; deterministic regressions and maintainer-confirmed Quilt testing pass (20 October 2026).
- [x] Merge the official Quilt profile/libraries into the shared resolution/download pipeline; automated profile tests and maintainer-confirmed boot pass.
- [x] Quilt starts through the shared launch pipeline; real startup confirmed by the maintainer on 20 October 2026 and shared coordinator regression added on 21 October.
- [x] Inspect quilt.mod.json IDs/versions/direct requirements with bounded advisory semantics; deterministic inventory regressions pass. This does not promise complete dependency constraint solving.
- [x] Existing version/loader Modrinth filter and verified install enabled for Quilt; private mod installation/startup confirmed by the maintainer on 21 October 2026.

## Historical compatibility

- [x] Define legacy Java policy: use the component/major supplied by Mojang metadata, including Java 8 when explicitly declared; fail before download when absent rather than guessing from the Minecraft ID. The existing managed-runtime platform/component availability rules still apply.
- [x] Test representative old/new Forge-era behavior within the bounded matrix: legacy 1.12.2 V1 and unsupported persisted selections fail closed, modern 1.21.1/52.1.20 official installation and private startup pass. Legacy Java-8 launch planning is tested separately, without claiming a historical Forge boot.
- [x] Legacy argument tokenization, classpath/native fallback and modern JVM/game arguments remain in core resolvers/planner; missing runtime metadata is rejected by the coordinator. No UI-era launch recipes introduced.

## v0.8 quality gate

- [x] Vanilla/Fabric/NeoForge/Forge/Quilt use one launcher core; a deterministic regression exercises all five real adapters, preparation, runtime selection, plan/process wiring, shared client cache and separate game directories. It uses synthetic metadata and Java subprocesses, not a Minecraft game boot.
- [x] Loader-specific behavior stays in adapters; core account/process layers have no loader-ID branches. Official Forge/NeoForge installer CI and shared pipeline regressions pass.
- [x] Supported version matrix, negative historical cases, metadata-driven Java policy and evidence boundaries are documented in TESTING_V0.8.md and tested. Hosted baselines 39634747655 and 39647415857 passed, including all three portable packages; the newly added audit/pack tests have local verification and await their own CI run.

## Optional compatibility expansion outside the accepted matrix

- [x] Expand modern V2 discovery and official processor acceptance to three additional current Minecraft versions; actual new-pair gameplay remains in the OS/game matrix. Evidence: [C08](MILESTONE_CLOSURE.md#c08).
- [ ] Historical Forge V1 is outside the current supported matrix; implementation and real old-game acceptance remain future compatibility work.
- [ ] Complete Quilt conditional/group/version-range and installed dependency solving. TOML syntax is now parsed fully; Quilt optional absence no longer raises required-missing warnings and complex expressions are explicitly advisory (see v0.6 reconciliation).
- [ ] Record an explicit OS/version/mod matrix for mixed-loader gameplay, native UI and interruption/recovery; existing cross-milestone/platform release gates remain open.

---

# v0.9 — OptiFine, Shaders and Resource Packs

Goal: manage graphics-related instance content without treating every component as a generic mod loader.

**Status: accepted within the documented support scope (21 October 2026).** After implementation, the maintainer confirmed private in-game texture activation, shaders with automatic Iris setup and Vanilla 1.21.1 / OptiFine J1 startup. No OS, exact Iris/loader/pack versions or logs were supplied. Official installer smoke and deterministic test/build/Linux packaging also pass; this is not an all-platform/all-version certification. See [TESTING_V0.9.md](TESTING_V0.9.md). Development proceeds to v0.10; release/tag/product version are unchanged. Broader native/platform acceptance remains explicitly tracked under v1.0.

## Local pack foundation

- [x] Add a UI-independent core service for instance-local resource/shader ZIP listing, validated staged import and recoverable removal under the existing activity lock. Preserve original ZIPs, existing destinations, other instances, mods and game settings; reject redirected paths, malformed/unsafe archives, invalid metadata and corrupt entry checksums. Deterministic regressions and local `test build` pass.
- [x] Provide translated catalog and My packs controls: loading/empty/busy/error/retry/cancel, installed inventory, Use/Turn off, recoverable Remove/Undo, secondary local import and metadata/icon presentation. Verify interaction/rendering at 820×650 and 400×400 in EN/PL/DE. Native/gameplay acceptance is tracked separately below.
- [x] Add bounded streaming downloads for official installer sources without Content-Length; reject oversize responses without retry, delete partial staging and preserve existing targets. Shared download regressions pass.

## OptiFine

- [x] Define OptiFine integration strategy compatible with licensing/distribution constraints: per-user official download/installer only, no bundled/mirrored/publicly redistributed binaries.
- [x] Detect compatible OptiFine versions where technically and legally possible: official listing with initial bounded Vanilla 1.21.1 / HD U J1 matrix.
- [x] Install/integrate OptiFine without corrupting loader abstraction: managed Java, private staging/headless installer worker, versioned instance component state, content-addressed artifacts and validated common launch overlay. Actual official installer/overlay smoke passes; graphical startup remains a separate gate.
- [x] Clearly communicate unsupported combinations: catalog availability is bounded, changed game/loader refuses enhanced launch with Turn off guidance, documentation excludes historical versions and OptiFine with Forge/Fabric/Quilt/NeoForge.

## Shaders

- [x] Manage shader packs per instance through the compatible Modrinth catalog and installed-pack inventory.
- [x] Support validated intact ZIP import, recoverable removal and in-dialog Undo; activate explicitly through Use.
- [x] Track enabled/selected shader through Iris properties, retaining unknown values and exact original-byte backups.
- [x] Resolve compatible Iris and its required dependency closure automatically through the existing mod pipeline; recheck game/loader/renderer hashes under the pack commit lock. Unsupported published combinations are not offered. ZIP download alone is never success.

## Resource packs

- [x] Browse/search compatible resource packs in the launcher; resolve required pack dependencies, verify/download/validate a whole batch and activate it with one Install action. Keep previous pack order and unrelated options, back up original settings, reject collisions/changed game versions and reuse identical files. Actual Minecraft activation remains a separate release check.
- [x] Manage resource packs per instance through My packs; enable/disable, import local ZIPs as a secondary action, recoverable Remove/Undo without replacing collisions.
- [x] Show bounded metadata/description and optional ZIP pack.png where available; malformed presentation data does not prevent listing.
- [x] Change selected resource-pack priority explicitly while preserving unrelated options/unknown selections and backing up original settings. Verify selection/removal/recovery/priority with deterministic regressions.

## v0.9 quality gate

- [x] Graphics integrations remain instance-scoped; deterministic isolation and unchanged-world/settings tests pass.
- [x] OptiFine remains a separate graphics component, outside the generic mod-loader registry; common pipeline and official profile overlay checks pass.
- [x] Existing mod management remains intact; full core/desktop regression suite passes, including repeated installs, dependency preparation and busy-instance guards.
- [x] Obtain explicit v0.9 in-game acceptance: the maintainer confirmed texture activation, shaders with automatic Iris setup and Vanilla 1.21.1 / OptiFine J1 startup in this chat after these changes. Exact OS/loader/renderer/pack matrix and extended native checks were not supplied; broader validation is tracked under v1.0.

---

# v0.10 — Modpacks

Goal: install/export repeatable instance definitions and support a mainstream modpack format.

**Status: implementation and installation acceptance complete within the documented Modrinth client scope (22 October 2026).** The child-first catalog installs required files and prepares the exact Minecraft/loader/managed runtime before a new UUID becomes visible and selected. Real Simply Optimized 1.21.1-6.0 / Fabric 0.17.3 cold installation and original export pass; no graphical Minecraft boot is claimed. Normal tests verify isolation/failure cleanup and translated UI. Broader gameplay/native matrix remains under v1.0. See [TESTING_V0.10.md](TESTING_V0.10.md). No release/tag/product-version change is implied.

## Modpack model

- [x] Define a UI-independent deterministic modpack installation plan with exact requested Minecraft/loader versions, required/optional client files, mirrored URLs, sizes, SHA-1/SHA-512 and retained unknown manifest metadata.
- [x] Validate format-1 Minecraft index UTF-8/bounds, known single-loader requirements, client/server environments, supported HTTPS sources, both hash fields and portable nonconflicting destinations without HTTP or filesystem mutation.
- [x] Map modpack manifest to instance creation: allocate a fresh UUID with exact game/loader, privately stage mutable content and publish only after successful verified preparation.
- [x] Preserve overrides/configs appropriately: common overrides followed by client overrides; dedicated-server overrides/files are not applied; unsafe/conflicting destinations and corrupt ZIP entries fail before game publication.
- [x] Verify downloaded files using declared SHA-1, SHA-512 and byte count through the shared downloader, with bounded transfers and mirror fallback.
- [x] Keep user-created mutable data safe on update/reinstall: every install/new release creates a separate game; existing games/worlds/configs are never installation targets.
- [x] Add transactional prepared-instance publication without holding the repository mutex throughout downloads; existing instance listing/launch leases stay responsive. Failed/cancelled staging never appears as a completed game.

## Modrinth modpacks

- [x] Browse/search ready-made modpacks/sets in the same child-first catalog and a home action available before creating a saved game; Install automatically prepares a separate playable game and selects it. Exact game/loader support is checked before publication; invalid/unsupported requirements have a recoverable translated error. File import remains secondary.
- [x] Import `.mrpack` as a bounded validated archive; verify payload CRC/sizes, index and client override destinations without following ZIP attributes/links.
- [x] Resolve exact Minecraft version and loader against existing Mojang/loader services without silent version replacement.
- [x] Install referenced required client files/dependencies, excluding optional and dedicated-server files under the documented initial policy.
- [x] Apply validated client overrides in the correct order.
- [x] Create a new isolated instance only after required game, loader and managed Java preparation; reuse the common Play preparation path without an account/process launch.
- [x] Report unsupported/invalid packs, checksum failures, network, runtime and storage failures distinctly; show install/progress/cancel/retry states, with no false Ready result.

## Export

- [x] Export a compatible reconstructable pack definition where feasible: export the verified original archive for an imported/catalog-installed pack through My packs. Arbitrary modified-instance export is outside this scope.
- [x] Do not add downloaded mod binaries, locally installed files, accounts or player worlds to exports; preserve author-supplied archive bytes/overrides instead of collecting a user's game directory. Original pack redistribution still follows its author's terms.
- [x] Preserve original manifest metadata/unknown properties and exact references/overrides; schema-1 receipt stores source SHA-512 and optional-file policy. Missing receipt leaves ordinary instances unchanged; unknown schemas/changed archives refuse export without mutation.

## v0.10 quality gate

- [x] A supported pack installs into a new instance from scratch: actual Simply Optimized 1.21.1-6.0 / Fabric 0.17.3 with required pack files, Minecraft libraries/assets and managed Java, then original export; no graphical game boot was performed.
- [x] Pack installation does not overwrite unrelated instances or an earlier installation of the same pack; deterministic world/config preservation and independent UUID tests pass.
- [x] Invalid/incomplete packs and cancelled/failed preparation never publish a falsely completed instance; deterministic CRC/hash/path/compatibility/cancellation/failure fixtures pass.
- [x] Catalog entry and one-click/retry/local import behavior render/interact in EN/PL/DE at wide/400×400 sizes; full regression tests/build and local Linux distributable pass.

---

# v0.11 — Diagnostics and Repair

**Implementation complete within the documented support scope.** Shared bounded crash classification is extended with file integrity scans, launch/runtime/mod evidence, an allowlisted support report and guarded explicit repair. Deterministic local tests cover repair/privacy/cancellation and translated compact UI; final native/real-game acceptance remains in v1.0. See [TESTING_V0.11.md](TESTING_V0.11.md).

## Evidence collection

- [x] Collect a launch plan summary without account/session or argument objects: main class and classpath count.
- [x] Capture selected Java runtime/version and required major.
- [x] Capture Minecraft/loader version, including the selected loader before first Play.
- [x] Capture installed mod identifiers, versions, enabled state and advisory dependency warnings.
- [x] Capture matching process exit code; reject stale previous-process evidence.
- [x] Parse bounded recent Minecraft latest.log through the existing classifier without reflecting raw text.
- [x] Inspect bounded recent crash reports; reject stale/symlink sources.

## Diagnostic engine

- [x] Structured finding codes, confidence, evidence category and safe repair hints.
- [x] Missing Java/executable and Java-version symptoms.
- [x] Missing/corrupt managed client/library/native/index/logging/asset files; explicitly count files without upstream checksums.
- [x] Common loader/dependency symptoms and inventory notices.
- [x] Evidence-backed duplicate/incompatible mod symptoms; no full version-range solver is claimed.
- [x] Separate confirmed file/runtime checks from probable log signatures and informational summaries.
- [x] Unknown failures remain unknown; a successful scan does not promise mod compatibility.

## Repair

- [x] Explicitly verify/repair Minecraft managed files with common preparation under the instance activity lease.
- [x] Re-download missing/corrupt assets/libraries using the existing verified downloader.
- [x] Recheck/reinstall managed Java through existing manifest-backed runtime resolution.
- [x] Suggest reviewing/updating/disabling extras when dependency/conflict evidence exists; never change user mods silently.
- [x] Repair is user-triggered and does not delete worlds/mods/settings; no destructive repair action is offered.

## v0.11 UI

- [x] Translated human-readable failure findings and confidence labels.
- [x] Safe technical summary/copy and explicit local game-log folder access; no automatic upload.
- [x] Suggested actions for Java, mods, memory, graphics, downloads and disk space.
- [x] Explicit repair, progress, cancellation, completion/failure and refreshed scan; conflicting Play is guarded.

## v0.11 quality gate

- [x] Reports contain allowlisted evidence only; credential-bearing raw logs/commands/exceptions are not exported.
- [x] Repair explains its scope; deterministic tests preserve worlds/options/mods and prove no game process or credential access.
- [x] Useful recent evidence supplements exit codes, with stale-process/privacy regression coverage retained.

---

# v0.12 — Production Hardening

Goal: prepare the launcher for stable public release.

## Self-update

**Explicit early delivery request (19 October 2026):** implement this v0.12 slice during current launcher development. Other v0.12 features and existing release gates remain unchanged. See [UPDATING.md](UPDATING.md).

- [x] Define update channel strategy: Stable uses non-prerelease GitHub Releases and SemVer; opt-in DEV/SNAPSHOT use isolated pre-releases and numeric CI run/attempt identity. Channel selection never permits an automatic rollback; remote and cached candidates must pass the same version/build comparison.
- [x] Isolate Linux jpackage child-launch state, preserve private startup diagnostics/exit codes, enable Skiko/JNA native access and initialize missing channel choices from the installed build; local native handoff and regression verification pass.
- [ ] 🚧 IN PROGRESS — Publish immutable DEV after verified main pushes and SNAPSHOT via manual main dispatch; persist translated launcher-channel choice and isolate cached recovery per channel. Current hosted CI 39901800185 passed all three platform bundles and public six-asset DEV publishing (dev-180.1-v0.7.1). Manual SNAPSHOT dispatch and native channel-switch/update acceptance remain open; the earlier HTTP 403 is historical.
- [x] Implement pre-home packaged startup checks, bounded network checks and Retry/Continue UI; development runs bypass execution. Mocked HTTP and Compose receipt/recovery tests pass.
- [x] Download through the shared client/downloader with SHA-256, length verification, cancellation, retries, `.part` staging and cached archive revalidation.
- [x] Verify GitHub release asset digest and per-file manifest SHA-256; reject wrong platform/version, unsafe paths/links, duplicate/extra ZIP entries and oversized packages.
- [x] Install app images beside the original, reuse verified images, repair into a fresh stage and atomically publish installation/state; a matching child startup receipt activates the image. Local Linux full-bundle extraction/revalidation passes.
- [x] Preserve active/previous state and original executable, terminate failed/timed-out children and fall back to a verified cached application; tested with a real Java child plus failure/cancellation/offline fixtures.
- [x] Generate consistent semantic build metadata, manifest-driven update ZIPs and an explicit full-bundle verification task; Python fixture tests pass.
- [ ] 🚧 IN PROGRESS — CI now runs desktop tests and packages/verifies all three update bundles, then publishes complete six-asset releases for matching version tags or eligible stable version bumps on `main`. Hosted DEV/Stable publication and all platform bundles are verified through CI 40067168228; actual native updater handoff/recovery on Windows/macOS remains open.
- [ ] Live old→new GitHub-hosted update, original-shortcut relaunch, offline/cancel/failed-child recovery, native permissions/trust and user-data continuity on Windows/Linux/macOS.
- [x] Define retention policy: keep original/active/previous/orphan update images and archives for recovery; document explicit offline cleanup. No automatic pruning.
- [ ] Release signing/notarization before production distribution; maintainer confirms no certificates/Apple account yet. Obtain prerequisites through [SIGNING.md](SIGNING.md), then integrate and verify native trust.

## Reliability

- [x] Managed Minecraft file integrity scan with validated asset-index traversal; launcher images already use manifest validation. Unchecksummed upstream files are reported separately.
- [x] Explicit guarded verified game/Java/loader preparation repairs managed cache corruption without deleting player data.
- [x] Bounded transient retry and verified cache reuse; interrupted payloads restart from zero rather than claiming HTTP Range resume.
- [x] Cooperative download/preparation/repair cancellation and cleanup; retry after cancelled repair is tested.
- [x] Central connect/request/socket deadlines and bounded operation-specific update/artifact overrides; see TESTING_V0.12.md.
- [x] Initial network scope explicitly defined as direct HTTPS; configurable authenticated proxy support is excluded from this release scope.
- [x] Shared per-download disk admission with 16 MiB safety margin, plus existing staged-instance/update checks; concurrent storage reservation is not claimed.
- [x] Verified private staging and atomic publication reused for game/runtime/modpack/updater operations; incomplete payloads never count as ready.

## Packaging

- [x] Windows x64 portable complete app-image installation and data/removal policy documented; clean-machine acceptance stays below.
- [x] Linux x64 portable image with preserved executable permissions; locally built distribution and data/removal policy documented.
- [x] macOS ARM64 complete app-bundle portable installation documented; native trust/signing acceptance stays open.
- [x] Shared icon, canonical product/build metadata and derived native versions integrated; final native visual acceptance remains in v1.0.
- [x] Explicit player selection, Vanilla/modded setup and one-action catalog installation provided; child/native usability acceptance remains in v1.0.
- [x] INSTALLATION.md documents backup, app-only removal, explicit data removal, OS credentials and updater retention.

## Native installers — requested 23 October 2026

- [x] Windows x64 WiX 7.0.0 MSI with a per-user wizard, Start menu shortcut, stable upgrade identity, same-core duplicate blocking and native install/payload/repair/removal/data-preservation CI smoke (40011156165).
- [x] macOS ARM64 branded DMG with Applications link and native verification/mount/copy/removal smoke (40011156165).
- [x] Linux x64 DEB/RPM plus pinned-tool AppImage, validated desktop integration, DEB/AppImage startup and Fedora 44 RPM dependency-resolved install/removal/data-preservation checks (CI 40013941601).
- [x] Require complete installers plus SHA-256 inventory alongside portable/update assets before DEV/SNAPSHOT/future Stable publication; preserve existing updater bootstrap/user-data ownership. DEV 188.1 published all 12 assets; the five inventory hashes match public GitHub asset digests (CI 40013941601).
- [ ] Clean-machine GUI/trust, representative cross-version upgrades, RPM graphical startup and additional distribution acceptance; signing/notarization stay independent gates.

## CI/CD

- [x] Read-only per-version acceptance audit runs in GitHub Actions; current canonical version and phase checklist remain separate (verified CI 40063797333). Evidence: [C12](MILESTONE_CLOSURE.md#c12).
- [x] Automatic Stable publication on canonical plain-version increases verified by Stable v0.9.2 / CI 40005510889; DEV 190.1 publishes all 12 current assets. Canonical version is 0.9.3-DEV; no new Stable publication is authorized. Evidence: [C12](MILESTONE_CLOSURE.md#c12).
- [x] Existing automated core/desktop and Python verification; this change adds diagnostics/repair/logging regressions.
- [x] Existing hosted Linux/Windows/macOS packaging baseline 39901800185; new revision must pass its own CI before release.
- [x] Existing portable app images and verified update bundles for all three published targets.
- [x] Existing canonical SemVer/tag matching and preview CI identity tests; no version bump/tag is authorized by this implementation task.
- [x] Existing release helper plus development release notes and final candidate checklist in RELEASE_NOTES.md.
- [x] Common JVM dependency graph locked; native Compose/Skiko versions derive from the pinned host-specific distribution. Byte-identical installers are not claimed.

## UX polish

- [x] Settings groups build identity, update channel, Mojang-review status, bounded launcher log and actual storage locations.
- [x] Existing preparation/current-file and completed/total progress with cancellation; repair uses the same visible status.
- [x] Existing catalog states retained; health/settings add scan/loading/empty-log/error/retry and explicit repair outcomes.
- [ ] 🚧 IN PROGRESS — Standard Compose controls and reachable 400×400 repair footer verified; native focus/keyboard/accessibility acceptance remains a final gate.
- [x] Health findings and operation outcomes localized in EN/PL/DE; technical report remains secondary. Broader native usability acceptance stays in v1.0.
- [x] Bounded local launcher-log viewer, rotating redacted messages and allowlisted diagnostic-copy report; raw game logs are not exported.

## v0.12 quality gate

- [ ] Clean installation works on supported operating systems.
- [ ] Upgrade from an earlier supported build preserves user instances/accounts/settings appropriately.
- [x] Existing hosted three-platform six-asset baseline 39901800185; current candidate CI is checked independently after push.
- [x] Existing integrity/staging/failed-child fixtures plus repair cancellation/retry coverage; native interruption acceptance remains explicitly open.

---

# v1.0 — Stable Release

Goal: declare the architecture and primary feature set stable for end users.

## Supported feature set

- [x] Microsoft identity selection and fresh verification enforcement in source; final AppID/live/native gameplay gates remain independent.
- [ ] Microsoft premium accounts.
- [ ] Vanilla Minecraft versions within documented support matrix.
- [x] Automatic Java runtime management. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Isolated instances. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Fabric. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] NeoForge. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Forge. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Quilt. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Local mod management. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [ ] 🚧 IN PROGRESS — Modrinth search/install/transactional updates, required and optional dependency choices implemented; full installed-version reconciliation remains open.
- [x] OptiFine integration within documented compatibility limits. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Shader management. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Resource-pack management. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Supported modpack import/export flow. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Diagnostics and repair. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [x] Launcher self-update. Implementation verified within [published scope](SUPPORT_MATRIX.md); final native/gameplay gates below remain independent.
- [ ] Windows/Linux/macOS support according to published matrix.

## Documentation and release readiness

- [ ] README reflects actual v1.0 behavior.
- [ ] Architecture documentation matches code.
- [x] User installation/use documentation exists. See [installation](INSTALLATION.md), [support matrix](SUPPORT_MATRIX.md) and [closure evidence](MILESTONE_CLOSURE.md).
- [x] Authentication/security behavior is documented. See [installation](INSTALLATION.md), [support matrix](SUPPORT_MATRIX.md) and [closure evidence](MILESTONE_CLOSURE.md).
- [x] Supported Minecraft/loader/platform matrix is published. See [installation](INSTALLATION.md), [support matrix](SUPPORT_MATRIX.md) and [closure evidence](MILESTONE_CLOSURE.md).
- [x] Known limitations are documented. See [installation](INSTALLATION.md), [support matrix](SUPPORT_MATRIX.md) and [closure evidence](MILESTONE_CLOSURE.md).
- [ ] Migration path from pre-1.0 launcher state is tested/documented.
- [ ] License/third-party notices are correct.
- [ ] Release notes are prepared.

## v1.0 quality gate

- [ ] Obtain trusted Windows signing and Apple Developer ID/notarization, integrate before update manifests and verify native trust. External prerequisites are documented in [SIGNING.md](SIGNING.md).

- [ ] Complete native graphics-catalog/installed-pack window, focus, keyboard, DPI and live cancellation acceptance across the published supported platforms; record exact game/loader/renderer/pack combinations and extended texture priority/restart/recovery behavior. Moved from v0.9 on 21 October 2026 following bounded private maintainer acceptance; not executed/certified by that confirmation.
- [ ] Full automated test suite passes.
- [ ] Release builds pass on supported target platforms.
- [ ] Representative vanilla and each supported loader launch successfully.
- [ ] Microsoft account flows, Java entitlement and verified-profile gameplay are confirmed on real eligible accounts.
- [ ] Modrinth install/update flow is verified.
- [ ] Modpack installation is verified.
- [ ] Repair workflow is verified on representative failures.
- [ ] No known critical data-loss/security defects remain open.
- [ ] `docs/WORKLOG.md` and this roadmap reflect final release state.

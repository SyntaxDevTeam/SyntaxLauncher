# SyntaxLauncher Roadmap

This file is the canonical project progress tracker.

Status rules:

- `- [ ]` — not completed
- `- [ ] 🚧 IN PROGRESS` — partially implemented, not complete
- `- [x]` — implemented and acceptance criteria satisfied

Agents and contributors MUST keep this file synchronized with actual repository state. See `AGENTS.md`.

---

# v0.1 — Minimal Vanilla Launcher

Goal: launch a selected vanilla Minecraft version using an offline account and an automatically resolved Java runtime.

**Current status:** 🚧 Implementation in progress. The Kotlin/Compose foundation, offline account primitives, Mojang version catalog, selected-version metadata resolver, Mojang rule evaluation, file planning, asset-index parsing, safe native extraction primitives and the first Java runtime boundary now exist. Actual game-file downloads, cache/integrity execution, managed Java installation, final launch-plan construction and process launch remain open.

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
- [ ] Persist last-used offline username without storing unnecessary sensitive data.
- [ ] 🚧 IN PROGRESS — offline nickname field and explanatory text are present in the v0.1 UI; final account-state integration remains pending.

## Minecraft version discovery

- [ ] 🚧 IN PROGRESS — official Mojang `version_manifest_v2.json` fetch service implemented; full Gradle/runtime verification remains pending.
- [ ] 🚧 IN PROGRESS — version manifest parser and DTO/domain mapping implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — release/snapshot/legacy type mapping implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — release filtering is implemented in the first Compose UI; full UI build/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — snapshots can be explicitly selected through the channel filter; full UI build/manual verification remains pending.
- [ ] Cache version manifest safely.
- [ ] 🚧 IN PROGRESS — version-catalog load failures produce a visible error and Retry action; typed network errors remain pending.

## Version metadata resolver

- [ ] 🚧 IN PROGRESS — selected version metadata download service implemented and wired to UI; full live/Gradle verification remains pending.
- [ ] 🚧 IN PROGRESS — version id/type parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — client download metadata parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — library/download/classifier metadata parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — asset-index metadata parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — main class parsing implemented; Gradle tests remain pending.
- [ ] 🚧 IN PROGRESS — modern JVM/game arguments and legacy `minecraftArguments` parsing implemented; final launch substitution remains pending.
- [ ] 🚧 IN PROGRESS — Java requirement/component metadata parsing implemented; managed runtime resolution remains pending.
- [ ] 🚧 IN PROGRESS — OS/version/architecture and feature rules parsed and normalized; Gradle tests remain pending.
- [ ] Support metadata inheritance where required by supported versions.
- [ ] 🚧 IN PROGRESS — deterministic ordered allow/disallow rule evaluation implemented with test sources; Gradle execution remains pending.
- [ ] 🚧 IN PROGRESS — representative modern/legacy metadata fixture tests added; full `./gradlew test` remains pending.

## Minecraft files

- [ ] Download Minecraft client JAR.
- [ ] Verify client file hash when metadata provides one.
- [ ] 🚧 IN PROGRESS — required classpath libraries are selected for the current OS/architecture/rules and planned to cache paths; execution verification remains pending.
- [ ] Download missing libraries.
- [ ] Verify library hashes where available.
- [ ] 🚧 IN PROGRESS — native classifier selection including `${arch}` replacement implemented and tested in source; Gradle execution remains pending.
- [ ] Download native libraries.
- [ ] 🚧 IN PROGRESS — safe native extractor implemented with `META-INF` exclusion and Zip Slip protection; download/extraction lifecycle integration remains pending.
- [ ] Fetch asset index.
- [ ] 🚧 IN PROGRESS — asset-index content parser and Mojang asset-object URL/cache planner implemented; actual index fetch/download remains pending.
- [ ] Download missing assets.
- [ ] Reuse valid cached assets/libraries.
- [ ] Avoid treating incomplete downloads as valid files.

## Download infrastructure

- [ ] 🚧 IN PROGRESS — shared Ktor `HttpClient` factory with timeouts, serialization and SyntaxLauncher User-Agent added; reusable streaming file downloader is still pending.
- [ ] Support cancellation.
- [ ] Add bounded concurrent downloading.
- [ ] Report progress to application/UI layer.
- [ ] Add retry policy for safe transient failures.
- [ ] Add checksum/integrity verification support.
- [ ] Use atomic/finalized file writes where practical.
- [ ] Provide typed download/network errors.

## Java Runtime Manager

- [ ] 🚧 IN PROGRESS — Java requirement is parsed from selected Minecraft metadata; full version/runtime verification remains pending.
- [ ] 🚧 IN PROGRESS — `JavaRuntime` model implemented; full build verification remains pending.
- [ ] 🚧 IN PROGRESS — `JavaRuntimeManager` service boundary defined; managed installation implementation remains pending.
- [ ] 🚧 IN PROGRESS — local runtime inspector can validate a Java home/executable and read its major version; managed-runtime inventory/reuse policy remains pending.
- [ ] Resolve a supported runtime distribution/source.
- [ ] Download missing runtime.
- [ ] Verify runtime installation integrity sufficiently for launch.
- [ ] 🚧 IN PROGRESS — platform-specific `bin/java` vs `bin/java.exe` discovery exists in the runtime inspector; installation integration remains pending.
- [ ] 🚧 IN PROGRESS — runtime inspector executes `java -version` and parses modern/legacy version strings; full tests remain pending.
- [ ] Return clear runtime installation/selection errors.
- [ ] 🚧 IN PROGRESS — runtime models/services remain in `launcher-core` and independent from Compose UI; full build verification remains pending.

## Launch planning

- [ ] 🚧 IN PROGRESS — `LaunchOptions` model defined with validated minimum/maximum RAM and extra JVM args; final builder integration remains pending.
- [ ] 🚧 IN PROGRESS — `LaunchRequest` defined around common account, resolved version, Java runtime, platform and options; final builder integration remains pending.
- [ ] 🚧 IN PROGRESS — target classpath is planned from selected platform libraries plus client JAR; final launch command construction remains pending.
- [ ] Substitute Minecraft argument placeholders correctly.
- [ ] 🚧 IN PROGRESS — Mojang rule filtering for JVM/game arguments is implemented; final launch builder application remains pending.
- [ ] 🚧 IN PROGRESS — memory bounds exist in `LaunchOptions`; Compose RAM value is not yet wired into a final request/plan.
- [ ] Configure assets directory/index.
- [ ] Configure natives path.
- [ ] Configure game directory.
- [ ] Configure offline username and UUID.
- [ ] Build final launch command/plan without starting the process.
- [ ] Unit test launch argument construction with fixtures.

## Process management

- [ ] Start Minecraft using resolved Java runtime.
- [ ] Capture process stdout/stderr.
- [ ] Write Minecraft process log separately from launcher log where practical.
- [ ] Expose running/stopped/crashed process state.
- [ ] Capture exit code.
- [ ] Prevent duplicate accidental launches from one launch action.
- [ ] Handle launcher shutdown while game is running deliberately.

## v0.1 UI

- [ ] 🚧 IN PROGRESS — offline nickname input and validation state implemented; persistence remains pending.
- [ ] 🚧 IN PROGRESS — Minecraft version selector backed by the Mojang manifest implemented; full build/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — release/snapshot channel filter implemented; full build/manual verification remains pending.
- [ ] 🚧 IN PROGRESS — RAM slider implemented; value is not yet persisted or wired into final launch options.
- [ ] Play button.
- [ ] Download/preparation progress.
- [ ] 🚧 IN PROGRESS — catalog plus selected-version metadata loading/error/retry states are implemented; download/runtime/launch errors remain pending.
- [ ] Disable conflicting controls during critical operations.
- [ ] Remember last selected version/settings.

## v0.1 quality gate

- [ ] `./gradlew test` passes.
- [ ] `./gradlew build` passes.
- [ ] Fresh launcher state can download prerequisites and start a supported vanilla Minecraft release.
- [ ] Second launch reuses valid cached data instead of redownloading everything.
- [ ] Offline identity remains stable for the same username.
- [ ] Java is selected automatically without requiring a globally installed Java when managed runtime installation succeeds.
- [ ] No premium authentication/mod/loader feature has leaked into v0.1 scope unnecessarily.

---

# v0.2 — Instance Manager

Goal: separate user installations into isolated, manageable Minecraft instances before introducing mods.

## Instance model

- [ ] Add `MinecraftInstance` domain model.
- [ ] Add stable instance id independent of display name.
- [ ] Add instance schema version.
- [ ] Store Minecraft version in instance metadata.
- [ ] Store RAM/JVM options per instance.
- [ ] Store game directory reference/derivation.
- [ ] Add serialization/deserialization tests.

## Instance storage

- [ ] Create isolated game directory per instance.
- [ ] Keep saves/config/resource packs/screenshots isolated.
- [ ] Continue reusing safe shared immutable caches.
- [ ] Add instance configuration persistence.
- [ ] Handle malformed instance metadata without deleting user data.
- [ ] Define migration mechanism for future schema changes.

## Instance management

- [ ] Create instance.
- [ ] Rename instance.
- [ ] Edit instance settings.
- [ ] Delete instance with explicit user-data behavior.
- [ ] Clone instance.
- [ ] List/sort instances.
- [ ] Select default/last-used instance.

## Launch integration

- [ ] Launch selected instance through existing launch pipeline.
- [ ] Remove global mutable game-directory assumptions.
- [ ] Make per-instance settings override launcher defaults where defined.

## v0.2 UI

- [ ] Instance list/grid.
- [ ] Create-instance flow.
- [ ] Instance settings screen.
- [ ] Clone/delete actions.
- [ ] Selected instance becomes primary Play target.

## v0.2 quality gate

- [ ] Existing v0.1 vanilla launching remains functional.
- [ ] Two instances of the same Minecraft version keep mutable game state isolated.
- [ ] Instance clone preserves intended data/settings.
- [ ] Instance metadata round-trip tests pass.
- [ ] Migration/versioning mechanism exists before later fields are added.

---

# v0.3 — Microsoft / Premium Accounts

Goal: add official premium authentication without changing the fundamental Minecraft launch pipeline.

## Account management

- [ ] Introduce account manager/service.
- [ ] Preserve offline account support.
- [ ] Add Microsoft account implementation/session model.
- [ ] Support multiple saved accounts.
- [ ] Allow account selection per launch or as launcher default.
- [ ] Display account type clearly.

## Microsoft authentication

- [ ] Implement browser-based Microsoft OAuth flow suitable for desktop apps.
- [ ] Use PKCE where applicable.
- [ ] Never request/store Microsoft password.
- [ ] Exchange Microsoft identity for Xbox Live authentication.
- [ ] Complete XSTS authentication.
- [ ] Authenticate with Minecraft Services.
- [ ] Fetch Minecraft profile.
- [ ] Obtain premium username and UUID.
- [ ] Detect account/profile errors cleanly.

## Token lifecycle

- [ ] Refresh expired/expiring sessions automatically when possible.
- [ ] Store long-lived secrets/tokens using an appropriate secure OS mechanism.
- [ ] Redact tokens from logs and exceptions.
- [ ] Support sign-out/revoke-local-session behavior.
- [ ] Handle refresh failure by requesting authentication again.

## Launch integration

- [ ] Convert authenticated account/session to existing launch account/session inputs.
- [ ] Supply valid access token/profile information to Minecraft launch arguments.
- [ ] Avoid a duplicate premium-specific launch pipeline.

## v0.3 UI

- [ ] Add account picker.
- [ ] Add `Add Microsoft account` flow.
- [ ] Preserve `Offline account` creation/selection.
- [ ] Show premium profile name/avatar when available.
- [ ] Provide logout/remove-account controls.

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

- [ ] Define update channel strategy.
- [ ] Check for launcher updates.
- [ ] Download update safely.
- [ ] Verify update artifact integrity/signature as appropriate.
- [ ] Apply updates atomically/recoverably.
- [ ] Support rollback/failure recovery strategy where practical.

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
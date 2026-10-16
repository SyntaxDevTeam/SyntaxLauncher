# SyntaxLauncher Worklog

This file is the append-only development journal for repository-changing work.

Newest entries go first, directly below this introduction. Historical entries should not be rewritten except to correct an obvious factual error.

Every code/build/documentation/CI task must add an entry according to `AGENTS.md`.

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
- `./gradlew build` — NOT RUN; repository currently contains documentation only and no Gradle project yet.
- Manual verification — documentation structure and cross-document milestone ordering reviewed for consistency.

### Roadmap updates

- Project roadmap created and initialized.
- No implementation milestone item is marked complete because application code has not been created yet.
- Documentation-bootstrap items are tracked separately from functional milestone completion.

### Follow-ups / known issues

- v0.1 implementation has not started.
- The exact supported desktop OS/architecture matrix should be finalized during v0.1 implementation based on chosen Java runtime distribution and Compose packaging constraints.
- Dependency versions are intentionally not frozen in documentation; Gradle files will become authoritative once the project is initialized.

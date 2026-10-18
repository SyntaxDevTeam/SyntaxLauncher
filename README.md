# SyntaxLauncher

SyntaxLauncher is a modern Minecraft launcher from SyntaxDevTeam, written primarily in Kotlin and developed incrementally from a minimal vanilla launcher into a modular launcher with premium authentication, isolated instances, mod loaders, Modrinth integration, modpacks and diagnostics.

The project is currently in **active v0.1 development**. The Kotlin/Compose launcher now connects the offline account, Mojang metadata, verified game preparation, automatic Java runtime installation, metadata-driven LaunchPlan and MinecraftProcessManager to an actual desktop Play workflow. A real clean-machine Minecraft launch is not yet verified.

## Project philosophy

SyntaxLauncher is intentionally developed milestone by milestone.

The first release will **not** try to compete with mature launchers by implementing every feature at once. Instead, v0.1 establishes a clean launcher core capable of:

- using an offline/non-premium account;
- listing/selecting Minecraft versions;
- resolving Minecraft metadata, assets, libraries and natives;
- selecting and installing the Java runtime required by the chosen Minecraft version;
- building the launch command from metadata;
- starting and monitoring Minecraft;
- exposing a small Compose Desktop interface.

Later features must extend that core rather than replace it.

## Current implementation

The current v0.1 implementation includes:

- Gradle Kotlin DSL multi-module structure;
- `launcher-core` separated from `launcher-desktop`;
- Kotlin/JVM 21 toolchain configuration;
- Compose Desktop presentation layer;
- Coroutines, Ktor Client and kotlinx.serialization dependencies;
- SLF4J/Logback logging foundation;
- common `Account` boundary and `OfflineAccount` implementation;
- offline username validation and deterministic Minecraft-compatible offline UUID generation;
- Mojang version manifest DTOs, parser and catalog service;
- selected-version metadata resolver with SHA-1 verification;
- release/snapshot classification;
- Mojang OS/architecture/feature rule evaluation;
- library/native/asset planning;
- centralized verified download infrastructure with cache reuse, `.part` staging, atomic publication, retries, cancellation, bounded concurrency and progress events;
- game-file preparation for client JAR, libraries, natives, asset index, asset objects and logging configuration;
- safe native extraction with Zip Slip protection;
- automatic Mojang Java runtime discovery and installation driven by the Minecraft `javaVersion.component` metadata;
- official Mojang runtime-index and per-runtime manifest resolution for Windows, Linux and macOS platform mappings;
- verified runtime file downloads through the same shared downloader;
- managed runtime reuse without a network request when a compatible verified installation already exists;
- runtime staging, executable-bit handling, manifest link creation, completion markers and final `java -version` verification;
- support for the macOS `jre.bundle/Contents/Home/bin/java` layout;
- `MinecraftLaunchCoordinator` integrating verified selected-version metadata, game preparation, Java Runtime Manager, launch planning and ProcessManager, with cancellation and typed progress stages;
- `DesktopLauncherSession` owning background launches independently of Compose recomposition and detaching from a running game on window close;
- the real `Play Minecraft` and `Stop Minecraft` UI actions, download/Java stage progress, crash state, PID/exit code and separate game-process log paths;
- controlled `MockEngine` pipeline test sources with fake Java manager and a local Java child process;
- normalized `LaunchAuthentication` for offline sessions without coupling the launch builder to a raw nickname;
- metadata-driven `LaunchPlanBuilder` for modern JVM/game arguments and legacy `minecraftArguments` fallback;
- strict placeholder substitution for Mojang account/version/assets/natives/classpath/auth/launcher fields;
- platform-aware classpath construction, RAM JVM arguments and downloaded logging-config injection;
- structured `LaunchPlan` output containing runtime, working directory, main class, JVM args, game args and the final argument-vector command without shell concatenation;
- typed launch-plan failures for missing artifacts and unresolved future placeholders;
- deterministic launch-plan test sources covering modern and legacy argument construction, offline identity, logging, paths containing spaces and unknown-placeholder rejection;
- `MinecraftProcessManager` with native `ProcessBuilder` execution, per-run stdout/stderr log files, structured lifecycle/PID/exit status, duplicate-launch protection and explicit detach/terminate shutdown policy;
- controlled process tests including a real local Java child and synthetic crash/force-stop/detach scenarios;
- initial unit/integration test sources using Ktor `MockEngine` rather than live Mojang services;
- a lightweight Compose screen with nickname validation, release/snapshot selection, version selection, RAM control, loading state and retryable metadata errors.

The remaining v0.1 engineering work is full Gradle and cross-platform build verification, first real vanilla Minecraft launch, persistence for offline nickname/version/RAM, manifest cache and UI polish. The new Play action is functional in code but not yet validated against a real Mojang download and game startup.

## Continuous integration

The [Build and package SyntaxLauncher](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/workflows/build.yml) workflow runs on pushes to `main`, pull requests and manual dispatch. It verifies the core tests and desktop compilation on Java 21, then builds Compose `createDistributable` portable applications for Linux, Windows and macOS. Workflow artifacts contain the runnable app-directory output for each platform; they are not signed native installers. Gradle 9.4.0 is provisioned with `gradle/actions/setup-gradle` until an official committed wrapper is available. CI status must be green before a v0.1 test release is called build-verified. Local developers can use the official committed `./gradlew :launcher-core:test :launcher-desktop:run` (on Windows use `gradlew.bat`). Platform builds use `./gradlew :launcher-desktop:createDistributable`; output is under `launcher-desktop/build/compose/binaries/main/app/`.

## Technology

- Kotlin/JVM
- Gradle Kotlin DSL
- Compose Desktop
- Kotlin Coroutines
- Ktor Client
- kotlinx.serialization
- SLF4J + Logback
- JUnit-compatible Kotlin tests

Build files are authoritative for exact dependency versions.

## Release roadmap

| Version | Primary goal |
|---|---|
| **v0.1** | Vanilla + offline account + version selection + automatic Java + game launch |
| **v0.2** | Isolated Minecraft instances |
| **v0.3** | Microsoft / premium accounts |
| **v0.4** | Fabric loader |
| **v0.5** | Local mod manager |
| **v0.6** | Modrinth search, install, updates and dependency resolution |
| **v0.7** | NeoForge |
| **v0.8** | Forge + Quilt |
| **v0.9** | OptiFine + shaders + resource packs |
| **v0.10** | Modpacks |
| **v0.11** | Diagnostics and repair |
| **v0.12** | Production hardening, packaging, CI/CD and self-update |
| **v1.0** | First stable release |

The detailed task-by-task checklist is maintained in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Architecture at a glance

The launcher is designed around a UI-independent core:

```text
Compose Desktop UI
        ↓
Application/services
        ↓
Minecraft launcher core
        ↓
Metadata / downloads / Java / filesystem / authentication adapters
```

The v0.1 implementation starts with:

```text
SyntaxLauncher/
├── launcher-core/
│   └── src/
│       ├── main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/
│       │   ├── account/
│       │   ├── java/
│       │   ├── launch/
│       │   ├── minecraft/
│       │   ├── network/
│       │   └── storage/
│       └── test/kotlin/
├── launcher-desktop/
│   └── src/main/
│       ├── kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/
│       └── resources/
├── docs/
└── AGENTS.md
```

Future abstractions such as mod loaders should only be introduced when the relevant milestone needs them. The project explicitly avoids speculative overengineering.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full architecture specification.

## Accounts

### Offline / non-premium

Available from v0.1.

Offline accounts are intended for local play and servers configured to accept offline clients. SyntaxLauncher will not use offline accounts to bypass authentication on servers operating in online mode.

The current implementation already provides a common account model, offline username validation and deterministic UUID generation using Minecraft's `OfflinePlayer:<username>` convention. The launch layer converts that account into normalized offline authentication/session placeholder values rather than embedding offline-specific branching throughout command construction.

### Microsoft / premium

Planned for v0.3.

Premium authentication will use the official Microsoft → Xbox Live → XSTS → Minecraft Services flow appropriate for a desktop application. SyntaxLauncher must never ask users to type their Microsoft password into the launcher itself.

The v0.1 launch builder already consumes a normalized authentication payload so v0.3 can provide premium access token/profile values without introducing a second premium-only launch engine.

## Java runtime management

SyntaxLauncher manages Java automatically through a core service rather than UI-version tables.

The selected Minecraft version's metadata supplies a Java component and major version. `MojangJavaRuntimeManager` then:

1. maps the detected OS/architecture to Mojang's runtime platform id;
2. reuses an already installed matching managed runtime when one verifies successfully;
3. otherwise fetches Mojang's official runtime index;
4. selects the requested component (`jre-legacy`, `java-runtime-*`, etc.);
5. downloads and SHA-1 verifies the component manifest;
6. stages every runtime file using the common verified `DownloadManager`;
7. creates directories/links and applies executable flags declared by the manifest;
8. verifies the staged runtime using `java -version`;
9. publishes the completed runtime into launcher-managed storage;
10. verifies it again and returns a normalized `JavaRuntime` descriptor to the launch layer.

The runtime is selected from metadata, so support can follow Mojang runtime components such as Java 17, Java 21 and Java 25 without teaching Compose UI about Minecraft-to-Java version mappings.

## Launch planning

`LaunchPlanBuilder` consumes only resolved/prepared inputs:

```text
Account / LaunchAuthentication
        +
PreparedMinecraftVersion
        +
JavaRuntime
        +
LaunchOptions / platform / feature rules
        ↓
LaunchPlan
```

The builder resolves Mojang JVM/game arguments, substitutes supported `${...}` placeholders, applies platform-specific classpath separators, injects prepared assets/natives/libraries/game paths, supplies offline identity/session values, adds RAM and logging configuration arguments and produces one structured argument vector.

It does **not** create one large shell command string. Paths containing spaces remain individual process arguments, which is safer and avoids platform-specific quoting problems. Legacy metadata without an explicit JVM argument list receives a small compatibility fallback for natives/classpath instead of a hardcoded per-version launch recipe.

Unknown future placeholders fail explicitly during planning instead of being passed unresolved into Java. Process creation is intentionally a separate next layer.

## Instances

Starting with v0.2, mutable Minecraft state will be isolated by instance.

Conceptually:

```text
SyntaxLauncher/
├── cache/
│   ├── assets/
│   ├── libraries/
│   └── versions/
├── runtimes/
└── instances/
    ├── vanilla-main/
    │   ├── instance.json
    │   └── game/
    └── fabric-test/
        ├── instance.json
        └── game/
```

This allows worlds, configs, mods and resource packs to remain isolated while safe immutable caches can still be shared.

## Mod loaders and mods

Loader support is intentionally staged:

- Fabric — v0.4
- NeoForge — v0.7
- Forge — v0.8
- Quilt — v0.8

Local mod management arrives in v0.5, followed by Modrinth integration and dependency resolution in v0.6.

OptiFine is planned as an instance component/integration rather than being forced into a generic mod-loader abstraction.

## Design direction

SyntaxLauncher should be visually distinctive and modern without becoming visually heavy. The interface should attract attention through polish, hierarchy and detail rather than excessive effects or permanently visible technical controls.

The canonical UI/UX rules are maintained in [`docs/DESIGN.md`](docs/DESIGN.md).

## Documentation

- [`docs/ROADMAP.md`](docs/ROADMAP.md) — canonical detailed project checklist and quality gates.
- [`docs/MILESTONES.md`](docs/MILESTONES.md) — scope, exclusions and acceptance intent for every planned version.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, domain concepts and extension boundaries.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — engineering conventions, testing, security and implementation workflow.
- [`docs/DESIGN.md`](docs/DESIGN.md) — mandatory visual and interaction direction.
- [`docs/WORKLOG.md`](docs/WORKLOG.md) — append-only development journal describing completed work and verification.
- [`AGENTS.md`](AGENTS.md) — mandatory rules for AI/agent contributors.

## AI / agent contribution policy

Any AI or autonomous coding agent modifying this repository must follow [`AGENTS.md`](AGENTS.md).

Among other rules, every repository-changing task must:

- update the detailed roadmap accurately;
- mark only actually completed checklist items as complete;
- leave partial work visibly open;
- append a detailed entry to `docs/WORKLOG.md`;
- record tests/build/manual verification;
- document remaining blockers or follow-ups.

This is intended to prevent the project roadmap from drifting away from the real implementation state.

## Current status

**v0.1 — Minimal Vanilla Launcher: in progress**

Implemented so far: project/module foundation, offline account primitives, Mojang version discovery and full selected-version parsing, shared verified download infrastructure, client/library/native/assets preparation, platform-aware managed Mojang Java installation, metadata-driven final launch-plan construction and the first lightweight Compose Desktop screen.

Still required before v0.1 can be considered accepted: Gradle wrapper/full automated build, real clean-machine vanilla Minecraft launch, cross-platform verification, settings persistence, version-manifest cache and manual UI validation.

## License

See [`LICENSE`](LICENSE).
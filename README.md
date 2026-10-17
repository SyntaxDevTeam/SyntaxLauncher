# SyntaxLauncher

SyntaxLauncher is a modern Minecraft launcher from SyntaxDevTeam, written primarily in Kotlin and developed incrementally from a minimal vanilla launcher into a modular launcher with premium authentication, isolated instances, mod loaders, Modrinth integration, modpacks and diagnostics.

The project is currently in **active v0.1 development**. The launcher already has its Kotlin/Gradle/Compose foundation, offline identity model, Mojang version resolution, metadata/rule handling and a verified game-file download/preparation pipeline. Managed Java installation, final launch-command construction and Minecraft process management are the next core stages.

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
- release/snapshot classification;
- selected-version `version.json` download with manifest SHA-1 verification;
- normalized Windows/Linux/macOS and architecture detection;
- deterministic Mojang allow/disallow rule evaluation;
- modern JVM/game argument parsing plus legacy `minecraftArguments` support;
- client/library/native/asset-index/logging metadata resolution;
- Java runtime requirement parsing plus an initial runtime model/inspector boundary;
- platform-aware library/native selection and classpath planning;
- platform-aware launcher/cache/game/runtime directory layout;
- content-addressed Minecraft asset planning;
- safe native extraction with `META-INF` filtering and Zip Slip protection;
- centralized streaming `DownloadManager` with bounded concurrency, cancellation, retry/backoff, progress events and typed failures;
- `.part` staging, SHA-1/size verification and atomic publication of downloaded artifacts;
- verified cache reuse for existing launcher-managed files;
- `MinecraftPreparationService` executing client, library, native, asset-index, asset-object and logging-config preparation;
- deterministic test sources using Ktor `MockEngine` and temporary filesystem fixtures for download/cache/retry/integrity/preparation behavior;
- a lightweight Compose screen with nickname validation, release/snapshot selection, version selection, RAM control, loading/error states and selected-version metadata readiness.

The remaining critical v0.1 path is now: managed Java runtime discovery/download/install → final placeholder substitution and `LaunchPlan` → Minecraft process start/monitoring → Compose Play/progress flow → persistence and full clean-machine verification.

The UI intentionally does not expose a fake `Play` action before that pipeline exists.

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

The v0.1 implementation currently follows:

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

The current implementation already provides a common account model, offline username validation and deterministic UUID generation using Minecraft's `OfflinePlayer:<username>` convention.

### Microsoft / premium

Planned for v0.3.

Premium authentication will use the official Microsoft → Xbox Live → XSTS → Minecraft Services flow appropriate for a desktop application. SyntaxLauncher must never ask users to type their Microsoft password into the launcher itself.

## Java runtime management

SyntaxLauncher is intended to manage Java automatically.

The selected Minecraft version's metadata determines the Java requirement. The runtime manager will then:

1. find a compatible launcher-managed Java runtime;
2. reuse it when available;
3. otherwise download/install a compatible runtime;
4. verify it;
5. provide its executable to the launch planner.

The core already parses Minecraft's Java component/major requirement and includes a `JavaRuntime` model, `JavaRuntimeManager` boundary and local runtime inspector capable of locating `java`/`java.exe` and reading legacy/modern Java versions. Managed runtime source resolution, download, installation inventory and lifecycle are the remaining Java work for v0.1.

The UI must not hardcode assumptions such as `Minecraft X = Java Y`.

## Download and game-file preparation

v0.1 now has shared infrastructure for launcher-managed artifacts rather than separate download code for every Minecraft component.

The download pipeline:

1. verifies an existing target by expected size/SHA-1 and reuses it when valid;
2. streams missing/invalid content into a sibling `.part` file;
3. reports progress and supports coroutine cancellation;
4. retries explicitly classified transient failures with bounded backoff;
5. verifies size and SHA-1 before publication;
6. atomically moves the verified file into its cache target when supported;
7. never treats a partial file as a completed artifact.

`MinecraftPreparationService` applies that mechanism to the resolved version plan and prepares the client JAR, libraries, native archives, asset index, asset objects and client logging configuration before extracting verified natives.

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

Implemented so far: project/module foundation, offline account primitives, Mojang version discovery, selected-version metadata parsing and integrity verification, OS/architecture rules, libraries/natives/assets planning, verified concurrent download/cache infrastructure, game-file preparation, initial Java runtime boundary and the first lightweight Compose Desktop screen.

Still required before v0.1 can be considered functional: managed Java runtime installation/reuse, final launch argument placeholder substitution, `LaunchPlan`, process management, Compose Play/progress integration, settings/manifest persistence, Gradle wrapper/full build-test execution and a real clean-machine vanilla launch.

## License

See [`LICENSE`](LICENSE).

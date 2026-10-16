# SyntaxLauncher

SyntaxLauncher is a planned modern Minecraft launcher from SyntaxDevTeam, written primarily in Kotlin and developed incrementally from a minimal vanilla launcher into a modular launcher with premium authentication, isolated instances, mod loaders, Modrinth integration, modpacks and diagnostics.

The project is currently in the **documentation / pre-v0.1** stage. Application code has not been initialized yet.

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

## Planned technology

- Kotlin/JVM
- Gradle Kotlin DSL
- Compose Desktop
- Kotlin Coroutines
- Ktor Client
- kotlinx.serialization
- JUnit-compatible Kotlin tests

Exact dependency versions will be defined by Gradle files once implementation starts.

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

The initial v0.1 core is expected to contain concepts similar to:

```text
launcher-core
├── account/
│   ├── Account
│   └── OfflineAccount
├── minecraft/
│   ├── MinecraftManifest
│   ├── MinecraftVersion
│   ├── VersionResolver
│   ├── AssetManager
│   ├── LibraryManager
│   └── NativeManager
├── java/
│   ├── JavaRuntime
│   └── JavaRuntimeManager
├── launch/
│   ├── LaunchRequest
│   ├── LaunchOptions
│   ├── LaunchCommandBuilder
│   └── MinecraftProcess
├── network/
└── storage/
```

Future abstractions such as mod loaders should only be introduced when the relevant milestone needs them. The project explicitly avoids speculative overengineering.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full architecture specification.

## Accounts

### Offline / non-premium

Available from v0.1.

Offline accounts are intended for local play and servers configured to accept offline clients. SyntaxLauncher will not use offline accounts to bypass authentication on servers operating in online mode.

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

The UI must not hardcode assumptions such as `Minecraft X = Java Y`.

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

## Documentation

- [`docs/ROADMAP.md`](docs/ROADMAP.md) — canonical detailed project checklist and quality gates.
- [`docs/MILESTONES.md`](docs/MILESTONES.md) — scope, exclusions and acceptance intent for every planned version.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, domain concepts and extension boundaries.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — engineering conventions, testing, security and implementation workflow.
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

**Pre-v0.1 / documentation phase**

The next implementation milestone is **v0.1 — Minimal Vanilla Launcher**.

The v0.1 quality target is straightforward: on a supported clean system, a user should be able to enter an offline nickname, select a supported vanilla Minecraft version, let SyntaxLauncher prepare the required files and Java runtime, and start the game successfully.

## License

See [`LICENSE`](LICENSE).

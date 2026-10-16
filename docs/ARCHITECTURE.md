# SyntaxLauncher Architecture

## 1. Purpose

SyntaxLauncher is designed as a modular Minecraft launcher whose early releases remain intentionally small while preserving clean extension points for later authentication providers, mod loaders, mod repositories and diagnostics.

The architectural priority is not to predict every future feature. It is to prevent early shortcuts from making later milestones expensive or unsafe.

## 2. Technology baseline

Planned baseline:

- Kotlin/JVM
- Gradle Kotlin DSL
- Compose Desktop for desktop UI
- Kotlin Coroutines for asynchronous work
- Ktor Client for HTTP/API/download operations
- kotlinx.serialization for persisted configuration and remote metadata where practical
- a lightweight logging backend behind a project logging facade
- JUnit/Kotlin test tooling

Exact library versions are intentionally not pinned in this document. Build files are authoritative for versions.

## 3. Module direction

Target structure as the project grows:

```text
SyntaxLauncher/
├── launcher-core/
│   ├── account/
│   ├── minecraft/
│   ├── java/
│   ├── launch/
│   ├── instance/
│   ├── loader/
│   ├── mods/
│   ├── network/
│   ├── diagnostics/
│   └── storage/
│
├── launcher-desktop/
│   └── Compose Desktop UI
│
├── docs/
└── AGENTS.md
```

Not every package/module must exist in v0.1. They should appear when the corresponding milestone needs them.

Dependency direction:

```text
Desktop UI
   ↓
Application services
   ↓
Core domain/services
   ↓
Infrastructure adapters
```

Core code must not depend on Compose UI.

## 4. v0.1 core boundary

The first release should remain close to:

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
│   └── shared HTTP/download services
└── storage/
    └── LauncherPaths
```

No mod-loader abstraction is required until v0.4 unless implementation evidence shows a smaller seam is necessary earlier.

## 5. Domain concepts

### Account

Represents the identity/session used to start Minecraft.

Conceptual model:

```kotlin
sealed interface Account {
    val username: String
    val uuid: UUID
}
```

Initial implementation:

```kotlin
data class OfflineAccount(
    override val username: String,
    override val uuid: UUID
) : Account
```

Later Microsoft accounts add authentication/session fields without duplicating the launch pipeline.

The launch layer should depend on a resolved launch/session representation rather than branch throughout the codebase on account type.

### MinecraftVersion

Represents resolved Mojang version metadata, including:

- version id/type;
- downloads;
- libraries;
- asset index;
- main class;
- JVM/game arguments;
- Java requirement;
- OS/architecture rules;
- logging metadata;
- inheritance where applicable.

Mojang metadata is authoritative. Avoid version-specific hardcoded launch recipes.

### JavaRuntime

Represents one verified local Java runtime and its capabilities.

The runtime manager is responsible for:

- interpreting Minecraft's required Java component/version;
- finding a compatible existing managed runtime;
- downloading/installing a runtime when necessary;
- verifying the executable;
- returning a normalized runtime descriptor.

Minecraft launching code must not assume a fixed `java` path.

### LaunchRequest

The launch layer accepts explicit inputs instead of reaching into global state.

Conceptually:

```kotlin
data class LaunchRequest(
    val account: Account,
    val game: ResolvedGame,
    val runtime: JavaRuntime,
    val options: LaunchOptions
)
```

A later instance-aware variant may include a `MinecraftInstance` or resolved instance configuration.

### MinecraftInstance

Introduced in v0.2.

An instance is a user-visible installation/profile with isolated mutable game state.

Typical state:

- name/id;
- Minecraft version;
- loader selection/version when supported;
- game directory;
- memory and JVM settings;
- mods;
- configs;
- saves;
- resource packs;
- shaders;
- per-instance settings.

Persisted instance metadata must contain a schema version once user data exists.

## 6. Storage layout

Proposed conceptual layout:

```text
SyntaxLauncher/
├── config/
├── accounts/
├── logs/
├── runtimes/
├── cache/
│   ├── assets/
│   ├── libraries/
│   └── versions/
└── instances/
    ├── vanilla-26.2/
    │   ├── instance.json
    │   └── game/
    └── fabric-example/
        ├── instance.json
        └── game/
```

Exact operating-system paths must follow platform conventions. Do not assume the application directory is writable.

Shared immutable/cached artifacts may be centralized. Mutable player/game state belongs to the instance from v0.2 onward.

## 7. Minecraft resolution pipeline

The normal resolution sequence should be conceptually:

```text
Version manifest
      ↓
Version metadata
      ↓
Inherited metadata merge (when applicable)
      ↓
Rule evaluation for current OS/architecture
      ↓
Library resolution
      ↓
Native classifier resolution/extraction
      ↓
Asset index + assets
      ↓
Java requirement resolution
      ↓
Launch argument resolution
      ↓
Launch plan
```

The launcher should be able to create a complete `LaunchPlan` before starting the process. This makes validation, diagnostics and testing easier.

## 8. Download subsystem

Downloads are a cross-cutting infrastructure concern.

The subsystem should support, as needed by milestones:

- concurrency with bounded parallelism;
- cancellation;
- progress reporting;
- retry for transient failures;
- checksum/hash verification when metadata supplies hashes;
- atomic completion to avoid treating partial files as valid;
- cache reuse;
- meaningful error classification.

Do not duplicate raw download code in assets, libraries, runtimes and mod integrations.

## 9. Platform rules

Minecraft metadata may contain OS/architecture-specific rules and native classifiers.

The platform abstraction should normalize at least:

- Windows;
- Linux;
- macOS;
- CPU architecture where required.

Rule evaluation must be unit tested because incorrect filtering commonly produces broken classpaths or missing natives.

## 10. Java runtime strategy

The launcher should prefer managed runtimes for predictable behavior while allowing explicit user override later if desired.

Responsibilities:

1. determine required Java capability from resolved Minecraft metadata;
2. map it to an available runtime distribution/component;
3. reuse an already installed compatible runtime;
4. otherwise download and install it into launcher-managed storage;
5. verify that the executable works;
6. expose the selected runtime to the launch planner.

Do not reduce this to a static table embedded in UI code.

## 11. Authentication architecture

### Offline accounts — v0.1

Offline accounts require:

- username validation;
- deterministic offline UUID generation compatible with Minecraft's offline identity convention;
- no fake premium token behavior;
- clear UI distinction from Microsoft-authenticated accounts.

They are intended for local play and servers configured to accept offline clients.

### Microsoft accounts — v0.3

Premium authentication must be implemented as a provider/service separate from the launch engine.

Conceptual flow:

```text
Microsoft OAuth (browser + PKCE)
        ↓
Microsoft token
        ↓
Xbox Live
        ↓
XSTS
        ↓
Minecraft Services
        ↓
Minecraft profile/session
```

Tokens must be redacted from logs and stored using an appropriate secure mechanism.

## 12. Instance architecture — v0.2

Instances are introduced before loaders because loader/mod support depends on isolated mutable state.

Required principles:

- globally unique instance id independent of display name;
- display name can be changed safely;
- each instance has its own game directory;
- instance configuration is serializable and schema-versioned;
- deleting an instance should distinguish deleting metadata from deleting user data where UX requires it;
- clone/copy operations should be explicit;
- shared caches are referenced, not duplicated unnecessarily.

## 13. Loader architecture — from v0.4

Only introduce a generic loader provider when Fabric support is implemented and later loaders are a real second implementation target.

Conceptual interface:

```kotlin
interface ModLoaderProvider {
    val id: String

    suspend fun versions(
        minecraftVersion: String
    ): List<LoaderVersion>

    suspend fun install(
        instance: MinecraftInstance,
        version: LoaderVersion
    ): LoaderInstallation
}
```

The interface must not assume all loaders install identically.

Planned implementations:

- Fabric — v0.4
- NeoForge — v0.7
- Forge — v0.8
- Quilt — v0.8

Loader-specific metadata belongs in loader adapters. Common Minecraft launch preparation remains in core.

## 14. Mod architecture — from v0.5

A mod entry should ultimately model more than a file path:

- project/mod id if known;
- display name;
- version;
- loader compatibility;
- Minecraft compatibility;
- enabled/disabled state;
- source (`local`, `modrinth`, later others);
- dependencies/conflicts when known;
- hashes for update matching where available.

Do not assume every JAR exposes the same metadata format.

## 15. Modrinth integration — v0.6

The Modrinth adapter should be isolated from the general mod manager.

It should resolve candidate versions using at least:

- Minecraft version;
- selected loader;
- client environment;
- dependency requirements.

The dependency resolver should return a plan before mutating the instance so the operation can be shown, validated and cancelled.

## 16. OptiFine, shaders and resource packs — v0.9

OptiFine should not distort the generic loader architecture. Treat graphics enhancements as instance components/integrations with their own compatibility rules.

Shaders and resource packs are instance-managed content with enablement/configuration handled without polluting core launch logic.

## 17. Modpacks — v0.10

Modpack import/export should operate on instance descriptors and content manifests rather than special-case the launcher state.

Initial target:

- Modrinth `.mrpack` import/export or import where appropriate;
- deterministic installation plan;
- dependency and file download verification;
- user-overrides preservation rules.

Additional formats can be adapters later.

## 18. Diagnostics — v0.11

Diagnostics should consume structured information already produced by the launcher:

- launch plan;
- Java runtime;
- loader and mod metadata;
- launcher log;
- Minecraft log;
- exit code;
- crash reports.

The diagnostic engine should separate:

1. evidence collection;
2. classification;
3. user-facing explanation;
4. optional repair action.

A repair action must be explicit and reversible where practical.

## 19. Updating and production hardening — v0.12

Production polish includes:

- launcher self-update strategy;
- safe/atomic updates;
- installation integrity checks;
- cache repair;
- download recovery;
- cancellation;
- robust logging;
- cross-platform packaging;
- first-run experience;
- release pipeline.

## 20. Error model

Prefer typed/domain-specific failures that preserve causes.

Examples:

```text
NetworkError
DownloadIntegrityError
MetadataParseError
UnsupportedPlatformError
RuntimeInstallError
AuthenticationError
TokenRefreshError
LoaderInstallError
DependencyConflictError
LaunchPreparationError
GameProcessError
```

UI may map these to concise messages, but logs should retain technical context.

## 21. Test architecture

High-value deterministic tests include:

- version manifest parsing;
- version metadata inheritance;
- Mojang rule evaluation;
- classpath resolution;
- native selection;
- offline UUID generation;
- Java requirement selection;
- launch argument placeholder substitution;
- instance serialization and migration;
- loader metadata parsing;
- mod dependency planning.

Use fixture JSON files rather than live network calls for parser/unit tests.

## 22. Non-goals

The project must not:

- bypass Microsoft/Minecraft premium authentication;
- impersonate authenticated players on online-mode servers;
- bundle arbitrary Minecraft game files where licensing/distribution rules do not permit it;
- hardcode one server ecosystem into core;
- make the desktop UI the owner of launch logic;
- sacrifice maintainability for early feature count.

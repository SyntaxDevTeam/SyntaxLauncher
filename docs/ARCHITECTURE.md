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

As of October 2026, native desktop OAuth should use the system browser with authorization code + S256 PKCE, random CSRF state and localhost callback. The Microsoft public-client app ID is not a client secret, but it must be owned and configured by the project; do not reuse another launcher's app ID. OAuth then exchanges Xbox user token, XSTS, Minecraft Services token and verified Java profile. Microsoft Services may reject unapproved apps with HTTP 403 Invalid app registration. A successful OAuth token alone must not be treated as Minecraft ownership.

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

### Initial Modrinth browser and installation slice (October 2026)

`core/mods/ModrinthCatalog.kt` owns API v2 popularity/search facets, detail metadata,
compatible release selection and required dependency plan expansion. It does not touch
files or reference Compose; returned `ModrinthInstallPlan` captures the selected
Minecraft version, loader, projects, versions, and SHA-1-verified CDN artifacts.
The shared `DownloadManager` stages every planned artifact, while
`ModrinthInstaller` calls the per-instance `LocalModManager.importBatch`
under the same cross-process lock used by clone and launching. Batch preflight
checks existing file and mod-id conflicts and restores the initial set on failure.
Installed-mod identity and updates remain a later v0.6 increment; do not present
the initial browser as a complete dependency compatibility resolver.

## 16. OptiFine, shaders and resource packs — v0.9

### Child-first catalog and pack ownership

Desktop `ContentCatalog` provides one Mods/Textures/Shaders/OptiFine catalog with one Install action. Categories appear only when the current game has a supported compatible implementation. Mods require a modded instance; Iris shaders require compatible published Fabric/Quilt/NeoForge releases; OptiFine is initially limited to Vanilla 1.21.1 / HD U J1. No game, loader or world is silently converted. More → My packs contains installed texture/shader selection, disable, recoverable removal/Undo, metadata/icons, texture priority and secondary local ZIP import. Folder packs remain unmanaged warnings.

`ModrinthPackCatalog` (with the source-compatible `ModrinthResourcePacks` alias) owns project-type/exact-game filtering, required same-type dependency resolution and verified ZIP downloads. Shader searches require Iris compatibility. It validates the complete ZIP batch before requesting renderer setup. `IrisRendererSetup` uses the existing mod resolver/installer for Iris and its declared required closure, preserves an installed compatible tracked Iris release, enables identical required disabled components, and refuses untracked renderer replacement or mixing OptiFine/Iris. The final pack transaction rechecks game, loader and SHA-256 proofs for every required renderer JAR under the instance activity lock. Partial renderer preparation may leave verified installed components for retry; failure never publishes a selected shader ZIP without its verified renderer.

`LocalPackManager` owns instance-local intact ZIP inventory/import/selection/removal/recovery. Mutations share `withInactiveInstance` with launch preparation and other profile operations; filesystem IO stays off Compose. Import stages and validates all archives before non-replacing publication, preserves identical existing files when explicitly requested and rolls back only newly published files. ZIP checks bound sizes/entry count and reject unsafe names, malformed metadata and CRC corruption. There is no archive extraction. Removal retains the ZIP in `.syntaxlauncher-pack-trash`, deselects it and restores the moved ZIP if settings publication fails. Undo refuses collisions and any recovery path outside that instance's trash. The desktop retains a last-removal receipt for the open dialog; it does not promise a persistent recovery inventory.

`ResourcePackSelection` edits only options.txt resourcePacks, preserving unrelated options, unknown lines, LF/CRLF and previous order unless the user explicitly changes priority. Dependencies precede the selected main pack; Minecraft's last selected pack has highest priority. `ShaderPackSelection` edits config/iris.properties with Java Properties ISO-8859-1/Unicode escaping, preserving unknown property values. Exact original settings bytes are retained under `.syntaxlauncher-pack-settings` before atomic replacement. Malformed/changed/redirected settings fail without destructive rewriting. Local import alone never activates a renderer or changes game options; Use performs that explicit action.

### OptiFine as an optional instance component

`OptiFineCatalog` discovers the supported filename on the official downloads page and obtains its private mirror download from optifine.net; no binaries are bundled, mirrored or publicly redistributed. See [upstream copyright](https://optifine.net/copyright) and [downloads](https://optifine.net/downloads). The official source does not publish a checksum/size in this flow: transport is HTTPS, the shared downloader enforces a streamed 64 MiB hard cap, and this is not presented as upstream checksum verification. Mojang base metadata/client still require their advertised SHA-1/size.

A JDK-only `OptiFineInstallWorker` invokes the official installer's own metadata/install functions in a separate headless JVM, using the runtime manager and a private staged Minecraft directory. It has timeout/cancellation cleanup and retained stdout/stderr logs. Filesystem staging and a separate JVM are **not** an OS security sandbox. Generated profile/libraries are inspected and hashed, then immutable generated JARs are stored under content-addressed `libraries/graphics/optifine/<sha256>/...` paths. Existing different files are never overwritten.

The additive instance game sidecar `.syntaxlauncher-graphics.json` uses schemaVersion 1, preserves unknown root properties during install/disable, and refuses unknown future schemas before mutation or network/installer work. Existing instances need no migration: absent sidecar means no enhancement. Each replacement retains exact original bytes in `.syntaxlauncher-graphics-backups`. It records the base game, generated profile, verified artifacts and enabled flag. `LocalGraphicsProfileResolver` validates the current game/loader and every cached artifact before overlaying the generated entry point/arguments/libraries into the common coordinator pipeline. Base client/assets/Java/account/process management remain authoritative. OptiFine is never registered as a mod loader. Changing the game or adding a loader requires turning off the incompatible component in My packs.

The official installer/overlay smoke is opt-in and independent from graphical gameplay acceptance. The maintainer separately confirmed private v0.9 gameplay, without an exact OS/loader/renderer/pack matrix. Neither a generated profile nor a selected ZIP itself certifies shader rendering on a real GPU; see [TESTING_V0.9.md](TESTING_V0.9.md).

## 17. Modpacks — v0.10

Modpack import/export should operate on instance descriptors and content manifests rather than special-case the launcher state.

Initial target:

- Modrinth `.mrpack` import/export or import where appropriate;
- deterministic installation plan;
- dependency and file download verification;
- user-overrides preservation rules.

Additional formats can be adapters later.

`core/modpack/ModrinthModpackPlanner` validates the format-1 index according to the [official Modrinth format](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack). `ModpackArchive` bounds/CRC-checks ZIP payloads and validates portable paths before applying client content. `ModpackCatalog` uses centralized HTTP/search/downloads and chooses the published release automatically; packs create new games rather than inheriting the currently selected game's version. The index remains authoritative for exact requirements; catalog/index metadata disagreement fails.

`ModpackInstaller` checks exact game/loader compatibility, verifies required client downloads with size/SHA-1/SHA-512, applies common then client overrides and omits optional/dedicated-server files. `MinecraftInstanceRepository.createPrepared` allocates an unpublished UUID/staging game and publishes metadata/directory only after the callback completes. The long callback does not hold the repository mutex, while the new UUID retains its activity lock. Other instance listing/leases remain usable. Cleanup removes only owned staging; original sources, prior installations and unrelated games remain intact. Immutable shared game/runtime caches may remain after cancellation for retry.

`MinecraftLaunchCoordinator.prepareForInstallation` and normal Play call the same private preparation sequence for metadata-driven game files, Java and loader installation; pack setup never starts a process or requires account credentials. The prepared-instance callback completes before atomic/non-replacing publication. Every reinstall/new release creates a new UUID; no in-place overwrite/update merge of user data is promised.

The additive `.syntaxlauncher-modpack` directory stores source.mrpack and a schema-1 receipt with original manifest, source SHA-512 and optional-file policy. Existing ordinary instances require no migration; unknown receipt schemas remain untouched. Export only verifies/copies the original archive through a private temporary file to a new destination outside games. It never collects a user's current game tree or adds downloaded binaries, local changes, accounts or player worlds. Author-supplied source overrides remain byte-identical; original author distribution terms still apply. This is original pack-definition export, not arbitrary modified-instance export.

The primary UI is home Modpacks or the shared extras catalog → Install → selected new game → Play. Local import/export are secondary. Preparation runs asynchronously, with progress/cancel, error distinctions and translated narrow layouts. Real pack/game/runtime setup evidence is separate from graphical Minecraft/native-platform acceptance; see [TESTING_V0.10.md](TESTING_V0.10.md).

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

### Startup update slice (requested early, October 2026)

The `launcher-core/update` package owns semantic version precedence, explicit Stable/DEV/SNAPSHOT GitHub release selection, manifest-driven bundle installation, cached-image validation, update state and startup transaction/receipt handling. It depends on the same HTTP client and download manager as other core services. Updates add SHA-256 verification without changing existing SHA-1 Minecraft contracts.

Desktop composes `StartupUpdateGate` before the home screen. Its state is supplied by `StartupUpdater`; disk/network/process work runs off the UI thread. Packaged builds embed a semantic product version from the sole `launcherVersion` property in root `gradle.properties` through a generated resource; Gradle modules, CI and publishing share that value. Platform-specific jpackage numeric versions are derived, not independently configured. Version tags must match the property at their source commit. The original packaged executable remains a bootstrap, and new complete application images are installed into user-owned version directories. The running app image and mutable user/game data are never replaced by extraction. A schema-versioned atomic pointer selects the active image only after the child's nonce/version/render receipt. The child bypasses a duplicate check for that one process handoff. A failed new image falls back to a verified installed image where available; process locks guard concurrent bootstrap transactions.

The distribution protocol records per-file hashes, executable flags, directories and safe relative symlinks. CI creates portable first-install archives and protocol ZIPs for the three currently packaged platforms. Stable uses product SemVer; opt-in DEV/SNAPSHOT use separate numeric CI run/attempt identity and per-channel recovery pointers. Every verified main build can be published as DEV, with manually dispatched SNAPSHOT previews. Explicit channel switches may select an older product version while retaining the startup receipt/integrity protocol. Full details, verification boundaries and the GitHub account trust model are in [UPDATING.md](UPDATING.md). This explicitly requested v0.12 slice does not complete v0.12 or change earlier milestone release gates.

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


## 23. v0.4 Loader architecture

`ModLoaderRegistry` maps provider IDs to independently implemented adapters. A nullable
`ModLoaderSelection` on an instance means Vanilla; an explicit ID/version selects a provider.
The selection is serialized as an optional schema-1 property, so pre-v0.4 instance JSON remains
readable without a migration or destructive rewrite.

The Fabric provider queries official Fabric Meta version/profile endpoints and overlays the
profile's entry point, arguments and libraries onto previously validated Mojang metadata.
Version IDs, game client JAR, assets, native rules, logging and Java runtime continue to come
from Mojang. Libraries are downloaded through the shared MinecraftPreparationService and
DownloadManager; shared immutable caches live outside instance game directories.

`MinecraftLaunchCoordinator` asks the registry to produce the effective version before its
existing prepare/Java/plan/process steps. The desktop never assembles game classpaths.
Other loaders may require processors and version-specific artifacts; such work must remain
inside each provider's own installation implementation, not in the coordinator.

**Limitation:** Some Fabric profile libraries have no upstream checksum fields. The centralized
downloader can verify supplied SHA-1/size fields, but absent hashes cannot be treated as
cryptographically verified. Integrity metadata and real-client launch validation are
acceptance gates before claiming comprehensive loader support.

## 23. Presentation localization

The desktop `i18n` package owns UTF-8 EN/PL/DE resources, locale selection and Compose-scoped `LocalStrings`. Core remains locale/UI independent and exposes typed launch and startup-update stages. Presentation maps those stages to translation keys; logs retain diagnostic text. An additive `ui_language` property uses the existing schema-1 atomic preferences store with dedicated language-only reads/writes so normal profile saves cannot overwrite the selected UI language. Missing language settings preserve backward reading compatibility; unsupported locale tags fall back to English without discarding stored data.

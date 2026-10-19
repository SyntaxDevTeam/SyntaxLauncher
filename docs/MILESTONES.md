# SyntaxLauncher Milestones

This document describes the intent, scope, implementation boundaries and acceptance expectations for every planned pre-1.0 release.

`docs/ROADMAP.md` is the canonical checkbox tracker. This file explains what each milestone is supposed to mean.

---

## v0.1 — Minimal Vanilla Launcher

### Objective

Deliver the smallest useful launcher: an offline user selects a Minecraft version, the launcher prepares the correct game files and Java runtime, then starts the game.

### User-visible scope

The user can:

- enter an offline nickname;
- choose a Minecraft release;
- optionally expose snapshots behind a filter/toggle;
- select RAM allocation;
- press Play;
- see preparation/download progress;
- receive a useful launch/preparation error;
- relaunch later using cached assets/libraries/runtime.

### Internal scope

v0.1 establishes the technical core of the whole project:

- version manifest and metadata resolution;
- rule evaluation;
- assets;
- libraries;
- natives;
- Java runtime management;
- launch argument construction;
- process management;
- shared download infrastructure;
- minimal account abstraction;
- desktop UI separated from core.

### Deliberately excluded

Do not implement in v0.1:

- Microsoft authentication;
- multiple user accounts;
- instances as a user-facing concept;
- Fabric/Forge/NeoForge/Quilt;
- mod management;
- Modrinth;
- OptiFine;
- shaders;
- modpacks;
- automatic crash diagnosis;
- launcher self-update.

### Architectural requirement

Even though only offline accounts exist, launch code must not be hardwired to a raw username string. A common account/session representation must exist so v0.3 can add Microsoft accounts without cloning launch logic.

Java handling must be a service, not UI code. Minecraft version-specific behavior must be metadata-driven rather than a hardcoded table of launch commands.

### Definition of successful v0.1

On a clean machine within the supported platform scope, a user without a suitable globally configured Java installation can select a supported vanilla Minecraft release, let SyntaxLauncher download everything it manages, and start the client successfully.

A second launch should substantially reuse verified cached files.

---

## v0.2 — Instance Manager

### Objective

Introduce isolated game installations before mods make shared `.minecraft` state dangerous.

### Why this milestone comes before mod support

A modded launcher without isolated instances quickly creates dependency conflicts, shared configs, mixed worlds and difficult recovery. Instances become the boundary for all mutable game state.

### User-visible scope

The user can:

- create an instance;
- name/rename it;
- select Minecraft version;
- configure RAM/JVM settings per instance;
- clone an instance;
- delete an instance;
- launch the selected instance;
- keep worlds/configs/resource packs/screenshots isolated from other instances.

### Persistence

Each instance should have:

- stable internal id;
- display name;
- schema version;
- Minecraft version;
- game directory;
- memory/JVM settings;
- room for future loader/mod metadata.

The model should not contain fake placeholders for every future feature. It should, however, be evolvable through explicit schema migrations.

### Definition of successful v0.2

Two instances using the same vanilla Minecraft version can have different saves/settings without modifying each other's mutable files, while safely sharing immutable launcher caches where appropriate.

---

## v0.3 — Microsoft / Premium Accounts

### Objective

Add official premium authentication while keeping offline mode available.

### User-visible scope

The user can:

- add a Microsoft account through the browser;
- add/use offline accounts;
- switch between saved accounts;
- see account identity/profile information;
- remove/sign out an account;
- start Minecraft with an authenticated premium session.

### Authentication pipeline

Conceptually:

```text
Microsoft OAuth
   ↓
Xbox Live authentication
   ↓
XSTS
   ↓
Minecraft Services
   ↓
Minecraft profile/session
```

The implementation must use the official authentication path appropriate to a desktop application. It must never ask the user to type their Microsoft password into SyntaxLauncher.

### Security requirements

- use browser-based auth and PKCE where applicable;
- redact access/refresh tokens from logs;
- use secure OS credential storage for long-lived secrets where practical;
- refresh sessions when possible;
- recover cleanly when reauthentication is required.

### Definition of successful v0.3

Offline and Microsoft accounts both feed the same core launch pipeline after account/session resolution. There is no duplicated `launchPremium()` versus `launchOffline()` engine.

---

## v0.4 — Fabric Loader

### Objective

Add the first mod loader and introduce the loader abstraction only when it becomes justified by a real implementation.

### User-visible scope

The user can create/edit an instance and select:

- Vanilla; or
- Fabric plus a compatible loader version.

### Internal scope

Introduce a loader provider/adapter boundary capable of representing:

- compatible loader versions;
- installation/preparation;
- resolved loader launch metadata;
- installed loader state in instance configuration.

Fabric-specific code must remain in the Fabric adapter.

### Definition of successful v0.4

A Fabric instance launches through the same generic Minecraft process pipeline used by vanilla, with Fabric metadata contributing only the changes required for that instance.

The abstraction should be ready for a second implementation without pretending every loader has identical installation mechanics.

---

## v0.5 — Local Mod Manager

### Objective

Manage manually supplied mods safely before adding an online catalog.

### User-visible scope

The user can:

- add a mod JAR to an instance;
- remove it;
- enable/disable it;
- see basic metadata such as mod id/name/version when detectable;
- see basic warnings about duplicates/missing declared dependencies.

### Important boundary

At this stage SyntaxLauncher does not yet promise to find missing dependencies online automatically. It can warn based on local metadata, but repository-backed dependency resolution belongs to v0.6.

### Definition of successful v0.5

Mod state is fully instance-scoped and reversible. Disabling a mod does not require deleting it, and managing one instance cannot mutate another instance's `mods` state.

---

## v0.6 — Modrinth Integration

### Objective

Turn the local mod manager into a practical mod installation/update workflow backed by Modrinth.

### User-visible scope

The user can:

- search Modrinth projects;
- see only compatible versions for the current Minecraft version/loader where possible;
- install a mod;
- review required dependencies;
- allow the launcher to install required dependencies;
- see available updates;
- update one or all compatible managed mods.

### Resolver behavior

Before writing files, build an installation plan that contains:

- selected project/version;
- target file;
- required dependencies;
- conflicts/incompatibilities;
- resulting operations.

The plan should be validated before mutating the instance.

### Definition of successful v0.6

SyntaxLauncher does not blindly install the newest release. It chooses a release compatible with the instance's Minecraft version, loader and client environment, or explains why no compatible release exists.

---

## v0.7 — NeoForge

### Objective

Add the first substantially different second loader and validate the abstraction designed in v0.4.

### User-visible scope

NeoForge becomes available during instance creation/editing, with compatible versions selected for the instance's Minecraft version.

### Internal scope

Support the installation/resolution mechanics NeoForge requires, including processors/generated artifacts when applicable, while keeping those details out of generic launch code.

### Definition of successful v0.7

Vanilla, Fabric and NeoForge instances coexist without architecture branches spread across unrelated services.

If adding NeoForge requires rewriting Fabric-specific assumptions in core, those assumptions should be removed rather than duplicated.

---

## v0.8 — Forge and Quilt

### Objective

Broaden loader support and handle historical compatibility explicitly.

### Forge

Forge is expected to be the most demanding loader because its installation behavior and Minecraft-era conventions vary significantly across versions.

SyntaxLauncher should define a tested support matrix rather than falsely claiming universal Forge compatibility if some historical ranges are not implemented.

### Quilt

Quilt should reuse the generic loader boundary while keeping Quilt-specific metadata/install behavior isolated.

### Java/legacy impact

This milestone should verify that runtime selection and launch argument resolution can support representative older Minecraft versions, not only current releases.

### Definition of successful v0.8

One launcher core supports Vanilla, Fabric, NeoForge, Forge and Quilt through adapters plus shared metadata/launch infrastructure.

---

## v0.9 — OptiFine, Shaders and Resource Packs

### Objective

Add graphics/content management without forcing OptiFine into a loader model that does not fit it.

### OptiFine

Treat OptiFine as an instance component/integration whose installation and compatibility behavior can differ depending on Minecraft/loader combinations.

Distribution/licensing constraints must be respected; do not assume SyntaxLauncher may mirror arbitrary OptiFine artifacts.

### Shaders

Allow per-instance shader-pack management, with compatibility hints for the active renderer stack (for example Iris/OptiFine where relevant).

### Resource packs

Allow importing/removing/managing packs per instance, preserving user data and metadata when possible.

### Definition of successful v0.9

Graphics-related features remain optional instance components and do not contaminate generic loader or launch abstractions.

---

## v0.10 — Modpacks

### Objective

Install reproducible modded environments from a manifest rather than forcing users to assemble every dependency manually.

### Initial format

Prioritize Modrinth `.mrpack` because Modrinth integration already exists from v0.6.

### Installation model

A modpack installation should:

1. validate manifest;
2. resolve Minecraft/loader requirements;
3. create a new instance;
4. build a download/install plan;
5. verify files;
6. apply overrides/configuration;
7. finalize the instance only after required operations succeed.

### Export

Where feasible, allow exporting a reconstructable pack/instance description without redistributing files whose licenses prohibit it.

### Definition of successful v0.10

A supported pack can be installed from scratch into an isolated instance without touching unrelated instances, and a failed installation does not masquerade as a completed one.

---

## v0.11 — Diagnostics and Repair

### Objective

Answer the practical question users actually have when Minecraft does not start: what failed, why, and what can be fixed safely?

### Evidence sources

Diagnostics may use:

- resolved launch plan;
- selected Java runtime;
- Minecraft version;
- loader version;
- installed mods;
- launcher logs;
- Minecraft logs;
- crash reports;
- exit code;
- integrity state of launcher-managed files.

### Output model

A diagnostic finding should separate:

- evidence;
- confidence;
- probable/confirmed cause;
- human explanation;
- optional remediation.

Avoid presenting heuristics as certainty.

### Repair examples

- re-download corrupt libraries/assets;
- repair a managed Java runtime;
- identify missing dependencies;
- suggest updating/disabling an incompatible mod;
- reinstall loader-managed artifacts.

Destructive repairs require explicit user confirmation.

### Definition of successful v0.11

For representative failures, the launcher provides more useful guidance than simply showing `Exit code 1`, while keeping tokens and private credentials out of reports/logs.

---

## v0.12 — Production Hardening

### Objective

Convert a feature-complete pre-release into software suitable for stable distribution.

### Reliability scope

- robust cancellation/retry;
- interrupted-download recovery;
- integrity repair;
- disk-space handling;
- network timeout behavior;
- safe state transitions;
- production-grade logs.

### Distribution scope

- Windows packaging;
- Linux packaging;
- macOS packaging;
- first-run behavior;
- application metadata/icons;
- documented data locations.

### Update scope

The launcher needs a safe self-update mechanism with integrity validation and failure recovery appropriate to the chosen packaging strategy.

**Explicit early scope requested on 19 October 2026:** implement the startup updater during current development: stable GitHub release checks, SHA-256 verified side-by-side application installation, startup receipts and previous-version recovery. See [UPDATING.md](UPDATING.md). This narrow request does not advance unrelated v0.12 features or waive real hosted/cross-platform acceptance.

### CI/CD scope

Automated tests and release builds must run in CI, with versioned release artifacts and a repeatable release process.

### Definition of successful v0.12

Release artifacts can be installed on supported targets, interrupted operations do not commonly corrupt state, and upgrades preserve user data appropriately.

---

## v1.0 — Stable

### Objective

Publish the first stable SyntaxLauncher release with a documented support matrix and migration guarantees for user data created by supported pre-1.0 builds.

### Expected feature set

- offline accounts;
- Microsoft premium accounts;
- vanilla versions within published support scope;
- managed Java runtimes;
- isolated instances;
- Fabric;
- NeoForge;
- Forge;
- Quilt;
- local mods;
- Modrinth install/update/dependency handling;
- OptiFine integration within documented limits;
- shaders;
- resource packs;
- modpack support;
- diagnostics/repair;
- self-update;
- supported Windows/Linux/macOS builds.

### Stability expectations

v1.0 does not mean every Minecraft version and every third-party loader/mod combination in history is guaranteed. It means supported combinations are documented, tested and failures outside the support matrix are communicated clearly.

### Release criteria

All v1.0 quality-gate items in `docs/ROADMAP.md` must be checked. Known critical security/data-loss issues block the release.

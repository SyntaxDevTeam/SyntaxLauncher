# AGENTS.md — SyntaxLauncher

This file defines mandatory working rules for AI/agentic contributors operating in this repository.

The rules below are part of the project workflow, not suggestions. An agent changing code, build configuration, documentation, CI, tests, project structure, or user interface MUST follow them.

## 1. Project goal

SyntaxLauncher is a modular Minecraft launcher written primarily in Kotlin. The project starts intentionally small and grows milestone by milestone without locking the architecture to one authentication type, one Minecraft version, one mod loader, or one operating system.

The launcher must ultimately be able to support:

- Microsoft-authenticated player profiles only, with a persistent player manager separate from game instances; historical local-only rows are inert and preserved only for recovery;
- official Microsoft/Minecraft authentication for premium accounts;
- multiple Minecraft versions;
- automatic Java runtime selection and installation;
- isolated game instances;
- Fabric, NeoForge, Forge and Quilt;
- local mods and Modrinth integration;
- OptiFine, shaders and resource packs;
- modpacks;
- diagnostics, repair and production-grade updating.

The product must also maintain a lightweight, highly modern and visually distinctive desktop experience. It should attract the user's attention through polish and strong hierarchy without overwhelming the user with controls, decoration or technical complexity. `docs/DESIGN.md` is authoritative for UI/UX direction.

Do not implement future milestones prematurely unless the current work explicitly requires a compatibility seam for them.

## 2. Mandatory files to read before making changes

Before starting any non-trivial task, read at minimum:

1. `README.md`
2. `docs/ARCHITECTURE.md`
3. `docs/ROADMAP.md`
4. `docs/MILESTONES.md`
5. `docs/DEVELOPMENT.md`
6. `docs/DESIGN.md` for any UI, UX, Compose, navigation, interaction, layout or presentation work
7. the latest entries in `docs/WORKLOG.md`

If the task concerns a specific milestone, read that milestone's complete section in `docs/MILESTONES.md` before editing code.

## 3. Roadmap is the source of truth

`docs/ROADMAP.md` is the canonical progress tracker for the project.

Every agent MUST update it whenever work changes the status of a roadmap item.

Rules:

- Use `- [ ]` for work not completed.
- Use `- [x]` only when the item is actually implemented and its acceptance criteria are satisfied.
- For partially implemented work, keep `- [ ]` and append `🚧 IN PROGRESS` plus a short note.
- Never mark an umbrella item complete while any required child item remains incomplete.
- Do not mark work complete merely because code was written. Build/tests/manual verification required by the milestone must also pass.
- When a task adds previously unplanned but necessary work, add a new checklist item in the correct milestone instead of hiding it in a worklog entry.
- If a task is intentionally moved to another version, move the roadmap item and explain why in `docs/WORKLOG.md`.

## 4. Worklog is mandatory

Every repository-changing task MUST append an entry to `docs/WORKLOG.md` in the same body of work.

Do not rewrite historical entries except to fix an obvious factual error. Append new entries at the top of the log below the introductory section.

Each entry MUST contain:

```markdown
## YYYY-MM-DD — <short task title>

**Milestone:** vX.Y  
**Author/Agent:** <name/model if known>  
**Status:** Completed | Partial | Blocked

### Completed
- ...

### Changed files
- `path/to/file` — what changed

### Verification
- `./gradlew test` — PASS/FAIL/NOT RUN + reason
- `./gradlew build` — PASS/FAIL/NOT RUN + reason
- Manual verification — description if applicable

### Roadmap updates
- `[x] vX.Y / Section / Item`
- `[ ] vX.Y / Section / Item — remains open because ...`

### Follow-ups / known issues
- ...
```

A documentation-only change may use `NOT RUN — documentation-only change` for build/test commands.

## 5. Definition of Done

A task is not done until all applicable points below are satisfied:

- implementation is complete for the requested scope;
- code follows the architecture boundaries documented in `docs/ARCHITECTURE.md`;
- UI/UX work follows `docs/DESIGN.md`;
- public behavior is covered by tests where practical;
- existing tests still pass;
- the project builds, unless the task is documentation-only;
- no secrets, access tokens, refresh tokens, credentials, machine-specific paths, or generated build artifacts are committed;
- relevant user/developer documentation is updated;
- `docs/ROADMAP.md` accurately reflects the resulting state;
- `docs/WORKLOG.md` contains a new entry describing the work and verification;
- incomplete work is explicitly left unchecked and documented.

## 6. Version discipline

The canonical SyntaxLauncher product version is `launcherVersion` in the root `gradle.properties`. Do not duplicate a SemVer constant in Gradle scripts, runtime defaults, CI, the Build Explorer publisher or release tooling. Gradle module versions, generated desktop metadata, update bundles and publication metadata must derive from this property. A release tag `vX.Y.Z[-suffix]` must exactly match the value at its tagged commit; CI must reject mismatches. Native jpackage versions may be mechanically normalized for platform rules but must not be maintained separately.


Work should target the earliest milestone that actually needs the feature.

Do not pull features forward simply because they are easy to add.

Examples:

- v0.1 must not contain Microsoft authentication, Fabric, Modrinth, modpacks, or a generic mod-loader framework unless a very small interface is genuinely required by current code.
- v0.2 introduces instances before mod support.
- v0.3 introduces Microsoft premium authentication.
- v0.4 is the first mod-loader milestone and may introduce the loader abstraction needed by Fabric and later loaders.
- later loader implementations must reuse that abstraction instead of duplicating launch logic.

If a future capability affects an early data model, prefer a narrow extensibility seam over implementing the future feature itself.

## 7. Architecture rules

### 7.1 Core must remain UI-independent

Minecraft resolution, downloading, Java management, authentication models, launch command construction and process management must not depend on Compose UI classes.

The desktop UI calls application/core services; core never calls UI code.

### 7.2 No hardcoded Minecraft launch recipes

Do not hardcode classpaths, libraries, asset indexes, natives, main classes, Java versions or launch arguments for individual Minecraft versions when these values exist in Minecraft version metadata.

Version metadata is authoritative.

### 7.3 Accounts are polymorphic

Launch code consumes the shared verified Microsoft account/session representation. Legacy identity parsing exists solely for preservation and must never enable an unauthenticated launch pipeline.

### 7.4 Instances own mutable game state

From v0.2 onward, saves, mods, configs, screenshots, resource packs and other mutable game data belong to an instance-specific game directory.

Shared immutable caches such as Mojang assets/libraries may be centralized where safe.

### 7.5 Java runtime management is a service

Minecraft code must request a compatible runtime from the runtime manager rather than constructing a fixed Java executable path.

### 7.6 Networking is centralized

Use the project's HTTP abstraction/client for downloads and APIs. Do not create ad-hoc HTTP clients throughout the codebase.

Required characteristics include sensible timeouts, cancellation, progress reporting where relevant, resumable/retry behavior where safe, and clear errors.

### 7.7 Avoid premature abstractions

Create abstractions when there are at least two realistic implementations or when a documented future milestone requires a stable boundary. Keep v0.1 deliberately small.

## 8. Authentication and security rules

**Current product requirement — 24 October 2026:** Only a Microsoft account with a freshly validated Minecraft Java entitlement/profile may launch. Never reintroduce local-only gameplay, nickname-based identities, synthetic sessions, or fallback after failed authentication. Enforce this in UI, DesktopLauncherSession, MinecraftLaunchCoordinator and LaunchPlanBuilder. Preserve legacy user files/worlds without allowing their identities to launch. The AppID remains unapproved: see [docs/APPID_COMPLIANCE.md](docs/APPID_COMPLIANCE.md) for outstanding validation and external website/Entra checks.

- Premium authentication must use official Microsoft/Xbox/Minecraft authentication flows.
- Never collect or store a user's Microsoft password.
- Desktop OAuth must use an appropriate browser-based authorization flow with PKCE where applicable.
- Access and refresh tokens must never be written to normal logs.
- Sensitive credentials must not be committed.
- Prefer OS credential/keychain storage for long-lived secrets once premium authentication is implemented.
- No game can start without an officially authenticated and freshly verified Minecraft Java entitlement/profile, regardless of the server's online-mode setting.
- Do not implement mechanisms intended to impersonate premium identities or defeat server authentication.

## 9. Logging rules

Application logs and the development worklog are separate concepts.

Runtime logs should:

- use structured/severity-based logging;
- avoid secrets and tokens;
- identify download, resolver, runtime and launch failures precisely;
- include enough context to diagnose a failed launch without dumping credentials;
- write launcher logs separately from Minecraft process logs where practical.

Development changes must additionally be recorded in `docs/WORKLOG.md` as described above.

## 10. Testing expectations

At minimum, add or maintain tests for logic such as:

- Minecraft version metadata parsing;
- Mojang rule evaluation and argument resolution;
- library classifier/native selection;
- OS/architecture rule handling;
- legacy identity preservation and unauthenticated launch rejection;
- Java compatibility selection;
- launch argument construction;
- instance serialization/migration;
- loader metadata parsing when loaders are added;
- dependency resolution when mod integration is added.

Prefer deterministic unit tests around parsers/resolvers and a smaller number of network/integration tests.

Do not make normal unit tests depend on Mojang, Microsoft, Modrinth or CurseForge being online.

## 11. Backward compatibility and data migrations

Once users can create launcher state (settings, accounts, instances), persisted formats must carry an explicit schema version.

When a format changes:

1. keep backward reading compatibility where reasonable;
2. implement a migration;
3. test the migration;
4. never silently discard unknown user data;
5. document the migration in `docs/WORKLOG.md`.

## 12. Error handling

Do not collapse actionable failures into generic messages such as `Something went wrong` or `Exit code 1`.

Distinguish failures such as:

- network/download failure;
- checksum/integrity failure;
- malformed metadata;
- unavailable Minecraft version;
- unsupported operating system/architecture;
- Java runtime installation failure;
- authentication failure;
- token expiry/refresh failure;
- incompatible loader/mod dependency;
- game process crash.

Preserve the technical cause in logs while showing a concise user-facing explanation.

## 13. Commit/change hygiene

- Keep changes scoped to the requested task.
- Do not reformat unrelated files.
- Do not commit IDE state, local caches, runtime downloads, Minecraft assets, game instances or tokens.
- Update `.gitignore` as new generated/local paths appear.
- Prefer conventional, descriptive commit messages such as `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:` and `ci:`.

## 14. Required end-of-task report

Before considering a repository task complete, the agent should be able to state:

1. what was implemented;
2. what was intentionally not implemented;
3. which verification steps passed;
4. which roadmap boxes were checked or left open;
5. which worklog entry was added;
6. whether there are known follow-ups or blockers.

If any of these cannot be answered, the repository state is not fully documented.

## 15. Mandatory UI/UX direction

**Non-negotiable audience requirement (21 October 2026):** SyntaxLauncher is for children playing Minecraft and must be extraordinarily simple to use. The normal path is a catalog inside the launcher: choose a single mod, modpack, shader, resource pack, OptiFine enhancement or compatible ready-made set, then use one clear Install/Play action. The launcher chooses compatible versions, loaders, Java and required dependencies; children must not manage JAR/ZIP files, paths, renderer internals or technical installation plans. Advanced/manual import remains secondary. A backend or file copier alone does not complete a user-facing feature. Never show a nonfunctional future category as available, or claim an installation is ready when required components are missing. This requirement governs every milestone and must be reflected in acceptance tests and docs/DESIGN.md.

All UI/UX work must follow `docs/DESIGN.md`.

The design objective is not generic minimalism. SyntaxLauncher should be visually memorable and clearly modern while remaining light, calm and easy to understand.

Mandatory principles:

- The interface must attract attention through polish, composition, typography and coherent identity rather than visual noise.
- The user must not be overwhelmed by the number of features supported internally.
- Prefer progressive disclosure: common actions stay visible; advanced/technical controls are shown when requested or contextually necessary.
- Keep one clear primary action per screen or context. On the normal launcher home screen, `Play` is the dominant action.
- Use a limited, intentional palette and restrained visual effects.
- Avoid excessive nested cards, borders, gradients, glow, blur, glass effects or animation.
- Animation and micro-interactions must communicate state or improve continuity, not exist only as decoration.
- Heavy work must never block the Compose UI thread. Perceived performance is part of the design quality requirement.
- Significant screens must define loading, empty, disabled and error states rather than only the ideal state.
- Layouts must remain usable across supported window sizes and common DPI scaling.
- Accessibility, contrast, focus states and clear state communication are part of normal component quality, not a post-v1.0 cleanup task.
- UI implementations should build on a small reusable design-token/component system rather than scattering arbitrary colors, spacing, radii and typography values across screens.
- The launcher should develop its own visual identity and must not simply clone another Minecraft launcher.

A UI task is not complete merely because every requested control exists. It must also preserve hierarchy, clarity, responsiveness and the lightweight visual character defined in `docs/DESIGN.md`.

## 16. Standing publication instruction (22 October 2026)

The maintainer explicitly requests: always push all changes. After completing and verifying repository work, commit all current project changes (including maintainer edits present in the working tree) and push the current branch to its configured remote without asking again. Preserve unrelated content, never force-push or rewrite shared history, and never include secrets, caches or generated artifacts. Check the resulting CI/build status and report the commit and any failures. This authorizes normal commits/pushes; creating release tags or publishing Stable releases still requires a specific request.

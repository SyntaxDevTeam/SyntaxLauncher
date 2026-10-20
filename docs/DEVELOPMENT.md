# SyntaxLauncher Development Guide

## 1. Scope

This document defines the expected engineering workflow and implementation conventions for SyntaxLauncher. AI/agent contributors must also follow `AGENTS.md`.

## 2. Core principles

1. **Metadata-driven Minecraft support.** Do not hardcode launch recipes that Mojang metadata already describes.
2. **Core independent from UI.** Compose Desktop is a presentation layer, not the launcher engine.
3. **Instances before mods.** Mutable game state becomes instance-scoped in v0.2 before loaders/mods arrive.
4. **Official premium auth only.** Premium support uses Microsoft/Xbox/Minecraft authentication; offline accounts never become an authentication bypass.
5. **Managed Java.** The launcher resolves a compatible Java runtime instead of assuming the user's PATH is correct.
6. **Incremental milestones.** Implement only the current release scope plus narrow extensibility seams required for future work.
7. **Recoverable operations.** Downloads/installations should avoid leaving partial files/state presented as complete.
8. **Actionable errors.** Preserve technical causes and present clear user-facing failure categories.

## 3. Planned stack

Initial preferred stack:

- Kotlin/JVM
- Gradle Kotlin DSL
- Compose Desktop
- Kotlin Coroutines
- Ktor Client
- kotlinx.serialization
- JUnit-compatible Kotlin testing

Libraries may change if there is a documented technical reason. Avoid adding large frameworks for functionality already available in Kotlin/JDK or existing project dependencies.

## 4. Suggested repository/module layout

Start small. A reasonable initial layout is:

```text
SyntaxLauncher/
├── launcher-core/
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
├── launcher-desktop/
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
├── docs/
├── AGENTS.md
├── README.md
├── settings.gradle.kts
└── build.gradle.kts
```

If a single Gradle module is simpler for the first implementation, package boundaries must still separate UI from core. Split into modules when it materially improves dependency control rather than for appearance alone.

## 5. Package responsibilities

### `account`

Account identity/session models and account providers. v0.1 contains offline account support. Microsoft-specific authentication belongs to v0.3.

### `minecraft`

Mojang version manifests, version metadata, rules, assets, libraries, natives and version resolution.

### `java`

Java runtime requirements, runtime inventory, download/install/verification and runtime selection.

### `launch`

Launch inputs, normalized launch plan, command construction, process start/monitoring and exit state.

### `network`

Shared HTTP/download infrastructure. Domain services should not each invent their own client/retry/checksum implementation.

### `storage`

Platform paths and persistence primitives.

### `instance`

Introduced in v0.2. Instance metadata, storage, migrations and operations.

### `loader`

Introduced when v0.4 Fabric work justifies it. Loader provider/adapters only.

### `mods`

Introduced in v0.5. Generic mod inventory/operations separate from Modrinth-specific API code.

### `diagnostics`

Introduced in v0.11. Evidence collection, findings and repair plans.

## 6. Coding conventions

- Prefer immutable data classes for resolved metadata/plans.
- Prefer explicit result/error types at domain boundaries over throwing generic exceptions everywhere.
- Use coroutines for network/disk preparation operations; do not block the Compose UI thread.
- Keep file/network side effects behind services that can be substituted in tests.
- Use clear domain names (`ResolvedMinecraftVersion`, `LaunchPlan`, `JavaRuntime`) rather than generic maps.
- Avoid global mutable singleton state.
- Avoid passing UI objects into core services.
- Avoid boolean parameter explosions; prefer options/configuration data classes.
- Keep serialization DTOs separate from domain models when remote/persisted schema shape would otherwise leak broadly.

## 7. Minecraft metadata handling

Minecraft version metadata should be treated as data, not translated into a growing set of version `if` statements.

Resolver responsibilities include:

- downloading/caching metadata;
- resolving inherited metadata where applicable;
- evaluating `rules` against current OS/architecture/features;
- resolving libraries/classifiers;
- selecting native artifacts;
- resolving assets;
- interpreting JVM/game arguments;
- interpreting Java runtime requirements.

When a historical version behaves differently, first determine whether the difference is represented in metadata. Add a targeted compatibility adapter only when metadata cannot express it.

## 8. Filesystem rules

Never assume the current working directory or application install directory is writable.

Use platform-appropriate application directories.

Separate concepts:

- **configuration** — small persisted launcher/user settings;
- **cache** — safely reproducible downloads such as metadata/assets/libraries;
- **runtime** — launcher-managed Java installations;
- **instances** — user game state;
- **logs** — launcher/Minecraft process logs.

Files downloaded from the network should generally be written to a temporary/partial path and moved into final place after successful completion and integrity checks.

## 9. Networking/download rules

All network consumers should use shared infrastructure.

Required behaviors as milestones need them:

- sensible connect/read/request timeouts;
- cancellation support;
- bounded concurrency;
- progress reporting;
- retry only for appropriate transient failures;
- checksum verification when source metadata supplies hashes;
- user-agent identification where APIs expect it;
- rate-limit-aware API adapters;
- no tokens in debug URLs/logging.

A failed checksum is not a successful download.

## 10. Java runtime rules

Do not map Minecraft version strings directly to Java versions in UI code.

The runtime manager should consume the resolved requirement and select/install a matching runtime.

Runtime installations should have enough metadata to answer:

- distribution/source;
- runtime version;
- Java major;
- OS;
- architecture;
- executable path;
- installation/verification state.

User-provided runtime override can be added later but should pass compatibility validation.

## 11. Account rules

### Offline

- validate username;
- deterministic offline UUID;
- explicitly presented as offline/non-premium mode;
- no fake premium token;
- intended for local/offline-mode server use.

### Microsoft

When added in v0.3:

- browser-based OAuth;
- PKCE where required/applicable;
- no password collection;
- secure token storage;
- token redaction;
- refresh/session expiry handling.

The launch planner should receive a normalized session/account payload rather than performing OAuth itself.

## 12. Instance persistence and migrations

From v0.2, every persisted instance descriptor must include a schema version.

Migration rules:

- migrations are explicit and tested;
- never silently discard unknown user state;
- prefer backup/copy before risky migrations;
- migration failure leaves original data intact where practical;
- worklog notes schema changes.

## 13. Testing strategy

### Unit tests

Use fixtures for:

- Mojang version manifests;
- old/new version JSON;
- OS rules;
- libraries/natives;
- argument placeholders;
- loader metadata;
- mod metadata.

High-value pure logic should run without network access.

### Integration tests

Use sparingly for:

- download pipeline behavior against controlled/local fixtures;
- filesystem installation plans;
- process construction;
- serialization migrations.

External services such as Microsoft or Modrinth should be abstracted so normal tests do not require real credentials or live APIs.

### Manual acceptance

Each milestone has user-level acceptance conditions in `docs/ROADMAP.md` and `docs/MILESTONES.md`. Record performed manual verification in `docs/WORKLOG.md`.

## 14. Logging

Use levels consistently:

- `TRACE` — highly verbose internals useful during diagnosis;
- `DEBUG` — resolver decisions, cache decisions, non-secret metadata;
- `INFO` — meaningful state transitions;
- `WARN` — recoverable abnormal conditions;
- `ERROR` — failed user operation/process requiring attention.

Never log:

- Microsoft access tokens;
- refresh tokens;
- authorization codes;
- passwords;
- complete sensitive request headers.

Launcher logs and raw Minecraft process logs should be distinguishable.

## 15. UI behavior

Compose Desktop UI should:

- reflect a state model exposed by services/view-model/application layer;
- remain responsive during downloads/resolution;
- expose cancellation where an operation supports it;
- not implement download/auth/version parsing logic;
- provide actionable error summaries with access to technical details;
- prevent duplicate conflicting actions while an operation is in a critical state.

## 16. Feature flags and partial features

Do not expose nonfunctional UI controls for future roadmap items merely to make the interface look complete.

If an experimental feature exists behind a development flag, it must not be marked complete on the roadmap until its milestone acceptance criteria are satisfied.

## 17. Dependency policy

Before adding a dependency, ask:

1. Is it necessary?
2. Is the project actively maintained?
3. Is its license compatible?
4. Does the JDK/Kotlin/existing stack already solve the problem?
5. Does it substantially increase binary size or runtime complexity?

Document non-obvious choices in the worklog or architecture document.

## 18. Security baseline

- Do not execute arbitrary downloaded code during launcher preparation unless it is an unavoidable, understood part of a supported loader installation process.
- Validate paths from archives to prevent path traversal/Zip Slip.
- Treat remote metadata as untrusted input.
- Verify hashes/signatures where the upstream provides them.
- Never build shell command strings from unescaped user data; use `ProcessBuilder` argument lists or equivalent.
- Sanitize filenames/instance ids generated from user-visible names.
- Keep secrets out of repository and logs.

## 19. Pull request/change checklist

Before merging or finishing a task:

- [ ] Scope matches requested/current milestone.
- [ ] Architecture boundaries are respected.
- [ ] Tests added/updated where practical.
- [ ] `./gradlew test` passes when code/build changed.
- [ ] `./gradlew build` passes when code/build changed.
- [ ] No secrets/generated runtime data committed.
- [ ] Relevant docs updated.
- [ ] `docs/ROADMAP.md` updated precisely.
- [ ] `docs/WORKLOG.md` entry appended.
- [ ] Remaining limitations/follow-ups documented.


## 20. Launcher updates and tagged releases

The maintainer requested the startup self-update slice of v0.12 early. Follow [UPDATING.md](UPDATING.md) when modifying versioning, bundle manifests, cached startup/rollback, the receipt protocol or publishing. Core update logic must remain independent of Compose; use the central downloader. Tests use local ZIP/HTTP/process fixtures and isolated data directories. The explicit `verifyUpdateBundle` task tests a real packaged archive without executing it. Set the single `launcherVersion` property in the root `gradle.properties` and commit it before releasing. `./gradlew` uses this version for all modules and embedded launcher metadata; `scripts/launcher_version.py` reads it for bundle/publication tooling. GitHub Actions rejects any `v*` release tag whose version differs from the property in the tagged commit, rather than silently overriding it. Native jpackage numeric versions are derived from the product SemVer for macOS compatibility. Publish normal, non-prerelease GitHub Releases automatically after a higher plain `launcherVersion=X.Y.Z` is pushed to `main` and full CI succeeds; a matching manually pushed stable version tag is an alternative. The publishing job rejects prerelease-tag publication, and no `-SNAPSHOT`/`-dev` tag should be published as a GitHub Release. Each normal release enters the stable updater channel; outstanding manual acceptance must be disclosed. Do not equate a successful fixture test with live hosted cross-platform self-update acceptance.

## Build Explorer publication

The existing build workflow verifies both core and desktop tests and builds portable
applications for Windows x64, macOS ARM64 and Linux x64. A dependent Linux job
publishes all three archives to the `syntaxlauncher` project only after the complete
matrix succeeds. Pull requests never publish or receive the publishing secret.

Repository secret: `BUILD_EXPLORER_TOKEN`. The server stores only its SHA-256 hash
and limits it to this project, application formats and 90 MiB per file. It cannot
manage retention or publish to other projects. Rotate with the miniPORTAL CLI
`bin/issue-build-ci-token.php syntaxlauncher NEW_SECRET_FILE --rotate`, replace
the repository secret and remove the transfer file. Never commit the token.

Pushes to `main` publish Snapshot builds using the Gradle product version. Tags
`vX.Y.Z` publish Release builds; prerelease tags publish Snapshot builds. The shared
GitHub run ID groups platforms into one release; rerunning the same run replaces
its platform artifacts. Publication is serial, validates all three files first,
and includes SHA-256, size, commit and OS/architecture metadata. Server-side
visibility is immediate for each successfully uploaded file; a network failure
midway can leave a partial release until the job is rerun.

Run `python3 -m unittest discover -s tests -p 'test_*.py'` for publisher tests.
For metadata-only checks, download the three workflow archives into `dist/` and
run `python3 scripts/publish_build_explorer.py --dry-run` with the standard GitHub
run environment. Native installer signing and clean-machine gameplay acceptance
remain separate roadmap items.

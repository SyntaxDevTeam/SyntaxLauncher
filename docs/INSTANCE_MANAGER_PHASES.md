# SyntaxLauncher v0.2 — Instance isolation and management implementation phases

**Canonical tracker:** [ROADMAP.md](ROADMAP.md), v0.2 section.  
**Milestone definition:** [MILESTONES.md](MILESTONES.md), v0.2.  
**Practical acceptance:** [TESTING_V0.2.md](TESTING_V0.2.md).  
**Last verified baseline:** GitHub Actions [39157681455](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39157681455), 4/4 jobs successful on the v0.2 implementation commit `7aa5605`.

This document divides v0.2 into two **ordered implementation tracks**, not a new competing roadmap. The existing code stays in place; the next iterations close gaps, verify real gameplay, and avoid changes to the working vanilla launch engine.

## Non-negotiable architectural invariants

- Every instance has a canonical, stable UUID. Renaming only changes the display name.
- An instance's mutable game state resides in `<dataDir>/instances/<uuid>/game`, **not** in the shared `<dataDir>/game` or a directory derived from the display name.
- The game files (`saves`, `config`, `mods` once available, `resourcepacks`, `shaderpacks`, `screenshots`, `options.txt`, etc.) remain isolated. No shared mutable directories or junction/symlink escapes.
- Immutable/re-downloadable artifacts (`versions`, `libraries`, `assets`, managed Java; generated native files must be treated carefully) can reuse common caches with integrity verification.
- Instance data stays valid if a version changes. Editing the Minecraft version is **not** permission to clear saves or configs.
- All normal operations are non-destructive: new instances stage their content, copies never modify their source, deletes go to recoverable trash, and malformed metadata stays on disk.
- `Legacy Vanilla` retains the existing v0.1 game directory. Migration is always explicit, copying rather than deleting its contents.
- UI invokes core services; Compose does not invent filesystem layouts or implement its own copy/delete algorithms.
- Instance/loader support is **Vanilla-only for v0.2**. Fabric belongs to v0.4, Microsoft authentication to v0.3.
- Current `MinecraftProcessManager` owns one active launch per launcher. Multiple **stored** instances are in scope; multiple simultaneous game processes are **not** promised for v0.2.

## Current implementation inventory (do not rebuild)

| Function | Current state | Existing implementation |
| --- | --- | --- |
| UUID identity, schema-v1 metadata, version/RAM/JVM fields | Implemented; unit tests passed | `core/instance/MinecraftInstanceRepository.kt` |
| Isolated game directories and shared Mojang caches | Implemented; path assertions passed; real gameplay pending | `MinecraftInstanceRepository.layoutFor()` |
| Per-instance working directory in game launch | Implemented; coordinator integration test passed | `launch/MinecraftLaunchCoordinator.kt` |
| Create/list/update/rename | Implemented; basic Compose wiring; UX acceptance pending | `MinecraftInstanceRepository`, `InstanceManagerDialog.kt` |
| Clone instance worlds/settings | Implemented; file-level tests passed; large-world/manual acceptance pending | `MinecraftInstanceRepository.cloneInstance()` |
| Move deleted instance into trash | Implemented; filesystem tests passed; **no GUI restore** | `MinecraftInstanceRepository.moveToTrash()` |
| Explicit copy/import of old v0.1 data | Implemented; file-level test passed; manual acceptance pending | `MinecraftInstanceRepository.importLegacy()` |
| Remember selected instance and restore its version/RAM | Implemented; CI passed; manual restart test pending | `LauncherPreferencesStore.kt`, Compose home |
| Dedicated instance management screen and richer progress | Not yet implemented | Existing UI is a single dialog |
| Large-data operation safety/recovery and active-process protections | Incomplete | Add to instance services/UI before broad beta |

## Track A — Separate Minecraft instances

### A1 — Instance identity and durable metadata

**Baseline:** implemented and unit-tested. **Next:** verify on real user data; avoid format churn.

**Deliverables**

- Maintain stable UUID per instance, separate from display name.
- Keep versioned `instance.json` with Minecraft version, RAM and JVM options.
- Preserve unknown schema-1 JSON fields on edit and clone, reject unsupported schemas without overwriting.
- Provide clear inventory errors if an instance folder/JSON is missing or corrupted.

**Acceptance**

1. Create two instances with equal names/version: their UUIDs and game directories differ.
2. Rename and restart: UUID, folder and saved settings remain unchanged.
3. Unknown JSON field survives rename and clone; newer/corrupt schema is left intact.
4. Unit tests pass on CI Linux; desktop builds on all supported targets.

### A2 — Data isolation and existing launch pipeline

**Baseline:** implemented and CI-tested; **manual real Minecraft verification is mandatory**.

**Deliverables**

- Continue resolving the selected instance's game directory through `MinecraftInstallationLayout`; do not fork `LaunchPlanBuilder` or Java management.
- Reuse safely verified client/libraries/assets/runtime caches across same-version instances.
- Prove that all game-created mutable files, including worlds, screenshots and settings, land under the selected instance.
- Ensure a missing saved instance cannot silently fall back to Legacy Vanilla.

**Acceptance**

1. Create instances A and B with the same Minecraft release.
2. Launch A, create `OnlyInA`, edit `options.txt`, quit.
3. Launch B: neither A's world nor configuration changes are present.
4. Create `OnlyInB`, return to A: A's world exists and B's does not.
5. Confirm the second launch reuses verified shared downloads while generating game-specific data in B.
6. Play remains disabled when the selected instance is missing or contains unsaved version/RAM edits.

### A3 — Explicit v0.1 legacy transition

**Baseline:** non-destructive importer exists; **needs end-to-end confirmation**.

**Deliverables**

- Keep `Legacy Vanilla` accessible as an intentional profile until migration is confirmed.
- Copy existing game content into a new UUID instance; never move/delete original content automatically.
- Display source path, destination and estimated required disk space before import (new work).
- Report progress and source/target copy errors; never expose a partial imported profile as valid (new work).

**Acceptance**

1. Run Legacy Vanilla and verify worlds still exist.
2. Import to a new instance; original directory remains byte-for-byte untouched.
3. Launch imported instance; its worlds and configs are present.
4. Cancel or simulate disk-full/permission error: partial destination is not selectable and the original is intact.
5. Explicitly document how users can recover data without using the launcher.

### A4 — Filesystem safety, concurrency and repair

**Baseline:** some safeguards exist (staging, atomic metadata, symlink rejection, trash); **additional work required before release acceptance**.

**Deliverables**

- Centralize real-path validation and detect source/destination symlink escapes, hard-link surprises where relevant, and overlapping roots.
- Add copy verification/error categorization, disk-space checks, cancellation/rollback and interruption recovery for large worlds. Core now has scanned size estimates, cooperative chunk cancellation, disk checks and staged rollback; desktop controls and interrupted-process recovery policy require acceptance.
- Prevent rename/version change/clone/trash/import of an active instance while Minecraft is running; account for detached game processes or explain and enforce conservative restrictions. PID/start-time activity markers now block mutations during launcher-managed preparation and detached Minecraft gameplay; complete manual multi-launch race verification before release.
- Ensure cleanup only touches the operation's own private staging area, never the original game or unknown folders.
- Add tests for interrupted staging, inaccessible files, malicious JSON paths, symlink trees, and partial copy failures.

**Acceptance**

1. A failing copy cannot alter the source or produce a visible half-instance.
2. An active instance cannot be trashed or mutated into an inconsistent state.
3. Error messages distinguish disk space, permissions, data corruption and locked files.
4. All existing isolation tests still pass and both Linux/Windows filesystem behavior is verified.

## Track B — Managing instances

### B1 — Create, list, select and rename

**Baseline:** available through the existing manager dialog and session; **manual usability verification pending**.

**Deliverables**

- Present a searchable or clearly sorted instance inventory including `Legacy Vanilla`.
- Creation gathers name and an available Minecraft version, with sane default RAM.
- Selecting an instance updates the main Play target and restores per-instance version/RAM.
- Rename only updates metadata, not filesystem identity or player saves.
- Store the last selected UUID independently from last used nickname.

**Acceptance**

1. User can create, select and rename an instance without editing files.
2. Restart restores the same selection, version and RAM.
3. Duplicate display names remain distinguishable in the UI (show version and an additional short identifier when needed).
4. A deleted/missing/corrupt selected profile is visibly reported, not launched as another profile.

### B2 — Instance settings and validation

**Baseline:** RAM/version editing and persisted extra JVM args exist in core; **dedicated edit UI incomplete**.

**Deliverables**

- Provide a focused instance settings screen/dialog with name, Minecraft version, RAM and advanced JVM args.
- Separate unsaved form state from persisted data; explicit Save/Cancel, validation and error feedback.
- Preserve existing worlds when changing version, but warn about Minecraft downgrade/world compatibility.
- Validate RAM within supported bounds and reject contradictory minimum/maximum memory.
- Clearly distinguish per-instance settings from global/offline account preferences.

**Acceptance**

1. Changes persist across restart, and Cancel causes no file write.
2. Reopening settings shows exactly the stored values.
3. Invalid version/RAM/JVM input cannot overwrite valid metadata.
4. An existing world remains on disk after switching version; UI warns before older Minecraft opens newer worlds.

### B3 — Clone, trash and recovery

**Baseline:** backend clone and trash exist; GUI restore and progress do not.

**Deliverables**

- Show expected data size and copy progress for clone, including cancellation and failure.
- Clone worlds/config/settings with a new UUID; preserve unknown JSON fields.
- Require explicit confirmation before trashing an instance; explain where it will be recoverable.
- Add **Restore from trash** in the app; allow name conflict handling and restore to original UUID when safe.
- Show storage size and ensure user never has to manually guess which folder contains a deleted world.

**Acceptance**

1. Clone has the same starting worlds/settings and later diverges independently.
2. Cancel/failure leaves no selectable partial copy.
3. Moving to trash does not destroy game data; restore returns worlds and metadata.
4. Failed restore does not remove the recoverable trash copy.
5. Cannot trash/clone a live instance in a way that damages an active game.

### B4 — Usable Compose instance workspace

**Baseline:** minimal instance dialog exists. This is an acknowledged UI/UX gap.

**Deliverables**

- Replace the dense temporary dialog with a lightweight dedicated Instances view: clear inventory, current selection and one primary action per context.
- Use a small create/edit flow, contextual clone/restore/trash actions and progress/status surfaces.
- Keep Play dominant on the normal home screen, not competing with destructive actions.
- Include empty/loading/error/corrupt inventory states and keyboard/focus accessibility.
- Preserve established launch callbacks, cancellation and shutdown policy; no visual changes in core.

**Acceptance**

1. New user can create an instance and start Minecraft without opening advanced options.
2. Standard operations require intuitive, predictable numbers of clicks.
3. Error/cancel/disabled states remain readable at 1080×720 and normal OS DPI scales.
4. Real desktop screenshots and manual feedback confirm the design; successful compilation alone is not a UX pass.

## Execution order and release gates

| Sequence | Phase | Priority | Existing status | Completion gate |
| --- | --- | --- | --- | --- |
| 1 | A1 + A2 | P0 | Backend + CI passed | Real two-world isolation and cache reuse |
| 2 | B1 + B2 | P0 | CRUD + basic UI; settings UI partial | Restart selection and explicit settings Save/Cancel |
| 3 | A3 + A4 | P0 | Import exists; safety hardening partial | Non-destructive legacy import and interruption tests |
| 4 | B3 | P0 | Clone/trash exist; restore/progress absent | Recoverable clone/trash/restore tested with worlds |
| 5 | B4 | P1 | Prototype manager dialog | Accessible usable UI manual acceptance |
| 6 | End-to-end acceptance | P0 | CI green on initial v0.2 slice | Linux/Windows/macOS builds plus real game tests |

**The milestone cannot be marked `DONE` until:** all P0 gates pass, existing v0.1 Legacy Vanilla Play still works, both same-version instances retain separate worlds after launcher restart, and the user can clone/delete/restore without accidental loss of data. Each task must update [ROADMAP.md](ROADMAP.md) and prepend [WORKLOG.md](WORKLOG.md); tests and the real acceptance evidence must be recorded, not merely planned.

## Deferred intentionally

- Fabric/Forge/NeoForge/Quilt loader support, mod installation, online accounts, modpacks and repository search belong to later milestones.
- Multiple simultaneously running Minecraft game processes are a separate architectural decision; current single-process management is preserved in v0.2.
- Schema-v2 migration routines should not be invented before an actual schema-changing requirement is defined. The invariant is **never discard v1 data**; create and test an explicit migration when the schema changes.
- Signed installers/auto-updates are not necessary to validate instance isolation and management.

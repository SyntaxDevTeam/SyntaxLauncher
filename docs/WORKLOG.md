# SyntaxLauncher Worklog

This file is the append-only development journal for repository-changing work.

Newest entries go first, directly below this introduction. Historical entries should not be rewritten except to correct an obvious factual error.

Every code/build/documentation/CI task must add an entry according to `AGENTS.md`.

---


## 2026-10-23 — Resolve initial native packaging checks and preview upgrade ordering

**Milestone:** v0.12 / native distribution
**Author/Agent:** Codex
**Status:** Partial — fixes locally verified; repeat native hosted checks pending

### Completed
- Initial CI 40009406959 passed application/loader verification and the full macOS DMG job. Windows correctly stopped at WiX WIX7015 requiring explicit EULA acceptance. The maintainer then explicitly accepted the WiX v7 OSMF EULA and confirmed compliance with its conditions in this chat; CI now records that acceptance before loading the UI extension. No fee purchase or signing/notarization claim is made.
- Linux built DEB/RPM/AppImage but DEB installation exposed missing writable XDG menu directories on the minimal runner. Added packaging-time adaptation of the matching JDK's own postinst/spec templates to create shared standard menu directories before registration, rather than masking failed package scripts or merely preparing the test host.
- Added explicit same-numeric-version MSI duplicate detection while permitting normal repair/maintenance, with a second-build native negative smoke. Added per-user parent-directory cleanup for empty directories and canonical version in installer comments.
- DEB/RPM preview revisions now include prerelease and CI build/attempt below Stable revision 1; real dpkg comparison tests verify repeated DEV → Stable → 1.0 ordering. Updated the RPM lifecycle environment to current Fedora 44, confirmed through the Fedora project's published download page.

### Changed files
- `scripts/{package_installers,test_package_installers}.py`, `packaging/smoke-{linux.sh,windows.ps1}`, workflow — native scripts, EULA acknowledgment, MSI guards, preview revisions and Fedora lifecycle.
- `docs/{INSTALLATION,DEVELOPMENT,WORKLOG}.md` — actual behavior, prerequisites and initial hosted outcomes.

### Verification
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — PASS, 21 tests, including actual dpkg version ordering and template fail-closed behavior.
- Application source and Gradle configuration are unchanged from the prior passing full test/build. Native fixes require their own repeated hosted acceptance; reported in chat after push.
- Initial hosted macOS job — PASS: DMG construction, read-only verification/mount, Applications link and copy/removal smoke. Initial Windows/Linux failures described above prevented incomplete preview publication.
- `git diff --check` — PASS; native clean-machine/signing/gameplay gates remain unchecked.

### Roadmap updates
- Installer items remain in progress until the corrected hosted lifecycle/publication succeeds; no final release gates waived.

### Follow-ups / known issues
- Same numeric MSI versions require uninstall/reinstall; the wizard now rejects accidental duplicate product registration. All signing/trust/native GUI and actual cross-version upgrade gates remain open.

---

## 2026-10-23 — Prepare WiX v7, DMG and multi-distribution Linux installers

**Milestone:** v0.12 / native distribution
**Author/Agent:** Codex
**Status:** Partial — implementation/local verification complete; native hosted acceptance tracked separately

### Completed
- Followed the maintainer's explicit request for Windows WiX v7, macOS and Linux formats beyond DEB. Added WiX 7.0.0 MSI, DMG, DEB/RPM and a single-file AppImage option using the same verified application payload as portable/update assets. Kept canonical version 0.9.3-DEV and public Stable 0.9.2 unchanged.
- Authored explicit Windows per-user components with stable path-derived GUIDs, HKCU key paths, stable UpgradeCode, downgrade blocking, real WixUI installation wizard, Start menu entry and app-only uninstall. WiX is pinned/checked independently of jpackage's Windows MSI integration.
- Added branded ICO/ICNS format conversions from the existing PNG; only macOS keeps the native pre-1.0 major mapping. Windows/Linux use the actual numeric SemVer core so future 1.0 Windows upgrades are not ordered below an artificial 1.9 package.
- Added host-native packaging and lifecycle CI: Windows payload hash/repair/uninstall/menu/data preservation; DMG verify/read-only mount/application copy/removal; DEB dependency installation/removal, isolated Xvfb startup; RPM installation/removal in Fedora 44 with dependency resolution; AppImage extract/startup. Installer tools/runtime use versioned official AppImage assets and pinned SHA-256.
- Added fail-closed complete-set/checksum verification before publication. New DEV/SNAPSHOT/future Stable jobs require five installers and SHA256SUMS-installers.txt alongside the six existing portable/update files. Build Explorer remains portable-only. Existing installed bootstrap/updater keeps newer images in user storage and never rewrites package-owned files.

### Changed files
- `scripts/{package_installers,verify_installers,test_package_installers,publish_preview}.py`, `packaging/smoke-*` and `.github/workflows/build.yml` — installer authoring/build, native acceptance, checksums and complete-set publishing.
- `launcher-desktop/build.gradle.kts` and `branding/{syntaxlauncher-icon.ico,syntaxlauncher-icon.icns,README.md}` — platform metadata/version behavior, icons and provenance.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,INSTALLATION,MILESTONES,ROADMAP,UPDATING,WORKLOG}.md` — exact scope, dependency/runtime/ABI requirements, user-data/upgrade policy and pending native gates.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS: 257 tests (253 passed, four opt-in network tests skipped), build and Linux app image.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — PASS, 19 tests including installer versions/identity/payload, wrong WiX rejection, real AppDir quoting/permissions and incomplete/corrupt-set publication rejection.
- `python3 -m unittest discover -s tests -p 'test_*.py'` — PASS, six Build Explorer tests. Workflow YAML parses; POSIX smoke scripts pass `bash -n`; `git diff --check` — PASS.
- Local DEB and AppImage construction — PASS. Initial DEB attempt correctly refused a JDK-26 image passed to JDK-25 jpackage; repeating with the image's exact JDK succeeded. AppImage tool/runtime bytes matched pinned official release digests. Inspected DEB metadata/dependencies. Did not install a system package on the maintainer's machine.
- Local extracted AppImage native startup — PASS: live manifest/metadata loaded using isolated temporary XDG data; deliberately stopped after 12 seconds (timeout 124). No account/world/gameplay acceptance is inferred.
- Commit/push, hosted Windows/macOS/Linux lifecycle checks and installer asset availability are checked and reported in chat; unverified roadmap items remain in progress until that evidence exists.

### Roadmap updates
- `[ ] v0.12 / Native installers — Windows WiX v7, DMG, DEB/RPM/AppImage and complete-set publication implemented; native hosted outcomes pending`
- `[ ] v0.12 / Native installers — clean-machine GUI/trust, prior-version upgrades, additional distributions and RPM graphical startup remain independent gates`

### Follow-ups / known issues
- First installer previews are unsigned. No certificates/notarization, Flatpak/repository hosting or CPU-target expansion is claimed. AppImage needs compatible glibc/graphics; Alpine/musl/all historical Linux releases are not promised. Same-numeric-version MSI previews require uninstall/reinstall; cross-version native upgrade acceptance remains open. OS package managers report the bootstrap version, not any newer side-by-side updater image.

---

## 2026-10-23 — Verify Stable 0.9.2 publication and preserve next development version

**Milestone:** v0.12 / release publication
**Author/Agent:** Codex
**Status:** Completed — Stable 0.9.2 verified; next DEV workflow status reported in chat

### Completed
- Published the explicitly approved Stable 0.9.2 from immutable commit `e5b4daaf2d6208316b452a11e7eb478b9c03a18f`. [GitHub Actions run 40005510889](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/40005510889) completed successfully: application tests, official NeoForge/Forge processor checks, all three native packages, Stable/preview publication and Build Explorer publication.
- Verified [public v0.9.2](https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/tag/v0.9.2): not draft, not prerelease; six non-empty uploaded assets, with portable TAR.GZ and update ZIP for Linux x64, Windows x64 and macOS ARM64.
- During hosted publication the maintainer independently committed `cc0e378` (0.9.2-DEV) and edited the canonical property again to 0.9.3-DEV. Preserved both edits under the standing instruction to push all current changes. This next development version does not replace Stable 0.9.2 or declare v1.0 acceptance.

### Changed files
- `gradle.properties` — maintainer's next development version, 0.9.3-DEV.
- `docs/{WORKLOG,RELEASE_READINESS}.md` — immutable Stable publication evidence and next-development scope.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS for the release sources (257 tests: 253 passed, four opt-in network tests skipped) and repeated after the canonical DEV edit; outcome reported in chat.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — PASS for the release sources (14 tests) and repeated for the DEV version.
- Hosted release CI — PASS, including Linux/Windows/macOS packages and all publication jobs.
- Public Stable assets — PASS: all six expected uploaded non-empty files; `git diff --check` — PASS.

### Roadmap updates
- No additional native/signing/premium/v1.0 checklist boxes changed; the Stable publication evidence does not certify those acceptance gates.

### Follow-ups / known issues
- Microsoft/Mojang approval and the final native/signing/gameplay acceptance remain open as documented. Next DEV CI is independent of the verified Stable release.

---

## 2026-10-23 — Embedded game profiles, guided creation and shared legal footer; Stable 0.9.2

**Milestone:** v0.12 / pre-1.0 interface corrections
**Author/Agent:** Codex
**Status:** Completed — implementation and local verification; hosted publication outcome reported in chat

### Completed
- Moved RAM allocation into settings for the active profile, with explicit Save and discarded unsaved drafts. Legacy settings save immediately without a delayed stale write; other game profiles retain their memory limits. Removed duplicate sidebar profile navigation and placed Game type below Game version.
- Embedded the searchable profile list and read-only content inventory in the main launcher, retaining per-row Edit, clone/import previews and recoverable trash/restore. Added a distinct generated voxel workshop background with the full prompt/tool provenance in the branding README. Inventory inspection is bounded and rejects symlink roots/folders; worlds are not opened or modified.
- Added a draft-only conditional creation wizard: Vanilla/mods, loader, complete pack/individual catalog additions/empty profile, name and final review. Loader-filtered packs validate their actual archive definition before publication. Compatible game/loader discovery, cancellation/retry and duplicate confirmation guards reuse existing services; the individual-additions catalog stays in the main window.
- Replaced the old footer slogan with accessible website, Discord and repository icons. On the maintainer's additional request, moved Privacy, Terms, publisher contact and the full existing unofficial/unaffiliated Mojang/Microsoft notice from the player sidebar to the shared footer on all pages. Compact layout reserves space for the full legal text and keeps the wizard's controls reachable at 400×400.
- Preserved the maintainer's local canonical version edit to 0.9.2. The maintainer explicitly authorized “Tak, opublikuj Stable 0.9.2”. Include all changes under the standing push instruction and use the existing tested three-platform Stable publication workflow. No account/world/settings schema changes or live premium approval claims.

### Changed files
- `launcher-desktop/.../ui/{SyntaxLauncherApp,InstanceWorkspace,ProfileWizard,ContentCatalog,LauncherSettingsContent,LauncherFooter,SyntaxComponents}.kt`, `desktop/{DesktopLauncherSession,ProfileContents}.kt` — main-window navigation, inventory/wizard/settings/persistence, community/legal footer.
- `launcher-core/.../modpack/ModpackCatalog.kt` and installer tests — chosen loader and profile-name handling while retaining the original installation API.
- Desktop home/settings/wizard/footer tests, EN/PL/DE resources and `branding/{profiles-workshop.png,README.md}` — translated interactions, responsive rendering and asset provenance.
- `gradle.properties`, `README.md`, `docs/{ARCHITECTURE,DESIGN,DEVELOPMENT,MILESTONES,ROADMAP,RELEASE_NOTES,RELEASE_READINESS,WORKLOG}.md` — canonical version and actual scope/acceptance/publication documentation.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS: 257 tests, 253 passed, 4 opt-in official-network tests skipped, zero failures/errors; Linux application image built.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — PASS: 14 publication/version/readiness tests.
- EN/PL/DE wide and 400×400 Compose rendering/interactions — profile inventory without world mutation, conditional wizard final-only writes, cancellation and duplicate prevention, active-profile/legacy RAM persistence and exact footer URLs/full disclaimer. A real main-window 400×400 wizard test covers the space occupied by header and legal footer; its initial clipped name field was corrected with a shorter adaptive wizard header.
- Visually inspected wide Polish profiles, settings and compact wizard renders. Generated test screenshots stay uncommitted in the build directory.
- Native Linux application-image startup — PASS with isolated temporary XDG data, live version catalog/metadata loaded; stopped deliberately after 12 seconds. No real player/account/world or Minecraft gameplay used. This smoke predates the final footer placement and is not cross-platform acceptance.
- `python3 scripts/release_readiness.py --format markdown` — PASS; v0.11 26 completed/0 open; v0.12 36 completed/9 open. `git diff --check` — PASS. Commit, hosted CI and public six-asset Stable release verification are reported in chat.

### Roadmap updates
- `[x] Main-window profiles and settings / selected-game memory, sidebar order and single profile navigation`
- `[x] Main-window profiles and settings / embedded inventory, thematic background and retained advanced management`
- `[x] Main-window profiles and settings / conditional creation, filtered packs, inline extras and mutation guards`
- `[x] Main-window profiles and settings / community links and shared legal footer across translated compact/wide layouts`
- `[ ] Native focus/DPI/window-manager/gameplay acceptance — remains an independent final gate`

### Follow-ups / known issues
- Microsoft live acceptance remains deferred pending Mojang approval. Native signing/notarization, clean-machine installs/upgrades and final gameplay/accessibility coverage remain open in the existing v0.12/v1.0 checklists. Stable 0.9.2 is a bounded pre-1.0 release, not completion of those gates.

---

## 2026-10-23 — Complete diagnostic implementation and production hardening before final acceptance

**Milestone:** v0.11 / v0.12
**Author/Agent:** Codex
**Status:** Completed — v0.11 and scoped production implementation; final native/signing/hosted-update acceptance remains Partial

### Completed
- Followed the maintainer's instruction to defer live Microsoft account work while Mojang AppID approval is pending. Preserved official premium entitlement checks and the pre-existing explicit offline/player-manager working-tree changes, including account/preferences/UI tests; no live premium success or review approval is claimed.
- Updated the stale checkout by fast-forwarding to origin/main a68c25d after retaining all local changes in a Git stash backup. Preserved the published bounded MinecraftDiagnostics classifier, privacy/stale-process tests, CI readiness audit and release evidence. The health scan reuses that classifier; old failure UI forwards into the same guarded repair path.
- Added managed client/library/native/index/logging/asset integrity scans, unchecksummed-file counts, launch/Java/main-class/classpath and local mod evidence, fixed confidence/category reports and safe report copy. Raw logs/account objects/argument values/private paths are not exported. A successful repair clears previous failure evidence, and preparation/repair cannot reuse old process logs or exits.
- Added selected-game health UI in EN/PL/DE, fixed-footer explicit repair, progress/cancel/outcome/re-scan, local game-log access and shared job/activity guards. Repair reuses the verified Minecraft/loader/Java preparation pipeline, validates unchanged profile requirements and never starts a game or deletes worlds/mods/options.
- Added shared disk-space admission, redacting rotating launcher logs, bounded settings log viewer/storage locations and common JVM dependency lockfiles. Native Compose/Skiko families remain host-selected at pinned distribution versions.
- Documented portable installation/first run, backup/removal/OS credentials, direct-HTTPS scope, interrupted-payload restart, updater retention, candidate notes and remaining native/signing gates. Product version stays 0.7.1; no Stable release/tag created.

### Changed files
- `launcher-core/.../diagnostics/{GameDiagnostics,SecretRedactor,MinecraftDiagnostics}.kt`, `launch/MinecraftLaunchCoordinator.kt`, `network/{DownloadManager,DiskSpace}.kt` — health evidence/scanning/classification, repair snapshots, privacy and download admission.
- `launcher-desktop/.../{DesktopLauncherSession,Main}.kt`, `ui/{GameHealthDialog,MinecraftDiagnosticsDialog,LauncherSettingsContent,SyntaxLauncherApp}.kt`, logging converter/config and EN/PL/DE bundles — guarded maintenance, health/settings UI, log retention and native wiring.
- Diagnostic/repair/disk/logging/session/translated-render regression tests; root/module build files and dependency locks.
- Pre-existing player manager/account/preferences/UI/translation/doc changes retained, verified and included under the standing publication instruction.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,DESIGN,MILESTONES,ROADMAP,RELEASE_READINESS,RELEASE_NOTES,INSTALLATION,TESTING_V0.11,TESTING_V0.12}.md` and this worklog — actual scope, evidence and final acceptance.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS on final sources: 248 tests, 244 passed, four opt-in official-network smoke tests skipped, zero failures/errors; Linux application image built. Common dependency locks regenerated/reviewed and used without `--write-locks` for final verification.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — PASS: 14 version/packaging/publication/readiness tests.
- `python3 scripts/release_readiness.py --format markdown` — PASS; v0.11 26 completed/0 open, v0.12 36 completed/9 open. The audit correctly keeps independent native/signing/deferred-account gates open.
- Deterministic repair — missing/corrupt file restoration, cancelled repair/retry, no child process, preserved worlds/options/mods, no account-vault access, duplicate Play protection and fresh subsequent scan pass. Stale/symlink/unrelated logs and previous same-game process evidence are rejected.
- EN/PL/DE health UI at 400×400 and 820×700 — PASS; fixed-footer repair is reachable and refreshed results display after repair. Inspected Polish 400×400 production Compose render; PNGs remain generated/uncommitted.
- Native Linux package smoke — PASS for application startup, live catalog/metadata loading and platform file-log output using isolated `/tmp` XDG data. Initial sandbox-only attempt could not access X11; the authorized native attempt succeeded and was deliberately stopped after 12 seconds (timeout status 124). No Minecraft game, real account or user world was used. No clean-machine/all-platform UX claim.
- `git diff --check` — PASS. Commit/push and this revision's hosted CI outcome are reported in chat after publication.

### Roadmap updates
- `[x] v0.11 / evidence, diagnostic engine, safe repair, translated UI and deterministic privacy/repair quality gates`
- `[x] v0.12 / reliability implementation, documented packaging/retention, versioned dependencies, settings and log/report access`
- `[ ] v0.12 / native installation/upgrade/channel-switch acceptance, signing/notarization and final keyboard/focus checks — remain open`
- `[ ] Microsoft / approved-AppID live acceptance — explicitly deferred by maintainer, not waived for a full premium release`

### Follow-ups / known issues
- Complete the nine explicitly open v0.12 acceptance items and broader v1.0 native/gameplay/migration matrix before declaring a stable public release. Signing certificates/trust acceptance and Windows/macOS sessions are not supplied by this Linux task.
- Existing limited Forge/OptiFine/dependency-parser support matrices remain in force; no historical compatibility expansion or complete mod solver is claimed.
- This task's initial local-only assessment missed newer published diagnostics. Synchronization and preservation are recorded above; no remote history was rewritten. The integration stash remains as a recoverable backup.


## 2026-10-23 — Verify final three-platform CI and resolve previous preview-publication blocker

**Milestone:** v0.11 — Diagnostics; v0.12 — CI/CD and distribution
**Author/Agent:** GPT-6
**Status:** Completed for hosted verification; live/native acceptance remains open

### Completed
- Confirmed GitHub Actions [39901800185](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39901800185) completed successfully across core+desktop verification, official loader smoke tests, Linux/Windows/macOS packaging, release bundle verification and Build Explorer publication.
- Confirmed public six-asset DEV release `dev-180.1-v0.7.1`. The earlier 403 from run 39900923246 is not a current publication failure; no speculation about what credential/permission changed.
- Kept Stable at v0.7.1. This is an explicit DEV development build, not a v0.11/v1.0 stable release, and native gameplay, Microsoft approval and signing remain independent.

### Changed files
- `docs/RELEASE_READINESS.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.11.md`, `docs/WORKLOG.md` — reconcile authenticated public publication and CI acceptance evidence.

### Verification
- `./gradlew test` / `./gradlew build` — PASS in hosted run 39901800185.
- All three platform packages and update bundles — PASS in hosted run 39901800185.
- GitHub DEV (six release assets), Build Explorer — PASS in hosted run 39901800185.
- Live native/gameplay/entitlement verification — NOT RUN as part of this CI; remains open.

### Roadmap updates
- `[x] v0.11 / Automated full tests and three-platform packaging` — verified.
- `[ ] v0.11 / Manual diagnostic and repair acceptance` — remains open.
- `[ ] v0.12 / Native update and SNAPSHOT channel acceptance` — remains open.

### Follow-ups / known issues
- Do not equate a successful DEV release with permission to advance the canonical Stable SemVer or to publish a v1.0 release.

---


## 2026-10-23 — Correct Kotlin diagnostic timestamp and verify v0.11 compile

**Milestone:** v0.11 — Diagnostics and Repair; v0.12 — Release Operations
**Author/Agent:** GPT-6
**Status:** Partial — hosted verify passed, final cross-platform/update publication acceptance pending

### Completed
- Fixed Kotlin smart-cast issue in the desktop module by assigning the nullable process timestamp to a local variable before checking it.
- The current source was verified in [Actions 39901800185](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39901800185): the core/desktop compilation, Gradle tests and existing official installer checks passed. Portable app matrix was queued after verify; do not infer its result from that fact.
- Verified earlier full native packaging (run 39900923246) passed on Windows, Linux and macOS, and Build Explorer publication succeeded; the *preview GitHub Release publication job* separately failed during tag creation with HTTP 403 from the personal access token. No successful DEV release is claimed.
- Maintained intentionally unchanged canonical product `launcherVersion=0.7.1`: an automatic stable version bump would publish development code before native/third-party release acceptance.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — local timestamp to satisfy cross-module nullability.
- `docs/{WORKLOG,ROADMAP,RELEASE_READINESS,TESTING_V0.11}.md`, `README.md` — accurate verification and remaining publication blockers.

### Verification
- `./gradlew test` / `./gradlew build` — PASS as part of hosted CI verify on 39901800185; not run locally.
- Built-in installer regression checks — PASS in the same hosted verify job.
- Native packaging for the *newest* source — queued at time of record; previous commit's three platform packages passed in run 39900923246.
- Manual in-game diagnostic and native update acceptance — NOT RUN.

### Roadmap updates
- `[ ] v0.11 / Diagnostics quality gate` — code and unit tests pass CI, native/real-failure validation still needed.
- `[ ] v0.12 / DEV publication and native update` — publication token authorization and real update matrix remain open.

### Follow-ups / known issues
- GitHub preview publishing: `SYNTAXLAUNCHER_RELEASE_TOKEN` currently receives `Resource not accessible by personal access token` (HTTP 403) creating `refs/tags/dev-*`. A repository/org owner must authorize Contents read/write for SyntaxDevTeam/SyntaxLauncher; do not paste tokens into chat or source.
- Microsoft AppID approval and native signing/notarization are independent v1.0 gates.

---


## 2026-10-23 — Add explicit local log-folder access to failure diagnostics

**Milestone:** v0.11 — Diagnostics and Repair
**Author/Agent:** GPT-6
**Status:** Partial — implemented, CI/native acceptance pending

### Completed
- Added an explicit Open local game logs action beside diagnosis/managed-file repair; it opens the last attempted game's own logs directory rather than the currently selected game's folder.
- Guarded against a redirected/missing logs directory and unsupported desktop folder-opening APIs. Game log contents are not loaded into the diagnostic dialog or transmitted by the launcher.
- Added EN/PL/DE translations, continuing the low-disclosure diagnostic guidance.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/MinecraftDiagnosticsDialog.kt` — safe native file manager action.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties` — labels.
- `docs/{ROADMAP,TESTING_V0.11,WORKLOG}.md` — partial acceptance status.

### Verification
- `./gradlew test` / `./gradlew build` — not run locally; GitHub CI required.
- Native desktop open-folder acceptance — pending.
- Earlier diagnostic core/desktop CI verify task passed on run 39900923246; additional changes require their own hosted acceptance.

### Roadmap updates
- `[ ] v0.11 / Technical details/log access` — implementation added; native acceptance pending.

### Follow-ups / known issues
- Raw logs can still contain sensitive information and should not be attached to support tickets without review.
- A complete redacted support-bundle export remains outside this action.

---


## 2026-10-23 — Add non-destructive release-readiness audit for final stabilization

**Milestone:** v0.12 — Production Hardening; v1.0 — Stable Release
**Author/Agent:** GPT-6
**Status:** Partial — read-only release gate auditing added, hosted verification pending

### Completed
- Introduced a dependency-free read-only roadmap auditor that lists completed/open/P0-or-approval checklist counts for each historical and final milestone.
- Explicitly separated canonical product SemVer from roadmap progress and refused automatic status rewriting, guessing native acceptance or counting a green build as AppID approval.
- Added deterministic Python tests covering blocked/open stages, duplicate milestones, version key integrity and report caveats.
- Added an informational per-run GitHub Actions summary after the existing Python test suites. It never fails CI merely because future v1.0 gates remain open.
- Recorded the remaining P0 production gates, including live Microsoft app approval, actual Java Edition entitlement verification, native release signing/notarization and multi-platform update/data-preservation acceptance.

### Changed files
- `scripts/release_readiness.py`, `scripts/test_release_readiness.py` — deterministic milestone scanner and tests.
- `.github/workflows/build.yml` — informational release gate summary.
- `docs/RELEASE_READINESS.md`, `docs/{ROADMAP,WORKLOG}.md`, `README.md` — remaining acceptance and usage.

### Verification
- `./gradlew test` and `./gradlew build` — NOT RUN locally (GitHub connector-only editing).
- Python script tests — NOT RUN locally; collected by existing hosted `unittest discover -s scripts -p test_*.py` step.
- CI — pending for this commit. Release audits do not certify gameplay or cross-platform native installation.
- Manual end-to-end — NOT RUN.

### Roadmap updates
- `[ ] v0.12 / CI/CD / Read-only milestone acceptance audit` — source implemented, awaiting CI.
- `[ ] v1.0 / Microsoft approval, platform and quality gates` — remain open.

### Follow-ups / known issues
- Run `python3 scripts/release_readiness.py --enforce v0.10` for a scope-limited v0.10 checklist check. Do not use this to infer independent v1.0 readiness.
- Do not bump `launcherVersion` or trigger stable publishing until current source CI is green and the stable release support scope is deliberately confirmed.

---


## 2026-10-23 — Begin v0.11 local diagnostics and scoped repair

**Milestone:** v0.11 — Diagnostics and Repair
**Author/Agent:** GPT-6
**Status:** Partial — source/test implementation; hosted CI and native acceptance pending

### Completed
- Confirmed the current head was green in hosted CI 39839934723; v0.9/v0.10 bounded roadmaps are checked and the maintainer reported completing the combined test session (specific platform/combination outcomes not supplied).
- Added a UI-independent, bounded, read-only diagnostic scanner for recent game logs, run output and crash reports. Returned findings are static messages, never raw log content, authentication arguments or tokens.
- Classified common Java, memory, mod dependencies, missing classes, ZIP/hash integrity, graphics and authentication errors with confidence levels; a bare exit code produces a possible UNKNOWN finding.
- Added a localized crash diagnostics dialog, tied to the actual last attempted game even if the user later changes selection.
- Added explicit Verify and repair managed files, reusing checksum-based game/loader/Java preparation with the existing per-instance activity lease. The action does not delete saved worlds or personal mods.
- Added synthetic tests for classification, stale/symlink rejection, bounded log tails and token non-reflection.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/diagnostics/MinecraftDiagnostics.kt` — scanner and structured result.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/diagnostics/MinecraftDiagnosticsTest.kt` — deterministic evidence/privacy tests.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopLauncherSession,ui/MinecraftDiagnosticsDialog,ui/SyntaxLauncherApp}.kt` — last game tracking, repair service and failure dialog.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties` — translated guidance.
- `README.md`, `docs/{MILESTONES,ROADMAP,TESTING_V0.11,WORKLOG}.md` — scope and acceptance.

### Verification
- `./gradlew test` / `./gradlew build` — NOT RUN locally (GitHub connector editing; hosted CI is required).
- Hosted CI — PENDING for the new commit; last pre-change head 39839934723 completed successfully.
- Native/real-world tests — NOT RUN for v0.11; the maintainer's v0.9/v0.10 completion report is separate.

### Roadmap updates
- `[ ] v0.11 / evidence, diagnosis, UI and explicit managed-file repair` — initial implementation submitted, awaiting CI/native acceptance.
- `[ ] v0.11 / complete mod inventory, repair policy, advanced evidence and quality gates` — not implemented.
- `[ ] v0.12 and v1.0 / native acceptance, signing, approval, production checks` — unchanged.

### Follow-ups / known issues
- Add verified runtime/mod inventory details and exact actionable checks; expand repair only with read-only integrity plans and user approval.
- Native Windows/Linux/macOS keyboard/privacy and actual repair flows require separate acceptance; no v1.0 release.
- Microsoft AppID approval remains unverified; do not claim premium live authentication acceptance or offline entitlement bypass.

---


## 2026-10-22 — Keep gateway branding in the header and clear the home hero

**Milestone:** v0.1 — Desktop visual refinement
**Author/Agent:** Codex
**Status:** Completed — local build/render verification; final maintainer review pending

### Completed
- Applied maintainer feedback after the gateway preview: retain the gateway in the header and application/window icon, remove the repeated foreground mark from the home Hero.
- Removed the hero-only halo/shadow and unused accent token. The existing landscape and readable native heading/profile copy remain.
- Simplified hero measurement: wide copy stays on the left, narrow copy uses the available width, profile details are bounded by the remaining height after the heading.
- Updated the existing resize regression to validate visible, nonoverlapping copy and no foreground hero logo across the same 27 size/density cases.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{LauncherArtwork,SyntaxTheme}.kt` — landscape-only hero and removed unused mark accent.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherHomeRenderTest.kt` — revised resize contract.
- `launcher-desktop/src/main/resources/branding/README.md`, `docs/{DESIGN,ROADMAP,WORKLOG}.md` — current placement and verification evidence.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS: 228 tests, 224 passed, four optional live tests skipped; no errors/failures. Unchanged core results reused, desktop suite reran and Linux image rebuilt.
- Manual render inspection — Polish 1440×810 home shows the gateway only in the header, full unobstructed landscape and readable copy. Existing wide/narrow/translated and resize/density regressions pass.
- `git diff --check` — PASS before publication. Native preview opened from the rebuilt production entry point; final push/CI status reported in chat.

### Roadmap updates
- `[x] v0.1 / UI / open-gateway header/icon integration with landscape-only Hero`
- `[ ] v0.1 / UI / final maintainer review and broader Windows/macOS package/DPI acceptance`

### Follow-ups / known issues
- Startup updater retains its separate brand mark; no website/Entra changes or version bump.
- The earlier native screen-capture restriction remains; inline preview comes from actual Compose rendering, not a claimed desktop screenshot.

## 2026-10-22 — Preview the selected open-gateway desktop identity

**Milestone:** v0.1 — Desktop branding refinement
**Author/Agent:** Codex
**Status:** Completed — integration and local verification; maintainer visual acceptance pending

### Completed
- Implemented the maintainer-selected “Otwarte wrota” concept as a separate transparent high-resolution foreground, retaining the existing scenic hero background.
- Exported the same gateway to the header/native window/startup/Linux package icon; changed only the foreground halo to cornflower blue, leaving the surrounding interface unchanged for comparison.
- Retained responsive measured logo placement and intrinsic aspect ratio. Explicit selection in this conversation supersedes the earlier retain-original instruction.
- Opened the actual production desktop entry point on Linux using the newly built application libraries, native rendering settings, Polish UI and isolated temporary preview state. Live version catalog and selected metadata loaded successfully. No account login or game launch was performed.

### Changed files
- `launcher-desktop/src/main/resources/branding/{hero-mark.png,syntaxlauncher-icon.png,README.md}` — gateway foreground/icon and exact generation provenance.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{LauncherArtwork,SyntaxTheme}.kt` — shared cornflower mark halo token.
- `docs/{DESIGN,ROADMAP,WORKLOG}.md` — selected desktop preview, completed integration and remaining visual acceptance.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS: 228 tests, 224 passed, four optional live smoke tests skipped; no failures/errors. Core tasks reused unchanged verified outputs; desktop tests reran and Linux app image rebuilt.
- Existing Compose render/layout regressions — PASS, including 27 live-resize/density combinations and wide/compact/translated screens. Inspected 1440×810 and 1024×640 renders: complete gateway, no heading/profile overlap and scenery visible through the doorway.
- Native Linux launch — PASS: actual 1440×840 window opened with the new mark; live metadata loaded. The first direct invocation lacked the packaged Skiko library path; adding the app image's native/resource settings corrected the invocation without changing application code.
- Native screen capture — unavailable: desktop screen-capture permission was denied. Delivered production Compose render instead of claiming it is a native window screenshot; actual preview window remains available on the desktop.
- `git diff --check` — checked before commit. Commit/push/hosted CI outcome reported in chat after publication.

### Roadmap updates
- `[x] v0.1 / UI / selected open-gateway integration and local verification`
- `[ ] v0.1 / UI / maintainer visual acceptance and broader Windows/macOS package/DPI review`

### Follow-ups / known issues
- This is the requested in-app visual preview, not a new release/version, website/Entra branding change, or trademark clearance.
- The surrounding cyan interface palette remains available for a separate coherent color review after evaluating the gateway in context.

## 2026-10-22 — Complete public policies, Entra metadata and renewed AppID submission

**Milestone:** v0.3 — Application review
**Author/Agent:** Codex
**Status:** Completed — publication/submission; allowlist decision pending

### Completed
- Published four project-specific application/privacy/terms/support pages in the existing miniPORTAL site; browser-verified content/contact and cross-links.
- Saved Entra homepage/privacy/terms, marketing/support URLs, description, contact notes and original logo. Reopened manifest to verify persistence. Removed unused default Graph User.Read without adding access; preserved native redirect/audience/security settings.
- Restored original launcher branding after maintainer feedback; repository branding files remain unchanged.
- Pushed implementation b915ad2 to main after explicit approval following automatic review's initial rejection.
- Submitted one renewed request for the same AppID/tenant, with earlier denial, material corrections, immutable code and honest live/native acceptance limits. Verified and saved the visible receipt: “Thank you for contacting Mojang Studios.”
- Retained exact fields/text and receipt in the private local delivery packet; no tenant/administrative contact or secrets committed publicly.

### Changed files
- `docs/{MICROSOFT_APP,APP_REVIEW_2026-10,ROADMAP,WORKLOG}.md` — verified publication, metadata, submission receipt and remaining gates.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS for b915ad2 including restored logo: 228 tests, 224 passed, four optional live tests skipped; Linux image built.
- `./gradlew test` / `./gradlew build` — NOT RUN again for this documentation-only follow-up; runtime is unchanged.
- Manual — original saved Entra logo and persisted URLs/description, public pages/cross-links and official receipt verified.
- `git diff --check` — PASS.
- Hosted CI — run 39836343471 compile/tests passed; installer/package/publication checks in progress when recorded. Final result reported in chat/local packet.

### Roadmap updates
- `[x] v0.3 / AppID review correction / public pages, Entra metadata and renewed request`
- `[ ] v0.3 / allowlist approval, live sign-in/entitlements/refresh/gameplay and native vault matrix — still open`

### Follow-ups / known issues
- Form acknowledgement is not approval; no duplicate forms while awaiting decision.
- No publisher/domain/partner verification is claimed. Optional service reference is blank without an organizational record.
- The immutable implementation linked in the form predates this documentation-only outcome record.


## 2026-10-22 — Correct authenticated-play policy and prepare renewed AppID review

**Milestone:** v0.3 — Microsoft authentication and review compliance
**Author/Agent:** Codex
**Status:** Partial — implementation verified; website/Entra publication and submission tracked below

### Completed
- Identified the live official AppID form's explicit authentication/ownership requirement regardless of server configuration; the first denial supplied no individual reason.
- Retired anonymous/offline gameplay in desktop and added an independent coordinator gate before preparation; retained legacy records/worlds.
- Added Java entitlement validation before profile/session publication; desktop requests fresh renewal/revalidation at every Play, including cached signed-in identities. Failures block gameplay.
- Used project-owned public client ID by default, added pre-consent privacy/terms links, direct maintainer-selected contact and independence notice. A speculative logo replacement was reverted after maintainer feedback; the original mark is retained.
- Prepared project-specific public notices and full Entra/API/review evidence checklist; updated active guidance to supersede historical offline-play goals.

### Changed files
- Core account/launch sources and ownership/coordinator/loader fixture tests; desktop session/default identity/UI/resources and account/render tests.
- `AGENTS.md`, `README.md`, `PRIVACY.md`, `TERMS.md`, `docs/{ARCHITECTURE,DEVELOPMENT,DESIGN,MILESTONES,ROADMAP,MICROSOFT_APP,APP_REVIEW_2026-10,WORKLOG}.md` — current policy, application review evidence and publication status.
- Original branding files restored unchanged; no logo redesign is part of this work.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS: 228 tests, 224 passed, four optional live tests skipped; no failures/errors. Linux app image built.
- Manual verification — inspected 1440×810 and 400×400 rendered UI, original launcher mark (restored after feedback), disabled anonymous Play, readable policy/contact and memory controls. EN/PL/DE regression renders pass. Actual game/live Microsoft API and wider native platform checks NOT RUN.
- `git diff --check` — PASS before publication.

### Roadmap updates
- `[x] v0.3 / AppID review correction / authenticated launch, entitlement tests and publisher/contact notices`
- `[ ] v0.3 / AppID review correction / website, Entra and renewed submission — publication in progress`
- `[ ] v0.3 / live API/gameplay and native secure-storage matrix — approval/acceptance still outstanding`

### Follow-ups / known issues
- Website publication, Entra saves, repository push/CI and actual form receipt must be recorded after verification; no claim of allowlisting or live authentication from fixtures.
- Existing historical previews may retain the retired offline workflow; current source/build and revised request supersede them. No deletion of old releases or user data was authorized or performed.
- Pre-existing Entra client secret was neither read nor used; native desktop flow embeds no secret. Publisher-domain/partner verification must not be claimed without completing their independent prerequisites.





## 2026-10-22 — Publish all current changes and retain standing push instruction

**Milestone:** Cross-milestone development workflow
**Author/Agent:** GPT-6 / Codex
**Status:** Completed — local preparation; push/CI outcome reported in chat

### Completed
- Record the maintainer's standing instruction to commit and push all current project changes after verified work, including existing maintainer edits, without repeat confirmation.
- Include all urgent UI/mod-notice corrections and the maintainer's pre-existing canonical version change from 0.7.0 to 0.7.1. No release tag or Stable release is created.

### Changed files
- `AGENTS.md` — persistent publication instruction.
- `docs/WORKLOG.md` — publication scope and verification.
- All implementation files listed in the preceding urgent-feedback entry, plus `gradle.properties`.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS in the preceding task using version 0.7.1: 221 passed, four optional tests skipped.
- Documentation-only addition after that build; no runtime changes in this publication step.
- `git diff --check` — PASS.
- Push and hosted CI — performed after this commit is created; outcome reported in the chat.

### Roadmap updates
- No further status changes; urgent-feedback items are complete and broader native acceptance stays open.

### Follow-ups / known issues
- Report push/CI outcome in the chat; no Stable publication requested.


## 2026-10-22 — Urgent mod notices, header settings and friendly catalog

**Milestone:** v0.5/v0.6/v0.9/v0.10 — build feedback corrections
**Author/Agent:** GPT-6 / Codex
**Status:** Completed

### Completed
- Read declared Fabric nested module IDs and provides aliases with archive/depth/byte bounds, including nested nesting; disabled parent modules never satisfy enabled dependencies. Keep genuinely missing requirements visible.
- Replace raw warning floods on Home with an advisory count and direct mod-list action. Show evidence beside affected files and localized missing-requirement guidance.
- Move language/settings to the header before build identity, add a gear and settings dialog for update preferences, remove the sidebar heading and rename navigation to Game profiles / Profile gry / Spielprofile. Compact navigation gets a separate row.
- Add Modrinth artwork with category vector fallbacks, shared restrained accent colors and labels for extras/modpacks. Bound background artwork requests/cache through the shared HTTP client. Localize short descriptions for five common projects and label other authors’ original text.

### Changed files
- Core `mods/{LocalModManager,CatalogIcons}.kt`, `modpack/ModpackCatalog.kt`, `content/ModrinthResourcePacks.kt` — local advisory metadata, optional artwork service and propagate project icon URLs for every catalog category.
- Desktop `DesktopLauncherSession.kt`, `ui/{SyntaxLauncherApp,SyntaxComponents,SyntaxTheme,ContentCatalog,LocalModsDialog}.kt`, `i18n/{en,pl,de}.properties` — header, catalog and actionable notices.
- Core `mods/{LocalModManagerTest,CatalogIconsTest}.kt`, `content/ModrinthResourcePacksTest.kt`, `modpack/ModpackInstallerTest.kt`, desktop `ui/LauncherHomeRenderTest.kt` — nested modules/disabled parents, real missing dependencies, icon limits/cache and responsive translated header regressions.
- `README.md`, `docs/{DESIGN,ROADMAP,WORKLOG}.md` — behavior and acceptance scope.

### Verification
- `./gradlew --offline test build :launcher-desktop:createDistributable` — PASS: 225 tests discovered, 221 passed, four opt-in live tests skipped, zero failures/errors. Linux app image generated.
- Manual verification — inspected generated PL wide/400 px header and catalog/modpack PNGs; EN/PL/DE wide/compact render interactions and 400 px header with 108 dp chrome clearance pass. Graphical Minecraft/native cross-platform checks not performed.
- `git diff --check` — PASS. Initial overlapping test processes collided in Gradle binary report output; both exited, and subsequent sequential full runs passed.

### Roadmap updates
- `[x] Urgent build feedback / local mod notices`
- `[x] Urgent build feedback / header and catalog`

### Follow-ups / known issues
- Third-party descriptions are not generally machine translated; curated explanations cover common projects. Full loader version-range solving and broader native window/DPI/gameplay acceptance remain open.
- Existing user edit to `gradle.properties` is preserved; no commit, tag or publication requested.


## 2026-10-22 — Block automatic rollback of a freshly downloaded launcher

**Milestone:** v0.12 — Startup auto-update correctness
**Author/Agent:** GPT-6
**Status:** Fixed and deterministically verified; hosted publication tracked separately

### Completed
- Reproduced the maintainer's downloaded build #169 (successful workflow 39801507233, commit 9211e8a) against an existing Stable preference and legacy Stable v0.6.0. The old updater treated any difference between selected and embedded channels as permission to install older code, including an old active/previous cached installation.
- Replaced the channel-switch exemption with one monotonic eligibility comparison used for release downloads, active/previous caches and release-versus-cache ordering. Older product SemVer is rejected; where both CI identities are available a candidate must also have a strictly newer run/attempt. Without both identities, a strictly newer product version is required. Channel choice never overrides these conditions.
- Removed cached/current-build early returns that could bypass normal selection of a newer eligible cached image. Failed-update recovery retains integrity and startup-receipt checks and can only start a cache already proven newer than the running app.
- Kept stored channel preference and recovery files intact. A selected channel behind the downloaded app now waits for a newer compatible candidate. Updated channel-change copy in English, Polish and German and documented this behavior.

### Files changed
- Core `update/{LauncherRelease,StartupUpdater}.kt` — shared comparison and startup candidate selection.
- `LauncherChannelsTest.kt` — exact #169 → legacy v0.6.0 regression, 54 channel/version/build combinations and six active/previous-cache online/offline combinations; existing startup/fallback tests retained.
- Desktop `i18n/{en,pl,de}.properties`, `README.md`, `docs/{ARCHITECTURE,ROADMAP,UPDATING,WORKLOG}.md` — UI wording and current update policy/evidence.

### Verification
- `./gradlew -PlauncherVersion=0.7.0 test build :launcher-desktop:createDistributable --offline` — PASS: 219 passed / 223 discovered, four opt-in live tests skipped; zero failures/errors. Linux app image built successfully using the committed product version.
- Rejected candidates cause neither archive download nor child launch; stale update state/unknown keys remain byte-identical. Newer same-version builds and forward product upgrades still hand off successfully across all channels.
- GitHub workflow read — maintainer-supplied run 39801507233 is successful build 169.1 at 9211e8a; latest Stable endpoint reports v0.6.0. Only the local channel/update pointer keys were inspected; no account credentials were read.
- `git diff --check` — PASS.

### Follow-ups / known issues
- Already downloaded #169 binaries contain the old rule; users must once install a build with this fix, or Continue with #169 and choose DEV to avoid its stale Stable preference. This source change does not retroactively alter old archives.
- Full cross-platform hosted old→new execution remains a separate acceptance item. Existing user version edit in `gradle.properties` is preserved and excluded; no product-version bump, Stable release or release tag is requested.


## 2026-10-22 — Recover important account persistence integration from IntelliJ Shelf

**Milestone:** v0.3 — Microsoft/Premium accounts; recovery after v0.10
**Author/Agent:** GPT-6
**Status:** Desktop integration implemented and deterministically verified; native/live acceptance remains open

### Completed
- Reviewed the nonempty two-file account integration patch in IntelliJ Shelf (20.10.2026, 20:42), preserving both the original shelf and an identical external recovery copy. Reimplemented its useful changes against current sources instead of restoring obsolete loader/modpack/UI code or a legacy hardcoded client-ID fallback.
- Connected the desktop entry point to AccountManager and the existing native refresh-token vault. Identity inventory remains separate from credentials; Microsoft app configuration/approval requirements remain unchanged.
- Reloaded saved identities in the home picker, saved offline players on Play and added one-click saved offline selection. Microsoft login persists before publishing success; sign-out removes the local vault credential before updating the UI and supports retry after storage errors.
- Resolved Microsoft sessions from the durable store on Play, including refresh after restart and the current verified username. Account preparation disables conflicting controls, supports cancellation and never falls back to an offline identity on authentication failure.
- Preserved the shared loader/modpack/graphics launch pipeline, instance leases and window-close behavior.

### Files changed
- Desktop `Main.kt`, `DesktopLauncherSession.kt`, `DesktopMicrosoftSignIn.kt`, `ui/SyntaxLauncherApp.kt` — production persistence, session resolution and picker integration.
- `DesktopLauncherSessionAccountsTest.kt`, `ui/LauncherHomeRenderTest.kt` — five isolated account lifecycle/launch regressions plus saved offline player selection rendered in EN/PL/DE.
- `README.md`, `docs/{ARCHITECTURE,ROADMAP,TESTING_V0.3,WORKLOG}.md` — current integration status and remaining acceptance.

### Verification
- `./gradlew test build :launcher-desktop:createDistributable --offline` — PASS: 217 passed / 221 discovered, four opt-in live tests skipped, zero failures/errors. Linux application image built successfully.
- Deterministic account tests — restart without vault access, no credential values in identity inventory, failed secure save/removal, refresh using the current verified profile, failed-refresh retry without offline launch and offline Play without credential-store access all pass. Launch tests use a fixture Java child, not real premium gameplay.
- Compose rendering — saved offline player selection passes in English, Polish and German; generated Polish home screenshot inspected. Existing window-size and graphics/modpack regressions pass.
- `git diff --check` — PASS.

### Follow-ups / known issues
- Native Windows DPAPI/macOS Keychain/Linux Secret Service acceptance, Microsoft app approval and live authenticated Minecraft gameplay remain open; these tests use an isolated in-memory vault and fake sign-in provider.
- The user's existing local `gradle.properties` version edit (`0.7.0` → `0.7.1`) is preserved separately and excluded from this source change/publication. No Stable release or release tag is created.
- Historical/deleted shelves and the original reviewed shelf are preserved; this integration does not delete IDE recovery history.


## 2026-10-22 — Merge remote modpack work into the complete catalog installer

**Milestone:** v0.10
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Fetched six newer origin/main commits before pushing and merged their history rather than overwriting it. Preserved the existing My games `.mrpack` import action, its translations and importer regression tests alongside the completed online catalog.
- Consolidated `ModrinthModpackImporter` into a secondary facade over `ModpackInstaller`: local import and catalog installation both verify exact game/loader, payloads and overrides, prepare game/runtime and publish only a successful new UUID. Removed duplicate file-only publication logic instead of maintaining two installers.
- Preserved explicit optional-path/name/download-progress API inputs from the remote implementation; the child-first UI still installs required client files only. Receipts record explicit selections when used. Missing client/server environment entries retain required defaults while unknown provided roles fail. Retained the remote policy rejecting player-world/screenshot/managed-version roots.
- Preserved responsive repository staging and the stronger final cancellation/metadata checks. Added Cancel for My games imports and propagated coroutine cancellation rather than displaying it as a generic failed operation.
- Resolved conflicting milestone/roadmap/checklist descriptions around the final implementation and evidence. Historical remote worklog entries remain unchanged; their earlier missing-source CI blocker is addressed by the preceding commit publishing the required v0.9 sources.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/{ModpackInstaller,ModpackCatalog,ModrinthModpackImporter,ModrinthModpackPlanner}.kt` — shared installer/facade, explicit optional selections and preserved path/environment policy.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/ModrinthModpackImporterTest.kt` — retain remote import/hash/path regressions with configured metadata/preparation fixtures; verify explicit optional file/name behavior.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopLauncherSession,ui/ContentCatalog,ui/InstanceWorkspace,ui/SyntaxLauncherApp}.kt`, `src/main/resources/i18n/{en,pl,de}.properties` — merged My games entry, single installer wiring and import cancellation.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,MILESTONES,ROADMAP,TESTING_V0.10,WORKLOG}.md` — resolved final scope/evidence and retained history.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build :launcher-desktop:createDistributable`; 176 core and 35 desktop tests pass, four opt-in live tests skip, zero failures/errors (211 passed / 215 discovered).
- `./gradlew build` — PASS in the same merged-source invocation.
- Linux `:launcher-desktop:createDistributable` — PASS; broader native/gameplay acceptance remains v1.0 work.
- Real online pack setup — preceding task's Simply Optimized 1.21.1-6.0 / Fabric 0.17.3 cold-install/original-export PASS remains separate evidence; no graphical game boot or all-platform certification is added by this merge.
- Manual verification — conflict resolutions and both desktop entry paths reviewed; existing translated render/interaction regressions pass.
- `git diff --check` — PASS; no merge markers, generated/private artifacts or product-version change are included.

### Roadmap updates
- `[x] v0.10 / Model, catalog/import, exact preparation, required payloads/overrides, new-instance isolation, original export and installation quality gates` — retained and retested with remote work.
- `[ ] v1.0 / Wider native/gameplay/platform matrix` — remains open; hosted CI publication status is distinct from local validation.

### Follow-ups / known issues
- Default UI omits optional files; explicit optional choices are available only through the preserved secondary importer API. Export reproduces the original author definition, not local edits/optional choices or player data.
- No forced push, release tag or product-version bump is requested or performed. Hosted workflow results are reported separately after source publication.


## 2026-10-22 — Harden v0.10 import concurrency and check actual CI blocker

**Milestone:** v0.10 — Modpacks / shared v0.9 acceptance
**Author/Agent:** GPT-6
**Status:** Partial — code merged, CI blocked by uncommitted v0.9 source dependencies

### Completed
- Moved lengthy modpack network/ZIP preparation outside the global instance repository mutex. The new profile remains hidden as .creating-UUID until guarded final publication, while other profiles stay responsive.
- Added a desktop preflight against the Mojang version catalog and registered loader/compatible version list before any downloaded modpack is published.
- Re-ran CI verification against current main. After the coroutine-context correction, no errors are reported from the new modpack source; compilation still stops in older v0.9 classes because the related source files were never committed.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — nonblocking staged preparation.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — Minecraft/loader availability checks.
- `docs/WORKLOG.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.10.md` — current acceptance status.

### Verification
- GitHub Actions 39779900659 — FAIL at `:launcher-core:compileKotlin` (unresolved v0.9 types only: ShaderRendererProof, ShaderPackSelection, GameSettingsWrite, ShaderRendererSetup, GraphicsProfileResolver, LocalGraphicsProfileResolver, GraphicsEnhancementException). This baseline was broken before the v0.10 commits.
- `./gradlew test` and `./gradlew build` — blocked in CI; no successful run claimed.
- Native/manual v0.9/v0.10 session — pending maintainer.

### Roadmap updates
- `[ ] v0.10 / CI and combined acceptance` — remains open until all missing v0.9 source is published and tested.
- `[ ] v0.10 / One-click online catalog and export` — not implemented.

### Follow-ups / known issues
- In addition to failing core compilation, desktop v0.9 references OptiFineCatalog and InstalledPacks that are absent from main. Do not produce a release until these original features are restored and full CI is green.
- Importing a supported local .mrpack still requires official metadata availability for exact game/loader versions, and the pack must use supported download hosts.

---


## 2026-10-22 — Expose local .mrpack import in My games

**Milestone:** v0.10 — Modpacks
**Author/Agent:** GPT-6
**Status:** Partial — UI wiring committed, pending CI and joint maintainer tests

### Completed
- Connected the format-1 modpack importer to desktop session without changing v0.9 catalog or launch paths.
- Added My games Import Modrinth modpack (.mrpack) file chooser, async installation in the established mutation lifecycle, existing UI errors and automatic profile selection after success.
- Added English/Polish/German labels; import remains disabled when game is active or instance operation in progress.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — importer service.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{InstanceWorkspace,SyntaxLauncherApp}.kt` — picker and async import action.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties` — localization.
- `README.md`, `docs/{ROADMAP,MILESTONES,TESTING_V0.10,WORKLOG}.md` — testing and scope.

### Verification
- `./gradlew test` and `./gradlew build` — NOT RUN locally (connector-based edit; hosted CI pending).
- Baseline v0.9 compiler issue in previous CI 39671357003 remains a release blocker until the missing source files are recovered and compilation reverified.
- Manual v0.9/v0.10 combined gameplay tests — pending.

### Roadmap updates
- `[ ] v0.10 / UI .mrpack import` — coded, awaiting CI/real-game acceptance.
- `[ ] v0.10 / Ready-made catalog and optional file picker` — not implemented.

### Follow-ups / known issues
- Finish in-app Modrinth modpack discovery and one-click install.
- Repair incomplete v0.9 source publication and obtain green CI before promoting release version.
- Keep product version unchanged until proper stable-release validation.

---


## 2026-10-22 — v0.10 Modrinth modpack importer and safe staged instance creation

**Milestone:** v0.10 — Modpacks
**Author/Agent:** GPT-6
**Status:** Partial — changes published for CI; merged v0.9/v0.10 manual acceptance pending

### Completed
- Added strict format-1 Modrinth modpack planner for Minecraft/loader, required/optional/server-only client files, portable destinations, bounded sources and SHA-1/SHA-512 metadata.
- Added .mrpack reader, preflight for hostile ZIP contents and checked client overrides, shared-download verified required files and SHA-512 verification.
- Extended instance repository with an invisible .creating-UUID installation callback. Incomplete imports cannot become playable; existing profiles are never changed.
- Added deterministic synthetic ZIP and Ktor MockEngine tests for installation, optional/server-only separation, checksum rejection and traversal/future-format rejection.
- Deferred v0.9 and v0.10 manual gameplay tests to one combined maintainer acceptance session.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/ModrinthModpackPlanner.kt` — format-1 parsing and path controls.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/ModrinthModpackImporter.kt` — downloads, overrides and publication.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — createPrepared.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/ModrinthModpackImporterTest.kt` — deterministic fixtures.
- `docs/ROADMAP.md`, `docs/MILESTONES.md`, `README.md`, `docs/TESTING_V0.10.md`, `docs/WORKLOG.md` — scope, acceptance and remaining work.

### Verification
- `./gradlew test` — NOT RUN locally (GitHub connector provides source editing, not Gradle dependencies); hosted verification pending.
- `./gradlew build` — NOT RUN locally for the same reason.
- Existing baseline CI 39671357003 — FAIL from missing committed v0.9 source types before this change; must be repaired before combined validation.
- v0.9/v0.10 real gameplay — pending the maintainer's combined test session.

### Roadmap updates
- `[ ] v0.10 / .mrpack files, overrides and staged instance publication` — code implemented, acceptance pending.
- `[ ] v0.10 / ready-made Modrinth catalog, UI options, export and full acceptance` — remains open.

### Follow-ups / known issues
- Resolve missing v0.9 code references on main, get CI green, add desktop modpack import and online catalog.
- Only bounded format-1 and approved hosts are accepted; download mirror fallback/extra content types require additional work.
- Product version remains 0.7.0 until an intentional stable release decision; roadmap phase is not equivalent to released SemVer.

---





## 2026-10-22 — Complete Modrinth modpack installation and prepare all pending sources for publication

**Milestone:** v0.10
**Author/Agent:** Codex / GPT-6
**Status:** Completed

Scope: complete client `.mrpack` installation/catalog/preparation and managed original-definition export. Graphical gameplay/all-platform certification, arbitrary modified-instance export and destructive in-place pack updates are not claimed.

### Completed
- Added a working Modpacks catalog on the home screen before any saved game exists, and in Choose extras. One Install action selects a published release, installs its required client files/overrides, prepares Minecraft/loader/managed Java and selects the completed new game for Play. Search, empty/error/retry/cancel/progress states and EN/PL/DE compact layouts remain shared with the child-first catalog. Local import lives under More; original pack export is a secondary My packs action.
- Implemented strict bounded `.mrpack` inspection: complete ZIP CRC/size checks, UTF-8 index and portable destinations, common/client override precedence, exclusion of optional/dedicated-server files, initial HTTPS mirror policy and case/file-parent/private-metadata collision refusal. Required payloads use the central downloader with declared SHA-1, SHA-512 and byte count.
- Added `createPrepared`: a privately staged fresh UUID publishes only after success; long downloads do not hold the repository mutex. Existing games remain listable/leaseable. Errors/cancellation remove owned staging without publishing a false completed profile. Every reinstall/new release creates a separate UUID, preserving prior worlds/configs and unrelated instances.
- Refactored the existing coordinator preparation into a shared routine used by Play and account-free pack setup. Exact game/loader/runtime metadata remains authoritative; no duplicate hardcoded launch recipe or new authentication pipeline was introduced.
- Added an additive schema-1 modpack receipt/source directory, retaining original index/unknown metadata, source SHA-512 and explicit optional-file policy. Existing instances need no migration; unknown future receipt schemas and changed originals refuse export without mutation. Export preserves the original archive only and never collects a user's current game tree or adds local worlds/accounts/downloaded binaries. Existing output files and game directories are not export targets. Author-provided source content/terms remain intact.
- Added deterministic catalog/installer/preparation/export fixtures and translated UI interactions, covering malformed archives/CRC/hashes/paths, exact loader refusal, safe failures/cancellation, unchanged worlds/configs and original export. Ran a real public Simply Optimized 1.21.1-6.0 / Fabric 0.17.3 installation from fresh temporary directories: 18 referenced files plus Minecraft libraries/assets and managed Java 21, followed by exact original export. No graphical game boot or real account/world was used.
- Detected that tracked edits had returned to their earlier state during development, restored the missing connections, retained a local patch backup and reran the entire suite/build/package with --rerun-tasks. The cause was not established; final validation uses the restored source state, not earlier cached results.
- Included the pending untracked v0.9 graphics services, standalone worker, tests/checklist and initial v0.10 planner in the publication scope requested by the maintainer. Those source files were required by already tracked integration changes; no downloaded game/mod/runtime binaries or generated reports are included.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/{ModrinthModpackPlanner,ModpackArchive,ModpackInstaller,ModpackCatalog}.kt` — plan, bounded ZIP/override validation, verified client installation, receipt/original export and catalog.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/{instance/MinecraftInstanceRepository,launch/MinecraftLaunchCoordinator}.kt` — transactional prepared-instance publication and shared account-free game setup.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopLauncherSession,ui/ContentCatalog,ui/SyntaxLauncherApp,ui/InstalledPacks}.kt` — services, first-game catalog, automatic new-game selection, local import/original export and compact home actions.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties` — simple pack actions/progress and actionable compatibility/integrity/runtime/storage errors.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/{ModrinthModpackPlannerTest,ModpackInstallerTest,ModpackOfficialSmokeTest}.kt` — normal deterministic fixtures and separate opt-in real installation evidence.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{ContentCatalogTest,LauncherHomeRenderTest}.kt` — one-click/error/local-import and pre-existing-game-free translated wide/compact regressions.
- Pending sources from the previous entry: `core/content/{GameSettingsWrite,IrisRendererSetup,LocalGraphicsProfile,OptiFineCatalog,ShaderPackSelection}.kt`, `core/content/OptiFineInstallWorker.java`, related graphics tests, `ui/InstalledPacks.kt` / its test and `docs/TESTING_V0.9.md` — included without generated/private artifacts.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,DESIGN,MILESTONES,ROADMAP,TESTING_V0.10,WORKLOG}.md` — final v0.10 contracts, limitations and evidence.

### Verification
- `./gradlew test` — PASS through `./gradlew --offline test build :launcher-desktop:createDistributable --rerun-tasks`; 172 core and 35 desktop tests passed, four opt-in live tests skipped, zero failures/errors (207 passed / 211 discovered).
- `./gradlew build` — PASS in the same complete invocation.
- `:launcher-desktop:createDistributable` — PASS locally on Linux; no cross-platform native certification claimed.
- `SYNTAXLAUNCHER_MODPACK_LIVE_SMOKE=1 ./gradlew --offline :launcher-core:test --tests '*ModpackOfficialSmokeTest' --rerun-tasks` — PASS; real Simply Optimized 1.21.1-6.0 / Fabric 0.17.3 / managed Java 21 preparation and original export in about 88 seconds. Generated report retained locally under launcher-core/build/reports; no graphical Minecraft started.
- Manual verification — reviewed compact Polish/German catalog images and Polish home pack entry; deterministic interactions cover all three languages and both window sizes. Native/actual Play acceptance remains under v1.0.
- `git diff --check` — PASS. Publication checks include all required untracked sources and exclude ignored local/game/build files.

### Roadmap updates
- `[x] v0.10 / Modpack model / Instance mapping, overrides, both-hash file verification and safe reinstall/new-release behavior`
- `[x] v0.10 / Modpack model / Transactional prepared-instance publication without long repository mutex retention`
- `[x] v0.10 / Modrinth modpacks / Catalog, local import, exact game/loader resolution, required files, overrides, isolated preparation/publication and actionable errors`
- `[x] v0.10 / Export / Original managed pack definition, no added player/local/downloaded data and original reconstruction metadata`
- `[x] v0.10 / Quality gate / Fresh supported installation, unrelated-data preservation, no falsely completed failed profiles and translated UI/full regression/local packaging checks`
- `[ ] v1.0 / Broader gameplay/native/platform acceptance — unchanged; installation evidence is not a graphical startup result`

### Follow-ups / known issues
- Optional file choices, other pack formats, arbitrary modified-instance export and in-place update/world merging are outside this initial scope. Latest published packs requiring unsupported exact versions give a recoverable refusal rather than a silent downgrade.
- Verified shared immutable caches can remain after cancellation; interrupted private staging stays hidden. Export reproduces the author's original archive, including existing author-provided content, under its original distribution terms.
- Canonical launcherVersion and release/tag policy remain unchanged. The maintainer explicitly requested committing/pushing all pending project changes; the final report records publication outcome separately from test evidence.


## 2026-10-21 — Complete bounded v0.9 graphics workflows and start v0.10 planning

**Milestone:** v0.9 — bounded acceptance; v0.10 — first read-only manifest-planning phase
**Author/Agent:** Codex / GPT-6
**Status:** Completed

Scope: the documented v0.9 implementation/support matrix and initial v0.10 planner. This does not claim complete modpack installation or universal native/platform acceptance.

### Completed
- Extended the child-first shared catalog with supported shaders and OptiFine: compatible choices, one Install action, automatic required components, error/retry/cancel states and renewed compatibility discovery after source failures. Compact layouts retain a reachable action in EN/PL/DE.
- Added Iris renderer preparation through the existing Modrinth resolver/installer, preserving compatible tracked versions, enabling byte-identical required disabled components and refusing untracked replacement or OptiFine/Iris mixing. Shader ZIPs validate before renderer work; game/loader/all required renderer hashes are checked again under the final pack activity lock. Stale mod installation plans now recheck the profile at publication too.
- Added Iris shader selection with correct Java Properties encoding, unknown-value retention and exact original-byte backups. My packs presents installed metadata/icons, Use/Turn off, texture priority, recoverable Remove/Undo and secondary ZIP import. Undo never overwrites collisions; its receipt lasts for the open dialog while removed ZIPs/backups remain retained. A successful restore followed by failed shader reactivation leaves an inactive restored file available for Use/retry.
- Implemented official private OptiFine download/installation for Vanilla 1.21.1 / HD U J1 only. Managed Java executes the official installer's own logic in a headless staged worker; generated artifacts are hashed/content-addressed and merged through an independent graphics component resolver in the common launch pipeline. No OptiFine binary is bundled/mirrored, no loader provider was added, and no worlds are converted. Retained logs, timeout/cancellation and unknown-future-schema refusal are included; staging is not an OS security sandbox.
- Added additive schema-1 instance graphics state with backward-compatible absence, preserved unknown root data, original-byte backups and per-launch artifact/game/loader validation. Disable recovers ordinary Vanilla even when artifacts are missing; future schemas remain untouched.
- Changed the shared downloader to a streamed response lifetime and optional maximumBytes limit because the official OptiFine source does not supply Content-Length. Oversize downloads fail without retries, do not publish partial data and preserve existing destinations. Generated artifact hashes are local integrity records, not upstream signatures.
- Ran the actual official private OptiFine installer/overlay smoke twice successfully after correcting the Content-Length assumption; this is distinct from starting graphical Minecraft. Source binaries/temp games stay outside tracked source. The latest smoke report is retained under launcher-core/build/reports/official-optifine-smoke.xml locally.
- The maintainer subsequently selected the explicit confirmation of v0.9 in-game textures, a shader with automatic Iris and Vanilla 1.21.1 / OptiFine J1 startup. No OS, exact loader/Iris/pack versions or logs were supplied. Recorded bounded acceptance without inventing those details. Moved the broader native/platform/extended recovery matrix from v0.9 to the v1.0 quality gate, where it remains unchecked; the private confirmation is not a waiver or a claim those checks ran.
- Started v0.10 with a deterministic read-only Modrinth index planner: exact requested game/known single-loader mapping, required/optional/server-only roles, SHA-1/SHA-512 metadata, bounded HTTPS mirrors and portable safe/nonconflicting file destinations. Unknown manifest metadata is retained; malformed/future formats and unsupported mandatory components fail without network, extraction or instance writes. This is not ZIP import, payload verification or a working modpack catalog.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/content/{IrisRendererSetup,ShaderPackSelection,GameSettingsWrite,LocalGraphicsProfile,OptiFineCatalog}.kt` — automatic renderer setup, settings safety and separate OptiFine component lifecycle.
- `launcher-core/src/main/java/pl/syntaxdevteam/syntaxlauncher/core/content/OptiFineInstallWorker.java` — JDK-only official installer child entry point, usable from the packaged core JAR.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/content/{LocalPackManager,ResourcePackSelection,ModrinthResourcePacks}.kt` — shader catalog/batch activation, installed metadata/state, selection/priority and safe recovery.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/{launch/MinecraftLaunchCoordinator,mods/LocalModManager,mods/ModrinthCatalog,mods/ModrinthInstaller}.kt` — common graphics overlay, selected renderer version resolution and stale-profile checks.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/network/{DownloadManager,DownloadModels,DownloadExceptions,DownloadIntegrityVerifier}.kt` — streaming lifetime and optional hard size cap.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/modpack/ModrinthModpackPlanner.kt` — first v0.10 manifest/plan model and validation, without installation side effects.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/{content/ShaderCatalogTest,content/GraphicsProfileTest,content/OptiFineOfficialSmokeTest,network/DownloadManagerTest,modpack/ModrinthModpackPlannerTest}.kt` — deterministic graphics/download/plan regressions and separate official live smoke.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopLauncherSession,Main,ui/ContentCatalog,ui/InstalledPacks}.kt` — shared services and asynchronous graphics/installed-pack workflows.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties`, `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{ContentCatalogTest,InstalledPacksTest}.kt` — translated states/actions and small/large interaction/render coverage.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,DESIGN,MILESTONES,ROADMAP,TESTING_V0.9,WORKLOG}.md` — bounded support, acceptance evidence, recovery/schema contracts, broader unverified matrix and first v0.10 scope.

### Verification
- `./gradlew test` — PASS via final `./gradlew --offline test build :launcher-desktop:createDistributable`; 167 core tests and 32 desktop tests passed, three opt-in live tests skipped, zero failures/errors (199 passed / 202 discovered).
- `./gradlew build` — PASS in the same full invocation.
- `./gradlew --offline :launcher-desktop:createDistributable` — PASS, local Linux app image; not a cross-platform native certification.
- `SYNTAXLAUNCHER_OPTIFINE_LIVE_SMOKE=1 ./gradlew --offline :launcher-core:test --tests '*OptiFineOfficialSmokeTest' --rerun-tasks` — PASS with official private installer and Mojang inputs; generated profile/arguments/artifacts, unchanged save marker and disable verified; no graphical game boot by the agent.
- Manual verification — reviewed compact Polish/German graphics catalog and installed-pack screenshots. Maintainer confirmed the requested v0.9 gameplay flows separately, without an exact platform/pack matrix. Native focus/DPI/platform and extended recovery checks remain unverified.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] v0.9 / Local foundation / Installed UI and bounded streaming download support`
- `[x] v0.9 / OptiFine / Strategy, bounded discovery, component integration and unsupported combinations`
- `[x] v0.9 / Shaders / Instance inventory/import/removal, selection and automatic compatible renderer closure`
- `[x] v0.9 / Resource packs / Installed management, metadata/icon and explicit priority`
- `[x] v0.9 / Quality gate / Scoped graphics, separate OptiFine architecture, mod regressions and private maintainer gameplay acceptance`
- `[x] v0.10 / Modpack model / Read-only deterministic installation plan and strict index validation`
- `[ ] v0.10 / Remaining installer/catalog/override/export/quality items — no complete playable modpack installation yet`
- `[ ] v1.0 / Quality gate / Broader native graphics/catalog/platform/extended recovery acceptance — moved explicitly from v0.9 after bounded confirmation; no test result is fabricated`

### Follow-ups / known issues
- Next v0.10 phase: safe `.mrpack` ZIP/override handling, exact compatibility discovery, both-hash payload verification, staged new-instance publication and the one-action catalog. Optional content remains a separate plan list; nothing is silently installed by this phase.
- OptiFine support is Vanilla 1.21.1 / J1 only; shaders currently target Iris-compatible published combinations. OptiFine shader selection, Forge shaders and historical/mixed OptiFine stacks are outside this matrix.
- Catalog badges are session-scoped, last-removal Undo is dialog-scoped, and retained trash/backups have no automatic cleanup or persistent recovery browser. Folder packs remain unmanaged. Native/platform/extended recovery acceptance stays open under v1.0.
- Canonical launcherVersion, release/tag state and Git history were not changed; no commit or push was made.


## 2026-10-21 — Make child-first catalog installation mandatory and deliver Mods/Textures

**Milestone:** v0.6 — simplified mod catalog; v0.9 — resource-pack catalog; cross-milestone UX
**Author/Agent:** Codex / GPT-6
**Status:** Completed

Scope: mandatory child-first policy and the working Mods/Textures catalog slice. Shader/OptiFine/modpack catalog completion remains open.

### Completed
- Persisted the maintainer's non-negotiable requirement in AGENTS.md and DESIGN.md: children browse an in-launcher catalog, choose a single item or ready-made set and Install/Play; compatible versions, loaders, Java, renderers and dependencies belong to the launcher. Manual files and technical plans are advanced tools; backend-only support and placeholder categories never count as completion.
- Added a shared translated Mods/Textures catalog as the normal home/new-modded-game entry. Results offer direct Install, Installing, Installed, Retry and Cancel states; mod versions/dependencies resolve automatically without the former technical review screen. Installed-mod management remains a secondary action. Vanilla managed instances expose textures without asking for a loader.
- Added Modrinth resource-pack search/popularity, exact game/project-type validation and transitive required resource-pack resolution with bounded recursion, cycle/conflicting-version checks, safe filenames and strict CDN/SHA-1/size validation through the shared downloader. Cross-type dependencies are rejected instead of copied to the wrong folder.
- Extended LocalPackManager with instance-locked whole-batch validation/publication, expected game-version checks, collision preflight and rollback of only newly published files. Identical installed ZIPs are reused without replacement; source ZIPs, other instances and pre-existing bytes are retained.
- Added explicit resource-pack activation through ResourcePackSelection: append dependencies/main pack to Minecraft's resourcePacks option, preserve previous order, unknown options and LF/CRLF, back up exact original bytes and publish settings atomically. Malformed/unsupported selection, invalid encoding, redirected/changed settings fail without rewriting unknown state. Core removal deselects only the removed pack and restores its ZIP if options publication fails.
- Made repeated installation of an already tracked, enabled and byte-identical mod project idempotent without redownload/replacement; existing unrelated, changed and disabled JARs are not silently replaced or enabled.
- Added backend dependency/rollback/settings regressions and three Compose tests for one-click/duplicate guards, errors/retry/running-game disabling, cancellation/cleanup/retry and EN/PL/DE wide/400×400 render/interaction. Updated the home regression for Choose extras and reviewed actual Polish catalog images with production Surface colors.

### Changed files
- `AGENTS.md`, `docs/DESIGN.md` — permanent mandatory child-first catalog and acceptance rules.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/content/{LocalPackManager,ModrinthResourcePacks,ResourcePackSelection}.kt` — transactional pack batch/selection and online resource-pack catalog installation.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthInstaller.kt` — idempotent tracked enabled main-mod installation.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/content/ModrinthResourcePacksTest.kt`, `mods/ModrinthInstallerTest.kt` — compatible dependency install, repeat operations, settings/backup/encoding preservation, negative metadata/batch cases and thread-safe download counting.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopLauncherSession,ui/ContentCatalog,ui/SyntaxLauncherApp}.kt` — catalog service wiring, child-first dialog and home/new-game entry.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties` — simple translated catalog actions/states/errors.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{ContentCatalogTest,LauncherHomeRenderTest}.kt` — one-click/cancellation/compact translated catalog and home regression coverage.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,MILESTONES,ROADMAP,TESTING_V0.9,WORKLOG}.md` — workflow, option/backup contract, evidence and clearly open shader/OptiFine/modpack work.

### Verification
- `./gradlew test` — PASS via final `./gradlew --offline test build`; 157 core and 30 desktop tests passed, two opt-in live installer tests skipped, zero failures/errors.
- `./gradlew build` — PASS in the same final full invocation. Desktop tests were already up to date from the preceding successful full run; final core test additions reran successfully.
- Focused `LocalPackManagerTest`, `ModrinthResourcePacksTest` and `ContentCatalogTest` — PASS. Corrected the old home label assertion for the deliberate UI rename and changed the test's plain integer HTTP counter to AtomicInteger because dependency downloads are concurrent.
- Manual visual review — inspected Polish catalog renders at 400×400 with production foreground/background colors; main action and success state are readable without a technical review. EN/PL/DE 820×650 and 400×400 semantics/button bounds assertions pass.
- Live/native acceptance — NOT RUN for this new catalog/texture activation. HTTP/ZIP/settings tests use local fixtures and temporary instances; no actual child/game/user data accessed. Hosted CI/new native packages were not triggered.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] Mandatory child-first content experience / Persistent AGENTS.md and DESIGN.md rule`.
- `[x] Mandatory child-first content experience / Shared direct Mods/Textures catalog and automatic supported dependencies`.
- `[x] v0.9 / Resource packs / Compatible catalog, verified batch install and backed-up automatic selection` — deterministic implementation/UI checks pass; live-game release acceptance remains separate.
- `[ ] v0.9 / Full installed-pack controls, reorder/recovery, shader catalog and automatic renderer setup, OptiFine and native/gameplay acceptance` — remain open.
- `[ ] v0.10 / Browse/install ready-made modpacks/sets through the same simple catalog` — explicitly tracked; no premature pack-format installer or placeholder category added.

### Follow-ups / known issues
- Next catalog categories must complete automatic compatible renderer/dependency/activation flows for shaders and OptiFine; ready-made modpacks/sets follow their roadmap scope. None is currently presented as a functioning category.
- Installed badges are scoped to the open catalog session. Repeat actions safely revalidate/reuse identical files; durable project badges, thumbnails and full resource-pack management/recovery remain open.
- Full version-range/cross-project conflicts and renderer-dependent/cross-type texture dependencies are not solved by this slice. Existing metadata/pack format declarations do not certify every game/renderer combination; real-game/native acceptance is still required.
- Game-owned options.txt keeps its existing format; no launcher settings/instance schema migration or product-version change is introduced. Existing worlds/accounts/loader settings are preserved. Prior uncommitted work is retained; no commit, push or release was performed.

---


## 2026-10-21 — Accept bounded v0.8 and start local graphics-pack core for v0.9

**Milestone:** v0.8 — Forge/Quilt acceptance; v0.9 — local resource/shader packs
**Author/Agent:** Codex / GPT-6
**Status:** Completed

Scope: v0.8 audit and the initial v0.9 core slice. Full v0.9 remains in progress.

### Completed
- Audited the v0.8 checklist, common coordinator, actual loader adapters and installer/metadata tests. Recorded the maintainer's direct private-test confirmation for actual Forge startup and Forge/Quilt mod installation/startup. No OS matrix, exact Quilt/mod versions or logs were supplied and none are inferred.
- Inspected hosted evidence through the connected GitHub tools: CI 39634747655 (`0ecf5b0`) passed core/desktop checks, official Forge/NeoForge installer processors and Windows/Linux/macOS packages; the newer baseline run 39647415857 (`3fdf34e`) also completed successfully with all three packages. These baseline runs do not contain the newly added audit/pack tests.
- Added a full synthetic coordinator matrix using every real provider (Vanilla/Fabric/Quilt/NeoForge/Forge), local HTTP profiles/installers, actual fixture processor JVMs and intercepted final `java -version` launches. Verified common runtime/plan/process wiring, loader arguments/entry points, generated runtime artifacts, one cached Vanilla client and five separate game directories with preserved save markers. This is not a graphical Minecraft boot.
- Added explicit negative historical/persisted-profile regressions: Forge 1.12.2/V1 is rejected, unsupported historical selections fail before network access, and unverified modern Forge selections cannot fetch installers. Documented metadata-declared Java selection (including explicit Java 8), legacy shared argument planning, and early rejection when Java metadata is absent.
- Accepted v0.8 only for its pre-existing bounded Forge 1.21.1/52.1.20 matrix and shared Quilt integration. Broader historical Forge, full dependency semantics and native OS/gameplay matrices remain visibly unchecked as optional expansion or existing platform gates; no universal compatibility or test waiver is claimed.
- Started v0.9 with UI-independent instance-local resource/shader ZIP inventory, staged import and recoverable removal, reusing the existing cross-process activity lock. Imports retain sources and existing targets, check ZIP paths/payload CRCs/structure and bounded metadata, and clean temporary files. Removal retains the original ZIP under the same instance's pack trash. No extraction, game settings edits, network client or persisted schema is introduced.
- Added seven pack tests covering isolation/restart/recovery, destination preservation, malformed/wrong-kind/unsafe ZIPs and oversized metadata, CRC corruption, active-instance locking, filenames/traversal and Unix symlink refusal. Updated architecture/development guidance, milestone status, support matrix and a new v0.9 checklist.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/content/LocalPackManager.kt` — local ZIP pack service, instance-locked safe mutations and recoverable removal.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/content/LocalPackManagerTest.kt` — seven deterministic data-preservation/archive/activity regressions.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/LoaderPipelineMatrixTest.kt` — all real loader adapters through one coordinator/process contract with synthetic artifacts.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/{InstallerLoaderDiscoveryTest,InstallerProfileInspectorTest}.kt` — historical and persisted unsupported Forge selections/V1 rejection.
- `README.md`, `docs/{ARCHITECTURE,DEVELOPMENT,MILESTONES,ROADMAP,TESTING_V0.8,TESTING_V0.9,WORKLOG}.md` — accepted scope/evidence, explicit compatibility limits and initial v0.9 service/remaining work.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build`; 153 core and 27 desktop tests passed, two opt-in live installer tests skipped, zero failures/errors. The pre-existing untracked download cancellation test from the preceding task remains present and is included in these local counts.
- `./gradlew build` — PASS in the same final full invocation.
- Focused loader/policy/pack tests — PASS, including actual fixture processor JVM execution for Forge and NeoForge.
- Hosted baseline CI — PASS as described above; current uncommitted audit/pack changes have not been pushed or run in hosted CI.
- Manual verification — maintainer privately confirmed Forge startup and Forge/Quilt mod workflow in this chat; no additional game/pack/native interaction tests were performed by the agent.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] v0.8 / Forge and Quilt / Shared integration, bounded metadata and private startup/mod acceptance` — completed within the documented matrix.
- `[x] v0.8 / Historical compatibility / Explicit Java/argument policy and negative legacy/unsupported-pair tests` — does not claim old Forge gameplay support.
- `[x] v0.8 / Quality gate / Shared core, adapter boundary and documented/tested supported matrix`.
- `[ ] v0.8 / Optional compatibility expansion` — historical/V2 expansion, full constraint parsing and explicit native OS/game/mod evidence remain open; they are not silently marked passed or moved to another version.
- `[x] v0.9 / Local pack foundation / Core instance-local ZIP inventory/import/recoverable removal`.
- `[ ] v0.9 / Shader and resource pack workflows — IN PROGRESS` — core is ready; desktop controls, metadata/icons, selection/order and renderer/game acceptance remain open.
- `[ ] v0.9 / OptiFine` — integration strategy, installation and compatibility remain open.

### Follow-ups / known issues
- Next: translated instance content workspace with busy/error/cancellation/progress and recovery guidance, then renderer/pack compatibility and real-game acceptance. Folder packs are unmanaged; no automatic selection or in-app restore is implemented.
- Core ZIP import validates structure and CRCs, not authenticity or renderer/Minecraft-version compatibility. A shader ZIP does not install a renderer.
- The Unix symlink test intentionally skips Windows hosts without assuming link-creation privileges. Full native acceptance and new-change hosted CI remain open.
- Canonical product version, release tags, publishing, current loader support filtering and existing UI/test warnings remain unchanged. No commit/push or release was performed by the agent.

---


## 2026-10-21 — Clean partial downloads after coroutine cancellation

**Milestone:** v0.1 — download infrastructure
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Reproduced cancellation leaving `.part` files behind: cleanup switched to the IO dispatcher with the already-cancelled job and therefore never performed the deletion. Both initial single-download and queued-batch regression tests failed on the original implementation.
- Shielded only partial-file deletion with `NonCancellable + Dispatchers.IO`, after the output stream has closed. Original cancellation still propagates and deletion failures retain the existing diagnostic without replacing the original cause.
- Added explicit cancellation checks in the buffered copy loop and immediately before verified publication, so already-buffered responses cannot bypass cancellation checks until the next suspending operation.
- Added controlled tests for cancellation preserving an existing destination, successful retry, two simultaneous active partial files, cancellation of queued work, and absence of spurious retry/failure/completion events. Tests use temporary storage and MockEngine, without external services or game data.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/network/DownloadManager.kt` — cancellation-safe cleanup and cooperative copy/publication checks.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/network/DownloadCancellationTest.kt` — single-file/retry and concurrent-batch cancellation regressions.
- `docs/DEVELOPMENT.md` — cleanup ordering, cancellation contract and test guidance.
- `docs/ROADMAP.md` — mark the v0.1 structured download cancellation item complete, retaining separate native acceptance.
- `docs/WORKLOG.md` — reproduction, scope and verification evidence.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build`; 143 core and 27 desktop tests passed, two opt-in live installer tests skipped, zero failures/errors.
- `./gradlew build` — PASS in the same full invocation.
- Focused `DownloadCancellationTest` — initial two regressions FAIL before the fix due to retained partial files; PASS after the fix. The final full suite also passes the strengthened two-concurrent-partial regression.
- Manual verification — NOT RUN; no real Minecraft preparation or native UI/platform cancellation acceptance claimed.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] v0.1 / Download infrastructure / Structured coroutine cancellation propagates through batches and removes partial files` — deterministic single-file and concurrent/queued batch behavior verified.
- `[ ] v0.1 / Native preparation cancellation and real Minecraft/platform acceptance` — existing manual release gates remain open; this fix does not certify gameplay or cross-platform acceptance.

### Follow-ups / known issues
- Abrupt process termination or filesystem refusal can still leave partial files; this change handles cooperative coroutine cancellation. Already-published verified artifacts remain cached.
- No UI, authentication, loader, persisted-schema, canonical product-version or release/publication changes were introduced.

---

## 2026-10-21 — Automatically select supported Minecraft when changing loader

**Milestone:** v0.4 — shared loader selection / v0.8 — Quilt compatibility correction
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Fixed Quilt loader-discovery HTTP 404 being surfaced as a raw HTTP/JSON error when the chosen Minecraft snapshot has no Quilt support. Discovery now returns no compatible versions for that status even with the shared client's `expectSuccess=true`; profile 404, other HTTP failures and cancellation remain failures.
- Added read-only registry discovery that preserves a compatible current Minecraft version, otherwise intersects the provider's supported games with Mojang versions ordered by release date and checks the newest compatible candidate. There is no seven-release fallback limit or hardcoded fallback Minecraft/Quilt version.
- Integrated automatic draft adjustment in instance settings, creation and quick modded setup, selecting the newest offered loader on fallback, including previews. Fabric/Quilt use official game indexes; NeoForge reuses Maven compatibility rules and Forge retains its verified V2 matrix.
- Added a short EN/PL/DE explanation of the adjustment before Save/Create. Existing-world downgrade warnings remain visible. Settings and creation retain explicit confirmation; discovery does not modify profiles, worlds or schema. Changing a loader cancels obsolete discovery and clears stale compatibility. Network/metadata errors show a concise translated message and retain diagnostic causes in logs.
- Added deterministic tests for Quilt 404 with strict HTTP validation, fallback beyond seven versions, unsorted/unknown upstream game entries, current-version preservation, empty support, server/profile errors and cancellation; Fabric and Forge-family fallback regressions; EN/PL/DE Compose settings confirmation and cancelled stale requests.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/{ModLoader,FabricLoaderProvider,QuiltLoaderProvider,InstallerLoaderDiscovery}.kt` — compatible-game discovery and provider-supported game filtering; Quilt discovery status handling.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/{FabricLoaderProviderTest,QuiltLoaderProviderTest,InstallerLoaderDiscoveryTest}.kt` — deterministic metadata/status/fallback regressions.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{InstanceSettingsDialog,InstanceWorkspace,QuickModdedGameDialog,SyntaxLauncherApp}.kt` — automatic draft selection, bounded discovery, inline feedback and registry wiring.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties` — translated adjustment message.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceLoaderSelectionTest.kt` — translated confirmation renders, newest loader choice and stale-discovery cancellation.
- `README.md`, `docs/{DESIGN,DEVELOPMENT,MILESTONES,ROADMAP,WORKLOG}.md` — requested compatibility behavior, selection rules and verification.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build`; 141 core and 27 desktop tests passed, two opt-in live installer tests skipped, zero failures/errors.
- `./gradlew build` — PASS in the same final invocation.
- Manual verification — inspected the generated Polish settings render showing the fixture adjustment from `26.4-snapshot-4` to `26.3`, the inline explanation and retained world-downgrade warning. EN/PL/DE render/confirmation assertions pass. Real Minecraft startup and native multi-platform interaction were not run.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] v0.4 / UI / Automatically adjust unsupported Minecraft/loader drafts` — implementation, deterministic provider/UI regressions and local build verified.
- `[ ] v0.4 / UI / Native simplified discovery/install/Play usability` — remains open; automated settings renders do not certify actual gameplay/platform acceptance.
- Existing v0.8 Forge/historical compatibility and other loader acceptance gates remain unchanged.

### Follow-ups / known issues
- Native Windows/macOS/Linux acceptance and real gameplay remain separate checks. Loader support changes upstream; selection uses live provider metadata, not a permanent promise about a specific version pair.
- The bundled Forge support matrix remains limited to its previously verified V2 pair. No updater changes, product-version bump, persisted-format migration or world conversion were needed.

---

## 2026-10-21 — Fix Linux packaged update handoff and native access

**Milestone:** v0.12 — startup updates; v0.1 — desktop runtime compatibility
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Reproduced the reported early child exit with a real packaged DEV parent and the existing verified Stable 0.6.0 image, entirely under temporary XDG/user-home directories. Newly retained child output exposed `Unrecognized option: --syntaxlauncher-update-handoff` before JVM startup.
- Removed the inherited `_JPACKAGE_LAUNCHER` marker when starting another packaged image. OpenJDK's Linux launcher otherwise assumes the command already contains JVM arguments and skips its own configuration, misinterpreting the application's handoff flag. All other environment values, including display variables, are retained.
- Added unique retained child stdout/stderr logs alongside startup receipts, POSIX owner-only permissions, and actionable early-exit code/log-path and timeout/log-path errors. Activation, integrity, receipt verification, cancellation and previous-image fallback remain intact.
- Enabled classpath native access for Skiko/JNA in packaged/Gradle application launches and desktop tests. Actual native startup no longer emits the restricted-System.load warning in the new package.
- Fixed missing channel preferences being treated as an explicit return from DEV to Stable. Packaged startup atomically initializes the choice from embedded channel metadata once; the picker uses the same fallback. Existing saved choices remain authoritative, including intentional older Stable selection. Missing/legacy settings and unknown properties are retained; unsupported schemas/channel IDs fail without rewrite.
- Extended deterministic preference regressions and added a real failing Java child test for stderr retention, private log permissions, command argument integrity and removal of a simulated inherited marker.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/ProcessUpdatedLauncherStarter.kt`, `StartupUpdater.kt` — clean jpackage child environment and preserve diagnostics/exit codes.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStore.kt` — embedded-channel fallback and atomic first-start initialization.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/{LauncherChannelsTest,LauncherUpdateTest}.kt` — preference/schema compatibility and real child/environment regressions.
- `launcher-desktop/build.gradle.kts` — explicit native-access JVM option for application images/runs and tests.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt`, `ui/LauncherUpdateChannelPicker.kt` — consistent startup and UI channel semantics.
- `README.md`, `docs/{DESIGN,DEVELOPMENT,ROADMAP,UPDATING,WORKLOG}.md` — channel defaults, native handoff/log contract and verification.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline --no-daemon -PlauncherBuildNumber=157 -PlauncherCommit=c54c72ae80aaac338b24db15ae86f42e5ec42e8e -PlauncherUpdateChannel=dev test build :launcher-desktop:createDistributable --no-build-cache`; 137 core and 25 desktop tests passed, two opt-in live installer tests skipped.
- `./gradlew build` — PASS in the same invocation.
- `:launcher-desktop:createDistributable`, `scripts/package_update.py`, `:launcher-desktop:verifyUpdateBundle` — PASS for Linux.
- Native Linux reproduction before fix — FAIL as reported: child exited 1 with unrecognized JVM option; stable image alone started successfully, isolating the packaged-parent environment as the trigger.
- Native Linux reproduction after fix — PASS: the new image wrote a startup receipt; an explicit Stable handoff to the actual verified 0.6.0 image received its receipt, activated the image and exited the parent with code 0. New image emitted no restricted native-access warning; the historical unmodified 0.6.0 image still emits its old warning.
- Fresh DEV native startup — PASS: writes `launcher_update_channel=dev`, remains running and emits no restricted native-access warning. All probes used temporary configuration/data/cache/home directories; existing user settings, accounts and worlds were not changed.
- Debugger — observed DEV current 2.0.0 / selected Stable / candidate 1.0.0 entering activation in the existing explicit-switch regression, and verified the paused call chain. Removed agent breakpoints and finished the session.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] v0.12 / Self-update / Isolate Linux jpackage child state, diagnostics, native access and initial channel selection` — verified by deterministic tests and actual packaged parent/child startup.
- `[ ] v0.12 / Full hosted Windows/Linux/macOS update/channel-switch acceptance` — remains open; this Linux reproduction does not certify every platform/failure mode.

### Follow-ups / known issues
- Existing installed bootstrap binaries need one manual replacement to gain the corrected child-launch behavior; merely publishing a new target cannot repair an old parent that fails before the child JVM starts.
- Startup diagnostic logs are retained; automatic cleanup/signing and upstream Compose/Hot Reload Gradle deprecations remain separate work.
- No historical Stable release was modified or republished, no product-version bump introduced and no existing game data migrated.

---

## 2026-10-21 — Migrate deprecated Kotlin and Compose APIs

**Milestone:** v0.1 — existing core/UI maintenance; v0.12 — build compatibility
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Applied `@ConsistentCopyVisibility` to OfflineAccount so the generated copy method respects its private constructor; all repository creation remains through validated `create`.
- Removed redundant Elvis/safe calls where Kotlin already proves that the delayed installer has a selected loader and registry. Loader resolution, preparation order and errors for missing services remain unchanged.
- Replaced deprecated image calls in the main window, header, hero and startup updater with one remembered classpath bitmap helper using the Compose resources decoder. Existing PNG/JPEG paths, artwork, sizing and native package icon remain intact; input streams close after reading.
- Migrated the instance and local-mod native windows from Dialog to DialogWindow, preserving size/state, resize support, busy close guards and content.
- Declared Material3 explicitly at its existing resolved version and Compose resources at the plugin version, avoiding deprecated Gradle dependency accessors. No warning suppressions or product-version bump introduced.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/account/OfflineAccount.kt` — consistent generated-copy visibility.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt` — remove proven-unnecessary null handling.
- `launcher-desktop/build.gradle.kts` — explicit Material3 and resources dependencies.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/BrandingResources.kt` — shared remembered painter using Compose resources bitmap decoding.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt`, `ui/{LauncherArtwork,StartupUpdateGate,SyntaxLauncherApp}.kt` — migrate all branding painter calls.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/{InstanceWorkspace,LocalModsDialog}.kt` — migrate native window API.
- `docs/{DESIGN,DEVELOPMENT,ROADMAP,WORKLOG}.md` — current resource/dependency conventions, maintenance progress and evidence.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline --no-daemon -PlauncherBuildNumber=156 -PlauncherCommit=e96b70e7be7a41a3ee0e15f32cf9fc3d1145ef67 -PlauncherUpdateChannel=dev test build :launcher-desktop:createDistributable --rerun-tasks --no-build-cache --warning-mode all`; 136 core and 25 desktop tests passed, two opt-in live tests skipped. All 19 actionable tasks executed.
- `./gradlew build` — PASS in the same invocation; zero Kotlin source or Gradle-script compiler warnings. Only the two upstream plugin deprecations listed below remain.
- `:launcher-desktop:createDistributable` — PASS for Linux. Verified that the packaged desktop JAR includes the new helper and all three original branding files. `scripts/package_update.py` and `:launcher-desktop:verifyUpdateBundle` also PASS for the complete Linux update archive.
- Manual verification — inspected regenerated Polish home (1440×810) and startup updater (440×600) renders. Branding, transparency and geometry preserved; native dialogs and Windows/macOS still require platform acceptance.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] Cross-milestone desktop development ergonomics / Deprecated core/Compose APIs` — local migration/verification complete.
- `[ ] Native UI and Windows/macOS/platform acceptance` — remains open; headless rendering and Linux packaging do not replace native checks.

### Follow-ups / known issues
- Two upstream Compose/Hot Reload Gradle deprecations remain: Configuration.setVisible and the HotReloadUsageType attribute value type. No repository calls use these APIs; upgrading or disabling plugins is outside the supplied source-warning cleanup.
- No authentication, loader feature, persistence-schema, artwork, canonical product-version or release-policy change. Hosted CI and native dialog focus/resize acceptance remain separate.

---

## 2026-10-21 — Fix nested home scrolling in CI render tests

**Milestone:** v0.1 — translated home regression coverage; v0.12 — CI reliability
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Retrieved the failed test artifact from Actions run 39599289671 for commit `9f3920e`: the translated home test failed at line 181 while checking the RAM label on the Polish 440×740 home.
- Confirmed the compact home has independently scrollable page and settings containers. The test scrolled only the first container, waited five frames and accepted any visible fragment of the label. Under the IntelliJ debugger, the old Polish assertion saw `Rect.fromLTRB(66.0, 693.0, 201.0, 698.0)` — just five visible pixels, explaining why a local pass did not prove usability.
- Updated both compact-home scrolling regressions to scroll every home/settings container and wait for its semantic vertical range to reach the end. The assertion now compares clipped label bounds against measured height and includes language/window/bounds context in failures.
- Reused the common readiness helper for translated scenes. Preserved concurrent IDE edits using typed millisecond durations and creating fixture directories on the IO dispatcher. Kept production UI, update channels, loaders, canonical version and CI gates unchanged.
- Removed diagnostic breakpoints; no debugger session or generated test artifacts are included in the commit.

### Changed files
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherHomeRenderTest.kt` — nested scroll completion and full-label visibility assertions.
- `docs/DEVELOPMENT.md` — semantic scrolling and CI-metadata reproduction guidance.
- `docs/ROADMAP.md` — annotate the corrected localization/render regression coverage; hosted/native acceptance remains open.
- `docs/WORKLOG.md` — failure evidence, corrective scope and verification.

### Verification
- Focused `LauncherHomeRenderTest` — PASS with DEV channel, CI build number 154 and full source SHA; all seven tests, including EN/PL/DE narrow/wide and 400×400/scaled cases.
- `./gradlew test` — PASS via `./gradlew --offline --no-daemon -PlauncherBuildNumber=154 -PlauncherCommit=9f3920ef7ec832d11a391d9d8c77cbc99c740a04 -PlauncherUpdateChannel=dev test build --rerun-tasks --no-build-cache`; all 25 desktop tests and 136 core tests passed, two opt-in live installer tests skipped. Final source hash was unchanged throughout verification.
- `./gradlew build` — PASS in the same full rerun; all 13 actionable tasks executed.
- Manual verification — inspected regenerated Polish 440×740 settings render: RAM heading and slider fully visible. Debugger observation and downloaded failed CI report were read-only; no Minecraft/account data accessed.
- `git diff --check` — PASS.

### Roadmap updates
- `[x] v0.1 / Interface languages / Resource and Compose render regressions` — retained with stronger nested-scroll visibility checks.
- `[ ] v0.12 / CI/CD / Build/test CI` and hosted channel-publication/native acceptance — remain open until corresponding checks pass; a local test does not complete release acceptance.

### Follow-ups / known issues
- The original assertion failure did not reproduce in standalone or full local runs, including DEV metadata. The downloaded CI report and debugger measurement establish the incomplete scroll/visibility check; hosted CI must confirm the corrected result.
- Existing Kotlin/Compose/Gradle deprecation warnings and previously documented release authorization limitations are outside this test fix. No Stable version bump, release recovery or token changes.

---

## 2026-10-21 — Reconcile local update channels with current GitHub main

**Milestone:** v0.7 / v0.8 / v0.12
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Backed up all 25 locally modified/untracked files in a temporary archive outside the repository and retained the full Git stash `c46e32c84dec46c1174564f070e2ce01f7af6c39` (`backup: local update channels before main synchronization 2026-10-21`).
- Fast-forwarded local main over 49 upstream commits, from `f7db09c` to `8c8fe5d`, then reapplied the local work. Resolved eight conflicting files by combining both feature sets instead of choosing one side wholesale.
- Preserved local DEV/SNAPSHOT publication, channel preferences, startup recovery, translated selection UI and regression tests alongside upstream NeoForge/Forge processors, loader-aware mods, compatibility restrictions and release recovery.
- Kept canonical `launcherVersion=0.7.0` from upstream; the older local `0.6.1` change is superseded. Preserved historical-release safeguards, both official loader smoke gates and the authorized release-token fallback; applied that same fallback to preview publication.
- Preserved both sides' historical worklog entries verbatim. Maintainer explicitly requested committing and pushing the reconciled result through a normal non-force push.

### Changed files
- `.github/workflows/build.yml` — combine preview channels with upstream release authorization, historical-version protection and Forge/NeoForge checks.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/{storage,update}/` and `src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/LauncherChannelsTest.kt` — retain local channel persistence/discovery/recovery and tests.
- `launcher-desktop/build.gradle.kts`, `src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopStartupUpdate,Main}.kt`, `ui/{SyntaxLauncherApp,LauncherUpdateChannelPicker}.kt`, `src/main/resources/i18n/{en,pl,de}.properties`, `src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherUpdateChannelPickerTest.kt` — preserve both loader wiring/warnings and channel controls/build identity.
- `scripts/publish_preview.py`, `scripts/test_publish_preview.py` — retain immutable preview publication and deterministic tests.
- `README.md`, `docs/{ARCHITECTURE,DESIGN,DEVELOPMENT,MILESTONES,ROADMAP,UPDATING,WORKLOG}.md` — retain both documentation sets and reconcile current version/publication behavior.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build :launcher-desktop:createDistributable`; 138 core tests (136 passed, two opt-in live installer tests skipped), 25 desktop tests passed; zero failures/errors.
- `./gradlew build` — PASS in the same invocation.
- `:launcher-desktop:createDistributable` — PASS, Linux application with canonical version 0.7.0.
- `scripts/package_update.py` and `:launcher-desktop:verifyUpdateBundle` — PASS, real Linux archive extraction/integrity verification without starting Minecraft.
- Python tests — PASS, 10 script tests and six Build Explorer tests.
- Workflow YAML, 14 shell snippets, seven channel/publication scenarios and retention of official loader gates/token fallback — PASS.
- `git diff --check HEAD` — PASS. Original local archive comparison and verbatim preservation of both worklog histories — PASS.
- Manual verification — inspected the actual Polish 400×400 channel-dialog render. No real Minecraft/account data accessed. Live installer/network, Windows/macOS packaging and hosted channel-switch acceptance were not repeated locally; main CI retains the official loader and platform checks.

### Roadmap updates
- `[x] v0.12 / Self-update / Define update channel strategy` — retained and extended with the local Stable/DEV/SNAPSHOT contract.
- `[ ] v0.12 / Self-update / DEV/SNAPSHOT publication and selection — IN PROGRESS` — combined implementation passes locally; hosted publication/native switching remain open.
- `[ ] v0.12 / CI/CD / Automatic Stable publication — IN PROGRESS` — current canonical version reconciled to 0.7.0; no Stable version bump or historical backfill introduced.
- Upstream v0.7/v0.8 implementation and remaining gameplay/platform acceptance checkboxes preserved.

### Follow-ups / known issues
- Existing GitHub release authorization failures remain documented in UPDATING.md; this merge does not change account/organization permissions or publish a historical Stable release.
- No force push, tag rewrite, new Stable release, signing or unrelated feature work. The backup stash remains available after synchronization.

---

## 2026-10-21 — Loader-aware mod inventory and Forge/NeoForge TOML diagnostics

**Milestone:** v0.8 — Forge and Quilt  
**Author/Agent:** GPT-6  
**Status:** Implementation committed; latest GitHub CI result not yet accepted

### Completed
- Added `ForgeTomlMetadataReader`: bounded, conservative parsing of `[[mods]]` and `[[dependencies.<modid>]]`, supporting legacy Forge `mandatory=true`, NeoForge `type=required`/`type=incompatible`, owner matching, and server-only exclusion. Optional or discouraged dependencies are intentionally not treated as mandatory.
- Integrated declared dependencies/conflicts into existing missing-mod and conflict warnings. The inventory continues to preserve original JAR files and does not attempt dependency auto-install.
- Added `sourceLoader` to extracted mod metadata. Select matching manifest first for the instance's loader; support dual-manifest JARs; warn on wrong loader families and caution that Forge-only metadata does not certify NeoForge compatibility.
- Added deterministic core tests for Forge and NeoForge dependency styles, malformed/untrusted IDs, bounded input, missing dependencies, conflicts cleared on disabling mods, dual-manifest selection and cross-loader JAR warnings.
- Kept `launcherVersion=0.7.0`; this is v0.8 development and is not a v0.8.0 release.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ForgeTomlMetadataReader.kt` — conservative Forge/NeoForge reader.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt` — loader-selected metadata and warnings.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ForgeTomlMetadataReaderTest.kt`, `LocalModManagerTest.kt` — regressions.
- `docs/ROADMAP.md`, `docs/TESTING_V0.8.md`, `README.md`, `docs/WORKLOG.md` — documentation.

### Verification
- Prior v0.8 integration and 3-OS package CI: **PASS** — run 39561846249.
- Current TOML changes: GitHub CI on latest `main` commit must pass before marking implementation validated.
- v0.7.0 public release: still absent in last checked GitHub Releases; retry of older workflow 39558146467 not yet reflected in its attempt count. No token or credentials read.
- Forge actual-game mod load and semantic TOML version-range checking — **NOT VERIFIED**.

### Roadmap updates
- `[ ] v0.8 / Forge/NeoForge metadata/compatibility` — first-pass implementation added; live game acceptance and full TOML remain open.

---


## 2026-10-21 — Verify official Forge V2 and enable bounded desktop test profiles

**Milestone:** v0.8 — Forge and Quilt  
**Author/Agent:** GPT-6  
**Status:** Partial — Forge official processor smoke passed, interactive game acceptance still open

### Completed
- Corrected Forge V2 runtime metadata that gives empty Maven URLs for generated processor libraries; do not interpret empty strings as relative or untrusted network destinations.
- Added regression fixture with an empty generated-library URL and safer diagnostics for rejected Maven sources.
- GitHub Actions 39560642818 passed the official Minecraft 1.21.1 + Forge 52.1.20 processor smoke, full core/desktop tests and official NeoForge smoke; native packaging jobs also completed successfully.
- Restricted Forge version discovery to the one verified Minecraft/Forge version pair for v0.8 testing; incompatible/historical versions are rejected in selection.
- Added a guarded Forge (v0.8) choice in game creation, instance settings and advanced modded-game setup, with EN/PL/DE warnings. Home modded mode and instance-local Mods support Forge.

### Changed files
- `NeoForgeInstallerEngine.kt`, `NeoForgeInstallerEngineTest.kt`, `InstallerLoaderDiscovery.kt`, `InstallerLoaderDiscoveryTest.kt` — Forge V2 URL and matrix validation.
- `InstanceSettingsDialog.kt`, `InstanceWorkspace.kt`, `QuickModdedGameDialog.kt`, `ModdedGameSetup.kt`, `SyntaxLauncherApp.kt`, `i18n/*` — gated test UI.
- `docs/ROADMAP.md`, `docs/TESTING_V0.8.md`, `README.md`, `docs/WORKLOG.md` — state and evidence.

### Verification
- Official Forge/NeoForge installer and tests — PASS, Actions 39560642818.
- New constrained UI changes — CI pending on the latest commit; no real Minecraft Forge game boot claimed.
- v0.7.0 GitHub Release — still blocked by GitHub HTTP 403; separate write-authorized release token/permission required.

### Roadmap updates
- `[x] v0.8 / Official Forge V2 processor smoke and compatibility selection restrictions`.
- `[ ] v0.8 / Real Forge game startup, Modrinth mod and historical compatibility acceptance` — remains open.

### Follow-ups / known issues
- The v0.8 test picker must never offer an unverified Forge/Minecraft combination.
- Product version remains 0.7.0 until v0.8 is verified and explicitly prepared for release.

---

## 2026-10-21 — Begin Forge V2 milestone and diagnose v0.7.0 publication

**Milestone:** v0.8 — Forge/Quilt  
**Author/Agent:** GPT-6  
**Status:** Partial — implementation and CI verification ongoing

### Completed
- Implemented SHA-1-verified official Forge V2 installer preflight, strict checksums, cache, client processor inspection and fail-closed legacy handling.
- Reused the existing NeoForge V2 child-Java processor engine via `ProcessorInstallerV2Engine`, wired Forge into the generic `PreparedModLoaderProvider` and shared desktop dependencies.
- Added mocked checksum/legacy/target-version regressions and a CI-gated official Forge 1.21.1/52.1.20 installer smoke test.
- Added Forge mods.toml local identity parsing and expanded isolated local/Modrinth mod operations to Forge and NeoForge while preserving Fabric/Quilt behavior.
- Fixed a stale `manifest` reference uncovered by CI; addressed pre-existing Fabric/Quilt-only restrictions uncovered by the new Forge tests.
- Diagnosed v0.7.0 release CI: verification and all-platform packaging passed, but GitHub Release creation returned HTTP 403; added optional repository release-token fallback.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/ForgeInstallerPreflight.kt`, `NeoForgeInstallerEngine.kt`, `InstallerLoaderDiscovery.kt`, plus Forge regressions.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt`, `ModrinthInstaller.kt` and mod tests.
- Desktop `Main.kt`, `.github/workflows/build.yml`, `README.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.8.md`, `docs/UPDATING.md`, `docs/WORKLOG.md`.

### Verification
- v0.7.0 candidate run 39558146467 — PASS tests/official NeoForge smoke and three-platform packaging, FAIL publication (GitHub HTTP 403).
- v0.8 Gradle and official Forge smoke — pending latest hosted CI after source/test fixes; do not claim all tests pass yet.
- Real Forge Minecraft launch — NOT RUN; no UI availability claim.

### Roadmap updates
- `[ ] v0.8 / Forge verified V2 adapter, native mod metadata` — implementation started; official CI/game acceptance pending.
- `[ ] v0.8 / Historical Java, Quilt/Forge cross-platform quality gates` — remains open.

### Follow-ups / known issues
- Write-authorized release token/policy required to publish v0.7.0 as an ordinary GitHub Release. The product SemVer remains 0.7.0 until v0.8 is accepted.
- Forge processor Java executes as third-party code; isolated working directory is not OS sandboxing.

---

## 2026-10-21 — Confirm v0.7 CI and publish version 0.7.0

**Milestone:** v0.7 — NeoForge
**Author/Agent:** GPT-6
**Status:** Released with documented limitations; additional compatibility checks deferred

### Completed
- Verified the latest v0.7 `main` CI run 39548448140 succeeded: core/desktop tests, official NeoForge 21.1.213 installer smoke, portable app builds for Windows/Linux/macOS and Build Explorer publication.
- Recorded maintainer's 21 October confirmation that testing is finished and instructed progress to the next milestone. No OS, Minecraft gameplay log, mod-set or matrix detail was provided; none is claimed.
- Reconciled implementation status in roadmap and acceptance docs; left incomplete broader gameplay, interrupted recovery and compatibility quality gates unchecked.
- Advanced the sole canonical product version from 0.6.0 to 0.7.0. The CI workflow on this version-change push is configured to publish an **ordinary stable GitHub Release** once all checks and platform packaging pass. No pre-releases.
- Prepared v0.8 as the next engineering focus; do not bundle incomplete Forge code in the 0.7.0 release.

### Changed files
- `gradle.properties`, `README.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.7.md`, `docs/WORKLOG.md`.

### Verification
- Previous commit CI: **PASS** — https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39548448140.
- Version-change release pipeline: **pending** until hosted CI completes; do not claim the release exists without verifying it.
- Manual verification: maintainer reports completed tests, test matrix and detailed results unspecified.

### Roadmap updates
- `[x] v0.7 / Official installer preflight/processor smoke; adapter integration`.
- `[ ] v0.7 / Full interruption and supported-platform graphical gameplay matrix` — explicitly remains open.

### Follow-ups / known issues
- A passing official installer smoke verifies generated NeoForge artifacts, not a Minecraft graphical boot.
- Follow-up v0.8 work must not overwrite or silently alter release candidate assets while release CI is processing.

---


## 2026-10-21 — NeoForge V2 processor integration and hosted acceptance gate

**Milestone:** v0.7 — NeoForge  
**Author/Agent:** GPT-6  
**Status:** Partial — implementation and CI submitted; release acceptance pending

### Completed
- Added a processor-driven NeoForge installer staging service, shared dependency downloader integration, verification of declared processor outputs, SHA-1-tracked generated runtime libraries and profile merge.
- Added a distinct prepared-loader interface. The standard coordinator obtains base client and Java first for processor-backed loaders; Fabric/Quilt retain the original metadata-only path.
- Exposed NeoForge version selection in instance settings, instance creation and advanced modded-game setup, plus home mode switch/Mods workspace reuse.
- Added simple NeoForge mods.toml ID/name/version extraction to local mod inventory. Full TOML dependency resolution remains future work.
- Added synthetic Java processor fixture and negative checksum/target tests. Added opt-in official NeoForge Maven integration test, run as a CI gate for main before platform packaging. Actual Minecraft client startup is not tested by this fixture.
- Left launcherVersion at 0.6.0 pending real NeoForge installer and gameplay acceptance; **did not publish a GitHub Release**.

### Changed files
- `launcher-core/.../loader/NeoForgeInstallerEngine.kt`, `ModLoader.kt`, `InstallerLoaderDiscovery.kt` — processor-driven loader adapter.
- `launcher-core/.../launch/MinecraftLaunchCoordinator.kt` and desktop `Main.kt` — correct runtime/preparation order and dependency injection.
- Desktop game setup, instance settings/workspace, home navigation and local-mod parser — NeoForge selection and metadata.
- Core NeoForge smoke/fixture tests and `.github/workflows/build.yml` — synthetic and official Maven acceptance coverage.
- `docs/ROADMAP.md`, `docs/TESTING_V0.7.md`, `README.md`, `docs/WORKLOG.md` — status and release gates.

### Verification
- `./gradlew test` — synthetic suite submitted to GitHub CI; latest run result must be checked.
- `./gradlew build` — submitted to hosted CI; this entry does not assume final success.
- Official NeoForge 21.1.213 V2 processor smoke — submitted as a hosted main-branch gate; must be checked.
- Real Minecraft startup/native user acceptance — NOT RUN.
- GitHub stable release — NOT PUBLISHED; do not call this a completed milestone yet.

### Roadmap updates
- `[x] v0.7 / Persist loader selection in instance metadata`
- `[ ] v0.7 / Processor installation, runtime launch, Modrinth and platform quality gates` — integration added but final acceptance missing.

### Follow-ups / known issues
- The external Java process is constrained to a staging working directory but not sandboxed by the operating system. Do not claim that it cannot write outside that directory.
- The live smoke test verifies processor artifacts, not that Minecraft opens a window or runs a NeoForge mod.
- Do not bump the single-source version to 0.7.0 until all release checks pass.

---


## 2026-10-21 — Begin v0.7 NeoForge installer acquisition and verification

**Milestone:** v0.7 — NeoForge  
**Author/Agent:** GPT-6  
**Status:** Partial — implementation committed; CI and real Maven/client acceptance unverified

### Completed
- Introduced a NeoForge-only preflight API to retrieve the official installer and checksum from a fixed Maven host. The executable archive is downloaded through the existing verified DownloadManager and is never executed during this stage.
- Added a dedicated version-scoped installer cache with symbolic-link refusal, strict checksum file parsing, SHA-256 validation, existing verified-cache reuse and read-only inspection of modern installer client processors.
- Added synthetic JAR/MockEngine regressions for cache reuse, integrity rejection, invalid checksum filename, unsafe version and wrong Minecraft version. No instance selection or launcher start behavior is changed.
- Kept Forge/NeoForge unavailable in UI until the processor stage can produce a real runnable client profile. Did not change launcherVersion: updating it now would automatically publish a normal GitHub Release before v0.7 is complete.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/NeoForgeInstallerPreflight.kt` — official NeoForge installer acquisition and verified read-only preflight.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/NeoForgeInstallerPreflightTest.kt` — deterministic mocked HTTP and synthetic installer tests.
- `docs/TESTING_V0.7.md` — staged acceptance and outstanding tests.
- `docs/ROADMAP.md` — partial preflight progress only.
- `docs/WORKLOG.md` — this entry.

### Verification
- `./gradlew test` — NOT RUN (GitHub connector access only; local dependency cache unavailable in execution environment).
- `./gradlew build` — NOT RUN (same reason).
- Manual verification — NOT RUN; official NeoForge installer and game boot still require hosted/native validation.
- Source review — completed for component boundaries and fail-closed behavior; CI must confirm compilation.

### Roadmap updates
- `[ ] v0.7 / NeoForge provider / Installer acquisition and verified inspection` — code added; automated/live acceptance pending.
- `[ ] v0.7 / NeoForge provider / Processor execution and generated artifacts` — not implemented.
- `[ ] v0.7 / quality gate` — not met.

### Follow-ups / known issues
- Do not expose NeoForge in the desktop loader picker or mark v0.7 done until a processor-backed launch path and real acceptance tests exist.
- SHA-256 provided by the same official Maven host detects corruption but is not independent code-signing attestation. Real installer code must run only after explicit installation confirmation and with staging/output controls.
- Large-download and interruption behavior requires native verification before running processor code.

---


## 2026-10-20 — Set the requested product version to 0.6.1

**Milestone:** v0.6 / v0.12 — patch version for the current launcher and update channels
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Changed the sole canonical `launcherVersion` from `0.6.0` to `0.6.1` at the maintainer's request. Modules, embedded launcher metadata and release tooling derive the version from that property.
- Updated current publishing examples and the roadmap publication target. Historical worklog records and independent version-test fixtures remain unchanged.

### Changed files
- `gradle.properties` — canonical product version `0.6.1`.
- `docs/UPDATING.md` — current version and next patch-release example.
- `docs/ROADMAP.md` — requested Stable publication target, with hosted acceptance still pending.
- `docs/WORKLOG.md` — version-change record and verification.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build` (unchanged core tests reused where up to date).
- `./gradlew build` — PASS in the same invocation; generated desktop metadata contains `version=0.6.1`.
- `python3 scripts/launcher_version.py --github-ref refs/tags/v0.6.1` — PASS.
- Python script tests — PASS, 10 tests.
- `git diff --check` — PASS.
- Manual verification — inspected generated version metadata; no UI/gameplay change in this version bump.

### Roadmap updates
- `[ ] v0.12 / CI/CD / Automatic Stable publication — IN PROGRESS` — target updated to `v0.6.1`; hosted publication remains unverified.
- Existing milestone and native/channel-switch acceptance boxes remain open; no milestone is completed by changing the version.

### Follow-ups / known issues
- No commit, push, tag or GitHub Release created. Once the changes reach main, the eligible version increase is intended to publish Stable `v0.6.1` and a separate DEV pre-release after full CI passes.

---

## 2026-10-20 — Add DEV and SNAPSHOT launcher update channels

**Milestone:** v0.12 — explicitly requested early updater/CI extension
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Added immutable DEV GitHub pre-release publication after successful main pushes, including same-product-version code changes. Added manual main-branch workflow dispatch for SNAPSHOT (default) or DEV. Both wait for tests and all three platform jobs; PRs/other branches never publish previews, and previews never become latest stable.
- Kept canonical `launcherVersion` unchanged. Preview tags carry channel/run/attempt plus the canonical version; exact-commit tag creation fails if the tag already exists. Publication requires all six nonempty assets, stages a draft, uploads everything and only then exposes the release. Retries have distinct attempt IDs; partial drafts/tags remain ignored.
- Added explicit channel filtering, paginated preview discovery, numeric CI revision ordering and same-version DEV updates. Stable retains SemVer precedence. Explicit channel switching may select an older product version; matching current build identity prevents repeated handoffs, including identical Stable/DEV promotion images.
- Added independent DEV/SNAPSHOT active/previous pointers and build identities in existing schema-1 update state. Existing stable keys and unknown data remain readable/preserved; receipts, SHA-256, safe extraction, cancellation and fallback protocols remain intact. A cached current build prevents an older previous image from being started unnecessarily.
- Added a compact Launcher updates dialog in Your setup with persisted Stable/SNAPSHOT/DEV choice, next-start instructions, preview explanation and load/save/retry states in EN/PL/DE. The dialog preserves the home hierarchy and 400×400 usability; Minecraft snapshot selection is separate.
- Added additive schema-1 channel preference reads/writes, defaulting to Stable, preserving profile/language/unknown fields and rejecting unknown schemas/channel IDs without rewriting them.
- Added five core channel/persistence regressions, one translated rendered selection/restart regression, and two preview-publication tests. Existing stable update and home tests remain passing.

### Changed files
- `.github/workflows/build.yml` — preview dispatch, build-channel metadata and complete gated preview publication alongside Stable.
- `scripts/publish_preview.py`, `scripts/test_publish_preview.py` — exact-commit immutable draft/upload/publish protocol and deterministic fixtures.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/LauncherRelease.kt`, `StartupUpdater.kt` — channel/revision discovery, selection, independent recovery state and duplicate-handoff prevention.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStore.kt` — additive channel preference preserving existing data.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/LauncherChannelsTest.kt` — pagination/filtering/integrity, same-version update, failed-child/offline recovery, return to older Stable and preferences compatibility.
- `launcher-desktop/build.gradle.kts`, `src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopStartupUpdate.kt`, `Main.kt` — embedded channel/run-attempt metadata and background preference-aware startup wiring.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherUpdateChannelPicker.kt`, `SyntaxLauncherApp.kt` — quiet settings entry and translated channel dialog.
- `launcher-desktop/src/main/resources/i18n/{en,pl,de}.properties`, `src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherUpdateChannelPickerTest.kt` — localized instructions/errors and actual selection/restart/compact render verification.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/DEVELOPMENT.md`, `docs/MILESTONES.md`, `docs/ROADMAP.md`, `docs/UPDATING.md`, `docs/WORKLOG.md` — channel contracts, publishing instructions, backward compatibility and acceptance boundaries.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build :launcher-desktop:createDistributable`; 121 core + 25 desktop tests, zero failures/errors/skips. Fixture HTTP/ZIP/processes and temporary settings only.
- `./gradlew build` — PASS in the same invocation.
- `:launcher-desktop:createDistributable` — PASS for Linux with both ordinary Stable metadata and explicit `-PlauncherUpdateChannel=dev -PlauncherBuildNumber=42`. Verified the packaged JAR contains canonical product version plus separate DEV/build/attempt metadata.
- `scripts/package_update.py` and `:launcher-desktop:verifyUpdateBundle` — PASS for the complete Linux DEV update archive; extracted files, permissions and links revalidated without starting the app.
- Python tests — PASS, 10 script tests plus 6 Build Explorer tests. Workflow YAML parsing, shell syntax and seven publication-trigger scenarios — PASS.
- `git diff --check` — PASS.
- Manual/visual verification — inspected actual Polish/German 400×400 channel-dialog renders; EN/PL/DE interaction/persistence and existing home-size regressions pass. Native Windows/macOS/keyboard/DPI and live GitHub channel-switch tests were not performed.

### Roadmap updates
- `[x] v0.12 / Self-update / Define update channel strategy` — updated the existing completed strategy to document Stable plus opt-in DEV/SNAPSHOT and explicit older-version switching.
- `[ ] v0.12 / Self-update / DEV/SNAPSHOT publication and selection — IN PROGRESS` — local implementation/tests/Linux packaging pass; actual hosted publication and native channel-switch acceptance remain open.
- `[ ] v0.12 / Self-update / Hosted old→new and Windows/Linux/macOS acceptance` — remains open; local fixtures are not hosted end-to-end evidence.

### Follow-ups / known issues
- No commit, push, GitHub release or tag was created by this task. The workflow becomes active after these changes reach GitHub. Install a channel-capable build once, select the desired channel and restart; older binaries cannot gain the picker without an initial update/install.
- Stable remains default. SNAPSHOT is a manually chosen preview rather than a daily promotion; nightly schedules, automatic release/image pruning, signing and unrelated milestones were not implemented.
- Preview release discovery scans at most 100 pages and reports an error rather than silently choosing an incomplete result if history exceeds that bound. Existing original-executable Continue recovery remains available.

---

## 2026-10-20 — Make Vanilla and modded play obvious on the home screen

**Milestone:** v0.4 / v0.6 — existing loader and mod-discovery UX
**Author/Agent:** Codex / GPT-6
**Status:** Completed — implementation/local verification; native child-usability acceptance remains separate

### Completed
- Added a direct Vanilla / With mods choice on the normal home screen. Switching reuses an existing Fabric/Quilt game and restores the prior Vanilla selection within the session; the active selection still persists through the existing preferences.
- Added a short first-time setup that discovers compatible versions through registered providers, prefers the selected Minecraft version and a stable loader, and checks at most seven additional manifest releases if the selected version is unsupported. Network failures never silently select an older version; explicit advanced version choices disable automatic fallback.
- Kept all compatibility lookup read-only and bounded by cancellation/a 20-second deadline. Only Choose mods confirmation creates a UUID profile using the existing repository, selects it and opens Modrinth discovery. Cancel creates nothing; duplicate confirmation and close-during-save are guarded.
- Kept existing Vanilla worlds/data untouched; no automatic conversion, clone or copy. Advanced name/Minecraft/Fabric/Quilt choices remain behind More options.
- Replaced home instance/legacy terminology with My games/My Vanilla game and a clear modded hero badge. Added direct Choose mods access and a compact managed-game version/RAM summary with Game settings instead of disabled technical controls.
- Added EN/PL/DE strings, localized states and accessibility labels. Kept ordinary existing manager/installed-mod entry points available.
- Added four deterministic compatibility-policy tests, three rendered setup/repository regressions, and two home interaction regressions. Verified stable preference, bounded fallback, explicit version selection, cancellation/network retry, duplicate clicks, isolated profile creation, Vanilla world preservation, Quilt reuse, and saved active selection. Updated scrolling tests to scroll to the end rather than assume a fixed 1000px content length.
- Inspected actual compact Polish setup and English modded-home/German Vanilla renders, including the minimum 400×400 setup. Used existing brand surfaces and kept Play as the dominant home action.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ModdedGameSetup.kt` — read-only bounded compatibility suggestion through existing providers.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/QuickModdedGameDialog.kt` — friendly setup, advanced disclosure, retry/cancel/save states and confirmation guard.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — mode switching, existing-profile reuse, new-profile selection, direct discovery and simpler managed-game settings.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherArtwork.kt`, `LocalModsDialog.kt` — accurate modded badge/friendly Vanilla profile name and optional initial discovery view.
- `launcher-desktop/src/main/resources/i18n/en.properties`, `pl.properties`, `de.properties` — translated simple-play controls, help and error states.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ModdedGameSetupTest.kt`, `ui/QuickModdedGameDialogTest.kt`, `ui/LauncherHomeRenderTest.kt` — compatibility, rendered setup/isolation and mode-switch/persistence regressions.
- `README.md`, `docs/DESIGN.md`, `docs/DEVELOPMENT.md`, `docs/MILESTONES.md`, `docs/TESTING_V0.4.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — simple entry point, safety/compatibility contract, acceptance and verification evidence.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build :launcher-desktop:createDistributable`; 116 core + 24 desktop tests, zero failures/errors/skips. Mock HTTP/provider responses, synthetic JARs and temporary profiles only; no real account/world state used.
- `./gradlew build` — PASS in the same full verification.
- `:launcher-desktop:createDistributable` — PASS for Linux; no product version bump or new release tag.
- `git diff --check` — PASS.
- Python repository checks — PASS: 8 script tests and 6 publisher tests.
- Manual/visual verification — reviewed generated `quick-game-pl-400x400.png`, translated compact setup images and wide home renders. Native Modrinth/file-chooser/gameplay flow and observation with children remain separate open acceptance checks.
- Repository publication — maintainer explicitly requested all work be committed and pushed at the end; normal non-force publication follows local verification.

### Roadmap updates
- `[x] v0.4 / UI / Simple Vanilla–With mods entry point, automatic setup and compact managed-profile controls`.
- `[x] v0.6 / UI / Direct home Choose mods entry and initial Modrinth discovery`.
- `[ ] v0.4 / UI / Native end-to-end and child-usability acceptance` — remains open; headless renders do not prove these tests.
- Existing Minecraft Services, Forge/NeoForge, loader integrity-policy and other release gates remain unchanged.

### Follow-ups / known issues
- New modded games intentionally start with their own empty game directory; existing Vanilla worlds are not automatically imported. Explicit clone/import remains in My games.
- Advanced users can keep Quilt and older version choices. No unsupported Forge/NeoForge choices were introduced and no mod is installed without its existing review/confirmation flow.
- Hosted CI/native supported-platform acceptance applies to the pushed commit; local package verification is Linux only.

---


## 2026-10-20 — Consolidate and verify all pending changes for repository push

**Milestone:** v0.1, v0.3, v0.4, v0.5, v0.6 and v0.8
**Author/Agent:** Codex / GPT-6
**Status:** Completed — local verification and requested commit preparation; hosted CI remains separate

### Completed
- Reviewed all tracked, staged and untracked source/documentation changes for the maintainer-requested push of all pending work.
- Included existing localization, preferences, update progress presentation, version-catalog cache, approved-logo icon export, regression tests and milestone acceptance documentation.
- Included pre-existing AccountManager/OS-vault foundations, bounded callback helper, deterministic tests and opt-in synthetic credential probe. These helpers remain separate from current desktop session/callback wiring; no live credentials were read or native vault probe run in this task.
- Corrected the Microsoft application record to distinguish submitted descriptions from actual desktop integration: the runtime still requires the client-ID environment setting, sessions remain in memory and approval/live acceptance remain open.
- Confirmed the local branch matched origin/main before creating the requested commit. No product-version change or release tag is included.

### Changed files
- All pending source/test/documentation files listed in the resulting commit — consolidated existing work at the user's explicit request.
- `docs/MICROSOFT_APP.md` — current integration limits alongside the registration/submission record.
- `docs/ROADMAP.md` — account/vault/callback foundations remain unchecked pending desktop/native acceptance.
- `docs/WORKLOG.md` — final combined verification and publication scope.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build :launcher-desktop:createDistributable`; 116 core and 16 desktop tests, zero failures/errors/skips (cached tasks reused where inputs were unchanged).
- `./gradlew build` — PASS in the same invocation.
- `:launcher-desktop:createDistributable` — PASS, Linux package regenerated with the existing approved-logo icon.
- `git diff --check HEAD` — PASS before commit preparation.
- Manual verification — reviewed pending-file inventory and public Client ID versus credentials; no generated builds, local state, private keys or actual access/refresh tokens included. Earlier Compose render inspection remains the visual evidence; native Windows/macOS and live Microsoft/gameplay checks were not repeated.

### Roadmap updates
- `[x] v0.1 / UI / Existing logo as small icon` — already verified.
- `[x] v0.1 / Interface languages and catalog cache` — existing passing evidence retained.
- `[ ] v0.3 / Account/vault/callback integration and native/live acceptance` — explicitly incomplete.
- Remaining platform/gameplay/loader acceptance gates retain their recorded state.

### Follow-ups / known issues
- Hosted CI must validate the pushed commit. Real Microsoft approval/login, desktop vault integration and native package appearance remain open.

---

## 2026-10-20 — Add EN/PL/DE localization and reconcile verified milestone work

**Milestone:** v0.1 — requested interface localization; v0.4/v0.5/v0.8 — acceptance and regression closure
**Author/Agent:** Codex / GPT-6
**Status:** Completed — requested localization and evidence-backed checklist reconciliation; remaining milestone gates stay open

### Completed
- Read the current architecture, roadmap, milestone definitions, development/design guidance and latest worklog; preserved pre-existing account/callback/cache work and unrelated index changes.
- Recorded the maintainer's explicit Fabric/Quilt test confirmation as real loader startup acceptance. Checked Fabric discovery/download/profile/shared launch and Quilt discovery/profile/shared launch items. Did not invent platform/version/log details or infer Forge/NeoForge, all-platform or fault-injection acceptance.
- Added UTF-8 English, Polish and German resource bundles with stable keys, numbered arguments and English fallback. Localized home/hero/navigation, instance management/editor, local mod controls, Modrinth browsing/review, window actions/accessibility labels and startup update controls/stages.
- Added a compact live language picker with full native language names, system-language default, persistent choice and an actionable save-failure message. Selection updates the Compose tree without changing global JVM locale, draft/profile/account/game data or Minecraft's own language.
- Added language-only atomic reads/writes to schema-1 preferences. This additive key requires no destructive migration: missing keys retain legacy behavior, unknown fields/language values remain stored, newer schemas are refused, and ordinary profile saves preserve the selected language.
- Added typed startup-update stages so presentation translates progress independently of core diagnostic text. Launch progress uses existing typed phases. User/mod/project names, upstream descriptions and detailed technical diagnostics keep their original language.
- Added deterministic resource/placeholder/Unicode/fallback tests, actual picker interaction and restart coverage, preference compatibility/preservation tests, EN/PL/DE wide/narrow home renders and translated updater renders.
- Closed 32 stale implementation checklist entries after reviewing code and passing test evidence. Added direct regressions for local-mod mutation rejection while an instance is active, conflict disappearance after disabling the conflicting mod, and legacy/unknown Minecraft version-type parsing.
- Inspected actual Polish compact and German desktop renders; shortened the language control after finding German header wrapping. Verified all three resource bundles are present in the Linux packaged application JAR.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/i18n/UiStrings.kt`, `LauncherLanguageHost.kt` — scoped localizer, fallback, argument formatting, persisted language selection and picker.
- `launcher-desktop/src/main/resources/i18n/en.properties`, `pl.properties`, `de.properties` — UTF-8 translation bundles.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — language host around home, startup and window controls.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt`, `LauncherArtwork.kt`, `InstanceWorkspace.kt`, `InstanceSettingsDialog.kt`, `LocalModsDialog.kt`, `ModrinthBrowsePane.kt`, `StartupUpdateGate.kt`, `WindowChrome.kt` — localized UI, accessibility and typed progress presentation.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStore.kt` — additive atomic language preference preserving all other fields.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/StartupUpdater.kt` — diagnostic-independent update stage enum.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStoreTest.kt`, `mods/LocalModManagerTest.kt`, `minecraft/VersionManifestParserTest.kt` — preference compatibility, active-instance protection, conflict and historical-type regressions.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/i18n/UiStringsTest.kt`, `LanguagePickerTest.kt`, `ui/LauncherHomeRenderTest.kt`, `ui/StartupUpdateScreenTest.kt` — resource, switching/restart and multi-language render coverage.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT.md`, `docs/DESIGN.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.4.md`, `docs/TESTING_V0.5.md`, `docs/TESTING_V0.6.md`, `docs/TESTING_V0.8.md`, `docs/WORKLOG.md` — localization contracts, accurate acceptance records, current limitations and reconciled progress.

### Verification
- `./gradlew test` — PASS in `./gradlew --offline test build :launcher-desktop:createDistributable`; 116 core + 16 desktop tests, zero failures/errors/skips. Normal tests use mocked HTTP, temporary files and synthetic activity markers; no live credentials are used.
- `./gradlew build` — PASS in the same verification; existing Compose/Gradle deprecation warnings remain.
- `:launcher-desktop:createDistributable` — PASS for Linux; packaged application JAR contains EN/PL/DE resources.
- `git diff --check HEAD` — PASS after representing meaningful resource trailing spaces as Unicode escapes.
- Manual/visual verification — inspected generated German 1440×810 and Polish 440×740 setup renders; translated updater/home renders generated under desktop build reports. Maintainer-confirmed real Fabric and Quilt tests recorded separately from automated evidence.

### Roadmap updates
- `[x] v0.1 / Interface languages` — translation resources, live/persistent choice, backward-compatible settings and deterministic/render coverage.
- `[x] v0.1 / Foundation, offline account and metadata parsers` — reconciled old execution-pending entries with passing implementation evidence.
- `[x] v0.4 / Fabric` and `[x] v0.8 / Quilt` — common-pipeline startup confirmed by the maintainer; second playable provider proves registry reuse.
- `[x] v0.5 / Local model, metadata/import/dependency warnings and reversible/protected mutations` — deterministic evidence and added conflict/activity regressions.
- `[ ] v0.4/v0.8 / Remaining quality gates` — platform/version matrix, loader switching/fault scenarios, missing-profile-hash integrity policy and Forge/NeoForge processors remain open.
- `[ ] v0.3, v0.6 and later milestones` — Microsoft approval/live premium acceptance, richer dependency reconciliation, crash-resume recovery and unrelated future functionality are not claimed complete.

### Follow-ups / known issues
- Native Windows/macOS language/font/keyboard/DPI acceptance remains open; local Linux packaging and headless renders do not substitute for those checks.
- Quilt startup confirmation does not close Forge/NeoForge installer execution or the full v0.8 umbrella. Real mod-management/Modrinth workflow acceptance is distinct from basic Fabric/Quilt boot.
- No release version bump, publication, tag, push or new commit was requested or performed.

---


## 2026-10-20 — Use the existing logo for the small launcher icon

**Milestone:** v0.1
**Author/Agent:** Codex / GPT-6
**Status:** Completed — existing-logo icon replacement

### Completed
- Replaced the old small icon with a transparent 512 × 512 export of the existing approved hero logo, preserving proportions.
- Restored the original hero logo after an incorrect new-logo interpretation. Removed the newly generated package assets and reverted this task's unrelated Microsoft configuration/help changes, preserving other existing work.

### Changed files
- `launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.png` — existing logo exported for header/window/Linux icon.
- `launcher-desktop/src/main/resources/branding/README.md` — export provenance.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — scoped tracking and verification.

### Verification
- `./gradlew test` — PASS via `./gradlew --offline test build`.
- `./gradlew build` — PASS in the same invocation.
- Manual verification — inspected the transparent icon and Polish 1440 × 810 Compose render: header and hero use the same existing logo without stretching. Native OS appearance not checked. `git diff --check` passed.

### Roadmap updates
- `[x] v0.1 / UI / Existing logo as small icon`; native package visual acceptance remains open.

### Follow-ups / known issues
- Native desktop/package visual acceptance remains open. No new logo, authentication feature or release is part of this change.

---

## 2026-10-20 — Cache the Minecraft version catalog for network outages

**Milestone:** v0.1
**Author/Agent:** Codex / GPT-6
**Status:** Completed — catalog cache scope; full offline gameplay and native acceptance remain separate

### Completed
- Implemented fresh-first Mojang catalog fetching with a 15-second request timeout, explicit HTTP/parse errors and fallback to a revalidated last-known valid catalog.
- Publish only successfully parsed, non-empty, consistent catalogs through unique sibling temporary files and atomic replacement where supported. Bad remote responses preserve the previous cache; a corrupt cache is retained on failed refresh and repaired by a valid online response.
- Run disk work on the IO dispatcher, propagate cancellation, and allow successful online startup even when cache storage is unavailable.
- Wired the desktop catalog to the platform cache directory. Kept existing shared HTTP and UI Retry behavior.
- Added seven deterministic MockEngine/temp-directory regressions for restart reuse, fresh replacement, HTTP/malformed/incomplete failures, missing/corrupt cache, unavailable storage and cancellation.
- Restored the missing `jna-platform:5.17.0` dependency required by pre-existing native credential source files; the initial full verification exposed unresolved JNA imports. Existing account/callback work was preserved.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/minecraft/MinecraftVersionCatalog.kt` — validated cache publication/fallback, background IO and actionable errors.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/minecraft/MinecraftVersionCatalogTest.kt` — seven deterministic catalog regressions.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — platform cache path injection.
- `launcher-core/build.gradle.kts` — missing native credential JNA dependency.
- `README.md`, `docs/DEVELOPMENT.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — cache behavior, limits and verification evidence.

### Verification
- `./gradlew test` — PASS in `./gradlew --offline test build`; 111 core + 10 desktop tests pass, including seven new catalog regressions; no failures/errors/skips. Initial failure from missing JNA dependency and a test's overly specific assumption about wrapped network exceptions were corrected before final verification.
- `./gradlew build` — PASS in the same final invocation; existing Kotlin/Compose/Gradle deprecation warnings remain.
- `git diff --check` — PASS.
- Manual verification — reviewed desktop path wiring and error/Retry compatibility. No real Mojang outage, native GUI gameplay or premium login was exercised; tests use mocked HTTP and disposable directories.

### Roadmap updates
- `[x] v0.1 / Minecraft version discovery / Cache version manifest safely`.
- `[x] v0.1 / v0.1 UI / Cache version manifest to support startup when Mojang is unreachable`.
- `[ ] v0.1 / Quality gate / Fresh real Minecraft launch and native acceptance` — remain open; cached catalog availability does not prove offline gameplay.

### Follow-ups / known issues
- Selected-version metadata and missing game/runtime artifacts can still require network access. This slice does not implement fully offline launch or advance loader milestones.
- Existing premium acceptance, Minecraft Services approval and Windows/macOS native gates remain open. No credentials, existing vault entries, release publication or commits were involved.

---

## 2026-10-20 — Forge-family installer preflight, Quilt metadata and rollback safety

**Milestone:** v0.6, v0.7 and v0.8  
**Author/Agent:** GPT-6  
**Status:** Partial — code pushed; final hosted CI and Minecraft acceptance pending

### Completed
- Scoped NeoForge Maven builds to the exact Minecraft patch line (e.g. 26.1 → 26.1.0.x, not 26.1.1.x).
- Added a read-only version-2 Forge/NeoForge JAR inspector for install_profile.json, version.json, required Maven libraries, client-side processor descriptions and embedded data keys.
- Rejected malformed/oversized archives, unsafe artifacts, missing client processors and cross-Minecraft version profiles rather than attempting unsupported launch.
- Added synthetic installer archive tests, Modrinth transactional update tests, and Quilt mod descriptor tests.
- Added quilt.mod.json metadata parsing for local Quilt instance JAR management.
- Improved rollback ordering to avoid retaining a completed-update marker when post-install validation fails.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/InstallerProfileInspector.kt`
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/InstallerProfileInspectorTest.kt`
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt`
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManagerTest.kt`, `ModrinthUpdateTransactionsTest.kt`
- `README.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md`

### Verification
- Previous complete CI run for `67a4092`: Gradle tests and all three OS packages **PASS**, but that commit predates Quilt metadata and installer inspector.
- Current `./gradlew test` and `./gradlew build`: NOT RUN locally; verify current GitHub Actions run.
- Live game launch and graphical interaction: NOT RUN; no v0.7/v0.8 quality gate closed.

### Roadmap updates
- NeoForge and Forge metadata/parser tasks 🚧, processor execution still open.
- Quilt selection/profile and local metadata tasks 🚧, manual game launch acceptance still open.
- Modrinth transactional updates/rollback 🚧, crash-resume journal and advanced dependency resolution still open.

### Follow-ups / known issues
- NeoForge/Forge client processors remain unimplemented; neither loader is exposed as playable.
- Real Quilt client installation/startup must be tested on targeted systems; v0.8 is not complete.
- Verify process-crash interruption recovery for Modrinth replace/rollback, beyond exception-safe in-process recovery.

---

## 2026-10-20 — Quilt loader integration and Forge-family discovery

**Milestone:** v0.7 NeoForge / v0.8 Forge and Quilt  
**Author/Agent:** GPT-6  
**Status:** Partial — GitHub CI and game acceptance pending

### Completed
- Added Quilt Meta v3 provider with compatible loader versions and official profile merge, reusing generic loader boundaries.
- Added Quilt to instance creation/editing and instance-scoped local/Modrinth JAR operation.
- Registered NeoForge and Forge Maven version-discovery adapters with safe metadata parsing and fail-closed launch behavior.
- Explicitly withheld the NeoForge/Forge UI choices until processor-based installation is implemented; exposing a download as a playable loader would be misleading.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/*`, `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/*`
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt`
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceWorkspace.kt`, `InstanceSettingsDialog.kt`, `SyntaxLauncherApp.kt`, `LocalModsDialog.kt`
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt`, `ModrinthInstaller.kt`
- `README.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md`

### Verification
- `./gradlew test`, `./gradlew build` — NOT RUN locally; hosted CI required.
- Real game launch — NOT RUN; Quilt support is unverified, Forge and NeoForge are intentionally not launchable.

### Roadmap updates
- v0.7/v0.8 official version discovery and Quilt adapter marked 🚧.
- Forge/NeoForge processors and generated artifacts remain unchecked.

### Follow-ups / known issues
- Implement verified official installer processors, generated libraries and Minecraft-version-specific support matrix for NeoForge/Forge.
- Confirm Quilt API and actual game boot on real installations before declaring v0.8 complete.

---

## 2026-10-20 — Transactional managed Modrinth updates and rollback

**Milestone:** v0.6 — Modrinth Integration  
**Author/Agent:** GPT-6  
**Status:** Partial — committed; CI and real-game acceptance pending

### Completed
- Added per-mod and bulk Modrinth updates with user confirmation and verified SHA-1 download.
- Added cross-process inactive-instance-locked JAR replacement, stale SHA-256 checks, duplicate mod-id/filename preflight, and permanent copies of replaced JARs.
- Added explicit rollback of the most recent completed batch, including managed project/version records; reject rollback if files changed.
- Fail closed for upgrades introducing different or missing required Modrinth project dependencies; do not silently claim full dependency reconciliation.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthUpdateTransactions.kt` — backup receipt types.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthCatalog.kt` — update metadata/dependency gates.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthInstaller.kt` — verified update downloads.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt` — guarded replacements and rollback.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LocalModsDialog.kt` — update confirmation and rollback controls.
- `README.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — scope and acceptance tracking.

### Verification
- `./gradlew test`, `./gradlew build` — NOT RUN locally (GitHub-hosted CI required).
- Manual game/GUI startup — NOT RUN; previously deferred by maintainer.

### Roadmap updates
- v0.6 update and rollback entries 🚧 pending hosted CI and user acceptance.
- Full dependency graph reconciliation, optional dependencies and automatic repair remain open.

### Follow-ups / known issues
- Verify interrupted update and rollback fault injection, crash recovery, and legacy/manual JAR handling.
- Record an explicit durability journal for hard process crash mid-update; current rollback handles exceptions while the process is alive.

---

## 2026-10-20 — Managed Modrinth identity and read-only updates

**Milestone:** v0.6 — Modrinth Integration  
**Author/Agent:** GPT-6  
**Status:** Partial — committed; hosted CI and gameplay acceptance pending

### Completed
- Persist project/version identity per Modrinth-installed JAR under the instance game directory; bind records to a locally calculated SHA-256 to avoid matching user-replaced files.
- Record managed identity in the same inactive-instance transaction as verified batch installation. Roll back newly published JARs if writing records fails.
- Preserve identity across disable/enable and remove it when moving a JAR to instance trash.
- Detect newer compatible Modrinth releases by verified project identity, loaded release metadata and publication date; leave unmatched local mods untouched.
- Display a non-destructive manual update check and managed version provenance inside the Mods dialog.
- Add MockEngine and temporary-filesystem tests.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthManagedMods.kt`, `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt`, `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthInstaller.kt`, `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthCatalog.kt`, `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LocalModsDialog.kt`
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManagerTest.kt`, `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthCatalogTest.kt`, `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthInstallerTest.kt`
- `README.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md`

### Verification
- `./gradlew test` — NOT RUN locally; awaiting GitHub Actions.
- `./gradlew build` — NOT RUN locally; awaiting GitHub Actions.
- Manual UI and Minecraft/Fabric launch — NOT RUN; owner deferred acceptance.

### Roadmap updates
- `[ ]` v0.6 Modrinth identity/update discovery/UI marked 🚧, pending verification.
- `[ ]` v0.6 per-mod and batch replacement, rollback and advanced dependency reconciliation remain open.

### Follow-ups / known issues
- Implement safe, reviewed mod update replacement and rollback without overwriting untracked local files.
- Validate live API and cross-platform manual gameplay tests; do not declare v0.6 completed.

---

## 2026-10-20 — Enforce normal GitHub Releases and advance canonical version

**Milestone:** v0.6 release packaging / early v0.12 CI/CD  
**Author/Agent:** GPT-6  
**Status:** Partial — source changes committed; GitHub CI publishing outcome pending

### Completed
- Investigated missing GitHub Releases despite green platform builds: the source product version stayed at `0.3.0-dev` while development reached v0.6, so the version-change release gate never opened.
- Advanced the sole canonical product version to `0.6.0`; no alternate product version or SNAPSHOT suffix was added.
- Restricted manual tag-triggered release jobs to plain stable tags, removed the prerelease publication branch, and required every GitHub Release to be ordinary/latest.
- Retained detection of forward stable changes, complete six-asset publication only after tests, and protection against overwriting existing releases.
- Updated publishing documentation to explicitly prohibit GitHub prereleases. Historical v0.4/v0.5 milestones are not falsely marked as having published releases.

### Changed files
- `gradle.properties` — canonical `0.6.0` product version.
- `.github/workflows/build.yml` — normal GitHub Release only, never prerelease.
- `README.md`, `docs/UPDATING.md`, `docs/DEVELOPMENT.md` — workflow semantics.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — progress record and limitations.

### Verification
- Prior successful run [39374099339](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39374099339) — cross-platform verification PASS but release skipped because the old canonical version was unchanged.
- `./gradlew test`, `./gradlew build` — NOT RUN locally; new GitHub Actions run pending.
- GitHub `v0.6.0` release publication — NOT YET CONFIRMED.
- Real Minecraft and cross-platform manual acceptance — NOT RUN; roadmap gates remain open.

### Roadmap updates
- `[ ]` Auto publishing remains 🚧 pending a verified hosted release.
- `[ ]` Gameplay, signing, updater and v0.6 completion checks remain open.

### Follow-ups / known issues
- Verify the new workflow publishes an ordinary `v0.6.0` latest release with all six platform artifacts after all CI jobs succeed.
- Stable auto-updating consumers can see this public release; reconcile functional acceptance before broad distribution.

## 2026-10-20 — Implement popular Modrinth discovery and verified installation slice

**Milestone:** v0.6 — Modrinth Integration  
**Author/Agent:** GPT-6  
**Status:** Partial — pushed to main; CI and real-game acceptance pending

### Completed
- Replaced the idea of a static popular-mod list with Modrinth API sorting by downloads, and exact instance loader/Minecraft version search facets.
- Added project detail retrieval, license/description, external project link and manual search/pagination.
- Planned latest compatible release (release channel before beta) and recursively resolved required Modrinth dependencies into a user-visible installation proposal.
- Added SHA-1/size checked staging through shared DownloadManager and guarded atomic batch import, protecting existing filenames/identities and keeping all writes instance-scoped.
- Connected the catalog to a new Discover view inside the existing Fabric Mods workspace; local imports remain intact.
- Added deterministic MockEngine tests for filtering/dependency plans plus verified installation and checksum failure.
- Updated README, architecture and milestone checklist.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthCatalog.kt` — official API and dependency resolver.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthInstaller.kt` — SHA-1-verified staging.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt` — atomic, preflighted importBatch.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/ModrinthBrowsePane.kt` — popular mods, search, detail and confirmation.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LocalModsDialog.kt`, `SyntaxLauncherApp.kt`, `DesktopLauncherSession.kt` — browsing and installation wiring.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/ModrinthCatalogTest.kt`, `ModrinthInstallerTest.kt` — tests.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.6.md`, `docs/WORKLOG.md` — developer/user documentation.

### Verification
- `./gradlew test` — NOT RUN locally; GitHub Actions pending.
- `./gradlew build` — NOT RUN locally; GitHub Actions pending.
- Manual Modrinth installation, actual Fabric boot and UI on target systems — NOT RUN; remains explicitly open.

### Roadmap updates
- `[ ]` v0.6 browsing/installation slice marked 🚧 pending verification.
- `[ ]` v0.6 identity persistence/updates and more advanced conflicts remain unimplemented.
- `[ ]` previous v0.4/v0.5 manual acceptance remains deferred, not passed.

### Follow-ups / known issues
- Investigate CI if compilation/test failures arise.
- Verify Modrinth rate-limit behavior and API release metadata against live responses.
- Add durable Modrinth project/version tracking for update matching, optional dependencies, update plans and rollback.
- Verify actual client launch and Compose UI operation on Windows, macOS and Linux.

## 2026-10-20 — Preserve in-flight stable version builds

**Milestone:** v0.12 — CI/CD  
**Author/Agent:** GPT-6  
**Status:** Partial — CI change committed; subsequent hosted verification pending

### Completed
- Kept the existing single-source automatic release workflow and tests without introducing a competing publisher.
- Prevented new commits to `main` from cancelling a verified-version build already in progress. Other refs retain cancellation of superseded runs.

### Changed files
- `.github/workflows/build.yml` — conditional Actions concurrency cancellation policy.
- `docs/WORKLOG.md` — this entry.

### Verification
- Workflow syntax inspected; `./gradlew test` and `./gradlew build` — NOT RUN locally, CI verification pending.
- No stable version was bumped and no actual GitHub Release was published.

### Roadmap updates
- `[ ]` v0.12 hosted automated release and updater acceptance remain open.

### Follow-ups / known issues
- A controlled forward stable bump is still needed to verify complete six-asset release publication.

## 2026-10-20 — Automatically publish GitHub Releases on stable version bumps

**Milestone:** v0.12 — Production Hardening (early CI/CD slice)  
**Author/Agent:** GPT-6  
**Status:** Partial — source committed; first live automated release unverified

### Completed
- Reused the single canonical `launcherVersion` property; compare current and previous `main` versions to detect intentional stable SemVer increments or prerelease-to-stable promotions.
- Reused existing Gradle/Python verification, all-platform portable builds and six verified update/archive assets before release publication.
- Create or validate the exact-commit tag, stage a draft, upload all artifacts and publish it; protect published tags/releases against overwrite, while keeping manual tag publishing.
- Added deterministic tests for release eligibility and documented how to publish stable releases.

### Changed files
- `.github/workflows/build.yml` — automatic version-triggered publishing in existing CI.
- `scripts/launcher_version.py`, `scripts/auto_release.py`, `scripts/test_auto_release.py` — canonical historical version comparison and tests.
- `docs/UPDATING.md`, `docs/DEVELOPMENT.md`, `README.md` — release and versioning instructions.
- `docs/ROADMAP.md` — outstanding hosted release acceptance.
- `docs/WORKLOG.md` — this entry.

### Verification
- Python tests — NOT RUN locally; GitHub CI executes `python3 -m unittest discover -s scripts -p 'test_*.py'`.
- Gradle `test` / `build` — NOT RUN locally; regular GitHub CI pending.
- Live automatic GitHub Release — NOT RUN; current version remains `0.3.0-dev` to avoid an unreviewed public release.

### Roadmap updates
- `[ ]` v0.12 / CI/CD / automatic stable GitHub Releases — first hosted version-bump test is still open.

### Follow-ups / known issues
- For the first reviewed stable version bump, verify the GitHub tag, six assets, release status and end-to-end old→new update.
- Remaining manual Minecraft and native package acceptance is independent of CI success.

## 2026-10-20 — Consolidate SyntaxLauncher version into gradle.properties

**Milestone:** Cross-milestone build/release ergonomics; v0.12 updater/CI slice  
**Author/Agent:** GPT-6  
**Status:** Partial — code and deterministic tests prepared for main; GitHub Actions and native package acceptance pending

### Completed
- Defined one canonical product SemVer as `launcherVersion=0.3.0-dev` in root `gradle.properties`, preserving the existing development product version.
- Derived root Gradle project versions, desktop generated build info and OS-compatible native package versions from that property; removed stale hardcoded product-version fallbacks.
- Updated GitHub Actions to read the property instead of inventing `0.3.0-dev` or overriding it from a tag; mismatched release tags fail early.
- Updated Build Explorer publisher to read the same property and validate tagged publication; added deterministic Python tests for property parsing, mismatch rejection and publisher metadata.
- Updated release instructions, architecture, contributor rules and roadmap.

### Changed files
- `gradle.properties`, `build.gradle.kts`, `launcher-desktop/build.gradle.kts`, `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopStartupUpdate.kt` — single Gradle/runtime version path.
- `.github/workflows/build.yml`, `scripts/launcher_version.py`, `scripts/test_launcher_version.py` — CI version propagation/tag enforcement.
- `scripts/publish_build_explorer.py`, `tests/test_build_explorer_publish.py` — unified Build Explorer metadata and regression tests.
- `AGENTS.md`, `README.md`, `docs/UPDATING.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — documentation and progress tracking.

### Verification
- `./gradlew test` — NOT RUN locally; GitHub Actions verification pending.
- `./gradlew build` — NOT RUN locally; GitHub Actions verification pending.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — NOT RUN locally; CI pending.
- `python3 -m unittest discover -s tests -p 'test_*.py'` — NOT RUN locally; CI pending.
- Manual cross-platform release/package validation — NOT RUN.

### Roadmap updates
- `[ ]` Cross-milestone / canonical version consolidation remains 🚧 until CI and native validation are confirmed.
- `[ ]` v0.12 / hosted updates, signing and native acceptance remain open.

### Follow-ups / known issues
- Verify CI output includes the exact source SemVer in all three update manifests and Build Explorer metadata.
- Manual native package and old→new update acceptance remains open; no release tag was created.
- Native jpackage's numeric version intentionally maps product major 0 to major 1 for macOS; it must not be used for updater SemVer comparisons.

## 2026-10-20 — Add pre-launch mod warnings and confirm dialog scope

**Milestone:** v0.5 — Local Mod Manager  
**Author/Agent:** GPT-6  
**Status:** Partial — submitted to main; final CI and manual verification pending

### Completed
- Show a concise warning summary for the selected Fabric instance in the home sidebar before Play; the full diagnostic list remains in Instances → Mods.
- Scope mod-removal confirmation to its own active modal window so it is not obscured by the Instances/Mods window.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — refresh warnings asynchronously on selection and after mod mutations.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LocalModsDialog.kt` — confirm removal inside the mod dialog.
- `docs/ROADMAP.md` — note pre-launch summaries.
- `docs/WORKLOG.md` — this entry.

### Verification
- `./gradlew test` — NOT RUN locally; final GitHub Actions pending.
- `./gradlew build` — NOT RUN locally; final GitHub Actions pending.
- Manual — NOT RUN; v0.4/v0.5 real Fabric launch validation deferred.

### Roadmap updates
- `[ ]` pre-launch warning UI remains 🚧 until acceptance.
- `[ ]` manual acceptance for v0.4/v0.5 remains open.

### Follow-ups / known issues
- Check CI and address regressions.
- Verify whether warnings are surfaced without excessive latency on large mod directories.

## 2026-10-20 — Begin v0.5 instance-local Fabric mod management

**Milestone:** v0.5 — Local Mod Manager  
**Author/Agent:** GPT-6  
**Status:** Partial — source pushed to main; CI and real game acceptance pending

### Completed
- Added a UI-independent local JAR manager reading optional Fabric mod metadata, computing SHA-256 and warning about duplicate active IDs, missing declared dependencies and conflicts.
- Implemented atomic, non-overwriting import from a user-selected JAR, a reversible enable/disable filename change, and a remove-to-private-trash operation.
- Kept writes scoped to one instance and synchronized with the repository's existing cross-process activity lock. Rejected symlinked mods locations and archive/filename traversal.
- Connected a scrollable Compose mod dialog and local file picker to Fabric instance management; future online downloads remain in v0.6.
- Added deterministic tests for imports, reversibility, metadata, missing dependencies, duplicates and Vanilla isolation.
- Maintainer explicitly deferred v0.4 manual tests; none were marked passed.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManager.kt` — local mod service and metadata diagnostics.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — guarded instance mutation access.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{DesktopLauncherSession.kt,ui/InstanceWorkspace.kt,ui/SyntaxLauncherApp.kt,ui/LocalModsDialog.kt}` — UI and wiring.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/mods/LocalModManagerTest.kt` — regression tests.
- `README.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.5.md`, `docs/WORKLOG.md` — documentation.

### Verification
- `./gradlew test` — NOT RUN locally; GitHub Actions pending.
- `./gradlew build` — NOT RUN locally; CI pending.
- Manual Minecraft startup, Fabric mod load and cross-platform GUI checks — NOT RUN (deferred).

### Roadmap updates
- `[ ]` v0.5 implementation items are marked 🚧 until tests and acceptance pass.
- `[ ]` v0.4 real client and desktop verification stay outstanding.

### Follow-ups / known issues
- Review CI, fix failures and reconcile passing automated checkpoints in roadmap.
- Run live Fabric with enabled/disabled local mods; validate interaction with other Fabric instances and Vanilla.
- Extend metadata matching/version range handling and pre-launch dependency warnings without hiding optional/unknown states.
- Confirm user-supplied local mod JAR provenance; enabled mods run third-party code in Minecraft.

## 2026-10-19 — Verify Fabric v0.4 implementation across CI platforms

**Milestone:** v0.4 — Fabric Loader
**Author/Agent:** GPT-6
**Status:** Partial — automated verification passed; live Fabric boot still unverified

### Completed
- Confirmed GitHub Actions run [39317269516](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39317269516) succeeded for core/desktop compile and tests, portable packaging on macOS, Linux and Windows and Build Explorer publication.
- Updated the v0.4 roadmap to mark implementation and test checks complete where CI provides sufficient evidence while retaining manual quality gates.

### Changed files
- `README.md` — recorded verified CI and remaining real-client acceptance.
- `docs/ROADMAP.md` — reconciled v0.4 checklist with automated build/test results.
- `docs/WORKLOG.md` — appended verification outcome.

### Verification
- `gradle --no-daemon --stacktrace test build` — PASS via GitHub Actions [39317269516](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39317269516).
- Portable application package builds — PASS for macOS, Ubuntu and Windows on that run.
- Build Explorer publication — PASS.
- Manual Fabric Minecraft launch — NOT RUN; quality gate remains open.

### Roadmap updates
- `[x]` generic loader selection/model and implementation tasks covered by passing CI.
- `[x]` v0.4 CI builds/tests.
- `[ ]` real Fabric boot, instance game-data isolation, visual UX and multi-platform acceptance.

### Follow-ups / known issues
- Test real Fabric client startups with stable releases and ensure appropriate game data separation.
- Verify hosted artifact checksum coverage for Fabric libraries without published hashes.
- Microsoft/Mojang app-approval dependency from v0.3 remains independent.

---

## 2026-10-19 — Begin v0.4 Fabric loader integration

**Milestone:** v0.4 — Fabric Loader
**Author/Agent:** GPT-6
**Status:** Partial — pushed to main; CI and actual Fabric boot acceptance pending

### Completed
- Evaluated OptiFine-first against Fabric-first and retained the documented Fabric-first plan. OptiFine remains scheduled for a separate graphics feature phase.
- Introduced a generic mod loader registry, provider contract and versioned selection stored optionally in existing schema-1 instance metadata.
- Added a Fabric Meta adapter for compatible loader discovery, profile parsing, trusted Maven sources, per-library metadata and overlay of Fabric entry point/dependencies onto the Mojang base version.
- Routed loader selection through the existing prepare/Java/launch pipeline; did not add a second game runner.
- Added Vanilla/Fabric controls, live compatibility loading, selection changes and feedback in create/edit instance screens.
- Added deterministic MockEngine and file-backed tests for compatibility, metadata, security and persistence.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/loader/*` — registry, Fabric integration and profile parser.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — optional loader field.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt` — loader resolution phase.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/{Main.kt,DesktopLauncherSession.kt,ui/*}` — loader service wiring and profile controls.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/{loader/FabricLoaderProviderTest.kt,instance/MinecraftInstanceRepositoryTest.kt}` — test coverage.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TESTING_V0.4.md`, `docs/WORKLOG.md` — delivery and verification documentation.

### Verification
- `./gradlew test` — NOT RUN locally; GitHub Actions verification running after source commits.
- `./gradlew build` — NOT RUN locally; local runtime has no network access to Gradle/dependencies.
- Manual verification — NOT RUN; requires a real Fabric client on target desktops.

### Roadmap updates
- v0.4 features marked `🚧 IN PROGRESS` instead of prematurely completed.
- Added explicit library integrity and real client/desktop acceptance checkpoints.

### Follow-ups / known issues
- Check and repair any CI compilation/tests failures.
- Verify a real Fabric instance launches and keeps saves/configs separated from Vanilla.
- Some Fabric profiles do not supply library checksums; strengthen verifiable artifact metadata before claiming complete integrity.
- v0.3 Microsoft app approval remains outstanding and independent of v0.4 offline launch verification.

---

## 2026-10-19 — Fix Compose Desktop draggable area import

**Milestone:** v0.3 desktop window chrome
**Status:** Source fix submitted; new CI verification pending

- The first GitHub Actions run for commit `1112479` failed Kotlin compilation because `WindowDraggableArea` was imported from the old `androidx.compose.ui.window` namespace.
- Corrected to the supported Compose Desktop import `androidx.compose.foundation.window.WindowDraggableArea`, matching the official Window API documentation.
- No window logic or Minecraft runtime behavior changed. Native UI acceptance remains pending.

---

## 2026-10-19 — Identify CI builds and replace native desktop title bars

**Milestone:** Cross-milestone v0.3 development UX / v0.12 build and distribution metadata
**Author/Agent:** GPT-6
**Status:** Source changes prepared for main; native/manual validation pending

### Completed
- Extended the generated launcher-build.properties resource with CI run number and commit SHA while preserving semantic version for updater comparison and handoff receipts; local execution is explicitly labeled LOCAL BUILD.
- Displayed build identity in the right side of the main header, with a compact form for smaller windows, and on the startup updater screen.
- Replaced the standard AWT decorations with Compose-native minimize, maximize/restore and close controls visible during normal operation and update startup.
- Enabled header dragging and installed eight custom resize hit areas with minimum-size-constrained geometry; preserved the original session close process and window minimum size.
- Added isolated resize geometry tests and passed build identifiers from GitHub Actions to build and packaging tasks.

### Changed files
- `launcher-desktop/build.gradle.kts`, `DesktopStartupUpdate.kt` — generated version/build/commit metadata.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt`, `ui/SyntaxLauncherApp.kt`, `ui/SyntaxComponents.kt`, `ui/WindowChrome.kt`, `ui/StartupUpdateGate.kt` — frameless window and visible build identifiers.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/WindowChromeTest.kt` — pure geometry tests for resize directions, limits and negative screen positions.
- `.github/workflows/build.yml` — CI build number and commit passed through Gradle.
- `README.md`, `docs/DESIGN.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — release and UX documentation.

### Verification
- Connector-level file and commit review only. Full Gradle compile/test and real UI execution were not possible in the agent's network-isolated runtime.
- Await GitHub Actions build/tests and manual window checks; do not treat source presence as visual acceptance.

### Roadmap updates
- Marked CI/build identity source wiring complete.
- Left native frameless window acceptance in progress and cross-platform/manual checks unchecked.

### Follow-ups / known issues
- Verify minimize/maximize/restore, border dragging, window restore bounds, updater controls, navigation hit testing, and layout under Windows/macOS and Linux X11/Wayland, including HiDPI and multi-monitor configurations.
- No runtime changes to the Minecraft launch process, premium authentication or instance storage.

---

## 2026-10-19 — Integrate updater with concurrent main publication changes

**Milestone:** v0.12 — startup updater; v0.1 — existing Build Explorer publication
**Author/Agent:** Codex / GPT-6
**Status:** Completed — merge integration verified locally

### Completed
- Integrated main commits `422c404` and `4f01065` with updater commit `d6e114f` after a normal push was rejected as non-fast-forward.
- Preserved the existing Build Explorer publisher/job and the updater release publisher/job, with both Python test suites and the full Gradle verification gate.
- Resolved adjacent documentation/cache-ignore additions by retaining both bodies of work; preserved historical worklog entries.
- Temporarily secured pre-existing staged/unstaged Gradle wrapper changes separately from all committed updater/integration files.

### Changed files
- `.github/workflows/build.yml` — retained both publication jobs and both Python test steps.
- `.gitignore` — combined Kotlin/Python cache exclusions.
- `docs/DEVELOPMENT.md` — retained updater and Build Explorer operating instructions.
- `docs/ROADMAP.md` — retained the upstream verified Build Explorer item and local updater implementation/acceptance status.
- `docs/WORKLOG.md` — retained both histories and recorded the integration.
- Upstream `scripts/publish_build_explorer.py` and `tests/test_build_explorer_publish.py` — retained without modification.

### Verification
- `./gradlew test` — PASS using the cached Gradle 9.8.1 executable while the original wrapper edits were secured; unchanged core/desktop tasks reused their passing results.
- `./gradlew build` — PASS in the same `--offline test build` invocation.
- Python update packaging tests — PASS, 2 tests.
- Python Build Explorer publisher tests — PASS, 5 tests.
- Workflow YAML/integration checks — PASS; all four jobs, both test suites and scoped publication permissions present.
- `git diff --check` and conflict-marker checks — PASS.

### Roadmap updates
- Retained `[x] v0.1 / Build and CI validation / Build Explorer publication`.
- Retained implemented v0.12 updater checks; hosted update/native acceptance and remaining production gates stay open.

### Follow-ups / known issues
- No release tag or hosted update acceptance is claimed by this merge. Normal main CI publication remains configured by the upstream changes.
- Existing user wrapper edits are excluded from updater and merge commits and restored separately.

---

## 2026-10-19 — Add startup launcher updates with verified side-by-side installs

**Milestone:** v0.12 — startup self-update slice explicitly requested early by the maintainer
**Author/Agent:** Codex / GPT-6
**Status:** Partial — implementation/local verification complete; hosted publication and native Windows/macOS acceptance remain open

### Completed
- Added a UI-independent update service that checks this project's stable public GitHub Releases before composing the launcher home. Uses semantic version ordering, owned HTTPS platform asset paths, GitHub SHA-256 digests, short check timeouts and the shared HTTP/download services.
- Extended the common downloader with optional SHA-256 and per-request timeout support, cache revalidation, oversized-response rejection and unchanged existing SHA-1 Minecraft support.
- Added schema-1 manifest-driven ZIP installation with archive/per-file SHA-256, fixed OS/CPU executable identity, executable bits, safe relative symlinks, archive/path/case/size validation, disk-space preflight and cancellable private staging. The real Linux package was installed and fully revalidated.
- Kept the original application and all user data untouched; installed complete launcher images beside each other in user-owned storage. Valid cached images are reused, corrupt images can be repaired into a fresh directory, and previous/orphan directories are retained.
- Added a cross-process update lock and atomic schema-versioned active/previous state, preserving unknown properties and refusing unknown schemas without rewriting them.
- Implemented startup receipts: the child validates the single-use destination/nonce, renders Compose content, and reports its embedded version after two frames. The bootstrap activates only a matching ready child. Failed/timed-out/cancelled children are terminated; failed new installs/starts fall back to a verified active version when available. Offline cached startup is covered.
- Added a branded checking/download/install/start screen with progress, Retry/Continue, actionable errors and protected process handoff. Gradle/IDE runs bypass automatic execution; packaged jpackage applications enable it.
- Generated build-time semantic version metadata (`-PlauncherVersion`), displayed the actual version in the header, and added a standalone full-bundle verification task that installs/revalidates without starting downloaded code.
- Added stdlib Python update packaging with file hashes, executable flags and symlink metadata. CI now runs desktop tests and package fixtures, builds/verifies all three update bundles and, for maintainer-pushed version tags only, publishes a complete draft-to-public release with update ZIPs and first-install portable archives. Published releases are not overwritten on rerun.
- Documented the updater trust model, first manual installation requirement, release process, recovery behavior and remaining native acceptance. This requested slice does not advance unrelated milestones or waive their quality gates.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/LauncherRelease.kt` — SemVer, platform mapping and stable GitHub release checks.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/UpdateBundleInstaller.kt` — safe staged installs, cache repair and per-file revalidation.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/StartupUpdater.kt` — update lifecycle, process receipt, locking, fallback and atomic state.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/network/DownloadModels.kt`, `DownloadManager.kt`, `DownloadIntegrityVerifier.kt`, `DownloadExceptions.kt` — SHA-256, length bounds and update download timeout.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/update/LauncherUpdateTest.kt` — release, integrity, filesystem, real-child, fallback, cancellation and offline regressions.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopStartupUpdate.kt`, `Main.kt`, `ui/StartupUpdateGate.kt`, `ui/SyntaxLauncherApp.kt` — build identity, packaged startup gate, receipt and UI.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/StartupUpdateScreenTest.kt`, `StartupHandoffTest.kt` — rendered states/actions and real Compose startup receipt.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/update/UpdateBundleSmoke.kt`, `launcher-desktop/build.gradle.kts` — consistent build resource and explicit real-bundle verification.
- `scripts/package_update.py`, `scripts/test_package_update.py`, `.github/workflows/build.yml`, `.gitignore` — package protocol, CI/publishing, local Kotlin/Python cache exclusions.
- `docs/UPDATING.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT.md`, `docs/DESIGN.md`, `docs/MILESTONES.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — protocol, early scope, UX, release/acceptance and progress.

### Verification
- `./gradlew test` — PASS, final offline build invocation; 78 core + 6 desktop tests, zero failures/errors/skips. Includes existing regressions, fifteen new core update tests, update UI states and rendered child receipts.
- `./gradlew build` — PASS in the same invocation; configuration-cache-compatible build-info task also verified in separate cached invocations.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` — PASS (2 tests).
- `./gradlew --no-configuration-cache :launcher-desktop:createDistributable` — PASS for Linux x64. Initially missing Compose runtime-probe tooling in the offline cache was fetched through Gradle; subsequent offline packaging passed.
- `scripts/package_update.py` and `:launcher-desktop:verifyUpdateBundle` — PASS on the real approximately 74 MiB Linux archive: staged extraction, all file/link/permission checks and cached-image revalidation.
- `git diff --check` — PASS. Workflow YAML parsed and expected jobs validated locally; hosted Actions execution is not claimed.
- Visual verification — inspected actual Compose startup checking/error renders; compact state/actions tested at 440 × 600. Core tests use isolated temporary state, mocked metadata/downloads and an actual local Java child; they never execute internet-downloaded code.
- Public latest release endpoint — HTTP 404 on 19 October 2026; no public stable release response available. A private/unavailable repo or absent stable release must not be confused with successful live update validation.
- Live GitHub old→new release, actual native child relaunch from a downloaded production release, Windows/macOS packaging/trust/permissions and user-data continuity — NOT RUN; these explicit gates remain open.

### Roadmap updates
- `[x] v0.12 / Self-update / Stable channel, SemVer, startup checks and UI`.
- `[x] v0.12 / Self-update / Shared verified downloads and bounded manifest installation`.
- `[x] v0.12 / Self-update / Atomic side-by-side publication, startup receipt, previous-version preservation/fallback`.
- `[x] v0.12 / Self-update / Build metadata, protocol packager and explicit real-bundle verification`.
- `[ ] v0.12 / Self-update / Hosted tagged release pipeline, live update and native cross-platform acceptance` — remains open.
- `[ ] v0.12 / Production / Release signing/notarization and old-version cleanup policy` — remains open. Full v0.12 is not complete.

### Follow-ups / known issues
- The first build containing the updater must be installed manually; old launcher binaries have no code to discover this feature. Public stable releases with all expected assets/digests are needed for the token-free channel. No release/tag was created.
- Integrity trust is HTTPS plus this project's GitHub publishing account; this is not an independent release signature. Native signing/notarization and actual platform-specific startup acceptance remain required before production claims.
- Previous/orphan app images are intentionally retained; no user/game directory or existing installed executable is deleted or replaced. A startup receipt proves initial rendering, not later gameplay stability.
- Source changes are local and reviewable; existing user Gradle wrapper modifications were not altered or included in this work.

---

## 2026-10-19 — Publish portable applications to Build Explorer

**Milestone:** v0.1 — Build and CI validation (explicit maintainer request)
**Author/Agent:** Codex
**Status:** Completed — CI and all three public downloads verified

### Completed
- Extended the existing three-platform workflow with a dependent publication job.
- Included desktop tests in the verification gate; pull requests never publish.
- Added serial uploads with integrity metadata, shared run identity and strict validation of the complete file set.
- Provisioned the portal project and a repository secret with project-scoped server permissions.

### Changed files
- `.github/workflows/build.yml` — full tests and publication after the complete platform matrix.
- `scripts/publish_build_explorer.py` — bounded, authenticated v2 publisher.
- `tests/test_build_explorer_publish.py` — grouping, channel, integrity, missing/corrupt/oversized input tests.
- `docs/DEVELOPMENT.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — operation and truthful progress.

### Verification
- `./gradlew test --console=plain build` — PASS; existing compiler/deprecation warnings.
- `python3 -m unittest discover -s tests -p 'test_*.py'` — PASS, five tests.
- GitHub PR #9 checks — PASS, core/desktop verification and Windows/macOS/Linux packaging; merged as `422c404cb489449daa52c71ea71f68327b051320`.
- Main CI [39290313808](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39290313808) — PASS, tests, three portable applications and Build Explorer publication.
- Public downloads — PASS, Linux 77,141,160 bytes, macOS 76,868,020 bytes, Windows 70,182,015 bytes; each downloaded SHA-256 matches CI metadata.
- Portal integration — PASS, three-platform grouping, retries, project scope, checksum rollback, retention denial, attachment downloads and desktop/mobile fallback chooser. Native OS gameplay remains outside this CI task.

### Roadmap updates
- `[x] v0.1 / Build and CI validation / Build Explorer publication` — real main pipeline and all three downloads verified.
- Existing native OS acceptance, installer signing and gameplay gates remain open.

### Follow-ups / known issues
- No automatic launcher self-update or native installer work is included.
- Each upload becomes visible individually; interrupted publication is recovered by rerunning its CI job.

---

## 2026-10-19 — Make the launcher logo respond to measured layout space

**Milestone:** v0.1 — UI visual foundation
**Author/Agent:** Codex / GPT-6
**Status:** Completed

### Completed
- Treated the block/arrow as launcher identity with its original asset proportions, rather than an arbitrarily sized decorative overlay.
- Measured the heading/profile before placing the logo in a separate remaining rectangle. Wide heroes reserve the right side; narrow heroes put the logo below the heading and beside the profile.
- Recomputed size and placement on layout changes, fitting both dimensions without cropping or stretching. Short heroes reduce typography and omit secondary tagline copy to preserve essential content.
- Added an accessible logo description and retained the existing separate transparent artwork and restrained separation effects.
- Added a regression using the same live composition across nine size changes at each of 100%, 150% and 200% density. It checks non-zero logo size, original aspect ratio, hero containment and absence of intersections between logo, heading and profile.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherArtwork.kt` — measured responsive layout and intrinsic logo aspect ratio.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherHomeRenderTest.kt` — 27 live-resize/density checks.
- `README.md`, `docs/DESIGN.md`, `launcher-desktop/src/main/resources/branding/README.md` — logo behavior and verification documentation.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — progress and verification record.

### Verification
- `./gradlew test` — PASS, executed as `./gradlew --offline test build`; 63 core + 4 desktop tests, zero failures/errors/skips.
- `./gradlew build` — PASS in the same invocation.
- `git diff --check` — PASS.
- Visual verification — inspected actual updated Compose renders of the complete home at 1440 × 810 and 440 × 740; the mark remains distinct from the scenery and separate from copy.
- Live resize regression — PASS for 27 measured size/density cases, including wide, narrow, short and return-to-wide transitions in the same composition.
- Native OS window dragging and Windows/macOS visual/focus acceptance — NOT RUN; broader platform gates remain open.

### Roadmap updates
- `[x] v0.1 / UI / Measured responsive launcher logo and live-resize/density regression`.
- `[ ] v0.1 / UI / Native keyboard/focus and cross-platform visual acceptance` — remains open.

### Follow-ups / known issues
- Scope is logo sizing/placement and directly related copy layout; no new artwork, animation, launch behavior or persisted format.
- Existing Gradle wrapper modifications and earlier UI changes remain intact.

---

## 2026-10-19 — Match the neon home reference and separate the foreground mark

**Milestone:** v0.1 — home visual foundation; v0.2 — existing instance selector presentation
**Author/Agent:** Codex / GPT-6
**Status:** Completed — requested visual implementation and deterministic render checks; broader native/platform acceptance remains open

### Completed
- Matched the supplied concept with detailed voxel scenery, ink-blue panels, shared cyan outline icons, stronger typography, compact rectangular selectors and a prominent gradient Play action.
- Applied the maintainer's follow-up: the block/orbit/arrow is a separate transparent foreground PNG, with a subtle static navy separation shadow and faint cyan halo. The complete mark fits on resize rather than being cropped with the scenery.
- Used built-in imagegen for the landscape and transparent mark; optimized both as local resources. Recorded final prompts and asset provenance beside the assets. The supplied Library attachment was used only as a reference and was not modified.
- Added a real quick instance dropdown over the existing selection path; preserved the instance manager, account handling, preparation/progress/cancel/stop behavior and shared launch pipeline.
- Moved Microsoft developer-configuration details behind Player information; kept available Microsoft account actions and improved invalid-nickname guidance. Corrected the disabled Play appearance and premium account status label.
- Added compact/stacked layouts, independent setup scrolling and a visible page scrollbar when required. Enforced a 400 × 400 native window minimum; constrained the initial window to the desktop work area.
- Added three deterministic headless Compose tests, including five size/scaling cases, selection interaction, nickname validation and reaching settings by scrolling.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxTheme.kt` — shared palette, typography and geometry.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxComponents.kt` — vector icons, section labels, selectors and accessible channel selection state.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherArtwork.kt` — independently composed scenery/mark with adaptive typography and separation effects.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — reference-style shell, setup controls, Play treatment, quick instance selection and adaptive layout.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — initial window proportions and minimum size.
- `launcher-desktop/src/main/resources/branding/hero-landscape.jpg`, `hero-mark.png`, `README.md` — optimized artwork and provenance/final prompts.
- `launcher-desktop/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/LauncherHomeRenderTest.kt` — isolated metadata-backed render/interaction regression tests and generated QA images.
- `launcher-desktop/build.gradle.kts` — JUnit5/Ktor mock desktop test dependencies and headless test setup.
- `README.md`, `docs/DESIGN.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — visual-system, verification and progress documentation.

### Verification
- `./gradlew test` — PASS, executed as `./gradlew --offline test build`; 63 core and 3 desktop tests, zero failures/errors/skips.
- `./gradlew build` — PASS in the same invocation using the existing local Gradle/JDK cache.
- Initial sandbox execution could not create Gradle's cache lock outside the repository; the authorized run with cache access passed. Pre-existing Gradle wrapper modifications were left untouched.
- `git diff --check` — PASS.
- Visual verification — inspected actual Compose/Skia renders at 1440 × 810, 1024 × 640, 440 × 740, 400 × 400 and 1200 × 900 at 150% density, including scrolled compact views. Verified the logo is separated, remains fully visible, Play fits normal/short layouts, and settings remain reachable.
- Interaction verification — mocked catalog/verified metadata, invalid nickname disables Play, quick instance selection updates profile/RAM, and compact-page scrolling exposes memory controls. All state is temporary; no real accounts or Minecraft downloads were used.
- Native OS keyboard/focus, Windows/macOS visual acceptance and actual Minecraft gameplay — NOT RUN; these broader acceptance gates remain open.

### Roadmap updates
- `[x] v0.1 / UI / Reference-matching artwork, independent mark, shared controls and Play styling`.
- `[x] v0.1 / UI / Deterministic rendering, resizing/scaling and scrolling checks`.
- `[x] v0.2 / UI / Quick home instance dropdown verified through Compose interaction`.
- `[ ] v0.1 / UI / Native keyboard/focus and cross-platform visual acceptance` — remains open.
- `[ ] v0.2 and v0.3 quality gates` — existing real-game/authentication acceptance and external app-approval blockers remain unchanged.

### Follow-ups / known issues
- Future Mods, Resource Packs and global Settings navigation were intentionally not introduced: the corresponding product features are not implemented in the current milestone.
- No launch/authentication/storage schema changes; no Minecraft launch or premium sign-in success is claimed by this visual task.
- Existing Compose resource API deprecation warnings remain; no new rendering dependency or continuously animated effect was added.

---

## 2026-10-19 — Document Mojang AppID allowlist as a v0.3 external blocker

**Milestone:** v0.3 — Microsoft / Premium authentication  
**Author/Agent:** ChatGPT GPT-6  
**Status:** Partial — BLOCKED for live premium authentication pending Minecraft/Mojang AppID approval

### Completed
- Recorded the maintainer's correction: a Microsoft Entra app registration does not itself grant Minecraft Services API authorization.
- Added an explicit dependency on Minecraft/Mojang's approval/allowlisting of SyntaxLauncher's own Client ID, before calling premium sign-in functional.
- Distinguished mocked Microsoft/Xbox/XSTS/Minecraft exchange tests from a live successful Java-profile login.
- Added approval, registration and real-token validation gates to the v0.3 testing checklist.

### Changed files
- `docs/ROADMAP.md` — external AppID approval blocker and release prerequisite.
- `docs/TESTING_V0.3.md` — registration, written approval and live Minecraft Services test steps.
- `docs/WORKLOG.md` — rationale and evidence boundaries.

### Verification
- `./gradlew test` — NOT RUN; documentation-only change.
- `./gradlew build` — NOT RUN; documentation-only change.
- Mojang approval — NOT OBTAINED / NOT VERIFIED.
- Live `login_with_xbox` / Java profile — NOT RUN.

### Roadmap updates
- [ ] v0.3 / P0 — app registration and allowlisting remain blocked externally.
- [ ] v0.3 / premium sign-in — requires approved Client ID and real Java profile test.

### Follow-ups / known issues
- A recent Microsoft Q&A thread reports a Minecraft AppID registration form and an independent developer receiving Mojang Enforcement approval; this report does not establish a guaranteed or currently active approval process.
- Do not use another launcher's registered app identity; the project's own approval is required.

---

## 2026-10-19 — Redact launch plan diagnostics and document premium acceptance

**Milestone:** v0.3
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — security/manual acceptance open

### Completed
- Prevented data-class LaunchPlan.toString() from exposing game arguments containing premium access tokens.
- Added real Java subprocess regression verifying authenticated stdout/stderr are not persisted by the default process manager.
- Added a dedicated v0.3 acceptance checklist for OAuth, account switching, live Minecraft ownership, refresh and sensitive local logs.

### Changed files
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/LaunchPlan.kt
- launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftProcessManagerTest.kt
- docs/TESTING_V0.3.md
- docs/ROADMAP.md and docs/WORKLOG.md

### Verification
- ./gradlew test — CI pending.
- ./gradlew build — CI pending.
- Actual Minecraft Premium game login — NOT RUN.

### Roadmap updates
- [ ] v0.3 process-log review still requires game-managed log/crash-report audit.
- [ ] v0.3 live account/refresh/launch test still requires approved app access.

### Follow-ups / known issues
- OS credential vault for refresh tokens is not yet implemented; sessions remain RAM-only.
- User must provide a registered Microsoft client ID with Minecraft Services authorization.

---

## 2026-10-19 — Wire Microsoft and offline account selection into desktop Play

**Milestone:** v0.3
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — real account consent, OS vault and UI acceptance still required

### Completed
- Added browser-enabled Microsoft account sign-in to DesktopLauncherSession when the independent application ID is configured.
- Added in-memory multiple premium identities, account selection and explicit Sign out; existing Offline nickname stays available.
- Refreshes expiring Microsoft/Minecraft tokens before launching, using the same coordinator and per-instance launch settings.
- Added actionable UI errors; no Microsoft password, refresh token or access token is saved in launcher preferences.
- Updated home UI to v0.3 DEV without presenting the milestone as production-ready.

### Changed files
- launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt
- launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt
- launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt
- docs/ROADMAP.md, docs/WORKLOG.md, README.md

### Verification
- ./gradlew test — CI pending.
- ./gradlew build — CI pending.
- Real Microsoft login/game launch — NOT RUN; requires approved app/client ID and consenting owner.

### Roadmap updates
- [ ] v0.3 browser and session picker have been implemented, but manual acceptance is still pending.
- [ ] v0.3 long-lived OS secure vault and live Microsoft Minecraft launch remain outstanding.

### Follow-ups / known issues
- Premium identities and refresh tokens are intentionally held only in memory until cross-platform secure OS credential storage is available.
- Default ProcessBuilder discards premium stdout/stderr; review game-owned logs before shipping.
- Application ID must be the project's own correctly authorized Microsoft registration.

---

## 2026-10-19 — Native browser login callback foundation

**Milestone:** v0.3
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — no UI or live approved app verification yet

### Completed
- Added public-client system browser sign-in with a single-use IPv4 loopback callback and random-port redirect.
- Verified OAuth state in constant time before accepting the authorization code; ignored unrelated local requests and rejected provider denial.
- Added cancellation-aware listener polling, short per-socket timeouts, overall sign-in timeout, and no OAuth tokens in local callback responses.
- Integrated Microsoft code/refresh with Xbox/XSTS/Minecraft Java profile resolution in the desktop authentication adapter.

### Changed files
- launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopMicrosoftSignIn.kt — browser flow adapter.
- docs/WORKLOG.md — integration notes.

### Verification
- ./gradlew test — CI pending.
- ./gradlew build — CI pending.
- Real user sign-in — NOT RUN (app registration and user consent required).

### Roadmap updates
- [ ] Native browser callback implemented; UI wiring and real application approval outstanding.

### Follow-ups / known issues
- Cross-platform browser callback acceptance on Windows/Linux/macOS required.
- Microsoft app registration must enable native/public client and localhost callback.

---

## 2026-10-19 — Share launch pipeline with credential-bearing sessions

**Milestone:** v0.3
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — desktop sign-in and Minecraft live testing remain

### Completed
- Extended the existing MinecraftLaunchCoordinator to accept a normalized premium LaunchAuthentication payload.
- Marked premium LaunchPlan as credential-sensitive while keeping the offline plan unchanged.
- Prevented raw stdout/stderr files for premium child processes in the default ProcessBuilder starter, avoiding accidental token leakage from redirected output.

### Changed files
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/LaunchPlan.kt
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/LaunchPlanBuilder.kt
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftProcessManager.kt
- docs/ROADMAP.md and docs/WORKLOG.md

### Verification
- ./gradlew test — NOT RUN locally; GitHub CI pending.
- ./gradlew build — NOT RUN locally; GitHub CI pending.
- Real Minecraft authenticated client — NOT RUN.

### Roadmap updates
- [ ] v0.3 premium process log review is partial; game-owned logs still pending.
- [ ] v0.3 launch wiring prepared; account UI/session management pending.

### Follow-ups / known issues
- Raw game-managed local logs and crash reports can still contain tokens and must never be uploaded without sanitization.
- A correctly approved Microsoft application registration is required.

---

## 2026-10-19 — Xbox/XSTS/Minecraft provider exchanges

**Milestone:** v0.3
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — mocked provider pipeline, live approval pending

### Completed
- Added typed Xbox Live, XSTS, Minecraft Services and verified Java profile exchanges.
- Added explicit 403 app-registration and missing Java profile errors, without leaking server bodies or tokens.
- Tested successful and rejected provider sequences via Ktor MockEngine.

### Changed files
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/account/MinecraftPremiumAuthentication.kt
- launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/account/MinecraftPremiumAuthenticationTest.kt
- docs/ROADMAP.md and docs/WORKLOG.md

### Verification
- ./gradlew test — CI pending; no local Gradle cache.
- ./gradlew build — CI pending.
- Real registered Microsoft/Minecraft services — NOT RUN.

### Roadmap updates
- [x] Provider API chain implementation with mock tests.
- [ ] Live approved application authentication and UI remain.

### Follow-ups / known issues
- Invalid app registration can block Minecraft login even when Microsoft and Xbox steps succeed.
- Do not consider authentication finished without live consent/account testing.

---

## 2026-10-19 — First v0.3 Microsoft OAuth milestone

**Milestone:** v0.3
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — first authentication phase

### Completed
- Added validated premium Java account and redacted session type.
- Added native public-client browser PKCE S256, CSRF nonce and Microsoft authorization code/refresh exchange.
- Refused fake offline credentials for premium accounts.
- Added deterministic MockEngine tests for PKCE and OAuth tokens.

### Changed files
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/account/PremiumAccount.kt
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/account/MicrosoftOAuth.kt
- launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/account/MicrosoftOAuthTest.kt
- launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/LaunchModels.kt
- docs/ROADMAP.md and docs/WORKLOG.md

### Verification
- ./gradlew test — NOT RUN locally, pending GitHub Actions.
- ./gradlew build — NOT RUN locally, pending GitHub Actions.
- Live account sign-in — NOT RUN; requires application registration.

### Roadmap updates
- [x] v0.3 premium identity and PKCE request/token exchange.
- [ ] v0.3 Xbox, XSTS, Minecraft Services, account GUI and vault pending.

### Follow-ups / known issues
- Use an independently registered app client ID, never a client secret.
- Refresh tokens remain in memory until a secure OS vault is implemented.

---

## 2026-10-19 — Waive v0.2 manual gate once and begin v0.3

**Milestone:** v0.2 transition / v0.3 Premium authentication
**Author/Agent:** ChatGPT GPT-6
**Status:** Partial — started milestone, without claiming real sign-in completed

### Completed
- Recorded the maintainer's explicit one-time exception to v0.2 manual Minecraft and visual acceptance solely for proceeding to v0.3.
- Kept unperformed v0.2 tests explicitly unverified and preserved the regression checklist.
- Defined v0.3 P0/P1 implementation gates and external Minecraft Services app-registration dependency.
- Updated architectural requirements for a native Microsoft OAuth browser/PKCE flow.

### Changed files
- README.md — milestone transition and one-time waiver.
- docs/ROADMAP.md — v0.3 execution order.
- docs/ARCHITECTURE.md — OAuth/Xbox/Minecraft constraints.
- docs/WORKLOG.md — exception and implementation scope.

### Verification
- ./gradlew test — NOT RUN (documentation-only commit).
- ./gradlew build — NOT RUN (documentation-only commit).
- Manual v0.2 client tests — WAIVED ONCE for v0.3 kickoff, NOT PASSED.

### Roadmap updates
- [ ] v0.2 manual acceptance remains untested, accepted as a one-time risk for progression.
- [ ] v0.3 new phases initiated; no premium release acceptance yet.

### Follow-ups / known issues
- Configure an independent Microsoft public application ID with Minecraft Services authorization.
- Never store Microsoft refresh tokens in plaintext or emit them in game logs.
- v0.3 requires live tests; the one-time waiver applies only to v0.2.

---

## 2026-10-19 — Align preview label and safe default for legacy import

**Milestone:** v0.2 / A3 and release validation  
**Author/Agent:** ChatGPT GPT-6  
**Status:** Partial — real game acceptance pending

### Completed
- Marked the launcher header as `v0.2 PREVIEW` rather than the obsolete `PREVIEW 0.1`.
- Preserved the selected Legacy Vanilla Minecraft version when importing an existing legacy world, avoiding unrequested automatic conversion to the latest release.
- Updated README and the canonical roadmap to clearly distinguish implementation/CI from still-open real Minecraft acceptance.
- Corrected Compose pane declarations after extracting compact/wide responsive UI (follow-up to earlier responsive layout commit).

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — preview marker and legacy version default.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceWorkspace.kt` — corrected extracted local panes (preceding follow-up commit).
- `README.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — release and acceptance status.

### Verification
- Previous implementation's Kotlin core tests and desktop compilation passed CI; final packaging and this documentation commit require the latest CI check.
- Manual real Minecraft gameplay/visual review — NOT RUN, environment has no graphical game client.

### Roadmap updates
- [ ] v0.2 final acceptance remains open for real-world evidence; CI must not be mistaken for game-client validation.

### Follow-ups / known issues
- Perform and record the manual release gates from `docs/TESTING_V0.2.md`, then mark v0.2 complete only if every result passes.

---

## 2026-10-19 — Exercise isolated launch with two real JVM child processes

**Milestone:** v0.2 / A2 and final regression  
**Author/Agent:** ChatGPT GPT-6  
**Status:** Partial — Minecraft game-client acceptance remains pending

### Completed
- Added a deterministic Java source-mode fixture process that writes world and options files to the process working directory.
- Launched the fixture twice via `MinecraftLaunchCoordinator` and the real `MinecraftProcessManager`, selecting two isolated game directories and shared cache paths.
- Asserted that each actual child process populated only its own instance, with no cross-instance worlds or options leakage.
- Used a local/mock artifact response and installed JDK only, with no dependence on Mojang or the availability of a graphical desktop.

### Changed files
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinatorTest.kt` — two-process isolation integration regression.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — updated acceptance evidence.

### Verification
- `./gradlew test` — NOT RUN locally; CI pending.
- `./gradlew build` — NOT RUN locally; CI pending.
- Actual Mojang client world A/B test — NOT RUN (manual release gate).

### Roadmap updates
- [ ] v0.2 / A2 — deterministic JVM child-process test added; real gameplay requirement still open.

### Follow-ups / known issues
- Execute v0.2 manual validation with official game client on a supported desktop before signing off the milestone.

---

## 2026-10-19 — Verify cloned bytes and close lifecycle safety gaps

**Milestone:** v0.2 / A2, A3, A4, B1  
**Author/Agent:** ChatGPT GPT-6  
**Status:** Partial — CI and manual Minecraft acceptance pending

### Completed
- Added SHA-256 read-back verification for every staged copied file, rejecting modified source attributes, mismatched size or hash before publishing a new profile.
- Made missing game directories an explicit inventory error; no silent recreation of apparently empty worlds when profile metadata survives.
- Hardened the preparation-to-running-game PID hand-off under `NonCancellable`: launcher shutdown cannot silently skip recording an active game lease; if hand-off fails, terminate the child conservatively.
- Added a regression for lost game data and expanded manual acceptance instructions.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/InstanceFileCopy.kt` — SHA-256 verification.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — missing game safety.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — non-cancellable lease hand-off.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — missing world test.
- `docs/ROADMAP.md`, `docs/TESTING_V0.2.md`, `docs/WORKLOG.md` — updated gate tracking.

### Verification
- `./gradlew test` and `./gradlew build` — NOT RUN locally; CI pending.
- Manual actual Minecraft A/B and large-world copy/disk failure — NOT RUN.

### Roadmap updates
- [ ] v0.2 / A2–A4, B1 — automated safeguards strengthened, manual acceptance still open.

### Follow-ups / known issues
- Check real-world copy throughput and cancellation on large saves on Windows and Linux.
- Review high-DPI instance layout with actual screenshots; compilation alone is not visual acceptance.

---

## 2026-10-19 — Restore separate legacy preferences and narrow workspace layout

**Milestone:** v0.2 / B1, B2 and B4  
**Author/Agent:** ChatGPT GPT-6  
**Status:** Partial — automatic build and manual GUI acceptance pending

### Completed
- Preserved Legacy Vanilla's independent version, RAM and snapshot channel while users switch to and from named UUID profiles, avoiding accidental overwrites from per-instance preferences.
- Returning to Legacy after trashing the selected profile restores the Legacy controls instead of retaining stale version/RAM state.
- Converted Instances workspace to adaptive single-column (compact) and dual-column (wide) layout with independent scrolling panes.
- Prevented initial launcher/manager window dimensions from forcing a too-wide window on smaller desktops.
- Added preferences restart regression and expanded the end-user acceptance checklist.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — independent Legacy preferences.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceWorkspace.kt` — responsive layout.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — work-area-aware dimensions.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStoreTest.kt` — settings persistence regression.
- `docs/ROADMAP.md`, `docs/TESTING_V0.2.md`, `docs/WORKLOG.md` — acceptance tracking.

### Verification
- `./gradlew test` and `./gradlew build` — NOT RUN locally; CI required.
- Actual resized/different-DPI UI review — NOT RUN; graphical desktop unavailable.

### Roadmap updates
- [ ] v0.2 / B1/B2/B4 — implementation strengthened; user acceptance pending.

### Follow-ups / known issues
- Physical 1080×720 and high-DPI visual review required before release.
- Use disposable test instances, not irreplaceable worlds, to verify deletion/downgrade flows.

---

## 2026-10-19 — Interprocess safety and five-gate regression foundation

**Milestone:** v0.2 (A2, A3, A4, B1–B4)  
**Author/Agent:** ChatGPT GPT-6  
**Status:** Partial — live Minecraft and visual acceptance are not testable in this environment

### Completed
- Introduced per-profile OS advisory file locks spanning entire filesystem mutation, removing the check-then-act race between independent SyntaxLauncher processes.
- Retained process PID/start-time leases across game detach/restart with fail-closed handling of corrupted markers.
- Added filesystem integration regression scenarios: two UUID worlds/options/screenshots, shared caches, reopened repository, trash recovery, cancellation during multi-megabyte import, and a second repository object attempting concurrent access.
- Kept real Minecraft gameplay as a distinct release acceptance gate; mock filesystem tests are not falsely counted as real-client verification.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/InstanceActivityGuard.kt` — interprocess locks and PID marker coordination.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — wrap all destructive mutations in a guarded critical section.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — new regressions for two profiles, cancellation and competing repositories.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — acceptance status and test evidence.

### Verification
- `./gradlew test` — NOT RUN locally; GitHub Actions job required.
- `./gradlew build` — NOT RUN locally; GitHub Actions job required.
- Real Minecraft A/B acceptance — NOT RUN; no graphical Minecraft client/environment available.
- Manual high-DPI keyboard/Windows filesystem acceptance — NOT RUN.

### Roadmap updates
- [ ] v0.2 / A2 — automated A/B world layout improved; actual Minecraft gameplay pending.
- [ ] v0.2 / A3/A4 — cancellation and interprocess protection strengthened; manual failure testing pending.
- [ ] v0.2 / B1–B4 — remains open for end-user review.

### Follow-ups / known issues
- A real offline-mode Minecraft launch and UI inspection on supported desktops are mandatory before calling v0.2 stable.

---

## 2026-10-19 — Replace temporary instance dialog with Instances workspace

**Milestone:** v0.2 / B4, A3, B3  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and visual/manual acceptance pending

### Completed
- Replaced the crowded temporary manager with a spacious, resizable and scrollable workspace using a searchable instance inventory and a focused details/action column.
- Added form-based creation, editing, clone preview, non-destructive legacy import preview, trash recovery and explicit delete confirmation.
- Added estimate/paths/byte+file progress display and direct cancellation for clone and import; the user sees real sizes before copying.
- Fixed streamed copying to retain empty source directories in new instances.
- Kept the main Play action on the home screen; no duplicate launcher engine or early mod features.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceWorkspace.kt` — new manager UI.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceManagerDialog.kt` — removed legacy dialog.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — preview, progress, cancellation and workspace wiring.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — safe location access for preview.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/InstanceFileCopy.kt` — preserve empty directories.
- `docs/ROADMAP.md`, `docs/INSTANCE_MANAGER_PHASES.md`, `docs/TESTING_V0.2.md`, `README.md`, `docs/WORKLOG.md` — updated status.

### Verification
- `./gradlew test` and `./gradlew build` — NOT RUN locally, CI pending.
- Actual Minecraft A/B isolation, visually responsive sizing and tested large-world disk failures — NOT RUN.

### Roadmap updates
- [ ] v0.2 / B4 — dedicated workspace implemented; accessibility and desktop review pending.
- [ ] v0.2 / A3, B3 — preflight/copy progress/cancel wired; manual acceptance remains open.

### Follow-ups / known issues
- Confirm desktop focus order and dialog layout on Windows/Linux/macOS at 1080×720 and high DPI.
- Stress-test cancellations with 10+ GiB saved worlds and hostile filesystem settings; the source is never moved or deleted.
- Confirm detachment locks and corruption recovery with actual Minecraft processes.

---

## 2026-10-19 — Protect instances across launcher detach and restart

**Milestone:** v0.2 / A4  
**Author/Agent:** ChatGPT  
**Status:** Partial — cross-process manual acceptance pending

### Completed
- Added a PID/start-time activity marker stored outside mutable game worlds with fail-closed handling of unreadable markers.
- Claim a profile before Minecraft preparation, atomically hand the lease to the game PID and retain it across launcher close/detach.
- Refuse rename/edit, clone, trash, restore and legacy import when the matching profile is active.
- Surface lease errors in the desktop UI and terminate a newly started game if its lease could not be handed off.
- Added regression checks for active preparation, blocked operations and release on cancellation.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/InstanceActivityGuard.kt` — process markers.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — gate mutation and expose launch lease operations.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — active-state tests.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — lease lifecycle.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — error feedback.
- `docs/ROADMAP.md`, `docs/INSTANCE_MANAGER_PHASES.md`, `docs/TESTING_V0.2.md`, `docs/WORKLOG.md` — evidence and next steps.

### Verification
- `./gradlew test` and `./gradlew build` — NOT RUN locally, CI pending.
- Detached game / restart scenario — NOT RUN manually.

### Roadmap updates
- [ ] v0.2 / A4 — process-activity protection implemented, manual real-game/multiple-launcher acceptance pending.

### Follow-ups / known issues
- PID leases are conservative rather than a kernel-held game process lock. Verify cross-launcher races, unusual process sandbox environments and abrupt termination.
- If a marker is corrupt, recover it manually after confirming the game has exited; do not discard it automatically.

---

## 2026-10-19 — Cancellable staged game data copying (A3/A4 core)

**Milestone:** v0.2 / A3 and A4  
**Author/Agent:** ChatGPT  
**Status:** Partial — desktop operation UI and manual filesystem acceptance pending

### Completed
- Added filesystem preflight with total-byte and file-count estimates, symlink ancestor checks, special-file rejection and available-space checks.
- Replaced opaque `Files.copy` for worlds with buffered, cancellable chunk copying and explicit byte/file progress events.
- Preserved source data and only published UUID profiles after a successful copy; rollback deletes only this operation's staging directory.
- Added tests for progress, estimated size, forced failure rollback and symbolic-link refusal.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/InstanceFileCopy.kt` — validation/estimate/copy services.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — progress-aware clone/import.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — safety tests.
- `docs/ROADMAP.md`, `docs/INSTANCE_MANAGER_PHASES.md`, `docs/TESTING_V0.2.md`, `docs/WORKLOG.md` — updated tracked status.

### Verification
- `./gradlew test` — NOT RUN locally; CI pending.
- `./gradlew build` — NOT RUN locally; CI pending.
- Real world copy and cancelled large-file test — NOT RUN manually.

### Roadmap updates
- [ ] v0.2 / A3 — core validation/streaming finished; end-user operations and real-world acceptance remain open.
- [ ] v0.2 / A4 — source/rollback/disk safeguards improved; process safety pending.

### Follow-ups / known issues
- Bind size estimates and progress to UI; make cancellation user-accessible.
- Track process activity across detached launcher sessions.
- Add an explicit policy for orphaned staging directories after hard shutdown.

---

## 2026-10-19 — Add explicit per-instance settings editor

**Milestone:** v0.2 (B2)  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and manual desktop acceptance pending

### Completed
- Added a dedicated settings dialog for per-instance name, Minecraft version, minimum/maximum RAM and advanced JVM options.
- Kept all editing in a local Compose draft; Cancel cannot write metadata, and Save validates memory, catalog version, name and JVM argument limits.
- Added clear warnings for version changes and possible world damage when downgrading; users must back up worlds before testing older versions.
- Made version/RAM controls on the home screen read-only for non-legacy instances, preventing accidental unsaved overrides. Legacy Vanilla retains its quick settings.
- Routed saving through the existing instance repository, then refresh/reselect the updated instance; no changes to Minecraft launch planner.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceSettingsDialog.kt` — new transactional settings dialog.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceManagerDialog.kt` — launch the editor from selected profile.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — route to settings and disable per-instance quick overrides.
- `docs/ROADMAP.md`, `docs/TESTING_V0.2.md`, `README.md`, `docs/WORKLOG.md` — documented scope and manual verification.

### Verification
- `./gradlew test` — NOT RUN locally, dependencies unavailable (network disabled).
- `./gradlew build` — NOT RUN locally, awaiting GitHub Actions on this commit.
- Manual UI Save/Cancel, downgrade warnings and real-world compatibility — NOT RUN.
- Previous v0.2 recovery commit `62746e7`: CI core tests and desktop compilation PASS; packaging was queued at time of writing.

### Roadmap updates
- [ ] v0.2 / B2 — editor implemented; CI and live user verification still required.

### Follow-ups / known issues
- Provide an explicit launch-time downgrade confirmation for existing worlds, not merely an edit-time warning.
- Replace the remaining dense manager dialog with dedicated instance workspace (B4).
- Verify keyboard/DPI behavior, cancellation and persistence across app restarts.

---

## 2026-10-19 — Add safe instance trash recovery

**Milestone:** v0.2 (B3 / A4 / B1)  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and real Minecraft manual acceptance pending

### Completed
- Implemented read-only trash inventory and in-app Restore, with original UUID, world files and unknown schema-1 metadata preserved.
- Validated trash entry naming and metadata UUID before restoration; reject duplicate destination UUIDs without overwriting or consuming the recoverable copy.
- Report corrupt/unreadable trash entries as non-destructive diagnostics.
- Refused special files while copying worlds, and prevented a new create/clone/import from accidentally reusing leftover operation staging.
- Made duplicate-name instances distinguishable by a short UUID; retained the launcher’s active-game UI mutation lock.
- Added automated recovery, metadata preservation, conflicting UUID and corrupt trash safety tests.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — recovery inventory/restore and staging/file safety.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — recovery regression tests.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — trash loading, error propagation and restore action.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceManagerDialog.kt` — recovery controls and short UUID labels.
- `docs/ROADMAP.md`, `docs/TESTING_V0.2.md`, `README.md`, `docs/WORKLOG.md` — updated status and acceptance instructions.

### Verification
- `./gradlew test` — NOT RUN locally; network-disabled environment lacks Gradle dependencies.
- `./gradlew build` — NOT RUN locally; GitHub Actions CI verification pending.
- Manual two-world gameplay, clone, legacy import, restore and active-game safety — NOT RUN.

### Roadmap updates
- [ ] v0.2 / B3 — restore implementation and tests added; remain open until CI and real-world acceptance.
- [ ] v0.2 / A4 — improved staging and special-file handling; complete disk checks, cancellation and cross-process locks still required.
- [ ] v0.2 / B1 — short UUID differentiator added; restart and missing/corrupt selection UX acceptance remains open.

### Follow-ups / known issues
- Keep deleted data recoverable after corrupted JSON; add explicit guided manual recovery in future UX.
- Add copy progress, cancellation, disk-space verification and crash-resumable cleanup.
- Persist active-instance process protection across launcher restarts/detached games, and verify multiple-process concurrency.
- Complete real Minecraft A/B data-isolation and recovered-world launch tests.

---

## 2026-10-19 — Replace procedural UI hero with approved visual identity

**Milestone:** v0.1 desktop UI / shared v0.2 shell  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and real visual acceptance pending

### Completed
- Compared the approved screenshot against the actual app screenshot: procedural scenery was near invisible, the scaled 128px icon became blurred, and the result lacked the promised cinematic atmosphere.
- Prepared an optimized, text-free hero image from the approved concept, preserving the glowing block and ascending arrow on the right while darkening the left side under real Compose content.
- Replaced the Canvas night scene and oversized icon with a real local bitmap and restrained scrims.
- Refined headline hierarchy, luminous Play CTA, brand header and right-side instance panel.
- Added only implemented navigation: Home is a static current location and Instances opens the existing instance manager. Deferred Mods and Resource Packs as they are not available in this milestone.

### Changed files
- `launcher-desktop/src/main/resources/branding/hero-landscape.jpg` — local concept-derived hero artwork.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — visual treatment, working Instances shortcut.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/HeroAtmosphere.kt` — retired (Canvas no longer used).
- `docs/DESIGN.md`, `README.md`, `docs/ROADMAP.md`, `docs/WORKLOG.md` — design/verification documentation.

### Verification
- GitHub Actions compilation/tests — PENDING.
- Cross-platform distributable packages — PENDING.
- Human visual inspection with actual launcher screenshot — PENDING.

### Roadmap updates
- [ ] v0.1 / UI / faithful cinematic hero — open pending build and manual visual acceptance.

### Follow-ups / known issues
- Compare output against reference at native window size, reduced desktop size and high DPI.
- Keep advanced roadmap navigation hidden until corresponding screens work.

---

## 2026-10-18 — Refine desktop hero and correct UI readability

**Milestone:** v0.1 UI polish / v0.2 shared desktop shell  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and visual desktop review pending

### Completed
- Replaced the decorative empty circle with a layered night-sky voxel landscape and enlarged branded grass-block/arrow mark.
- Added restrained gradient scrims behind text; split hero title into high-contrast white and blue lines.
- Fixed dark-on-dark labels throughout the launcher by setting app-level Material Surface content color.
- Restyled the header, launch card, Play action and instance panel with deliberate borders, spacing and the approved navy/cyan palette.
- Made the initial window size respect the user's desktop work area and added a visible scrollbar plus compact settings spacing and RAM range labels.
- Preserved profile, version, Java, memory, launch, stop and download logic.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/HeroAtmosphere.kt` — deterministic Compose voxel landscape.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — hero, header, contrast and instance sidebar.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — work-area-aware window size.
- `docs/DESIGN.md`, `README.md` — current visual design and responsive behavior.
- `docs/ROADMAP.md`, `docs/WORKLOG.md` — progress tracking and verification.

### Verification
- `./gradlew test` — NOT RUN locally (no network access to Gradle dependencies).
- `./gradlew build` — NOT RUN locally (no network access to Gradle dependencies).
- Cross-platform GitHub Actions and manual screenshot review — pending.

### Roadmap updates
- [ ] v0.1 / UI / premium hero, settings scroll and typography — pending CI plus visual accessibility acceptance.

### Follow-ups / known issues
- The first CI attempt failed on a wrong import for `clip`; corrected in follow-up commit, verification rerun initiated.
- Inspect text wrapping at compact widths and standard/high-DPI scaling.
- Verify all three portable distributions and manually review the loaded/launching/error states.

---

## 2026-10-18 — Apply blue SyntaxLauncher brand and application logo

**Milestone:** v0.1 UI / shared v0.2 desktop shell  
**Author/Agent:** ChatGPT  
**Status:** Partial — CI and GUI review pending

### Completed
- Derived a small transparent launcher PNG from the approved blue Minecraft grass block / orbital arrow artwork.
- Replaced the generic letter S in the title header with the logo, added a desktop window icon and configured Linux package icon.
- Consolidated dark navy, cobalt and cyan accent colors in Material 3 theme and hero tokens.
- Aligned the hero/background/labels/dividers, kept errors semantic and made offline status intentionally subdued.
- Documented palette roles and asset usage.

### Changed files
- `launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.png` — optimized product artwork.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxTheme.kt` — design tokens.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — icon and blue UI.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — window icon.
- `launcher-desktop/build.gradle.kts` — Linux package icon.
- `docs/DESIGN.md`, `docs/ROADMAP.md`, `README.md` — new brand rules and tracking.
- `docs/WORKLOG.md` — this entry.

### Verification
- `./gradlew test` — NOT RUN, local Gradle dependency access unavailable.
- `./gradlew build` — NOT RUN, local Gradle dependency access unavailable.
- Manual desktop review — NOT RUN, pending test builds.
- PNG Git blob SHA matched the locally generated optimized asset before integration.

### Roadmap updates
- [ ] v0.1 / UI / blue branding remains open until CI and manual screen review.

### Follow-ups / known issues
- Confirm cross-platform compilation and package startup in GitHub Actions.
- Windows and macOS installer branding would need separate ICO/ICNS assets at release time; PNG is used for the Compose window on all platforms.

---

## 2026-10-18 — Stage v0.2 instance isolation and management delivery

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Completed (planning/documentation only; implementation gates remain open)

### Completed

- Reviewed the current v0.2 repository, tests, LaunchCoordinator integration and Compose instance manager against AGENTS.md, milestone scope, design rules and previous CI evidence.
- Created a single, concrete two-track implementation plan: Track A for separate Minecraft instances (identity, isolated game storage, shared cache, legacy import and safety); Track B for instance management (creation/selection, per-instance settings, clone/trash/restore and a usable Compose workspace).
- Defined phase dependencies, P0/P1 priorities, in-code baseline versus missing behavior, acceptance tests, file/data safety invariants and intentionally deferred features.
- Identified remaining gaps without claiming they exist already: automatic import copy progress/cancellation, safety around active/detached processes, in-app trash restore, fully editable JVM settings, duplicate-name disambiguation and UI acceptance testing.
- Preserved existing implementation and recorded the real-game isolation gate separately from the previously green 4/4 CI results.

### Changed files

- `docs/INSTANCE_MANAGER_PHASES.md` — detailed two-track phased plan, criteria and completion gates.
- `docs/ROADMAP.md` — new prioritized v0.2 checklist linked to the plan.
- `README.md` — direct link to phased v0.2 plan.
- `docs/WORKLOG.md` — this entry.

### Verification

- Existing GitHub Actions run `39157681455` — historical PASS, 4/4 jobs for the current v0.2 implementation baseline; no new code was added in this change.
- `./gradlew test` — NOT RUN; documentation-only change.
- `./gradlew build` — NOT RUN; documentation-only change.
- Manual gameplay acceptance — NOT RUN; two-world isolation/clone/legacy import remain separate open gates.

### Roadmap updates

- Added track A/B scoped, ordered tasks with P0/P1 and specific practical release gates; no newly completed boxes were checked.
- Existing implemented code/test milestones retain their verified status and v0.2 remains in progress.

### Follow-ups / known issues

- Start with A2's real two-world acceptance and B2's complete instance settings editor, then harden A3/A4's non-destructive operations.
- Implement in-app restore and a dedicated Instances UI before release.
- Update the roadmap/worklog and retain red/green evidence as each implementation phase is finished.

---

## 2026-10-18 — Confirm green v0.2 build and publish manual acceptance checklist

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Verified GitHub Actions run `39157681455` at commit `7aa5605`: all four jobs PASS (core unit/integration tests, desktop compilation, Linux, Windows and macOS application distributions).
- Verified successful upload of three platform-specific preview archives and a test-results artifact.
- Recorded newly verified v0.2 repository, schema, per-instance directories and launch layout integration tasks in the canonical roadmap.
- Added explicit two-world isolation, clone/rename/trash, legacy import and restart-persistence manual testing checklist.
- Updated README with CI and v0.2 test guide links.

### Changed files
- `docs/ROADMAP.md` — checked only CI-tested individual items; manual acceptance remains open.
- `docs/WORKLOG.md` — this verification entry.
- `docs/TESTING_V0.2.md` — manual test guide.
- `README.md` — test guide and CI link.

### Verification
- GitHub Actions `39157681455` — PASS 4/4 jobs.
- `:launcher-core:test` / `:launcher-desktop:compileKotlin` — PASS in CI.
- `:launcher-desktop:createDistributable` — PASS on Linux x64, Windows x64 and macOS ARM64.
- `./gradlew build` — NOT RUN locally, full aggregate build task not requested by CI.
- Manual Minecraft game-state isolation, large-world cloning, legacy import and trash recovery — NOT RUN in this environment.

### Roadmap updates
- Tested v0.2 domain, schema storage, per-instance paths, preferences and launch layout items marked `[x]`.
- UI usability and v0.2 real-game quality gates remain `🚧` until manual validation.

### Follow-ups / known issues
- Test two instances with the same Minecraft version and separate worlds using the downloadable preview.
- Verify existing v0.1 game directory is preserved across import.
- Add an in-app trash restore path and explicit schema migration when the schema evolves.

---

## 2026-10-18 — Harden restored instance selection, clone metadata and launch tests

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- CI run `39157356664` successfully compiled the new instance core/desktop implementation and passed launcher-core tests; platform packages were still in progress.
- Prevented silently launching Legacy Vanilla if a saved selected instance cannot be found.
- Restored the selected instance's Minecraft version and RAM settings after asynchronous inventory loading.
- Preserved unknown schema-1 JSON properties while cloning an instance, in addition to updates.
- Added an integration assertion for the actual LaunchPlan working directory when a per-instance layout is supplied.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — copy unknown instance metadata during clone.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — verify copied unknown JSON field.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinatorTest.kt` — assert instance-specific working directory and shared cache layout.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — restore profile settings and reject missing-profile fallback.
- `docs/ROADMAP.md` and `docs/WORKLOG.md` — track changes and verification.

### Verification
- Prior CI verification for initial v0.2 commit: PASS (core tests + desktop Kotlin compile).
- `./gradlew test` — follow-up CI pending, not run locally.
- `./gradlew build` — follow-up CI packaging pending, not run locally.
- Manual Minecraft two-instance isolation: NOT RUN.

### Roadmap updates
- Instance launch and persistence improvements still in progress pending follow-up CI and live user testing.

### Follow-ups / known issues
- Inspect full CI results, fix any Kotlin/UI/test problems.
- Confirm world isolation, import and trash behavior in actual Minecraft sessions.

---

## 2026-10-18 — Implement first v0.2 Instance Manager vertical slice

**Milestone:** v0.2 — Instance Manager  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftInstance` with immutable UUID, schema version, display name, resolved Minecraft version, RAM and optional JVM arguments.
- Added a core repository with atomic JSON writes, unknown metadata field preservation, strict schema checks and per-profile error inventory.
- Each instance stores mutable worlds/config/resources under a private UUID-named game directory; Mojang libraries/assets/versions and Java remain in existing shared cache directories.
- Added create/list/update/rename/clone/trash and explicit non-destructive v0.1 legacy game import operations.
- Clone stages worlds and settings before publication, rejects symlink entries and never deletes the source. Delete moves instance directory into recoverable `instance-trash`.
- Added a per-call instance layout to the existing MinecraftLaunchCoordinator, preserving default legacy behavior.
- Extended desktop session's Play to use an optional instance and per-instance JVM/RAM options with the same core launch pipeline.
- Added selected-instance UUID to existing launcher preferences with a backwards-compatible optional field.
- Added a simple Compose Instance Manager dialog for selecting, creating, renaming, cloning, updating, importing v0.1 worlds and confirming trash operations.
- Added five repository test cases for isolation/shared caches, clone/restore, metadata changes/unknown fields, legacy import and invalid paths.

### Changed files

- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepository.kt` — domain model and storage.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/instance/MinecraftInstanceRepositoryTest.kt` — storage/isolated worlds tests.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt` — optional per-instance game layout.
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/storage/LauncherPreferencesStore.kt` — selected-instance UUID persistence.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — repository dependency wiring.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — per-instance game launch.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/InstanceManagerDialog.kt` — CRUD Compose dialog.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — selection/management and primary Play target.
- `docs/ROADMAP.md`, `docs/WORKLOG.md`, `README.md` — status, changelog and user notes.

### Verification

- Source review completed for instance-specific mutable game directories and non-destructive operations.
- `./gradlew test` — awaiting CI run of new tests.
- `./gradlew build` — awaiting CI compile and platform distributable matrix.
- Manual two-instance Minecraft world test — NOT RUN; user acceptance required.

### Roadmap updates

- Core, storage, management, UI and launch integration marked `🚧 IN PROGRESS` until CI and manual verification.
- v0.2 quality gates remain open; no claim that v0.2 is already a stable completed release.

### Follow-ups / known issues

- Confirm CI build/test and fix any compiler/test errors before providing preview.
- Perform real-world clone, trash, legacy import and cache-sharing acceptance tests.
- Provide in-app trash restore and schema migration when a new schema is actually introduced.
- Maintain the old v0.1 game folder until explicit import, never remove it during this milestone.

---

## 2026-10-18 — Fix Kotlin Compose when-expression compilation

**Milestone:** v0.1 — UI polish  
**Status:** Partial

- CI run 39153778139: core tests passed; desktop compilation identified trailing commas after `else` expressions in the redesigned Compose home.
- Removed invalid trailing commas from UI `when` expressions without changing launch/runtime behavior.
- Changed `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` and this worklog.
- Verification: CI compiler diagnostics reviewed; follow-up run pending, no manual visual inspection yet.
- Roadmap remains in progress until Gradle and visual smoke acceptance.

---

## 2026-10-18 — Introduce SyntaxLauncher premium-feel home UI

**Milestone:** v0.1 — Vanilla Launcher UX polish  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Replaced the original two dense configuration cards with a visually distinct hero and streamlined right-side setup rail.
- Created a minimal branded header/footer, focused mint-on-deep-slate palette, strong headline hierarchy and atmospheric gradient surface without remote image dependencies or constant animation.
- Made Play the singular prominent action and kept download progress, cancellation, runtime state and stop behavior adjacent to it.
- Kept nickname, release/snapshot channel, version selection and RAM controls with the existing persisted state, metadata validation and launch coordinator.
- Reduced technical metadata during normal use; exposed failure details and logs conditionally.
- Preserved async catalog/loading/retry handling and existing process lifecycle.

### Changed files
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt`
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxTheme.kt`
- `docs/ROADMAP.md`
- `docs/WORKLOG.md`
- `README.md`

### Verification
- UI source reviewed for existing callback/state wiring; complete Gradle CI and actual desktop appearance/testing pending.
- No core launch pipeline changes; previous CI green on v0.1 before redesign.
- Manual multi-resolution/DPI keyboard/focus assessment pending.

### Follow-ups
- Fix any compiler errors reported by cross-platform GitHub CI.
- Review screen visually on desktop at 1080×720 and narrow/min-size windows, keyboard navigation and scaling.
- Consider logo/visual refinements only after GUI screenshots and feedback.

---

## 2026-10-18 — Confirm all three v0.1 preview artifacts and green CI

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Confirmed GitHub Actions run `39125171338` finished with all four jobs SUCCESS: core tests plus desktop compile, Linux x64, Windows x64, macOS ARM64 `createDistributable` packaging.
- Verified Actions uploaded archive-wrapped preview app images, preserving Unix executable bits/symlinks: Windows artifact `12135981958`, Linux `12136131266`, macOS `12136425435`; also test-results artifact `12135961632`.
- Confirmed official Gradle wrapper commit `03d2650`, Kotlin compile/test repairs and schema-versioned preferences sources are on main.
- Synchronized roadmap acceptance status and documented the difference between CI-verifiable packages and an actual clean-machine Minecraft launch.

### Changed files

- `docs/ROADMAP.md` — mark CI build/packaging and official Gradle wrapper complete, keep full v0.1 game-run quality gates open.
- `docs/WORKLOG.md` — this verification entry.
- `README.md` — links to the verified packaging run and preview test instructions.

### Verification

- GitHub Actions `39125171338`: 4/4 jobs PASS, `:launcher-core:test` PASS, desktop Kotlin compile PASS, Windows/Linux/macOS portable app packaging PASS.
- Official wrapper generation `39123801774`: PASS.
- Actual game launch, fresh Mojang Java install, second-run cache test and GUI smoke on users' operating systems: NOT RUN.
- Full `./gradlew build` and local `./gradlew test` not executed in this network-isolated container; equivalent CI Gradle tasks reported above are PASS.

### Roadmap updates

- CI test, package matrix, official wrapper and identified compiler/build blockers checked complete.
- Full v0.1 release acceptance remains `🚧` until a real vanilla launch, platform smoke and cache reuse are demonstrated.

### Follow-ups / known issues

- Have a tester extract a preview build and complete `docs/TESTING_V0.1.md` with real-world game launch evidence and sanitized logs.
- Address any runtime, Java-provider, account/profile or packaging issues identified during the first Minecraft launch.
- Later add signed native installers, offline catalog caching and expanded platform acceptance.

---

## 2026-10-18 — Harden portable artifacts and document v0.1 preview testing

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Confirmed GitHub Actions run `39124788473` passed core tests, desktop compile and platform Compose app-image creation on Linux and macOS; Windows job passed its build and entered cleanup.
- Changed CI to put the complete Compose app image into a platform-specific tar.gz archive before uploading GitHub Actions artifacts, retaining POSIX executable bits and macOS symlinks that upload-artifact ZIP would otherwise normalize.
- Included the JDK `jdk.crypto.ec` module explicitly in the jlinked native distribution for JSSE HTTPS/TLS elliptic curve support.
- Added v0.1 manual test guide for Windows/Linux/macOS preview artifacts, cache behavior, Java auto-install, game launch, logs, known limitations and bug reporting.
- Clarified README instructions for extraction and unsigned preview-build status.

### Changed files

- `.github/workflows/build.yml` — create permission-preserving platform archives before artifact upload.
- `launcher-desktop/build.gradle.kts` — include jdk.crypto.ec in native app runtime.
- `docs/TESTING_V0.1.md` — manual acceptance/test guide.
- `README.md` — preview artifact extraction and testing links.
- `docs/ROADMAP.md` — portability/crypto task tracking.
- `docs/WORKLOG.md` — this entry.

### Verification

- Previous CI `39124788473`: core tests PASS, desktop compile PASS, Linux/macOS image creation PASS, Windows image creation PASS; archive-format changes pending next run.
- `./gradlew test` / `./gradlew build` — not executed locally in isolated container; CI runs equivalent test/build tasks.
- Manual clean-machine vanilla Minecraft launch — NOT RUN; no complete v0.1 release acceptance claim.

### Roadmap updates

- Portable artifact archive integrity and jlink TLS module task marked `🚧 IN PROGRESS` pending final CI and manual run.
- v0.1 quality gate remains open.

### Follow-ups / known issues

- Verify all three CI tar.gz preview artifacts can be extracted and launched.
- Real Minecraft launch and second-launch cache test required.
- Add native signed installers only after portability/game acceptance.

---

## 2026-10-18 — Repair macOS jpackage version and add v0.1 settings persistence

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Parsed detailed macOS jpackage error from Actions run `39124386892`: macOS app bundle rejects app-version with leading zero in first component.
- Set the technical native packageVersion to 1.0.0 to satisfy jpackage while retaining the product milestone v0.1; documented the difference.
- Implemented `LauncherPreferencesStore` with schema 1, atomic writes, safe validation, retention of unknown property keys and rejection of future-schema files without overwriting.
- Saved v0.1 non-sensitive settings only: offline nickname, selected Minecraft version, release/snapshot filter and RAM allocation.
- Connected preferences to desktop session and Compose state restoration. Debounced writes occur outside Compose, and window close attempts to flush the last pending settings.
- Added tests for settings round-trip, forward unknown-property retention and newer-schema preservation.

### Changed files

- `launcher-desktop/build.gradle.kts` — technical macOS-compatible package version.
- `launcher-core/src/main/kotlin/.../storage/LauncherPreferencesStore.kt` — settings persistence.
- `launcher-core/src/test/kotlin/.../storage/LauncherPreferencesStoreTest.kt` — settings validation tests.
- `launcher-desktop/src/main/kotlin/.../desktop/DesktopLauncherSession.kt` — debounced saves and close flushing.
- `launcher-desktop/src/main/kotlin/.../desktop/Main.kt` — storage wiring.
- `launcher-desktop/src/main/kotlin/.../desktop/ui/SyntaxLauncherApp.kt` — startup restore and persistence.
- `docs/ROADMAP.md` — settings/packaging status.
- `docs/WORKLOG.md` — this entry.
- `README.md` — settings format and jpackage note.

### Verification

- macOS jpackage failure: CONFIRMED from workflow error log, quoting the leading-zero version restriction.
- Linux/Windows packages and core tests: PASS in previous Actions run, before these changes.
- New v0.1 settings test sources added; `./gradlew test` and `./gradlew build` not executed in network-isolated local container. New CI run pending.
- Real GUI/Minecraft manual launch: NOT RUN.

### Roadmap updates

- Preferences and macOS packaging fix now `🚧 IN PROGRESS` pending CI and real platform acceptance.
- v0.1 acceptance gates remain open until a real Minecraft launch is verified.

### Follow-ups / known issues

- Verify Gradle tests and all three portable Compose distributions succeed with the corrected macOS app version.
- Run a real new-install Minecraft start using a generated app distribution.
- Test fresh start/restart persistence and cached second launch on supported desktop platforms.
- Add reliable manifest caching and improve first-run offline/error recovery.

---

## 2026-10-18 — Confirm test and Windows/Linux packages; capture macOS jpackage logs

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Verified Actions run `39123949155` completed the core tests and desktop Kotlin compilation successfully.
- Confirmed Windows and Linux Compose `createDistributable` job success and published platform-specific portable distribution artifacts.
- Confirmed the restricted wrapper bootstrap workflow `39123801774` succeeded and committed `gradlew`, `gradlew.bat`, official `gradle-wrapper.jar` and properties to main as commit `03d2650`.
- Identified remaining macOS failure at `:launcher-desktop:createDistributable` after both core and desktop Kotlin compiled; the underlying jpackage output was written to a local build log not included in the original Actions job output.
- Updated packaging workflow to print and attach Compose/jpackage logs when packaging fails.
- Updated README to document local test/run commands and portable distribution outputs.

### Changed files

- `.github/workflows/build.yml` — jpackage diagnostic log printing and upload on failure.
- `README.md` — verified wrapper and build instructions.
- `docs/ROADMAP.md` — reflect green verify/Windows/Linux jobs, wrapper and macOS packaging pending.
- `docs/WORKLOG.md` — this entry.

### Verification

- GitHub Actions run `39123949155`: core tests PASS; desktop Kotlin compile PASS; portable Linux/Windows app creation PASS; macOS app creation FAIL at jpackage.
- Official Gradle wrapper generation workflow `39123801774`: PASS; all expected wrapper files confirmed on main.
- `./gradlew test` / `./gradlew build`: not executed locally in the network-isolated container. Actions executes equivalent Gradle test and distribution tasks.
- Actual Minecraft game start on a desktop machine: NOT RUN.
- macOS jpackage diagnostic rerun: pending new CI run.

### Roadmap updates

- CI verification and Windows/Linux packaging now source-verified passing; remaining macOS/real-game acceptance open.
- Wrapper exists; remaining quality gates not marked complete until full build and real-game verification.

### Follow-ups / known issues

- Read jpackage error log from the next macOS packaging job and implement a targeted fix.
- Complete v0.1 settings persistence and manual launch instructions.
- Verify packaged app starts Minecraft and runs on supported platforms.

---

## 2026-10-18 — Repair malformed launch integration test source

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Analyzed GitHub Actions run `39123801750`: production launcher-core and launcher-desktop code compiled, but integration test source was corrupted by a string replacement.
- Recreated `MinecraftLaunchCoordinatorTest.kt` from the last known intact revision, retaining actual Kotlin literal placeholder escaping via `\$` characters.
- Removed the malformed duplicated source fragments rather than attempting incremental repairs.

### Changed files
- `launcher-core/src/test/kotlin/.../launch/MinecraftLaunchCoordinatorTest.kt` — restore tests and valid literal Minecraft arguments.
- `docs/WORKLOG.md` — this entry.

### Verification
- Previous Actions run: production core and desktop Kotlin compiled; test source compilation failed due malformed placeholder string.
- Tests/build: awaiting full CI rerun with corrected source.
- Live Minecraft: NOT RUN.

### Roadmap updates
- CI remains IN PROGRESS pending test and packaging pass.

### Follow-ups / known issues
- Inspect JUnit results after source is compiled.
- Verify platform app package jobs.
- Verify official Gradle wrapper bootstrapped by workflow.

## 2026-10-18 — Fix desktop Ktor dependency and launch test interpolation; bootstrap wrapper

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Parsed Actions run `39123629786`: `launcher-core:compileKotlin` passed, while desktop compilation failed because `DesktopLauncherSession` references Ktor `HttpClient` and the desktop module lacked a direct dependency.
- Added explicit desktop `ktor-client-core` implementation dependency.
- Corrected two test strings in `MinecraftLaunchCoordinatorTest` to preserve literal Minecraft placeholders rather than using Kotlin variable interpolation.
- Added restricted `bootstrap-gradle-wrapper.yml` job to generate the official Gradle 9.4.0 wrapper from a pinned distribution, verify it, and commit scripts/JAR/properties back to main as the GitHub Actions bot. The change only runs on main when the workflow itself is introduced or manually dispatched.
- Kept shared GitHub Actions compile/test/portable packaging workflow separate from one-time wrapper bootstrap.

### Changed files

- `launcher-desktop/build.gradle.kts` — add Ktor core API to the desktop compile classpath.
- `launcher-core/src/test/kotlin/.../launch/MinecraftLaunchCoordinatorTest.kt` — literal placeholder escaping.
- `.github/workflows/bootstrap-gradle-wrapper.yml` — restricted official wrapper bootstrap.
- `docs/ROADMAP.md` — updated build milestones.
- `docs/WORKLOG.md` — this entry.

### Verification

- Actions `39123629786`: core compilation PASS, desktop compilation and core test source compilation FAIL with the diagnosed missing Ktor dependency/literal placeholder errors.
- `./gradlew test` and `./gradlew build`: NOT RUN locally; awaiting wrapper/bootstrap and GitHub Actions rerun.
- Native packaging/real Minecraft: NOT RUN.

### Roadmap updates

- Gradle wrapper generation in progress, all v0.1 build tests/packaging remain incomplete until successful Jobs.

### Follow-ups / known issues

- Inspect wrapper bootstrap execution and confirm official JAR is committed.
- Repair any compiler/test errors from the next full CI run.
- Verify portable artifacts for Linux, Windows and macOS.
- Add version/nickname/RAM persistence and real-Minecraft acceptance testing.

---

## 2026-10-18 — Fix Minecraft classpath Kotlin compilation

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed
- Read GitHub Actions run `39123484537` and isolated Kotlin compilation errors at `MinecraftFilePlan.kt:58` and `:77`.
- Replaced ambiguous `MutableList<Path> += Path` with `MutableList.add(path)`, fixing Kotlin overload resolution where `Path` is also `Iterable<Path>`.

### Changed files
- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/minecraft/MinecraftFilePlan.kt` — classpath addition.
- `docs/WORKLOG.md` — this entry.

### Verification
- Actions run `39123484537`: Gradle resolved external dependencies; Kotlin core compilation failed in two identified lines.
- `./gradlew test` / `./gradlew build`: NOT RUN locally because wrapper/network access is unavailable.
- New CI run: pending.

### Roadmap updates
- No acceptance box checked; compilation, tests and package readiness remain in progress.

### Follow-ups / known issues
- Examine next compiler/test failures and fix until the CI verification stage passes.
- Verify native desktop packages and official wrapper.

## 2026-10-18 — Fix Compose dependency resolution in CI

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Read first GitHub Actions build `39123291984` failure: eleven unresolved `androidx.*` dependencies, including lifecycle, savedstate, runtime and annotations.
- Root cause was missing Google Maven in Gradle dependency and plugin repositories.
- Added `google()` to both dependency and plugin repository declarations; no speculative Kotlin or Compose version downgrade.

### Changed files

- `settings.gradle.kts` — Google Maven resolution.
- `docs/ROADMAP.md` — running build/CI progress and blocker recorded.
- `docs/WORKLOG.md` — this entry.

### Verification

- Original Actions log — FAIL: module resolution due to missing Google Maven.
- `./gradlew test` and `./gradlew build` — NOT RUN locally (no wrapper/network); subsequent GitHub Actions rerun is pending.
- Real Minecraft launch — NOT RUN.

### Roadmap updates

- CI stays `🚧 IN PROGRESS`, dependency resolution fix requires Actions verification.

### Follow-ups / known issues

- Recheck CI logs for compiler and test errors after Maven repository resolution.
- Continue platform packaging and official wrapper generation.

---

## 2026-10-18 — Introduce cross-platform build and packaging CI

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added first GitHub Actions workflow running Kotlin core tests and desktop compilation with pinned Gradle 9.4.0 / Temurin JDK 21.
- Added a Linux/Windows/macOS portable desktop distribution matrix using Compose `createDistributable`.
- Added automatic upload of zipped portable application directories and test report artifacts for GitHub Actions build runs.
- Preserved read-only workflow permissions and concurrency cancellation for duplicate runs.
- Verified Kotlin 2.4.21 and Compose 1.12.1 plugin releases exist on official plugin portals; avoided speculative version changes.
- Identified missing official Gradle wrapper as a separate reproducibility task; CI installs Gradle through a pinned trusted GitHub Action until the official wrapper jar can be generated.

### Changed files

- `.github/workflows/build.yml` — verify/tests and portable app matrix packaging workflow.
- `docs/ROADMAP.md` — build/CI and acceptance tasks.
- `docs/WORKLOG.md` — this entry.
- `README.md` — CI workflow and artifacts instructions.

### Verification

- GitHub repository check — no prior workflows or workflow runs before this change.
- Gradle plugin versions checked against official plugin registries.
- `./gradlew test` — NOT RUN: repository did not contain a wrapper; this container cannot resolve GitHub/Maven URLs.
- `./gradlew build` — NOT RUN: same blocker.
- Workflow build/packaging — pending first GitHub Actions run; no verified distribution claimed.

### Roadmap updates

- CI and platform distributions `🚧 IN PROGRESS` pending real Actions job success.
- Official Gradle wrapper generation and v0.1 real-game launch remain open.

### Follow-ups / known issues

- Inspect the first CI errors and fix compilation/test/runtime problems before marking jobs complete.
- Generate and commit the official Gradle wrapper artifacts in a build-capable environment.
- Run a full packaged-app launch and verify Minecraft gameplay.
- Test platform-specific packaging/installers and verify executable resources included by jpackage.

---

## 2026-10-18 — Wire complete Minecraft launch pipeline into Compose Desktop

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftLaunchCoordinator` in launcher-core, sequentially performing prepared version → game files/asset index/assets/natives → required managed Java → structured LaunchPlan → ProcessBuilder launch.
- The version metadata is already resolved and SHA-1 verified by the version picker before Play; pressing Play reuses this resolved metadata rather than performing an unnecessary second metadata request.
- Added explicit phases IDLE/PREPARING_GAME/PREPARING_JAVA/BUILDING_PLAN/STARTING_GAME/STARTED/FAILED/CANCELLED and a StateFlow for current detail, download counts and per-file progress.
- Mapped core MinecraftPreparationEvent, JavaRuntimeEvent and DownloadEvent into user-visible progress stages.
- Guarded parallel Play requests with a core Mutex plus session ownership; missing Java requirement is reported before starting a large game download.
- Added a narrow, dedicated `DesktopLauncherSession` owning the coroutine scope, shared HTTP lifecycle and cancellation independently of Compose recomposition.
- On close, cancel and join any preparation job, detach from a running child process, then close the Ktor client and exit the app on the Swing dispatcher.
- Updated the Compose main screen with a real enabled/disabled Play button and Stop action; nickname, Minecraft version and RAM are forwarded to the core pipeline.
- The UI now displays preparation/Java file progress, retry for metadata fetch, process running/stopped/crashed status, PID, exit code, log path, cancellation and failure summaries.
- Controls are disabled during preparation and when the game is running; existing offline-play warning and restrained dark visual identity were preserved.
- Added controlled test sources covering a synthetic Mojang client download + offline LaunchPlan + fake Java provider + local Java child-process launch, and early failure when the Java requirement is absent.
- Kept login/premium/mod features out of v0.1, and did not claim a real Minecraft launch before acceptance tests.

### Changed files

- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinator.kt` — launch orchestration and shared typed StateFlow progress.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftLaunchCoordinatorTest.kt` — controlled end-to-end-style pipeline test sources.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/DesktopLauncherSession.kt` — coroutines, launch cancellation, process detachment and client shutdown.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/Main.kt` — composition root / dependency wiring.
- `launcher-desktop/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/ui/SyntaxLauncherApp.kt` — actual Play/Stop, progress/error/status UI and control locking.
- `docs/ROADMAP.md` — v0.1 process/launch/UI tracker synchronized.
- `docs/WORKLOG.md` — this entry.
- `README.md` — project overview updated to describe the actual Compose pipeline.

### Verification

- GitHub file/API review — source interfaces for catalog, metadata, preparation, Java Manager, LaunchPlan, ProcessManager, downloader and Compose dependencies checked before integration.
- Source/static review — phase ordering, cancellation lifecycle, no Compose dependency inside launcher-core, stable session ownership and supported offline scope reviewed.
- Deterministic integration-style test sources added with Ktor MockEngine and local Java child process; actual Gradle/JUnit execution NOT RUN.
- `./gradlew test` — NOT RUN; no Gradle wrapper is committed and container DNS/dependency resolution is unavailable.
- `./gradlew build` — NOT RUN for the same reason.
- Real Minecraft/Compose GUI manual launch — NOT RUN. Play exists in the source but may require fixes after first full build and live test.

### Roadmap updates

- Compose Play, Stop, runtime/download status, progress, cancellation, control locking, process status and close lifecycle now `🚧 IN PROGRESS` pending build and live acceptance.
- UI RAM now feeds actual LaunchOptions; process and Java stages are wired to desktop through a core coordinator.
- v0.1 quality gates remain unchecked; persistence, manifest caching, first real-game launch and supported-platform checks remain open.

### Follow-ups / known issues

- Commit a Gradle wrapper, run test/build and resolve any Kotlin/Compose library API mismatches identified by a real build.
- Perform a fresh Mojang vanilla install and launch on supported desktop platforms; verify current official Java runtime metadata/downloads and stdout/stderr behavior.
- Persist nickname, selected Minecraft version and RAM, and cache version manifest with safe fallback/error recovery.
- Before v0.3 premium authentication, replace raw child-process log redirection with a token-safe logging design.

---

## 2026-10-18 — Implement Minecraft process lifecycle management

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftProcessManager` to launcher-core using the existing, already-resolved `LaunchPlan` without hardcoded Minecraft launch recipes.
- Launched native child processes with `ProcessBuilder`, the resolved Java executable, structured command arguments, and the prepared game working directory.
- Added distinct per-run Minecraft log directories under launcher logs, with separate operating-system redirected stdout/stderr files. POSIX files are created with owner-only permissions.
- Added `StateFlow<MinecraftProcessStatus>` with IDLE, STARTING, RUNNING, STOPPED, CRASHED and DETACHED states, timestamps, PID, log paths, error summary and exit code.
- Added mutex-guarded single-session ownership, rejecting accidental duplicate starts and releasing the slot after exit.
- Added natural exit monitoring through `Process.onExit()`, distinguishing code-zero exits, unexpected nonzero exits and user-requested termination.
- Added graceful termination with a configurable timeout and forced termination fallback.
- Added explicit launcher-shutdown policy: `DETACH` preserves the child and native log redirects, `TERMINATE` stops the child.
- Added typed start, duplicate, closed-manager and termination failures without logging the command line or any launch credentials.
- Added a narrow injectable process-starter seam for deterministic tests; no Compose dependency.
- Added five controlled process scenarios covering a real JDK subprocess, logs, duplicate protection, crash exit code, relaunch, forced stop, detach and startup-error recovery.

### Changed files

- `launcher-core/src/main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftProcessManager.kt` — process start/exit/stop/detach lifecycle, logging and typed status/errors.
- `launcher-core/src/test/kotlin/pl/syntaxdevteam/syntaxlauncher/core/launch/MinecraftProcessManagerTest.kt` — five lifecycle test cases, including an actual local Java subprocess.
- `docs/ROADMAP.md` — precise process-management implementation status and premium-log redaction prerequisite.
- `docs/WORKLOG.md` — this entry.
- `README.md` — actual implementation status and remaining v0.1 tasks.

### Verification

- Isolated `kotlinc` compilation against installed JDK 21, coroutines JAR and minimal domain/logging stubs — PASS.
- Five executable local smoke scenarios (using an actual Java subprocess plus controlled fake processes) — PASS.
- Architecture review — PASS for Compose-independent process ownership and not printing command arguments.
- `./gradlew test` — NOT RUN; the repository still lacks a committed Gradle wrapper and this container cannot resolve github.com/Maven dependencies.
- `./gradlew build` — NOT RUN for the same infrastructure reasons.
- Clean-machine vanilla Minecraft launch — NOT RUN; Compose Play wiring is not yet implemented.
- Direct raw stdout/stderr redirection is suitable for offline v0.1; redaction must be revisited before introducing premium access tokens in v0.3.

### Roadmap updates

- All new process-start/state/log/exit-code/duplicate-protection/stop/shutdown/error/test items marked `🚧 IN PROGRESS` pending Gradle and real-game acceptance.
- Explicit v0.3 dependency added for reviewing sensitive strings in Minecraft process logs.
- Compose launch orchestration and window-close wiring remain unchecked; no fake Play control was added.
- v0.1 test/build/real-launch quality gates remain unchecked.

### Follow-ups / known issues

- Connect metadata → game preparation → managed Java → LaunchPlan → ProcessManager into the real Compose Play action.
- Map progress, errors and running/stopped/crashed states to a lightweight UI with conflict-safe controls.
- Persist offline username, selected version and RAM; cache version catalog where appropriate.
- Commit a Gradle wrapper, run full test/build, then verify a clean-machine vanilla launch and the supported OS matrix.
- Before v0.3 Microsoft authentication, change raw Minecraft process log capture to guarantee token redaction without breaking detach behavior.

---

## 2026-10-17 — Build metadata-driven Minecraft LaunchPlan

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Implemented `LaunchPlan` as the structured, process-independent output of the v0.1 launch pipeline: resolved Java runtime, working directory, main class, JVM arguments, game arguments and final argument-vector command.
- Refactored `LaunchRequest` to consume a verified `PreparedMinecraftVersion` instead of only raw version metadata, so launch planning uses the exact prepared classpath/directories rather than recalculating filesystem state.
- Added `LaunchAuthentication` as a narrow normalized session boundary. v0.1 maps an offline account to stable UUID, `accessToken=0`, legacy user type and a deterministic offline session string; v0.3 can later provide Microsoft credentials without cloning the launch engine.
- Added `MinecraftArgumentPlaceholderResolver` with explicit unresolved-placeholder failure rather than silently starting Java with invalid `${...}` values.
- Added metadata-driven substitution for `auth_player_name`, `version_name`, `game_directory`, `assets_root`, `game_assets`, `assets_index_name`, `auth_uuid`, `auth_access_token`, `auth_session`, `user_type`, `user_properties`, `version_type`, `natives_directory`, `launcher_name`, `launcher_version`, `classpath`, `library_directory`, `classpath_separator`, `auth_xuid` and `clientid`.
- Added platform-aware classpath joining (`;` on Windows, `:` elsewhere) while preserving each Java/process argument as a separate list element instead of building a shell command string.
- Added JVM memory arguments from `LaunchOptions` plus extra user JVM arguments.
- Applied already-existing Mojang OS/architecture/feature rules before resolving JVM/game placeholders.
- Added logging configuration injection by resolving the metadata logging argument `${path}` to the verified downloaded logging configuration.
- Added legacy launch fallback for versions without metadata JVM arguments: explicit `-Djava.library.path`, `-cp` and the prepared classpath while continuing to use legacy `minecraftArguments` parsing.
- Added `librariesDirectory` to `PreparedMinecraftVersion` so `${library_directory}` is sourced from preparation output rather than guessed by the launch layer.
- Added typed launch-plan failures for missing required artifacts and unknown/unresolved placeholders.
- Added deterministic tests covering modern metadata-driven plans, memory/classpath/natives/logging/offline identity substitution, legacy JVM fallback, paths containing spaces, full command ordering and explicit failure for future unknown placeholders.
- Cross-checked standard launch placeholder usage against current PrismLauncher launch/session handling and historical metadata fixtures before freezing the v0.1 mapping.
- Updated `docs/ROADMAP.md` so all launch-planning items reflect implemented-but-not-yet-fully-executed state.

### Changed files

- `launcher-core/src/main/kotlin/.../launch/LaunchModels.kt` — prepared-game request boundary plus normalized offline launch authentication.
- `launcher-core/src/main/kotlin/.../launch/LaunchPlan.kt` — structured final launch plan and typed launch-plan errors.
- `launcher-core/src/main/kotlin/.../launch/MinecraftArgumentPlaceholderResolver.kt` — strict Mojang placeholder substitution.
- `launcher-core/src/main/kotlin/.../launch/LaunchPlanBuilder.kt` — modern/legacy JVM and game argument construction, classpath, memory, logging and offline identity wiring.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftPreparationService.kt` — exposes prepared libraries directory to launch planning.
- `launcher-core/src/test/kotlin/.../launch/LaunchPlanBuilderTest.kt` — modern/legacy structured launch-plan tests.
- `launcher-core/src/test/kotlin/.../launch/MinecraftArgumentPlaceholderResolverTest.kt` — placeholder and path-with-spaces tests.
- `docs/ROADMAP.md` — synchronized launch-planning status.
- `docs/WORKLOG.md` — this entry.

### Verification

- Launch core `kotlinc` smoke compilation with minimal domain stubs — PASS on the available JDK 21 environment; the new launch classes compile as a coherent unit.
- Architecture/source review — PASS: launch construction remains in `launcher-core`, consumes prepared/runtime state, does not depend on Compose and never uses shell command concatenation.
- GitHub compare review — PASS for the intended v0.1 launch-planning files and deterministic tests.
- External launcher compatibility review — PASS for standard Mojang placeholders/session mapping patterns used by PrismLauncher; unknown future placeholders intentionally fail explicitly.
- Container repository access — BLOCKED; the execution container still cannot resolve `github.com`, preventing a real repository clone/dependency resolution.
- `./gradlew test` — NOT RUN; repository still lacks a committed Gradle wrapper and external Gradle/Maven dependency access is unavailable in the execution environment.
- `./gradlew build` — NOT RUN for the same environment/tooling limitation.
- Real Minecraft process launch — NOT RUN by design in this task; process execution is the next v0.1 stage.

### Roadmap updates

- `LaunchRequest`, `LaunchOptions`, classpath construction, placeholder substitution, Mojang argument rules, memory, assets, natives, game directory, offline identity, logging config, legacy fallback and final `LaunchPlan` are now `🚧 IN PROGRESS` with implementation/test sources present.
- The new normalized authentication seam is documented as infrastructure for the common launch pipeline, not as premature Microsoft authentication.
- Process-management items remain fully open.
- v0.1 quality gates remain unchecked until full Gradle tests/build and a real clean-machine vanilla launch pass.

### Follow-ups / known issues

- Implement `MinecraftProcessManager` around `ProcessBuilder` using `LaunchPlan.command` and `LaunchPlan.workingDirectory`.
- Capture stdout/stderr into a separate Minecraft process log and expose running/stopped/crashed state plus exit code.
- Prevent duplicate launches and define launcher shutdown behavior while Minecraft remains running.
- Wire the real end-to-end pipeline (metadata → preparation → Java → launch plan → process) into Compose and only then expose `Play`.
- Add persistence for nickname/version/RAM selections and version-manifest cache.
- Legacy asset indexes using `virtual` / `map_to_resources` still require materialized legacy asset views before claiming broad historical-version compatibility.
- Add a Gradle wrapper and execute the complete automated test/build suite in an environment with dependency access.

---

## 2026-10-17 — Implement automatic managed Mojang Java Runtime Manager

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Implemented `MojangJavaRuntimeManager` as the concrete v0.1 `JavaRuntimeManager` implementation.
- Confirmed and integrated Mojang's current official Java runtime index endpoint rather than hardcoding Minecraft-version-to-Java tables.
- Added platform mapping for Mojang runtime ids: Windows x86/x64/ARM64, Linux x86/x64, macOS x64 and macOS ARM64; unsupported official combinations fail explicitly.
- Added runtime target selection by Minecraft-declared component (`jre-legacy`, `java-runtime-*`) and release availability.
- Added runtime-index and runtime-manifest parsing for files, directories, links, executable flags and raw download descriptors.
- Added verified runtime-manifest caching using the common `DownloadManager` and Mojang-provided SHA-1/size metadata.
- Added complete staged runtime installation into launcher-managed `runtimes/` storage instead of mutating the final runtime directory during download.
- Reused the shared verified downloader for every runtime file, preserving retries, cancellation, bounded concurrency and progress events.
- Added manifest-path validation preventing absolute/path-traversal writes outside the staging root.
- Added manifest-declared directory creation, executable-bit handling and symbolic-link creation with safe target validation.
- Added macOS Java-home detection for `jre.bundle/Contents/Home`, alongside Windows `bin/java.exe` and Linux/Unix `bin/java`.
- Hardened `JavaRuntimeInspector` with a bounded `java -version` process timeout and modern/legacy Java-major parsing.
- Added pre-publication and post-publication runtime verification against the Minecraft-required Java major.
- Added atomic directory publication where supported plus a schema-versioned `.syntaxlauncher-runtime.json` completion marker.
- Added warm-start reuse before any runtime-index network call.
- Strengthened warm-start validation to verify the cached runtime manifest plus every managed runtime file by SHA-1/size, directory presence, symlink target and a working `java -version`; corrupt/incomplete installations are rejected and reinstalled automatically.
- Extended `JavaRuntime` with source/component/platform/release identity for later launch diagnostics without coupling the launch layer to Mojang DTOs.
- Added typed runtime failures for unsupported platform, metadata, manifest, installation, verification and major-version mismatch cases.
- Added runtime lifecycle events (`checking`, metadata, downloads, installation, verification and ready) for later Compose progress/status presentation.
- Added deterministic parser tests plus a `MockEngine` integration-style manager test covering initial installation, no-network warm reuse and automatic repair after deleting an installed runtime file.
- Updated README and v0.1 roadmap to match the actual implementation state.

### Changed files

- `launcher-core/src/main/kotlin/.../java/JavaRuntime.kt` — managed-runtime identity/source fields.
- `launcher-core/src/main/kotlin/.../java/JavaRuntimeManager.kt` — runtime events, probe boundary and hardened `JavaRuntimeInspector`.
- `launcher-core/src/main/kotlin/.../java/JavaRuntimeExceptions.kt` — typed runtime failures.
- `launcher-core/src/main/kotlin/.../java/MojangRuntimeMetadata.kt` — Mojang runtime index/manifest models, parser and platform mapping.
- `launcher-core/src/main/kotlin/.../java/MojangJavaRuntimeManager.kt` — discovery, download, staging, verification, publication, reuse and repair pipeline.
- `launcher-core/src/test/kotlin/.../java/MojangRuntimeMetadataParserTest.kt` — controlled runtime metadata parsing tests.
- `launcher-core/src/test/kotlin/.../java/MojangJavaRuntimeManagerTest.kt` — controlled installation/reuse/repair test using Ktor `MockEngine` and a fake runtime probe.
- `README.md` — documented implemented automatic Java runtime management.
- `docs/ROADMAP.md` — synchronized Java Runtime Manager progress and remaining platform/build acceptance work.
- `docs/WORKLOG.md` — this entry.

### Verification

- Official metadata verification — PASS: Mojang's current runtime index endpoint exposes component-specific manifests for supported platform keys and currently includes runtime components through `java-runtime-epsilon`/Java 25 where available.
- Source/architecture review — PASS for metadata-driven selection, core/UI separation, staged publication, path safety, reuse-before-network and full warm-cache integrity checks.
- Controlled tests added — parser coverage plus managed install/reuse/corruption-repair behavior using Ktor `MockEngine`; no live Mojang dependency in normal tests.
- GitHub compare review — PASS; changes remain within v0.1 Java runtime management plus required documentation.
- `./gradlew test` — NOT RUN; repository still lacks a committed Gradle wrapper and the execution container cannot resolve external Gradle/Maven/GitHub dependencies.
- `./gradlew build` — NOT RUN for the same environment/tooling limitation.
- Real Windows/Linux/macOS Mojang runtime installation — NOT RUN in this environment; roadmap items therefore remain `🚧 IN PROGRESS` rather than `[x]`.

### Roadmap updates

- Mojang runtime source/selection, managed reuse, download/install, integrity verification, executable resolution and typed errors are now marked `🚧 IN PROGRESS` with implementation present and execution acceptance pending.
- Runtime manifest/file verification and automatic repair are explicitly tracked.
- Supported runtime platform matrix finalization remains open because official Mojang component availability differs by OS/architecture/component.
- v0.1 quality gates remain unchecked until full Gradle tests/build and a real clean-machine Minecraft launch pass.

### Follow-ups / known issues

- Build final placeholder substitution and `LaunchPlan` using `PreparedMinecraftVersion` plus the resolved managed `JavaRuntime`.
- Add Minecraft process start/monitoring after launch-plan validation.
- Wire Java/download/preparation events into the lightweight Compose launch progress state and only then expose the real `Play` action.
- Add a Gradle wrapper and run the complete automated test/build suite.
- Perform real runtime-install acceptance on the supported Windows/Linux/macOS architecture matrix.
- Decide whether Linux ARM64 should remain explicitly unsupported when Mojang has no matching official runtime entry or receive a later alternate runtime-provider adapter.

---

## 2026-10-17 — Add verified download and Minecraft preparation pipeline

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added a reusable `DownloadManager` around the shared Ktor client for streamed artifact downloads.
- Added `.part` staging and atomic finalization so incomplete downloads are never published as valid launcher files.
- Added SHA-1 and expected-size validation for both new downloads and existing cached files.
- Added verified cache reuse: valid files skip the network while invalid files are replaced only after a successful new download.
- Added bounded batch concurrency using coroutines and a configurable semaphore (default parallelism: 6).
- Added structured cancellation behavior; cancelled/failed operations clean their partial files while already completed verified cache files remain reusable.
- Added bounded retry/backoff for transient network failures, HTTP 408/425/429/5xx responses and integrity/size failures.
- Added typed download failures for network, HTTP, checksum, size, filesystem and conflicting-target conditions.
- Added typed download events for batch/file start, progress, retry, completion and failure, providing the future Compose progress UI with a core-level event source.
- Added duplicate-target coalescing so identical requests for one cache artifact are executed only once and conflicting definitions fail explicitly.
- Increased the shared HTTP request timeout to better accommodate binary artifact downloads while retaining separate connect/socket limits.
- Added `MinecraftPreparationService`, which executes the existing file plan: client JAR, libraries, native archives, asset index, logging configuration and asset objects.
- Added verified asset-index loading, content-addressed asset download planning and duplicate asset-hash de-duplication.
- Added deterministic version-specific native-directory regeneration and extraction after native archives have been verified.
- Added `PreparedMinecraftVersion` as the output boundary that carries the resolved version, file plan, parsed asset index, prepared directories and download results toward the future launch planner.
- Added controlled `MockEngine` test sources for cache reuse, transient retry, failed-integrity publication safety and end-to-end Minecraft preparation from synthetic client/library/native/asset payloads.

### Changed files

- `launcher-core/src/main/kotlin/.../network/DownloadModels.kt` — download requests/results/events and retry policy.
- `launcher-core/src/main/kotlin/.../network/DownloadExceptions.kt` — typed download failures.
- `launcher-core/src/main/kotlin/.../network/DownloadIntegrityVerifier.kt` — cached-file size/SHA-1 verification.
- `launcher-core/src/main/kotlin/.../network/DownloadManager.kt` — streaming downloads, cache reuse, retries, cancellation, bounded concurrency, progress and atomic publication.
- `launcher-core/src/main/kotlin/.../network/LauncherHttpClientFactory.kt` — increased request timeout for artifact downloads.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftPreparationService.kt` — executable game-file preparation pipeline and stage events.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftFilePlan.kt` — de-duplicates asset objects by content hash.
- `launcher-core/src/test/kotlin/.../network/DownloadManagerTest.kt` — controlled cache/retry/integrity tests.
- `launcher-core/src/test/kotlin/.../minecraft/MinecraftPreparationServiceTest.kt` — controlled preparation integration test using `MockEngine` and temporary filesystem state.
- `docs/ROADMAP.md` — synchronized download/game-file preparation progress.
- `docs/WORKLOG.md` — this entry.

### Verification

- Architecture/source review — PASS for core/UI separation, shared download ownership, retry classification, partial-file lifecycle, integrity checks and preparation sequencing.
- Ktor API review — PASS against current `bodyAsChannel`/`ByteReadChannel.readAvailable` and MockEngine response APIs used by the implementation.
- GitHub compare review — PASS; implementation changes are scoped to download/preparation infrastructure and controlled tests.
- Container repository clone/build attempt — BLOCKED; execution environment cannot resolve `github.com`, so the repository/dependencies cannot be fetched into the build container.
- `./gradlew test` — NOT RUN; Gradle wrapper is still not committed and external Gradle/Maven dependency resolution is unavailable in the execution environment.
- `./gradlew build` — NOT RUN for the same tooling/network reason.
- New roadmap items therefore remain `🚧 IN PROGRESS` rather than `[x]` even where implementation/test sources are present.

### Roadmap updates

- Client/library/native/asset-index/asset-object download execution is now `🚧 IN PROGRESS` pending Gradle/integration execution.
- Hash/size validation, verified cache reuse and partial-file publication safety are now `🚧 IN PROGRESS` with controlled test sources present.
- Download cancellation, bounded concurrency, progress events, retry policy, atomic writes and typed failures are now `🚧 IN PROGRESS` pending test execution.
- Native extraction lifecycle is now connected to verified downloads and remains `🚧 IN PROGRESS` pending execution.
- Assets/natives/game-directory outputs are now exposed toward launch planning, but placeholder substitution and final `LaunchPlan` remain open.
- Compose download/progress presentation remains only partially implemented because the core event stream is not yet wired to UI state.

### Follow-ups / known issues

- Implement managed Java runtime discovery/download/install and reuse the same verified downloader for runtime artifacts.
- Build final placeholder substitution and `LaunchPlan` using the prepared classpath/assets/natives/game directories.
- Add Minecraft process start/monitoring only after Java selection and launch-plan validation exist.
- Wire preparation/download events into the lightweight Compose progress UI when a real Play flow is available.
- Add persistence for launcher selections and manifest/settings cache.
- Legacy asset indexes using `virtual` / `map_to_resources` still need materialized legacy views if those historical versions become part of the supported v0.1 matrix.
- Add a Gradle wrapper and run full `test`/`build` plus a real clean-machine Minecraft launch before completing acceptance boxes.

---

## 2026-10-16 — Resolve version metadata and plan Minecraft files

**Milestone:** v0.1 — Minimal Vanilla Launcher  
**Author/Agent:** ChatGPT  
**Status:** Partial

### Completed

- Added `MinecraftVersionResolver` for downloading the selected version metadata and verifying its manifest SHA-1 before parsing.
- Added domain models and DTO parsing for client download metadata, libraries, native classifiers, asset indexes, main class, modern JVM/game arguments, legacy `minecraftArguments`, Java runtime requirements and client logging configuration.
- Added normalized Windows/Linux/macOS and CPU-architecture detection for Minecraft rule evaluation.
- Added deterministic Mojang-style allow/disallow rule evaluation including OS/version/architecture and feature predicates.
- Added argument filtering/resolution for the selected platform and feature set.
- Added platform-aware library and native-classifier resolution.
- Added `MinecraftInstallationLayout` and a `MinecraftFilePlanner` that plans client, library, native, asset-index, logging-config and classpath locations without performing downloads yet.
- Added asset-index parsing plus planning of Mojang asset-object URLs and hash-prefix cache paths.
- Added safe native JAR extraction with `META-INF` filtering and Zip Slip protection.
- Added initial `JavaRuntime`, `JavaRuntimeManager` boundary and `JavaRuntimeInspector` capable of validating a local Java executable and parsing modern/legacy Java version strings.
- Added initial `LaunchOptions` and `LaunchRequest` models so the future launch builder receives explicit account/version/runtime/platform/options state.
- Connected selected-version resolution to Compose Desktop. The UI now verifies `version.json` after version selection and shows Java requirement/library count or a retryable metadata error.
- Added unit-test sources for rule evaluation, modern/legacy version metadata parsing, native resolution, asset-index parsing, file planning, Zip Slip rejection and Java version parsing.

### Changed files

- `launcher-core/src/main/kotlin/.../minecraft/MinecraftPlatform.kt` — normalized OS/architecture model.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftRule.kt` — Mojang rule model/evaluator.
- `launcher-core/src/main/kotlin/.../minecraft/ResolvedMinecraftVersion.kt` — resolved metadata domain, argument resolution and legacy tokenizer.
- `launcher-core/src/main/kotlin/.../minecraft/VersionMetadataDto.kt` — serialization DTOs for `version.json`.
- `launcher-core/src/main/kotlin/.../minecraft/VersionMetadataParser.kt` — metadata-to-domain parser.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftVersionResolver.kt` — selected-version metadata fetch and SHA-1 verification.
- `launcher-core/src/main/kotlin/.../minecraft/LibraryResolver.kt` — OS/rule/native library selection.
- `launcher-core/src/main/kotlin/.../minecraft/AssetIndex.kt` — asset-index model/parser including legacy `map_to_resources` support.
- `launcher-core/src/main/kotlin/.../minecraft/MinecraftFilePlan.kt` — download/classpath/native planning.
- `launcher-core/src/main/kotlin/.../minecraft/NativeExtractor.kt` — safe native extraction.
- `launcher-core/src/main/kotlin/.../storage/MinecraftInstallationLayout.kt` — v0.1 game/cache directory layout.
- `launcher-core/src/main/kotlin/.../java/JavaRuntime.kt` — resolved runtime model.
- `launcher-core/src/main/kotlin/.../java/JavaRuntimeManager.kt` — runtime-manager boundary and local runtime inspector.
- `launcher-core/src/main/kotlin/.../launch/LaunchModels.kt` — initial launch request/options models.
- `launcher-core/src/test/kotlin/...` — metadata/rules/files/natives/Java test sources.
- `launcher-desktop/src/main/kotlin/.../Main.kt` — creates and injects `MinecraftVersionResolver`.
- `launcher-desktop/src/main/kotlin/.../ui/SyntaxLauncherApp.kt` — resolves selected version metadata and exposes readiness/error state without adding a fake Play action.
- `docs/ROADMAP.md` — synchronized v0.1 implementation state.
- `docs/WORKLOG.md` — this entry.

### Verification

- Source review — PASS for architecture boundaries, v0.1 scope, metadata/rule semantics, path planning and Zip Slip guard.
- Container repository clone/build attempt — BLOCKED; execution environment cannot resolve `github.com`, so dependencies/repository cannot be fetched for a complete local Gradle run.
- `./gradlew test` — NOT RUN; Gradle wrapper is still not committed and external dependency access is unavailable in the execution environment.
- `./gradlew build` — NOT RUN for the same tooling/network reason.
- Roadmap items that require build/test acceptance therefore remain unchecked or `🚧 IN PROGRESS` rather than being marked complete.

### Roadmap updates

- Selected-version metadata download/parsing, Java requirement parsing, OS/architecture rules and deterministic rule evaluation are now `🚧 IN PROGRESS` pending full build/runtime verification.
- Library/native selection, asset-index parsing/planning and safe native extraction are now `🚧 IN PROGRESS`; actual downloads/integrity/cache reuse remain open.
- `JavaRuntime`, `JavaRuntimeManager`, local runtime inspection, `LaunchOptions` and `LaunchRequest` now exist but runtime installation and final launch planning remain open.
- Desktop version-metadata readiness/error handling is now implemented but remains `🚧 IN PROGRESS` pending full Compose build/manual execution.

### Follow-ups / known issues

- Implement the reusable downloader with atomic partial files, hash verification, bounded concurrency, cancellation, retry and progress reporting.
- Fetch/verify the asset index, plan its objects, and execute client/library/native/asset/log-config downloads.
- Add deterministic native extraction lifecycle after native downloads finish.
- Implement managed Java runtime discovery/download/install using a supported upstream runtime source.
- Build placeholder substitution and final `LaunchPlan`, then process execution and logging.
- Add a Gradle wrapper and perform complete `test`/`build` verification in an environment with dependency access before checking acceptance boxes.

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
- `./gradlew build` — NOT RUN; documentation-only change and the repository still has no Gradle implementation.
- Manual verification — documentation structure and cross-document milestone ordering reviewed for consistency.

### Roadmap updates

- Project roadmap created and initialized.
- No implementation milestone item is marked complete because application code has not been created yet.
- Documentation-bootstrap items are tracked separately from functional milestone completion.

### Follow-ups / known issues

- v0.1 implementation has not started.
- The exact supported desktop OS/architecture matrix should be finalized during v0.1 implementation based on chosen Java runtime distribution and Compose packaging constraints.
- Dependency versions are intentionally not frozen in documentation; Gradle files will become authoritative once the project is initialized.
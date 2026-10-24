# v0.6 — Modrinth discovery and installation acceptance

## Automated (local suite passed; hosted acceptance tracked separately)
- [ ] MockEngine popularity search uses Modrinth `downloads` sort and the exact Fabric/Minecraft facets.
- [ ] Reading project detail displays the author/description/license without executing remote content.
- [ ] Version selection rejects incompatible and server-only results.
- [ ] Required transitive dependency closure creates a reviewable ordered installation plan.
- [ ] Modrinth file host/filename/checksum constraints are enforced.
- [ ] Verified DownloadManager writes all requested artifacts to a staging area.
- [ ] Guarded instance import installs all JARs as one batch, rejecting name/ID collisions.
- [ ] Any failed checksum or interrupted preparation preserves installed mods and removes the staging files.
- [ ] Gradle tests/desktop compilation and native package builds succeed across supported platforms.

## Manual (Modrinth workflows not yet accepted; Fabric/Quilt startup confirmed on 20 October 2026)
- [ ] Open Fabric instance → Mods → Discover on Modrinth.
- [ ] Popular list is sorted by download count and filtered for this instance's Minecraft version and loader.
- [ ] Search a known mod and inspect full project description and Modrinth page.
- [ ] Review installed file(s), version(s), optional notes and required dependency list before approving.
- [ ] Install with required dependencies, confirm files are enabled under only this instance's mods folder.
- [ ] Relaunch and verify Minecraft/Fabric loads installed mods.
- [ ] Change Minecraft version and confirm incompatible mods are no longer offered.
- [ ] Verify broken internet, 429, no matching versions, missing dependency, wrong checksum and duplicate conflicts.
- [ ] Confirm importing a local JAR still functions.
- [ ] Test resizing (400×400 up), keyboard navigation, confirmation and disabling controls while installation is busy.
- [ ] Confirm external URL opens only the official Modrinth project page.

**Limitations:** This is a v0.6 slice, not the complete milestone. Managed project/version persistence, compatible updates, backups and explicit last-update rollback are implemented and deterministically tested. Optional dependency choices, comprehensive version-constraint reconciliation, crash-resume recovery and native/game workflow acceptance remain open. Source project
and dependencies remain under Modrinth distribution rights. User-installed mods
execute third-party code when Minecraft loads.

## Additional closure — 24 October 2026

Optional branches can be selected in advanced review; unselected branches are not fetched. Tests cover required closure of selected branches, deselection, same-plan conflicts, enabled managed-origin conflicts before download and a locked publication recheck. EN/PL/DE compact UI tests prevent installation during replanning. API 429/transient retry is bounded to three attempts with capped Retry-After. Full arbitrary installed-mod/version-range reconciliation and native gameplay acceptance remain open. See [C06](MILESTONE_CLOSURE.md#c06).

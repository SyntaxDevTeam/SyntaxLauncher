# SyntaxLauncher v0.4 — Fabric acceptance checklist

**Status:** Not yet executed on real Minecraft clients. Keep roadmap quality gates open
until representative checks pass. Use a dedicated disposable world for version changes.

## Automated verification

- [ ] GitHub Actions `gradle --no-daemon test build` passes on main.
- [ ] Loader Meta API mock tests pass: discovery, profile inheritance, library ordering,
      Mojang client preservation, untrusted source and incompatible loader rejection.
- [ ] Existing Vanilla instance persistence tests pass; optional loader metadata is reversible.

## Desktop acceptance

- [ ] Create a new Vanilla instance; play and verify its own saves/configs remain intact.
- [ ] Create a Fabric instance on an officially compatible Minecraft release.
- [ ] Select a stable Fabric version, then edit and switch to another compatible one.
- [ ] Try a Minecraft version without Fabric availability and observe a clear error.
- [ ] Launch the Fabric instance using an offline account and examine game stdout/stderr.
- [ ] Confirm Fabric Loader initializes; place a known compatible Fabric mod only as a
      manual smoke test (local mod management remains v0.5).
- [ ] Confirm launcher uses original Mojang game artifacts and the chosen Java runtime.
- [ ] Confirm deleting/restoring/cloning an instance keeps its loader selection and
      does not mutate another instance's game directory.
- [ ] Switch Fabric back to Vanilla; confirm no Fabric entry point or libraries remain
      in the launch plan and worlds remain untouched.
- [ ] Test cancellation, retry and disk/network failure; incomplete downloads must not
      be treated as complete.
- [ ] Check UI at 400×400 and normal desktop sizes without losing controls.
- [ ] Test Windows, Linux and macOS builds with a real supported Fabric profile.

## Integrity consideration

Fabric launcher profiles may lack declared artifact SHA-1/size checksums. Verify
retrieval of official hash metadata for such artifacts before promoting v0.4 to
release-ready. The current centralized downloader validates checksums only when supplied.

## Manual test record

Date/platform: _not recorded_

Minecraft/Fabric versions: _not recorded_

Result/log links: _not recorded_

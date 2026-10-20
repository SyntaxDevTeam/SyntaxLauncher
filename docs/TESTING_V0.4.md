# SyntaxLauncher v0.4 — Fabric acceptance checklist

**Status:** The maintainer confirmed real Fabric and Quilt testing in the project chat on 20 October 2026. Fabric startup is accepted below. Platform/version details and separate failure/switching checks were not supplied and are not invented. Use a dedicated disposable world for version changes.

## Automated verification

- [ ] GitHub Actions `gradle --no-daemon test build` passes on main.
- [x] Loader Meta API mock tests pass: discovery, profile inheritance, library ordering,
      Mojang client preservation, untrusted source and incompatible loader rejection.
- [x] Existing Vanilla instance persistence tests pass; optional loader metadata is reversible.

## Desktop acceptance

- [ ] Create a new Vanilla instance; play and verify its own saves/configs remain intact.
- [ ] Create a Fabric instance on an officially compatible Minecraft release.
- [ ] Select a stable Fabric version, then edit and switch to another compatible one.
- [ ] Try a Minecraft version without Fabric availability and observe a clear error.
- [x] Launch a real Fabric instance — confirmed by the maintainer on 20 October 2026. Account type and log-audit details were not separately recorded.
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

Date: 20 October 2026. Platform: _not supplied_.

Minecraft/Fabric versions: _not supplied_.

Result: maintainer-confirmed successful Fabric testing in the project chat. Logs: _not supplied_.

Quilt startup was confirmed in the same message; see [v0.8 acceptance](TESTING_V0.8.md). This confirmation does not imply all-platform, cancellation, mod-management or failure-injection acceptance.

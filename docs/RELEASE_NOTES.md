# SyntaxLauncher 0.9.2

The maintainer approved Stable publication on 23 October 2026. This is a pre-1.0 release within the existing documented support matrix.

- Memory allocation moves to settings for the currently selected game. Save persists the change; Close discards an unsaved draft. Other profiles keep their own memory settings.
- Game profiles opens inside the main launcher window. The workshop backdrop, searchable list, content inventory and per-profile Edit make prepared games easier to inspect.
- Create a new profile guides you through Vanilla/mods, loader, complete pack/individual extras/empty, name and final review. Nothing is written before confirmation. Complete packs are filtered/validated for the chosen loader and installed with their exact definition; individual extras open in the same main window.
- Clone/import/trash/restore and secondary local import remain available. Existing worlds, accounts, mods and settings keep their formats.
- Home places Game type below Game version and removes the duplicate sidebar Game profiles button.
- Footer website, Discord and GitHub icons open the requested destinations; the previous slogan is removed. Privacy, Terms, publisher contact and the complete Mojang/Microsoft unofficial/unaffiliated notice now live in the shared footer rather than the player sidebar.
- The prior release-development work adds explicit offline profiles, game diagnostics/managed-file repair, safe support summaries, rotating redacted logs, disk admission and dependency locks.

## Support and limitations

Packaging targets remain Linux x64, Windows x64 and macOS ARM64. Forge and OptiFine retain their bounded matrices; arbitrary historical versions and a complete dependency solver are not promised. Empty profiles prepare game files and Java on first Play.

Microsoft live sign-in/entitlement/gameplay acceptance remains deferred pending Mojang AppID approval. Offline profiles remain explicit and cannot authenticate to servers requiring verified Microsoft identities. Native signing/notarization, clean-machine installs/upgrades, broad focus/DPI/gameplay acceptance and final 1.0 criteria remain open in ROADMAP.md and TESTING_V0.12.md.

Publication is performed by the existing canonical-version-validated CI only after tests and all three platform packages pass. Verify the public v0.9.2 release and its three portable archives plus three update ZIPs before claiming availability.

# Support and acceptance matrix

Status: 24 October 2026, development `0.9.3-DEV`. The matrix separates distributed targets, installer checks and actual Minecraft gameplay. It does not promise all versions on every OS.

| Distribution target | Formats | Automated evidence | Remaining native acceptance |
| --- | --- | --- | --- |
| Windows x64 | portable, MSI (WiX 7) | hosted package, install/repair/remove, data preservation, PE icon verification | clean Windows GUI/trust, signed publisher, prior-version updates |
| Linux x64 | portable, DEB, RPM, AppImage | DEB/AppImage startup; Fedora 44 dependency-resolved RPM install/remove; local packaged Mojang HTTPS | RPM GUI, additional distributions, Wayland/DPI and real updater recovery |
| macOS ARM64 | portable app, DMG | hosted package, mount/copy/remove and bundle identity | Apple signing/notarization, clean-machine trust, GUI/game and update acceptance |

Linux DEB targets compatible Debian/Ubuntu/Mint systems; RPM and AppImage broaden packaging choices, not guaranteed compatibility with every distribution. System libraries and graphics support still apply. Other CPU/OS combinations are not shipped native targets. Runtime metadata contains more platform mappings than the actual distribution matrix.

| Game mode | Discovery / preparation | Graphical game evidence |
| --- | --- | --- |
| Vanilla | official metadata, checksums, declared Java; deterministic preparation/planning tests | earlier private Vanilla 1.21.1/OptiFine J1 acceptance; clean plain-Vanilla cold/warm matrix remains open |
| Fabric | official Meta + common preparation | maintainer confirms Linux + Windows 10, Minecraft 1.21.11 / 26.2 / 26.3, start and world save; exact loader versions/distro not recorded |
| Quilt | official Meta v3 + common preparation | private startup/mod workflow confirmed 20–21 October; exact pair/OS not recorded |
| NeoForge | official Maven, modern V2 processors; reference 1.21.1 / 21.1.213 | earlier maintainer v0.7 acceptance; wider exact matrix open |
| Forge | exact official Minecraft coordinate matching; modern V2; four reference pairs in [C08](MILESTONE_CLOSURE.md#c08) | private 1.21.1 / 52.1.20 acceptance; newer reference pairs have installer evidence only |

Minecraft Java requirements come from verified Mojang metadata. Reference installations use Java 21 for 1.21.1/1.21.11 and Java 25 for 26.2/26.3. Missing component/platform metadata fails clearly rather than guessing. Historical assets/arguments/inheritance have automated coverage; historical Forge V1 remains unavailable. Future upstream builds may change installer formats and are not automatically certified by appearing in discovery.

Microsoft gameplay is deferred pending Mojang approval. Explicit offline profiles remain available; this does not enable access to authenticated servers. Full complex dependency/version solving and arbitrary mod compatibility are not promised. See [release gates](RELEASE_READINESS.md), [installation](INSTALLATION.md), [signing prerequisites](SIGNING.md) and [milestone evidence](MILESTONE_CLOSURE.md).

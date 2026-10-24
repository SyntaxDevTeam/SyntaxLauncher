# v0.8 — Forge and Quilt acceptance

## Acceptance decision — 21 October 2026

v0.8 is accepted for the bounded support matrix below and development proceeds to v0.9. The maintainer answered the direct question about actual Forge 1.21.1/52.1.20 startup and mod installation/startup on Forge and Quilt: **“Tak, zostały przetestowane prywatnie.”** This is private maintainer acceptance, not an automated test result. No OS list, exact Quilt/mod versions or logs were supplied; none are inferred. The earlier 20 October confirmation of Fabric and Quilt startup remains valid.

The original acceptance did not certify every upstream version or replace the remaining native UI/OS and earlier milestone clean-machine release gates. No product-version bump, tag or release publication is part of this decision.

## Supported scope and evidence

| Mode | v0.8 support boundary | Evidence |
| --- | --- | --- |
| Vanilla | Existing metadata-driven pipeline; explicitly declared Java requirement | Parser/rule/planner/runtime/process regressions; shared five-mode coordinator test. Earlier clean-machine acceptance remains separate. |
| Fabric | Official Meta compatibility discovery and profile overlay; no claim of every upstream game/loader pair | Existing provider regressions, five-mode coordinator test, maintainer-confirmed startup on 20 October; exact private pair not recorded. |
| Quilt | Official Meta v3 compatibility discovery/profile overlay and loader-filtered mod workflow | Provider/inventory/Modrinth regressions; five-mode coordinator test; maintainer-confirmed startup on 20 October and private mod workflow on 21 October. Exact private pair not recorded. |
| NeoForge | Shared processor-backed modern V2 adapter; official reference installation: Minecraft 1.21.1 / NeoForge 21.1.213 | Official installer CI, synthetic processor/coordinator tests and the existing v0.7 maintainer acceptance. Broader discovery is not an exhaustive tested matrix. |
| Forge | Modern V2 builds from exact official Minecraft coordinates; reference installations listed in [C08](MILESTONE_CLOSURE.md#c08) | SHA-1/V2 inspector/processor tests, official installation CI, shared coordinator regression and private maintainer startup/mod acceptance on 21 October. |
| Historical Forge | **Unsupported:** legacy V1, including the representative 1.12.2 case,  | Rejected during provider install even when persisted metadata bypasses the selector; the inspector rejects legacy embedded versionInfo rather than interpreting it as V2. Negative tests do not claim a historical game boot. |

### Java and argument policy

The Mojang base metadata supplies `javaVersion.component` and `majorVersion`; loaders retain this requirement. Explicit Java 8 requirements use the same runtime-manager contract as modern Java. An absent requirement fails before game/runtime downloads: there is no UI-side version table, guessed default or use of whatever Java happens to be on PATH. The managed Mojang component must actually exist for the detected platform and pass manifest/file/executable checks. No broad historical Java/platform support is inferred from argument tests.

Legacy `minecraftArguments` tokenization, quoted values, classpath/native JVM fallback and normalized account placeholders live in core resolvers and `LaunchPlanBuilder`. Modern JVM/game lists and loader overlays use the same planner. Existing Java-8 legacy planner and missing-requirement coordinator tests cover these boundaries. General metadata inheritance and legacy asset views now have deterministic preparation tests; historical Forge installation remains outside the matrix; no historical gameplay support is claimed.

## Automated verification

- [x] Official Forge Maven discovery, SHA-1 installer verification, corruption/wrong-target/V1 rejection.
- [x] Official Minecraft 1.21.1 / Forge 52.1.20 installer and client processors: [CI 39560642818](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39560642818); reconfirmed in [CI 39634747655](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39634747655).
- [x] NeoForge official installer/processor regression passed in that same later CI run.
- [x] Core/desktop tests and Windows, Linux and macOS portable packages passed in CI 39634747655. Packaging is not native interaction/gameplay acceptance on all three systems. The newer [CI 39647415857](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39647415857) also completed successfully for baseline commit `3fdf34e`. The new coordinator matrix, historical-policy tests and v0.9 service added during this audit are verified locally and have not yet been submitted to hosted CI.
- [x] `LoaderPipelineMatrixTest` runs **all real providers** with synthetic HTTP profiles/installers, real fixture processor JVMs, shared preparation/runtime/planner/process services and separate game directories. Final process startup is intercepted with local `java -version`; it does not execute the fixture Minecraft classes or boot a graphical game.
- [x] The five-mode test checks shared verified Vanilla client reuse, selected Java requirement propagation, loader entry points/arguments, generated Forge-family runtime libraries and preservation of per-instance save markers.
- [x] Forge/NeoForge bounded TOML diagnostics and Quilt direct metadata inventory regressions pass in the normal core suite.
- [x] Local `./gradlew --offline test build` passes after this acceptance audit and the initial v0.9 core slice; exact test counts are recorded in WORKLOG.md.

## Maintainer acceptance

- [x] Fabric and Quilt real startup — confirmed 20 October 2026.
- [x] Forge actual startup and Forge/Quilt mod install/startup — privately confirmed 21 October 2026 in this chat.
- [x] NeoForge v0.7 acceptance — previously recorded maintainer confirmation; this audit does not invent additional environments/results.

## Advisory metadata boundaries

The mod inventory now uses the full TOML syntax parser TomlJ 2.2.0 with explicit input/depth/identity/dependency limits. Quoted keys, escaped/multiline strings, inline tables and multiple mod records are supported. Dependencies from all declared owners are retained and bundled identities satisfy local presence checks. Forge `mandatory=true` and NeoForge `type=required` produce missing-dependency warnings; NeoForge `type=incompatible` produces enabled-ID conflicts. Optional, discouraged and server-only requirements do not produce client missing-dependency errors. Matching manifests take priority; wrong loader families produce warnings. Quilt IDs, versions and direct requirements are read without claiming complete nested/version-range semantics. Quilt optional dependencies are not misreported as missing required mods; conditional/group and complex version expressions are flagged as advisory rather than flattened. These are inventory diagnostics, not a complete dependency solver.

## Optional expansion and remaining platform gates

- [x] Additional Forge V2 official installations: 1.21.11/61.2.4 (Java 21), 26.2/65.1.5 and 26.3/66.1.0 (Java 25), locally verified 24 October; CI smoke includes all four pairs. No new graphical Forge boot is inferred.
- [ ] Historical V1/Java compatibility and actual old-game startup.
- [x] Full TOML syntax with bounded interpretation and bundled identities; regression fixtures include fake headers in multiline descriptions, invalid syntax and excessive nesting.
- [ ] Complex Quilt expressions and installed version-range reconciliation.
- [ ] Explicit Windows/Linux/macOS game/mod/loader-switching matrix with exact versions and logs.
- [ ] Native window/DPI/keyboard interaction, interruption/concurrency/recovery matrix and earlier clean-machine release acceptance.

These remain visible in the roadmap. They are not represented as tests passed or silently removed by this bounded v0.8 acceptance.

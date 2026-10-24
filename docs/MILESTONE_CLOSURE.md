# Milestone closure evidence — 24 October 2026

This register reconciles obsolete implementation checkboxes with current code and verification. It does **not** certify every milestone as released. ROADMAP.md remains authoritative; open manual/native/security criteria stay open. Product version remains `0.9.3-DEV`; Stable remains `0.9.2`.

## C01

v0.1 implementation: metadata/catalog/rules, downloads, runtime selection, launch planning, process management, schema-versioned preferences and redacted logging are present and covered by the normal core/desktop suite. New `MinecraftVersionResolverTest` verifies checksum/identity-checked parent inheritance, child library precedence, inherited arguments and cycle/unknown-parent rejection. `LegacyAssetViewsTest` and `MinecraftPreparationServiceTest` verify virtual/map-to-resources generation, corruption recovery, path/link rejection and preservation of conflicting user files. These enable historical metadata handling but do not certify historical games whose metadata omits a required Java component.

The desktop uses the canonical build version for launch placeholders and User-Agent. `LauncherPreferencesStoreTest`, launch/coordinator/process and account tests cover the existing foundations; no actual graphical Vanilla cold start is inferred. The packaged Linux runtime fetched and verified Mojang catalog/release metadata over HTTPS. CI now repeats this probe on each native app image, before publication.

### Further v0.1 closure — typed launch advice and managed Java

`LaunchFailureReason` now provides bounded, type-based safe categories instead of guessing exception names. Desktop localizes all categories and shows complete recovery advice in EN/PL/DE. Actual Play→Java metadata failure and reachable retry are rendered/tested at wide and 400×400 sizes. Malformed runtime-index JSON has a typed domain failure. `ManagedJavaOfficialSmokeTest` adds real cold installation, execution, network-disabled reuse and controlled repair on each published native target; local Linux 21/25 passed before push. Hosted CI 40095551689 then passed both Java references on Windows x64, Linux x64 and macOS ARM64. All three downloaded JUnit archives were checked: zero skips/failures/errors and two successful offline-reuse/repair markers per target. Runtime platform/executable gates are now closed within this reference scope. This does not establish graphical Minecraft/native trust acceptance. See [v0.1 evidence](TESTING_V0.1.md#typed-launch-failures-and-native-java-verification).

## C02

v0.2 implementation: the UUID repository, explicit non-destructive legacy import, atomic metadata, independent game directories, activity leases, clone/trash/restore and selected-game RAM/JVM overrides are covered by repository, copy/activity and launch integration tests. Profile deletion confirmation and direct restore interactions were verified in DEV 190.1 / CI 40063797333. These are automated filesystem/UI results, not confirmation of two simultaneously played Minecraft worlds or large real-world save migration. Existing schema 1 remains backward readable and preserves unknown fields; no incompatible schema 2 has been introduced.

## C03

v0.3 existing foundations only: Microsoft OAuth/PKCE, Xbox/XSTS/Minecraft session mapping, account inventory and player selection have deterministic coverage; registration and the actual submission record are documented in [MICROSOFT_APP.md](MICROSOFT_APP.md). Historical “register/submit” items are distinct from **approval**, which has not been obtained. Live Microsoft account work remains deferred by the maintainer. The dual offline/Microsoft product scope and original AppID submission differ; no compliance or authorization is inferred. Native vault, consent, refresh and premium game acceptance remain unchecked.

## C04

v0.4: real Fabric/Quilt providers use the shared launch pipeline; serialization, Vanilla reset, profile persistence and loader matrix tests cover code isolation. Unchecksummed library/native cache entries now require a local SHA-256 receipt tied to URL and size. Missing/damaged receipt or mismatching bytes forces a fresh download. The receipt detects local corruption and **does not establish upstream authenticity**; upstream SHA-1/SHA-256 remains preferred. `DownloadManagerTest` covers initial adoption, reuse, same-size corruption, changed source and malformed receipt.

Maintainer confirmation on 24 October: **Linux and Windows 10; Minecraft 1.21.11, 26.2 and 26.3; Fabric; game startup and world saving**. Exact Fabric versions, Linux distribution, account type and per-machine pairing were not supplied. Record this limited evidence without inventing them. Earlier Fabric/Quilt confirmations remain valid. It does not certify A/B world isolation, Vanilla cold start, every add-on workflow or macOS.

## C05

v0.5: local import/inventory, SHA-256, reversible enable/disable, trash, duplicate/missing/conflict notices and instance locking are implemented and tested by `LocalModManagerTest`. Metadata warnings remain advisory for unsupported version expressions. Real per-instance mod enable/disable/restart and native UI acceptance remain open.

## C06

v0.6: `ModrinthCatalogTest`, installer/update tests and desktop catalog tests cover search/details/filtering, required closure, hashes, staged import, transactional update/rollback and preservation of existing files. New optional-dependency choices resolve only selected branches, retaining the normal one-action required-only path. Explicit incompatible declarations are rejected within a plan and against enabled managed origins, both before downloads and again under the instance lock before publication. They do not reconcile arbitrary local files or solve version ranges. The secondary review picker is verified in EN/PL/DE and compact layouts by `ModrinthOptionalChoicesTest`.

HTTP 408/425/429/500/502/503/504 receive at most three attempts, per-request deadlines and bounded Retry-After delays; permanent errors fail immediately. Unit fixtures cover 429 and retry limits. Real marketplace/game workflow and full installed dependency reconciliation remain open.

## C07

v0.7: official installer, generated-artifact, loader identity and mixed-family metadata warnings have existing deterministic and hosted evidence. NeoForge 1.21 discovery now maps to the official `21.0.*` family (distinct from 1.21.1 → `21.1.*`); regression verifies that boundary. Full TOML syntax is now parsed with pinned TomlJ 2.2.0 (256 KiB, nesting 32, 128 identities, 512 dependency records), preserving quoted/escaped/multiline values, inline tables and bundled mod identities. Client dependencies from all declared owners are retained; server/optional/discouraged entries are not required. Fake headers inside descriptions do not become mods. Existing and new syntax/size/depth/duplicate tests pass. Full installed version solving and interruption/recovery acceptance remain separate.

## C08

v0.8: Forge discovery now filters official Maven coordinates by **exact selected Minecraft version**, numerically orders modern V2 builds and rechecks membership at installation. It no longer exposes only one hardcoded pair. Historical V1 remains explicitly unsupported, with negative tests; a published coordinate alone is not proof that every future installer format is understood.

Actual official downloads and client processors completed locally for:

| Minecraft | Forge | Managed Java | Result |
| --- | --- | --- | --- |
| 1.21.1 | 52.1.20 | 21 | Existing official CI and private maintainer gameplay acceptance |
| 1.21.11 | 61.2.4 | 21 | Official installer/processors passed 24 October |
| 26.2 | 65.1.5 | 25 | Official installer/processors passed 24 October |
| 26.3 | 66.1.0 | 25 | Official installer/processors passed 24 October |

New rows do **not** claim a graphical Forge boot. `ForgeOfficialSmokeTest` uses Mojang-declared managed Java, verified client artifacts, the real provider and checks generated libraries plus save-directory preservation. Hosted CI runs all four pairs. See [TESTING_V0.8.md](TESTING_V0.8.md).

## C12

v0.12: prior successful CI 40063797333 produced DEV 190.1 with 12 assets; Stable 0.9.2 was explicitly approved and published by CI 40005510889. Native lifecycle/publication checks are described in [INSTALLATION.md](INSTALLATION.md) and [RELEASE_READINESS.md](RELEASE_READINESS.md). These reconcile stale “publication pending” entries, not manual update/trust criteria. The new packaged HTTPS check runs on every platform. Stable 1.x and later now require `release_readiness.py --enforce-production` before publication, while pre-1.0/preview builds remain available for testing.

Certificates and Apple Developer membership are unavailable, per maintainer confirmation. [SIGNING.md](SIGNING.md) explains obtaining them. No purchase, registration, signature or notarization is claimed.

## Remaining sequence

1. v0.1–v0.2: exact Vanilla fresh/cached runs, native lifecycle/focus and real A/B worlds/import/recovery.
2. v0.3: external Mojang approval and subsequent real premium/vault/security acceptance; deferred rather than waived.
3. v0.4–v0.8: native mod selection/update/rollback/gameplay matrix; full installed dependency version reconciliation and complex Quilt semantics; historical Forge V1 is excluded from current support.
4. v0.9–v0.11: preserve accepted bounded implementation; finish wider native graphics/game/repair acceptance under v1.0.
5. v0.12: signed/notarized native trust, actual old→new updates, interruption and clean-machine/extra-distribution acceptance.
6. v1.0: final security/dependency/license review, final candidate documentation and all independent release gates. A version bump cannot substitute for evidence.

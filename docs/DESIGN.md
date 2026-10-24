# SyntaxLauncher Design Guidelines

**Current account policy — 24 October 2026:** Present a player selector and manager for Microsoft Java Edition players only. Persist selection across restarts. Add through browser sign-in and remove with confirmation without deleting game data. Keep Play disabled without a selected Microsoft player; every Play requires fresh entitlement/profile verification and failures block launch. Keep Privacy/Terms, contact and independence notice visible. Preserve the selected blue gateway header/application icon and landscape-only home hero.

This document defines the mandatory visual and interaction principles for SyntaxLauncher.

The launcher should attract attention because it feels polished, deliberate and modern — not because it fills the screen with effects, controls or decoration.

The target character is:

> **Lightweight, modern, distinctive, calm and immediately understandable.**

A successful screen should look interesting at first glance while remaining easy to scan and comfortable to use for long periods.

---

## 1. Core design principle

### Mandatory child-first catalog workflow

The launcher is for children. Extraordinary ease of use is an acceptance requirement, not optional polish. Browse a catalog inside the launcher, choose a single item or compatible ready-made set and press Install; then Play. This applies to mods, modpacks, shaders, resource packs, OptiFine and future content. Compatible versions, loaders, Java, renderers and required dependencies are the launcher’s responsibility. Do not make a child pick files, folders, hashes or dependency versions, or navigate technical review screens as the normal path. Show short names, short explanations and clear Installing/Installed/Retry states. Keep local import and technical details behind secondary controls. Preserve existing games/worlds and never silently downgrade/convert a game to make an item fit.

Only expose working catalog categories. Backend-only support, downloaded ZIPs without required renderer setup, placeholder tabs and an Install button that cannot finish the job do not satisfy this requirement. Ready-made sets belong in the same catalog experience when their milestone is implemented.

SyntaxLauncher must balance two goals that are equally important:

1. **Visual appeal** — the application should feel premium, contemporary and recognizable.
2. **Low cognitive load** — the interface must never overwhelm the user with information, controls or decoration.

The design must not become visually heavy merely to appear advanced.

Modern does not mean busy.

Minimal does not mean empty or generic.

The preferred result is a visually restrained interface with a strong identity, clear hierarchy and high-quality details.

---

## 2. Visual weight

The UI should feel light even when the launcher eventually contains many features.

Prefer:

- generous but efficient spacing;
- clear grouping;
- a small number of visible actions per screen;
- restrained use of borders and separators;
- hierarchy created primarily through spacing, typography and surface contrast;
- simple surfaces instead of deeply nested cards;
- one obvious primary action per context;
- compact secondary information.

Avoid:

- excessive cards inside cards;
- thick borders around every element;
- large blocks of explanatory text on primary screens;
- showing every available setting at once;
- multiple competing accent colors;
- excessive gradients, glow, blur or glass effects;
- decorative elements that reduce content clarity.

---

## 3. Modern visual language

The application should look contemporary without depending on short-lived design trends.

The visual system should use:

- clean typography;
- consistent corner radii;
- restrained elevation/shadows where useful;
- coherent iconography;
- subtle depth rather than heavy skeuomorphism;
- modern spacing and sizing;
- smooth state transitions;
- a limited and intentional accent palette.

Effects such as translucency, blur, gradients or glow may be used only when they improve hierarchy or identity. They must not become the dominant visual language.

The launcher should have its own SyntaxLauncher identity rather than visually cloning the official Minecraft Launcher, Prism Launcher, Modrinth App or another existing product.

---

## 4. Information hierarchy

Every important screen must answer these questions immediately:

1. **Where am I?**
2. **What is currently selected?**
3. **What is the primary action?**
4. **Is anything wrong or requiring attention?**

For the primary launch screen, the most important information is expected to be approximately:

1. selected instance/version;
2. selected account;
3. readiness/status;
4. Play action;
5. secondary settings/details.

Advanced technical information should not compete visually with the Play action.

---

## 5. Progressive disclosure

As SyntaxLauncher gains features, complexity should be revealed only when needed.

Examples:

- common RAM settings may be visible while advanced JVM arguments remain under Advanced settings;
- installed mod count/status may be visible while dependency metadata appears inside the mod manager;
- Java runtime should normally be selected automatically, with manual override available in advanced settings;
- diagnostic details should appear when a problem exists rather than permanently occupying the main screen.

Do not expose implementation complexity simply because the launcher internally supports it.

The user should not need to understand Minecraft metadata, classpaths, Java components or loader internals to launch the game.

---

## 6. Primary action discipline

The main action on the launcher home screen is **Play**.

It must be visually unambiguous and must not compete with several equally prominent buttons.

Secondary actions such as:

- settings;
- account management;
- instance editing;
- mods;
- logs;
- diagnostics;

should remain easy to find without stealing visual priority from normal game launch flow.

During preparation/download/launch, the same area may transform into progress/status/cancel behavior instead of adding more competing controls.

---

## 7. Color and accents

Use color intentionally.

Guidelines:

- maintain a limited core palette;
- reserve the primary accent for important interactive/state emphasis;
- do not use bright colors decoratively everywhere;
- use warning/error/success colors semantically and consistently;
- preserve sufficient contrast for text and controls;
- do not rely on color alone to communicate important status.

The final brand palette may evolve, but the application should never become visually noisy through uncontrolled accent usage.

---

### SyntaxLauncher blue identity (October 2026)

The maintainer-selected desktop mark is a rounded cornflower-blue stone arch with two open pale-blue doors and a bright threshold. The header, window, startup updater and Linux package icon share this identity. Following maintainer feedback, the home hero contains landscape and copy only; never repeat the gateway there. The mark uses cornflower `#779CF6`, royal blue and icy white, without green/teal or an orbital arrow. Existing interface accents remain unchanged during this in-app branding preview. Website and Entra artwork are outside this change; their earlier submitted logo remains a historical review record.

| UI role | Token |
| --- | --- |
| Background | `#030F20` |
| Surface | `#08192E` |
| Surface variant | `#10243B` |
| Primary / Play | `#00D5FF` |
| Primary container | `#06355A` |
| Secondary | `#91B5DB` |
| Cyan detail | `#00D5FF` |
| Main text | `#F3F7FF` |
| Muted text | `#B3CBEA` |
| Outline | `#284967` |

Panels use restrained `#0C2444 → #08192E` depth, with `#135582` outlines. The enabled Play button uses `#34D7F5 → #009EF4`; its disabled state uses a quiet solid field color. Reserve vivid neon for the icon and small interactive accents. Do not reintroduce green/teal prototype UI colors or recolor semantic warnings and errors blue. Use the central `SyntaxTheme.kt` tokens instead of scattering color literals.

### Desktop hero and responsive shell (October 2026)

- The home hero uses the existing local cinematic voxel valley JPEG, with native Compose heading/profile copy and readable horizontal/vertical scrims. It has no foreground logo, gateway, backing shadow or halo: product identity stays in the header. See [asset provenance and prompts](../launcher-desktop/src/main/resources/branding/README.md).
- Keep copy on the left in wide heroes, leaving the right landscape unobstructed. In narrow heroes, use the full available copy width. Measure the heading first, then constrain profile details to the remaining vertical space, preserving readable copy and disjoint heading/profile rectangles during live resizing and DPI changes. Short heroes reduce typography and omit the secondary tagline.
- Use an application-wide Material `Surface` for proper light text over the dark theme. Do not depend on default `Text` content colors after drawing raw backgrounds.
- The main window chooses an initial size constrained by the desktop work area. The instance panel is vertically scrollable, with a visible scrollbar; crucial controls should be visible without scrolling on typical full-HD screens.
- Compact hero typography avoids text collisions. Below 760 dp of content width, stack the hero/Play and setup panel with a visible page scrollbar; otherwise keep Play visible and scroll the setup panel independently. Very short windows scroll the page as well. The minimum native window is 400 × 400. Never make unsupported future features appear as active navigation.
- Optimized JPEG/PNG resources load locally via `rememberBrandingPainter` and the Compose resources bitmap decoder, retaining the packaged classpath paths. Decoding is remembered per resource path and streams are closed after reading; no network access, runtime blur, repeated animation or dynamic downloads. Shared vector icons, 18 dp panel radii, 9 dp field radii and typography live in `SyntaxTheme.kt` / `SyntaxComponents.kt`. Account setup details belong behind the Player information action, keeping the normal launch screen concise.

### Startup update screen

Packaged startup uses the same brand palette and responsive open-gateway logo on a quiet background. Show one short phase message, indeterminate/proportional progress, and a secondary Continue action. Errors show an actionable summary with Retry and Continue. Continue cancels checking/downloading/extraction; disable it during child process handoff to prevent launching two home screens. Keep technical metadata out of this screen and keep file/network work on background dispatchers. Only compose the main home after continuing, finding no update, or receiving a verified child startup. Rendered recovery states and a real Compose startup receipt are part of regression coverage.

## 8. Typography

Typography should carry a substantial part of the interface hierarchy.

Use a small, consistent type scale for:

- page/screen titles;
- section titles;
- primary values/content;
- body text;
- labels/metadata;
- captions/status information.

Avoid unnecessary font-size variation and excessive bold text.

Dense technical metadata should use a presentation appropriate to technical content without making the entire launcher feel like a developer tool.

---

## 9. Motion and micro-interactions

Motion should make the interface feel responsive and polished, not animated for its own sake.

Appropriate uses include:

- screen/section transitions;
- selection changes;
- Play-to-progress state transition;
- download progress;
- success/error state changes;
- expanding advanced options;
- subtle hover/focus/press feedback.

Animations should generally be:

- short;
- smooth;
- interruptible where applicable;
- consistent;
- low-cost to render.

Avoid long cinematic transitions, constant background movement and repeated attention-seeking animation.

The UI must remain usable when animation is reduced or unavailable.

---

## 10. Performance is part of the design

A visually modern launcher that feels slow has failed the design goal.

The UI must remain responsive while:

- downloading game files;
- resolving metadata;
- extracting natives;
- installing Java;
- scanning mods;
- performing update checks.

Heavy work must never block the Compose UI thread.

Visual effects must be evaluated for rendering cost. Expensive blur, continuously animated backgrounds and unnecessary large images should be avoided when they provide little UX value.

Startup should feel immediate even if background data is still loading.

---

## 11. Empty, loading and error states

Polish is particularly important outside the ideal path.

Every significant screen should eventually define:

- loading state;
- empty state;
- error state;
- disabled state;
- success/ready state where applicable.

Do not show raw empty containers or generic `Something went wrong` placeholders.

Errors should be concise in the primary UI with technical detail available on demand.

---

## 12. Responsive desktop layout

The launcher is a desktop application, but window sizes and display scaling differ.

The interface must:

- tolerate resizing within supported limits;
- work correctly with common DPI scaling;
- avoid layouts that depend on one exact pixel size;
- prevent important actions from being clipped;
- preserve hierarchy as available space changes.

A reasonable minimum window size may be enforced if necessary, but layout should still be adaptive within the supported range.

---

## 13. Accessibility and usability

Visual quality must not reduce usability.

At minimum:

- maintain readable contrast;
- expose clear keyboard focus states;
- use sensible control hit targets;
- avoid tiny secondary text for important information;
- do not communicate state using color alone;
- provide tooltips or secondary explanations for unfamiliar technical actions where appropriate;
- preserve predictable keyboard navigation where practical.

Accessibility should be considered during component creation rather than postponed until v1.0.

---

## 14. Design system approach

Compose UI should not accumulate arbitrary local values for every screen.

As the desktop application is implemented, define reusable design tokens/components for concepts such as:

- spacing scale;
- typography scale;
- color roles;
- radii;
- elevation;
- animation timing;
- common buttons;
- input fields;
- selection controls;
- navigation;
- status/progress presentation;
- dialogs and notifications.

Do not create a huge design-system framework before v0.1 needs it. Start with a small coherent token set and extend it as repeated patterns emerge.

---

## 15. v0.1 design expectation

The first functional release is intentionally small, but it must already establish the product's visual direction.

The v0.1 home/launch screen should prioritize:

- nickname/account identity;
- Minecraft version selection;
- RAM setting at an appropriate level of prominence;
- preparation/download state;
- one prominent Play action.

It should not look like a temporary developer form merely because the feature set is small.

At the same time, v0.1 must not spend disproportionate engineering effort on decorative effects before the launch pipeline is reliable.

The desired balance is:

> **Production-quality visual foundation with intentionally limited functionality.**

---

## 16. Review checklist for UI changes

Before considering a significant UI change complete, ask:

- Is the primary action immediately obvious?
- Is the screen visually lighter than the amount of functionality it contains?
- Can any visible information be moved behind progressive disclosure without harming normal use?
- Are spacing, typography, color roles and components consistent with existing screens?
- Does the screen remain understandable without decorative effects?
- Are loading/error/disabled states handled?
- Does the layout tolerate expected resizing and DPI scaling?
- Are animations subtle and functional?
- Does the implementation remain responsive during background work?
- Does the result feel recognizably modern without copying another launcher?

If the answer to several of these questions is no, the UI should not be considered finished.

---

## 17. Frameless window chrome and build identification

- Use the existing branded launcher header as the title-bar drag region; **never** introduce another full-height title bar.
- The operating-system frame is disabled in the packaged app. Top-right controls must support minimize, maximize/restore, and close, remain accessible on the startup update/error screen, and not obscure existing navigation or technical build information.
- Retain the 400×400 minimum and preserve resize grips on all eight edges/corners. On maximized windows, disable custom grips.
- Make the exact installed development artifact diagnosable: display the embedded product version, GitHub Actions run number, and abbreviated commit hash. Developer/IDE executions show LOCAL BUILD rather than a fictitious CI number.
- Stable versions still use SemVer ordering. A new build number **must not** masquerade as a new stable product release or change the SemVer comparator/receipt format. Opt-in DEV/SNAPSHOT channels use separate numeric CI run/attempt ordering for successive builds of the same product version.
- Accept the UI only after verifying clickable controls, drag vs navigation interactions, double-click conventions where supported, resizing and maximized-restore bounds, small-screen layout, and DPI/multiple-monitor behavior on Windows, macOS, and Linux X11/Wayland. These manual cross-platform checks remain pending until actually performed.

## 18. Interface languages

English, Polish and German share the same design system. Keep the language menu compact (current language code with the full language names in its dropdown) in the top header, immediately before the settings gear and build identity; it must not compete with Play. Use translated accessibility descriptions for window/language/account actions. Allow longer German labels and Polish glyphs, retain normal scrolling, and verify wide and narrow layouts in all three languages. Switching changes UI text without resetting drafts, selections, user names, instance data or Minecraft settings. Upstream descriptions and diagnostic details keep their original text.

## 19. Simple Vanilla / With mods entry point

The launcher is aimed at children as well as experienced players. The normal home screen must offer a plain-language Vanilla / With mods choice, without requiring knowledge of instances, loader installation or loader versions. Keep Play as the sole dominant action. Reuse existing games when changing mode. Show a quiet Choose mods action for the selected modded game; Game profiles opens the advanced manager.

When no modded game exists, a short setup automatically suggests a compatible game and prefers a stable Fabric loader. Show the chosen Minecraft version before confirmation. Do not create or change files during compatibility discovery. Explicit Choose mods creates a separate UUID profile and opens discovery. State clearly that Vanilla worlds remain unchanged. Cancel writes nothing. More options reveals naming/version/loader controls, including Quilt; unavailable versions, network failures, loading and saving must have clear states. When switching to a loader that cannot run the selected Minecraft version, automatically adjust the draft to the newest supported Minecraft and loader versions and explain the change inline before Save/Create (requested 21 October 2026). A manual version choice in quick setup remains exact until the loader is changed. Keep existing-world downgrade warnings and require Save before persisting settings. Preserve selections, drafts and worlds when switching modes and retain keyboard access, scrolling and EN/PL/DE labels on small screens.

## 20. Launcher update channels

Keep Launcher updates as a quiet secondary action in Your setup. Its compact dialog shows the saved Stable/SNAPSHOT/DEV choice, a brief preview explanation, loading/disabled/error/Retry states and an explicit next-start message. Without a saved choice, show the running build’s embedded channel: Stable for releases, DEV/SNAPSHOT for preview archives. Explain that switching can install an older launcher, independently of Minecraft snapshot selection. Keep network/disk work off the UI thread; update checks continue through the existing branded startup screen. Retain EN/PL/DE labels, keyboard access and the 400×400 minimum.

## 21. Graphics catalog and My packs

Choose extras shares one Install interaction across compatible Mods, Textures, Shaders and OptiFine. Show graphics categories only for supported games; never promise that an arbitrary shader ZIP is ready without renderer setup. Install automatically resolves compatible Iris and required components. OptiFine initially appears only for supported Vanilla 1.21.1; no silent loader/game/world conversion is allowed.

More → My packs is secondary to discovery. Present installed texture/shader names, optional descriptions/icons and a clear active state. Use/Turn off, Remove/Undo and texture priority must describe their effect plainly; local ZIP import is an advanced option. Keep one main action per pack, guard concurrent operations, allow cancellation and preserve recoverable files. Small windows must retain scrollable content and reachable controls. EN/PL/DE screenshots and deterministic interactions supplement actual native focus/DPI/gameplay acceptance.

## 22. Modpacks

A child can open Modpacks before creating any saved game. The catalog shares search, one Install action and simple progress/cancel/retry with other extras. Explain briefly that a separate game is created and existing worlds stay safe; do not ask for Minecraft/loader/hash/file-plan choices. Automatically select the completed new game and return to the home Play action. Never expose a staged/failed game as ready. Existing games stay available after newer-pack installation. Manual import lives under More; original-pack export lives under My packs → More and explicitly excludes player worlds/local changes. Keep secondary home actions usable with longer translations and retain the 400×400 minimum.

## 20. Build feedback corrections — 22 October 2026

The header orders language code/dropdown → settings gear → installed build identity. Launcher update-channel selection lives behind the gear; player/game/memory controls stay contextual beside Play. Narrow windows move Home/Game profiles navigation to a second header row, leaving space for native window controls. Polish navigation uses **Profile gry** (EN: Game profiles; DE: Spielprofile).

Catalog cards for individual extras and complete modpacks use actual Modrinth project artwork when available, with category-specific vector fallbacks, restrained accent strips and readable category captions. Artwork is optional: bounded asynchronous loading through the shared HTTP client must never block search or installation. Curated short explanations for common projects are localized; other authors’ descriptions are explicitly labelled as original-language text. Do not imply arbitrary upstream text was translated.

Local mod checks are advisory. Account for declared Fabric nested modules and provided aliases before claiming dependencies are absent. Show a calm notice count and a direct Review extras action on Home; the mod manager shows the same evidence in a summary and beside the affected file. Missing dependency copy explains that the required extra must be installed or enabled. Keep advanced metadata available without displaying dozens of raw warnings beside Play. These checks are not a complete loader version-range solver.

## Diagnostics and release settings — 23 October 2026

Keep Play dominant. A secondary **Game health and repair** entry beside its status opens a scrollable EN/PL/DE dialog: plain-language findings, confirmed/probable/informational confidence, scan counts, optional safe technical summary and explicit Repair. Explain that repair retains worlds/mods/settings and does not start the game. During repair disable conflicting Play/mutations, show existing preparation progress and offer Cancel. Report copy never uploads anything. Global settings groups update channel, build identity, Mojang-review status and optional local logs/storage information. Long logs remain secondary and bounded; technical data must not replace the normal child-first path. Native focus/DPI acceptance remains a final gate.

## Main-window profiles and settings — maintainer feedback, 23 October 2026

This instruction supersedes older sidebar-memory and separate instance-window guidance. Keep one Game profiles entry in the header. It changes the main content region to a profile library with a distinct workshop/world-archive backdrop, a searchable list, per-row Edit and an explanatory content inventory for the selected profile. No native window is created for the profile page or creation wizard. On narrow screens the bounded list and details remain reachable through scrolling. Use this profile returns to Home; the prepared game remains selected. Clone, legacy import, recoverable trash and local file import remain secondary.

Place Game type below Game version on Home. Remove its duplicate Game profiles button and the RAM slider/value from the sidebar. The settings gear exposes memory for the currently selected game, with an explicit Save, clear scope, disabled busy state and an unsaved draft that Close discards. Legacy memory remains a launcher preference; named profiles retain their own memory settings.

Create a new profile launches a draft-only in-page wizard: Vanilla/With mods → loader for modded games → complete modpack / individual catalog extras / empty → conditional pack selection or game compatibility → name/review → explicit creation. Packs determine their exact versions and are filtered/validated against the chosen loader family. Other paths use compatible loader discovery and explain any version adjustment. Only final confirmation writes data; cancellation, duplicate-click guards, progress and retry preserve existing games. Catalog extras open inside the same main content region after creation.

The footer contains accessible clickable website, Discord and GitHub icons targeting the exact maintainer URLs. Remove the Built for adventure slogan. Keep Privacy, Terms, publisher contact and the full unofficial/unaffiliated Mojang/Microsoft notice in this shared footer, including on profile pages; do not duplicate them in the player sidebar. Continue EN/PL/DE, 400×400 and 150% rendering checks; no new native or graphical Minecraft acceptance is inferred.

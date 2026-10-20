# SyntaxLauncher Design Guidelines

This document defines the mandatory visual and interaction principles for SyntaxLauncher.

The launcher should attract attention because it feels polished, deliberate and modern — not because it fills the screen with effects, controls or decoration.

The target character is:

> **Lightweight, modern, distinctive, calm and immediately understandable.**

A successful screen should look interesting at first glance while remaining easy to scan and comfortable to use for long periods.

---

## 1. Core design principle

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

The approved mark is a voxel dirt/grass block with a navy/cobalt/cyan top and an upward blue orbital arrow. It should appear in desktop branding and distribution artwork.

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

- The hero uses a local cinematic voxel valley JPEG plus a separate transparent PNG of the blue block/orbit/arrow. Both were generated with the built-in imagegen tool from the approved reference; see [asset provenance and prompts](../launcher-desktop/src/main/resources/branding/README.md). The landscape deliberately has no logo, pre-rendered labels or UI controls. Avoid upscaling a tiny 128px app icon into hero artwork; that produced visibly blurry results. The source screenshot is not rendered as a UI screenshot. All controls remain native Compose.
- Preserve a left-to-right dark scrim. Text and the Minecraft version must stay readable independent of background artwork.
- Position the foreground mark independently with `ContentScale.Fit` and derive its aspect ratio from the asset. Measure the heading/profile first and reserve disjoint rectangles: text on the left and logo on the right above 640 dp of hero width; below that width, put the logo below the heading and beside the profile. Fit both dimensions to the available space on every measurement, so resizing never crops its arrow, distorts the mark or places it underneath text. Compact, short heroes reduce the title and omit the secondary tagline to preserve the logo and profile. A soft static navy radial shadow and faint cyan halo distinguish it from the landscape without a rectangular backing, runtime blur or continuous animation. Use the bright mark and cyan secondary heading as the focal details; do not replicate glow throughout all controls.
- Use an application-wide Material `Surface` for proper light text over the dark theme. Do not depend on default `Text` content colors after drawing raw backgrounds.
- The main window chooses an initial size constrained by the desktop work area. The instance panel is vertically scrollable, with a visible scrollbar; crucial controls should be visible without scrolling on typical full-HD screens.
- Compact hero typography avoids text collisions. Below 760 dp of content width, stack the hero/Play and setup panel with a visible page scrollbar; otherwise keep Play visible and scroll the setup panel independently. Very short windows scroll the page as well. The minimum native window is 400 × 400. Never make unsupported future features appear as active navigation.
- Optimized JPEG/PNG resources load locally via painterResource without network access, runtime blur, repeated animation or dynamic downloads. Shared vector icons, 18 dp panel radii, 9 dp field radii and typography live in `SyntaxTheme.kt` / `SyntaxComponents.kt`. Account setup details belong behind the Player information action, keeping the normal launch screen concise.

### Startup update screen

Packaged startup uses the same brand palette and responsive block/arrow logo on a quiet background. Show one short phase message, indeterminate/proportional progress, and a secondary Continue action. Errors show an actionable summary with Retry and Continue. Continue cancels checking/downloading/extraction; disable it during child process handoff to prevent launching two home screens. Keep technical metadata out of this screen and keep file/network work on background dispatchers. Only compose the main home after continuing, finding no update, or receiving a verified child startup. Rendered recovery states and a real Compose startup receipt are part of regression coverage.

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
- Version is still SemVer for update ordering. A new build number **must not** masquerade as a new stable product release or change the startup updater's version comparator/receipt format.
- Accept the UI only after verifying clickable controls, drag vs navigation interactions, double-click conventions where supported, resizing and maximized-restore bounds, small-screen layout, and DPI/multiple-monitor behavior on Windows, macOS, and Linux X11/Wayland. These manual cross-platform checks remain pending until actually performed.

## 18. Interface languages

English, Polish and German share the same design system. Keep the language menu compact (current language code with the full language names in its dropdown) beside Your setup; it must not compete with Play. Use translated accessibility descriptions for window/language/account actions. Allow longer German labels and Polish glyphs, retain normal scrolling, and verify wide and narrow layouts in all three languages. Switching changes UI text without resetting drafts, selections, user names, instance data or Minecraft settings. Upstream descriptions and diagnostic details keep their original text.

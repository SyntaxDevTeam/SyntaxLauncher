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

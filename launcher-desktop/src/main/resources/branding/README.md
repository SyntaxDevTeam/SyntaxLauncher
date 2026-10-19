# Home artwork

The home hero is composed from two independent local assets:

- `hero-landscape.jpg` — scenic background, 1862 × 845, optimized JPEG.
- `hero-mark.png` — transparent block/orbit/arrow foreground, optimized PNG. Keep the alpha channel.

`LauncherArtwork.kt` positions the foreground independently of the scenery. A static radial navy shadow and a very faint cyan halo separate the mark from the landscape. The layout reads the mark’s intrinsic proportions and measures the copy before fitting the logo into its own remaining rectangle. Wide heroes place it to the right; compact heroes place it below the heading, beside the profile. The mark always fits inside the hero without overlapping text, including live resize and DPI changes. No background blur, animated lighting, network image fetch or screenshot of controls is involved. The window/header/package icon remains `syntaxlauncher-icon.png`.

## Generation provenance

Generated with the built-in imagegen tool on 19 October 2026, using the user-supplied “Neonowy interfejs SyntaxLaunchera” concept as visual reference. The attachment itself was not modified. A generated scenic concept was then separated into the two production layers below. Pillow operations were limited to compression and downsampling, preserving the mark's alpha channel.

Final background prompt:

> Edit target: supplied wide voxel landscape. Remove ONLY the large foreground blue cube, luminous orbit, upward arrow and their floating neon square particles from the right third. Fill their previous area naturally with the same continuous Minecraft voxel river, forested riverbank and mountains. Preserve the exact overall camera, landscape, lighting, dark left reading area, bright blue right sky, dimensions and detailed cinematic voxel render. This is a production background layer; the cube will be composited separately in native UI. No text, borders, logos, UI, lettering or new objects. Keep background subtly subdued behind where cube used to be, but still scenic.

Final transparent foreground prompt:

> Edit target: supplied illustration. Extract ONLY the blue voxel cube together with its sweeping neon cyan orbit and upward arrow from the right foreground. Preserve their exact identity, perspective, texture, blue/cyan neon colors and design. Remove ALL landscape, river, water reflections, vegetation, mountains, sky, background and text. Genuine transparent background. Show complete isolated cobalt pixelated grass/dirt cube with three faces and clean cyan luminous edge accents, the cyan orbital trail curves behind it and up into the full arrow at upper right. Keep a restrained faint cyan aura tight around the cutout, and a few nearby tiny blue pixels if needed. Tight portrait framing around full cube and arrow with 5% transparent padding; no clipping, no floor, no ground, no scene. This is a transparent foreground branding asset for composition over a separate landscape.

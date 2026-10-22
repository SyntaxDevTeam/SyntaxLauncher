# Desktop open-gateway branding

## Selected desktop preview — 22 October 2026

The maintainer explicitly selected **Otwarte wrota / Open gates** from the gateway comparison board and requested an in-app preview. This supersedes the earlier instruction to keep the voxel block/orbit mark. The earlier artwork remains recoverable through Git history; public website/Entra assets have not been changed by this desktop task. Selection for an in-app preview does not constitute final trademark or broader platform acceptance.

- `hero-landscape.jpg` — existing independent cinematic voxel valley background, 1862 × 845; unchanged.
- `hero-mark.png` — generated 1254 × 1254 RGBA open-gateway foreground: seven rounded blue stones, two pale-blue open doors and a bright threshold. The outside, gaps and doorway retain transparency; scenery can show through.
- `syntaxlauncher-icon.png` — 512 × 512 RGBA export of the same mark, proportionally resized into 472 × 472 with transparent padding. Used by the header, native window, startup updater and Linux package.

`LauncherArtwork.kt` keeps copy and mark in separate measured rectangles and derives the asset's aspect ratio. A static navy shadow and faint cornflower-blue halo provide separation from the scenery. No rasterized UI, wordmark, comparison-board background, runtime blur or network artwork is included. The logo's blue/icy-white palette removes the previously rejected greenish accent; existing interface colors are retained during this preview.

## Generation provenance

The open-gateway foreground was extracted/reproduced using the built-in imagegen tool from the center concept of the three-way gateway board on 22 October 2026. The generated square foreground was saved losslessly; Pillow was used only for PNG compression and proportion-preserving application-icon export, retaining alpha.

Final foreground prompt:

> Edit target: attached three-column SyntaxLauncher concept board. Extract and reproduce ONLY the large CENTER logo labelled '02 / OTWARTE WROTA' as a production transparent PNG icon. Preserve its identity faithfully: seven chunky softly rounded cornflower-blue stone blocks form a squat arch, royal-blue right/bottom bevels, icy-white/pale-blue two door leaves swung open symmetrically into the dark passage, tiny dark rectangular handles, bright blue-white trapezoidal threshold with two blue steps beneath. Exact central reference design, front view, no redesign, no enclosing tile, no lettering. Remove ALL board background, labels, wordmarks, other logos, dividers, app tiles and frame. Genuine transparent alpha both outside arch and through open doorway and gaps between stones. The doorway must NOT retain rectangular navy background fill: only the two door leaves and threshold are solid inside arch. Preserve slight restrained blue light tightly around threshold only, no wide glow. Center the complete freestanding gateway mark on square transparent canvas with even ~7 percent padding, full arch and steps visible, no cropping. Medium-large high resolution bitmap suitable for a launcher hero foreground and 512px application icon. Colors blue #779CF6 royal #4273D9 pale #DBE7FF, no cyan teal turquoise mint green. NO text, no backdrop, no cast ground shadow.

## Existing landscape provenance — 19 October 2026

Generated with the built-in imagegen tool from the user-supplied “Neonowy interfejs SyntaxLaunchera” reference. The landscape and foreground were kept as independent layers. Its final background prompt was:

> Edit target: supplied wide voxel landscape. Remove ONLY the large foreground blue cube, luminous orbit, upward arrow and their floating neon square particles from the right third. Fill their previous area naturally with the same continuous Minecraft voxel river, forested riverbank and mountains. Preserve the exact overall camera, landscape, lighting, dark left reading area, bright blue right sky, dimensions and detailed cinematic voxel render. This is a production background layer; the cube will be composited separately in native UI. No text, borders, logos, UI, lettering or new objects. Keep background subtly subdued behind where cube used to be, but still scenic.

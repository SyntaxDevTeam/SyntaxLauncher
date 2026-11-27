<div align="center">
  <img src="launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.png" alt="Syntax Launcher" width="120">
  <h1>Syntax Launcher</h1>
  <p><strong>Pick your next Minecraft adventure and get straight to playing.</strong></p>
  <p><a href="#download">Download</a> · <a href="#quick-start">Quick start</a> · <a href="https://syntaxdevteam.pl/p/syntaxlauncher">Project website</a></p>
</div>

**Syntax Launcher** brings Minecraft: Java Edition and its add-ons together in one desktop app. Start a classic survival world, try a modpack, or give your game a new look with shaders and resource packs.

- **Find something new to play.** Browse mods, modpacks, shaders and resource packs from Modrinth and CurseForge in the built-in catalog.
- **Keep your worlds organized.** Create separate installations with their own saves, mods and settings.
- **Get set up automatically.** The launcher prepares the game files and Java runtime for the version you choose.
- **Get back to playing.** Check your game's health and repair managed files from the launcher.

## Download

Find MSI, DMG and AppImage installers in [Build Explorer](https://syntaxdevteam.pl/builds). [GitHub Releases](https://github.com/SyntaxDevTeam/SyntaxLauncher/releases) also includes DEB, RPM and portable builds.

| Platform | Installer |
| --- | --- |
| Windows x64 | MSI |
| macOS on Apple silicon | DMG |
| Linux x64 | DEB, RPM or AppImage |

## Quick start

1. Download the build for your system and open Syntax Launcher.
2. Pick a Minecraft version for a classic game, or open the catalog to find an add-on or modpack.
3. Select **Play** to start your game once it's ready.

For local development with an approved CurseForge key, set the `CURSEFORGE_API_KEY` environment variable before starting the launcher. The launcher sends the key in the `x-api-key` header to the official API and allowlisted HTTPS CDN file endpoints. It validates every CDN redirect before sending the next request. Confirm a deployment model with CurseForge before enabling the integration in a public release. The developer key is confidential under the [third-party API terms](https://support.curseforge.com/support/solutions/articles/9000207405-curse-forge-3rd-party-api-terms-and-conditions).

The launcher blocks automatic installation when an author disables third-party distribution or the API supplies no supported download URL. This applies to required dependencies and required modpack components. If the API supplies a CurseForge project page, users can open it from the catalog or the installation error. The launcher verifies file sizes and SHA-1 hashes from the API. It installs supported loose configuration, script, and resource files from modpack overrides and stops on embedded mods, nested archives, and other unsupported payloads whose distribution it cannot verify. CurseForge catalog metadata is held in memory for display. Installed content and local profile settings are saved, while the CurseForge archive and manifest are temporary.

## Discord Rich Presence

Discord can display **Active in Launcher** or **Playing Minecraft** under the SyntaxLauncher application name. The launcher sends the activity and elapsed time to the local Discord desktop client. Visibility follows the user's Discord activity privacy settings.

Fabric profiles on Minecraft 26.2 and 26.3 also report the game menu, singleplayer, and multiplayer. Server domain names are shown by default. In launcher settings you can hide server addresses or turn off Discord activity entirely.

Official builds use the SyntaxLauncher Discord application by default. For a custom build, set the public Application ID in `SYNTAXLAUNCHER_DISCORD_APPLICATION_ID` or pass `-PdiscordApplicationId=<id>` to Gradle. Discord loads the SyntaxLauncher logo from the project's public GitHub repository. [Discord Developer Portal](https://discord.com/developers/applications)

Made by SyntaxDevTeam. Released under the [MIT License](LICENSE).

**Unofficial Minecraft project. Not approved by or associated with Mojang or Microsoft.**

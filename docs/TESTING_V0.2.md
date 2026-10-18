# SyntaxLauncher v0.2 — Instance Manager manual acceptance

The initial v0.2 functionality is a preview. Automated core tests validate file isolation and cloning, but **real Minecraft gameplay must confirm that worlds and settings remain separate**.

## Prepare

- Download a successful Windows x64 / Linux x64 / macOS ARM64 preview from the [build workflow](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/workflows/build.yml).
- Keep a copy of important worlds before performing any preview migration.
- Note that `Legacy Vanilla` still uses the existing v0.1 directory. New instances are saved under the SyntaxLauncher data directory's `instances/<uuid>/game`.

## Isolation and Play

1. Open **Manage instances** and create **Survival A** for an available Minecraft release.
2. Select it and click **Play Minecraft**. Create a world called `OnlyInA`; save and quit.
3. Create **Survival B** for exactly the same Minecraft version. Run B and check that `OnlyInA` is absent.
4. Create `OnlyInB`, then return to instance A. Confirm `OnlyInA` is present and `OnlyInB` is absent.
5. Confirm that assets, libraries, client JARs and managed Java are shared and reused (no unnecessary complete re-download).
6. Change version or RAM for A, click **Manage instances → Save version / RAM**, restart SyntaxLauncher and verify values persist.
7. Confirm the launcher rejects Play if an edited version/RAM has not yet been saved to the active instance.

## Clone, rename and trash

1. Select A, click **Clone**, then launch the clone. `OnlyInA` must exist in the clone.
2. Change a world/config in the clone. The original A must retain its original data.
3. Rename the clone. Its internal UUID/path should not change.
4. Move the clone to trash and confirm it disappears from the launcher list.
5. Confirm its entire directory still exists under `instance-trash` and can be recovered manually. **There is no in-app restore button yet.**
6. If the `Legacy Vanilla` game data exists, use **Import v0.1 game (copy worlds)**, verify imported saves, and confirm the original legacy game folder remains untouched.

## Failure handling and migration

- A malformed or newer-than-supported `instance.json` should not be deleted or overwritten by routine list/update actions.
- Missing previously selected instance should generate an actionable warning instead of silently opening Legacy Vanilla.
- Instance metadata uses schema v1. Unknown fields are retained during update and clone. Schema-changing migrations will be added when a later format is introduced.
- The metadata `instance.json` is stored beside each instance's `game` directory. The folder name and JSON `id` must agree.

## Report a problem

Include launcher commit SHA, operating system/architecture, Minecraft version, instance name (not the UUID), operation, reproduction steps, and sanitized process errors/logs. Never include private credentials or other users' sensitive data.

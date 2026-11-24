package pl.syntaxdevteam.syntaxlauncher.core.storage

import java.nio.file.Path
import kotlin.io.path.createDirectories

data class MinecraftInstallationLayout(
    val gameDir: Path,
    val versionsDir: Path,
    val librariesDir: Path,
    val assetsDir: Path,
    val assetIndexesDir: Path,
    val assetObjectsDir: Path,
    val loggingConfigsDir: Path,
    val nativesDir: Path,
) {
    fun ensureDirectories() {
        listOf(
            gameDir,
            versionsDir,
            librariesDir,
            assetsDir,
            assetIndexesDir,
            assetObjectsDir,
            loggingConfigsDir,
            nativesDir,
        ).forEach(Path::createDirectories)
    }

    fun versionDirectory(versionId: String): Path = versionsDir.resolve(versionId)

    fun versionJar(versionId: String): Path =
        versionDirectory(versionId).resolve("$versionId.jar")

    fun versionNatives(versionId: String): Path = nativesDir.resolve(versionId)

    companion object {
        fun from(paths: LauncherPaths): MinecraftInstallationLayout {
            val assets = paths.cacheDir.resolve("assets")
            return MinecraftInstallationLayout(
                gameDir = paths.dataDir.resolve("game"),
                versionsDir = paths.cacheDir.resolve("versions"),
                librariesDir = paths.cacheDir.resolve("libraries"),
                assetsDir = assets,
                assetIndexesDir = assets.resolve("indexes"),
                assetObjectsDir = assets.resolve("objects"),
                loggingConfigsDir = assets.resolve("log_configs"),
                nativesDir = paths.cacheDir.resolve("natives"),
            )
        }
    }
}

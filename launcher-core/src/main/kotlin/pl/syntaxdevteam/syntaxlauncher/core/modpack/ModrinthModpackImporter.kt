package pl.syntaxdevteam.syntaxlauncher.core.modpack

import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEventListener

/** Secondary file-import facade; catalog and local import share the same complete installer. */
class ModrinthModpackImporter(private val installer: ModpackInstaller) {
    suspend fun inspect(archive: Path): ModpackInstallPlan = withContext(Dispatchers.IO) {
        ModpackArchive.inspect(archive).plan
    }

    suspend fun install(archive: Path, name: String? = null, optionalPaths: Set<String> = emptySet(),
        progress: DownloadEventListener = {}): MinecraftInstance = installer.install(archive,
            optionalPaths = optionalPaths, name = name, downloadProgress = progress)
}

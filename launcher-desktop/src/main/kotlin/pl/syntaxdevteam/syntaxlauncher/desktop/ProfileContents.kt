package pl.syntaxdevteam.syntaxlauncher.desktop

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance

data class ProfileContents(val mods: List<String>, val worlds: List<String>, val textures: List<String>,
    val shaders: List<String>, val configs: Int, val modpack: Boolean)

/** Read-only, bounded inventory. Never opens a world or creates a missing game directory. */
suspend fun describeProfile(session: DesktopLauncherSession, instance: MinecraftInstance?): ProfileContents = withContext(Dispatchers.IO) {
    val root = instance?.let(session.instances::gameDirectory) ?: session.instances.legacyGameDirectory()
    check(!Files.isSymbolicLink(root)) { "Redirected game directory." }
    suspend fun names(directory: String, directoriesOnly: Boolean = false): List<String> {
        val folder = root.resolve(directory)
        if (!Files.isDirectory(folder, NOFOLLOW_LINKS)) return emptyList()
        return Files.newDirectoryStream(folder).use { entries ->
            val result = mutableListOf<String>()
            for (path in entries) {
                currentCoroutineContext().ensureActive()
                if (!Files.isSymbolicLink(path) && (!directoriesOnly || Files.isDirectory(path, NOFOLLOW_LINKS))) result += path.fileName.toString()
                if (result.size >= 1000) break
            }
            result.sorted()
        }
    }
    val mods = if (instance?.loader != null) session.modManager.list(instance.id).mods.map {
        (it.metadata?.name ?: it.fileName) + " · " + (it.metadata?.version ?: "?") + if (!it.enabled) " (off)" else ""
    } else names("mods")
    ProfileContents(mods, names("saves", true), names("resourcepacks"), names("shaderpacks"), names("config").size,
        Files.isDirectory(root.resolve(".syntaxlauncher-modpack"), NOFOLLOW_LINKS))
}

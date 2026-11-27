package pl.syntaxdevteam.syntaxlauncher.desktop.discord

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection

/** Each supported game receives its own short-lived local bridge. */
internal class DiscordGamePresence {
    private val active = AtomicReference<Session>()

    internal class Session(val directory: Path, val arguments: List<String>) {
        val status: Path get() = directory.resolve("activity")
    }

    fun prepare(gameVersion: String, loader: ModLoaderSelection?, jvmArguments: List<String>): Session? {
        if (loader?.id != "fabric" || gameVersion !in setOf("26.2", "26.3")) return null
        val directory = Files.createTempDirectory("syntax-presence-")
        try {
            val mod = directory.resolve("syntax-game-presence.jar")
            if (File.pathSeparatorChar in mod.toString()) throw IOException("Invalid Fabric mod path")
            val bridge = checkNotNull(javaClass.getResourceAsStream("/syntax-game-presence.jar"))
            bridge.use { Files.copy(it, mod) }
            Files.createFile(directory.resolve("active"))
            val extraMods = jvmArguments.lastOrNull { it.startsWith(ADDITIONAL_MODS) }
                ?.removePrefix(ADDITIONAL_MODS)?.takeIf { it.isNotBlank() }
            val mods = listOfNotNull(extraMods, mod.toString()).joinToString(File.pathSeparator)
            val session = Session(directory, listOf(ADDITIONAL_MODS + mods,
                "-Dsyntaxlauncher.presenceFile=" + directory.resolve("activity")))
            active.getAndSet(session)?.let(::remove)
            return session
        } catch (failure: Exception) {
            remove(Session(directory, emptyList()))
            throw failure
        }
    }

    fun details(shareServer: Boolean): String? {
        val path = active.get()?.status ?: return null
        return try {
            if (!Files.isRegularFile(path, NOFOLLOW_LINKS) ||
                Files.getLastModifiedTime(path, NOFOLLOW_LINKS).toInstant().isBefore(Instant.now().minusSeconds(5))) return null
            val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(512) }
            if (bytes.size == 512) return null
            describeGamePresence(bytes.decodeToString(), shareServer)
        } catch (_: IOException) { null }
    }

    fun finish(session: Session) {
        active.compareAndSet(session, null)
        remove(session)
    }

    fun close() {
        active.getAndSet(null)?.let(::remove)
    }

    private fun remove(session: Session) {
        for (name in listOf("active", "activity", "activity.tmp", "syntax-game-presence.jar")) {
            runCatching { Files.deleteIfExists(session.directory.resolve(name)) }
        }
        runCatching { Files.deleteIfExists(session.directory) }
    }

    private companion object {
        const val ADDITIONAL_MODS = "-Dfabric.addMods="
    }
}

private val dnsServerAddress = Regex(
    """(?=.{1,110}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+(?![0-9]+$)[a-zA-Z][a-zA-Z0-9-]{0,62}""",
)

internal fun describeGamePresence(snapshot: String, shareServer: Boolean): String? =
    when (snapshot.substringBefore('\n')) {
        "menu" -> "In Minecraft Menu"
        "singleplayer" -> "Playing Singleplayer"
        "multiplayer" -> {
            val server = snapshot.substringAfter('\n', "").substringBefore(':')
            if (shareServer && dnsServerAddress.matches(server) &&
                server.substringAfterLast('.').lowercase() !in setOf("local", "localhost", "internal", "lan", "home")) {
                "On Server $server"
            } else "Playing Multiplayer"
        }
        else -> null
    }

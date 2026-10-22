package pl.syntaxdevteam.syntaxlauncher.core.content

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Properties

/** Iris reads ISO-8859-1 Java Properties, not UTF-8. Unknown property values are retained. */
internal object ShaderPackSelection {
    fun prepare(game: Path, fileName: String?, remove: Boolean = false): GameSettingsWrite? {
        val config = game.resolve("config")
        if (Files.exists(config, NOFOLLOW_LINKS) && !Files.isDirectory(config, NOFOLLOW_LINKS)) {
            throw LocalPackException("Shader configuration directory is redirected.")
        }
        val file = config.resolve("iris.properties")
        val original = GameSettingsWrite.read(file)
        if (remove && original == null) return null
        val properties = Properties()
        try { original?.inputStream()?.use(properties::load) }
        catch (failure: Exception) { throw LocalPackException("Cannot safely read shader settings.", failure) }
        if (remove && properties.getProperty("shaderPack") != fileName) return null
        properties.setProperty("shaderPack", if (remove) "" else fileName.orEmpty())
        properties.setProperty("enableShaders", if (remove || fileName == null) "false" else "true")
        val bytes = ByteArrayOutputStream().also { properties.store(it, "Iris shader selection") }.toByteArray()
        return GameSettingsWrite(file, original, bytes, game.resolve(".syntaxlauncher-pack-settings"))
    }
}

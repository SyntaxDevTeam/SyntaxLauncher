package pl.syntaxdevteam.syntaxlauncher.core.storage

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount

data class LauncherPreferences(
    val nickname: String = "",
    val selectedVersionId: String = "",
    val snapshots: Boolean = false,
    val maximumMemoryMiB: Int = 4096,
    val selectedInstanceId: String = "",
) {
    init {
        require(nickname.isBlank() || OfflineAccount.isValidUsername(nickname)) {
            "Invalid saved offline nickname."
        }
        require(selectedVersionId.length <= 100 &&
            selectedVersionId.none { it.code in 0..31 || it.code == 127 }) {
            "Invalid Minecraft version id."
        }
        require(selectedInstanceId.isBlank() ||
            runCatching { java.util.UUID.fromString(selectedInstanceId).toString() == selectedInstanceId }.getOrDefault(false)) {
            "Invalid selected instance identifier."
        }
        require(maximumMemoryMiB in 1024..16384) {
            "Saved RAM setting must be between 1024 and 16384 MiB."
        }
    }
}

/**
 * Small, schema-versioned and atomic settings file.
 * Unknown property keys are preserved when saving; malformed/newer settings
 * are rejected, never reset or automatically overwritten.
 * Only non-sensitive offline profile choices are stored.
 */
class LauncherPreferencesStore(private val file: Path) {
    private val mutex = Mutex()

    suspend fun load(): LauncherPreferences = withContext(Dispatchers.IO) {
        mutex.withLock {
            val properties = readProperties()
            if (properties.isEmpty()) LauncherPreferences() else decode(properties)
        }
    }

    suspend fun save(preferences: LauncherPreferences) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val properties = readProperties()
            if (!properties.isEmpty()) validateSchema(properties)
            properties.setProperty("schema_version", "1")
            properties.setProperty("nickname", preferences.nickname)
            properties.setProperty("minecraft_version", preferences.selectedVersionId)
            properties.setProperty("snapshots", preferences.snapshots.toString())
            properties.setProperty("ram_mib", preferences.maximumMemoryMiB.toString())
            properties.setProperty("selected_instance_id", preferences.selectedInstanceId)
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling("." + file.fileName + "." + UUID.randomUUID() + ".part")
            try {
                Files.newOutputStream(temp).use { properties.store(it, "SyntaxLauncher v0.1 preferences") }
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }

    private fun readProperties(): Properties = Properties().apply {
        if (Files.isRegularFile(file)) {
            Files.newInputStream(file).use { stream -> load(stream) }
        }
    }

    private fun validateSchema(properties: Properties) {
        val schema = properties.getProperty("schema_version")
        require(schema == "1") {
            "Unknown launcher preferences schema: " + (schema ?: "missing") + ". File preserved."
        }
    }

    private fun decode(properties: Properties): LauncherPreferences {
        validateSchema(properties)
        return LauncherPreferences(
            nickname = properties.getProperty("nickname", ""),
            selectedVersionId = properties.getProperty("minecraft_version", ""),
            snapshots = properties.getProperty("snapshots", "false").toBooleanStrict(),
            maximumMemoryMiB = properties.getProperty("ram_mib", "4096").toInt(),
            selectedInstanceId = properties.getProperty("selected_instance_id", ""),
        )
    }
}

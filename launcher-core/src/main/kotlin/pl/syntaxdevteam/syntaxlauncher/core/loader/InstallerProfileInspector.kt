package pl.syntaxdevteam.syntaxlauncher.core.loader

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Read-only preflight for modern Forge/NeoForge client installers.
 * This never executes downloaded code or pretends that a processor list is a launch profile.
 */
data class InstallerProcessorDescriptor(
    val artifact: String,
    val classpath: List<String>,
    val args: List<String>,
    val outputs: Map<String, String>,
)

data class InstallerProfileInspection(
    val gameVersion: String,
    val launcherVersionId: String,
    val processors: List<InstallerProcessorDescriptor>,
    val installerLibraries: List<String>,
    val versionLibraries: List<String>,
    val embeddedDataNames: Set<String>,
)

/**
 * Version 2 install_profile.json + version.json. Legacy v1 profiles and absent
 * processors are rejected rather than silently yielding incomplete clients.
 */
class InstallerProfileInspector {
    private val json = Json { ignoreUnknownKeys = true }
    private val coordinate = Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9._+~-]+(:[A-Za-z0-9_.-]+)?")
    private val safeEntry = Regex("[A-Za-z0-9_./+-]{1,200}")

    fun inspect(jar: Path, requestedGameVersion: String): InstallerProfileInspection {
        require(Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(jar) in 1..(512L * 1024 * 1024)) { "Invalid installer archive." }
        require(requestedGameVersion.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        ZipFile(jar.toFile()).use { zip ->
            fun read(name: String): JsonObject {
                val entry = zip.getEntry(name) ?: throw ModLoaderException(
                    "Installer is missing " + name + "; legacy/installless layouts are unsupported.")
                require(!entry.isDirectory && entry.size in 1..(2L * 1024 * 1024)) {
                    "Oversized or empty installer metadata: " + name
                }
                val bytes = zip.getInputStream(entry).use { it.readNBytes(2 * 1024 * 1024 + 1) }
                require(bytes.size <= 2 * 1024 * 1024) { "Installer metadata too large." }
                return json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
                    ?: throw ModLoaderException("Invalid installer metadata: " + name)
            }
            val install = read("install_profile.json")
            val version = read("version.json")
            val game = install.text("minecraft") ?: throw ModLoaderException(
                "Installer has no Minecraft target.")
            require(game == requestedGameVersion) { "Installer targets " + game + ", not " + requestedGameVersion }
            val inherits = version.text("inheritsFrom")
            require(inherits == requestedGameVersion) {
                "Generated version profile targets another Minecraft version."
            }
            val id = version.text("id") ?: error("Generated version id is missing.")
            require(id.matches(Regex("[A-Za-z0-9._+~-]{1,120}"))) { "Unsafe installer version id." }
            val rawProcessors = install.array("processors")
            require(rawProcessors.isNotEmpty() && rawProcessors.size <= 128) {
                "Installer lacks a supported processor list."
            }
            val processors = rawProcessors.mapNotNull { element ->
                val processor = element as? JsonObject ?: error("Malformed installer processor.")
                val sides = processor.array("sides").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (sides.isNotEmpty() && "client" !in sides) return@mapNotNull null
                val artifact = processor.text("jar") ?: error("Processor JAR missing.")
                require(validArtifact(artifact)) { "Unsafe processor artifact." }
                val classpath = processor.array("classpath").map {
                    val value = (it as? JsonPrimitive)?.contentOrNull ?: error("Invalid processor classpath.")
                    require(validArtifact(value)) { "Unsafe processor classpath." }
                    value
                }
                val args = processor.array("args").map {
                    val value = (it as? JsonPrimitive)?.contentOrNull ?: error("Invalid processor argument.")
                    require(value.length in 1..500 && value.none { c -> c.code < 32 }) {
                        "Unsafe processor argument."
                    }
                    value
                }
                val outputs = (processor["outputs"] as? JsonObject)?.mapValues { (_, value) ->
                    (value as? JsonPrimitive)?.contentOrNull ?: error("Invalid processor output.")
                }.orEmpty()
                require(outputs.size <= 128 && outputs.all { (key, value) ->
                    key.length <= 500 && value.length <= 500
                }) { "Invalid processor outputs." }
                InstallerProcessorDescriptor(artifact, classpath, args, outputs)
            }
            require(processors.isNotEmpty()) { "Installer has no client-side processors." }
            fun libraries(objectValue: JsonObject): List<String> =
                objectValue.array("libraries").map {
                    val name = (it as? JsonObject)?.text("name") ?: error("Unnamed installer library.")
                    require(coordinate.matches(name)) { "Unsafe library coordinate: " + name }
                    name
                }
            val embedded = (install["data"] as? JsonObject)?.keys.orEmpty()
            require(embedded.size <= 128 && embedded.all { safeEntry.matches(it) && ".." !in it }) {
                "Invalid installer data names."
            }
            return InstallerProfileInspection(
                game, id, processors, libraries(install), libraries(version), embedded,
            )
        }
    }

    private fun validArtifact(s: String): Boolean {
        val coordinateValue = s.removePrefix("[").removeSuffix("]")
        return coordinate.matches(coordinateValue) &&
            (!s.startsWith("[") || s.endsWith("]"))
    }

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.array(name: String): List<kotlinx.serialization.json.JsonElement> =
        (this[name] as? JsonArray)?.toList().orEmpty()
}

package pl.syntaxdevteam.syntaxlauncher.core.mods

/**
 * Conservative, bounded subset of Forge/NeoForge TOML metadata for mod inventory.
 *
 * A complete TOML implementation is deliberately NOT claimed: only simple
 * quoted scalar fields, booleans and array-of-tables headers are read. Unknown
 * constructs are ignored instead of guessed, so a dependency is reported only
 * when its type and client applicability are explicit.
 */
internal object ForgeTomlMetadataReader {
    private val safeId = Regex("[a-z][a-z0-9_-]{0,63}")
    private val arraySection = Regex("""^\s*\[\[([a-zA-Z0-9_.-]{1,160})]]\s*(?:#.*)?$""")
    private val property = Regex("""^\s*([a-zA-Z][a-zA-Z0-9_]*)\s*=\s*(.*?)\s*$""")
    private val quotedValue = Regex("""^["']([^"'\r\n]{0,180})["'](?:\s*#.*)?$""")
    private val boolValue = Regex("""^(true|false)(?:\s*#.*)?$""")
    private const val maxLength = 256 * 1024

    private class Requirement {
        var modId: String? = null
        var mandatory: Boolean? = null
        var type: String? = null
        var range: String = "*"
        var side: String = "BOTH"
    }

    fun parse(source: String): LocalModMetadata? {
        require(source.length <= maxLength) { "Forge mod metadata is too large." }
        var inFirstMod = false
        var modAlreadySeen = false
        var id: String? = null
        var name: String? = null
        var version: String? = null
        var current: Requirement? = null
        val requirements = mutableListOf<Pair<String, Requirement>>()

        for (rawLine in source.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                val header = arraySection.matchEntire(line)?.groupValues?.get(1)
                inFirstMod = header == "mods" && !modAlreadySeen
                if (header == "mods") modAlreadySeen = true
                current = null
                if (header != null && header.startsWith("dependencies.")) {
                    val owner = header.removePrefix("dependencies.")
                    if (owner.matches(safeId)) {
                        current = Requirement().also { requirements += owner to it }
                    }
                }
                continue
            }
            val match = property.matchEntire(line) ?: continue
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            val string = quotedValue.matchEntire(value)?.groupValues?.get(1)
            if (inFirstMod) {
                when (key) {
                    "modId" -> if (string != null) id = string
                    "displayName" -> if (string != null) name = string
                    "version" -> if (string != null) version = string
                }
            } else {
                val dependency = current ?: continue
                when (key) {
                    "modId" -> if (string != null) dependency.modId = string
                    "mandatory" -> boolValue.matchEntire(value)?.let {
                        dependency.mandatory = it.groupValues[1] == "true"
                    }
                    "type" -> if (string != null) dependency.type = string.lowercase()
                    "versionRange" -> if (string != null) dependency.range = string
                    "side" -> if (string != null) dependency.side = string.uppercase()
                }
            }
        }

        val modId = id?.takeIf { it.matches(safeId) } ?: return null
        val depends = linkedMapOf<String, String>()
        val breaks = linkedMapOf<String, String>()
        requirements.asSequence().filter { (owner, _) -> owner == modId }
            .map { it.second }.forEach { dependency ->
                val requiredId = dependency.modId?.takeIf { it.matches(safeId) } ?: return@forEach
                // Server-only requirements do not apply to this client launcher.
                if (dependency.side == "SERVER") return@forEach
                when {
                    dependency.type == "required" || (dependency.type == null &&
                        dependency.mandatory == true) ->
                        depends[requiredId] = dependency.range.take(180)
                    dependency.type == "incompatible" ->
                        breaks[requiredId] = dependency.range.take(180)
                }
            }
        return LocalModMetadata(
            id = modId,
            name = name?.takeIf { it.isNotBlank() }?.take(120) ?: modId,
            version = version?.takeIf { it.isNotBlank() }?.take(120) ?: "unknown",
            environment = null,
            depends = depends,
            breaks = breaks,
        )
    }
}

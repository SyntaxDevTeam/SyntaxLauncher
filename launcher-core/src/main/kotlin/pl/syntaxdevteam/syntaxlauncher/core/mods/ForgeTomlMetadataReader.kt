package pl.syntaxdevteam.syntaxlauncher.core.mods

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseOptions
import org.tomlj.TomlTable

/** Full TOML syntax, bounded inventory interpretation; dependency ranges remain advisory. */
internal object ForgeTomlMetadataReader {
    private val safeId = Regex("[a-z][a-z0-9_-]{0,63}")
    private val options = TomlParseOptions.sourceless().withMaxNestingDepth(32)

    fun parse(source: String): LocalModMetadata? {
        require(source.length <= 256 * 1024) { "Forge mod metadata is too large." }
        val document = Toml.parse(source, options)
        require(!document.hasErrors()) { "Invalid Forge TOML metadata." }
        val mods = (document.get("mods") as? TomlArray) ?: return null
        require(mods.size() <= 128) { "Too many mod identities." }
        val declared = (0 until mods.size()).mapNotNull { index ->
            val mod = mods.get(index) as? TomlTable ?: return@mapNotNull null
            mod.string("modId")?.takeIf { it.matches(safeId) }?.let { it to mod }
        }
        val (id, primary) = declared.firstOrNull() ?: return null
        require(declared.map { it.first }.distinct().size == declared.size) { "Duplicate mod identity." }
        val dependencies = document.get("dependencies") as? TomlTable
        val depends = linkedMapOf<String, String>()
        val breaks = linkedMapOf<String, String>()
        var count = 0
        for ((owner, _) in declared) {
            val requirements = dependencies?.get(listOf(owner)) as? TomlArray ?: continue
            require(requirements.size() <= 512 - count) { "Too many mod dependencies." }
            count += requirements.size()
            for (index in 0 until requirements.size()) {
                val requirement = requirements.get(index) as? TomlTable ?: continue
                val target = requirement.string("modId")?.takeIf { it.matches(safeId) } ?: continue
                val side = requirement.string("side")?.uppercase() ?: "BOTH"
                if (side !in setOf("BOTH", "CLIENT")) continue
                val type = requirement.string("type")?.lowercase()
                val range = requirement.string("versionRange")?.take(180) ?: "*"
                val destination = when {
                    type == "required" || (type == null && requirement.get("mandatory") == true) -> depends
                    type == "incompatible" -> breaks
                    else -> continue
                }
                // Keep both constraints from bundled mods; never silently choose one.
                val previous = destination[target]
                destination[target] = if (previous == null || previous == range) range else "($previous) && ($range)"
            }
        }
        return LocalModMetadata(id, primary.string("displayName")?.takeIf { it.isNotBlank() }?.take(120) ?: id,
            primary.string("version")?.takeIf { it.isNotBlank() }?.take(120) ?: "unknown", null,
            depends, breaks, providedIds = declared.drop(1).map { it.first }.toSet())
    }

    private fun TomlTable.string(key: String): String? = get(key) as? String
}

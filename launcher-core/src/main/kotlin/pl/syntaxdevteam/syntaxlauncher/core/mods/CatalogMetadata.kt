package pl.syntaxdevteam.syntaxlauncher.core.mods

data class CatalogCategory(val id: String, val name: String)

data class CatalogRelease(val id: String, val name: String, val channel: String,
    val gameVersions: List<String>, val loaders: List<String>, val published: String,
    val downloadable: Boolean = true, val distributionBlocked: Boolean = false)

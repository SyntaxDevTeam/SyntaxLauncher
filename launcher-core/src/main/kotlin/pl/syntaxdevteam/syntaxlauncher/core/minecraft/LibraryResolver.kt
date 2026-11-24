package pl.syntaxdevteam.syntaxlauncher.core.minecraft

data class ResolvedMinecraftLibrary(
    val name: String,
    val classpathArtifact: MinecraftArtifact?,
    val nativeArtifact: MinecraftArtifact?,
    val nativeExcludes: List<String>,
)

class LibraryResolver {
    fun resolve(
        libraries: List<MinecraftLibrary>,
        platform: MinecraftPlatform,
        features: Map<String, Boolean> = emptyMap(),
    ): List<ResolvedMinecraftLibrary> =
        libraries.mapNotNull { resolve(it, platform, features) }

    fun resolve(
        library: MinecraftLibrary,
        platform: MinecraftPlatform,
        features: Map<String, Boolean> = emptyMap(),
    ): ResolvedMinecraftLibrary? {
        if (!MinecraftRuleEvaluator.isAllowed(library.rules, platform, features)) return null

        val classifier = library.natives[platform.os.nativeKey]
            ?.replace("${'$'}{arch}", platform.architecture.classifierToken)
        val nativeArtifact = classifier?.let(library.downloads.classifiers::get)

        return ResolvedMinecraftLibrary(
            name = library.name,
            classpathArtifact = library.downloads.artifact,
            nativeArtifact = nativeArtifact,
            nativeExcludes = library.nativeExcludes,
        )
    }
}

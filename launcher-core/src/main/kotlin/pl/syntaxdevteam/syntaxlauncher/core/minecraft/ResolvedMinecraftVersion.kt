package pl.syntaxdevteam.syntaxlauncher.core.minecraft

data class MinecraftArtifact(
    val path: String? = null,
    val sha1: String,
    val size: Long,
    val url: String,
)

data class MinecraftAssetIndex(
    val id: String,
    val sha1: String,
    val size: Long,
    val totalSize: Long,
    val url: String,
)

data class JavaRuntimeRequirement(
    val component: String,
    val majorVersion: Int,
)

data class MinecraftLibraryDownloads(
    val artifact: MinecraftArtifact? = null,
    val classifiers: Map<String, MinecraftArtifact> = emptyMap(),
)

data class MinecraftLibrary(
    val name: String,
    val downloads: MinecraftLibraryDownloads,
    val natives: Map<String, String> = emptyMap(),
    val rules: List<MinecraftRule> = emptyList(),
    val nativeExcludes: List<String> = emptyList(),
)

data class MinecraftArgument(
    val values: List<String>,
    val rules: List<MinecraftRule> = emptyList(),
)

data class MinecraftArguments(
    val game: List<MinecraftArgument> = emptyList(),
    val jvm: List<MinecraftArgument> = emptyList(),
    val legacyGameArguments: String? = null,
) {
    fun resolveGame(
        platform: MinecraftPlatform,
        features: Map<String, Boolean> = emptyMap(),
    ): List<String> =
        if (game.isNotEmpty()) {
            game.resolve(platform, features)
        } else {
            legacyGameArguments?.let(CommandLineTokenizer::tokenize).orEmpty()
        }

    fun resolveJvm(
        platform: MinecraftPlatform,
        features: Map<String, Boolean> = emptyMap(),
    ): List<String> = jvm.resolve(platform, features)

    private fun List<MinecraftArgument>.resolve(
        platform: MinecraftPlatform,
        features: Map<String, Boolean>,
    ): List<String> =
        flatMap { argument ->
            if (MinecraftRuleEvaluator.isAllowed(argument.rules, platform, features)) {
                argument.values
            } else {
                emptyList()
            }
        }
}

data class MinecraftLoggingFile(
    val id: String,
    val sha1: String,
    val size: Long,
    val url: String,
)

data class MinecraftLoggingConfiguration(
    val argument: String,
    val file: MinecraftLoggingFile,
)

data class ResolvedMinecraftVersion(
    val id: String,
    val type: MinecraftVersionType,
    val mainClass: String,
    val assetsId: String?,
    val assetIndex: MinecraftAssetIndex?,
    val clientDownload: MinecraftArtifact?,
    val libraries: List<MinecraftLibrary>,
    val arguments: MinecraftArguments,
    val javaRequirement: JavaRuntimeRequirement?,
    val logging: MinecraftLoggingConfiguration?,
    val inheritsFrom: String? = null,
)

object CommandLineTokenizer {
    fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        fun flush() {
            if (current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
        }

        input.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                quote != null && char == quote -> quote = null
                quote == null && (char == '"' || char == '\'') -> quote = char
                quote == null && char.isWhitespace() -> flush()
                else -> current.append(char)
            }
        }

        if (escaped) current.append('\\')
        flush()
        return result
    }
}

package pl.syntaxdevteam.syntaxlauncher.core.launch

import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType

class LaunchPlanBuilder(
    private val launcherName: String = "SyntaxLauncher",
    private val launcherVersion: String = "0.1",
    private val placeholderResolver: MinecraftArgumentPlaceholderResolver = MinecraftArgumentPlaceholderResolver(),
) {
    fun build(request: LaunchRequest): LaunchPlan {
        val prepared = request.prepared
        val version = prepared.version
        val classpath = prepared.filePlan.classpath
        if (classpath.isEmpty()) {
            throw MissingLaunchArtifactException("Minecraft classpath is empty for ${version.id}")
        }

        val separator = when (request.platform.os) {
            MinecraftOperatingSystem.WINDOWS -> ";"
            else -> ":"
        }
        val classpathValue = classpath.joinToString(separator) { it.toString() }
        val compactUuid = request.account.uuid.toString().replace("-", "")
        val assetIndexName = version.assetIndex?.id ?: version.assetsId.orEmpty()
        val legacyGameAssets = prepared.assetsDirectory
            .resolve("virtual")
            .resolve(version.assetsId ?: assetIndexName.ifBlank { "legacy" })

        val placeholders = linkedMapOf(
            "auth_player_name" to request.account.username,
            "version_name" to version.id,
            "game_directory" to prepared.gameDirectory.toString(),
            "assets_root" to prepared.assetsDirectory.toString(),
            "game_assets" to legacyGameAssets.toString(),
            "assets_index_name" to assetIndexName,
            "auth_uuid" to compactUuid,
            "auth_access_token" to request.authentication.accessToken,
            "auth_session" to request.authentication.sessionId,
            "user_type" to request.authentication.userType,
            "user_properties" to request.authentication.userPropertiesJson,
            "version_type" to version.type.launchValue(),
            "natives_directory" to prepared.nativesDirectory.toString(),
            "launcher_name" to launcherName,
            "launcher_version" to launcherVersion,
            "classpath" to classpathValue,
            "library_directory" to prepared.librariesDirectory.toString(),
            "classpath_separator" to separator,
            "auth_xuid" to request.authentication.xuid,
            "clientid" to request.authentication.clientId,
        )

        val metadataJvm = version.arguments.resolveJvm(request.platform, request.features)
        val resolvedMetadataJvm = placeholderResolver.resolveAll(metadataJvm, placeholders)
        val jvmArguments = buildList {
            add("-Xms${request.options.minimumMemoryMiB}M")
            add("-Xmx${request.options.maximumMemoryMiB}M")

            if (resolvedMetadataJvm.isEmpty()) {
                add("-Djava.library.path=${prepared.nativesDirectory}")
                add("-cp")
                add(classpathValue)
            } else {
                addAll(resolvedMetadataJvm)
            }

            version.logging?.let { logging ->
                val config = prepared.filePlan.loggingConfig
                    ?: throw MissingLaunchArtifactException(
                        "logging configuration '${logging.file.id}' for ${version.id}",
                    )
                add(
                    placeholderResolver.resolve(
                        logging.argument,
                        placeholders + ("path" to config.target.toString()),
                    ),
                )
            }

            addAll(request.options.extraJvmArguments)
        }

        val rawGameArguments = version.arguments.resolveGame(request.platform, request.features)
        val gameArguments = placeholderResolver.resolveAll(rawGameArguments, placeholders)

        return LaunchPlan(
            runtime = request.runtime,
            workingDirectory = prepared.gameDirectory,
            mainClass = version.mainClass,
            jvmArguments = jvmArguments,
            gameArguments = gameArguments,
            sensitiveOutput = request.authentication.userType == "msa",
        )
    }

    private fun MinecraftVersionType.launchValue(): String =
        when (this) {
            MinecraftVersionType.RELEASE -> "release"
            MinecraftVersionType.SNAPSHOT -> "snapshot"
            MinecraftVersionType.OLD_BETA -> "old_beta"
            MinecraftVersionType.OLD_ALPHA -> "old_alpha"
            MinecraftVersionType.UNKNOWN -> "unknown"
        }
}

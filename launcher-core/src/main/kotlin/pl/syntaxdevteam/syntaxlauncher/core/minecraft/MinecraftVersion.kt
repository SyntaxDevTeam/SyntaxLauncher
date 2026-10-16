package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.time.Instant

enum class MinecraftVersionType {
    RELEASE,
    SNAPSHOT,
    OLD_BETA,
    OLD_ALPHA,
    UNKNOWN,
}

data class MinecraftVersion(
    val id: String,
    val type: MinecraftVersionType,
    val metadataUrl: String,
    val sha1: String,
    val complianceLevel: Int,
    val releaseTime: Instant,
    val updatedAt: Instant,
)

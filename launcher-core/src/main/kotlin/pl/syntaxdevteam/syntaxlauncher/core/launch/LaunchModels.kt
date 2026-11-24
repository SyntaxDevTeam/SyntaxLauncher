package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.time.Instant
import pl.syntaxdevteam.syntaxlauncher.core.account.Account
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumAccount
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.PreparedMinecraftVersion

data class LaunchOptions(
    val minimumMemoryMiB: Int = 512,
    val maximumMemoryMiB: Int = 4096,
    val extraJvmArguments: List<String> = emptyList(),
) {
    init {
        require(minimumMemoryMiB > 0) { "Minimum memory must be greater than zero." }
        require(maximumMemoryMiB >= minimumMemoryMiB) {
            "Maximum memory must be greater than or equal to minimum memory."
        }
    }
}

data class LaunchAuthentication(
    val accessToken: String,
    val sessionId: String,
    val userType: String,
    val userPropertiesJson: String = "{}",
    val clientId: String = "",
    val xuid: String = "",
    val expiresAt: Instant,
) {
    companion object {
        fun offline(): LaunchAuthentication = LaunchAuthentication(
            accessToken = "0",
            sessionId = "0",
            userType = "legacy",
            expiresAt = Instant.MAX,
        )
    }

    internal fun isValidFor(account: Account, now: Instant): Boolean = when (account) {
        is OfflineAccount -> this == offline()
        is PremiumAccount -> userType == "msa" && accessToken.isNotBlank() && accessToken != "0" &&
            clientId.isNotBlank() && sessionId.isNotBlank() && expiresAt.isAfter(now)
        else -> false
    }

    override fun toString() = "LaunchAuthentication(type=" + userType + ", credentials=[REDACTED])"

}

data class LaunchRequest(
    val account: Account,
    val prepared: PreparedMinecraftVersion,
    val runtime: JavaRuntime,
    val platform: MinecraftPlatform,
    val options: LaunchOptions,
    val authentication: LaunchAuthentication,
    val features: Map<String, Boolean> = emptyMap(),
)

package pl.syntaxdevteam.syntaxlauncher.core.launch

import pl.syntaxdevteam.syntaxlauncher.core.account.Account
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
) {
    override fun toString() = "LaunchAuthentication(type=" + userType + ", credentials=[REDACTED])"

    companion object {
        fun offline(account: Account): LaunchAuthentication {
            require(account !is PremiumAccount) { "Premium account requires a verified session." }
            val compactUuid = account.uuid.toString().replace("-", "")
            return LaunchAuthentication(
                accessToken = "0",
                sessionId = "token:0:$compactUuid",
                userType = "legacy",
            )
        }
    }
}

data class LaunchRequest(
    val account: Account,
    val prepared: PreparedMinecraftVersion,
    val runtime: JavaRuntime,
    val platform: MinecraftPlatform,
    val options: LaunchOptions,
    val authentication: LaunchAuthentication = LaunchAuthentication.offline(account),
    val features: Map<String, Boolean> = emptyMap(),
)

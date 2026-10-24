package pl.syntaxdevteam.syntaxlauncher.core.account

import java.time.Instant
import java.util.UUID
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchAuthentication

/** A premium identity must come from a verified Minecraft Java profile. */
data class PremiumAccount(override val username: String, override val uuid: UUID) : Account {
    init { require(Regex("[A-Za-z0-9_]{3,16}").matches(username)) }

    companion object {
        fun fromProfile(id: String, name: String): PremiumAccount {
            require(Regex("[0-9a-fA-F]{32}").matches(id))
            return PremiumAccount(name, UUID.fromString(id.replace(
                Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5")))
        }
    }
}

/** A non-data class avoids accidental leaks through generated toString/copy. */
class PremiumSession(
    val account: PremiumAccount,
    val minecraftToken: String,
    val expiresAt: Instant,
    val microsoftRefreshToken: String?,
) {
    init { require(minecraftToken.isNotBlank()) }
    fun launchAuthentication(clientId: String): LaunchAuthentication =
        LaunchAuthentication(
            accessToken = minecraftToken,
            sessionId = "token:" + minecraftToken + ":" + account.uuid.toString().replace("-", ""),
            userType = "msa",
            clientId = clientId,
            expiresAt = expiresAt,
        )
    override fun toString() = "PremiumSession([REDACTED])"
}

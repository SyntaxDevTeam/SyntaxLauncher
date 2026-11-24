package pl.syntaxdevteam.syntaxlauncher.core.account

import java.nio.charset.StandardCharsets
import java.util.UUID

@ConsistentCopyVisibility
data class OfflineAccount private constructor(
    override val username: String,
    override val uuid: UUID,
) : Account {

    companion object {
        private val usernamePattern = Regex("^[A-Za-z0-9_]{3,16}$")

        fun create(username: String): OfflineAccount {
            val normalized = username.trim()
            require(isValidUsername(normalized)) {
                "Offline Minecraft username must contain 3-16 letters, digits or underscores."
            }

            return OfflineAccount(
                username = normalized,
                uuid = offlineUuid(normalized),
            )
        }

        fun isValidUsername(username: String): Boolean =
            usernamePattern.matches(username)

        fun offlineUuid(username: String): UUID =
            UUID.nameUUIDFromBytes(
                "OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8),
            )
    }
}

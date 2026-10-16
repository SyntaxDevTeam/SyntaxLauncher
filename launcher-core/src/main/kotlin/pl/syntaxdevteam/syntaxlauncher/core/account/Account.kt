package pl.syntaxdevteam.syntaxlauncher.core.account

import java.util.UUID

/**
 * Identity used by the launch layer.
 *
 * v0.1 provides only [OfflineAccount]. Premium accounts will implement the same
 * boundary later without duplicating the Minecraft launch pipeline.
 */
interface Account {
    val username: String
    val uuid: UUID
}

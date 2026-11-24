package pl.syntaxdevteam.syntaxlauncher.core.account

import java.util.UUID

/** Minecraft player identity. */
interface Account {
    val username: String
    val uuid: UUID
}

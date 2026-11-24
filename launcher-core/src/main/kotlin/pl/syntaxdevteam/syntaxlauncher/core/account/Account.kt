package pl.syntaxdevteam.syntaxlauncher.core.account

import java.util.UUID

/** Identity for Microsoft Java account gameplay. */
interface Account {
    val username: String
    val uuid: UUID
}

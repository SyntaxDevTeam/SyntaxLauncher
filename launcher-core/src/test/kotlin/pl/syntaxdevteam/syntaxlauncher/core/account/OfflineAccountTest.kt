package pl.syntaxdevteam.syntaxlauncher.core.account

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OfflineAccountTest {
    @Test
    fun `creates deterministic vanilla-compatible offline UUID`() {
        val account = OfflineAccount.create("Notch")

        assertEquals("Notch", account.username)
        assertEquals(UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f"), account.uuid)
        assertEquals(account.uuid, OfflineAccount.create("Notch").uuid)
    }

    @Test
    fun `accepts normal Minecraft username`() {
        assertTrue(OfflineAccount.isValidUsername("Adrian_26"))
    }

    @Test
    fun `rejects malformed username`() {
        assertFailsWith<IllegalArgumentException> {
            OfflineAccount.create("a!")
        }
    }
}

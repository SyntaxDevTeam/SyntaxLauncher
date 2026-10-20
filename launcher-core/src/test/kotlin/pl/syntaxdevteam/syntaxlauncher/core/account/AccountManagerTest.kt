package pl.syntaxdevteam.syntaxlauncher.core.account

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Properties
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AccountManagerTest {
    @TempDir lateinit var root: Path
    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val profile = PremiumAccount.fromProfile("12345678123412341234123456789abc", "PremiumSteve")
    private class Vault : RefreshTokenVault {
        val entries = mutableMapOf<String, String>()
        var locked = false
        override fun read(key: String): String? { check(!locked); return entries[key] }
        override fun write(key: String, token: String) { check(!locked); entries[key] = token }
        override fun remove(key: String) { check(!locked); entries.remove(key) }
    }
    private fun session(refresh: String? = "private-refresh", expiry: Instant = now.plusSeconds(3600)) =
        PremiumSession(profile, "private-access", expiry, refresh)
    private fun manager(vault: Vault, refresh: suspend (String) -> PremiumSession = { session() }) =
        AccountManager(root.resolve("accounts.properties"), vault, "owned-client", refresh,
            Clock.fixed(now, ZoneOffset.UTC))

    @Test fun multipleAccountsSurviveRestartWithoutPlaintextCredentials() = runBlocking {
        val vault = Vault()
        val first = manager(vault)
        first.saveOffline("OfflineSteve")
        first.saveOffline("OfflineAlex")
        first.savePremium(session())
        first.savePremium(session())
        val restarted = manager(vault)
        assertEquals(3, restarted.accounts().size)
        assertEquals(profile, restarted.session(profile.uuid.toString()).account)
        assertFalse(Files.readString(root.resolve("accounts.properties")).contains("private-"))
        restarted.remove(profile.uuid.toString())
        assertTrue(vault.entries.isEmpty())
        assertEquals(2, manager(vault).accounts().size)
    }

    @Test fun rotationIsSavedAndMissingRotationRetainsOldCredential() = runBlocking {
        val vault = Vault()
        manager(vault).savePremium(session())
        var calls = 0
        val restarted = manager(vault) { token ->
            assertEquals("private-refresh", token)
            calls++
            session("rotated-secret")
        }
        restarted.session(profile.uuid.toString())
        restarted.session(profile.uuid.toString())
        assertEquals(1, calls)
        assertEquals("rotated-secret", vault.entries.values.single())
        manager(vault) { session(null) }.session(profile.uuid.toString())
        assertEquals("rotated-secret", vault.entries.values.single())
    }

    @Test fun expiringSessionRefreshesAndRejectsChangedIdentity() = runBlocking {
        val vault = Vault()
        val service = manager(vault) {
            PremiumSession(PremiumAccount.fromProfile("abcdefabcdefabcdefabcdefabcdefab", "OtherPlayer"),
                "different-private", now.plusSeconds(3600), "wrong-refresh")
        }
        service.savePremium(session(expiry = now.plusSeconds(179)))
        assertFailsWith<IllegalStateException> { service.session(profile.uuid.toString()) }
        assertEquals("private-refresh", vault.entries.values.single())
    }

    @Test fun refreshFailureAndMissingCredentialNeverSupplyOfflineFallback() = runBlocking {
        val vault = Vault()
        manager(vault).savePremium(session())
        assertFailsWith<IllegalStateException> {
            manager(vault) { error("Microsoft rejected refresh. Sign in again.") }.session(profile.uuid.toString())
        }
        vault.entries.clear()
        val error = assertFailsWith<IllegalStateException> { manager(vault).session(profile.uuid.toString()) }
        assertTrue(error.message!!.contains("Sign in again"))
        assertEquals(1, manager(vault).accounts().size)
    }

    @Test fun lockedVaultCannotPublishAccountOrClaimSignOut() = runBlocking {
        val vault = Vault()
        val service = manager(vault)
        vault.locked = true
        assertFailsWith<IllegalStateException> { service.savePremium(session()) }
        assertTrue(service.accounts().isEmpty())
        vault.locked = false
        service.savePremium(session())
        vault.locked = true
        assertFailsWith<IllegalStateException> { service.remove(profile.uuid.toString()) }
        assertEquals(1, service.accounts().size)
    }

    @Test fun unknownSchemasAndUnknownFieldsArePreserved() = runBlocking {
        val vault = Vault()
        val service = manager(vault)
        val file = root.resolve("accounts.properties")
        service.saveOffline("OfflineSteve")
        val props = Properties().apply { Files.newInputStream(file).use { load(it) } }
        props.setProperty("future.field", "keep")
        Files.newOutputStream(file).use { props.store(it, "fixture") }
        service.saveOffline("OfflineAlex")
        assertTrue(Files.readString(file).contains("future.field=keep"))
        Files.writeString(file, "schema_version=99\nfuture.field=keep\n")
        val before = Files.readString(file)
        assertFailsWith<IllegalStateException> { service.savePremium(session()) }
        assertEquals(before, Files.readString(file))
        assertTrue(vault.entries.isEmpty())
    }

    @Test fun concurrentRefreshAndRemovalNeverResurrectSignedOutAccount() = runBlocking {
        val vault = Vault()
        manager(vault).savePremium(session())
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val service = manager(vault) { started.complete(Unit); finish.await(); session("rotated") }
        val refreshing = async { service.session(profile.uuid.toString()) }
        started.await()
        val removing = async { service.remove(profile.uuid.toString()) }
        finish.complete(Unit)
        refreshing.await(); removing.await()
        assertTrue(service.accounts().isEmpty())
        assertTrue(vault.entries.isEmpty())
    }
}

package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import pl.syntaxdevteam.syntaxlauncher.core.account.*
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.*

class DesktopLauncherSessionAccountsTest {
    private val id = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private fun premium(name: String = "PlayerOld") = PremiumSession(
        PremiumAccount(name, id), "fixture-access", Instant.now().plusSeconds(3600), "fixture-refresh")

    private class Vault : RefreshTokenVault {
        val tokens = mutableMapOf<String, String>()
        var locked = false
        override fun read(key: String): String? { check(!locked) { "Unlock credential store." }; return tokens[key] }
        override fun write(key: String, token: String) { check(!locked) { "Unlock credential store." }; tokens[key] = token }
        override fun remove(key: String) { check(!locked) { "Unlock credential store." }; tokens.remove(key) }
    }

    private inner class Fixture(val root: Path = Files.createTempDirectory("desktop-accounts-"), val vault: Vault = Vault()) {
        val file = root.resolve("accounts.properties")
        var refresh: suspend (String) -> PremiumSession = { premium("PlayerNew") }
        val provider = object : MicrosoftSignInService {
            override val clientId = "fixture-public-app"
            override suspend fun signIn() = premium()
            override suspend fun refresh(refreshToken: String) = refresh.invoke(refreshToken)
        }
        val store = AccountManager(file, vault, provider.clientId, { provider.refresh(it) })
        val bytes = "fixture-client".toByteArray()
        val http = HttpClient(MockEngine { respond(bytes) })
        val layout = MinecraftInstallationLayout(root.resolve("game"), root.resolve("versions"),
            root.resolve("libraries"), root.resolve("assets"), root.resolve("indexes"),
            root.resolve("objects"), root.resolve("logging"), root.resolve("natives"))
        val captured = CompletableDeferred<LaunchPlan>()
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
        val processes = MinecraftProcessManager(root.resolve("logs")) { plan, stdout, stderr ->
            captured.complete(plan)
            ProcessBuilder(java.toString(), "-version").redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
        }
        val runtime = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener) = JavaRuntime(java.parent.parent, java, 21, "21")
        }
        val coordinator = MinecraftLaunchCoordinator(MinecraftPreparationService(DownloadManager(http)),
            runtime, LaunchPlanBuilder(), processes, layout, MinecraftPlatform.detect())
        val session = DesktopLauncherSession(MinecraftVersionCatalog(http), MinecraftVersionResolver(http), coordinator,
            processes, LauncherPreferencesStore(root.resolve("settings.properties")),
            MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"), layout), layout, http,
            microsoftSignIn = provider, accounts = store)
        val version = ResolvedMinecraftVersion("fixture", MinecraftVersionType.RELEASE, "test.Main", null, null,
            MinecraftArtifact(sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) },
                size = bytes.size.toLong(), url = "https://fixture.invalid/client.jar"), emptyList(),
            MinecraftArguments(jvm = listOf(MinecraftArgument(listOf("-cp", "\${classpath}"))),
                game = listOf(MinecraftArgument(listOf("--username", "\${auth_player_name}", "--userType", "\${user_type}")))),
            JavaRuntimeRequirement("fixture", 21), null)
        suspend fun close() {
            val done = CompletableDeferred<Unit>()
            session.close { done.complete(Unit) }
            withTimeout(5000) { done.await() }
        }
        suspend fun awaitAccountPreparation() = withTimeout(5000) {
            while (session.preparingAccount.value) delay(10)
        }
    }

    @Test fun savedIdentitiesSurviveWindowRestartWithoutReadingVaultUntilPlay() = runBlocking {
        val first = Fixture()
        try {
            first.store.saveOffline("ChildOne")
            first.session.addPremiumAccount()
            assertEquals(2, first.session.savedAccounts.value.size)
        } finally { first.close() }
        val restarted = Fixture(first.root, first.vault)
        try {
            restarted.vault.locked = true
            restarted.session.loadAccounts()
            assertEquals(listOf("ChildOne", "PlayerOld"), restarted.session.savedAccounts.value.map { it.account.username })
            assertEquals("PlayerOld", restarted.session.premiumAccounts.value.single().username)
            val disk = Files.readString(restarted.file)
            assertFalse(disk.contains("fixture-refresh"))
            assertFalse(disk.contains("fixture-access"))
        } finally { restarted.close() }
        assertEquals(1, first.vault.tokens.size, "Closing the launcher must preserve the saved login")
    }

    @Test fun failedVaultWritesAndRemovalDoNotPublishFalseSuccess() = runBlocking {
        val f = Fixture()
        try {
            f.vault.locked = true
            assertFailsWith<IllegalStateException> { f.session.addPremiumAccount() }
            assertTrue(f.session.premiumAccounts.value.isEmpty())
            f.vault.locked = false
            f.session.addPremiumAccount()
            f.vault.locked = true
            assertFailsWith<IllegalStateException> { f.session.removePremiumAccount(id.toString()) }
            assertEquals(1, f.session.premiumAccounts.value.size)
            f.vault.locked = false
            f.session.removePremiumAccount(id.toString())
            assertTrue(f.session.premiumAccounts.value.isEmpty())
            assertTrue(f.vault.tokens.isEmpty())
        } finally { f.close() }
    }

    @Test fun restartedLoginRefreshesAndLaunchesWithCurrentVerifiedName() = runBlocking {
        val first = Fixture()
        try { first.session.addPremiumAccount() } finally { first.close() }
        val f = Fixture(first.root, first.vault)
        try {
            val refreshed = CompletableDeferred<String>()
            f.refresh = { token -> refreshed.complete(token); premium("PlayerNew") }
            f.session.loadAccounts()
            assertTrue(f.session.play(f.version, "OfflineChild", 2048, premiumId = id.toString()))
            val plan = withTimeout(10000) { f.captured.await() }
            f.awaitAccountPreparation()
            assertEquals("fixture-refresh", refreshed.await())
            assertTrue("PlayerNew" in plan.gameArguments)
            assertFalse("PlayerOld" in plan.gameArguments)
            assertFalse("OfflineChild" in plan.gameArguments)
            assertTrue("msa" in plan.gameArguments)
            assertEquals("PlayerNew", f.session.premiumAccounts.value.single().username)
        } finally { f.close() }
    }

    @Test fun failedRefreshNeverStartsOfflineAndCanBeRetried() = runBlocking {
        val first = Fixture()
        try { first.session.addPremiumAccount() } finally { first.close() }
        val f = Fixture(first.root, first.vault)
        try {
            f.session.loadAccounts()
            f.refresh = { error("Sign in again.") }
            assertTrue(f.session.play(f.version, "OfflineChild", 2048, premiumId = id.toString()))
            f.awaitAccountPreparation()
            assertEquals("Sign in again.", f.session.launchIssue.value)
            assertFalse(f.captured.isCompleted)
            assertEquals(MinecraftProcessState.IDLE, f.processes.status.value.state)
            f.refresh = { premium("PlayerNew") }
            assertTrue(f.session.play(f.version, "OfflineChild", 2048, premiumId = id.toString()))
            withTimeout(10000) { f.captured.await() }
            f.awaitAccountPreparation()
            assertNull(f.session.launchIssue.value)
        } finally { f.close() }
    }

    @Test fun offlinePlaySavesPlayerWithoutAccessingCredentialStore() = runBlocking {
        val f = Fixture()
        try {
            f.vault.locked = true
            assertTrue(f.session.play(f.version, "ChildOne", 2048))
            withTimeout(10000) { f.captured.await() }
            f.awaitAccountPreparation()
            assertEquals("ChildOne", f.session.savedAccounts.value.single().account.username)
            assertTrue(f.vault.tokens.isEmpty())
        } finally { f.close() }
    }
}

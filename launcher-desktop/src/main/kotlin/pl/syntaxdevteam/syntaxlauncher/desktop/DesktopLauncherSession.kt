package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumAccount
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumSession
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchAuthentication
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderRegistry
import pl.syntaxdevteam.syntaxlauncher.core.mods.LocalModManager
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchOptions
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftLaunchCoordinator
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessManager
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftShutdownPolicy
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionResolver
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferences
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore

/**
 * Window lifetime: download jobs survive recompositions but not launcher
 * shutdown. DETACH never stops a Minecraft process that has already launched.
 */
class DesktopLauncherSession(
    val versionCatalog: MinecraftVersionCatalog,
    val versionResolver: MinecraftVersionResolver,
    val coordinator: MinecraftLaunchCoordinator,
    val processManager: MinecraftProcessManager,
    val preferences: LauncherPreferencesStore,
    val instances: MinecraftInstanceRepository,
    private val legacyLayout: MinecraftInstallationLayout,
    private val httpClient: HttpClient,
    val loaders: ModLoaderRegistry? = null,
    private val microsoftSignIn: DesktopMicrosoftSignIn? = null,
) {
    val modManager = LocalModManager(instances)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val closing = AtomicBoolean(false)
    private var launchJob: Job? = null
    private var settingsJob: Job? = null
    private var pendingPreferences: LauncherPreferences? = null
    private val mutableLaunchIssue = MutableStateFlow<String?>(null)
    val launchIssue = mutableLaunchIssue.asStateFlow()
    private val premiumSessions = mutableMapOf<String, PremiumSession>()
    private val mutablePremiumAccounts = MutableStateFlow<List<PremiumAccount>>(emptyList())
    val premiumAccounts: StateFlow<List<PremiumAccount>> = mutablePremiumAccounts.asStateFlow()
    val microsoftEnabled: Boolean get() = microsoftSignIn != null

    /** Premium tokens stay in memory until an OS secure vault is implemented. */
    suspend fun addPremiumAccount(): PremiumAccount {
        val browser = microsoftSignIn
            ?: throw IllegalStateException("Configure SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID first.")
        val session = browser.signIn()
        synchronized(lock) {
            check(!closing.get()) { "SyntaxLauncher is closing." }
            premiumSessions[session.account.uuid.toString()] = session
            mutablePremiumAccounts.value = premiumSessions.values.map { it.account }
        }
        return session.account
    }

    fun removePremiumAccount(uuid: String) {
        synchronized(lock) {
            premiumSessions.remove(uuid)
            mutablePremiumAccounts.value = premiumSessions.values.map { it.account }
        }
    }

    fun rememberPreferences(value: LauncherPreferences) {
        synchronized(lock) {
            if (closing.get()) return
            pendingPreferences = value
            settingsJob?.cancel()
            settingsJob = scope.launch {
                delay(400)
                runCatching { preferences.save(value) }
            }
        }
    }

    fun play(
        version: ResolvedMinecraftVersion,
        nickname: String,
        maximumMemoryMiB: Int,
        instance: MinecraftInstance? = null,
        premiumId: String? = null,
    ): Boolean {
        synchronized(lock) {
            if (closing.get() || launchJob?.isActive == true ||
                processManager.status.value.state in setOf(
                    MinecraftProcessState.RUNNING, MinecraftProcessState.STARTING,
                )
            ) return false
            val account = if (premiumId != null) {
                premiumSessions[premiumId]?.account ?: return false
            } else OfflineAccount.create(nickname)
            mutableLaunchIssue.value = null
            launchJob = scope.launch {
                var leased = false
                try {
                    val authentication = if (premiumId != null) {
                        val saved = synchronized(lock) { premiumSessions[premiumId] }
                            ?: throw IllegalStateException("Microsoft account was removed. Sign in again.")
                        val current = if (saved.expiresAt.isBefore(Instant.now().plusSeconds(180))) {
                            val refreshed = microsoftSignIn?.refresh(saved)
                                ?: throw IllegalStateException("Microsoft sign-in is not configured.")
                            check(refreshed.account.uuid == saved.account.uuid) {
                                "Microsoft account changed during refresh; sign in again."
                            }
                            synchronized(lock) {
                                premiumSessions[premiumId] = refreshed
                                mutablePremiumAccounts.value = premiumSessions.values.map { it.account }
                            }
                            refreshed
                        } else saved
                        current.launchAuthentication(microsoftSignIn!!.clientId)
                    } else LaunchAuthentication.offline(account)
                    instances.beginLaunch(instance?.id)
                    leased = true
                    coordinator.launch(
                        version = version,
                        account = account,
                        options = LaunchOptions(
                            minimumMemoryMiB = instance?.minimumMemoryMiB ?: 512,
                            maximumMemoryMiB = maximumMemoryMiB,
                            extraJvmArguments = instance?.extraJvmArguments.orEmpty(),
                        ),
                        instanceLayout = instance?.let(instances::layoutFor) ?: legacyLayout,
                        authentication = authentication,
                        loader = instance?.loader,
                    )
                    // Transfer the preparing lease to the child under NonCancellable,
                    // so closing the launcher between process.start() and hand-off is safe.
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableLaunchIssue.value = error.message ?: "Could not protect the active instance."
                } finally {
                    if (leased) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            val running = processManager.status.value
                            val gamePid = running.pid
                            if (running.state == MinecraftProcessState.RUNNING && gamePid != null) {
                                try {
                                    instances.markGameStarted(instance?.id, gamePid)
                                } catch (error: Exception) {
                                    mutableLaunchIssue.value = error.message
                                        ?: "Failed to protect the running game; stopping it."
                                    // Never leave a child game writing into an unprotected profile.
                                    runCatching { processManager.terminate() }
                                }
                            }
                            runCatching { instances.endPreparation(instance?.id) }
                        }
                    }
                }
            }
            return true
        }
    }

    fun cancelPreparation() {
        synchronized(lock) { launchJob?.cancel() }
    }

    fun stopGame() {
        if (closing.get()) return
        scope.launch { processManager.terminate() }
    }

    fun close(onClosed: () -> Unit) {
        if (!closing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val current = synchronized(lock) { launchJob }
                current?.cancelAndJoin()
                val pending = synchronized(lock) { pendingPreferences }
                settingsJob?.cancelAndJoin()
                pending?.let { runCatching { preferences.save(it) } }
                processManager.shutdown(MinecraftShutdownPolicy.DETACH)
                synchronized(lock) {
                    premiumSessions.clear()
                    mutablePremiumAccounts.value = emptyList()
                }
            } finally {
                httpClient.close()
                withContext(Dispatchers.Swing) { onClosed() }
                scope.cancel()
            }
        }
    }
}

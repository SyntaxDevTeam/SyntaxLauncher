package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Path
import pl.syntaxdevteam.syntaxlauncher.core.diagnostics.MinecraftDiagnostics
import pl.syntaxdevteam.syntaxlauncher.core.diagnostics.MinecraftDiagnosticReport
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
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumAccount
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.syntaxdevteam.syntaxlauncher.core.account.AccountManager
import pl.syntaxdevteam.syntaxlauncher.core.account.SavedAccount
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderRegistry
import pl.syntaxdevteam.syntaxlauncher.core.mods.LocalModManager
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthCatalog
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstaller
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
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

/** Metadata retained for the *last attempted game*, independent of later UI selection. */
data class LastGameAttempt(
    val gameDirectory: Path,
    val gameVersion: String,
    val loader: pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection?,
    val instanceId: String?,
)

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
    private val microsoftSignIn: MicrosoftSignInService? = null,
    private val accounts: AccountManager? = null,
    val optiFine: pl.syntaxdevteam.syntaxlauncher.core.content.OptiFineCatalog? = null,
) {
    val packManager = pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackManager(instances)
    val resourcePackCatalog = pl.syntaxdevteam.syntaxlauncher.core.content.ModrinthResourcePacks(
        httpClient, instances, packManager, DownloadManager(httpClient))
    val modManager = LocalModManager(instances)
    val catalogIcons = pl.syntaxdevteam.syntaxlauncher.core.mods.CatalogIcons(httpClient)
    val modrinthCatalog = ModrinthCatalog(httpClient)
    val modrinthInstaller = ModrinthInstaller(instances, modManager, DownloadManager(httpClient))
    val shaderRenderer = pl.syntaxdevteam.syntaxlauncher.core.content.IrisRendererSetup(
        instances, modManager, modrinthCatalog, modrinthInstaller)
    val shaderPackCatalog = pl.syntaxdevteam.syntaxlauncher.core.content.ModrinthPackCatalog(
        httpClient, instances, packManager, DownloadManager(httpClient),
        kind = pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackKind.SHADER_PACK, renderer = shaderRenderer)
    val modpackInstaller = loaders?.let { registry -> pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackInstaller(
        instances, DownloadManager(httpClient), versionCatalog, versionResolver, registry,
        prepareGame = { version, layout, loader -> coordinator.prepareForInstallation(version, layout, loader) }) }
    val modpackImporter = modpackInstaller?.let { pl.syntaxdevteam.syntaxlauncher.core.modpack.ModrinthModpackImporter(it) }
    val modpackCatalog = modpackInstaller?.let { pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackCatalog(httpClient,
        DownloadManager(httpClient), it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val closing = AtomicBoolean(false)
    private var launchJob: Job? = null
    private var settingsJob: Job? = null
    private var pendingPreferences: LauncherPreferences? = null
    private val mutableLaunchIssue = MutableStateFlow<String?>(null)
    val launchIssue = mutableLaunchIssue.asStateFlow()
    @Volatile var lastGameAttempt: LastGameAttempt? = null
        private set
    private val diagnostics = MinecraftDiagnostics()

    /** Read bounded local game logs; never return raw log text, tokens or process arguments. */
    suspend fun diagnoseLastGame(): MinecraftDiagnosticReport? = withContext(Dispatchers.IO) {
        val attempt = lastGameAttempt ?: return@withContext null
        val failedPreparation = coordinator.status.value
            .takeIf { it.phase == pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchPhase.FAILED }
            ?.detail
        diagnostics.analyze(
            attempt.gameDirectory, attempt.gameVersion, attempt.loader?.id,
            processManager.status.value, failedPreparation)
    }

    /**
     * Explicit, safe repair: verified game/loader/Java preparation only.
     * Does not remove mods, saves, configs or replace the instance metadata.
     */
    suspend fun repairLastGameFiles() = withContext(Dispatchers.IO) {
        val attempt = lastGameAttempt ?: error("No game attempt to repair.")
        check(processManager.status.value.state !in setOf(
            MinecraftProcessState.RUNNING, MinecraftProcessState.STARTING)) {
            "Stop Minecraft before verifying its managed files."
        }
        val profile = attempt.instanceId?.let { instances.get(it) }
        val layout = profile?.let(instances::layoutFor) ?: legacyLayout
        check(layout.gameDir.toAbsolutePath().normalize() ==
            attempt.gameDirectory.toAbsolutePath().normalize()) {
            "The game directory changed since the failure."
        }
        check(profile == null || (profile.minecraftVersionId == attempt.gameVersion &&
            profile.loader == attempt.loader)) {
            "The game settings changed. Launch again before repairing."
        }
        val entry = versionCatalog.fetchManifest().versions
            .firstOrNull { it.id == attempt.gameVersion }
            ?: error("The Minecraft version is no longer present in Mojang metadata.")
        val resolved = versionResolver.resolve(entry)
        instances.beginLaunch(attempt.instanceId)
        try {
            coordinator.prepareForInstallation(resolved, layout, attempt.loader)
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                instances.endPreparation(attempt.instanceId)
            }
        }
    }
    private val accountMutex = Mutex()
    private val mutableSavedAccounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val savedAccounts = mutableSavedAccounts.asStateFlow()
    private val mutablePremiumAccounts = MutableStateFlow<List<PremiumAccount>>(emptyList())
    val premiumAccounts: StateFlow<List<PremiumAccount>> = mutablePremiumAccounts.asStateFlow()
    private val mutablePreparingAccount = MutableStateFlow(false)
    val preparingAccount = mutablePreparingAccount.asStateFlow()
    val microsoftEnabled: Boolean get() = microsoftSignIn != null && accounts != null

    private suspend fun publishAccounts() {
        val saved = accounts?.accounts().orEmpty()
        mutableSavedAccounts.value = saved
        mutablePremiumAccounts.value = saved.filter { it.microsoft }.map { it.account as PremiumAccount }
    }

    suspend fun loadAccounts() = accountMutex.withLock {
        check(!closing.get()) { "SyntaxLauncher is closing." }
        publishAccounts()
    }

    suspend fun addPremiumAccount(): PremiumAccount {
        val browser = microsoftSignIn
            ?: throw IllegalStateException("Configure SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID first.")
        val store = accounts ?: error("Account storage is unavailable.")
        val session = browser.signIn()
        accountMutex.withLock {
            check(!closing.get()) { "SyntaxLauncher is closing." }
            store.savePremium(session)
            publishAccounts()
        }
        return session.account
    }

    suspend fun removePremiumAccount(uuid: String) = accountMutex.withLock {
        check(!closing.get()) { "SyntaxLauncher is closing." }
        accounts?.remove(uuid) ?: error("Account storage is unavailable.")
        publishAccounts()
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
            if (premiumId == null || mutablePremiumAccounts.value.none { it.uuid.toString() == premiumId }) {
                mutableLaunchIssue.value = "Sign in with a Microsoft account entitled to Minecraft Java before playing."
                return false
            }
            lastGameAttempt = LastGameAttempt(
                gameDirectory = instance?.let(instances::gameDirectory) ?: legacyLayout.gameDir,
                gameVersion = version.id, loader = instance?.loader, instanceId = instance?.id)
            mutableLaunchIssue.value = null
            mutablePreparingAccount.value = true
            launchJob = scope.launch {
                var leased = false
                try {
                    val premium = accountMutex.withLock {
                            check(microsoftSignIn != null) { "Microsoft sign-in is not configured." }
                            val current = accounts?.session(premiumId, requireFresh = true)
                                ?: error("Account storage is unavailable.")
                            publishAccounts()
                            current
                    }
                    // Refresh may change the player's name; launch with the verified current profile.
                    val account = premium.account
                    val authentication = premium.launchAuthentication(microsoftSignIn!!.clientId)
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
            launchJob!!.invokeOnCompletion { mutablePreparingAccount.value = false }
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
                accountMutex.withLock {
                    mutableSavedAccounts.value = emptyList()
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

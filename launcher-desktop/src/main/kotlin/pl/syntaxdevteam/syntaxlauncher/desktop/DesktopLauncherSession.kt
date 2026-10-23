package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumAccount
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchAuthentication
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
    val requestedAt: Instant = Instant.now(),
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
    val launcherPaths: pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths? = null,
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
    suspend fun lastDiagnosticTarget(): Pair<ResolvedMinecraftVersion, MinecraftInstance?> {
        val attempt = lastGameAttempt ?: error("No attempted game.")
        val profile = attempt.instanceId?.let { instances.get(it) }
        check(profile == null || profile.minecraftVersionId == attempt.gameVersion && profile.loader == attempt.loader)
        val entry = versionCatalog.fetchManifest().versions.first { it.id == attempt.gameVersion }
        return versionResolver.resolve(entry) to profile
    }

    fun gameLogs(instance: MinecraftInstance?): java.nio.file.Path =
        (instance?.let(instances::layoutFor) ?: legacyLayout).gameDir.resolve("logs")

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

    suspend fun addOfflineAccount(nickname: String): OfflineAccount = accountMutex.withLock {
        check(!closing.get()) { "SyntaxLauncher is closing." }
        val result = accounts?.saveOffline(nickname) ?: error("Account storage is unavailable.")
        publishAccounts()
        result
    }

    suspend fun renameOfflineAccount(uuid: String, nickname: String): OfflineAccount = accountMutex.withLock {
        check(!closing.get()) { "SyntaxLauncher is closing." }
        val result = accounts?.renameOffline(uuid, nickname) ?: error("Account storage is unavailable.")
        publishAccounts()
        result
    }

    suspend fun removePlayerAccount(uuid: String) = accountMutex.withLock {
        check(!closing.get()) { "SyntaxLauncher is closing." }
        accounts?.remove(uuid) ?: error("Account storage is unavailable.")
        publishAccounts()
    }

    suspend fun removePremiumAccount(uuid: String) = removePlayerAccount(uuid)

    suspend fun savePreferencesNow(value: LauncherPreferences) {
        val old = synchronized(lock) {
            check(!closing.get()) { "SyntaxLauncher is closing." }
            settingsJob.also { it?.cancel(); settingsJob = null }
        }
        old?.cancelAndJoin()
        preferences.save(value)
        synchronized(lock) { pendingPreferences = value }
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
            if (premiumId != null && mutablePremiumAccounts.value.none { it.uuid.toString() == premiumId }) {
                mutableLaunchIssue.value = "The selected Microsoft profile is unavailable. Sign in again."
                return false
            }
            if (premiumId == null && !OfflineAccount.isValidUsername(nickname)) {
                mutableLaunchIssue.value = "Choose an offline player with a valid nickname."
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
                    val (account, authentication) = accountMutex.withLock {
                        if (premiumId == null) {
                            val offline = accounts?.saveOffline(nickname) ?: OfflineAccount.create(nickname)
                            publishAccounts()
                            offline to LaunchAuthentication.offline(offline)
                        } else {
                            check(microsoftSignIn != null) { "Microsoft sign-in is not configured." }
                            val current = accounts?.session(premiumId, requireFresh = true)
                                ?: error("Account storage is unavailable.")
                            publishAccounts()
                            current.account to current.launchAuthentication(microsoftSignIn.clientId)
                        }
                    }
                    instances.beginLaunch(instance?.id)
                    leased = true
                    lastDiagnosticInstanceId = instance?.id
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

    suspend fun readLauncherLog(): String = withContext(Dispatchers.IO) {
        val file = launcherPaths?.logsDir?.resolve("launcher.log") ?: return@withContext ""
        if (!java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return@withContext ""
        java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.READ).use { channel ->
            val buffer = java.nio.ByteBuffer.allocate(minOf(channel.size(), 64L * 1024).toInt())
            channel.position((channel.size() - buffer.capacity()).coerceAtLeast(0))
            while (buffer.hasRemaining() && channel.read(buffer) > 0) kotlinx.coroutines.currentCoroutineContext().ensureActive()
            pl.syntaxdevteam.syntaxlauncher.core.diagnostics.SecretRedactor.redact(
                String(buffer.array(), 0, buffer.position(), Charsets.UTF_8))
        }
    }

    suspend fun diagnose(version: ResolvedMinecraftVersion, instance: MinecraftInstance?):
        pl.syntaxdevteam.syntaxlauncher.core.diagnostics.DiagnosticReport {
        val evidence = coordinator.evidence?.takeIf {
            it.minecraft == version.id && lastDiagnosticInstanceId == instance?.id
        }
        val inventory = instance?.let { modManager.list(it.id) }
        return pl.syntaxdevteam.syntaxlauncher.core.diagnostics.GameDiagnostics().inspect(
            if (evidence != null) coordinator.preparedMetadata ?: version else version,
            pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform.detect(),
            instance?.let(instances::layoutFor) ?: legacyLayout,
            evidence, if (evidence != null) processManager.status.value else null,
            inventory?.mods.orEmpty().map { mod ->
                pl.syntaxdevteam.syntaxlauncher.core.diagnostics.ModEvidence(
                    mod.metadata?.id ?: "unknown", mod.metadata?.version ?: "unknown", mod.enabled)
            }, inventory?.warnings?.isNotEmpty() == true, instance?.loader?.let { "${it.id}/${it.version}" })
    }

    private val mutableMaintenanceResult = MutableStateFlow<String?>(null)
    val maintenanceResult = mutableMaintenanceResult.asStateFlow()

    @Volatile private var lastDiagnosticInstanceId: String? = null

    /** Reuses verified preparation. No process, account or mutable world/config deletion. */
    fun repair(version: ResolvedMinecraftVersion, instance: MinecraftInstance?): Boolean = synchronized(lock) {
        if (closing.get() || launchJob?.isActive == true ||
            processManager.status.value.state in setOf(MinecraftProcessState.RUNNING, MinecraftProcessState.STARTING)) return false
        mutablePreparingAccount.value = true
        mutableLaunchIssue.value = null
        mutableMaintenanceResult.value = null
        launchJob = scope.launch {
            var leased = false
            try {
                instances.beginLaunch(instance?.id)
                leased = true
                if (instance != null) {
                    val current = instances.get(instance.id)
                    check(current.minecraftVersionId == version.id && current.loader == instance.loader) {
                        "The game settings changed. Reopen repair."
                    }
                }
                coordinator.prepareForInstallation(version, instance?.let(instances::layoutFor) ?: legacyLayout,
                    instance?.loader, recordRepair = true)
                lastDiagnosticInstanceId = instance?.id
                mutableMaintenanceResult.value = "success"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                mutableMaintenanceResult.value = "cancelled"; throw cancelled
            }
            catch (_: Exception) { mutableMaintenanceResult.value = "failed"; mutableLaunchIssue.value = "Repair failed. Check your connection and available disk space, then retry." }
            finally {
                if (leased) withContext(kotlinx.coroutines.NonCancellable) { instances.endPreparation(instance?.id) }
            }
        }
        launchJob!!.invokeOnCompletion { mutablePreparingAccount.value = false }
        true
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

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
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val closing = AtomicBoolean(false)
    private var launchJob: Job? = null
    private var settingsJob: Job? = null
    private var pendingPreferences: LauncherPreferences? = null

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
    ): Boolean {
        synchronized(lock) {
            if (closing.get() || launchJob?.isActive == true ||
                processManager.status.value.state in setOf(
                    MinecraftProcessState.RUNNING, MinecraftProcessState.STARTING,
                )
            ) return false
            val account = OfflineAccount.create(nickname)
            launchJob = scope.launch {
                coordinator.launch(
                    version = version,
                    account = account,
                    options = LaunchOptions(
                        minimumMemoryMiB = instance?.minimumMemoryMiB ?: 512,
                        maximumMemoryMiB = maximumMemoryMiB,
                        extraJvmArguments = instance?.extraJvmArguments.orEmpty(),
                    ),
                    instanceLayout = instance?.let(instances::layoutFor) ?: legacyLayout,
                )
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
            } finally {
                httpClient.close()
                withContext(Dispatchers.Swing) { onClosed() }
                scope.cancel()
            }
        }
    }
}

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
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchOptions
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftLaunchCoordinator
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessManager
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftShutdownPolicy
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionResolver
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

/**
 * Window lifetime: download jobs survive recompositions but not launcher
 * shutdown. DETACH never stops a Minecraft process that has already launched.
 */
class DesktopLauncherSession(
    val versionCatalog: MinecraftVersionCatalog,
    val versionResolver: MinecraftVersionResolver,
    val coordinator: MinecraftLaunchCoordinator,
    val processManager: MinecraftProcessManager,
    private val httpClient: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val closing = AtomicBoolean(false)
    private var launchJob: Job? = null

    fun play(version: ResolvedMinecraftVersion, nickname: String, maximumMemoryMiB: Int): Boolean {
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
                        minimumMemoryMiB = 512,
                        maximumMemoryMiB = maximumMemoryMiB,
                    ),
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
                processManager.shutdown(MinecraftShutdownPolicy.DETACH)
            } finally {
                httpClient.close()
                withContext(Dispatchers.Swing) { onClosed() }
                scope.cancel()
            }
        }
    }
}

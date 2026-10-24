package pl.syntaxdevteam.syntaxlauncher.core.launch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
import pl.syntaxdevteam.syntaxlauncher.core.account.Account
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderRegistry
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderException
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeEvent
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPreparationEvent
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPreparationService
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPreparationStage
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEvent
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class InvalidPlayerAuthenticationException : IllegalStateException(
    "The selected player profile has invalid authentication.")

enum class LaunchPhase {
    IDLE, PREPARING_LOADER, PREPARING_GAME, PREPARING_JAVA, BUILDING_PLAN, STARTING_GAME, STARTED, FAILED, CANCELLED,
}

data class LaunchPipelineStatus(
    val phase: LaunchPhase = LaunchPhase.IDLE,
    val detail: String = "Ready to play",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val currentFileProgress: Float? = null,
    val failure: LaunchFailureReason? = null,
) {
    val isPreparing: Boolean
        get() = phase in setOf(
            LaunchPhase.PREPARING_LOADER, LaunchPhase.PREPARING_GAME, LaunchPhase.PREPARING_JAVA,
            LaunchPhase.BUILDING_PLAN, LaunchPhase.STARTING_GAME,
        )
}

/**
 * Runs the already-resolved Minecraft launch pipeline without UI dependencies.
 * Process lifetime remains with MinecraftProcessManager after launch returns.
 */
class MinecraftLaunchCoordinator(
    private val preparation: MinecraftPreparationService,
    private val runtimes: JavaRuntimeManager,
    private val plans: LaunchPlanBuilder,
    private val processes: MinecraftProcessManager,
    private val layout: MinecraftInstallationLayout,
    private val platform: MinecraftPlatform,
    private val loaders: ModLoaderRegistry? = null,
    private val graphics: pl.syntaxdevteam.syntaxlauncher.core.content.GraphicsProfileResolver =
        pl.syntaxdevteam.syntaxlauncher.core.content.LocalGraphicsProfileResolver(),
) {
    private val log = LoggerFactory.getLogger(MinecraftLaunchCoordinator::class.java)
    private val launchMutex = Mutex()
    private val mutableStatus = MutableStateFlow(LaunchPipelineStatus())
    val status: StateFlow<LaunchPipelineStatus> = mutableStatus.asStateFlow()

    @Volatile var evidence: pl.syntaxdevteam.syntaxlauncher.core.diagnostics.LaunchEvidence? = null
        private set
    @Volatile var preparedMetadata: ResolvedMinecraftVersion? = null
        private set

    suspend fun launch(
        version: ResolvedMinecraftVersion,
        account: Account,
        options: LaunchOptions,
        instanceLayout: MinecraftInstallationLayout = layout,
        authentication: LaunchAuthentication,
        loader: ModLoaderSelection? = null,
    ): LaunchPipelineStatus {
        if (!launchMutex.tryLock()) return mutableStatus.value
        try {
            evidence = pl.syntaxdevteam.syntaxlauncher.core.diagnostics.LaunchEvidence(
                version.id, loader?.let { "${it.id}/${it.version}" }, version.javaRequirement?.majorVersion)
            preparedMetadata = null
            val validAuthentication = account is pl.syntaxdevteam.syntaxlauncher.core.account.PremiumAccount &&
                authentication.userType == "msa" && authentication.accessToken.isNotBlank() &&
                authentication.accessToken != "0" && authentication.clientId.isNotBlank() &&
                authentication.sessionId.isNotBlank()
            if (!validAuthentication) {
                throw InvalidPlayerAuthenticationException()
            }
            val (prepared, runtime) = prepareShared(version, instanceLayout, loader)
            evidence = evidence?.copy(runtime = runtime, classpathEntries = prepared.filePlan.classpath.size)
            preparedMetadata = prepared.version
            stage(LaunchPhase.BUILDING_PLAN, "Preparing launch arguments")
            val plan = plans.build(
                LaunchRequest(
                    account = account,
                    prepared = prepared,
                    runtime = runtime,
                    platform = platform,
                    options = options,
                    authentication = authentication,
                ),
            )
            evidence = evidence?.copy(runtime = runtime, mainClass = plan.mainClass,
                classpathEntries = prepared.filePlan.classpath.size)
            preparedMetadata = prepared.version
            stage(LaunchPhase.STARTING_GAME, "Starting Minecraft")
            val running = processes.launch(plan)
            evidence = evidence?.copy(processStarted = true)
            stage(LaunchPhase.STARTED, "Minecraft started (PID ${running.pid ?: "unknown"})")
        } catch (cancelled: CancellationException) {
            stage(LaunchPhase.CANCELLED, "Preparation cancelled")
            throw cancelled
        } catch (error: Exception) {
            evidence = evidence?.copy(failureType = error.javaClass.simpleName)
            // Never print a full command or authentication arguments.
            log.warn("Minecraft launch pipeline failed: {}", error.javaClass.simpleName)
            val reason = LaunchFailureReason.from(error)
            mutableStatus.value = LaunchPipelineStatus(phase = LaunchPhase.FAILED, detail = reason.summary, failure = reason)
        } finally {
            launchMutex.unlock()
        }
        return mutableStatus.value
    }

    /** Common game/Java/loader preparation without a process or account. */
    suspend fun prepareForInstallation(version: ResolvedMinecraftVersion, instanceLayout: MinecraftInstallationLayout,
        loader: ModLoaderSelection?, recordRepair: Boolean = false) {
        launchMutex.lock()
        try {
            val (prepared, runtime) = prepareShared(version, instanceLayout, loader)
            if (recordRepair) {
                evidence = pl.syntaxdevteam.syntaxlauncher.core.diagnostics.LaunchEvidence(
                    version.id, loader?.let { "${it.id}/${it.version}" }, version.javaRequirement?.majorVersion,
                    runtime = runtime, mainClass = prepared.version.mainClass,
                    classpathEntries = prepared.filePlan.classpath.size)
                preparedMetadata = prepared.version
            }
        }
        finally { stage(LaunchPhase.IDLE, "Ready to play"); launchMutex.unlock() }
    }

    private suspend fun prepareShared(version: ResolvedMinecraftVersion, instanceLayout: MinecraftInstallationLayout,
        loader: ModLoaderSelection?): Pair<pl.syntaxdevteam.syntaxlauncher.core.minecraft.PreparedMinecraftVersion,
            pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime> {
        // Fail before downloading a game whose runtime is not known.
        val requirement = version.javaRequirement
            ?: throw IllegalArgumentException(
                "Minecraft does not declare a supported Java runtime.",
            )
        val delayedInstaller = loader != null && loaders?.requiresInstallation(loader.id) == true
        val loaderVersion = if (loader == null || delayedInstaller) version else {
            stage(LaunchPhase.PREPARING_LOADER, "Resolving ${loader.id} ${loader.version}")
            val registry = loaders ?: throw ModLoaderException("Mod loader service is not configured.")
            registry.resolve(version, loader)
        }
        val effectiveVersion = graphics.resolve(loaderVersion, instanceLayout, loader)
        stage(LaunchPhase.PREPARING_GAME, "Preparing Minecraft ${version.id}")
        val vanillaPrepared = preparation.prepare(effectiveVersion, platform, instanceLayout) { event ->
            when (event) {
                is MinecraftPreparationEvent.StageChanged -> {
                    val name = when (event.stage) {
                        MinecraftPreparationStage.CORE_FILES -> "Downloading game files"
                        MinecraftPreparationStage.ASSETS -> "Checking and downloading assets"
                        MinecraftPreparationStage.NATIVES -> "Extracting native libraries"
                        MinecraftPreparationStage.COMPLETE -> "Game files ready"
                    }
                    mutableStatus.update { it.copy(detail = name, currentFileProgress = null) }
                }
                is MinecraftPreparationEvent.Download -> downloaded(event.event)
            }
        }

        stage(LaunchPhase.PREPARING_JAVA, "Checking Java ${requirement.majorVersion}")
        val runtime = runtimes.resolve(requirement, platform) { event ->
            when (event) {
                is JavaRuntimeEvent.CheckingInstalled -> detail("Checking installed Java")
                is JavaRuntimeEvent.InstalledRuntimeFound -> detail("Using Java ${event.runtime.majorVersion}")
                is JavaRuntimeEvent.ResolvingMetadata -> detail("Finding a compatible Java runtime")
                is JavaRuntimeEvent.ManifestResolved -> detail("Java runtime: ${event.fileCount} files")
                is JavaRuntimeEvent.Installing -> detail("Installing Java: ${event.fileCount} files")
                is JavaRuntimeEvent.Verifying -> detail("Verifying Java ${event.expectedMajorVersion}")
                is JavaRuntimeEvent.Ready -> detail("Java ${event.runtime.majorVersion} ready")
                is JavaRuntimeEvent.Download -> downloaded(event.event)
            }
        }

        val prepared = if (delayedInstaller) {
            val selected = loader
            stage(LaunchPhase.PREPARING_LOADER, "Installing " + selected.id + " " + selected.version)
            val installed = loaders.install(version, selected, instanceLayout, runtime)
            stage(LaunchPhase.PREPARING_GAME, "Verifying installed ${selected.id} files")
            preparation.prepare(installed, platform, instanceLayout) { event ->
                if (event is MinecraftPreparationEvent.Download) downloaded(event.event)
            }
        } else vanillaPrepared
        return prepared to runtime
    }

    private fun stage(phase: LaunchPhase, message: String) {
        mutableStatus.value = LaunchPipelineStatus(phase = phase, detail = message)
    }

    private fun detail(message: String) {
        mutableStatus.update { it.copy(detail = message, currentFileProgress = null) }
    }

    private fun downloaded(event: DownloadEvent) {
        mutableStatus.update { old ->
            when (event) {
                is DownloadEvent.BatchStarted -> old.copy(
                    totalFiles = event.totalFiles, completedFiles = 0, currentFileProgress = null,
                )
                is DownloadEvent.FileStarted -> old.copy(
                    detail = "Checking ${event.request.target.fileName}",
                    currentFileProgress = null,
                )
                is DownloadEvent.FileProgress -> {
                    val total = event.totalBytes?.takeIf { it > 0 }
                    old.copy(
                        detail = "Downloading ${event.request.target.fileName}",
                        currentFileProgress = total?.let {
                            (event.downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
                        },
                    )
                }
                is DownloadEvent.FileCompleted -> old.copy(
                    completedFiles = (old.completedFiles + 1).coerceAtMost(old.totalFiles),
                    currentFileProgress = null,
                )
                is DownloadEvent.FileRetrying -> old.copy(
                    detail = "Retrying ${event.request.target.fileName} (attempt ${event.nextAttempt})",
                    currentFileProgress = null,
                )
                is DownloadEvent.FileFailed -> old.copy(
                    detail = "Failed to download ${event.request.target.fileName}",
                    currentFileProgress = null,
                )
                is DownloadEvent.BatchCompleted -> old.copy(
                    completedFiles = event.totalFiles,
                    totalFiles = event.totalFiles,
                    currentFileProgress = null,
                )
            }
        }
    }

}

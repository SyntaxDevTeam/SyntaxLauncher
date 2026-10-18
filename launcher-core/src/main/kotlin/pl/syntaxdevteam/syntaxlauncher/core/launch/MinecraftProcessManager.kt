package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

enum class MinecraftProcessState {
    IDLE, STARTING, RUNNING, STOPPED, CRASHED, DETACHED,
}

enum class MinecraftShutdownPolicy {
    /** Close launcher monitoring; the game keeps running and writing its logs. */
    DETACH,

    /** Ask the game to stop; forcibly terminate it after the grace period. */
    TERMINATE,
}

data class MinecraftProcessStatus(
    val state: MinecraftProcessState = MinecraftProcessState.IDLE,
    val pid: Long? = null,
    val exitCode: Int? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val stdoutLog: Path? = null,
    val stderrLog: Path? = null,
    val errorMessage: String? = null,
)

open class MinecraftProcessException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class MinecraftAlreadyRunningException :
    MinecraftProcessException("A Minecraft launch is already active in this launcher.")

class MinecraftProcessManagerClosedException :
    MinecraftProcessException("Minecraft process manager was shut down and cannot launch again.")

class MinecraftProcessStartException(cause: Throwable) :
    MinecraftProcessException("Minecraft could not be started. Check the Java executable and game directory permissions.", cause)

class MinecraftProcessTerminationException :
    MinecraftProcessException("Minecraft did not exit after a forced termination request.")

/**
 * Test seam only: normal operation launches [LaunchPlan.command] via ProcessBuilder.
 * Do not print the command: it may contain session credentials in future versions.
 */
fun interface MinecraftProcessStarter {
    fun start(plan: LaunchPlan, stdoutLog: Path, stderrLog: Path): Process
}

/**
 * Owns at most one Minecraft process at a time. Does not depend on Compose.
 * OS-level stdout/stderr file redirection is intentional: DETACH works even if
 * the launcher JVM exits. These files contain raw game output and are private
 * on POSIX systems; a future premium flow must review possible token emission.
 */
class MinecraftProcessManager(
    private val logsDirectory: Path,
    private val starter: MinecraftProcessStarter = MinecraftProcessStarter { plan, stdout, stderr ->
        ProcessBuilder(plan.command)
            .directory(plan.workingDirectory.toFile())
            .redirectOutput(ProcessBuilder.Redirect.to(stdout.toFile()))
            .redirectError(ProcessBuilder.Redirect.to(stderr.toFile()))
            .start()
    },
) {
    private data class Session(
        val process: Process,
        val pid: Long,
        val startedAt: Instant,
        val stdoutLog: Path,
        val stderrLog: Path,
        var stopRequested: Boolean = false,
    )

    private val log = LoggerFactory.getLogger(MinecraftProcessManager::class.java)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStatus = MutableStateFlow(MinecraftProcessStatus())
    val status: StateFlow<MinecraftProcessStatus> = mutableStatus.asStateFlow()

    private var active: Session? = null
    private var closed = false

    suspend fun launch(plan: LaunchPlan): MinecraftProcessStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) throw MinecraftProcessManagerClosedException()
            if (active != null) throw MinecraftAlreadyRunningException()
            mutableStatus.value = MinecraftProcessStatus(state = MinecraftProcessState.STARTING)

            var startedProcess: Process? = null
            try {
                require(Files.isDirectory(plan.workingDirectory)) {
                    "Minecraft game directory does not exist."
                }
                val root = logsDirectory.resolve("minecraft")
                Files.createDirectories(root)
                val runDirectory = Files.createTempDirectory(root, "run-")
                val stdout = createPrivateLog(runDirectory.resolve("stdout.log"))
                val stderr = createPrivateLog(runDirectory.resolve("stderr.log"))
                val process = starter.start(plan, stdout, stderr)
                startedProcess = process
                val session = Session(
                    process = process,
                    pid = process.pid(),
                    startedAt = Instant.now(),
                    stdoutLog = stdout,
                    stderrLog = stderr,
                )
                active = session
                mutableStatus.value = session.snapshot(MinecraftProcessState.RUNNING)
                log.info("Minecraft process started, pid={}", session.pid)

                process.onExit().whenComplete { _, problem ->
                    scope.launch {
                        val code = runCatching { process.exitValue() }.getOrNull()
                        complete(session, code, problem)
                    }
                }
                mutableStatus.value
            } catch (error: Exception) {
                // Never leave an unmonitored child running if bookkeeping fails.
                startedProcess?.let { runCatching { it.destroyForcibly() } }
                active = null
                // The command and its authentication arguments are never logged.
                mutableStatus.value = MinecraftProcessStatus(
                    state = MinecraftProcessState.CRASHED,
                    finishedAt = Instant.now(),
                    errorMessage = "Could not start Minecraft. Verify Java and game directory permissions.",
                )
                throw MinecraftProcessStartException(error)
            }
        }
    }

    /** Returns false if no game is currently tracked. Never runs on the UI thread. */
    suspend fun terminate(gracePeriodMillis: Long = 10_000L): Boolean {
        require(gracePeriodMillis >= 0) { "Grace period must be non-negative." }
        val session = mutex.withLock {
            active?.also { it.stopRequested = true }
        } ?: return false

        withContext(Dispatchers.IO) {
            if (session.process.isAlive) session.process.destroy()
            if (!awaitExit(session.process, gracePeriodMillis)) {
                session.process.destroyForcibly()
                if (!awaitExit(session.process, 5_000L)) {
                    throw MinecraftProcessTerminationException()
                }
            }
            complete(session, runCatching { session.process.exitValue() }.getOrNull(), null)
        }
        return true
    }

    /**
     * Explicit window-close policy. DETACH leaves Minecraft alive; native file
     * redirects continue to capture output after the launcher process exits.
     */
    suspend fun shutdown(
        policy: MinecraftShutdownPolicy = MinecraftShutdownPolicy.DETACH,
        gracePeriodMillis: Long = 10_000L,
    ) {
        if (policy == MinecraftShutdownPolicy.TERMINATE) {
            mutex.withLock { closed = true }
            try {
                terminate(gracePeriodMillis)
            } finally {
                scope.cancel()
            }
        } else {
            mutex.withLock {
                closed = true
                active?.let { session ->
                    mutableStatus.value = session.snapshot(MinecraftProcessState.DETACHED)
                    log.info("Detached from Minecraft process, pid={}", session.pid)
                }
                active = null
            }
            scope.cancel()
        }
    }

    private suspend fun awaitExit(process: Process, timeoutMillis: Long): Boolean {
        if (!process.isAlive) return true
        if (timeoutMillis == 0L) return false
        return withTimeoutOrNull(timeoutMillis) {
            while (process.isAlive) delay(25)
            true
        } ?: !process.isAlive
    }

    private suspend fun complete(session: Session, exitCode: Int?, problem: Throwable?) {
        mutex.withLock {
            if (active !== session) return@withLock
            val stopped = session.stopRequested || (problem == null && exitCode == 0)
            mutableStatus.value = session.snapshot(
                state = if (stopped) MinecraftProcessState.STOPPED else MinecraftProcessState.CRASHED,
                exitCode = exitCode,
                finishedAt = Instant.now(),
                errorMessage = if (stopped) null else "Minecraft exited unexpectedly (code: ${exitCode ?: "unavailable"}).",
            )
            active = null
            log.info("Minecraft process ended, pid={}, exitCode={}, requestedStop={}",
                session.pid, exitCode, session.stopRequested)
        }
    }

    private fun Session.snapshot(
        state: MinecraftProcessState,
        exitCode: Int? = null,
        finishedAt: Instant? = null,
        errorMessage: String? = null,
    ) = MinecraftProcessStatus(
        state = state,
        pid = pid,
        exitCode = exitCode,
        startedAt = startedAt,
        finishedAt = finishedAt,
        stdoutLog = stdoutLog,
        stderrLog = stderrLog,
        errorMessage = errorMessage,
    )

    private fun createPrivateLog(path: Path): Path {
        try {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"),
            ))
        } catch (_: UnsupportedOperationException) {
            Files.createFile(path)
        }
        return path
    }
}

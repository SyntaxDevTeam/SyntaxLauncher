package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime

class MinecraftProcessManagerTest {
    @Test
    fun actualJavaSubprocessLogsAndExits() = runBlocking {
        val root = Files.createTempDirectory("syntax-process-real-")
        val java = javaExecutable()
        val manager = MinecraftProcessManager(root.resolve("logs"))
        val started = manager.launch(plan(root, java, listOf("-version")))
        assertTrue(Files.isRegularFile(started.stdoutLog))
        assertTrue(Files.isRegularFile(started.stderrLog))
        val ended = awaitState(manager, MinecraftProcessState.STOPPED)
        assertEquals(0, ended.exitCode)
        assertNotNull(ended.pid)
        assertTrue(Files.readString(ended.stderrLog).contains("version"))
        manager.shutdown()
    }

    @Test
    fun authenticatedGameOutputIsNeverStoredRaw() = runBlocking {
        val root = Files.createTempDirectory("syntax-process-premium-")
        val manager = MinecraftProcessManager(root.resolve("logs"))
        try {
            val launch = plan(root, javaExecutable(), listOf("-version"))
                .copy(sensitiveOutput = true)
            val started = manager.launch(launch)
            val completed = awaitState(manager, MinecraftProcessState.STOPPED)
            assertEquals(0, completed.exitCode)
            assertEquals(0L, Files.size(started.stdoutLog))
            assertEquals(0L, Files.size(started.stderrLog))
            assertFalse(launch.toString().contains("java -version"))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun duplicateLaunchNonzeroExitAndRelaunch() = runBlocking {
        val root = Files.createTempDirectory("syntax-process-duplicate-")
        val first = ControlledProcess()
        val second = ControlledProcess()
        val processes = ArrayDeque(listOf(first, second))
        val manager = MinecraftProcessManager(root.resolve("logs")) { _, out, err ->
            Files.writeString(out, "stdout")
            Files.writeString(err, "stderr")
            processes.removeFirst()
        }
        val request = plan(root, Path.of("fake-java"))
        val running = manager.launch(request)
        assertEquals(MinecraftProcessState.RUNNING, running.state)
        assertFailsWith<MinecraftAlreadyRunningException> { manager.launch(request) }
        first.finish(7)
        val crashed = awaitState(manager, MinecraftProcessState.CRASHED)
        assertEquals(7, crashed.exitCode)
        assertTrue(crashed.errorMessage!!.contains("7"))
        assertEquals("stdout", Files.readString(crashed.stdoutLog))
        assertEquals("stderr", Files.readString(crashed.stderrLog))
        assertEquals(MinecraftProcessState.RUNNING, manager.launch(request).state)
        second.finish(0)
        assertEquals(0, awaitState(manager, MinecraftProcessState.STOPPED).exitCode)
        manager.shutdown()
    }

    @Test
    fun terminationEscalatesToForceAndReportsRequestedStop() = runBlocking {
        val root = Files.createTempDirectory("syntax-process-stop-")
        val process = ControlledProcess(exitOnGracefulDestroy = false)
        val manager = MinecraftProcessManager(root.resolve("logs")) { _, _, _ -> process }
        manager.launch(plan(root, Path.of("fake-java")))
        assertTrue(manager.terminate(gracePeriodMillis = 30))
        assertTrue(process.destroyCalled)
        assertTrue(process.forceCalled)
        assertEquals(MinecraftProcessState.STOPPED, manager.status.value.state)
        assertEquals(137, manager.status.value.exitCode)
        assertFalse(manager.terminate())
        manager.shutdown()
    }

    @Test
    fun detachDoesNotKillChildAndClosesManager() = runBlocking {
        val root = Files.createTempDirectory("syntax-process-detach-")
        val process = ControlledProcess()
        val manager = MinecraftProcessManager(root.resolve("logs")) { _, _, _ -> process }
        val request = plan(root, Path.of("fake-java"))
        manager.launch(request)
        manager.shutdown(MinecraftShutdownPolicy.DETACH)
        assertEquals(MinecraftProcessState.DETACHED, manager.status.value.state)
        assertTrue(process.isAlive)
        assertFalse(process.destroyCalled)
        assertFailsWith<MinecraftProcessManagerClosedException> { manager.launch(request) }
        process.finish(0)
    }

    @Test
    fun startFailureDoesNotPermanentlyBlockLaunches() = runBlocking {
        val root = Files.createTempDirectory("syntax-process-start-failure-")
        val good = ControlledProcess()
        var fail = true
        val manager = MinecraftProcessManager(root.resolve("logs")) { _, _, _ ->
            if (fail) {
                fail = false
                throw IOException("simulated failure")
            }
            good
        }
        val request = plan(root, Path.of("fake-java"))
        assertFailsWith<MinecraftProcessStartException> { manager.launch(request) }
        assertEquals(MinecraftProcessState.CRASHED, manager.status.value.state)
        assertEquals(MinecraftProcessState.RUNNING, manager.launch(request).state)
        good.finish(0)
        awaitState(manager, MinecraftProcessState.STOPPED)
        manager.shutdown()
    }

    private suspend fun awaitState(
        manager: MinecraftProcessManager,
        expected: MinecraftProcessState,
    ): MinecraftProcessStatus = withTimeout(5_000) {
        while (manager.status.value.state != expected) delay(10)
        manager.status.value
    }

    private fun javaExecutable(): Path = Path.of(
        System.getProperty("java.home"),
        "bin",
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java",
    )

    private fun plan(root: Path, executable: Path, jvm: List<String> = emptyList()): LaunchPlan =
        LaunchPlan(
            runtime = JavaRuntime(
                home = executable.parent ?: root,
                executable = executable,
                majorVersion = 21,
                versionString = "21",
            ),
            workingDirectory = root,
            mainClass = "synthetic.Main",
            jvmArguments = jvm,
            gameArguments = emptyList(),
        )

    private class ControlledProcess(
        private val exitOnGracefulDestroy: Boolean = true,
    ) : Process() {
        private val completion = CompletableFuture<Process>()
        private var code = 0
        var destroyCalled = false
            private set
        var forceCalled = false
            private set

        fun finish(exitCode: Int) {
            code = exitCode
            completion.complete(this)
        }

        override fun onExit(): CompletableFuture<Process> = completion
        override fun pid(): Long = 1234L
        override fun isAlive(): Boolean = !completion.isDone
        override fun waitFor(): Int {
            completion.get()
            return code
        }
        override fun exitValue(): Int {
            if (isAlive) throw IllegalThreadStateException("Process is still running")
            return code
        }
        override fun destroy() {
            destroyCalled = true
            if (exitOnGracefulDestroy) finish(143)
        }
        override fun destroyForcibly(): Process {
            forceCalled = true
            finish(137)
            return this
        }
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))
    }
}

package pl.syntaxdevteam.syntaxlauncher.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths

class FactoryResetTest {
    @TempDir lateinit var temp: Path

    @Test fun removesSharedRootsOnceAndIncludesSeparateStorage() {
        val windows = LauncherPaths.resolve(mapOf("LOCALAPPDATA" to temp.toString()), temp, "Windows 11")
        assertEquals(listOf(temp.resolve("SyntaxLauncher")), FactoryReset.resetRoots(windows))

        val unix = LauncherPaths.resolve(emptyMap(), temp, "Linux")
        assertEquals(setOf(unix.dataDir, unix.configDir, unix.cacheDir, unix.logsDir.parent),
            FactoryReset.resetRoots(unix).toSet())
    }

    @Test fun posixWorkerWaitsForExitAndDoesNotFollowLinks() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val work = Files.createDirectory(temp.resolve("worker"))
        val data = Files.createDirectory(temp.resolve("launcher data"))
        val external = Files.createDirectory(temp.resolve("external"))
        Files.writeString(external.resolve("keep.txt"), "untouched")
        Files.createSymbolicLink(data.resolve("linked"), external)
        Files.writeString(data.resolve("settings"), "remove")
        val pending = Files.createFile(temp.resolve("pending"))
        val failed = temp.resolve("failed")
        val script = work.resolve("factory-reset.sh")
        checkNotNull(javaClass.getResourceAsStream("/reset/factory-reset.sh")).use { Files.copy(it, script) }
        Files.writeString(work.resolve("roots.txt"), "$data\n")
        val owner = ProcessBuilder("/bin/sleep", "20").start()
        val worker = ProcessBuilder("/bin/sh", script.toString(), owner.pid().toString(), work.toString(),
            pending.toString(), failed.toString()).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!Files.exists(work.resolve("ready")) && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(Files.exists(work.resolve("ready")))
            assertTrue(Files.exists(data.resolve("settings")))
            assertTrue(Files.exists(pending))
            owner.destroy()
            assertTrue(owner.waitFor(5, TimeUnit.SECONDS))
            assertTrue(worker.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, worker.exitValue())
            assertFalse(Files.exists(data))
            assertFalse(Files.exists(pending))
            assertFalse(Files.exists(failed))
            assertEquals("untouched", Files.readString(external.resolve("keep.txt")))
        } finally {
            owner.destroyForcibly()
            worker.destroyForcibly()
        }
    }
}

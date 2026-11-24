package pl.syntaxdevteam.syntaxlauncher.core.diagnostics

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.network.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond

class GameDiagnosticsTest {
    @TempDir lateinit var root: Path
    private val platform = MinecraftPlatform(MinecraftOperatingSystem.LINUX, MinecraftArchitecture.X86_64, "test", "amd64")
    private val payload = "verified-client".encodeToByteArray()
    private fun version() = ResolvedMinecraftVersion("test", MinecraftVersionType.RELEASE,
        "net.minecraft.client.main.Main", "test", null,
        MinecraftArtifact(sha1 = MessageDigest.getInstance("SHA-1").digest(payload).joinToString("") { "%02x".format(it) },
            size = payload.size.toLong(), url = "https://example.invalid/client"),
        emptyList(), MinecraftArguments(), JavaRuntimeRequirement("test-java", 21), null)
    private fun layout() = MinecraftInstallationLayout(root.resolve("game"), root.resolve("versions"),
        root.resolve("libraries"), root.resolve("assets"), root.resolve("assets/indexes"),
        root.resolve("assets/objects"), root.resolve("assets/log_configs"), root.resolve("natives"))

    @Test fun `integrity scan and repair restore managed file without touching worlds or starting a process`() = runBlocking {
        val layout = layout()
        layout.ensureDirectories()
        val world = layout.gameDir.resolve("saves/world/level.dat")
        Files.createDirectories(world.parent); Files.writeString(world, "precious world")
        val options = layout.gameDir.resolve("options.txt"); Files.writeString(options, "my settings")
        val mods = layout.gameDir.resolve("mods/private.jar")
        Files.createDirectories(mods.parent); Files.writeString(mods, "local mod")
        val diagnostics = GameDiagnostics()
        assertEquals(1, diagnostics.inspect(version(), platform, layout).damagedFiles)
        val http = HttpClient(MockEngine { respond(payload) })
        val process = MinecraftProcessManager(root.resolve("logs")) { _, _, _ -> error("Repair must never start a game") }
        var runtimeChecks = 0
        val runtime = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener): JavaRuntime {
                runtimeChecks++
                return JavaRuntime(root, root.resolve("java"), 21, "21")
            }
        }
        val coordinator = MinecraftLaunchCoordinator(MinecraftPreparationService(DownloadManager(http)), runtime,
            LaunchPlanBuilder(), process, layout, platform)
        try {
            coordinator.prepareForInstallation(version(), layout, null)
            assertEquals(0, diagnostics.inspect(version(), platform, layout).damagedFiles)
            Files.writeString(layout.versionJar("test"), "corrupt")
            assertEquals(1, diagnostics.inspect(version(), platform, layout).damagedFiles)
            coordinator.prepareForInstallation(version(), layout, null)
            assertContentEquals(payload, Files.readAllBytes(layout.versionJar("test")))
            assertEquals(2, runtimeChecks)
            assertEquals(MinecraftProcessState.IDLE, process.status.value.state)
            assertEquals("precious world", Files.readString(world))
            assertEquals("my settings", Files.readString(options))
            assertEquals("local mod", Files.readString(mods))
        } finally { http.close(); process.shutdown() }
    }

    @Test fun `fresh crash evidence is classified without exporting credentials raw logs or private paths`() = runBlocking {
        val layout = layout(); layout.ensureDirectories()
        val log = layout.gameDir.resolve("logs/latest.log")
        Files.createDirectories(log.parent)
        val secret = "private-refresh-credential"
        Files.writeString(log, "OutOfMemoryError\nrefresh_token=$secret\n--accessToken $secret\n${root}")
        val report = GameDiagnostics().inspect(version(), platform, layout,
            LaunchEvidence("test", "fabric/1", 21, Instant.now().minusSeconds(1), processStarted = true),
            MinecraftProcessStatus(MinecraftProcessState.CRASHED, exitCode = 1))
        assertTrue(report.findings.any { it.code == DiagnosticCode.MEMORY && it.confidence == HealthConfidence.PROBABLE })
        assertEquals(1, report.logsRead)
        assertFalse(report.export().contains(secret))
        assertFalse(report.export().contains(root.toString()))
        assertFalse(report.export().contains("OutOfMemoryError"))
        assertEquals(1, report.exitCode)
        Files.setLastModifiedTime(log, FileTime.from(Instant.now().minusSeconds(3600)))
        val stale = GameDiagnostics().inspect(version(), platform, layout, LaunchEvidence("test", null, 21, processStarted = true))
        assertEquals(0, stale.logsRead)
        assertFalse(stale.findings.any { it.code == DiagnosticCode.MEMORY })
    }

    @Test fun `unrelated run and symlinked log never diagnose the selected game`() = runBlocking {
        val layout = layout(); layout.ensureDirectories()
        val old = root.resolve("old.log"); Files.writeString(old, "OutOfMemoryError")
        val report = GameDiagnostics().inspect(version(), platform, layout,
            LaunchEvidence("other-version", null, 21), MinecraftProcessStatus(stdoutLog = old))
        assertEquals(0, report.logsRead)
        assertNull(report.exitCode)
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Files.createDirectories(layout.gameDir.resolve("logs"))
            Files.createSymbolicLink(layout.gameDir.resolve("logs/latest.log"), old)
            assertEquals(0, GameDiagnostics().inspect(version(), platform, layout,
                LaunchEvidence("test", null, 21, processStarted = true)).logsRead)
        }
    }

    @Test fun `cancellation of repair releases coordinator and leaves no partial ready file`() = runBlocking {
        val layout = layout(); layout.ensureDirectories()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        var slow = true
        val http = HttpClient(MockEngine {
            if (slow) { entered.complete(Unit); kotlinx.coroutines.awaitCancellation() }
            respond(payload)
        })
        val process = MinecraftProcessManager(root.resolve("logs")) { _, _, _ -> error("Must not start") }
        val runtime = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener) = JavaRuntime(root, root.resolve("java"), 21, "21")
        }
        val coordinator = MinecraftLaunchCoordinator(MinecraftPreparationService(DownloadManager(http)), runtime,
            LaunchPlanBuilder(), process, layout, platform)
        try {
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch {
                coordinator.prepareForInstallation(version(), layout, null)
            }
            kotlinx.coroutines.withTimeout(5000) { entered.await() }
            job.cancel(); job.join()
            assertFalse(Files.exists(layout.versionJar("test")))
            assertFalse(Files.exists(layout.versionJar("test").resolveSibling("test.jar.part")))
            assertEquals(LaunchPhase.IDLE, coordinator.status.value.phase)
            slow = false
            coordinator.prepareForInstallation(version(), layout, null)
            assertEquals(0, GameDiagnostics().inspect(version(), platform, layout).damagedFiles)
        } finally { http.close(); process.shutdown() }
    }

    @Test fun `preparation and successful repair never reuse previous same game exit or logs`() = runBlocking {
        val layout = layout(); layout.ensureDirectories()
        val log = layout.gameDir.resolve("logs/latest.log")
        Files.createDirectories(log.parent); Files.writeString(log, "OutOfMemoryError")
        val report = GameDiagnostics().inspect(version(), platform, layout,
            LaunchEvidence("test", null, 21, processStarted = false),
            MinecraftProcessStatus(MinecraftProcessState.CRASHED, exitCode = 1, startedAt = Instant.now().minusSeconds(1)))
        assertEquals(0, report.logsRead)
        assertNull(report.exitCode)
        assertFalse(report.findings.any { it.code == DiagnosticCode.MEMORY })
    }

    @Test fun `redactor covers headers arguments json query fields and jwt`() {
        val cases = listOf("Authorization: Bearer supersecret", "--accessToken supersecret",
            "{\"refresh_token\":\"supersecret\"}", "https://example.invalid/?access_token=supersecret&next=1",
            "password=supersecret", "XBL3.0 x=123;supersecret", "eyJheader.payload.signature")
        cases.forEach { assertFalse(SecretRedactor.redact(it).contains("supersecret"), it) }
        assertEquals("healthy message", SecretRedactor.redact("healthy message"))
    }

    @Test fun `disk admission fails before transfer and protects against overflow`() {
        val disk = DiskSpace { 100 }
        assertFailsWith<InsufficientDiskSpaceException> { disk.requireAvailable(root.resolve("client"), 101) }
        assertFailsWith<InsufficientDiskSpaceException> { disk.requireAvailable(root.resolve("client"), Long.MAX_VALUE) }
        DiskSpace { Long.MAX_VALUE }.requireAvailable(root.resolve("client"), 1024)
    }
}

package pl.syntaxdevteam.syntaxlauncher.core.launch

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeEventListener
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.JavaRuntimeRequirement
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArgument
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArchitecture
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArtifact
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPreparationService
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class MinecraftLaunchCoordinatorTest {
    private val platform = MinecraftPlatform(
        MinecraftOperatingSystem.LINUX, MinecraftArchitecture.X86_64, "test", "amd64",
    )

    @Test
    fun preparesClientJavaLaunchPlanAndProcess() = runBlocking {
        val client = "client-fixture".encodeToByteArray()
        val root = Files.createTempDirectory("syntaxlauncher-pipeline-")
        val http = HttpClient(MockEngine { respond(client) })
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
        val runtime = JavaRuntime(java.parent.parent, java, 21, "21")
        var captured: LaunchPlan? = null
        val process = MinecraftProcessManager(root.resolve("logs")) { plan, stdout, stderr ->
            captured = plan
            ProcessBuilder(java.toString(), "-version")
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start()
        }
        val layout = makeLayout(root)
        val instanceLayout = layout.copy(gameDir = root.resolve("instances/isolated-game"))
        val coordinator = MinecraftLaunchCoordinator(
            preparation = MinecraftPreparationService(
                DownloadManager(http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))),
            runtimes = runtimeManager(runtime),
            plans = LaunchPlanBuilder(),
            processes = process,
            layout = layout,
            platform = platform,
        )
        try {
            val status = coordinator.launch(version(client),
                OfflineAccount.create("SyntaxTest"), LaunchOptions(maximumMemoryMiB = 3072),
                instanceLayout = instanceLayout)
            assertEquals(LaunchPhase.STARTED, status.phase)
            assertTrue(Files.exists(layout.versionJar("test-release")))
            val plan = assertNotNull(captured)
            assertTrue("-Xmx3072M" in plan.jvmArguments)
            assertTrue("SyntaxTest" in plan.gameArguments)
            assertEquals(runtime.executable, plan.runtime.executable)
            assertEquals(instanceLayout.gameDir, plan.workingDirectory)
            assertTrue(Files.isDirectory(instanceLayout.gameDir))
            assertTrue(Files.isDirectory(layout.versionsDir))
            assertNotNull(process.status.value.stderrLog)
        } finally {
            process.shutdown(MinecraftShutdownPolicy.DETACH)
            http.close()
        }
    }

    @Test
    fun actualJavaChildWritesToOnlyItsSelectedInstanceDirectory() = runBlocking {
        val bytes = "client-fixture".encodeToByteArray()
        val root = Files.createTempDirectory("syntax-instance-actual-process-")
        val source = root.resolve("FixtureGame.java")
        Files.writeString(source, """
            import java.nio.file.*;
            public class FixtureGame {
                public static void main(String[] args) throws Exception {
                    Path game = Path.of(System.getProperty("user.dir"));
                    String id = game.getParent().getFileName().toString();
                    Path world = game.resolve("saves").resolve(id);
                    Files.createDirectories(world);
                    Files.writeString(world.resolve("level.dat"), "world-" + id);
                    Files.writeString(game.resolve("options.txt"), "profile-" + id);
                }
            }
        """.trimIndent())

        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
        val runtime = JavaRuntime(java.parent.parent, java, 21, "21")
        val http = HttpClient(MockEngine { respond(bytes) })
        val processManager = MinecraftProcessManager(root.resolve("logs")) { plan, stdout, stderr ->
            ProcessBuilder(java.toString(), source.toString())
                .directory(plan.workingDirectory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start()
        }
        val shared = makeLayout(root)
        val coordinator = MinecraftLaunchCoordinator(
            preparation = MinecraftPreparationService(
                DownloadManager(http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))),
            runtimes = runtimeManager(runtime),
            plans = LaunchPlanBuilder(),
            processes = processManager,
            layout = shared,
            platform = platform,
        )
        try {
            val one = shared.copy(gameDir = root.resolve("instances/a/game"))
            val two = shared.copy(gameDir = root.resolve("instances/b/game"))
            for (target in listOf(one, two)) {
                assertEquals(LaunchPhase.STARTED, coordinator.launch(
                    version(bytes), OfflineAccount.create("SyntaxTest"),
                    LaunchOptions(maximumMemoryMiB = 2048), instanceLayout = target,
                ).phase)
                withTimeout(30_000) {
                    while (processManager.status.value.state == MinecraftProcessState.RUNNING) {
                        delay(25)
                    }
                }
                assertEquals(MinecraftProcessState.STOPPED, processManager.status.value.state)
            }
            assertEquals("world-a", Files.readString(one.gameDir.resolve("saves/a/level.dat")))
            assertEquals("world-b", Files.readString(two.gameDir.resolve("saves/b/level.dat")))
            assertEquals("profile-a", Files.readString(one.gameDir.resolve("options.txt")))
            assertEquals("profile-b", Files.readString(two.gameDir.resolve("options.txt")))
            assertTrue(Files.notExists(one.gameDir.resolve("saves/b/level.dat")))
            assertTrue(Files.notExists(two.gameDir.resolve("saves/a/level.dat")))
            assertEquals(shared.versionsDir, one.versionsDir)
            assertEquals(shared.assetsDir, two.assetsDir)
        } finally {
            processManager.shutdown(MinecraftShutdownPolicy.DETACH)
            http.close()
        }
    }

    @Test
    fun missingRuntimeRequirementFailsBeforeNetwork() = runBlocking {
        val root = Files.createTempDirectory("syntaxlauncher-no-java-")
        var calls = 0
        val http = HttpClient(MockEngine { calls++; respond("unexpected") })
        val process = MinecraftProcessManager(root.resolve("logs"))
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val coordinator = MinecraftLaunchCoordinator(
            MinecraftPreparationService(DownloadManager(http)),
            runtimeManager(JavaRuntime(java.parent.parent, java, 21, "21")),
            LaunchPlanBuilder(), process, makeLayout(root), platform,
        )
        try {
            val result = coordinator.launch(
                version("client-fixture".encodeToByteArray()).copy(javaRequirement = null),
                OfflineAccount.create("SyntaxTest"), LaunchOptions())
            assertEquals(LaunchPhase.FAILED, result.phase)
            assertEquals(0, calls)
            assertEquals(MinecraftProcessState.IDLE, process.status.value.state)
        } finally {
            process.shutdown()
            http.close()
        }
    }

    private fun runtimeManager(runtime: JavaRuntime): JavaRuntimeManager =
        object : JavaRuntimeManager {
            override suspend fun resolve(
                requirement: JavaRuntimeRequirement,
                platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener,
            ): JavaRuntime = runtime
        }

    private fun version(client: ByteArray) = ResolvedMinecraftVersion(
        id = "test-release",
        type = MinecraftVersionType.RELEASE,
        mainClass = "test.Main",
        assetsId = null,
        assetIndex = null,
        clientDownload = MinecraftArtifact(
            sha1 = MessageDigest.getInstance("SHA-1").digest(client)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
            size = client.size.toLong(),
            url = "https://example.invalid/client.jar",
        ),
        libraries = emptyList(),
        arguments = MinecraftArguments(
            jvm = listOf(MinecraftArgument(listOf("-cp", "\${classpath}"))),
            game = listOf(MinecraftArgument(listOf("--username", "\${auth_player_name}"))),
        ),
        javaRequirement = JavaRuntimeRequirement("java-runtime-delta", 21),
        logging = null,
    )

    private fun makeLayout(root: Path) = MinecraftInstallationLayout(
        gameDir = root.resolve("game"),
        versionsDir = root.resolve("cache/versions"),
        librariesDir = root.resolve("cache/libraries"),
        assetsDir = root.resolve("cache/assets"),
        assetIndexesDir = root.resolve("cache/assets/indexes"),
        assetObjectsDir = root.resolve("cache/assets/objects"),
        loggingConfigsDir = root.resolve("cache/assets/log_configs"),
        nativesDir = root.resolve("cache/natives"),
    )
}

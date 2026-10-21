package pl.syntaxdevteam.syntaxlauncher.core.launch

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeEventListener
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** Synthetic metadata through all real adapters; deliberately does not boot Minecraft. */
class LoaderPipelineMatrixTest {
    @TempDir lateinit var root: Path

    @Test
    fun allFiveModesSharePreparationRuntimePlanProcessAndIsolatedGameDirectories() = runBlocking {
        val clientBytes = "fixture Minecraft client".toByteArray()
        val processor = processorJar()
        val installers = listOf("forge", "neoforge").associateWith { installer(it, processor) }
        val seen = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            val host = request.url.host
            val path = request.url.encodedPath
            seen += "$host$path"
            when {
                host in setOf("example.invalid", "maven.fabricmc.net", "maven.quiltmc.org") -> respond(clientBytes)
                path.endsWith("maven-metadata.xml") -> respond("""<metadata><versioning><versions>
                    <version>1.21.1-52.1.20</version><version>21.1.213</version>
                    </versions></versioning></metadata>""")
                host == "meta.fabricmc.net" || host == "meta.quiltmc.org" -> {
                    val id = if (host == "meta.fabricmc.net") "fabric" else "quilt"
                    if (path.endsWith("/profile/json")) respond("""{
                        "inheritsFrom":"1.21.1","mainClass":"fixture.$id.Main",
                        "libraries":[{"name":"net.example:$id:1.0","url":"https://maven.${if (id == "fabric") "fabricmc.net" else "quiltmc.org"}/"}],
                        "arguments":{"game":["--loader","$id"]}}
                    """) else respond("""[{"loader":{"version":"1.0","stable":true}}]""")
                }
                else -> {
                    val id = if (host == "maven.minecraftforge.net") "forge" else "neoforge"
                    val archive = installers.getValue(id)
                    when {
                        path.endsWith(".sha1") -> respond(hash(archive, "SHA-1"))
                        path.endsWith(".sha256") -> respond(hash(archive, "SHA-256"))
                        path.endsWith("-installer.jar") -> respond(archive)
                        else -> error("Unexpected download: ${request.url}")
                    }
                }
            }
        })
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("runtimes"))
        val shared = MinecraftInstallationLayout.from(paths)
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
        val runtime = JavaRuntime(java.parent.parent, java, 21, "fixture runtime")
        val requirements = mutableListOf<JavaRuntimeRequirement>()
        val runtimes = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener): JavaRuntime {
                requirements += requirement
                return runtime
            }
        }
        val plans = mutableListOf<LaunchPlan>()
        val processes = MinecraftProcessManager(paths.logsDir) { plan, stdout, stderr ->
            plans += plan
            ProcessBuilder(java.toString(), "-version").directory(plan.workingDirectory.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
        }
        val downloader = DownloadManager(http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
        val coordinator = MinecraftLaunchCoordinator(
            MinecraftPreparationService(downloader), runtimes, LaunchPlanBuilder(), processes, shared,
            MinecraftPlatform(MinecraftOperatingSystem.LINUX, MinecraftArchitecture.X86_64, "fixture", "amd64"),
            ModLoaderRegistry(listOf(FabricLoaderProvider(http), QuiltLoaderProvider(http),
                NeoForgeLoaderProvider(http, downloader), ForgeLoaderProvider(http, downloader))),
        )
        val base = VersionMetadataParser().parse("""{
            "id":"1.21.1","mainClass":"fixture.vanilla.Main",
            "downloads":{"client":{"url":"https://example.invalid/client.jar",
                "sha1":"${hash(clientBytes, "SHA-1")}","size":${clientBytes.size}}},
            "javaVersion":{"component":"java-runtime-delta","majorVersion":21},
            "arguments":{"jvm":["-cp","${'$'}{classpath}"],
                "game":["--username","${'$'}{auth_player_name}","--gameDir","${'$'}{game_directory}"]}}
        """)
        val selections = listOf(null, ModLoaderSelection("fabric", "1.0"), ModLoaderSelection("quilt", "1.0"),
            ModLoaderSelection("neoforge", "21.1.213"), ModLoaderSelection("forge", "52.1.20"))
        try {
            for (selection in selections) {
                val id = selection?.id ?: "vanilla"
                val layout = shared.copy(gameDir = root.resolve("instances/$id/game"))
                Files.createDirectories(layout.gameDir.resolve("saves"))
                Files.writeString(layout.gameDir.resolve("saves/world.txt"), id)
                val result = coordinator.launch(base, OfflineAccount.create("SyntaxTest"),
                    LaunchOptions(maximumMemoryMiB = 2048), instanceLayout = layout, loader = selection)
                assertEquals(LaunchPhase.STARTED, result.phase, "$id: ${result.detail}")
                val plan = plans.last()
                assertEquals("fixture.$id.Main", plan.mainClass)
                assertEquals(layout.gameDir, plan.workingDirectory)
                assertEquals(runtime, plan.runtime)
                assertTrue("SyntaxTest" in plan.gameArguments)
                assertTrue(layout.gameDir.toString() in plan.gameArguments)
                assertTrue(plan.jvmArguments.any { shared.versionJar(base.id).toString() in it })
                if (selection != null) assertTrue(id in plan.gameArguments)
                assertEquals(id, Files.readString(layout.gameDir.resolve("saves/world.txt")))
                withTimeout(10_000) {
                    while (processes.status.value.state == MinecraftProcessState.RUNNING) delay(10)
                }
                assertEquals(MinecraftProcessState.STOPPED, processes.status.value.state)
            }
            assertEquals(List(5) { base.javaRequirement }, requirements)
            assertEquals(1, seen.count { it == "example.invalid/client.jar" }, "All modes reuse the verified Vanilla client.")
            assertEquals(5, plans.map { it.workingDirectory }.distinct().size)
            for (id in listOf("forge", "neoforge")) {
                assertEquals("fixture runtime library", Files.readString(
                    shared.librariesDir.resolve("net/example/$id/1.0/$id-1.0.jar")))
            }
        } finally {
            processes.shutdown(MinecraftShutdownPolicy.TERMINATE)
            http.close()
        }
    }

    private fun processorJar(): ByteArray {
        val source = root.resolve("FixtureProcessor.java")
        Files.writeString(source, """
            import java.nio.file.*;
            public class FixtureProcessor {
                public static void main(String[] args) throws Exception {
                    Path output = Path.of(args[0]);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "fixture runtime library");
                }
            }
        """.trimIndent())
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler())
        assertEquals(0, compiler.run(null, null, null, "-d", root.toString(), source.toString()))
        val manifest = Manifest().apply {
            mainAttributes.putValue("Manifest-Version", "1.0")
            mainAttributes.putValue("Main-Class", "FixtureProcessor")
        }
        val out = ByteArrayOutputStream()
        JarOutputStream(out, manifest).use {
            it.putNextEntry(JarEntry("FixtureProcessor.class"))
            it.write(Files.readAllBytes(root.resolve("FixtureProcessor.class")))
            it.closeEntry()
        }
        return out.toByteArray()
    }

    private fun installer(id: String, processor: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val files = mapOf(
                "install_profile.json" to """{"minecraft":"1.21.1",
                    "libraries":[{"name":"net.example:processor:1.0"}],
                    "processors":[{"sides":["client"],"jar":"net.example:processor:1.0",
                        "classpath":[],"args":["[net.example:$id:1.0]"],
                        "outputs":{"[net.example:$id:1.0]":"${hash("fixture runtime library".toByteArray(), "SHA-1")}"}}]}
                """.toByteArray(),
                "version.json" to """{"id":"$id-fixture","inheritsFrom":"1.21.1",
                    "mainClass":"fixture.$id.Main","libraries":[{"name":"net.example:$id:1.0","url":""}],
                    "arguments":{"game":["--loader","$id"]}}
                """.toByteArray(),
                "maven/net/example/processor/1.0/processor-1.0.jar" to processor,
            )
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun hash(bytes: ByteArray, algorithm: String): String = MessageDigest.getInstance(algorithm)
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}

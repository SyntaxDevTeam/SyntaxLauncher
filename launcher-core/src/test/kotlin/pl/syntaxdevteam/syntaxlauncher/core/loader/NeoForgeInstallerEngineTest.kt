package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class NeoForgeInstallerEngineTest {
    private val version = "21.1.213"
    private val jarName = "neoforge-" + version + "-installer.jar"

    private fun processor(): ByteArray {
        val root = Files.createTempDirectory("neoforge-fixture-processor")
        val source = root.resolve("Processor.java")
        Files.writeString(source, """
            package example;
            import java.nio.file.*;
            public class Processor {
                public static void main(String[] args) throws Exception {
                    Path output = Path.of(args[0]);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "generated-client-artifact");
                }
            }
        """.trimIndent())
        val compiler = ToolProvider.getSystemJavaCompiler()
        check(compiler != null && compiler.run(null, null, null,
            "-d", root.toString(), source.toString()) == 0)
        val manifest = Manifest()
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")
        manifest.mainAttributes.putValue("Main-Class", "example.Processor")
        val result = ByteArrayOutputStream()
        JarOutputStream(result, manifest).use { jar ->
            jar.putNextEntry(JarEntry("example/Processor.class"))
            jar.write(Files.readAllBytes(root.resolve("example/Processor.class")))
            jar.closeEntry()
        }
        return result.toByteArray()
    }

    private fun installer(processor: ByteArray, wrongTarget: Boolean = false): ByteArray {
        val profile = """{
            "minecraft":"1.21.1",
            "libraries":[{"name":"net.example:processor:1.0"}],
            "processors":[{"sides":["client"],"jar":"net.example:processor:1.0",
                "classpath":["net.example:processor:1.0"],
                "args":["[net.example:generated:1.0]"],
                "outputs":{"[net.example:generated:1.0]":"ignored"}}]
        }""".replace("1.21.1", if (wrongTarget) "1.21.2" else "1.21.1")
        val versionProfile = """{
            "id":"neoforge-fixture","inheritsFrom":"1.21.1",
            "mainClass":"example.Launcher",
            "libraries":[{"name":"net.example:generated:1.0"}],
            "arguments":{"game":["--test-neo"],"jvm":[]}
        }"""
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            listOf("install_profile.json" to profile.toByteArray(),
                "version.json" to versionProfile.toByteArray(),
                "maven/net/example/processor/1.0/processor-1.0.jar" to processor
            ).forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun layout(root: Path) = MinecraftInstallationLayout(
        gameDir = root.resolve("instances/isolated/game"),
        versionsDir = root.resolve("cache/versions"),
        librariesDir = root.resolve("cache/libraries"),
        assetsDir = root.resolve("cache/assets"),
        assetIndexesDir = root.resolve("cache/assets/indexes"),
        assetObjectsDir = root.resolve("cache/assets/objects"),
        loggingConfigsDir = root.resolve("cache/assets/logging"),
        nativesDir = root.resolve("cache/natives"),
    )

    private fun base() = ResolvedMinecraftVersion(
        "1.21.1", MinecraftVersionType.RELEASE, "minecraft.Main",
        null, null, null, emptyList(), MinecraftArguments(), null, null,
    )

    @Test fun stagedProcessorCreatesRuntimeLibraryAndDoesNotWriteToGameDirectory() = runBlocking {
        val root = Files.createTempDirectory("neoforge-engine")
        val dirs = layout(root)
        Files.createDirectories(dirs.versionDirectory("1.21.1"))
        Files.writeString(dirs.versionJar("1.21.1"), "vanilla client")
        val installer = installer(processor())
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".sha256")) respond(sha256(installer))
            else if (request.url.encodedPath.endsWith(".jar")) respond(installer)
            else error("Unexpected network request: " + request.url)
        })
        try {
            val manager = DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
            val runtime = JavaRuntime(java.parent.parent, java, 21, "21")
            val result = NeoForgeInstallerEngine(client, manager).install(base(), version, dirs, runtime)
            val generated = dirs.librariesDir.resolve("net/example/generated/1.0/generated-1.0.jar")
            assertTrue(Files.isRegularFile(generated))
            assertEquals("generated-client-artifact", Files.readString(generated))
            assertEquals("example.Launcher", result.mainClass)
            assertTrue(result.libraries.any { it.name == "net.example:generated:1.0" })
            assertEquals("--test-neo", result.arguments.game.single().values.single())
            assertTrue(Files.notExists(dirs.gameDir))
        } finally { client.close() }
    }

    @Test fun incorrectInstallerTargetFailsWithoutGeneratedArtifacts() = runBlocking {
        val root = Files.createTempDirectory("neoforge-engine-wrong")
        val dirs = layout(root)
        Files.createDirectories(dirs.versionDirectory("1.21.1"))
        Files.writeString(dirs.versionJar("1.21.1"), "vanilla client")
        val bytes = installer(processor(), wrongTarget = true)
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".sha256")) respond(sha256(bytes))
            else respond(bytes)
        })
        try {
            val downloader = DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
            val runtime = JavaRuntime(java.parent.parent, java, 21, "21")
            assertFailsWith<IllegalArgumentException> {
                NeoForgeInstallerEngine(client, downloader).install(base(), version, dirs, runtime)
            }
            assertTrue(Files.notExists(dirs.librariesDir.resolve("net/example/generated/1.0/generated-1.0.jar")))
            assertTrue(Files.notExists(dirs.gameDir))
        } finally { client.close() }
    }
}

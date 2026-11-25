package pl.syntaxdevteam.syntaxlauncher.core.content

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeEventListener
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.LauncherHttpClientFactory
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** Opt-in private official installer smoke; no Minecraft game boot or public binary redistribution. */
class OptiFineOfficialSmokeTest {
    @Test fun officialStableVanillaComponent() = verify("1.21.1")
    @Test fun officialPreviewFor26_1_2() = verify("26.1.2")
    @Test fun officialPreviewFor26_2FromShaderSetup() = verify("26.2")

    private fun verify(game: String) = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_OPTIFINE_LIVE_SMOKE") == "1")
        val root = Files.createTempDirectory("optifine-official-smoke-")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes"))
        val layout = MinecraftInstallationLayout.from(paths)
        val instances = MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"), layout)
        val instance = instances.create("OptiFine private smoke", game)
        val instanceLayout = instances.layoutFor(instance)
        val world = instanceLayout.gameDir.resolve("saves/keep.txt")
        Files.createDirectories(world.parent); Files.writeString(world, "world must stay unchanged")
        val http = LauncherHttpClientFactory.create()
        val platform = MinecraftPlatform.detect()
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
        val runtimes = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener): JavaRuntime {
                val major = if (game == "1.21.1") 21 else 25
                assertEquals(major, requirement.majorVersion)
                val override = System.getenv("SYNTAXLAUNCHER_OPTIFINE_TEST_JAVA_$major")
                val executable = override?.let(Path::of) ?: java
                return JavaRuntime(executable.parent.parent, executable, major, "Smoke-test Java")
            }
        }
        try {
            val catalog = MinecraftVersionCatalog(http)
            val downloads = DownloadManager(http)
            val service = OptiFineCatalog(http, downloads, instances, catalog, runtimes, platform)
            val offered = service.projects(instance).first()
            if (game == "26.2") {
                val source = root.resolve("fixture.zip")
                ZipOutputStream(Files.newOutputStream(source)).use { zip ->
                    zip.putNextEntry(ZipEntry("shaders/final.fsh"))
                    zip.write("#version 120\nvoid main() { gl_FragColor = vec4(1.0); }".toByteArray())
                    zip.closeEntry()
                }
                val packs = LocalPackManager(instances)
                val shaders = ModrinthPackCatalog(http, instances, packs, downloads,
                    kind = LocalPackKind.SHADER_PACK, optiFine = service)
                packs.importZip(instance.id, LocalPackKind.SHADER_PACK, source)
                assertFalse(service.enabled(instance.id))
                val installed = shaders.selectInstalledShader(instance.id, "fixture.zip")
                assertTrue(installed.packs.single().selected)
                assertContains(Files.readString(instanceLayout.gameDir.resolve("optionsshaders.txt")), "shaderPack=fixture.zip")
            } else {
                service.install(instance.id, offered)
            }
            assertEquals(offered.id, service.ensureForShaders(instance))
            assertTrue(service.enabled(instance.id))
            val base = MinecraftVersionResolver(http).resolve(catalog.fetchManifest().versions.single { it.id == game })
            val merged = LocalGraphicsProfileResolver().resolve(base, instanceLayout, null)
            assertEquals(base.id, merged.id)
            assertEquals(base.clientDownload, merged.clientDownload)
            assertEquals(base.javaRequirement, merged.javaRequirement)
            assertNotEquals(base.mainClass, merged.mainClass)
            assertTrue(merged.libraries.any { it.name.startsWith("optifine:") })
            assertTrue("--tweakClass" in merged.arguments.resolveGame(platform), "Official OptiFine profile must contribute its own arguments.")
            assertEquals("world must stay unchanged", Files.readString(world))
            service.disable(instance.id)
            assertEquals(base, LocalGraphicsProfileResolver().resolve(base, instanceLayout, null))
            println("Official OptiFine ${offered.id} install/overlay PASS; isolated evidence root: $root")
        } finally { http.close() }
    }
}

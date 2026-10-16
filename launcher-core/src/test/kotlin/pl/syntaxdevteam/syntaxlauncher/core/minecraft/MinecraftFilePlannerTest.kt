package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class MinecraftFilePlannerTest {
    private val root = Path.of("test-root")
    private val layout = MinecraftInstallationLayout(
        gameDir = root.resolve("game"),
        versionsDir = root.resolve("cache/versions"),
        librariesDir = root.resolve("cache/libraries"),
        assetsDir = root.resolve("cache/assets"),
        assetIndexesDir = root.resolve("cache/assets/indexes"),
        assetObjectsDir = root.resolve("cache/assets/objects"),
        loggingConfigsDir = root.resolve("cache/assets/log_configs"),
        nativesDir = root.resolve("cache/natives"),
    )

    @Test
    fun `plans client library native and asset index targets`() {
        val version = ResolvedMinecraftVersion(
            id = "26.2",
            type = MinecraftVersionType.RELEASE,
            mainClass = "net.minecraft.client.main.Main",
            assetsId = "26",
            assetIndex = MinecraftAssetIndex(
                id = "26",
                sha1 = "asset-index",
                size = 10,
                totalSize = 20,
                url = "https://example.invalid/assets.json",
            ),
            clientDownload = MinecraftArtifact(
                sha1 = "client",
                size = 100,
                url = "https://example.invalid/client.jar",
            ),
            libraries = listOf(
                MinecraftLibrary(
                    name = "org.example:demo:1.0",
                    downloads = MinecraftLibraryDownloads(
                        artifact = MinecraftArtifact(
                            path = "org/example/demo/1.0/demo-1.0.jar",
                            sha1 = "library",
                            size = 50,
                            url = "https://example.invalid/library.jar",
                        ),
                        classifiers = mapOf(
                            "natives-linux" to MinecraftArtifact(
                                path = "org/example/demo/1.0/demo-1.0-natives-linux.jar",
                                sha1 = "native",
                                size = 30,
                                url = "https://example.invalid/native.jar",
                            ),
                        ),
                    ),
                    natives = mapOf("linux" to "natives-linux"),
                ),
            ),
            arguments = MinecraftArguments(),
            javaRequirement = JavaRuntimeRequirement("java-runtime-delta", 21),
            logging = null,
        )

        val platform = MinecraftPlatform(
            os = MinecraftOperatingSystem.LINUX,
            architecture = MinecraftArchitecture.X86_64,
            osVersion = "6.0",
            rawArchitecture = "amd64",
        )

        val plan = MinecraftFilePlanner().planVersion(version, platform, layout)
        val client = assertNotNull(plan.client)

        assertEquals(root.resolve("cache/versions/26.2/26.2.jar"), client.target)
        assertEquals(1, plan.libraries.size)
        assertEquals(1, plan.natives.size)
        assertTrue(client.target in plan.classpath)
        assertEquals(root.resolve("cache/assets/indexes/26.json"), plan.assetIndex?.target)
    }

    @Test
    fun `plans asset object under hash prefix`() {
        val asset = MinecraftAssetObject(
            logicalPath = "example",
            hash = "abcdef0123456789abcdef0123456789abcdef01",
            size = 5,
        )

        val plan = MinecraftFilePlanner().planAssets(
            MinecraftAssetIndexContent(listOf(asset), virtual = false, mapToResources = false),
            layout,
        )

        assertEquals(
            root.resolve("cache/assets/objects/ab/${asset.hash}"),
            plan.single().target,
        )
    }
}

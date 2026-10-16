package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class MinecraftFilePlannerTest {
    private val layout = MinecraftInstallationLayout(
        gameDir = Path.of("/tmp/game"),
        versionsDir = Path.of("/tmp/cache/versions"),
        librariesDir = Path.of("/tmp/cache/libraries"),
        assetsDir = Path.of("/tmp/cache/assets"),
        assetIndexesDir = Path.of("/tmp/cache/assets/indexes"),
        assetObjectsDir = Path.of("/tmp/cache/assets/objects"),
        loggingConfigsDir = Path.of("/tmp/cache/assets/log_configs"),
        nativesDir = Path.of("/tmp/cache/natives"),
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

        assertEquals(Path.of("/tmp/cache/versions/26.2/26.2.jar"), plan.client?.target)
        assertEquals(1, plan.libraries.size)
        assertEquals(1, plan.natives.size)
        assertTrue(plan.classpath.contains(plan.client?.target))
        assertEquals(Path.of("/tmp/cache/assets/indexes/26.json"), plan.assetIndex?.target)
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
            Path.of("/tmp/cache/assets/objects/ab/${asset.hash}"),
            plan.single().target,
        )
    }
}

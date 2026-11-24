package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.storage.*

class LegacyAssetViewsTest {
    private fun layout(): MinecraftInstallationLayout {
        val root = Files.createTempDirectory("legacy-assets-")
        return MinecraftInstallationLayout.from(LauncherPaths(root.resolve("data"), root.resolve("config"),
            root.resolve("cache"), root.resolve("logs"), root.resolve("java"))).also { it.ensureDirectories() }
    }
    private fun asset(layout: MinecraftInstallationLayout, path: String = "sound/test.ogg"): MinecraftAssetObject {
        val bytes = "verified audio".toByteArray()
        val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        val source = layout.assetObjectsDir.resolve(hash.take(2)).resolve(hash)
        Files.createDirectories(source.parent); Files.write(source, bytes)
        return MinecraftAssetObject(path, hash, bytes.size.toLong())
    }
    @Test fun virtualViewsRepairButDifferentUserResourcesArePreserved() = runBlocking {
        val layout = layout()
        val asset = asset(layout)
        val views = LegacyAssetViews()
        val index = MinecraftAssetIndexContent(listOf(asset), true, true)
        views.materialize(index, "legacy", layout)
        val virtual = layout.assetsDir.resolve("virtual/legacy/sound/test.ogg")
        val resources = layout.gameDir.resolve("resources/sound/test.ogg")
        assertEquals("verified audio", Files.readString(virtual))
        assertEquals("verified audio", Files.readString(resources))
        Files.writeString(virtual, "corrupt generated file")
        views.materialize(index, "legacy", layout)
        assertEquals("verified audio", Files.readString(virtual))
        Files.writeString(resources, "user audio")
        assertFailsWith<IllegalStateException> { views.materialize(index, "legacy", layout) }
        assertEquals("user audio", Files.readString(resources))
    }
    @Test fun refusesTraversalAndCorruptObjectsBeforePublishingViews() = runBlocking {
        val layout = layout()
        val valid = asset(layout)
        for (path in listOf("../escape", "/absolute", "C:/absolute", "sound/../../escape", "sound\\escape")) {
            assertFailsWith<IllegalArgumentException> {
                LegacyAssetViews().materialize(MinecraftAssetIndexContent(listOf(valid.copy(logicalPath = path)), true, true), "legacy", layout)
            }
        }
        Files.writeString(layout.assetObjectsDir.resolve(valid.hash.take(2)).resolve(valid.hash), "bad")
        assertFailsWith<IllegalStateException> {
            LegacyAssetViews().materialize(MinecraftAssetIndexContent(listOf(valid), true, false), "legacy", layout)
        }
        assertFalse(Files.exists(layout.assetsDir.resolve("virtual")))
    }
    @Test fun neverFollowsLinksInsideTheGameResourceView() = runBlocking {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows", true))
        val layout = layout()
        val asset = asset(layout)
        val outside = Files.createTempDirectory("legacy-outside-")
        Files.createSymbolicLink(layout.gameDir.resolve("resources"), outside)
        assertFailsWith<IllegalStateException> {
            LegacyAssetViews().materialize(MinecraftAssetIndexContent(listOf(asset), false, true), "legacy", layout)
        }
        assertEquals(0L, Files.list(outside).use { it.count() })
    }
}

package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class ModrinthUpdateTransactionsTest {
    private fun manager(): Pair<MinecraftInstanceRepository, LocalModManager> {
        val root = Files.createTempDirectory("modrinth-update-test")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"),
            root.resolve("cache"), root.resolve("logs"), root.resolve("java"))
        val instances = MinecraftInstanceRepository(paths.dataDir.resolve("instances"),
            paths.dataDir.resolve("trash"), MinecraftInstallationLayout.from(paths))
        return instances to LocalModManager(instances)
    }

    private fun jar(path: Path, id: String, version: String): Path {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write("""{"schemaVersion":1,"id":"$id","name":"$id","version":"$version"}""".toByteArray())
            zip.closeEntry()
        }
        return path
    }

    @Test fun upgradesWithoutTouchingOtherInstancesAndRollsBackOriginalBytes() = runBlocking {
        val (instances, manager) = manager()
        val first = instances.create("Modded A", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val second = instances.create("Modded B", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val temp = Files.createTempDirectory("modrinth-update-files")
        val oldJar = jar(temp.resolve("old.jar"), "upgradeable", "1.0.0")
        val newJar = jar(temp.resolve("new.jar"), "upgradeable", "2.0.0")
        val oldInventory = manager.importBatch(first.id, listOf(oldJar),
            listOf(ModrinthManagedInstall("old.jar", "ABCDEFGH", "RELEASEA")))
        val oldMod = oldInventory.mods.single()
        val replaced = manager.replaceManaged(first.id, listOf(
            ModrinthReplacement("old.jar", "ABCDEFGH", "RELEASEA", oldMod.sha256, "RELEASEB", newJar)
        ))
        assertEquals(listOf("new.jar"), replaced.mods.map { it.fileName })
        assertEquals("RELEASEB", replaced.mods.single().modrinth?.versionId)
        assertEquals("2.0.0", replaced.mods.single().metadata?.version)
        assertTrue(manager.list(second.id).mods.isEmpty())
        val after = LocalModManager(instances).rollbackLastManagedUpdate(first.id)
        assertEquals("old.jar", after.mods.single().fileName)
        assertEquals("1.0.0", after.mods.single().metadata?.version)
        assertEquals(oldMod.sha256, after.mods.single().sha256)
        assertEquals("RELEASEA", after.mods.single().modrinth?.versionId)
        assertFailsWith<IllegalStateException> { manager.rollbackLastManagedUpdate(first.id) }
    }

    @Test fun refusesEditedTrackedJarAndRetainsUserBytes() = runBlocking {
        val (instances, manager) = manager()
        val profile = instances.create("Edited", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val temp = Files.createTempDirectory("modified-update")
        val oldJar = jar(temp.resolve("old.jar"), "upgradeable", "1.0.0")
        val updated = jar(temp.resolve("new.jar"), "upgradeable", "2.0.0")
        val local = manager.importBatch(profile.id, listOf(oldJar),
            listOf(ModrinthManagedInstall("old.jar", "ABCDEFGH", "RELEASEA"))).mods.single()
        val installed = instances.gameDirectory(profile).resolve("mods/old.jar")
        jar(installed, "modified", "custom")
        assertFailsWith<IllegalArgumentException> {
            manager.replaceManaged(profile.id, listOf(ModrinthReplacement(
                "old.jar", "ABCDEFGH", "RELEASEA", local.sha256, "RELEASEB", updated)))
        }
        assertEquals("modified", manager.list(profile.id).mods.single().metadata?.id)
    }

    @Test fun disabledModsStayDisabledThroughUpdateAndRollback() = runBlocking {
        val (instances, manager) = manager()
        val profile = instances.create("Disabled", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val temp = Files.createTempDirectory("disabled-update")
        val old = jar(temp.resolve("old.jar"), "upgradeable", "1.0.0")
        val new = jar(temp.resolve("new.jar"), "upgradeable", "2.0.0")
        val oldMod = manager.importBatch(profile.id, listOf(old),
            listOf(ModrinthManagedInstall("old.jar", "ABCDEFGH", "RELEASEA"))).mods.single()
        manager.setEnabled(profile.id, "old.jar", false)
        val update = manager.replaceManaged(profile.id, listOf(
            ModrinthReplacement("old.jar.disabled", "ABCDEFGH", "RELEASEA",
                oldMod.sha256, "RELEASEB", new)
        ))
        assertEquals("new.jar.disabled", update.mods.single().fileName)
        assertEquals(false, update.mods.single().enabled)
        val restored = manager.rollbackLastManagedUpdate(profile.id)
        assertEquals("old.jar.disabled", restored.mods.single().fileName)
        assertEquals(false, restored.mods.single().enabled)
    }
}

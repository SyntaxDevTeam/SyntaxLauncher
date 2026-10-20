package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class LocalModManagerTest {
    private fun setup(): Pair<MinecraftInstanceRepository, LocalModManager> {
        val root = Files.createTempDirectory("syntax-local-mod-")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"),
            root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes"))
        val instances = MinecraftInstanceRepository(
            paths.dataDir.resolve("instances"),
            paths.dataDir.resolve("instance-trash"),
            MinecraftInstallationLayout.from(paths),
        )
        return instances to LocalModManager(instances)
    }

    private fun jar(path: Path, id: String, requires: String = "{}", breaks: String = "{}"): Path {
        ZipOutputStream(Files.newOutputStream(path)).use { archive ->
            archive.putNextEntry(ZipEntry("fabric.mod.json"))
            archive.write(
                """{"schemaVersion":1,"id":"$id","name":"Test $id","version":"1.2.3","depends":$requires,"breaks":$breaks}"""
                    .toByteArray())
            archive.closeEntry()
        }
        return path
    }

    @Test fun importingTogglingAndRemovingKeepsInstanceDataSafe() = runBlocking {
        val (repo, manager) = setup()
        val first = repo.create("Fabric A", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val second = repo.create("Fabric B", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val source = jar(Files.createTempFile("demo-mod", ".jar"), "demo")
        val initial = manager.importJar(first.id, source)
        assertEquals(1, initial.mods.size)
        val entry = initial.mods.single()
        assertTrue(entry.enabled)
        assertEquals("demo", entry.metadata?.id)
        assertEquals("1.2.3", entry.metadata?.version)
        assertEquals(64, entry.sha256.length)
        assertTrue(Files.exists(source))
        assertTrue(manager.list(second.id).mods.isEmpty())

        val off = manager.setEnabled(first.id, entry.fileName, false).mods.single()
        assertFalse(off.enabled)
        assertTrue(off.fileName.endsWith(".jar.disabled"))
        assertTrue(Files.exists(repo.gameDirectory(first).resolve("mods").resolve(off.fileName)))
        val on = manager.setEnabled(first.id, off.fileName, true).mods.single()
        assertTrue(on.enabled)
        assertEquals(entry.sha256, on.sha256)

        val removed = manager.remove(first.id, on.fileName)
        assertTrue(removed.mods.isEmpty())
        val trash = repo.gameDirectory(first).resolve(".syntaxlauncher-mod-trash")
        Files.newDirectoryStream(trash).use { assertEquals(1, it.count()) }
        assertTrue(Files.exists(source))
        assertTrue(manager.list(second.id).mods.isEmpty())
    }

    @Test fun missingDependenciesAndDuplicateIdsProduceWarnings() = runBlocking {
        val (repo, manager) = setup()
        val profile = repo.create("Fabric", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val a = jar(Files.createTempDirectory("mods").resolve("alpha.jar"), "alpha",
            """{"fabricloader":">=0.14.0","missing-lib":"*"}""")
        val b = jar(Files.createTempDirectory("mods").resolve("another.jar"), "alpha")
        val first = manager.importJar(profile.id, a)
        assertTrue(first.warnings.any { "missing-lib" in it })
        val second = manager.importJar(profile.id, b)
        assertTrue(second.warnings.any { "Duplicate enabled mod id 'alpha'" in it })
        assertFailsWith<IllegalStateException> {
            manager.importJar(profile.id, b)
        }
    }

    @Test fun refusesVanillaInvalidArchivesAndUntrustedNames() = runBlocking {
        val (repo, manager) = setup()
        val vanilla = repo.create("Vanilla", "26.3")
        val fabric = repo.create("Fabric", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val jar = jar(Files.createTempDirectory("mod-jar").resolve("example.jar"), "example")
        assertFailsWith<IllegalArgumentException> { manager.importJar(vanilla.id, jar) }
        val fake = Files.createTempDirectory("mod-bad").resolve("broken.jar")
        Files.writeString(fake, "not a ZIP/JAR")
        assertFailsWith<Exception> { manager.importJar(fabric.id, fake) }
        assertTrue(manager.list(fabric.id).mods.isEmpty())
        assertFailsWith<IllegalArgumentException> { manager.setEnabled(fabric.id, "../path.jar", false) }
    }

    @Test fun managedIdentitySurvivesRestartToggleAndRemoval() = runBlocking {
        val (repo, manager) = setup()
        val instance = repo.create("Managed", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val input = jar(Files.createTempDirectory("managed").resolve("tracked.jar"), "tracked")
        val installed = manager.importBatch(instance.id, listOf(input),
            listOf(ModrinthManagedInstall("tracked.jar", "ABCDEFGH", "RELEASEA")))
        assertEquals("ABCDEFGH", installed.mods.single().modrinth?.projectId)
        val reloaded = LocalModManager(repo)
        assertEquals("RELEASEA", reloaded.list(instance.id).mods.single().modrinth?.versionId)
        val disabled = reloaded.setEnabled(instance.id, "tracked.jar", false).mods.single()
        assertEquals("ABCDEFGH", disabled.modrinth?.projectId)
        val enabled = reloaded.setEnabled(instance.id, disabled.fileName, true).mods.single()
        assertEquals("ABCDEFGH", enabled.modrinth?.projectId)
        reloaded.remove(instance.id, enabled.fileName)
        assertTrue(reloaded.list(instance.id).mods.isEmpty())
        assertTrue(Files.readString(repo.gameDirectory(instance).resolve(".syntaxlauncher-modrinth.json"))
            .contains("\"entries\": []"))
    }

    @Test fun editedJarCannotInheritPreviousModrinthOrigin() = runBlocking {
        val (repo, manager) = setup()
        val instance = repo.create("Managed", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val input = jar(Files.createTempDirectory("managed").resolve("tracked.jar"), "tracked")
        manager.importBatch(instance.id, listOf(input),
            listOf(ModrinthManagedInstall("tracked.jar", "ABCDEFGH", "RELEASEA")))
        jar(repo.gameDirectory(instance).resolve("mods").resolve("tracked.jar"), "changed")
        val scanned = manager.list(instance.id)
        assertEquals(null, scanned.mods.single().modrinth)
        assertTrue(scanned.warnings.any { "changed since its Modrinth" in it })
    }

    @Test fun parsesQuiltModMetadataWithoutFabricManifest() = runBlocking {
        val (repo, manager) = setup()
        val profile = repo.create("Quilt", "1.21.1", loader = ModLoaderSelection("quilt", "0.28.0"))
        val file = Files.createTempDirectory("quilt-meta").resolve("quilt-only.jar")
        ZipOutputStream(Files.newOutputStream(file)).use { archive ->
            archive.putNextEntry(ZipEntry("quilt.mod.json"))
            archive.write("""{"schema_version":1,"quilt_loader":{
                "id":"quilt_mod","version":"2.0","group":"example",
                "metadata":{"name":"Quilt Example"},
                "depends":[{"id":"quilt_loader","versions":">=0.26"}]}}""".toByteArray())
            archive.closeEntry()
        }
        val inventory = manager.importJar(profile.id, file)
        assertEquals("quilt_mod", inventory.mods.single().metadata?.id)
        assertEquals("Quilt Example", inventory.mods.single().metadata?.name)
        assertEquals("2.0", inventory.mods.single().metadata?.version)
        assertEquals(">=0.26", inventory.mods.single().metadata?.depends?.get("quilt_loader"))
    }

    @Test fun disabledModDoesNotSatisfyRequiredDependency() = runBlocking {
        val (repo, manager) = setup()
        val profile = repo.create("Fabric", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val dependent = jar(Files.createTempDirectory("dependent").resolve("dependent.jar"),
            "dependent", """{"library":"*"}""")
        val library = jar(Files.createTempDirectory("library").resolve("library.jar"), "library")
        manager.importJar(profile.id, dependent)
        manager.importJar(profile.id, library)
        assertFalse(manager.list(profile.id).warnings.any { "requires 'library'" in it })
        manager.setEnabled(profile.id, "library.jar", false)
        assertTrue(manager.list(profile.id).warnings.any { "requires 'library'" in it })
    }
    @Test fun declaredConflictDisappearsWhenConflictingModIsDisabled() = runBlocking {
        val (repo, manager) = setup()
        val profile = repo.create("Conflict", "26.3", loader = ModLoaderSelection("fabric", "0.18.0"))
        val source = Files.createTempDirectory("mod-conflict-")
        manager.importJar(profile.id, jar(source.resolve("alpha.jar"), "alpha", breaks = """{"beta":"*"}"""))
        val result = manager.importJar(profile.id, jar(source.resolve("beta.jar"), "beta"))
        assertTrue(result.warnings.any { "conflict with enabled 'beta'" in it })
        val disabled = manager.setEnabled(profile.id, "beta.jar", false)
        assertTrue(disabled.warnings.none { "conflict with enabled 'beta'" in it })
    }

    @Test fun activeInstanceRejectsEveryLocalJarMutationWithoutChangingFiles() = runBlocking {
        val (repo, manager) = setup()
        val profile = repo.create("Busy", "26.3", loader = ModLoaderSelection("quilt", "0.28.0"))
        val source = Files.createTempDirectory("mod-busy-")
        manager.importJar(profile.id, jar(source.resolve("alpha.jar"), "alpha"))
        val next = jar(source.resolve("beta.jar"), "beta")
        val before = manager.list(profile.id)
        val guard = pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceActivityGuard(
            repo.gameDirectory(profile).parent.parent.resolve(".activity"))
        guard.begin(profile.id)
        try {
            assertFailsWith<pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInUseException> {
                manager.importJar(profile.id, next)
            }
            assertFailsWith<pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInUseException> {
                manager.setEnabled(profile.id, "alpha.jar", false)
            }
            assertFailsWith<pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInUseException> {
                manager.remove(profile.id, "alpha.jar")
            }
            assertEquals(before, manager.list(profile.id))
            assertTrue(Files.exists(next))
        } finally { guard.releasePreparation(profile.id) }
        assertFalse(manager.setEnabled(profile.id, "alpha.jar", false).mods.single().enabled)
    }

}

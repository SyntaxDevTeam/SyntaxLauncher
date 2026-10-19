package pl.syntaxdevteam.syntaxlauncher.core.instance

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class MinecraftInstanceRepositoryTest {
    private fun fixture(): Pair<MinecraftInstanceRepository, MinecraftInstallationLayout> {
        val base = Files.createTempDirectory("syntax-instances-test-")
        val paths = LauncherPaths(
            dataDir = base.resolve("data"),
            configDir = base.resolve("config"),
            cacheDir = base.resolve("cache"),
            logsDir = base.resolve("logs"),
            runtimesDir = base.resolve("runtimes"),
        )
        val layout = MinecraftInstallationLayout.from(paths)
        return MinecraftInstanceRepository(
            paths.dataDir.resolve("instances"),
            paths.dataDir.resolve("instance-trash"),
            layout,
        ) to layout
    }

    @Test fun independentGameDirectoriesShareOnlyImmutableCache() = runBlocking {
        val (repo, layout) = fixture()
        val first = repo.create("Creative", "26.3", maximumMemoryMiB = 6144)
        val second = repo.create("Survival", "26.3")
        assertNotEquals(first.id, second.id)
        assertTrue(UUID.fromString(first.id).toString() == first.id)
        assertNotEquals(repo.layoutFor(first).gameDir, repo.layoutFor(second).gameDir)
        assertEquals(layout.assetsDir, repo.layoutFor(first).assetsDir)
        assertEquals(layout.librariesDir, repo.layoutFor(second).librariesDir)
        assertEquals(layout.versionsDir, repo.layoutFor(second).versionsDir)
        val firstWorld = repo.gameDirectory(first).resolve("saves/WorldA/level.dat")
        Files.createDirectories(firstWorld.parent)
        Files.writeString(firstWorld, "independent world")
        assertFalse(Files.exists(repo.gameDirectory(second).resolve("saves/WorldA/level.dat")))
        val listing = repo.list()
        assertEquals(listOf("Creative", "Survival"), listing.instances.map { it.name })
        assertTrue(listing.problems.isEmpty())
    }

    @Test fun clonePreservesFilesSettingsAndIsIndependent() = runBlocking {
        val (repo, _) = fixture()
        val original = repo.create("Original", "26.2", maximumMemoryMiB = 8192,
            extraJvmArguments = listOf("-Dtest=ok"))
        val originalWorld = repo.gameDirectory(original).resolve("saves/world/level.dat")
        Files.createDirectories(originalWorld.parent)
        Files.writeString(originalWorld, "initial")
        val originalMetadata = repo.gameDirectory(original).parent.resolve("instance.json")
        Files.writeString(originalMetadata,
            Files.readString(originalMetadata).replaceFirst("{", """{ "futureInfo": "preserved","""))
        val clone = repo.cloneInstance(original.id, "Cloned")
        assertTrue(Files.readString(repo.gameDirectory(clone).parent.resolve("instance.json"))
            .contains("futureInfo"))
        assertNotEquals(original.id, clone.id)
        assertEquals(8192, clone.maximumMemoryMiB)
        assertEquals(listOf("-Dtest=ok"), clone.extraJvmArguments)
        val cloneWorld = repo.gameDirectory(clone).resolve("saves/world/level.dat")
        assertEquals("initial", Files.readString(cloneWorld))
        Files.writeString(cloneWorld, "modified clone")
        assertEquals("initial", Files.readString(originalWorld))
        val trashPath = repo.moveToTrash(clone.id)
        assertTrue(Files.exists(trashPath.resolve("game/saves/world/level.dat")))
        assertFalse(Files.exists(repo.gameDirectory(clone)))
        assertEquals(listOf("Original"), repo.list().instances.map { it.name })
    }

    @Test fun changesKeepUnknownMetadataAndRejectFutureSchema() = runBlocking {
        val (repo, _) = fixture()
        val profile = repo.create("One", "26.3")
        val file = repo.gameDirectory(profile).parent.resolve("instance.json")
        val json = Files.readString(file)
        Files.writeString(file, json.replaceFirst("{", """{ "futureField": {"value": 3},"""))
        val changed = repo.update(profile.copy(name = "Two", minecraftVersionId = "26.2"))
        assertEquals("Two", changed.name)
        assertTrue(Files.readString(file).contains("futureField"))
        val corrupted = Files.readString(file).replace(""""schemaVersion": 1""", """"schemaVersion": 99""")
        Files.writeString(file, corrupted)
        val problems = repo.list().problems
        assertEquals(1, problems.size)
        assertFailsWith<IllegalArgumentException> { repo.update(profile.copy(name = "Three")) }
        assertEquals(corrupted, Files.readString(file))
    }

    @Test fun legacyImportCopiesWithoutDeletingOriginalGameData() = runBlocking {
        val (repo, layout) = fixture()
        val legacyWorld = layout.gameDir.resolve("saves/legacy/level.dat")
        Files.createDirectories(legacyWorld.parent)
        Files.writeString(legacyWorld, "legacy world")
        assertTrue(repo.legacyGameAvailable())
        val copy = repo.importLegacy("From v0.1", "26.3")
        assertEquals("legacy world",
            Files.readString(repo.gameDirectory(copy).resolve("saves/legacy/level.dat")))
        assertEquals("legacy world", Files.readString(legacyWorld))
    }

    @Test fun restoreKeepsWorldsUuidAndUnknownMetadata() = runBlocking {
        val (repo, _) = fixture()
        assertTrue(repo.listTrashed().entries.isEmpty())
        val original = repo.create("Backup", "26.3")
        val world = repo.gameDirectory(original).resolve("saves/important/level.dat")
        Files.createDirectories(world.parent)
        Files.writeString(world, "world data")
        val metadata = repo.gameDirectory(original).parent.resolve("instance.json")
        Files.writeString(metadata, Files.readString(metadata)
            .replaceFirst("{", """{ "futureSetting": {"enabled": true},"""))
        val removed = repo.moveToTrash(original.id)
        val deleted = repo.listTrashed()
        assertTrue(deleted.problems.isEmpty())
        assertEquals(1, deleted.entries.size)
        assertEquals(removed.fileName.toString(), deleted.entries.single().entryName)
        val restored = repo.restoreFromTrash(deleted.entries.single().entryName)
        assertEquals(original, restored)
        assertEquals(original.id, repo.list().instances.single().id)
        assertEquals("world data", Files.readString(world))
        assertTrue(Files.readString(metadata).contains("futureSetting"))
        assertTrue(repo.listTrashed().entries.isEmpty())
    }

    @Test fun restoreCannotOverwriteAnExistingUuidAndRetainsBackup() = runBlocking {
        val (repo, _) = fixture()
        val original = repo.create("Safe", "26.3")
        val removed = repo.moveToTrash(original.id)
        val destination = repo.gameDirectory(original).parent
        Files.createDirectories(destination)
        val sentinel = destination.resolve("existing.txt")
        Files.writeString(sentinel, "do not replace")
        assertFailsWith<IllegalStateException> {
            repo.restoreFromTrash(removed.fileName.toString())
        }
        assertEquals("do not replace", Files.readString(sentinel))
        assertTrue(Files.exists(removed.resolve("instance.json")))
        Files.delete(sentinel)
        Files.delete(destination)
        assertEquals(original.id, repo.restoreFromTrash(removed.fileName.toString()).id)
    }

    @Test fun corruptOrDisguisedTrashDataIsNotPublished() = runBlocking {
        val (repo, _) = fixture()
        val original = repo.create("Recovery", "26.3")
        val removed = repo.moveToTrash(original.id)
        val metadata = removed.resolve("instance.json")
        Files.writeString(metadata, Files.readString(metadata)
            .replace(""""schemaVersion": 1""", """"schemaVersion": 999"""))
        val inventory = repo.listTrashed()
        assertTrue(inventory.entries.isEmpty())
        assertEquals(1, inventory.problems.size)
        assertFailsWith<IllegalArgumentException> {
            repo.restoreFromTrash(removed.fileName.toString())
        }
        assertTrue(Files.exists(metadata))
        assertFailsWith<IllegalArgumentException> { repo.restoreFromTrash("../../instances") }
    }

    @Test fun importReportsProgressAndKeepsOriginalUntouched() = runBlocking {
        val (repo, layout) = fixture()
        val source = layout.gameDir.resolve("saves/legacy/world.dat")
        Files.createDirectories(source.parent)
        Files.write(source, ByteArray(1024 * 1024) { 7 })
        val estimate = repo.estimateLegacyImport()
        assertEquals(1024L * 1024, estimate.totalBytes)
        assertEquals(1, estimate.fileCount)
        val updates = mutableListOf<InstanceCopyProgress>()
        val imported = repo.importLegacy("Imported", "26.3", onProgress = { updates.add(it) })
        assertEquals(0L, updates.first().copiedBytes)
        assertEquals(estimate.totalBytes, updates.last().copiedBytes)
        assertEquals(1, updates.last().copiedFiles)
        assertTrue(updates.last().fraction == 1f)
        assertEquals(Files.readAllBytes(source).size.toLong(),
            Files.size(repo.gameDirectory(imported).resolve("saves/legacy/world.dat")))
        assertEquals(7, Files.readAllBytes(source).first().toInt())
    }

    @Test fun abortedCopyNeverPublishesIncompleteProfile() = runBlocking {
        val (repo, layout) = fixture()
        val original = layout.gameDir.resolve("saves/large.dat")
        Files.createDirectories(original.parent)
        Files.write(original, ByteArray(1024 * 1024) { 5 })
        assertFailsWith<IllegalStateException> {
            repo.importLegacy("Cancel test", "26.3", onProgress = { status ->
                if (status.copiedBytes > 0) throw IllegalStateException("Simulated I/O failure")
            })
        }
        assertTrue(repo.list().instances.isEmpty())
        assertEquals(1024L * 1024, Files.size(original))
        assertTrue(Files.list(layout.gameDir.parent.resolve("instances")).use { stream ->
            stream.noneMatch { it.fileName.toString().startsWith(".creating-") }
        })
    }

    @Test fun rejectsLinkedSourceBeforeAnyInstanceIsPublished() = runBlocking {
        val (repo, layout) = fixture()
        val other = Files.createTempFile("linked-source-", ".dat")
        Files.createDirectories(layout.gameDir)
        val link = layout.gameDir.resolve("linked.dat")
        val supported = runCatching { Files.createSymbolicLink(link, other) }.isSuccess
        if (supported) {
            assertFailsWith<InstanceCopyException> { repo.importLegacy("Unsafe", "26.3") }
            assertTrue(repo.list().instances.isEmpty())
        }
    }

    @Test fun invalidMetadataCannotEscapeInstancesRoot() = runBlocking {
        val (repo, _) = fixture()
        assertFailsWith<IllegalArgumentException> { repo.create("Danger", "../outside") }
        assertFailsWith<IllegalArgumentException> { repo.moveToTrash("../../bad") }
    }
}

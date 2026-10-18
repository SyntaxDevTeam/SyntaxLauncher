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
        val clone = repo.cloneInstance(original.id, "Cloned")
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

    @Test fun invalidMetadataCannotEscapeInstancesRoot() = runBlocking {
        val (repo, _) = fixture()
        assertFailsWith<IllegalArgumentException> { repo.create("Danger", "../outside") }
        assertFailsWith<IllegalArgumentException> { repo.moveToTrash("../../bad") }
    }
}

package pl.syntaxdevteam.syntaxlauncher.core.content

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInUseException
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class LocalPackManagerTest {
    @TempDir lateinit var root: Path

    private fun repository(): MinecraftInstanceRepository {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("runtimes"))
        return MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"),
            MinecraftInstallationLayout.from(paths))
    }

    private val metadata = """{"pack":{"pack_format":34,"description":"Fixture pack"}}"""

    private fun archive(name: String, entries: Map<String, String>, stored: Boolean = false): Path {
        val path = root.resolve(name)
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            for ((entryName, content) in entries) {
                val bytes = content.toByteArray()
                val entry = ZipEntry(entryName)
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return path
    }

    @Test fun importRestartAndRecoverableRemovalPreserveSourceOtherPacksAndInstances() = runBlocking {
        val repo = repository()
        val first = repo.create("First", "1.21.1")
        val second = repo.create("Second", "1.21.1")
        val manager = LocalPackManager(repo)
        val resource = archive("Tekstury.zip", mapOf("pack.mcmeta" to metadata, "assets/fixture/example.txt" to "texture"))
        val shader = archive("Shaders.zip", mapOf("shaders/fixture.fsh" to "shader source"))
        val originalResource = Files.readAllBytes(resource)
        val originalShader = Files.readAllBytes(shader)
        val game = repo.gameDirectory(first)
        Files.writeString(game.resolve("options.txt"), "existing settings")
        Files.createDirectories(game.resolve("mods"))
        Files.writeString(game.resolve("mods/unrelated.jar"), "unrelated mod")

        assertTrue(manager.list(first.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
        assertFalse(Files.exists(game.resolve("resourcepacks")), "Listing must not create content directories.")
        assertEquals("Tekstury.zip", manager.importZip(first.id, LocalPackKind.RESOURCE_PACK, resource).packs.single().fileName)
        manager.importZip(first.id, LocalPackKind.SHADER_PACK, shader)
        val restarted = LocalPackManager(repository())
        assertEquals(1, restarted.list(first.id, LocalPackKind.RESOURCE_PACK).packs.size)
        assertEquals(1, restarted.list(first.id, LocalPackKind.SHADER_PACK).packs.size)
        assertTrue(restarted.list(second.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
        assertTrue(restarted.list(second.id, LocalPackKind.SHADER_PACK).packs.isEmpty())
        val removed = restarted.remove(first.id, LocalPackKind.RESOURCE_PACK, "Tekstury.zip")
        assertTrue(restarted.list(first.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
        assertTrue(removed.recoveryPath.startsWith(game.resolve(".syntaxlauncher-pack-trash")))
        assertContentEquals(originalResource, Files.readAllBytes(removed.recoveryPath))
        assertContentEquals(originalResource, Files.readAllBytes(resource))
        assertContentEquals(originalShader, Files.readAllBytes(shader))
        assertContentEquals(originalShader, Files.readAllBytes(game.resolve("shaderpacks/Shaders.zip")))
        assertEquals("existing settings", Files.readString(game.resolve("options.txt")))
        assertEquals("unrelated mod", Files.readString(game.resolve("mods/unrelated.jar")))
    }

    @Test fun collisionsAndBadArchivesNeverReplaceExistingBytesOrLeaveStagingFiles() = runBlocking {
        val repo = repository()
        val instance = repo.create("Packs", "1.21.1")
        val manager = LocalPackManager(repo)
        val source = archive("existing.zip", mapOf("pack.mcmeta" to metadata))
        manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source)
        val target = repo.gameDirectory(instance).resolve("resourcepacks/existing.zip")
        val before = Files.readAllBytes(target)
        Files.writeString(source, "replacement bytes")
        assertFailsWith<LocalPackException> { manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source) }
        assertContentEquals(before, Files.readAllBytes(target))
        val invalid = root.resolve("broken.zip")
        Files.writeString(invalid, "not a ZIP")
        assertFailsWith<LocalPackException> { manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, invalid) }
        Files.newDirectoryStream(target.parent).use { entries ->
            assertEquals(listOf("existing.zip"), entries.map { it.fileName.toString() })
        }
    }

    @Test fun refusesWrongPackKindNestedRootUnsafeEntriesAndInvalidMetadata() = runBlocking {
        val repo = repository()
        val instance = repo.create("Validation", "1.21.1")
        val manager = LocalPackManager(repo)
        val cases = listOf(
            LocalPackKind.RESOURCE_PACK to mapOf("shaders/example.fsh" to "source"),
            LocalPackKind.SHADER_PACK to mapOf("pack.mcmeta" to metadata),
            LocalPackKind.RESOURCE_PACK to mapOf("wrapper/pack.mcmeta" to metadata),
            LocalPackKind.RESOURCE_PACK to mapOf("pack.mcmeta" to metadata, "../outside" to "unsafe"),
            LocalPackKind.RESOURCE_PACK to mapOf("pack.mcmeta" to metadata, "/absolute" to "unsafe"),
            LocalPackKind.RESOURCE_PACK to mapOf("pack.mcmeta" to metadata, "C:\\escape" to "unsafe"),
            LocalPackKind.RESOURCE_PACK to mapOf("pack.mcmeta" to "{}"),
            LocalPackKind.RESOURCE_PACK to mapOf("pack.mcmeta" to "not JSON"),
            LocalPackKind.RESOURCE_PACK to mapOf("pack.mcmeta" to "x".repeat(1024 * 1024 + 1)),
        )
        for ((index, item) in cases.withIndex()) {
            val source = archive("invalid-$index.zip", item.second)
            assertFailsWith<LocalPackException>("Case $index") { manager.importZip(instance.id, item.first, source) }
            assertTrue(manager.list(instance.id, item.first).packs.isEmpty())
        }
        assertFalse(Files.exists(root.resolve("outside")))
    }

    @Test fun validatesPayloadChecksumsRatherThanOnlyReadingTheZipDirectory() = runBlocking {
        val repo = repository()
        val instance = repo.create("CRC", "1.21.1")
        val manager = LocalPackManager(repo)
        val content = "unique uncompressed shader payload"
        val source = archive("corrupt.zip", mapOf("shaders/test.fsh" to content), stored = true)
        val bytes = Files.readAllBytes(source)
        val index = bytes.toString(Charsets.ISO_8859_1).indexOf(content)
        assertTrue(index >= 0)
        bytes[index] = (bytes[index].toInt() xor 1).toByte()
        Files.write(source, bytes)
        assertFailsWith<LocalPackException> { manager.importZip(instance.id, LocalPackKind.SHADER_PACK, source) }
        assertTrue(manager.list(instance.id, LocalPackKind.SHADER_PACK).packs.isEmpty())
    }

    @Test fun activeInstanceRefusesImportsAndRemovals() = runBlocking {
        val repo = repository()
        val instance = repo.create("Active", "1.21.1")
        val source = archive("active.zip", mapOf("pack.mcmeta" to metadata))
        val manager = LocalPackManager(repo)
        manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source)
        repo.beginLaunch(instance.id)
        try {
            assertEquals(1, manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.size)
            assertFailsWith<InstanceInUseException> { manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source) }
            assertFailsWith<InstanceInUseException> { manager.remove(instance.id, LocalPackKind.RESOURCE_PACK, "active.zip") }
        } finally { repo.endPreparation(instance.id) }
        assertEquals(1, manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.size)
    }

    @Test fun unsafeFilenamesAndRemovalTraversalAreRejected() = runBlocking {
        val repo = repository()
        val instance = repo.create("Names", "1.21.1")
        val manager = LocalPackManager(repo)
        val source = archive("CON.zip", mapOf("pack.mcmeta" to metadata))
        assertFailsWith<LocalPackException> { manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source) }
        for (name in listOf("../outside.zip", "C:\\outside.zip", "nested/pack.zip", "pack.jar", ".hidden.zip")) {
            assertFailsWith<LocalPackException> { manager.remove(instance.id, LocalPackKind.RESOURCE_PACK, name) }
        }
        assertTrue(manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
    }

    @Test fun rejectsRedirectedSourcePackDirectoryTrashAndPackFiles() = runBlocking {
        // Unix is the local test target; Windows creation of symlinks depends on host privileges.
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows", true))
        val repo = repository()
        val instance = repo.create("Links", "1.21.1")
        val manager = LocalPackManager(repo)
        val source = archive("valid.zip", mapOf("pack.mcmeta" to metadata))
        val linked = Files.createSymbolicLink(root.resolve("linked.zip"), source)
        assertFailsWith<LocalPackException> { manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, linked) }
        val game = repo.gameDirectory(instance)
        val external = Files.createDirectory(root.resolve("external"))
        val packs = Files.createSymbolicLink(game.resolve("resourcepacks"), external)
        assertFailsWith<LocalPackException> { manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source) }
        Files.delete(packs)
        manager.importZip(instance.id, LocalPackKind.RESOURCE_PACK, source)
        Files.createSymbolicLink(packs.resolve("linked.zip"), source)
        assertTrue(manager.list(instance.id, LocalPackKind.RESOURCE_PACK).warnings.any { "linked.zip" in it })
        assertFailsWith<LocalPackException> { manager.remove(instance.id, LocalPackKind.RESOURCE_PACK, "linked.zip") }
        Files.createSymbolicLink(game.resolve(".syntaxlauncher-pack-trash"), external)
        assertFailsWith<LocalPackException> { manager.remove(instance.id, LocalPackKind.RESOURCE_PACK, "valid.zip") }
        assertTrue(Files.isRegularFile(packs.resolve("valid.zip")))
        Files.newDirectoryStream(external).use { assertFalse(it.iterator().hasNext()) }
    }
}

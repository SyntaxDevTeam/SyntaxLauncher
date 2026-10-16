package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeExtractorTest {
    @Test
    fun `extracts native files and skips meta inf`() {
        val root = createTempDirectory("syntaxlauncher-native-test")
        val archive = root.resolve("native.jar")
        val target = root.resolve("out")

        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/example.dll"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write(byteArrayOf(4))
            zip.closeEntry()
        }

        NativeExtractor().extract(archive, target)

        assertTrue(target.resolve("lib/example.dll").exists())
        assertTrue(!target.resolve("META-INF/MANIFEST.MF").exists())
    }

    @Test
    fun `rejects zip slip entry`() {
        val root = createTempDirectory("syntaxlauncher-zip-slip-test")
        val archive = root.resolve("native.jar")

        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.dll"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }

        assertFailsWith<IllegalArgumentException> {
            NativeExtractor().extract(archive, root.resolve("out"))
        }

        assertTrue(Files.notExists(root.resolve("outside.dll")))
    }
}

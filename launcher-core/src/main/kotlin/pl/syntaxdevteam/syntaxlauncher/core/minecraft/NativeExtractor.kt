package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream

class NativeExtractor {
    fun extract(
        archive: Path,
        targetDirectory: Path,
        excludes: List<String> = emptyList(),
    ) {
        targetDirectory.createDirectories()
        val normalizedRoot = targetDirectory.toAbsolutePath().normalize()

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace('\\', '/')
                if (!entry.isDirectory && !isExcluded(entryName, excludes)) {
                    val target = normalizedRoot.resolve(entryName).normalize()
                    require(target.startsWith(normalizedRoot)) {
                        "Native archive entry escapes target directory: ${entry.name}"
                    }

                    target.parent?.createDirectories()
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun isExcluded(
        entryName: String,
        excludes: List<String>,
    ): Boolean =
        entryName.startsWith("META-INF/") || excludes.any { excluded ->
            entryName.startsWith(excluded.replace('\\', '/'))
        }
}

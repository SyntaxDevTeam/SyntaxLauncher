package pl.syntaxdevteam.syntaxlauncher.desktop.update

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.syntaxdevteam.syntaxlauncher.core.update.LauncherRelease
import pl.syntaxdevteam.syntaxlauncher.core.update.LauncherVersion
import pl.syntaxdevteam.syntaxlauncher.core.update.UpdateBundleInstaller
import pl.syntaxdevteam.syntaxlauncher.core.update.UpdatePlatform

/** Explicit packaging check; never part of normal unit tests and never starts a downloaded process. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 1 && args[0].isNotBlank()) { "Use -PupdateBundle=<update ZIP>." }
    val archive = Path.of(args[0]).toAbsolutePath()
    val metadata = ZipFile(archive.toFile()).use { zip ->
        Json.parseToJsonElement(zip.getInputStream(zip.getEntry(UpdateBundleInstaller.MANIFEST))
            .bufferedReader().use { it.readText() }).jsonObject
    }
    val version = metadata.getValue("version").jsonPrimitive.content
    val platform = UpdatePlatform.entries.single { it.id == metadata.getValue("platform").jsonPrimitive.content }
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(archive).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    val root = Files.createTempDirectory("syntaxlauncher-update-smoke-")
    try {
        val installer = UpdateBundleInstaller(root.resolve("installations"))
        val installed = installer.install(archive,
            LauncherRelease(LauncherVersion.parse(version), "https://fixture.invalid", Files.size(archive), sha256), platform)
        check(installer.validate(installed.directory, platform).version.value == version)
        println("Update bundle verified: $version / ${platform.id}; all files, links and permissions installed and revalidated.")
    } finally {
        Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}

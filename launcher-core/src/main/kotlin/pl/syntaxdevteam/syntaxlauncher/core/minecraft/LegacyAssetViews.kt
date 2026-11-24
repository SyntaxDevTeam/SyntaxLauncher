package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.*
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** Materializes metadata-declared legacy views without replacing game-owned resources. */
internal class LegacyAssetViews {
    suspend fun materialize(index: MinecraftAssetIndexContent, id: String, layout: MinecraftInstallationLayout) = withContext(Dispatchers.IO) {
        if (!index.virtual && !index.mapToResources) return@withContext
        require(id.matches(Regex("[A-Za-z0-9_.-]+")) && id !in setOf(".", "..")) { "Invalid legacy asset index ID" }
        val files = index.objects.map { asset ->
            require(asset.hash.matches(Regex("[a-fA-F0-9]{40}")) && asset.size >= 0) { "Invalid legacy asset integrity" }
            val parts = asset.logicalPath.split('/')
            require(parts.all { it.isNotEmpty() && it !in setOf(".", "..") && it.none { c -> c in "\\:<>\"|?*" || c.code < 32 } }) {
                "Invalid legacy asset path"
            }
            asset to parts
        }
        for ((asset, parts) in files) {
            currentCoroutineContext().ensureActive()
            val source = layout.assetObjectsDir.resolve(asset.hash.take(2)).resolve(asset.hash)
            check(regular(source) && matches(source, asset)) { "Legacy asset source is missing or corrupt" }
            if (index.virtual) copy(source, layout.assetsDir, listOf("virtual", id) + parts, asset, replaceManaged = true)
            if (index.mapToResources) copy(source, layout.gameDir, listOf("resources") + parts, asset, replaceManaged = false)
        }
    }

    private fun copy(source: Path, root: Path, parts: List<String>, asset: MinecraftAssetObject, replaceManaged: Boolean) {
        check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) { "Legacy asset root is not a regular directory" }
        // Resolve the trusted root once (e.g. macOS /var -> /private/var); refuse links inside its view.
        var parent = root.toRealPath()
        for (part in parts.dropLast(1)) {
            parent = parent.resolve(part)
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                check(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent)) { "Legacy asset path contains a link or file" }
            } else Files.createDirectory(parent)
        }
        val target = parent.resolve(parts.last())
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            check(regular(target)) { "Legacy asset target is not a regular file" }
            if (matches(target, asset)) return
            check(replaceManaged) { "Legacy resource conflicts with an existing game file; original preserved" }
        }
        val stage = Files.createTempFile(parent, ".syntax-asset-", ".part")
        try {
            Files.copy(source, stage, StandardCopyOption.REPLACE_EXISTING)
            check(matches(stage, asset)) { "Legacy asset changed while copying" }
            val options = if (replaceManaged) arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                else arrayOf(StandardCopyOption.ATOMIC_MOVE)
            // An atomic move without REPLACE_EXISTING can still replace on some providers.
            // User-owned resources therefore use the non-replacing Files.move contract.
            if (!replaceManaged) Files.move(stage, target)
            else try { Files.move(stage, target, *options) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(stage) }
    }

    private fun regular(path: Path) = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
    private fun matches(path: Path, asset: MinecraftAssetObject): Boolean {
        if (Files.size(path) != asset.size) return false
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val size = stream.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(asset.hash, true)
    }
}

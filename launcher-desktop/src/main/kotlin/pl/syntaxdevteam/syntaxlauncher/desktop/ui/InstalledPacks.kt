package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import org.slf4j.LoggerFactory
import pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackInventory
import pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackKind
import pl.syntaxdevteam.syntaxlauncher.core.content.RemovedLocalPack
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

@Composable
fun InstalledPacks(instance: MinecraftInstance, session: DesktopLauncherSession, canModify: Boolean,
    onBusyChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf(LocalPackKind.RESOURCE_PACK) }
    var inventory by remember { mutableStateOf<LocalPackInventory?>(null) }
    var operation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var managedModpack by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var optiFineEnabled by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf<Pair<LocalPackKind, RemovedLocalPack>?>(null) }
    LaunchedEffect(kind, instance, refresh) {
        inventory = null; error = null
        try {
            inventory = session.packManager.list(instance.id, kind)
            optiFineEnabled = session.optiFine?.enabled(instance.id) == true
            managedModpack = session.modpackInstaller?.hasSource(instance.id) == true
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = strings.text("catalog.pack_error") }
    }
    fun run(action: suspend () -> Unit) {
        if (busy || !canModify) return
        busy = true; error = null; onBusyChange(true)
        operation = scope.launch {
            try { action(); inventory = session.packManager.list(instance.id, kind) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                LoggerFactory.getLogger("InstalledPacks").warn("Pack operation failed", failure)
                error = strings.text("catalog.pack_error")
            } finally { busy = false; onBusyChange(false) }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.text("catalog.my_packs"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { if (busy) operation?.cancel() else onBack() }) { Text(strings.text(if (busy) "ui.cancel" else "catalog.back")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocalPackKind.entries.forEach { item -> ChannelButton(
                strings.text(if (item == LocalPackKind.RESOURCE_PACK) "catalog.textures" else "catalog.shaders"),
                kind == item, !busy, { kind = item }, Modifier.weight(1f)) }
        }
        if (optiFineEnabled) TextButton(enabled = !busy && canModify, onClick = { run {
            session.optiFine?.disable(instance.id); optiFineEnabled = false
        } }) { Text(strings.text("catalog.disable_optifine")) }
        if (!canModify) Text(strings.text("catalog.stop_game"))
        if (busy || (inventory == null && error == null)) CircularProgressIndicator()
        if (error != null) TextButton(enabled = !busy, onClick = { refresh++ }) { Text(strings.text("ui.retry")) }
        var toolsOpen by remember { mutableStateOf(false) }
        Box {
            TextButton(enabled = !busy && canModify, onClick = { toolsOpen = true }) { Text(strings.text("catalog.more")) }
            DropdownMenu(toolsOpen, onDismissRequest = { toolsOpen = false }) {
                DropdownMenuItem(text = { Text(strings.text("catalog.import_pack")) }, onClick = {
                    toolsOpen = false
                    val picker = javax.swing.JFileChooser().apply {
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip")
                    }
                    if (picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val source = picker.selectedFile.toPath()
                        run { session.packManager.importZip(instance.id, kind, source) }
                    }
                })
                if (managedModpack) DropdownMenuItem(text = { Text(strings.text("catalog.export_modpack")) }, onClick = {
                    toolsOpen = false
                    val picker = javax.swing.JFileChooser().apply {
                        selectedFile = java.io.File("modpack.mrpack")
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Modrinth modpack", "mrpack")
                    }
                    if (picker.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val target = picker.selectedFile.toPath()
                        run { requireNotNull(session.modpackInstaller).exportOriginal(instance.id, target); exported = true }
                    }
                })
            }
        }
        if (exported) Text(strings.text("catalog.exported"), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        removed?.let { receipt ->
            TextButton(enabled = !busy && canModify, onClick = { run {
                session.packManager.restore(instance.id, receipt.first, receipt.second)
                removed = null
                if (receipt.second.wasSelected) {
                    val proof = if (receipt.first == LocalPackKind.SHADER_PACK) session.shaderRenderer.ensure(instance) else null
                    session.packManager.setSelected(instance.id, receipt.first, receipt.second.originalFileName, true, proof)
                }
                removed = null
            } }) { Text(strings.text("catalog.undo_remove")) }
        }
        if (inventory?.packs?.isEmpty() == true) Text(strings.text("catalog.packs_empty"))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(inventory?.packs.orEmpty(), key = { it.fileName }) { pack ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(SyntaxBrand.controlRadius)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val icon = remember(pack.iconPngBase64) { runCatching {
                                pack.iconPngBase64?.let { encoded -> SkiaImage.makeFromEncoded(java.util.Base64.getDecoder().decode(encoded)).use { it.toComposeImageBitmap() } }
                            }.getOrNull() }
                            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(pack.fileName.removeSuffix(".zip"), style = MaterialTheme.typography.titleMedium)
                                pack.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (pack.selected) Text(strings.text("catalog.active"), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (kind == LocalPackKind.RESOURCE_PACK && pack.selected) Row {
                            TextButton(enabled = !busy && canModify, onClick = { run {
                                session.packManager.moveResourcePack(instance.id, pack.fileName, true)
                            } }) { Text(strings.text("catalog.priority_up")) }
                            TextButton(enabled = !busy && canModify, onClick = { run {
                                session.packManager.moveResourcePack(instance.id, pack.fileName, false)
                            } }) { Text(strings.text("catalog.priority_down")) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && canModify, onClick = { run {
                                val proof = if (kind == LocalPackKind.SHADER_PACK && !pack.selected) session.shaderRenderer.ensure(instance) else null
                                session.packManager.setSelected(instance.id, kind, pack.fileName, !pack.selected, proof)
                            } }) { Text(strings.text(if (pack.selected) "catalog.disable_pack" else "catalog.use_pack")) }
                            TextButton(enabled = !busy && canModify, onClick = { run {
                                removed = kind to session.packManager.remove(instance.id, kind, pack.fileName)
                            } }) { Text(strings.text("catalog.remove_pack")) }
                        }
                    }
                }
            }
        }
        if (inventory?.warnings?.isNotEmpty() == true) Text(strings.text("catalog.unmanaged_packs"), style = MaterialTheme.typography.bodySmall)
    }
}

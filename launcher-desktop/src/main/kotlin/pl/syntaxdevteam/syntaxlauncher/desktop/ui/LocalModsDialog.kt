package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.mods.LocalModInventory
import pl.syntaxdevteam.syntaxlauncher.core.mods.LocalModManager
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthCatalog
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstaller
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstallPlan
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthUpdateReport

/** Local JARs only; online dependency resolution belongs to v0.6. */
@Composable
fun LocalModsDialog(
    instance: MinecraftInstance,
    manager: LocalModManager,
    catalog: ModrinthCatalog,
    installer: ModrinthInstaller,
    canModify: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var inventory by remember(instance.id) { mutableStateOf<LocalModInventory?>(null) }
    var error by remember(instance.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    var confirmingUpdates by remember { mutableStateOf<List<pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthAvailableUpdate>?>(null) }
    var browsing by remember { mutableStateOf(false) }
    var updateReport by remember(instance.id) { mutableStateOf<ModrinthUpdateReport?>(null) }
    var checkingUpdates by remember(instance.id) { mutableStateOf(false) }

    LaunchedEffect(instance.id) {
        try {
            inventory = manager.list(instance.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to inspect local mods."
        }
    }

    fun operate(operation: suspend () -> LocalModInventory, afterSuccess: () -> Unit = {}) {
        if (busy || !canModify) return
        busy = true
        onBusyChange(true)
        scope.launch {
            try {
                inventory = operation()
                updateReport = null
                error = null
                afterSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Mod operation failed; original files were preserved."
            } finally {
                busy = false
                onBusyChange(false)
            }
        }
    }

    val workArea = remember {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    }
    val dialogSize = DpSize(
        minOf(840, (workArea.width - 48).coerceAtLeast(400)).dp,
        minOf(630, (workArea.height - 48).coerceAtLeast(400)).dp,
    )
    Dialog(
        onCloseRequest = { if (!busy) onDismiss() },
        title = "SyntaxLauncher — Local mods",
        state = rememberDialogState(size = dialogSize),
        resizable = true,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
        confirmingUpdates?.let { updates ->
            AlertDialog(
                onDismissRequest = { if (!busy) confirmingUpdates = null },
                title = { Text("Install verified Modrinth updates?") },
                text = { Text("Replace ${updates.size} tracked mod(s). Previous JARs will be saved for rollback. Review dependency notices before continuing.") },
                confirmButton = {
                    TextButton(enabled = canModify && !busy, onClick = {
                        confirmingUpdates = null
                        operate { installer.update(instance.id, updates) }
                    }) { Text("Update mods") }
                },
                dismissButton = { TextButton(onClick = { confirmingUpdates = null }) { Text("Cancel") } },
            )
        }
        removing?.let { fileName ->
            AlertDialog(
                onDismissRequest = { if (!busy) removing = null },
                title = { Text("Remove local mod?") },
                text = { Text("Move $fileName to the instance's private mod trash? You can recover the JAR manually.") },
                confirmButton = {
                    TextButton(enabled = canModify && !busy, onClick = {
                        removing = null
                        operate(operation = { manager.remove(instance.id, fileName) })
                    }) { Text("Move to trash") }
                },
                dismissButton = {
                    TextButton(onClick = { removing = null }) { Text("Cancel") }
                },
            )
        }


                if (browsing) {
                    ModrinthBrowsePane(
                        instance = instance, catalog = catalog,
                        busy = busy, canModify = canModify,
                        onInstall = { plan: ModrinthInstallPlan ->
                            operate({ installer.install(instance.id, plan) }) { browsing = false }
                        },
                        onDismiss = { browsing = false },
                    )
                } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("LOCAL MODS", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(instance.name, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text("Fabric · Minecraft ${instance.minecraftVersionId} · Files stay in this instance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${inventory?.mods?.count { it.enabled } ?: 0} enabled / ${inventory?.mods?.size ?: 0} installed",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = canModify && !busy,
                        onClick = { browsing = true }) { Text("Discover on Modrinth") }
                    Button(enabled = canModify && !busy, onClick = {
                        val picker = JFileChooser().apply {
                            dialogTitle = "Import a local Fabric mod"
                            fileFilter = FileNameExtensionFilter("Minecraft mod JAR (*.jar)", "jar")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val picked: Path = picker.selectedFile.toPath()
                            operate(operation = { manager.importJar(instance.id, picked) })
                        }
                    }) { Text("Import JAR") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && !checkingUpdates && inventory != null,
                        onClick = {
                            checkingUpdates = true
                            error = null
                            scope.launch {
                                try {
                                    updateReport = catalog.checkUpdates(instance, manager.list(instance.id))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Could not check Modrinth updates."
                                } finally { checkingUpdates = false }
                            }
                        }) { Text("Check Modrinth updates") }
                    if (checkingUpdates) CircularProgressIndicator()
                }
                updateReport?.let { report ->
                    Text(if (report.updates.isEmpty()) "No newer compatible managed releases found."
                        else "${report.updates.size} compatible updates found:",
                        style = MaterialTheme.typography.titleSmall)
                    report.updates.forEach { item ->
                        Text("• ${item.fileName} → ${item.newVersionNumber} (available on Modrinth)",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.unmatched.isNotEmpty()) Text(
                        "${report.unmatched.size} local/unmatched JARs cannot be checked automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    report.notices.forEach { note ->
                        Text(note, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (report.updates.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = canModify && !busy, onClick = {
                                confirmingUpdates = report.updates
                            }) { Text("Update all compatible") }
                            report.updates.forEach { item ->
                                TextButton(enabled = canModify && !busy, onClick = {
                                    confirmingUpdates = listOf(item)
                                }) { Text("Update ${item.fileName.take(20)}") }
                            }
                        }
                    }
                    OutlinedButton(enabled = canModify && !busy,
                        onClick = { operate { installer.rollback(instance.id) } }) {
                        Text("Rollback last Modrinth update")
                    }
                }
                if (busy) CircularProgressIndicator()
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                val warnings = inventory?.warnings.orEmpty()
                if (warnings.isNotEmpty()) {
                    Text("Compatibility warnings (local metadata only)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp)
                        .verticalScroll(rememberScrollState())) {
                        warnings.forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (inventory == null) {
                        Text("Scanning instance mods…")
                    } else if (inventory!!.mods.isEmpty()) {
                        Text("No local JARs yet. Import a Fabric mod to get started.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inventory?.mods.orEmpty().forEach { mod ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(mod.metadata?.name ?: mod.fileName,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${mod.metadata?.id ?: "Unknown mod ID"} · " +
                                        "${mod.metadata?.version ?: "Unknown version"} · " +
                                        "${mod.sizeBytes / 1024} KiB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                mod.metadata?.let { info ->
                                    if (info.depends.isNotEmpty()) Text(
                                        "Requires: " + info.depends.entries.joinToString { "${it.key} ${it.value}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                mod.modrinth?.let { source ->
                                    Text("Modrinth · version ${source.versionId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Text(mod.fileName + " · SHA-256 " + mod.sha256.take(12) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = mod.enabled,
                                enabled = canModify && !busy,
                                onCheckedChange = { next ->
                                    operate(operation = { manager.setEnabled(instance.id, mod.fileName, next) })
                                },
                            )
                            TextButton(enabled = canModify && !busy,
                                onClick = { removing = mod.fileName }) { Text("Remove") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Text("Disabled JARs are renamed to .jar.disabled. Removed JARs are kept in " +
                    "game/.syntaxlauncher-mod-trash. External dependencies are not installed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

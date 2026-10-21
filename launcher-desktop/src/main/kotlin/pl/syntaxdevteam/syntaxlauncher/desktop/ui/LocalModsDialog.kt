package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

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
import androidx.compose.ui.window.DialogWindow
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
    initialBrowse: Boolean = false,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var inventory by remember(instance.id) { mutableStateOf<LocalModInventory?>(null) }
    var error by remember(instance.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    var confirmingUpdates by remember { mutableStateOf<List<pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthAvailableUpdate>?>(null) }
    var browsing by remember(instance.id) { mutableStateOf(initialBrowse) }
    var updateReport by remember(instance.id) { mutableStateOf<ModrinthUpdateReport?>(null) }
    var checkingUpdates by remember(instance.id) { mutableStateOf(false) }

    LaunchedEffect(instance.id) {
        try {
            inventory = manager.list(instance.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: strings.text("ui.unable_to_inspect_local_mods")
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
                error = failure.message ?: strings.text("ui.mod_operation_failed_original_files_were_preserved")
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
    DialogWindow(
        onCloseRequest = { if (!busy) onDismiss() },
        title = strings.text("ui.syntaxlauncher_local_mods"),
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
                title = { Text(strings.text("ui.install_verified_modrinth_updates")) },
                text = { Text(strings.text("ui.replace_0_tracked_mod_s_previous_jars_will_be_saved_for_rollba", updates.size)) },
                confirmButton = {
                    TextButton(enabled = canModify && !busy, onClick = {
                        confirmingUpdates = null
                        operate(operation = { installer.update(instance.id, updates) })
                    }) { Text(strings.text("ui.update_mods")) }
                },
                dismissButton = { TextButton(onClick = { confirmingUpdates = null }) { Text(strings.text("ui.cancel")) } },
            )
        }
        removing?.let { fileName ->
            AlertDialog(
                onDismissRequest = { if (!busy) removing = null },
                title = { Text(strings.text("ui.remove_local_mod")) },
                text = { Text(strings.text("ui.move_0_to_the_instance_s_private_mod_trash_you_can_recover_the", fileName)) },
                confirmButton = {
                    TextButton(enabled = canModify && !busy, onClick = {
                        removing = null
                        operate(operation = { manager.remove(instance.id, fileName) })
                    }) { Text(strings.text("ui.move_to_trash")) }
                },
                dismissButton = {
                    TextButton(onClick = { removing = null }) { Text(strings.text("ui.cancel")) }
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
                        Text(strings.text("ui.local_mods"), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(instance.name, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(strings.text("ui.0_minecraft_1_files_stay_in_this_instance", instance.loader?.id ?: "Vanilla", instance.minecraftVersionId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(enabled = !busy, onClick = onDismiss) { Text(strings.text("ui.close")) }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.text("ui.0_enabled_1_installed", inventory?.mods?.count { it.enabled } ?: 0, inventory?.mods?.size ?: 0),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = canModify && !busy,
                        onClick = { browsing = true }) { Text(strings.text("ui.discover_on_modrinth")) }
                    Button(enabled = canModify && !busy, onClick = {
                        val picker = JFileChooser().apply {
                            dialogTitle = strings.text("ui.import_a_local_mod_for_this_instance")
                            fileFilter = FileNameExtensionFilter(strings.text("ui.minecraft_mod_jar_jar"), "jar")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val picked: Path = picker.selectedFile.toPath()
                            operate(operation = { manager.importJar(instance.id, picked) })
                        }
                    }) { Text(strings.text("ui.import_jar")) }
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
                                    error = failure.message ?: strings.text("ui.could_not_check_modrinth_updates")
                                } finally { checkingUpdates = false }
                            }
                        }) { Text(strings.text("ui.check_modrinth_updates")) }
                    if (checkingUpdates) CircularProgressIndicator()
                }
                updateReport?.let { report ->
                    Text(if (report.updates.isEmpty()) strings.text("ui.no_newer_compatible_managed_releases_found")
                        else strings.text("ui.0_compatible_updates_found", report.updates.size),
                        style = MaterialTheme.typography.titleSmall)
                    report.updates.forEach { item ->
                        Text(strings.text("ui.0_1_available_on_modrinth", item.fileName, item.newVersionNumber),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.unmatched.isNotEmpty()) Text(
                        strings.text("ui.0_local_unmatched_jars_cannot_be_checked_automatically", report.unmatched.size),
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
                            }) { Text(strings.text("ui.update_all_compatible")) }
                            report.updates.forEach { item ->
                                TextButton(enabled = canModify && !busy, onClick = {
                                    confirmingUpdates = listOf(item)
                                }) { Text(strings.text("ui.update_0", item.fileName.take(20))) }
                            }
                        }
                    }
                    OutlinedButton(enabled = canModify && !busy,
                        onClick = { operate(operation = { installer.rollback(instance.id) }) }) {
                        Text(strings.text("ui.rollback_last_modrinth_update"))
                    }
                }
                if (busy) CircularProgressIndicator()
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                val warnings = inventory?.warnings.orEmpty()
                if (warnings.isNotEmpty()) {
                    Text(strings.text("ui.compatibility_warnings_local_metadata_only"),
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
                        Text(strings.text("ui.scanning_instance_mods"))
                    } else if (inventory!!.mods.isEmpty()) {
                        Text(strings.text("ui.no_local_jars_yet_import_a_fabric_mod_to_get_started"),
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
                                    "${mod.metadata?.id ?: strings.text("mod.unknown_id")} · " +
                                        "${mod.metadata?.version ?: strings.text("mod.unknown_version")} · " +
                                        "${mod.sizeBytes / 1024} KiB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                mod.metadata?.let { info ->
                                    if (info.depends.isNotEmpty()) Text(
                                        strings.text("ui.requires") + info.depends.entries.joinToString { "${it.key} ${it.value}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                mod.modrinth?.let { source ->
                                    Text(strings.text("ui.modrinth_version_0", source.versionId),
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
                                onClick = { removing = mod.fileName }) { Text(strings.text("ui.remove")) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Text(strings.text("ui.disabled_jars_are_renamed_to_jar_disabled_removed_jars_are_kep") +
                    strings.text("ui.game_syntaxlauncher_mod_trash_external_dependencies_are_not_in"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

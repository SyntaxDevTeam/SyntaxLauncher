package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceCopyProgress
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion
import pl.syntaxdevteam.syntaxlauncher.core.instance.TrashedMinecraftInstance

data class InstanceCopyPreview(
    val from: MinecraftInstance?,
    val name: String,
    val minecraftVersionId: String,
    val sourcePath: String,
    val destinationPath: String,
    val totalBytes: Long,
    val totalFiles: Int,
)

private fun displaySize(strings: UiStrings, bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(java.util.Locale.forLanguageTag(strings.language.code), bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(java.util.Locale.forLanguageTag(strings.language.code), bytes / (1024.0 * 1024))
    else -> strings.text("unit.bytes", bytes)
}

/** Dedicated, scrollable Instances workspace. The home screen continues to own Play. */
@Composable
fun InstanceWorkspace(
    instances: List<MinecraftInstance>,
    trashed: List<TrashedMinecraftInstance>,
    selectedId: String,
    selected: MinecraftInstance?,
    canModify: Boolean,
    isBusy: Boolean,
    isLoadingInventory: Boolean,
    hasLegacyGame: Boolean,
    latestVersion: String,
    availableVersions: List<String>,
    loadLoaderVersions: suspend (String, String) -> List<ModLoaderVersion> = { _, _ -> emptyList() },
    error: String?,
    preview: InstanceCopyPreview?,
    copyProgress: InstanceCopyProgress?,
    onSelect: (String) -> Unit,
    onCreate: (String, String, ModLoaderSelection?) -> Unit,
    onEdit: (MinecraftInstance) -> Unit,
    onManageMods: (MinecraftInstance) -> Unit,
    onClonePreview: (MinecraftInstance, String) -> Unit,
    onLegacyPreview: (String, String) -> Unit,
    onCancelPreview: () -> Unit,
    onConfirmPreview: () -> Unit,
    onCancelCopy: () -> Unit,
    onDelete: (MinecraftInstance) -> Unit,
    onRestore: (TrashedMinecraftInstance) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val workArea = remember {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    }
    val width = minOf(1020, (workArea.width - 48).coerceAtLeast(420)).dp
    val height = minOf(760, (workArea.height - 48).coerceAtLeast(400)).dp
    var search by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newVersion by remember { mutableStateOf(latestVersion) }
    var newLoader by remember { mutableStateOf("vanilla") }
    var newFabricVersion by remember { mutableStateOf("") }
    var compatible by remember { mutableStateOf<List<ModLoaderVersion>>(emptyList()) }
    var fabricLoading by remember { mutableStateOf(false) }
    var fabricError by remember { mutableStateOf<String?>(null) }
    val fabricValid = newLoader == "vanilla" ||
        (!fabricLoading && fabricError == null && compatible.any { it.version == newFabricVersion })
    LaunchedEffect(newVersion, newLoader) {
        compatible = emptyList()
        fabricError = null
        if (newLoader in setOf("fabric", "quilt", "neoforge", "forge") && newVersion in availableVersions) {
            fabricLoading = true
            try {
                compatible = loadLoaderVersions(newLoader, newVersion)
                if (compatible.isEmpty()) {
                    fabricError = strings.text("loader.unavailable", newLoader.replaceFirstChar { it.uppercase() }, newVersion)
                } else if (compatible.none { it.version == newFabricVersion }) {
                    newFabricVersion = compatible.firstOrNull { it.stable }?.version
                        ?: compatible.first().version
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fabricError = error.message ?: strings.text("loader.compatibility_error")
            } finally {
                fabricLoading = false
            }
        }
    }
    var cloneName by remember(selected?.id) {
        mutableStateOf(selected?.name?.take(43)?.plus(strings.text("ui.copy")).orEmpty())
    }
    var legacyName by remember { mutableStateOf(strings.text("ui.imported_vanilla")) }
    var confirmDelete by remember { mutableStateOf<MinecraftInstance?>(null) }
    val filtered = instances.filter {
        search.isBlank() || it.name.contains(search, true) ||
            it.minecraftVersionId.contains(search, true) || it.id.startsWith(search, true)
    }

    if (confirmDelete != null) {
        val deleting = confirmDelete!!
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(strings.text("ui.move_0_to_trash", deleting.name)) },
            text = { Text(strings.text("ui.all_worlds_and_settings_will_remain_recoverable_from_this_work")) },
            confirmButton = {
                TextButton(onClick = { onDelete(deleting); confirmDelete = null }, enabled = canModify) {
                    Text(strings.text("ui.move_to_trash"))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(strings.text("ui.cancel")) } },
        )
    }

    Dialog(
        onCloseRequest = { if (!isBusy) onDismiss() },
        title = strings.text("ui.syntaxlauncher_instances"),
        state = rememberDialogState(size = DpSize(width, height)),
        resizable = true,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(strings.text("ui.instances_741b2b"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(strings.text("ui.your_minecraft_worlds"), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text(strings.text("ui.independent_saves_and_settings_shared_game_downloads"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onDismiss, enabled = !isBusy) { Text(strings.text("ui.close")) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                val inventoryPane: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(
                        modifier = paneModifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            label = { Text(strings.text("ui.find_instance")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        InstanceListRow(
                            title = strings.text("ui.legacy_vanilla"),
                            subtitle = strings.text("ui.original_v0_1_game"),
                            active = selectedId.isEmpty(),
                            enabled = canModify,
                            onClick = { onSelect("") },
                        )
                        if (isLoadingInventory) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (filtered.isEmpty()) {
                            Text(if (instances.isEmpty()) strings.text("ui.no_instances_yet_create_your_first_profile")
                                else strings.text("ui.no_profiles_match_your_search"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        filtered.forEach { profile ->
                            InstanceListRow(
                                title = profile.name,
                                subtitle = profile.minecraftVersionId + (profile.loader?.let { " · ${it.id} ${it.version}" } ?: "") + " · " + profile.id.take(8),
                                active = selectedId == profile.id,
                                enabled = canModify,
                                onClick = { onSelect(profile.id) },
                            )
                        }
                    }
                }
                val detailPane: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(
                        modifier = paneModifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Text(if (selected != null) strings.text("ui.selected_profile") else strings.text("ui.create_a_profile"),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        if (selected == null) {
                            Text(
                                if (selectedId.isNotEmpty()) strings.text("ui.the_previously_selected_instance_is_missing_select_an_existing")
                                else strings.text("ui.keep_different_worlds_settings_and_minecraft_versions_separate"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(selected.name, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium)
                            Text("Minecraft ${selected.minecraftVersionId} · ${selected.loader?.let { "${it.id} ${it.version} · " } ?: "Vanilla · "}${selected.maximumMemoryMiB} MiB RAM · ${selected.id.take(8)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onEdit(selected) }, enabled = canModify) {
                                    Text(strings.text("ui.settings"))
                                }
                                if (selected.loader?.id in setOf("fabric", "quilt", "neoforge", "forge")) {
                                    OutlinedButton(onClick = { onManageMods(selected) },
                                        enabled = canModify) { Text(strings.text("ui.mods")) }
                                }
                                OutlinedButton(onClick = { confirmDelete = selected },
                                    enabled = canModify) { Text(strings.text("ui.move_to_trash")) }
                            }
                            OutlinedTextField(
                                value = cloneName,
                                onValueChange = { cloneName = it },
                                label = { Text(strings.text("ui.clone_as")) },
                                singleLine = true,
                                enabled = canModify,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(
                                onClick = { onClonePreview(selected, cloneName.trim()) },
                                enabled = canModify && cloneName.trim().length in 1..48,
                            ) { Text(strings.text("ui.review_clone")) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(strings.text("ui.new_instance"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = newName, onValueChange = { newName = it },
                            label = { Text(strings.text("ui.name")) }, singleLine = true,
                            enabled = canModify, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newVersion, onValueChange = { newVersion = it },
                            label = { Text(strings.text("ui.minecraft_version")) },
                            supportingText = { Text(strings.text("ui.choose_a_version_listed_in_the_mojang_catalog")) },
                            singleLine = true, enabled = canModify,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (newVersion !in availableVersions) {
                            Text(strings.text("ui.version_not_found_in_the_catalog"), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(strings.text("ui.mod_loader"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { newLoader = "vanilla" }, enabled = canModify) {
                                Text(if (newLoader == "vanilla") "✓ Vanilla" else "Vanilla")
                            }
                            TextButton(onClick = { newLoader = "fabric" }, enabled = canModify) {
                                Text(if (newLoader == "fabric") "✓ Fabric" else "Fabric")
                            }
                            TextButton(onClick = { newLoader = "quilt" }, enabled = canModify) {
                                Text(if (newLoader == "quilt") "✓ Quilt" else "Quilt")
                            }
                            TextButton(onClick = { newLoader = "neoforge" }, enabled = canModify) {
                                Text(if (newLoader == "neoforge") "✓ NeoForge" else "NeoForge")
                            }
                        }
                        TextButton(onClick = { newLoader = "forge" }, enabled = canModify) {
                            Text(if (newLoader == "forge") "✓ Forge (v0.8)" else "Forge (v0.8)")
                        }
                        if (newLoader == "forge") Text(
                            strings.text("ui.forge_v2_test_only"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        }
                        if (newLoader in setOf("fabric", "quilt", "neoforge", "forge")) {
                            OutlinedTextField(
                                value = newFabricVersion,
                                onValueChange = { newFabricVersion = it },
                                label = { Text(newLoader.replaceFirstChar { it.uppercase() } + strings.text("ui.loader_version")) },
                                singleLine = true, enabled = canModify,
                                isError = !fabricValid,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text(
                                    if (fabricLoading) strings.text("ui.checking_compatibility")
                                    else fabricError ?: strings.text("ui.select_a_compatible_version")
                                ) },
                            )
                            compatible.take(6).forEach { loader ->
                                TextButton(onClick = { newFabricVersion = loader.version }, enabled = canModify) {
                                    Text(loader.version + if (loader.stable) strings.text("ui.stable") else strings.text("ui.preview"))
                                }
                            }
                            if (fabricError != null) Text(fabricError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        Button(onClick = {
                            onCreate(newName.trim(), newVersion.trim(),
                                if (newLoader in setOf("fabric", "quilt", "neoforge", "forge")) ModLoaderSelection(newLoader, newFabricVersion) else null)
                        },
                            enabled = canModify && newName.trim().length in 1..48 &&
                                newVersion in availableVersions && fabricValid) { Text(strings.text("ui.create_instance")) }
                        if (hasLegacyGame) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(strings.text("ui.import_old_game_data"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = legacyName, onValueChange = { legacyName = it },
                                label = { Text(strings.text("ui.imported_profile_name")) },
                                singleLine = true, enabled = canModify,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(
                                onClick = { onLegacyPreview(legacyName.trim(), latestVersion) },
                                enabled = canModify && legacyName.trim().length in 1..48,
                            ) { Text(strings.text("ui.review_v0_1_import")) }
                        }
                        if (trashed.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(strings.text("ui.recently_deleted"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            trashed.forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.instance.name, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(entry.instance.minecraftVersionId + " · " +
                                            entry.instance.id.take(8),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onRestore(entry) }, enabled = canModify) {
                                        Text(strings.text("ui.restore"))
                                    }
                                }
                            }
                        }
                    }
                }
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth < 730.dp) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            inventoryPane(Modifier.fillMaxWidth().heightIn(max = 175.dp))
                            detailPane(Modifier.fillMaxWidth().weight(1f))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            inventoryPane(Modifier.width(284.dp))
                            detailPane(Modifier.weight(1f))
                        }
                    }
                }
                if (preview != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(if (preview.from == null) strings.text("ui.confirm_legacy_import") else strings.text("ui.confirm_clone"),
                                style = MaterialTheme.typography.titleMedium)
                            Text(strings.text("ui.source_0", preview.sourcePath), style = MaterialTheme.typography.bodySmall)
                            Text(strings.text("ui.destination_0", preview.destinationPath),
                                style = MaterialTheme.typography.bodySmall)
                            Text(strings.text("ui.0_files_1_to_copy_plus_reserve", preview.totalFiles, displaySize(strings, preview.totalBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onConfirmPreview, enabled = canModify) { Text(strings.text("ui.start_copy")) }
                                TextButton(onClick = onCancelPreview, enabled = canModify) { Text(strings.text("ui.cancel")) }
                            }
                        }
                    }
                }
                if (copyProgress != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.text("ui.copying_game_data_0_1_files", copyProgress.copiedFiles, copyProgress.totalFiles),
                                style = MaterialTheme.typography.bodySmall)
                            Text(displaySize(strings, copyProgress.copiedBytes) + " / " +
                                displaySize(strings, copyProgress.totalBytes),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { copyProgress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(copyProgress.currentFile ?: strings.text("ui.preparing_files"),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = onCancelCopy) { Text(strings.text("ui.cancel_copying")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceListRow(
    title: String,
    subtitle: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

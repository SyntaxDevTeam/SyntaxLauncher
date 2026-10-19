package pl.syntaxdevteam.syntaxlauncher.desktop.ui

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

private fun displaySize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024))
    else -> "$bytes bytes"
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
    error: String?,
    preview: InstanceCopyPreview?,
    copyProgress: InstanceCopyProgress?,
    onSelect: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onEdit: (MinecraftInstance) -> Unit,
    onClonePreview: (MinecraftInstance, String) -> Unit,
    onLegacyPreview: (String, String) -> Unit,
    onCancelPreview: () -> Unit,
    onConfirmPreview: () -> Unit,
    onCancelCopy: () -> Unit,
    onDelete: (MinecraftInstance) -> Unit,
    onRestore: (TrashedMinecraftInstance) -> Unit,
    onDismiss: () -> Unit,
) {
    val workArea = remember {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    }
    val width = minOf(1020, (workArea.width - 48).coerceAtLeast(420)).dp
    val height = minOf(760, (workArea.height - 48).coerceAtLeast(400)).dp
    var search by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newVersion by remember { mutableStateOf(latestVersion) }
    var cloneName by remember(selected?.id) {
        mutableStateOf(selected?.name?.take(43)?.plus(" copy").orEmpty())
    }
    var legacyName by remember { mutableStateOf("Imported Vanilla") }
    var confirmDelete by remember { mutableStateOf<MinecraftInstance?>(null) }
    val filtered = instances.filter {
        search.isBlank() || it.name.contains(search, true) ||
            it.minecraftVersionId.contains(search, true) || it.id.startsWith(search, true)
    }

    if (confirmDelete != null) {
        val deleting = confirmDelete!!
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Move ${deleting.name} to trash?") },
            text = { Text("All worlds and settings will remain recoverable from this workspace.") },
            confirmButton = {
                TextButton(onClick = { onDelete(deleting); confirmDelete = null }, enabled = canModify) {
                    Text("Move to trash")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    Dialog(
        onCloseRequest = { if (!isBusy) onDismiss() },
        title = "SyntaxLauncher — Instances",
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
                        Text("INSTANCES", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Your Minecraft worlds", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text("Independent saves and settings. Shared game downloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onDismiss, enabled = !isBusy) { Text("Close") }
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            label = { Text("Find instance") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        InstanceListRow(
                            title = "Legacy Vanilla",
                            subtitle = "Original v0.1 game",
                            active = selectedId.isEmpty(),
                            enabled = canModify,
                            onClick = { onSelect("") },
                        )
                        if (isLoadingInventory) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (filtered.isEmpty()) {
                            Text(if (instances.isEmpty()) "No instances yet. Create your first profile."
                                else "No profiles match your search.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        filtered.forEach { profile ->
                            InstanceListRow(
                                title = profile.name,
                                subtitle = profile.minecraftVersionId + " · " + profile.id.take(8),
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
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Text(if (selected != null) "SELECTED PROFILE" else "CREATE A PROFILE",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        if (selected == null) {
                            Text(
                                if (selectedId.isNotEmpty()) "The previously selected instance is missing. Select an existing one."
                                else "Keep different worlds, settings and Minecraft versions separate.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(selected.name, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium)
                            Text("Minecraft ${selected.minecraftVersionId} · ${selected.maximumMemoryMiB} MiB RAM · ${selected.id.take(8)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onEdit(selected) }, enabled = canModify) {
                                    Text("Settings")
                                }
                                OutlinedButton(onClick = { confirmDelete = selected },
                                    enabled = canModify) { Text("Move to trash") }
                            }
                            OutlinedTextField(
                                value = cloneName,
                                onValueChange = { cloneName = it },
                                label = { Text("Clone as") },
                                singleLine = true,
                                enabled = canModify,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(
                                onClick = { onClonePreview(selected, cloneName.trim()) },
                                enabled = canModify && cloneName.trim().length in 1..48,
                            ) { Text("Review clone…") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("NEW INSTANCE", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = newName, onValueChange = { newName = it },
                            label = { Text("Name") }, singleLine = true,
                            enabled = canModify, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newVersion, onValueChange = { newVersion = it },
                            label = { Text("Minecraft version") },
                            supportingText = { Text("Choose a version listed in the Mojang catalog.") },
                            singleLine = true, enabled = canModify,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (newVersion !in availableVersions) {
                            Text("Version not found in the catalog.", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onCreate(newName.trim(), newVersion.trim()) },
                            enabled = canModify && newName.trim().length in 1..48 &&
                                newVersion in availableVersions) { Text("Create instance") }
                        if (hasLegacyGame) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("IMPORT OLD GAME DATA", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = legacyName, onValueChange = { legacyName = it },
                                label = { Text("Imported profile name") },
                                singleLine = true, enabled = canModify,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(
                                onClick = { onLegacyPreview(legacyName.trim(), latestVersion) },
                                enabled = canModify && legacyName.trim().length in 1..48,
                            ) { Text("Review v0.1 import…") }
                        }
                        if (trashed.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("RECENTLY DELETED", style = MaterialTheme.typography.labelMedium,
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
                                        Text("Restore")
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
                            Text(if (preview.from == null) "Confirm legacy import" else "Confirm clone",
                                style = MaterialTheme.typography.titleMedium)
                            Text("Source: ${preview.sourcePath}", style = MaterialTheme.typography.bodySmall)
                            Text("Destination: ${preview.destinationPath}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("${preview.totalFiles} files · ${displaySize(preview.totalBytes)} to copy (plus reserve)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onConfirmPreview, enabled = canModify) { Text("Start copy") }
                                TextButton(onClick = onCancelPreview, enabled = canModify) { Text("Cancel") }
                            }
                        }
                    }
                }
                if (copyProgress != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Copying game data — ${copyProgress.copiedFiles} / ${copyProgress.totalFiles} files",
                                style = MaterialTheme.typography.bodySmall)
                            Text(displaySize(copyProgress.copiedBytes) + " / " +
                                displaySize(copyProgress.totalBytes),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { copyProgress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(copyProgress.currentFile ?: "Preparing files…",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = onCancelCopy) { Text("Cancel copying") }
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

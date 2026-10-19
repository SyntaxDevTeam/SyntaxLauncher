package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.TrashedMinecraftInstance

/** Destructive actions are never issued until explicitly confirmed. */
@Composable
fun InstanceManagerDialog(
    instances: List<MinecraftInstance>,
    trashed: List<TrashedMinecraftInstance>,
    selected: MinecraftInstance?,
    canModify: Boolean,
    hasLegacyGame: Boolean,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (MinecraftInstance, String) -> Unit,
    onClone: (MinecraftInstance, String) -> Unit,
    onEditSettings: (MinecraftInstance) -> Unit,
    onImportLegacy: (String) -> Unit,
    onDelete: (MinecraftInstance) -> Unit,
    onRestore: (TrashedMinecraftInstance) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(selected?.id) { mutableStateOf(selected?.name.orEmpty()) }
    var deleting by remember { mutableStateOf(false) }
    val valid = name.trim().length in 1..48
    if (deleting && selected != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Move instance to trash?") },
            text = { Text("Worlds and settings for ${selected.name} will be moved to recoverable storage. You can restore them from this manager.") },
            confirmButton = {
                TextButton(onClick = { onDelete(selected); deleting = false }, enabled = canModify) { Text("Move to trash") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Minecraft instances") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Each profile has its own worlds and settings. Downloads and Java are shared.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { onSelect("") }, enabled = canModify,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Legacy Vanilla (v0.1)") }
                instances.forEach { instance ->
                    OutlinedButton(
                        onClick = { onSelect(instance.id) }, enabled = canModify,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text((if (selected?.id == instance.id) "●  " else "") +
                            "${instance.name}   ·   ${instance.minecraftVersionId}   ·   ${instance.id.take(8)}")
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Instance name") },
                    enabled = canModify, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCreate(name.trim()) },
                        enabled = canModify && valid,
                    ) { Text("Create") }
                    if (selected != null) {
                        OutlinedButton(
                            onClick = { onRename(selected, name.trim()) },
                            enabled = canModify && valid,
                        ) { Text("Rename") }
                    }
                }
                if (selected != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onClone(selected, name.trim() + " copy") },
                            enabled = canModify && valid && name.trim().length < 43,
                        ) { Text("Clone") }
                        OutlinedButton(
                            onClick = { onEditSettings(selected) },
                            enabled = canModify,
                        ) { Text("Instance settings") }
                    }
                    OutlinedButton(
                        onClick = { deleting = true },
                        enabled = canModify,
                    ) { Text("Move selected instance to trash") }
                }
                if (trashed.isNotEmpty()) {
                    Text("Recover deleted instances", style = MaterialTheme.typography.titleSmall)
                    trashed.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.instance.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    entry.instance.minecraftVersionId + " · " + entry.instance.id.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { onRestore(entry) }, enabled = canModify) {
                                Text("Restore")
                            }
                        }
                    }
                }
                if (hasLegacyGame) {
                    OutlinedButton(
                        onClick = { onImportLegacy(if (valid) name.trim() else "Imported Vanilla") },
                        enabled = canModify,
                    ) { Text("Import v0.1 game (copy worlds)") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

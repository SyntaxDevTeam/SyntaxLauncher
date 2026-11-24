package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore
import pl.syntaxdevteam.syntaxlauncher.core.update.LauncherUpdateChannel
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo

@Composable
internal fun LauncherUpdateChannelPicker(store: LauncherPreferencesStore, enabled: Boolean = true) {
    val strings = LocalStrings.current
    var channel by remember(store) { mutableStateOf<LauncherUpdateChannel?>(null) }
    var open by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        saving = true
        try { channel = store.loadUpdateChannel(LauncherBuildInfo.updateChannel); failed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { saving = false }
    }
    LaunchedEffect(store) { load() }
    androidx.compose.material3.TextButton(onClick = { open = true }, enabled = enabled) {
        Text(strings.text("updates.channel") + ": " + (channel?.label ?: "…"))
    }
    if (open) androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) open = false },
        title = { Text(strings.text("updates.channel")) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { open = false }, enabled = !saving) {
                Text(strings.text("updates.done"))
            }
        },
        text = { Column {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && !saving && channel != null,
            modifier = Modifier.fillMaxWidth()) {
            Text(channel?.label ?: strings.text("updates.loading"))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            LauncherUpdateChannel.entries.forEach { next ->
                DropdownMenuItem(text = { Text(next.label) }, onClick = {
                    expanded = false
                    if (next != channel) scope.launch {
                        saving = true
                        try {
                            store.saveUpdateChannel(next)
                            channel = next; changed = true; failed = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { saving = false }
                    }
                })
            }
        }
        Text(strings.text(if (changed) "updates.restart" else "updates.help"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed) {
            Text(strings.text("updates.error"), color = MaterialTheme.colorScheme.error)
            androidx.compose.material3.TextButton(onClick = { scope.launch { load() } }, enabled = !saving) {
                Text(strings.text("updates.retry"))
            }
        }
    } })
}

package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.account.*
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

@Composable
internal fun PlayerProfilesDialog(
    players: List<SavedAccount>, selectedId: String, microsoftEnabled: Boolean,
    onSelect: (SavedAccount) -> Unit,
    onAddMicrosoft: suspend () -> PremiumAccount,
    onRemove: suspend (String) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(strings.text("players.manage")) },
        text = { PlayerProfilesContent(players, selectedId, microsoftEnabled, onSelect,
            onAddMicrosoft, onRemove, { busy = it; onBusyChange(it) }) },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.text("players.done")) } },
    )
}

@Composable
internal fun PlayerProfilesContent(
    players: List<SavedAccount>, selectedId: String, microsoftEnabled: Boolean,
    onSelect: (SavedAccount) -> Unit,
    onAddMicrosoft: suspend () -> PremiumAccount,
    onRemove: suspend (String) -> Unit,
    onBusyChange: (Boolean) -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    fun perform(action: suspend () -> Unit) {
        busy = true
        error = null
        onBusyChange(true)
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: strings.text("players.failed") }
            finally { busy = false; onBusyChange(false) }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.text("players.microsoft_hint"), style = MaterialTheme.typography.bodySmall)
        if (players.none { it.microsoft }) Text(strings.text("players.empty"))
        players.filter { it.microsoft }.forEach { player ->
            val id = player.account.uuid.toString()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onSelect(player) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("select-player-$id")) {
                    Text(player.account.username + " · Microsoft" +
                        if (selectedId == id) " ✓" else "")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { removingId = id }, enabled = !busy,
                        modifier = Modifier.testTag("remove-player-$id")) { Text(strings.text("players.remove")) }
                }
                if (removingId == id) {
                    Text(strings.text("players.remove_hint"), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { perform { onRemove(id); removingId = null } }, enabled = !busy) {
                            Text(strings.text("players.confirm_remove"))
                        }
                        TextButton(onClick = { removingId = null }, enabled = !busy) { Text(strings.text("players.cancel")) }
                    }
                }
            }
        }
        HorizontalDivider()
        if (microsoftEnabled) OutlinedButton(onClick = { perform {
            onSelect(SavedAccount(onAddMicrosoft(), true))
        } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(strings.text("ui.add_microsoft")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

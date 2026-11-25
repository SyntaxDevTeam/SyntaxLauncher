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
    onAddOffline: suspend (String) -> OfflineAccount,
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
            onAddMicrosoft, onAddOffline, onRemove, { busy = it; onBusyChange(it) }) },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.text("players.done")) } },
    )
}

@Composable
internal fun PlayerProfilesContent(
    players: List<SavedAccount>, selectedId: String, microsoftEnabled: Boolean,
    onSelect: (SavedAccount) -> Unit,
    onAddMicrosoft: suspend () -> PremiumAccount,
    onAddOffline: suspend (String) -> OfflineAccount,
    onRemove: suspend (String) -> Unit,
    onBusyChange: (Boolean) -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    var addingOffline by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
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
        if (players.isEmpty()) Text(strings.text("players.empty"))
        players.forEach { player ->
            val id = player.account.uuid.toString()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onSelect(player) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("select-player-$id")) {
                    Text(player.displayName() + if (selectedId == id) " ✓" else "")
                }
                TextButton(onClick = { removingId = id }, enabled = !busy,
                    modifier = Modifier.testTag("remove-player-$id")) {
                    Text(strings.text(if (player.microsoft) "players.sign_out" else "players.remove"))
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
        if (players.isNotEmpty()) HorizontalDivider()
        if (microsoftEnabled) OutlinedButton(onClick = { perform {
            onSelect(SavedAccount(onAddMicrosoft(), true))
        } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(strings.text("ui.add_microsoft")) }
        if (addingOffline) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(strings.text("players.nickname")) },
                supportingText = { Text(strings.text("players.name_hint")) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("offline-player-nickname"),
            )
            Row {
                TextButton(
                    onClick = { perform {
                        onSelect(SavedAccount(onAddOffline(nickname.trim()), false))
                        addingOffline = false
                        nickname = ""
                    } },
                    enabled = !busy && OfflineAccount.isValidUsername(nickname.trim()),
                ) { Text(strings.text("players.save")) }
                TextButton(onClick = { addingOffline = false; nickname = ""; error = null }, enabled = !busy) {
                    Text(strings.text("players.cancel"))
                }
            }
        } else {
            TextButton(
                onClick = { addingOffline = true; error = null },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.text("players.add_offline")) }
        }
        if (microsoftEnabled) Text(strings.text("account.privacy_summary"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

internal fun SavedAccount.displayName(): String = account.username + if (microsoft) " · Microsoft" else ""

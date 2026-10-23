package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

@Composable
internal fun LauncherSettingsContent(session: DesktopLauncherSession, memoryMiB: Int? = null,
    profileName: String = "", memoryEnabled: Boolean = true, onMemoryBusy: (Boolean) -> Unit = {},
    onSaveMemory: suspend (Int) -> Unit = {}) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var paths by remember { mutableStateOf(false) }
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SyntaxLauncher " + LauncherBuildInfo.version + " · " + LauncherBuildInfo.buildLabel,
            style = MaterialTheme.typography.labelSmall)
        memoryMiB?.let { memory ->
            MemoryAllocationSettings(profileName, memory, memoryEnabled, onMemoryBusy, onSaveMemory)
            HorizontalDivider()
        }
        LauncherUpdateChannelPicker(session.preferences)
        Text(strings.text("settings.account_pending"), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            scope.launch {
                loading = true; failed = false
                try { logs = session.readLauncherLog() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { loading = false }
            }
        }, enabled = !loading) { Text(strings.text("settings.logs")) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(strings.text("health.error"), color = MaterialTheme.colorScheme.error)
        logs?.let { text ->
            Text(strings.text("settings.logs_help"), style = MaterialTheme.typography.bodySmall)
            SelectionContainer { Text(text.ifBlank { strings.text("settings.logs_empty") },
                style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = { paths = !paths }) { Text(strings.text("settings.storage")) }
        if (paths) session.launcherPaths?.let {
            SelectionContainer { Text("Data: ${it.dataDir}\nSettings: ${it.configDir}\nCache: ${it.cacheDir}\nLogs: ${it.logsDir}",
                style = MaterialTheme.typography.bodySmall) }
            Text(strings.text("settings.retention"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun MemoryAllocationSettings(profileName: String, memoryMiB: Int, enabled: Boolean,
    onBusyChange: (Boolean) -> Unit = {}, onSave: suspend (Int) -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var draft by remember(profileName, memoryMiB) { mutableFloatStateOf(memoryMiB.toFloat()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    Text(strings.text("ui.memory_allocation"), style = MaterialTheme.typography.titleMedium)
    Text(strings.text("settings.memory_scope", profileName), style = MaterialTheme.typography.bodySmall)
    Text("${draft.toInt()} MiB · ${"%.1f".format(java.util.Locale.ROOT, draft / 1024)} GB")
    Slider(value = draft, onValueChange = { draft = it; saved = false }, valueRange = 1024f..16384f,
        steps = 14, enabled = enabled && !saving, modifier = Modifier.fillMaxWidth().testTag("settings-memory-slider"))
    if (!enabled && !saving) Text(strings.text("settings.memory_locked"), style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        if (saving) return@Button
        saving = true; failed = false; onBusyChange(true)
        scope.launch {
            try { onSave(draft.toInt()); saved = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false; onBusyChange(false) }
        }
    }, enabled = enabled && !saving && draft.toInt() != memoryMiB, modifier = Modifier.testTag("settings-memory-save")) {
        Text(strings.text("ui.save_settings"))
    }
    if (saved) Text(strings.text("settings.memory_saved"))
    if (failed) Text(strings.text("settings.memory_error"), color = MaterialTheme.colorScheme.error)
}

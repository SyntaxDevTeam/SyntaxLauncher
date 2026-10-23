package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

@Composable
internal fun LauncherSettingsContent(session: DesktopLauncherSession) {
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

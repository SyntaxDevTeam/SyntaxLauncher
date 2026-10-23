package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

/** Compatibility entry for the last failed game; all repairs use the shared guarded health flow. */
@Composable
fun MinecraftDiagnosticsDialog(session: DesktopLauncherSession, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var target by remember { mutableStateOf<Pair<ResolvedMinecraftVersion, MinecraftInstance?>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val busy by session.preparingAccount.collectAsState()
    val process by session.processManager.status.collectAsState()
    LaunchedEffect(session) {
        try { target = session.lastDiagnosticTarget() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val selected = target
    if (selected != null) GameHealthDialog(session, selected.first, selected.second,
        canRepair = !busy && process.state !in setOf(MinecraftProcessState.RUNNING, MinecraftProcessState.STARTING),
        onDismiss = onDismiss)
    else AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.text("health.title")) },
        text = { if (failed) Text(strings.text("health.error")) else CircularProgressIndicator() },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.text("ui.close")) } })
}

package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.diagnostics.DiagnosticReport
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

@Composable
internal fun GameHealthDialog(session: DesktopLauncherSession, version: ResolvedMinecraftVersion,
    instance: MinecraftInstance?, canRepair: Boolean, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf<DiagnosticReport?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val busy by session.preparingAccount.collectAsState()
    val maintenance by session.maintenanceResult.collectAsState()
    var requestedRepair by remember { mutableStateOf(false) }
    suspend fun scan() {
        scanning = true; failed = false; copied = false
        try { report = session.diagnose(version, instance) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { scanning = false }
    }
    LaunchedEffect(version, instance?.id) { scan() }
    LaunchedEffect(busy) { if (requestedRepair && !busy) scan() }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(strings.text("health.title")) },
        confirmButton = {
            if (busy && requestedRepair) TextButton(onClick = session::cancelPreparation) {
                Text(strings.text("ui.cancel_preparation"))
            } else Button(onClick = {
                requestedRepair = session.repair(version, instance)
                if (!requestedRepair) failed = true
            }, enabled = canRepair && !busy && !scanning) { Text(strings.text("health.repair")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.text("ui.close")) } },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.text("health.repair_help"))
            if (scanning || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(strings.text("health.error"), color = MaterialTheme.colorScheme.error)
            report?.let { current ->
                current.findings.forEach { finding ->
                    Text(strings.text("health.cause.${finding.code.name.lowercase()}"),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(strings.text("health.confidence.${finding.confidence.name.lowercase()}"),
                        style = MaterialTheme.typography.labelSmall)
                }
                Text(strings.text("health.files", current.checkedFiles, current.damagedFiles, current.unverifiedFiles),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { details = !details }) { Text(strings.text("health.details")) }
                if (details) Text(current.export(), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { clipboard.setText(AnnotatedString(current.export())); copied = true }) {
                    Text(strings.text(if (copied) "health.copied" else "health.copy"))
                }
            }
            TextButton(onClick = { scope.launch {
                try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val folder = session.gameLogs(instance)
                    check(java.nio.file.Files.isDirectory(folder, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    check(java.awt.Desktop.isDesktopSupported())
                    java.awt.Desktop.getDesktop().open(folder.toFile())
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            } }) { Text(strings.text("diagnostics.open_logs")) }
            TextButton(onClick = { scope.launch { scan() } }, enabled = !scanning && !busy) {
                Text(strings.text("health.scan"))
            }
            Text(strings.text("health.help"), style = MaterialTheme.typography.bodySmall)
            if (requestedRepair && !busy && maintenance != null) {
                Text(strings.text("health.repair_" + maintenance))
            }
        } })
}

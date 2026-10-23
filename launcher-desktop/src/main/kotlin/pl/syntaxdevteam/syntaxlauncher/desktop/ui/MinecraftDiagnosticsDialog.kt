package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.diagnostics.DiagnosticAction
import pl.syntaxdevteam.syntaxlauncher.core.diagnostics.MinecraftDiagnosticReport
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

/** Shows only static, localized findings. Raw game logs and token-bearing commands stay private. */
@Composable
fun MinecraftDiagnosticsDialog(session: DesktopLauncherSession, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var report by remember(session) { mutableStateOf<MinecraftDiagnosticReport?>(null) }
    var loading by remember(session) { mutableStateOf(true) }
    var repairing by remember(session) { mutableStateOf(false) }
    var repaired by remember(session) { mutableStateOf(false) }
    var problem by remember(session) { mutableStateOf(false) }

    LaunchedEffect(session) {
        loading = true
        try { report = session.diagnoseLastGame() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { problem = true }
        finally { loading = false }
    }

    val hasRepair = report?.findings?.any { it.action == DiagnosticAction.REPAIR_MANAGED_FILES } == true
    AlertDialog(
        onDismissRequest = { if (!repairing) onDismiss() },
        title = { Text(strings.text("diagnostics.title")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loading) CircularProgressIndicator()
                if (problem) Text(strings.text("diagnostics.read_error"),
                    color = MaterialTheme.colorScheme.error)
                report?.let { result ->
                    Text(strings.text("diagnostics.context", result.minecraftVersion,
                        result.loader ?: "Vanilla", result.exitCode ?: "—"),
                        style = MaterialTheme.typography.bodySmall)
                    result.findings.forEach { finding ->
                        Text(strings.text("diagnostics.finding.${finding.code.lowercase()}.title"),
                            style = MaterialTheme.typography.titleSmall)
                        Text(strings.text("diagnostics.finding.${finding.code.lowercase()}.detail"),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(strings.text("diagnostics.confidence.${finding.confidence.name.lowercase()}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(strings.text("diagnostics.sources", result.reviewedSources.size),
                        style = MaterialTheme.typography.bodySmall)
                    Text(strings.text("diagnostics.privacy"), style = MaterialTheme.typography.bodySmall)
                }
                if (repaired) Text(strings.text("diagnostics.repaired"), color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !repairing) {
                Text(strings.text("ui.close"))
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                session.lastGameAttempt?.let { attempt ->
                    TextButton(enabled = !repairing && !loading, onClick = {
                        val folder = attempt.gameDirectory.resolve("logs")
                        try {
                            check(java.nio.file.Files.isDirectory(folder,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS)) { "Game logs directory is unavailable." }
                            check(java.awt.Desktop.isDesktopSupported() &&
                                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                                "Opening folders is not supported by this desktop."
                            }
                            java.awt.Desktop.getDesktop().open(folder.toFile())
                        } catch (_: Exception) { problem = true }
                    }) { Text(strings.text("diagnostics.open_logs")) }
                }
                if (hasRepair && !repaired) TextButton(
                    enabled = !repairing && !loading,
                    onClick = {
                        repairing = true
                        scope.launch {
                            try { session.repairLastGameFiles(); repaired = true }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { problem = true }
                            finally { repairing = false }
                        }
                    },
                ) { Text(strings.text(if (repairing) "diagnostics.repairing" else "diagnostics.repair")) }
            }
        },
    )
}

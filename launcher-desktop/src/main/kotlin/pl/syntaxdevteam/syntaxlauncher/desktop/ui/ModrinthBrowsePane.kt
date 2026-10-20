package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthCatalog
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstallPlan
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProjectDetail

/** Search + popularity + details + user-approved verified installation, scoped to an instance. */
@Composable
fun ModrinthBrowsePane(
    instance: MinecraftInstance,
    catalog: ModrinthCatalog,
    busy: Boolean,
    canModify: Boolean,
    onInstall: (ModrinthInstallPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(instance.id) { mutableStateOf("") }
    var submitted by remember(instance.id) { mutableStateOf("") }
    var offset by remember(instance.id) { mutableStateOf(0) }
    var found by remember(instance.id) { mutableStateOf<List<ModrinthProject>>(emptyList()) }
    var selected by remember(instance.id) { mutableStateOf<ModrinthProject?>(null) }
    var detail by remember(instance.id) { mutableStateOf<ModrinthProjectDetail?>(null) }
    var plan by remember(instance.id) { mutableStateOf<ModrinthInstallPlan?>(null) }
    var loading by remember { mutableStateOf(false) }
    var detailsLoading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(instance.id, submitted, offset) {
        loading = true
        error = null
        try {
            found = catalog.search(instance, submitted, offset)
            selected = null
            detail = null
            plan = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            found = emptyList()
            error = failure.message ?: "Could not load Modrinth mods."
        } finally { loading = false }
    }
    LaunchedEffect(selected?.id) {
        detail = null
        plan = null
        val item = selected ?: return@LaunchedEffect
        detailsLoading = true
        try {
            detail = catalog.detail(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not read mod details."
        } finally { detailsLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DISCOVER MODS · MODRINTH", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Minecraft " + instance.minecraftVersionId + " · " +
                    (instance.loader?.id ?: "Vanilla"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(enabled = !busy && !resolving, onClick = onDismiss) { Text("← Installed mods") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 100) text = it },
                label = { Text("Find a mod") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = !busy,
            )
            Button(enabled = !loading && !busy, onClick = {
                submitted = text.trim()
                offset = 0
            }) { Text("Search") }
        }
        Text(if (submitted.isEmpty()) "Most downloaded compatible mods"
            else "Results for “$submitted”",
            style = MaterialTheme.typography.titleSmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
        if (loading || detailsLoading || resolving) {
            CircularProgressIndicator()
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val stacked = maxWidth < 620.dp
            val list: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier = modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!loading && found.isEmpty()) Text(
                        "No mods were found for this Minecraft version and loader.",
                        style = MaterialTheme.typography.bodyMedium)
                    found.forEach { project ->
                        val isSelected = selected?.id == project.id
                        OutlinedButton(
                            onClick = { selected = project; error = null },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(project.title + (if (isSelected) " · Selected" else ""),
                                    fontWeight = FontWeight.SemiBold)
                                Text(project.summary.take(135),
                                    style = MaterialTheme.typography.bodySmall)
                                Text("by " + project.author + " · " + project.downloads + " downloads",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { offset = (offset - 20).coerceAtLeast(0) },
                            enabled = offset > 0 && !loading && !busy) { Text("Previous") }
                        Text("Page " + (offset / 20 + 1), modifier = Modifier.padding(top = 10.dp))
                        OutlinedButton(onClick = { offset += 20 },
                            enabled = found.size == 20 && !loading && !busy && offset < 1000) {
                            Text("Next") }
                    }
                }
            }
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier = modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = selected
                    if (current == null) {
                        Text("Select a mod to read its description and review a compatible download.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(current.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(current.summary, style = MaterialTheme.typography.bodyMedium)
                        detail?.let { info ->
                            Text("License: " + (info.license ?: "Unknown"),
                                style = MaterialTheme.typography.labelMedium)
                            OutlinedButton(enabled = !busy, onClick = {
                                runCatching {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().browse(URI(info.projectUrl))
                                    }
                                }.onFailure { error = it.message ?: "Could not open browser." }
                            }) { Text("Read on Modrinth ↗") }
                            HorizontalDivider()
                            Text(info.description.ifBlank { "No further project description." }.take(3500),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (plan == null && !detailsLoading) {
                            Button(enabled = canModify && !busy && !resolving,
                                onClick = {
                                    resolving = true
                                    error = null
                                    scope.launch {
                                        try { plan = catalog.plan(instance, current) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) {
                                            error = failure.message ?: "No compatible version found."
                                        } finally { resolving = false }
                                    }
                                }) { Text("Review installation") }
                        }
                        plan?.let { proposal ->
                            HorizontalDivider()
                            Text("Ready to install", style = MaterialTheme.typography.titleMedium)
                            proposal.entries.forEach { entry ->
                                Text("• " + entry.title + " · " + entry.versionNumber +
                                    (if (entry.dependency) " (required)" else ""),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            proposal.notices.forEach { notice ->
                                Text("Note: " + notice, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            Text("Downloads are checked against Modrinth SHA-1 and file size. " +
                                "Mods execute third-party code when Minecraft starts.",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = canModify && !busy,
                                    onClick = { onInstall(proposal) }) { Text("Install to this instance") }
                                TextButton(enabled = !busy, onClick = { plan = null }) {
                                    Text("Cancel") }
                            }
                        }
                    }
                }
            }
            if (stacked) {
                Column(modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    list(Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider()
                    preview(Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    list(Modifier.weight(1f))
                    preview(Modifier.weight(1f))
                }
            }
        }
        Text("Results are provided by Modrinth. Only listed compatible versions are offered; " +
            "the loader must already be installed in the selected instance.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

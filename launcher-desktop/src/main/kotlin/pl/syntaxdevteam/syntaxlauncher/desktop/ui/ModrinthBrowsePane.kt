package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

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
import androidx.compose.material3.Checkbox
import androidx.compose.ui.platform.testTag
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
    val strings = LocalStrings.current
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
            error = failure.message ?: strings.text("ui.could_not_load_modrinth_mods")
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
            error = failure.message ?: strings.text("ui.could_not_read_mod_details")
        } finally { detailsLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.text("ui.discover_mods_modrinth"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Minecraft " + instance.minecraftVersionId + " · " +
                    (instance.loader?.id ?: "Vanilla"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(enabled = !busy && !resolving, onClick = onDismiss) { Text(strings.text("ui.installed_mods")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 100) text = it },
                label = { Text(strings.text("ui.find_a_mod")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = !busy && !resolving,
            )
            Button(enabled = !loading && !busy && !resolving, onClick = {
                submitted = text.trim()
                offset = 0
            }) { Text(strings.text("ui.search")) }
        }
        Text(if (submitted.isEmpty()) strings.text("ui.most_downloaded_compatible_mods")
            else strings.text("ui.results_for_0", submitted),
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
                        strings.text("ui.no_mods_were_found_for_this_minecraft_version_and_loader"),
                        style = MaterialTheme.typography.bodyMedium)
                    found.forEach { project ->
                        val isSelected = selected?.id == project.id
                        OutlinedButton(
                            onClick = { selected = project; error = null },
                            modifier = Modifier.fillMaxWidth().testTag("modrinth-project-${project.id}"), enabled = !busy && !resolving,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(project.title + (if (isSelected) strings.text("ui.selected") else ""),
                                    fontWeight = FontWeight.SemiBold)
                                Text(project.summary.take(135),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(strings.text("ui.by") + project.author + " · " + project.downloads + strings.text("ui.downloads"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { offset = (offset - 20).coerceAtLeast(0) },
                            enabled = offset > 0 && !loading && !busy && !resolving) { Text(strings.text("ui.previous")) }
                        Text(strings.text("ui.page") + (offset / 20 + 1), modifier = Modifier.padding(top = 10.dp))
                        OutlinedButton(onClick = { offset += 20 },
                            enabled = found.size == 20 && !loading && !busy && !resolving && offset < 1000) {
                            Text(strings.text("ui.next")) }
                    }
                }
            }
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier = modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = selected
                    if (current == null) {
                        Text(strings.text("ui.select_a_mod_to_read_its_description_and_review_a_compatible_d"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(current.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(current.summary, style = MaterialTheme.typography.bodyMedium)
                        detail?.let { info ->
                            Text(strings.text("ui.license") + (info.license ?: strings.text("ui.unknown")),
                                style = MaterialTheme.typography.labelMedium)
                            OutlinedButton(enabled = !busy && !resolving, onClick = {
                                runCatching {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().browse(URI(info.projectUrl))
                                    }
                                }.onFailure { error = it.message ?: strings.text("ui.could_not_open_browser") }
                            }) { Text(strings.text("ui.read_on_modrinth")) }
                            HorizontalDivider()
                            Text(info.description.ifBlank { strings.text("ui.no_further_project_description") }.take(3500),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (plan == null && !detailsLoading) {
                            Button(enabled = canModify && !busy && !resolving,
                                modifier = Modifier.testTag("modrinth-review-plan"), onClick = {
                                    resolving = true
                                    error = null
                                    scope.launch {
                                        try { plan = catalog.plan(instance, current) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) {
                                            error = failure.message ?: strings.text("ui.no_compatible_version_found")
                                        } finally { resolving = false }
                                    }
                                }) { Text(strings.text("ui.review_installation")) }
                        }
                        plan?.let { proposal ->
                            HorizontalDivider()
                            Text(strings.text("ui.ready_to_install"), style = MaterialTheme.typography.titleMedium)
                            proposal.entries.forEach { entry ->
                                Text("• " + entry.title + " · " + entry.versionNumber +
                                    (if (entry.optional) strings.text("mods.optional_label") else if (entry.dependency) strings.text("ui.required") else ""),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (proposal.optionalDependencies.isNotEmpty()) {
                                Text(strings.text("mods.optional_choices"), style = MaterialTheme.typography.titleSmall)
                                proposal.optionalDependencies.forEach { choice ->
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Checkbox(checked = choice.selected, enabled = canModify && !busy && !resolving,
                                            modifier = Modifier.testTag("modrinth-optional-${choice.key}"),
                                            onCheckedChange = { checked ->
                                                val selectedChoices = proposal.optionalDependencies.filter { it.selected }.map { it.key }.toSet()
                                                val next = if (checked) selectedChoices + choice.key else selectedChoices - choice.key
                                                resolving = true; error = null
                                                scope.launch {
                                                    try {
                                                        plan = catalog.plan(instance, current,
                                                            proposal.entries.single { !it.dependency }.versionId, next)
                                                    } catch (cancelled: CancellationException) { throw cancelled }
                                                    catch (failure: Exception) { error = failure.message ?: strings.text("catalog.install_error") }
                                                    finally { resolving = false }
                                                }
                                            })
                                        Text(choice.title, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            proposal.notices.forEach { notice ->
                                Text(strings.text("ui.note") + notice, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            Text(strings.text("ui.downloads_are_checked_against_modrinth_sha_1_and_file_size") +
                                strings.text("ui.mods_execute_third_party_code_when_minecraft_starts"),
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = canModify && !busy && !resolving,
                                    modifier = Modifier.testTag("modrinth-install-plan"), onClick = { onInstall(proposal) }) { Text(strings.text("ui.install_to_this_instance")) }
                                TextButton(enabled = !busy && !resolving, onClick = { plan = null }) {
                                    Text(strings.text("ui.cancel")) }
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
        Text(strings.text("ui.results_are_provided_by_modrinth_only_listed_compatible_versio") +
            strings.text("ui.the_loader_must_already_be_installed_in_the_selected_instance"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

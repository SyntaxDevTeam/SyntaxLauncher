package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import org.slf4j.LoggerFactory

enum class CatalogSection(val label: String) { MODS("catalog.mods"), TEXTURES("catalog.textures") }

@Composable
fun ContentCatalogDialog(instance: MinecraftInstance, session: DesktopLauncherSession, canModify: Boolean,
    onBusyChange: (Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var busy by remember { mutableStateOf(false) }
    DialogWindow(onCloseRequest = { if (!busy) onDismiss() }, title = strings.text("catalog.title"),
        state = rememberDialogState(size = DpSize(820.dp, 650.dp)), resizable = true) {
        SyntaxTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ContentCatalog(instance, canModify, search = { section, query, offset ->
                    when (section) {
                        CatalogSection.MODS -> session.modrinthCatalog.search(instance, query, offset)
                        CatalogSection.TEXTURES -> session.resourcePackCatalog.search(instance, query, offset)
                    }
                }, install = { section, project ->
                    when (section) {
                        CatalogSection.MODS -> session.modrinthInstaller.install(instance.id,
                            session.modrinthCatalog.plan(instance, project))
                        CatalogSection.TEXTURES -> session.resourcePackCatalog.install(instance.id, project)
                    }
                }, onBusyChange = { busy = it; onBusyChange(it) }, onManage = onManage, onDismiss = onDismiss)
            }
        }
    }
}

/** No file/version/dependency review in the normal path: pick a compatible item, then Install. */
@Composable
fun ContentCatalog(instance: MinecraftInstance, canModify: Boolean,
    search: suspend (CatalogSection, String, Int) -> List<ModrinthProject>,
    install: suspend (CatalogSection, ModrinthProject) -> Unit,
    onBusyChange: (Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sections = remember(instance.loader?.id) {
        if (instance.loader != null) CatalogSection.entries else listOf(CatalogSection.TEXTURES)
    }
    var section by remember(instance.id) { mutableStateOf(sections.first()) }
    var query by remember(instance.id) { mutableStateOf("") }
    var submitted by remember(instance.id) { mutableStateOf("") }
    var offset by remember(instance.id) { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<ModrinthProject>>(emptyList()) }
    var installed by remember(instance.id) { mutableStateOf(setOf<String>()) }
    var installing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var installationJob by remember { mutableStateOf<Job?>(null) }
    val busy = installing != null
    LaunchedEffect(instance.id, section, submitted, offset, refresh) {
        loading = true
        error = null
        success = false
        found = emptyList()
        try { found = search(section, submitted, offset) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            LoggerFactory.getLogger("ContentCatalog").warn("Catalog search failed", failure)
            error = strings.text("catalog.load_error")
        } finally { loading = false }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 550.dp
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(strings.text("catalog.title"), style = MaterialTheme.typography.titleLarge)
                    Text(instance.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = !busy, onClick = onDismiss) { Text(strings.text("ui.close")) }
            }
            if (sections.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { item -> ChannelButton(strings.text(item.label), section == item, !busy, {
                    section = item; offset = 0; query = ""; submitted = ""
                }, Modifier.weight(1f)) }
            }
            if (!compact) Text(strings.text("catalog.hint"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { if (it.length <= 100) query = it }, singleLine = true,
                    label = { Text(strings.text("catalog.search")) }, enabled = !busy, modifier = Modifier.weight(1f))
                Button(enabled = !busy && !loading, onClick = { submitted = query.trim(); offset = 0; refresh++ }) {
                    Text(strings.text("ui.search"))
                }
            }
            if (!canModify) Text(strings.text("catalog.stop_game"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) TextButton(onClick = { installationJob?.cancel() }) { Text(strings.text("ui.cancel")) }
            if (cancelled) Text(strings.text("catalog.cancelled"), style = MaterialTheme.typography.bodySmall)
            if (success) Text(strings.text("catalog.success"), color = MaterialTheme.colorScheme.primary)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy, onClick = { refresh++ }) { Text(strings.text("ui.retry")) }
            }
            if (loading) CircularProgressIndicator()
            if (!loading && error == null && found.isEmpty()) Text(strings.text("catalog.empty"))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(found, key = { it.id }) { project ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(SyntaxBrand.controlRadius)) {
                        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
                            val description: @Composable (Modifier) -> Unit = { modifier ->
                                Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                                    Text(project.summary.take(150), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val action: @Composable () -> Unit = {
                                val key = section.name + ":" + project.id
                                Button(enabled = canModify && !busy && key !in installed,
                                    modifier = Modifier.testTag("catalog-install-${project.id}"), onClick = {
                                        if (installing == null && canModify && key !in installed) {
                                            installing = project.id; onBusyChange(true); error = null; success = false; cancelled = false
                                            installationJob = scope.launch {
                                                try {
                                                    install(section, project)
                                                    installed = installed + key
                                                    success = true
                                                } catch (cancellation: CancellationException) { cancelled = true; throw cancellation }
                                                catch (failure: Exception) {
                                                    LoggerFactory.getLogger("ContentCatalog").warn("Catalog installation failed", failure)
                                                    error = strings.text(when (failure) {
                                                        is pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInUseException -> "catalog.stop_game"
                                                        is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadNetworkException,
                                                        is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadHttpException -> "catalog.connection_error"
                                                        is pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackException -> "catalog.pack_error"
                                                        else -> "catalog.install_error"
                                                    })
                                                } finally { installing = null; onBusyChange(false) }
                                            }
                                        }
                                    }) {
                                    Text(strings.text(when {
                                        installing == project.id -> "catalog.installing"
                                        key in installed -> "catalog.installed"
                                        else -> "catalog.install"
                                    }))
                                }
                            }
                            if (maxWidth < 480.dp) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                description(Modifier.fillMaxWidth()); action()
                            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                description(Modifier.weight(1f)); action()
                            }
                        }
                    }
                }
            }
            if (!compact || found.size == 20 || offset > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(enabled = !busy && !loading && offset > 0,
                    onClick = { offset = (offset - 20).coerceAtLeast(0) }) { Text(strings.text("ui.previous")) }
                Text(strings.text("catalog.source"), style = MaterialTheme.typography.labelSmall)
                OutlinedButton(enabled = !busy && !loading && found.size == 20 && offset < 1000,
                    onClick = { offset += 20 }) { Text(strings.text("ui.next")) }
            }
            if (instance.loader != null) TextButton(enabled = !busy, onClick = onManage) {
                Text(strings.text("catalog.manage"))
            }
        }
    }
}

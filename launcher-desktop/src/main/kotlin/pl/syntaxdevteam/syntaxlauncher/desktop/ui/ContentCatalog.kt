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

enum class CatalogSection(val label: String) { MODS("catalog.mods"), TEXTURES("catalog.textures"), SHADERS("catalog.shaders"), OPTIFINE("catalog.optifine"), MODPACKS("catalog.modpacks") }

@Composable
fun ContentCatalogDialog(instance: MinecraftInstance, session: DesktopLauncherSession, canModify: Boolean,
    onBusyChange: (Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit,
    onInstanceInstalled: (MinecraftInstance) -> Unit = {}) {
    val strings = LocalStrings.current
    var busy by remember { mutableStateOf(false) }
    var shaderAvailable by remember(instance.id) { mutableStateOf(false) }
    var optiFineAvailable by remember(instance.id) { mutableStateOf(false) }
    var managingPacks by remember { mutableStateOf(false) }
    var compatibilityRefresh by remember { mutableStateOf(0) }
    var shaderIssue by remember(instance) { mutableStateOf(false) }
    var optiFineIssue by remember(instance) { mutableStateOf(false) }
    LaunchedEffect(instance, managingPacks, compatibilityRefresh) {
        shaderIssue = false
        try { shaderAvailable = session.shaderPackCatalog.available(instance) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { shaderIssue = true; LoggerFactory.getLogger("ContentCatalog").warn("Shader availability check failed", failure) }
    }
    LaunchedEffect(instance, session.optiFine, compatibilityRefresh) {
        optiFineIssue = false
        try { optiFineAvailable = session.optiFine?.projects(instance)?.isNotEmpty() == true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { optiFineIssue = true; LoggerFactory.getLogger("ContentCatalog").warn("OptiFine availability check failed", failure) }
    }
    DialogWindow(onCloseRequest = { if (!busy) onDismiss() }, title = strings.text("catalog.title"),
        state = rememberDialogState(size = DpSize(820.dp, 650.dp)), resizable = true) {
        SyntaxTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (managingPacks) InstalledPacks(instance, session, canModify,
                    onBusyChange = { busy = it; onBusyChange(it) }, onBack = { managingPacks = false })
                else ContentCatalog(instance, canModify, search = { section, query, offset ->
                    when (section) {
                        CatalogSection.MODS -> session.modrinthCatalog.search(instance, query, offset)
                        CatalogSection.TEXTURES -> session.resourcePackCatalog.search(instance, query, offset)
                        CatalogSection.SHADERS -> session.shaderPackCatalog.search(instance, query, offset)
                        CatalogSection.OPTIFINE -> session.optiFine?.projects(instance).orEmpty()
                        CatalogSection.MODPACKS -> requireNotNull(session.modpackCatalog).search(query, offset)
                    }
                }, install = { section, project ->
                    when (section) {
                        CatalogSection.MODS -> session.modrinthInstaller.install(instance.id,
                            session.modrinthCatalog.plan(instance, project))
                        CatalogSection.TEXTURES -> session.resourcePackCatalog.install(instance.id, project)
                        CatalogSection.SHADERS -> session.shaderPackCatalog.install(instance.id, project)
                        CatalogSection.MODPACKS -> onInstanceInstalled(requireNotNull(session.modpackCatalog).install(project))
                        CatalogSection.OPTIFINE -> session.optiFine?.install(instance.id, project)
                            ?: throw pl.syntaxdevteam.syntaxlauncher.core.content.GraphicsEnhancementException("OptiFine service is unavailable.")
                    }
                }, onBusyChange = { busy = it; onBusyChange(it) }, onManage = onManage, onDismiss = onDismiss,
                    modpacksAvailable = session.modpackCatalog != null,
                    importPack = session.modpackInstaller?.let { installer -> {
                        val selected = chooseModpackFile() ?: throw CancellationException("Import cancelled")
                        onInstanceInstalled(installer.install(selected))
                    } },
                    shaderAvailable = shaderAvailable, optiFineAvailable = optiFineAvailable, onManagePacks = { managingPacks = true },
                    onRetryCompatibility = if (shaderIssue || optiFineIssue) ({ compatibilityRefresh++ }) else null)
            }
        }
    }
}

/** No file/version/dependency review in the normal path: pick a compatible item, then Install. */
@Composable
fun ContentCatalog(instance: MinecraftInstance?, canModify: Boolean,
    search: suspend (CatalogSection, String, Int) -> List<ModrinthProject>,
    install: suspend (CatalogSection, ModrinthProject) -> Unit,
    onBusyChange: (Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit,
    shaderAvailable: Boolean = false, optiFineAvailable: Boolean = false, onManagePacks: (() -> Unit)? = null,
    onRetryCompatibility: (() -> Unit)? = null, modpacksAvailable: Boolean = false,
    importPack: (suspend () -> Unit)? = null, installationDetail: String? = null) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sections = remember(instance?.loader?.id, shaderAvailable, optiFineAvailable, modpacksAvailable) {
        buildList {
            if (instance?.loader != null) add(CatalogSection.MODS)
            if (instance != null) add(CatalogSection.TEXTURES)
            if (shaderAvailable) add(CatalogSection.SHADERS)
            if (optiFineAvailable) add(CatalogSection.OPTIFINE)
            if (modpacksAvailable) add(CatalogSection.MODPACKS)
        }
    }
    var section by remember(instance?.id) { mutableStateOf(sections.first()) }
    var query by remember(instance?.id) { mutableStateOf("") }
    var submitted by remember(instance?.id) { mutableStateOf("") }
    var offset by remember(instance?.id) { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<ModrinthProject>>(emptyList()) }
    var installed by remember(instance?.id) { mutableStateOf(setOf<String>()) }
    var installing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var installationJob by remember { mutableStateOf<Job?>(null) }
    val busy = installing != null
    LaunchedEffect(instance?.id, section, submitted, offset, refresh) {
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
    fun beginInstall(id: String, key: String, action: suspend () -> Unit) {
        if (installing != null || !canModify || key in installed) return
        installing = id; onBusyChange(true); error = null; success = false; cancelled = false
        installationJob = scope.launch {
            try { action(); installed = installed + key; success = true }
            catch (cancellation: CancellationException) { cancelled = true; throw cancellation }
            catch (failure: Exception) {
                LoggerFactory.getLogger("ContentCatalog").warn("Catalog installation failed", failure)
                error = strings.text(when (failure) {
                    is pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackCompatibilityException,
                    is pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderException -> "catalog.modpack_compatibility_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackFormatException -> "catalog.modpack_format_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackIntegrityException,
                    is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadIntegrityException,
                    is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadSizeMismatchException -> "catalog.integrity_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeException -> "catalog.runtime_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInUseException -> "catalog.stop_game"
                    is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadNetworkException,
                    is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadHttpException -> "catalog.connection_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.content.GraphicsEnhancementException -> "catalog.graphics_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackException -> "catalog.pack_error"
                    is java.io.IOException, is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadFileSystemException -> "catalog.storage_error"
                    else -> "catalog.install_error"
                })
            } finally { installing = null; onBusyChange(false) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 550.dp
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(strings.text("catalog.title"), style = MaterialTheme.typography.titleLarge)
                    Text(instance?.name ?: strings.text("catalog.modpacks"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { if (busy) installationJob?.cancel() else onDismiss() }) {
                    Text(strings.text(if (busy) "ui.cancel" else "ui.close"))
                }
            }
            if (sections.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { item -> ChannelButton(strings.text(item.label), section == item, !busy, {
                    section = item; offset = 0; query = ""; submitted = ""
                }, Modifier.weight(1f)) }
            }
            if (section == CatalogSection.MODPACKS) Text(strings.text("catalog.modpack_hint"), style = MaterialTheme.typography.bodySmall)
            else if (!compact) Text(strings.text("catalog.hint"), style = MaterialTheme.typography.bodySmall)
            if (busy && installationDetail != null) Text(installationDetail, style = MaterialTheme.typography.bodySmall)
            if (section != CatalogSection.OPTIFINE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { if (it.length <= 100) query = it }, singleLine = true,
                    label = { Text(strings.text("catalog.search")) }, enabled = !busy, modifier = Modifier.weight(1f))
                Button(enabled = !busy && !loading, onClick = { submitted = query.trim(); offset = 0; refresh++ }) {
                    Text(strings.text("ui.search"))
                }
            }
            onRetryCompatibility?.let { retry -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.text("catalog.compatibility_error"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = retry) { Text(strings.text("ui.retry")) }
            } }
            if (!canModify) Text(strings.text("catalog.stop_game"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cancelled) Text(strings.text("catalog.cancelled"), style = MaterialTheme.typography.bodySmall)
            if (success && !compact) Text(strings.text("catalog.success"), color = MaterialTheme.colorScheme.primary)
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
                        BoxWithConstraints(Modifier.fillMaxWidth().padding(if (compact) 8.dp else 14.dp)) {
                            val description: @Composable (Modifier) -> Unit = { modifier ->
                                Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(project.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                                    if (!compact) Text(if (section == CatalogSection.OPTIFINE) strings.text("catalog.optifine_hint") else project.summary.take(150), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val action: @Composable () -> Unit = {
                                val key = section.name + ":" + project.id
                                Button(enabled = canModify && !busy && key !in installed,
                                    modifier = Modifier.testTag("catalog-install-${project.id}"), onClick = {
                                        beginInstall(project.id, key) { install(section, project) }
                                    }) {
                                    Text(strings.text(when {
                                        installing == project.id -> "catalog.installing"
                                        key in installed -> "catalog.installed"
                                        else -> "catalog.install"
                                    }))
                                }
                            }
                            if (maxWidth < 480.dp && !compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                description(Modifier.fillMaxWidth()); action()
                            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                description(Modifier.weight(1f)); action()
                            }
                        }
                    }
                }
            }
            var moreOpen by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (found.size == 20 || offset > 0) {
                    OutlinedButton(enabled = !busy && !loading && offset > 0,
                        onClick = { offset = (offset - 20).coerceAtLeast(0) }) { Text(strings.text("ui.previous")) }
                    OutlinedButton(enabled = !busy && !loading && found.size == 20 && offset < 1000,
                        onClick = { offset += 20 }) { Text(strings.text("ui.next")) }
                } else if (!compact) Text(strings.text("catalog.source"), style = MaterialTheme.typography.labelSmall)
                if (onManagePacks != null || instance?.loader != null || importPack != null) androidx.compose.foundation.layout.Box {
                    TextButton(enabled = !busy, onClick = { moreOpen = true }) { Text(strings.text("catalog.more")) }
                    androidx.compose.material3.DropdownMenu(moreOpen, onDismissRequest = { moreOpen = false }) {
                        importPack?.let { importer -> androidx.compose.material3.DropdownMenuItem(
                            text = { Text(strings.text("catalog.import_modpack")) },
                            onClick = { moreOpen = false; beginInstall("local-import", "local-" + System.nanoTime()) { importer() } }) }
                        onManagePacks?.let { manage -> androidx.compose.material3.DropdownMenuItem(
                            text = { Text(strings.text("catalog.my_packs")) }, onClick = { moreOpen = false; manage() }) }
                        if (instance?.loader != null) androidx.compose.material3.DropdownMenuItem(
                            text = { Text(strings.text("catalog.manage")) }, onClick = { moreOpen = false; onManage() })
                    }
                }
            }
        }
    }
}

internal fun chooseModpackFile(): java.nio.file.Path? {
    val picker = javax.swing.JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Modrinth modpack", "mrpack") }
    return if (picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) picker.selectedFile.toPath() else null
}

@Composable
fun ModpackCatalogDialog(session: DesktopLauncherSession, canModify: Boolean, onBusyChange: (Boolean) -> Unit,
    onInstalled: (MinecraftInstance) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackProgress?>(null) }
    val report: (pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackProgress) -> Unit = { progress = it }
    val detail = progress?.let { update -> strings.text(when (update.phase) {
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.VALIDATING -> "catalog.checking_pack"
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.FILES -> "catalog.downloading_pack"
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.GAME -> "catalog.preparing_game"
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.COMPLETE -> "catalog.installed"
    }) + if (update.total > 0) " ${update.completed}/${update.total}" else "" }
    DialogWindow(onCloseRequest = { if (!busy) onDismiss() }, title = strings.text("catalog.modpacks"),
        state = rememberDialogState(size = DpSize(820.dp, 650.dp)), resizable = true) {
        SyntaxTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ContentCatalog(null, canModify,
                search = { _, query, offset -> requireNotNull(session.modpackCatalog).search(query, offset) },
                install = { _, project -> onInstalled(requireNotNull(session.modpackCatalog).install(project, report)) },
                onBusyChange = { busy = it; onBusyChange(it) }, onManage = {}, onDismiss = onDismiss,
                modpacksAvailable = true, importPack = {
                    val path = chooseModpackFile() ?: throw CancellationException("Import cancelled")
                    onInstalled(requireNotNull(session.modpackInstaller).install(path, report))
                }, installationDetail = detail)
        } }
    }
}

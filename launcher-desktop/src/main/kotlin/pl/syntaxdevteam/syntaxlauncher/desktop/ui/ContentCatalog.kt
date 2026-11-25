package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.mods.CurseForgeDownloadUnavailable
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.modpack.CurseForgeOverrideUnverified
import pl.syntaxdevteam.syntaxlauncher.core.network.CurseForgeCdnAuthenticationException
import pl.syntaxdevteam.syntaxlauncher.core.network.CurseForgeCdnRedirectException
import pl.syntaxdevteam.syntaxlauncher.core.mods.CatalogCategory
import pl.syntaxdevteam.syntaxlauncher.core.mods.CatalogRelease
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings
import org.slf4j.LoggerFactory

enum class CatalogSection(val label: String) { MODS("catalog.mods"), TEXTURES("catalog.textures"), SHADERS("catalog.shaders"), OPTIFINE("catalog.optifine"), MODPACKS("catalog.modpacks") }

@Composable
internal fun ContentCatalogPanel(instance: MinecraftInstance, session: DesktopLauncherSession, canModify: Boolean,
    onBusyChange: (Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit,
    onInstanceInstalled: (MinecraftInstance) -> Unit = {}, games: List<String> = emptyList()) {
    val strings = LocalStrings.current
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
        SyntaxTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (managingPacks) InstalledPacks(instance, session, canModify,
                    onBusyChange = onBusyChange, onBack = { managingPacks = false })
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
                }, onBusyChange = onBusyChange, onManage = onManage, onDismiss = onDismiss,
                    modpacksAvailable = session.modpackCatalog != null,
                    importPack = session.modpackInstaller?.let { installer -> {
                        val selected = chooseModpackFile() ?: throw CancellationException("Import cancelled")
                        onInstanceInstalled(installer.install(selected))
                    } },
                    loadIcon = session.catalogIcons::load,
                    shaderAvailable = shaderAvailable, optiFineAvailable = optiFineAvailable, onManagePacks = { managingPacks = true },
                    onRetryCompatibility = if (shaderIssue || optiFineIssue) ({ compatibilityRefresh++ }) else null,
                    browser = CatalogBrowser(session, games, instance, onInstalled = onInstanceInstalled))
            }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContentCatalog(instance: MinecraftInstance?, canModify: Boolean,
    search: suspend (CatalogSection, String, Int) -> List<ModrinthProject>,
    install: suspend (CatalogSection, ModrinthProject) -> Unit,
    onBusyChange: (Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit,
    shaderAvailable: Boolean = false, optiFineAvailable: Boolean = false, onManagePacks: (() -> Unit)? = null,
    onRetryCompatibility: (() -> Unit)? = null, modpacksAvailable: Boolean = false,
    importPack: (suspend () -> Unit)? = null, installationDetail: String? = null,
    loadIcon: suspend (String) -> ByteArray? = { null }, browser: CatalogBrowser? = null,
    onPick: ((ModrinthProject, CatalogRelease?, CatalogSource) -> Unit)? = null) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sections = remember(instance?.loader?.id, shaderAvailable, optiFineAvailable, modpacksAvailable) {
        buildList {
            if (instance?.loader != null || (instance != null && browser != null)) add(CatalogSection.MODS)
            if (instance != null) add(CatalogSection.TEXTURES)
            if (shaderAvailable) add(CatalogSection.SHADERS)
            if (optiFineAvailable) add(CatalogSection.OPTIFINE)
            if (modpacksAvailable) add(CatalogSection.MODPACKS)
        }
    }
    var section by remember(instance?.id) { mutableStateOf(if (instance != null && instance.loader == null) CatalogSection.TEXTURES else sections.first()) }
    var query by remember(instance?.id) { mutableStateOf("") }
    var submitted by remember(instance?.id) { mutableStateOf("") }
    var offset by remember(instance?.id) { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(CatalogPage(emptyList(), false)) }
    val found = page.projects
    var installed by remember(instance?.id) { mutableStateOf(setOf<String>()) }
    var installing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorProjectUrl by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    var success by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var installationJob by remember { mutableStateOf<Job?>(null) }
    var source by remember { mutableStateOf(CatalogSource.MODRINTH) }
    var game by remember(instance?.id) { mutableStateOf(instance?.minecraftVersionId) }
    var loader by remember(instance?.id) { mutableStateOf(browser?.requiredLoader ?: instance?.loader?.id) }
    var category by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var selection by remember { mutableStateOf<ModrinthProject?>(null) }
    val activeSource = if (section.supportsCurseForge) source else CatalogSource.MODRINTH
    val selectedGame = if (section == CatalogSection.OPTIFINE) instance?.minecraftVersionId else game
    val selectedLoader = when (section) {
        CatalogSection.MODS, CatalogSection.MODPACKS -> loader
        CatalogSection.SHADERS -> instance?.let { browser?.shaderLoader(it) }
        else -> null
    }
    val criteria = CatalogCriteria(activeSource, section, selectedGame, selectedLoader, category)
    LaunchedEffect(browser?.session, activeSource, section, refresh) {
        categories = emptyList()
        try { categories = browser?.categories(activeSource, section).orEmpty() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { LoggerFactory.getLogger("ContentCatalog").warn("Category lookup failed", failure) }
    }
    val busy = installing != null
    LaunchedEffect(instance?.id, criteria, submitted, offset, refresh) {
        loading = true
        error = null
        errorProjectUrl = null
        success = false
        page = CatalogPage(emptyList(), false)
        try {
            page = if (browser != null && section != CatalogSection.OPTIFINE) browser.search(criteria, submitted, offset)
                else search(section, submitted, offset).let { CatalogPage(it, it.size == 20) }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            LoggerFactory.getLogger("ContentCatalog").warn("Catalog search failed", failure)
            error = strings.text(when {
                failure is CurseForgeKeyMissing -> "catalog.curseforge_key"
                failure is pl.syntaxdevteam.syntaxlauncher.core.mods.CurseForgeApiException &&
                    failure.statusCode in setOf(401, 403) -> "catalog.curseforge_auth"
                else -> "catalog.load_error"
            })
        } finally { loading = false }
    }
    fun beginInstall(id: String, key: String, action: suspend () -> Unit) {
        if (installing != null || !canModify || key in installed) return
        installing = id; onBusyChange(true); error = null; success = false; cancelled = false
        errorProjectUrl = null
        installationJob = scope.launch {
            try { action(); installed = installed + key; success = true }
            catch (cancellation: CancellationException) { cancelled = true; throw cancellation }
            catch (failure: Exception) {
                LoggerFactory.getLogger("ContentCatalog").warn("Catalog installation failed", failure)
                errorProjectUrl = (failure as? CurseForgeDownloadUnavailable)?.projectUrl
                error = strings.curseForgeInstallError(failure) ?: strings.text(when (failure) {
                    is pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackCompatibilityException,
                    is pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderException -> "catalog.modpack_compatibility_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackFormatException -> "catalog.modpack_format_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackIntegrityException,
                    is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadIntegrityException,
                    is pl.syntaxdevteam.syntaxlauncher.core.network.DownloadSizeMismatchException -> "catalog.integrity_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthConflictException -> "mods.conflict_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.mods.CurseForgeApiException -> "catalog.connection_error"
                    is pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthApiException -> "catalog.connection_error"
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
                    section = item; offset = 0; query = ""; submitted = ""; category = null; selection = null
                    game = if (item == CatalogSection.MODPACKS) null else instance?.minecraftVersionId
                    loader = browser?.requiredLoader ?: if (item == CatalogSection.MODPACKS) null else instance?.loader?.id
                }, Modifier.weight(1f)) }
            }
            if (browser == null && section == CatalogSection.MODPACKS) Text(strings.text("catalog.modpack_hint"), style = MaterialTheme.typography.bodySmall)
            else if (browser == null && !compact) Text(strings.text("catalog.hint"), style = MaterialTheme.typography.bodySmall)
            if (busy && installationDetail != null) Text(installationDetail, style = MaterialTheme.typography.bodySmall)
            if (selection == null && section != CatalogSection.OPTIFINE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { if (it.length <= 100) query = it }, singleLine = true,
                    label = { Text(strings.text("catalog.search")) }, enabled = !busy, modifier = Modifier.weight(1f))
                Button(enabled = !busy && !loading, onClick = { submitted = query.trim(); offset = 0; refresh++ }) {
                    Text(strings.text("ui.search"))
                }
            }
            if (selection == null && browser != null && section != CatalogSection.OPTIFINE) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section.supportsCurseForge) CatalogOptions(strings.text("catalog.provider"), activeSource.name,
                        CatalogSource.entries.map { it.name to it.label }, !busy) {
                        source = CatalogSource.valueOf(requireNotNull(it))
                        if (source == CatalogSource.CURSEFORGE && section.usesLoaderFilter && loader != null && game == null)
                            game = instance?.minecraftVersionId ?: browser.games.firstOrNull()
                        category = null; offset = 0
                    }
                    CatalogOptions(strings.text("catalog.categories"), category, listOf(null to strings.text("catalog.all")) +
                        categories.map { it.id to it.name }, !busy) { category = it; offset = 0 }
                    if (section.usesLoaderFilter) {
                        val loaderChoices = listOf("fabric" to "Fabric", "forge" to "Forge", "neoforge" to "NeoForge", "quilt" to "Quilt")
                        CatalogOptions(strings.text("catalog.loaders"), selectedLoader,
                            listOf(null to strings.text("catalog.all")) + loaderChoices, !busy && browser.requiredLoader == null) {
                            loader = it
                            if (activeSource == CatalogSource.CURSEFORGE && it != null && game == null)
                                game = instance?.minecraftVersionId ?: browser.games.firstOrNull()
                            offset = 0
                        }
                    } else if (selectedLoader != null) {
                        Text(if (selectedLoader == "iris") "Iris" else "OptiFine", modifier = Modifier.padding(vertical = 12.dp))
                    }
                    val needsGame = activeSource == CatalogSource.CURSEFORGE && section.usesLoaderFilter && selectedLoader != null
                    CatalogOptions(strings.text("ui.game_version"), selectedGame,
                        (if (needsGame) emptyList() else listOf(null to strings.text("catalog.all"))) +
                            (listOfNotNull(instance?.minecraftVersionId) + browser.games).distinct().map { it to it },
                        !busy) { game = it; offset = 0 }
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
                errorProjectUrl?.let { url ->
                    TextButton(onClick = { uriHandler.openUri(url) }) { Text(CatalogSource.CURSEFORGE.label) }
                }
                TextButton(enabled = !busy, onClick = { refresh++ }) { Text(strings.text("ui.retry")) }
            }
            val openProject = selection
            if (openProject != null && browser != null) {
                CatalogDetails(openProject, criteria, browser, Modifier.weight(1f), busy, canModify,
                    loadIcon, picking = onPick != null, onBack = { selection = null; error = null; errorProjectUrl = null },
                    installed = { id -> "${activeSource.name}:${section.name}:${openProject.id}:$id" in installed },
                    onChoose = { release, optional ->
                        if (onPick != null) onPick(openProject, release, activeSource)
                        else beginInstall(openProject.id, "${activeSource.name}:${section.name}:${openProject.id}:${release.id}") {
                            browser.install(criteria, openProject, release.id, optional)
                        }
                    })
            } else {
                if (loading) CircularProgressIndicator()
                if (!loading && error == null && found.isEmpty()) Text(strings.text("catalog.empty"))
                key(criteria, offset, submitted) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(found, key = { it.id }) { project ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(SyntaxBrand.controlRadius)) {
                                Column {
                                Box(Modifier.fillMaxWidth().background(SyntaxBrand.catalogAccents[section.ordinal % SyntaxBrand.catalogAccents.size]).size(3.dp))
                                BoxWithConstraints(Modifier.fillMaxWidth().padding(if (compact) 8.dp else 14.dp)) {
                                    val description: @Composable (Modifier) -> Unit = { modifier ->
                                        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CatalogProjectIcon(project, section, loadIcon, compact)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Text(project.title, maxLines = 2, style = MaterialTheme.typography.titleMedium,
                                                    modifier = if (browser != null && section != CatalogSection.OPTIFINE) Modifier.clickable(enabled = !busy) { selection = project }
                                                        else if (onPick != null) Modifier.clickable { onPick(project, null, activeSource) } else Modifier)
                                                Text(strings.text(section.label), color = SyntaxBrand.catalogAccents[section.ordinal % SyntaxBrand.catalogAccents.size],
                                                    style = MaterialTheme.typography.labelSmall)
                                                if (browser != null && section != CatalogSection.OPTIFINE) {
                                                    Text(listOfNotNull(project.author.takeIf { it.isNotBlank() },
                                                        strings.text("catalog.downloads", java.text.NumberFormat.getIntegerInstance().format(project.downloads)),
                                                        project.updated?.take(10)).joinToString("  ·  "), style = MaterialTheme.typography.labelSmall)
                                                    val tags = project.loaders.map { loaderId ->
                                                        when (loaderId) {
                                                            "neoforge" -> "NeoForge"
                                                            "optifine" -> "OptiFine"
                                                            else -> loaderId.replaceFirstChar(Char::uppercase)
                                                        }
                                                    } + project.categories.filterNot { it.lowercase() in project.loaders }
                                                    if (tags.isNotEmpty()) Text(tags.take(7).joinToString("  ·  "),
                                                        style = MaterialTheme.typography.labelSmall)
                                                }
                                                if (section == CatalogSection.OPTIFINE && project.id.startsWith("preview_")) {
                                                    Text(strings.text("catalog.optifine_preview"), color = MaterialTheme.colorScheme.tertiary,
                                                        style = MaterialTheme.typography.labelMedium)
                                                }
                                                if (!compact || browser != null) {
                                                    val summaryKey = catalogSummaryKey(project.title)
                                                    if (section == CatalogSection.OPTIFINE) Text(strings.text("catalog.optifine_hint"), style = MaterialTheme.typography.bodySmall)
                                                    else if (summaryKey != null && (browser == null || project.summary.isBlank())) Text(strings.text(summaryKey), style = MaterialTheme.typography.bodySmall)
                                                    else {
                                                        Text(project.summary.take(180), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val action: @Composable () -> Unit = {
                                        val key = section.name + ":" + project.id
                                        if (browser != null && section != CatalogSection.OPTIFINE) {
                                            OutlinedButton(enabled = !busy, onClick = { selection = project }) {
                                                Text(strings.text("catalog.details"))
                                            }
                                        } else {
                                            Button(enabled = canModify && !busy && key !in installed,
                                                modifier = Modifier.testTag("catalog-install-${project.id}"), onClick = {
                                                    if (onPick != null) onPick(project, null, activeSource)
                                                    else beginInstall(project.id, key) { install(section, project) }
                                                }) {
                                                Text(strings.text(when {
                                                    onPick != null -> "catalog.choose_pack"
                                                    installing == project.id -> "catalog.installing"
                                                    key in installed -> "catalog.installed"
                                                    else -> "catalog.install"
                                                }))
                                            }
                                        }
                                    }
                                    if (maxWidth < 480.dp && (!compact || browser != null)) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        description(Modifier.fillMaxWidth()); action()
                                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        description(Modifier.weight(1f)); action()
                                    }
                                }
                            }
                            }
                        }
                    }
                }
                var moreOpen by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (page.hasNext || offset > 0) {
                        OutlinedButton(enabled = !busy && !loading && offset > 0,
                            onClick = { offset = (offset - 20).coerceAtLeast(0) }) { Text(strings.text("ui.previous")) }
                        OutlinedButton(enabled = !busy && !loading && page.hasNext && offset < 1000,
                            onClick = { offset += 20 }) { Text(strings.text("ui.next")) }
                    } else if (!compact) Text(if (browser == null) strings.text("catalog.source")
                        else if (section == CatalogSection.OPTIFINE) "OptiFine" else activeSource.label,
                        style = MaterialTheme.typography.labelSmall)
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
}

internal fun chooseModpackFile(): java.nio.file.Path? {
    val picker = javax.swing.JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Modrinth modpack", "mrpack") }
    return if (picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) picker.selectedFile.toPath() else null
}

@Composable
internal fun ModpackCatalogPanel(session: DesktopLauncherSession, canModify: Boolean, onBusyChange: (Boolean) -> Unit,
    onInstalled: (MinecraftInstance) -> Unit, onDismiss: () -> Unit, games: List<String>) {
    val strings = LocalStrings.current
    var progress by remember { mutableStateOf<pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackProgress?>(null) }
    val report: (pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackProgress) -> Unit = { progress = it }
    val detail = progress?.let { update -> strings.text(when (update.phase) {
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.VALIDATING -> "catalog.checking_pack"
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.FILES -> "catalog.downloading_pack"
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.GAME -> "catalog.preparing_game"
        pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackPhase.COMPLETE -> "catalog.installed"
    }) + if (update.total > 0) " ${update.completed}/${update.total}" else "" }
        SyntaxTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ContentCatalog(null, canModify,
                search = { _, query, offset -> requireNotNull(session.modpackCatalog).search(query, offset) },
                install = { _, project -> onInstalled(requireNotNull(session.modpackCatalog).install(project, report)) },
                onBusyChange = onBusyChange, onManage = {}, onDismiss = onDismiss,
                modpacksAvailable = true, importPack = {
                    val path = chooseModpackFile() ?: throw CancellationException("Import cancelled")
                    onInstalled(requireNotNull(session.modpackInstaller).install(path, onProgress = report))
                }, installationDetail = detail, loadIcon = session.catalogIcons::load,
                browser = CatalogBrowser(session, games, onInstalled = onInstalled, modpackProgress = report))
        } }
}


internal fun catalogSummaryKey(title: String): String? = when (title.lowercase(java.util.Locale.ROOT)) {
    "fabric api" -> "catalog.summary.fabric-api"
    "sodium" -> "catalog.summary.sodium"
    "iris", "iris shaders" -> "catalog.summary.iris"
    "remarkably optimized" -> "catalog.summary.remarkably-optimized"
    "simply optimized" -> "catalog.summary.simply-optimized"
    else -> null
}

@Composable
internal fun CatalogProjectIcon(project: ModrinthProject, section: CatalogSection,
    load: suspend (String) -> ByteArray?, compact: Boolean) {
    var bitmap by remember(project.iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(project.iconUrl) {
        bitmap = null
        try {
            bitmap = project.iconUrl?.let { url -> load(url)?.let { bytes -> withContext(Dispatchers.Default) {
                org.jetbrains.skia.Image.makeFromEncoded(bytes).use { image ->
                    if (image.width in 1..1024 && image.height in 1..1024) image.toComposeImageBitmap() else null
                }
            } } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Artwork is optional; the colored category icon remains usable. */ }
    }
    val accent = SyntaxBrand.catalogAccents[section.ordinal % SyntaxBrand.catalogAccents.size]
    Box(Modifier.size(if (compact) 44.dp else 64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        .background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        val icon = bitmap
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
        else BrandIcon(when (section) {
            CatalogSection.MODPACKS -> SyntaxSymbol.Instances
            CatalogSection.MODS -> SyntaxSymbol.Cube
            CatalogSection.TEXTURES -> SyntaxSymbol.Texture
            CatalogSection.SHADERS, CatalogSection.OPTIFINE -> SyntaxSymbol.Sun
        }, tint = accent, size = if (compact) 26.dp else 36.dp)
    }
}


internal fun UiStrings.curseForgeInstallError(failure: Exception): String? = when (failure) {
    is CurseForgeDownloadUnavailable -> text(
        if (failure.distributionBlocked) "catalog.curseforge_distribution_blocked" else "catalog.curseforge_file_unavailable",
        failure.projectName)
    is CurseForgeOverrideUnverified -> text("catalog.curseforge_override_unverified", failure.path)
    is CurseForgeCdnAuthenticationException -> text("catalog.curseforge_auth")
    is CurseForgeCdnRedirectException -> text("catalog.curseforge_redirect")
    else -> null
}

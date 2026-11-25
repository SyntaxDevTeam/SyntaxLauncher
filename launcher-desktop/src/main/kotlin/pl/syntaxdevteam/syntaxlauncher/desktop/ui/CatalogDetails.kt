package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackProgress
import pl.syntaxdevteam.syntaxlauncher.core.mods.CatalogRelease
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProjectDetail
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

internal enum class CatalogSource(val label: String) { MODRINTH("Modrinth"), CURSEFORGE("CurseForge") }
internal val CatalogSection.usesLoaderFilter get() = this == CatalogSection.MODS || this == CatalogSection.MODPACKS
internal val CatalogSection.supportsCurseForge get() = this != CatalogSection.OPTIFINE
internal val CatalogSection.projectType get() = when (this) {
    CatalogSection.MODS -> "mod"
    CatalogSection.MODPACKS -> "modpack"
    CatalogSection.TEXTURES -> "resourcepack"
    CatalogSection.SHADERS -> "shader"
    CatalogSection.OPTIFINE -> error("OptiFine uses its official catalog.")
}

internal data class CatalogCriteria(val source: CatalogSource, val section: CatalogSection,
    val game: String?, val loader: String?, val category: String?)

internal data class CatalogPage(val projects: List<ModrinthProject>, val hasNext: Boolean)

internal class CurseForgeKeyMissing : IllegalStateException()

internal class CatalogBrowser(
    val session: DesktopLauncherSession,
    val games: List<String>,
    private val instance: MinecraftInstance? = null,
    val requiredLoader: String? = null,
    private val onInstalled: (MinecraftInstance) -> Unit = {},
    private val modpackProgress: (ModpackProgress) -> Unit = {},
) {
    private val modrinth get() = session.modrinthCatalog
    private val curseForge get() = session.curseForgeCatalog ?: throw CurseForgeKeyMissing()

    fun shaderLoader(instance: MinecraftInstance) = session.shaderPackCatalog.rendererLoader(instance)

    suspend fun search(criteria: CatalogCriteria, query: String, offset: Int): CatalogPage = with(criteria) {
        val hits = if (source == CatalogSource.CURSEFORGE) {
            curseForge.search(section.projectType, query, offset, game, loader.takeUnless { section == CatalogSection.SHADERS }, category)
        } else modrinth.browse(section.projectType, query, offset, game, loader, category)
        // Modrinth facets are project-wide. Both tags can match different releases of the same project.
        val projects = if (game != null && loader != null &&
            (source == CatalogSource.MODRINTH || section == CatalogSection.SHADERS)) coroutineScope {
            val requests = Semaphore(4)
            hits.map { project ->
                async {
                    requests.withPermit {
                        try {
                            project.takeIf { releases(criteria, it).isNotEmpty() }
                        } catch (missing: pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthApiException) {
                            if (missing.statusCode == 404) null else throw missing
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        } else hits
        CatalogPage(projects, hits.size == 20)
    }

    suspend fun categories(source: CatalogSource, section: CatalogSection) = when {
        section == CatalogSection.OPTIFINE -> emptyList()
        source == CatalogSource.CURSEFORGE -> session.curseForgeCatalog?.categories(section.projectType).orEmpty()
        else -> modrinth.categories(section.projectType)
    }

    suspend fun detail(criteria: CatalogCriteria, project: ModrinthProject) =
        if (criteria.source == CatalogSource.CURSEFORGE) curseForge.detail(project)
        else modrinth.detail(project, criteria.section.projectType)

    suspend fun releases(criteria: CatalogCriteria, project: ModrinthProject) = with(criteria) {
        if (source == CatalogSource.CURSEFORGE) curseForge.releases(section.projectType, project, game, loader)
        else modrinth.releases(project, game, loader, section.projectType)
    }

    suspend fun changelog(source: CatalogSource, project: ModrinthProject, release: CatalogRelease) =
        if (source == CatalogSource.CURSEFORGE) curseForge.changelog(project, release.id)
        else modrinth.changelog(release.id)

    fun compatible(section: CatalogSection, release: CatalogRelease): Boolean {
        if (section == CatalogSection.MODPACKS) {
            return if (requiredLoader != null) requiredLoader in release.loaders
            else release.loaders.any { it in setOf("minecraft", "fabric", "forge", "neoforge", "quilt") }
        }
        val target = instance ?: return false
        return target.minecraftVersionId in release.gameVersions && when (section) {
            CatalogSection.MODS -> release.loaders.any { it.equals(target.loader?.id, ignoreCase = true) }
            CatalogSection.SHADERS -> release.loaders.isEmpty() || shaderLoader(target) in release.loaders
            else -> true
        }
    }

    suspend fun install(criteria: CatalogCriteria, project: ModrinthProject, releaseId: String, optional: Set<String> = emptySet()) {
        if (criteria.section == CatalogSection.MODPACKS) {
            onInstalled(installPack(criteria.source, project, releaseId, progress = modpackProgress))
            return
        }
        val target = requireNotNull(instance)
        if (criteria.source == CatalogSource.CURSEFORGE) {
            curseForge.install(target.id, criteria.section.projectType, project, releaseId)
            return
        }
        when (criteria.section) {
            CatalogSection.MODS -> session.modrinthInstaller.install(target.id, modrinth.plan(target, project, releaseId, optional))
            CatalogSection.TEXTURES -> session.resourcePackCatalog.install(target.id, project, selectedVersionId = releaseId)
            CatalogSection.SHADERS -> session.shaderPackCatalog.install(target.id, project, selectedVersionId = releaseId)
            else -> error("Unsupported release installation.")
        }
    }
    fun canReviewDependencies(criteria: CatalogCriteria) =
        criteria.source == CatalogSource.MODRINTH && criteria.section == CatalogSection.MODS && instance?.loader != null

    suspend fun modPlan(project: ModrinthProject, release: CatalogRelease, optional: Set<String>) =
        modrinth.plan(requireNotNull(instance), project, release.id, optional)

    suspend fun installPack(source: CatalogSource, project: ModrinthProject, releaseId: String, name: String? = null,
        progress: (ModpackProgress) -> Unit = {}): MinecraftInstance =
        if (source == CatalogSource.CURSEFORGE) curseForge.installModpack(project, releaseId,
            requireNotNull(session.modpackInstaller), name, requiredLoader, progress)
        else requireNotNull(session.modpackCatalog).installVersion(project, releaseId, requiredLoader, name, progress)

}

@Composable
internal fun CatalogOptions(label: String, selected: String?, options: List<Pair<String?, String>>,
    enabled: Boolean, modifier: Modifier = Modifier, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) {
            Text("$label · ${options.firstOrNull { it.first == selected }?.second ?: selected.orEmpty()}  ▾",
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@Composable
internal fun CatalogDetails(project: ModrinthProject, criteria: CatalogCriteria, browser: CatalogBrowser,
    modifier: Modifier, busy: Boolean, canModify: Boolean, loadIcon: suspend (String) -> ByteArray?,
    onBack: () -> Unit, installed: (String) -> Boolean, onChoose: (CatalogRelease, Set<String>) -> Unit,
    picking: Boolean = false) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    var detail by remember(project, criteria.source) { mutableStateOf<ModrinthProjectDetail?>(null) }
    var releases by remember(project, criteria) { mutableStateOf<List<CatalogRelease>?>(null) }
    var release by remember(project, criteria) { mutableStateOf<CatalogRelease?>(null) }
    var changelog by remember(project, release) { mutableStateOf<String?>(null) }
    var showingVersions by remember(project) { mutableStateOf(false) }
    var releaseFailure by remember(project, criteria) { mutableStateOf(false) }
    var detailFailure by remember(project, criteria.source) { mutableStateOf(false) }
    var reviewDependencies by remember(project, release) { mutableStateOf(false) }
    var optional by remember(project, release) { mutableStateOf(emptySet<String>()) }
    var plan by remember(project, release, optional) {
        mutableStateOf<pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstallPlan?>(null)
    }
    var planFailure by remember(project, release, optional) { mutableStateOf(false) }
    var retry by remember(project) { mutableIntStateOf(0) }

    LaunchedEffect(project, criteria, retry) {
        releaseFailure = false
        try {
            releases = browser.releases(criteria, project)
            release = releases?.firstOrNull { it.downloadable && browser.compatible(criteria.section, it) }
                ?: releases?.firstOrNull()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { releaseFailure = true }
    }
    LaunchedEffect(project, criteria.source, retry) {
        detailFailure = false
        try { detail = browser.detail(criteria, project) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { detailFailure = true }
    }
    LaunchedEffect(project, criteria.source, release, showingVersions, retry) {
        val version = release ?: return@LaunchedEffect
        if (!showingVersions) return@LaunchedEffect
        changelog = null
        try { changelog = browser.changelog(criteria.source, project, version) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { changelog = strings.text("catalog.load_error") }
    }

    LaunchedEffect(project, release, optional, reviewDependencies, retry) {
        val version = release ?: return@LaunchedEffect
        if (!reviewDependencies) return@LaunchedEffect
        plan = null
        planFailure = false
        try { plan = browser.modPlan(project, version, optional) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { planFailure = true }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !busy, onClick = onBack) { Text(strings.text("wizard.back")) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CatalogProjectIcon(project, criteria.section, loadIcon, false)
            Column(Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.headlineSmall)
                Text(listOf(project.author, criteria.source.label, detail?.license.orEmpty())
                    .filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium)
            }
            (detail?.projectUrl?.takeIf { it.isNotBlank() } ?: project.projectUrl)?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) { Text(criteria.source.label) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogOptions(strings.text("catalog.release"), release?.id,
                releases.orEmpty().map { it.id to it.name }, !busy && !releases.isNullOrEmpty(), Modifier.weight(1f, fill = false)) { id ->
                release = releases?.firstOrNull { it.id == id }
            }
            val selected = release
            if (selected != null) Button(
                modifier = Modifier.testTag("modrinth-install-plan"),
                enabled = canModify && !busy && selected.downloadable && detail?.distributionBlocked != true &&
                    browser.compatible(criteria.section, selected) && !installed(selected.id) &&
                    (!reviewDependencies || plan != null),
                onClick = { onChoose(selected, optional) }) {
                Text(strings.text(when {
                    picking -> "catalog.choose_pack"
                    busy -> "catalog.installing"
                    installed(selected.id) -> "catalog.installed"
                    else -> "catalog.install"
                }))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelButton(strings.text("catalog.overview"), !showingVersions, true, { showingVersions = false })
            ChannelButton(strings.text("catalog.versions"), showingVersions, true, { showingVersions = true })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (releaseFailure || detailFailure) item {
                Text(strings.text("catalog.load_error"), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy, onClick = { retry++ }) { Text(strings.text("ui.retry")) }
            }
            if (releases == null && !releaseFailure) item { CircularProgressIndicator() }
            val distributionBlocked = detail?.distributionBlocked == true || release?.distributionBlocked == true
            if (distributionBlocked) item { Text(strings.text("catalog.curseforge_distribution_blocked", project.title)) }
            if (releases?.isEmpty() == true && !distributionBlocked) item { Text(strings.text("catalog.no_releases")) }
            release?.let { version ->
                if (!distributionBlocked && !version.downloadable) item { Text(strings.text("catalog.download_unavailable")) }
                else if (!distributionBlocked && !browser.compatible(criteria.section, version)) item { Text(strings.text("catalog.incompatible_profile")) }
                if (showingVersions) {
                    item { Text(listOf(version.channel, version.published.take(10)).filter { it.isNotBlank() }.joinToString(" · ")) }
                    item { Text("Minecraft " + version.gameVersions.joinToString(", ")) }
                    if (version.loaders.isNotEmpty()) item { Text(version.loaders.joinToString(", ")) }
                    item { Text(strings.text("catalog.changelog"), style = MaterialTheme.typography.titleMedium) }
                    item {
                        val notes = changelog
                        if (notes == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else Text(notes.ifBlank { strings.text("catalog.no_changelog") })
                    }
                }
            }
            val selected = release
            if (selected != null && browser.canReviewDependencies(criteria) &&
                browser.compatible(criteria.section, selected) && !showingVersions) {
                item {
                    TextButton(enabled = !busy, modifier = Modifier.testTag("modrinth-review-plan"),
                        onClick = { reviewDependencies = !reviewDependencies; optional = emptySet() }) {
                        Text(strings.text("catalog.dependencies"))
                    }
                }
                if (reviewDependencies) item {
                    when {
                        planFailure -> {
                            Text(strings.text("catalog.load_error"), color = MaterialTheme.colorScheme.error)
                            TextButton(enabled = !busy, onClick = { retry++ }) { Text(strings.text("ui.retry")) }
                        }
                        plan == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        else -> {
                            val resolved = requireNotNull(plan)
                            resolved.entries.filter { it.dependency && !it.optional }.forEach { dep ->
                                Text("${dep.title} · ${dep.versionNumber}")
                            }
                            resolved.notices.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            resolved.optionalDependencies.forEach { choice ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(choice.selected, enabled = !busy, modifier = Modifier.testTag("modrinth-optional-${choice.key}"),
                                        onCheckedChange = { checked ->
                                        optional = if (checked) optional + choice.key else optional - choice.key
                                    })
                                    Text(choice.title)
                                }
                            }
                            if (resolved.entries.none { it.dependency } && resolved.optionalDependencies.isEmpty())
                                Text(strings.text("catalog.no_dependencies"))
                        }
                    }
                }
            }
            if (!showingVersions) item { Text(detail?.description?.ifBlank { project.summary } ?: project.summary) }
        }
    }
}

package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.mods.CatalogRelease
import pl.syntaxdevteam.syntaxlauncher.core.mods.CurseForgeDownloadUnavailable
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

private val loaderFamilies = listOf("fabric", "neoforge", "forge", "quilt")

internal enum class ProfileStep { STYLE, LOADER, CONTENT, PACK, DETAILS, REVIEW }
internal enum class ProfileContent { PACK, EXTRAS, EMPTY }

/** Branches remain drafts; only the final confirmation writes a profile or installs a pack. */
@Composable
internal fun ProfileWizard(preferredVersion: String, releases: List<String>,
    findCompatible: suspend (String, String) -> CompatibleLoaderGame?,
    searchPacks: suspend (String, String) -> List<ModrinthProject>,
    create: suspend (String, String, ModLoaderSelection?) -> MinecraftInstance,
    installPack: suspend (ModrinthProject, String, String) -> MinecraftInstance,
    loadIcon: suspend (String) -> ByteArray? = { null },
    onCreated: (MinecraftInstance, Boolean) -> Unit, onBusyChange: (Boolean) -> Unit, onCancel: () -> Unit,
    vanillaOnly: Boolean = false,
    packBrowser: ((String, String) -> CatalogBrowser)? = null,
) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val firstStep = if (vanillaOnly) ProfileStep.DETAILS else ProfileStep.STYLE
    var step by remember { mutableStateOf(firstStep) }
    var modded by remember { mutableStateOf(false) }
    var loader by remember { mutableStateOf("fabric") }
    var moddedContent by remember { mutableStateOf(ProfileContent.EMPTY) }
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(preferredVersion) }
    var setup by remember { mutableStateOf<CompatibleLoaderGame?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var creationJob by remember { mutableStateOf<Job?>(null) }
    var wasCancelled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var installFailure by remember { mutableStateOf<Exception?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var packSource by remember { mutableStateOf(CatalogSource.MODRINTH) }
    var packVersion by remember { mutableStateOf<CatalogRelease?>(null) }
    var pack by remember { mutableStateOf<ModrinthProject?>(null) }
    var compatibleLoaders by remember(version) { mutableStateOf<List<String>?>(null) }
    val content = if (modded) moddedContent else ProfileContent.EMPTY
    val needsCompatibility = content != ProfileContent.PACK && modded && step in setOf(ProfileStep.DETAILS, ProfileStep.REVIEW)
    LaunchedEffect(step, version, retry) {
        if (step != ProfileStep.LOADER) return@LaunchedEffect
        compatibleLoaders = null
        val candidates = loaderFamilies.map { family ->
            async {
                try {
                    withTimeout(20_000) { findCompatible(family, version) }?.versions?.isNotEmpty() == true
                } catch (_: TimeoutCancellationException) {
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    true // A failed lookup is distinct from an unsupported Minecraft version.
                }
            }
        }.awaitAll()
        val supported = loaderFamilies.filterIndexed { index, _ -> candidates[index] }
        compatibleLoaders = supported
        if (loader !in supported && supported.isNotEmpty()) loader = supported.first()
    }
    LaunchedEffect(needsCompatibility, loader, version, retry) {
        setup = null; error = false
        if (needsCompatibility) {
            loading = true
            try { setup = withTimeout(20_000) { findCompatible(loader, version) }; error = setup?.versions.isNullOrEmpty() }
            catch (cancelled: CancellationException) { if (cancelled is TimeoutCancellationException) error = true else throw cancelled }
            catch (_: Exception) { error = true }
            finally { loading = false }
        }
    }
    val versionPicker: @Composable () -> Unit = {
        Text(strings.text("ui.game_version"))
        CatalogOptions(strings.text("ui.game_version"), version,
            (listOf(preferredVersion) + releases).distinct().map { it to it }, !busy,
            Modifier.testTag("wizard-game-version")) {
            version = requireNotNull(it)
            pack = null
            packVersion = null
        }
    }
    val validName = name.trim().length in 1..48 && name.none { it.code < 32 || it.code == 127 }
    val validSetup = !modded || !setup?.versions.isNullOrEmpty()
    val canNext = !busy && !loading && when (step) {
        ProfileStep.LOADER -> compatibleLoaders?.contains(loader) == true
        ProfileStep.PACK -> pack != null && !error
        ProfileStep.DETAILS, ProfileStep.REVIEW -> validName && if (content == ProfileContent.PACK) pack != null else validSetup && !error
        else -> true
    }
    fun previous() {
        error = false
        installFailure = null
        step = when (step) {
            ProfileStep.STYLE -> ProfileStep.STYLE
            ProfileStep.LOADER -> ProfileStep.STYLE
            ProfileStep.CONTENT -> ProfileStep.LOADER
            ProfileStep.PACK -> ProfileStep.CONTENT
            ProfileStep.DETAILS -> when {
                !modded -> ProfileStep.STYLE
                content == ProfileContent.PACK -> ProfileStep.PACK
                else -> ProfileStep.CONTENT
            }
            ProfileStep.REVIEW -> ProfileStep.DETAILS
        }
    }
    if (step == ProfileStep.PACK) {
        ContentCatalog(null, canModify = true, search = { _, query, _ -> searchPacks(query, loader) },
            install = { _, _ -> }, onBusyChange = {}, onManage = {}, onDismiss = { previous() },
            modpacksAvailable = true, loadIcon = loadIcon, browser = packBrowser?.invoke(loader, version),
            onPick = { selected, release, source ->
                packSource = source
                pack = selected
                packVersion = release
                if (name.isBlank()) name = selected.title.take(48)
                // A version choice completes the browser step. The profile is written only after review.
                step = ProfileStep.DETAILS
            })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("profile-wizard")) {
        val compact = maxHeight < 440.dp || maxWidth < 600.dp
        val short = maxHeight < 240.dp
        Image(rememberBrandingPainter("branding/profiles-workshop.png"), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(SyntaxBrand.background.copy(alpha = .86f)))
        Column(Modifier.fillMaxSize().padding(if (short) 8.dp else if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (short) 4.dp else if (compact) 8.dp else 12.dp)) {
            if (!short) Text(strings.text("profiles.create"), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
            Text(strings.text("wizard.step.${step.name.lowercase()}"), color = SyntaxBrand.cyan,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    ProfileStep.STYLE -> {
                        versionPicker()
                        Text(strings.text("wizard.style_help"))
                        WizardChoice(strings.text("game.vanilla"), !modded, !busy) { modded = false; pack = null }
                        WizardChoice(strings.text("game.with_mods"), modded, !busy) { modded = true; pack = null }
                    }
                    ProfileStep.LOADER -> {
                        Text(strings.text("wizard.loader_help"))
                        val options = compatibleLoaders
                        if (options == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else if (options.isEmpty()) Text(strings.text("wizard.no_compatible_loaders"))
                        options.orEmpty().forEach { id ->
                            WizardChoice(if (id == "neoforge") "NeoForge" else id.replaceFirstChar { it.uppercase() }, loader == id, !busy) { loader = id; pack = null }
                        }
                        if (loader == "forge" && options?.contains(loader) == true) Text(strings.text("ui.forge_v2_test_only"), style = MaterialTheme.typography.bodySmall)
                    }
                    ProfileStep.CONTENT -> {
                        Text(strings.text("wizard.content_help"))
                        ProfileContent.entries.forEach { route ->
                            WizardChoice(strings.text("wizard.content.${route.name.lowercase()}"), content == route, !busy) { moddedContent = route; pack = null }
                        }
                    }
                    ProfileStep.PACK -> Unit
                    ProfileStep.DETAILS -> {
                        OutlinedTextField(name, { name = it }, label = { Text(strings.text("game.name")) }, isError = name.isNotBlank() && !validName,
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("wizard-name"))
                        if (content == ProfileContent.PACK) {
                            Text(pack?.title.orEmpty())
                            val release = packVersion
                            if (release == null) Text(strings.text("wizard.pack_versions"))
                            else Text(listOf(release.name, release.gameVersions.joinToString(", ")).joinToString(" · "))
                        } else {
                            if (vanillaOnly) versionPicker()
                            else Text("Minecraft $version")
                            setup?.let { compatible ->
                                Text("Minecraft ${compatible.minecraftVersion} · $loader ${compatible.versions.firstOrNull { it.stable }?.version ?: compatible.versions.firstOrNull()?.version.orEmpty()}")
                                if (compatible.minecraftVersion != version) Text(strings.text("loader.version_adjusted", version, compatible.minecraftVersion))
                            }
                        }
                        Text(strings.text("wizard.no_changes"), style = MaterialTheme.typography.bodySmall)
                    }
                    ProfileStep.REVIEW -> {
                        Text(name.trim(), style = MaterialTheme.typography.headlineSmall)
                        Text(if (!modded) "Vanilla" else if (loader == "neoforge") "NeoForge" else loader.replaceFirstChar { it.uppercase() })
                        if (modded) Text(strings.text("wizard.content.${content.name.lowercase()}"))
                        if (content == ProfileContent.PACK) {
                            Text(pack?.title.orEmpty())
                            val release = packVersion
                            if (release == null) Text(strings.text("wizard.pack_versions"))
                            else Text(listOf(packSource.label, release.name, release.gameVersions.joinToString(", ")).joinToString(" · "))
                        }
                        else { Text("Minecraft ${setup?.minecraftVersion ?: version}"); Text(strings.text("wizard.first_play")) }
                        Text(strings.text("wizard.separate"))
                    }
                }
                if (wasCancelled) Text(strings.text("catalog.cancelled"))
                if (error) {
                    Text(installFailure?.let(strings::curseForgeInstallError) ?: strings.text("wizard.error"),
                        color = MaterialTheme.colorScheme.error)
                    (installFailure as? CurseForgeDownloadUnavailable)?.projectUrl?.let { url ->
                        TextButton(onClick = { uriHandler.openUri(url) }) { Text(CatalogSource.CURSEFORGE.label) }
                    }
                    TextButton(onClick = { installFailure = null; retry++ }) { Text(strings.text("ui.retry")) }
                }
            }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (busy) TextButton(onClick = { creationJob?.cancel() }) { Text(strings.text("ui.cancel")) }
                else TextButton(onClick = { if (step == firstStep) onCancel() else previous() }) { Text(strings.text(if (step == firstStep) "ui.cancel" else "wizard.back")) }
                Button(enabled = canNext, modifier = Modifier.testTag("wizard-next"), onClick = {
                    if (busy || !canNext) return@Button
                    if (step == ProfileStep.REVIEW) {
                        installFailure = null
                        busy = true; error = false; wasCancelled = false; onBusyChange(true)
                        creationJob = scope.launch {
                            try {
                                val result = if (content == ProfileContent.PACK) {
                                    val selected = requireNotNull(pack)
                                    val release = packVersion
                                    val browser = packBrowser?.invoke(loader, version)
                                    if (release != null && browser != null)
                                        browser.installPack(packSource, selected, release.id, name.trim())
                                    else installPack(selected, loader, name.trim())
                                }
                                else {
                                    val game = setup?.minecraftVersion ?: version
                                    val selectedLoader = if (!modded) null else ModLoaderSelection(loader,
                                        requireNotNull(setup).versions.firstOrNull { it.stable }?.version ?: requireNotNull(setup).versions.first().version)
                                    create(name.trim(), game, selectedLoader)
                                }
                                onCreated(result, content == ProfileContent.EXTRAS)
                            } catch (cancelled: CancellationException) { wasCancelled = true; throw cancelled }
                            catch (failure: Exception) { installFailure = failure; error = true }
                            finally { busy = false; onBusyChange(false) }
                        }
                    } else step = when (step) {
                        ProfileStep.STYLE -> if (modded) ProfileStep.LOADER else ProfileStep.DETAILS
                        ProfileStep.LOADER -> ProfileStep.CONTENT
                        ProfileStep.CONTENT -> if (content == ProfileContent.PACK) ProfileStep.PACK else ProfileStep.DETAILS
                        ProfileStep.PACK -> ProfileStep.DETAILS
                        ProfileStep.DETAILS -> ProfileStep.REVIEW
                        ProfileStep.REVIEW -> step
                    }
                }) { Text(strings.text(if (step == ProfileStep.REVIEW) "profiles.create" else "wizard.next")) }
            }
        }
    }
}

@Composable
private fun WizardChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, if (selected) SyntaxBrand.cyan else SyntaxBrand.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) SyntaxBrand.field else SyntaxBrand.background.copy(alpha = .5f)),
        shape = RoundedCornerShape(10.dp)) { Text((if (selected) "✓ " else "") + label) }
}

package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

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
    onCreated: (MinecraftInstance, Boolean) -> Unit, onBusyChange: (Boolean) -> Unit, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(ProfileStep.STYLE) }
    var modded by remember { mutableStateOf(false) }
    var loader by remember { mutableStateOf("fabric") }
    var content by remember { mutableStateOf(ProfileContent.EMPTY) }
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(preferredVersion) }
    var versionMenu by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<CompatibleLoaderGame?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var creationJob by remember { mutableStateOf<Job?>(null) }
    var wasCancelled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var packs by remember { mutableStateOf<List<ModrinthProject>>(emptyList()) }
    var pack by remember { mutableStateOf<ModrinthProject?>(null) }
    val family = if (modded) loader else "minecraft"
    val needsCompatibility = content != ProfileContent.PACK && modded && step in setOf(ProfileStep.DETAILS, ProfileStep.REVIEW)
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
    LaunchedEffect(step == ProfileStep.PACK, family, submitted, retry) {
        if (step == ProfileStep.PACK) {
            loading = true; error = false; packs = emptyList()
            try { packs = withTimeout(20_000) { searchPacks(submitted, family) } }
            catch (cancelled: CancellationException) { if (cancelled is TimeoutCancellationException) error = true else throw cancelled }
            catch (_: Exception) { error = true }
            finally { loading = false }
        }
    }
    val validName = name.trim().length in 1..48 && name.none { it.code < 32 || it.code == 127 }
    val validSetup = !modded || !setup?.versions.isNullOrEmpty()
    val canNext = !busy && !loading && when (step) {
        ProfileStep.PACK -> pack != null && !error
        ProfileStep.DETAILS, ProfileStep.REVIEW -> validName && if (content == ProfileContent.PACK) pack != null else validSetup && !error
        else -> true
    }
    fun previous() {
        error = false
        step = when (step) {
            ProfileStep.STYLE -> ProfileStep.STYLE
            ProfileStep.LOADER -> ProfileStep.STYLE
            ProfileStep.CONTENT -> if (modded) ProfileStep.LOADER else ProfileStep.STYLE
            ProfileStep.PACK -> ProfileStep.CONTENT
            ProfileStep.DETAILS -> if (content == ProfileContent.PACK) ProfileStep.PACK else ProfileStep.CONTENT
            ProfileStep.REVIEW -> ProfileStep.DETAILS
        }
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
                        Text(strings.text("wizard.style_help"))
                        WizardChoice(strings.text("game.vanilla"), !modded, !busy) { modded = false; pack = null }
                        WizardChoice(strings.text("game.with_mods"), modded, !busy) { modded = true; pack = null }
                    }
                    ProfileStep.LOADER -> {
                        Text(strings.text("wizard.loader_help"))
                        listOf("fabric", "neoforge", "forge", "quilt").forEach { id ->
                            WizardChoice(if (id == "neoforge") "NeoForge" else id.replaceFirstChar { it.uppercase() }, loader == id, !busy) { loader = id; pack = null }
                        }
                        if (loader == "forge") Text(strings.text("ui.forge_v2_test_only"), style = MaterialTheme.typography.bodySmall)
                    }
                    ProfileStep.CONTENT -> {
                        Text(strings.text("wizard.content_help"))
                        ProfileContent.entries.forEach { route ->
                            WizardChoice(strings.text("wizard.content.${route.name.lowercase()}"), content == route, !busy) { content = route; pack = null }
                        }
                    }
                    ProfileStep.PACK -> {
                        Text(strings.text("wizard.pack_help", family))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(query, { if (it.length <= 100) query = it }, label = { Text(strings.text("catalog.search")) },
                                singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(onClick = { submitted = query.trim(); retry++ }, enabled = !loading) { Text(strings.text("ui.search")) }
                        }
                        if (!loading && packs.isEmpty() && !error) Text(strings.text("catalog.empty"))
                        packs.forEach { item ->
                            OutlinedButton(onClick = { pack = item; if (name.isBlank()) name = item.title.take(48) },
                                modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (pack?.id == item.id) SyntaxBrand.cyan else SyntaxBrand.border)) {
                                CatalogProjectIcon(item, CatalogSection.MODPACKS, loadIcon, true)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text((if (pack?.id == item.id) "✓ " else "") + item.title)
                                    Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    ProfileStep.DETAILS -> {
                        OutlinedTextField(name, { name = it }, label = { Text(strings.text("game.name")) }, isError = name.isNotBlank() && !validName,
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("wizard-name"))
                        if (content == ProfileContent.PACK) {
                            Text(pack?.title.orEmpty()); Text(strings.text("wizard.pack_versions"))
                        } else {
                            Text(strings.text("ui.game_version"))
                            Box {
                                SelectorButton(version, { versionMenu = true }, enabled = !busy)
                                DropdownMenu(versionMenu, { versionMenu = false }) {
                                    (listOf(preferredVersion) + releases).distinct().forEach { id ->
                                        DropdownMenuItem(text = { Text(id) }, onClick = { version = id; versionMenu = false })
                                    }
                                }
                            }
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
                        Text(strings.text("wizard.content.${content.name.lowercase()}"))
                        if (content == ProfileContent.PACK) { Text(pack?.title.orEmpty()); Text(strings.text("wizard.pack_versions")) }
                        else { Text("Minecraft ${setup?.minecraftVersion ?: version}"); Text(strings.text("wizard.first_play")) }
                        Text(strings.text("wizard.separate"))
                    }
                }
                if (wasCancelled) Text(strings.text("catalog.cancelled"))
                if (error) { Text(strings.text("wizard.error"), color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text(strings.text("ui.retry")) } }
            }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (busy) TextButton(onClick = { creationJob?.cancel() }) { Text(strings.text("ui.cancel")) }
                else TextButton(onClick = { if (step == ProfileStep.STYLE) onCancel() else previous() }) { Text(strings.text(if (step == ProfileStep.STYLE) "ui.cancel" else "wizard.back")) }
                Button(enabled = canNext, modifier = Modifier.testTag("wizard-next"), onClick = {
                    if (busy || !canNext) return@Button
                    if (step == ProfileStep.REVIEW) {
                        busy = true; error = false; wasCancelled = false; onBusyChange(true)
                        creationJob = scope.launch {
                            try {
                                val result = if (content == ProfileContent.PACK) installPack(requireNotNull(pack), family, name.trim())
                                else {
                                    val game = setup?.minecraftVersion ?: version
                                    val selectedLoader = if (!modded) null else ModLoaderSelection(loader,
                                        requireNotNull(setup).versions.firstOrNull { it.stable }?.version ?: requireNotNull(setup).versions.first().version)
                                    create(name.trim(), game, selectedLoader)
                                }
                                onCreated(result, content == ProfileContent.EXTRAS)
                            } catch (cancelled: CancellationException) { wasCancelled = true; throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { busy = false; onBusyChange(false) }
                        }
                    } else step = when (step) {
                        ProfileStep.STYLE -> if (modded) ProfileStep.LOADER else ProfileStep.CONTENT
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

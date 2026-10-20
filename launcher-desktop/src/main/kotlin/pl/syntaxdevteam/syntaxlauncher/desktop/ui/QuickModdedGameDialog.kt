package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.slf4j.LoggerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion
import pl.syntaxdevteam.syntaxlauncher.desktop.ModdedGameSetup
import pl.syntaxdevteam.syntaxlauncher.desktop.suggestModdedGame
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

/** Friendly first-run flow; technical choices stay behind More options. */
@Composable
internal fun QuickModdedGameDialog(
    preferredVersion: String,
    releases: List<String>,
    defaultName: String,
    loadVersions: suspend (String, String) -> List<ModLoaderVersion>,
    create: suspend (String, String, ModLoaderSelection) -> MinecraftInstance,
    onCreated: (MinecraftInstance) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(defaultName) }
    var loader by remember { mutableStateOf("fabric") }
    var requestedVersion by remember { mutableStateOf(preferredVersion) }
    var automaticFallback by remember { mutableStateOf(true) }
    var setup by remember { mutableStateOf<ModdedGameSetup?>(null) }
    var chosenLoader by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var advanced by remember { mutableStateOf(false) }
    var versionMenu by remember { mutableStateOf(false) }
    var loaderMenu by remember { mutableStateOf(false) }
    LaunchedEffect(loader, requestedVersion, automaticFallback, attempt) {
        setup = null
        errorKey = null
        loading = true
        try {
            setup = withTimeout(20_000) {
                suggestModdedGame(requestedVersion, releases, loader, automaticFallback, loadVersions)
            }
            chosenLoader = setup?.recommended?.version.orEmpty()
            if (setup == null) errorKey = "game.no_compatible_version"
        } catch (_: TimeoutCancellationException) {
            errorKey = "game.check_failed"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            LoggerFactory.getLogger("QuickModdedGameSetup").warn("Cannot discover compatible game versions", failure)
            errorKey = "game.check_failed"
        }
        finally { loading = false }
    }
    val validName = name.trim().length in 1..48 && name.none { it.code < 32 || it.code == 127 }
    val resolved = setup
    val ready = resolved != null && resolved.versions.any { it.version == chosenLoader } &&
        validName && !loading && !saving
    AlertDialog(
        containerColor = SyntaxBrand.panel,
        titleContentColor = SyntaxBrand.heroText,
        textContentColor = SyntaxBrand.heroMuted,
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(strings.text("game.setup_title")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.text("game.separate_game"))
                if (loading) {
                    CircularProgressIndicator()
                    Text(strings.text("game.finding_version"))
                } else if (resolved != null) {
                    Text(strings.text("game.ready_version", resolved.minecraftVersion),
                        style = MaterialTheme.typography.titleMedium)
                    Text(strings.text("game.automatic_setup"), style = MaterialTheme.typography.bodySmall)
                }
                errorKey?.let { key ->
                    Text(strings.text(key), color = MaterialTheme.colorScheme.error)
                    if (!saving) TextButton(onClick = { attempt++ }) { Text(strings.text("ui.retry")) }
                }
                TextButton(onClick = { advanced = !advanced }, enabled = !saving) {
                    Text(strings.text(if (advanced) "game.fewer_options" else "game.more_options"))
                }
                if (advanced) {
                    OutlinedTextField(name, { name = it }, enabled = !saving, singleLine = true,
                        label = { Text(strings.text("game.name")) }, isError = !validName,
                        supportingText = { if (!validName) Text(strings.text("game.name_hint")) },
                        modifier = Modifier.fillMaxWidth())
                    Box {
                        SelectorButton(resolved?.minecraftVersion ?: requestedVersion,
                            onClick = { versionMenu = true }, enabled = !saving,
                            modifier = Modifier.testTag("quick-game-version"))
                        DropdownMenu(versionMenu, onDismissRequest = { versionMenu = false }) {
                            (listOf(preferredVersion) + releases).filter(String::isNotBlank).distinct().forEach { version ->
                                DropdownMenuItem(text = { Text(version) }, onClick = {
                                    requestedVersion = version
                                    automaticFallback = false
                                    versionMenu = false
                                })
                            }
                        }
                    }
                    Text(strings.text("game.technical_options"), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (id in listOf("fabric", "quilt")) ChannelButton(
                            id.replaceFirstChar { it.uppercase() }, loader == id, !saving, { loader = id })
                    }
                    if (resolved != null) Box {
                        SelectorButton(chosenLoader, onClick = { loaderMenu = true }, enabled = !saving)
                        DropdownMenu(loaderMenu, onDismissRequest = { loaderMenu = false }) {
                            resolved.versions.forEach { version ->
                                DropdownMenuItem(text = { Text(version.version) }, onClick = {
                                    chosenLoader = version.version
                                    loaderMenu = false
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = ready, modifier = Modifier.testTag("quick-game-create"), onClick = {
                if (saving || loading) return@Button
                val selected = resolved ?: return@Button
                saving = true
                scope.launch {
                    try {
                        val created = create(name.trim(), selected.minecraftVersion,
                            ModLoaderSelection(selected.loaderId, chosenLoader))
                        onCreated(created)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        LoggerFactory.getLogger("QuickModdedGameSetup").warn("Cannot create the modded game profile", failure)
                        errorKey = "game.create_failed"
                    }
                    finally { saving = false }
                }
            }) { Text(strings.text(if (saving) "game.creating" else "game.choose_mods")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(strings.text("ui.cancel")) } },
    )
}

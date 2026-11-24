package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.syntaxdevteam.syntaxlauncher.core.instance.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.ProfileContents

data class InstanceCopyPreview(val from: MinecraftInstance?, val name: String, val minecraftVersionId: String,
    val sourcePath: String, val destinationPath: String, val totalBytes: Long, val totalFiles: Int)

/** An ordinary page inside the main launcher window. It never creates a native window. */
@Composable
fun InstanceWorkspace(
    instances: List<MinecraftInstance>, trashed: List<TrashedMinecraftInstance>, selectedId: String,
    selected: MinecraftInstance?, canModify: Boolean, isBusy: Boolean, isLoadingInventory: Boolean,
    hasLegacyGame: Boolean, latestVersion: String, error: String?, preview: InstanceCopyPreview?,
    copyProgress: InstanceCopyProgress?, contents: ProfileContents?, contentsLoading: Boolean,
    onSelect: (String) -> Unit, onCreateNew: () -> Unit, onEdit: (MinecraftInstance) -> Unit,
    onManageMods: (MinecraftInstance) -> Unit, onClonePreview: (MinecraftInstance, String) -> Unit,
    onLegacyPreview: (String, String) -> Unit, onCancelPreview: () -> Unit, onConfirmPreview: () -> Unit,
    onCancelCopy: () -> Unit, onDelete: (MinecraftInstance) -> Unit, onRestore: (TrashedMinecraftInstance) -> Unit,
    onDismiss: () -> Unit, onImportModpack: () -> Unit = {}, onCancelImport: (() -> Unit)? = null,
    onRetry: () -> Unit = {}, onEditLegacy: () -> Unit = {},
) {
    val strings = LocalStrings.current
    var search by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var cloneName by remember(selectedId) { mutableStateOf(selected?.name?.plus(" copy").orEmpty()) }
    var legacyName by remember { mutableStateOf("Vanilla") }
    var deleting by remember { mutableStateOf<MinecraftInstance?>(null) }
    val filtered = instances.filter { search.isBlank() || (it.name + " " + it.minecraftVersionId + " " + it.loader?.id).contains(search, true) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("profiles-page").clip(RoundedCornerShape(SyntaxBrand.panelRadius))) {
        val compact = maxWidth < 600.dp || maxHeight < 360.dp
        Image(rememberBrandingPainter("branding/profiles-workshop.png"), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(SyntaxBrand.background.copy(alpha = .25f), SyntaxBrand.background.copy(alpha = .88f)))))
        deleting?.let { target ->
            Surface(Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 480.dp),
                shape = RoundedCornerShape(14.dp), color = SyntaxBrand.panel) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(strings.text("profiles.delete") + " · " + target.name, style = MaterialTheme.typography.titleLarge)
                    Text(strings.text("ui.all_worlds_and_settings_will_remain_recoverable_from_this_work"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { deleting = null }, modifier = Modifier.testTag("cancel-delete-profile")) {
                            Text(strings.text("ui.cancel"))
                        }
                        TextButton(onClick = { onDelete(target); deleting = null }, enabled = canModify,
                            modifier = Modifier.testTag("confirm-delete-profile"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(strings.text("profiles.delete"))
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.text("game.my_games"), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    if (!compact) Text(strings.text("profiles.subtitle"), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onCreateNew, enabled = canModify, modifier = Modifier.testTag("create-profile")) {
                    Text(strings.text("profiles.create"))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text(strings.text("ui.retry")) } }
            val list: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.background(SyntaxBrand.background.copy(alpha = .9f), RoundedCornerShape(14.dp))
                    .padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(search, { search = it }, singleLine = true, label = { Text(strings.text("ui.find_instance")) }, modifier = Modifier.fillMaxWidth())
                    ProfileRow(strings.text("ui.legacy_vanilla"), strings.text("ui.original_v0_1_game"), selectedId.isBlank(),
                        canModify, { onSelect("") }, onEditLegacy)
                    if (isLoadingInventory) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!isLoadingInventory && filtered.isEmpty()) Text(strings.text(if (instances.isEmpty())
                        "ui.no_instances_yet_create_your_first_profile" else "ui.no_profiles_match_your_search"))
                    filtered.forEach { profile ->
                        ProfileRow(profile.name, "Minecraft ${profile.minecraftVersionId} · ${profile.loader?.id ?: "Vanilla"}",
                            selectedId == profile.id, canModify, { onSelect(profile.id) }, { onEdit(profile) },
                            onDelete = { deleting = profile }, profileId = profile.id)
                    }
                    if (trashed.isNotEmpty()) {
                        HorizontalDivider()
                        trashed.forEach { entry ->
                            TextButton(onClick = { onRestore(entry) }, enabled = canModify,
                                modifier = Modifier.testTag("restore-profile-${entry.instance.id}")) {
                                Text(strings.text("ui.restore") + " · " + entry.instance.name)
                            }
                        }
                    }
                }
            }
            val detail: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.background(SyntaxBrand.background.copy(alpha = .91f), RoundedCornerShape(14.dp))
                    .padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(selected?.name ?: strings.text(if (selectedId.isBlank()) "ui.legacy_vanilla" else "ui.unavailable_instance"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(strings.text(if (selected?.loader == null) "profiles.vanilla_info" else "profiles.modded_info"))
                    Text("Minecraft ${selected?.minecraftVersionId ?: latestVersion} · ${selected?.loader?.let { "${it.id} ${it.version}" } ?: "Vanilla"}", style = MaterialTheme.typography.bodyMedium)
                    if (contentsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    contents?.let { info ->
                        HorizontalDivider()
                        Text(strings.text("profiles.contains"), style = MaterialTheme.typography.titleMedium)
                        fun items(names: List<String>) = names.take(8).joinToString(", ").ifEmpty { strings.text("profiles.none") } +
                            if (names.size > 8) " …" else ""
                        Text(strings.text("profiles.mods", info.mods.size, items(info.mods)))
                        Text(strings.text("profiles.worlds", info.worlds.size, items(info.worlds)))
                        Text(strings.text("profiles.textures", info.textures.size, items(info.textures)))
                        Text(strings.text("profiles.shaders", info.shaders.size, items(info.shaders)))
                        Text(strings.text("profiles.configs", info.configs))
                        if (info.modpack) Text(strings.text("profiles.modpack_info"), color = SyntaxBrand.cyan)
                    }
                    Button(onClick = onDismiss, enabled = canModify && (selectedId.isBlank() || selected != null)) { Text(strings.text("profiles.use")) }
                    if (selected == null && selectedId.isBlank()) OutlinedButton(onClick = onEditLegacy, enabled = canModify) { Text(strings.text("profiles.edit")) }
                    selected?.let { profile ->
                        OutlinedButton(onClick = { onEdit(profile) }, enabled = canModify) { Text(strings.text("profiles.edit")) }
                        OutlinedButton(onClick = { onManageMods(profile) }, enabled = canModify) { Text(strings.text("catalog.open")) }
                    }
                    TextButton(onClick = { advanced = !advanced }) { Text(strings.text("game.more_options")) }
                    if (advanced) {
                        selected?.let { profile ->
                            OutlinedTextField(cloneName, { cloneName = it }, label = { Text(strings.text("ui.clone_as")) }, singleLine = true, enabled = canModify, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { onClonePreview(profile, cloneName.trim()) }, enabled = canModify && cloneName.trim().length in 1..48) { Text(strings.text("ui.review_clone")) }
                        }
                        OutlinedButton(onClick = onImportModpack, enabled = canModify) { Text(strings.text("modpack.import_file")) }
                        onCancelImport?.let { TextButton(onClick = it) { Text(strings.text("ui.cancel")) } }
                        if (hasLegacyGame) {
                            OutlinedTextField(legacyName, { legacyName = it }, label = { Text(strings.text("ui.imported_profile_name")) }, enabled = canModify, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { onLegacyPreview(legacyName.trim(), latestVersion) }, enabled = canModify && legacyName.trim().length in 1..48) { Text(strings.text("ui.review_v0_1_import")) }
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val detailsHeight = maxOf(320.dp, maxHeight)
                if (maxWidth < 730.dp) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    list(Modifier.fillMaxWidth().height(180.dp))
                    detail(Modifier.fillMaxWidth().height(detailsHeight))
                } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    list(Modifier.width(300.dp).fillMaxHeight()); detail(Modifier.weight(1f).fillMaxHeight())
                }
            }
            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            copyProgress?.let {
                Text(strings.text("ui.copying_game_data_0_1_files", it.copiedFiles, it.totalFiles))
                LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onCancelCopy) { Text(strings.text("ui.cancel_copying")) }
            }
        }
    }
    preview?.let { request -> AlertDialog(onDismissRequest = onCancelPreview, title = { Text(strings.text("ui.confirm_clone")) },
        text = { Column { Text(strings.text("ui.source_0", request.sourcePath)); Text(strings.text("ui.destination_0", request.destinationPath)); Text(strings.text("ui.0_files_1_to_copy_plus_reserve", request.totalFiles, request.totalBytes.toString() + " B")) } },
        confirmButton = { TextButton(onClick = onConfirmPreview, enabled = canModify) { Text(strings.text("ui.start_copy")) } },
        dismissButton = { TextButton(onClick = onCancelPreview) { Text(strings.text("ui.cancel")) } }) }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit, onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)? = null, profileId: String = "") {
    Column(Modifier.fillMaxWidth().background(if (selected) SyntaxBrand.field else Color.Transparent, RoundedCornerShape(10.dp))
        .border(1.dp, if (selected) SyntaxBrand.cyan else SyntaxBrand.border, RoundedCornerShape(10.dp)).padding(10.dp)) {
        TextButton(onClick = onSelect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        }
        Row {
            onEdit?.let { TextButton(onClick = it, enabled = enabled) { Text(LocalStrings.current.text("profiles.edit")) } }
            onDelete?.let { action ->
                TextButton(onClick = action, enabled = enabled, modifier = Modifier.testTag("delete-profile-$profileId"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(LocalStrings.current.text("profiles.delete"))
                }
            }
        }
    }
}

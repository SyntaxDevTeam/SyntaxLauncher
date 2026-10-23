package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LanguagePicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceCopyEstimate
import pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceCopyProgress
import pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInventory
import pl.syntaxdevteam.syntaxlauncher.core.instance.TrashedInstanceInventory
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchPhase
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionManifest
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferences

private sealed interface ManifestState {
    data object Loading : ManifestState
    data class Ready(val manifest: MinecraftVersionManifest) : ManifestState
    data class Error(val message: String) : ManifestState
}

private sealed interface VersionMetadataState {
    data object Idle : VersionMetadataState
    data object Loading : VersionMetadataState
    data class Ready(val metadata: ResolvedMinecraftVersion) : VersionMetadataState
    data class Error(val message: String) : VersionMetadataState
}

@Composable
fun SyntaxLauncherApp(
    session: DesktopLauncherSession,
    chromeInset: Dp = 0.dp,
    headerDragArea: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
) {
    val strings = LocalStrings.current
    SyntaxTheme {
        var catalog by remember { mutableStateOf<ManifestState>(ManifestState.Loading) }
        var reloadCatalog by remember { mutableIntStateOf(0) }
        var persisted by remember(session) { mutableStateOf<LauncherPreferences?>(null) }
        var settingsError by remember(session) { mutableStateOf<String?>(null) }
        var instanceOpenRequest by remember { mutableIntStateOf(0) }
        var setupOpen by remember { mutableStateOf(false) }

        LaunchedEffect(session) {
            try {
                persisted = session.preferences.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                settingsError = error.message ?: strings.text("ui.cannot_read_launcher_settings")
            }
        }

        LaunchedEffect(reloadCatalog) {
            catalog = ManifestState.Loading
            catalog = try {
                ManifestState.Ready(session.versionCatalog.fetchManifest())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ManifestState.Error(error.message ?: strings.text("ui.could_not_load_minecraft_versions"))
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().border(1.dp, SyntaxBrand.border,
                RoundedCornerShape(10.dp))) {
            val compactShell = maxWidth < 760.dp
            val tinyShell = chromeInset > 0.dp && maxWidth < 560.dp
            Column(modifier = Modifier.fillMaxSize()) {
            headerDragArea {
            Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(if (compactShell) 72.dp else 84.dp)
                    .background(Brush.horizontalGradient(listOf(SyntaxBrand.panel, SyntaxBrand.background)))
                    .padding(start = if (compactShell) 12.dp else 28.dp,
                        end = if (chromeInset > 0.dp) chromeInset else if (compactShell) 16.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Image(
                        painter = rememberBrandingPainter("branding/syntaxlauncher-icon.png"),
                        contentDescription = "SyntaxLauncher",
                        modifier = Modifier.size(if (compactShell) 44.dp else 64.dp),
                    )
                    if (!tinyShell) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "SYNTAX", color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            "L A U N C H E R", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (!compactShell) Spacer(Modifier.weight(1f))
                if (!compactShell) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { instanceOpenRequest = minOf(instanceOpenRequest - 1, -1) },
                        shape = RoundedCornerShape(SyntaxBrand.controlRadius),
                        border = BorderStroke(1.dp, SyntaxBrand.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        BrandIcon(SyntaxSymbol.Home, description = if (compactShell) strings.text("ui.home") else null)
                        if (!compactShell) { Spacer(Modifier.width(10.dp)); Text(strings.text("ui.home")) }
                    }
                    TextButton(onClick = { instanceOpenRequest = maxOf(instanceOpenRequest + 1, 1) }) {
                        BrandIcon(SyntaxSymbol.Instances, tint = SyntaxBrand.heroMuted, description = strings.text("game.my_games"))
                        if (!compactShell) {
                            Spacer(Modifier.width(10.dp))
                            Text(strings.text("game.my_games"), color = SyntaxBrand.heroMuted)
                        }
                    }
                }
                if (!compactShell) Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanguagePicker()
                    IconButton(onClick = { setupOpen = true }, modifier = Modifier.testTag("launcher-settings")) {
                        BrandIcon(SyntaxSymbol.Settings, description = strings.text("ui.your_setup"))
                    }
                }
                if (!compactShell) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("JAVA EDITION   /   v" + LauncherBuildInfo.version,
                            color = SyntaxBrand.heroMuted, style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp)
                        Text(LauncherBuildInfo.buildLabel + "   /   " + LauncherBuildInfo.shortCommit,
                            color = SyntaxBrand.cyan, style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.6.sp)
                    }
                } else {
                    Spacer(Modifier.width(6.dp))
                    Text(LauncherBuildInfo.compactBuildLabel, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = SyntaxBrand.cyan, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (compactShell) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { instanceOpenRequest = minOf(instanceOpenRequest - 1, -1) }) {
                    BrandIcon(SyntaxSymbol.Home)
                    Spacer(Modifier.width(8.dp)); Text(strings.text("ui.home"))
                }
                TextButton(onClick = { instanceOpenRequest = maxOf(instanceOpenRequest + 1, 1) }) {
                    BrandIcon(SyntaxSymbol.Instances)
                    Spacer(Modifier.width(8.dp)); Text(strings.text("game.my_games"))
                }
            }
            }
            }
            if (setupOpen) androidx.compose.material3.AlertDialog(
                onDismissRequest = { setupOpen = false },
                title = { Text(strings.text("ui.your_setup")) },
                text = { LauncherUpdateChannelPicker(session.preferences) },
                confirmButton = { TextButton(onClick = { setupOpen = false }) { Text(strings.text("ui.close")) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Box(modifier = Modifier.weight(1f).padding(horizontal = if (compactShell) 12.dp else 20.dp,
                vertical = 12.dp)) {
                when (val state = catalog) {
                    ManifestState.Loading -> CenterMessage(strings.text("ui.connecting_to_minecraft"), strings.text("ui.loading_official_versions"), true)
                    is ManifestState.Error -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(strings.text("ui.could_not_load_versions"), style = MaterialTheme.typography.headlineMedium)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { reloadCatalog++ }) { Text(strings.text("ui.try_again")) }
                    }
                    is ManifestState.Ready -> when {
                        settingsError != null -> CenterMessage(
                            strings.text("ui.settings_need_attention"),
                            settingsError.orEmpty() + strings.text("ui.your_file_was_preserved"),
                        )
                        persisted == null -> CenterMessage(strings.text("ui.loading_profile"), strings.text("ui.restoring_your_settings"), true)
                        else -> LauncherPanel(state.manifest, session, persisted!!, instanceOpenRequest)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = if (compactShell) 16.dp else 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.text("ui.syntaxdevteam_minecraft_launcher"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.7.sp)
                if (!compactShell) Text(strings.text("ui.built_for_the_adventure"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.7.sp)
            }
            }
            }
        }
    }
}

@Composable
private fun LauncherPanel(
    manifest: MinecraftVersionManifest,
    session: DesktopLauncherSession,
    saved: LauncherPreferences,
    instanceOpenRequest: Int,
) {
    val strings = LocalStrings.current
    var nickname by remember { mutableStateOf(saved.nickname) }
    var selectedPremiumId by remember { mutableStateOf<String?>(null) }
    var signingInPremium by remember { mutableStateOf(false) }
    var loadingAccounts by remember { mutableStateOf(true) }
    var accountReload by remember { mutableIntStateOf(0) }
    var premiumError by remember { mutableStateOf<String?>(null) }
    val premiumAccounts by session.premiumAccounts.collectAsState()
    val preparingAccount by session.preparingAccount.collectAsState()
    LaunchedEffect(session, accountReload) {
        loadingAccounts = true
        premiumError = null
        try {
            session.loadAccounts()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            premiumError = error.message ?: strings.text("ui.microsoft_sign_in_failed")
        } finally {
            loadingAccounts = false
        }
    }
    val selectedPremium = premiumAccounts.firstOrNull { it.uuid.toString() == selectedPremiumId }
    var showSnapshots by remember { mutableStateOf(saved.snapshots) }
    var selected by remember(manifest) {
        mutableStateOf(manifest.versions.firstOrNull { it.id == saved.selectedVersionId }
            ?: manifest.versions.firstOrNull {
                it.id == if (saved.snapshots) manifest.latestSnapshot else manifest.latestRelease
            })
    }
    var menuOpen by remember { mutableStateOf(false) }
    var instanceMenuOpen by remember { mutableStateOf(false) }
    var accountInfoOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var ramMiB by remember { mutableStateOf(saved.maximumMemoryMiB.toFloat()) }
    // Preserve Legacy Vanilla's own settings when switching to UUID-scoped profiles.
    var legacyVersionId by remember { mutableStateOf(saved.selectedVersionId) }
    var legacyRamMiB by remember { mutableStateOf(saved.maximumMemoryMiB.toFloat()) }
    var legacySnapshots by remember { mutableStateOf(saved.snapshots) }
    var reloadMetadata by remember { mutableIntStateOf(0) }
    var metadata by remember { mutableStateOf<VersionMetadataState>(VersionMetadataState.Idle) }
    val scope = rememberCoroutineScope()
    var inventory by remember(session) { mutableStateOf<InstanceInventory?>(null) }
    var trashInventory by remember(session) { mutableStateOf<TrashedInstanceInventory?>(null) }
    var instanceId by remember { mutableStateOf(saved.selectedInstanceId) }
    var managerOpen by remember { mutableStateOf(false) }
    var editingInstance by remember { mutableStateOf<MinecraftInstance?>(null) }
    var managingMods by remember { mutableStateOf<MinecraftInstance?>(null) }
    var modpackImportJob by remember { mutableStateOf<Job?>(null) }
    var browsingModpacks by remember { mutableStateOf(false) }
    var browseModsFirst by remember { mutableStateOf(false) }
    var quickSetupOpen by remember { mutableStateOf(false) }
    var lastVanillaId by remember { mutableStateOf("") }
    var lastModdedId by remember { mutableStateOf(saved.selectedInstanceId) }
    var modsBusy by remember { mutableStateOf(false) }
    var copyPreview by remember { mutableStateOf<InstanceCopyPreview?>(null) }
    var copyProgress by remember { mutableStateOf<InstanceCopyProgress?>(null) }
    var copyJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(instanceOpenRequest) {
        managerOpen = instanceOpenRequest > 0
        if (instanceOpenRequest < 0) { editingInstance = null; copyPreview = null }
    }
    var mutationBusy by remember { mutableStateOf(false) }
    var instanceError by remember { mutableStateOf<String?>(null) }
    val activeInstance = inventory?.instances?.firstOrNull { it.id == instanceId }
    val withMods = activeInstance?.loader?.id in setOf("fabric", "quilt", "neoforge", "forge")
    var localModWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(activeInstance?.id, modsBusy) {
        localModWarnings = emptyList()
        val current = activeInstance
        if (current != null && current.loader?.id in setOf("fabric", "quilt", "neoforge", "forge") && !modsBusy) {
            localModWarnings = try {
                session.modManager.list(current.id).warnings
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                listOf(failure.message ?: strings.text("ui.could_not_inspect_local_mods"))
            }
        }
    }

    val launch by session.coordinator.status.collectAsState()
    val process by session.processManager.status.collectAsState()
    val processProtectionError by session.launchIssue.collectAsState()

    val isRunning = process.state == MinecraftProcessState.RUNNING ||
        process.state == MinecraftProcessState.STARTING
    val locked = isRunning || launch.isPreparing || mutationBusy || modsBusy || signingInPremium || loadingAccounts || preparingAccount
    val validName = selectedPremium != null
    val resolved = (metadata as? VersionMetadataState.Ready)?.metadata
        ?.takeIf { it.id == selected?.id }
    val unsavedInstanceSettings = activeInstance != null &&
        (activeInstance.minecraftVersionId != selected?.id ||
            activeInstance.maximumMemoryMiB != ramMiB.toInt())
    val missingInstance = inventory != null && instanceId.isNotBlank() && activeInstance == null
    val canPlay = !locked && validName && resolved != null &&
        !unsavedInstanceSettings && !missingInstance

    LaunchedEffect(session) {
        try {
            inventory = session.instances.list()
            trashInventory = session.instances.listTrashed()
            inventory?.instances?.firstOrNull { it.id == instanceId }?.let { profile ->
                selected = manifest.versions.firstOrNull { it.id == profile.minecraftVersionId }
                ramMiB = profile.maximumMemoryMiB.toFloat()
                showSnapshots = selected?.type == MinecraftVersionType.SNAPSHOT
            }
            instanceError = (inventory?.problems.orEmpty() + trashInventory?.problems.orEmpty())
                .takeIf { it.isNotEmpty() }?.joinToString("; ")
        }
        catch (error: Exception) { instanceError = error.message ?: strings.text("ui.cannot_load_instances") }
    }

    fun chooseInstance(id: String) {
        if (instanceId.isBlank()) {
            legacyVersionId = selected?.id.orEmpty()
            legacyRamMiB = ramMiB
            legacySnapshots = showSnapshots
        }
        instanceId = id
        val target = inventory?.instances?.firstOrNull { it.id == id }
        if (id.isBlank() || target?.loader == null) lastVanillaId = id
        else if (target.loader?.id in setOf("fabric", "quilt", "neoforge", "forge")) lastModdedId = id
        if (id.isBlank()) {
            selected = manifest.versions.firstOrNull { it.id == legacyVersionId }
                ?: manifest.versions.firstOrNull {
                    it.id == if (legacySnapshots) manifest.latestSnapshot else manifest.latestRelease
                }
            ramMiB = legacyRamMiB
            showSnapshots = legacySnapshots
        } else {
            inventory?.instances?.firstOrNull { it.id == id }?.let { instance ->
                selected = manifest.versions.firstOrNull { it.id == instance.minecraftVersionId }
                ramMiB = instance.maximumMemoryMiB.toFloat()
                showSnapshots = selected?.type == MinecraftVersionType.SNAPSHOT
            }
        }
    }

    fun selectVanillaMode() {
        if (locked || inventory == null || !withMods) return
        lastModdedId = instanceId
        val previous = inventory?.instances?.firstOrNull { it.id == lastVanillaId && it.loader == null }
        chooseInstance(previous?.id.orEmpty())
    }

    fun selectModdedMode() {
        if (locked || inventory == null || withMods) return
        lastVanillaId = instanceId
        val profiles = inventory?.instances.orEmpty().filter { it.loader?.id in setOf("fabric", "quilt", "neoforge", "forge") }
        val existing = profiles.firstOrNull { it.id == lastModdedId }
            ?: profiles.firstOrNull { it.minecraftVersionId == selected?.id }
            ?: profiles.firstOrNull()
        if (existing != null) chooseInstance(existing.id) else quickSetupOpen = true
    }

    fun modifyInstance(action: suspend () -> MinecraftInstance?): Job? {
        if (locked) return null
        mutationBusy = true
        return scope.launch {
            try {
                val newSelection = action()
                inventory = session.instances.list()
                trashInventory = session.instances.listTrashed()
                instanceError = (inventory?.problems.orEmpty() + trashInventory?.problems.orEmpty())
                    .takeIf { it.isNotEmpty() }?.joinToString("; ")
                if (newSelection != null) chooseInstance(newSelection.id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                instanceError = error.message ?: strings.text("ui.instance_operation_failed")
            } finally { mutationBusy = false }
        }
    }

    fun previewCopy(from: MinecraftInstance?, name: String, version: String) {
        if (locked || copyPreview != null) return
        mutationBusy = true
        scope.launch {
            try {
                val estimate: InstanceCopyEstimate = if (from == null) {
                    session.instances.estimateLegacyImport()
                } else session.instances.estimateClone(from.id)
                copyPreview = InstanceCopyPreview(
                    from = from,
                    name = name,
                    minecraftVersionId = version,
                    sourcePath = (from?.let(session.instances::gameDirectory)
                        ?: session.instances.legacyGameDirectory()).toString(),
                    destinationPath = session.instances.instancesDirectory()
                        .resolve("<new UUID>/game").toString(),
                    totalBytes = estimate.totalBytes,
                    totalFiles = estimate.fileCount,
                )
                instanceError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                instanceError = error.message ?: strings.text("ui.could_not_inspect_the_game_data")
            } finally {
                mutationBusy = false
            }
        }
    }

    fun startCopy() {
        if (locked) return
        val request = copyPreview ?: return
        copyPreview = null
        mutationBusy = true
        copyProgress = InstanceCopyProgress(0L, request.totalBytes, 0, request.totalFiles)
        copyJob = scope.launch {
            try {
                var lastShown = 0L
                val onProgress: (InstanceCopyProgress) -> Unit = { update ->
                    val now = System.nanoTime()
                    if (update.copiedFiles == update.totalFiles || update.copiedBytes == 0L ||
                        now - lastShown >= 120_000_000L) {
                        lastShown = now
                        scope.launch(Dispatchers.Swing) {
                            if (mutationBusy) copyProgress = update
                        }
                    }
                }
                val imported = if (request.from != null) {
                    session.instances.cloneInstance(request.from.id, request.name, onProgress)
                } else {
                    session.instances.importLegacy(request.name, request.minecraftVersionId,
                        maximumMemoryMiB = ramMiB.toInt(), onProgress = onProgress)
                }
                inventory = session.instances.list()
                trashInventory = session.instances.listTrashed()
                chooseInstance(imported.id)
                instanceError = null
            } catch (_: CancellationException) {
                instanceError = strings.text("ui.copy_cancelled_original_files_were_preserved")
            } catch (error: Exception) {
                instanceError = error.message ?: strings.text("ui.copy_failed_original_files_were_preserved")
            } finally {
                mutationBusy = false
                copyProgress = null
                copyJob = null
            }
        }
    }

    LaunchedEffect(nickname, selected?.id, showSnapshots, ramMiB, instanceId) {
        if (nickname.isBlank() || OfflineAccount.isValidUsername(nickname.trim())) {
            session.rememberPreferences(
                LauncherPreferences(
                    nickname = nickname.trim(),
                    selectedVersionId = if (instanceId.isBlank()) selected?.id.orEmpty() else legacyVersionId,
                    snapshots = if (instanceId.isBlank()) showSnapshots else legacySnapshots,
                    maximumMemoryMiB = if (instanceId.isBlank()) ramMiB.toInt() else legacyRamMiB.toInt(),
                    selectedInstanceId = instanceId,
                ),
            )
        }
    }

    LaunchedEffect(selected?.id, reloadMetadata) {
        val version = selected
        if (version == null) {
            metadata = VersionMetadataState.Idle
            return@LaunchedEffect
        }
        metadata = VersionMetadataState.Loading
        metadata = try {
            VersionMetadataState.Ready(session.versionResolver.resolve(version))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            VersionMetadataState.Error(error.message ?: strings.text("ui.could_not_resolve_this_version"))
        }
    }
    LaunchedEffect(locked) {
        if (locked) { menuOpen = false; instanceMenuOpen = false }
    }

    if (quickSetupOpen) {
        val baseName = strings.text("game.default_name")
        val names = inventory?.instances.orEmpty().map { it.name }.toSet()
        val suggestedName = generateSequence(1) { it + 1 }
            .map { if (it == 1) baseName else "$baseName $it" }.first { it !in names }
        QuickModdedGameDialog(
            preferredVersion = selected?.id ?: manifest.latestRelease,
            releases = manifest.versions.filter { it.type == MinecraftVersionType.RELEASE }.map { it.id },
            defaultName = suggestedName,
            loadVersions = { id, game -> session.loaders?.compatibleVersions(id, game).orEmpty() },
            findCompatibleGame = { id, game -> session.loaders?.compatibleGame(
                id, game, manifest.versions.sortedByDescending { it.releaseTime }.map { it.id }) },
            create = { name, game, loader ->
                session.instances.create(name, game, maximumMemoryMiB = ramMiB.toInt(), loader = loader)
            },
            onCreated = { created ->
                val previous = inventory ?: InstanceInventory(emptyList(), emptyList())
                inventory = previous.copy(instances = (previous.instances + created).sortedBy { it.name.lowercase() })
                chooseInstance(created.id)
                quickSetupOpen = false
                browseModsFirst = true
                managingMods = created
            },
            onDismiss = { quickSetupOpen = false },
        )
    }

    val editing = editingInstance
    if (editing != null) {
        InstanceSettingsDialog(
            instance = editing,
            availableVersions = manifest.versions.map { it.id },
            loadLoaderVersions = { id, game -> session.loaders?.compatibleVersions(id, game).orEmpty() },
            findCompatibleGame = { id, game -> session.loaders?.compatibleGame(
                id, game, manifest.versions.sortedByDescending { it.releaseTime }.map { it.id }) },
            onSave = { updated ->
                modifyInstance { session.instances.update(updated) }
                editingInstance = null
            },
            onCancel = { editingInstance = null },
        )
    } else if (managerOpen) {
        InstanceWorkspace(
            instances = inventory?.instances.orEmpty(),
            trashed = trashInventory?.entries.orEmpty(),
            selectedId = instanceId,
            selected = activeInstance,
            canModify = !locked,
            isBusy = mutationBusy,
            isLoadingInventory = inventory == null,
            hasLegacyGame = session.instances.legacyGameAvailable(),
            // Import an existing legacy world with its selected Minecraft version by default,
            // not an unrelated newer release that might convert world data.
            latestVersion = legacyVersionId.ifBlank { manifest.latestRelease },
            availableVersions = manifest.versions.map { it.id },
            loadLoaderVersions = { id, game -> session.loaders?.compatibleVersions(id, game).orEmpty() },
            findCompatibleGame = { id, game -> session.loaders?.compatibleGame(
                id, game, manifest.versions.sortedByDescending { it.releaseTime }.map { it.id }) },
            error = instanceError,
            preview = copyPreview,
            copyProgress = copyProgress,
            onSelect = ::chooseInstance,
            onCreate = { name, version, loader -> modifyInstance {
                session.instances.create(name, version, maximumMemoryMiB = ramMiB.toInt(), loader = loader)
            } },
            onEdit = { instance -> editingInstance = instance },
            onManageMods = { instance -> browseModsFirst = false; managingMods = instance },
            onClonePreview = { instance, name ->
                previewCopy(instance, name, instance.minecraftVersionId)
            },
            onLegacyPreview = { name, version -> previewCopy(null, name, version) },
            onCancelPreview = { copyPreview = null },
            onConfirmPreview = ::startCopy,
            onCancelCopy = { copyJob?.cancel() },
            onDelete = { instance -> modifyInstance {
                session.instances.moveToTrash(instance.id)
                if (instanceId == instance.id) chooseInstance("")
                null
            } },
            onRestore = { entry -> modifyInstance {
                session.instances.restoreFromTrash(entry.entryName)
            } },
            onDismiss = { managerOpen = false; copyPreview = null },
            onCancelImport = if (mutationBusy && modpackImportJob?.isActive == true) ({ modpackImportJob?.cancel() }) else null,
            onImportModpack = {
                if (!locked && !mutationBusy) {
                    val picker = javax.swing.JFileChooser().apply {
                        dialogTitle = strings.text("modpack.import_file")
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                            strings.text("modpack.file_description"), "mrpack")
                        isAcceptAllFileFilterUsed = false
                    }
                    if (picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val source = picker.selectedFile.toPath()
                        modpackImportJob = modifyInstance {
                            requireNotNull(session.modpackImporter).install(source)
                        }
                    }
                }
            },
        )
    }

    fun selectInstalledPack(created: MinecraftInstance) {
        scope.launch {
            inventory = session.instances.list()
            chooseInstance(created.id)
            browsingModpacks = false
            managingMods = null
        }
    }
    if (browsingModpacks) ModpackCatalogDialog(session, canModify = !isRunning && !launch.isPreparing && !mutationBusy,
        onBusyChange = { modsBusy = it }, onInstalled = ::selectInstalledPack,
        onDismiss = { if (!modsBusy) browsingModpacks = false })

    managingMods?.let { target ->
        if (browseModsFirst) ContentCatalogDialog(target, session,
            canModify = !isRunning && !launch.isPreparing && !mutationBusy,
            onBusyChange = { modsBusy = it }, onManage = { browseModsFirst = false },
            onInstanceInstalled = ::selectInstalledPack,
            onDismiss = { if (!modsBusy) managingMods = null })
        else LocalModsDialog(
            instance = target,
            initialBrowse = browseModsFirst,
            manager = session.modManager,
            catalog = session.modrinthCatalog,
            installer = session.modrinthInstaller,
            canModify = !isRunning && !launch.isPreparing && !mutationBusy,
            onBusyChange = { modsBusy = it },
            onDismiss = { if (!modsBusy) managingMods = null },
        )
    }

    if (diagnosticsOpen) MinecraftDiagnosticsDialog(session, onDismiss = { diagnosticsOpen = false })

    if (accountInfoOpen) {
        AlertDialog(onDismissRequest = { accountInfoOpen = false },
            title = { Text(strings.text("ui.player_accounts")) },
            text = { Text(strings.text("account.sign_in_required") + "\n\n" +
                strings.text("account.privacy_summary")) },
            confirmButton = { TextButton(onClick = { accountInfoOpen = false }) { Text(strings.text("ui.got_it")) } })
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val stacked = maxWidth < 760.dp
    val shortWindow = maxHeight < 440.dp
    val scrollPage = stacked || shortWindow
    val homeScroll = rememberScrollState()
    val sidebarWidth = if (maxWidth < 1100.dp) 310.dp else 354.dp
    val contentHeight = maxOf(maxHeight, 440.dp)
    val leftContent: @Composable (Modifier) -> Unit = { columnModifier ->
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(SyntaxBrand.panelGap),
        ) {
            LauncherHero(selected?.id, activeInstance?.name, showSnapshots,
                Modifier.weight(1f).fillMaxWidth(), modded = withMods)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(SyntaxBrand.panelRadius))
                    .background(Brush.linearGradient(listOf(SyntaxBrand.panelHighlight, SyntaxBrand.panel)))
                    .border(1.dp, SyntaxBrand.border,
                        RoundedCornerShape(SyntaxBrand.panelRadius))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    BrandIcon(SyntaxSymbol.Gamepad, size = 30.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            when {
                                isRunning -> strings.text("ui.you_re_in_the_game")
                                launch.isPreparing -> strings.text("ui.getting_everything_ready")
                                launch.phase == LaunchPhase.FAILED -> strings.text("ui.preparation_needs_attention")
                                process.state == MinecraftProcessState.CRASHED -> strings.text("ui.minecraft_closed_unexpectedly")
                                selectedPremium == null -> strings.text("account.sign_in")
                                else -> strings.text("ui.ready_when_you_are")
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                isRunning -> strings.text("ui.minecraft_is_running_in_a_separate_window")
                                launch.isPreparing -> strings.text(when (launch.phase) {
                                    LaunchPhase.PREPARING_LOADER -> "launch.loader"
                                    LaunchPhase.PREPARING_GAME -> "launch.game"
                                    LaunchPhase.PREPARING_JAVA -> "launch.java"
                                    LaunchPhase.BUILDING_PLAN -> "launch.plan"
                                    else -> "launch.start"
                                })
                                launch.phase == LaunchPhase.FAILED -> launch.detail
                                else -> strings.text("ui.your_game_files_and_java_are_managed_automatically")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SurfaceTag(
                        when {
                            isRunning -> strings.text("state.running")
                            launch.isPreparing -> strings.text("state.preparing")
                            launch.phase == LaunchPhase.FAILED ||
                                process.state == MinecraftProcessState.CRASHED -> strings.text("state.error")
                            selectedPremium != null -> "MICROSOFT"
                            else -> strings.text("account.sign_in")
                        },
                        when {
                            launch.phase == LaunchPhase.FAILED ||
                                process.state == MinecraftProcessState.CRASHED -> MaterialTheme.colorScheme.error
                            launch.isPreparing -> MaterialTheme.colorScheme.tertiary
                            isRunning -> MaterialTheme.colorScheme.primary
                            else -> SyntaxBrand.success
                        },
                    )
                }
                if (launch.isPreparing || preparingAccount) {
                    val fraction = launch.currentFileProgress
                    if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    if (launch.totalFiles > 0) {
                        Text(strings.text("ui.0_1_files", launch.completedFiles, launch.totalFiles),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { resolved?.let {
                        session.play(it, nickname.trim(), ramMiB.toInt(), activeInstance,
                            premiumId = selectedPremiumId)
                    } },
                    enabled = canPlay,
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                        .background(Brush.horizontalGradient(
                            if (canPlay) listOf(SyntaxBrand.playStart, SyntaxBrand.blue)
                            else listOf(SyntaxBrand.field, SyntaxBrand.field),
                        ), RoundedCornerShape(14.dp))
                        .border(1.dp, if (canPlay) SyntaxBrand.playBorder else SyntaxBrand.fieldBorder,
                            RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = SyntaxBrand.heroMuted.copy(alpha = 0.55f),
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (canPlay) {
                        BrandIcon(SyntaxSymbol.Play, tint = MaterialTheme.colorScheme.onPrimary, size = 22.dp)
                        Spacer(Modifier.width(24.dp))
                    }
                    Text(
                        when {
                            launch.isPreparing || preparingAccount -> strings.text("ui.preparing_minecraft")
                            isRunning -> strings.text("ui.minecraft_is_running")
                            else -> strings.text("ui.play_minecraft")
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.8.sp,
                        fontSize = 16.sp,
                    )
                }
                if (launch.isPreparing || preparingAccount) {
                    OutlinedButton(onClick = session::cancelPreparation, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.text("ui.cancel_preparation"))
                    }
                } else if (isRunning) {
                    OutlinedButton(onClick = session::stopGame, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.text("ui.stop_minecraft"))
                    }
                }
            }
        }
    }
    val sidebarContent: @Composable (Modifier) -> Unit = { sidebarModifier ->
        val sidebarScroll = rememberScrollState()
        Box(
            modifier = sidebarModifier
                .clip(RoundedCornerShape(SyntaxBrand.panelRadius))
                .background(Brush.linearGradient(listOf(SyntaxBrand.panelHighlight, SyntaxBrand.panel)))
                .border(1.dp, SyntaxBrand.border,
                    RoundedCornerShape(SyntaxBrand.panelRadius)),
        ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(sidebarScroll)
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                SectionLabel(strings.text("game.style"), SyntaxSymbol.Gamepad)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelButton(strings.text("game.vanilla"), !withMods, !locked && inventory != null,
                        ::selectVanillaMode, Modifier.weight(1f).testTag("game-style-vanilla"))
                    ChannelButton(strings.text("game.with_mods"), withMods, !locked && inventory != null,
                        ::selectModdedMode, Modifier.weight(1f).testTag("game-style-modded"))
                }
                Box {
                    SelectorButton(activeInstance?.name ?: if (missingInstance) strings.text("ui.unavailable_instance") else strings.text("game.vanilla_game"),
                        onClick = { instanceMenuOpen = true }, enabled = !locked,
                        symbol = SyntaxSymbol.Cube)
                    DropdownMenu(expanded = instanceMenuOpen && !locked,
                        onDismissRequest = { instanceMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(strings.text("game.vanilla_game")) },
                            onClick = { chooseInstance(""); instanceMenuOpen = false })
                        inventory?.instances.orEmpty().forEach { profile ->
                            DropdownMenuItem(text = { Text(profile.name + " · " + profile.id.take(8)) },
                                onClick = { chooseInstance(profile.id); instanceMenuOpen = false })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    if (activeInstance != null) TextButton(onClick = {
                        browseModsFirst = true
                        managingMods = activeInstance
                    }, enabled = !locked, modifier = Modifier.weight(1f)) { Text(strings.text("catalog.open")) }
                    else if (session.modpackCatalog == null) Text(strings.text("game.vanilla_hint"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (session.modpackCatalog != null) TextButton(onClick = { browsingModpacks = true },
                        enabled = !locked, modifier = Modifier.weight(1f).testTag("browse-modpacks")) { Text(strings.text("catalog.modpacks")) }
                    TextButton(onClick = { managerOpen = true }, enabled = !locked,
                        modifier = Modifier.weight(1f).testTag("manage-games")) { Text(strings.text("game.my_games")) }
                }
                if (missingInstance) {
                    Text(strings.text("ui.selected_instance_is_unavailable_choose_a_valid_instance_or_le"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (unsavedInstanceSettings) {
                    Text(strings.text("ui.instance_settings_differ_from_the_saved_profile_re_select_it_o"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                (processProtectionError ?: instanceError)?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
                if (localModWarnings.isNotEmpty()) {
                    Text(strings.text("mods.attention", localModWarnings.size),
                        color = SyntaxBrand.warning, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        browseModsFirst = false
                        managingMods = activeInstance
                    }, enabled = !locked) { Text(strings.text("mods.review")) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(strings.text("ui.player"), SyntaxSymbol.Player)
                    IconButton(onClick = { accountInfoOpen = true }, modifier = Modifier.size(24.dp)) {
                        BrandIcon(SyntaxSymbol.Info, size = 17.dp, tint = SyntaxBrand.heroMuted,
                            description = strings.text("ui.account_information"))
                    }
                }
                if (session.microsoftEnabled || premiumAccounts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (session.microsoftEnabled) {
                        OutlinedButton(
                            onClick = {
                                signingInPremium = true
                                premiumError = null
                                scope.launch {
                                    try {
                                        val account = session.addPremiumAccount()
                                        selectedPremiumId = account.uuid.toString()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        premiumError = error.message ?: strings.text("ui.microsoft_sign_in_failed")
                                    } finally {
                                        signingInPremium = false
                                    }
                                }
                            },
                            enabled = !locked,
                        ) { Text(if (signingInPremium) strings.text("ui.signing_in") else strings.text("ui.add_microsoft")) }
                    }
                }
                if (selectedPremiumId == null) {
                    Text(strings.text("account.sign_in_required"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(selectedPremium?.username ?: strings.text("ui.this_microsoft_session_is_unavailable"))
                    Text(strings.text("ui.verified_minecraft_java_profile_microsoft"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                premiumAccounts.forEach { profile ->
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        FilterChip(
                            selected = selectedPremiumId == profile.uuid.toString(),
                            onClick = { selectedPremiumId = profile.uuid.toString() },
                            label = { Text(profile.username) },
                            enabled = !locked,
                        )
                        TextButton(onClick = {
                            signingInPremium = true
                            premiumError = null
                            scope.launch {
                                try {
                                    session.removePremiumAccount(profile.uuid.toString())
                                    if (selectedPremiumId == profile.uuid.toString()) selectedPremiumId = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    premiumError = error.message ?: strings.text("ui.microsoft_sign_in_failed")
                                } finally {
                                    signingInPremium = false
                                }
                            }
                        }, enabled = !locked) { Text(strings.text("ui.sign_out")) }
                    }
                }
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { uriHandler.openUri("https://syntaxdevteam.pl/p/syntaxlauncher-prywatnosc") }) {
                        Text(strings.text("legal.privacy"))
                    }
                    TextButton(onClick = { uriHandler.openUri("https://syntaxdevteam.pl/p/syntaxlauncher-warunki-uzytkowania") }) {
                        Text(strings.text("legal.terms"))
                    }
                }
                premiumError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { accountReload++ }, enabled = !locked) {
                        Text(strings.text("ui.retry"))
                    }
                }
            }
            Text(strings.text("legal.disclaimer"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("SyntaxDevTeam · wieszczy85@gmail.com", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(strings.text("ui.game_version"), SyntaxSymbol.Cube)
                if (activeInstance != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Minecraft " + activeInstance.minecraftVersionId)
                        Text("${ramMiB.toInt() / 1024} GB", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { editingInstance = activeInstance }, enabled = !locked) {
                        Text(strings.text("game.settings"))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChannelButton(strings.text("ui.releases"), modifier = Modifier.weight(1f),
                            selected = !showSnapshots, enabled = !locked,
                            onClick = {
                                showSnapshots = false
                                selected = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                            },
                        )
                        ChannelButton(strings.text("ui.snapshots"), modifier = Modifier.weight(1f),
                            selected = showSnapshots, enabled = !locked,
                            onClick = {
                                showSnapshots = true
                                selected = manifest.versions.firstOrNull { it.id == manifest.latestSnapshot }
                            },
                        )
                    }
                    Box {
                        SelectorButton(selected?.id ?: strings.text("ui.select_version"),
                            onClick = { menuOpen = true }, enabled = !locked)
                        DropdownMenu(
                            expanded = menuOpen && !locked,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            manifest.versions.asSequence()
                                .filter { it.type == if (showSnapshots)
                                    MinecraftVersionType.SNAPSHOT else MinecraftVersionType.RELEASE }
                                .take(80)
                                .forEach { version ->
                                    DropdownMenuItem(
                                        text = { VersionLabel(version, manifest) },
                                        onClick = { selected = version; menuOpen = false },
                                    )
                                }
                        }
                    }
                }
                when (val state = metadata) {
                    VersionMetadataState.Loading ->
                        Text(strings.text("ui.verifying_version"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    is VersionMetadataState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { reloadMetadata++ }, enabled = !locked) {
                            Text(strings.text("ui.retry"))
                        }
                    }
                    is VersionMetadataState.Ready -> if (activeInstance == null)
                        Text(
                            strings.text("ui.verified") +
                                (state.metadata.javaRequirement?.let { "Java ${it.majorVersion}" }
                                    ?: strings.text("ui.java_not_declared")),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    VersionMetadataState.Idle -> Unit
                }
            }
            if (activeInstance == null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionLabel(strings.text("ui.memory_allocation"), SyntaxSymbol.Memory)
                    Text("${ramMiB.toInt() / 1024} GB", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = ramMiB, onValueChange = { ramMiB = it },
                    valueRange = 1024f..16384f, steps = 14,
                    modifier = Modifier.height(32.dp),
                    enabled = !locked,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("1 GB", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("16 GB", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Text(
                when (process.state) {
                    MinecraftProcessState.RUNNING -> strings.text("ui.running_pid_0", process.pid ?: "?")
                    MinecraftProcessState.STOPPED -> strings.text("ui.last_exit_code_0", process.exitCode ?: "?")
                    MinecraftProcessState.CRASHED -> process.errorMessage ?: strings.text("ui.minecraft_crashed")
                    else -> if (selectedPremium != null) strings.text("ui.microsoft_account") + selectedPremium.username
                        else strings.text("account.sign_in_required")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (process.state == MinecraftProcessState.CRASHED ||
                launch.phase == LaunchPhase.FAILED) {
                TextButton(
                    onClick = { diagnosticsOpen = true },
                    enabled = !locked,
                ) { Text(strings.text("diagnostics.open")) }
                process.stderrLog?.parent?.let {
                    Text(strings.text("ui.logs_0", it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(sidebarScroll),
            modifier = Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight().width(9.dp).padding(vertical = 15.dp, horizontal = 2.dp),
        )
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (stacked) {
            Column(Modifier.fillMaxSize().verticalScroll(homeScroll).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(SyntaxBrand.panelGap)) {
                leftContent(Modifier.fillMaxWidth().height(590.dp))
                sidebarContent(Modifier.fillMaxWidth().height(690.dp))
            }
        } else {
            Row(Modifier.fillMaxSize().then(if (scrollPage) Modifier.verticalScroll(homeScroll)
                .padding(end = 10.dp) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(SyntaxBrand.panelGap)) {
                leftContent(Modifier.weight(1f).height(contentHeight))
                sidebarContent(Modifier.width(sidebarWidth).height(contentHeight))
            }
        }
        if (scrollPage) VerticalScrollbar(rememberScrollbarAdapter(homeScroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp))
    }
    }
}

@Composable
private fun VersionLabel(version: MinecraftVersion, manifest: MinecraftVersionManifest) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(version.id)
        if (version.id == manifest.latestRelease || version.id == manifest.latestSnapshot) {
            Text(strings.text("ui.latest"), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SurfaceTag(label: String, color: Color) {
    Row(
        modifier = Modifier.background(SyntaxBrand.field, RoundedCornerShape(10.dp))
            .border(1.dp, SyntaxBrand.fieldBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, color = SyntaxBrand.heroMuted, style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenterMessage(title: String, description: String, loading: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(18.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

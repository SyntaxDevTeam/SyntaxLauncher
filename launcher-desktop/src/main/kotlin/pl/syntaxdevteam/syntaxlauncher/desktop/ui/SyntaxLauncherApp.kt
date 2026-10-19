package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
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
fun SyntaxLauncherApp(session: DesktopLauncherSession) {
    SyntaxTheme {
        var catalog by remember { mutableStateOf<ManifestState>(ManifestState.Loading) }
        var reloadCatalog by remember { mutableIntStateOf(0) }
        var persisted by remember(session) { mutableStateOf<LauncherPreferences?>(null) }
        var settingsError by remember(session) { mutableStateOf<String?>(null) }
        var instanceOpenRequest by remember { mutableIntStateOf(0) }

        LaunchedEffect(session) {
            try {
                persisted = session.preferences.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                settingsError = error.message ?: "Cannot read launcher settings."
            }
        }

        LaunchedEffect(reloadCatalog) {
            catalog = ManifestState.Loading
            catalog = try {
                ManifestState.Ready(session.versionCatalog.fetchManifest())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ManifestState.Error(error.message ?: "Could not load Minecraft versions.")
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Image(
                        painter = painterResource("branding/syntaxlauncher-icon.png"),
                        contentDescription = "SyntaxLauncher",
                        modifier = Modifier.size(64.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "HOME",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        )
                    }
                    TextButton(onClick = { instanceOpenRequest++ }) {
                        Text("Instances", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(22.dp))
                    Text(
                        "JAVA EDITION   /   v0.2 PREVIEW",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Box(modifier = Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 18.dp)) {
                when (val state = catalog) {
                    ManifestState.Loading -> CenterMessage("Connecting to Minecraft", "Loading official versions…", true)
                    is ManifestState.Error -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Could not load versions", style = MaterialTheme.typography.headlineMedium)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { reloadCatalog++ }) { Text("Try again") }
                    }
                    is ManifestState.Ready -> when {
                        settingsError != null -> CenterMessage(
                            "Settings need attention",
                            settingsError.orEmpty() + " — your file was preserved.",
                        )
                        persisted == null -> CenterMessage("Loading profile", "Restoring your settings…", true)
                        else -> LauncherPanel(state.manifest, session, persisted!!, instanceOpenRequest)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("SYNTAXDEVTEAM   /   MINECRAFT LAUNCHER", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.7.sp)
                Text("BUILT FOR THE ADVENTURE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.7.sp)
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
    var nickname by remember { mutableStateOf(saved.nickname) }
    var showSnapshots by remember { mutableStateOf(saved.snapshots) }
    var selected by remember(manifest) {
        mutableStateOf(manifest.versions.firstOrNull { it.id == saved.selectedVersionId }
            ?: manifest.versions.firstOrNull {
                it.id == if (saved.snapshots) manifest.latestSnapshot else manifest.latestRelease
            })
    }
    var menuOpen by remember { mutableStateOf(false) }
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
    var copyPreview by remember { mutableStateOf<InstanceCopyPreview?>(null) }
    var copyProgress by remember { mutableStateOf<InstanceCopyProgress?>(null) }
    var copyJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(instanceOpenRequest) {
        if (instanceOpenRequest > 0) managerOpen = true
    }
    var mutationBusy by remember { mutableStateOf(false) }
    var instanceError by remember { mutableStateOf<String?>(null) }
    val activeInstance = inventory?.instances?.firstOrNull { it.id == instanceId }
    val launch by session.coordinator.status.collectAsState()
    val process by session.processManager.status.collectAsState()
    val processProtectionError by session.launchIssue.collectAsState()

    val isRunning = process.state == MinecraftProcessState.RUNNING ||
        process.state == MinecraftProcessState.STARTING
    val locked = isRunning || launch.isPreparing || mutationBusy
    val validName = nickname.isNotBlank() && OfflineAccount.isValidUsername(nickname.trim())
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
        catch (error: Exception) { instanceError = error.message ?: "Cannot load instances" }
    }

    fun chooseInstance(id: String) {
        if (instanceId.isBlank()) {
            legacyVersionId = selected?.id.orEmpty()
            legacyRamMiB = ramMiB
            legacySnapshots = showSnapshots
        }
        instanceId = id
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

    fun modifyInstance(action: suspend () -> MinecraftInstance?) {
        if (locked) return
        mutationBusy = true
        scope.launch {
            try {
                val newSelection = action()
                inventory = session.instances.list()
                trashInventory = session.instances.listTrashed()
                instanceError = (inventory?.problems.orEmpty() + trashInventory?.problems.orEmpty())
                    .takeIf { it.isNotEmpty() }?.joinToString("; ")
                if (newSelection != null) chooseInstance(newSelection.id)
            } catch (error: Exception) {
                instanceError = error.message ?: "Instance operation failed"
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
                instanceError = error.message ?: "Could not inspect the game data."
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
                instanceError = "Copy cancelled. Original files were preserved."
            } catch (error: Exception) {
                instanceError = error.message ?: "Copy failed; original files were preserved."
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
            VersionMetadataState.Error(error.message ?: "Could not resolve this version.")
        }
    }
    LaunchedEffect(locked) {
        if (locked) menuOpen = false
    }

    val editing = editingInstance
    if (editing != null) {
        InstanceSettingsDialog(
            instance = editing,
            availableVersions = manifest.versions.map { it.id },
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
            error = instanceError,
            preview = copyPreview,
            copyProgress = copyProgress,
            onSelect = ::chooseInstance,
            onCreate = { name, version -> modifyInstance {
                session.instances.create(name, version, maximumMemoryMiB = ramMiB.toInt())
            } },
            onEdit = { instance -> editingInstance = instance },
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
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val narrowLayout = maxWidth < 1050.dp
    val sidebarWidth = if (narrowLayout) 306.dp else 342.dp
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(if (narrowLayout) 14.dp else 18.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SyntaxBrand.heroEnd)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        RoundedCornerShape(24.dp)),
            ) {
                val compactHero = maxHeight < 405.dp || maxWidth < 650.dp
                // Cropped from the approved concept: actual cinematic voxel artwork.
                // The left-side text baked into the reference is fully masked in this asset.
                Image(
                    painter = painterResource("branding/hero-landscape.jpg"),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                // Secondary Compose scrim gives the content a stable reading surface.
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                    0f to Color(0x99051123),
                    0.48f to Color(0x4C07182E),
                    1f to Color.Transparent,
                )))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.75f to Color.Transparent,
                    1f to Color(0x99071324),
                )))
                Column(
                    modifier = Modifier.fillMaxSize().padding(if (compactHero) 24.dp else 34.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(if (compactHero) 6.dp else 12.dp)) {
                        SurfaceTag("VANILLA MINECRAFT", MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.height(if (compactHero) 2.dp else 8.dp))
                        Text(
                            "Your world.",
                            fontSize = if (compactHero) 35.sp else 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = if (compactHero) 40.sp else 54.sp,
                            letterSpacing = (-1.2).sp,
                            color = SyntaxBrand.heroText,
                        )
                        Text(
                            "Your adventure.",
                            fontSize = if (compactHero) 35.sp else 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = if (compactHero) 40.sp else 54.sp,
                            letterSpacing = (-1.2).sp,
                            color = Color(0xFF69CCFA),
                        )
                        Text(
                            "No distractions. Just you and Minecraft.",
                            color = SyntaxBrand.heroMuted,
                            style = if (compactHero) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(if (compactHero) 8.dp else 12.dp)) {
                        Text(
                            activeInstance?.name?.uppercase() ?: "LEGACY VANILLA PROFILE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SyntaxBrand.heroMuted,
                            letterSpacing = 1.6.sp,
                        )
                        Text(
                            selected?.id ?: "Choose a Minecraft version",
                            style = if (compactHero) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        HorizontalDivider(color = Color(0xFF72B4E6).copy(alpha = 0.32f))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("JAVA EDITION", style = MaterialTheme.typography.labelSmall,
                                color = SyntaxBrand.heroMuted)
                            Text("•", color = SyntaxBrand.heroMuted)
                            Text(if (showSnapshots) "SNAPSHOT CHANNEL" else "STABLE RELEASE",
                                style = MaterialTheme.typography.labelSmall, color = SyntaxBrand.heroMuted)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when {
                                isRunning -> "You're in the game"
                                launch.isPreparing -> "Getting everything ready"
                                launch.phase == LaunchPhase.FAILED -> "Preparation needs attention"
                                process.state == MinecraftProcessState.CRASHED -> "Minecraft closed unexpectedly"
                                else -> "Ready when you are"
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                isRunning -> "Minecraft is running in a separate window."
                                launch.isPreparing -> launch.detail
                                launch.phase == LaunchPhase.FAILED -> launch.detail
                                else -> "Your game files and Java are managed automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SurfaceTag(
                        when {
                            isRunning -> "RUNNING"
                            launch.isPreparing -> "PREPARING"
                            launch.phase == LaunchPhase.FAILED ||
                                process.state == MinecraftProcessState.CRASHED -> "ERROR"
                            else -> "OFFLINE"
                        },
                        when {
                            launch.phase == LaunchPhase.FAILED ||
                                process.state == MinecraftProcessState.CRASHED -> MaterialTheme.colorScheme.error
                            launch.isPreparing -> MaterialTheme.colorScheme.tertiary
                            isRunning -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (launch.isPreparing) {
                    val fraction = launch.currentFileProgress
                    if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    if (launch.totalFiles > 0) {
                        Text("${launch.completedFiles} / ${launch.totalFiles} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { resolved?.let {
                        session.play(it, nickname.trim(), ramMiB.toInt(), activeInstance)
                    } },
                    enabled = canPlay,
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                        .background(Brush.horizontalGradient(
                            listOf(Color(0xFF57D3FF), Color(0xFF1CAAF6)),
                        ), RoundedCornerShape(15.dp))
                        .border(1.dp, Color(0xFF90E8FF), RoundedCornerShape(15.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF06172D),
                    ),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(
                        when {
                            launch.isPreparing -> "PREPARING MINECRAFT"
                            isRunning -> "MINECRAFT IS RUNNING"
                            else -> "▶     PLAY MINECRAFT"
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                if (launch.isPreparing) {
                    OutlinedButton(onClick = session::cancelPreparation, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel preparation")
                    }
                } else if (isRunning) {
                    OutlinedButton(onClick = session::stopGame, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop Minecraft")
                    }
                }
            }
        }
        val sidebarScroll = rememberScrollState()
        Box(
            modifier = Modifier.width(sidebarWidth).fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    RoundedCornerShape(22.dp)),
        ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(sidebarScroll)
                .padding(start = 22.dp, top = 20.dp, end = 28.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("INSTANCE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Text(activeInstance?.name ?: "Legacy Vanilla",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = { managerOpen = true }, enabled = !locked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text("Manage instances (${inventory?.instances?.size ?: 0})")
                }
                if (missingInstance) {
                    Text("Selected instance is unavailable. Choose a valid instance or Legacy Vanilla.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (unsavedInstanceSettings) {
                    Text("Instance settings differ from the saved profile. Re-select it or edit it in Manage instances.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                (processProtectionError ?: instanceError)?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YOUR SETUP", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Text("Make it yours.", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("PLAYER", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    label = { Text("Offline nickname") },
                    supportingText = {
                        Text(if (nickname.isNotBlank() && !validName)
                            "Use 3–16 letters, numbers or underscores." else "For offline-compatible servers.")
                    },
                    isError = nickname.isNotBlank() && !validName,
                    enabled = !locked, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GAME VERSION", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showSnapshots, enabled = !locked && activeInstance == null,
                        onClick = {
                            showSnapshots = false
                            selected = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                        }, label = { Text("Releases") },
                    )
                    FilterChip(
                        selected = showSnapshots, enabled = !locked && activeInstance == null,
                        onClick = {
                            showSnapshots = true
                            selected = manifest.versions.firstOrNull { it.id == manifest.latestSnapshot }
                        }, label = { Text("Snapshots") },
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true }, enabled = !locked && activeInstance == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(selected?.id ?: "Select version") }
                    DropdownMenu(
                        expanded = menuOpen && !locked && activeInstance == null,
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
                when (val state = metadata) {
                    VersionMetadataState.Loading ->
                        Text("Verifying version…", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    is VersionMetadataState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { reloadMetadata++ }, enabled = !locked) {
                            Text("Retry")
                        }
                    }
                    is VersionMetadataState.Ready ->
                        Text(
                            "Verified   •   " +
                                (state.metadata.javaRequirement?.let { "Java ${it.majorVersion}" }
                                    ?: "Java not declared"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    VersionMetadataState.Idle -> Unit
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("MEMORY", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
                    Text("${ramMiB.toInt() / 1024} GB", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = ramMiB, onValueChange = { ramMiB = it },
                    valueRange = 1024f..16384f, steps = 14,
                    enabled = !locked && activeInstance == null,
                )
                if (activeInstance != null) {
                    Text(
                        "Version and RAM belong to this instance. Edit them under Manage instances → Instance settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
                    MinecraftProcessState.RUNNING -> "Running · PID ${process.pid ?: "?"}"
                    MinecraftProcessState.STOPPED -> "Last exit code: ${process.exitCode ?: "?"}"
                    MinecraftProcessState.CRASHED -> process.errorMessage ?: "Minecraft crashed"
                    else -> "Offline profile · Vanilla"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (process.state == MinecraftProcessState.CRASHED ||
                launch.phase == LaunchPhase.FAILED) {
                process.stderrLog?.parent?.let {
                    Text("Logs: $it", style = MaterialTheme.typography.bodySmall,
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
    }
}

@Composable
private fun VersionLabel(version: MinecraftVersion, manifest: MinecraftVersionManifest) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(version.id)
        if (version.id == manifest.latestRelease || version.id == manifest.latestSnapshot) {
            Text("latest", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SurfaceTag(label: String, color: Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall,
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

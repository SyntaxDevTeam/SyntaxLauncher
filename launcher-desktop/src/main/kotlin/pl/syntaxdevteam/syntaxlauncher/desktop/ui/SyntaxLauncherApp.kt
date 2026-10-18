package pl.syntaxdevteam.syntaxlauncher.desktop.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.instance.InstanceInventory
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

        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).background(
                            MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("S", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("SYNTAX", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text("LAUNCHER", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                    }
                }
                Text(
                    "JAVA EDITION     /     PREVIEW 0.1",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.2.sp,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            Box(modifier = Modifier.weight(1f).padding(28.dp)) {
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
                        else -> LauncherPanel(state.manifest, session, persisted!!)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(45.dp).padding(horizontal = 36.dp),
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

@Composable
private fun LauncherPanel(
    manifest: MinecraftVersionManifest,
    session: DesktopLauncherSession,
    saved: LauncherPreferences,
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
    var reloadMetadata by remember { mutableIntStateOf(0) }
    var metadata by remember { mutableStateOf<VersionMetadataState>(VersionMetadataState.Idle) }
    val scope = rememberCoroutineScope()
    var inventory by remember(session) { mutableStateOf<InstanceInventory?>(null) }
    var instanceId by remember { mutableStateOf(saved.selectedInstanceId) }
    var managerOpen by remember { mutableStateOf(false) }
    var mutationBusy by remember { mutableStateOf(false) }
    var instanceError by remember { mutableStateOf<String?>(null) }
    val activeInstance = inventory?.instances?.firstOrNull { it.id == instanceId }
    val launch by session.coordinator.status.collectAsState()
    val process by session.processManager.status.collectAsState()

    val isRunning = process.state == MinecraftProcessState.RUNNING ||
        process.state == MinecraftProcessState.STARTING
    val locked = isRunning || launch.isPreparing || mutationBusy
    val validName = nickname.isNotBlank() && OfflineAccount.isValidUsername(nickname.trim())
    val resolved = (metadata as? VersionMetadataState.Ready)?.metadata
        ?.takeIf { it.id == selected?.id }
    val unsavedInstanceSettings = activeInstance != null &&
        (activeInstance.minecraftVersionId != selected?.id ||
            activeInstance.maximumMemoryMiB != ramMiB.toInt())
    val canPlay = !locked && validName && resolved != null && !unsavedInstanceSettings

    LaunchedEffect(session) {
        try { inventory = session.instances.list() }
        catch (error: Exception) { instanceError = error.message ?: "Cannot load instances" }
    }

    fun chooseInstance(id: String) {
        instanceId = id
        inventory?.instances?.firstOrNull { it.id == id }?.let { instance ->
            selected = manifest.versions.firstOrNull { it.id == instance.minecraftVersionId }
            ramMiB = instance.maximumMemoryMiB.toFloat()
            showSnapshots = selected?.type == MinecraftVersionType.SNAPSHOT
        }
    }

    fun modifyInstance(action: suspend () -> MinecraftInstance?) {
        if (locked) return
        scope.launch {
            mutationBusy = true
            try {
                val newSelection = action()
                inventory = session.instances.list()
                instanceError = inventory?.problems?.takeIf { it.isNotEmpty() }?.joinToString("; ")
                if (newSelection != null) chooseInstance(newSelection.id)
            } catch (error: Exception) {
                instanceError = error.message ?: "Instance operation failed"
            } finally { mutationBusy = false }
        }
    }

    LaunchedEffect(nickname, selected?.id, showSnapshots, ramMiB, instanceId) {
        if (nickname.isBlank() || OfflineAccount.isValidUsername(nickname.trim())) {
            session.rememberPreferences(
                LauncherPreferences(
                    nickname = nickname.trim(),
                    selectedVersionId = selected?.id.orEmpty(),
                    snapshots = showSnapshots,
                    maximumMemoryMiB = ramMiB.toInt(),
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

    if (managerOpen) {
        InstanceManagerDialog(
            instances = inventory?.instances.orEmpty(),
            selected = activeInstance,
            canModify = !locked,
            hasLegacyGame = session.instances.legacyGameAvailable(),
            onSelect = ::chooseInstance,
            onCreate = { name -> modifyInstance {
                session.instances.create(name, selected?.id ?: manifest.latestRelease,
                    maximumMemoryMiB = ramMiB.toInt())
            } },
            onRename = { instance, name -> modifyInstance {
                session.instances.update(instance.copy(name = name))
            } },
            onClone = { instance, name -> modifyInstance {
                session.instances.cloneInstance(instance.id, name)
            } },
            onSaveSettings = { instance -> modifyInstance {
                session.instances.update(instance.copy(
                    minecraftVersionId = selected?.id ?: instance.minecraftVersionId,
                    maximumMemoryMiB = ramMiB.toInt(),
                ))
            } },
            onDelete = { instance -> modifyInstance {
                session.instances.moveToTrash(instance.id)
                if (instanceId == instance.id) instanceId = ""
                null
            } },
            onImportLegacy = { name -> modifyInstance {
                session.instances.importLegacy(name, selected?.id ?: manifest.latestRelease,
                    maximumMemoryMiB = ramMiB.toInt())
            } },
            onDismiss = { managerOpen = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF172E2C), Color(0xFF122123), Color(0xFF161C28)),
                    ),
                    RoundedCornerShape(26.dp),
                ),
            ) {
                // Lightweight atmospheric shapes; no remote images or expensive effects.
                Box(
                    modifier = Modifier.size(230.dp).align(Alignment.TopEnd)
                        .padding(24.dp).background(
                            Color(0xFF65DCC0).copy(alpha = 0.075f), CircleShape,
                        ),
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(36.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SurfaceTag("VANILLA MINECRAFT", Color(0xFF86E3C9))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your world.\nYour adventure.",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 57.sp,
                            letterSpacing = (-1.5).sp,
                            color = Color(0xFFF3F9F8),
                        )
                        Text(
                            "No distractions. Just you and Minecraft.",
                            color = Color(0xFFBDD0CD),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            activeInstance?.name?.uppercase() ?: "LEGACY VANILLA PROFILE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9CB8B3),
                            letterSpacing = 2.sp,
                        )
                        Text(
                            selected?.id ?: "Choose a Minecraft version",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.13f))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("JAVA EDITION", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFAEC7C1))
                            Text("•", color = Color(0xFFAEC7C1))
                            Text(if (showSnapshots) "SNAPSHOT CHANNEL" else "STABLE RELEASE",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFFAEC7C1))
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(
                    MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp),
                ).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            launch.phase == LaunchPhase.FAILED -> "ERROR"
                            else -> "OFFLINE"
                        },
                        if (launch.phase == LaunchPhase.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
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
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
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
        Column(
            modifier = Modifier.width(320.dp).fillMaxSize().background(
                MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp),
            ).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("INSTANCE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Text(activeInstance?.name ?: "Legacy Vanilla",
                    style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { managerOpen = true }, enabled = !locked,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Manage instances (${inventory?.instances?.size ?: 0})")
                }
                if (unsavedInstanceSettings) {
                    Text("Save version / RAM in Manage instances before playing.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                instanceError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YOUR SETUP", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Text("Make it yours.", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
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
                        selected = !showSnapshots, enabled = !locked,
                        onClick = {
                            showSnapshots = false
                            selected = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                        }, label = { Text("Releases") },
                    )
                    FilterChip(
                        selected = showSnapshots, enabled = !locked,
                        onClick = {
                            showSnapshots = true
                            selected = manifest.versions.firstOrNull { it.id == manifest.latestSnapshot }
                        }, label = { Text("Snapshots") },
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true }, enabled = !locked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(selected?.id ?: "Select version") }
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
                    valueRange = 1024f..16384f, steps = 14, enabled = !locked,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
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

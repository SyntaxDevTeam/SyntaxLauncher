package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
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
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 32.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "SyntaxLauncher",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Minecraft, without the clutter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val state = catalog) {
                ManifestState.Loading -> InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(modifier = Modifier.width(26.dp))
                        Text("Loading official Minecraft versions...")
                    }
                }
                is ManifestState.Error -> InfoCard {
                    Text("Version catalog unavailable", fontWeight = FontWeight.SemiBold)
                    Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { reloadCatalog++ }) { Text("Retry") }
                }
                is ManifestState.Ready -> when {
                    settingsError != null -> InfoCard {
                        Text("Settings file requires attention", color = MaterialTheme.colorScheme.error)
                        Text(settingsError.orEmpty())
                        Text("Your existing settings were preserved; please repair them before starting the launcher.")
                    }
                    persisted == null -> InfoCard { Text("Loading saved settings...") }
                    else -> LauncherPanel(state.manifest, session, persisted!!)
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
    val launch by session.coordinator.status.collectAsState()
    val process by session.processManager.status.collectAsState()

    val isRunning = process.state == MinecraftProcessState.RUNNING ||
        process.state == MinecraftProcessState.STARTING
    val locked = isRunning || launch.isPreparing
    val validName = nickname.isNotBlank() && OfflineAccount.isValidUsername(nickname.trim())
    val resolved = (metadata as? VersionMetadataState.Ready)?.metadata
        ?.takeIf { it.id == selected?.id }
    val canPlay = !locked && validName && resolved != null

    LaunchedEffect(nickname, selected?.id, showSnapshots, ramMiB) {
        if (nickname.isBlank() || OfflineAccount.isValidUsername(nickname.trim())) {
            session.rememberPreferences(
                LauncherPreferences(
                    nickname = nickname.trim(),
                    selectedVersionId = selected?.id.orEmpty(),
                    snapshots = showSnapshots,
                    maximumMemoryMiB = ramMiB.toInt(),
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

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        InfoCard(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Heading(
                    "Play profile",
                    "Offline identity, vanilla Minecraft and a managed Java runtime.",
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Offline nickname") },
                    supportingText = { Text("3–16 letters, digits or underscores. Offline servers only.") },
                    isError = nickname.isNotBlank() && !validName,
                    enabled = !locked,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version channel", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showSnapshots,
                            enabled = !locked,
                            onClick = {
                                showSnapshots = false
                                selected = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                            },
                            label = { Text("Releases") },
                        )
                        FilterChip(
                            selected = showSnapshots,
                            enabled = !locked,
                            onClick = {
                                showSnapshots = true
                                selected = manifest.versions.firstOrNull { it.id == manifest.latestSnapshot }
                            },
                            label = { Text("Snapshots") },
                        )
                    }
                    Box {
                        OutlinedButton(
                            onClick = { menuOpen = true },
                            enabled = !locked,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Minecraft  ${selected?.id ?: "Choose version"}")
                        }
                        DropdownMenu(
                            expanded = menuOpen && !locked,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            manifest.versions
                                .asSequence()
                                .filter { it.type == if (showSnapshots) MinecraftVersionType.SNAPSHOT else MinecraftVersionType.RELEASE }
                                .take(80)
                                .forEach { version ->
                                    DropdownMenuItem(
                                        text = { VersionLabel(version, manifest) },
                                        onClick = {
                                            selected = version
                                            menuOpen = false
                                        },
                                    )
                                }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Memory", style = MaterialTheme.typography.labelLarge)
                        Text("${ramMiB.toInt()} MiB", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = ramMiB,
                        onValueChange = { ramMiB = it },
                        valueRange = 1024f..16384f,
                        steps = 14,
                        enabled = !locked,
                    )
                    Text(
                        "Advanced JVM settings will remain separate from the main screen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.width(320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoCard {
                Heading("Launch readiness", "Everything needed for the selected version.")
                Spacer(Modifier.height(12.dp))
                StatusLine("Account", if (validName) "Offline · valid" else "Enter a nickname")
                StatusLine("Catalog", "${manifest.versions.size} versions")
                when (val state = metadata) {
                    VersionMetadataState.Idle -> StatusLine("Metadata", "Choose a version")
                    VersionMetadataState.Loading -> StatusLine("Metadata", "Verifying...")
                    is VersionMetadataState.Ready -> {
                        StatusLine("Metadata", if (resolved == null) "Version changed" else "Verified")
                        StatusLine(
                            "Java",
                            state.metadata.javaRequirement?.let { "Java ${it.majorVersion}" }
                                ?: "Not declared",
                        )
                        StatusLine("Libraries", "${state.metadata.libraries.size}")
                    }
                    is VersionMetadataState.Error -> {
                        StatusLine("Metadata", "Unavailable")
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            enabled = !locked,
                            onClick = { reloadMetadata++ },
                        ) { Text("Retry metadata") }
                    }
                }
                val stateLabel = when (process.state) {
                    MinecraftProcessState.RUNNING -> "Running · PID ${process.pid ?: "?"}"
                    MinecraftProcessState.STARTING -> "Starting"
                    MinecraftProcessState.STOPPED -> "Stopped · ${process.exitCode ?: "?"}"
                    MinecraftProcessState.CRASHED -> "Crashed · ${process.exitCode ?: "?"}"
                    MinecraftProcessState.DETACHED -> "Detached"
                    MinecraftProcessState.IDLE -> if (launch.isPreparing) "Preparing" else "Not running"
                }
                StatusLine("Minecraft", stateLabel)
                if (launch.phase != LaunchPhase.IDLE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        launch.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (launch.phase == LaunchPhase.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (launch.isPreparing) {
                        Spacer(Modifier.height(8.dp))
                        val progress = launch.currentFileProgress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (launch.totalFiles > 0) {
                            Text(
                                "${launch.completedFiles}/${launch.totalFiles} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (process.state == MinecraftProcessState.CRASHED) {
                    Text(
                        process.errorMessage ?: "Minecraft exited unexpectedly.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                process.stderrLog?.parent?.let { folder ->
                    Text(
                        "Process logs: $folder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = {
                    resolved?.let { session.play(it, nickname.trim(), ramMiB.toInt()) }
                },
                enabled = canPlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (launch.isPreparing) "Preparing..." else if (isRunning) "Playing" else "Play Minecraft")
            }
            if (launch.isPreparing) {
                OutlinedButton(
                    onClick = session::cancelPreparation,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel preparation") }
            } else if (isRunning) {
                OutlinedButton(
                    onClick = session::stopGame,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Stop Minecraft") }
            }
            Text(
                "Offline mode works with local play and servers accepting offline clients. Closing this launcher leaves a running game open.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
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

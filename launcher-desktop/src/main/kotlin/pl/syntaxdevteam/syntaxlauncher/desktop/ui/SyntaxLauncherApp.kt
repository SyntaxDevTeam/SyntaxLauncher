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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionManifest
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType

private enum class VersionFilter {
    RELEASES,
    SNAPSHOTS,
}

@Composable
fun SyntaxLauncherApp(versionCatalog: MinecraftVersionCatalog) {
    SyntaxTheme {
        var manifestState by remember { mutableStateOf<ManifestState>(ManifestState.Loading) }
        var reloadToken by remember { mutableIntStateOf(0) }

        LaunchedEffect(reloadToken) {
            manifestState = ManifestState.Loading
            manifestState = runCatching { versionCatalog.fetchManifest() }
                .fold(
                    onSuccess = { ManifestState.Ready(it) },
                    onFailure = { ManifestState.Error(it.message ?: "Unable to load Minecraft versions.") },
                )
        }

        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(horizontal = 48.dp, vertical = 36.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Header()

                    Box(modifier = Modifier.weight(1f)) {
                        when (val state = manifestState) {
                            ManifestState.Loading -> LoadingPanel()
                            is ManifestState.Error -> ErrorPanel(
                                message = state.message,
                                onRetry = { reloadToken++ },
                            )
                            is ManifestState.Ready -> LauncherPanel(state.manifest)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SyntaxLauncher",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Minecraft, without the clutter.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingPanel() {
    StatusCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp))
            Column {
                Text("Loading Minecraft versions", fontWeight = FontWeight.Medium)
                Text(
                    "Fetching the official Mojang version catalog.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
) {
    StatusCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Could not load versions", fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherPanel(manifest: MinecraftVersionManifest) {
    var nickname by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VersionFilter.RELEASES) }
    var selectedVersion by remember(manifest) {
        mutableStateOf(
            manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                ?: manifest.versions.firstOrNull(),
        )
    }
    var versionMenuOpen by remember { mutableStateOf(false) }
    var memoryMb by remember { mutableStateOf(4096f) }

    val visibleVersions = remember(manifest, filter) {
        manifest.versions.filter {
            when (filter) {
                VersionFilter.RELEASES -> it.type == MinecraftVersionType.RELEASE
                VersionFilter.SNAPSHOTS -> it.type == MinecraftVersionType.SNAPSHOT
            }
        }
    }

    val accountValidation = remember(nickname) {
        if (nickname.isBlank()) {
            null
        } else {
            runCatching { OfflineAccount.create(nickname) }.exceptionOrNull()?.message
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SectionTitle(
                    title = "Play profile",
                    subtitle = "v0.1 starts with a simple offline profile and vanilla Minecraft.",
                )

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Offline nickname") },
                    supportingText = {
                        Text(
                            accountValidation
                                ?: "3-16 letters, digits or underscores. Used only for offline-mode play.",
                        )
                    },
                    isError = accountValidation != null,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Channel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = filter == VersionFilter.RELEASES,
                            onClick = {
                                filter = VersionFilter.RELEASES
                                selectedVersion = manifest.versions.firstOrNull {
                                    it.id == manifest.latestRelease
                                }
                            },
                            label = { Text("Releases") },
                        )
                        FilterChip(
                            selected = filter == VersionFilter.SNAPSHOTS,
                            onClick = {
                                filter = VersionFilter.SNAPSHOTS
                                selectedVersion = manifest.versions.firstOrNull {
                                    it.id == manifest.latestSnapshot
                                }
                            },
                            label = { Text("Snapshots") },
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = versionMenuOpen,
                    onExpandedChange = { versionMenuOpen = !versionMenuOpen },
                ) {
                    OutlinedTextField(
                        value = selectedVersion?.id.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Minecraft version") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionMenuOpen)
                        },
                    )

                    ExposedDropdownMenu(
                        expanded = versionMenuOpen,
                        onDismissRequest = { versionMenuOpen = false },
                    ) {
                        visibleVersions.take(80).forEach { version ->
                            DropdownMenuItem(
                                text = {
                                    VersionMenuLabel(
                                        version = version,
                                        isLatest = version.id == manifest.latestRelease ||
                                            version.id == manifest.latestSnapshot,
                                    )
                                },
                                onClick = {
                                    selectedVersion = version
                                    versionMenuOpen = false
                                },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Memory", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${memoryMb.toInt()} MB",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Slider(
                        value = memoryMb,
                        onValueChange = { memoryMb = it },
                        valueRange = 1024f..16384f,
                        steps = 14,
                    )
                    Text(
                        "Advanced JVM options will stay out of the main launch flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.width(320.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StatusCard {
                SectionTitle(
                    title = "v0.1 foundation",
                    subtitle = "The launcher core is being built in small, testable layers.",
                )
                Spacer(Modifier.height(18.dp))
                StatusLine("Account", if (accountValidation == null && nickname.isNotBlank()) "Valid offline profile" else "Not ready")
                StatusLine("Version catalog", "${manifest.versions.size} versions loaded")
                StatusLine("Latest release", manifest.latestRelease)
                StatusLine("Java runtime", "Next implementation step")
                StatusLine("Game launch", "Next implementation step")
            }

            Text(
                text = "The Play action will appear when the launch preparation pipeline is functional; SyntaxLauncher does not expose fake controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionMenuLabel(
    version: MinecraftVersion,
    isLatest: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(version.id)
        if (isLatest) {
            Text(
                "latest",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            content = content,
        )
    }
}

private sealed interface ManifestState {
    data object Loading : ManifestState
    data class Ready(val manifest: MinecraftVersionManifest) : ManifestState
    data class Error(val message: String) : ManifestState
}

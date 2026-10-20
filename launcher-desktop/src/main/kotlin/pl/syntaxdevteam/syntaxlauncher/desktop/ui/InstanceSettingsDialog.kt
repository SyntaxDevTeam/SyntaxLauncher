package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion

/**
 * Draft values stay in Compose state until explicit Save; Cancel writes nothing.
 * Each JVM option occupies one line and becomes exactly one ProcessBuilder argument.
 */
@Composable
fun InstanceSettingsDialog(
    instance: MinecraftInstance,
    availableVersions: List<String>,
    loadLoaderVersions: suspend (String, String) -> List<ModLoaderVersion> = { _, _ -> emptyList() },
    onSave: (MinecraftInstance) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(instance.id) { mutableStateOf(instance.name) }
    var version by remember(instance.id) { mutableStateOf(instance.minecraftVersionId) }
    var minimum by remember(instance.id) { mutableStateOf(instance.minimumMemoryMiB.toString()) }
    var maximum by remember(instance.id) { mutableStateOf(instance.maximumMemoryMiB.toString()) }
    var jvmArguments by remember(instance.id) {
        mutableStateOf(instance.extraJvmArguments.joinToString("\n"))
    }
    var advanced by remember(instance.id) { mutableStateOf(instance.extraJvmArguments.isNotEmpty()) }
    var suggestions by remember { mutableStateOf(false) }
    var loaderId by remember(instance.id) { mutableStateOf(instance.loader?.id ?: "vanilla") }
    var fabricVersion by remember(instance.id) { mutableStateOf(instance.loader?.version.orEmpty()) }
    var compatible by remember(instance.id) { mutableStateOf<List<ModLoaderVersion>>(emptyList()) }
    var loaderLoading by remember(instance.id) { mutableStateOf(false) }
    var loaderError by remember(instance.id) { mutableStateOf<String?>(null) }

    val minRam = minimum.toIntOrNull()
    val maxRam = maximum.toIntOrNull()
    val ramValid = minRam != null && maxRam != null &&
        minRam in 256..16384 && maxRam in 1024..16384 && maxRam >= minRam
    val instanceName = name.trim()
    val nameValid = instanceName.length in 1..48 &&
        instanceName.none { it.code in 0..31 || it.code == 127 }
    val versionId = version.trim()
    val versionValid = availableVersions.contains(versionId)
    val args = jvmArguments.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val argumentsValid = args.size <= 32 && args.all {
        it.length <= 512 && it.none { ch -> ch.code in 0..31 }
    }
    val fabricValid = loaderId == "vanilla" ||
        (!loaderLoading && loaderError == null && compatible.any { it.version == fabricVersion })
    val canSave = nameValid && versionValid && ramValid && argumentsValid && fabricValid
    LaunchedEffect(versionId, loaderId) {
        compatible = emptyList()
        loaderError = null
        if (loaderId in setOf("fabric", "quilt") && versionValid) {
            loaderLoading = true
            try {
                compatible = loadLoaderVersions(loaderId, versionId)
                if (compatible.isEmpty()) {
                    loaderError = "Fabric is not available for Minecraft $versionId."
                } else if (compatible.none { it.version == fabricVersion }) {
                    fabricVersion = compatible.firstOrNull { it.stable }?.version
                        ?: compatible.first().version
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loaderError = error.message ?: "Cannot load compatible Fabric versions."
            } finally {
                loaderLoading = false
            }
        }
    }
    val versionChanged = versionId != instance.minecraftVersionId

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Instance settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Settings for this instance only. Cancel will discard every change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Instance name") },
                    singleLine = true,
                    isError = !nameValid,
                    supportingText = { Text("1–48 characters. The instance UUID never changes.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it; suggestions = true },
                    label = { Text("Minecraft version") },
                    singleLine = true,
                    isError = !versionValid,
                    supportingText = {
                        Text(if (versionValid) "From the Mojang version catalog."
                            else "Select an existing version from the catalog.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions) {
                    availableVersions.asSequence()
                        .filter { it.contains(versionId, ignoreCase = true) }
                        .take(6)
                        .forEach { choice ->
                            TextButton(onClick = { version = choice; suggestions = false }) {
                                Text(choice)
                            }
                        }
                }
                if (versionChanged) {
                    Text(
                        "Changing the game version does not convert worlds. Back up saves first: " +
                            "opening newer worlds in older Minecraft versions may permanently damage them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("MOD LOADER", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { loaderId = "vanilla" }) {
                        Text(if (loaderId == "vanilla") "✓ Vanilla" else "Vanilla")
                    }
                    TextButton(onClick = { loaderId = "fabric" }) {
                        Text(if (loaderId == "fabric") "✓ Fabric" else "Fabric")
                    }
                    TextButton(onClick = { loaderId = "quilt" }) {
                        Text(if (loaderId == "quilt") "✓ Quilt" else "Quilt")
                    }
                }
                if (loaderId in setOf("fabric", "quilt")) {
                    OutlinedTextField(
                        value = fabricVersion,
                        onValueChange = { fabricVersion = it },
                        label = { Text(loaderId.replaceFirstChar { it.uppercase() } + " loader version") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !fabricValid,
                        supportingText = { Text(
                            if (loaderLoading) "Checking compatible versions…"
                            else loaderError ?: "Choose one of the compatible versions below."
                        ) },
                    )
                    compatible.take(7).forEach { choice ->
                        TextButton(onClick = { fabricVersion = choice.version }) {
                            Text(choice.version + if (choice.stable) " · stable" else " · preview")
                        }
                    }
                    if (loaderError != null) {
                        Text(loaderError.orEmpty(), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minimum,
                        onValueChange = { minimum = it.filter(Char::isDigit).take(5) },
                        label = { Text("Minimum RAM (MiB)") },
                        singleLine = true,
                        isError = !ramValid,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maximum,
                        onValueChange = { maximum = it.filter(Char::isDigit).take(5) },
                        label = { Text("Maximum RAM (MiB)") },
                        singleLine = true,
                        isError = !ramValid,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!ramValid) {
                    Text("Minimum 256 MiB; maximum 1024–16384 MiB and not below minimum.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "Hide advanced JVM arguments" else "Advanced JVM arguments")
                }
                if (advanced) {
                    OutlinedTextField(
                        value = jvmArguments,
                        onValueChange = { jvmArguments = it },
                        label = { Text("Additional JVM arguments") },
                        minLines = 3,
                        maxLines = 6,
                        isError = !argumentsValid,
                        supportingText = {
                            Text("One argument per line, no shell parsing. Up to 32 arguments, 512 characters each.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    // Values are already validated; the repository is the final authority.
                    onSave(instance.copy(
                        name = instanceName,
                        minecraftVersionId = versionId,
                        minimumMemoryMiB = minRam!!,
                        maximumMemoryMiB = maxRam!!,
                        extraJvmArguments = args,
                        loader = if (loaderId in setOf("fabric", "quilt")) ModLoaderSelection(loaderId, fabricVersion) else null,
                    ))
                },
            ) { Text("Save settings") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

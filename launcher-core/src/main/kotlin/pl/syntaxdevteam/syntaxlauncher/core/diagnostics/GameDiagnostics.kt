package pl.syntaxdevteam.syntaxlauncher.core.diagnostics

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessStatus
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadIntegrityVerifier
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** No account, token, command or arbitrary exception text is retained here. */
data class LaunchEvidence(
    val minecraft: String,
    val loader: String?,
    val expectedJava: Int?,
    val startedAt: Instant = Instant.now(),
    val runtime: JavaRuntime? = null,
    val mainClass: String? = null,
    val classpathEntries: Int = 0,
    val failureType: String? = null,
    val processStarted: Boolean = false,
)

enum class DiagnosticCode { JAVA, FILES, DEPENDENCIES, MOD_CONFLICT, MEMORY, GRAPHICS, NETWORK, DISK, AUTH, UNKNOWN, READY }
enum class HealthConfidence { CONFIRMED, PROBABLE, INFORMATION }
data class HealthFinding(val code: DiagnosticCode, val confidence: HealthConfidence,
    val source: String, val safeRepair: Boolean = false)
data class ModEvidence(val id: String, val version: String, val enabled: Boolean)
data class DiagnosticReport(
    val evidence: LaunchEvidence?, val exitCode: Int?, val findings: List<HealthFinding>,
    val checkedFiles: Int, val damagedFiles: Int, val unverifiedFiles: Int,
    val mods: List<ModEvidence>, val logsRead: Int,
) {
    /** Deliberately export a structured summary, never raw logs, paths, player identities or command arguments. */
    fun export(): String = buildString {
        appendLine("SyntaxLauncher diagnostic report — schema 1")
        evidence?.let {
            appendLine("Minecraft: ${safe(it.minecraft)}")
            appendLine("Loader: ${safe(it.loader ?: "vanilla")}")
            appendLine("Java required/selected: ${it.expectedJava ?: "unknown"}/${it.runtime?.majorVersion ?: "unknown"}")
            appendLine("Java version: ${safe(it.runtime?.versionString ?: "not selected")}")
            appendLine("Main class: ${safe(it.mainClass ?: "not built")}; classpath entries: ${it.classpathEntries}")
        }
        appendLine("Exit: ${exitCode ?: "unavailable"}; logs inspected: $logsRead")
        appendLine("Managed files checked/damaged/without upstream checksum: $checkedFiles/$damagedFiles/$unverifiedFiles")
        findings.forEach { appendLine("${it.code} / ${it.confidence} / ${it.source}") }
        mods.forEach { appendLine("Mod: ${safe(it.id)} ${safe(it.version)} enabled=${it.enabled}") }
    }
    // Version metadata can itself contain arbitrary text; only bounded identifiers belong in an export.
    private fun safe(value: String): String = value.take(120).replace(Regex("[^A-Za-z0-9._+:/ -]"), "_")
}

class GameDiagnostics {
    suspend fun inspect(
        version: ResolvedMinecraftVersion, platform: MinecraftPlatform, layout: MinecraftInstallationLayout,
        evidence: LaunchEvidence? = null, process: MinecraftProcessStatus? = null,
        mods: List<ModEvidence> = emptyList(), dependencyWarnings: Boolean = false, selectedLoader: String? = null,
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<HealthFinding>()
        val matching = evidence?.takeIf { it.minecraft == version.id }
        val planner = MinecraftFilePlanner()
        val plan = planner.planVersion(version, platform, layout)
        val requests = buildList {
            plan.client?.let(::add); addAll(plan.libraries); addAll(plan.natives.map { it.download })
            plan.assetIndex?.let(::add); plan.loggingConfig?.let(::add)
        }.toMutableList()
        val verifier = DownloadIntegrityVerifier()
        suspend fun valid(file: PlannedMinecraftDownload): Boolean = verifier.isValid(DownloadRequest(
            id = file.kind.name, url = file.url, target = file.target,
            expectedSha1 = file.sha1.takeIf { it.isNotBlank() }, expectedSize = file.size.takeIf { it >= 0 }))
        // Never trust a corrupt index to choose files for the scan.
        plan.assetIndex?.let { index ->
            if (valid(index) && Files.size(index.target) <= 16L * 1024 * 1024) {
                try { requests += planner.planAssets(AssetIndexParser().parse(Files.readString(index.target)), layout) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { findings += HealthFinding(DiagnosticCode.FILES,
                    HealthConfidence.CONFIRMED, "asset-index", true) }
            }
        }
        var damaged = 0
        var unverified = 0
        requests.forEach { file ->
            currentCoroutineContext().ensureActive()
            if (!valid(file)) damaged++
            else if (file.sha1.isBlank()) unverified++
        }
        if (damaged > 0) findings += HealthFinding(DiagnosticCode.FILES,
            HealthConfidence.CONFIRMED, "integrity-scan", true)
        if (matching?.runtime != null && (!Files.isRegularFile(matching.runtime.executable) ||
                matching.runtime.majorVersion != matching.expectedJava)) {
            findings += HealthFinding(DiagnosticCode.JAVA, HealthConfidence.CONFIRMED, "runtime", true)
        }
        matching?.failureType?.let { type ->
            val code = when {
                type.contains("Space") -> DiagnosticCode.DISK
                type.contains("Java") -> DiagnosticCode.JAVA
                type.contains("Integrity") || type.contains("Artifact") -> DiagnosticCode.FILES
                type.contains("Download") -> DiagnosticCode.NETWORK
                type.contains("Loader") -> DiagnosticCode.DEPENDENCIES
                else -> DiagnosticCode.UNKNOWN
            }
            findings += HealthFinding(code, HealthConfidence.PROBABLE, "preparation",
                code == DiagnosticCode.JAVA || code == DiagnosticCode.FILES)
        }
        if (dependencyWarnings) findings += HealthFinding(DiagnosticCode.DEPENDENCIES,
            HealthConfidence.PROBABLE, "mod-inventory")
        // Only logs from this launch are evidence. Old crash reports must not diagnose a new run.
        var read = 0
        val candidate = process?.takeIf { status ->
            val start = status.startedAt
            matching?.processStarted == true && (start == null || !start.isBefore(matching.startedAt.minusSeconds(5)))
        }
        if (matching?.processStarted == true) {
            val result = MinecraftDiagnostics().analyze(layout.gameDir, version.id, matching.loader,
                (candidate ?: MinecraftProcessStatus()).copy(startedAt = matching.startedAt))
            read = result.reviewedSources.size
            result.findings.filter { it.code != "UNKNOWN" }.forEach { finding ->
                val code = when (finding.code) {
                    "JAVA_VERSION", "JAVA_MISSING" -> DiagnosticCode.JAVA
                    "MOD_DEPENDENCIES", "MISSING_CLASS" -> DiagnosticCode.DEPENDENCIES
                    "FILE_INTEGRITY" -> DiagnosticCode.FILES
                    "DUPLICATE_MOD" -> DiagnosticCode.MOD_CONFLICT
                    "MEMORY" -> DiagnosticCode.MEMORY
                    "GRAPHICS" -> DiagnosticCode.GRAPHICS
                    "DISK" -> DiagnosticCode.DISK
                    "AUTH" -> DiagnosticCode.AUTH
                    else -> DiagnosticCode.UNKNOWN
                }
                findings += HealthFinding(code, HealthConfidence.PROBABLE, "game-log")
            }
        }
        val exit = candidate?.exitCode
        if (findings.isEmpty()) findings += HealthFinding(
            if (exit != null && exit != 0 || candidate?.state == MinecraftProcessState.CRASHED) DiagnosticCode.UNKNOWN else DiagnosticCode.READY,
            HealthConfidence.INFORMATION, "summary")
        DiagnosticReport(matching ?: LaunchEvidence(version.id, selectedLoader, version.javaRequirement?.majorVersion), exit, findings.distinct(), requests.size, damaged, unverified, mods, read)
    }

}

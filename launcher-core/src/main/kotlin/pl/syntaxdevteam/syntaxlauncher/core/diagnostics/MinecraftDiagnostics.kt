package pl.syntaxdevteam.syntaxlauncher.core.diagnostics

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.Instant
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessStatus

enum class DiagnosticConfidence { CONFIRMED, PROBABLE, POSSIBLE }
enum class DiagnosticAction { REPAIR_MANAGED_FILES, REVIEW_MODS, REVIEW_JAVA, REVIEW_GRAPHICS, SIGN_IN, VIEW_LOGS, NONE }

data class DiagnosticFinding(
    val code: String,
    val title: String,
    val explanation: String,
    val action: DiagnosticAction,
    val confidence: DiagnosticConfidence,
)

/**
 * Never contains raw log lines, command-line arguments, credentials or user-provided text.
 * It can safely be shown in the UI without exposing Minecraft tokens from logs.
 */
data class MinecraftDiagnosticReport(
    val minecraftVersion: String,
    val loader: String?,
    val exitCode: Int?,
    val findings: List<DiagnosticFinding>,
    val reviewedSources: List<String>,
)

/**
 * Local, strictly bounded evidence collection. This does not upload or export logs.
 * Every finding is a static sentence, so the log contents cannot be reflected back
 * into UI/telemetry and accidental bearer-token disclosure is avoided.
 */
class MinecraftDiagnostics(
    private val maxLogBytes: Int = 256 * 1024,
) {
    init { require(maxLogBytes in 1024..1024 * 1024) }

    fun analyze(
        gameDirectory: Path,
        minecraftVersion: String,
        loader: String?,
        process: MinecraftProcessStatus,
        preparationFailure: String? = null,
    ): MinecraftDiagnosticReport {
        val sources = mutableListOf<String>()
        val text = buildString {
            fun collect(label: String, path: Path?, since: Instant? = null) {
                if (path == null) return
                val data = tail(path, since) ?: return
                sources += label
                append(data).append('\n')
            }
            // Premium game stdout/stderr is intentionally not persisted by the process manager.
            collect("Process stderr", process.stderrLog, process.startedAt)
            collect("Process stdout", process.stdoutLog, process.startedAt)
            val logs = safeChild(gameDirectory, "logs", "latest.log")
            collect("Minecraft latest.log", logs, process.startedAt)
            val crashes = safeChild(gameDirectory, "crash-reports")
            if (crashes != null && Files.isDirectory(crashes, NOFOLLOW_LINKS)) {
                val latest = Files.newDirectoryStream(crashes, "*.txt").use { files ->
                    files.asSequence()
                        .filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
                        .filter { process.startedAt == null || !Files.getLastModifiedTime(it, NOFOLLOW_LINKS)
                            .toInstant().isBefore(process.startedAt.minusSeconds(60)) }
                        .take(256)
                        .maxByOrNull { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toMillis() }
                }
                collect("Minecraft crash report", latest, process.startedAt)
            }
            // Include the already-safe category string only if the coordinator has failed.
            // Do not include arbitrary exception messages or authentication arguments.
        }.lowercase(java.util.Locale.ROOT)
        val findings = mutableListOf<DiagnosticFinding>()
        fun add(code: String, title: String, message: String, action: DiagnosticAction,
                confidence: DiagnosticConfidence) {
            if (findings.none { it.code == code }) findings +=
                DiagnosticFinding(code, title, message, action, confidence)
        }
        when {
            "unsupportedclassversionerror" in text || "class file version" in text ->
                add("JAVA_VERSION", "Java version mismatch",
                    "One component was compiled for a different Java version. Verify the managed Java runtime and mod versions.",
                    DiagnosticAction.REVIEW_JAVA, DiagnosticConfidence.PROBABLE)
            "could not find java" in text || "java executable not found" in text ->
                add("JAVA_MISSING", "Java runtime unavailable",
                    "A required Java executable was not found. Recheck or repair the managed runtime.",
                    DiagnosticAction.REVIEW_JAVA, DiagnosticConfidence.PROBABLE)
        }
        if ("outofmemoryerror" in text || "java heap space" in text) {
            add("MEMORY", "Minecraft ran out of memory",
                "The game reported a Java memory exhaustion. Review RAM allocation and high-memory mods.",
                DiagnosticAction.REVIEW_MODS, DiagnosticConfidence.PROBABLE)
        }
        if (listOf("modresolutionexception", "modloadingexception", "incompatible mod",
                "failed to load mod", "missing dependency", "mod resolution failed").any { it in text }) {
            add("MOD_DEPENDENCIES", "Mod loading or compatibility failure",
                "A mod loader reported a loading or dependency problem. Inspect installed mods and required versions.",
                DiagnosticAction.REVIEW_MODS, DiagnosticConfidence.PROBABLE)
        }
        if ("noclassdeffounderror" in text || "classnotfoundexception" in text) {
            add("MISSING_CLASS", "Required Java class unavailable",
                "A library or mod may be missing or incompatible. Inspect mod dependencies before changing game files.",
                DiagnosticAction.REVIEW_MODS, DiagnosticConfidence.POSSIBLE)
        }
        if (listOf("zipexception", "invalid cen header", "checksum mismatch",
                "failed to verify library", "hash mismatch").any { it in text }) {
            add("FILE_INTEGRITY", "Possible corrupt game or library files",
                "A file-integrity symptom appeared. Verify and re-download launcher-managed game files; personal worlds are not repair targets.",
                DiagnosticAction.REPAIR_MANAGED_FILES, DiagnosticConfidence.PROBABLE)
        }
        if ("glfw error 65542" in text || "opengl" in text && "not supported" in text) {
            add("GRAPHICS", "Graphics initialization failure",
                "OpenGL initialization appears to have failed. Check graphics driver support and shader compatibility.",
                DiagnosticAction.REVIEW_GRAPHICS, DiagnosticConfidence.PROBABLE)
        }
        if ("invalid session" in text || "invalid credentials" in text || "authentication failed" in text) {
            add("AUTH", "Authentication problem",
                "The game reported an authentication error. Sign in again with the entitled Microsoft account.",
                DiagnosticAction.SIGN_IN, DiagnosticConfidence.POSSIBLE)
        }
        // The preparation status contains sanitized, user-visible categories, not stack traces.
        if (preparationFailure != null && findings.isEmpty()) {
            add("PREPARATION", "Launcher preparation failed",
                "The launcher could not finish preparing the game. Check the download connection and Java installation, then retry.",
                DiagnosticAction.REPAIR_MANAGED_FILES, DiagnosticConfidence.POSSIBLE)
        }
        if (findings.isEmpty()) {
            add("UNKNOWN", "No specific cause identified",
                "The exit code alone does not identify a cause. Review the local game logs; no repair should be applied blindly.",
                DiagnosticAction.VIEW_LOGS, DiagnosticConfidence.POSSIBLE)
        }
        return MinecraftDiagnosticReport(
            minecraftVersion = minecraftVersion,
            loader = loader,
            exitCode = process.exitCode,
            findings = findings,
            reviewedSources = sources,
        )
    }

    private fun safeChild(root: Path, vararg components: String): Path? {
        var current = root.toAbsolutePath().normalize()
        if (Files.isSymbolicLink(current)) return null
        for (component in components) {
            current = current.resolve(component)
            if (Files.isSymbolicLink(current)) return null
        }
        return current
    }

    private fun tail(file: Path, since: Instant?): String? {
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) return null
        val modified = Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toInstant()
        if (since != null && modified.isBefore(since.minusSeconds(60))) return null
        val size = Files.size(file)
        if (size <= 0L) return null
        return RandomAccessFile(file.toFile(), "r").use { handle ->
            val read = minOf(size, maxLogBytes.toLong()).toInt()
            handle.seek(size - read)
            val bytes = ByteArray(read)
            handle.readFully(bytes)
            bytes.toString(Charsets.UTF_8)
        }
    }
}

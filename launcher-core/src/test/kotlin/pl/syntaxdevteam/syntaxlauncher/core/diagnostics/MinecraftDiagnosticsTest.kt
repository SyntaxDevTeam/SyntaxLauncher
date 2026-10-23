package pl.syntaxdevteam.syntaxlauncher.core.diagnostics

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessStatus

class MinecraftDiagnosticsTest {
    private val diagnostics = MinecraftDiagnostics()

    @Test fun reportsEvidenceWithoutReflectingCredentialsOrRawLogLines() {
        val game = Files.createTempDirectory("diagnostic-game")
        val logs = Files.createDirectories(game.resolve("logs"))
        val secret = "Bearer a_very_sensitive_account_token_123456"
        Files.writeString(logs.resolve("latest.log"),
            "[main/ERROR] ModResolutionException: incompatible mod\n$secret\n")
        val result = diagnostics.analyze(game, "1.21.1", "fabric",
            MinecraftProcessStatus(state = MinecraftProcessState.CRASHED, exitCode = 1))
        assertEquals("MOD_DEPENDENCIES", result.findings.first().code)
        assertEquals(DiagnosticAction.REVIEW_MODS, result.findings.first().action)
        assertTrue("Minecraft latest.log" in result.reviewedSources)
        assertFalse(result.toString().contains(secret))
        assertFalse(result.findings.any { it.title.contains("Bearer") || it.explanation.contains("Bearer") })
    }

    @Test fun doesNotReadStaleLogsOrFollowRedirectedGameLog() {
        val game = Files.createTempDirectory("diagnostic-stale")
        val logs = Files.createDirectories(game.resolve("logs"))
        val file = Files.writeString(logs.resolve("latest.log"), "OutOfMemoryError")
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")))
        val process = MinecraftProcessStatus(state = MinecraftProcessState.CRASHED,
            startedAt = Instant.parse("2026-10-20T10:00:00Z"), exitCode = 1)
        val stale = diagnostics.analyze(game, "1.21.1", null, process)
        assertEquals("UNKNOWN", stale.findings.single().code)
        assertTrue(stale.reviewedSources.isEmpty())
        Files.delete(file)
        val external = Files.writeString(Files.createTempFile("private-notes", ".log"), "ModLoadingException")
        try {
            Files.createSymbolicLink(file, external)
            val redirected = diagnostics.analyze(game, "1.21.1", null,
                process.copy(startedAt = null))
            assertEquals("UNKNOWN", redirected.findings.single().code)
        } catch (_: UnsupportedOperationException) {
            // A platform without symlink support still exercises the stale-log case.
        } catch (_: java.nio.file.FileSystemException) {
            // Windows test runners can forbid symlink creation for unprivileged users.
        }
    }

    @Test fun identifiesDistinctRuntimeMemoryAndIntegritySymptoms() {
        val game = Files.createTempDirectory("diagnostic-patterns")
        val logs = Files.createDirectories(game.resolve("logs"))
        val file = logs.resolve("latest.log")
        val status = MinecraftProcessStatus(state = MinecraftProcessState.CRASHED, exitCode = 1)
        Files.writeString(file, "UnsupportedClassVersionError\njava.lang.OutOfMemoryError: Java heap space")
        val first = diagnostics.analyze(game, "26.3", "neoforge", status)
        assertEquals(listOf("JAVA_VERSION", "MEMORY"), first.findings.map { it.code })
        Files.writeString(file, "java.util.zip.ZipException: invalid CEN header\nGLFW error 65542")
        val second = diagnostics.analyze(game, "26.3", "neoforge", status)
        assertEquals(setOf("FILE_INTEGRITY", "GRAPHICS"), second.findings.map { it.code }.toSet())
    }

    @Test fun preparationFailureNeverReturnsUntrustedErrorText() {
        val game = Files.createTempDirectory("diagnostic-preparation")
        val secret = "accessToken=super-secret"
        val report = diagnostics.analyze(game, "26.3", "quilt",
            MinecraftProcessStatus(), secret)
        assertEquals("PREPARATION", report.findings.single().code)
        assertFalse(report.toString().contains(secret))
    }

    @Test fun oversizedGameLogUsesBoundedTail() {
        val game = Files.createTempDirectory("diagnostic-tail")
        val logs = Files.createDirectories(game.resolve("logs"))
        Files.writeString(logs.resolve("latest.log"),
            "ModLoadingException\n" + "x".repeat(300_000) + "\nOutOfMemoryError")
        val report = diagnostics.analyze(game, "1.21.1", "fabric",
            MinecraftProcessStatus(exitCode = 1))
        assertEquals("MEMORY", report.findings.first().code)
        assertFalse(report.findings.any { it.code == "MOD_DEPENDENCIES" })
    }
}

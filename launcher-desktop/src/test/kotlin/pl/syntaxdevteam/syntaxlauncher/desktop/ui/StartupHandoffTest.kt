package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdater
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopUpdateHandoff
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo

class StartupHandoffTest {
    @TempDir lateinit var root: Path

    @Test fun renderedChildWritesMatchingVersionReceiptAndRejectsOtherLocations() = runBlocking<Unit>(Dispatchers.Swing) {
        val receipt = root.resolve("startup-${UUID.randomUUID()}.ready")
        val nonce = UUID.randomUUID().toString()
        val handoff = DesktopUpdateHandoff.parse(arrayOf(StartupUpdater.HANDOFF_FLAG,
            receipt.toString(), nonce), root)!!
        assertFalse(Files.exists(receipt))
        val scene = ImageComposeScene(400, 400, coroutineContext = Dispatchers.Swing) {
            StartupUpdateGate(null, handoff, { error("Child must not hand off again") }) { Text("Ready") }
        }
        try {
            withTimeout(5000) {
                while (!Files.exists(receipt)) { scene.render(System.nanoTime()).close(); delay(20) }
            }
            assertEquals("$nonce\n${LauncherBuildInfo.version}", Files.readString(receipt))
        } finally { scene.close() }
        assertFailsWith<IllegalArgumentException> {
            DesktopUpdateHandoff.parse(arrayOf(StartupUpdater.HANDOFF_FLAG,
                root.resolve("../outside.ready").toString(), nonce), root)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopUpdateHandoff.parse(arrayOf(StartupUpdater.HANDOFF_FLAG, receipt.toString(), "bad nonce"), root)
        }
    }
}

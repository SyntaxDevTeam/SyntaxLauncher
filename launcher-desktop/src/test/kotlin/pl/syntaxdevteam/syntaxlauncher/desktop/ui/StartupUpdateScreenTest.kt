package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdateProgress

class StartupUpdateScreenTest {
    @Test fun compactStartupHasProgressErrorRecoveryAndSafeHandoffStates() {
        for ((name, progress, error) in listOf(
            Triple("checking", StartupUpdateProgress("Checking for launcher updates…"), null),
            Triple("downloading", StartupUpdateProgress("Downloading SyntaxLauncher 0.3.1…", 0.42f), null),
            Triple("starting", StartupUpdateProgress("Starting SyntaxLauncher 0.3.1…", canContinue = false), null),
            Triple("error", StartupUpdateProgress("Verifying update…"), "The update could not be verified. Your installed launcher was preserved."),
        )) {
            var continued = false
            var retried = false
            val scene = ImageComposeScene(440, 600) {
                SyntaxTheme { StartupUpdateScreen(progress, error, { retried = true }, { continued = true }) }
            }
            try {
                repeat(3) { scene.render(System.nanoTime()).close() }
                fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
                val nodes = scene.semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
                val skip = nodes.first { it.config.getOrNull(SemanticsProperties.Text)?.any {
                    it.text == "Continue with installed version" } == true }
                if (name == "starting") assertTrue(skip.config.contains(SemanticsProperties.Disabled))
                else {
                    assertFalse(skip.config.contains(SemanticsProperties.Disabled))
                    skip.config[SemanticsActions.OnClick].action!!.invoke()
                    assertTrue(continued)
                }
                if (error != null) {
                    nodes.first { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Try again" } == true }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    assertTrue(retried)
                }
                val output = Path.of("build/reports/ui/startup-$name.png")
                Files.createDirectories(output.parent)
                scene.render(System.nanoTime()).use { image ->
                    image.encodeToData()!!.use { Files.write(output, it.bytes) }
                }
            } finally { scene.close() }
        }
    }
}

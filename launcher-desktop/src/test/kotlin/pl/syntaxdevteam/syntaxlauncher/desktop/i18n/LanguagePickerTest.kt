package pl.syntaxdevteam.syntaxlauncher.desktop.i18n

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.SyntaxTheme

class LanguagePickerTest {
    @TempDir lateinit var root: Path

    @Test fun switchesLanguageInPlaceAndRestoresSavedChoiceAfterRestart() = runBlocking(Dispatchers.Swing) {
        val store = LauncherPreferencesStore(root.resolve("settings.properties"))
        store.saveLanguage("en")
        fun scene() = ImageComposeScene(500, 300, coroutineContext = Dispatchers.Swing) {
            LauncherLanguageHost(store) {
                SyntaxTheme { Column { LanguagePicker(); Text(LocalStrings.current.text("ui.play_minecraft")) } }
            }
        }
        var scene = scene()
        try {
            scene.until { nodes -> nodes.any { it.label() == "PLAY MINECRAFT" } &&
                nodes.any { it.label().matches(Regex("(EN|PL|DE) ▾")) && !it.config.contains(SemanticsProperties.Disabled) } }
            for ((label, code, play) in listOf(Triple("Polski", "pl", "GRAJ W MINECRAFT"),
                    Triple("Deutsch", "de", "MINECRAFT SPIELEN"))) {
                scene.nodes().first { it.label().matches(Regex("(EN|PL|DE) ▾")) }
                    .config[SemanticsActions.OnClick].action!!.invoke()
                scene.until { nodes -> nodes.any { it.label() == label } }
                scene.nodes().first { it.label() == label }.config[SemanticsActions.OnClick].action!!.invoke()
                scene.until { nodes -> nodes.any { it.label() == play } }
                assertEquals(code, store.loadLanguage())
            }
            scene.close()
            scene = scene()
            scene.until { nodes -> nodes.any { it.label() == "MINECRAFT SPIELEN" } }
            assertTrue(scene.nodes().any { it.label() == "DE ▾" })
        } finally { scene.close() }
    }

    private suspend fun ImageComposeScene.until(ready: (List<SemanticsNode>) -> Boolean) {
        withTimeout(10000) {
            while (true) {
                render(System.nanoTime()).close()
                if (ready(nodes())) break
                delay(20)
            }
        }
    }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
    }
    private fun SemanticsNode.label() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}

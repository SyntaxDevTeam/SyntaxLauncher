package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore
import pl.syntaxdevteam.syntaxlauncher.core.update.LauncherUpdateChannel
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiLanguage
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings

class LauncherUpdateChannelPickerTest {
    @TempDir lateinit var root: Path

    @Test fun persistsChannelAndRendersCompactTranslatedSettings() = runBlocking(Dispatchers.Swing) {
        val store = LauncherPreferencesStore(root.resolve("settings.properties"))
        for (language in UiLanguage.entries) {
            store.saveUpdateChannel(LauncherUpdateChannel.STABLE)
            val strings = UiStrings(language)
            fun scene() = ImageComposeScene(400, 400, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    SyntaxTheme { LauncherUpdateChannelPicker(store) }
                }
            }
            var scene = scene()
            try {
                val title = strings.text("updates.channel")
                scene.until { it.any { node -> node.label() == "$title: Stable" } }
                scene.click("$title: Stable")
                scene.until { it.any { node -> node.label() == "Stable" } }
                scene.click("Stable")
                scene.until { it.any { node -> node.label() == "DEV" } }
                scene.click("DEV")
                scene.until { it.any { node -> node.label() == strings.text("updates.restart") } &&
                    it.none { node -> node.label() == "SNAPSHOT" } }
                assertEquals(LauncherUpdateChannel.DEV, store.loadUpdateChannel())
                val directory = Path.of("build/reports/ui"); Files.createDirectories(directory)
                scene.render(System.nanoTime()).use { image ->
                    Files.write(directory.resolve("update-channel-${language.code}-400x400.png"),
                        image.encodeToData()!!.use { it.bytes })
                }
                scene.click(strings.text("updates.done"))
                scene.close(); scene = scene()
                scene.until { it.any { node -> node.label() == "$title: DEV" } }
            } finally { scene.close() }
        }
    }

    private fun ImageComposeScene.click(label: String) {
        nodes().first { it.label() == label }.config[SemanticsActions.OnClick].action!!.invoke()
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

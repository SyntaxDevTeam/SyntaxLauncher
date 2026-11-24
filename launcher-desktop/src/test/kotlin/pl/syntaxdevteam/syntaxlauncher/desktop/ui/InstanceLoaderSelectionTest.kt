package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.loader.CompatibleLoaderGame
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiLanguage
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings

class InstanceLoaderSelectionTest {
    @Test fun quiltFallbackIsVisibleUsesLatestLoaderAndOnlySavesOnConfirmation() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) {
            val strings = UiStrings(language)
            val instance = MinecraftInstance(id = java.util.UUID.randomUUID().toString(), name = "Test",
                minecraftVersionId = "26.4-snapshot-4")
            var saved: MinecraftInstance? = null
            var cancelled = false
            val latest = listOf(ModLoaderVersion("0.30.0-beta.1", false), ModLoaderVersion("0.29.0", true))
            val scene = ImageComposeScene(900, 900, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    SyntaxTheme { InstanceSettingsDialog(instance, listOf("26.4-snapshot-4", "26.3"),
                        findCompatibleGame = { id, _ ->
                            assertEquals("quilt", id)
                            CompatibleLoaderGame("26.3", latest)
                        }, onSave = { saved = it }, onCancel = { cancelled = true }) }
                }
            }
            try {
                scene.frames()
                scene.text("Quilt").click()
                val notice = strings.text("loader.version_adjusted", "26.4-snapshot-4", "26.3")
                scene.until { nodes -> nodes.any { it.label() == notice } &&
                    nodes.any { it.label() == strings.text("ui.save_settings") && !it.config.contains(SemanticsProperties.Disabled) } }
                assertEquals(null, saved)
                val directory = Path.of("build/reports/ui").also(Files::createDirectories)
                scene.render(System.nanoTime()).use { image ->
                    Files.write(directory.resolve("loader-fallback-${language.code}.png"), image.encodeToData()!!.use { it.bytes })
                }
                scene.text(strings.text("ui.save_settings")).click()
                val updated = requireNotNull(saved)
                assertEquals("26.3", updated.minecraftVersionId)
                assertEquals(ModLoaderSelection("quilt", "0.30.0-beta.1"), updated.loader)
                scene.text(strings.text("ui.cancel")).click()
                assertTrue(cancelled)
                assertEquals("26.4-snapshot-4", instance.minecraftVersionId)
            } finally { scene.close() }
        }
    }

    @Test fun switchingLoaderCancelsOldDiscoveryAndCannotSaveStaleCompatibility() = runBlocking(Dispatchers.Swing) {
        val instance = MinecraftInstance(id = java.util.UUID.randomUUID().toString(), name = "Test",
            minecraftVersionId = "26.4-snapshot-4")
        val pending = CompletableDeferred<CompatibleLoaderGame?>()
        var saved: MinecraftInstance? = null
        val scene = ImageComposeScene(900, 900, coroutineContext = Dispatchers.Swing) {
            SyntaxTheme { InstanceSettingsDialog(instance, listOf("26.4-snapshot-4", "26.3"),
                findCompatibleGame = { _, _ -> pending.await() }, onSave = { saved = it }, onCancel = {}) }
        }
        try {
            scene.frames()
            scene.text("Quilt").click()
            scene.frames()
            assertTrue(scene.text("Save settings").config.contains(SemanticsProperties.Disabled))
            scene.text("Vanilla").click()
            scene.frames()
            pending.complete(CompatibleLoaderGame("26.3", listOf(ModLoaderVersion("0.29.0", true))))
            scene.frames()
            scene.text("Save settings").click()
            val updated = requireNotNull(saved)
            assertEquals("26.4-snapshot-4", updated.minecraftVersionId)
            assertEquals(null, updated.loader)
        } finally { scene.close(); pending.cancel() }
    }

    private fun SemanticsNode.label() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.text(label: String) = nodes().first { it.label() == label }
    private fun SemanticsNode.click() { config[SemanticsActions.OnClick].action!!.invoke() }
    private suspend fun ImageComposeScene.frames() { repeat(3) { render(System.nanoTime()).close(); delay(20) } }
    private suspend fun ImageComposeScene.until(ready: (List<SemanticsNode>) -> Boolean) {
        withTimeout(10000) { while (true) { render(System.nanoTime()).close(); if (ready(nodes())) break; delay(20) } }
    }
}

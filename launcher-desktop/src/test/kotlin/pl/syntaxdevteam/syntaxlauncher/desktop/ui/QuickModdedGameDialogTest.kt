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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiLanguage
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings

class QuickModdedGameDialogTest {
    @TempDir lateinit var root: Path

    private fun repository(): MinecraftInstanceRepository {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("runtimes"))
        return MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"),
            MinecraftInstallationLayout.from(paths))
    }

    @Test fun simpleConfirmationCreatesOneIsolatedGameAndNeverChangesVanilla() = runBlocking(Dispatchers.Swing) {
        val repo = repository()
        val vanilla = repo.create("My world", "current")
        val world = repo.gameDirectory(vanilla).resolve("saves/keep.txt")
        Files.createDirectories(world.parent)
        Files.writeString(world, "precious world")
        var created: MinecraftInstance? = null
        var cancelled = false
        val publish = CompletableDeferred<Unit>()
        val scene = ImageComposeScene(900, 740, coroutineContext = Dispatchers.Swing) {
            SyntaxTheme { QuickModdedGameDialog("current", listOf("older"), "My modded game",
                loadVersions = { _, game -> if (game == "current") emptyList() else
                    listOf(ModLoaderVersion("preview", false), ModLoaderVersion("stable", true)) },
                create = { name, game, loader -> publish.await(); repo.create(name, game, loader = loader) },
                onCreated = { created = it }, onDismiss = { cancelled = true }) }
        }
        try {
            scene.until { nodes -> nodes.any { it.label() == "Minecraft older · Ready for mods" } }
            assertEquals(1, repo.list().instances.size)
            assertFalse(scene.nodes().any { it.label() == "Mod loader (advanced)" })
            val confirm = scene.text("Choose mods")
            confirm.click()
            confirm.click() // Even two queued clicks must create only one profile.
            scene.frames()
            assertTrue(scene.text("Preparing your game…").config.contains(SemanticsProperties.Disabled))
            assertTrue(scene.text("Cancel").config.contains(SemanticsProperties.Disabled))
            publish.complete(Unit)
            scene.until { created != null }
            val profile = requireNotNull(created)
            val loader = requireNotNull(profile.loader)
            assertEquals("older", profile.minecraftVersionId)
            assertEquals("stable", loader.version)
            assertEquals("fabric", loader.id)
            assertEquals(2, repo.list().instances.size)
            assertEquals(vanilla, repo.get(vanilla.id))
            assertEquals("precious world", Files.readString(world))
            assertTrue(repo.gameDirectory(profile) != repo.gameDirectory(vanilla))
            assertFalse(cancelled)
        } finally { publish.complete(Unit); scene.close() }
    }

    @Test fun unavailableNetworkCanRetryAndCancellationDoesNotPublishAnything() = runBlocking(Dispatchers.Swing) {
        var offline = true
        var creations = 0
        var cancelled = false
        val scene = ImageComposeScene(800, 700, coroutineContext = Dispatchers.Swing) {
            SyntaxTheme { QuickModdedGameDialog("current", listOf("current"), "My mods",
                loadVersions = { _, _ -> if (offline) error("offline") else listOf(ModLoaderVersion("stable", true)) },
                create = { _, _, _ -> creations++; error("must not publish") },
                onCreated = {}, onDismiss = { cancelled = true }) }
        }
        try {
            scene.until { nodes -> nodes.any { it.label().startsWith("Could not check compatible") } }
            assertTrue(scene.text("Choose mods").config.contains(SemanticsProperties.Disabled))
            offline = false
            scene.text("Retry").click()
            scene.until { nodes -> nodes.any { it.label() == "Minecraft current · Ready for mods" } }
            scene.text("Cancel").click()
            assertTrue(cancelled)
            assertEquals(0, creations)
        } finally { scene.close() }
    }

    @Test fun translatedCompactSetupsKeepConfirmationAndAdvancedChoicesReachable() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(440 to 640, 400 to 400)) {
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    SyntaxTheme { QuickModdedGameDialog("26.3", listOf("26.3"), strings.text("game.default_name"),
                        loadVersions = { _, _ -> listOf(ModLoaderVersion("stable", true)) },
                        create = { _, _, _ -> error("not requested") }, onCreated = {}, onDismiss = {}) }
                }
            }
            try {
                scene.until { nodes -> nodes.any { it.label() == strings.text("game.ready_version", "26.3") } }
                val button = scene.text(strings.text("game.choose_mods"))
                assertFalse(button.config.contains(SemanticsProperties.Disabled))
                assertTrue(button.boundsInRoot.right <= width && button.boundsInRoot.bottom <= height)
                val directory = Path.of("build/reports/ui").also(Files::createDirectories)
                scene.render(System.nanoTime()).use { image ->
                    Files.write(directory.resolve("quick-game-${language.code}-${width}x${height}.png"), image.encodeToData()!!.use { it.bytes })
                }
                scene.text(strings.text("game.more_options")).click()
                scene.frames()
                assertTrue(scene.nodes().any { it.label() == strings.text("game.name") })
            } finally { scene.close() }
        }
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

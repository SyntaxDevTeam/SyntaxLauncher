package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiLanguage
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings

class ContentCatalogTest {
    private val project = ModrinthProject("PACKROOT", "Forest adventure", "A colorful world for your next adventure.", "Fixture", 100, null)
    private fun instance(modded: Boolean = false) = MinecraftInstance(id = UUID.randomUUID().toString(),
        name = "My Minecraft", minecraftVersionId = "1.21.1", loader = if (modded) ModLoaderSelection("fabric", "fixture") else null)

    @Test fun oneClickInstallsWithoutTechnicalReviewGuardsDuplicatesAndRendersTranslations() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(820 to 650, 400 to 400)) {
            val strings = UiStrings(language)
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            var busy = false
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    CatalogTestTheme { ContentCatalog(instance = rememberInstance,
                        canModify = true, search = { section, _, _ ->
                            assertEquals(CatalogSection.TEXTURES, section); listOf(project)
                        }, install = { _, _ -> calls++; gate.await() },
                        onBusyChange = { busy = it }, onManage = {}, onDismiss = {}) }
                }
            }
            try {
                scene.until { it.any { node -> node.config.getOrNull(SemanticsProperties.TestTag) == "catalog-install-PACKROOT" } }
                assertFalse(scene.nodes().any { it.label() == strings.text("catalog.mods") })
                scene.tag("catalog-install-PACKROOT").click()
                scene.frames()
                assertEquals(1, calls)
                assertTrue(busy)
                assertTrue(scene.tag("catalog-install-PACKROOT").config.contains(SemanticsProperties.Disabled))
                assertTrue(scene.nodes().any { it.label() == strings.text("catalog.installing") })
                gate.complete(Unit)
                scene.until { nodes -> nodes.any { it.label() == strings.text("catalog.installed") } }
                assertFalse(busy)
                scene.tag("catalog-install-PACKROOT").config[SemanticsActions.OnClick].action?.invoke()
                assertEquals(1, calls)
                val button = scene.tag("catalog-install-PACKROOT")
                assertTrue(button.boundsInRoot.height >= 40 && button.boundsInRoot.bottom <= height)
                val dir = Path.of("build/reports/ui").also(Files::createDirectories)
                scene.render(System.nanoTime()).use { image -> Files.write(
                    dir.resolve("catalog-${language.code}-$width.png"), image.encodeToData()!!.use { it.bytes }) }
            } finally { gate.cancel(); scene.close() }
        }
    }

    // Stable identities are required across recomposition.
    private val rememberInstance = instance()

    @Test fun searchAndInstallErrorsOfferRetryAndBusyGameNeverCallsInstaller() = runBlocking(Dispatchers.Swing) {
        var searches = 0
        var installs = 0
        val profile = instance(true)
        val scene = ImageComposeScene(820, 650, coroutineContext = Dispatchers.Swing) {
            CatalogTestTheme { ContentCatalog(profile, canModify = true,
                search = { _, _, _ -> if (searches++ == 0) error("Fixture HTTP failure") else listOf(project) },
                install = { _, _ -> if (installs++ == 0) error("Fixture install failure") },
                onBusyChange = {}, onManage = {}, onDismiss = {}) }
        }
        try {
            scene.until { it.any { node -> node.label() == "We could not open the catalog. Check your connection and try again." } }
            scene.text("Retry").click()
            scene.until { it.any { node -> node.config.getOrNull(SemanticsProperties.TestTag) == "catalog-install-PACKROOT" } }
            scene.tag("catalog-install-PACKROOT").click()
            scene.until { it.any { node -> node.label() == "Installation did not finish. Try again, or choose another extra." } }
            scene.tag("catalog-install-PACKROOT").click()
            scene.until { it.any { node -> node.label() == "Installed" } }
            assertEquals(2, installs)
        } finally { scene.close() }
        val disabled = ImageComposeScene(400, 500, coroutineContext = Dispatchers.Swing) {
            CatalogTestTheme { ContentCatalog(rememberInstance, false, search = { _, _, _ -> listOf(project) },
                install = { _, _ -> installs++ }, onBusyChange = {}, onManage = {}, onDismiss = {}) }
        }
        try {
            disabled.until { it.any { node -> node.config.getOrNull(SemanticsProperties.TestTag) == "catalog-install-PACKROOT" } }
            assertTrue(disabled.tag("catalog-install-PACKROOT").config.contains(SemanticsProperties.Disabled))
            disabled.tag("catalog-install-PACKROOT").config[SemanticsActions.OnClick].action?.invoke()
            assertEquals(2, installs)
        } finally { disabled.close() }
    }

    @Test fun cancellingInstallationWaitsForCleanupAndAllowsRetry() = runBlocking(Dispatchers.Swing) {
        var calls = 0
        var busy = false
        var cleaned = false
        val scene = ImageComposeScene(400, 500, coroutineContext = Dispatchers.Swing) {
            CatalogTestTheme { ContentCatalog(rememberInstance, true,
                search = { _, _, _ -> listOf(project) }, install = { _, _ ->
                    if (calls++ == 0) try { awaitCancellation() } finally { cleaned = true }
                }, onBusyChange = { busy = it }, onManage = {}, onDismiss = {}) }
        }
        try {
            scene.until { it.any { node -> node.config.getOrNull(SemanticsProperties.TestTag) == "catalog-install-PACKROOT" } }
            scene.tag("catalog-install-PACKROOT").click()
            scene.frames()
            assertTrue(busy)
            scene.text("Cancel").click()
            scene.until { !busy && cleaned }
            assertTrue(scene.nodes().any { it.label() == "Cancelled. You can try again whenever you like." })
            scene.tag("catalog-install-PACKROOT").click()
            scene.until { it.any { node -> node.label() == "Installed" } }
            assertEquals(2, calls)
        } finally { scene.close() }
    }

    @androidx.compose.runtime.Composable
    private fun CatalogTestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
        SyntaxTheme {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) { content() }
        }
    }

    private fun SemanticsNode.label() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.text(value: String) = nodes().first { it.label() == value }
    private fun ImageComposeScene.tag(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == value }
    private fun SemanticsNode.click() { config[SemanticsActions.OnClick].action!!.invoke() }
    private suspend fun ImageComposeScene.frames() { repeat(3) { render(System.nanoTime()).close(); delay(20) } }
    private suspend fun ImageComposeScene.until(predicate: (List<SemanticsNode>) -> Boolean) {
        withTimeout(10000) { while (true) { render(System.nanoTime()).close(); if (predicate(nodes())) break; delay(20) } }
    }
}

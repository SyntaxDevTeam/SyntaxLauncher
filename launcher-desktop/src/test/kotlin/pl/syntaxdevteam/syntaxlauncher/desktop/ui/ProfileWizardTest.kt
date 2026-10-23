package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class ProfileWizardTest {
    @Test fun vanillaDraftStaysUnwrittenUntilFinalConfirmationInEveryLanguageAndSize() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(1000 to 720, 400 to 400)) {
            val strings = UiStrings(language)
            val created = mutableListOf<MinecraftInstance>()
            var cancelled = false
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxTheme {
                    ProfileWizard("26.3", listOf("26.3"), { _, _ -> error("Vanilla needs no loader") },
                        { _, _ -> error("No pack search") },
                        create = { name, game, loader -> MinecraftInstance(id = UUID.randomUUID().toString(), name = name, minecraftVersionId = game, loader = loader).also(created::add) },
                        installPack = { _, _, _ -> error("No pack install") },
                        onCreated = { _, extras -> assertFalse(extras) }, onBusyChange = {}, onCancel = { cancelled = true })
                } }
            }
            try {
                scene.frames()
                scene.click(strings.text("wizard.next"))
                assertNotNull(scene.text("✓ " + strings.text("wizard.content.empty")))
                scene.click(strings.text("wizard.next"))
                scene.typeName("My world")
                scene.click(strings.text("wizard.next"))
                assertTrue(created.isEmpty())
                scene.screenshot("wizard-review-${language.code}-$width")
                scene.click(strings.text("wizard.back"))
                assertTrue(created.isEmpty())
                scene.click(strings.text("wizard.next"))
                scene.click(strings.text("profiles.create"))
                withTimeout(5000) { while (created.isEmpty()) { scene.frames() } }
                assertEquals(1, created.size)
                assertEquals("My world", created.single().name)
                assertNull(created.single().loader)
                assertFalse(cancelled)
            } finally { scene.close() }
        }
    }

    @Test fun moddedCatalogBranchUsesVerifiedFallbackAndOpensExtrasOnlyAfterCreation() = runBlocking(Dispatchers.Swing) {
        var created: MinecraftInstance? = null
        var extras = false
        val scene = ImageComposeScene(900, 700, coroutineContext = Dispatchers.Swing) {
            SyntaxTheme { ProfileWizard("new-snapshot", listOf("26.3"),
                findCompatible = { id, game -> assertEquals("quilt", id); assertEquals("new-snapshot", game)
                    CompatibleLoaderGame("26.3", listOf(ModLoaderVersion("0.29", true))) },
                searchPacks = { _, _ -> error("Not a pack") },
                create = { name, game, loader -> MinecraftInstance(id = UUID.randomUUID().toString(), name = name, minecraftVersionId = game, loader = loader).also { created = it } },
                installPack = { _, _, _ -> error("Not a pack") }, onCreated = { _, open -> extras = open }, onBusyChange = {}, onCancel = {}) }
        }
        try {
            scene.frames(); scene.click("With mods"); scene.click("Next"); scene.click("Quilt"); scene.click("Next")
            scene.click("Individual extras from the catalog"); scene.click("Next")
            withTimeout(5000) { while (scene.nodes().none { it.label().contains("Minecraft 26.3 · quilt 0.29") }) scene.frames() }
            scene.typeName("Quilt fun"); scene.click("Next"); assertNull(created)
            scene.click("Create a new profile"); scene.frames()
            assertEquals(ModLoaderSelection("quilt", "0.29"), created?.loader)
            assertEquals("26.3", created?.minecraftVersionId); assertTrue(extras)
        } finally { scene.close() }
    }

    @Test fun packBranchFiltersByFamilyAndInstallsOnlyAfterNamedReview() = runBlocking(Dispatchers.Swing) {
        val pack = ModrinthProject("PACKROOT", "Forest", "A forest adventure", "Author", 1, null)
        var family: String? = null
        var installed = 0
        val scene = ImageComposeScene(900, 700, coroutineContext = Dispatchers.Swing) {
            SyntaxTheme { ProfileWizard("26.3", listOf("26.3"), { _, _ -> error("The pack determines exact versions") },
                searchPacks = { _, loader -> family = loader; listOf(pack) },
                create = { _, _, _ -> error("Do not create an empty profile for a pack") },
                installPack = { project, loader, name ->
                    assertEquals(pack, project); assertEquals("neoforge", loader); assertEquals("Forest trip", name); installed++
                    MinecraftInstance(id = UUID.randomUUID().toString(), name = name, minecraftVersionId = "1.21.1", loader = ModLoaderSelection(loader, "21.1.213"))
                }, onCreated = { _, extras -> assertFalse(extras) }, onBusyChange = {}, onCancel = {}) }
        }
        try {
            scene.frames(); scene.click("With mods"); scene.click("Next"); scene.click("NeoForge"); scene.click("Next")
            scene.click("A complete modpack"); scene.click("Next")
            withTimeout(5000) { while (scene.nodes().none { it.label().startsWith("Forest") }) scene.frames() }
            assertEquals("neoforge", family); scene.click("Forest"); scene.click("Next")
            scene.typeName("Forest trip"); scene.click("Next"); assertEquals(0, installed)
            scene.click("Create a new profile"); scene.frames(); assertEquals(1, installed)
        } finally { scene.close() }
    }

    @Test fun duplicateConfirmationIsGuardedAndLongCreationCanBeCancelled() = runBlocking(Dispatchers.Swing) {
        var attempts = 0
        var completed = 0
        val states = mutableListOf<Boolean>()
        val never = CompletableDeferred<MinecraftInstance>()
        val scene = ImageComposeScene(900, 700, coroutineContext = Dispatchers.Swing) {
            SyntaxTheme { ProfileWizard("26.3", listOf("26.3"), { _, _ -> error("Vanilla") }, { _, _ -> emptyList() },
                create = { _, _, _ -> attempts++; never.await() }, installPack = { _, _, _ -> error("Not a pack") },
                onCreated = { _, _ -> completed++ }, onBusyChange = { states += it }, onCancel = {}) }
        }
        try {
            scene.frames(); scene.click("Next"); scene.click("Next"); scene.typeName("Cancel me"); scene.click("Next")
            val confirm = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "wizard-next" }.config[SemanticsActions.OnClick].action!!
            confirm.invoke(); confirm.invoke(); scene.frames()
            assertEquals(1, attempts)
            scene.click("Cancel")
            withTimeout(5000) { while (states.lastOrNull() != false) scene.frames() }
            assertEquals(0, completed)
            assertNotNull(scene.text(UiStrings(UiLanguage.EN).text("catalog.cancelled")))
        } finally { never.cancel(); scene.close() }
    }

    private fun SemanticsNode.label() = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        return semanticsOwners.flatMap { all(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.text(label: String) = nodes().first { it.label() == label }
    private suspend fun ImageComposeScene.click(label: String) {
        frames()
        val node = nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) &&
            (it.label() == label || it.label() == "✓ $label" || it.label().startsWith("$label ")) }
            ?: error("No clickable $label: " + nodes().filter { it.config.contains(SemanticsActions.OnClick) }.map { it.label() })
        assertFalse(node.config.contains(SemanticsProperties.Disabled), label)
        assertTrue(node.boundsInRoot.height > 0, "Visible: $label")
        node.config[SemanticsActions.OnClick].action!!.invoke()
        frames()
    }
    private suspend fun ImageComposeScene.typeName(value: String) {
        nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "wizard-name" }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); frames()
    }
    private suspend fun ImageComposeScene.frames() { repeat(3) { render(System.nanoTime()).close(); delay(20) } }
    private fun ImageComposeScene.screenshot(name: String) {
        val directory = Path.of("build/reports/ui").also(Files::createDirectories)
        render(System.nanoTime()).use { image -> Files.write(directory.resolve("$name.png"), image.encodeToData()!!.use { it.bytes }) }
    }
}

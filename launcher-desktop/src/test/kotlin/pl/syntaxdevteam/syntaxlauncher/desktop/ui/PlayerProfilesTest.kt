package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.core.account.*
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class PlayerProfilesTest {
    @Test fun createRenameSelectAndRemovePlayersInEveryLanguageAtBothSizes() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(820 to 760, 400 to 400)) {
            val file = Files.createTempDirectory("player-manager-ui").resolve("accounts.properties")
            val vault = object : RefreshTokenVault {
                override fun read(key: String): String? = error("Offline manager must not read credentials")
                override fun write(key: String, token: String) = error("Offline manager must not write credentials")
                override fun remove(key: String) = error("Offline manager must not remove credentials")
            }
            val store = AccountManager(file, vault, "fixture", { error("Offline manager must not refresh") })
            var players by mutableStateOf<List<SavedAccount>>(emptyList())
            var selected by mutableStateOf("")
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxTheme { Surface {
                    PlayerProfilesContent(players, selected, true,
                        onSelect = { selected = it.account.uuid.toString() },
                        onAddOffline = { store.saveOffline(it).also { players = store.accounts() } },
                        onRename = { id, name -> store.renameOffline(id, name).also { players = store.accounts() } },
                        onAddMicrosoft = { error("Microsoft authorization unavailable") },
                        onRemove = { store.remove(it); players = store.accounts(); if (selected == it) selected = "" })
                } } }
            }
            fun nodes(): List<SemanticsNode> {
                fun collect(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::collect)
                return scene.semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
            }
            fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            suspend fun waitFor(predicate: () -> Boolean) = withTimeout(5000) {
                while (true) { scene.render(System.nanoTime()).close(); if (predicate()) break; delay(20) }
            }
            suspend fun click(key: String) {
                waitFor { nodes().any { label(it) == strings.text(key) } }
                repeat(20) {
                    val target = nodes().first { label(it) == strings.text(key) }
                    if (target.boundsInRoot.height >= 40) {
                        assertFalse(target.config.contains(SemanticsProperties.Disabled), key)
                        assertTrue(target.config[SemanticsActions.OnClick].action!!.invoke())
                        scene.render(System.nanoTime()).close(); delay(30)
                        return
                    }
                    val delta = if (target.positionInRoot.y < 0) -80f else 80f
                    nodes().filter { it.config.contains(SemanticsActions.ScrollBy) }.forEach {
                        it.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta)
                    }
                    scene.render(System.nanoTime()).close(); delay(50)
                }
                error("Control not reachable: $key $language $width")
            }
            suspend fun type(name: String) {
                waitFor { nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "player-nickname" } }
                nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "player-nickname" }
                    .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(name))
                scene.render(System.nanoTime()).close(); delay(30)
            }
            try {
                type("!")
                assertTrue(nodes().first { label(it) == strings.text("players.save") }.config.contains(SemanticsProperties.Disabled))
                type("ChildOne")
                click("players.save")
                waitFor { players.size == 1 && selected == players.single().account.uuid.toString() }
                val first = selected
                click("players.rename")
                type("ChildTwo")
                click("players.save")
                waitFor { players.single().account.username == "ChildTwo" && selected != first }
                val renamed = selected
                assertEquals(renamed, AccountManager(file, vault, "fixture", { error("No refresh") }).accounts().single().account.uuid.toString())
                click("ui.add_microsoft")
                waitFor { nodes().any { label(it) == "Microsoft authorization unavailable" } }
                assertEquals(renamed, selected)
                assertEquals(1, players.size)
                val directory = Path.of("build/reports/ui").also(Files::createDirectories)
                scene.render(System.nanoTime()).use { image ->
                    Files.write(directory.resolve("player-manager-${language.code}-$width.png"), image.encodeToData()!!.use { it.bytes })
                }
                click("players.remove")
                click("players.cancel")
                assertEquals(1, store.accounts().size)
                click("players.remove")
                click("players.confirm_remove")
                waitFor { players.isEmpty() && selected.isEmpty() }
                assertTrue(store.accounts().isEmpty())
            } finally { scene.close() }
        }
    }
}

package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import java.util.UUID
import java.time.Instant
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.core.account.*
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class PlayerProfilesTest {
    @Test fun managerOffersOnlyMicrosoftAccountsInAllLanguages() = runBlocking(Dispatchers.Swing) {
        val premium = PremiumAccount("JavaPlayer", UUID.fromString("12345678-1234-1234-1234-123456789abc"))
        val saved = listOf(SavedAccount(premium, true),
            SavedAccount(OfflineAccount.create("OldPlayer"), false))
        for (language in UiLanguage.entries) {
            val strings = UiStrings(language)
            val scene = ImageComposeScene(820, 760, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    SyntaxTheme {
                        PlayerProfilesContent(players = saved, selectedId = premium.uuid.toString(),
                            microsoftEnabled = true,
                            onSelect = {},
                            onAddMicrosoft = { PremiumAccount("NewPlayer", UUID.randomUUID()) },
                            onRemove = {})
                    }
                }
            }
            try {
                repeat(8) { scene.render(System.nanoTime()).close() }
                fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
                val texts = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                    .map { it.text }
                assertTrue(texts.any { it.contains("JavaPlayer") })
                assertTrue(texts.any { it.contains(strings.text("ui.add_microsoft")) })
                assertFalse(texts.any { it.contains("OldPlayer") })
                assertFalse(texts.any { it.contains("offline", ignoreCase = true) ||
                    it.contains("non-premium", ignoreCase = true) })
            } finally { scene.close() }
        }
    }
}

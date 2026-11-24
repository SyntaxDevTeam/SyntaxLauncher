package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class LauncherFooterTest {
    @Test fun wideFooterAlignsTheDisclaimerWithLinksWithoutClipping() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for (width in listOf(760, 1024, 1440)) {
            val scene = ImageComposeScene(width, 130, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides UiStrings(language)) { SyntaxTheme { LauncherFooter() } }
            }
            try {
                repeat(3) { scene.render(System.nanoTime()).close(); delay(20) }
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
                val nodes = scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
                fun tagged(tag: String) = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.boundsInRoot
                val disclaimer = tagged("footer-disclaimer")
                for (tag in listOf("footer-privacy", "footer-terms", "footer-github")) {
                    val link = tagged(tag)
                    assertTrue(kotlin.math.abs(disclaimer.center.y - link.center.y) < 1f, "$language / $width / $tag")
                    assertTrue(disclaimer.right <= link.left)
                }
                assertTrue(disclaimer.width > 100 && disclaimer.bottom <= 130)
            } finally { scene.close() }
        }
    }

    @Test fun iconsOpenExactCommunityDestinationsInAllLanguagesWithoutTheOldSlogan() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) {
            val opened = mutableListOf<String>()
            val strings = UiStrings(language)
            val scene = ImageComposeScene(400, 130, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings,
                    LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) { opened += uri } }) {
                    SyntaxTheme { LauncherFooter(true) }
                }
            }
            try {
                repeat(3) { scene.render(System.nanoTime()).close(); delay(20) }
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
                val nodes = scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
                for (id in listOf("website", "discord", "github")) {
                    val button = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "footer-$id" }
                    assertTrue(button.boundsInRoot.width >= 36 && button.boundsInRoot.height >= 36)
                    assertTrue(button.boundsInRoot.left >= 0 && button.boundsInRoot.right <= 400)
                    assertTrue(button.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(strings.text("footer.$id")) == true)
                    button.config[SemanticsActions.OnClick].action!!.invoke()
                }
                for ((id, _) in legalLinks) {
                    nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "footer-$id" }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                }
                assertEquals(communityLinks.map { it.second } + legalLinks.map { it.second }, opened)
                val disclaimer = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "footer-disclaimer" }
                assertEquals(strings.text("legal.disclaimer"), disclaimer.config[SemanticsProperties.Text].single().text)
                assertTrue(disclaimer.boundsInRoot.bottom <= 130)
                assertTrue(nodes.none { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == strings.text("ui.built_for_the_adventure") } == true })
            } finally { scene.close() }
        }
    }
}

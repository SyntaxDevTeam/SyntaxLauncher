package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackKind
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.MojangJavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.*
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class InstalledPacksTest {
    @Test fun selectionRemovalAndUndoAreUsableAtBothSizesInEveryLanguage() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(820 to 650, 400 to 400)) {
            val root = Files.createTempDirectory("installed-packs-ui")
            val layout = MinecraftInstallationLayout(root.resolve("game"), root.resolve("versions"), root.resolve("libraries"),
                root.resolve("assets"), root.resolve("indexes"), root.resolve("objects"), root.resolve("logging"), root.resolve("natives"))
            val http = HttpClient(MockEngine { error("Installed textures require no network") })
            val downloads = DownloadManager(http)
            val processes = MinecraftProcessManager(root.resolve("logs"))
            val repo = MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"), layout)
            val session = DesktopLauncherSession(MinecraftVersionCatalog(http), MinecraftVersionResolver(http),
                MinecraftLaunchCoordinator(MinecraftPreparationService(downloads),
                    MojangJavaRuntimeManager(http, downloads, root.resolve("runtimes")), LaunchPlanBuilder(),
                    processes, layout, MinecraftPlatform.detect()), processes, LauncherPreferencesStore(root.resolve("settings")), repo, layout, http)
            val profile = repo.create("My game", "1.21.1")
            val source = root.resolve("Forest.zip")
            ZipOutputStream(Files.newOutputStream(source)).use {
                it.putNextEntry(ZipEntry("pack.mcmeta"))
                it.write("""{"pack":{"pack_format":34,"description":"Forest colors"}}""".toByteArray())
                it.closeEntry()
            }
            session.packManager.importZip(profile.id, LocalPackKind.RESOURCE_PACK, source)
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    SyntaxTheme { androidx.compose.material3.Surface {
                        InstalledPacks(profile, session, true, {}, {})
                    } }
                }
            }
            fun nodes(): List<SemanticsNode> {
                fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
                return scene.semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
            }
            fun label(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            suspend fun find(key: String): SemanticsNode = withTimeout(10000) {
                while (true) {
                    scene.render(System.nanoTime()).close()
                    nodes().firstOrNull { label(it) == strings.text(key) }?.let { return@withTimeout it }
                    delay(20)
                }
                error("Unreachable")
            }
            suspend fun click(key: String) {
                val node = find(key)
                assertTrue(node.boundsInRoot.height >= 40 && node.boundsInRoot.bottom <= height,
                    "Visible $key required for $language $width")
                node.config[SemanticsActions.OnClick].action!!.invoke()
            }
            try {
                click("catalog.use_pack")
                find("catalog.active")
                val dir = Path.of("build/reports/ui").also(Files::createDirectories)
                scene.render(System.nanoTime()).use { image -> Files.write(dir.resolve("installed-packs-${language.code}-$width.png"), image.encodeToData()!!.use { it.bytes }) }
                click("catalog.remove_pack")
                find("catalog.packs_empty")
                click("catalog.undo_remove")
                find("catalog.active")
                click("catalog.disable_pack")
                find("catalog.use_pack")
                assertFalse(session.packManager.list(profile.id, LocalPackKind.RESOURCE_PACK).packs.single().selected)
                assertTrue(Files.isRegularFile(source))
            } finally {
                scene.close()
                val closed = CompletableDeferred<Unit>()
                session.close { closed.complete(Unit) }
                withTimeout(5000) { closed.await() }
            }
        }
    }
}

package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.*
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class GameHealthDialogTest {
    @Test fun diagnoseRepairAndTranslatedCompactControls() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for (width in listOf(400, 820)) {
            val root = Files.createTempDirectory("health-ui-")
            val payload = "client".encodeToByteArray()
            val http = HttpClient(MockEngine { respond(payload) })
            val layout = MinecraftInstallationLayout(root.resolve("game"), root.resolve("versions"), root.resolve("libs"),
                root.resolve("assets"), root.resolve("indexes"), root.resolve("objects"), root.resolve("logging"), root.resolve("natives"))
            val process = MinecraftProcessManager(root.resolve("logs")) { _, _, _ -> error("Repair started a game") }
            val runtime = object : JavaRuntimeManager {
                override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                    listener: JavaRuntimeEventListener) = JavaRuntime(Path.of(System.getProperty("java.home")), Path.of(System.getProperty("java.home"), "bin",
                    if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"), 21, "21")
            }
            val coordinator = MinecraftLaunchCoordinator(MinecraftPreparationService(DownloadManager(http)), runtime,
                LaunchPlanBuilder(), process, layout, MinecraftPlatform.detect())
            val session = DesktopLauncherSession(MinecraftVersionCatalog(http), MinecraftVersionResolver(http), coordinator,
                process, LauncherPreferencesStore(root.resolve("settings.properties")),
                MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"), layout), layout, http)
            val version = ResolvedMinecraftVersion("test", MinecraftVersionType.RELEASE, "test.Main", null, null,
                MinecraftArtifact(sha1 = MessageDigest.getInstance("SHA-1").digest(payload).joinToString("") { "%02x".format(it) },
                    size = payload.size.toLong(), url = "https://example.invalid/client"), emptyList(), MinecraftArguments(),
                JavaRuntimeRequirement("test", 21), null)
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, if (width == 400) 400 else 700, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxTheme {
                    GameHealthDialog(session, version, null, true) {}
                } }
            }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun label(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            suspend fun until(predicate: () -> Boolean) = withTimeout(10000) {
                while (true) { scene.render(System.nanoTime()).close(); if (predicate()) break; delay(25) }
            }
            suspend fun click(key: String) {
                until { nodes().any { label(it) == strings.text(key) } }
                repeat(40) {
                    val node = nodes().first { label(it) == strings.text(key) }
                    if (node.boundsInRoot.height >= 35 && !node.config.contains(SemanticsProperties.Disabled)) {
                        node.config[SemanticsActions.OnClick].action!!.invoke(); return
                    }
                    nodes().filter { it.config.contains(SemanticsActions.ScrollBy) }.forEach {
                        it.config[SemanticsActions.ScrollBy].action!!.invoke(0f, if (node.positionInRoot.y < 0) -70f else 70f)
                    }
                    scene.render(System.nanoTime()).close(); delay(40)
                }
                val target = nodes().first { label(it) == strings.text(key) }
                error("Unreachable control $key $language $width: bounds=${target.boundsInRoot}, pos=${target.positionInRoot}, disabled=${target.config.contains(SemanticsProperties.Disabled)}, scroll=" +
                    nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) }.joinToString { "${it.value()}/${it.maxValue()}" })
            }
            try {
                until { nodes().any { label(it) == strings.text("health.cause.files") } }
                val directory = Path.of("build/reports/ui").also(Files::createDirectories)
                scene.render(System.nanoTime()).use { image ->
                    Files.write(directory.resolve("health-${language.code}-$width.png"), image.encodeToData()!!.use { it.bytes })
                }
                click("health.repair")
                until { session.maintenanceResult.value == "success" && !session.preparingAccount.value }
                until { nodes().any { label(it) == strings.text("health.cause.ready") } }
                assertEquals(MinecraftProcessState.IDLE, process.status.value.state)
                assertContentEquals(payload, Files.readAllBytes(layout.versionJar("test")))
            } finally {
                scene.close()
                val closed = CompletableDeferred<Unit>(); session.close { closed.complete(Unit) }
                withTimeout(5000) { closed.await() }
            }
        }
    }
}

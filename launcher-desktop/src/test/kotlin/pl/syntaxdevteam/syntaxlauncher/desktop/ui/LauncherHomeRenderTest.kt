package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.MojangJavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchPlanBuilder
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftLaunchCoordinator
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessManager
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPreparationService
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionResolver
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferences
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopLauncherSession

/** Real Compose rendering and interactions, with deterministic metadata and isolated user state. */
class LauncherHomeRenderTest {
    @TempDir lateinit var root: Path

    @Test
    fun homeShowsReadyControlsAndInvalidNicknameDisablesPlay() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val scene = ImageComposeScene(1440, 810, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            scene.screenshot("home-1440x810")
            assertTrue(scene.text("MEMORY ALLOCATION").boundsInRoot.bottom < 810)
            assertTrue(scene.text("Offline profile · Vanilla").boundsInRoot.bottom < 780)
            val play = scene.text("PLAY MINECRAFT")
            assertFalse(play.config.contains(SemanticsProperties.Disabled))
            val nickname = scene.nodes().first { it.config.contains(SemanticsActions.SetText) }
            nickname.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("invalid nickname!"))
            scene.frames()
            assertTrue(scene.text("PLAY MINECRAFT").config.contains(SemanticsProperties.Disabled))
        } finally { scene.close(); session.shutdown() }
    }

    @Test
    fun instanceDropdownSwitchesProfileWithoutOpeningManager() = runBlocking(Dispatchers.Swing) {
        val session = session()
        session.instances.create("Survival", "26.3", maximumMemoryMiB = 4096)
        val scene = ImageComposeScene(1440, 810, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            scene.text("Legacy Vanilla").click()
            scene.frames()
            scene.nodes().first { it.label().startsWith("Survival · ") }.click()
            scene.frames()
            assertNotNull(scene.text("SURVIVAL"))
            assertNotNull(scene.text("4 GB"))
            assertTrue(scene.text("PLAY MINECRAFT").boundsInRoot.bottom < 810)
        } finally { scene.close(); session.shutdown() }
    }

    @Test
    fun narrowAndScaledWindowsKeepPlayAndSettingsReachable() = runBlocking(Dispatchers.Swing) {
        for ((width, height, density) in listOf(Triple(440, 740, 1f), Triple(1200, 900, 1.5f),
            Triple(1024, 640, 1f), Triple(400, 400, 1f))) {
            val session = session()
            val scene = ImageComposeScene(width, height, density = Density(density),
                coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
            try {
                scene.awaitReady()
                scene.screenshot("home-${width}x${height}-${density}x")
                // The page has a scroll action when it cannot fit vertically; no fixed-width clipping.
                val play = scene.text("PLAY MINECRAFT")
                assertTrue(play.boundsInRoot.left >= 0)
                assertTrue(play.boundsInRoot.right <= width)
                if (width / density >= 760) {
                    assertTrue(play.boundsInRoot.bottom <= height, "Play must fit at $width x $height")
                } else {
                    val scroll = scene.nodes().first { it.config.contains(SemanticsActions.ScrollBy) }
                    scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1000f)
                    scene.frames()
                    scene.screenshot("home-${width}x${height}-scrolled")
                    val memory = scene.text("MEMORY ALLOCATION")
                    assertTrue(memory.boundsInRoot.bottom > 0 && memory.boundsInRoot.top < height,
                        "Settings must be reachable by scrolling")
                }
            } finally { scene.close(); session.shutdown() }
        }
    }

    @Test
    fun logoKeepsItsProportionsAndAvoidsTextDuringLiveResize() = runBlocking(Dispatchers.Swing) {
        for (density in listOf(1f, 1.5f, 2f)) {
            val scene = ImageComposeScene(2800, 1200, density = Density(density),
                coroutineContext = Dispatchers.Swing) {
                SyntaxTheme { LauncherHero("26.3", "A very long survival profile name", false) }
            }
            try {
                // Reuse the same composition: verify both directions across layout breakpoints.
                val sizes = listOf(1040 to 500, 658 to 330, 620 to 360, 580 to 400,
                    392 to 400, 300 to 270, 800 to 270, 1400 to 360, 1040 to 500)
                val asset = javax.imageio.ImageIO.read(javaClass.getResource("/branding/hero-mark.png"))
                val expectedRatio = asset.width.toFloat() / asset.height
                for ((width, height) in sizes) {
                    scene.constraints = Constraints(maxWidth = (width * density).toInt(),
                        maxHeight = (height * density).toInt())
                    scene.frames()
                    fun tagged(tag: String) = scene.nodes().first {
                        it.config.getOrNull(SemanticsProperties.TestTag) == tag
                    }
                    val hero = tagged("launcher-hero").boundsInRoot
                    val logo = tagged("hero-logo").boundsInRoot
                    val intro = tagged("hero-intro").boundsInRoot
                    val profile = tagged("hero-profile").boundsInRoot
                    val context = "$width x $height at $density density"
                    assertTrue(logo.width > 0 && logo.height > 0, "Visible logo: $context")
                    assertTrue(kotlin.math.abs(logo.width / logo.height - expectedRatio) < 0.015f,
                        "Original logo proportions: $context")
                    assertTrue(logo.left >= hero.left && logo.top >= hero.top &&
                        logo.right <= hero.right && logo.bottom <= hero.bottom, "Full logo: $context")
                    assertFalse(logo.overlaps(intro), "Logo overlaps heading: $context")
                    assertFalse(logo.overlaps(profile), "Logo overlaps profile: $context")
                    assertFalse(intro.overlaps(profile), "Heading overlaps profile: $context")
                }
            } finally { scene.close() }
        }
    }

    private suspend fun session(): DesktopLauncherSession {
        val state = Files.createTempDirectory(root, "profile-")
        val metadata = """{"id":"26.3","type":"release","mainClass":"fixture.Main",
            "javaVersion":{"component":"fixture","majorVersion":25}}""".trimIndent()
        val sha1 = MessageDigest.getInstance("SHA-1").digest(metadata.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val http = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.endsWith("version_manifest_v2.json")) """
                {"latest":{"release":"26.3","snapshot":"26.3"},"versions":[{
                 "id":"26.3","type":"release","url":"https://fixture.test/version.json",
                 "time":"2026-10-01T00:00:00Z","releaseTime":"2026-10-01T00:00:00Z","sha1":"$sha1"}]}
            """.trimIndent() else metadata)
        })
        val layout = MinecraftInstallationLayout(state.resolve("game"), state.resolve("versions"),
            state.resolve("libraries"), state.resolve("assets"), state.resolve("indexes"),
            state.resolve("objects"), state.resolve("logging"), state.resolve("natives"))
        val downloads = DownloadManager(http)
        val processes = MinecraftProcessManager(state.resolve("logs"))
        val preferences = LauncherPreferencesStore(state.resolve("settings.properties"))
        preferences.save(LauncherPreferences(nickname = "SyntaxPlayer", selectedVersionId = "26.3",
            maximumMemoryMiB = 8192))
        return DesktopLauncherSession(MinecraftVersionCatalog(http), MinecraftVersionResolver(http),
            MinecraftLaunchCoordinator(MinecraftPreparationService(downloads),
                MojangJavaRuntimeManager(http, downloads, state.resolve("runtimes")), LaunchPlanBuilder(),
                processes, layout, MinecraftPlatform.detect()), processes, preferences,
            MinecraftInstanceRepository(state.resolve("instances"), state.resolve("trash"), layout),
            layout, http)
    }

    private suspend fun DesktopLauncherSession.shutdown() {
        val closed = CompletableDeferred<Unit>()
        close { closed.complete(Unit) }
        withTimeout(5000) { closed.await() }
    }

    private suspend fun ImageComposeScene.awaitReady() {
        withTimeout(10000) {
            while (nodes().none { it.label() == "PLAY MINECRAFT" &&
                    !it.config.contains(SemanticsProperties.Disabled) }) {
                render().close()
                delay(20)
            }
        }
        frames()
    }

    private suspend fun ImageComposeScene.frames() {
        repeat(5) { render(System.nanoTime()).close(); delay(20) }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return semanticsOwners.flatMap { collect(it.rootSemanticsNode) }
    }

    private fun SemanticsNode.label(): String = config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }.orEmpty()
    private fun ImageComposeScene.text(label: String) = nodes().first { it.label() == label }
    private fun SemanticsNode.click() { assertTrue(config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun ImageComposeScene.screenshot(name: String) {
        val directory = Path.of("build/reports/ui").also(Files::createDirectories)
        render(System.nanoTime()).use { image ->
            Files.write(directory.resolve("$name.png"), image.encodeToData()!!.use { it.bytes })
        }
    }
}

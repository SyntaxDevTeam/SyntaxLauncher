package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiLanguage
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
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
import kotlin.time.Duration.Companion.milliseconds

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
            scene.text("My Vanilla game").click()
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
                    scene.scrollSettingsToEnd()
                    scene.screenshot("home-${width}x${height}-scrolled")
                    val memory = scene.text("MEMORY ALLOCATION")
                    memory.assertFullyVisible(height, "Settings at $width x $height, density $density")
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

    @Test
    fun translatedHomeKeepsPlayAndSetupUsableAcrossLanguagesAndWindowSizes() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(1440 to 810, 440 to 740)) {
            val session = session()
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxLauncherApp(session) }
            }
            try {
                scene.awaitReady(strings.text("ui.play_minecraft"))
                val play = scene.text(strings.text("ui.play_minecraft"))
                assertTrue(play.boundsInRoot.left >= 0 && play.boundsInRoot.right <= width,
                    "Play bounds for ${language.code} at $width x $height: ${play.boundsInRoot}")
                assertNotNull(scene.text(strings.text("ui.your_world")))
                assertNotNull(scene.text(strings.text("ui.your_adventure")))
                scene.screenshot("home-${language.code}-${width}x${height}")
                if (width < 760) {
                    scene.scrollSettingsToEnd()
                    val memory = scene.text(strings.text("ui.memory_allocation"))
                    memory.assertFullyVisible(height, "Settings for ${language.code} at $width x $height")
                    scene.screenshot("home-${language.code}-${width}x${height}-setup")
                }
            } finally { scene.close(); session.shutdown() }
        }
    }

    @Test
    fun gameTypeButtonsReuseQuiltAndRestoreSavedVanillaWithoutTouchingWorlds() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val vanilla = session.instances.create("My Vanilla", "26.3")
        val quilt = session.instances.create("My mods", "26.3", loader = ModLoaderSelection("quilt", "0.28.0"))
        val world = session.instances.gameDirectory(vanilla).resolve("saves/keep.txt")
        Files.createDirectories(world.parent)
        Files.writeString(world, "my world")
        session.preferences.save(session.preferences.load().copy(selectedInstanceId = vanilla.id))
        val scene = ImageComposeScene(1440, 810, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            assertNotNull(scene.text("MY VANILLA"))
            scene.text("With mods").click()
            scene.frames()
            assertNotNull(scene.text("MY MODS"))
            assertNotNull(scene.text("MINECRAFT WITH MODS"))
            assertNotNull(scene.text("Choose mods"))
            scene.screenshot("home-modded-1440x810")
            scene.text("Vanilla").click()
            scene.frames()
            assertNotNull(scene.text("MY VANILLA"))
            scene.text("With mods").click()
            scene.frames()
            assertNotNull(scene.text("MY MODS"))
            assertEquals(2, session.instances.list().instances.size)
            assertEquals(vanilla, session.instances.get(vanilla.id))
            assertEquals(quilt, session.instances.get(quilt.id))
            assertEquals("my world", Files.readString(world))
        } finally { scene.close(); session.shutdown() }
        assertEquals(quilt.id, session.preferences.load().selectedInstanceId)
    }

    @Test
    fun withModsOffersSetupAndCancelKeepsOriginalVanillaSelection() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val scene = ImageComposeScene(1440, 810, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            scene.text("With mods").click()
            scene.frames()
            assertNotNull(scene.text("Let’s play with mods"))
            assertTrue(session.instances.list().instances.isEmpty())
            scene.text("Cancel").click()
            scene.frames()
            assertNotNull(scene.text("My Vanilla game"))
            assertTrue(session.instances.list().instances.isEmpty())
        } finally { scene.close(); session.shutdown() }
        assertEquals("", session.preferences.load().selectedInstanceId)
    }

    private suspend fun session(): DesktopLauncherSession {
        val state = withContext(Dispatchers.IO) {
            Files.createTempDirectory(root, "profile-")
        }
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
        withTimeout(5000.milliseconds) { closed.await() }
    }

    private suspend fun ImageComposeScene.awaitReady(playLabel: String = "PLAY MINECRAFT") {
        withTimeout(10000.milliseconds) {
            while (nodes().none { it.label() == playLabel &&
                    !it.config.contains(SemanticsProperties.Disabled) }) {
                render().close()
                delay(20.milliseconds)
            }
        }
        frames()
    }

    private suspend fun ImageComposeScene.scrollSettingsToEnd() {
        // Compact home has a page scroll and an independently scrollable settings panel.
        val containers = nodes().filter { it.config.contains(SemanticsActions.ScrollBy) }
        assertTrue(containers.isNotEmpty(), "Expected scrollable home/settings")
        for (container in containers) {
            val range = container.config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(container.config[SemanticsActions.ScrollBy].action!!.invoke(0f, range.maxValue()))
            // ScrollBy animates asynchronously; a fixed number of frames depends on machine speed.
            withTimeout(5000.milliseconds) {
                do {
                    render(System.nanoTime()).close()
                    delay(20.milliseconds)
                } while (range.value() < range.maxValue() - 0.5f)
            }
        }
        frames()
    }

    private fun SemanticsNode.assertFullyVisible(height: Int, context: String) {
        val bounds = boundsInRoot
        assertTrue(bounds.top >= 0 && bounds.bottom <= height && size.height > 0 &&
            bounds.height >= size.height - 0.5f,
            "$context must show the full label: bounds=$bounds, size=$size, viewportHeight=$height")
    }

    private suspend fun ImageComposeScene.frames() {
        repeat(5) { render(System.nanoTime()).close(); delay(20.milliseconds) }
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

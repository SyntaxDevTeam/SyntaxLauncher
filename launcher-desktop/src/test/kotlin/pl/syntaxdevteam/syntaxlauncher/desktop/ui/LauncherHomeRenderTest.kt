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
    fun homeAllowsExplicitOfflinePlayerBeforeMicrosoftAppApproval() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val scene = ImageComposeScene(1440, 810, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            scene.screenshot("home-1440x810")
            assertTrue(scene.nodes().none { it.label() == "MEMORY ALLOCATION" })
            assertNotNull(scene.text("SyntaxPlayer · Offline / non-premium"))
            assertFalse(scene.text("PLAY MINECRAFT").config.contains(SemanticsProperties.Disabled))
            assertTrue(scene.nodes().none { it.label() == "Offline nickname" })
        } finally { scene.close(); session.shutdown() }
    }

    @Test fun emptyPlayerManagerCreatesAnExplicitPlayableOfflineProfile() = runBlocking(Dispatchers.Swing) {
        val session = session(emptyPlayers = true)
        val scene = ImageComposeScene(1440, 810, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            assertTrue(scene.text("PLAY MINECRAFT").config.contains(SemanticsProperties.Disabled))
            scene.text("Manage players").click()
            scene.frames()
            val field = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "player-nickname" }
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("TesterOne"))
            scene.frames()
            scene.text("Save player").click()
            withTimeout(5000) {
                while (session.savedAccounts.value.isEmpty()) { scene.render(System.nanoTime()).close(); delay(20) }
            }
            scene.frames()
            scene.screenshot("player-manager-dialog")
            scene.text("Done").click()
            scene.frames()
            assertNotNull(scene.text("TesterOne · Offline / non-premium"))
            assertFalse(scene.text("PLAY MINECRAFT").config.contains(SemanticsProperties.Disabled))
        } finally { scene.close(); session.shutdown() }
        assertEquals(pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount.create("TesterOne").uuid.toString(),
            session.preferences.load().selectedPlayerId)
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
            assertTrue(scene.nodes().none { it.label() == "MEMORY ALLOCATION" })
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
                    assertTrue(scene.nodes().none { it.label() == "MEMORY ALLOCATION" })
                }
            } finally { scene.close(); session.shutdown() }
        }
    }

    @Test
    fun landscapeOnlyHeroKeepsCopyVisibleDuringLiveResize() = runBlocking(Dispatchers.Swing) {
        for (density in listOf(1f, 1.5f, 2f)) {
            val scene = ImageComposeScene(2800, 1200, density = Density(density),
                coroutineContext = Dispatchers.Swing) {
                SyntaxTheme { LauncherHero("26.3", "A very long survival profile name", false) }
            }
            try {
                // Reuse the same composition: verify both directions across layout breakpoints.
                val sizes = listOf(1040 to 500, 658 to 330, 620 to 360, 580 to 400,
                    392 to 400, 300 to 270, 800 to 270, 1400 to 360, 1040 to 500)
                for ((width, height) in sizes) {
                    scene.constraints = Constraints(maxWidth = (width * density).toInt(),
                        maxHeight = (height * density).toInt())
                    scene.frames()
                    fun tagged(tag: String) = scene.nodes().first {
                        it.config.getOrNull(SemanticsProperties.TestTag) == tag
                    }
                    val hero = tagged("launcher-hero").boundsInRoot
                    val intro = tagged("hero-intro").boundsInRoot
                    val profile = tagged("hero-profile").boundsInRoot
                    val context = "$width x $height at $density density"
                    assertTrue(scene.nodes().none {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "hero-logo"
                    }, "Branding is confined to the header: $context")
                    for (copy in listOf(intro, profile)) {
                        assertTrue(copy.width > 0 && copy.height > 0, "Visible copy: $context")
                        assertTrue(copy.left >= hero.left && copy.top >= hero.top &&
                            copy.right <= hero.right && copy.bottom <= hero.bottom,
                            "Full hero copy: $context")
                    }
                    assertFalse(intro.overlaps(profile), "Heading overlaps profile: $context")
                }
            } finally { scene.close() }
        }
    }

    @Test
    fun translatedHomeKeepsPlayAndSetupUsableAcrossLanguagesAndWindowSizes() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(1440 to 810, 440 to 740, 400 to 500)) {
            val session = session()
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxLauncherApp(session, chromeInset = if (width == 400) androidx.compose.ui.unit.Dp(108f) else androidx.compose.ui.unit.Dp(0f)) }
            }
            try {
                scene.awaitReady(strings.text("ui.play_minecraft"))
                val play = scene.text(strings.text("ui.play_minecraft"))
                assertTrue(play.boundsInRoot.left >= 0 && play.boundsInRoot.right <= width,
                    "Play bounds for ${language.code} at $width x $height: ${play.boundsInRoot}")
                val settings = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "launcher-settings" }
                assertTrue(settings.boundsInRoot.top >= 0 && settings.boundsInRoot.bottom <= 84)
                assertTrue(settings.boundsInRoot.left >= 0 && settings.boundsInRoot.right <= width - if (width == 400) 108 else 0)
                val languageMenu = scene.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(strings.text("language.choose")) == true }
                assertTrue(languageMenu.boundsInRoot.right <= settings.boundsInRoot.left)
                assertEquals(1, scene.nodes().count { it.label() == strings.text("game.my_games") })
                assertTrue(scene.nodes().none { it.label() == strings.text("ui.memory_allocation") })
                assertTrue(scene.text(strings.text("game.style")).positionInRoot.y > scene.text(strings.text("ui.game_version")).positionInRoot.y)
                assertTrue(scene.nodes().none { it.label() == strings.text("ui.built_for_the_adventure") })
                assertNotNull(scene.text(strings.text("ui.your_world")))
                assertNotNull(scene.text(strings.text("ui.your_adventure")))
                scene.screenshot("home-${language.code}-${width}x${height}")
                if (width < 760) {
                    scene.scrollSettingsToEnd()
                    assertTrue(scene.nodes().none { it.label() == strings.text("ui.memory_allocation") })
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
            assertNotNull(scene.text("Choose extras"))
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

    @Test fun modpacksCanBeDiscoveredBeforeCreatingAnySavedGame() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(1440 to 810, 400 to 400)) {
            val session = session(packCatalog = true)
            val strings = UiStrings(language)
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxLauncherApp(session) }
            }
            try {
                scene.awaitReady(strings.text("ui.play_minecraft"))
                fun packButton() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "browse-modpacks" }
                withTimeout(10000) {
                    while (packButton().boundsInRoot.height < packButton().size.height - 0.5f) {
                        // Bring the button into each nested viewport. Scrolling both containers
                        // by a fixed amount can overshoot the short German settings panel.
                        scene.nodes().filter { it.config.contains(SemanticsActions.ScrollBy) }.reversed().forEach { container ->
                            val top = packButton().positionInRoot.y
                            val bottom = top + packButton().size.height
                            val viewport = container.boundsInRoot
                            val delta = when {
                                viewport.height < 40 -> 0f
                                top < viewport.top -> top - viewport.top - 8f
                                bottom > viewport.bottom -> bottom - viewport.bottom + 8f
                                else -> 0f
                            }
                            if (delta != 0f) container.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta)
                        }
                        scene.frames()
                    }
                }
                val button = packButton()
                assertFalse(button.config.contains(SemanticsProperties.Disabled))
                assertTrue(session.instances.list().instances.isEmpty())
                assertTrue(button.boundsInRoot.left >= 0 && button.boundsInRoot.right <= width && button.boundsInRoot.bottom <= height)
                scene.screenshot("home-modpacks-${language.code}-$width")
            } finally { scene.close(); session.shutdown() }
        }
    }

    @Test
    fun savedOfflinePlayerIsSelectableInEveryLanguage() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) {
            val session = session(savedPlayer = true)
            val strings = UiStrings(language)
            val scene = ImageComposeScene(1440, 900, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxLauncherApp(session) }
            }
            try {
                scene.awaitReady(strings.text("ui.play_minecraft"))
                scene.text("SyntaxPlayer · " + strings.text("players.offline")).click()
                scene.frames()
                scene.text("ChildTwo · " + strings.text("players.offline")).click()
                scene.frames()
                assertFalse(scene.text(strings.text("ui.play_minecraft")).config.contains(SemanticsProperties.Disabled))
                delay(500)
                assertEquals(pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount.create("ChildTwo").uuid.toString(),
                    session.preferences.load().selectedPlayerId)
                scene.screenshot("home-saved-player-${language.code}")
            } finally { scene.close(); session.shutdown() }
        }
    }

    @Test fun profileNavigationUsesMainPageAndShowsContentsWithoutChangingWorlds() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(1440 to 810, 400 to 400)) {
            val strings = UiStrings(language)
            val session = session()
            val profile = session.instances.create("Adventure", "26.3")
            val game = session.instances.gameDirectory(profile)
            Files.createDirectories(game.resolve("saves/My world"))
            Files.writeString(game.resolve("saves/My world/level.dat"), "keep")
            Files.createDirectories(game.resolve("resourcepacks")); Files.writeString(game.resolve("resourcepacks/Blue.zip"), "zip")
            session.preferences.save(session.preferences.load().copy(selectedInstanceId = profile.id))
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides strings) { SyntaxLauncherApp(session) }
            }
            try {
                scene.awaitReady(strings.text("ui.play_minecraft"))
                scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-profiles" }.click()
                withTimeout(5000) {
                    while (scene.nodes().none { it.label().contains("My world") }) { scene.render(System.nanoTime()).close(); delay(20) }
                }
                assertTrue(scene.nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "profiles-page" })
                assertTrue(scene.nodes().none { it.label() == strings.text("ui.play_minecraft") })
                assertTrue(scene.nodes().any { it.label() == strings.text("profiles.create") })
                assertTrue(scene.nodes().any { it.label().contains("Blue.zip") })
                val create = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "create-profile" }
                assertTrue(create.boundsInRoot.height >= 40 && create.boundsInRoot.right <= width)
                scene.screenshot("profiles-${language.code}-$width")
                scene.text(strings.text("ui.home")).click(); scene.frames()
                assertNotNull(scene.text(strings.text("ui.play_minecraft")))
                assertEquals("keep", Files.readString(game.resolve("saves/My world/level.dat")))
                assertEquals(profile, session.instances.get(profile.id))
            } finally { scene.close(); session.shutdown() }
        }
    }

    @Test fun settingsUseActiveProfileMemoryAndPersistWithoutChangingOtherGames() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val active = session.instances.create("Active", "26.3", maximumMemoryMiB = 4096)
        val other = session.instances.create("Other", "26.3", maximumMemoryMiB = 2048)
        session.preferences.save(session.preferences.load().copy(selectedInstanceId = active.id))
        val scene = ImageComposeScene(1440, 900, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "launcher-settings" }.click()
            scene.frames()
            assertNotNull(scene.text("MEMORY ALLOCATION"))
            val slider = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "settings-memory-slider" }
            slider.config[SemanticsActions.SetProgress].action!!.invoke(6144f)
            scene.frames()
            scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "settings-memory-save" }.click()
            withTimeout(5000) {
                while (session.instances.get(active.id).maximumMemoryMiB != 6144) { scene.render(System.nanoTime()).close(); delay(20) }
            }
            scene.frames(); scene.screenshot("settings-memory")
            assertEquals(2048, session.instances.get(other.id).maximumMemoryMiB)
            assertEquals(8192, session.preferences.load().maximumMemoryMiB)
        } finally { scene.close(); session.shutdown() }
    }

    @Test fun legacyMemoryDraftIsDiscardedOnCloseAndSaveSurvivesRestart() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val scene = ImageComposeScene(1440, 900, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            fun gear() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "launcher-settings" }
            fun slider() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "settings-memory-slider" }
            gear().click(); scene.frames()
            slider().config[SemanticsActions.SetProgress].action!!.invoke(5120f); scene.frames()
            scene.text("Close").click(); scene.frames()
            assertEquals(8192, session.preferences.load().maximumMemoryMiB)
            gear().click(); scene.frames()
            assertNotNull(scene.text("8192 MiB · 8.0 GB"))
            slider().config[SemanticsActions.SetProgress].action!!.invoke(5120f); scene.frames()
            scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "settings-memory-save" }.click()
            withTimeout(5000) { while (session.preferences.load().maximumMemoryMiB != 5120) { scene.render(System.nanoTime()).close(); delay(20) } }
        } finally { scene.close(); session.shutdown() }
        assertEquals(5120, session.preferences.load().maximumMemoryMiB)
    }

    @Test fun creationWizardFitsInsideTheActualMinimumLauncherWindow() = runBlocking(Dispatchers.Swing) {
        val session = session()
        val scene = ImageComposeScene(400, 400, coroutineContext = Dispatchers.Swing) { SyntaxLauncherApp(session) }
        try {
            scene.awaitReady()
            scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "nav-profiles" }.click(); scene.frames()
            scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "create-profile" }.click(); scene.frames()
            fun next() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "wizard-next" }
            next().assertFullyVisible(400, "Wizard footer")
            next().click(); scene.frames()
            assertNotNull(scene.text("✓ Empty — I will add extras later"))
            next().click(); scene.frames()
            val name = scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "wizard-name" }
            name.assertFullyVisible(400, "Wizard name")
            name.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Small adventure")); scene.frames()
            next().click(); scene.frames()
            assertTrue(session.instances.list().instances.isEmpty())
            scene.screenshot("wizard-inside-400")
            next().click()
            withTimeout(5000) { while (session.instances.list().instances.isEmpty()) { scene.render(System.nanoTime()).close(); delay(20) } }
            assertEquals("Small adventure", session.instances.list().instances.single().name)
        } finally { scene.close(); session.shutdown() }
    }

    private suspend fun session(packCatalog: Boolean = false, savedPlayer: Boolean = false, emptyPlayers: Boolean = false): DesktopLauncherSession {
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
        preferences.save(LauncherPreferences(nickname = if (emptyPlayers) "" else "SyntaxPlayer", selectedVersionId = "26.3",
            maximumMemoryMiB = 8192))
        val accounts = pl.syntaxdevteam.syntaxlauncher.core.account.AccountManager(
            state.resolve("accounts.properties"), object : pl.syntaxdevteam.syntaxlauncher.core.account.RefreshTokenVault {
                override fun read(key: String): String? = error("Offline UI must not read credentials")
                override fun write(key: String, token: String) = error("Offline UI must not write credentials")
                override fun remove(key: String) = error("Offline UI must not remove credentials")
            }, "fixture-app", { error("Offline UI must not refresh credentials") })
        if (!emptyPlayers) accounts.saveOffline("SyntaxPlayer")
        if (savedPlayer) accounts.saveOffline("ChildTwo")
        return DesktopLauncherSession(MinecraftVersionCatalog(http), MinecraftVersionResolver(http),
            MinecraftLaunchCoordinator(MinecraftPreparationService(downloads),
                MojangJavaRuntimeManager(http, downloads, state.resolve("runtimes")), LaunchPlanBuilder(),
                processes, layout, MinecraftPlatform.detect()), processes, preferences,
            MinecraftInstanceRepository(state.resolve("instances"), state.resolve("trash"), layout),
            layout, http, accounts = accounts,
            microsoftSignIn = object : pl.syntaxdevteam.syntaxlauncher.desktop.MicrosoftSignInService {
                override val clientId = "fixture-app"
                override suspend fun signIn(): pl.syntaxdevteam.syntaxlauncher.core.account.PremiumSession = error("Fixture sign-in")
                override suspend fun refresh(refreshToken: String): pl.syntaxdevteam.syntaxlauncher.core.account.PremiumSession = error("Fixture refresh")
            }, loaders = if (packCatalog) pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderRegistry(emptyList()) else null)
    }

    private suspend fun DesktopLauncherSession.shutdown() {
        val closed = CompletableDeferred<Unit>()
        close { closed.complete(Unit) }
        withTimeout(5000.milliseconds) { closed.await() }
    }

    private suspend fun ImageComposeScene.awaitReady(playLabel: String = "PLAY MINECRAFT") {
        withTimeout(10000.milliseconds) {
            while (nodes().none { it.label() == playLabel }) {
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

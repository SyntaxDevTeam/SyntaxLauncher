package pl.syntaxdevteam.syntaxlauncher.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.GraphicsEnvironment
import pl.syntaxdevteam.syntaxlauncher.core.java.MojangJavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderRegistry
import pl.syntaxdevteam.syntaxlauncher.core.loader.FabricLoaderProvider
import pl.syntaxdevteam.syntaxlauncher.core.loader.QuiltLoaderProvider
import pl.syntaxdevteam.syntaxlauncher.core.loader.NeoForgeLoaderProvider
import pl.syntaxdevteam.syntaxlauncher.core.loader.ForgeLoaderProvider
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchPlanBuilder
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftLaunchCoordinator
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessManager
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPreparationService
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionResolver
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.LauncherHttpClientFactory
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LauncherLanguageHost
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.SyntaxLauncherApp
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.StartupUpdateGate
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.WindowControls
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.WindowResizeHandles
import pl.syntaxdevteam.syntaxlauncher.core.update.UpdatePlatform
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdater
import pl.syntaxdevteam.syntaxlauncher.core.update.GitHubLauncherReleases
import pl.syntaxdevteam.syntaxlauncher.core.update.LauncherVersion

fun main(args: Array<String>) {
    val paths = LauncherPaths.resolve()
    val platform = MinecraftPlatform.detect()
    val httpClient = LauncherHttpClientFactory.create()
    val downloader = DownloadManager(httpClient)
    val updateRoot = paths.dataDir.resolve("launcher-updates")
    val preferences = LauncherPreferencesStore(paths.configDir.resolve("settings.properties"))
    val handoff = DesktopUpdateHandoff.parse(args, updateRoot)
    val updatePlatform = UpdatePlatform.detect()
    val updater = if (System.getProperty("jpackage.app-path") != null && updatePlatform != null) {
        StartupUpdater(
            GitHubLauncherReleases(httpClient), downloader,
            updateRoot, paths.cacheDir.resolve("launcher-updates"), updatePlatform,
            LauncherVersion.parse(LauncherBuildInfo.version),
            selectedChannel = { preferences.loadUpdateChannel() },
            currentChannel = LauncherBuildInfo.updateChannel, currentBuild = LauncherBuildInfo.revision)
    } else null
    val processManager = MinecraftProcessManager(paths.logsDir)
    val modLoaders = ModLoaderRegistry(listOf(
        FabricLoaderProvider(httpClient), QuiltLoaderProvider(httpClient),
        NeoForgeLoaderProvider(httpClient, downloader), ForgeLoaderProvider(httpClient, downloader),
    ))
    val session = DesktopLauncherSession(
        versionCatalog = MinecraftVersionCatalog(
            httpClient, cacheFile = paths.cacheDir.resolve("minecraft/version_manifest_v2.json")),
        versionResolver = MinecraftVersionResolver(httpClient),
        coordinator = MinecraftLaunchCoordinator(
            preparation = MinecraftPreparationService(downloader),
            runtimes = MojangJavaRuntimeManager(httpClient, downloader, paths.runtimesDir),
            plans = LaunchPlanBuilder(),
            processes = processManager,
            layout = MinecraftInstallationLayout.from(paths),
            platform = platform,
            loaders = modLoaders,
        ),
        processManager = processManager,
        preferences = preferences,
        legacyLayout = MinecraftInstallationLayout.from(paths),
        instances = MinecraftInstanceRepository(
            root = paths.dataDir.resolve("instances"),
            trash = paths.dataDir.resolve("instance-trash"),
            sharedLayout = MinecraftInstallationLayout.from(paths),
        ),
        httpClient = httpClient,
        loaders = modLoaders,
        microsoftSignIn = System.getenv("SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID")
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { clientId -> runCatching { DesktopMicrosoftSignIn(httpClient, clientId) }.getOrNull() },
    )

    application {
        // Respect smaller laptop desktops; short windows remain fully usable via sidebar scrolling.
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // Never create a window larger than the actual usable desktop bounds.
        val initialWidth = minOf(1440, (screen.width - 40).coerceAtLeast(400)).dp
        val initialHeight = minOf(840, (screen.height - 40).coerceAtLeast(400)).dp
        val windowState = rememberWindowState(width = initialWidth, height = initialHeight)
        val closeLauncher = { session.close { exitApplication() } }
        Window(
            onCloseRequest = closeLauncher,
            title = "SyntaxLauncher · v" + LauncherBuildInfo.version + " · " + LauncherBuildInfo.buildLabel,
            icon = painterResource("branding/syntaxlauncher-icon.png"),
            state = windowState,
            undecorated = true,
            resizable = true,
        ) {
            window.minimumSize = java.awt.Dimension(400, 400)
            LauncherLanguageHost(session.preferences) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                StartupUpdateGate(updater, handoff, onTransferred = closeLauncher) {
                    SyntaxLauncherApp(
                        session = session,
                        chromeInset = 144.dp,
                        headerDragArea = { content -> WindowDraggableArea { content() } },
                    )
                }
                WindowResizeHandles(window, windowState.placement == WindowPlacement.Maximized)
                WindowControls(
                    state = windowState,
                    onClose = closeLauncher,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = if (maxWidth < 760.dp) 18.dp else 24.dp),
                )
            }
            }
        }
    }
}

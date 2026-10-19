package pl.syntaxdevteam.syntaxlauncher.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.GraphicsEnvironment
import pl.syntaxdevteam.syntaxlauncher.core.java.MojangJavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
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
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.SyntaxLauncherApp
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.StartupUpdateGate
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
    val handoff = DesktopUpdateHandoff.parse(args, updateRoot)
    val updatePlatform = UpdatePlatform.detect()
    val updater = if (System.getProperty("jpackage.app-path") != null && updatePlatform != null) {
        StartupUpdater(
            GitHubLauncherReleases(httpClient), downloader,
            updateRoot, paths.cacheDir.resolve("launcher-updates"), updatePlatform,
            LauncherVersion.parse(LauncherBuildInfo.version))
    } else null
    val processManager = MinecraftProcessManager(paths.logsDir)
    val session = DesktopLauncherSession(
        versionCatalog = MinecraftVersionCatalog(httpClient),
        versionResolver = MinecraftVersionResolver(httpClient),
        coordinator = MinecraftLaunchCoordinator(
            preparation = MinecraftPreparationService(downloader),
            runtimes = MojangJavaRuntimeManager(httpClient, downloader, paths.runtimesDir),
            plans = LaunchPlanBuilder(),
            processes = processManager,
            layout = MinecraftInstallationLayout.from(paths),
            platform = platform,
        ),
        processManager = processManager,
        preferences = LauncherPreferencesStore(paths.configDir.resolve("settings.properties")),
        legacyLayout = MinecraftInstallationLayout.from(paths),
        instances = MinecraftInstanceRepository(
            root = paths.dataDir.resolve("instances"),
            trash = paths.dataDir.resolve("instance-trash"),
            sharedLayout = MinecraftInstallationLayout.from(paths),
        ),
        httpClient = httpClient,
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
        Window(
            onCloseRequest = { session.close { exitApplication() } },
            title = "SyntaxLauncher",
            icon = painterResource("branding/syntaxlauncher-icon.png"),
            state = WindowState(width = initialWidth, height = initialHeight),
        ) {
            window.minimumSize = java.awt.Dimension(400, 400)
            StartupUpdateGate(updater, handoff,
                onTransferred = { session.close { exitApplication() } }) {
                SyntaxLauncherApp(session)
            }
        }
    }
}

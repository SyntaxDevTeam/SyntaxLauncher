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

fun main() {
    val paths = LauncherPaths.resolve()
    val platform = MinecraftPlatform.detect()
    val httpClient = LauncherHttpClientFactory.create()
    val downloader = DownloadManager(httpClient)
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
    )

    application {
        // Respect smaller laptop desktops; short windows remain fully usable via sidebar scrolling.
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val initialWidth = minOf(1320, (screen.width - 64).coerceAtLeast(920)).dp
        val initialHeight = minOf(860, (screen.height - 64).coerceAtLeast(640)).dp
        Window(
            onCloseRequest = { session.close { exitApplication() } },
            title = "SyntaxLauncher",
            icon = painterResource("branding/syntaxlauncher-icon.png"),
            state = WindowState(width = initialWidth, height = initialHeight),
        ) {
            SyntaxLauncherApp(session)
        }
    }
}

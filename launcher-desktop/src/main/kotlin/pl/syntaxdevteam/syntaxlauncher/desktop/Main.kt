package pl.syntaxdevteam.syntaxlauncher.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.network.LauncherHttpClientFactory
import pl.syntaxdevteam.syntaxlauncher.desktop.ui.SyntaxLauncherApp

fun main() {
    val httpClient = LauncherHttpClientFactory.create()
    val versionCatalog = MinecraftVersionCatalog(httpClient)

    application {
        Window(
            onCloseRequest = {
                httpClient.close()
                exitApplication()
            },
            title = "SyntaxLauncher",
            state = WindowState(width = 1080.dp, height = 720.dp),
        ) {
            SyntaxLauncherApp(versionCatalog)
        }
    }
}

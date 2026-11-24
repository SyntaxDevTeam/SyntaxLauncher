package pl.syntaxdevteam.syntaxlauncher.core.storage

import java.nio.file.Path
import kotlin.io.path.Path

data class LauncherPaths(
    val dataDir: Path,
    val configDir: Path,
    val cacheDir: Path,
    val logsDir: Path,
    val runtimesDir: Path,
) {
    companion object {
        fun resolve(
            environment: Map<String, String> = System.getenv(),
            userHome: Path = Path(System.getProperty("user.home")),
            osName: String = System.getProperty("os.name"),
        ): LauncherPaths {
            val os = osName.lowercase()

            return when {
                os.contains("win") -> windows(environment, userHome)
                os.contains("mac") || os.contains("darwin") -> macOs(userHome)
                else -> unix(environment, userHome)
            }
        }

        private fun windows(
            environment: Map<String, String>,
            userHome: Path,
        ): LauncherPaths {
            val localAppData = environment["LOCALAPPDATA"]
                ?.takeIf(String::isNotBlank)
                ?.let(::Path)
                ?: userHome.resolve("AppData").resolve("Local")

            val root = localAppData.resolve("SyntaxLauncher")
            return fromSingleRoot(root)
        }

        private fun macOs(userHome: Path): LauncherPaths {
            val data = userHome.resolve("Library").resolve("Application Support").resolve("SyntaxLauncher")
            val cache = userHome.resolve("Library").resolve("Caches").resolve("SyntaxLauncher")
            val logs = userHome.resolve("Library").resolve("Logs").resolve("SyntaxLauncher")

            return LauncherPaths(
                dataDir = data,
                configDir = data.resolve("config"),
                cacheDir = cache,
                logsDir = logs,
                runtimesDir = data.resolve("runtimes"),
            )
        }

        private fun unix(
            environment: Map<String, String>,
            userHome: Path,
        ): LauncherPaths {
            val dataHome = environment.pathOrFallback("XDG_DATA_HOME", userHome.resolve(".local").resolve("share"))
            val configHome = environment.pathOrFallback("XDG_CONFIG_HOME", userHome.resolve(".config"))
            val cacheHome = environment.pathOrFallback("XDG_CACHE_HOME", userHome.resolve(".cache"))
            val stateHome = environment.pathOrFallback("XDG_STATE_HOME", userHome.resolve(".local").resolve("state"))

            return LauncherPaths(
                dataDir = dataHome.resolve("syntaxlauncher"),
                configDir = configHome.resolve("syntaxlauncher"),
                cacheDir = cacheHome.resolve("syntaxlauncher"),
                logsDir = stateHome.resolve("syntaxlauncher").resolve("logs"),
                runtimesDir = dataHome.resolve("syntaxlauncher").resolve("runtimes"),
            )
        }

        private fun fromSingleRoot(root: Path): LauncherPaths =
            LauncherPaths(
                dataDir = root,
                configDir = root.resolve("config"),
                cacheDir = root.resolve("cache"),
                logsDir = root.resolve("logs"),
                runtimesDir = root.resolve("runtimes"),
            )

        private fun Map<String, String>.pathOrFallback(
            key: String,
            fallback: Path,
        ): Path =
            get(key)
                ?.takeIf(String::isNotBlank)
                ?.let(::Path)
                ?: fallback
    }
}

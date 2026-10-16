package pl.syntaxdevteam.syntaxlauncher.core.storage

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherPathsTest {
    @Test
    fun `uses XDG directories on Linux`() {
        val paths = LauncherPaths.resolve(
            environment = mapOf(
                "XDG_DATA_HOME" to "/data",
                "XDG_CONFIG_HOME" to "/config",
                "XDG_CACHE_HOME" to "/cache",
                "XDG_STATE_HOME" to "/state",
            ),
            userHome = Path("/home/test"),
            osName = "Linux",
        )

        assertEquals(Path("/data/syntaxlauncher"), paths.dataDir)
        assertEquals(Path("/config/syntaxlauncher"), paths.configDir)
        assertEquals(Path("/cache/syntaxlauncher"), paths.cacheDir)
        assertEquals(Path("/state/syntaxlauncher/logs"), paths.logsDir)
        assertEquals(Path("/data/syntaxlauncher/runtimes"), paths.runtimesDir)
    }

    @Test
    fun `uses LocalAppData on Windows`() {
        val paths = LauncherPaths.resolve(
            environment = mapOf("LOCALAPPDATA" to "C:/Users/Test/AppData/Local"),
            userHome = Path("C:/Users/Test"),
            osName = "Windows 11",
        )

        assertEquals(
            Path("C:/Users/Test/AppData/Local").resolve("SyntaxLauncher"),
            paths.dataDir,
        )
    }
}

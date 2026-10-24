package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumAccount
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArgument
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArchitecture
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftDownloadKind
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftFilePlan
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLoggingConfiguration
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLoggingFile
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.PlannedMinecraftDownload
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.PreparedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

class LaunchPlanBuilderTest {
    private val account = PremiumAccount("SyntaxUser", java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc"))
    private val verified = LaunchAuthentication("fixture-access", "fixture-session", "msa", clientId = "fixture-client", expiresAt = java.time.Instant.MAX)
    private val platform = MinecraftPlatform(
        os = MinecraftOperatingSystem.LINUX,
        architecture = MinecraftArchitecture.X86_64,
        osVersion = "6.0",
        rawArchitecture = "amd64",
    )

    @Test
    fun `builds modern metadata driven launch plan`() {
        val root = Path.of("root with spaces")
        val loggingTarget = root.resolve("assets/log_configs/client.xml")
        val version = ResolvedMinecraftVersion(
            id = "26.2",
            type = MinecraftVersionType.RELEASE,
            mainClass = "net.minecraft.client.main.Main",
            assetsId = "26",
            assetIndex = null,
            clientDownload = null,
            libraries = emptyList(),
            arguments = MinecraftArguments(
                jvm = listOf(
                    MinecraftArgument(listOf("-Djava.library.path=${'$'}{natives_directory}")),
                    MinecraftArgument(listOf("-Dminecraft.launcher.brand=${'$'}{launcher_name}")),
                    MinecraftArgument(listOf("-Dminecraft.launcher.version=${'$'}{launcher_version}")),
                    MinecraftArgument(listOf("-cp", "${'$'}{classpath}")),
                ),
                game = listOf(
                    MinecraftArgument(
                        listOf(
                            "--username", "${'$'}{auth_player_name}",
                            "--version", "${'$'}{version_name}",
                            "--gameDir", "${'$'}{game_directory}",
                            "--assetsDir", "${'$'}{assets_root}",
                            "--assetIndex", "${'$'}{assets_index_name}",
                            "--uuid", "${'$'}{auth_uuid}",
                            "--accessToken", "${'$'}{auth_access_token}",
                            "--userType", "${'$'}{user_type}",
                            "--versionType", "${'$'}{version_type}",
                        ),
                    ),
                ),
            ),
            javaRequirement = null,
            logging = MinecraftLoggingConfiguration(
                argument = "-Dlog4j.configurationFile=${'$'}{path}",
                file = MinecraftLoggingFile(
                    id = "client.xml",
                    sha1 = "sha1",
                    size = 1,
                    url = "https://example.invalid/client.xml",
                ),
            ),
        )
        val classpath = listOf(
            root.resolve("libraries/example.jar"),
            root.resolve("versions/26.2/26.2.jar"),
        )
        val prepared = prepared(version, root, classpath, loggingTarget)
        val runtime = JavaRuntime(
            home = root.resolve("runtime"),
            executable = root.resolve("runtime/bin/java"),
            majorVersion = 21,
            versionString = "21.0.8",
        )

        val plan = LaunchPlanBuilder(launcherVersion = "0.1-test").build(
            LaunchRequest(
                account = account,
                prepared = prepared,
                runtime = runtime,
                platform = platform,
                options = LaunchOptions(
                    minimumMemoryMiB = 1024,
                    maximumMemoryMiB = 4096,
                    extraJvmArguments = listOf("-Dsyntax.test=true"),
                ),
                authentication = verified,
            ),
        )

        assertEquals(root.resolve("game"), plan.workingDirectory)
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertTrue("-Xms1024M" in plan.jvmArguments)
        assertTrue("-Xmx4096M" in plan.jvmArguments)
        assertTrue("-Djava.library.path=${root.resolve("natives")}" in plan.jvmArguments)
        assertTrue("-Dminecraft.launcher.brand=SyntaxLauncher" in plan.jvmArguments)
        assertTrue("-Dminecraft.launcher.version=0.1-test" in plan.jvmArguments)
        assertTrue("-Dlog4j.configurationFile=$loggingTarget" in plan.jvmArguments)
        assertTrue("-Dsyntax.test=true" in plan.jvmArguments)
        assertTrue(classpath.joinToString(":") { it.toString() } in plan.jvmArguments)
        assertTrue(account.username in plan.gameArguments)
        assertTrue(account.uuid.toString().replace("-", "") in plan.gameArguments)
        assertTrue("fixture-access" in plan.gameArguments)
        assertEquals(runtime.executable.toString(), plan.command.first())
        assertFalse(plan.command.any { "${'$'}{" in it })
        assertTrue(root.resolve("game").toString() in plan.gameArguments)
    }

    @Test
    fun `adds legacy classpath and natives when metadata has no jvm arguments`() {
        val root = Path.of("legacy")
        val version = ResolvedMinecraftVersion(
            id = "1.6.4",
            type = MinecraftVersionType.RELEASE,
            mainClass = "net.minecraft.client.main.Main",
            assetsId = "legacy",
            assetIndex = null,
            clientDownload = null,
            libraries = emptyList(),
            arguments = MinecraftArguments(
                legacyGameArguments = "--username ${'$'}{auth_player_name} --session ${'$'}{auth_session} --gameDir ${'$'}{game_directory}",
            ),
            javaRequirement = null,
            logging = null,
        )
        val classpath = listOf(root.resolve("client.jar"))
        val prepared = prepared(version, root, classpath, loggingTarget = null)
        val plan = LaunchPlanBuilder().build(
            LaunchRequest(
                account = account,
                prepared = prepared,
                runtime = JavaRuntime(
                    home = root.resolve("runtime"),
                    executable = root.resolve("runtime/bin/java"),
                    majorVersion = 8,
                    versionString = "1.8.0_472",
                ),
                platform = platform,
                options = LaunchOptions(),
                    authentication = verified,
            ),
        )

        assertTrue("-Djava.library.path=${root.resolve("natives")}" in plan.jvmArguments)
        assertTrue("-cp" in plan.jvmArguments)
        assertTrue(classpath.single().toString() in plan.jvmArguments)
        assertTrue("fixture-session" in plan.gameArguments)
    }

    @Test
    fun `fails explicitly for unknown placeholder`() {
        val root = Path.of("unknown")
        val version = ResolvedMinecraftVersion(
            id = "test",
            type = MinecraftVersionType.RELEASE,
            mainClass = "Main",
            assetsId = null,
            assetIndex = null,
            clientDownload = null,
            libraries = emptyList(),
            arguments = MinecraftArguments(
                game = listOf(MinecraftArgument(listOf("--future", "${'$'}{future_placeholder}"))),
            ),
            javaRequirement = null,
            logging = null,
        )

        assertFailsWith<UnresolvedLaunchPlaceholderException> {
            LaunchPlanBuilder().build(
                LaunchRequest(
                    account = account,
                    prepared = prepared(version, root, listOf(root.resolve("client.jar")), null),
                    runtime = JavaRuntime(
                        home = root.resolve("runtime"),
                        executable = root.resolve("runtime/bin/java"),
                        majorVersion = 21,
                        versionString = "21",
                    ),
                    platform = platform,
                    options = LaunchOptions(),
                    authentication = verified,
                ),
            )
        }
    }

    @Test fun tokenExpiringDuringPreparationCannotBuildAProcessPlan() {
        val expires = java.time.Instant.parse("2026-10-24T12:00:00Z")
        val root = Path.of("fixture")
        val version = ResolvedMinecraftVersion(id = "fixture", type = MinecraftVersionType.RELEASE,
            mainClass = "Main", assetsId = null, assetIndex = null, clientDownload = null,
            libraries = emptyList(), arguments = MinecraftArguments(), javaRequirement = null, logging = null)
        val request = LaunchRequest(account, prepared(version, root, listOf(root.resolve("client.jar")), null),
            JavaRuntime(root, root.resolve("bin/java"), 21, "21"), platform, LaunchOptions(),
            verified.copy(expiresAt = expires))
        LaunchPlanBuilder(clock = java.time.Clock.fixed(expires.minusSeconds(1), java.time.ZoneOffset.UTC)).build(request)
        for (now in listOf(expires, expires.plusSeconds(1))) {
            assertFailsWith<IllegalArgumentException> {
                LaunchPlanBuilder(clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC)).build(request)
            }
        }
    }

    private fun prepared(
        version: ResolvedMinecraftVersion,
        root: Path,
        classpath: List<Path>,
        loggingTarget: Path?,
    ): PreparedMinecraftVersion =
        PreparedMinecraftVersion(
            version = version,
            filePlan = MinecraftFilePlan(
                client = null,
                libraries = emptyList(),
                natives = emptyList(),
                assetIndex = null,
                loggingConfig = loggingTarget?.let {
                    PlannedMinecraftDownload(
                        kind = MinecraftDownloadKind.LOGGING_CONFIG,
                        url = "https://example.invalid/client.xml",
                        target = it,
                        sha1 = "sha1",
                        size = 1,
                    )
                },
                classpath = classpath,
            ),
            assetIndex = null,
            downloadResults = emptyList(),
            gameDirectory = root.resolve("game"),
            librariesDirectory = root.resolve("libraries"),
            assetsDirectory = root.resolve("assets"),
            nativesDirectory = root.resolve("natives"),
        )
}

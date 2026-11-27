import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains:markdown-jvm:0.7.3")
    implementation("org.jsoup:jsoup:1.24.1")
    implementation(project(":launcher-core"))
    // Desktop itself owns HttpClient lifecycle and therefore needs this API directly.
    implementation("io.ktor:ktor-client-core:3.6.2")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("ch.qos.logback:logback-classic:1.6.8")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-client-mock:3.6.2")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val launcherVersion = providers.gradleProperty("launcherVersion")
val nativePackageVersion = launcherVersion.map { product ->
    val numeric = product.substringBefore('-').substringBefore('+')
    val parts = numeric.split('.')
    require(parts.size == 3 && parts.all { it.matches(Regex("(0|[1-9][0-9]*)")) }) {
        "launcherVersion must contain a numeric SemVer core."
    }
    numeric
}
val launcherBuildNumber = providers.gradleProperty("launcherBuildNumber")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER")).orElse("local")
val launcherCommit = providers.gradleProperty("launcherCommit")
    .orElse(providers.environmentVariable("GITHUB_SHA")).orElse("unknown")
val discordApplicationId = providers.gradleProperty("discordApplicationId")
    .orElse(providers.environmentVariable("SYNTAXLAUNCHER_DISCORD_APPLICATION_ID").filter { it.isNotBlank() })
    .orElse("1575963809129037937")
abstract class GenerateLauncherBuildInfo : DefaultTask() {
    @get:Input abstract val releaseVersion: Property<String>
    @get:Input abstract val buildNumber: Property<String>
    @get:Input abstract val commitSha: Property<String>
    @get:Input abstract val updateChannel: Property<String>
    @get:Input abstract val buildAttempt: Property<String>
    @get:Input abstract val discordId: Property<String>
    @get:OutputDirectory abstract val destination: DirectoryProperty

    @TaskAction fun generate() {
        val value = releaseVersion.get()
        require(value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?(?:\\+[A-Za-z0-9.-]+)?"))) {
            "launcherVersion must be a semantic version."
        }
        val channel = updateChannel.get()
        val attempt = buildAttempt.get()
        require(channel in setOf("stable", "snapshot", "dev"))
        require(attempt.matches(Regex("[1-9][0-9]*")))
        val build = buildNumber.get()
        val commit = commitSha.get()
        val discord = discordId.get().trim()
        require(discord.isEmpty() || (discord.matches(Regex("[1-9][0-9]{16,19}")) && discord.toULongOrNull() != null)) {
            "discordApplicationId must contain a Discord Application ID."
        }
        require(build == "local" || build.matches(Regex("[1-9][0-9]*"))) {
            "launcherBuildNumber must be 'local' or a positive integer."
        }
        require(commit == "unknown" || commit.matches(Regex("[0-9a-fA-F]{40}"))) {
            "launcherCommit must be 'unknown' or a 40-character Git commit SHA."
        }
        destination.file("launcher-build.properties").get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$value\nbuild=$build\ncommit=$commit\nchannel=$channel\nattempt=$attempt\ndiscordApplicationId=$discord\n")
        }
    }
}
val generateLauncherBuildInfo = tasks.register<GenerateLauncherBuildInfo>("generateLauncherBuildInfo") {
    releaseVersion.set(launcherVersion)
    buildNumber.set(launcherBuildNumber)
    updateChannel.set(providers.gradleProperty("launcherUpdateChannel").orElse("stable"))
    buildAttempt.set(providers.environmentVariable("GITHUB_RUN_ATTEMPT").orElse("1"))
    discordId.set(discordApplicationId)
    commitSha.set(launcherCommit)
    destination.set(layout.buildDirectory.dir("generated/launcher-build-info"))
}
sourceSets.main { resources.srcDir(generateLauncherBuildInfo) }

tasks.register<JavaExec>("verifyUpdateBundle") {
    group = "verification"
    description = "Install and verify an explicit update bundle without starting it."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("pl.syntaxdevteam.syntaxlauncher.desktop.update.UpdateBundleSmokeKt")
    args(providers.gradleProperty("updateBundle").getOrElse(""))
}

compose.desktop {
    application {
        mainClass = "pl.syntaxdevteam.syntaxlauncher.desktop.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            // Keep JSSE elliptic-curve TLS support in the trimmed jlink runtime.
            modules("jdk.crypto.ec")
            packageName = "SyntaxLauncher"
            // Compatibility mapping only; the embedded product version is always launcherVersion.
            packageVersion = nativePackageVersion.get()
            description = "A lightweight, modern Minecraft launcher by SyntaxDevTeam"
            vendor = "SyntaxDevTeam"
            licenseFile.set(rootProject.file("LICENSE"))
            windows {
                iconFile.set(project.file("src/main/resources/branding/syntaxlauncher-icon.ico"))
            }
            macOS {
                packageVersion = nativePackageVersion.get().let { if (it.startsWith("0.")) "1." + it.substringAfter('.') else it }
                bundleID = "pl.syntaxdevteam.syntaxlauncher"
                iconFile.set(project.file("src/main/resources/branding/syntaxlauncher-icon.icns"))
            }
            linux {
                packageName = "syntaxlauncher"
                debMaintainer = "wieszczy85@gmail.com"
                rpmLicenseType = "MIT"
                menuGroup = "Game"
                shortcut = true
                iconFile.set(project.file("src/main/resources/branding/syntaxlauncher-icon.png"))
            }
        }
    }
}

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
    implementation(project(":launcher-core"))
    // Desktop itself owns HttpClient lifecycle and therefore needs this API directly.
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.6.5")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

val launcherVersion = providers.gradleProperty("launcherVersion").orElse("0.3.0-dev")
abstract class GenerateLauncherBuildInfo : DefaultTask() {
    @get:Input abstract val releaseVersion: Property<String>
    @get:OutputDirectory abstract val destination: DirectoryProperty

    @TaskAction fun generate() {
        val value = releaseVersion.get()
        require(value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?(?:\\+[A-Za-z0-9.-]+)?"))) {
            "launcherVersion must be a semantic version."
        }
        destination.file("launcher-build.properties").get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$value\n")
        }
    }
}
val generateLauncherBuildInfo = tasks.register<GenerateLauncherBuildInfo>("generateLauncherBuildInfo") {
    releaseVersion.set(launcherVersion)
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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Keep JSSE elliptic-curve TLS support in the trimmed jlink runtime.
            modules("jdk.crypto.ec")
            packageName = "SyntaxLauncher"
            // macOS jpackage forbids a leading zero in app-version; product milestone remains v0.1.
            packageVersion = "1.0.0"
            description = "A lightweight, modern Minecraft launcher by SyntaxDevTeam"
            vendor = "SyntaxDevTeam"
            linux {
                iconFile.set(project.file("src/main/resources/branding/syntaxlauncher-icon.png"))
            }
        }
    }
}

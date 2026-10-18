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
}

compose.desktop {
    application {
        mainClass = "pl.syntaxdevteam.syntaxlauncher.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SyntaxLauncher"
            packageVersion = "0.1.0"
            description = "A lightweight, modern Minecraft launcher by SyntaxDevTeam"
            vendor = "SyntaxDevTeam"
        }
    }
}

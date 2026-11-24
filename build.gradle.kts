plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

val productVersion = providers.gradleProperty("launcherVersion").get()

allprojects {
    group = "pl.syntaxdevteam.syntaxlauncher"
    version = productVersion
    dependencyLocking {
        lockAllConfigurations()
        // Compose selects different native modules on each host. Their versions are fixed by
        // the pinned Compose/Skiko distribution; keep the shared JVM graph portable.
        ignoredDependencies.addAll(listOf("org.jetbrains.compose.desktop:*", "org.jetbrains.skiko:*"))
    }
}

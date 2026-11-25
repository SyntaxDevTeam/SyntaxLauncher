plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")

    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-cio:3.6.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")

    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    implementation("org.tomlj:tomlj:2.2.0")

    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-client-mock:3.6.1")
}

tasks.test {
    useJUnitPlatform()
    val liveSmoke = listOf(
        "SYNTAXLAUNCHER_JAVA_LIVE_SMOKE",
        "SYNTAXLAUNCHER_NEOFORGE_LIVE_SMOKE",
        "SYNTAXLAUNCHER_FORGE_LIVE_SMOKE",
        "SYNTAXLAUNCHER_MODPACK_LIVE_SMOKE",
        "SYNTAXLAUNCHER_OPTIFINE_LIVE_SMOKE",
        "SYNTAXLAUNCHER_UPDATE_LIVE_SMOKE",
    ).any { providers.environmentVariable(it).orNull == "1" }
    inputs.property("liveSmoke", liveSmoke)
    outputs.upToDateWhen { !liveSmoke }
    outputs.doNotCacheIf("Live smoke tests check external services") { liveSmoke }
}

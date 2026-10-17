package pl.syntaxdevteam.syntaxlauncher.core.java

import java.nio.file.Path

enum class JavaRuntimeSource {
    MANAGED_MOJANG,
    EXTERNAL,
}

data class JavaRuntime(
    val home: Path,
    val executable: Path,
    val majorVersion: Int,
    val versionString: String,
    val source: JavaRuntimeSource = JavaRuntimeSource.EXTERNAL,
    val component: String? = null,
    val platformKey: String? = null,
    val releaseName: String? = null,
)

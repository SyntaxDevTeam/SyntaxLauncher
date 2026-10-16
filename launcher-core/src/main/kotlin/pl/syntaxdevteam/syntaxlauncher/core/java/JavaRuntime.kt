package pl.syntaxdevteam.syntaxlauncher.core.java

import java.nio.file.Path

data class JavaRuntime(
    val home: Path,
    val executable: Path,
    val majorVersion: Int,
    val versionString: String,
)

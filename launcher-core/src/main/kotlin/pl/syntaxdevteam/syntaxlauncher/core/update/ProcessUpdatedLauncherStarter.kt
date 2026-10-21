package pl.syntaxdevteam.syntaxlauncher.core.update

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/** Keep child startup diagnostics instead of discarding the cause of failed handoffs. */
internal object ProcessUpdatedLauncherStarter : UpdatedLauncherStarter {
    fun logFile(receipt: Path): Path = receipt.resolveSibling("${receipt.fileName}.log")

    override fun start(executable: Path, receipt: Path, nonce: String): Process {
        val log = logFile(receipt)
        if ("posix" in log.fileSystem.supportedFileAttributeViews()) {
            Files.createFile(log, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else Files.createFile(log)
        return processBuilder(executable, receipt, nonce)
            .directory(executable.parent.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start()
    }

    internal fun processBuilder(executable: Path, receipt: Path, nonce: String,
        inheritedEnvironment: Map<String, String> = System.getenv()): ProcessBuilder =
        ProcessBuilder(executable.toString(), StartupUpdater.HANDOFF_FLAG, receipt.toString(), nonce).apply {
            environment().clear()
            environment().putAll(inheritedEnvironment)
            // Linux jpackage sets this marker for its own relaunch, with already-expanded JVM args.
            // A different app image needs to read its own .cfg instead of treating handoff args as JVM options.
            environment().remove("_JPACKAGE_LAUNCHER")
        }
}

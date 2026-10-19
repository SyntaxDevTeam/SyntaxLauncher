package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.nio.file.Path
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime

data class LaunchPlan(
    val runtime: JavaRuntime,
    val workingDirectory: Path,
    val mainClass: String,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    /** Avoid persisting raw child stdout/stderr when session credentials are present. */
    val sensitiveOutput: Boolean = false,
) {
    val command: List<String>
        get() = buildList {
            add(runtime.executable.toString())
            addAll(jvmArguments)
            add(mainClass)
            addAll(gameArguments)
        }
}

open class LaunchPlanException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class UnresolvedLaunchPlaceholderException(
    val placeholder: String,
    argument: String,
) : LaunchPlanException(
    "Minecraft launch argument contains unresolved placeholder '$placeholder': $argument",
)

class MissingLaunchArtifactException(
    description: String,
) : LaunchPlanException("Required launch artifact is unavailable: $description")

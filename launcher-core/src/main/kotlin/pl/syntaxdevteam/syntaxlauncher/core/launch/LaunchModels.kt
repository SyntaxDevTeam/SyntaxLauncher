package pl.syntaxdevteam.syntaxlauncher.core.launch

import pl.syntaxdevteam.syntaxlauncher.core.account.Account
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

data class LaunchOptions(
    val minimumMemoryMiB: Int = 512,
    val maximumMemoryMiB: Int = 4096,
    val extraJvmArguments: List<String> = emptyList(),
) {
    init {
        require(minimumMemoryMiB > 0) { "Minimum memory must be greater than zero." }
        require(maximumMemoryMiB >= minimumMemoryMiB) {
            "Maximum memory must be greater than or equal to minimum memory."
        }
    }
}

data class LaunchRequest(
    val account: Account,
    val version: ResolvedMinecraftVersion,
    val runtime: JavaRuntime,
    val platform: MinecraftPlatform,
    val options: LaunchOptions,
)

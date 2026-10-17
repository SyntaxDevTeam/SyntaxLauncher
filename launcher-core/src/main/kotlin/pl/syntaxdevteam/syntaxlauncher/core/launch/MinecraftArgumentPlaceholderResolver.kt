package pl.syntaxdevteam.syntaxlauncher.core.launch

class MinecraftArgumentPlaceholderResolver {
    fun resolve(
        argument: String,
        values: Map<String, String>,
    ): String {
        var resolved = argument
        PLACEHOLDER_REGEX.findAll(argument).forEach { match ->
            val key = match.groupValues[1]
            val value = values[key]
                ?: throw UnresolvedLaunchPlaceholderException(match.value, argument)
            resolved = resolved.replace(match.value, value)
        }

        PLACEHOLDER_REGEX.find(resolved)?.let { remaining ->
            throw UnresolvedLaunchPlaceholderException(remaining.value, resolved)
        }
        return resolved
    }

    fun resolveAll(
        arguments: List<String>,
        values: Map<String, String>,
    ): List<String> = arguments.map { resolve(it, values) }

    companion object {
        private val PLACEHOLDER_REGEX = Regex("""\$\{([A-Za-z0-9_]+)}""")
    }
}

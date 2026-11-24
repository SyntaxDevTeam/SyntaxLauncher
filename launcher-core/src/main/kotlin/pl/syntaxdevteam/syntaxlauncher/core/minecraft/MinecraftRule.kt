package pl.syntaxdevteam.syntaxlauncher.core.minecraft

enum class MinecraftRuleAction {
    ALLOW,
    DISALLOW,
}

data class MinecraftOsRule(
    val name: String? = null,
    val versionRegex: String? = null,
    val architectureRegex: String? = null,
)

data class MinecraftRule(
    val action: MinecraftRuleAction,
    val os: MinecraftOsRule? = null,
    val features: Map<String, Boolean> = emptyMap(),
)

object MinecraftRuleEvaluator {
    fun isAllowed(
        rules: List<MinecraftRule>,
        platform: MinecraftPlatform,
        features: Map<String, Boolean> = emptyMap(),
    ): Boolean {
        if (rules.isEmpty()) return true

        var allowed = false
        rules.forEach { rule ->
            if (matches(rule, platform, features)) {
                allowed = rule.action == MinecraftRuleAction.ALLOW
            }
        }
        return allowed
    }

    private fun matches(
        rule: MinecraftRule,
        platform: MinecraftPlatform,
        features: Map<String, Boolean>,
    ): Boolean =
        matchesOs(rule.os, platform) &&
            rule.features.all { (name, expected) -> (features[name] ?: false) == expected }

    private fun matchesOs(
        rule: MinecraftOsRule?,
        platform: MinecraftPlatform,
    ): Boolean {
        if (rule == null) return true

        if (rule.name != null && rule.name != platform.os.ruleName) return false
        if (!matchesRegex(rule.versionRegex, platform.osVersion)) return false
        if (!matchesRegex(rule.architectureRegex, platform.rawArchitecture)) return false

        return true
    }

    private fun matchesRegex(pattern: String?, value: String): Boolean {
        if (pattern == null) return true
        return runCatching { Regex(pattern).containsMatchIn(value) }.getOrDefault(false)
    }
}

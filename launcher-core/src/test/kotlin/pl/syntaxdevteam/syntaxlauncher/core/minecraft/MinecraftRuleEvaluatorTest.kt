package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinecraftRuleEvaluatorTest {
    private val windows = MinecraftPlatform(
        os = MinecraftOperatingSystem.WINDOWS,
        architecture = MinecraftArchitecture.X86_64,
        osVersion = "10.0",
        rawArchitecture = "amd64",
    )

    @Test
    fun `empty rules allow artifact`() {
        assertTrue(MinecraftRuleEvaluator.isAllowed(emptyList(), windows))
    }

    @Test
    fun `last matching rule wins`() {
        val rules = listOf(
            MinecraftRule(MinecraftRuleAction.ALLOW),
            MinecraftRule(
                action = MinecraftRuleAction.DISALLOW,
                os = MinecraftOsRule(name = "windows"),
            ),
        )

        assertFalse(MinecraftRuleEvaluator.isAllowed(rules, windows))
    }

    @Test
    fun `feature rule requires matching feature value`() {
        val rules = listOf(
            MinecraftRule(
                action = MinecraftRuleAction.ALLOW,
                features = mapOf("has_custom_resolution" to true),
            ),
        )

        assertFalse(MinecraftRuleEvaluator.isAllowed(rules, windows))
        assertTrue(
            MinecraftRuleEvaluator.isAllowed(
                rules,
                windows,
                mapOf("has_custom_resolution" to true),
            ),
        )
    }
}

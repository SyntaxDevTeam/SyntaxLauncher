package pl.syntaxdevteam.syntaxlauncher.desktop.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiStringsTest {
    @Test fun allLanguagesHaveIdenticalKeysAndNumberedArguments() {
        val english = UiStrings.bundles.getValue(UiLanguage.EN)
        val placeholders = Regex("\\{[0-9]+}")
        for ((language, bundle) in UiStrings.bundles) {
            assertEquals(english.keys, bundle.keys, language.code)
            for ((key, value) in bundle) {
                assertTrue(value.isNotBlank(), "$language / $key")
                assertEquals(placeholders.findAll(english.getValue(key)).map { it.value }.toList().sorted(),
                    placeholders.findAll(value).map { it.value }.toList().sorted(), "$language / $key")
            }
        }
    }

    @Test fun unicodeAndArgumentReorderingArePreservedAndArgumentsAreNotReinterpreted() {
        assertEquals("GRAJ W MINECRAFT", UiStrings(UiLanguage.PL).text("ui.play_minecraft"))
        assertEquals("Schließen", UiStrings(UiLanguage.DE).text("ui.close"))
        assertEquals("{0} aktualisieren", UiStrings(UiLanguage.DE).text("ui.update_0", "{0}"))
        assertEquals("Zarządzaj instancjami (5)", UiStrings(UiLanguage.PL).text("ui.manage_instances_0", 5))
        assertFailsWith<IllegalArgumentException> { UiStrings(UiLanguage.EN).text("ui.update_0") }
        assertFailsWith<IllegalStateException> { UiStrings(UiLanguage.PL).text("missing") }
    }

    @Test fun languageTagsUseEnglishFallbackWithoutChangingGlobalLocale() {
        assertEquals(UiLanguage.PL, UiLanguage.from("pl-PL"))
        assertEquals(UiLanguage.DE, UiLanguage.from("DE_de"))
        assertEquals(UiLanguage.EN, UiLanguage.from("fr-FR"))
        assertEquals(UiLanguage.EN, UiLanguage.from(null))
    }
    @Test fun accountGuidanceNeverOffersUnauthenticatedGameplay() {
        for ((language, bundle) in UiStrings.bundles) {
            assertTrue(bundle.keys.none { it in setOf("players.add_offline", "players.offline", "ui.offline_nickname") })
            for (key in listOf("account.sign_in_required", "players.intro", "settings.account_pending", "health.cause.auth")) {
                val text = bundle.getValue(key)
                assertTrue(text.contains("Microsoft"), "$language / $key")
                assertTrue(!text.contains("offline", ignoreCase = true), "$language / $key")
            }
        }
    }

}

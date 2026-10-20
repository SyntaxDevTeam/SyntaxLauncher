package pl.syntaxdevteam.syntaxlauncher.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import java.util.Properties

enum class UiLanguage(val code: String, val label: String) {
    EN("en", "English"), PL("pl", "Polski"), DE("de", "Deutsch");

    companion object {
        fun from(code: String?): UiLanguage = entries.firstOrNull {
            it.code == code?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
        } ?: EN
    }
}

/** UTF-8 resources, explicit language choice, and single-pass numbered placeholders. */
class UiStrings(val language: UiLanguage) {
    fun text(key: String, vararg arguments: Any?): String {
        val pattern = bundles.getValue(language)[key] ?: bundles.getValue(UiLanguage.EN)[key]
            ?: error("Missing UI translation key: $key")
        return placeholder.replace(pattern) { match ->
            val index = match.groupValues[1].toInt()
            require(index < arguments.size) { "Missing argument $index for $key" }
            arguments[index]?.toString().orEmpty()
        }
    }

    companion object {
        private val placeholder = Regex("\\{([0-9]+)}")
        internal val bundles: Map<UiLanguage, Map<String, String>> = UiLanguage.entries.associateWith { language ->
            val resource = requireNotNull(UiStrings::class.java.getResourceAsStream("/i18n/${language.code}.properties"))
            resource.bufferedReader(Charsets.UTF_8).use { reader ->
                Properties().apply { load(reader) }.entries.associate { it.key.toString() to it.value.toString() }
            }
        }
    }
}

val LocalStrings = staticCompositionLocalOf { UiStrings(UiLanguage.EN) }

package pl.syntaxdevteam.syntaxlauncher.desktop.i18n

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore

private data class LanguageChoice(val select: (UiLanguage) -> Unit = {}, val saving: Boolean = false,
    val error: Boolean = false)
private val LocalLanguageChoice = staticCompositionLocalOf { LanguageChoice() }

@Composable
fun LauncherLanguageHost(store: LauncherPreferencesStore, content: @Composable () -> Unit) {
    var language by remember(store) { mutableStateOf(UiLanguage.from(Locale.getDefault().language)) }
    var loaded by remember(store) { mutableStateOf(false) }
    var saving by remember(store) { mutableStateOf(false) }
    var error by remember(store) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) {
        try {
            val saved = store.loadLanguage()
            if (saved != null) language = UiLanguage.from(saved)
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loaded = true }
    }
    val strings = remember(language) { UiStrings(language) }
    val choice = LanguageChoice(select = { next ->
        if (loaded && !saving) scope.launch {
            saving = true
            try {
                store.saveLanguage(next.code)
                language = next
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }, saving = !loaded || saving, error = error)
    CompositionLocalProvider(LocalStrings provides strings, LocalLanguageChoice provides choice, content = content)
}

@Composable
fun LanguagePicker() {
    val strings = LocalStrings.current
    val choice = LocalLanguageChoice.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }, enabled = !choice.saving,
            modifier = Modifier.semantics { contentDescription = strings.text("language.choose") }) {
            Text(strings.language.code.uppercase(Locale.ROOT) + " ▾", maxLines = 1)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            UiLanguage.entries.forEach { language ->
                DropdownMenuItem(text = { Text(language.label) }, onClick = {
                    expanded = false
                    choice.select(language)
                })
            }
        }
        if (choice.error) Text(strings.text("language.save_error"))
    }
}

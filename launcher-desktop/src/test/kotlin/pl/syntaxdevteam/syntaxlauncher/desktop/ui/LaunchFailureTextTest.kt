package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import kotlin.test.*
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class LaunchFailureTextTest {
    @Test fun everyFailureIsLocalizedAndRawDetailsAreNeverAFallback() {
        for (language in UiLanguage.entries) {
            val strings = UiStrings(language)
            for (reason in LaunchFailureReason.entries) {
                val status = LaunchPipelineStatus(phase = LaunchPhase.FAILED, detail = "token=private-secret", failure = reason)
                val text = localizedLaunchFailure(status, strings)
                assertTrue(text.isNotBlank())
                assertFalse(text.contains("private-secret"))
                if (language != UiLanguage.EN) assertNotEquals(localizedLaunchFailure(status, UiStrings(UiLanguage.EN)), text)
            }
            val unknown = localizedLaunchFailure(LaunchPipelineStatus(phase = LaunchPhase.FAILED, detail = "private-secret"), strings)
            assertEquals(localizedLaunchFailure(LaunchPipelineStatus(failure = LaunchFailureReason.PREPARATION), strings), unknown)
        }
    }
}

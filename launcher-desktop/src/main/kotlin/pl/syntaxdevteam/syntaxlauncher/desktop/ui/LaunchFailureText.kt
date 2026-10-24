package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import java.util.Locale
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchFailureReason
import pl.syntaxdevteam.syntaxlauncher.core.launch.LaunchPipelineStatus
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.UiStrings

/** A raw core detail/exception is never a localized user-facing fallback. */
internal fun localizedLaunchFailure(status: LaunchPipelineStatus, strings: UiStrings): String =
    strings.text("launch.failure." + (status.failure ?: LaunchFailureReason.PREPARATION).name.lowercase(Locale.ROOT))

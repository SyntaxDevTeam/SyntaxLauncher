package pl.syntaxdevteam.syntaxlauncher.desktop.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import pl.syntaxdevteam.syntaxlauncher.core.diagnostics.SecretRedactor

/** Exception messages/stacks may contain credential-bearing URLs; retain only the exception type. */
class SafeLogMessage : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String = SecretRedactor.redact(event.formattedMessage) +
        (event.throwableProxy?.let { " [${it.className.substringAfterLast('.')}]" } ?: "")
}

package pl.syntaxdevteam.syntaxlauncher.desktop.logging

import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import kotlin.test.*

class SafeLogMessageTest {
    @Test fun redactsFormattedMessageAndDoesNotRenderCredentialBearingException() {
        val event = LoggingEvent().apply {
            message = "Download failed: access_token=secret-value"
            setThrowableProxy(ThrowableProxy(IllegalStateException("refresh_token=other-secret")))
        }
        val rendered = SafeLogMessage().convert(event)
        assertFalse(rendered.contains("secret-value"))
        assertFalse(rendered.contains("other-secret"))
        assertTrue(rendered.contains("IllegalStateException"))
    }
}

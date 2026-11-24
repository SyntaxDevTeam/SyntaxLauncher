package pl.syntaxdevteam.syntaxlauncher.core.diagnostics

/** Defense in depth for launcher messages. Diagnostic exports never include raw logs at all. */
object SecretRedactor {
    private val fields = Regex("(?i)([\\\"']?(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization|password|session(?:id)?)[\\\"']?\\s*[:=]\\s*)(?:[\\\"'][^\\\"'\\r\\n]*[\\\"']|[^\\s,;}]+)")
    private val arguments = Regex("(?i)(--(?:accessToken|session|clientSecret|password)\\s+)(?:\\\"[^\\\"]*\\\"|\\S+)")
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")
    private val xbox = Regex("XBL3\\.0\\s+x=[^\\s,]+")
    fun redact(text: String): String {
        var value = bearer.replace(text, "Bearer [REDACTED]")
        value = xbox.replace(value, "XBL3.0 [REDACTED]")
        value = jwt.replace(value, "[REDACTED]")
        value = arguments.replace(value) { it.groupValues[1] + "[REDACTED]" }
        return fields.replace(value) { it.groupValues[1] + "[REDACTED]" }
    }
}

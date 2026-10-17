package pl.syntaxdevteam.syntaxlauncher.core.java

open class JavaRuntimeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class UnsupportedJavaRuntimePlatformException(
    message: String,
) : JavaRuntimeException(message)

class JavaRuntimeMetadataException(
    message: String,
    cause: Throwable? = null,
) : JavaRuntimeException(message, cause)

class JavaRuntimeManifestException(
    message: String,
    cause: Throwable? = null,
) : JavaRuntimeException(message, cause)

class JavaRuntimeInstallException(
    message: String,
    cause: Throwable? = null,
) : JavaRuntimeException(message, cause)

class JavaRuntimeVerificationException(
    message: String,
    cause: Throwable? = null,
) : JavaRuntimeException(message, cause)

class JavaRuntimeVersionMismatchException(
    expected: Int,
    actual: Int,
    releaseName: String,
) : JavaRuntimeException(
    "Mojang runtime '$releaseName' reports Java $actual, but Minecraft requires Java $expected.",
)

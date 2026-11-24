package pl.syntaxdevteam.syntaxlauncher.core.account

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** No plaintext fallback and no secrets in process arguments, environment or shell commands. */
class OsRefreshTokenVault(private val directory: Path) : RefreshTokenVault {
    private val delegate: RefreshTokenVault by lazy {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") || os.contains("darwin") -> MacVault()
            os.contains("win") -> WindowsVault(directory)
            os.contains("linux") -> LinuxVault()
            else -> error("Unsupported secure credential store.")
        }
    }
    override fun read(key: String): String? = safe { delegate.read(key) }
    override fun write(key: String, token: String) = safe {
        require(token.isNotBlank() && '\u0000' !in token)
        delegate.write(key, token)
    }
    override fun remove(key: String) = safe { delegate.remove(key) }
    private fun <T> safe(action: () -> T): T = try { action() } catch (_: Exception) {
        throw IllegalStateException("Unlock the OS credential store and retry. Linux requires libsecret and a Secret Service keyring.")
    } catch (_: LinkageError) {
        throw IllegalStateException("OS secure storage is unavailable. Linux requires libsecret and a Secret Service keyring.")
    }
}

internal interface SecretLibrary : Library {
    fun secret_schema_new(name: String, flags: Int, vararg attributes: Any?): Pointer
    fun secret_password_lookup_sync(schema: Pointer, cancellable: Pointer?, error: PointerByReference,
                                    vararg attributes: Any?): Pointer?
    fun secret_password_store_sync(schema: Pointer, collection: String?, label: String, password: String,
                                   cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?): Int
    fun secret_password_clear_sync(schema: Pointer, cancellable: Pointer?, error: PointerByReference,
                                   vararg attributes: Any?): Int
    fun secret_password_free(password: Pointer)
}
internal interface GlibLibrary : Library { fun g_error_free(error: Pointer) }

private class LinuxVault : RefreshTokenVault {
    private val lib = Native.load("libsecret-1.so.0", SecretLibrary::class.java)
    private val glib = Native.load("glib-2.0", GlibLibrary::class.java)
    private val schema = lib.secret_schema_new("pl.syntaxdevteam.SyntaxLauncher.Refresh.v1", 0, "account", 0, null)
    private fun checkError(error: PointerByReference) {
        error.value?.let { glib.g_error_free(it); error("Secret Service operation failed.") }
    }
    override fun read(key: String): String? {
        val error = PointerByReference()
        val result = lib.secret_password_lookup_sync(schema, null, error, "account", key, null)
        checkError(error)
        return result?.let { try { it.getString(0, "UTF-8") } finally { lib.secret_password_free(it) } }
    }
    override fun write(key: String, token: String) {
        val error = PointerByReference()
        val ok = lib.secret_password_store_sync(schema, "default", "SyntaxLauncher Microsoft session", token,
            null, error, "account", key, null)
        checkError(error)
        check(ok != 0) { "Secret Service refused credential storage." }
    }
    override fun remove(key: String) {
        val error = PointerByReference()
        lib.secret_password_clear_sync(schema, null, error, "account", key, null)
        checkError(error)
    }
}

private class WindowsVault(private val directory: Path) : RefreshTokenVault {
    private fun file(key: String): Path = directory.resolve(
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".dpapi-v1")
    override fun read(key: String): String? {
        val path = file(key)
        if (!Files.exists(path)) return null
        val plain = Crypt32Util.cryptUnprotectData(Files.readAllBytes(path))
        return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    }
    override fun write(key: String, token: String) {
        Files.createDirectories(directory)
        val plain = token.toByteArray(Charsets.UTF_8)
        val encrypted = try { Crypt32Util.cryptProtectData(plain) } finally { plain.fill(0) }
        val temporary = Files.createTempFile(directory, ".credential-", ".part")
        try {
            Files.write(temporary, encrypted)
            // A failed atomic publication preserves the previous credential.
            Files.move(temporary, file(key), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    override fun remove(key: String) { Files.deleteIfExists(file(key)) }
}

internal interface MacSecurity : Library {
    fun SecKeychainFindGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray,
        accountLength: Int, account: ByteArray, length: IntByReference?, data: PointerByReference?,
        item: PointerByReference?): Int
    fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray,
        accountLength: Int, account: ByteArray, length: Int, data: ByteArray, item: PointerByReference?): Int
    fun SecKeychainItemModifyAttributesAndData(item: Pointer, attributes: Pointer?, length: Int, data: ByteArray): Int
    fun SecKeychainItemDelete(item: Pointer): Int
    fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer): Int
}
internal interface CoreFoundation : Library { fun CFRelease(value: Pointer) }
private class MacVault : RefreshTokenVault {
    private val security = Native.load("/System/Library/Frameworks/Security.framework/Security", MacSecurity::class.java)
    private val foundation = Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation::class.java)
    private val service = "pl.syntaxdevteam.SyntaxLauncher.Refresh.v1".toByteArray()
    private fun find(key: String, length: IntByReference?, data: PointerByReference?, item: PointerByReference?): Int {
        val account = key.toByteArray(Charsets.UTF_8)
        return security.SecKeychainFindGenericPassword(null, service.size, service, account.size, account, length, data, item)
    }
    override fun read(key: String): String? {
        val length = IntByReference()
        val data = PointerByReference()
        val status = find(key, length, data, null)
        if (status == -25300) return null // errSecItemNotFound
        check(status == 0) { "Keychain lookup failed." }
        return try { data.value.getByteArray(0, length.value).toString(Charsets.UTF_8) }
        finally { security.SecKeychainItemFreeContent(null, data.value) }
    }
    override fun write(key: String, token: String) {
        val item = PointerByReference()
        val status = find(key, null, null, item)
        val bytes = token.toByteArray(Charsets.UTF_8)
        try {
            if (status == -25300) {
                val account = key.toByteArray(Charsets.UTF_8)
                check(security.SecKeychainAddGenericPassword(null, service.size, service,
                    account.size, account, bytes.size, bytes, null) == 0) { "Keychain storage failed." }
            } else {
                check(status == 0) { "Keychain lookup failed." }
                try {
                    check(security.SecKeychainItemModifyAttributesAndData(item.value, null, bytes.size, bytes) == 0)
                } finally { foundation.CFRelease(item.value) }
            }
        } finally { bytes.fill(0) }
    }
    override fun remove(key: String) {
        val item = PointerByReference()
        val status = find(key, null, null, item)
        if (status == -25300) return
        check(status == 0) { "Keychain lookup failed." }
        try { check(security.SecKeychainItemDelete(item.value) == 0) }
        finally { foundation.CFRelease(item.value) }
    }
}

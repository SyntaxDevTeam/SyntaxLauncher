package pl.syntaxdevteam.syntaxlauncher.core.account

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.Clock
import java.time.Instant
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Secrets are addressed by application + profile, never by a token in a filename. */
interface RefreshTokenVault {
    fun read(key: String): String?
    fun write(key: String, token: String)
    fun remove(key: String)
}

data class SavedAccount(val account: Account, val microsoft: Boolean)

/** Versioned identity inventory; only refresh credentials go into the OS vault. */
class AccountManager(
    private val file: Path,
    private val vault: RefreshTokenVault,
    private val applicationId: String,
    private val refresh: suspend (String) -> PremiumSession,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, PremiumSession>()
    private fun key(id: String) = "$applicationId/$id"

    suspend fun accounts(): List<SavedAccount> = io { decode(read()) }

    suspend fun saveOffline(username: String): OfflineAccount = io {
        val account = OfflineAccount.create(username)
        val properties = read()
        put(properties, account, false)
        save(properties)
        account
    }

    suspend fun savePremium(session: PremiumSession) = io {
        val properties = read()
        decode(properties) // Refuse malformed/unsupported state before writing a secret.
        val token = session.microsoftRefreshToken
            ?: throw IllegalStateException("Microsoft did not grant offline access. Sign in again.")
        val id = session.account.uuid.toString()
        put(properties, session.account, true)
        vault.write(key(id), token)
        save(properties)
        sessions[id] = session
    }

    suspend fun session(id: String, requireFresh: Boolean = false): PremiumSession = io {
        val identity = decode(read()).firstOrNull { it.microsoft && it.account.uuid.toString() == id }
            ?: throw IllegalStateException("Microsoft account was removed. Sign in again.")
        val cached = sessions[id]
        if (!requireFresh && cached != null && cached.expiresAt.isAfter(Instant.now(clock).plusSeconds(180))) return@io cached
        val credential = vault.read(key(id))
            ?: throw IllegalStateException("Microsoft session is unavailable. Sign in again using Add Microsoft.")
        val result = refresh(credential)
        check(result.account.uuid == identity.account.uuid) { "Microsoft identity changed. Sign in again." }
        check(result.expiresAt.isAfter(Instant.now(clock).plusSeconds(180))) {
            "Microsoft returned an expired session. Sign in again."
        }
        // Persist rotation before publishing the new session. Missing rotation retains the old token.
        vault.write(key(id), result.microsoftRefreshToken ?: credential)
        val properties = read()
        put(properties, result.account, true)
        save(properties)
        sessions[id] = PremiumSession(result.account, result.minecraftToken, result.expiresAt,
            result.microsoftRefreshToken ?: credential)
        sessions.getValue(id)
    }

    suspend fun remove(id: String) = io {
        val properties = read()
        val account = decode(properties).firstOrNull { it.account.uuid.toString() == id } ?: return@io
        if (account.microsoft) vault.remove(key(id)) // Failure must not masquerade as successful sign-out.
        sessions.remove(id)
        val ids = ids(properties).filterNot { it == id }
        properties.setProperty("accounts", ids.joinToString(","))
        properties.remove("$id.name")
        properties.remove("$id.type")
        save(properties)
    }

    private suspend fun <T> io(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            Files.createDirectories(file.parent)
            // Two launcher processes cannot race refresh-token rotation or inventory writes.
            java.nio.channels.FileChannel.open(file.resolveSibling(file.fileName.toString() + ".lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE).use { channel ->
                val lock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                check(lock != null) { "Accounts are busy in another launcher. Retry shortly." }
                lock.use { action() }
            }
        }
    }

    private fun read() = Properties().apply {
        if (Files.exists(file)) {
            Files.newInputStream(file).use { load(it) }
            check(getProperty("schema_version") == "1") { "Unsupported accounts schema. File preserved." }
        }
    }
    private fun ids(p: Properties) = p.getProperty("accounts", "").split(',').filter(String::isNotEmpty)
    private fun decode(p: Properties): List<SavedAccount> = ids(p).map { id ->
        check(UUID.fromString(id).toString() == id) { "Invalid account identity. File preserved." }
        val name = p.getProperty("$id.name") ?: error("Missing account name. File preserved.")
        when (p.getProperty("$id.type")) {
            "microsoft" -> SavedAccount(PremiumAccount(name, UUID.fromString(id)), true)
            "offline" -> SavedAccount(OfflineAccount.create(name).also {
                check(it.uuid.toString() == id) { "Invalid offline identity. File preserved." }
            }, false)
            else -> error("Unsupported account type. File preserved.")
        }
    }.also { check(it.map { entry -> entry.account.uuid }.distinct().size == it.size) }

    private fun put(p: Properties, account: Account, microsoft: Boolean) {
        decode(p)
        val id = account.uuid.toString()
        val existingType = p.getProperty("$id.type")
        val type = if (microsoft) "microsoft" else "offline"
        check(existingType == null || existingType == type) { "Conflicting account identity." }
        p.setProperty("schema_version", "1")
        p.setProperty("accounts", (ids(p) + id).distinct().joinToString(","))
        p.setProperty("$id.name", account.username)
        p.setProperty("$id.type", type)
    }
    private fun save(p: Properties) {
        val temporary = Files.createTempFile(file.parent, ".accounts-", ".part")
        try {
            Files.newOutputStream(temporary).use { p.store(it, "SyntaxLauncher identities; no credentials") }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
    }
}

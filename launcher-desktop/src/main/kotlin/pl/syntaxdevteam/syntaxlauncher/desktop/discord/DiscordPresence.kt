package pl.syntaxdevteam.syntaxlauncher.desktop.discord

import java.io.Closeable
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessStatus
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem

private const val LAUNCHER_LOGO_URL = "https://raw.githubusercontent.com/SyntaxDevTeam/SyntaxLauncher/main/" +
    "launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.png"

/** Publishes only the launcher/game state and timer to the local Discord desktop client. */
internal class DiscordPresence(
    private val applicationId: String,
    private val os: MinecraftOperatingSystem,
    private val environment: Map<String, String> = System.getenv(),
) {
    private val log = LoggerFactory.getLogger(DiscordPresence::class.java)

    suspend fun publish(process: StateFlow<MinecraftProcessStatus>) = withContext(Dispatchers.IO) {
        val launcherStarted = Instant.now()
        while (currentCoroutineContext().isActive) {
            try {
                connect()?.use { connection ->
                    connection.send(0, buildJsonObject {
                        put("v", 1)
                        put("client_id", applicationId)
                    }.toString())
                    connection.await { it.string("evt") == "READY" }

                    var published: Activity? = null
                    var lastUpdate = 0L
                    while (currentCoroutineContext().isActive) {
                        val current = process.value
                        val activity = if (current.state == MinecraftProcessState.RUNNING)
                            Activity("Playing Minecraft", current.startedAt ?: launcherStarted)
                        else Activity("Active in Launcher", launcherStarted)
                        // Periodic acknowledgements also detect a closed Windows pipe, where available() reports zero.
                        if (activity != published || System.nanoTime() - lastUpdate >= 30_000_000_000L) {
                            val nonce = UUID.randomUUID().toString()
                            connection.send(1, activity.command(nonce).toString())
                            connection.await { it.string("nonce") == nonce && it.string("cmd") == "SET_ACTIVITY" }
                            published = activity
                            lastUpdate = System.nanoTime()
                        }
                        connection.poll()
                        delay(250)
                    }
                }
            } catch (failure: IOException) {
                log.debug("Discord IPC disconnected: {}", failure.javaClass.simpleName)
            } catch (failure: SerializationException) {
                log.debug("Discord IPC returned an invalid message")
            }
            delay(5_000)
        }
    }

    private fun connect(): RpcConnection? {
        val names = (0..9).map { "discord-ipc-$it" }
        val pipe = when (os) {
            MinecraftOperatingSystem.WINDOWS -> names.firstNotNullOfOrNull { name ->
                open { WindowsPipe(RandomAccessFile("\\\\?\\pipe\\$name", "rw")) }
            }
            MinecraftOperatingSystem.LINUX, MinecraftOperatingSystem.MACOS -> {
                val dirs = listOfNotNull(
                    environment["XDG_RUNTIME_DIR"],
                    environment["XDG_RUNTIME_DIR"]?.let { "$it/app/com.discordapp.Discord" },
                    environment["TMPDIR"], environment["TMP"], environment["TEMP"], "/tmp",
                ).filter(String::isNotBlank).distinct()
                dirs.firstNotNullOfOrNull { dir -> names.firstNotNullOfOrNull { name ->
                    open { UnixPipe(SocketChannel.open(
                        UnixDomainSocketAddress.of(Path.of(dir, name)),
                    ).apply { configureBlocking(false) }) }
                } }
            }
            MinecraftOperatingSystem.UNKNOWN -> null
        }
        return pipe?.let(::RpcConnection)
    }

    private fun open(create: () -> Pipe): Pipe? = try {
        create()
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }

    private data class Activity(val details: String, val start: Instant) {
        fun command(nonce: String) = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("nonce", nonce)
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                putJsonObject("activity") {
                    put("type", 0)
                    put("details", details)
                    putJsonObject("timestamps") { put("start", start.epochSecond) }
                    putJsonObject("assets") {
                        put("large_image", LAUNCHER_LOGO_URL)
                        put("large_text", "SyntaxLauncher")
                    }
                }
            }
        }
    }

    companion object {
        fun validApplicationId(id: String): Boolean =
            id.length in 17..20 && id.firstOrNull() in '1'..'9' &&
                id.all { it in '0'..'9' } && id.toULongOrNull() != null
    }
}

private interface Pipe : Closeable {
    fun read(target: ByteBuffer): Int
    fun write(source: ByteBuffer): Int
}

private class UnixPipe(private val socket: SocketChannel) : Pipe {
    override fun read(target: ByteBuffer) = socket.read(target)
    override fun write(source: ByteBuffer) = socket.write(source)
    override fun close() = socket.close()
}

private class WindowsPipe(private val file: RandomAccessFile) : Pipe {
    private val input = FileInputStream(file.fd)
    private val buffer = ByteArray(8192)

    override fun read(target: ByteBuffer): Int {
        val available = minOf(input.available(), target.remaining(), buffer.size)
        if (available == 0) return 0
        val count = input.read(buffer, 0, available)
        if (count > 0) target.put(buffer, 0, count)
        return count
    }

    override fun write(source: ByteBuffer): Int {
        val size = source.remaining()
        val bytes = ByteArray(size)
        source.get(bytes)
        file.write(bytes)
        return size
    }

    override fun close() = file.close()
}

private class RpcConnection(private val pipe: Pipe) : Closeable {
    private val incoming = ByteBuffer.allocate(8 + MAX_PAYLOAD).order(ByteOrder.LITTLE_ENDIAN)

    suspend fun send(opcode: Int, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val frame = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        frame.putInt(opcode).putInt(bytes.size).put(bytes).flip()
        withTimeoutOrNull(2_000) {
            while (frame.hasRemaining()) {
                if (pipe.write(frame) == 0) delay(20)
            }
        } ?: throw IOException("Discord IPC write timed out")
    }

    suspend fun await(matches: (JsonObject) -> Boolean) {
        withTimeoutOrNull(3_000) {
            while (true) {
                if (poll()?.let(matches) == true) return@withTimeoutOrNull
                delay(20)
            }
        } ?: throw IOException("Discord IPC response timed out")
    }

    suspend fun poll(): JsonObject? {
        val frame = readFrame() ?: return null
        val (opcode, payload) = frame
        return when (opcode) {
            1 -> (Json.parseToJsonElement(payload) as? JsonObject)?.also {
                if (it.string("evt") == "ERROR") throw IOException("Discord rejected the activity")
            }
            2 -> throw EOFException("Discord closed IPC")
            3 -> { send(4, payload); null }
            else -> null
        }
    }

    private fun readFrame(): Pair<Int, String>? {
        val count = pipe.read(incoming)
        if (count < 0 && incoming.position() == 0) throw EOFException("Discord IPC closed")
        incoming.flip()
        try {
            if (incoming.remaining() < 8) return incomplete(count)
            incoming.mark()
            val opcode = incoming.int
            val length = incoming.int
            if (length !in 0..MAX_PAYLOAD) throw IOException("Invalid Discord IPC frame length")
            if (incoming.remaining() < length) {
                incoming.reset()
                return incomplete(count)
            }
            val bytes = ByteArray(length)
            incoming.get(bytes)
            return opcode to bytes.toString(Charsets.UTF_8)
        } finally {
            incoming.compact()
        }
    }

    private fun incomplete(count: Int): Nothing? {
        if (count < 0) throw EOFException("Discord IPC frame truncated")
        return null
    }

    override fun close() = pipe.close()

    private companion object {
        const val MAX_PAYLOAD = 64 * 1024
    }
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content

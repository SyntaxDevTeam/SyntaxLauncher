package pl.syntaxdevteam.syntaxlauncher.desktop.discord

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessState
import pl.syntaxdevteam.syntaxlauncher.core.launch.MinecraftProcessStatus
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem

class DiscordPresenceTest {
    @Test fun publishesLauncherAndGameActivityThroughFlatpakSocket() = runBlocking {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"))
        val runtime = Files.createTempDirectory("sl-rpc")
        val discord = Files.createDirectories(runtime.resolve("app").resolve("com.discordapp.Discord"))
        val endpoint = discord.resolve("discord-ipc-0")
        val appId = "123456789012345678"
        val state = MutableStateFlow(MinecraftProcessStatus())
        val enabled = AtomicBoolean(true)
        val gameDetails = AtomicReference<String>()
        val startedAt = Instant.parse("2026-08-01T20:30:00Z")
        try {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
                server.bind(UnixDomainSocketAddress.of(endpoint))
                val publisher = launch {
                    DiscordPresence(appId, MinecraftOperatingSystem.LINUX,
                        mapOf("XDG_RUNTIME_DIR" to runtime.toString())).publish(state, enabled::get, gameDetails::get)
                }
                try {
                    withTimeout(8_000) {
                        runInterruptible(Dispatchers.IO) {
                            server.accept().use { client ->
                                assertEquals(0 to """{"v":1,"client_id":"$appId"}""", client.readFrame())
                                client.sendFrame(1, """{"cmd":"DISPATCH","evt":"READY"}""")
                                val initial = client.expectActivity("Active in Launcher")
                                client.sendFrame(3, """{"nonce":"ping"}""")
                                assertEquals(4 to """{"nonce":"ping"}""", client.readFrame())
                                state.value = MinecraftProcessStatus(MinecraftProcessState.RUNNING, startedAt = startedAt)
                                assertEquals(startedAt.epochSecond, client.expectActivity("Playing Minecraft"))
                                gameDetails.set("In Minecraft Menu")
                                client.expectActivity("In Minecraft Menu")
                                gameDetails.set("Playing Singleplayer")
                                client.expectActivity("Playing Singleplayer")
                                gameDetails.set("On Server MoonVale.pl")
                                client.expectActivity("On Server MoonVale.pl")
                                gameDetails.set("In Minecraft Menu")
                                client.expectActivity("In Minecraft Menu")
                                state.value = MinecraftProcessStatus(MinecraftProcessState.STOPPED)
                                assertEquals(initial, client.expectActivity("Active in Launcher"))
                                enabled.set(false)
                                assertEquals(JsonNull, client.readActivity())
                            }
                        }
                    }
                } finally {
                    publisher.cancelAndJoin()
                }
            }
        } finally {
            runtime.toFile().deleteRecursively()
        }
        Unit
    }

    @Test fun gamePresenceSharesOnlyServerDomainsWhenEnabled() {
        assertEquals("In Minecraft Menu", describeGamePresence("menu", true))
        assertEquals("Playing Singleplayer", describeGamePresence("singleplayer", true))
        assertEquals("On Server MoonVale.pl", describeGamePresence("multiplayer\nMoonVale.pl:25565", true))
        assertEquals("Playing Multiplayer", describeGamePresence("multiplayer\nMoonVale.pl", false))
        assertEquals("Playing Multiplayer", describeGamePresence("multiplayer\n192.168.0.2", true))
        assertEquals("Playing Multiplayer", describeGamePresence("multiplayer\nplay.lan", true))
        assertEquals(null, describeGamePresence("invalid", true))
    }

    private fun SocketChannel.expectActivity(details: String): Long {
        val activity = readActivity().jsonObject
        assertEquals(details, activity.getValue("details").jsonPrimitive.content)
        return activity.getValue("timestamps").jsonObject.getValue("start").jsonPrimitive.content.toLong()
    }

    private fun SocketChannel.readActivity(): kotlinx.serialization.json.JsonElement {
        val (opcode, payload) = readFrame()
        assertEquals(1, opcode)
        val json = Json.parseToJsonElement(payload).jsonObject
        assertEquals("SET_ACTIVITY", json.getValue("cmd").jsonPrimitive.content)
        val nonce = json.getValue("nonce").jsonPrimitive.content
        sendFrame(1, """{"cmd":"SET_ACTIVITY","nonce":"$nonce"}""")
        return json.getValue("args").jsonObject.getValue("activity")
    }

    private fun SocketChannel.readFrame(): Pair<Int, String> {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        while (header.hasRemaining()) check(read(header) >= 0)
        header.flip()
        val opcode = header.int
        val body = ByteBuffer.allocate(header.int)
        while (body.hasRemaining()) check(read(body) >= 0)
        return opcode to body.array().decodeToString()
    }

    private fun SocketChannel.sendFrame(opcode: Int, text: String) {
        val bytes = text.encodeToByteArray()
        val frame = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        frame.putInt(opcode).putInt(bytes.size).put(bytes).flip()
        while (frame.hasRemaining()) write(frame)
    }
}

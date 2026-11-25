package pl.syntaxdevteam.syntaxlauncher.core.account

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

data class PlayerSkin(val png: ByteArray, val slim: Boolean)

class PlayerSkinCatalog(private val http: HttpClient) {
    private val cache = object : LinkedHashMap<UUID, PlayerSkin>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, PlayerSkin>?) = size > 16
    }

    suspend fun load(uuid: UUID): PlayerSkin? {
        synchronized(cache) { cache[uuid] }?.let { return it }
        return withTimeoutOrNull(8_000) {
            val id = uuid.toString().replace("-", "")
            val profile = fetch("https://sessionserver.mojang.com/session/minecraft/profile/$id", 32 * 1024)
                ?: return@withTimeoutOrNull null
            val texture = Json.parseToJsonElement(profile.decodeToString()).jsonObject["properties"]
                ?.jsonArray?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "textures" }
                ?.jsonObject?.get("value")?.jsonPrimitive?.content ?: return@withTimeoutOrNull null
            val skin = Json.parseToJsonElement(Base64.getDecoder().decode(texture).decodeToString())
                .jsonObject["textures"]?.jsonObject?.get("SKIN")?.jsonObject ?: return@withTimeoutOrNull null
            val url = skin["url"]?.jsonPrimitive?.content.orEmpty()
            val hash = Regex("https?://textures\\.minecraft\\.net/texture/([0-9a-f]{32,64})")
                .matchEntire(url)?.groupValues?.get(1) ?: return@withTimeoutOrNull null
            val png = fetch("https://textures.minecraft.net/texture/$hash", 128 * 1024)
                ?: return@withTimeoutOrNull null
            val header = java.nio.ByteBuffer.wrap(png)
            if (png.size < 24 || header.long != 0x89504E470D0A1A0AuL.toLong() ||
                header.int != 13 || header.int != 0x49484452 || header.int != 64 ||
                header.int !in setOf(32, 64)) return@withTimeoutOrNull null
            PlayerSkin(png, skin["metadata"]?.jsonObject?.get("model")?.jsonPrimitive?.content == "slim")
                .also { synchronized(cache) { cache[uuid] = it } }
        }
    }

    private suspend fun fetch(url: String, limit: Int): ByteArray? =
        http.prepareGet(url).execute { response ->
            if (response.status.value != 200) return@execute null
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(4096)
            val output = ByteArrayOutputStream()
            while (true) {
                val count = channel.readAvailable(buffer, 0, buffer.size)
                if (count < 0) break
                if (output.size() + count > limit) return@execute null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
}

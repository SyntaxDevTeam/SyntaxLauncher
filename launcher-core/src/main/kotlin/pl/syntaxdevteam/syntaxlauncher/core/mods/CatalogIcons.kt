package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.utils.io.readAvailable
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.URI

/** Optional artwork uses the application's HTTP client and a bounded in-memory cache. */
class CatalogIcons(private val http: HttpClient) {
    private val cache = object : LinkedHashMap<String, ByteArray>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 64
    }
    private val requests = Semaphore(4)

    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext null
        if (uri.scheme != "https" || uri.host != "cdn.modrinth.com" || uri.userInfo != null ||
            uri.port !in listOf(-1, 443)) return@withContext null
        synchronized(cache) { cache[url] }?.let { return@withContext it }
        requests.withPermit {
            withTimeout(8000) {
                http.prepareGet(url).execute { response ->
                    if (response.status.value != 200) return@execute null
                    val output = ByteArrayOutputStream()
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (output.size() + count > 512 * 1024) return@execute null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray().also { bytes -> synchronized(cache) { cache[url] = bytes } }
                }
            }
        }
    }
}

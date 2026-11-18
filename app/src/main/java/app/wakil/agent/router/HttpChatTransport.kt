package app.wakil.agent.router

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain HttpURLConnection transport (no extra dependency). The API key is only put in the Authorization header. */
class HttpChatTransport(private val timeoutMs: Int = 60_000) : ChatTransport {

    @Throws(IOException::class)
    override suspend fun post(url: String, apiKey: String, body: String): HttpReply = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val headers = HashMap<String, String>()
            for ((name, values) in conn.headerFields) {
                if (name != null && values.isNotEmpty()) headers[name.lowercase()] = values.first()
            }
            HttpReply(status, text, headers)
        } finally {
            conn.disconnect()
        }
    }
}

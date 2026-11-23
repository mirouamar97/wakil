package app.wakil.agent.router

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Plain HttpURLConnection transport with streaming support. The API key is only put in the Authorization header. */
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

    @Throws(IOException::class)
    override suspend fun stream(
        url: String,
        apiKey: String,
        body: String,
        onChunk: (String) -> Unit
    ): HttpReply = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")

            // Make sure stream is true in body
            val streamBody = try {
                JSONObject(body).put("stream", true).toString()
            } catch (_: Exception) {
                body
            }

            conn.outputStream.use { it.write(streamBody.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val headers = HashMap<String, String>()
            for ((name, values) in conn.headerFields) {
                if (name != null && values.isNotEmpty()) headers[name.lowercase()] = values.first()
            }

            if (status !in 200..299) {
                val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                return@withContext HttpReply(status, errText, headers)
            }

            val fullText = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullText.append(content)
                                onChunk(content)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val synthesizedBody = """{"choices":[{"message":{"role":"assistant","content":"${fullText.toString().replace("\"", "\\\"")}}}]}"""
            HttpReply(status, synthesizedBody, headers)
        } finally {
            conn.disconnect()
        }
    }
}

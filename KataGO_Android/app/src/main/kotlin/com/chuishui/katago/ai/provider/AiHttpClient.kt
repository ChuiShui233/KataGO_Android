package com.chuishui.katago.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Minimal HTTP abstraction so providers can be unit tested with a fake client. */
interface AiHttpClient {
    suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): AiHttpResponse
}

data class AiHttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Default [AiHttpClient] built on [HttpURLConnection]. Runs on Dispatchers.IO.
 */
class HttpUrlConnectionClient : AiHttpClient {

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): AiHttpResponse = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            AiHttpResponse(code, responseBody)
        } catch (e: SocketTimeoutException) {
            throw AiException.Timeout("request timed out", e)
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.NetworkError("http failure: ${e.message}", e)
        } finally {
            conn.disconnect()
        }
    }
}

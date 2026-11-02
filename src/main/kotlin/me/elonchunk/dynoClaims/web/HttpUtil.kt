package me.elonchunk.dynoClaims.web

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

object HttpUtil {

    val gson: Gson = Gson()

    private const val MAX_BODY_BYTES = 1_000_000
    private const val GZIP_THRESHOLD = 1024

    fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split("&")
            .filter { it.isNotEmpty() }
            .map { it.split("=", limit = 2) }
            .associate { parts ->
                val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else ""
                key to value
            }
    }

    /** Reads the request body. Returns "" if it is larger than 1 MB (the caller treats that as malformed). */
    fun readBody(exchange: HttpExchange): String {
        val bytes = exchange.requestBody.readNBytes(MAX_BODY_BYTES + 1)
        if (bytes.size > MAX_BODY_BYTES) return ""
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun sendJson(exchange: HttpExchange, status: Int, allowedOrigin: String, json: String) {
        var bytes = json.toByteArray(StandardCharsets.UTF_8)
        val headers = exchange.responseHeaders
        headers.add("Content-Type", "application/json; charset=utf-8")
        headers.add("Cache-Control", "no-store")
        headers.add("Access-Control-Allow-Origin", allowedOrigin)
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        headers.add("Access-Control-Allow-Headers", "Content-Type")
        headers.add("Access-Control-Max-Age", "600")

        val acceptsGzip = exchange.requestHeaders.getFirst("Accept-Encoding")
            ?.contains("gzip", ignoreCase = true) == true
        if (acceptsGzip && bytes.size > GZIP_THRESHOLD) {
            val buffer = ByteArrayOutputStream()
            GZIPOutputStream(buffer).use { it.write(bytes) }
            bytes = buffer.toByteArray()
            headers.add("Content-Encoding", "gzip")
        }

        if (status == 204 || bytes.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    fun handlePreflight(exchange: HttpExchange, allowedOrigin: String): Boolean {
        if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
            sendJson(exchange, 204, allowedOrigin, "")
            return true
        }
        return false
    }
}

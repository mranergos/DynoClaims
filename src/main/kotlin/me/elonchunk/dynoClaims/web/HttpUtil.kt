package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object HttpUtil {

    fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .associate { parts ->
                val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else ""
                key to value
            }
    }

    fun readBody(exchange: HttpExchange): String =
        exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)

    fun sendJson(exchange: HttpExchange, status: Int, allowedOrigin: String, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", allowedOrigin)
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { os: OutputStream -> os.write(bytes) }
    }

    fun handlePreflight(exchange: HttpExchange, allowedOrigin: String): Boolean {
        if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
            sendJson(exchange, 204, allowedOrigin, "")
            return true
        }
        return false
    }
}
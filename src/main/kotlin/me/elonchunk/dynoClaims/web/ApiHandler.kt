package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import me.elonchunk.dynoClaims.DynoClaims
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class Reply(val status: Int, val body: Any? = null, val rawJson: String? = null)

/**
 * Shared plumbing for every endpoint: CORS preflight, method check, JSON encoding,
 * error handling and "run this as the logged-in player on the main thread".
 */
abstract class ApiHandler(
    protected val plugin: DynoClaims,
    protected val allowedOrigin: String,
    private val method: String
) : HttpHandler {

    final override fun handle(exchange: HttpExchange) {
        try {
            if (HttpUtil.handlePreflight(exchange, allowedOrigin)) return

            val reply = if (!exchange.requestMethod.equals(method, ignoreCase = true)) {
                fail(405, "$method only")
            } else {
                process(exchange)
            }
            val json = reply.rawJson ?: HttpUtil.gson.toJson(reply.body)
            HttpUtil.sendJson(exchange, reply.status, allowedOrigin, json)
        } catch (e: Exception) {
            plugin.logger.warning("${javaClass.simpleName} failed: ${e.javaClass.simpleName}: ${e.message}")
            try {
                HttpUtil.sendJson(
                    exchange, 500, allowedOrigin,
                    HttpUtil.gson.toJson(mapOf("ok" to false, "error" to "Server error. Check the server console."))
                )
            } catch (_: Exception) {
                // client already gone
            }
        } finally {
            exchange.close()
        }
    }

    protected abstract fun process(exchange: HttpExchange): Reply

    protected fun fail(status: Int, message: String): Reply =
        Reply(status, mapOf("ok" to false, "error" to message))

    /** Resolves the token, then runs [block] for that player on the server thread. */
    protected fun withPlayer(token: String?, block: (Player) -> Reply): Reply {
        val entry = plugin.tokenStore.resolve(token)
            ?: return fail(401, "This link is invalid or expired. Run /webclaim in-game to get a new one.")

        return MainThread.call(plugin) {
            val player = Bukkit.getPlayer(entry.playerId)
            if (player == null || !player.isOnline) {
                fail(409, "You need to be online in-game to do that.")
            } else {
                block(player)
            }
        }
    }
}

package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ClaimHandler(
    private val plugin: DynoClaims,
    private val allowedOrigin: String
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        if (HttpUtil.handlePreflight(exchange, allowedOrigin)) return

        if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
            HttpUtil.sendJson(exchange, 405, allowedOrigin, """{"ok":false,"error":"POST only"}""")
            return
        }

        val req = ClaimRequest.fromJson(HttpUtil.readBody(exchange))
        val player = SessionUtil.resolvePlayerOrRespond(exchange, plugin, req.token, allowedOrigin) ?: return

        val resultFuture = CompletableFuture<String>()

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val town = TownyBridge.getTown(player)
            val world = Bukkit.getWorld(req.world ?: "")

            if (town == null || world == null) {
                resultFuture.complete("""{"ok":false,"error":"Missing town or world."}""")
                return@Runnable
            }

            // Multi-pass claiming: Towny checks adjacency against the
            // CURRENT real town shape at the moment each chunk is claimed.
            // A big blob selection has to grow outward one connected ring
            // at a time, so we keep looping over the still-unclaimed list
            // until either everything succeeds or a full pass makes zero
            // progress (meaning whatever's left is genuinely disconnected,
            // e.g. a separate landmass).
            val remaining = req.chunks.toMutableList()
            var claimed = 0
            var madeProgressThisPass: Boolean

            do {
                madeProgressThisPass = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val chunk = iterator.next()
                    val success = TownyBridge.claimChunk(town, world, chunk[0], chunk[1])
                    if (success) {
                        claimed++
                        iterator.remove()
                        madeProgressThisPass = true
                    }
                }
            } while (madeProgressThisPass && remaining.isNotEmpty())

            val failed = remaining.size
            resultFuture.complete("""{"ok":true,"claimed":$claimed,"failed":$failed}""")
        })

        try {
            val json = resultFuture.get(15, TimeUnit.SECONDS)
            HttpUtil.sendJson(exchange, 200, allowedOrigin, json)
        } catch (e: Exception) {
            plugin.logger.warning("Claim request failed: ${e.message}")
            HttpUtil.sendJson(exchange, 500, allowedOrigin,
                """{"ok":false,"error":"Claim timed out or failed on the server."}""")
        }
    }
}
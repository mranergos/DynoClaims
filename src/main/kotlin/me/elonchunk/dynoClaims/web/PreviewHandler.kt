package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge
import org.bukkit.Bukkit

/**
 * POST /api/preview — tells the page which of the given chunks are already owned,
 * so it can drop them from the selection before the player confirms.
 */
class PreviewHandler(plugin: DynoClaims, origin: String) : ApiHandler(plugin, origin, "POST") {

    private companion object {
        const val MAX_CHECK = 50_000
    }

    override fun process(exchange: HttpExchange): Reply {
        val req = ClaimRequest.fromJson(HttpUtil.readBody(exchange))
            ?: return fail(400, "Malformed request.")
        if (req.chunks.size > MAX_CHECK) return fail(413, "Selection is too large to check.")

        return withPlayer(req.token) { player ->
            TownyBridge.getTown(player) ?: return@withPlayer fail(403, "You need a town first.")
            val world = Bukkit.getWorld(req.world ?: "")
                ?: return@withPlayer fail(400, "Unknown world \"${req.world}\".")

            val owned = req.chunks.filter { !TownyBridge.isChunkFree(world, it[0], it[1]) }

            Reply(200, mapOf(
                "ok" to true,
                "claimable" to req.chunks.size - owned.size,
                "owned" to owned,
                "cost" to 0
            ))
        }
    }
}

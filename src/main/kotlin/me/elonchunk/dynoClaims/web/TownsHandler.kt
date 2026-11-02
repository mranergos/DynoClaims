package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge
import org.bukkit.Bukkit

/**
 * GET /api/towns?world=<name> — every claimed chunk in the world as per-town runs
 * ([z, xStart, length]). Cached for a few seconds so many open map pages don't each
 * make the server thread walk every town block.
 */
class TownsHandler(plugin: DynoClaims, origin: String) : ApiHandler(plugin, origin, "GET") {

    private companion object {
        const val CACHE_MILLIS = 5_000L
    }

    override fun process(exchange: HttpExchange): Reply {
        val worldName = HttpUtil.parseQuery(exchange.requestURI.rawQuery)["world"].orEmpty()

        val now = System.currentTimeMillis()
        plugin.townsCache[worldName]?.let { (time, json) ->
            if (now - time < CACHE_MILLIS) return Reply(200, rawJson = json)
        }

        return MainThread.call(plugin) {
            val world = Bukkit.getWorld(worldName) ?: return@call fail(400, "Unknown world.")
            val json = HttpUtil.gson.toJson(mapOf("ok" to true, "towns" to TownyBridge.getClaimedRuns(world)))
            plugin.townsCache[worldName] = System.currentTimeMillis() to json
            Reply(200, rawJson = json)
        }
    }
}

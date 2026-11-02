package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge

/** GET /api/session?token=...  — who is this, which town, which world, where are they standing. */
class SessionHandler(plugin: DynoClaims, origin: String) : ApiHandler(plugin, origin, "GET") {

    override fun process(exchange: HttpExchange): Reply {
        val token = HttpUtil.parseQuery(exchange.requestURI.rawQuery)["token"]

        return withPlayer(token) { player ->
            val town = TownyBridge.getTown(player)
                ?: return@withPlayer fail(403, "You need to create or join a town before claiming land.")

            val world = player.world
            val location = player.location

            Reply(200, mapOf(
                "ok" to true,
                "player" to player.name,
                "town" to town.name,
                "nation" to TownyBridge.getNationName(player),
                "world" to plugin.cfg("web.world-name").ifEmpty { world.name },
                "mapWorld" to plugin.cfg("web.squaremap-world-id").ifEmpty { plugin.mapWorldId(world) },
                "x" to location.blockX,
                "z" to location.blockZ,
                "maxChunks" to plugin.maxChunksPerRequest(),
                "canClaim" to plugin.mayClaim(player)
            ))
        }
    }
}

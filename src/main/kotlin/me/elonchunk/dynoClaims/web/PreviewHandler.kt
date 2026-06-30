package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge
import org.bukkit.Bukkit

class PreviewHandler(
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
        plugin.logger.info("Preview request: world='${req.world}' chunks=${req.chunks.map { "[${it[0]},${it[1]}]" }}")

        val player = SessionUtil.resolvePlayerOrRespond(exchange, plugin, req.token, allowedOrigin) ?: return

        val town = TownyBridge.getTown(player)
        if (town == null) {
            HttpUtil.sendJson(exchange, 403, allowedOrigin,
                """{"ok":false,"error":"You need a town first."}""")
            return
        }

        val world = Bukkit.getWorld(req.world ?: "")
        if (world == null) {
            plugin.logger.warning("Preview: world '${req.world}' did not resolve to any loaded Bukkit world.")
            HttpUtil.sendJson(exchange, 400, allowedOrigin,
                """{"ok":false,"error":"Unknown world."}""")
            return
        }
        plugin.logger.info("Preview: resolved world to '${world.name}'")

        var claimable = 0
        var rejected = 0
        for (chunk in req.chunks) {
            val free = TownyBridge.isChunkFree(world, chunk[0], chunk[1])
            val reason = if (free) TownyBridge.checkClaimAllowed(town, world, chunk[0], chunk[1]) else "Already claimed"
            val allowed = reason == null
            plugin.logger.info("Checking chunk [${chunk[0]}, ${chunk[1]}] in world '${world.name}' -> free=$free allowed=$allowed reason=$reason")
            if (allowed) claimable++ else rejected++
        }

        val json = """{"ok":true,"claimable":$claimable,"rejected":$rejected,"cost":0}"""
        HttpUtil.sendJson(exchange, 200, allowedOrigin, json)
    }
}
package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge

class SessionHandler(
    private val plugin: DynoClaims,
    private val allowedOrigin: String
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        if (HttpUtil.handlePreflight(exchange, allowedOrigin)) return

        val query = HttpUtil.parseQuery(exchange.requestURI.query)
        val player = SessionUtil.resolvePlayerOrRespond(exchange, plugin, query["token"], allowedOrigin) ?: return

        val town = TownyBridge.getTown(player)
        if (town == null) {
            HttpUtil.sendJson(exchange, 403, allowedOrigin,
                """{"ok":false,"error":"You need to create or join a town before claiming land."}""")
            return
        }

        val nationName = TownyBridge.getNationName(player)
        val json = """{"ok":true,"player":"${esc(player.name)}","town":"${esc(town.name)}","nation":"${esc(nationName)}"}"""
        HttpUtil.sendJson(exchange, 200, allowedOrigin, json)
    }

    private fun esc(s: String) = s.replace("\"", "\\\"")
}
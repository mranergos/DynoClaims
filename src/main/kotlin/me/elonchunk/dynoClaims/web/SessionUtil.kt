package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import me.elonchunk.dynoClaims.DynoClaims
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object SessionUtil {

    fun resolvePlayerOrRespond(
        exchange: HttpExchange,
        plugin: DynoClaims,
        token: String?,
        allowedOrigin: String
    ): Player? {
        val entry = plugin.tokenStore.resolve(token)
        if (entry == null) {
            HttpUtil.sendJson(exchange, 401, allowedOrigin,
                """{"ok":false,"error":"Invalid or expired token. Run /dynoclaim verify again."}""")
            return null
        }

        val player = Bukkit.getPlayer(entry.playerId)
        if (player == null || !player.isOnline) {
            HttpUtil.sendJson(exchange, 409, allowedOrigin,
                """{"ok":false,"error":"You must be online in-game to do that."}""")
            return null
        }

        return player
    }
}
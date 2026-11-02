package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import me.elonchunk.dynoClaims.DynoClaims

/** GET /api/ping — open http://your-server:PORT/api/ping in a browser to test that the port is reachable. */
class PingHandler(plugin: DynoClaims, origin: String) : ApiHandler(plugin, origin, "GET") {
    override fun process(exchange: HttpExchange): Reply =
        Reply(200, mapOf("ok" to true, "plugin" to "DynoClaims", "version" to plugin.pluginMeta.version))
}

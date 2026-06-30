package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpServer
import me.elonchunk.dynoClaims.DynoClaims
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class ClaimHttpServer(plugin: DynoClaims, port: Int) {

    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)

    init {
        val allowedOrigin = plugin.config.getString("web.allowed-origin", "*") ?: "*"

        server.createContext("/api/session", SessionHandler(plugin, allowedOrigin))
        server.createContext("/api/preview", PreviewHandler(plugin, allowedOrigin))
        server.createContext("/api/claim", ClaimHandler(plugin, allowedOrigin))

        server.executor = Executors.newFixedThreadPool(4)
    }

    fun start() = server.start()
    fun stop() = server.stop(0)
}
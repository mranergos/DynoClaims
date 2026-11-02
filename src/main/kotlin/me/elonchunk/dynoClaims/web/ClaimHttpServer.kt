package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpServer
import me.elonchunk.dynoClaims.DynoClaims
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ClaimHttpServer(plugin: DynoClaims, bindAddress: String, port: Int) {

    private val executor: ExecutorService = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "DynoClaims-HTTP").apply { isDaemon = true }
    }
    private val server: HttpServer = HttpServer.create(InetSocketAddress(bindAddress, port), 0)

    init {
        val origin = plugin.cfg("web.allowed-origin").ifEmpty { "*" }

        server.createContext("/api/ping", PingHandler(plugin, origin))
        server.createContext("/api/session", SessionHandler(plugin, origin))
        server.createContext("/api/preview", PreviewHandler(plugin, origin))
        server.createContext("/api/claim", ClaimHandler(plugin, origin))
        server.createContext("/api/towns", TownsHandler(plugin, origin))

        server.executor = executor
    }

    fun start() = server.start()

    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }
}

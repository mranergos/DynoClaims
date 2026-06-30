package me.elonchunk.dynoClaims

import me.elonchunk.dynoClaims.commands.DynoClaimCommand
import me.elonchunk.dynoClaims.store.TokenStore
import me.elonchunk.dynoClaims.web.ClaimHttpServer
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.util.logging.Level

class DynoClaims : JavaPlugin() {

    lateinit var tokenStore: TokenStore
        private set

    private var httpServer: ClaimHttpServer? = null

    override fun onEnable() {
        saveDefaultConfig()
        tokenStore = TokenStore()

        val port = config.getInt("web.port", 8124)
        try {
            val server = ClaimHttpServer(this, port)
            server.start()
            httpServer = server
            logger.info("DynoClaims web API listening on port $port")
        } catch (e: IOException) {
            logger.log(Level.SEVERE, "Could not start DynoClaims web server on port $port", e)
        }

        val claimCommand = DynoClaimCommand(this)
        getCommand("webclaim")?.setExecutor(claimCommand)
    }

    override fun onDisable() {
        httpServer?.stop()
        httpServer = null
    }
}
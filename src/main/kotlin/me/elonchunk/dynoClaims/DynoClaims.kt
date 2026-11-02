package me.elonchunk.dynoClaims

import me.elonchunk.dynoClaims.commands.DynoClaimCommand
import me.elonchunk.dynoClaims.commands.ReloadCommand
import me.elonchunk.dynoClaims.store.TokenStore
import me.elonchunk.dynoClaims.web.ClaimHttpServer
import me.elonchunk.dynoClaims.web.MapPlatform
import me.elonchunk.dynoClaims.web.WebInstaller
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

class DynoClaims : JavaPlugin() {

    lateinit var tokenStore: TokenStore
        private set

    /** world name -> (timestamp, json) for /api/towns. Cleared whenever someone claims. */
    val townsCache = ConcurrentHashMap<String, Pair<Long, String>>()

    private var httpServer: ClaimHttpServer? = null
    private var installResults: Map<MapPlatform, String> = emptyMap()

    override fun onEnable() {
        saveDefaultConfig()
        tokenStore = TokenStore()

        getCommand("webclaim")?.setExecutor(DynoClaimCommand(this))
        getCommand("dynoclaimreload")?.setExecutor(ReloadCommand(this))

        startHttpServer()

        // Expired tokens used to pile up forever; sweep them once a minute.
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { tokenStore.sweepExpired() }, 1200L, 1200L)

        // Give Dynmap/squaremap a moment to create their web folders, then deploy and report.
        server.scheduler.runTaskLater(this, Runnable {
            installWebFiles()
            statusLines().forEach { logger.info(it) }
        }, 40L)
    }

    override fun onDisable() {
        httpServer?.stop()
        httpServer = null
    }

    // ---- lifecycle helpers -------------------------------------------------------------------

    fun startHttpServer(): Boolean {
        httpServer?.stop()
        httpServer = null

        val port = apiPort()
        val bind = cfg("web.bind-address").ifEmpty { "0.0.0.0" }
        return try {
            val created = ClaimHttpServer(this, bind, port)
            created.start()
            httpServer = created
            true
        } catch (e: Exception) {
            logger.severe(
                "Could not start the claim API on $bind:$port (${e.message}). " +
                    "Is the port already in use? Change web.port in config.yml and run /dynoclaimreload."
            )
            false
        }
    }

    fun installWebFiles() {
        installResults = WebInstaller(this).installAll()
    }

    /** Reloads config, restarts the API server (so port changes apply) and redeploys the web files. */
    fun reloadAll(): List<String> {
        reloadConfig()
        townsCache.clear()
        startHttpServer()
        installWebFiles()
        return statusLines()
    }

    fun statusLines(): List<String> {
        val lines = ArrayList<String>()
        lines += if (httpServer != null) {
            "Claim API: listening on ${cfg("web.bind-address").ifEmpty { "0.0.0.0" }}:${apiPort()} " +
                "(test it: http://<your-server>:${apiPort()}/api/ping)"
        } else {
            "Claim API: NOT RUNNING - see the error above in the console."
        }

        var anyMap = false
        for (platform in MapPlatform.entries) {
            val mapPlugin = enabledMapPlugin(platform) ?: continue
            anyMap = true
            lines += "${platform.pluginName}: web port ${platform.detectPort(mapPlugin)} - ${installResults[platform] ?: "not deployed yet"}"
        }
        if (!anyMap) lines += "No supported map plugin found. Install Dynmap or squaremap."

        val base = webBaseUrl(null)
        lines += if (base != null) {
            "Claim links will use: $base/claimmanager.html"
        } else {
            "Claim links will use the address each player joined with (set web.public-host to override)."
        }
        return lines
    }

    // ---- config helpers ----------------------------------------------------------------------

    fun cfg(path: String): String = config.getString(path, "")?.trim() ?: ""

    fun apiPort(): Int = config.getInt("web.port", 12011)

    fun maxChunksPerRequest(): Int = config.getInt("claims.max-chunks-per-request", 400).coerceIn(1, 5000)

    fun mayClaim(player: Player): Boolean {
        val node = cfg("claims.permission")
        return node.isEmpty() || player.hasPermission(node)
    }

    /** squaremap names a world's tile folder after its key, with ':' replaced by '_'. */
    fun mapWorldId(world: World): String = world.key.toString().replace(':', '_')

    // ---- map plugin detection ----------------------------------------------------------------

    fun enabledMapPlugin(platform: MapPlatform): Plugin? =
        server.pluginManager.getPlugin(platform.pluginName)?.takeIf { it.isEnabled }

    fun preferredPlatform(): MapPlatform? {
        val wanted = cfg("web.map-platform").lowercase()
        MapPlatform.entries
            .firstOrNull { it.name.lowercase() == wanted && enabledMapPlugin(it) != null }
            ?.let { return it }
        return listOf(MapPlatform.SQUAREMAP, MapPlatform.DYNMAP).firstOrNull { enabledMapPlugin(it) != null }
    }

    /**
     * Where the claim page lives. Explicit web.web-base-url wins; otherwise it's built from
     * web.public-host (or the address the player joined with) plus the map plugin's own port.
     */
    fun webBaseUrl(playerHost: String?): String? {
        cfg("web.web-base-url").trimEnd('/').takeIf { it.isNotEmpty() }?.let { return it }

        val platform = preferredPlatform() ?: return null
        val host = cfg("web.public-host").ifEmpty { playerHost ?: return null }
        val port = platform.detectPort(enabledMapPlugin(platform) ?: return null)
        return "http://$host:$port"
    }
}

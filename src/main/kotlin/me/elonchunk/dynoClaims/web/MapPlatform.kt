package me.elonchunk.dynoClaims.web

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

enum class MapPlatform(val pluginName: String, val templateFolder: String, val defaultPort: Int) {
    DYNMAP("dynmap", "dynmap", 8123),
    SQUAREMAP("squaremap", "squaremap", 8080);

    /** Best-effort read of the map plugin's own web-server port, so admins don't have to repeat it. */
    fun detectPort(mapPlugin: Plugin): Int {
        val configFile = when (this) {
            DYNMAP -> File(mapPlugin.dataFolder, "configuration.txt")
            SQUAREMAP -> File(mapPlugin.dataFolder, "config.yml")
        }
        if (!configFile.isFile) return defaultPort

        val yaml = try {
            YamlConfiguration.loadConfiguration(configFile)
        } catch (_: Exception) {
            return defaultPort
        }

        return when (this) {
            DYNMAP -> yaml.getInt("webserver-port", defaultPort)
            SQUAREMAP -> yaml.getInt(
                "settings.internal-webserver.port",
                yaml.getInt("internal-webserver.port", defaultPort)
            )
        }
    }
}

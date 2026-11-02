package me.elonchunk.dynoClaims.web

import me.elonchunk.dynoClaims.DynoClaims
import java.io.File
import java.io.IOException

class WebInstaller(private val plugin: DynoClaims) {

    /** Copies the claim page into every installed map plugin. Returns a human-readable result per platform. */
    fun installAll(): Map<MapPlatform, String> {
        val results = LinkedHashMap<MapPlatform, String>()

        for (platform in MapPlatform.entries) {
            val mapPlugin = plugin.enabledMapPlugin(platform) ?: continue
            val webFolder = File(mapPlugin.dataFolder, "web")

            results[platform] = when {
                !webFolder.isDirectory ->
                    "web folder not found at ${webFolder.path} (let ${platform.pluginName} start once, then run /dynoclaimreload)"
                else -> try {
                    install(platform, webFolder)
                    "claim page installed in ${webFolder.path}"
                } catch (e: IOException) {
                    "FAILED to write files: ${e.message}"
                }
            }
        }
        return results
    }

    private fun install(platform: MapPlatform, webFolder: File) {
        val vars = mapOf(
            "{{API_BASE}}" to js(plugin.cfg("web.public-base-url").trimEnd('/')),
            "{{API_PORT}}" to plugin.apiPort().toString(),
            "{{SELECTION_COLOR}}" to js(plugin.cfg("web.selection-color").ifEmpty { "#000000" }),
            "{{VERSION}}" to js(plugin.pluginMeta.version)
        )

        val files = listOf(
            "common/claimcore.js" to "claimcore.js",
            "${platform.templateFolder}/claimmanager.html" to "claimmanager.html",
            "${platform.templateFolder}/claimmanager.js" to "claimmanager.js"
        )

        for ((resource, target) in files) {
            val stream = plugin.getResource("webtemplates/$resource")
                ?: throw IOException("Missing bundled template: $resource")

            var content = stream.bufferedReader().use { it.readText() }
            for ((placeholder, value) in vars) content = content.replace(placeholder, value)

            val out = File(webFolder, target)
            if (!out.exists() || out.readText() != content) out.writeText(content)
        }
    }

    /** Makes a config value safe to drop inside a JS string literal. */
    private fun js(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
}

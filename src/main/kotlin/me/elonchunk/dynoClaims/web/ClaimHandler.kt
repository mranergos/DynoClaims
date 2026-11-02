package me.elonchunk.dynoClaims.web

import com.sun.net.httpserver.HttpExchange
import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge
import org.bukkit.Bukkit

/** POST /api/claim — claims the given chunks for the player's town. */
class ClaimHandler(plugin: DynoClaims, origin: String) : ApiHandler(plugin, origin, "POST") {

    private companion object {
        val NEIGHBORS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
    }

    override fun process(exchange: HttpExchange): Reply {
        val req = ClaimRequest.fromJson(HttpUtil.readBody(exchange))
            ?: return fail(400, "Malformed request.")

        return withPlayer(req.token) { player ->
            if (!plugin.mayClaim(player)) {
                return@withPlayer fail(403, "You don't have permission to claim land for your town.")
            }
            val town = TownyBridge.getTown(player)
                ?: return@withPlayer fail(403, "You need to create or join a town before claiming land.")
            val world = Bukkit.getWorld(req.world ?: "")
                ?: return@withPlayer fail(400, "Unknown world \"${req.world}\".")

            val max = plugin.maxChunksPerRequest()
            if (req.chunks.size > max) {
                return@withPlayer fail(413, "Too many chunks in one request (the limit is $max).")
            }

            // Towny only accepts a chunk if it touches the town's CURRENT shape, so a big blob has to
            // grow outward. Instead of looping over the whole list until nothing changes (O(n * passes)),
            // every chunk is tried once and is only retried when one of its neighbours gets claimed.
            val pending = LinkedHashMap<Long, IntArray>()
            for (chunk in req.chunks) pending.putIfAbsent(key(chunk[0], chunk[1]), chunk)

            val queue = java.util.ArrayDeque<IntArray>(pending.values)
            val lastReason = HashMap<Long, String>()
            var claimed = 0
            var skippedOwned = 0

            while (true) {
                val chunk = queue.poll() ?: break
                val k = key(chunk[0], chunk[1])
                if (!pending.containsKey(k)) continue

                val reason = TownyBridge.claimChunk(town, world, chunk[0], chunk[1])
                if (reason == null) {
                    pending.remove(k)
                    lastReason.remove(k)
                    claimed++
                    for (n in NEIGHBORS) {
                        pending[key(chunk[0] + n[0], chunk[1] + n[1])]?.let { queue.add(it) }
                    }
                } else if (reason == TownyBridge.ALREADY_CLAIMED) {
                    pending.remove(k)
                    skippedOwned++
                } else {
                    lastReason[k] = reason
                }
            }

            val failedChunks = pending.values.toList()
            val reasons = lastReason.values.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }
                .take(5)
                .map { mapOf("reason" to it.key, "count" to it.value) }

            if (claimed > 0) {
                plugin.townsCache.clear()
                plugin.logger.info("${player.name} claimed $claimed chunk(s) for ${town.name} via the web map.")
            }

            Reply(200, mapOf(
                "ok" to true,
                "claimed" to claimed,
                "failed" to failedChunks.size,
                "failedChunks" to failedChunks,
                "reasons" to reasons,
                "skippedOwned" to skippedOwned
            ))
        }
    }
}

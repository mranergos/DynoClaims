package me.elonchunk.dynoClaims.towny

import com.palmergames.bukkit.towny.TownyAPI
import com.palmergames.bukkit.towny.`object`.Resident
import com.palmergames.bukkit.towny.`object`.Town
import com.palmergames.bukkit.towny.`object`.TownBlock
import com.palmergames.bukkit.towny.`object`.TownBlockType
import com.palmergames.bukkit.towny.`object`.WorldCoord
import org.bukkit.World
import org.bukkit.entity.Player

object TownyBridge {

    const val ALREADY_CLAIMED = "Already claimed"

    private val api: TownyAPI get() = TownyAPI.getInstance()

    fun getResident(player: Player): Resident? = api.getResident(player)

    fun getTown(player: Player): Town? {
        val resident = getResident(player) ?: return null
        return api.getResidentTownOrNull(resident)
    }

    fun getNationName(player: Player): String {
        val town = getTown(player) ?: return ""
        val nation = api.getTownNationOrNull(town)
        return nation?.name ?: ""
    }

    fun isChunkFree(world: World, chunkX: Int, chunkZ: Int): Boolean =
        api.getTownBlock(WorldCoord(world.name, chunkX, chunkZ)) == null

    /** Returns null if Towny would allow the claim, otherwise a readable reason. */
    fun checkClaimAllowed(town: Town, worldCoord: WorldCoord): String? =
        try {
            api.testTownClaimOrThrow(town, worldCoord, false, false)
            null
        } catch (e: Exception) {
            clean(e.message ?: "Claim not allowed for this chunk.")
        }

    /** Returns null on success, otherwise the reason the chunk could not be claimed. */
    fun claimChunk(town: Town, world: World, chunkX: Int, chunkZ: Int): String? {
        val worldCoord = WorldCoord(world.name, chunkX, chunkZ)

        if (api.getTownBlock(worldCoord) != null) return ALREADY_CLAIMED
        checkClaimAllowed(town, worldCoord)?.let { return it }

        return try {
            val townyWorld = api.getTownyWorld(world) ?: return "This world is not managed by Towny."
            val townBlock = TownBlock(chunkX, chunkZ, townyWorld)
            townBlock.town = town
            townBlock.setType(TownBlockType.RESIDENTIAL)
            townBlock.save()
            null
        } catch (e: Exception) {
            clean(e.message ?: "Unexpected error while claiming.")
        }
    }

    /**
     * All claimed chunks in [world], grouped per town and run-length encoded as
     * [z, xStart, length] rows. A 5000-chunk town becomes a few dozen runs, which
     * keeps the response tiny and lets the web map draw one rectangle per run.
     */
    fun getClaimedRuns(world: World): List<Map<String, Any>> {
        val townyWorld = api.getTownyWorld(world) ?: return emptyList()

        val cellsByTown = HashMap<String, MutableList<IntArray>>()
        for (tb in townyWorld.townBlocks) {
            val name = tb.townOrNull?.name ?: continue
            cellsByTown.getOrPut(name) { ArrayList() }.add(intArrayOf(tb.x, tb.z))
        }

        return cellsByTown.map { (name, cells) ->
            cells.sortWith(compareBy({ it[1] }, { it[0] }))
            val runs = ArrayList<IntArray>()
            var current: IntArray? = null
            for (cell in cells) {
                val run = current
                if (run != null && run[0] == cell[1] && run[1] + run[2] == cell[0]) {
                    run[2]++
                } else {
                    val fresh = intArrayOf(cell[1], cell[0], 1)
                    runs.add(fresh)
                    current = fresh
                }
            }
            mapOf("town" to name, "runs" to runs)
        }
    }

    /** Strips Minecraft colour codes from Towny's messages. */
    private fun clean(message: String): String = message.replace(Regex("§."), "").trim()
}

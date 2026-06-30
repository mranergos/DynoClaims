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

    fun isChunkFree(world: World, chunkX: Int, chunkZ: Int): Boolean {
        val worldCoord = WorldCoord(world.name, chunkX, chunkZ)
        return api.getTownBlock(worldCoord) == null
    }

    fun checkClaimAllowed(town: Town, world: World, chunkX: Int, chunkZ: Int): String? {
        val worldCoord = WorldCoord(world.name, chunkX, chunkZ)
        return try {
            api.testTownClaimOrThrow(town, worldCoord, false, false)
            null
        } catch (e: Exception) {
            e.message ?: "Claim not allowed for this chunk."
        }
    }

    fun claimChunk(town: Town, world: World, chunkX: Int, chunkZ: Int): Boolean {
        val worldCoord = WorldCoord(world.name, chunkX, chunkZ)

        if (api.getTownBlock(worldCoord) != null) return false
        if (checkClaimAllowed(town, world, chunkX, chunkZ) != null) return false

        return try {
            val townyWorld = api.getTownyWorld(world) ?: return false
            val townBlock = TownBlock(chunkX, chunkZ, townyWorld)
            townBlock.town = town
            townBlock.setType(TownBlockType.RESIDENTIAL)
            townBlock.save()
            true
        } catch (_: Exception) {
            false
        }
    }
}
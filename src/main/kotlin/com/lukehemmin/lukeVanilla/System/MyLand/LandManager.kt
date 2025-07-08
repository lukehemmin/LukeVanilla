package com.lukehemmin.lukeVanilla.System.MyLand

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import org.bukkit.Chunk
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.floor

data class LandArea(val worldName: String, val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
    val minChunkX = floor(minX.toDouble() / 16).toInt()
    val maxChunkX = floor(maxX.toDouble() / 16).toInt()
    val minChunkZ = floor(minZ.toDouble() / 16).toInt()
    val maxChunkZ = floor(maxZ.toDouble() / 16).toInt()
}

class LandManager(private val plugin: Main, private val database: Database, private val debugManager: DebugManager) {
    // Player UUID -> List of their claimed chunks (as Pair<Int, Int> for X and Z) per world
    private val ownedChunks = mutableMapOf<UUID, MutableMap<String, MutableList<Pair<Int, Int>>>>()
    private var claimedChunks = mutableMapOf<String, MutableMap<Pair<Int, Int>, UUID>>()

    private var useAreaRestriction: Boolean = true
    private var claimableArea: LandArea? = null
    private val landData: LandData

    init {
        this.landData = LandData(database)
    }

    fun loadClaimsFromDatabase() {
        claimedChunks = landData.loadAllClaims()
        // Populate ownedChunks from the loaded claimedChunks
        ownedChunks.clear()
        claimedChunks.forEach { (worldName, chunks) ->
            chunks.forEach { (chunkCoords, ownerId) ->
                ownedChunks.computeIfAbsent(ownerId) { mutableMapOf() }
                    .computeIfAbsent(worldName) { mutableListOf() }
                    .add(chunkCoords)
            }
        }
        plugin.logger.info("[MyLand] ${claimedChunks.values.sumOf { it.size }}개의 토지 정보를 데이터베이스에서 불러왔습니다.")
    }

    fun loadConfig() {
        plugin.config.getConfigurationSection("myland")?.let { section ->
            useAreaRestriction = section.getBoolean("use-area-restriction", true)
            if (useAreaRestriction) {
                val worldName = section.getString("area.world") ?: "world"
                val x1 = section.getInt("area.x1")
                val z1 = section.getInt("area.z1")
                val x2 = section.getInt("area.x2")
                val z2 = section.getInt("area.z2")
                claimableArea = LandArea(worldName, minOf(x1, x2), maxOf(x1, x2), minOf(z1, z2), maxOf(z1, z2))
                plugin.logger.info("[MyLand] 지역 제한이 활성화되었습니다. 월드: $worldName, 좌표: ($x1, $z1) - ($x2, $z2)")
            } else {
                plugin.logger.info("[MyLand] 지역 제한이 비활성화되었습니다. 모든 지역에서 땅을 구매할 수 있습니다.")
            }
        }
    }

    fun isChunkInClaimableArea(chunk: Chunk): Boolean {
        if (!useAreaRestriction) return true
        
        val area = claimableArea ?: return false
        if (chunk.world.name != area.worldName) {
            return false
        }
        
        return chunk.x in area.minChunkX..area.maxChunkX &&
               chunk.z in area.minChunkZ..area.maxChunkZ
    }

    fun isChunkClaimed(chunk: Chunk): Boolean {
        return claimedChunks[chunk.world.name]?.containsKey(chunk.x to chunk.z) ?: false
    }

    fun getOwnerOfChunk(chunk: Chunk): UUID? {
        return claimedChunks[chunk.world.name]?.get(chunk.x to chunk.z)
    }

    fun getClaimInfo(chunk: Chunk): ClaimInfo? {
        return landData.getClaimInfo(chunk.world.name, chunk.x, chunk.z)
    }

    fun claimChunk(chunk: Chunk, player: Player): ClaimResult {
        debugManager.log("MyLand", "Attempting to claim chunk (${chunk.x}, ${chunk.z}) for ${player.name}.")
        if (!isChunkInClaimableArea(chunk)) {
            debugManager.log("MyLand", "Claim failed: Chunk is NOT in the claimable area.")
            return ClaimResult.NOT_IN_AREA
        }
        if (isChunkClaimed(chunk)) {
            debugManager.log("MyLand", "Claim failed: Chunk is ALREADY claimed by ${getOwnerOfChunk(chunk)}.")
            return ClaimResult.ALREADY_CLAIMED
        }
        
        val chunkCoords = chunk.x to chunk.z
        val worldName = chunk.world.name

        // Add to claimedChunks map
        claimedChunks.computeIfAbsent(worldName) { mutableMapOf() }[chunkCoords] = player.uniqueId

        // Add to ownedChunks map
        ownedChunks.computeIfAbsent(player.uniqueId) { mutableMapOf() }
            .computeIfAbsent(worldName) { mutableListOf() }
            .add(chunkCoords)
            
        // Save to database
        landData.saveClaim(worldName, chunk.x, chunk.z, player.uniqueId)
        
        debugManager.log("MyLand", "Claim successful for chunk (${chunk.x}, ${chunk.z}).")
            
        return ClaimResult.SUCCESS
    }
    
    fun unclaimChunk(chunk: Chunk, actor: Player?, reason: String): UnclaimResult {
        val ownerUuid = getOwnerOfChunk(chunk)
        if (ownerUuid == null) {
            return UnclaimResult.NOT_CLAIMED
        }
        
        // If actor is a player (not system), check for permission if they are not the owner
        if (actor != null && ownerUuid != actor.uniqueId && !actor.hasPermission("myland.admin.unclaim")) {
             return UnclaimResult.NO_PERMISSION
        }
        
        val chunkCoords = chunk.x to chunk.z
        val worldName = chunk.world.name
        
        claimedChunks[worldName]?.remove(chunkCoords)
        ownedChunks[ownerUuid]?.get(worldName)?.remove(chunkCoords)
        
        // Delete from database
        landData.deleteClaim(worldName, chunk.x, chunk.z)
        
        // Log the history
        landData.logClaimHistory(worldName, chunk.x, chunk.z, ownerUuid, actor?.uniqueId, reason)
        
        debugManager.log("MyLand", "Unclaim successful for chunk (${chunk.x}, ${chunk.z}). Actor: ${actor?.name ?: "System"}, Reason: $reason")
        
        return UnclaimResult.SUCCESS
    }
}

enum class ClaimResult {
    SUCCESS,
    ALREADY_CLAIMED,
    NOT_IN_AREA,
}

enum class UnclaimResult {
    SUCCESS,
    NOT_CLAIMED,
    NO_PERMISSION
} 
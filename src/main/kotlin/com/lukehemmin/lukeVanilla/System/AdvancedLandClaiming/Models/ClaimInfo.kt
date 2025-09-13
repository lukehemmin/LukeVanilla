package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models

import java.util.*

data class AdvancedClaimInfo(
    val chunkX: Int,
    val chunkZ: Int,
    val worldName: String,
    val ownerUuid: UUID,
    val ownerName: String,
    val claimType: ClaimType,
    val createdAt: Long,
    val lastUpdated: Long,
    val villageId: Int? = null,
    val claimCost: ClaimCost? = null
)

enum class ClaimType {
    PERSONAL,
    VILLAGE
}

data class ClaimCost(
    val resourceType: ClaimResourceType,
    val amount: Int,
    val usedFreeSlots: Int
)

enum class ClaimResourceType {
    FREE,
    IRON_INGOT,
    DIAMOND,
    NETHERITE_INGOT
}

data class ChunkCoordinate(
    val x: Int,
    val z: Int,
    val worldName: String
) {
    fun toChunkKey(): String = "${worldName}_${x}_${z}"
}

data class ConnectedChunks(
    val chunks: Set<ChunkCoordinate>,
    val ownerUuid: UUID
) {
    val size: Int get() = chunks.size
    
    fun contains(coord: ChunkCoordinate): Boolean = chunks.contains(coord)
    
    fun isConnectedTo(coord: ChunkCoordinate): Boolean {
        return chunks.any { chunk ->
            val dx = Math.abs(chunk.x - coord.x)
            val dz = Math.abs(chunk.z - coord.z)
            (dx == 1 && dz == 0) || (dx == 0 && dz == 1)
        }
    }
}
package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models

import java.util.*

data class VillageInfo(
    val villageId: Int,
    val villageName: String,
    val mayorUuid: UUID,
    val mayorName: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val isActive: Boolean = true
)

data class VillageMember(
    val villageId: Int,
    val memberUuid: UUID,
    val memberName: String,
    val role: VillageRole,
    val joinedAt: Long,
    val lastSeen: Long,
    val isActive: Boolean = true
)

data class VillagePermission(
    val villageId: Int,
    val memberUuid: UUID,
    val permissions: Set<VillagePermissionType>
)

enum class VillageRole {
    MAYOR,          // 이장
    DEPUTY_MAYOR,   // 부이장  
    MEMBER          // 구성원
}

enum class VillagePermissionType {
    // 구성원 관리
    INVITE_MEMBERS,     // 구성원 초대
    KICK_MEMBERS,       // 구성원 추방
    MANAGE_ROLES,       // 역할 관리
    
    // 토지 관리
    EXPAND_LAND,        // 토지 확장
    REDUCE_LAND,        // 토지 축소
    MANAGE_LAND,        // 토지 관리
    
    // 건설 권한
    BUILD,              // 건설 허용
    BREAK_BLOCKS,       // 블록 파괴 허용
    USE_CONTAINERS,     // 상자 등 컨테이너 사용
    USE_REDSTONE,       // 레드스톤 사용
    
    // 마을 관리
    MANAGE_PERMISSIONS, // 권한 관리
    DISSOLVE_VILLAGE,   // 마을 해체
    RENAME_VILLAGE      // 마을 이름 변경
}

data class VillageChunks(
    val villageId: Int,
    val chunks: Set<ChunkCoordinate>,
    val connectedAreas: List<ConnectedChunks>
) {
    val totalChunks: Int get() = chunks.size
    
    fun isConnected(): Boolean {
        return connectedAreas.size == 1
    }
    
    fun canAddChunk(newChunk: ChunkCoordinate): Boolean {
        return connectedAreas.any { area ->
            area.isConnectedTo(newChunk)
        }
    }
}
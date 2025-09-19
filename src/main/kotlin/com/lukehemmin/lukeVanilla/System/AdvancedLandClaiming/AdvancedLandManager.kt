package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.PlayTime.PlayTimeManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

data class ClaimResult(
    val success: Boolean,
    val message: String,
    val claimInfo: AdvancedClaimInfo? = null
)

class AdvancedLandManager(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager,
    private val playTimeManager: PlayTimeManager
) {
    // 메모리 캐시: 빠른 조회를 위해 (동시성 보호)
    private val claimedChunks = ConcurrentHashMap<String, ConcurrentHashMap<Pair<Int, Int>, AdvancedClaimInfo>>()
    private val playerClaims = ConcurrentHashMap<UUID, MutableList<AdvancedClaimInfo>>()
    
    private val landData: AdvancedLandData
    
    // LandManager 참조 (연결된 청크 그룹 시스템을 위해)
    private var landManager: com.lukehemmin.lukeVanilla.System.MyLand.LandManager? = null
    
    // 클레이밍 설정
    companion object {
        const val FREE_CLAIMS_COUNT = 4              // 무료 클레이밍 수
        const val NEWBIE_MAX_CLAIMS = 9              // 신규 플레이어 최대 클레이밍
        const val VETERAN_DAYS_THRESHOLD = 7         // 베테랑 플레이어 기준 (일)

        // 자원 비용 설정
        const val IRON_COST = 64                     // 철괴 64개 (스택 1개)
        const val DIAMOND_COST = 8                   // 다이아몬드 8개
        const val NETHERITE_COST = 2                 // 네더라이트 주괴 2개

        // 캐시 크기 제한 (OOM 방지)
        const val MAX_CLAIMED_CHUNKS_CACHE_SIZE = 10000  // 최대 청크 캐시 수
        const val MAX_PLAYER_CLAIMS_CACHE_SIZE = 1000    // 최대 플레이어 캐시 수
        const val CACHE_CLEANUP_THRESHOLD = 0.8          // 캐시 정리 임계점 (80%)
    }
    
    init {
        this.landData = AdvancedLandData(database)
        loadClaimsFromDatabase()
    }
    
    /**
     * 데이터베이스에서 모든 클레이밍 정보를 불러와 캐시에 저장합니다.
     */
    fun loadClaimsFromDatabase() {
        val loadedClaims = landData.loadAllClaims()
        claimedChunks.clear()
        playerClaims.clear()

        // ConcurrentHashMap에 안전하게 데이터 로드
        loadedClaims.forEach { (worldName, chunks) ->
            val worldChunks = ConcurrentHashMap<Pair<Int, Int>, AdvancedClaimInfo>()
            worldChunks.putAll(chunks)
            claimedChunks[worldName] = worldChunks
        }

        // 플레이어별 클레이밍 캐시 구성 (동시성 안전)
        loadedClaims.forEach { (worldName, chunks) ->
            chunks.forEach { (chunkCoords, claimInfo) ->
                playerClaims.computeIfAbsent(claimInfo.ownerUuid) {
                    Collections.synchronizedList(mutableListOf())
                }.add(claimInfo)
            }
        }

        // 초기 로딩 후 캐시 정리 (필요시)
        cleanupCaches()

        val totalClaims = claimedChunks.values.sumOf { it.size }
        plugin.logger.info("[AdvancedLandClaiming] ${totalClaims}개의 고급 토지 클레이밍을 데이터베이스에서 불러왔습니다.")
    }
    
    /**
     * 청크를 클레이밍합니다.
     */
    fun claimChunk(player: Player, chunk: Chunk, resourceType: ClaimResourceType? = null): ClaimResult {
        val chunkCoord = Pair(chunk.x, chunk.z)
        val worldName = chunk.world.name
        
        // 이미 클레이밍된 청크인지 확인
        if (isChunkClaimed(worldName, chunk.x, chunk.z)) {
            val owner = getClaimOwner(worldName, chunk.x, chunk.z)
            return ClaimResult(false, "이 청크는 이미 ${owner?.ownerName ?: "다른 플레이어"}가 소유하고 있습니다.")
        }
        
        // 플레이타임 기반 제한 확인
        val playerUuid = player.uniqueId
        val playTimeInfo = playTimeManager.getPlayTimeInfo(playerUuid)
        val totalPlayDays = playTimeInfo?.let { it.totalPlaytimeSeconds / (24 * 60 * 60) } ?: 0
        val isVeteran = totalPlayDays >= VETERAN_DAYS_THRESHOLD
        
        // 현재 플레이어의 클레이밍 수 확인
        val currentClaims = getPlayerClaimCount(playerUuid)
        
        if (!isVeteran && currentClaims >= NEWBIE_MAX_CLAIMS) {
            return ClaimResult(false, "신규 플레이어는 최대 ${NEWBIE_MAX_CLAIMS}개의 청크만 클레이밍할 수 있습니다. (현재: ${currentClaims}개)")
        }
        
        // 클레이밍 비용 계산
        val costResult = calculateClaimCost(playerUuid, resourceType)
        if (!costResult.success) {
            return ClaimResult(false, costResult.message)
        }
        
        val claimCost = costResult.claimInfo?.claimCost
        
        // 자원 확인 및 소모
        if (claimCost != null && claimCost.resourceType != ClaimResourceType.FREE) {
            if (!hasRequiredResources(player, claimCost)) {
                return ClaimResult(false, "필요한 자원이 부족합니다: ${getResourceName(claimCost.resourceType)} ${claimCost.amount}개")
            }
            
            if (!consumeResources(player, claimCost)) {
                return ClaimResult(false, "자원 소모 중 오류가 발생했습니다.")
            }
        }
        
        // 클레이밍 정보 생성
        val claimInfo = AdvancedClaimInfo(
            chunkX = chunk.x,
            chunkZ = chunk.z,
            worldName = worldName,
            ownerUuid = playerUuid,
            ownerName = player.name,
            claimType = ClaimType.PERSONAL,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            villageId = null,
            claimCost = claimCost
        )
        
        // 데이터베이스에 저장
        if (landData.saveClaim(claimInfo)) {
            // 캐시 업데이트
            claimedChunks.computeIfAbsent(worldName) { ConcurrentHashMap() }[chunkCoord] = claimInfo
            playerClaims.computeIfAbsent(playerUuid) { mutableListOf() }.add(claimInfo)

            // 캐시 크기 관리
            cleanupCaches()
            
            val costMessage = if (claimCost?.resourceType == ClaimResourceType.FREE) {
                "무료 슬롯 사용"
            } else {
                "${getResourceName(claimCost?.resourceType)} ${claimCost?.amount}개 소모"
            }
            
            debugManager.log("AdvancedLandClaiming", "[CLAIM] ${player.name}: 청크 (${chunk.x}, ${chunk.z}) - $costMessage")
            return ClaimResult(true, "청크를 성공적으로 클레이밍했습니다! ($costMessage)", claimInfo)
        } else {
            return ClaimResult(false, "데이터베이스 저장 중 오류가 발생했습니다.")
        }
    }
    
    /**
     * 클레이밍 비용을 계산합니다.
     */
    private fun calculateClaimCost(playerUuid: UUID, requestedResourceType: ClaimResourceType?): ClaimResult {
        val usedFreeSlots = landData.getPlayerUsedFreeSlots(playerUuid)
        
        // 무료 슬롯이 남아있는 경우
        if (usedFreeSlots < FREE_CLAIMS_COUNT) {
            val claimCost = ClaimCost(ClaimResourceType.FREE, 0, usedFreeSlots + 1)
            val claimInfo = AdvancedClaimInfo(0, 0, "", playerUuid, "", ClaimType.PERSONAL, 0, 0, null, claimCost)
            return ClaimResult(true, "무료 슬롯 사용 가능", claimInfo)
        }
        
        // 유료 클레이밍
        val resourceType = requestedResourceType ?: ClaimResourceType.IRON_INGOT
        val amount = when (resourceType) {
            ClaimResourceType.IRON_INGOT -> IRON_COST
            ClaimResourceType.DIAMOND -> DIAMOND_COST
            ClaimResourceType.NETHERITE_INGOT -> NETHERITE_COST
            ClaimResourceType.FREE -> return ClaimResult(false, "무료 슬롯이 모두 사용되었습니다.")
        }
        
        val claimCost = ClaimCost(resourceType, amount, usedFreeSlots)
        val claimInfo = AdvancedClaimInfo(0, 0, "", playerUuid, "", ClaimType.PERSONAL, 0, 0, null, claimCost)
        return ClaimResult(true, "유료 클레이밍", claimInfo)
    }
    
    /**
     * 플레이어가 필요한 자원을 가지고 있는지 확인합니다.
     */
    private fun hasRequiredResources(player: Player, claimCost: ClaimCost): Boolean {
        val material = when (claimCost.resourceType) {
            ClaimResourceType.IRON_INGOT -> Material.IRON_INGOT
            ClaimResourceType.DIAMOND -> Material.DIAMOND
            ClaimResourceType.NETHERITE_INGOT -> Material.NETHERITE_INGOT
            ClaimResourceType.FREE -> return true
        }
        
        val inventory = player.inventory
        var totalAmount = 0
        
        for (item in inventory.contents) {
            if (item != null && item.type == material) {
                totalAmount += item.amount
            }
        }
        
        return totalAmount >= claimCost.amount
    }
    
    /**
     * 플레이어의 인벤토리에서 자원을 소모합니다.
     */
    private fun consumeResources(player: Player, claimCost: ClaimCost): Boolean {
        val material = when (claimCost.resourceType) {
            ClaimResourceType.IRON_INGOT -> Material.IRON_INGOT
            ClaimResourceType.DIAMOND -> Material.DIAMOND
            ClaimResourceType.NETHERITE_INGOT -> Material.NETHERITE_INGOT
            ClaimResourceType.FREE -> return true
        }
        
        val inventory = player.inventory
        var remainingToRemove = claimCost.amount
        
        for (i in inventory.contents.indices) {
            val item = inventory.getItem(i)
            if (item != null && item.type == material && remainingToRemove > 0) {
                val toRemove = minOf(item.amount, remainingToRemove)
                if (item.amount <= toRemove) {
                    inventory.setItem(i, null)
                } else {
                    item.amount -= toRemove
                }
                remainingToRemove -= toRemove
                
                if (remainingToRemove <= 0) break
            }
        }
        
        return remainingToRemove <= 0
    }
    
    /**
     * 청크 클레이밍을 해제합니다.
     */
    fun unclaimChunk(player: Player, chunk: Chunk): ClaimResult {
        val worldName = chunk.world.name
        val chunkCoord = Pair(chunk.x, chunk.z)
        
        val claimInfo = getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            return ClaimResult(false, "이 청크는 클레이밍되지 않았습니다.")
        }
        
        if (claimInfo.ownerUuid != player.uniqueId) {
            return ClaimResult(false, "본인의 청크만 포기할 수 있습니다.")
        }
        
        // 환불 아이템 계산 (데이터베이스 제거 전에 미리 계산)
        val refundItems = calculateRefundItems(claimInfo.claimCost)
        
        // 데이터베이스에서 제거
        if (landData.removeClaim(worldName, chunk.x, chunk.z, player.uniqueId, player.name, "자발적 포기")) {
            // 캐시에서 제거
            claimedChunks[worldName]?.remove(chunkCoord)
            playerClaims[player.uniqueId]?.removeAll { it.chunkX == chunk.x && it.chunkZ == chunk.z && it.worldName == worldName }
            
            // 환불 지급 (50% 환불)
            giveRefundItemsSafely(player, refundItems)
            
            debugManager.log("AdvancedLandClaiming", "[UNCLAIM] ${player.name}: 청크 (${chunk.x}, ${chunk.z}) 포기")
            return ClaimResult(true, "청크 클레이밍을 성공적으로 포기했습니다.")
        } else {
            return ClaimResult(false, "데이터베이스 처리 중 오류가 발생했습니다.")
        }
    }
    
    /**
     * 특정 청크가 클레이밍되었는지 확인합니다.
     */
    fun isChunkClaimed(worldName: String, chunkX: Int, chunkZ: Int): Boolean {
        return claimedChunks[worldName]?.containsKey(chunkX to chunkZ) ?: false
    }
    
    /**
     * 특정 청크의 소유자 정보를 반환합니다.
     */
    fun getClaimOwner(worldName: String, chunkX: Int, chunkZ: Int): AdvancedClaimInfo? {
        return claimedChunks[worldName]?.get(chunkX to chunkZ)
    }
    
    /**
     * 플레이어의 클레이밍 수를 반환합니다.
     */
    fun getPlayerClaimCount(playerUuid: UUID): Int {
        return playerClaims[playerUuid]?.size ?: 0
    }
    
    /**
     * 플레이어가 베테랑인지 확인합니다.
     */
    fun isVeteranPlayer(playerUuid: UUID): Boolean {
        val playTimeInfo = playTimeManager.getPlayTimeInfo(playerUuid)
        val totalPlayDays = playTimeInfo?.let { it.totalPlaytimeSeconds / (24 * 60 * 60) } ?: 0
        return totalPlayDays >= VETERAN_DAYS_THRESHOLD
    }
    
    /**
     * 자원 타입의 표시 이름을 반환합니다.
     */
    private fun getResourceName(resourceType: ClaimResourceType?): String {
        return when (resourceType) {
            ClaimResourceType.IRON_INGOT -> "철괴"
            ClaimResourceType.DIAMOND -> "다이아몬드"
            ClaimResourceType.NETHERITE_INGOT -> "네더라이트 주괴"
            ClaimResourceType.FREE -> "무료"
            null -> "알 수 없음"
        }
    }
    
    /**
     * 플레이어의 클레이밍 정보를 요약해서 반환합니다.
     */
    fun getPlayerClaimSummary(playerUuid: UUID): String {
        val claims = playerClaims[playerUuid] ?: emptyList()
        val claimCount = claims.size
        val usedFreeSlots = landData.getPlayerUsedFreeSlots(playerUuid)
        val isVeteran = isVeteranPlayer(playerUuid)
        
        val maxClaims = if (isVeteran) "무제한" else NEWBIE_MAX_CLAIMS.toString()
        val freeSlots = "$usedFreeSlots/$FREE_CLAIMS_COUNT"
        
        return "클레이밍: ${claimCount}개/${maxClaims} | 무료 슬롯: $freeSlots | 등급: ${if (isVeteran) "베테랑" else "신규"}"
    }
    
    // ===== 연결된 청크 그룹 시스템 =====
    
    /**
     * 특정 청크가 속한 연결된 청크 그룹의 대표 청크를 반환합니다.
     * 대표 청크는 그룹 내에서 가장 작은 (x, z) 좌표를 가진 청크입니다.
     * 
     * @param playerUuid 소유자 UUID
     * @param targetChunk 조회할 청크
     * @return 대표 청크, 또는 연결된 그룹이 없으면 null
     */
    fun getRepresentativeChunk(playerUuid: UUID, targetChunk: Chunk): Chunk? {
        val worldName = targetChunk.world.name
        val targetCoord = ChunkCoordinate(targetChunk.x, targetChunk.z, worldName)
        
        // 플레이어의 연결된 청크 그룹들 조회
        val connectedGroups = landData.getPlayerConnectedChunks(playerUuid)
        
        // targetChunk가 속한 그룹 찾기
        val containingGroup = connectedGroups.find { group ->
            group.contains(targetCoord)
        } ?: return null
        
        // 그룹 내에서 가장 작은 (x,z) 좌표 찾기
        val representativeCoord = containingGroup.chunks.minWith(
            compareBy<ChunkCoordinate> { it.x }.thenBy { it.z }
        )
        
        // Chunk 객체로 변환하여 반환
        val world = plugin.server.getWorld(representativeCoord.worldName)
        return world?.getChunkAt(representativeCoord.x, representativeCoord.z)
    }
    
    /**
     * 두 청크가 같은 연결된 그룹에 속하는지 확인합니다.
     * 
     * @param playerUuid 소유자 UUID
     * @param chunk1 첫 번째 청크
     * @param chunk2 두 번째 청크  
     * @return 같은 그룹에 속하면 true
     */
    fun isChunkInSameGroup(playerUuid: UUID, chunk1: Chunk, chunk2: Chunk): Boolean {
        if (chunk1.world.name != chunk2.world.name) return false
        
        val coord1 = ChunkCoordinate(chunk1.x, chunk1.z, chunk1.world.name)
        val coord2 = ChunkCoordinate(chunk2.x, chunk2.z, chunk2.world.name)
        
        val connectedGroups = landData.getPlayerConnectedChunks(playerUuid)
        
        return connectedGroups.any { group ->
            group.contains(coord1) && group.contains(coord2)
        }
    }
    
    /**
     * 특정 청크가 속한 연결된 그룹의 모든 청크를 반환합니다.
     * 
     * @param playerUuid 소유자 UUID
     * @param targetChunk 조회할 청크
     * @return 그룹에 속한 모든 청크 좌표 리스트
     */
    fun getGroupMemberChunks(playerUuid: UUID, targetChunk: Chunk): Set<ChunkCoordinate> {
        val worldName = targetChunk.world.name
        val targetCoord = ChunkCoordinate(targetChunk.x, targetChunk.z, worldName)
        
        val connectedGroups = landData.getPlayerConnectedChunks(playerUuid)
        
        return connectedGroups.find { group ->
            group.contains(targetCoord)
        }?.chunks ?: emptySet()
    }
    
    /**
     * 플레이어가 특정 청크를 소유하고 있는지 확인합니다.
     * 
     * @param playerUuid 플레이어 UUID
     * @param chunk 확인할 청크
     * @return 소유하고 있으면 true
     */
    fun isPlayerOwner(playerUuid: UUID, chunk: Chunk): Boolean {
        val claimInfo = getClaimOwner(chunk.world.name, chunk.x, chunk.z)
        return claimInfo?.ownerUuid == playerUuid
    }
    
    /**
     * LandManager 참조를 설정합니다.
     * 
     * @param landManager MyLand 시스템의 LandManager
     */
    fun setLandManager(landManager: com.lukehemmin.lukeVanilla.System.MyLand.LandManager) {
        this.landManager = landManager
    }
    
    /**
     * LandManager 참조를 반환합니다.
     * 
     * @return LandManager 인스턴스 또는 null
     */
    fun getLandManager(): com.lukehemmin.lukeVanilla.System.MyLand.LandManager? {
        return landManager
    }
    
    // ===== 마을 관련 메서드들 =====
    
    /**
     * 마을을 생성하고 개인 토지를 마을 토지로 전환합니다.
     */
    fun createVillage(player: Player, villageName: String, connectedChunks: Set<org.bukkit.Chunk>): ClaimResult {
        try {
            // 1. 마을 이름 중복 확인
            if (landData.isVillageNameExists(villageName)) {
                return ClaimResult(false, "이미 존재하는 마을 이름입니다.")
            }
            
            // 2. 마을 정보를 데이터베이스에 저장
            val villageId = landData.createVillage(villageName, player.uniqueId, player.name)
            if (villageId == null) {
                return ClaimResult(false, "마을 생성 중 데이터베이스 오류가 발생했습니다.")
            }
            
            // 3. 마을 이장을 멤버로 추가
            if (!landData.addVillageMember(villageId as Int, player.uniqueId, player.name, VillageRole.MAYOR)) {
                return ClaimResult(false, "마을 이장 등록 중 오류가 발생했습니다.")
            }
            
            // 4. 연결된 모든 청크를 마을 토지로 전환
            var convertedCount = 0
            for (chunk in connectedChunks) {
                val currentClaimInfo = getClaimOwner(chunk.world.name, chunk.x, chunk.z)
                if (currentClaimInfo != null) {
                    val updatedClaimInfo = currentClaimInfo.copy(
                        claimType = ClaimType.VILLAGE,
                        villageId = villageId as Int,
                        lastUpdated = System.currentTimeMillis()
                    )
                    
                    if (landData.updateClaimToVillage(updatedClaimInfo)) {
                        // 캐시 업데이트
                        val chunkCoord = Pair(chunk.x, chunk.z)
                        claimedChunks.computeIfAbsent(chunk.world.name) { ConcurrentHashMap() }
                            .put(chunkCoord, updatedClaimInfo)
                        
                        // 플레이어 캐시에서 기존 정보 제거하고 새 정보 추가
                        playerClaims[player.uniqueId]?.removeIf { 
                            it.chunkX == chunk.x && it.chunkZ == chunk.z && it.worldName == chunk.world.name 
                        }
                        playerClaims.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(updatedClaimInfo)
                        
                        convertedCount++
                    }
                }
            }
            
            if (convertedCount == connectedChunks.size) {
                debugManager.log("AdvancedLandClaiming", "[VILLAGE_CREATE] ${player.name}: 마을 '$villageName' 생성, ${convertedCount}개 청크 전환")
                return ClaimResult(true, "마을이 성공적으로 생성되었습니다!")
            } else {
                return ClaimResult(false, "일부 청크 전환에 실패했습니다. ($convertedCount/${connectedChunks.size})")
            }
            
        } catch (e: Exception) {
            debugManager.log("AdvancedLandClaiming", "[VILLAGE_CREATE_ERROR] ${player.name}: ${e.message}")
            e.printStackTrace()
            return ClaimResult(false, "마을 생성 중 예상치 못한 오류가 발생했습니다.")
        }
    }
    
    /**
     * 마을 정보를 조회합니다.
     */
    fun getVillageInfo(villageId: Int): VillageInfo? {
        return landData.getVillageInfo(villageId)
    }
    
    /**
     * 청크가 속한 마을의 ID를 반환합니다.
     */
    fun getChunkVillageId(worldName: String, chunkX: Int, chunkZ: Int): Int? {
        val claimInfo = getClaimOwner(worldName, chunkX, chunkZ)
        return if (claimInfo?.claimType == ClaimType.VILLAGE) claimInfo.villageId else null
    }
    
    /**
     * 마을의 모든 멤버를 조회합니다.
     */
    fun getVillageMembers(villageId: Int): List<VillageMember> {
        return landData.getVillageMembers(villageId)
    }
    
    /**
     * 마을에 새로운 멤버를 추가합니다.
     */
    fun addVillageMember(villageId: Int, memberUuid: UUID, memberName: String, role: VillageRole): Boolean {
        return landData.addVillageMember(villageId, memberUuid, memberName, role)
    }
    
    /**
     * 마을이 소유한 총 청크 개수를 반환합니다.
     */
    fun getVillageChunkCount(villageId: Int): Int {
        var count = 0
        claimedChunks.forEach { (_, chunks) ->
            chunks.forEach { (_, claimInfo) ->
                if (claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId == villageId) {
                    count++
                }
            }
        }
        return count
    }
    
    /**
     * 마을 토지들을 반환합니다.
     */
    fun returnVillageChunks(
        actor: Player,
        villageId: Int,
        chunks: Set<org.bukkit.Chunk>,
        reason: String
    ): ClaimResult {
        try {
            var returnedCount = 0
            
            for (chunk in chunks) {
                val claimInfo = getClaimOwner(chunk.world.name, chunk.x, chunk.z)
                if (claimInfo != null && 
                    claimInfo.claimType == ClaimType.VILLAGE && 
                    claimInfo.villageId == villageId) {
                    
                    // 데이터베이스에서 클레이밍 삭제
                    if (landData.removeClaim(
                        chunk.world.name, 
                        chunk.x, 
                        chunk.z, 
                        actor.uniqueId, 
                        actor.name, 
                        reason
                    )) {
                        // 캐시에서 제거
                        val chunkCoord = Pair(chunk.x, chunk.z)
                        claimedChunks[chunk.world.name]?.remove(chunkCoord)
                        
                        // 플레이어 캐시에서 제거
                        playerClaims[claimInfo.ownerUuid]?.removeIf { 
                            it.chunkX == chunk.x && it.chunkZ == chunk.z && it.worldName == chunk.world.name 
                        }
                        
                        // 멤버 정보도 삭제 (MyLand 시스템의 멤버 테이블)
                        landManager?.let { manager ->
                            // LandData를 통해 멤버 정보 삭제
                            val landDataField = manager::class.java.getDeclaredField("landData")
                            landDataField.isAccessible = true
                            val landData = landDataField.get(manager) as com.lukehemmin.lukeVanilla.System.MyLand.LandData
                            landData.deleteAllMembers(chunk.world.name, chunk.x, chunk.z)
                        }
                        
                        returnedCount++
                    }
                }
            }
            
            if (returnedCount == chunks.size) {
                debugManager.log("AdvancedLandClaiming", "[VILLAGE_RETURN] ${actor.name}: 마을 ID $villageId, ${returnedCount}개 청크 반환")
                
                // 마을에 더 이상 토지가 없으면 마을을 비활성화
                val remainingChunks = getVillageChunkCount(villageId)
                if (remainingChunks == 0) {
                    landData.deactivateVillage(villageId)
                    debugManager.log("AdvancedLandClaiming", "[VILLAGE_DEACTIVATE] 마을 ID $villageId 비활성화 (토지 없음)")
                }
                
                return ClaimResult(true, "마을 토지가 성공적으로 반환되었습니다!")
            } else {
                return ClaimResult(false, "일부 토지 반환에 실패했습니다. ($returnedCount/${chunks.size})")
            }
            
        } catch (e: Exception) {
            debugManager.log("AdvancedLandClaiming", "[VILLAGE_RETURN_ERROR] ${actor.name}: ${e.message}")
            e.printStackTrace()
            return ClaimResult(false, "마을 토지 반환 중 예상치 못한 오류가 발생했습니다.")
        }
    }
    
    /**
     * 마을 구성원의 역할을 변경합니다.
     */
    fun changeVillageMemberRole(villageId: Int, memberUuid: UUID, newRole: VillageRole): Boolean {
        return landData.updateVillageMemberRole(villageId, memberUuid, newRole)
    }
    
    /**
     * 마을 구성원을 추방합니다.
     * @param villageId 마을 ID
     * @param memberUuid 추방할 구성원의 UUID
     * @return 추방 성공 여부
     */
    fun kickVillageMember(villageId: Int, memberUuid: UUID): Boolean {
        return landData.removeVillageMember(villageId, memberUuid)
    }
    
    /**
     * 플레이어가 소유한 연결된 청크 그룹들을 조회합니다.
     * @param playerUuid 플레이어 UUID
     * @return 연결된 청크 그룹 목록
     */
    fun getPlayerConnectedChunks(playerUuid: UUID): List<ConnectedChunks> {
        return landData.getPlayerConnectedChunks(playerUuid)
    }
    
    // ===== 마을 클레이밍 관련 메서드들 =====
    
    /**
     * 플레이어가 특정 마을의 구성원인지 확인합니다.
     * @param playerUuid 플레이어 UUID
     * @return 플레이어가 속한 마을 정보, 없으면 null
     */
    fun getPlayerVillageMembership(playerUuid: UUID): VillageMember? {
        // 모든 활성 마을의 구성원 목록에서 해당 플레이어 찾기
        return landData.getPlayerVillageMembership(playerUuid)
    }
    
    /**
     * 플레이어가 마을 토지 확장 권한이 있는지 확인합니다.
     * @param playerUuid 플레이어 UUID
     * @param villageId 마을 ID
     * @return 권한이 있으면 true
     */
    fun hasVillageExpandPermission(playerUuid: UUID, villageId: Int): Boolean {
        val membership = getPlayerVillageMembership(playerUuid)
        if (membership == null || membership.villageId != villageId || !membership.isActive) {
            return false
        }
        
        // 이장과 부이장은 기본적으로 토지 확장 권한을 가짐
        return when (membership.role) {
            VillageRole.MAYOR, VillageRole.DEPUTY_MAYOR -> true
            VillageRole.MEMBER -> {
                // 일반 구성원은 별도 권한 확인
                landData.hasVillagePermission(playerUuid, villageId, VillagePermissionType.EXPAND_LAND)
            }
        }
    }
    
    /**
     * 청크가 특정 마을의 기존 토지와 연결되어 있는지 확인합니다.
     * @param chunk 확인할 청크
     * @param villageId 마을 ID
     * @return 연결되어 있으면 true
     */
    fun isChunkConnectedToVillage(chunk: org.bukkit.Chunk, villageId: Int): Boolean {
        val worldName = chunk.world.name
        val targetCoord = ChunkCoordinate(chunk.x, chunk.z, worldName)
        
        // 주변 4개 청크만 확인 (상하좌우, 대각선 제외)
        val directions = listOf(
            Pair(0, 1),   // 북쪽
            Pair(0, -1),  // 남쪽
            Pair(1, 0),   // 동쪽
            Pair(-1, 0)   // 서쪽
        )
        
        for ((dx, dz) in directions) {
            val nearbyX = chunk.x + dx
            val nearbyZ = chunk.z + dz
            
            val nearbyClaimInfo = getClaimOwner(worldName, nearbyX, nearbyZ)
            if (nearbyClaimInfo != null && 
                nearbyClaimInfo.claimType == ClaimType.VILLAGE && 
                nearbyClaimInfo.villageId == villageId) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 마을 토지로 청크를 클레이밍합니다.
     * @param player 클레이밍을 수행하는 플레이어
     * @param chunk 클레이밍할 청크
     * @param resourceType 사용할 자원 타입 (null이면 자동 계산)
     * @return 클레이밍 결과
     */
    fun claimChunkForVillage(player: Player, chunk: org.bukkit.Chunk, resourceType: ClaimResourceType? = null): ClaimResult {
        val playerUuid = player.uniqueId
        val worldName = chunk.world.name
        val chunkCoord = Pair(chunk.x, chunk.z)
        
        // 1. 이미 클레이밍된 청크인지 확인
        if (isChunkClaimed(worldName, chunk.x, chunk.z)) {
            val owner = getClaimOwner(worldName, chunk.x, chunk.z)
            return ClaimResult(false, "이 청크는 이미 ${owner?.ownerName ?: "다른 플레이어"}가 소유하고 있습니다.")
        }
        
        // 2. 플레이어가 마을 구성원인지 확인
        val membership = getPlayerVillageMembership(playerUuid)
        if (membership == null || !membership.isActive) {
            return ClaimResult(false, "마을 구성원만 마을 토지를 클레이밍할 수 있습니다.")
        }
        
        val villageId = membership.villageId
        
        // 3. 마을 토지 확장 권한 확인
        if (!hasVillageExpandPermission(playerUuid, villageId)) {
            return ClaimResult(false, "마을 토지 확장 권한이 없습니다. 마을 이장이나 권한을 가진 구성원에게 문의하세요.")
        }
        
        // 4. 청크가 기존 마을 토지와 연결되어 있는지 확인
        if (!isChunkConnectedToVillage(chunk, villageId)) {
            return ClaimResult(false, "마을 토지는 기존 마을 영역과 연결되어야 합니다.")
        }
        
        // 5. 클레이밍 비용 계산 (개인 클레이밍과 동일한 비용 체계)
        val costResult = calculateClaimCost(playerUuid, resourceType)
        if (!costResult.success) {
            return ClaimResult(false, costResult.message)
        }
        
        val claimCost = costResult.claimInfo?.claimCost
        
        // 6. 자원 확인 및 소모
        if (claimCost != null && claimCost.resourceType != ClaimResourceType.FREE) {
            if (!hasRequiredResources(player, claimCost)) {
                return ClaimResult(false, "필요한 자원이 부족합니다: ${getResourceName(claimCost.resourceType)} ${claimCost.amount}개")
            }
            
            if (!consumeResources(player, claimCost)) {
                return ClaimResult(false, "자원 소모 중 오류가 발생했습니다.")
            }
        }
        
        // 7. 마을 정보 조회
        val villageInfo = getVillageInfo(villageId)
        if (villageInfo == null || !villageInfo.isActive) {
            return ClaimResult(false, "마을 정보를 찾을 수 없거나 비활성화된 마을입니다.")
        }
        
        // 8. 마을 토지로 클레이밍 정보 생성
        val claimInfo = AdvancedClaimInfo(
            chunkX = chunk.x,
            chunkZ = chunk.z,
            worldName = worldName,
            ownerUuid = villageInfo.mayorUuid, // 마을 토지의 소유자는 이장으로 설정
            ownerName = "${villageInfo.villageName} (마을)",
            claimType = ClaimType.VILLAGE,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            villageId = villageId,
            claimCost = claimCost
        )
        
        // 9. 데이터베이스에 저장
        if (landData.saveClaim(claimInfo)) {
            // 캐시 업데이트
            claimedChunks.computeIfAbsent(worldName) { ConcurrentHashMap() }[chunkCoord] = claimInfo
            playerClaims.computeIfAbsent(villageInfo.mayorUuid) { mutableListOf() }.add(claimInfo)
            
            val costMessage = if (claimCost?.resourceType == ClaimResourceType.FREE) {
                "무료 슬롯 사용"
            } else {
                "${getResourceName(claimCost?.resourceType)} ${claimCost?.amount}개 소모"
            }
            
            debugManager.log("AdvancedLandClaiming", "[VILLAGE_CLAIM] ${player.name}: 마을 '${villageInfo.villageName}' 청크 (${chunk.x}, ${chunk.z}) - $costMessage")
            return ClaimResult(true, "마을 '${villageInfo.villageName}'의 토지가 성공적으로 확장되었습니다! ($costMessage)", claimInfo)
        } else {
            return ClaimResult(false, "데이터베이스 저장 중 오류가 발생했습니다.")
        }
    }
    
    // ===== 환불 시스템 (새로운 기능 - 기존 코드에 영향 없음) =====
    
    /**
     * 50% 환불 계산 (새로운 기능)
     * 기존 시스템에 영향을 주지 않는 독립적인 메서드
     */
    fun calculateRefundItems(claimCost: ClaimCost?): List<ItemStack> {
        if (claimCost == null || claimCost.resourceType == ClaimResourceType.FREE) {
            return emptyList()
        }
        
        val refundAmount = (claimCost.amount * 0.5).toInt()
        if (refundAmount <= 0) return emptyList()
        
        val material = when (claimCost.resourceType) {
            ClaimResourceType.IRON_INGOT -> Material.IRON_INGOT
            ClaimResourceType.DIAMOND -> Material.DIAMOND
            ClaimResourceType.NETHERITE_INGOT -> Material.NETHERITE_INGOT
            ClaimResourceType.FREE -> return emptyList()
        }
        
        return listOf(ItemStack(material, refundAmount))
    }
    
    /**
     * 환불 아이템 안전 지급 (새로운 기능)
     * 인벤토리 부족 시 드롭 + 알림
     */
    fun giveRefundItemsSafely(player: Player, refundItems: List<ItemStack>) {
        if (refundItems.isEmpty()) return
        
        val failedItems = player.inventory.addItem(*refundItems.toTypedArray())
        
        if (failedItems.isNotEmpty()) {
            // 플레이어 바로 아래에 드롭
            failedItems.values.forEach { item ->
                player.world.dropItemNaturally(player.location, item)
            }
            
            // 드롭 알림
            player.sendMessage(Component.text(
                "인벤토리 공간이 부족하여 환불 아이템이 드롭되었습니다.", 
                NamedTextColor.YELLOW
            ))
        }
        
        // 환불 완료 메시지
        val totalAmount = refundItems.sumOf { it.amount }
        val itemName = when (refundItems.firstOrNull()?.type) {
            Material.IRON_INGOT -> "철괴"
            Material.DIAMOND -> "다이아몬드"
            Material.NETHERITE_INGOT -> "네더라이트 주괴"
            else -> "아이템"
        }
        
        player.sendMessage(Component.text(
            "${itemName} ${totalAmount}개가 50% 환불되었습니다.", 
            NamedTextColor.GREEN
        ))
    }
    
    /**
     * LandData 접근 메서드 (권한 시스템용 - 새로운 기능)
     */
    fun getLandData(): AdvancedLandData {
        return landData
    }
    
    // ===== 마을 해체 및 이장 양도 시스템 =====
    
    /**
     * 마을을 해체하고 모든 마을 토지를 개인 토지로 변환합니다.
     * @param mayorPlayer 마을장 플레이어
     * @param villageId 해체할 마을 ID
     * @return 해체 결과
     */
    fun disbandVillage(mayorPlayer: Player, villageId: Int): ClaimResult {
        try {
            // 1. 마을 정보 조회
            val villageInfo = getVillageInfo(villageId)
            if (villageInfo == null) {
                return ClaimResult(false, "마을 정보를 찾을 수 없습니다.")
            }
            
            // 2. 마을장 권한 확인
            if (villageInfo.mayorUuid != mayorPlayer.uniqueId) {
                return ClaimResult(false, "마을장만 마을을 해체할 수 있습니다.")
            }
            
            // 3. 마을의 모든 청크 조회
            val villageChunks = claimedChunks.values.flatMap { worldChunks ->
                worldChunks.filter { (_, claimInfo) -> 
                    claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId == villageId 
                }.map { (chunkCoord, claimInfo) ->
                    Triple(claimInfo.worldName, chunkCoord.first, chunkCoord.second)
                }
            }
            
            debugManager.log("AdvancedLandClaiming", "[DISBAND] 마을 '${villageInfo.villageName}' 해체 시작 - ${villageChunks.size}개 청크")
            
            // 4. 각 청크를 개인 토지로 변환
            villageChunks.forEach { (worldName, chunkX, chunkZ) ->
                val claimInfo = claimedChunks[worldName]?.get(chunkX to chunkZ)
                if (claimInfo != null) {
                    // 청크를 개인 토지로 변환
                    val personalClaimInfo = claimInfo.copy(
                        claimType = ClaimType.PERSONAL,
                        villageId = null,
                        ownerUuid = villageInfo.mayorUuid,
                        ownerName = villageInfo.mayorName
                    )
                    
                    // 캐시 업데이트
                    claimedChunks.computeIfAbsent(worldName) { ConcurrentHashMap() }
                        .put(chunkX to chunkZ, personalClaimInfo)
                    
                    // 데이터베이스 업데이트
                    landData.updateClaimToPersonal(worldName, chunkX, chunkZ, villageInfo.mayorUuid, villageInfo.mayorName)
                }
            }
            
            // 5. 모든 마을 멤버 제거
            val members = getVillageMembers(villageId)
            members.forEach { member ->
                landData.removeVillageMember(villageId, member.memberUuid)
            }
            
            // 6. 마을 비활성화
            landData.deactivateVillage(villageId)
            
            // 7. 캐시에서 플레이어 클레임 리스트 업데이트
            playerClaims[villageInfo.mayorUuid]?.clear()
            claimedChunks.values.forEach { worldChunks ->
                worldChunks.values.filter { it.ownerUuid == villageInfo.mayorUuid }.forEach { claimInfo ->
                    playerClaims.computeIfAbsent(villageInfo.mayorUuid) { mutableListOf() }.add(claimInfo)
                }
            }
            
            debugManager.log("AdvancedLandClaiming", "[DISBAND] 마을 '${villageInfo.villageName}' 해체 완료")
            
            // 8. 온라인 멤버들에게 알림
            members.forEach { member ->
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                if (onlinePlayer != null && member.memberUuid != mayorPlayer.uniqueId) {
                    onlinePlayer.sendMessage(
                        Component.text()
                            .append(Component.text("📢 ", NamedTextColor.RED))
                            .append(Component.text("마을 '", NamedTextColor.WHITE))
                            .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                            .append(Component.text("'이 해체되었습니다.", NamedTextColor.WHITE))
                    )
                }
            }
            
            return ClaimResult(true, "마을 '${villageInfo.villageName}'이 성공적으로 해체되었습니다. 모든 마을 토지가 개인 토지로 변환되었습니다.")
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ClaimResult(false, "마을 해체 중 오류가 발생했습니다: ${e.message}")
        }
    }
    
    /**
     * 마을장을 다른 플레이어에게 양도합니다.
     * @param currentMayor 현재 마을장
     * @param villageId 마을 ID
     * @param newMayorUuid 새로운 마을장의 UUID
     * @param newMayorName 새로운 마을장의 이름
     * @return 양도 결과
     */
    fun transferVillageMayorship(currentMayor: Player, villageId: Int, newMayorUuid: UUID, newMayorName: String): ClaimResult {
        try {
            // 1. 마을 정보 조회
            val villageInfo = getVillageInfo(villageId)
            if (villageInfo == null) {
                return ClaimResult(false, "마을 정보를 찾을 수 없습니다.")
            }
            
            // 2. 현재 마을장 권한 확인
            if (villageInfo.mayorUuid != currentMayor.uniqueId) {
                return ClaimResult(false, "마을장만 이장을 양도할 수 있습니다.")
            }
            
            // 3. 새로운 마을장이 마을 멤버인지 확인
            val members = getVillageMembers(villageId)
            val newMayorMember = members.find { it.memberUuid == newMayorUuid }
            if (newMayorMember == null) {
                return ClaimResult(false, "마을 구성원만 이장으로 양도할 수 있습니다.")
            }
            
            // 4. 자기 자신에게 양도하는 경우 확인
            if (newMayorUuid == currentMayor.uniqueId) {
                return ClaimResult(false, "자기 자신에게는 이장을 양도할 수 없습니다.")
            }
            
            debugManager.log("AdvancedLandClaiming", "[MAYOR_TRANSFER] 마을 '${villageInfo.villageName}' 이장 양도: ${currentMayor.name} → ${newMayorName}")
            
            // 5. 데이터베이스에서 마을장 정보 업데이트
            if (!landData.updateVillageMayor(villageId, newMayorUuid, newMayorName)) {
                return ClaimResult(false, "데이터베이스 업데이트 중 오류가 발생했습니다.")
            }
            
            // 6. 새로운 마을장의 역할을 MAYOR로 변경
            if (!changeVillageMemberRole(villageId, newMayorUuid, VillageRole.MAYOR)) {
                return ClaimResult(false, "새로운 마을장의 역할 변경 중 오류가 발생했습니다.")
            }
            
            // 7. 기존 마을장을 일반 멤버로 변경
            if (!changeVillageMemberRole(villageId, currentMayor.uniqueId, VillageRole.MEMBER)) {
                return ClaimResult(false, "기존 마을장의 역할 변경 중 오류가 발생했습니다.")
            }
            
            // 8. 모든 마을 토지의 소유권을 새로운 마을장으로 변경
            val villageChunks = claimedChunks.values.flatMap { worldChunks ->
                worldChunks.filter { (_, claimInfo) -> 
                    claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId == villageId 
                }.map { (chunkCoord, claimInfo) ->
                    Triple(claimInfo.worldName, chunkCoord.first, chunkCoord.second)
                }
            }
            
            villageChunks.forEach { (worldName, chunkX, chunkZ) ->
                val claimInfo = claimedChunks[worldName]?.get(chunkX to chunkZ)
                if (claimInfo != null) {
                    val updatedClaimInfo = claimInfo.copy(
                        ownerUuid = newMayorUuid,
                        ownerName = "${villageInfo.villageName} (마을)"
                    )
                    claimedChunks.computeIfAbsent(worldName) { ConcurrentHashMap() }
                        .put(chunkX to chunkZ, updatedClaimInfo)
                    landData.updateClaimOwner(worldName, chunkX, chunkZ, newMayorUuid, "${villageInfo.villageName} (마을)")
                }
            }
            
            // 9. 플레이어 클레임 캐시 업데이트
            // 기존 마을장에서 마을 청크들 제거
            playerClaims[currentMayor.uniqueId]?.removeAll { claim ->
                claim.claimType == ClaimType.VILLAGE && claim.villageId == villageId
            }
            
            // 새로운 마을장에게 마을 청크들 추가
            villageChunks.forEach { (worldName, chunkX, chunkZ) ->
                val claimInfo = claimedChunks[worldName]?.get(chunkX to chunkZ)
                if (claimInfo != null) {
                    playerClaims.computeIfAbsent(newMayorUuid) { mutableListOf() }.add(claimInfo)
                }
            }
            
            debugManager.log("AdvancedLandClaiming", "[MAYOR_TRANSFER] 마을 '${villageInfo.villageName}' 이장 양도 완료")
            
            // 10. 마을 멤버들에게 알림
            members.forEach { member ->
                val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                if (onlinePlayer != null) {
                    when (member.memberUuid) {
                        currentMayor.uniqueId -> {
                            onlinePlayer.sendMessage(
                                Component.text()
                                    .append(Component.text("👑 ", NamedTextColor.GOLD))
                                    .append(Component.text("마을 '", NamedTextColor.WHITE))
                                    .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                                    .append(Component.text("'의 이장을 ", NamedTextColor.WHITE))
                                    .append(Component.text(newMayorName, NamedTextColor.AQUA))
                                    .append(Component.text("님께 양도했습니다.", NamedTextColor.WHITE))
                            )
                        }
                        newMayorUuid -> {
                            onlinePlayer.sendMessage(
                                Component.text()
                                    .append(Component.text("🎉 ", NamedTextColor.GOLD))
                                    .append(Component.text("마을 '", NamedTextColor.WHITE))
                                    .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                                    .append(Component.text("'의 새로운 이장이 되었습니다!", NamedTextColor.WHITE))
                            )
                        }
                        else -> {
                            onlinePlayer.sendMessage(
                                Component.text()
                                    .append(Component.text("📢 ", NamedTextColor.BLUE))
                                    .append(Component.text("마을 '", NamedTextColor.WHITE))
                                    .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                                    .append(Component.text("'의 이장이 ", NamedTextColor.WHITE))
                                    .append(Component.text(newMayorName, NamedTextColor.AQUA))
                                    .append(Component.text("님으로 변경되었습니다.", NamedTextColor.WHITE))
                            )
                        }
                    }
                }
            }
            
            return ClaimResult(true, "마을 '${villageInfo.villageName}'의 이장을 ${newMayorName}님께 성공적으로 양도했습니다.")
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ClaimResult(false, "이장 양도 중 오류가 발생했습니다: ${e.message}")
        }
    }

    // ===== 마을 권한 관리 시스템 =====

    /**
     * 마을 구성원에게 권한을 부여합니다.
     */
    fun grantMemberPermission(
        grantedBy: Player,
        villageId: Int,
        memberUuid: UUID,
        permissionType: VillagePermissionType
    ): ClaimResult {
        try {
            // 1. 권한 부여자가 권한 관리 권한이 있는지 확인
            val grantedByMembership = getPlayerVillageMembership(grantedBy.uniqueId)
            if (grantedByMembership?.villageId != villageId) {
                return ClaimResult(false, "이 마을의 구성원이 아닙니다.")
            }

            val canManagePermissions = when (grantedByMembership.role) {
                VillageRole.MAYOR -> true
                VillageRole.DEPUTY_MAYOR -> hasVillagePermission(grantedBy.uniqueId, villageId, VillagePermissionType.MANAGE_PERMISSIONS)
                VillageRole.MEMBER -> false
            }

            if (!canManagePermissions) {
                return ClaimResult(false, "권한을 관리할 수 있는 권한이 없습니다.")
            }

            // 2. 대상 멤버가 마을 구성원인지 확인
            val targetMembership = getPlayerVillageMembership(memberUuid)
            if (targetMembership?.villageId != villageId) {
                return ClaimResult(false, "대상자가 이 마을의 구성원이 아닙니다.")
            }

            // 3. 권한 부여
            val success = landData.grantVillagePermission(villageId, memberUuid, permissionType, grantedBy.uniqueId, grantedBy.name)

            if (success) {
                val targetPlayer = org.bukkit.Bukkit.getPlayer(memberUuid)
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(
                        Component.text()
                            .append(Component.text("✅ ", NamedTextColor.GREEN))
                            .append(Component.text("마을에서 새로운 권한이 부여되었습니다: ", NamedTextColor.WHITE))
                            .append(Component.text(getPermissionDisplayName(permissionType), NamedTextColor.YELLOW))
                    )
                }

                return ClaimResult(true, "${targetMembership.memberName}님에게 '${getPermissionDisplayName(permissionType)}' 권한을 부여했습니다.")
            } else {
                return ClaimResult(false, "권한 부여 중 오류가 발생했습니다.")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return ClaimResult(false, "권한 부여 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 마을 구성원의 권한을 해제합니다.
     */
    fun revokeMemberPermission(
        revokedBy: Player,
        villageId: Int,
        memberUuid: UUID,
        permissionType: VillagePermissionType
    ): ClaimResult {
        try {
            // 1. 권한 해제자가 권한 관리 권한이 있는지 확인
            val revokedByMembership = getPlayerVillageMembership(revokedBy.uniqueId)
            if (revokedByMembership?.villageId != villageId) {
                return ClaimResult(false, "이 마을의 구성원이 아닙니다.")
            }

            val canManagePermissions = when (revokedByMembership.role) {
                VillageRole.MAYOR -> true
                VillageRole.DEPUTY_MAYOR -> hasVillagePermission(revokedBy.uniqueId, villageId, VillagePermissionType.MANAGE_PERMISSIONS)
                VillageRole.MEMBER -> false
            }

            if (!canManagePermissions) {
                return ClaimResult(false, "권한을 관리할 수 있는 권한이 없습니다.")
            }

            // 2. 대상 멤버가 마을 구성원인지 확인
            val targetMembership = getPlayerVillageMembership(memberUuid)
            if (targetMembership?.villageId != villageId) {
                return ClaimResult(false, "대상자가 이 마을의 구성원이 아닙니다.")
            }

            // 3. 권한 해제
            val success = landData.revokeVillagePermission(villageId, memberUuid, permissionType)

            if (success) {
                val targetPlayer = org.bukkit.Bukkit.getPlayer(memberUuid)
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(
                        Component.text()
                            .append(Component.text("❌ ", NamedTextColor.RED))
                            .append(Component.text("마을에서 권한이 해제되었습니다: ", NamedTextColor.WHITE))
                            .append(Component.text(getPermissionDisplayName(permissionType), NamedTextColor.YELLOW))
                    )
                }

                return ClaimResult(true, "${targetMembership.memberName}님의 '${getPermissionDisplayName(permissionType)}' 권한을 해제했습니다.")
            } else {
                return ClaimResult(false, "권한 해제 중 오류가 발생했습니다.")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return ClaimResult(false, "권한 해제 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 마을 구성원의 권한 목록을 가져옵니다.
     */
    fun getMemberPermissions(villageId: Int, memberUuid: UUID): Set<VillagePermissionType> {
        return landData.getMemberPermissions(villageId, memberUuid)
    }

    /**
     * 마을의 모든 구성원 권한을 가져옵니다.
     */
    fun getAllMemberPermissions(villageId: Int): Map<UUID, Set<VillagePermissionType>> {
        return landData.getAllMemberPermissions(villageId)
    }

    /**
     * 플레이어가 특정 마을 권한을 가지고 있는지 확인합니다.
     */
    fun hasVillagePermission(playerUuid: UUID, villageId: Int, permissionType: VillagePermissionType): Boolean {
        return try {
            val memberPermissions = getMemberPermissions(villageId, playerUuid)
            memberPermissions.contains(permissionType)
        } catch (e: Exception) {
            debugManager.log("AdvancedLandClaiming", "[PERMISSION_CHECK] 권한 확인 중 오류 발생: ${e.message}")
            false
        }
    }

    /**
     * 권한의 표시명을 반환합니다.
     */
    fun getPermissionDisplayName(permission: VillagePermissionType): String {
        return when (permission) {
            VillagePermissionType.INVITE_MEMBERS -> "멤버 초대"
            VillagePermissionType.KICK_MEMBERS -> "멤버 추방"
            VillagePermissionType.MANAGE_ROLES -> "역할 관리"
            VillagePermissionType.EXPAND_LAND -> "토지 확장"
            VillagePermissionType.REDUCE_LAND -> "토지 축소"
            VillagePermissionType.MANAGE_LAND -> "토지 관리"
            VillagePermissionType.BUILD -> "건설"
            VillagePermissionType.BREAK_BLOCKS -> "블록 파괴"
            VillagePermissionType.USE_CONTAINERS -> "컨테이너 사용"
            VillagePermissionType.USE_REDSTONE -> "레드스톤 사용"
            VillagePermissionType.MANAGE_PERMISSIONS -> "권한 관리"
            VillagePermissionType.DISSOLVE_VILLAGE -> "마을 해체"
            VillagePermissionType.RENAME_VILLAGE -> "마을 이름 변경"
        }
    }

    /**
     * 캐시 크기 관리 - 청크 캐시 정리
     */
    private fun checkAndCleanupChunkCache() {
        val totalChunks = claimedChunks.values.sumOf { it.size }
        if (totalChunks > MAX_CLAIMED_CHUNKS_CACHE_SIZE * CACHE_CLEANUP_THRESHOLD) {
            debugManager.log("AdvancedLandClaiming", "[CACHE] 청크 캐시 정리 시작: $totalChunks chunks")

            // 가장 적은 수의 청크를 가진 월드부터 제거
            val sortedWorlds = claimedChunks.entries.sortedBy { it.value.size }
            var removedChunks = 0
            val targetRemoval = (totalChunks * 0.2).toInt() // 20% 제거

            for ((worldName, chunks) in sortedWorlds) {
                if (removedChunks >= targetRemoval) break

                val chunksToRemove = minOf(chunks.size / 2, targetRemoval - removedChunks)
                val chunkList = chunks.keys.toList()

                for (i in 0 until chunksToRemove) {
                    chunks.remove(chunkList[i])
                    removedChunks++
                }
            }

            debugManager.log("AdvancedLandClaiming", "[CACHE] 청크 캐시 정리 완료: ${removedChunks}개 제거")
        }
    }

    /**
     * 캐시 크기 관리 - 플레이어 캐시 정리
     */
    private fun checkAndCleanupPlayerCache() {
        if (playerClaims.size > MAX_PLAYER_CLAIMS_CACHE_SIZE * CACHE_CLEANUP_THRESHOLD) {
            debugManager.log("AdvancedLandClaiming", "[CACHE] 플레이어 캐시 정리 시작: ${playerClaims.size} players")

            // 가장 적은 수의 청크를 가진 플레이어부터 제거
            val sortedPlayers = playerClaims.entries.sortedBy { it.value.size }
            var removedPlayers = 0
            val targetRemoval = (playerClaims.size * 0.2).toInt() // 20% 제거

            for ((playerUuid, claims) in sortedPlayers) {
                if (removedPlayers >= targetRemoval) break

                // 클레이밍이 적은 플레이어 우선 제거 (최소 1개 이상 가진 경우에만)
                if (claims.size <= 2) {
                    playerClaims.remove(playerUuid)
                    removedPlayers++
                }
            }

            debugManager.log("AdvancedLandClaiming", "[CACHE] 플레이어 캐시 정리 완료: ${removedPlayers}명 제거")
        }
    }

    /**
     * 전체 캐시 정리 실행
     */
    private fun cleanupCaches() {
        checkAndCleanupChunkCache()
        checkAndCleanupPlayerCache()
    }
}
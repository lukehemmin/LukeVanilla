package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.PlayTime.PlayTimeManager
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.TimeUnit

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
    // 메모리 캐시: 빠른 조회를 위해
    private val claimedChunks = mutableMapOf<String, MutableMap<Pair<Int, Int>, AdvancedClaimInfo>>()
    private val playerClaims = mutableMapOf<UUID, MutableList<AdvancedClaimInfo>>()
    
    private val landData: AdvancedLandData
    
    // 클레이밍 설정
    companion object {
        const val FREE_CLAIMS_COUNT = 4              // 무료 클레이밍 수
        const val NEWBIE_MAX_CLAIMS = 9              // 신규 플레이어 최대 클레이밍
        const val VETERAN_DAYS_THRESHOLD = 7         // 베테랑 플레이어 기준 (일)
        
        // 자원 비용 설정
        const val IRON_COST = 64                     // 철괴 64개 (스택 1개)
        const val DIAMOND_COST = 8                   // 다이아몬드 8개
        const val NETHERITE_COST = 2                 // 네더라이트 주괴 2개
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
        
        claimedChunks.putAll(loadedClaims)
        
        // 플레이어별 클레이밍 캐시 구성
        loadedClaims.forEach { (worldName, chunks) ->
            chunks.forEach { (chunkCoords, claimInfo) ->
                playerClaims.computeIfAbsent(claimInfo.ownerUuid) { mutableListOf() }
                    .add(claimInfo)
            }
        }
        
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
            claimedChunks.computeIfAbsent(worldName) { mutableMapOf() }[chunkCoord] = claimInfo
            playerClaims.computeIfAbsent(playerUuid) { mutableListOf() }.add(claimInfo)
            
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
        
        // 데이터베이스에서 제거
        if (landData.removeClaim(worldName, chunk.x, chunk.z, player.uniqueId, player.name, "자발적 포기")) {
            // 캐시에서 제거
            claimedChunks[worldName]?.remove(chunkCoord)
            playerClaims[player.uniqueId]?.removeAll { it.chunkX == chunk.x && it.chunkZ == chunk.z && it.worldName == worldName }
            
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
}
package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Service

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Cache.AdvancedLandCache
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Cache.ChunkCoordinate
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.AdvancedLandData
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.ClaimResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Chunk
import org.bukkit.entity.Player
import java.util.*

/**
 * 마을 관리 전담 서비스
 *
 * 기존 AdvancedLandManager에서 마을 관련 기능만 분리
 * - 단일 책임 원칙 적용
 * - 의존성 주입으로 테스트 가능성 향상
 * - 비즈니스 로직과 데이터 레이어 분리
 */
class VillageManagementService(
    private val landData: AdvancedLandData,
    private val cache: AdvancedLandCache,
    private val atomicClaimService: AtomicClaimService,
    private val debugManager: DebugManager
) {

    /**
     * 🏘️ 마을 생성 (개선된 버전)
     *
     * 기존 문제: 거대한 메서드 (58줄)
     * 해결: 작은 메서드들로 분리, 책임 분담
     */
    fun createVillage(
        player: Player,
        villageName: String,
        connectedChunks: Set<Chunk>
    ): ClaimResult {
        debugManager.log("VillageManagement", "[CREATE_VILLAGE] 시작: '$villageName' by ${player.name}")

        return try {
            // 1단계: 사전 검증
            val validationResult = validateVillageCreation(player, villageName, connectedChunks)
            if (!validationResult.success) {
                return validationResult
            }

            // 2단계: 마을 생성
            val villageId = createVillageRecord(villageName, player)
                ?: return ClaimResult(false, "마을 생성 중 데이터베이스 오류가 발생했습니다.")

            // 3단계: 이장 등록
            if (!registerMayor(villageId, player)) {
                return ClaimResult(false, "마을 이장 등록 중 오류가 발생했습니다.")
            }

            // 4단계: 청크들을 마을 토지로 변환
            val conversionResult = convertChunksToVillage(villageId, connectedChunks, player)
            if (!conversionResult.success) {
                return conversionResult
            }

            // 5단계: 캐시 업데이트
            updateCacheForVillageCreation(villageId, connectedChunks)

            debugManager.log("VillageManagement", "[CREATE_VILLAGE_SUCCESS] '$villageName' (ID: $villageId)")
            ClaimResult(true, "마을 '$villageName'이 성공적으로 생성되었습니다!")

        } catch (e: Exception) {
            debugManager.log("VillageManagement", "[CREATE_VILLAGE_ERROR] ${player.name}: ${e.message}")
            e.printStackTrace()
            ClaimResult(false, "마을 생성 중 예상치 못한 오류가 발생했습니다.")
        }
    }

    /**
     * 마을 생성 사전 검증
     */
    private fun validateVillageCreation(
        player: Player,
        villageName: String,
        connectedChunks: Set<Chunk>
    ): ClaimResult {
        // 마을 이름 중복 확인
        if (landData.isVillageNameExists(villageName)) {
            return ClaimResult(false, "이미 존재하는 마을 이름입니다.")
        }

        // 플레이어가 모든 청크의 소유자인지 확인
        for (chunk in connectedChunks) {
            val claimInfo = cache.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
            if (claimInfo == null || claimInfo.ownerUuid != player.uniqueId) {
                return ClaimResult(false, "마을로 변환할 수 없는 청크가 포함되어 있습니다: ${chunk.world.name}(${chunk.x},${chunk.z})")
            }
            if (claimInfo.claimType != ClaimType.PERSONAL) {
                return ClaimResult(false, "개인 토지만 마을로 변환할 수 있습니다.")
            }
        }

        return ClaimResult(true, "검증 완료")
    }

    /**
     * 마을 레코드 생성
     */
    private fun createVillageRecord(villageName: String, player: Player): Int? {
        return landData.createVillage(villageName, player.uniqueId, player.name)
    }

    /**
     * 이장 등록
     */
    private fun registerMayor(villageId: Int, player: Player): Boolean {
        return landData.addVillageMember(villageId, player.uniqueId, player.name, VillageRole.MAYOR)
    }

    /**
     * 청크들을 마을 토지로 변환
     */
    private fun convertChunksToVillage(
        villageId: Int,
        connectedChunks: Set<Chunk>,
        player: Player
    ): ClaimResult {
        var convertedCount = 0

        for (chunk in connectedChunks) {
            val currentClaimInfo = cache.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
            if (currentClaimInfo != null) {
                val updatedClaimInfo = currentClaimInfo.copy(
                    claimType = ClaimType.VILLAGE,
                    villageId = villageId,
                    lastUpdated = System.currentTimeMillis()
                )

                if (landData.updateClaimToVillage(updatedClaimInfo)) {
                    cache.addClaim(updatedClaimInfo) // 캐시 업데이트
                    convertedCount++
                }
            }
        }

        return if (convertedCount == connectedChunks.size) {
            ClaimResult(true, "모든 청크 변환 완료: ${convertedCount}개")
        } else {
            ClaimResult(false, "일부 청크 변환에 실패했습니다. ($convertedCount/${connectedChunks.size})")
        }
    }

    /**
     * 마을 생성 시 캐시 업데이트
     */
    private fun updateCacheForVillageCreation(villageId: Int, connectedChunks: Set<Chunk>) {
        // 이미 convertChunksToVillage에서 개별적으로 캐시 업데이트됨
        debugManager.log("VillageManagement", "[CACHE_UPDATE] 마을 $villageId, ${connectedChunks.size}개 청크")
    }

    /**
     * 🏘️ 마을 해체 (개선된 버전)
     *
     * 기존 문제: 거대한 메서드 (83줄)
     * 해결: 단계별 메서드 분리, 캐시 최적화 활용
     */
    fun disbandVillage(mayorPlayer: Player, villageId: Int): ClaimResult {
        debugManager.log("VillageManagement", "[DISBAND_VILLAGE] 시작: 마을 ID $villageId by ${mayorPlayer.name}")

        return try {
            // 1단계: 권한 검증
            val validationResult = validateVillageDisband(mayorPlayer, villageId)
            if (!validationResult.success) return validationResult

            val villageInfo = validationResult.claimInfo as VillageInfo

            // 2단계: 🚀 캐시 최적화 활용 - O(1) 청크 조회
            val villageChunks = cache.getVillageChunks(villageId)
            debugManager.log("VillageManagement", "[DISBAND] 마을 '${villageInfo.villageName}' 해체 - ${villageChunks.size}개 청크")

            // 3단계: 마을 토지를 개인 토지로 변환 (캐시 레벨에서 원자적 처리)
            val convertedClaims = cache.convertVillageToPersonalLands(
                villageId,
                villageInfo.mayorUuid,
                villageInfo.mayorName
            )

            // 4단계: 데이터베이스 업데이트 (배치 처리)
            updateDatabaseForDisband(convertedClaims)

            // 5단계: 마을 구성원 및 마을 정보 정리
            cleanupVillageData(villageId)

            // 6단계: 온라인 멤버들에게 알림
            notifyVillageMembers(villageId, villageInfo, NotificationType.DISBAND)

            debugManager.log("VillageManagement", "[DISBAND_SUCCESS] 마을 '${villageInfo.villageName}' 해체 완료")
            ClaimResult(true, "마을 '${villageInfo.villageName}'이 성공적으로 해체되었습니다.")

        } catch (e: Exception) {
            debugManager.log("VillageManagement", "[DISBAND_ERROR] ${mayorPlayer.name}: ${e.message}")
            e.printStackTrace()
            ClaimResult(false, "마을 해체 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 마을 해체 권한 검증
     */
    private fun validateVillageDisband(mayorPlayer: Player, villageId: Int): ClaimResult {
        val villageInfo = landData.getVillageInfo(villageId)
            ?: return ClaimResult(false, "마을 정보를 찾을 수 없습니다.")

        if (villageInfo.mayorUuid != mayorPlayer.uniqueId) {
            return ClaimResult(false, "마을장만 마을을 해체할 수 있습니다.")
        }

        return ClaimResult(true, "검증 완료")
    }

    /**
     * 해체 시 데이터베이스 업데이트 (배치 처리)
     */
    private fun updateDatabaseForDisband(convertedClaims: List<AdvancedClaimInfo>) {
        convertedClaims.forEach { claimInfo ->
            landData.updateClaimToPersonal(
                claimInfo.worldName,
                claimInfo.chunkX,
                claimInfo.chunkZ,
                claimInfo.ownerUuid,
                claimInfo.ownerName
            )
        }
    }

    /**
     * 마을 데이터 정리
     */
    private fun cleanupVillageData(villageId: Int) {
        // 모든 마을 멤버 제거
        val members = landData.getVillageMembers(villageId)
        members.forEach { member ->
            landData.removeVillageMember(villageId, member.memberUuid)
        }

        // 마을 비활성화
        landData.deactivateVillage(villageId)
    }

    /**
     * 👑 이장 양도 (개선된 버전)
     *
     * 기존 문제: 거대한 메서드 (125줄)
     * 해결: 단계별 분리, 캐시 최적화 활용
     */
    fun transferMayorship(
        currentMayor: Player,
        villageId: Int,
        newMayorUuid: UUID,
        newMayorName: String
    ): ClaimResult {
        debugManager.log("VillageManagement", "[TRANSFER_MAYOR] 시작: 마을 ID $villageId, ${currentMayor.name} → $newMayorName")

        return try {
            // 1단계: 권한 및 자격 검증
            val validationResult = validateMayorshipTransfer(currentMayor, villageId, newMayorUuid, newMayorName)
            if (!validationResult.success) return validationResult

            val villageInfo = validationResult.claimInfo as VillageInfo

            // 2단계: 데이터베이스 업데이트
            if (!updateMayorshipInDatabase(villageId, newMayorUuid, newMayorName, currentMayor.uniqueId)) {
                return ClaimResult(false, "이장 양도 중 데이터베이스 오류가 발생했습니다.")
            }

            // 3단계: 🚀 캐시 최적화 - 마을 토지 소유권 일괄 변경
            val villageChunks = cache.getVillageChunks(villageId)
            updateVillageChunkOwnership(villageChunks, newMayorUuid, "${villageInfo.villageName} (마을)")

            // 4단계: 멤버들에게 알림
            notifyMayorshipTransfer(villageId, villageInfo, currentMayor, newMayorName, newMayorUuid)

            debugManager.log("VillageManagement", "[TRANSFER_MAYOR_SUCCESS] 마을 '${villageInfo.villageName}' 이장 양도 완료")
            ClaimResult(true, "마을 '${villageInfo.villageName}'의 이장을 ${newMayorName}님께 성공적으로 양도했습니다.")

        } catch (e: Exception) {
            debugManager.log("VillageManagement", "[TRANSFER_MAYOR_ERROR] ${currentMayor.name}: ${e.message}")
            e.printStackTrace()
            ClaimResult(false, "이장 양도 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 이장 양도 검증
     */
    private fun validateMayorshipTransfer(
        currentMayor: Player,
        villageId: Int,
        newMayorUuid: UUID,
        newMayorName: String
    ): ClaimResult {
        val villageInfo = landData.getVillageInfo(villageId)
            ?: return ClaimResult(false, "마을 정보를 찾을 수 없습니다.")

        if (villageInfo.mayorUuid != currentMayor.uniqueId) {
            return ClaimResult(false, "마을장만 이장을 양도할 수 있습니다.")
        }

        if (newMayorUuid == currentMayor.uniqueId) {
            return ClaimResult(false, "자기 자신에게는 이장을 양도할 수 없습니다.")
        }

        val members = landData.getVillageMembers(villageId)
        val newMayorMember = members.find { it.memberUuid == newMayorUuid }
        if (newMayorMember == null) {
            return ClaimResult(false, "마을 구성원만 이장으로 양도할 수 있습니다.")
        }

        return ClaimResult(true, "검증 완료")
    }

    /**
     * 데이터베이스에서 이장 정보 업데이트
     */
    private fun updateMayorshipInDatabase(
        villageId: Int,
        newMayorUuid: UUID,
        newMayorName: String,
        oldMayorUuid: UUID
    ): Boolean {
        // 1. 마을 정보 업데이트
        if (!landData.updateVillageMayor(villageId, newMayorUuid, newMayorName)) {
            return false
        }

        // 2. 새 이장 역할 변경
        if (!landData.updateVillageMemberRole(villageId, newMayorUuid, VillageRole.MAYOR)) {
            return false
        }

        // 3. 기존 이장을 일반 멤버로 변경
        if (!landData.updateVillageMemberRole(villageId, oldMayorUuid, VillageRole.MEMBER)) {
            return false
        }

        return true
    }

    /**
     * 마을 토지 소유권 업데이트
     */
    private fun updateVillageChunkOwnership(
        villageChunks: Set<ChunkCoordinate>,
        newOwnerUuid: UUID,
        newOwnerName: String
    ) {
        villageChunks.forEach { chunkCoord ->
            val currentClaim = cache.getClaimOwner(chunkCoord.worldName, chunkCoord.x, chunkCoord.z)
            if (currentClaim != null) {
                val updatedClaim = currentClaim.copy(
                    ownerUuid = newOwnerUuid,
                    ownerName = newOwnerName,
                    lastUpdated = System.currentTimeMillis()
                )
                cache.addClaim(updatedClaim)

                // 데이터베이스도 업데이트
                landData.updateClaimOwner(chunkCoord.worldName, chunkCoord.x, chunkCoord.z, newOwnerUuid, newOwnerName)
            }
        }
    }

    // === 알림 시스템 ===

    /**
     * 마을 구성원들에게 알림
     */
    private fun notifyVillageMembers(
        villageId: Int,
        villageInfo: VillageInfo,
        notificationType: NotificationType
    ) {
        val members = landData.getVillageMembers(villageId)
        members.forEach { member ->
            val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
            if (onlinePlayer != null && member.memberUuid != villageInfo.mayorUuid) {
                val message = when (notificationType) {
                    NotificationType.DISBAND -> Component.text()
                        .append(Component.text("📢 ", NamedTextColor.RED))
                        .append(Component.text("마을 '", NamedTextColor.WHITE))
                        .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                        .append(Component.text("'이 해체되었습니다.", NamedTextColor.WHITE))
                    NotificationType.TRANSFER -> Component.text("이장이 양도되었습니다.", NamedTextColor.YELLOW)
                    NotificationType.MEMBER_JOIN -> Component.text("마을에 새 멤버가 가입했습니다.", NamedTextColor.GREEN)
                    NotificationType.MEMBER_LEAVE -> Component.text("마을에서 멤버가 탈퇴했습니다.", NamedTextColor.YELLOW)
                }
                onlinePlayer.sendMessage(message)
            }
        }
    }

    /**
     * 이장 양도 알림
     */
    private fun notifyMayorshipTransfer(
        villageId: Int,
        villageInfo: VillageInfo,
        currentMayor: Player,
        newMayorName: String,
        newMayorUuid: UUID
    ) {
        val members = landData.getVillageMembers(villageId)
        members.forEach { member ->
            val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
            if (onlinePlayer != null) {
                val message = when (member.memberUuid) {
                    currentMayor.uniqueId -> Component.text()
                        .append(Component.text("👑 ", NamedTextColor.GOLD))
                        .append(Component.text("마을 '", NamedTextColor.WHITE))
                        .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                        .append(Component.text("'의 이장을 ", NamedTextColor.WHITE))
                        .append(Component.text(newMayorName, NamedTextColor.AQUA))
                        .append(Component.text("님께 양도했습니다.", NamedTextColor.WHITE))

                    newMayorUuid -> Component.text()
                        .append(Component.text("🎉 ", NamedTextColor.GOLD))
                        .append(Component.text("마을 '", NamedTextColor.WHITE))
                        .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                        .append(Component.text("'의 새로운 이장이 되었습니다!", NamedTextColor.WHITE))

                    else -> Component.text()
                        .append(Component.text("📢 ", NamedTextColor.BLUE))
                        .append(Component.text("마을 '", NamedTextColor.WHITE))
                        .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                        .append(Component.text("'의 이장이 ", NamedTextColor.WHITE))
                        .append(Component.text(newMayorName, NamedTextColor.AQUA))
                        .append(Component.text("님으로 변경되었습니다.", NamedTextColor.WHITE))
                }
                onlinePlayer.sendMessage(message)
            }
        }
    }

    // === 조회 메서드들 ===

    fun getVillageInfo(villageId: Int): VillageInfo? = landData.getVillageInfo(villageId)
    fun getVillageMembers(villageId: Int): List<VillageMember> = landData.getVillageMembers(villageId)
    fun getVillageChunkCount(villageId: Int): Int = cache.getVillageChunkCount(villageId)
    fun getPlayerVillageMembership(playerUuid: UUID): VillageMember? = landData.getPlayerVillageMembership(playerUuid)
}

/**
 * 알림 타입
 */
enum class NotificationType {
    DISBAND,
    TRANSFER,
    MEMBER_JOIN,
    MEMBER_LEAVE
}


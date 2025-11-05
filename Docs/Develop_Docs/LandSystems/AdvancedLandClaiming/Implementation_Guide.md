# AdvancedLandClaiming 미구현 기능 구현 가이드

## 1. 개요
AdvancedLandClaiming 시스템에서 아직 구현되지 않은 주요 기능들의 구현 방법을 상세히 안내합니다.

## 2. 마을 초대 수락/거절 시스템

### 2.1 현재 상태
- 현재: `addVillageMember()`로 직접 추가만 가능
- 필요: 초대 → 수락/거절 → 가입 플로우

### 2.2 구현 계획

#### 데이터베이스 테이블 추가
```sql
-- 마을 초대 정보를 저장하는 테이블
CREATE TABLE advanced_village_invitations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_id INT NOT NULL,
    inviter_uuid VARCHAR(36) NOT NULL,
    inviter_name VARCHAR(50) NOT NULL,
    invitee_uuid VARCHAR(36) NOT NULL,
    invitee_name VARCHAR(50) NOT NULL,
    invited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED') DEFAULT 'PENDING',
    message TEXT,
    
    FOREIGN KEY (village_id) REFERENCES advanced_villages(id) ON DELETE CASCADE,
    INDEX idx_invitee (invitee_uuid),
    INDEX idx_status (status),
    INDEX idx_expires (expires_at)
);
```

#### 새로운 명령어 구조
```kotlin
// 초대 관련 명령어들
/고급땅 마을초대 <플레이어>         // 초대 전송
/고급땅 초대목록                    // 받은 초대 목록
/고급땅 초대수락 <마을ID>           // 초대 수락
/고급땅 초대거절 <마을ID>           // 초대 거절
/고급땅 보낸초대목록                // 보낸 초대 목록 (이장용)
/고급땅 초대취소 <플레이어>         // 초대 취소 (이장용)
```

#### VillageInvitation 데이터 모델
```kotlin
data class VillageInvitation(
    val id: Int,
    val villageId: Int,
    val inviterUuid: UUID,
    val inviterName: String,
    val inviteeUuid: UUID,
    val inviteeName: String,
    val invitedAt: Long,
    val expiresAt: Long,
    val status: InvitationStatus,
    val message: String?
)

enum class InvitationStatus {
    PENDING,    // 대기 중
    ACCEPTED,   // 수락됨
    REJECTED,   // 거절됨
    EXPIRED     // 만료됨
}
```

#### 구현 단계별 가이드

##### 1단계: 데이터 레이어 구현
```kotlin
// AdvancedLandData.kt에 추가
class AdvancedLandData(private val database: Database) {
    
    fun createInvitation(
        villageId: Int, 
        inviterUuid: UUID, 
        inviterName: String,
        inviteeUuid: UUID, 
        inviteeName: String,
        expirationHours: Int = 48,
        message: String? = null
    ): Boolean {
        val sql = """
            INSERT INTO advanced_village_invitations 
            (village_id, inviter_uuid, inviter_name, invitee_uuid, invitee_name, expires_at, message)
            VALUES (?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR), ?)
        """
        
        return database.executeUpdate(sql, villageId, inviterUuid.toString(), inviterName, 
                                     inviteeUuid.toString(), inviteeName, expirationHours, message) > 0
    }
    
    fun getInvitationsForPlayer(playerUuid: UUID): List<VillageInvitation> {
        val sql = """
            SELECT i.*, v.village_name 
            FROM advanced_village_invitations i
            JOIN advanced_villages v ON i.village_id = v.id
            WHERE i.invitee_uuid = ? AND i.status = 'PENDING' AND i.expires_at > NOW()
            ORDER BY i.invited_at DESC
        """
        
        return database.executeQuery(sql, playerUuid.toString()) { rs ->
            val invitations = mutableListOf<VillageInvitation>()
            while (rs.next()) {
                invitations.add(VillageInvitation(
                    id = rs.getInt("id"),
                    villageId = rs.getInt("village_id"),
                    inviterUuid = UUID.fromString(rs.getString("inviter_uuid")),
                    inviterName = rs.getString("inviter_name"),
                    inviteeUuid = playerUuid,
                    inviteeName = rs.getString("invitee_name"),
                    invitedAt = rs.getTimestamp("invited_at").time,
                    expiresAt = rs.getTimestamp("expires_at").time,
                    status = InvitationStatus.valueOf(rs.getString("status")),
                    message = rs.getString("message")
                ))
            }
            invitations
        } ?: emptyList()
    }
    
    fun updateInvitationStatus(invitationId: Int, status: InvitationStatus): Boolean {
        val sql = "UPDATE advanced_village_invitations SET status = ? WHERE id = ?"
        return database.executeUpdate(sql, status.name, invitationId) > 0
    }
    
    fun hasActiveInvitation(villageId: Int, inviteeUuid: UUID): Boolean {
        val sql = """
            SELECT COUNT(*) FROM advanced_village_invitations 
            WHERE village_id = ? AND invitee_uuid = ? AND status = 'PENDING' AND expires_at > NOW()
        """
        
        return database.executeQuery(sql, villageId, inviteeUuid.toString()) { rs ->
            rs.next() && rs.getInt(1) > 0
        } ?: false
    }
}
```

##### 2단계: 비즈니스 로직 구현
```kotlin
// AdvancedLandManager.kt에 추가
class AdvancedLandManager {
    
    fun invitePlayerToVillage(
        inviter: Player, 
        inviteeName: String, 
        villageId: Int,
        message: String? = null
    ): ClaimResult {
        // 1. 초대자 권한 확인
        val membership = getPlayerVillageMembership(inviter.uniqueId)
        if (membership == null || membership.villageId != villageId) {
            return ClaimResult(false, "해당 마을의 구성원이 아닙니다.")
        }
        
        if (!hasVillagePermission(inviter.uniqueId, villageId, VillagePermissionType.INVITE_MEMBERS)) {
            return ClaimResult(false, "마을 초대 권한이 없습니다.")
        }
        
        // 2. 초대받을 플레이어 확인
        val invitee = Bukkit.getOfflinePlayer(inviteeName)
        if (!invitee.hasPlayedBefore() && !invitee.isOnline) {
            return ClaimResult(false, "존재하지 않는 플레이어입니다.")
        }
        
        // 3. 이미 마을 멤버인지 확인
        if (getPlayerVillageMembership(invitee.uniqueId) != null) {
            return ClaimResult(false, "${inviteeName}님은 이미 다른 마을의 구성원입니다.")
        }
        
        // 4. 이미 초대가 있는지 확인
        if (landData.hasActiveInvitation(villageId, invitee.uniqueId)) {
            return ClaimResult(false, "${inviteeName}님에게 이미 보낸 초대가 있습니다.")
        }
        
        // 5. 초대 생성
        val success = landData.createInvitation(
            villageId, inviter.uniqueId, inviter.name, 
            invitee.uniqueId, inviteeName, 48, message
        )
        
        if (success) {
            // 6. 초대받은 플레이어에게 알림 (온라인인 경우)
            if (invitee.isOnline) {
                val villageInfo = getVillageInfo(villageId)
                invitee.player?.sendMessage(
                    Component.text("${villageInfo?.villageName} 마을에서 초대가 도착했습니다!", NamedTextColor.GREEN)
                )
                invitee.player?.sendMessage(
                    Component.text("/고급땅 초대목록 명령어로 확인하세요.", NamedTextColor.YELLOW)
                )
            }
            
            return ClaimResult(true, "${inviteeName}님에게 마을 초대를 보냈습니다.")
        } else {
            return ClaimResult(false, "초대 전송 중 오류가 발생했습니다.")
        }
    }
    
    fun acceptVillageInvitation(player: Player, villageId: Int): ClaimResult {
        // 1. 해당 초대가 있는지 확인
        val invitations = landData.getInvitationsForPlayer(player.uniqueId)
        val invitation = invitations.find { it.villageId == villageId }
            ?: return ClaimResult(false, "해당 마을로부터 초대가 없습니다.")
        
        // 2. 이미 다른 마을 멤버인지 재확인
        if (getPlayerVillageMembership(player.uniqueId) != null) {
            return ClaimResult(false, "이미 다른 마을의 구성원입니다.")
        }
        
        // 3. 마을 정보 확인
        val villageInfo = getVillageInfo(villageId)
            ?: return ClaimResult(false, "마을을 찾을 수 없습니다.")
        
        // 4. 마을 멤버로 추가
        val addSuccess = addVillageMember(villageId, player.uniqueId, player.name, VillageRole.MEMBER)
        if (!addSuccess) {
            return ClaimResult(false, "마을 가입 중 오류가 발생했습니다.")
        }
        
        // 5. 초대 상태 업데이트
        landData.updateInvitationStatus(invitation.id, InvitationStatus.ACCEPTED)
        
        // 6. 알림
        player.sendMessage(Component.text("${villageInfo.villageName} 마을에 가입했습니다!", NamedTextColor.GREEN))
        
        // 7. 초대자에게 알림 (온라인인 경우)
        val inviter = Bukkit.getPlayer(invitation.inviterUuid)
        inviter?.sendMessage(
            Component.text("${player.name}님이 마을 초대를 수락했습니다!", NamedTextColor.GREEN)
        )
        
        return ClaimResult(true, "마을 가입이 완료되었습니다!")
    }
    
    fun rejectVillageInvitation(player: Player, villageId: Int): ClaimResult {
        val invitations = landData.getInvitationsForPlayer(player.uniqueId)
        val invitation = invitations.find { it.villageId == villageId }
            ?: return ClaimResult(false, "해당 마을로부터 초대가 없습니다.")
        
        landData.updateInvitationStatus(invitation.id, InvitationStatus.REJECTED)
        
        val villageInfo = getVillageInfo(villageId)
        player.sendMessage(Component.text("${villageInfo?.villageName} 마을 초대를 거절했습니다.", NamedTextColor.YELLOW))
        
        // 초대자에게 알림
        val inviter = Bukkit.getPlayer(invitation.inviterUuid)
        inviter?.sendMessage(
            Component.text("${player.name}님이 마을 초대를 거절했습니다.", NamedTextColor.RED)
        )
        
        return ClaimResult(true, "초대를 거절했습니다.")
    }
}
```

##### 3단계: 명령어 구현
```kotlin
// AdvancedLandCommand.kt에 추가
private fun handleVillageInvite(player: Player, args: Array<out String>) {
    if (args.size < 2) {
        player.sendMessage(Component.text("사용법: /고급땅 마을초대 <플레이어> [메시지]", NamedTextColor.RED))
        return
    }
    
    val targetPlayerName = args[1]
    val message = if (args.size > 2) args.drop(2).joinToString(" ") else null
    
    // 현재 위치에서 마을 ID 확인
    val chunk = player.location.chunk
    val claimInfo = advancedLandManager.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
    
    if (claimInfo?.claimType != ClaimType.VILLAGE || claimInfo.villageId == null) {
        player.sendMessage(Component.text("마을 토지에서만 초대할 수 있습니다.", NamedTextColor.RED))
        return
    }
    
    val result = advancedLandManager.invitePlayerToVillage(player, targetPlayerName, claimInfo.villageId!!, message)
    val color = if (result.success) NamedTextColor.GREEN else NamedTextColor.RED
    player.sendMessage(Component.text(result.message, color))
}

private fun handleInvitationList(player: Player, args: Array<out String>) {
    val invitations = advancedLandManager.getPlayerInvitations(player.uniqueId)
    
    if (invitations.isEmpty()) {
        player.sendMessage(Component.text("받은 초대가 없습니다.", NamedTextColor.YELLOW))
        return
    }
    
    player.sendMessage(Component.text("=== 마을 초대 목록 ===", NamedTextColor.GOLD))
    invitations.forEach { invitation ->
        val villageInfo = advancedLandManager.getVillageInfo(invitation.villageId)
        val timeLeft = (invitation.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60) // 시간
        
        player.sendMessage(Component.text("${villageInfo?.villageName} (ID: ${invitation.villageId})", NamedTextColor.AQUA))
        player.sendMessage(Component.text("  초대자: ${invitation.inviterName}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  남은 시간: ${timeLeft}시간", NamedTextColor.GRAY))
        invitation.message?.let { msg ->
            player.sendMessage(Component.text("  메시지: $msg", NamedTextColor.WHITE))
        }
        player.sendMessage(Component.text("  /고급땅 초대수락 ${invitation.villageId} 또는 /고급땅 초대거절 ${invitation.villageId}", NamedTextColor.YELLOW))
    }
}
```

## 3. 마을 해체 시스템

### 3.1 현재 상태
- 마을 해체 기능이 구현되지 않음
- 모든 토지를 개인 토지로 전환하는 로직 필요

### 3.2 구현 계획

#### 마을 해체 권한
- 이장만 해체 가능
- 이중 확인 과정 필요 (실수 방지)

#### 해체 시 처리 사항
1. 모든 마을 토지를 이장의 개인 토지로 전환
2. 연결성 유지 (ConnectedChunks 업데이트)
3. 모든 마을 멤버에게 알림
4. 마을 관련 데이터 정리 (비활성화)

#### 구현 코드
```kotlin
// AdvancedLandManager.kt에 추가
fun disbandVillage(player: Player, villageId: Int, confirmation: String? = null): ClaimResult {
    // 1. 권한 확인 (이장인지)
    val villageInfo = getVillageInfo(villageId) 
        ?: return ClaimResult(false, "마을을 찾을 수 없습니다.")
    
    if (villageInfo.mayorUuid != player.uniqueId) {
        return ClaimResult(false, "마을 이장만 마을을 해체할 수 있습니다.")
    }
    
    // 2. 이중 확인
    if (confirmation != "확인") {
        return ClaimResult(false, "마을 해체를 확인하려면 '/고급땅 마을해체 ${villageId} 확인'을 입력하세요. 이 작업은 되돌릴 수 없습니다!")
    }
    
    // 3. 마을 토지 목록 조회
    val villageChunks = getVillageChunks(villageId)
    if (villageChunks.isEmpty()) {
        // 토지가 없는 마을은 바로 해체
        return deactivateVillageOnly(villageId)
    }
    
    // 4. 모든 마을 멤버에게 알림 (해체 전)
    val members = getVillageMembers(villageId)
    members.forEach { member ->
        val memberPlayer = Bukkit.getPlayer(member.memberUuid)
        memberPlayer?.sendMessage(
            Component.text("${villageInfo.villageName} 마을이 해체되었습니다.", NamedTextColor.RED)
        )
    }
    
    // 5. 모든 마을 토지를 개인 토지로 전환
    var convertedCount = 0
    villageChunks.forEach { chunkCoord ->
        val world = plugin.server.getWorld(chunkCoord.worldName)
        val chunk = world?.getChunkAt(chunkCoord.x, chunkCoord.z)
        
        if (chunk != null) {
            val claimInfo = getClaimOwner(chunkCoord.worldName, chunkCoord.x, chunkCoord.z)
            if (claimInfo != null && claimInfo.claimType == ClaimType.VILLAGE) {
                val updatedClaimInfo = claimInfo.copy(
                    claimType = ClaimType.PERSONAL,
                    villageId = null,
                    ownerUuid = villageInfo.mayorUuid,
                    ownerName = villageInfo.mayorName,
                    lastUpdated = System.currentTimeMillis()
                )
                
                if (landData.updateClaimToPersonal(updatedClaimInfo)) {
                    updateClaimCache(updatedClaimInfo)
                    convertedCount++
                }
            }
        }
    }
    
    // 6. 연결된 청크 그룹 업데이트
    updateConnectedChunks(villageInfo.mayorUuid)
    
    // 7. 마을 비활성화
    landData.deactivateVillage(villageId)
    
    // 8. 로그 기록
    landData.addHistory(
        worldName = "VILLAGE_SYSTEM",
        chunkX = 0, chunkZ = 0,
        actionType = "VILLAGE_DISBAND",
        actorUuid = player.uniqueId,
        actorName = player.name,
        reason = "마을 해체",
        details = "마을ID: $villageId, 이장: ${player.name}, 전환된 토지: ${convertedCount}개"
    )
    
    debugManager.log("AdvancedLandClaiming", "[VILLAGE_DISBAND] ${player.name}: 마을 '${villageInfo.villageName}' 해체, ${convertedCount}개 토지 전환")
    
    return ClaimResult(true, "마을이 해체되었습니다. ${convertedCount}개의 토지가 개인 토지로 전환되었습니다.")
}

// 새로운 메서드들
private fun getVillageChunks(villageId: Int): List<ChunkCoordinate> {
    val chunks = mutableListOf<ChunkCoordinate>()
    claimedChunks.forEach { (worldName, worldChunks) ->
        worldChunks.forEach { (chunkCoord, claimInfo) ->
            if (claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId == villageId) {
                chunks.add(ChunkCoordinate(chunkCoord.first, chunkCoord.second, worldName))
            }
        }
    }
    return chunks
}

private fun deactivateVillageOnly(villageId: Int): ClaimResult {
    return if (landData.deactivateVillage(villageId)) {
        ClaimResult(true, "마을이 해체되었습니다.")
    } else {
        ClaimResult(false, "마을 해체 중 오류가 발생했습니다.")
    }
}
```

## 4. 이장 양도 시스템

### 4.1 구현 계획

#### 양도 과정
1. 현재 이장이 다른 마을원에게 양도 제안
2. 대상자가 수락/거절
3. 수락 시 이장 권한 이전 및 모든 토지 소유권 이전

#### 데이터베이스 테이블
```sql
-- 이장 양도 요청 테이블
CREATE TABLE advanced_mayor_transfers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_id INT NOT NULL,
    current_mayor_uuid VARCHAR(36) NOT NULL,
    target_member_uuid VARCHAR(36) NOT NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED') DEFAULT 'PENDING',
    message TEXT,
    
    FOREIGN KEY (village_id) REFERENCES advanced_villages(id) ON DELETE CASCADE,
    INDEX idx_target (target_member_uuid),
    INDEX idx_status (status)
);
```

#### 구현 코드
```kotlin
fun requestMayorTransfer(currentMayor: Player, targetMemberName: String, villageId: Int): ClaimResult {
    // 1. 현재 이장 권한 확인
    val villageInfo = getVillageInfo(villageId)
        ?: return ClaimResult(false, "마을을 찾을 수 없습니다.")
    
    if (villageInfo.mayorUuid != currentMayor.uniqueId) {
        return ClaimResult(false, "마을 이장만 이장 양도를 할 수 있습니다.")
    }
    
    // 2. 대상 멤버 확인
    val targetPlayer = Bukkit.getOfflinePlayer(targetMemberName)
    val membership = getPlayerVillageMembership(targetPlayer.uniqueId)
    
    if (membership == null || membership.villageId != villageId) {
        return ClaimResult(false, "${targetMemberName}님은 이 마을의 구성원이 아닙니다.")
    }
    
    // 3. 양도 요청 생성
    val success = landData.createMayorTransferRequest(villageId, currentMayor.uniqueId, targetPlayer.uniqueId)
    
    if (success) {
        // 4. 대상자에게 알림
        targetPlayer.player?.sendMessage(
            Component.text("${villageInfo.villageName} 마을의 이장 양도 제안이 도착했습니다!", NamedTextColor.GOLD)
        )
        
        return ClaimResult(true, "${targetMemberName}님에게 이장 양도 제안을 보냈습니다.")
    } else {
        return ClaimResult(false, "양도 요청 중 오류가 발생했습니다.")
    }
}

fun acceptMayorTransfer(player: Player, villageId: Int): ClaimResult {
    // 양도 수락 로직
    // 1. 모든 마을 토지의 소유권을 새 이장으로 변경
    // 2. 마을 정보 업데이트
    // 3. 역할 변경 (기존 이장 → 부이장, 새 이장 → 이장)
    // 4. 연결된 청크 그룹 소유권 변경
}
```

## 5. 개인 토지 반환 시스템 (MyLand)

### 5.1 현재 상태
- MyLand에는 토지 반환 기능이 없음
- AdvancedLand에만 환불 시스템 존재

### 5.2 구현 계획

#### LandManager에 추가할 메서드
```kotlin
// MyLand LandManager.kt에 추가
fun returnLand(chunk: Chunk, player: Player): ClaimResult {
    // 1. 소유권 확인
    val owner = getChunkOwner(chunk)
    if (owner == null) {
        return ClaimResult.NOT_CLAIMED
    }
    
    if (owner.ownerUuid != player.uniqueId) {
        return ClaimResult.NOT_OWNER
    }
    
    // 2. 데이터베이스에서 제거
    val success = landData.removeClaim(chunk)
    
    if (success) {
        // 3. 캐시 업데이트
        removeFromCache(chunk)
        
        // 4. 히스토리 추가
        landData.addHistory(
            chunk = chunk,
            actionType = "RETURN",
            actorUuid = player.uniqueId,
            actorName = player.name,
            targetUuid = null,
            targetName = null,
            details = "토지 반환"
        )
        
        // 5. 향후 환불 시스템 확장을 위한 훅
        // TODO: 환불 아이템 계산 및 지급 로직 추가 예정
        handleLandReturnRefund(player, chunk, owner)
        
        debugManager.log("MyLand", "[RETURN] ${player.name}: 청크 (${chunk.x}, ${chunk.z}) 반환")
        return ClaimResult.SUCCESS
    } else {
        return ClaimResult.DATABASE_ERROR
    }
}

// 향후 환불 시스템을 위한 확장 포인트
private fun handleLandReturnRefund(player: Player, chunk: Chunk, owner: ChunkOwner) {
    // TODO: 환불 로직 구현 예정
    // 1. 클레이밍 비용 계산
    // 2. 환불율 적용 (예: 50%)
    // 3. 아이템 지급 또는 경제 시스템 연동
    
    // 현재는 메시지만 표시
    player.sendMessage("토지가 반환되었습니다. (환불 시스템은 향후 추가 예정)")
}
```

## 6. 연결성 체크 최적화 (4방향 전용)

### 6.1 현재 문제
- 현재 8방향(대각선 포함) 연결성 체크
- 요구사항: 4방향(상하좌우)만 연결로 인정

### 6.2 수정 코드
```kotlin
// AdvancedLandManager.kt의 isChunkConnectedToVillage 메서드 수정
fun isChunkConnectedToVillage(chunk: org.bukkit.Chunk, villageId: Int): Boolean {
    val worldName = chunk.world.name
    
    // 4방향만 체크 (대각선 제거)
    val directions = listOf(
        Pair(0, 1),   // 북쪽
        Pair(0, -1),  // 남쪽
        Pair(1, 0),   // 동쪽
        Pair(-1, 0)   // 서쪽
        // 대각선 방향 제거됨
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

// ConnectedChunks 업데이트 로직도 수정
private fun isConnected(chunk1: ChunkCoordinate, chunk2: ChunkCoordinate): Boolean {
    if (chunk1.worldName != chunk2.worldName) return false
    
    val dx = abs(chunk1.x - chunk2.x)
    val dz = abs(chunk1.z - chunk2.z)
    
    // 4방향 연결만 인정 (대각선 제거)
    return (dx == 1 && dz == 0) || (dx == 0 && dz == 1)
}
```

## 7. 명령어 개선

### 7.1 "땅 요약" → "땅 정보" 변경
```kotlin
// LandCommand.kt에서 명령어 매핑 수정
private fun handleCommand(player: Player, args: Array<out String>) {
    when (args.getOrNull(0)?.lowercase()) {
        "정보", "info" -> handleInfo(player, args)  // "요약" 제거
        "상태", "status" -> handleStatus(player, args)  // 새로운 명령어
        // ...
    }
}

private fun handleStatus(player: Player, args: Array<out String>) {
    // 기존 "요약" 기능을 "상태"로 이동
    val summary = landManager.getPlayerClaimSummary(player.uniqueId)
    player.sendMessage("§6=== 토지 상태 ===")
    player.sendMessage(summary)
}
```

### 7.2 "땅 비용" 명령어 추가
```kotlin
// AdvancedLandCommand.kt에 추가
private fun handleCost(player: Player, args: Array<out String>) {
    val usedFreeSlots = advancedLandManager.getPlayerUsedFreeSlots(player.uniqueId)
    val isVeteran = advancedLandManager.isVeteranPlayer(player.uniqueId)
    
    player.sendMessage("§6=== 클레이밍 비용 ===")
    
    if (usedFreeSlots < AdvancedLandManager.FREE_CLAIMS_COUNT) {
        val remainingFree = AdvancedLandManager.FREE_CLAIMS_COUNT - usedFreeSlots
        player.sendMessage("§a무료 슬롯: ${remainingFree}개 남음")
    } else {
        player.sendMessage("§c무료 슬롯: 모두 사용됨")
    }
    
    player.sendMessage("§7유료 클레이밍 비용:")
    player.sendMessage("§7- 철괴: §f${AdvancedLandManager.IRON_COST}개")
    player.sendMessage("§7- 다이아몬드: §f${AdvancedLandManager.DIAMOND_COST}개")
    player.sendMessage("§7- 네더라이트 주괴: §f${AdvancedLandManager.NETHERITE_COST}개")
    
    if (!isVeteran) {
        val currentClaims = advancedLandManager.getPlayerClaimCount(player.uniqueId)
        val maxClaims = AdvancedLandManager.NEWBIE_MAX_CLAIMS
        player.sendMessage("§e신규 플레이어 제한: ${currentClaims}/${maxClaims}개")
    } else {
        player.sendMessage("§b베테랑 플레이어: 무제한 클레이밍 가능")
    }
}
```

## 8. 구현 우선순위

### Phase 1 (높은 우선순위)
1. **연결성 체크 수정** - 간단하고 중요한 수정
2. **명령어 개선** - 사용자 경험 개선
3. **개인 토지 반환 시스템** - 기본 기능

### Phase 2 (중간 우선순위)
1. **마을 초대 수락/거절 시스템** - 사용자 편의성
2. **"땅 비용" 명령어** - 정보 제공

### Phase 3 (낮은 우선순위)
1. **마을 해체 시스템** - 복잡하지만 중요한 기능
2. **이장 양도 시스템** - 고급 기능

## 9. 테스팅 가이드

각 기능 구현 후 다음 테스트를 수행하세요:

### 연결성 체크 테스트
```
1. 4방향 연결된 청크들로 마을 생성
2. 대각선으로만 연결된 청크 확장 시도 (실패해야 함)
3. 기존 연결된 청크에서 4방향 확장 (성공해야 함)
```

### 초대 시스템 테스트
```
1. 이장이 아닌 플레이어가 초대 시도 (실패)
2. 정상적인 초대 전송
3. 초대 수락/거절
4. 만료된 초대 처리
5. 중복 초대 방지
```

### 마을 해체 테스트
```
1. 이장이 아닌 플레이어가 해체 시도 (실패)
2. 확인 없이 해체 시도 (실패)
3. 정상적인 해체 과정
4. 모든 토지가 개인 토지로 전환되는지 확인
5. 연결성이 유지되는지 확인
```

이 구현 가이드를 따라 미구현 기능들을 단계적으로 개발할 수 있습니다.
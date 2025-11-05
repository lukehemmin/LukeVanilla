# MyLand 시스템 API 레퍼런스

## 개요
MyLand 시스템의 모든 공개 API와 사용법을 상세히 설명합니다.

## 1. LandManager API

### 1.1 클레이밍 관련 API

#### `claimChunk(chunk: Chunk, player: Player, claimType: String = "GENERAL"): ClaimResult`
청크를 클레이밍합니다.

**매개변수:**
- `chunk`: 클레이밍할 청크
- `player`: 클레이밍하는 플레이어
- `claimType`: 클레이밍 타입 (기본값: "GENERAL")

**반환값:**
- `ClaimResult.SUCCESS`: 성공
- `ClaimResult.ALREADY_CLAIMED`: 이미 클레이밍됨
- `ClaimResult.NOT_IN_AREA`: 클레이밍 가능 구역 밖
- `ClaimResult.MAX_CLAIMS_REACHED`: 최대 클레이밍 수 도달
- `ClaimResult.DATABASE_ERROR`: 데이터베이스 오류

**사용 예시:**
```kotlin
val chunk = player.location.chunk
val result = landManager.claimChunk(chunk, player)
when (result) {
    ClaimResult.SUCCESS -> player.sendMessage("성공적으로 클레이밍했습니다!")
    ClaimResult.ALREADY_CLAIMED -> player.sendMessage("이미 클레이밍된 청크입니다.")
    else -> player.sendMessage("클레이밍에 실패했습니다.")
}
```

#### `unclaimChunk(chunk: Chunk, player: Player): ClaimResult`
청크 클레이밍을 해제합니다.

**매개변수:**
- `chunk`: 해제할 청크
- `player`: 해제하는 플레이어

**반환값:** `ClaimResult` enum 값

**사용 예시:**
```kotlin
val result = landManager.unclaimChunk(chunk, player)
if (result == ClaimResult.SUCCESS) {
    player.sendMessage("클레이밍이 해제되었습니다.")
}
```

#### `isChunkClaimed(chunk: Chunk): Boolean`
청크가 클레이밍되었는지 확인합니다.

**사용 예시:**
```kotlin
if (landManager.isChunkClaimed(chunk)) {
    player.sendMessage("이 청크는 이미 클레이밍되었습니다.")
}
```

#### `getChunkOwner(chunk: Chunk): ChunkOwner?`
청크의 소유자 정보를 반환합니다.

**반환값:** `ChunkOwner` 객체 또는 null

**사용 예시:**
```kotlin
val owner = landManager.getChunkOwner(chunk)
if (owner != null) {
    player.sendMessage("소유자: ${owner.ownerName}")
} else {
    player.sendMessage("클레이밍되지 않은 청크입니다.")
}
```

### 1.2 멤버 관리 API

#### `addMember(chunk: Chunk, member: Player, actor: Player): MemberResult`
청크에 멤버를 추가합니다.

**매개변수:**
- `chunk`: 대상 청크
- `member`: 추가할 멤버
- `actor`: 멤버를 추가하는 플레이어

**반환값:**
- `MemberResult.SUCCESS`: 성공
- `MemberResult.NOT_OWNER`: 소유자가 아님
- `MemberResult.CHUNK_NOT_CLAIMED`: 클레이밍되지 않은 청크
- `MemberResult.ALREADY_MEMBER`: 이미 멤버임
- `MemberResult.CANNOT_ADD_OWNER`: 소유자는 멤버로 추가할 수 없음
- `MemberResult.DATABASE_ERROR`: 데이터베이스 오류

**사용 예시:**
```kotlin
val result = landManager.addMember(chunk, memberPlayer, ownerPlayer)
when (result) {
    MemberResult.SUCCESS -> ownerPlayer.sendMessage("${memberPlayer.name}님을 친구로 추가했습니다.")
    MemberResult.NOT_OWNER -> ownerPlayer.sendMessage("이 땅의 소유자만 친구를 추가할 수 있습니다.")
    MemberResult.ALREADY_MEMBER -> ownerPlayer.sendMessage("이미 친구로 등록된 플레이어입니다.")
    else -> ownerPlayer.sendMessage("친구 추가에 실패했습니다.")
}
```

#### `removeMember(chunk: Chunk, member: Player, actor: Player): MemberResult`
청크에서 멤버를 제거합니다.

**사용 예시:**
```kotlin
val result = landManager.removeMember(chunk, memberPlayer, ownerPlayer)
if (result == MemberResult.SUCCESS) {
    ownerPlayer.sendMessage("${memberPlayer.name}님을 친구에서 제거했습니다.")
}
```

#### `isMember(chunk: Chunk, player: Player): Boolean`
플레이어가 해당 청크의 멤버인지 확인합니다.

**사용 예시:**
```kotlin
if (landManager.isMember(chunk, player)) {
    player.sendMessage("이 땅에서 건축할 수 있습니다.")
}
```

#### `getMembers(chunk: Chunk): List<UUID>`
청크의 모든 멤버 UUID 목록을 반환합니다.

**사용 예시:**
```kotlin
val memberUuids = landManager.getMembers(chunk)
val memberNames = memberUuids.mapNotNull { uuid ->
    Bukkit.getOfflinePlayer(uuid).name
}
player.sendMessage("멤버: ${memberNames.joinToString(", ")}")
```

#### `getMemberNames(chunk: Chunk): List<String>`
청크의 모든 멤버 이름 목록을 반환합니다.

**사용 예시:**
```kotlin
val memberNames = landManager.getMemberNames(chunk)
player.sendMessage("친구 목록: ${memberNames.joinToString(", ")}")
```

### 1.3 정보 조회 API

#### `getPlayerClaims(playerUuid: UUID): List<ChunkLocation>`
플레이어가 소유한 모든 청크 목록을 반환합니다.

**사용 예시:**
```kotlin
val claims = landManager.getPlayerClaims(player.uniqueId)
player.sendMessage("소유한 땅: ${claims.size}개")
claims.forEach { location ->
    player.sendMessage("- (${location.chunkX}, ${location.chunkZ}) in ${location.worldName}")
}
```

#### `getChunkInfo(chunk: Chunk): ChunkInfo?`
청크의 상세 정보를 반환합니다.

**사용 예시:**
```kotlin
val info = landManager.getChunkInfo(chunk)
if (info != null) {
    player.sendMessage("=== 땅 정보 ===")
    player.sendMessage("소유자: ${info.owner.ownerName}")
    player.sendMessage("멤버: ${info.members.joinToString(", ")}")
    info.farmVillageNumber?.let { number ->
        player.sendMessage("농사마을: ${number}번")
    }
}
```

#### `getClaimHistory(chunk: Chunk): List<HistoryEntry>`
청크의 소유권 변경 이력을 반환합니다.

**사용 예시:**
```kotlin
val history = landManager.getClaimHistory(chunk)
player.sendMessage("=== 이력 ===")
history.forEach { entry ->
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(entry.timestamp))
    player.sendMessage("$date - ${entry.actionType}: ${entry.actorName}")
}
```

### 1.4 설정 관리 API

#### `isChunkInClaimableArea(chunk: Chunk): Boolean`
청크가 클레이밍 가능 구역에 있는지 확인합니다.

**사용 예시:**
```kotlin
if (!landManager.isChunkInClaimableArea(chunk)) {
    player.sendMessage("이 구역에서는 땅을 클레이밍할 수 없습니다.")
}
```

#### `loadConfig()`
설정 파일을 다시 로드합니다.

**사용 예시:**
```kotlin
landManager.loadConfig()
player.sendMessage("땅 시스템 설정이 다시 로드되었습니다.")
```

#### `reloadConfig()`
설정을 다시 로드하고 캐시를 업데이트합니다.

## 2. LandData API

### 2.1 클레이밍 데이터 API

#### `saveClaim(chunk: Chunk, ownerUuid: UUID, ownerName: String, claimType: String): Boolean`
클레이밍 정보를 데이터베이스에 저장합니다.

#### `removeClaim(chunk: Chunk): Boolean`
클레이밍 정보를 데이터베이스에서 제거합니다.

#### `getChunkOwner(chunk: Chunk): ChunkOwner?`
데이터베이스에서 청크 소유자 정보를 조회합니다.

#### `getPlayerClaims(playerUuid: UUID): List<ChunkLocation>`
플레이어의 모든 클레이밍 정보를 데이터베이스에서 조회합니다.

### 2.2 멤버 데이터 API

#### `addMember(chunk: Chunk, memberUuid: UUID, memberName: String): Boolean`
멤버 정보를 데이터베이스에 추가합니다.

#### `removeMember(chunk: Chunk, memberUuid: UUID): Boolean`
멤버 정보를 데이터베이스에서 제거합니다.

#### `getMembers(chunk: Chunk): List<UUID>`
청크의 모든 멤버를 데이터베이스에서 조회합니다.

#### `deleteAllMembers(worldName: String, chunkX: Int, chunkZ: Int): Boolean`
특정 청크의 모든 멤버를 삭제합니다. (주로 FarmVillage 연동 시 사용)

### 2.3 히스토리 데이터 API

#### `addHistory(chunk: Chunk, actionType: String, actorUuid: UUID, actorName: String, targetUuid: UUID?, targetName: String?, details: String?): Boolean`
이력을 데이터베이스에 추가합니다.

**매개변수:**
- `actionType`: "CLAIM", "UNCLAIM", "ADD_MEMBER", "REMOVE_MEMBER" 등
- `targetUuid/targetName`: 대상이 있는 경우 (멤버 추가/제거 시)
- `details`: 추가 상세 정보

#### `getHistory(chunk: Chunk): List<HistoryEntry>`
청크의 모든 이력을 조회합니다.

## 3. 데이터 모델 API

### 3.1 ChunkOwner
```kotlin
data class ChunkOwner(
    val ownerUuid: UUID,     // 소유자 UUID
    val ownerName: String,   // 소유자 이름
    val claimType: String,   // 클레이밍 타입
    val claimedAt: Long      // 클레이밍 시각 (timestamp)
)
```

### 3.2 ChunkLocation
```kotlin
data class ChunkLocation(
    val worldName: String,   // 월드 이름
    val chunkX: Int,        // 청크 X 좌표
    val chunkZ: Int         // 청크 Z 좌표
)
```

### 3.3 ChunkInfo
```kotlin
data class ChunkInfo(
    val location: ChunkLocation,        // 청크 위치
    val owner: ChunkOwner,             // 소유자 정보
    val members: List<String>,         // 멤버 이름 목록
    val farmVillageNumber: Int? = null // 농사마을 번호 (FarmVillage 연동)
)
```

### 3.4 HistoryEntry
```kotlin
data class HistoryEntry(
    val actionType: String,    // 액션 타입
    val actorUuid: UUID,      // 행위자 UUID
    val actorName: String,    // 행위자 이름
    val targetUuid: UUID?,    // 대상 UUID (있는 경우)
    val targetName: String?,  // 대상 이름 (있는 경우)
    val details: String?,     // 상세 정보
    val timestamp: Long       // 발생 시각
)
```

## 4. 이벤트 API

### 4.1 커스텀 이벤트

#### `LandClaimEvent`
땅이 클레이밍될 때 발생하는 이벤트

```kotlin
class LandClaimEvent(
    val chunk: Chunk,
    val player: Player,
    val claimType: String
) : Event(), Cancellable {
    // 이벤트를 취소할 수 있음
}
```

**사용 예시:**
```kotlin
@EventHandler
fun onLandClaim(event: LandClaimEvent) {
    if (someCondition) {
        event.isCancelled = true
        event.player.sendMessage("클레이밍이 취소되었습니다.")
    }
}
```

#### `LandUnclaimEvent`
땅 클레이밍이 해제될 때 발생하는 이벤트

```kotlin
class LandUnclaimEvent(
    val chunk: Chunk,
    val player: Player,
    val reason: String
) : Event(), Cancellable
```

#### `LandMemberAddEvent`
멤버가 추가될 때 발생하는 이벤트

```kotlin
class LandMemberAddEvent(
    val chunk: Chunk,
    val member: Player,
    val actor: Player
) : Event(), Cancellable
```

#### `LandMemberRemoveEvent`
멤버가 제거될 때 발생하는 이벤트

```kotlin
class LandMemberRemoveEvent(
    val chunk: Chunk,
    val member: Player,
    val actor: Player
) : Event(), Cancellable
```

## 5. 유틸리티 API

### 5.1 ChunkUtils
```kotlin
object ChunkUtils {
    fun getChunkKey(chunk: Chunk): String
    fun getChunkFromKey(key: String): Chunk?
    fun getAdjacentChunks(chunk: Chunk): List<Chunk>
    fun isChunkLoaded(chunk: Chunk): Boolean
}
```

### 5.2 LandPermissionUtils
```kotlin
object LandPermissionUtils {
    fun hasLandPermission(player: Player, permission: String): Boolean
    fun hasAdminPermission(player: Player): Boolean
    fun canModifyLand(player: Player, chunk: Chunk): Boolean
}
```

## 6. 통합 API (다른 시스템과의 연동)

### 6.1 FarmVillage 연동
```kotlin
// LandCommand에서 농사마을 정보 표시
fun setFarmVillageManager(farmVillageManager: FarmVillageManager) {
    this.farmVillageManager = farmVillageManager
}

// 농사마을 번호 조회
private fun getFarmVillageNumber(chunk: Chunk): Int? {
    return farmVillageManager?.getChunkVillageNumber(chunk)
}
```

### 6.2 AdvancedLandClaiming 연동
```kotlin
// AdvancedLandClaiming과의 연동
fun setAdvancedLandManager(advancedLandManager: AdvancedLandManager) {
    this.advancedLandManager = advancedLandManager
}

// 고급 토지 정보 확인
private fun getAdvancedLandInfo(chunk: Chunk): AdvancedClaimInfo? {
    return advancedLandManager?.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
}
```

## 7. 에러 처리 및 예외

### 7.1 일반적인 예외 상황
```kotlin
try {
    val result = landManager.claimChunk(chunk, player)
    // 결과 처리
} catch (e: DatabaseException) {
    logger.severe("데이터베이스 오류: ${e.message}")
    player.sendMessage("시스템 오류가 발생했습니다. 관리자에게 문의하세요.")
} catch (e: ConfigurationException) {
    logger.severe("설정 오류: ${e.message}")
} catch (e: Exception) {
    logger.severe("예상치 못한 오류: ${e.message}")
    e.printStackTrace()
}
```

### 7.2 권한 확인 패턴
```kotlin
// 권한 확인 후 작업 수행 패턴
if (!player.hasPermission("myland.use")) {
    player.sendMessage("권한이 없습니다.")
    return
}

val chunk = player.location.chunk
val owner = landManager.getChunkOwner(chunk)

when {
    owner == null -> {
        // 클레이밍되지 않은 땅
    }
    owner.ownerUuid == player.uniqueId -> {
        // 소유자
    }
    landManager.isMember(chunk, player) -> {
        // 멤버
    }
    else -> {
        // 권한 없음
        player.sendMessage("이 땅에 대한 권한이 없습니다.")
        return
    }
}
```

## 8. 성능 최적화 가이드

### 8.1 캐싱 활용
```kotlin
// 자주 조회되는 데이터는 캐싱
private val chunkOwnerCache = mutableMapOf<String, ChunkOwner?>()

fun getChunkOwner(chunk: Chunk): ChunkOwner? {
    val key = getChunkKey(chunk)
    return chunkOwnerCache.getOrPut(key) {
        landData.getChunkOwner(chunk)
    }
}
```

### 8.2 배치 처리
```kotlin
// 여러 청크를 한 번에 처리
fun getMultipleChunkOwners(chunks: List<Chunk>): Map<Chunk, ChunkOwner?> {
    return landData.getMultipleChunkOwners(chunks)
}
```

## 9. 테스팅 가이드

### 9.1 단위 테스트 예시
```kotlin
@Test
fun testClaimChunk() {
    val chunk = mockChunk(0, 0, "world")
    val player = mockPlayer("TestPlayer")
    
    val result = landManager.claimChunk(chunk, player)
    
    assertEquals(ClaimResult.SUCCESS, result)
    assertTrue(landManager.isChunkClaimed(chunk))
    assertEquals(player.uniqueId, landManager.getChunkOwner(chunk)?.ownerUuid)
}
```

### 9.2 통합 테스트 예시
```kotlin
@Test
fun testMemberManagement() {
    val chunk = mockChunk(0, 0, "world")
    val owner = mockPlayer("Owner")
    val member = mockPlayer("Member")
    
    // 먼저 클레이밍
    landManager.claimChunk(chunk, owner)
    
    // 멤버 추가
    val addResult = landManager.addMember(chunk, member, owner)
    assertEquals(MemberResult.SUCCESS, addResult)
    assertTrue(landManager.isMember(chunk, member))
    
    // 멤버 제거
    val removeResult = landManager.removeMember(chunk, member, owner)
    assertEquals(MemberResult.SUCCESS, removeResult)
    assertFalse(landManager.isMember(chunk, member))
}
```

이 API 레퍼런스를 통해 MyLand 시스템의 모든 기능을 프로그래밍적으로 활용할 수 있습니다.
# MyLand 시스템 개발 문서

## 1. 시스템 개요

MyLand는 개인 토지 소유 및 관리 시스템으로, 플레이어가 청크 단위로 토지를 클레임하고 친구들과 공유할 수 있는 기본적인 땅 시스템입니다.

### 주요 기능
- 청크 단위 토지 클레이밍
- 친구 시스템을 통한 토지 공유
- 토지 보호 (블록 파괴/설치/상호작용 제한)
- 클레이밍 가능 구역 설정
- 토지 정보 및 이력 관리

## 2. 아키텍처 설계

```
MyLand 시스템 구조:
┌─────────────────────────────────────────┐
│              Main.kt                    │
│         (시스템 초기화)                   │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│          PrivateLandSystem              │
│     (시스템 통합 및 라이프사이클 관리)     │
└─────────────────┬───────────────────────┘
                  │
        ┌─────────┼─────────┐
        │         │         │
        ▼         ▼         ▼
┌─────────┐ ┌─────────┐ ┌──────────────┐
│LandManager│ │LandCommand│ │LandProtection│
│   (핵심   │ │ (명령어  │ │   Listener   │
│  비즈니스 │ │  처리)   │ │  (이벤트처리) │
│   로직)   │ │         │ │              │
└─────┬───┘ └─────────┘ └──────────────┘
      │
      ▼
┌─────────┐
│ LandData│
│(데이터   │
│ 접근)   │
└─────────┘
```

## 3. 데이터베이스 스키마

### 3.1 land_claims 테이블
```sql
CREATE TABLE land_claims (
    id INT AUTO_INCREMENT PRIMARY KEY,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    owner_uuid VARCHAR(36) NOT NULL,
    owner_name VARCHAR(50) NOT NULL,
    claim_type VARCHAR(20) DEFAULT 'GENERAL',
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_chunk (world_name, chunk_x, chunk_z)
);
```

### 3.2 land_members 테이블
```sql
CREATE TABLE land_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    member_name VARCHAR(50) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_member (world_name, chunk_x, chunk_z, member_uuid)
);
```

### 3.3 land_history 테이블
```sql
CREATE TABLE land_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    actor_uuid VARCHAR(36),
    actor_name VARCHAR(50),
    target_uuid VARCHAR(36),
    target_name VARCHAR(50),
    details TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 4. 주요 클래스 및 메서드

### 4.1 PrivateLandSystem 클래스
시스템의 진입점이며 라이프사이클을 관리합니다.

```kotlin
class PrivateLandSystem(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager
) {
    fun enable()                    // 시스템 활성화
    fun disable()                   // 시스템 비활성화
    fun getLandManager(): LandManager
    fun setFarmVillageManager(farmVillageManager)
    fun setAdvancedLandManager(advancedLandManager)
    fun getLandCommand(): LandCommand
}
```

### 4.2 LandManager 클래스
핵심 비즈니스 로직을 담당합니다.

```kotlin
class LandManager(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager
) {
    // === 설정 관리 ===
    fun loadConfig()                                    // 설정 로드
    fun reloadConfig()                                  // 설정 리로드
    
    // === 클레이밍 관리 ===
    fun claimChunk(chunk: Chunk, player: Player, claimType: String = "GENERAL"): ClaimResult
    fun unclaimChunk(chunk: Chunk, player: Player): ClaimResult
    fun isChunkClaimed(chunk: Chunk): Boolean
    fun getChunkOwner(chunk: Chunk): ChunkOwner?
    fun isChunkInClaimableArea(chunk: Chunk): Boolean
    
    // === 멤버 관리 ===
    fun addMember(chunk: Chunk, member: Player, actor: Player): MemberResult
    fun removeMember(chunk: Chunk, member: Player, actor: Player): MemberResult
    fun getMembers(chunk: Chunk): List<UUID>
    fun getMemberNames(chunk: Chunk): List<String>
    fun isMember(chunk: Chunk, player: Player): Boolean
    
    // === 정보 조회 ===
    fun getPlayerClaims(playerUuid: UUID): List<ChunkLocation>
    fun getChunkInfo(chunk: Chunk): ChunkInfo?
    fun getClaimHistory(chunk: Chunk): List<HistoryEntry>
    
    // === 데이터 관리 ===
    fun loadClaimsFromDatabase()
    fun saveClaimToDatabase(chunk: Chunk, owner: Player, claimType: String): Boolean
    fun removeClaimFromDatabase(chunk: Chunk): Boolean
}
```

### 4.3 LandCommand 클래스
명령어 처리를 담당합니다.

```kotlin
class LandCommand(private val landManager: LandManager) : CommandExecutor, TabCompleter {
    // === 명령어 처리 ===
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>?
    
    // === 하위 명령어들 ===
    private fun handleClaim(player: Player, args: Array<out String>)        // 클레임
    private fun handleUnclaim(player: Player, args: Array<out String>)      // 언클레임
    private fun handleInfo(player: Player, args: Array<out String>)         // 정보
    private fun handleList(player: Player, args: Array<out String>)         // 목록
    private fun handleAddFriend(player: Player, args: Array<out String>)    // 친구추가
    private fun handleRemoveFriend(player: Player, args: Array<out String>) // 친구삭제
    private fun handleFriendList(player: Player, args: Array<out String>)   // 친구목록
    private fun handleHistory(player: Player, args: Array<out String>)      // 이력
    
    // === 시스템 연동 ===
    fun setFarmVillageManager(farmVillageManager)
    fun setAdvancedLandManager(advancedLandManager)
}
```

### 4.4 LandProtectionListener 클래스
이벤트 기반 토지 보호를 처리합니다.

```kotlin
class LandProtectionListener(private val landManager: LandManager) : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent)           // 블록 파괴 보호
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent)           // 블록 설치 보호
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent)   // 상호작용 보호
    
    private fun canPlayerModify(chunk: Chunk, player: Player): Boolean  // 권한 확인
}
```

### 4.5 LandData 클래스
데이터베이스 접근을 담당합니다.

```kotlin
class LandData(private val database: Database) {
    // === 클레임 데이터 ===
    fun saveClaim(chunk: Chunk, ownerUuid: UUID, ownerName: String, claimType: String): Boolean
    fun removeClaim(chunk: Chunk): Boolean
    fun getChunkOwner(chunk: Chunk): ChunkOwner?
    fun getPlayerClaims(playerUuid: UUID): List<ChunkLocation>
    
    // === 멤버 데이터 ===
    fun addMember(chunk: Chunk, memberUuid: UUID, memberName: String): Boolean
    fun removeMember(chunk: Chunk, memberUuid: UUID): Boolean
    fun getMembers(chunk: Chunk): List<UUID>
    fun deleteAllMembers(worldName: String, chunkX: Int, chunkZ: Int): Boolean
    
    // === 히스토리 데이터 ===
    fun addHistory(chunk: Chunk, actionType: String, actorUuid: UUID, actorName: String, 
                  targetUuid: UUID?, targetName: String?, details: String?): Boolean
    fun getHistory(chunk: Chunk): List<HistoryEntry>
}
```

## 5. 주요 데이터 모델

### 5.1 ChunkOwner
```kotlin
data class ChunkOwner(
    val ownerUuid: UUID,
    val ownerName: String,
    val claimType: String,
    val claimedAt: Long
)
```

### 5.2 ChunkLocation
```kotlin
data class ChunkLocation(
    val worldName: String,
    val chunkX: Int,
    val chunkZ: Int
)
```

### 5.3 ChunkInfo
```kotlin
data class ChunkInfo(
    val location: ChunkLocation,
    val owner: ChunkOwner,
    val members: List<String>,
    val farmVillageNumber: Int? = null  // FarmVillage 연동
)
```

### 5.4 ClaimResult (Enum)
```kotlin
enum class ClaimResult {
    SUCCESS,
    ALREADY_CLAIMED,
    NOT_IN_AREA,
    MAX_CLAIMS_REACHED,
    DATABASE_ERROR
}
```

### 5.5 MemberResult (Enum)
```kotlin
enum class MemberResult {
    SUCCESS,
    NOT_OWNER,
    CHUNK_NOT_CLAIMED,
    ALREADY_MEMBER,
    NOT_MEMBER,
    CANNOT_ADD_OWNER,
    DATABASE_ERROR
}
```

## 6. 설정 파일

### 6.1 config.yml 설정
```yaml
land:
  # 클레이밍 가능 구역 설정
  claimable-area:
    enabled: true
    min-x: -5000
    max-x: 5000
    min-z: -5000
    max-z: 5000
    worlds:
      - "world"
  
  # 최대 클레임 수 (향후 확장 가능)
  max-claims: 50
  
  # 보호 설정
  protection:
    block-break: true
    block-place: true
    interact: true
```

## 7. 사용 예제

### 7.1 시스템 초기화 (Main.kt)
```kotlin
// Main.kt의 onEnable()에서
if (serviceType == "Vanilla") {
    privateLandSystem = PrivateLandSystem(this, database, debugManager)
    privateLandSystem?.enable()
}
```

### 7.2 다른 시스템과의 연동
```kotlin
// FarmVillage 시스템과 연동
farmVillageSystem?.let { farmVillage ->
    privateLand.setFarmVillageManager(farmVillage.getFarmVillageManager())
}

// AdvancedLandClaiming 시스템과 연동
advancedLandSystem?.let { advancedLand ->
    val advancedLandManager = advancedLand.getAdvancedLandManager()
    privateLand.setAdvancedLandManager(advancedLandManager)
    advancedLandManager.setLandManager(privateLand.getLandManager())
}
```

### 7.3 API 사용 예제
```kotlin
// 다른 플러그인에서 MyLand API 사용
val landManager = privateLandSystem.getLandManager()

// 청크 소유자 확인
val chunk = player.location.chunk
val owner = landManager.getChunkOwner(chunk)
if (owner != null) {
    player.sendMessage("이 땅의 소유자: ${owner.ownerName}")
}

// 친구 권한 확인
if (landManager.isMember(chunk, player)) {
    player.sendMessage("이 땅에서 건축할 수 있습니다.")
}
```

## 8. 명령어 사용법

### 8.1 기본 명령어
- `/땅 클레임` - 현재 위치 청크를 클레임
- `/땅 포기` - 현재 위치 청크를 포기
- `/땅 정보` - 현재 위치 청크 정보 표시
- `/땅 목록` - 내가 소유한 땅 목록 표시

### 8.2 친구 관리 명령어
- `/땅 친구추가 <플레이어>` - 친구 추가
- `/땅 친구삭제 <플레이어>` - 친구 삭제
- `/땅 친구목록` - 친구 목록 표시

### 8.3 정보 조회 명령어
- `/땅 이력` - 현재 청크의 소유권 변경 이력

## 9. 트러블슈팅

### 9.1 일반적인 문제들

**Q: 클레이밍이 안 됩니다.**
A: 클레이밍 가능 구역 설정을 확인하세요. config.yml의 claimable-area 설정을 검토하세요.

**Q: 친구가 블록을 부술 수 없습니다.**
A: AdvancedLandClaiming 시스템과의 연동을 확인하세요. Main.kt에서 양방향 연결이 제대로 설정되었는지 확인하세요.

**Q: 데이터베이스 오류가 발생합니다.**
A: DatabaseInitializer에서 테이블이 제대로 생성되었는지 확인하세요.

### 9.2 디버깅

```kotlin
// 디버그 모드 활성화
debugManager.log("MyLand", "디버그 메시지")

// 클레임 캐시 상태 확인
landManager.loadClaimsFromDatabase()  // 캐시 리로드
```

## 10. 확장 가능성

### 10.1 향후 추가 가능한 기능
- 토지 반환 시 환불 시스템
- 토지 비용 시스템
- 토지 임대 시스템
- 토지 거래 시스템

### 10.2 다른 시스템과의 연동
- FarmVillage: 농사마을 표시 기능
- AdvancedLandClaiming: 개인 토지 → 마을 전환
- Economy: 토지 구매/판매 시스템

## 11. 개발 가이드라인

### 11.1 코드 스타일
- Kotlin 표준 코딩 컨벤션 준수
- 명확한 메서드명과 변수명 사용
- 적절한 주석 작성

### 11.2 성능 고려사항
- 데이터베이스 쿼리 최소화
- 캐시 시스템 활용
- 이벤트 리스너 효율성

### 11.3 보안 고려사항
- 플레이어 권한 검증
- SQL 인젝션 방지
- 데이터 무결성 보장
# FarmVillage 시스템 개발 문서

## 1. 시스템 개요

FarmVillage는 관리자가 지정하고 운영하는 농사마을 시스템으로, MyLand 시스템을 기반으로 구축되어 플레이어들에게 공동 농업 공간을 제공합니다.

### 주요 기능
- 관리자 지정 농사마을 생성 및 관리
- 마을 번호 시스템으로 체계적 관리
- MyLand 시스템과 연동된 토지 보호
- 마을별 멤버 관리 시스템
- LuckPerms와 연동된 권한 관리

### 시스템 특징
- **관리자 중심**: 마을 생성/삭제는 관리자만 가능
- **MyLand 의존**: 기본 토지 시스템 위에 구축
- **번호 기반**: 각 마을은 고유 번호로 식별
- **권한 통합**: LuckPerms 그룹과 연동

## 2. 아키텍처 설계

```
FarmVillage 시스템 구조:
┌─────────────────────────────────────────┐
│              Main.kt                    │
│       (시스템 초기화 - 야생서버만)        │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│          FarmVillageSystem              │
│     (시스템 통합 및 라이프사이클 관리)     │
│     + LuckPerms 의존성 주입             │
└─────────────────┬───────────────────────┘
                  │
        ┌─────────┼─────────┐
        │         │         │
        ▼         ▼         ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│FarmVillage   │ │FarmVillage  │ │FarmVillage  │
│Manager       │ │Command      │ │Data         │
│(비즈니스로직) │ │(명령어처리)  │ │(데이터접근) │
└──────┬───────┘ └─────────────┘ └─────────────┘
       │
       ▼
┌──────────────┐
│ MyLand       │
│ LandManager  │
│ (토지 관리)  │
└──────────────┘
```

## 3. 데이터베이스 스키마

### 3.1 farm_villages 테이블
```sql
CREATE TABLE farm_villages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_number INT NOT NULL UNIQUE,
    village_name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_uuid VARCHAR(36),
    owner_name VARCHAR(50),
    created_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);
```

### 3.2 farm_village_members 테이블
```sql
CREATE TABLE farm_village_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_number INT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    member_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_number) REFERENCES farm_villages(village_number),
    UNIQUE KEY unique_member (village_number, member_uuid)
);
```

### 3.3 farm_village_lands 테이블
```sql
CREATE TABLE farm_village_lands (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_number INT NOT NULL,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_number) REFERENCES farm_villages(village_number),
    UNIQUE KEY unique_land (world_name, chunk_x, chunk_z)
);
```

## 4. 주요 클래스 및 메서드

### 4.1 FarmVillageSystem 클래스
시스템의 진입점이며 의존성을 관리합니다.

```kotlin
class FarmVillageSystem(
    private val plugin: Main,
    private val database: Database,
    private val privateLandSystem: PrivateLandSystem,
    private val debugManager: DebugManager,
    private val luckPerms: LuckPerms?
) {
    fun enable()                                    // 시스템 활성화
    fun disable()                                   // 시스템 비활성화
    fun getFarmVillageManager(): FarmVillageManager // 매니저 반환
}
```

### 4.2 FarmVillageManager 클래스
핵심 비즈니스 로직을 담당합니다.

```kotlin
class FarmVillageManager(
    private val farmVillageData: FarmVillageData,
    private val landManager: LandManager,
    private val debugManager: DebugManager,
    private val luckPerms: LuckPerms?
) {
    // === 마을 관리 ===
    fun createVillage(villageNumber: Int, villageName: String, creator: CommandSender): Boolean
    fun deleteVillage(villageNumber: Int, actor: CommandSender): Boolean
    fun getVillageInfo(villageNumber: Int): FarmVillage?
    fun getAllVillages(): List<FarmVillage>
    fun isVillageExists(villageNumber: Int): Boolean
    
    // === 멤버 관리 ===
    fun addMember(villageNumber: Int, playerUuid: UUID, playerName: String, actor: CommandSender): Boolean
    fun removeMember(villageNumber: Int, playerUuid: UUID, actor: CommandSender): Boolean
    fun getVillageMembers(villageNumber: Int): List<FarmVillageMember>
    fun isMember(villageNumber: Int, playerUuid: UUID): Boolean
    
    // === 토지 관리 ===
    fun assignLandToVillage(villageNumber: Int, chunk: Chunk, actor: CommandSender): Boolean
    fun removeLandFromVillage(chunk: Chunk, actor: CommandSender): Boolean
    fun getVillageLands(villageNumber: Int): List<ChunkLocation>
    fun getChunkVillageNumber(chunk: Chunk): Int?
    
    // === 권한 관리 (LuckPerms 연동) ===
    fun syncMemberPermissions(villageNumber: Int, playerUuid: UUID): Boolean
    fun removeMemberPermissions(villageNumber: Int, playerUuid: UUID): Boolean
    fun updateAllVillagePermissions(villageNumber: Int): Boolean
}
```

### 4.3 FarmVillageCommand 클래스
관리자 명령어를 처리합니다.

```kotlin
class FarmVillageCommand(
    private val farmVillageManager: FarmVillageManager
) : CommandExecutor, TabCompleter {
    // === 명령어 처리 ===
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>?
    
    // === 하위 명령어들 (관리자 전용) ===
    private fun handleCreate(sender: CommandSender, args: Array<out String>)     // 마을 생성
    private fun handleDelete(sender: CommandSender, args: Array<out String>)     // 마을 삭제
    private fun handleInfo(sender: CommandSender, args: Array<out String>)       // 마을 정보
    private fun handleList(sender: CommandSender, args: Array<out String>)       // 마을 목록
    private fun handleAddMember(sender: CommandSender, args: Array<out String>)  // 멤버 추가
    private fun handleRemoveMember(sender: CommandSender, args: Array<out String>) // 멤버 제거
    private fun handleAssignLand(sender: CommandSender, args: Array<out String>) // 토지 할당
    private fun handleRemoveLand(sender: CommandSender, args: Array<out String>) // 토지 해제
    private fun handleSyncPerms(sender: CommandSender, args: Array<out String>)  // 권한 동기화
}
```

### 4.4 FarmVillageData 클래스
데이터베이스 접근을 담당합니다.

```kotlin
class FarmVillageData(private val database: Database) {
    // === 마을 데이터 ===
    fun createVillage(villageNumber: Int, villageName: String, creatorUuid: String): Boolean
    fun deleteVillage(villageNumber: Int): Boolean
    fun getVillage(villageNumber: Int): FarmVillage?
    fun getAllVillages(): List<FarmVillage>
    fun updateVillage(village: FarmVillage): Boolean
    
    // === 멤버 데이터 ===
    fun addMember(villageNumber: Int, memberUuid: UUID, memberName: String, role: String): Boolean
    fun removeMember(villageNumber: Int, memberUuid: UUID): Boolean
    fun getVillageMembers(villageNumber: Int): List<FarmVillageMember>
    fun isMember(villageNumber: Int, memberUuid: UUID): Boolean
    
    // === 토지 데이터 ===
    fun assignLand(villageNumber: Int, worldName: String, chunkX: Int, chunkZ: Int): Boolean
    fun removeLand(worldName: String, chunkX: Int, chunkZ: Int): Boolean
    fun getVillageLands(villageNumber: Int): List<ChunkLocation>
    fun getChunkVillageNumber(worldName: String, chunkX: Int, chunkZ: Int): Int?
}
```

## 5. 주요 데이터 모델

### 5.1 FarmVillage
```kotlin
data class FarmVillage(
    val villageNumber: Int,
    val villageName: String,
    val description: String?,
    val ownerUuid: UUID?,
    val ownerName: String?,
    val createdBy: UUID,
    val createdAt: Long,
    val isActive: Boolean = true
)
```

### 5.2 FarmVillageMember
```kotlin
data class FarmVillageMember(
    val villageNumber: Int,
    val memberUuid: UUID,
    val memberName: String,
    val role: String,
    val joinedAt: Long
)
```

### 5.3 VillageRole (Enum)
```kotlin
enum class VillageRole(val displayName: String) {
    OWNER("소유자"),
    MANAGER("관리자"),
    MEMBER("멤버")
}
```

## 6. LuckPerms 연동

### 6.1 권한 그룹 구조
```
농사마을 권한 시스템:
- farmvillage.admin       : 전체 관리자 권한
- farmvillage.member.{번호} : 특정 마을 멤버 권한

예시:
- farmvillage.member.1    : 1번 마을 멤버
- farmvillage.member.2    : 2번 마을 멤버
```

### 6.2 권한 동기화 로직
```kotlin
// 멤버 추가 시 권한 부여
fun syncMemberPermissions(villageNumber: Int, playerUuid: UUID): Boolean {
    return luckPerms?.let { api ->
        val user = api.userManager.getUser(playerUuid)
        user?.let {
            val groupName = "farmvillage.member.$villageNumber"
            val group = api.groupManager.getGroup(groupName)
            if (group != null) {
                user.data().add(InheritanceNode.builder(group).build())
                api.userManager.saveUser(user)
                true
            } else false
        }
    } ?: false
}
```

## 7. MyLand 시스템과의 연동

### 7.1 토지 할당 과정
```kotlin
fun assignLandToVillage(villageNumber: Int, chunk: Chunk, actor: CommandSender): Boolean {
    // 1. 해당 청크가 이미 클레이밍되어 있는지 확인
    if (!landManager.isChunkClaimed(chunk)) {
        return false // 클레이밍되지 않은 땅은 할당 불가
    }
    
    // 2. 농사마을 토지로 지정
    if (farmVillageData.assignLand(villageNumber, chunk.world.name, chunk.x, chunk.z)) {
        // 3. 로그 기록
        debugManager.log("FarmVillage", "[ASSIGN] Village $villageNumber: chunk (${chunk.x}, ${chunk.z})")
        return true
    }
    
    return false
}
```

### 7.2 LandCommand에서의 표시
```kotlin
// MyLand의 LandCommand에서 농사마을 번호 표시
private fun showChunkInfo(player: Player, chunk: Chunk) {
    val chunkInfo = landManager.getChunkInfo(chunk)
    if (chunkInfo != null) {
        player.sendMessage("§6=== 땅 정보 ===")
        player.sendMessage("§7소유자: §f${chunkInfo.owner.ownerName}")
        
        // 농사마을 번호 표시
        chunkInfo.farmVillageNumber?.let { villageNumber ->
            player.sendMessage("§7농사마을: §e${villageNumber}번 마을")
        }
    }
}
```

## 8. 설정 및 의존성

### 8.1 시스템 의존성
```kotlin
// Main.kt에서의 초기화 순서 (중요!)
if (serviceType == "Vanilla") {
    // 1. 먼저 PrivateLandSystem 초기화
    privateLandSystem = PrivateLandSystem(this, database, debugManager)
    privateLandSystem?.enable()
    
    // 2. 그 다음 FarmVillageSystem 초기화 (PrivateLandSystem 의존)
    privateLandSystem?.let { privateLand ->
        farmVillageSystem = FarmVillageSystem(this, database, privateLand, debugManager, luckPerms)
        farmVillageSystem?.enable()
        
        // 3. 상호 참조 설정
        farmVillageSystem?.let { farmVillage ->
            privateLand.setFarmVillageManager(farmVillage.getFarmVillageManager())
        }
    }
}
```

### 8.2 권한 설정 (plugin.yml)
```yaml
permissions:
  farmvillage.admin:
    description: "농사마을 관리자 권한"
    default: op
  farmvillage.command:
    description: "농사마을 명령어 사용 권한"
    default: op
```

## 9. 명령어 사용법

### 9.1 관리자 명령어 (`/농사마을` 또는 `/farmvillage`)

#### 마을 관리
- `/농사마을 생성 <번호> <이름>` - 새 농사마을 생성
- `/농사마을 삭제 <번호>` - 농사마을 삭제
- `/농사마을 정보 <번호>` - 마을 정보 조회
- `/농사마을 목록` - 모든 마을 목록 조회

#### 멤버 관리
- `/농사마을 멤버추가 <번호> <플레이어>` - 마을 멤버 추가
- `/농사마을 멤버제거 <번호> <플레이어>` - 마을 멤버 제거

#### 토지 관리
- `/농사마을 토지할당 <번호>` - 현재 위치 청크를 마을에 할당
- `/농사마을 토지해제` - 현재 위치 청크의 마을 할당 해제

#### 권한 관리
- `/농사마을 권한동기화 <번호>` - 마을 권한 재동기화

### 9.2 사용 예시
```
# 1번 농사마을 "벼농사마을" 생성
/농사마을 생성 1 벼농사마을

# lukehemmin을 1번 마을에 추가
/농사마을 멤버추가 1 lukehemmin

# 현재 위치를 1번 마을 토지로 할당
/농사마을 토지할당 1

# 1번 마을 정보 조회
/농사마을 정보 1
```

## 10. 트러블슈팅

### 10.1 일반적인 문제들

**Q: LuckPerms 권한이 제대로 부여되지 않습니다.**
A: 
1. LuckPerms 플러그인이 활성화되어 있는지 확인
2. 권한 그룹이 미리 생성되어 있는지 확인 (`farmvillage.member.{번호}`)
3. `/농사마을 권한동기화 <번호>` 명령어로 재동기화

**Q: 마을 토지 할당이 안 됩니다.**
A:
1. 해당 청크가 먼저 MyLand로 클레이밍되어 있는지 확인
2. 관리자 권한이 있는지 확인
3. 마을 번호가 존재하는지 확인

**Q: MyLand 명령어에서 농사마을 번호가 표시되지 않습니다.**
A: FarmVillageManager가 LandCommand에 제대로 연결되었는지 확인하세요.

### 10.2 디버깅 방법

```kotlin
// 디버그 로그 확인
debugManager.log("FarmVillage", "디버그 메시지")

// 데이터베이스 상태 확인
val village = farmVillageManager.getVillageInfo(villageNumber)
if (village == null) {
    // 마을이 존재하지 않음
}

// LuckPerms 연동 상태 확인
if (luckPerms == null) {
    // LuckPerms가 비활성화됨
}
```

## 11. 확장 가능성

### 11.1 향후 추가 가능한 기능
- 마을별 설정 시스템 (PvP 허용 여부, 폭발 허용 여부 등)
- 마을 레벨 시스템
- 마을별 경제 시스템
- 마을 이벤트 시스템

### 11.2 다른 시스템과의 추가 연동
- Economy: 마을 세금 시스템
- Discord: 마을 전용 채널 생성
- Statistics: 마을별 통계

## 12. 개발 가이드라인

### 12.1 코드 작성 원칙
- MyLand 시스템에 의존하되 침해하지 않기
- 관리자 중심의 안전한 API 제공
- LuckPerms 연동 시 null-safe 처리

### 12.2 성능 고려사항
- 마을 수가 많아질 경우 캐싱 시스템 도입
- 대용량 토지 할당 시 배치 처리
- 권한 동기화 최적화

### 12.3 보안 고려사항
- 관리자 권한 엄격한 검증
- SQL 인젝션 방지
- 권한 상승 공격 방지
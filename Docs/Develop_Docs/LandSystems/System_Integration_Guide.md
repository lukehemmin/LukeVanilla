# 토지 시스템 통합 가이드

## 1. 개요
MyLand, FarmVillage, AdvancedLandClaiming 세 시스템이 어떻게 통합되어 작동하는지 설명하는 문서입니다.

## 2. 시스템 아키텍처 전체 구조

```
                    ┌─────────────────┐
                    │    Main.kt      │
                    │   (통합 관리)   │
                    └─────────┬───────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌─────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   MyLand    │    │  FarmVillage    │    │AdvancedLand     │
│   System    │◄──►│    System       │    │Claiming System  │
│  (기본토지)  │    │  (농사마을)     │    │  (고급마을)     │
└─────────────┘    └─────────────────┘    └─────────────────┘
       │                     │                       │
       │                     │                       │
       ▼                     ▼                       ▼
┌─────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ land_claims │    │ farm_villages   │    │advanced_land    │
│land_members │    │farm_village_    │    │_claims          │
│land_history │    │members          │    │advanced_        │
│             │    │farm_village_    │    │villages         │
│             │    │lands            │    │...              │
└─────────────┘    └─────────────────┘    └─────────────────┘
```

## 3. 시스템 초기화 순서

### 3.1 Main.kt에서의 초기화 순서 (중요!)
```kotlin
// Main.kt onEnable() 순서
if (serviceType == "Vanilla") {
    // 1. PlayTimeSystem 먼저 초기화 (AdvancedLand 의존성)
    playTimeSystem = PlayTimeSystem(this, database, debugManager)
    playTimeSystem?.enable()
    
    // 2. AdvancedLandSystem 초기화 (PlayTime 의존)
    val playTimeManager = playTimeSystem?.getPlayTimeManager()
    if (playTimeManager != null) {
        advancedLandSystem = AdvancedLandSystem(this, database, debugManager, playTimeManager)
        advancedLandSystem?.enable()
    }
    
    // 3. PrivateLandSystem(MyLand) 초기화
    privateLandSystem = PrivateLandSystem(this, database, debugManager)
    privateLandSystem?.enable()
    
    // 4. FarmVillageSystem 초기화 (MyLand 의존)
    privateLandSystem?.let { privateLand ->
        farmVillageSystem = FarmVillageSystem(this, database, privateLand, debugManager, luckPerms)
        farmVillageSystem?.enable()
        
        // 5. 시스템 간 연결 설정
        farmVillageSystem?.let { farmVillage ->
            privateLand.setFarmVillageManager(farmVillage.getFarmVillageManager())
        }
        
        advancedLandSystem?.let { advancedLand ->
            val advancedLandManager = advancedLand.getAdvancedLandManager()
            if (advancedLandManager != null) {
                // 양방향 연결 설정 (중요!)
                privateLand.setAdvancedLandManager(advancedLandManager)
                advancedLandManager.setLandManager(privateLand.getLandManager())
            }
        }
    }
}
```

### 3.2 왜 이 순서가 중요한가?
1. **PlayTime → AdvancedLand**: 플레이타임 기반 클레이밍 제한
2. **MyLand → FarmVillage**: 농사마을이 MyLand 클레이밍 시스템 사용
3. **MyLand ↔ AdvancedLand**: 친구 시스템 공유를 위한 양방향 연결

## 4. 데이터 흐름 및 연동

### 4.1 청크 정보 조회 시 데이터 흐름

```
플레이어가 /땅 정보 입력
        │
        ▼
┌─────────────────┐
│ LandCommand     │
│ (MyLand)        │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ 기본 MyLand     │    │ FarmVillage      │    │ AdvancedLand    │
│ 정보 조회       │───►│ 번호 확인        │───►│ 고급정보 확인   │
│                 │    │ (농사마을 번호)  │    │ (마을정보)      │
└─────────────────┘    └──────────────────┘    └─────────────────┘
          │                       │                       │
          ▼                       ▼                       ▼
    "소유자: steve"         "농사마을: 1번"         "마을: 우리마을"
```

### 4.2 실제 코드에서의 연동 예시

#### MyLand LandCommand에서 통합 정보 표시
```kotlin
private fun showChunkInfo(player: Player, chunk: Chunk) {
    val chunkInfo = landManager.getChunkInfo(chunk)
    if (chunkInfo != null) {
        player.sendMessage("§6=== 땅 정보 ===")
        player.sendMessage("§7소유자: §f${chunkInfo.owner.ownerName}")
        player.sendMessage("§7청크: §f(${chunk.x}, ${chunk.z})")
        
        // FarmVillage 정보 표시
        chunkInfo.farmVillageNumber?.let { villageNumber ->
            player.sendMessage("§7농사마을: §e${villageNumber}번 마을")
        }
        
        // AdvancedLand 정보 표시
        advancedLandManager?.let { manager ->
            val advancedInfo = manager.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
            if (advancedInfo?.claimType == ClaimType.VILLAGE && advancedInfo.villageId != null) {
                val villageInfo = manager.getVillageInfo(advancedInfo.villageId!!)
                villageInfo?.let {
                    player.sendMessage("§7고급마을: §b${it.villageName}")
                }
            }
        }
        
        // 멤버 정보 표시
        if (chunkInfo.members.isNotEmpty()) {
            player.sendMessage("§7친구: §f${chunkInfo.members.joinToString(", ")}")
        }
    } else {
        player.sendMessage("§c클레이밍되지 않은 땅입니다.")
    }
}
```

## 5. 권한 시스템 통합

### 5.1 권한 우선순위
```
1순위: 관리자 권한 (advancedland.admin.bypass, myland.admin 등)
2순위: 소유자 권한 (땅 소유자는 모든 권한)
3순위: AdvancedLand 마을 권한 (마을 멤버의 역할 기반)
4순위: MyLand 친구 권한 (개인 토지 친구)
5순위: FarmVillage 권한 (LuckPerms 그룹 기반)
6순위: 권한 없음 (접근 차단)
```

### 5.2 AdvancedLandProtectionListener에서의 통합 권한 체크
```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onBlockBreak(event: BlockBreakEvent) {
    val player = event.player
    val chunk = event.block.chunk
    
    // 1. 관리자 권한 체크
    if (player.hasPermission("advancedland.admin.bypass")) {
        return // 관리자는 모든 권한
    }
    
    val claimInfo = advancedLandManager.getClaimOwner(chunk.world.name, chunk.x, chunk.z) ?: return
    
    // 2. 소유자 권한 체크
    if (claimInfo.ownerUuid == player.uniqueId) {
        return // 소유자는 모든 권한
    }
    
    // 3. 토지 타입별 권한 체크
    when (claimInfo.claimType) {
        ClaimType.PERSONAL -> {
            // MyLand 친구 시스템 권한 체크
            val landManager = advancedLandManager.getLandManager()
            if (landManager != null && landManager.isMember(chunk, player)) {
                return // 친구 권한
            }
        }
        
        ClaimType.VILLAGE -> {
            // AdvancedLand 마을 권한 체크
            if (claimInfo.villageId != null) {
                if (hasVillagePermission(player.uniqueId, claimInfo.villageId!!, VillagePermissionType.BREAK_BLOCKS)) {
                    return // 마을 권한
                }
            }
        }
    }
    
    // 4. FarmVillage 권한 체크 (추가 확인)
    // 이 부분은 별도 리스너에서 처리하거나 여기서 통합 처리
    
    // 모든 권한 체크 실패 시 이벤트 취소
    event.isCancelled = true
    player.sendMessage("권한이 없습니다.")
}
```

## 6. 데이터베이스 통합 전략

### 6.1 테이블 간 관계

#### 직접 연결 (Foreign Key)
- `advanced_land_claims.village_id` → `advanced_villages.id`
- `advanced_village_members.village_id` → `advanced_villages.id`

#### 논리적 연결 (Application Level)
- `land_claims` ↔ `farm_village_lands` (world_name, chunk_x, chunk_z로 연결)
- `land_claims` ↔ `advanced_land_claims` (world_name, chunk_x, chunk_z로 연결)

### 6.2 데이터 일관성 유지

#### 청크 클레이밍 시
```kotlin
// AdvancedLandManager.claimChunk()에서
fun claimChunk(player: Player, chunk: Chunk): ClaimResult {
    // 1. MyLand에서 이미 클레이밍되었는지 확인
    val landManager = getLandManager()
    if (landManager?.isChunkClaimed(chunk) == true) {
        return ClaimResult(false, "이미 MyLand로 클레이밍된 청크입니다.")
    }
    
    // 2. AdvancedLand로 클레이밍 진행
    // ...
}
```

#### 청크 언클레이밍 시
```kotlin
fun unclaimChunk(player: Player, chunk: Chunk): ClaimResult {
    // 1. AdvancedLand 클레이밍 해제
    val success = landData.removeClaim(/*...*/)
    
    if (success) {
        // 2. 관련된 모든 시스템의 데이터 정리
        // FarmVillage 할당 해제
        farmVillageData?.removeLand(chunk.world.name, chunk.x, chunk.z)
        
        // MyLand 멤버 정보 정리 (필요시)
        landManager?.let { 
            // ConnectedChunks에서 제거 등
        }
    }
    
    return if (success) ClaimResult(true, "성공") else ClaimResult(false, "실패")
}
```

## 7. 명령어 시스템 통합

### 7.1 명령어 우선순위 및 라우팅

#### `/땅` 명령어의 동작
```kotlin
// LandCommand.kt
override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
    when (args.getOrNull(0)) {
        "클레임" -> {
            // MyLand 기본 클레이밍
            handleClaim(player, args)
        }
        "고급클레임" -> {
            // AdvancedLand 클레이밍으로 리다이렉트
            advancedLandManager?.let {
                // AdvancedLandCommand로 전달
            }
        }
        "정보" -> {
            // 통합된 정보 표시 (MyLand + FarmVillage + AdvancedLand)
            handleInfo(player, args)
        }
        "마을생성" -> {
            // AdvancedLand 마을 생성으로 리다이렉트
            advancedLandManager?.let {
                // 연결된 청크 그룹을 마을로 전환
            }
        }
        // ... 기타 명령어들
    }
}
```

### 7.2 명령어 탭 완성 통합
```kotlin
override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
    return when (args.size) {
        1 -> mutableListOf(
            // MyLand 명령어
            "클레임", "포기", "정보", "목록", "친구추가", "친구삭제", "친구목록",
            // AdvancedLand 명령어 통합
            "마을생성", "마을정보", "마을초대", "마을추방", "마을설정",
            // 관리자 전용 (FarmVillage)
            "농사마을생성", "농사마을삭제"
        ).filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        
        // ... 추가 탭 완성 로직
    }
}
```

## 8. 이벤트 시스템 통합

### 8.1 이벤트 처리 순서
```kotlin
// EventPriority 설정으로 처리 순서 제어
@EventHandler(priority = EventPriority.LOWEST)   // 가장 먼저
fun onBlockBreakEarly(event: BlockBreakEvent) {
    // 사전 검증, 로깅 등
}

@EventHandler(priority = EventPriority.HIGH)     // 메인 처리
fun onBlockBreakMain(event: BlockBreakEvent) {
    // 실제 권한 체크 및 차단
}

@EventHandler(priority = EventPriority.MONITOR)  // 가장 나중
fun onBlockBreakMonitor(event: BlockBreakEvent) {
    // 이벤트 결과에 따른 후처리, 로깅
    if (!event.isCancelled) {
        // 성공 로깅
    }
}
```

### 8.2 커스텀 이벤트 연동
```kotlin
// 새로운 통합 이벤트 정의
class LandSystemIntegratedEvent(
    val chunk: Chunk,
    val player: Player,
    val actionType: String,
    val systemType: String  // "MyLand", "FarmVillage", "AdvancedLand"
) : Event(), Cancellable {
    // 다른 플러그인에서 이 이벤트를 감지하여 추가 처리 가능
}

// 각 시스템에서 이벤트 발생
fun notifyIntegratedAction(chunk: Chunk, player: Player, action: String, system: String) {
    val event = LandSystemIntegratedEvent(chunk, player, action, system)
    Bukkit.getPluginManager().callEvent(event)
}
```

## 9. API 통합 가이드

### 9.1 다른 플러그인에서 토지 시스템 사용

#### 기본 API 접근
```kotlin
// 다른 플러그인에서
class ExamplePlugin : JavaPlugin() {
    private var landSystems: LandSystemsAPI? = null
    
    override fun onEnable() {
        val lukeVanillaPlugin = server.pluginManager.getPlugin("LukeVanilla") as? Main
        landSystems = lukeVanillaPlugin?.getLandSystemsAPI()
    }
    
    fun checkLandOwnership(chunk: Chunk): String? {
        return landSystems?.let { api ->
            // 우선순위: AdvancedLand > MyLand > FarmVillage
            api.getAdvancedLandOwner(chunk)?.ownerName
                ?: api.getMyLandOwner(chunk)?.ownerName
                ?: api.getFarmVillageNumber(chunk)?.let { "농사마을 ${it}번" }
        }
    }
}
```

#### 통합 API 인터페이스
```kotlin
interface LandSystemsAPI {
    // 통합 조회
    fun getLandOwner(chunk: Chunk): LandOwnerInfo?
    fun canPlayerModify(chunk: Chunk, player: Player): Boolean
    fun getLandType(chunk: Chunk): LandType  // PERSONAL, VILLAGE, FARM_VILLAGE, NONE
    
    // 개별 시스템 접근
    fun getMyLandManager(): LandManager?
    fun getAdvancedLandManager(): AdvancedLandManager?
    fun getFarmVillageManager(): FarmVillageManager?
}

data class LandOwnerInfo(
    val ownerName: String,
    val ownerUuid: UUID,
    val landType: LandType,
    val additionalInfo: Map<String, Any> = emptyMap()
)

enum class LandType {
    PERSONAL,      // MyLand 개인 토지
    VILLAGE,       // AdvancedLand 마을
    FARM_VILLAGE,  // FarmVillage 농사마을
    NONE           // 클레이밍되지 않음
}
```

## 10. 성능 최적화 통합 전략

### 10.1 캐싱 전략

#### 통합 캐시 시스템
```kotlin
object LandSystemCache {
    private val chunkInfoCache = mutableMapOf<String, ChunkCacheInfo>()
    private val cacheTimeout = 300_000L // 5분
    
    data class ChunkCacheInfo(
        val myLandInfo: ChunkOwner?,
        val advancedLandInfo: AdvancedClaimInfo?,
        val farmVillageNumber: Int?,
        val cachedAt: Long
    )
    
    fun getCachedChunkInfo(chunk: Chunk): ChunkCacheInfo? {
        val key = "${chunk.world.name}:${chunk.x}:${chunk.z}"
        val cached = chunkInfoCache[key]
        
        return if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < cacheTimeout) {
            cached
        } else {
            chunkInfoCache.remove(key)
            null
        }
    }
    
    fun cacheChunkInfo(chunk: Chunk, info: ChunkCacheInfo) {
        val key = "${chunk.world.name}:${chunk.x}:${chunk.z}"
        chunkInfoCache[key] = info.copy(cachedAt = System.currentTimeMillis())
    }
}
```

### 10.2 데이터베이스 쿼리 최적화

#### 배치 쿼리 사용
```kotlin
// 여러 청크 정보를 한 번에 조회
fun getBatchChunkInfo(chunks: List<Chunk>): Map<Chunk, LandOwnerInfo?> {
    val chunkKeys = chunks.map { "${it.world.name}:${it.x}:${it.z}" }
    
    // 단일 쿼리로 모든 시스템 정보 조회
    val sql = """
        SELECT 
            lc.world_name, lc.chunk_x, lc.chunk_z, 
            lc.owner_name as myland_owner,
            alc.owner_name as advanced_owner, alc.claim_type,
            fvl.village_number as farm_village_num
        FROM land_claims lc
        LEFT JOIN advanced_land_claims alc ON (lc.world_name = alc.world_name AND lc.chunk_x = alc.chunk_x AND lc.chunk_z = alc.chunk_z)
        LEFT JOIN farm_village_lands fvl ON (lc.world_name = fvl.world_name AND lc.chunk_x = fvl.chunk_x AND lc.chunk_z = fvl.chunk_z)
        WHERE CONCAT(lc.world_name, ':', lc.chunk_x, ':', lc.chunk_z) IN (${chunkKeys.joinToString(",") { "'$it'" }})
    """
    
    // 결과를 Map으로 변환하여 반환
}
```

## 11. 문제 해결 및 디버깅

### 11.1 시스템 간 충돌 해결

#### 일반적인 충돌 상황
1. **중복 클레이밍**: 한 청크가 여러 시스템에 클레이밍됨
2. **권한 충돌**: 서로 다른 시스템의 권한이 상충
3. **데이터 불일치**: 시스템 간 데이터 동기화 문제

#### 해결 방법
```kotlin
// 데이터 일관성 검사 도구
class LandSystemIntegrityChecker {
    fun checkDataIntegrity(): List<IntegrityIssue> {
        val issues = mutableListOf<IntegrityIssue>()
        
        // 1. 중복 클레이밍 검사
        val duplicateClaims = findDuplicateClaims()
        issues.addAll(duplicateClaims.map { 
            IntegrityIssue.DuplicateClaim(it.worldName, it.chunkX, it.chunkZ) 
        })
        
        // 2. 고아 데이터 검사
        val orphanData = findOrphanData()
        issues.addAll(orphanData)
        
        // 3. 권한 불일치 검사
        val permissionIssues = findPermissionInconsistencies()
        issues.addAll(permissionIssues)
        
        return issues
    }
    
    fun fixIntegrityIssues(issues: List<IntegrityIssue>) {
        issues.forEach { issue ->
            when (issue) {
                is IntegrityIssue.DuplicateClaim -> resolveDuplicateClaim(issue)
                is IntegrityIssue.OrphanData -> removeOrphanData(issue)
                is IntegrityIssue.PermissionMismatch -> fixPermissionMismatch(issue)
            }
        }
    }
}
```

### 11.2 디버깅 도구

#### 통합 디버그 명령어
```kotlin
// /땅 디버그 <청크좌표> - 해당 청크의 모든 시스템 정보 출력
private fun handleDebug(player: Player, args: Array<out String>) {
    val chunk = player.location.chunk
    
    player.sendMessage("§6=== 통합 디버그 정보 ===")
    
    // MyLand 정보
    val myLandOwner = landManager.getChunkOwner(chunk)
    player.sendMessage("§7MyLand: ${myLandOwner?.ownerName ?: "없음"}")
    
    // AdvancedLand 정보
    advancedLandManager?.let { manager ->
        val advancedInfo = manager.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
        player.sendMessage("§7AdvancedLand: ${advancedInfo?.ownerName ?: "없음"}")
        advancedInfo?.let {
            player.sendMessage("  §8타입: ${it.claimType}")
            it.villageId?.let { id -> player.sendMessage("  §8마을ID: $id") }
        }
    }
    
    // FarmVillage 정보
    farmVillageManager?.let { manager ->
        val villageNumber = manager.getChunkVillageNumber(chunk)
        player.sendMessage("§7FarmVillage: ${villageNumber?.let { "${it}번 마을" } ?: "없음"}")
    }
    
    // 권한 정보
    player.sendMessage("§7권한 체크:")
    player.sendMessage("  §8MyLand 멤버: ${landManager.isMember(chunk, player)}")
    advancedLandManager?.let {
        // AdvancedLand 권한 체크 로직
    }
    
    // 캐시 정보
    val cacheInfo = LandSystemCache.getCachedChunkInfo(chunk)
    player.sendMessage("§7캐시: ${if (cacheInfo != null) "있음" else "없음"}")
}
```

## 12. 향후 확장 계획

### 12.1 새로운 통합 기능

#### 토지 거래 시스템 (계획)
- MyLand ↔ AdvancedLand 간 토지 타입 변환
- 경제 시스템과 연동된 토지 매매
- 토지 임대 시스템

#### 토지 연합 시스템 (계획)
- 여러 AdvancedLand 마을 간 연합
- 연합 간 자원 공유 및 교역
- 대규모 건축 프로젝트 지원

### 12.2 성능 개선 계획

#### 비동기 처리 강화
```kotlin
// 대용량 연결성 체크를 비동기로 처리
fun updateConnectedChunksAsync(playerUuid: UUID): CompletableFuture<Boolean> {
    return CompletableFuture.supplyAsync {
        // 무거운 연결성 계산 작업
        updateConnectedChunks(playerUuid)
    }.thenApplyAsync { result ->
        // 메인 스레드에서 캐시 업데이트
        Bukkit.getScheduler().runTask(plugin) {
            updateChunkCache(playerUuid)
        }
        result
    }
}
```

#### 데이터베이스 연결 풀 최적화
```kotlin
// HikariCP 설정 최적화
val config = HikariConfig().apply {
    jdbcUrl = "jdbc:mysql://localhost/lukevanilla"
    username = "minecraft"
    password = "password"
    
    // 토지 시스템 특성에 맞는 최적화
    maximumPoolSize = 10
    minimumIdle = 3
    connectionTimeout = 30000
    idleTimeout = 600000
    maxLifetime = 1800000
    
    // 읽기 전용 쿼리용 별도 커넥션
    readOnly = false
    leakDetectionThreshold = 60000
}
```

이 통합 가이드를 통해 세 시스템이 어떻게 협력하여 작동하는지 완전히 이해할 수 있으며, 새로운 기능 추가나 문제 해결 시 참고할 수 있습니다.
# AdvancedLandManager 마이그레이션 가이드

## 현황
현재 프로젝트에 두 가지 AdvancedLandManager 구현체가 공존하고 있습니다:

1. **AdvancedLandManager.kt** (1257줄) - 현재 사용 중
2. **ImprovedAdvancedLandManager.kt** (391줄) - 개선된 버전

## ImprovedAdvancedLandManager의 장점

### 1. Service 계층 분리
- `AtomicClaimService`: Race Condition 방지
- `VillageManagementService`: 마을 관리 로직 분리
- 단일 책임 원칙 준수

### 2. Thread-Safe 캐시
- `AdvancedLandCache`: ConcurrentHashMap + Read-Write Lock
- 마을별 청크 인덱스로 O(1) 조회
- 메모리 최적화

### 3. Race Condition 방지
- SELECT FOR UPDATE 사용
- 청크별 ReentrantLock
- 트랜잭션 롤백

### 4. 코드 크기
- 기존 1257줄 → 개선 391줄 (66% 감소)

## 마이그레이션 계획

### 단계 1: 인터페이스 정의 (권장)
현재 두 구현체 모두 직접 사용되고 있어, 인터페이스를 먼저 정의하는 것을 권장합니다.

```kotlin
interface LandClaimManager {
    fun claimChunk(player: Player, chunk: Chunk, resourceType: ClaimResourceType?): ClaimResult
    fun unclaimChunk(player: Player, chunk: Chunk): ClaimResult
    fun isChunkClaimed(worldName: String, chunkX: Int, chunkZ: Int): Boolean
    fun getClaimOwner(worldName: String, chunkX: Int, chunkZ: Int): AdvancedClaimInfo?
    fun getPlayerClaimCount(playerUuid: UUID): Int
    fun isVeteranPlayer(playerUuid: UUID): Boolean
    fun getPlayerClaimSummary(playerUuid: UUID): String
    
    // 마을 관련
    fun createVillage(player: Player, villageName: String, connectedChunks: Set<Chunk>): ClaimResult
    fun getVillageInfo(villageId: Int): VillageInfo?
    fun getVillageMembers(villageId: Int): List<VillageMember>
    fun getPlayerVillageMembership(playerUuid: UUID): VillageMember?
    
    // 기존 호환성
    fun setLandManager(landManager: com.lukehemmin.lukeVanilla.System.MyLand.LandManager)
    fun getLandManager(): com.lukehemmin.lukeVanilla.System.MyLand.LandManager?
    fun getLandData(): AdvancedLandData
}
```

### 단계 2: ImprovedAdvancedLandManager가 인터페이스 구현
```kotlin
class ImprovedAdvancedLandManager(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager,
    private val playTimeManager: PlayTimeManager
) : LandClaimManager {
    // 기존 구현 유지
}
```

### 단계 3: AdvancedLandSystem 수정
```kotlin
class AdvancedLandSystem(...) {
    // 변경 전
    private lateinit var advancedLandManager: AdvancedLandManager
    
    // 변경 후
    private lateinit var advancedLandManager: LandClaimManager
    
    fun enable() {
        // ImprovedAdvancedLandManager 사용
        advancedLandManager = ImprovedAdvancedLandManager(plugin, database, debugManager, playTimeManager)
        // ...
    }
}
```

### 단계 4: 테스트
- [ ] 청크 클레이밍 테스트
- [ ] 청크 반환 테스트
- [ ] 마을 생성 테스트
- [ ] 마을 해체 테스트
- [ ] 동시성 테스트 (여러 플레이어가 동시에 클레임)
- [ ] 성능 테스트

### 단계 5: 구버전 제거
테스트가 모두 통과하면:
1. `AdvancedLandManager.kt` 파일 삭제
2. 관련 import 문 정리
3. 커밋 및 배포

## 호환성 체크리스트

### 메서드 시그니처 비교

#### claimChunk
```kotlin
// 기존
fun claimChunk(player: Player, chunk: Chunk, resourceType: ClaimResourceType?): ClaimResult

// 개선
fun claimChunk(player: Player, chunk: Chunk, resourceType: ClaimResourceType?): ClaimResult
```
✅ 호환됨

#### 마을 관련
```kotlin
// 기존
fun createVillage(player: Player, villageName: String, connectedChunks: Set<org.bukkit.Chunk>): ClaimResult

// 개선
fun createVillage(player: Player, villageName: String, connectedChunks: Set<Chunk>): ClaimResult
```
✅ 호환됨 (`org.bukkit.Chunk` == `Chunk`)

### 누락된 메서드 확인

#### 기존에만 있는 메서드
- `calculateRefundItems(claimCost: ClaimCost?): List<ItemStack>`
- `giveRefundItemsSafely(player: Player, refundItems: List<ItemStack>)`
- `getRepresentativeChunk(playerUuid: UUID, targetChunk: Chunk): Chunk?`
- `isChunkInSameGroup(playerUuid: UUID, chunk1: Chunk, chunk2: Chunk): Boolean`
- `getGroupMemberChunks(playerUuid: UUID, targetChunk: Chunk): Set<ChunkCoordinate>`
- `isPlayerOwner(playerUuid: UUID, chunk: Chunk): Boolean`

#### 개선 버전에만 있는 메서드
- `getVillageChunkCount(villageId: Int): Int`
- `validateCacheConsistency(): Map<String, Any>`
- `optimizeMemory()`

#### 조치 필요
ImprovedAdvancedLandManager에 누락된 메서드 추가 필요:
```kotlin
// ImprovedAdvancedLandManager에 추가
fun getRepresentativeChunk(playerUuid: UUID, targetChunk: Chunk): Chunk? {
    // landData.getPlayerConnectedChunks 사용하여 구현
}

fun isChunkInSameGroup(playerUuid: UUID, chunk1: Chunk, chunk2: Chunk): Boolean {
    // 구현 필요
}

fun getGroupMemberChunks(playerUuid: UUID, targetChunk: Chunk): Set<ChunkCoordinate> {
    // 구현 필요
}

fun isPlayerOwner(playerUuid: UUID, chunk: Chunk): Boolean {
    val claimInfo = getClaimOwner(chunk.world.name, chunk.x, chunk.z)
    return claimInfo?.ownerUuid == playerUuid
}
```

## 롤백 계획
문제 발생 시:
1. Git revert
2. AdvancedLandSystem에서 다시 AdvancedLandManager 사용
3. 원인 분석 후 재시도

## 권장 순서
1. **먼저**: DB 마이그레이션 (system_type 컬럼 추가)
2. **다음**: MyLand Race Condition 수정 (완료)
3. **마지막**: AdvancedLandManager 통합

이유: DB 구조가 안정되어야 Manager 교체가 안전함

## 현재 상태
- ✅ Critical 이슈 4개 해결 완료
- ✅ High 이슈 3개 해결 완료
- ⏳ AdvancedLandManager 통합 준비 중
- ⏳ DB 마이그레이션 스크립트 생성 완료 (실행 대기)

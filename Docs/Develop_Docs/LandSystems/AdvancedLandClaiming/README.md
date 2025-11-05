# AdvancedLandClaiming 시스템 개발 문서

## 1. 시스템 개요

AdvancedLandClaiming은 플레이어가 주도하는 고급 마을 시스템으로, 개인 토지를 마을로 전환하여 여러 플레이어가 함께 건축하고 관리할 수 있는 협동 커뮤니티 공간을 제공합니다.

### 주요 기능
- 플레이타임 기반 클레이밍 제한 (신규 7일 미만: 9개, 베테랑: 무제한)
- 무료/유료 클레이밍 시스템 (무료 4개 + 자원 비용)
- 연결된 청크 그룹 관리 시스템
- 개인 토지 → 마을 전환 기능
- 역할 기반 마을 권한 시스템 (이장/부이장/마을원)
- 마을 토지 확장 및 반환
- 50% 환불 시스템
- MyLand 친구 시스템과의 완전 통합

### 시스템 특징
- **플레이어 주도**: 마을 생성/관리를 플레이어가 직접 수행
- **연결성 기반**: 붙어있는 토지만 하나의 마을로 관리
- **권한 세분화**: 건축/파괴/컨테이너/레드스톤 등 세부 권한 관리
- **확장성**: 마을 토지를 점진적으로 확장 가능

## 2. 아키텍처 설계

```
AdvancedLandClaiming 시스템 구조:
┌─────────────────────────────────────────┐
│              Main.kt                    │
│     (시스템 초기화 - PlayTime 이후)      │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│        AdvancedLandSystem               │
│     (시스템 통합 및 라이프사이클 관리)     │
│     + PlayTimeManager 의존성            │
└─────────────────┬───────────────────────┘
                  │
        ┌─────────┼─────────┐
        │         │         │
        ▼         ▼         ▼
┌──────────────┐ ┌──────────────┐ ┌─────────────────┐
│AdvancedLand  │ │AdvancedLand  │ │AdvancedLand     │
│Manager       │ │Command       │ │ProtectionListener│
│(핵심로직)      │ │(명령어처리)    │ │(이벤트보호)      │
└──────┬───────┘ └──────────────┘ └─────────────────┘
       │
       ▼
┌──────────────┐    ┌─────────────┐    ┌─────────────┐
│AdvancedLand  │◄──►│   MyLand    │◄──►│VillageSettings│
│Data          │    │ LandManager │    │GUI          │
│(데이터접근)  │    │(친구시스템) │    │(GUI관리)    │
└──────────────┘    └─────────────┘    └─────────────┘
```

## 3. 데이터베이스 스키마

### 3.1 advanced_land_claims 테이블
```sql
CREATE TABLE advanced_land_claims (
    id INT AUTO_INCREMENT PRIMARY KEY,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    owner_uuid VARCHAR(36) NOT NULL,
    owner_name VARCHAR(50) NOT NULL,
    claim_type ENUM('PERSONAL', 'VILLAGE') NOT NULL DEFAULT 'PERSONAL',
    village_id INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    claim_cost_type ENUM('FREE', 'IRON_INGOT', 'DIAMOND', 'NETHERITE_INGOT') DEFAULT 'FREE',
    claim_cost_amount INT DEFAULT 0,
    claim_cost_slot_used INT DEFAULT 0,
    UNIQUE KEY unique_chunk (world_name, chunk_x, chunk_z),
    INDEX idx_owner (owner_uuid),
    INDEX idx_village (village_id),
    FOREIGN KEY (village_id) REFERENCES advanced_villages(id) ON DELETE SET NULL
);
```

### 3.2 advanced_villages 테이블
```sql
CREATE TABLE advanced_villages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_name VARCHAR(100) NOT NULL UNIQUE,
    mayor_uuid VARCHAR(36) NOT NULL,
    mayor_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    INDEX idx_mayor (mayor_uuid),
    INDEX idx_active (is_active)
);
```

### 3.3 advanced_village_members 테이블
```sql
CREATE TABLE advanced_village_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_id INT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    member_name VARCHAR(50) NOT NULL,
    role ENUM('MAYOR', 'DEPUTY_MAYOR', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    permissions JSON DEFAULT NULL,
    FOREIGN KEY (village_id) REFERENCES advanced_villages(id) ON DELETE CASCADE,
    UNIQUE KEY unique_member (village_id, member_uuid),
    INDEX idx_member (member_uuid),
    INDEX idx_role (role)
);
```

### 3.4 advanced_village_permissions 테이블
```sql
CREATE TABLE advanced_village_permissions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    village_id INT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    permission_type ENUM('BUILD', 'BREAK_BLOCKS', 'USE_CONTAINERS', 'USE_REDSTONE', 'EXPAND_LAND', 'INVITE_MEMBERS', 'KICK_MEMBERS') NOT NULL,
    granted BOOLEAN DEFAULT TRUE,
    granted_by VARCHAR(36),
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_id) REFERENCES advanced_villages(id) ON DELETE CASCADE,
    UNIQUE KEY unique_permission (village_id, member_uuid, permission_type)
);
```

### 3.5 advanced_connected_chunks 테이블
```sql
CREATE TABLE advanced_connected_chunks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    owner_uuid VARCHAR(36) NOT NULL,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_group (group_id),
    INDEX idx_owner (owner_uuid),
    UNIQUE KEY unique_chunk_group (world_name, chunk_x, chunk_z, group_id)
);
```

### 3.6 advanced_land_history 테이블
```sql
CREATE TABLE advanced_land_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    world_name VARCHAR(100) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    action_type ENUM('CLAIM', 'UNCLAIM', 'CONVERT_TO_VILLAGE', 'VILLAGE_EXPAND', 'VILLAGE_RETURN') NOT NULL,
    actor_uuid VARCHAR(36) NOT NULL,
    actor_name VARCHAR(50) NOT NULL,
    reason VARCHAR(200),
    details JSON DEFAULT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk (world_name, chunk_x, chunk_z),
    INDEX idx_actor (actor_uuid),
    INDEX idx_timestamp (timestamp)
);
```

## 4. 주요 클래스 및 메서드

### 4.1 AdvancedLandSystem 클래스
시스템의 진입점이며 의존성을 관리합니다.

```kotlin
class AdvancedLandSystem(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager,
    private val playTimeManager: PlayTimeManager
) {
    fun enable()                                        // 시스템 활성화
    fun disable()                                       // 시스템 비활성화
    fun getAdvancedLandManager(): AdvancedLandManager?  // 매니저 반환
}
```

### 4.2 AdvancedLandManager 클래스
핵심 비즈니스 로직을 담당하는 메인 클래스입니다.

```kotlin
class AdvancedLandManager(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager,
    private val playTimeManager: PlayTimeManager
) {
    // === 기본 클레이밍 ===
    fun claimChunk(player: Player, chunk: Chunk, resourceType: ClaimResourceType? = null): ClaimResult
    fun unclaimChunk(player: Player, chunk: Chunk): ClaimResult
    fun isChunkClaimed(worldName: String, chunkX: Int, chunkZ: Int): Boolean
    fun getClaimOwner(worldName: String, chunkX: Int, chunkZ: Int): AdvancedClaimInfo?
    fun getPlayerClaimCount(playerUuid: UUID): Int
    fun isVeteranPlayer(playerUuid: UUID): Boolean
    fun getPlayerClaimSummary(playerUuid: UUID): String
    
    // === 연결된 청크 그룹 시스템 ===
    fun getRepresentativeChunk(playerUuid: UUID, targetChunk: Chunk): Chunk?
    fun isChunkInSameGroup(playerUuid: UUID, chunk1: Chunk, chunk2: Chunk): Boolean
    fun getGroupMemberChunks(playerUuid: UUID, targetChunk: Chunk): Set<ChunkCoordinate>
    fun getPlayerConnectedChunks(playerUuid: UUID): List<ConnectedChunks>
    
    // === 마을 시스템 ===
    fun createVillage(player: Player, villageName: String, connectedChunks: Set<Chunk>): ClaimResult
    fun getVillageInfo(villageId: Int): VillageInfo?
    fun getChunkVillageId(worldName: String, chunkX: Int, chunkZ: Int): Int?
    fun getVillageMembers(villageId: Int): List<VillageMember>
    fun addVillageMember(villageId: Int, memberUuid: UUID, memberName: String, role: VillageRole): Boolean
    fun kickVillageMember(villageId: Int, memberUuid: UUID): Boolean
    fun changeVillageMemberRole(villageId: Int, memberUuid: UUID, newRole: VillageRole): Boolean
    
    // === 마을 토지 관리 ===
    fun claimChunkForVillage(player: Player, chunk: Chunk, resourceType: ClaimResourceType? = null): ClaimResult
    fun returnVillageChunks(actor: Player, villageId: Int, chunks: Set<Chunk>, reason: String): ClaimResult
    fun isChunkConnectedToVillage(chunk: Chunk, villageId: Int): Boolean
    fun hasVillageExpandPermission(playerUuid: UUID, villageId: Int): Boolean
    fun getPlayerVillageMembership(playerUuid: UUID): VillageMember?
    
    // === 환불 시스템 ===
    fun calculateRefundItems(claimCost: ClaimCost?): List<ItemStack>
    fun giveRefundItemsSafely(player: Player, refundItems: List<ItemStack>)
    
    // === 시스템 연동 ===
    fun setLandManager(landManager: LandManager)        // MyLand 연동
    fun getLandManager(): LandManager?
    fun getLandData(): AdvancedLandData
    
    // === 내부 메서드들 ===
    private fun calculateClaimCost(playerUuid: UUID, requestedResourceType: ClaimResourceType?): ClaimResult
    private fun hasRequiredResources(player: Player, claimCost: ClaimCost): Boolean
    private fun consumeResources(player: Player, claimCost: ClaimCost): Boolean
    private fun getResourceName(resourceType: ClaimResourceType?): String
}
```

### 4.3 AdvancedLandCommand 클래스
플레이어 명령어를 처리합니다.

```kotlin
class AdvancedLandCommand(
    private val advancedLandManager: AdvancedLandManager
) : CommandExecutor, TabCompleter {
    // === 명령어 처리 ===
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>?
    
    // === 기본 명령어들 ===
    private fun handleClaim(player: Player, args: Array<out String>)             // 클레임
    private fun handleUnclaim(player: Player, args: Array<out String>)           // 언클레임
    private fun handleInfo(player: Player, args: Array<out String>)              // 정보
    private fun handleList(player: Player, args: Array<out String>)              // 목록
    private fun handleCost(player: Player, args: Array<out String>)              // 비용
    
    // === 마을 명령어들 ===
    private fun handleCreateVillage(player: Player, args: Array<out String>)     // 마을생성
    private fun handleVillageInfo(player: Player, args: Array<out String>)       // 마을정보
    private fun handleVillageInvite(player: Player, args: Array<out String>)     // 마을초대
    private fun handleVillageKick(player: Player, args: Array<out String>)       // 마을추방
    private fun handleVillageSettings(player: Player, args: Array<out String>)   // 마을설정
    private fun handleVillageClaim(player: Player, args: Array<out String>)      // 마을클레임
    private fun handleVillageReturn(player: Player, args: Array<out String>)     // 마을반환
    
    // === 연결성 명령어들 ===
    private fun handleConnectedInfo(player: Player, args: Array<out String>)     // 연결정보
    private fun handleGroupList(player: Player, args: Array<out String>)         // 그룹목록
}
```

### 4.4 AdvancedLandProtectionListener 클래스
토지 보호 이벤트를 처리합니다.

```kotlin
class AdvancedLandProtectionListener(
    private val advancedLandManager: AdvancedLandManager
) : Listener {
    // === 이벤트 처리 ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent)                    // 블록 파괴 보호
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent)                    // 블록 설치 보호
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent)            // 상호작용 보호
    
    // === 권한 확인 메서드들 ===
    private fun hasVillagePermission(playerUuid: UUID, villageId: Int, permission: VillagePermissionType): Boolean
    private fun isInteractableBlock(material: Material): Boolean
    private fun isContainer(material: Material): Boolean
    private fun isRedstoneDevice(material: Material): Boolean
    private fun isDoor(material: Material): Boolean
}
```

### 4.5 AdvancedLandData 클래스
데이터베이스 접근을 담당합니다.

```kotlin
class AdvancedLandData(private val database: Database) {
    // === 클레이밍 데이터 ===
    fun saveClaim(claimInfo: AdvancedClaimInfo): Boolean
    fun removeClaim(worldName: String, chunkX: Int, chunkZ: Int, actorUuid: UUID, actorName: String, reason: String): Boolean
    fun loadAllClaims(): Map<String, MutableMap<Pair<Int, Int>, AdvancedClaimInfo>>
    fun getPlayerUsedFreeSlots(playerUuid: UUID): Int
    fun updateClaimToVillage(claimInfo: AdvancedClaimInfo): Boolean
    
    // === 마을 데이터 ===
    fun createVillage(villageName: String, mayorUuid: UUID, mayorName: String): Int?
    fun getVillageInfo(villageId: Int): VillageInfo?
    fun isVillageNameExists(villageName: String): Boolean
    fun deactivateVillage(villageId: Int): Boolean
    
    // === 마을 멤버 데이터 ===
    fun addVillageMember(villageId: Int, memberUuid: UUID, memberName: String, role: VillageRole): Boolean
    fun removeVillageMember(villageId: Int, memberUuid: UUID): Boolean
    fun getVillageMembers(villageId: Int): List<VillageMember>
    fun updateVillageMemberRole(villageId: Int, memberUuid: UUID, newRole: VillageRole): Boolean
    fun getPlayerVillageMembership(playerUuid: UUID): VillageMember?
    
    // === 마을 권한 데이터 ===
    fun hasVillagePermission(playerUuid: UUID, villageId: Int, permission: VillagePermissionType): Boolean
    fun setVillagePermission(villageId: Int, memberUuid: UUID, permission: VillagePermissionType, granted: Boolean): Boolean
    fun getVillagePermissions(villageId: Int, memberUuid: UUID): Map<VillagePermissionType, Boolean>
    
    // === 연결된 청크 데이터 ===
    fun getPlayerConnectedChunks(playerUuid: UUID): List<ConnectedChunks>
    fun updateConnectedChunks(playerUuid: UUID): Boolean
    fun addToConnectedGroup(groupId: String, chunkCoordinate: ChunkCoordinate, ownerUuid: UUID): Boolean
    fun removeFromConnectedGroup(chunkCoordinate: ChunkCoordinate): Boolean
    
    // === 히스토리 데이터 ===
    fun addHistory(worldName: String, chunkX: Int, chunkZ: Int, actionType: String, 
                  actorUuid: UUID, actorName: String, reason: String?, details: String?): Boolean
    fun getChunkHistory(worldName: String, chunkX: Int, chunkZ: Int): List<AdvancedHistoryEntry>
}
```

### 4.6 VillageSettingsGUI 클래스
마을 설정 GUI를 처리합니다.

```kotlin
class VillageSettingsGUI(
    private val plugin: Main,
    private val advancedLandManager: AdvancedLandManager
) : Listener {
    // === GUI 생성 ===
    fun openVillageSettingsGUI(player: Player, villageId: Int)
    fun openVillageMembersGUI(player: Player, villageId: Int)
    fun openVillagePermissionsGUI(player: Player, villageId: Int, targetMemberUuid: UUID)
    
    // === 이벤트 처리 ===
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent)
    
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent)
    
    // === 내부 메서드들 ===
    private fun createVillageInfoGUI(villageInfo: VillageInfo): Inventory
    private fun createMembersListGUI(members: List<VillageMember>): Inventory
    private fun createPermissionsGUI(member: VillageMember, permissions: Map<VillagePermissionType, Boolean>): Inventory
}
```

## 5. 주요 데이터 모델

### 5.1 AdvancedClaimInfo
```kotlin
data class AdvancedClaimInfo(
    val chunkX: Int,
    val chunkZ: Int,
    val worldName: String,
    val ownerUuid: UUID,
    val ownerName: String,
    val claimType: ClaimType,
    val createdAt: Long,
    val lastUpdated: Long,
    val villageId: Int? = null,
    val claimCost: ClaimCost? = null
)
```

### 5.2 VillageInfo
```kotlin
data class VillageInfo(
    val villageId: Int,
    val villageName: String,
    val mayorUuid: UUID,
    val mayorName: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val isActive: Boolean,
    val description: String? = null
)
```

### 5.3 VillageMember
```kotlin
data class VillageMember(
    val villageId: Int,
    val memberUuid: UUID,
    val memberName: String,
    val role: VillageRole,
    val joinedAt: Long,
    val isActive: Boolean = true
)
```

### 5.4 ConnectedChunks
```kotlin
data class ConnectedChunks(
    val groupId: String,
    val ownerUuid: UUID,
    val chunks: Set<ChunkCoordinate>
) {
    fun contains(coordinate: ChunkCoordinate): Boolean = chunks.contains(coordinate)
    fun size(): Int = chunks.size
}
```

### 5.5 ClaimCost
```kotlin
data class ClaimCost(
    val resourceType: ClaimResourceType,
    val amount: Int,
    val slotUsed: Int
)
```

### 5.6 Enum 클래스들

#### ClaimType
```kotlin
enum class ClaimType {
    PERSONAL,    // 개인 토지
    VILLAGE      // 마을 토지
}
```

#### ClaimResourceType
```kotlin
enum class ClaimResourceType {
    FREE,            // 무료
    IRON_INGOT,      // 철괴
    DIAMOND,         // 다이아몬드
    NETHERITE_INGOT  // 네더라이트 주괴
}
```

#### VillageRole
```kotlin
enum class VillageRole {
    MAYOR,          // 이장
    DEPUTY_MAYOR,   // 부이장
    MEMBER          // 마을원
}
```

#### VillagePermissionType
```kotlin
enum class VillagePermissionType {
    BUILD,          // 건축 권한
    BREAK_BLOCKS,   // 블록 파괴 권한
    USE_CONTAINERS, // 컨테이너 사용 권한
    USE_REDSTONE,   // 레드스톤 사용 권한
    EXPAND_LAND,    // 토지 확장 권한
    INVITE_MEMBERS, // 멤버 초대 권한
    KICK_MEMBERS    // 멤버 추방 권한
}
```

## 6. 클레이밍 시스템 상세

### 6.1 플레이타임 기반 제한
```kotlin
// 플레이타임 확인 로직
val playTimeInfo = playTimeManager.getPlayTimeInfo(playerUuid)
val totalPlayDays = playTimeInfo?.let { it.totalPlaytimeSeconds / (24 * 60 * 60) } ?: 0
val isVeteran = totalPlayDays >= VETERAN_DAYS_THRESHOLD // 7일

// 신규 플레이어 제한
if (!isVeteran && currentClaims >= NEWBIE_MAX_CLAIMS) { // 9개
    return ClaimResult(false, "신규 플레이어는 최대 9개의 청크만 클레이밍할 수 있습니다.")
}
```

### 6.2 무료/유료 시스템
```kotlin
// 무료 슬롯 확인
val usedFreeSlots = landData.getPlayerUsedFreeSlots(playerUuid)
if (usedFreeSlots < FREE_CLAIMS_COUNT) { // 4개
    // 무료 클레이밍
    val claimCost = ClaimCost(ClaimResourceType.FREE, 0, usedFreeSlots + 1)
} else {
    // 유료 클레이밍
    val resourceType = requestedResourceType ?: ClaimResourceType.IRON_INGOT
    val amount = when (resourceType) {
        ClaimResourceType.IRON_INGOT -> IRON_COST        // 64개
        ClaimResourceType.DIAMOND -> DIAMOND_COST        // 8개
        ClaimResourceType.NETHERITE_INGOT -> NETHERITE_COST // 2개
        ClaimResourceType.FREE -> error("무료 슬롯 소진")
    }
}
```

### 6.3 환불 시스템
```kotlin
// 50% 환불 계산
fun calculateRefundItems(claimCost: ClaimCost?): List<ItemStack> {
    if (claimCost == null || claimCost.resourceType == ClaimResourceType.FREE) {
        return emptyList()
    }
    
    val refundAmount = (claimCost.amount * 0.5).toInt() // 50% 환불
    val material = when (claimCost.resourceType) {
        ClaimResourceType.IRON_INGOT -> Material.IRON_INGOT
        ClaimResourceType.DIAMOND -> Material.DIAMOND
        ClaimResourceType.NETHERITE_INGOT -> Material.NETHERITE_INGOT
        ClaimResourceType.FREE -> return emptyList()
    }
    
    return listOf(ItemStack(material, refundAmount))
}

// 안전한 아이템 지급
fun giveRefundItemsSafely(player: Player, refundItems: List<ItemStack>) {
    val failedItems = player.inventory.addItem(*refundItems.toTypedArray())
    
    if (failedItems.isNotEmpty()) {
        // 인벤토리 부족 시 드롭
        failedItems.values.forEach { item ->
            player.world.dropItemNaturally(player.location, item)
        }
        player.sendMessage("인벤토리 공간이 부족하여 환불 아이템이 드롭되었습니다.")
    }
}
```

## 7. 연결된 청크 그룹 시스템

### 7.1 연결성 감지 알고리즘
```kotlin
// 4방향 연결성 체크 (대각선 제외)
fun isConnected(chunk1: ChunkCoordinate, chunk2: ChunkCoordinate): Boolean {
    if (chunk1.worldName != chunk2.worldName) return false
    
    val dx = abs(chunk1.x - chunk2.x)
    val dz = abs(chunk1.z - chunk2.z)
    
    // 상하좌우 인접한 경우만 연결된 것으로 판단
    return (dx == 1 && dz == 0) || (dx == 0 && dz == 1)
}
```

### 7.2 Union-Find 기반 그룹 관리
```kotlin
// 연결된 청크들을 그룹으로 묶는 로직
fun updateConnectedChunks(playerUuid: UUID): Boolean {
    val playerChunks = getPlayerAllChunks(playerUuid)
    val groups = mutableListOf<Set<ChunkCoordinate>>()
    
    // Union-Find 알고리즘 적용
    for (chunk in playerChunks) {
        var connectedGroup: MutableSet<ChunkCoordinate>? = null
        
        for (group in groups) {
            if (group.any { isConnected(it, chunk) }) {
                if (connectedGroup == null) {
                    connectedGroup = group.toMutableSet()
                } else {
                    // 두 그룹을 합침
                    connectedGroup.addAll(group)
                    groups.remove(group)
                }
            }
        }
        
        if (connectedGroup != null) {
            connectedGroup.add(chunk)
            groups.add(connectedGroup)
        } else {
            groups.add(setOf(chunk))
        }
    }
    
    return saveConnectedGroups(playerUuid, groups)
}
```

### 7.3 대표 청크 시스템
```kotlin
// 그룹의 대표 청크 선정 (가장 작은 x,z 좌표)
fun getRepresentativeChunk(playerUuid: UUID, targetChunk: Chunk): Chunk? {
    val connectedGroups = landData.getPlayerConnectedChunks(playerUuid)
    val targetCoord = ChunkCoordinate(targetChunk.x, targetChunk.z, targetChunk.world.name)
    
    val containingGroup = connectedGroups.find { group ->
        group.contains(targetCoord)
    } ?: return null
    
    val representativeCoord = containingGroup.chunks.minWith(
        compareBy<ChunkCoordinate> { it.x }.thenBy { it.z }
    )
    
    val world = plugin.server.getWorld(representativeCoord.worldName)
    return world?.getChunkAt(representativeCoord.x, representativeCoord.z)
}
```

## 8. 마을 시스템 상세

### 8.1 마을 생성 과정
```kotlin
fun createVillage(player: Player, villageName: String, connectedChunks: Set<Chunk>): ClaimResult {
    // 1. 마을 이름 중복 확인
    if (landData.isVillageNameExists(villageName)) {
        return ClaimResult(false, "이미 존재하는 마을 이름입니다.")
    }
    
    // 2. 마을 정보 데이터베이스 저장
    val villageId = landData.createVillage(villageName, player.uniqueId, player.name)
        ?: return ClaimResult(false, "마을 생성 중 데이터베이스 오류가 발생했습니다.")
    
    // 3. 마을 이장을 멤버로 추가
    if (!landData.addVillageMember(villageId, player.uniqueId, player.name, VillageRole.MAYOR)) {
        return ClaimResult(false, "마을 이장 등록 중 오류가 발생했습니다.")
    }
    
    // 4. 연결된 모든 청크를 마을 토지로 전환
    for (chunk in connectedChunks) {
        val currentClaimInfo = getClaimOwner(chunk.world.name, chunk.x, chunk.z)
        if (currentClaimInfo != null) {
            val updatedClaimInfo = currentClaimInfo.copy(
                claimType = ClaimType.VILLAGE,
                villageId = villageId,
                lastUpdated = System.currentTimeMillis()
            )
            
            landData.updateClaimToVillage(updatedClaimInfo)
            // 캐시 업데이트
            updateCache(updatedClaimInfo)
        }
    }
    
    return ClaimResult(true, "마을이 성공적으로 생성되었습니다!")
}
```

### 8.2 마을 권한 시스템
```kotlin
// 역할별 기본 권한
fun getDefaultPermissions(role: VillageRole): Map<VillagePermissionType, Boolean> {
    return when (role) {
        VillageRole.MAYOR -> mapOf(
            VillagePermissionType.BUILD to true,
            VillagePermissionType.BREAK_BLOCKS to true,
            VillagePermissionType.USE_CONTAINERS to true,
            VillagePermissionType.USE_REDSTONE to true,
            VillagePermissionType.EXPAND_LAND to true,
            VillagePermissionType.INVITE_MEMBERS to true,
            VillagePermissionType.KICK_MEMBERS to true
        )
        VillageRole.DEPUTY_MAYOR -> mapOf(
            VillagePermissionType.BUILD to true,
            VillagePermissionType.BREAK_BLOCKS to true,
            VillagePermissionType.USE_CONTAINERS to true,
            VillagePermissionType.USE_REDSTONE to true,
            VillagePermissionType.EXPAND_LAND to true,
            VillagePermissionType.INVITE_MEMBERS to false,
            VillagePermissionType.KICK_MEMBERS to false
        )
        VillageRole.MEMBER -> mapOf(
            VillagePermissionType.BUILD to false,
            VillagePermissionType.BREAK_BLOCKS to false,
            VillagePermissionType.USE_CONTAINERS to false,
            VillagePermissionType.USE_REDSTONE to false,
            VillagePermissionType.EXPAND_LAND to false,
            VillagePermissionType.INVITE_MEMBERS to false,
            VillagePermissionType.KICK_MEMBERS to false
        )
    }
}
```

### 8.3 마을 토지 확장
```kotlin
fun claimChunkForVillage(player: Player, chunk: Chunk, resourceType: ClaimResourceType? = null): ClaimResult {
    // 1. 플레이어가 마을 구성원인지 확인
    val membership = getPlayerVillageMembership(player.uniqueId)
        ?: return ClaimResult(false, "마을 구성원만 마을 토지를 클레이밍할 수 있습니다.")
    
    val villageId = membership.villageId
    
    // 2. 마을 토지 확장 권한 확인
    if (!hasVillageExpandPermission(player.uniqueId, villageId)) {
        return ClaimResult(false, "마을 토지 확장 권한이 없습니다.")
    }
    
    // 3. 청크가 기존 마을 토지와 연결되어 있는지 확인
    if (!isChunkConnectedToVillage(chunk, villageId)) {
        return ClaimResult(false, "마을 토지는 기존 마을 영역과 연결되어야 합니다.")
    }
    
    // 4. 일반 클레이밍 과정과 동일 (비용 지불 등)
    // 5. 마을 토지로 설정
    val villageInfo = getVillageInfo(villageId)!!
    val claimInfo = AdvancedClaimInfo(
        chunkX = chunk.x,
        chunkZ = chunk.z,
        worldName = chunk.world.name,
        ownerUuid = villageInfo.mayorUuid,
        ownerName = "${villageInfo.villageName} (마을)",
        claimType = ClaimType.VILLAGE,
        createdAt = System.currentTimeMillis(),
        lastUpdated = System.currentTimeMillis(),
        villageId = villageId,
        claimCost = claimCost
    )
    
    return if (landData.saveClaim(claimInfo)) {
        ClaimResult(true, "마을 토지가 성공적으로 확장되었습니다!")
    } else {
        ClaimResult(false, "데이터베이스 저장 중 오류가 발생했습니다.")
    }
}
```

## 9. MyLand 시스템과의 통합

### 9.1 친구 시스템 연동
```kotlin
// AdvancedLandProtectionListener에서 개인 토지 친구 권한 확인
if (claimInfo.claimType == ClaimType.PERSONAL) {
    val landManager = advancedLandManager.getLandManager()
    
    if (landManager != null) {
        if (landManager.isMember(chunk, player)) {
            return // 친구로 등록되어 있음 - 행동 허용
        }
    }
}
```

### 9.2 시스템 간 연결 설정
```kotlin
// Main.kt에서 양방향 연결 설정
advancedLandSystem?.let { advancedLand ->
    val advancedLandManager = advancedLand.getAdvancedLandManager()
    if (advancedLandManager != null) {
        // AdvancedLand → MyLand 연결
        privateLand.setAdvancedLandManager(advancedLandManager)
        
        // MyLand → AdvancedLand 연결 (친구 시스템 사용을 위해)
        advancedLandManager.setLandManager(privateLand.getLandManager())
    }
}
```

## 10. 명령어 사용법

### 10.1 기본 클레이밍 명령어
- `/고급땅 클레임 [자원타입]` - 청크 클레이밍 (철괴/다이아/네더라이트)
- `/고급땅 포기` - 청크 포기 (50% 환불)
- `/고급땅 정보` - 현재 청크 정보
- `/고급땅 목록` - 내 토지 목록
- `/고급땅 비용` - 클레이밍 비용 정보

### 10.2 마을 관리 명령어
- `/고급땅 마을생성 <마을이름>` - 연결된 개인 토지를 마을로 전환
- `/고급땅 마을정보 [마을ID]` - 마을 정보 조회
- `/고급땅 마을초대 <플레이어>` - 마을 멤버 초대
- `/고급땅 마을추방 <플레이어>` - 마을 멤버 추방
- `/고급땅 마을설정` - 마을 설정 GUI 열기
- `/고급땅 마을클레임 [자원타입]` - 마을 토지 확장
- `/고급땅 마을반환` - 마을 토지 반환

### 10.3 연결성 관련 명령어
- `/고급땅 연결정보` - 현재 청크의 연결 그룹 정보
- `/고급땅 그룹목록` - 내가 소유한 모든 연결 그룹

### 10.4 사용 예시
```
# 철괴로 청크 클레이밍
/고급땅 클레임 철괴

# 마을 생성
/고급땅 마을생성 우리마을

# 마을 멤버 초대
/고급땅 마을초대 steve

# 마을 토지 확장
/고급땅 마을클레임 다이아

# 토지 포기 (50% 환불)
/고급땅 포기
```

## 11. GUI 시스템

### 11.1 마을 설정 GUI 구조
```
┌─────────────────────────────────┐
│      [마을이름] 설정 메뉴         │
├─────────────────────────────────┤
│ [정보] [멤버] [권한] [토지] [설정] │
│                                 │
│ 마을 정보:                      │
│ - 이장: [이장이름]               │
│ - 멤버 수: [X명]                │
│ - 토지 수: [Y개]                │
│ - 생성일: [날짜]                │
│                                 │
│ [마을 해체] [이장 양도] [닫기]    │
└─────────────────────────────────┘
```

### 11.2 권한 설정 GUI
```
┌─────────────────────────────────┐
│      [플레이어명] 권한 설정       │
├─────────────────────────────────┤
│ 역할: [이장/부이장/마을원]       │
│                                 │
│ 권한:                          │
│ [✓] 건축 권한                   │
│ [✓] 파괴 권한                   │
│ [✗] 컨테이너 사용               │
│ [✗] 레드스톤 사용               │
│ [✗] 토지 확장                   │
│                                 │
│ [저장] [초기화] [뒤로가기]       │
└─────────────────────────────────┘
```

## 12. 설정 및 최적화

### 12.1 시스템 상수 설정
```kotlin
companion object {
    const val FREE_CLAIMS_COUNT = 4              // 무료 클레이밍 수
    const val NEWBIE_MAX_CLAIMS = 9              // 신규 플레이어 최대 클레이밍
    const val VETERAN_DAYS_THRESHOLD = 7         // 베테랑 플레이어 기준 (일)
    
    const val IRON_COST = 64                     // 철괴 64개
    const val DIAMOND_COST = 8                   // 다이아몬드 8개
    const val NETHERITE_COST = 2                 // 네더라이트 주괴 2개
}
```

### 12.2 캐싱 시스템
```kotlin
// 메모리 캐시로 성능 최적화
private val claimedChunks = mutableMapOf<String, MutableMap<Pair<Int, Int>, AdvancedClaimInfo>>()
private val playerClaims = mutableMapOf<UUID, MutableList<AdvancedClaimInfo>>()

// 캐시 동기화
fun loadClaimsFromDatabase() {
    val loadedClaims = landData.loadAllClaims()
    claimedChunks.clear()
    playerClaims.clear()
    claimedChunks.putAll(loadedClaims)
    
    // 플레이어별 캐시 구성
    loadedClaims.forEach { (worldName, chunks) ->
        chunks.forEach { (chunkCoords, claimInfo) ->
            playerClaims.computeIfAbsent(claimInfo.ownerUuid) { mutableListOf() }
                .add(claimInfo)
        }
    }
}
```

## 13. 트러블슈팅

### 13.1 일반적인 문제들

**Q: 친구가 개인 토지에서 건축할 수 없습니다.**
A: Main.kt에서 AdvancedLandManager와 LandManager의 양방향 연결이 설정되었는지 확인하세요.

**Q: 마을 생성이 안 됩니다.**
A: 
1. 연결된 개인 토지가 있는지 확인
2. 마을 이름이 중복되지 않는지 확인
3. 데이터베이스 테이블이 올바르게 생성되었는지 확인

**Q: 환불이 제대로 안 됩니다.**
A: 무료로 클레이밍한 토지는 환불되지 않습니다. 자원을 사용한 토지만 50% 환불됩니다.

**Q: 마을 토지 확장이 안 됩니다.**
A:
1. 마을 토지 확장 권한이 있는지 확인
2. 확장하려는 청크가 기존 마을 토지와 연결되어 있는지 확인
3. 4방향 연결성만 인정됨 (대각선 불가)

### 13.2 디버깅 도구
```kotlin
// 연결성 확인
val connectedGroups = advancedLandManager.getPlayerConnectedChunks(playerUuid)
debugManager.log("AdvancedLand", "Player $playerUuid has ${connectedGroups.size} connected groups")

// 권한 확인
val hasPermission = advancedLandManager.hasVillagePermission(playerUuid, villageId, VillagePermissionType.BUILD)
debugManager.log("AdvancedLand", "Player $playerUuid build permission: $hasPermission")

// 캐시 상태 확인
advancedLandManager.loadClaimsFromDatabase()  // 캐시 리로드
```

## 14. 향후 개발 계획

### 14.1 추가 예정 기능
- 마을 해체 시스템
- 이장 양도 시스템
- 마을 초대 수락/거절 시스템
- 개인 토지 반환 시스템 (환불 포함)
- 마을별 설정 시스템 (PvP, 폭발 등)

### 14.2 성능 최적화
- 대용량 연결성 체크 최적화
- 권한 캐싱 시스템
- 비동기 데이터베이스 처리

### 14.3 확장성
- 다른 플러그인과의 API 연동
- 웹 대시보드 연동
- 통계 시스템 연동

## 15. 개발 가이드라인

### 15.1 코드 작성 원칙
- **안전성 우선**: 모든 데이터베이스 작업에 트랜잭션 사용
- **확장성 고려**: 새로운 기능 추가 시 기존 코드 영향 최소화
- **성능 최적화**: 캐싱과 배치 처리 적극 활용
- **오류 처리**: 명확한 오류 메시지와 로깅

### 15.2 테스트 권장사항
- 연결성 알고리즘 단위 테스트
- 권한 시스템 통합 테스트
- 대용량 데이터 성능 테스트
- MyLand 시스템과의 통합 테스트

이 문서를 바탕으로 AdvancedLandClaiming 시스템을 완전히 이해하고 개발/유지보수할 수 있습니다.
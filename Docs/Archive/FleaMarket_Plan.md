# 플리마켓 시스템 기능정의서 (Flea Market System)

## 📋 개요
플리마켓(중고장터) 시스템은 플레이어 간 아이템 거래를 가능하게 하는 P2P 마켓 시스템입니다.  
플레이어가 보유한 아이템을 원하는 가격에 등록하고, 다른 플레이어가 구매할 수 있습니다.

---

## 🎯 핵심 기능

### 1. **아이템 등록 (판매)**
- 플레이어가 인벤토리에 보유한 아이템을 마켓에 등록
- 등록 시 판매 가격 설정 가능
- 등록된 아이템은 플레이어 인벤토리에서 제거되고 마켓에 저장
- 등록 가능한 최대 개수 제한 (예: 플레이어당 10개)

### 2. **아이템 구매**
- 다른 플레이어가 등록한 아이템을 구매
- 구매자는 설정된 가격만큼 돈을 지불
- 구매 완료 시:
  - 구매자: 돈 차감 + 아이템 획득
  - 판매자: 돈 지급 (오프라인에도 적용)
- 잔액 부족 시 구매 불가

### 3. **아이템 회수**
- 판매자가 판매되지 않은 아이템을 회수 가능
- 회수 시 아이템은 즉시 인벤토리로 반환
- 회수 후 마켓 리스트에서 즉시 제거

### 4. **마켓 GUI**
- 등록된 아이템 목록 표시
- 아이템 정보 표시: 판매자, 가격, 아이템 이름/설명
- 필터링/정렬 기능 (가격순, 최신순 등)
- 자신이 등록한 아이템과 다른 플레이어 아이템 구분 표시

### 5. **거래 내역 조회**
- 플레이어의 판매/구매 기록 조회 가능
- 거래 유형별 필터링 (판매, 구매, 회수)
- 거래 시간, 상대방, 아이템, 가격 정보 표시
- GUI를 통한 직관적인 거래 내역 확인

### 6. **판매 알림 시스템**
- 플레이어의 아이템이 판매되었을 때 실시간 알림
- **온라인 상태**: 아이템 판매 즉시 채팅으로 알림
- **오프라인 → 온라인**: 접속 시 미확인 판매 내역 표시
- 알림 내용: 판매된 아이템, 구매자, 판매 가격
- **표시 시점**: 채팅 청소 후, 환영 메시지 및 기타 안내 메시지 표시 이후
- 알림 확인 후 `is_notified` 플래그 업데이트

---

## 🗄️ 데이터 구조

### MarketItem (마켓 아이템 엔티티)
```kotlin
data class MarketItem(
    val id: Int,                    // 고유 ID (AUTO_INCREMENT)
    val sellerUuid: UUID,           // 판매자 UUID
    val sellerName: String,         // 판매자 이름 (표시용)
    val itemStack: String,          // 아이템 직렬화 데이터 (Base64)
    val price: Double,              // 판매 가격
    val registeredAt: Long          // 등록 시간 (timestamp)
)
```

### MarketLog (거래 기록 엔티티)
```kotlin
data class MarketLog(
    val id: Int,                    // 고유 ID (AUTO_INCREMENT)
    val playerUuid: UUID,           // 거래한 플레이어 UUID
    val playerName: String,         // 거래한 플레이어 이름
    val transactionType: MarketTransactionType, // 거래 유형 (판매/구매/회수)
    val itemName: String,           // 아이템 이름
    val itemData: String,           // 아이템 직렬화 데이터 (선택적)
    val price: Double,              // 거래 가격
    val counterpartUuid: UUID?,     // 거래 상대방 UUID (구매/판매 시)
    val counterpartName: String?,   // 거래 상대방 이름
    val transactionAt: Long,        // 거래 시간 (timestamp)
    val isNotified: Boolean = false // 알림 확인 여부 (SELL 타입에만 사용)
)
```

### MarketTransactionType (거래 유형)
```kotlin
enum class MarketTransactionType {
    REGISTER,   // 아이템 등록
    SELL,       // 판매 완료 (판매자 입장)
    BUY,        // 구매 완료 (구매자 입장)
    WITHDRAW    // 회수
}
```

### 데이터베이스 테이블: `flea_market`
| 컬럼명 | 타입 | 설명 | 제약조건 |
|--------|------|------|----------|
| id | INT | 고유 ID | PRIMARY KEY, AUTO_INCREMENT |
| seller_uuid | VARCHAR(36) | 판매자 UUID | NOT NULL |
| seller_name | VARCHAR(16) | 판매자 이름 | NOT NULL |
| item_data | TEXT | 아이템 직렬화 데이터 | NOT NULL |
| price | DOUBLE | 판매 가격 | NOT NULL, >= 0 |
| registered_at | BIGINT | 등록 시간 | NOT NULL |

### 데이터베이스 테이블: `market_logs`
| 컬럼명 | 타입 | 설명 | 제약조건 |
|--------|------|------|----------|
| id | INT | 고유 ID | PRIMARY KEY, AUTO_INCREMENT |
| player_uuid | VARCHAR(36) | 거래한 플레이어 UUID | NOT NULL |
| player_name | VARCHAR(16) | 거래한 플레이어 이름 | NOT NULL |
| transaction_type | VARCHAR(20) | 거래 유형 | NOT NULL |
| item_name | VARCHAR(255) | 아이템 이름 | NOT NULL |
| item_data | TEXT | 아이템 직렬화 데이터 | NULL |
| price | DOUBLE | 거래 가격 | NOT NULL |
| counterpart_uuid | VARCHAR(36) | 거래 상대방 UUID | NULL |
| counterpart_name | VARCHAR(16) | 거래 상대방 이름 | NULL |
| transaction_at | BIGINT | 거래 시간 | NOT NULL |
| is_notified | TINYINT(1) | 알림 확인 여부 (SELL 타입 전용) | DEFAULT 0 |

**인덱스**:
- `idx_player_uuid`: player_uuid에 인덱스 (조회 성능 향상)
- `idx_transaction_at`: transaction_at에 인덱스 (시간순 정렬 성능 향상)
- `idx_is_notified`: is_notified에 인덱스 (미확인 판매 조회 성능 향상)

---

## 🏗️ 시스템 아키텍처

### 계층 구조
```
FleaMarketManager (진입점)
    ├─ FleaMarketService (비즈니스 로직)
    │   └─ MarketCache (메모리 캐시)
    ├─ FleaMarketRepository (DB 처리)
    ├─ FleaMarketGUI (GUI 구현)
    └─ FleaMarketCommand (명령어 처리)
```

### 클래스 설계

#### 1. FleaMarketManager
```kotlin
class FleaMarketManager(
    private val plugin: LukeVanilla,
    private val economyManager: EconomyManager
) {
    val service: FleaMarketService
    val gui: FleaMarketGUI
    
    fun initialize()
    fun shutdown()
}
```

#### 2. FleaMarketService
```kotlin
class FleaMarketService(
    private val repository: FleaMarketRepository,
    private val economyManager: EconomyManager
) {
    // 아이템 등록
    fun registerItem(seller: Player, itemStack: ItemStack, price: Double): Boolean
    
    // 아이템 구매
    fun purchaseItem(buyer: Player, itemId: Int): Boolean
    
    // 아이템 회수
    fun withdrawItem(seller: Player, itemId: Int): Boolean
    
    // 모든 아이템 조회
    fun getAllItems(): List<MarketItem>
    
    // 판매자별 아이템 조회
    fun getItemsBySeller(uuid: UUID): List<MarketItem>
    
    // 거래 내역 조회
    fun getPlayerLogs(uuid: UUID, limit: Int = 50): List<MarketLog>
    
    // 거래 유형별 내역 조회
    fun getPlayerLogsByType(uuid: UUID, type: MarketTransactionType, limit: Int = 50): List<MarketLog>
    
    // === 판매 알림 관련 ===
    // 미확인 판매 내역 조회
    fun getUnnotifiedSales(uuid: UUID): List<MarketLog>
    
    // 판매 알림 확인 처리
    fun markSalesAsNotified(uuid: UUID)
    
    // 온라인 플레이어에게 즉시 판매 알림 전송
    fun sendInstantSaleNotification(seller: Player, itemName: String, buyerName: String, price: Double)
    
    // 캐시 로드
    fun loadCache()
}
```

#### 3. FleaMarketRepository
```kotlin
class FleaMarketRepository(private val database: Database) {
    // === 아이템 관련 ===
    // 아이템 등록
    fun insertItem(item: MarketItem): CompletableFuture<Int>
    
    // 아이템 삭제 (구매 또는 회수)
    fun deleteItem(itemId: Int): CompletableFuture<Boolean>
    
    // 모든 아이템 조회
    fun getAllItemsAsync(): CompletableFuture<List<MarketItem>>
    
    // 판매자별 아이템 조회
    fun getItemsBySellerAsync(uuid: UUID): CompletableFuture<List<MarketItem>>
    
    // 특정 아이템 조회
    fun getItemByIdAsync(itemId: Int): CompletableFuture<MarketItem?>
    
    // === 거래 로그 관련 ===
    // 거래 기록 삽입
    fun insertLog(log: MarketLog): CompletableFuture<Void>
    
    // 플레이어의 거래 내역 조회
    fun getPlayerLogsAsync(uuid: UUID, limit: Int): CompletableFuture<List<MarketLog>>
    
    // 유형별 거래 내역 조회
    fun getPlayerLogsByTypeAsync(uuid: UUID, type: MarketTransactionType, limit: Int): CompletableFuture<List<MarketLog>>
    
    // === 판매 알림 관련 ===
    // 미확인 판매 내역 조회 (SELL 타입, is_notified = 0)
    fun getUnnotifiedSalesAsync(uuid: UUID): CompletableFuture<List<MarketLog>>
    
    // 특정 플레이어의 모든 미확인 판매 로그를 is_notified = 1로 업데이트
    fun markSalesAsNotifiedAsync(uuid: UUID): CompletableFuture<Void>
    
    // === 테이블 초기화 ===
    fun initializeTables()
}
```

#### 4. FleaMarketGUI
```kotlin
class FleaMarketGUI(
    private val service: FleaMarketService
) : Listener {
    // 마켓 메인 GUI 열기
    fun openMarket(player: Player)
    
    // 거래 내역 GUI 열기
    fun openTransactionHistory(player: Player)
    
    // GUI 내용 업데이트
    fun updateInventory(inventory: Inventory)
    
    // 클릭 이벤트 처리
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent)
}
```

---

## 🔄 주요 프로세스 흐름

### 1. 아이템 등록 프로세스
```
플레이어 등록 요청
    ↓
손에 들고 있는 아이템 확인
    ↓
가격 유효성 검증 (0 이상)
    ↓
등록 개수 제한 확인
    ↓
아이템 직렬화 (Base64)
    ↓
DB에 아이템 저장
    ↓
메모리 캐시 업데이트
    ↓
플레이어 인벤토리에서 아이템 제거
    ↓
[거래 로그 기록: REGISTER]
    ↓
등록 완료 메시지
```

### 2. 아이템 구매 프로세스
```
플레이어 구매 요청
    ↓
아이템 존재 여부 확인
    ↓
자기 자신이 등록한 아이템인지 확인
    ↓
구매자 잔액 확인
    ↓
구매자 인벤토리 여유 공간 확인
    ↓
[트랜잭션 시작]
    ├─ 구매자 돈 차감 (EconomyService.withdraw)
    ├─ 판매자 돈 지급 (오프라인 처리)
    ├─ DB에서 아이템 삭제
    ├─ 메모리 캐시에서 제거
    ├─ [구매자 거래 로그 기록: BUY]
    └─ [판매자 거래 로그 기록: SELL, is_notified=0]
[트랜잭션 완료]
    ↓
아이템 역직렬화
    ↓
구매자 인벤토리에 아이템 지급
    ↓
[판매 알림 처리]
    ├─ 판매자 온라인 여부 확인
    ├─ 온라인: 즉시 채팅 알림 전송 + is_notified=1 업데이트
    └─ 오프라인: is_notified=0 유지 (다음 접속 시 표시)
    ↓
구매 완료 메시지 (구매자 + 판매자)
```

### 3. 아이템 회수 프로세스
```
플레이어 회수 요청
    ↓
아이템 존재 여부 확인
    ↓
본인이 등록한 아이템인지 확인
    ↓
플레이어 인벤토리 여유 공간 확인
    ↓
DB에서 아이템 삭제
    ↓
메모리 캐시에서 제거
    ↓
아이템 역직렬화
    ↓
플레이어 인벤토리에 반환
    ↓
[거래 로그 기록: WITHDRAW]
    ↓
회수 완료 메시지
```

### 4. 플레이어 접속 시 판매 알림 프로세스
```
플레이어 접속 (PlayerJoinEvent)
    ↓
60틱(3초) 후 스케줄러 실행
    ↓
채팅 청소 (100줄 공백)
    ↓
환영 메시지 표시
    ↓
기타 안내 메시지들 표시
    ├─ 문의 케이스 알림
    ├─ 아이템 복구 안내
    └─ 지도 사이트 링크 등
    ↓
[플리마켓 판매 알림 표시]
    ↓
미확인 판매 내역 조회 (is_notified=0 인 SELL 로그)
    ↓
판매 내역이 있는가?
    ├─ 없음: 프로세스 종료
    └─ 있음: 계속 진행
        ↓
    각 판매 내역별로 채팅 메시지 전송
        ├─ "§a§l[마켓] §f{itemName}§a이(가) §f{buyerName}§a님에게 §f{price}원§a에 판매되었습니다!"
        └─ 여러 건인 경우 모두 표시
        ↓
    모든 알림 표시 완료
        ↓
    is_notified = 1로 업데이트 (markSalesAsNotified)
        ↓
    알림 처리 완료
```

---

## 💰 경제 시스템 연동

### TransactionType 추가
```kotlin
enum class TransactionType {
    // 기존 타입들...
    
    MARKET_SELL,      // 마켓 판매 수익
    MARKET_BUY,       // 마켓 구매 지출
}
```

### 구매 시 경제 처리
```kotlin
// 구매자: 돈 차감
economyManager.withdraw(
    player = buyer,
    amount = item.price,
    type = TransactionType.MARKET_BUY,
    description = "마켓 구매: ${item.sellerName}의 ${itemName}"
)

// 판매자: 돈 지급 (오프라인 처리)
economyManager.service.deposit(
    uuid = item.sellerUuid,
    amount = item.price,
    type = TransactionType.MARKET_SELL,
    description = "마켓 판매: ${buyer.name}에게 ${itemName}"
)
```

### Player_Join_And_Quit_Message_Listener 연동
플레이어 접속 시 판매 알림을 표시하기 위해 기존 `Player_Join_And_Quit_Message_Listener`와 연동합니다.

**연동 위치**:
- `Player_Join_And_Quit_Message_Listener.kt`의 `onPlayerJoin` 이벤트 핸들러 내
- 스케줄러 (60틱) 실행 블록 내부
- 기존 안내 메시지들 (지도 링크 등) 표시 이후

**연동 코드 예시**:
```kotlin
// Player_Join_And_Quit_Message_Listener.kt의 60틱 스케줄러 내부
Bukkit.getScheduler().runTaskLater(plugin, Runnable {
    // ... (기존 코드: 채팅 청소, 환영 메시지, 문의 케이스 등) ...
    
    player.spigot().sendMessage(mapLink)
    player.sendMessage("")
    
    // === 플리마켓 판매 알림 추가 ===
    val fleaMarketManager = plugin.fleaMarketManager  // 플러그인에서 FleaMarketManager 인스턴스 가져오기
    val unnotifiedSales = fleaMarketManager.service.getUnnotifiedSales(player.uniqueId)
    
    if (unnotifiedSales.isNotEmpty()) {
        player.sendMessage("")
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        player.sendMessage("§a§l               [플리마켓 판매 알림]")
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        unnotifiedSales.forEach { sale ->
            player.sendMessage("  §a§l[판매완료] §f${sale.itemName}§a이(가) §f${sale.counterpartName}§a님에게 §f${sale.price}원§a에 판매되었습니다!")
        }
        
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        player.sendMessage("")
        
        // 알림 확인 처리
        fleaMarketManager.service.markSalesAsNotified(player.uniqueId)
    }
}, 60L)
```

---

## 🎨 GUI 설계

### 기본 레이아웃 (마켓 메인 GUI - 6줄 인벤토리)
```
[0-44] 마켓 아이템 표시
[45] 새로고침
[46] 내 상품
[47] 거래 내역
[48] 공백
[49] 정렬 방식
[50-52] 공백
[53] 닫기
```

### 거래 내역 GUI (6줄 인벤토리)
```
[0-44] 거래 내역 표시
[45] 새로고침
[46] 판매 내역
[47] 구매 내역
[48] 회수 내역
[49] 전체 보기
[50-52] 공백
[53] 뒤로가기
```

### 아이템 표시 정보
- **아이템**: 실제 등록된 아이템
- **Lore**:
  - `판매자: {sellerName}`
  - `가격: {price}원`
  - `등록일: {registeredAt}`
  - `좌클릭: 구매` (다른 사람 아이템)
  - `우클릭: 회수` (본인 아이템)

### 거래 내역 표시 정보
- **아이템**: 거래한 아이템 아이콘
- **Lore**:
  - `거래 유형: {판매/구매/회수}`
  - `상대방: {counterpartName}` (판매/구매 시)
  - `가격: {price}원`
  - `거래 시간: {transactionAt}`

### GUI 상호작용
- **좌클릭**: 아이템 구매 (타인 아이템)
- **우클릭**: 아이템 회수 (본인 아이템)
- **새로고침 버튼**: GUI 갱신
- **내 상품 버튼**: 본인이 등록한 아이템만 필터링
- **거래 내역 버튼**: 거래 내역 GUI 열기
- **정렬 버튼**: 가격순/최신순 전환
- **필터 버튼** (거래 내역): 판매/구매/회수/전체 필터링

---

## 📝 명령어

### 플레이어 명령어
| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/market` 또는 `/플마` | 마켓 GUI 열기 | 기본 권한 |
| `/market sell <가격>` | 손에 든 아이템 등록 | 기본 권한 |
| `/market history` | 거래 내역 조회 | 기본 권한 |
| `/market history <유형>` | 특정 유형 거래 내역 조회 (sell/buy/withdraw) | 기본 권한 |

### 관리자 명령어
| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/market clear` | 모든 마켓 아이템 초기화 | OP |
| `/market remove <itemId>` | 특정 아이템 제거 | OP |

---

## ⚠️ 예외 처리 및 검증

### 아이템 등록 시
- [ ] 손에 아이템이 없는 경우
- [ ] 가격이 0 이하인 경우
- [ ] 등록 개수 제한 초과
- [ ] 인벤토리에서 아이템 제거 실패

### 아이템 구매 시
- [ ] 아이템이 존재하지 않는 경우 (이미 판매됨)
- [ ] 자기 자신이 등록한 아이템 구매 시도
- [ ] 잔액 부족
- [ ] 인벤토리 공간 부족
- [ ] 아이템 역직렬화 실패

### 아이템 회수 시
- [ ] 아이템이 존재하지 않는 경우
- [ ] 본인이 등록한 아이템이 아닌 경우
- [ ] 인벤토리 공간 부족
- [ ] 아이템 역직렬화 실패

---

## 🔒 보안 및 안정성

### 동시성 처리
- 동일 아이템에 대한 동시 구매 방지
  - DB 트랜잭션 또는 락 메커니즘 사용
  - 메모리 캐시 동기화

### 데이터 무결성
- 아이템 직렬화/역직렬화 안정성
- 판매자 오프라인 시 잔액 지급 보장
- DB 저장 실패 시 롤백 처리

### 악용 방지
- 비정상적인 가격 설정 방지 (최소/최대 가격 제한)
- 등록 개수 제한으로 스팸 방지
- 아이템 복사 버그 방지

---

## 📊 성능 최적화

### 메모리 캐싱
- 서버 시작 시 전체 아이템 로드
- 아이템 등록/삭제 시 캐시 업데이트
- 주기적 캐시 동기화 (옵션)

### 비동기 처리
- DB 작업은 모두 비동기로 처리
- CompletableFuture 활용

### GUI 최적화
- 페이지네이션 구현 (아이템 많을 경우)
- 필터링/정렬은 메모리 캐시 기반

---

## 🚀 구현 우선순위

### Phase 1: 기본 기능 (MVP)
1. ✅ 데이터베이스 테이블 설계 및 생성 (`flea_market`, `market_logs`)
2. ✅ Repository 계층 구현 (아이템 + 로그)
3. ✅ Service 계층 구현 (등록/구매/회수 + 로그 기록)
4. ✅ 기본 GUI 구현 (마켓 메인)
5. ✅ 명령어 구현

### Phase 2: 품질 개선
1. ⏳ 예외 처리 강화
2. ⏳ 메시지 시스템 구현
3. ⏳ 동시성 처리 개선
4. ⏳ 로깅 추가
5. ⏳ 거래 내역 GUI 구현
6. ⏳ 거래 내역 조회 명령어 구현

### Phase 3: 고급 기능
1. ⏳ 페이지네이션
2. ⏳ 고급 필터링/검색
3. ⏳ 카테고리 분류
4. ⏳ 거래 수수료 시스템
5. ⏳ 거래 통계 기능

---

## 📌 참고사항

### 아이템 직렬화
- Bukkit의 `ItemStack.serialize()` 및 `ItemStack.deserialize()` 사용
- 또는 Base64 인코딩 방식 사용
```kotlin
// 직렬화
fun serializeItemStack(item: ItemStack): String {
    val outputStream = ByteArrayOutputStream()
    val dataOutput = BukkitObjectOutputStream(outputStream)
    dataOutput.writeObject(item)
    dataOutput.close()
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}

// 역직렬화
fun deserializeItemStack(data: String): ItemStack {
    val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(data))
    val dataInput = BukkitObjectInputStream(inputStream)
    val item = dataInput.readObject() as ItemStack
    dataInput.close()
    return item
}
```

### 오프라인 플레이어 처리
- 판매자가 오프라인이어도 구매 가능
- DB에 직접 잔액 업데이트
- 다음 접속 시 캐시 로드로 자동 반영

---

## 🎯 완료 체크리스트

### 데이터베이스
- [ ] `flea_market` 테이블 생성
- [ ] `market_logs` 테이블 생성
- [ ] 인덱스 설정 (player_uuid, transaction_at)

### 백엔드
- [ ] MarketItem 엔티티 구현
- [ ] MarketLog 엔티티 구현
- [ ] MarketTransactionType enum 구현
- [ ] Repository 클래스 구현 (아이템 관련)
- [ ] Repository 클래스 구현 (로그 관련)
- [ ] Service 클래스 구현 (비즈니스 로직)
- [ ] TransactionType 추가 (MARKET_BUY, MARKET_SELL)

### GUI
- [ ] 마켓 메인 GUI 구현
- [ ] 거래 내역 GUI 구현
- [ ] 필터링/정렬 기능 구현

### 명령어
- [ ] `/market` 명령어 구현
- [ ] `/market sell` 명령어 구현
- [ ] `/market history` 명령어 구현
- [ ] 관리자 명령어 구현

### 품질
- [ ] 예외 처리 구현
- [ ] 동시성 처리 구현
- [ ] 메시지 시스템 구현
- [ ] 테스트 완료
- [ ] 문서화 완료

---

**작성일**: 2025-11-21  
**버전**: 1.0  
**작성자**: LukeVanilla Development Team

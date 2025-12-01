# 돈 시스템 개선 계획서 (Money System Improvement Plan)

## 1. 개요 (Overview)
현재 단순한 형태의 경제 시스템을 확장성 있고, 안정적이며, 성능이 최적화된 시스템으로 리팩토링하기 위한 계획서입니다.
기존 코드는 동기식 데이터베이스 처리로 인한 메인 스레드 블로킹 위험과 트랜잭션 안전성 부족 등의 문제가 있어 이를 개선하고자 합니다.

## 2. 현행 시스템 분석 (AS-IS Analysis)

### 2.1 구조
- **EconomyManager**: 데이터베이스와 직접 통신하여 잔액 조회 및 수정을 담당.
- **MoneyCommand**: `/돈` 명령어를 처리하며 비즈니스 로직을 일부 포함.
- **Database**: HikariCP를 사용한 커넥션 풀링을 지원하지만, 모든 호출이 동기식(Synchronous)으로 이루어짐.

### 2.2 문제점
1.  **메인 스레드 블로킹 (Blocking I/O)**:
    - `MoneyCommand`에서 `economyManager.getBalance` 등을 호출할 때 메인 스레드에서 DB 쿼리가 실행됨.
    - DB 응답이 지연되면 서버 전체가 멈추는(Lag) 현상 발생 가능.
2.  **트랜잭션 부재 (Lack of Transactions)**:
    - 송금 기능(`보내기`) 구현 시, `removeBalance` 후 `addBalance`를 별도로 호출함.
    - 보내는 사람의 돈은 차감되었으나, 받는 사람에게 지급하는 과정에서 오류가 발생하면 돈이 증발할 수 있음.
3.  **캐싱 미비 (No Caching)**:
    - 잔액을 확인하거나 변경할 때마다 매번 DB에 쿼리를 날림.
    - 접속자가 많아지면 DB 부하가 급증함.
4.  **기능 확장성 부족**:
    - 단순 잔액 저장 외에 거래 내역(Log), 관리자 기능, 다중 통화 등의 확장이 어려움.

## 3. 개선 목표 (TO-BE Goals)

### 3.1 핵심 목표
- **비동기 처리 (Asynchronous Processing)**: 모든 DB 입출력을 별도 스레드에서 처리하여 서버 성능 저하 방지.
- **데이터 무결성 (Data Integrity)**: 트랜잭션을 도입하여 송금 시 원자성(Atomicity) 보장.
- **고성능 (High Performance)**: 로컬 캐시(In-memory Cache)를 도입하여 DB 부하 최소화.
- **확장성 (Extensibility)**: Vault API 연동 고려 및 추후 기능 확장을 위한 구조 설계.

## 4. 기능 정의 (Functional Specifications)

### 4.1 기본 기능
1.  **잔액 조회**: 자신의 잔액 또는 타인의 잔액(관리자 권한) 조회.
2.  **송금**: 오프라인/온라인 플레이어에게 안전하게 돈 보내기.
3.  **관리자 명령어**:
    - *관리자 개입 최소화 원칙에 따라 임의로 돈을 생성/삭제하는 명령어는 제공하지 않음.*
    - 오직 시스템(상점, 룰렛 등)에 의해서만 돈이 변동되도록 함.
4.  **순위(랭킹)**: 부자 순위 확인 기능 (캐시 기반).
5.  **거래 내역 (통장 기록)**:
    - **핵심 요구사항**: 모든 돈의 흐름(룰렛, 송금, 상점 이용 등)을 은행 통장 내역처럼 기록해야 함.
    - **목적**: 유저가 "돈이 사라졌다"고 문의했을 때, 정확히 언제, 어디서, 무엇 때문에 돈이 변동되었는지 추적 가능해야 함.
    - **기록 항목**:
        - 거래 일시
        - 거래 유형 (송금, 상점구매, 룰렛, 관리자지급 등)
        - 변동 금액 (+/-)
        - 변동 후 잔액 (Balance Snapshot) -> *통장처럼 잔액 흐름 파악 가능*
        - 비고 (상대방 이름, 구매 물품명 등 상세 사유)

### 4.2 고급 기능 (선택 사항)
1.  **이자 시스템**: 일정 시간마다 이자 지급.
2.  **수표 시스템**: 돈을 아이템(종이)으로 변환하여 거래.

## 5. 기술적 아키텍처 (Technical Architecture)

### 5.1 데이터 흐름
1.  **접속 시 (Join)**: DB에서 플레이어 데이터를 비동기로 로드하여 메모리(Cache)에 저장.
2.  **조회/사용 시**: 메모리에 있는 데이터를 즉시 반환/수정 (매우 빠름).
3.  **저장 시 (Save)**:
    - 주기적 저장 (Auto-save task).
    - 접속 종료 시 (Quit) 저장.
    - 중요 거래 발생 시 즉시 비동기 저장.

### 5.2 데이터베이스 스키마 변경 (자동 마이그레이션 포함)
시스템 시작 시 테이블 존재 여부 및 컬럼 변경 사항을 확인하여 **자동으로 DB 구조를 업데이트**해야 합니다.

```sql
-- 기존 테이블 (유지)
CREATE TABLE IF NOT EXISTS player_balance (
    uuid VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(20, 2) DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- (신규) 거래 내역 테이블 (통장 기록)
CREATE TABLE IF NOT EXISTS economy_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,      -- 거래 주체
    transaction_type VARCHAR(32) NOT NULL, -- SEND, RECEIVE, SHOP_BUY, ROULETTE, ADMIN, etc.
    amount DECIMAL(20, 2) NOT NULL,        -- 변동 금액 (음수/양수)
    balance_after DECIMAL(20, 2) NOT NULL, -- 변동 후 잔액 (스냅샷)
    related_uuid VARCHAR(36),              -- 거래 상대방 (송금 시)
    description TEXT,                      -- 상세 내용 (예: "다이아몬드 검 구매", "홍길동에게 송금")
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player_uuid (player_uuid),   -- 조회 성능을 위한 인덱스
    INDEX idx_created_at (created_at)
);
```

### 5.3 외부 시스템 연동 (API 설계)
새로운 기능(상점, 룰렛 등)이 추가될 때, 개발자가 로그를 "깜빡"하지 않도록 **돈을 변경하는 메서드 자체에 로그 정보를 필수 인자로 강제**합니다.

```kotlin
interface EconomyService {
    // 돈 지급 (입금)
    fun deposit(player: Player, amount: Double, type: TransactionType, description: String)

    // 돈 차감 (출금) - 잔액 부족 시 false 반환
    fun withdraw(player: Player, amount: Double, type: TransactionType, description: String): Boolean

    // 송금
    fun transfer(sender: Player, receiver: Player, amount: Double, description: String): Boolean
}

// 사용 예시 (상점 플러그인에서)
economyService.withdraw(
    player = player, 
    amount = 1000.0, 
    type = TransactionType.SHOP_BUY, 
    description = "다이아몬드 검 구매"
)
// -> 이 함수 내부에서 잔액 차감 + 로그 기록이 동시에 수행됨 (원자성 보장)
```

## 6. 구현 단계 (Implementation Steps)

1.  **DB 마이그레이션 로직 구현**: 플러그인 활성화 시 `economy_logs` 테이블이 없으면 생성하고, 스키마 변경이 필요하면 자동으로 적용하는 로직 추가.
2.  **Repository 계층 분리**: `EconomyManager`의 DB 코드를 `EconomyRepository`로 분리하고 비동기 지원 메서드 추가.
3.  **로그 시스템 구현**: 돈이 변동되는 모든 지점(`addBalance`, `removeBalance`)에서 자동으로 로그를 남기도록 `EconomyService`에 로직 통합.
4.  **캐시 시스템 구현**: `HashMap<UUID, Double>` 형태의 인메모리 저장소 구현.
5.  **트랜잭션 로직 구현**: 송금 등 복합 작업의 원자성 보장.
6.  **명령어 리팩토링**: `/돈 로그` 또는 `/돈 내역` 명령어를 통해 유저가 자신의 통장 내역을 볼 수 있게 구현.

---
**작성자**: Antigravity AI
**날짜**: 2025-11-21

# 🤖 AI 에이전트 가이드 (AGENTS.md)

> 이 문서는 AI(Claude, GPT 등)가 LukeVanilla 프로젝트를 이해하고 코드를 수정할 때 참조하는 종합 가이드입니다.

---

## 📋 목차
1. [프로젝트 개요](#1-프로젝트-개요)
2. [문서 네비게이션](#2-문서-네비게이션)
3. [코드 수정 시 주의사항](#3-코드-수정-시-주의사항)
4. [시스템 의존성 체인](#4-시스템-의존성-체인)
5. [자주 수정되는 파일](#5-자주-수정되는-파일)
6. [코드 스타일](#6-코드-스타일)
7. [테스트 및 빌드](#7-테스트-및-빌드)
8. [문서화 규칙](#8-문서화-규칙)
9. [시스템 목록과 경로](#9-시스템-목록과-경로)
10. [명령어 빠른 참조](#10-명령어-빠른-참조)
11. [DB 테이블 빠른 참조](#11-db-테이블-빠른-참조)
12. [AI 작업 시 체크리스트](#12-ai-작업-시-체크리스트)

---

## 1. 프로젝트 개요

### 📌 기본 정보
- **프로젝트명**: LukeVanilla
- **유형**: Kotlin/PaperMC 기반 마인크래프트 서버 플러그인
- **서버 구조**: Velocity 프록시를 통한 멀티서버 (Lobby + Vanilla)

### 🛠️ 기술 스택
| 기술 | 용도 |
|------|------|
| **Kotlin** | 주 프로그래밍 언어 |
| **PaperMC** | Minecraft 서버 플랫폼 |
| **Velocity** | 프록시 서버 |
| **MySQL/MariaDB** | 데이터베이스 |
| **JDA** | Discord 봇 연동 |
| **OpenAI API** | AI 어시스턴트 기능 |
| **Gradle (Kotlin DSL)** | 빌드 도구 |

### 🎮 서버 타입
- **Lobby**: 로비 서버 (Discord 봇 전체 기능, AI 어시스턴트, 고객지원 시스템)
- **Vanilla**: 야생 서버 (토지 시스템, 상인 시스템, 게임 콘텐츠)

---

## 2. 문서 네비게이션

### 📚 주요 문서
| 문서 | 설명 | 경로 |
|------|------|------|
| **전체 구조** | 프로젝트 아키텍처 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **프로젝트 소개** | README | [README.md](README.md) |
| **빌드 설정** | Gradle 빌드 | [build.gradle.kts](build.gradle.kts) |

### 📂 시스템별 상세 문서
| 시스템 | README 경로 |
|--------|-------------|
| Database | [System/Database/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Database/README.md) |
| Discord | [System/Discord/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Discord/README.md) |
| Economy | [System/Economy/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Economy/README.md) |
| MyLand | [System/MyLand/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/MyLand/README.md) |
| AdvancedLandClaiming | [System/AdvancedLandClaiming/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/AdvancedLandClaiming/README.md) |
| FarmVillage | [System/FarmVillage/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/FarmVillage/README.md) |
| FishMerchant | [System/FishMerchant/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/FishMerchant/README.md) |
| FleaMarket | [System/FleaMarket/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/FleaMarket/README.md) |
| VillageMerchant | [System/VillageMerchant/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/VillageMerchant/README.md) |
| BookSystem | [System/BookSystem/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/BookSystem/README.md) |
| Roulette | [System/Roulette/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Roulette/README.md) |
| PlayTime | [System/PlayTime/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/PlayTime/README.md) |
| ChatSystem | [System/ChatSystem/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/ChatSystem/README.md) |
| Items | [System/Items/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Items/README.md) |
| MultiServer | [System/MultiServer/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/MultiServer/README.md) |
| NPC | [System/NPC/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/NPC/README.md) |
| Command | [System/Command/README.md](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Command/README.md) |

### 🌐 외부 API 참조
- **Discord (JDA)**: [JDA Documentation](https://ci.dv8tion.net/job/JDA5/javadoc/)
- **PaperMC**: [Paper API](https://papermc.io/javadocs/)
- **OpenAI API**: OpenAI Function Calling 사용

---

## 3. 코드 수정 시 주의사항

### ⚠️ 서버 타입 분기
```kotlin
// Main.kt에서 서버 타입 확인
val serviceType = config.getString("service.type") ?: "Vanilla"

// 서버 타입별 분기 예시
if (serviceType == "Lobby") {
    // 로비 서버에서만 실행
} else if (serviceType == "Vanilla") {
    // 야생 서버에서만 실행
}
```

**⚡ 중요**: 새 기능 추가 시 반드시 서버 타입을 확인하고 적절히 분기해야 합니다.

### 🗄️ 데이터베이스 스키마 변경
새 테이블이나 컬럼 추가 시:
1. `DatabaseInitializer.kt`에 테이블 생성 메소드 추가
2. `createTables()` 메소드에서 새 메소드 호출
3. 기존 테이블 마이그레이션 로직 포함 (ALTER TABLE)

```kotlin
// DatabaseInitializer.kt 패턴
private fun createNewSystemTable() {
    database.getConnection().use { connection ->
        val statement = connection.createStatement()
        statement.executeUpdate("""
            CREATE TABLE IF NOT EXISTS new_table (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                -- columns
            );
        """)
        
        // 마이그레이션: 기존 테이블에 컬럼 추가
        try {
            statement.executeUpdate("ALTER TABLE new_table ADD COLUMN `new_column` VARCHAR(50)")
        } catch (e: Exception) { /* 컬럼이 이미 존재함 */ }
    }
}
```

### 📝 새 명령어 추가
1. **plugin.yml 업데이트** (`src/main/resources/plugin.yml`)
```yaml
commands:
  새명령어:
    description: "명령어 설명"
    usage: "/새명령어 [인자]"
    permission: "lukevanilla.newcommand"

permissions:
  lukevanilla.newcommand:
    description: "새 명령어 권한"
    default: op
```

2. **Main.kt에 명령어 등록**
```kotlin
getCommand("새명령어")?.setExecutor(NewCommandExecutor())
getCommand("새명령어")?.tabCompleter = NewCommandCompleter()
```

### 🤖 Discord 기능 사용
Discord 기능은 반드시 `discordBot` 초기화 후에만 사용 가능:
```kotlin
if (::discordBot.isInitialized) {
    discordBot.jda.addEventListener(NewListener())
}
```

### 🎯 NPC 상호작용
NPC 클릭 이벤트는 `NPCInteractionRouter`를 통해 중앙 관리됩니다:
```kotlin
// NPC 핸들러 등록
npcInteractionRouter.registerHandler(npcId, MyNPCHandler())
```

---

## 4. 시스템 의존성 체인

### 💰 Economy 기반 시스템
```
Economy (EconomyManager)
    ├── FleaMarket (벼룩시장)
    ├── FishMerchant (물고기 상인)
    └── Roulette (룰렛)
```

### 🏞️ 토지 시스템 체인
```
MyLand (개인 토지)
    └── AdvancedLandClaiming (고급 토지)
        └── FarmVillage (농장 마을)
            └── VillageMerchant (마을 상인)
```

### 💬 Discord 기반 시스템
```
DiscordBot (JDA)
    ├── WarningSystem (경고 시스템)
    ├── SupportSystem (고객지원)
    ├── AIassistant (AI 도우미)
    └── DiscordAuth (인증)
```

### ⏰ PlayTime 의존 시스템
```
PlayTimeSystem
    ├── AdvancedLandClaiming (플레이타임 기반 무료 청크)
    └── SupportSystem (고객지원 시 플레이타임 표시)
```

---

## 5. 자주 수정되는 파일

### 🔴 핵심 파일 (수정 시 주의)
| 파일 | 설명 | 수정 시 영향 |
|------|------|-------------|
| [Main.kt](src/main/kotlin/com/lukehemmin/lukeVanilla/Main.kt) | 플러그인 진입점, 시스템 등록 | 전체 플러그인 |
| [plugin.yml](src/main/resources/plugin.yml) | 명령어 및 권한 정의 | 모든 명령어 |
| [DatabaseInitializer.kt](src/main/kotlin/com/lukehemmin/lukeVanilla/System/Database/DatabaseInitializer.kt) | DB 스키마 정의 | 데이터베이스 구조 |

### 🟡 시스템별 핵심 파일
| 시스템 | 핵심 파일 |
|--------|----------|
| Economy | `EconomyManager.kt`, `EconomyService.kt` |
| MyLand | `LandManager.kt`, `LandCommand.kt` |
| Discord | `DiscordBot.kt`, `DiscordAuth.kt` |
| FishMerchant | `FishMerchantManager.kt`, `FishMerchantGUI.kt` |

---

## 6. 코드 스타일

### 📐 Kotlin 컨벤션
```kotlin
// 클래스명: PascalCase
class MyNewSystem

// 함수명: camelCase
fun processPlayerData()

// 상수: SCREAMING_SNAKE_CASE
const val MAX_CHUNK_COUNT = 100

// 변수명: camelCase
private val playerCache: MutableMap<UUID, PlayerData>
```

### 🔄 비동기 처리
```kotlin
// 코루틴 사용 (권장)
suspend fun loadDataAsync(): Result {
    return withContext(Dispatchers.IO) {
        // DB 작업
    }
}

// CompletableFuture 사용
fun loadDataFuture(): CompletableFuture<Result> {
    return CompletableFuture.supplyAsync {
        // DB 작업
    }
}

// Bukkit 스케줄러
server.scheduler.runTaskAsynchronously(plugin, Runnable {
    // 비동기 작업
})
```

### 🗃️ DB 접근 패턴 (Repository Pattern)
```kotlin
class NewSystemRepository(private val database: Database) {
    fun findById(id: Int): NewEntity? {
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("SELECT * FROM table WHERE id = ?")
            stmt.setInt(1, id)
            val rs = stmt.executeQuery()
            return if (rs.next()) {
                NewEntity(rs.getInt("id"), rs.getString("name"))
            } else null
        }
    }
}
```

---

## 7. 테스트 및 빌드

### 🔨 빌드 명령어
```bash
# 전체 빌드
./gradlew build

# 클린 빌드
./gradlew clean build

# JAR만 생성
./gradlew jar
```

### 📦 빌드 결과물
```
build/libs/LukeVanilla-*.jar
```

### ✅ 빌드 전 체크
1. Kotlin 문법 오류 없음
2. 모든 import 문 정상
3. plugin.yml 명령어 등록 확인

---

## 8. 문서화 규칙

### 📝 새 시스템 추가 시
1. 시스템 폴더에 `README.md` 생성
2. `ARCHITECTURE.md`에 시스템 등록
3. 이 문서(`AGENTS.md`)의 시스템 목록 업데이트

### 📋 README.md 템플릿
```markdown
# 시스템명

## 개요
시스템 설명

## 주요 기능
- 기능 1
- 기능 2

## 파일 구조
- `File1.kt`: 설명
- `File2.kt`: 설명

## 사용법
명령어 및 사용 방법

## 데이터베이스
관련 테이블 설명
```

### 🌐 언어
- 모든 문서는 **한국어**로 작성
- 코드 주석도 한국어 권장

---

## 9. 시스템 목록과 경로

### 🎮 로비 서버 전용
| 시스템 | 경로 | 설명 |
|--------|------|------|
| SnowMinigame | `Lobby/SnowMinigame.kt` | 눈싸움 미니게임 |
| SupportSystem | `System/Discord/SupportSystem.kt` | 고객지원 시스템 |
| AdminAssistant | `System/Discord/AIassistant/` | AI 관리자 도우미 |

### 🌍 야생 서버 전용
| 시스템 | 경로 | 설명 |
|--------|------|------|
| PrivateLandSystem | `System/MyLand/` | 개인 토지 시스템 |
| AdvancedLandSystem | `System/AdvancedLandClaiming/` | 고급 토지 청구 |
| FarmVillageSystem | `System/FarmVillage/` | 농장 마을 |
| FishMerchantManager | `System/FishMerchant/` | 물고기 상인 |
| BookSystem | `System/BookSystem/` | 책 시스템 |
| FleaMarketManager | `System/FleaMarket/` | 벼룩시장 |
| VillageMerchantSystem | `System/VillageMerchant/` | 마을 상인 |
| SafeZoneManager | `System/SafeZoneManager.kt` | 안전 구역 |
| WardrobeLocationSystem | `System/WardrobeLocationSystem.kt` | 옷장 위치 |

### 🔄 공통 시스템
| 시스템 | 경로 | 설명 |
|--------|------|------|
| EconomyManager | `System/Economy/` | 경제 시스템 |
| PlayTimeSystem | `System/PlayTime/` | 플레이타임 |
| RouletteSystem | `System/Roulette/` | 룰렛 |
| WarningSystem | `System/WarningSystem/` | 경고 시스템 |
| DiscordBot | `System/Discord/DiscordBot.kt` | Discord 봇 |
| NametagManager | `System/ChatSystem/NametagManager.kt` | 칭호 시스템 |
| StatsSystem | `System/Items/StatsSystem/` | 아이템 통계 |

---

## 10. 명령어 빠른 참조

### 💰 경제 명령어
| 명령어 | 권한 | 담당 시스템 |
|--------|------|-------------|
| `/돈`, `/ehs` | 기본 | Economy |
| `/플마`, `/market` | 기본 | FleaMarket |

### 🏞️ 토지 명령어
| 명령어 | 권한 | 담당 시스템 |
|--------|------|-------------|
| `/땅`, `/land` | 기본 | MyLand |
| `/마을` | 기본 | MyLand (Village) |
| `/마을초대` | 기본 | MyLand |
| `/농사마을` | 관리자 | FarmVillage |

### 🏪 상인 명령어
| 명령어 | 권한 | 담당 시스템 |
|--------|------|-------------|
| `/낚시상인` | 관리자 | FishMerchant |
| `/농사상점`, `/마을상인` | 관리자 | VillageMerchant |
| `/룰렛` | 기본 | Roulette |

### 📚 기타 명령어
| 명령어 | 권한 | 담당 시스템 |
|--------|------|-------------|
| `/책`, `/book` | 기본 | BookSystem |
| `/아이템`, `/item` | 기본 | ItemSeasonSystem |
| `/플레이타임`, `/pt` | 기본 | PlayTime |
| `/경고`, `/warn` | 관리자 | WarningSystem |
| `/nametag` | 관리자 | ChatSystem |

### 🔧 관리자 명령어
| 명령어 | 권한 | 담당 시스템 |
|--------|------|-------------|
| `/lukereload` | 관리자 | Main |
| `/아이템복구` | 관리자 | ItemRestoreCommand |
| `/서버연결` | 관리자 | ServerConnectionCommand |
| `/관리자채팅` | 관리자 | AdminChatManager |

---

## 11. DB 테이블 빠른 참조

### 👤 플레이어 관련
| 테이블 | 담당 시스템 | 설명 |
|--------|-------------|------|
| `Player_Data` | Core | 플레이어 기본 정보 |
| `Player_Auth` | DiscordAuth | 인증 정보 |
| `Player_NameTag` | ChatSystem | 칭호 |
| `playtime_data` | PlayTime | 플레이타임 |
| `Connection_IP` | Core | IP 접속 기록 |

### 💰 경제 관련
| 테이블 | 담당 시스템 | 설명 |
|--------|-------------|------|
| (economy 테이블) | Economy | 잔액 정보 |
| (flea_market 테이블) | FleaMarket | 벼룩시장 매물 |

### 🏞️ 토지 관련
| 테이블 | 담당 시스템 | 설명 |
|--------|-------------|------|
| `myland_claims` | MyLand | 토지 청구 |
| `myland_members` | MyLand | 토지 멤버 |
| `myland_claim_history` | MyLand | 청구 이력 |
| `villages` | MyLand | 마을 정보 |
| `village_members` | MyLand | 마을 멤버 |
| `village_permissions` | MyLand | 마을 권한 |
| `farmvillage_plots` | FarmVillage | 농장 땅 |
| `farmvillage_package_items` | FarmVillage | 패키지 아이템 |

### 🏪 상인 관련
| 테이블 | 담당 시스템 | 설명 |
|--------|-------------|------|
| `fish_merchant_npc` | FishMerchant | 낚시 상인 NPC |
| `fish_prices` | FishMerchant | 물고기 가격 |
| `fish_sell_history` | FishMerchant | 판매 기록 |
| `villagemerchant_npcs` | VillageMerchant | 마을 상인 NPC |

### 🎰 룰렛 관련
| 테이블 | 담당 시스템 | 설명 |
|--------|-------------|------|
| `roulette_config` | Roulette | 룰렛 설정 |
| `roulette_items` | Roulette | 룰렛 아이템 |
| `roulette_history` | Roulette | 플레이 기록 |
| `roulette_trigger_mapping` | Roulette | NPC/Nexo 매핑 |
| `random_scroll_config` | RandomScrollRoulette | 스크롤 설정 |
| `random_scroll_rewards` | RandomScrollRoulette | 스크롤 보상 |
| `random_scroll_history` | RandomScrollRoulette | 스크롤 기록 |

### 📚 기타
| 테이블 | 담당 시스템 | 설명 |
|--------|-------------|------|
| `books` | BookSystem | 책 정보 |
| `book_sessions` | BookSystem | 웹 세션 |
| `warnings_players` | WarningSystem | 경고 플레이어 |
| `warnings_records` | WarningSystem | 경고 기록 |
| `SupportChatLink` | SupportSystem | 고객지원 연결 |
| `server_heartbeat` | MultiServer | 서버 상태 |
| `server_online_players` | MultiServer | 온라인 플레이어 |
| `Settings` | Core | 전역 설정 |

---

## 12. AI 작업 시 체크리스트

### ✅ 코드 수정 전 확인
- [ ] 서버 타입 분기 필요 여부 확인 (Lobby vs Vanilla)
- [ ] 의존 시스템 확인 (Economy, PlayTime 등)
- [ ] 관련 README.md 문서 확인
- [ ] 기존 코드 패턴 확인

### ✅ 새 시스템 추가 시
- [ ] 시스템 폴더 생성 (`System/NewSystem/`)
- [ ] README.md 문서 작성
- [ ] Main.kt에 시스템 등록
- [ ] plugin.yml에 명령어 추가 (필요시)
- [ ] DatabaseInitializer.kt에 테이블 추가 (필요시)
- [ ] ARCHITECTURE.md 업데이트
- [ ] 이 문서(AGENTS.md) 업데이트

### ✅ 코드 수정 후 확인
- [ ] 빌드 성공 확인 (`./gradlew build`)
- [ ] 문법 오류 없음
- [ ] import 문 정상
- [ ] 서버 타입별 테스트 (해당 시)
- [ ] 관련 문서 업데이트

### ✅ DB 스키마 변경 시
- [ ] DatabaseInitializer.kt에 CREATE TABLE 추가
- [ ] 기존 테이블 마이그레이션 로직 포함
- [ ] 관련 Repository 클래스 업데이트
- [ ] README.md에 테이블 설명 추가

### ✅ 명령어 추가 시
- [ ] plugin.yml에 명령어 정의
- [ ] plugin.yml에 권한 정의
- [ ] Main.kt에 executor/completer 등록
- [ ] 명령어 클래스 구현
- [ ] 관련 문서 업데이트

---

## 📞 추가 참고

### 외부 플러그인 의존성
| 플러그인 | 의존 타입 | 연동 시스템 |
|----------|----------|-------------|
| HMCCosmetics | 필수 | WardrobeLocationSystem |
| CustomCrops | 필수 | FarmVillage |
| Citizens | 선택 | FishMerchant, VillageMerchant, Roulette |
| Nexo/Oraxen | 선택 | ItemSeasonSystem, Roulette |
| LuckPerms | 선택 | DiscordRoleManager, FarmVillage |

### 플러그인 메시지 채널
| 채널 | 방향 | 용도 |
|------|------|------|
| `lukevanilla:serverstatus_request` | Lobby → Vanilla | 서버 상태 요청 |
| `lukevanilla:serverstatus_response` | Vanilla → Lobby | 서버 상태 응답 |

---

> 📝 **최종 업데이트**: 2024년 12월
> 
> 이 문서는 AI 에이전트가 LukeVanilla 프로젝트를 효과적으로 이해하고 수정할 수 있도록 작성되었습니다.
> 프로젝트 구조 변경 시 이 문서도 함께 업데이트해 주세요.
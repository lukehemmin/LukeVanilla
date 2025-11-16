# CLAUDE.md - LukeVanilla 프로젝트 가이드

이 문서는 AI 어시스턴트가 LukeVanilla 프로젝트를 효과적으로 이해하고 작업할 수 있도록 돕는 가이드입니다.

## 프로젝트 개요

**LukeVanilla**는 Kotlin으로 개발된 Minecraft Paper 1.21.x + Velocity 통합 플러그인입니다.

### 주요 기능
- **Discord 연동**: JDA 기반 봇, 인증/역할 부여, 관리자 채팅 동기화, 티켓 시스템, OpenAI 기반 관리자 어시스턴트
- **서버 통합**: 로비↔야생 간 PluginMessage 기반 상태 동기화
- **토지 시스템**: MyLand (청크 보호), FarmVillage (농사 특화 영역)
- **아이템 시스템**: 시즌 아이템, 킬 통계, Nexo 아이템 복구
- **경고/차단**: 자동 차단 임계치 포함 경고 시스템
- **경제**: 간단한 송금 명령어

### 기술 스택
- **언어**: Kotlin 2.0.21 (JVM 21)
- **빌드**: Gradle 8.x + ShadowJar
- **플랫폼**: Paper API 1.21.8-R0.1, Velocity API 3.1.1
- **데이터베이스**: MySQL 8.x + HikariCP 5.0.1
- **주요 라이브러리**:
  - JDA 5.6.1 (Discord)
  - OpenAI Java SDK 1.6.1
  - Ktor 2.3.7 (웹서버 - BookSystem)
  - Kotlinx Serialization

## 코드 구조

```
src/main/kotlin/com/lukehemmin/lukeVanilla/
├── Main.kt                          # 메인 플러그인 클래스
├── Lobby/                           # 로비 전용 기능 (눈 미니게임 등)
├── velocity/                        # Velocity 프록시 플러그인
└── System/
    ├── Discord/                     # Discord 봇 & 연동
    │   ├── DiscordBot.kt           # JDA 봇 메인
    │   ├── DiscordAuth.kt          # 인증 시스템
    │   ├── AdminChatSync.kt        # 관리자 채팅 동기화
    │   ├── TicketSystem.kt         # 고객지원 티켓
    │   ├── DynamicVoiceChannel.kt  # 동적 음성채널
    │   └── AIassistant/            # OpenAI 기반 관리자 어시스턴트
    ├── Database/                    # 데이터베이스 관리
    │   ├── Database.kt             # HikariCP 커넥션 풀
    │   ├── AsyncDatabaseManager.kt # 비동기 DB 작업
    │   └── DatabaseInitializer.kt  # 테이블 초기화
    ├── MyLand/                      # 청크 보호 시스템
    │   └── MyLand_System_Documentation.md
    ├── FarmVillage/                 # 농사 특화 영역 시스템
    ├── AdvancedLandClaiming/        # 고급 토지 시스템 (개발 중)
    ├── Items/
    │   ├── ItemSeasonSystem/       # 시즌 아이템 (할로윈, 크리스마스 등)
    │   ├── StatsSystem/            # 아이템 킬 통계
    │   └── CustomItemSystem/       # 커스텀 아이템 유틸
    ├── WarningSystem/               # 경고 및 차단 시스템
    ├── MultiServer/                 # 서버 간 통신 (PluginMessage)
    ├── Economy/                     # 경제 시스템
    ├── PlayTime/                    # 플레이타임 추적
    ├── BookSystem/                  # 책 시스템 (Ktor 웹서버 포함)
    ├── Roulette/                    # 룰렛 시스템
    ├── PeperoEvent/                 # 빼빼로 이벤트
    ├── ChatSystem/                  # 채팅 시스템
    └── Utils/                       # 유틸리티 클래스
```

## 핵심 설계 원칙

### 1. Service Type 분기
프로젝트는 `config.yml`의 `service.type` 설정에 따라 동작이 달라집니다:

```yaml
service:
  type: "Vanilla"  # 또는 "Lobby"
```

- **Lobby**: 로비 서버 기능 활성화
  - 야생 서버 연결 상태 체크 (`ServerConnectionCommand`)
  - 일부 Discord 음성채널 자동화
  - 눈 미니게임 등

- **Vanilla**: 생존 서버 기능 활성화
  - 안전구역 관리 (`SafeZoneManager`)
  - HMCCosmetics 옷장 위치 시스템
  - MyLand/FarmVillage 토지 시스템
  - 서버 종료 알림 전송

**Main.kt**에서 `getServerType()` 함수로 현재 타입 조회 가능

### 2. 데이터베이스 설계

#### Settings 테이블 기반 동적 설정
많은 설정값이 `Settings` 테이블에 key-value 형태로 저장됩니다:

```kotlin
// 예시: Discord 토큰 조회
val token = database.getSetting("DiscordToken")
```

**필수 Settings 키**:
- Discord: `DiscordToken`, `DiscordServerID`, `AuthChannel`, `AdminChatChannel`, `SystemChannel`, `SupportCategoryId` 등
- 음성채널: `TRIGGER_VOICE_CHANNEL_ID_1/2`, `VOICE_CATEGORY_ID_1/2`
- OpenAI: `OpenAI_API_Token`, `OpenAI_API_Endpoint`, `OpenAI_API_Model`

#### 비동기 DB 처리 패턴
```kotlin
// AsyncDatabaseManager 사용 예시
database.executeAsync { connection ->
    // DB 작업
} ?: handleError()
```

### 3. 서버 간 통신 (PluginMessage)

로비↔야생 서버 간 상태 동기화를 위해 PluginMessage 채널 사용:

- `lukevanilla:status_request` - 로비가 야생에게 상태 요청
- `lukevanilla:status_response` - 야생이 로비에게 상태 응답

관련 코드:
- `Main.kt:103-161` - 채널 등록
- `System/MultiServer/` - 통신 로직
- `System/Discord/ServerStatusRequester.kt` - 로비 측 요청

### 4. 플러그인 연동

#### compileOnly vs implementation
```kotlin
// compileOnly: 런타임에 서버가 제공 (플러그인 의존성)
compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
compileOnly("com.nexomc:nexo:1.8.0")
compileOnly("com.hibiscusmc:HMCCosmetics:2.7.9-ff1addfd")
compileOnly("net.momirealms:custom-crops:3.6.41")

// implementation: JAR에 포함 (라이브러리)
implementation("net.dv8tion:JDA:5.6.1")
implementation("com.openai:openai-java:1.6.1")
```

#### 필수 플러그인
- HMCCosmetics (코스메틱 시스템)
- Custom-Crops (농사 시스템)

#### 권장 플러그인
- Nexo (커스텀 아이템)
- Citizens (NPC)
- LuckPerms (권한 관리)

## 중요 주의사항

### 1. 데이터베이스 스키마명 하드코딩
**AdminAssistant** 등 일부 시스템은 `lukevanilla` 스키마를 하드코딩으로 참조합니다.

```yaml
# config.yml
database:
  name: "lukevanilla"  # 반드시 이 이름 사용 권장
```

다른 이름 사용 시 해당 SQL 쿼리를 수동으로 수정해야 합니다.

### 2. 비활성화된 시스템

#### Nexo 작업대 제한
현재 주석 처리되어 비활성화 상태:
- `Main.kt` 내 `NexoCraftingRestriction` 등록 주석됨
- 사용하려면 주석 해제 + `plugin.yml`에 명령어 추가 필요

#### 기타 주석 처리된 기능
- `NexoLuckPermsGranter` - Nexo 아이템 획득 시 권한 부여 (Main.kt:32, 61)

### 3. ShadowJar 출력 경로
```kotlin
// build.gradle.kts:124-131
destinationDirectory.set(
    if (isCI) file("build/libs")
    else file("/home/lukehemmin/LukeVanilla/jars")  // 로컬 환경
)
```

CI 환경이 아니면 특정 로컬 경로로 출력됩니다. 개발 환경에 맞게 수정하세요.

### 4. Discord 기능 초기화 순서
Discord 봇 초기화 실패 시 많은 기능이 동작하지 않습니다. Settings 테이블에 `DiscordToken`이 올바르게 설정되어 있는지 확인하세요.

## 개발 워크플로우

### 빌드
```bash
./gradlew shadowJar
```

출력: `build/libs/LukeVanilla-1.0-SNAPSHOT-all.jar` (CI) 또는 로컬 경로

### 설정 파일

#### config.yml
```yaml
service:
  type: "Vanilla"  # Lobby 또는 Vanilla

database:
  host: "localhost"
  port: 3306
  name: "lukevanilla"  # 하드코딩 참조 주의
  user: "root"
  password: ""

wardrobe:
  world: "world"
  x: 0.5
  y: 100.0
  z: 0.5
  radius: 20.0

debug:
  farmVillage: false
  privateLand: false
  # ... 시스템별 디버그 플래그
```

#### plugin.yml
Paper 플러그인 메타데이터 (명령어, 권한 정의)

#### velocity-plugin.json
Velocity 플러그인 메타데이터

### 의존성 설치
Gradle이 자동으로 처리하지만, 서버에는 다음 플러그인 필수:
1. HMCCosmetics
2. Custom-Crops
3. (선택) Nexo, Citizens, LuckPerms 등

### 데이터베이스 초기화
1. MySQL 스키마 생성:
   ```sql
   CREATE DATABASE lukevanilla CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 최초 실행 시 테이블 자동 생성 (`DatabaseInitializer.kt`)

3. Settings 테이블에 Discord 토큰 등 설정값 수동 삽입:
   ```sql
   INSERT INTO Settings (setting_key, setting_value) VALUES
   ('DiscordToken', 'YOUR_DISCORD_BOT_TOKEN'),
   ('DiscordServerID', 'YOUR_SERVER_ID'),
   ('AuthChannel', 'CHANNEL_ID'),
   -- ... 기타 설정
   ```

### 테스트
개발 서버에서 테스트 시:
1. `config.yml` 설정 확인
2. MySQL 연결 확인
3. Discord 토큰 유효성 확인
4. `/reload confirm` 사용 시 주의 (일부 시스템 재초기화 안 될 수 있음)

## 코딩 스타일 & 컨벤션

### Kotlin 스타일
- Kotlin 공식 코딩 컨벤션 따름
- 들여쓰기: 스페이스 4칸
- 주석: 한글/영어 혼용 가능

### 패키지 구조
- 기능별로 `System/` 하위에 패키지 분리
- 각 시스템은 독립적으로 동작 가능하도록 설계

### 네이밍
- 클래스: PascalCase (예: `DiscordBot`)
- 함수/변수: camelCase (예: `getServerType`)
- 상수: UPPER_SNAKE_CASE (예: `CHANNEL_SERVER_STATUS_REQUEST`)

### 비동기 처리
- DB 작업: `AsyncDatabaseManager` 사용
- Discord API: JDA의 비동기 메서드 활용 (예: `queue()`)
- Bukkit 스케줄러: `runTaskAsynchronously` 등

## 주요 명령어 (플레이어용)

```
/아이템 [등록|조회|수령] [시즌]  - 시즌 아이템 시스템
/아이템정보                    - 아이템 킬 통계
/아이템복구                    - Nexo 아이템 복구
/관리자채팅 [활성화|비활성화]    - 관리자 채팅 토글
/티토커메시지 [보기|끄기]       - TTS 채널 메시지 표시
/음성채널메시지 [보기|끄기]     - 음성채널 메시지 표시
/wleh                         - 지도 링크
/블록위치                      - 블록 좌표 확인 모드
/서버시간                      - 서버 시간 표시
/refreshmessages              - 접속/퇴장 메시지 갱신
/경고 [주기|차감|확인|목록]     - 경고 시스템
/서버연결 [status|test|...]   - (로비) 야생 서버 연결 점검
```

## 주요 권한 노드

```
lukevanilla.admin              - 관리자 기능
lukevanilla.adminchat          - 관리자 채팅
lukevanilla.item               - 아이템 명령어
lukevanilla.nametag            - 네임태그 관리
lukevanilla.transparentframe   - 투명 액자
lukevanilla.reload             - 리로드 명령어
advancedwarnings.*             - 경고 시스템
itemstats.admin                - 아이템 통계 관리
```

## 문서 및 참고 자료

### 프로젝트 내 문서
- `README.md` - 사용자 가이드
- `INTEGRATION_GUIDE.md` - 통합 가이드
- `System/MyLand/MyLand_System_Documentation.md` - MyLand 시스템 문서
- `BookSystem-nginx-setup.md` - BookSystem nginx 설정
- `NexoAPI.md` - Nexo API 참고
- `nexo-crafting-troubleshooting.md` - Nexo 제작 문제 해결

### 외부 API 문서
`Docs/API/` 폴더 참조:
- HMCCosmetics API
- Custom-Crops API
- Citizens API
- LuckPerms API
- Nexo API

### 이슈 및 기여
- GitHub Issues: https://github.com/lukehemmin/LukeVanilla/issues
- Pull Request: 기능 단위로 작게, 테스트 포함 권장

## 라이선스

MIT License - 자세한 내용은 `LICENSE` 파일 참조

**주의**: 프로젝트 아이콘/이미지는 별도 저작권이 적용될 수 있습니다.

---

## AI 어시스턴트를 위한 팁

### 코드 탐색 시작점
1. `Main.kt` - 플러그인 엔트리포인트, 시스템 초기화 순서 확인
2. `Database.kt` / `AsyncDatabaseManager.kt` - DB 작업 패턴 파악
3. `Discord/DiscordBot.kt` - Discord 연동 구조 이해
4. 각 시스템의 `...Command.kt` - 명령어 처리 로직

### 변경 시 주의사항
- Settings 테이블 키 변경 시 관련 모든 코드 검색 필요
- `service.type` 분기 로직 수정 시 양쪽 모두 테스트
- Discord 채널 ID 관련 코드는 Settings 테이블과 동기화 확인
- DB 스키마 변경 시 `DatabaseInitializer.kt` 업데이트

### 디버깅
- `config.yml`의 `debug` 섹션 활용
- 각 시스템별 디버그 플래그 확인 가능
- Discord 봇 로그는 `DiscordBot.kt`에서 확인

### 자주 묻는 질문
**Q: AdminAssistant가 동작하지 않아요**
A: `database.name`이 `lukevanilla`인지, OpenAI 토큰이 Settings에 있는지 확인

**Q: Discord 봇이 시작되지 않아요**
A: Settings 테이블에 `DiscordToken`, `DiscordServerID`가 올바른지 확인

**Q: 로비-야생 간 통신이 안 돼요**
A: Velocity 설정 확인, PluginMessage 채널 등록 로그 확인

**Q: Nexo 아이템 복구가 안 돼요**
A: Nexo 플러그인 설치 여부, `RestoreItemInfo` 채널 설정 확인

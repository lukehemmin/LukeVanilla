# Lobby 시스템 문서

## 개요

LukeVanilla 플러그인의 Lobby 시스템은 눈싸움 미니게임을 관리하는 시스템입니다. 이 시스템은 플레이어들이 특정 영역에 들어가면 자동으로 게임에 참여하고, 팀 기반의 PvP 눈싸움 게임을 진행할 수 있도록 합니다.

## 시스템 구성

### 파일 구조
```
com.lukehemmin.lukeVanilla.Lobby/
├── SnowGameCommand.kt     # 관리자 명령어 처리
└── SnowMinigame.kt        # 메인 게임 로직
```

### 주요 기능
- **자동 게임 참여**: 플레이어가 아레나 영역에 들어가면 자동으로 게임 대기열에 추가
- **팀 기반 게임**: 최대 8개 팀으로 구성된 멀티플레이어 게임
- **상태 관리**: 대기, 카운트다운, 게임 진행, 종료 등의 상태 관리
- **관전 시스템**: 탈락한 플레이어는 관전 모드로 전환
- **아레나 관리**: 게임 종료 후 자동으로 맵 복구

---

## SnowGameCommand 클래스

### 개요
관리자가 눈싸움 게임을 제어할 수 있는 명령어를 제공하는 클래스입니다.

### 클래스 정보
- **패키지**: `com.lukehemmin.lukeVanilla.Lobby`
- **상속**: `CommandExecutor`
- **의존성**: `SnowMinigame` 인스턴스

### 생성자
```kotlin
class SnowGameCommand(private val snowMinigame: SnowMinigame) : CommandExecutor
```

### 주요 메서드

#### onCommand()
```kotlin
override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean
```

**기능**: 관리자 명령어를 처리합니다.

**권한 확인**: 
- 플레이어만 사용 가능
- OP 권한 필요

**지원 명령어**:
- `forcestart`: 게임 강제 시작
- `forcereset` / `forcestop`: 게임 강제 초기화

**사용법**: `/snowgame <forcestart|forcereset>`

---

## SnowMinigame 클래스

### 개요
눈싸움 미니게임의 핵심 로직을 담당하는 메인 클래스입니다. 게임 상태 관리, 플레이어 관리, 이벤트 처리 등 모든 게임 기능을 통합 관리합니다.

### 클래스 정보
- **패키지**: `com.lukehemmin.lukeVanilla.Lobby`
- **상속**: `Listener`
- **의존성**: `JavaPlugin` 인스턴스

### 데이터 클래스

#### TeamData
```kotlin
data class TeamData(val name: String, val color: ChatColor, val spawn: Location)
```
팀 정보를 저장하는 데이터 클래스입니다.

### 주요 속성

#### 플레이어 관리
- `waitingPlayers: MutableSet<UUID>` - 게임 대기 중인 플레이어
- `spectatingPlayers: MutableSet<UUID>` - 관전 중인 플레이어
- `playerInventories: MutableMap<UUID, Array<ItemStack?>>` - 플레이어 인벤토리 백업
- `playerTeams: MutableMap<UUID, TeamData>` - 플레이어 팀 배정

#### 게임 상태
- `gameState: GameState` - 현재 게임 상태
- `gameMovementAllowed: Boolean` - 게임 중 이동 허용 여부
- `countdownTask: BukkitTask?` - 게임 시작 카운트다운 작업
- `gameStartCountdownTask: BukkitTask?` - 게임 시작 전 카운트다운 작업

#### 게임 설정
- `minPlayers: Int = 2` - 최소 플레이어 수
- `maxPlayers: Int = 8` - 최대 플레이어 수
- `countdownSeconds: Int = 30` - 카운트다운 시간

#### 아레나 정보
- `normalizedCorner1: Location` - 아레나 영역 좌표 1
- `normalizedCorner2: Location` - 아레나 영역 좌표 2
- `arenaWorld: World` - 아레나 월드
- `teams: MutableList<TeamData>` - 팀 목록

---

## 게임 상태 관리 시스템

### GameState 열거형
```kotlin
enum class GameState {
    WAITING,        // 대기 중
    COUNTING_DOWN,  // 카운트다운 진행 중
    PAUSED,         // 일시 정지
    IN_GAME,        // 게임 진행 중
    FINISHED,       // 게임 종료
    RESETTING       // 리셋 중
}
```

### 상태 전환 흐름
1. **WAITING** → **COUNTING_DOWN**: 최소 인원 충족 시
2. **COUNTING_DOWN** → **PAUSED**: 최대 인원 초과 시
3. **COUNTING_DOWN** → **WAITING**: 최소 인원 미달 시
4. **PAUSED** → **COUNTING_DOWN**: 인원 정상화 시
5. **COUNTING_DOWN** → **IN_GAME**: 카운트다운 완료 시
6. **IN_GAME** → **RESETTING**: 게임 종료 시
7. **RESETTING** → **WAITING**: 리셋 완료 시

### 상태별 동작

#### WAITING (대기)
- 플레이어 참여/퇴장 허용
- 최소 인원 충족 시 자동으로 카운트다운 시작

#### COUNTING_DOWN (카운트다운)
- 30초 카운트다운 진행
- 인원 변동에 따른 상태 변경 가능
- 10초 단위 및 5초 이하에서 알림 메시지

#### PAUSED (일시정지)
- 최대 인원 초과로 인한 일시정지
- 인원 정상화 시 카운트다운 재개

#### IN_GAME (게임 진행)
- 실제 게임 플레이
- 플레이어 이동 제한 후 허용
- 승리 조건 확인

#### RESETTING (리셋)
- 게임 종료 후 정리 작업
- 플레이어 상태 복원
- 아레나 복구

---

## 팀 시스템 및 플레이어 관리

### 팀 구성
총 8개 팀으로 구성되며, 각 팀은 고유한 색상과 스폰 위치를 가집니다.

#### 팀 목록
1. **빨강팀** (RED) - (0, 5, 58)
2. **주황팀** (GOLD) - (13, 5, 64)
3. **흰색팀** (WHITE) - (19, 5, 77)
4. **노랑팀** (YELLOW) - (13, 5, 90)
5. **검정팀** (BLACK) - (0, 5, 96)
6. **연두팀** (GREEN) - (-13, 5, 90)
7. **갈색팀** (DARK_RED) - (-19, 5, 77)
8. **파란팀** (BLUE) - (-13, 5, 64)

### 플레이어 관리 기능

#### addPlayer()
- 아레나 영역 진입 시 자동 호출
- 게임 상태 및 인원 수 확인
- 대기열에 플레이어 추가
- 카운트다운 상태 관리

#### removePlayer()
- 아레나 영역 이탈 시 자동 호출
- 인벤토리 복원
- 게임 모드 및 상태 초기화
- 승리 조건 확인 (게임 중인 경우)

#### 플레이어 상태 관리
- **인벤토리 백업/복원**: 게임 참여 시 기존 인벤토리 저장 후 게임 아이템 지급
- **게임 모드 변경**: 게임 중 서바이벌, 탈락 시 관전 모드
- **체력/배고픔 관리**: 게임 종료 시 최대 상태로 복원
- **포션 효과 제거**: 게임 종료 시 모든 효과 제거

---

## 이벤트 핸들러 시스템

### 이동 관련 이벤트

#### onPlayerMove()
**기능**: 플레이어 이동을 감지하고 게임 참여/퇴장 및 제한 사항을 처리합니다.

**주요 처리 사항**:
- 아레나 영역 진입/이탈 감지
- 게임 중 이동 제한 (시작 전)
- 용암 접촉 시 탈락 처리
- 관전자 영역 이탈 방지

### 블록 관련 이벤트

#### onBlockBreak()
**기능**: 블록 파괴를 제어합니다.

**제한 사항**:
- 게임 중이 아닐 때: 아레나 내 모든 블록 파괴 금지
- 게임 중: 눈 블록만 파괴 허용 (이동 허용 상태에서)
- 관전자: 모든 블록 파괴 금지

#### onBlockPlace()
**기능**: 블록 설치를 제어합니다.

**제한 사항**:
- 게임 중: 모든 블록 설치 금지
- 관전자: 모든 블록 설치 금지

### 플레이어 상태 이벤트

#### onPlayerDeath()
**기능**: 플레이어 사망 시 관전자로 전환합니다.

**처리 과정**:
1. 아이템 드롭 방지
2. 인벤토리 유지
3. 대기 목록에서 관전 목록으로 이동
4. 팀에서 제거
5. 관전 모드로 변경 (1틱 딜레이)
6. 승리 조건 확인

#### onPlayerRespawn()
**기능**: 관전자 리스폰 위치를 설정합니다.

**리스폰 위치**: (0, 15, 77) - 아레나 중앙 상공

#### onPlayerQuit()
**기능**: 플레이어 서버 이탈 시 정리 작업을 수행합니다.

**처리 사항**:
- 대기 목록에서 제거
- 팀에서 제거
- 관전 목록에서 제거
- 게임 상태 재확인

### 상호작용 이벤트

#### onPlayerInteract() / onPlayerInteractEntity()
**기능**: 관전자의 상호작용을 차단합니다.

---

## 아레나 관리 및 블록 시스템

### 아레나 영역 설정

#### 좌표 정보
- **Point 1**: (21, 12, 56)
- **Point 2**: (-21, 0, 98)
- **정규화된 영역**: 두 점을 기준으로 최소/최대 좌표 계산

#### isInsideArena()
```kotlin
private fun isInsideArena(location: Location): Boolean
```
플레이어가 아레나 영역 내부에 있는지 확인하는 함수입니다.

### 게임 시작/종료 시 블록 관리

#### 게임 시작 시
- **(1,7,57) ~ (-1,5,57)** 영역을 BARRIER 블록으로 차단
- 플레이어들이 게임 시작 전에 나가는 것을 방지

#### 게임 종료 시
- 차단된 영역을 AIR 블록으로 복원
- 플레이어들이 자유롭게 이동할 수 있도록 함

### 눈 블록 재생성 시스템

#### refillSnowArea()
```kotlin
private fun refillSnowArea(world: World, x1: Int, z1: Int, x2: Int, z2: Int)
```

**기능**: 지정된 영역을 눈 블록(Y=4)으로 채웁니다.

**재생성 영역**: 총 19개의 구역으로 나누어 체계적으로 복구
- 중앙 영역부터 외곽 영역까지 순차적으로 복구
- 대칭적인 패턴으로 배치

### 아레나 정리 시스템

#### 드롭된 아이템 제거
- 게임 종료 시 아레나 내 모든 드롭된 아이템 자동 제거
- 다음 게임을 위한 깨끗한 환경 조성

#### clearArenaArea()
```kotlin
private fun clearArenaArea()
```
- Y=1~11 범위의 모든 블록을 AIR로 변경
- 바닥(Y=0)과 천장(Y=12+)은 보존

---

## 게임 플로우

### 1. 게임 준비 단계
1. 플레이어가 아레나 영역에 진입
2. 자동으로 대기열에 추가
3. 최소 인원(2명) 충족 시 30초 카운트다운 시작
4. 최대 인원(8명) 초과 시 일시정지

### 2. 게임 시작 단계
1. 카운트다운 완료 후 게임 시작
2. 플레이어들을 팀별로 배정 및 텔레포트
3. 인벤토리 백업 후 게임 아이템 지급
4. 아레나 출입구 차단
5. 4초 게임 시작 카운트다운
6. 이동 허용 및 게임 시작

### 3. 게임 진행 단계
1. 플레이어들이 눈 블록을 파괴하며 전투
2. 사망 시 관전 모드로 전환
3. 용암 접촉 시 즉시 탈락
4. 최후 1인 또는 1팀까지 진행

### 4. 게임 종료 단계
1. 승리 조건 달성 시 게임 종료
2. 승자 발표 및 축하 메시지
3. 모든 플레이어 상태 복원
4. 아레나 정리 및 눈 블록 재생성
5. 대기 상태로 복귀

---

## 관리자 기능

### 강제 시작
```
/snowgame forcestart
```
- 현재 대기 중인 플레이어들로 즉시 게임 시작
- 카운트다운 무시
- WAITING, PAUSED, COUNTING_DOWN 상태에서만 사용 가능

### 강제 초기화
```
/snowgame forcereset
/snowgame forcestop
```
- 현재 진행 중인 게임을 즉시 종료
- 모든 플레이어 상태 복원
- 아레나 정리 및 복구
- 모든 상태에서 사용 가능

---

## 기술적 세부사항

### 동시성 처리
- `gameState`를 통한 상태 기반 동시성 제어
- `RESETTING` 상태로 중복 리셋 방지
- 스케줄러 작업의 안전한 취소 및 재시작

### 메모리 관리
- 게임 종료 시 모든 맵 데이터 정리
- 플레이어 인벤토리 백업의 적절한 해제
- 이벤트 리스너의 효율적인 처리

### 오류 처리
- 플레이어 오프라인 상태 확인
- 월드 로드 상태 검증
- 예외 상황에 대한 안전한 폴백

### 성능 최적화
- 블록 단위 이동 감지로 불필요한 처리 방지
- 청크 단위 블록 처리로 성능 향상
- 효율적인 플레이어 목록 관리

---

## 설정 및 커스터마이징

### 게임 설정 변경 가능 항목
- `minPlayers`: 최소 플레이어 수 (기본값: 2)
- `maxPlayers`: 최대 플레이어 수 (기본값: 8)
- `countdownSeconds`: 카운트다운 시간 (기본값: 30초)

### 아레나 설정
- 아레나 좌표는 `initializeArena()` 메서드에서 수정 가능
- 팀 스폰 위치는 `initializeTeams()` 메서드에서 조정 가능

### 게임 아이템
- 철 삽 (IRON_SHOVEL) 1개
- 구운 소고기 (COOKED_BEEF) 64개

---

## 알려진 제한사항 및 주의사항

1. **월드 의존성**: 'world' 월드가 반드시 로드되어 있어야 함
2. **좌표 고정**: 아레나 좌표가 하드코딩되어 있음
3. **단일 게임**: 동시에 하나의 게임만 진행 가능
4. **권한 시스템**: OP 권한 기반의 단순한 권한 체계

---

## 향후 개선 방향

1. **설정 파일화**: 하드코딩된 값들을 설정 파일로 분리
2. **다중 아레나**: 여러 아레나에서 동시 게임 진행
3. **권한 플러그인 연동**: LuckPerms 등과의 연동
4. **통계 시스템**: 플레이어 승률, 킬/데스 등 통계 기능
5. **보상 시스템**: 게임 승리 시 보상 지급
6. **GUI 시스템**: 게임 참여/설정을 위한 GUI 인터페이스

---
# LukeVanilla Velocity 시스템 문서

## 개요

LukeVanilla Velocity 시스템은 Velocity 프록시 서버에서 동작하는 플러그인으로, 멀티 서버 환경에서 서버 간 통신과 플레이어 관리를 담당합니다. 이 시스템은 로비 서버와 야생 서버 간의 상태 정보 중계 및 자동 서버 리디렉션 기능을 제공합니다.

## 시스템 구성 요소

### 1. VelocityMain.kt - 메인 플러그인 클래스

#### 기본 정보
- **플러그인 ID**: `lukevanilla`
- **플러그인 명**: `LukeVanilla Velocity`
- **버전**: `1.0-SNAPSHOT`
- **작성자**: `lukehemmin`

#### 주요 기능

##### 1.1 플러그인 초기화
```kotlin
@Subscribe
fun onProxyInitialization(event: ProxyInitializeEvent)
```
- Velocity 프록시 서버 시작 시 자동 실행
- 채널 등록: `luke:vanilla_status` 채널을 통한 서버 간 통신
- 명령어 시스템 초기화
- 자동 서버 리디렉션 시스템 활성화

##### 1.2 명령어 시스템

**등록된 명령어:**
- `/로비서버`: 로비 서버로 이동하는 명령어
- `/야생서버`: 야생 서버로 이동하는 명령어

**특징:**
- 한글 명령어 지원
- 별칭 없이 정확한 명령어만 사용
- SimpleCommand 인터페이스 구현

##### 1.3 종료 처리
```kotlin
@Subscribe
fun onProxyShutdown(event: ProxyShutdownEvent)
```
- 프록시 서버 종료 시 정리 작업 수행

### 2. ServerStatusProxy.kt - 서버 상태 프록시

#### 기본 정보
- **플러그인 ID**: `lukevanilla-serverstatus`
- **플러그인 명**: `LukeVanilla ServerStatus Proxy`
- **버전**: `1.0`

#### 주요 기능

##### 2.1 채널 관리
```kotlin
companion object {
    val REQUEST_CHANNEL = MinecraftChannelIdentifier.create("lukevanilla", "serverstatus_request")
    val RESPONSE_CHANNEL = MinecraftChannelIdentifier.create("lukevanilla", "serverstatus_response")
    const val SURVIVAL_SERVER_ID = "survival"
}
```

**채널 구성:**
- **요청 채널**: `lukevanilla:serverstatus_request`
- **응답 채널**: `lukevanilla:serverstatus_response`
- **대상 서버**: `survival` (야생 서버)

##### 2.2 초기화 과정
- 통신 채널 등록
- 메시지 리스너 등록
- 서버 상태 프록시 시스템 활성화

### 3. ServerStatusMessageListener.kt - 메시지 리스너

#### 주요 기능

##### 3.1 플러그인 메시지 처리
```kotlin
@Subscribe
fun onPluginMessage(event: PluginMessageEvent)
```

**처리하는 메시지 유형:**
1. **상태 요청 메시지** (`REQUEST_CHANNEL`)
2. **상태 응답 메시지** (`RESPONSE_CHANNEL`)

##### 3.2 상태 요청 처리 (`handleStatusRequest`)

**처리 과정:**
1. 로비 서버에서 온 요청 확인
2. 메시지 페이로드 검증
3. 야생 서버 검색 및 연결 확인
4. 요청 메시지를 야생 서버로 전달
5. 로그 기록

**보안 기능:**
- 요청 소스 검증 (ServerConnection 타입 확인)
- 대상 서버 존재 여부 확인
- 오류 처리 및 로깅

##### 3.3 상태 응답 처리 (`handleStatusResponse`)

**처리 과정:**
1. 야생 서버에서 온 응답 확인
2. 응답 데이터 추출 및 로깅
3. 모든 로비 서버에 브로드캐스트
4. 송신자 제외 처리

**브로드캐스트 로직:**
```kotlin
server.allServers.forEach { serverInfo ->
    if (serverInfo.serverInfo.name != source.serverInfo.name) {
        val optServer = server.getServer(serverInfo.serverInfo.name)
        if (optServer.isPresent) {
            optServer.get().sendPluginMessage(ServerStatusProxy.RESPONSE_CHANNEL, data)
        }
    }
}
```

## 시스템 아키텍처

### 통신 흐름

```
[로비 서버] --요청--> [Velocity 프록시] --전달--> [야생 서버]
     ↑                        ↓                      ↓
     ←--------응답 브로드캐스트←--------←------응답------
```

### 메시지 흐름 상세

1. **요청 단계**:
   - 로비 서버 → `lukevanilla:serverstatus_request` → Velocity 프록시
   - Velocity 프록시 → `lukevanilla:serverstatus_request` → 야생 서버

2. **응답 단계**:
   - 야생 서버 → `lukevanilla:serverstatus_response` → Velocity 프록시
   - Velocity 프록시 → `lukevanilla:serverstatus_response` → 모든 로비 서버

### 컴포넌트 관계도

```
VelocityMain (메인 플러그인)
    ├── 채널 등록 (luke:vanilla_status)
    ├── 명령어 등록 (/로비서버, /야생서버)
    └── 초기화 관리

ServerStatusProxy (상태 프록시)
    ├── 통신 채널 관리
    ├── 메시지 리스너 등록
    └── 서버 식별자 관리

ServerStatusMessageListener (메시지 처리)
    ├── 요청 메시지 중계
    ├── 응답 메시지 브로드캐스트
    └── 오류 처리 및 로깅
```

## 설정 및 사용법

### 서버 설정 요구사항

1. **Velocity 프록시 서버**
   - Velocity API 호환
   - 플러그인 로딩 활성화

2. **백엔드 서버 설정**
   - 로비 서버: 상태 요청 기능 구현 필요
   - 야생 서버: 상태 응답 기능 구현 필요
   - 서버 ID: `survival` (야생 서버)

### 플러그인 설치

1. 컴파일된 JAR 파일을 Velocity 프록시의 `plugins` 디렉토리에 배치
2. Velocity 프록시 서버 재시작
3. 로그에서 초기화 메시지 확인:
   ```
   [INFO] LukeVanilla Velocity plugin has been enabled!
   [INFO] Automatic server redirection system initialized
   [INFO] Successfully registered commands: /로비서버, /야생서버
   ```

### 명령어 사용법

- `/로비서버`: 플레이어를 로비 서버로 이동
- `/야생서버`: 플레이어를 야생 서버로 이동

## 로깅 및 디버깅

### 로그 메시지 유형

1. **초기화 로그**:
   ```
   [INFO] LukeVanilla Velocity plugin has been enabled!
   [INFO] [LukeVanilla] 서버 상태 프록시 초기화 완료
   ```

2. **통신 로그**:
   ```
   [INFO] [LukeVanilla] 서버 상태 요청: lobby -> survival
   [INFO] [LukeVanilla] 서버 상태 응답: survival -> ...
   ```

3. **오류 로그**:
   ```
   [WARNING] [LukeVanilla] 대상 서버를 찾을 수 없음: survival
   [WARNING] [LukeVanilla] 서버 상태 메시지 처리 중 오류: ...
   ```

### 디버깅 팁

1. **서버 연결 확인**: 야생 서버가 `survival` ID로 등록되어 있는지 확인
2. **채널 등록 확인**: 백엔드 서버에서 동일한 채널을 사용하는지 확인
3. **권한 확인**: 플레이어가 명령어 사용 권한을 가지고 있는지 확인

## 보안 고려사항

### 메시지 검증
- 송신자 타입 검증 (ServerConnection)
- 대상 서버 존재 여부 확인
- 예외 처리 및 로깅

### 접근 제어
- 특정 서버에서만 요청 허용 가능 (확장 가능)
- 명령어 권한 시스템 (필요시 구현)

## 확장 가능성

### 향후 개발 방향

1. **동적 서버 관리**: 서버 목록 동적 로딩
2. **권한 시스템**: 명령어별 권한 설정
3. **GUI 인터페이스**: 서버 선택 GUI
4. **로드 밸런싱**: 서버 부하에 따른 자동 분산
5. **상태 모니터링**: 실시간 서버 상태 대시보드

### 설정 파일 지원
현재는 하드코딩된 설정을 사용하지만, 향후 YAML/JSON 설정 파일 지원 예정:
```yaml
servers:
  survival: "survival"
  lobby: "lobby"
channels:
  request: "lukevanilla:serverstatus_request"
  response: "lukevanilla:serverstatus_response"
```

## 문제 해결

### 자주 발생하는 문제

1. **서버를 찾을 수 없음**
   - 원인: 서버 ID 불일치
   - 해결: Velocity 설정에서 서버 ID 확인

2. **메시지 전달 실패**
   - 원인: 채널 등록 누락
   - 해결: 백엔드 서버에서 동일한 채널 등록 확인

3. **명령어 작동 안함**
   - 원인: 플러그인 로딩 실패
   - 해결: 로그 확인 및 의존성 검증

## 버전 정보

- **현재 버전**: 1.0-SNAPSHOT
- **Velocity API**: 호환
- **Kotlin**: 사용
- **최종 수정**: 2025-09-06

---

*이 문서는 LukeVanilla Velocity 시스템의 코드 분석을 바탕으로 작성되었습니다.*
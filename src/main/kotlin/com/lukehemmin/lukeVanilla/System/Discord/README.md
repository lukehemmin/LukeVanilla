# 🎮 Discord 시스템

> LukeVanilla 서버의 Discord 통합 시스템

## 📋 개요

Discord 시스템은 마인크래프트 서버와 Discord 서버를 연동하여 다양한 기능을 제공합니다. JDA(Java Discord API) 라이브러리를 기반으로 하며, 사용자 인증, 역할 관리, 음성 채널 알림, AI 어시스턴트 등의 기능을 포함합니다.

---

## 🏗️ 핵심 컴포넌트

### 📌 메인 클래스

| 파일 | 설명 |
|------|------|
| [`DiscordBot.kt`](./DiscordBot.kt) | JDA 기반 Discord 봇 메인 클래스. Gateway Intent 설정 및 봇 초기화 담당 |

### 🔐 인증 시스템

| 파일 | 설명 |
|------|------|
| [`DiscordAuth.kt`](./DiscordAuth.kt) | 마인크래프트-디스코드 계정 연동 인증 처리. 6자리 인증코드 검증 및 역할 부여 |
| [`DiscordRoleManager.kt`](./DiscordRoleManager.kt) | Discord 역할 자동 부여/제거 관리 |
| [`DiscordLeave.kt`](./DiscordLeave.kt) | Discord 서버 탈퇴 시 마인크래프트 접속 차단 처리 |

### 🔊 음성 채널 시스템

| 파일 | 설명 |
|------|------|
| [`DiscordVoiceChannelListener.kt`](./DiscordVoiceChannelListener.kt) | 음성채널 입장/퇴장 시 마인크래프트 인게임 알림 |
| [`DynamicVoiceChannelManager.kt`](./DynamicVoiceChannelManager.kt) | 동적 음성채널 생성/삭제 (🌟 Lobby 서버 전용) |
| [`VoiceChannelTextListener.kt`](./VoiceChannelTextListener.kt) | 음성채널 내 텍스트 메시지를 마인크래프트로 전달 |

### 💬 채팅 리스너

| 파일 | 설명 |
|------|------|
| [`TitokerChatListener.kt`](./TitokerChatListener.kt) | 티토커 전용 채널 채팅 리스너. 같은 음성채널에 있는 플레이어에게 메시지 전달 |
| [`YeonhongChatListener.kt`](./YeonhongChatListener.kt) | 연홍 전용 채널 채팅 리스너 |

### 🎫 고객지원 시스템

| 파일 | 설명 |
|------|------|
| [`SupportSystem.kt`](./SupportSystem.kt) | 고객지원 티켓 시스템 메인 클래스 (🌟 Lobby 서버 전용) |
| [`SupportCaseManager.kt`](./SupportCaseManager.kt) | 지원 케이스 생성/관리/종료 처리 |
| [`SupportCaseListener.kt`](./SupportCaseListener.kt) | 지원 케이스 버튼 상호작용 리스너 |
| [`SupportCaseModal.kt`](./SupportCaseModal.kt) | 지원 케이스 모달 폼 처리 |

### 📊 서버 상태 시스템

| 파일 | 설명 |
|------|------|
| [`ServerStatusProvider.kt`](./ServerStatusProvider.kt) | 로비/야생 서버 상태 정보 제공 (TPS, MSPT, 플레이어 수) |
| [`ServerStatusListener.kt`](./ServerStatusListener.kt) | 서버 상태 요청 수신 및 응답 (야생 서버) |
| [`ServerStatusRequester.kt`](./ServerStatusRequester.kt) | 다른 서버에 상태 정보 요청 (로비 서버) |

### 🎃 시즌 아이템 조회

| 파일 | 설명 |
|------|------|
| [`SeasonItemViewer.kt`](./SeasonItemViewer.kt) | 할로윈/크리스마스/발렌타인 시즌 아이템 등록 정보 조회 |
| [`HalloweenItemViewer.kt`](./HalloweenItemViewer.kt) | 할로윈 아이템 전용 조회 |
| [`ItemRestoreLogger.kt`](./ItemRestoreLogger.kt) | 아이템 복구 내역 Discord 채널 로깅 |

### 👤 플레이어 이벤트

| 파일 | 설명 |
|------|------|
| [`PlayerJoinListener.kt`](./PlayerJoinListener.kt) | 플레이어 접속 시 Discord 역할 확인/부여 |
| [`PlayerLoginListener.kt`](./PlayerLoginListener.kt) | 플레이어 로그인 이벤트 처리 |

### 🛠️ 유틸리티

| 파일 | 설명 |
|------|------|
| [`EmojiUtil.kt`](./EmojiUtil.kt) | Discord 이모지를 텍스트로 변환하는 유틸리티 |

---

## 🤖 AI Assistant 서브시스템

> `AIassistant/` 폴더에 위치한 OpenAI 기반 관리자 AI 어시스턴트 (🌟 Lobby 서버 전용)

### 📁 파일 구조

| 파일 | 설명 |
|------|------|
| [`AdminAssistant.kt`](./AIassistant/AdminAssistant.kt) | OpenAI API를 통한 관리자 AI 어시스턴트 메인 클래스 |
| [`PromptManager.kt`](./AIassistant/PromptManager.kt) | AI 시스템 프롬프트 관리 |
| [`ToolManager.kt`](./AIassistant/ToolManager.kt) | MCP 스타일 AI 도구 관리 및 실행 |
| [`ToolDefinition.kt`](./AIassistant/ToolDefinition.kt) | 도구 정의 데이터 클래스 |

### 🔧 AI 도구 목록 (`tools/`)

| 파일 | 설명 |
|------|------|
| [`PlayerInfoTool.kt`](./AIassistant/tools/PlayerInfoTool.kt) | 플레이어 정보 조회 도구 |
| [`WarningTool.kt`](./AIassistant/tools/WarningTool.kt) | 경고 부여/조회/차감 도구 |
| [`ServerStatusTool.kt`](./AIassistant/tools/ServerStatusTool.kt) | 서버 상태 조회 도구 |
| [`ResetPlayerAuthTool.kt`](./AIassistant/tools/ResetPlayerAuthTool.kt) | 플레이어 인증 초기화 도구 |

---

## ⌨️ 관련 명령어

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/티토커메시지 <보기/끄기>` | 티토커 채팅 메시지 표시 설정 | 일반 |
| `/연홍메시지 <보기/끄기>` | 연홍 채팅 메시지 표시 설정 | 일반 |
| `/음성채널메시지 <보기/끄기>` | 음성채널 채팅 메시지 표시 설정 | 일반 |
| `/아이템복구` | 커스텀 아이템 복구 | 관리자 |

---

## 💾 데이터베이스 테이블

### `discord_authentication` (인증)
```sql
- UUID: 플레이어 UUID
- AuthCode: 6자리 인증코드
- IsAuth: 인증 완료 여부
```

### `Player_Data` (플레이어 데이터)
```sql
- UUID: 플레이어 UUID
- DiscordID: 연동된 Discord 사용자 ID
- NickName: 마인크래프트 닉네임
```

### `Discord_Account_Link` (계정 연동)
```sql
- primary_uuid: 기본 계정 UUID
- secondary_uuid: 부계정 UUID
```

### `SupportChatLink` (고객지원)
```sql
- UUID: 플레이어 UUID
- SupportID: 문의 케이스 ID (#000001 형식)
- CaseClose: 문의 종료 여부
- MessageLink: Discord 채널 링크
```

### `DynamicVoiceChannels` (동적 음성채널)
```sql
- channel_id: 생성된 음성채널 ID
- owner_id: 채널 소유자 Discord ID
```

### `Settings` (시스템 설정)
```sql
- setting_type: 설정 키
- setting_value: 설정 값
```

#### 주요 설정 키
| 키 | 설명 |
|-----|------|
| `DiscordToken` | Discord 봇 토큰 |
| `DiscordServerID` | Discord 서버 ID |
| `AuthChannel` | 인증 채널 ID |
| `AuthLogChannel` | 인증 로그 채널 ID |
| `DiscordAuthRole` | 인증 역할 ID |
| `SystemChannel` | 고객지원 시스템 채널 ID |
| `SupportCategoryId` | 지원 케이스 카테고리 ID |
| `SupportArchiveCategoryId` | 종료된 케이스 아카이브 카테고리 ID |
| `TTS_Channel` | 티토커 채팅 채널 ID |
| `TRIGGER_VOICE_CHANNEL_ID_1/2` | 동적 채널 생성 트리거 채널 ID |
| `VOICE_CATEGORY_ID_1/2` | 동적 채널 생성 대상 카테고리 ID |
| `RestoreItemInfo` | 아이템 복구 로그 채널 ID |
| `Assistant_Channel` | AI 어시스턴트 채널 ID |
| `OpenAI_API_Token` | OpenAI API 키 |
| `OpenAI_API_Endpoint` | OpenAI API 엔드포인트 |
| `OpenAI_API_Model` | 사용할 AI 모델 |

---

## 📦 의존성

### 외부 라이브러리
- **JDA (Java Discord API)** - Discord 봇 프레임워크
- **OpenAI Java SDK** - AI 어시스턴트용 (AIassistant 전용)
- **Gson** - JSON 파싱

### 내부 의존성
- `Database` - 데이터베이스 연결 관리
- `WarningService` - 경고 시스템
- `PlayTimeManager` - 플레이타임 조회
- `MultiServerReader` - 멀티서버 정보 조회
- `BanManager` - 차단 관리
- `BanEvasionDetector` - 차단 우회 감지

---

## 🖥️ 서버 타입별 기능

### 🏠 Lobby 서버 전용

| 기능 | 설명 |
|------|------|
| 🤖 **AdminAssistant** | OpenAI 기반 관리자 AI 어시스턴트 |
| 🎫 **SupportSystem** | 고객지원 티켓 시스템 |
| 🔊 **DynamicVoiceChannelManager** | 동적 음성채널 생성/삭제 |
| 📊 **ServerStatusRequester** | 야생 서버 상태 요청 |

### ⛏️ Vanilla (야생) 서버 전용

| 기능 | 설명 |
|------|------|
| 🔐 **DiscordAuth** | 인증코드 처리 (차단 우회 감지 포함) |
| 📊 **ServerStatusListener** | 서버 상태 응답 |

### 🌐 공통 기능 (모든 서버)

| 기능 | 설명 |
|------|------|
| 🔊 **DiscordVoiceChannelListener** | 음성채널 입장/퇴장 알림 |
| 💬 **VoiceChannelTextListener** | 음성채널 텍스트 메시지 |
| 👤 **DiscordLeave** | Discord 서버 탈퇴 시 처리 |
| 💬 **TitokerChatListener** | 티토커 채팅 리스너 |
| 💬 **YeonhongChatListener** | 연홍 채팅 리스너 |
| 📋 **ItemRestoreLogger** | 아이템 복구 로깅 |
| 🎃 **SeasonItemViewer** | 시즌 아이템 조회 |

---

## 🔄 데이터 흐름

### 인증 프로세스
```
1. 플레이어가 마인크래프트에서 /인증 명령어 실행
2. 6자리 인증코드 생성 및 Player_Auth 테이블에 저장
3. 플레이어가 Discord 인증 채널에 코드 입력
4. DiscordAuth가 코드 검증 및 Player_Data.DiscordID 업데이트
5. DiscordRoleManager가 인증 역할 부여
6. BanEvasionDetector가 차단 우회 시도 검사
```

### 음성채널 알림 프로세스
```
1. Discord 음성채널 입장/퇴장 이벤트 발생
2. DiscordVoiceChannelListener가 이벤트 감지
3. 같은 음성채널에 있는 인증된 플레이어 조회
4. 마인크래프트 인게임 메시지 전송
```

### 고객지원 프로세스
```
1. 사용자가 Discord에서 "관리자 문의" 버튼 클릭
2. SupportCaseModal에서 제목/설명 입력
3. SupportCaseManager가 새 채널 생성 및 DB 저장
4. 관리자가 해당 채널에서 응대
5. "문의 종료" 버튼으로 케이스 종료 및 아카이브
```

---

## ⚙️ 초기화 순서

```kotlin
// Main.kt에서의 초기화 순서
1. Database 초기화
2. DiscordBot 생성 및 시작
3. DiscordRoleManager 초기화
4. DiscordVoiceChannelListener 등록
5. VoiceChannelTextListener 등록
6. DynamicVoiceChannelManager 등록 (Lobby만)
7. ItemRestoreLogger 초기화
8. DiscordLeave 등록
9. TitokerChatListener 등록
10. YeonhongChatListener 등록
11. DiscordAuth 등록 (Vanilla만)
12. AdminAssistant 등록 (Lobby만, OpenAI 키 필요)
13. SupportSystem 등록 (Lobby만)
```

---

## 🛡️ Gateway Intents

[`DiscordBot.kt`](./DiscordBot.kt)에서 사용하는 인텐트:

| Intent | 용도 |
|--------|------|
| `GUILD_MESSAGES` | 서버 메시지 수신 |
| `MESSAGE_CONTENT` | 메시지 내용 읽기 |
| `GUILD_MEMBERS` | 멤버 정보 접근 |
| `GUILD_VOICE_STATES` | 음성 상태 추적 |
| `GUILD_EMOJIS_AND_STICKERS` | 커스텀 이모지 사용 |
| `SCHEDULED_EVENTS` | 예약 이벤트 |

---

## 📝 참고 사항

- Discord 봇은 서버 종료 시 자동으로 정리됩니다 (`Main.onDisable()`)
- 인증 관련 메시지는 1분 후 자동 삭제됩니다
- 동적 음성채널은 모든 사용자가 퇴장하면 자동 삭제됩니다
- AI 어시스턴트는 채널별로 최근 8개 대화를 컨텍스트로 유지합니다
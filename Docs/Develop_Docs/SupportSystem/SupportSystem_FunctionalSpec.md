# 고객지원 시스템 고도화 기능 정의서

## 1. 문서 개요
- **목적**: `SupportSystem`(src/main/kotlin/com/lukehemmin/lukeVanilla/System/Discord/SupportSystem.kt)에서 제공하는 고객지원 채널 기능을 확장하여, 디스코드 사용자 기준으로 마인크래프트 계정 정보를 조회·관리하고 인증 절차를 제어한다.
- **범위**: 디스코드 지원 채널 기본 메시지, 계정 정보 조회, 부계정 연동/해제, 인증 해제 확인 절차, 시즌 아이템 정보 연계를 포함한다.
- **선행 조건**: JDA 5 기반 봇이 `Settings` 테이블에 등록된 `SystemChannel`에 상주하며, `DiscordServerID`·`DiscordAuthRole`·`DiscordToken` 설정과 데이터베이스 초기 스키마(`DatabaseInitializer`)가 적용되어 있어야 한다.

## 2. 현행 시스템 요약
- 지원 채널 초기화 시 3개의 버튼(`my_info`, `admin_support`, `halloween_info`)이 포함된 임베드 메시지를 발행한다.
- `my_info` 버튼은 현재 "내 정보 기능은 아직 구현 중입니다."라는 에피머럴 응답만 반환하며, 실질적인 데이터 조회 로직은 존재하지 않는다.
- 시즌 아이템 조회는 `SeasonItemViewer` 클래스에서 버튼 인터랙션을 받아 처리할 준비가 되어 있다.

## 3. 신규 기능 요구사항
### 3.1 지원 채널 메시지 및 버튼 구성
- 기존 메시지를 재사용하되 버튼을 아래와 같이 재배치한다.
  - `my_info` (Primary, 라벨: `내 정보`)
  - `admin_support` (Secondary, 라벨: `관리자 문의`)
  - `season_items` (Success, 라벨: `아이템 등록 정보 보기`) — 기존 `halloween_info` 대신 통합 식별자 사용
- `season_items` 버튼 인터랙션을 수신하면 즉시 `SeasonItemViewer.showSeasonSelectionButtons(event)`를 호출해 기존 시즌별 버튼 셀렉터(`season_halloween`, `season_christmas`, `season_valentine`)를 에피머럴로 띄운다. 기존 ID를 계속 사용하므로 `SeasonItemViewer` 본체 수정은 필요 없다.
- `my_info` 이외 버튼 동작은 변하지 않으며, 신규 로직은 모두 `my_info` 하위에서 트리거된다.

### 3.2 내 정보 조회 플로우
| 단계 | 설명 |
| --- | --- |
| 1 | 버튼 인터랙션 수신 후 에피머럴 응답을 위해 `deferReply(true)` 수행 |
| 2 | `Discord_Account_Link`에서 `primary_uuid`를 조회하여 기본 계정 UUID를 확보<br>- 조회되지 않으면 "연동된 마인크래프트 계정을 찾을 수 없습니다." 반환 |
| 3 | `Player_Data`에서 기본 계정 UUID로 닉네임, 마지막 접속 IP 등 기본 정보를 조회 |
| 4 | 기본 계정 UUID로 `Player_Auth`, `Player_NameTag`를 조회하여 인증 상태, 칭호를 확보 |
| 5 | 플레이타임 조회 (§8 하이브리드 방식 사용)<br>- `PlayTimeManager.isPlayerOnline(uuid)` 확인<br>- 온라인이면 `getCurrentTotalPlayTime(player)` 사용 (현재 세션 포함)<br>- 오프라인이면 `getSavedTotalPlayTime(uuid)` 사용 (DB만) |
| 6 | 플레이어 스킨 이미지는 `https://mc-heads.net/avatar/{uuid}/128` 형식 URL을 사용 |
| 7 | 조회 결과를 임베드 형태로 반환하며, 하단에 인터랙션 버튼을 배치 |

- **메인 계정 임베드 필드**
  - 제목: `⚙ 내 계정 정보`
  - 필수 필드: 닉네임, UUID, 칭호(없으면 `없음`), 누적 플레이타임(예: `10일 2시간 30분`), 마지막 접속 IP (`Player_Data.Lastest_IP`), 인증 상태(`Player_Auth.IsAuth`)
  - 썸네일: 스킨 머리 이미지 URL
  - 푸터: "최근 갱신: {YYYY-MM-DD HH:MM:SS KST}" (DB 조회 시각 기준)
- **임베드 하단 버튼**
  - `auth_unlink_primary` (Danger, 라벨: `인증 해제`)
  - `link_secondary_account` (Primary, 라벨: `부계정 연결`) — 부계정이 없을 때만 활성화
  - `show_secondary_account` (Secondary, 라벨: `부계정 정보`) — 부계정이 연결되어 있을 때만 노출
  - `season_items_from_profile` (Success, 라벨: `아이템 등록 보기`)

### 3.3 부계정 기능
#### 3.3.0 기본/부계정 데이터 보존 전략
- `Player_Data`는 그대로 유지하며, 각 계정의 `DiscordID`는 인증 시점에 디스코드 사용자 ID로 업데이트한다. 기본 계정과 부계정 모두 동일한 `DiscordID`를 가지며, 구분용 메타데이터는 별도 링크 테이블에서 관리한다.
- 신규 테이블 `Discord_Account_Link`(§4.2 참조)를 사용하여 기본-부계정 관계를 관리한다.
  - `primary_uuid`는 기본 계정 UUID를 저장한다.
  - `secondary_uuid`는 부계정 UUID를 저장하며, **UNIQUE 제약**을 통해 한 부계정이 여러 디스코드에 연결되는 것을 방지한다.

#### 3.3.0.1 마이그레이션 절차
초기 배포 시 다음 절차로 `Discord_Account_Link` 테이블을 채운다:

1. **테이블 생성**
   - `Discord_Account_Link` 테이블을 생성한다 (§4.2 참조).

2. **기본 계정 선정**
   - `Player_Data`에서 `DiscordID IS NOT NULL`인 레코드를 `DiscordID` 기준으로 그룹화한다.
   - 각 그룹에서 최근 로그인 시각을 기준으로 기본 계정을 선정한다:
     ```sql
     SELECT pd.UUID, pd.DiscordID
     FROM Player_Data pd
     INNER JOIN (
         SELECT UUID, MAX(ConnectedAt) as latest_connection
         FROM Connection_IP
         GROUP BY UUID
     ) ci ON pd.UUID = ci.UUID
     WHERE pd.DiscordID IS NOT NULL
     ORDER BY ci.latest_connection DESC
     ```
   - 각 `DiscordID` 그룹에서 가장 최근 `ConnectedAt`을 가진 UUID를 `primary_uuid`로 선정한다.

3. **데이터 삽입**
   - 선정된 기본 계정을 `Discord_Account_Link`에 삽입한다:
     ```sql
     INSERT INTO Discord_Account_Link (primary_uuid, secondary_uuid)
     VALUES (?, NULL)
     ```

4. **부계정 처리**
   - 같은 `DiscordID`를 가진 나머지 UUID는 부계정 후보 목록으로 관리한다.
   - 추후 UI를 통해 사용자가 직접 부계정으로 연결하거나, 관리자 스크립트로 `secondary_uuid`를 설정한다.

- 인증 해제로 `Player_Data.DiscordID`가 `NULL`이 되면 `Discord_Account_Link`에서 해당 행을 삭제하여 재연동 시 중복 데이터를 방지한다.
#### 3.3.1 부계정 연결 모달
- 모달 ID: `link_secondary_account_modal`
- 입력 요소
  - `auth_code` (단일 라인, 라벨: `마인크래프트 인증코드`, 길이 6)

- 처리 로직 (`SupportCaseListener.kt`의 `onModalInteraction` 패턴 참조)
  1. **검증 단계**
     - `Player_Auth.AuthCode`와 일치하는 UUID를 조회한다.
     - 다음 조건을 모두 만족하는 계정만 허용:
       - `Player_Auth.IsAuth = 0` (미인증 상태)
       - `Player_Data.DiscordID IS NULL` (다른 디스코드에 연결되지 않음)
       - `Discord_Account_Link.secondary_uuid`에 해당 UUID가 존재하지 않음 (이미 부계정으로 등록되지 않음)

  2. **연결 처리**
     - 검증 통과 시 해당 UUID의 `Player_Data.DiscordID`를 호출자 디스코드 ID로 갱신한다.
     - `Player_Auth.IsAuth`를 `1`로 설정한다.
     - 호출자의 `Discord_Account_Link` 행에서 `secondary_uuid` 칼럼을 해당 UUID로 갱신한다:
       ```sql
       UPDATE Discord_Account_Link
       SET secondary_uuid = ?
       WHERE primary_uuid = ?
       ```

  3. **성공 응답**
     - "부계정 연결이 완료되었습니다." 에피머럴 메시지를 전송한다.
     - 부계정 정보를 다시 로딩하여 임베드를 업데이트한다.

- 실패 시 에피머럴 오류 메시지 반환 (예: "인증코드가 유효하지 않습니다.", "이미 다른 디스코드에 연결된 계정입니다.")

#### 3.3.2 부계정 정보 조회
- `show_secondary_account` 버튼 클릭 시 메인 계정 플로우와 동일한 형식으로 임베드를 작성하되, 제목을 `👥 부계정 정보`로 표기하고 `부계정 연결 해제`, `아이템 등록 보기` 버튼만 노출한다.
- 버튼 구성
  - `unlink_secondary_account` (Danger, 라벨: `부계정 연결 해제`)
  - `season_items_from_secondary` (Success, 라벨: `아이템 등록 보기`)

#### 3.3.3 부계정 연결 해제
- `unlink_secondary_account` 클릭 시 트랜잭션으로 다음 작업 수행:
  ```kotlin
  database.getConnection().use { conn ->
      conn.autoCommit = false
      try {
          // 1. Player_Data 업데이트
          // 2. Player_Auth 업데이트
          // 3. Discord_Account_Link 업데이트
          conn.commit()
      } catch (e: Exception) {
          conn.rollback()
          throw e
      }
  }
  ```

  1. `Player_Data`에서 부계정 UUID의 `DiscordID`를 `NULL`로 업데이트
  2. `Player_Auth.IsAuth`를 `0`으로 설정 (인증코드는 재발급하지 않음 - §5.1 참조)
  3. `Discord_Account_Link.secondary_uuid`를 `NULL`로 업데이트
  4. 완료 후 "부계정 연결이 해제되었습니다." 에피머럴 알림을 전송하고 메인 정보 임베드 새로 고침

### 3.4 인증 해제 플로우 (기본 계정)
1. `auth_unlink_primary` 클릭 시 아래 텍스트를 포함한 임베드를 에피머럴로 전송하고 5분 동안 유지한다.
   > "인증을 해제하는 경우 디스코드 인증 역할이 제외되어 다른 채팅방을 이용할 수 없게 되며 기존 계정으로 접속이 불가능합니다. 다시 인증이 가능하며 다른 마인크래프트 계정, 아이디로 본계를 전환할 때 이 기능을 사용할 수 있습니다. 마인크래프트 도전과제 진행 정보는 이동되지 않으며 아이템도 이동되지 않습니다. 이 상황을 이해했고 계속 진행하고 싶으면 아래 버튼을 클릭하여 연결을 해제하거나 취소할 수 있습니다."
2. 임베드 하단 버튼
   - `auth_unlink_confirm` (Success, 라벨: `계속합니다.`)
   - `auth_unlink_cancel` (Danger, 라벨: `취소`)

3. `auth_unlink_confirm` 처리 (트랜잭션 사용)
   ```kotlin
   database.getConnection().use { conn ->
       conn.autoCommit = false
       try {
           // 1. Player_Data 업데이트
           // 2. Player_Auth 업데이트
           // 3. Discord_Account_Link 삭제
           // 4. 디스코드 역할 제거
           conn.commit()
       } catch (e: Exception) {
           conn.rollback()
           throw e
       }
   }
   ```

   - **데이터베이스 작업**:
     - `Player_Data`에서 기본 UUID의 `DiscordID`를 `NULL`로 설정
     - 연결된 부계정이 있으면 부계정 UUID의 `DiscordID`도 `NULL`로 설정
     - `Player_Auth.IsAuth`를 두 계정 모두 `0`으로 갱신 (인증코드는 재발급하지 않음 - §5.1 참조)
     - `Discord_Account_Link` 행 삭제

   - **디스코드 역할 제거** (`DiscordRoleManager.kt` 패턴 재사용):
     ```kotlin
     fun removeAuthRole(discordId: String) {
         val serverId = database.getSettingValue("DiscordServerID") ?: return
         val roleId = database.getSettingValue("DiscordAuthRole") ?: return

         val guild = jda.getGuildById(serverId) ?: return
         val role = guild.getRoleById(roleId) ?: return

         val member = try {
             guild.retrieveMemberById(discordId).complete()
         } catch (e: Exception) {
             logger.warning("멤버 조회 실패: ${e.message}")
             return
         }

         if (member.roles.contains(role)) {
             guild.removeRoleFromMember(member, role).queue(
                 { logger.info("역할 제거 성공: $discordId") },
                 { error -> logger.severe("역할 제거 실패: ${error.message}") }
             )
         }
     }
     ```

   - **완료 처리**:
     - 성공 시 "인증이 해제되었습니다." 에피머럴 확인 메시지 전송
     - 실패 시 롤백 후 "작업 중 오류가 발생했습니다. 다시 시도해주세요." 메시지 전송

4. `auth_unlink_cancel` 버튼 클릭 시 "작업이 취소되었습니다." 에피머럴 메시지를 전송한다.

5. **메시지 타임아웃 처리**
   - 에피머럴 메시지 전송 후 5분 뒤 자동 삭제:
     ```kotlin
     event.reply("...")
         .setEphemeral(true)
         .queue { hook ->
             hook.deleteOriginal().queueAfter(5, TimeUnit.MINUTES)
         }
     ```
   - JDA의 버튼 상호작용 타임아웃(15분)보다 짧은 5분을 사용하여 사용자가 오래된 메시지를 클릭하는 것을 방지한다.
   - 타임아웃 후 버튼 클릭 시 "This interaction failed" 오류가 발생하므로, 버튼 핸들러에서 별도 예외 처리를 추가할 수 있다.

### 3.5 아이템 등록 보기 연동
- `season_items_from_profile` 및 `season_items_from_secondary` 버튼은 `SeasonItemViewer`의 버튼 핸들러를 재사용한다.
- 재사용 방법: 해당 버튼 인터랙션에서 `SeasonItemViewer.showSeasonSelectionButtons(event)` 호출 후 종료한다.

## 4. 데이터 요구사항
### 4.1 기존 테이블 활용
| 테이블 | 주요 칼럼 | 용도 |
| --- | --- | --- |
| `Player_Data` | `UUID`, `NickName`, `DiscordID`, `Lastest_IP` | 계정 기본 정보 및 디스코드 연동 상태 |
| `Player_Auth` | `UUID`, `IsAuth`, `AuthCode`, `IsFirst` | 인증 여부 및 인증코드 검증 |
| `Player_NameTag` | `UUID`, `Tag` | 계정 칭호 |
| `playtime_data` | `player_uuid`, `total_playtime_seconds`, `session_start_time` | 플레이타임 산출 |

### 4.2 신규 테이블 : `Discord_Account_Link`
```sql
CREATE TABLE IF NOT EXISTS Discord_Account_Link (
    primary_uuid VARCHAR(36) NOT NULL,
    secondary_uuid VARCHAR(36) UNIQUE,
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (primary_uuid, secondary_uuid),
    INDEX idx_primary (primary_uuid),
    INDEX idx_secondary (secondary_uuid)
);
```

| 칼럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `primary_uuid` | VARCHAR(36) | NOT NULL | 기본 계정 UUID |
| `secondary_uuid` | VARCHAR(36) | **UNIQUE** | 부계정 UUID (없을 경우 NULL)<br>**UNIQUE 제약으로 한 부계정이 여러 디스코드에 연결되는 것 방지** |
| `linked_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 최초 연동 일시 |

- **테이블 설계 특징**:
  - `primary_uuid`와 `secondary_uuid`의 조합이 PRIMARY KEY이다.
  - `secondary_uuid`에 **UNIQUE 제약**을 추가하여 한 마인크래프트 계정이 여러 디스코드 계정에 부계정으로 등록되는 것을 방지한다.
  - 한 `primary_uuid`는 하나의 `secondary_uuid`만 가질 수 있다 (1:1 관계).

- **데이터 관리 규칙**:
  - `Player_Data.DiscordID`는 기본·부계정 모두에 동일한 디스코드 ID를 저장한다.
  - 인증 해제 시 `Player_Data.DiscordID`가 `NULL`이 되면 이 테이블에서 해당 행도 삭제한다.
  - 초기 배포 시 마이그레이션은 §3.3.0.1 참조.

### 4.3 데이터 상태 규칙 및 정합성 검증
- **부계정 연결 조건**:
  1. 해당 마인크래프트 계정의 `Player_Data.DiscordID IS NULL` (다른 디스코드에 미인증)
  2. 해당 UUID가 `Discord_Account_Link.secondary_uuid`에 존재하지 않음 (이미 부계정으로 등록 안됨)
  3. 연결하려는 디스코드 사용자의 `Discord_Account_Link.secondary_uuid IS NULL` (부계정이 없는 상태)

- **검증 쿼리 예시**:
  ```sql
  SELECT COUNT(*) FROM Player_Data
  WHERE UUID = ? AND (DiscordID IS NULL OR DiscordID = '')

  AND NOT EXISTS (
      SELECT 1 FROM Discord_Account_Link WHERE secondary_uuid = ?
  )
  ```

- **기본/부계정 구분 로직**:
  1. `Discord_Account_Link` 테이블을 먼저 조회한다.
  2. `secondary_uuid`로 조회되면 → 부계정
  3. 조회되지 않으면 → 기본 계정
  4. 이후 `Player_Data`에서 상세 정보를 조회한다.

- **인증 해제 시 처리**:
  - 기본 계정 인증 해제: `Discord_Account_Link` 행 전체를 삭제한다.
  - 부계정 연결 해제: `Discord_Account_Link.secondary_uuid`를 `NULL`로 설정한다.

## 5. 메시지 및 임베드 규격
| 구분 | 제목 | 색상 | 특징 |
| --- | --- | --- | --- |
| 기본 계정 정보 | `⚙ 내 계정 정보` | `Color.BLUE` | 버튼 4종 포함 |
| 부계정 정보 | `👥 부계정 정보` | `Color.CYAN` | 연결 해제·아이템 버튼만 |
| 인증 해제 경고 | `⚠ 인증 해제 안내` | `Color.ORANGE` | 사용자 전용, 5분 유지 |
| 성공/실패 알림 | `✅ 작업 완료` / `❌ 작업 실패` | 상황별 | 에피머럴 텍스트 또는 소형 임베드 |

## 5.1 인증코드 재발급 시나리오
- `PlayerLoginListener`는 로그인 이벤트에서 항상 `database.ensurePlayerAuth(uuid)`를 호출한다(`PlayerLoginListener.kt:20`).
- `ensurePlayerAuth`는 `Player_Auth`에 행이 없으면 생성하고, 기존 행이면 저장된 `AuthCode`를 그대로 반환한다(`Database.kt:170 이후`).
- 로그인 후 `Player_Auth.IsAuth = 0`이면 서버는 `PlayerLoginEvent.Result.KICK_OTHER`로 접속을 거부하면서 최신 인증코드를 킥 메시지에 포함한다(`PlayerLoginListener.kt:52`).
- 따라서 인증 해제 후 유저가 다시 접속하면 자동으로 새 코드가 노출되며, 별도 재발급 API 없이도 디스코드 인증 채널에서 동일한 코드로 연동을 진행할 수 있다.

## 6. 오류 및 예외 처리
- **미연동 사용자**: `Discord_Account_Link` 조회 결과가 없거나 `Player_Data.DiscordID`가 NULL이면 "연동된 마인크래프트 계정을 찾을 수 없습니다." 반환
- **인증 코드 오류**:
  - 코드 미일치: "인증코드가 유효하지 않습니다."
  - 이미 다른 디스코드에 연결: "이미 다른 디스코드에 연결된 계정입니다."
  - 이미 부계정으로 등록: "이미 부계정으로 등록된 계정입니다."
- **중복 요청**: 이미 부계정이 연결된 상태에서 다시 연결 시도 시 "부계정이 이미 연결되어 있습니다." 반환
- **트랜잭션 실패**:
  - 데이터베이스 작업 실패 시 자동 롤백
  - 사용자에게 "작업 중 오류가 발생했습니다. 다시 시도해주세요." 에피머럴 메시지 전송
  - 서버 로그에 상세 오류 기록
- **역할 제거 실패**:
  - JDA 예외 발생 시 서버 콘솔에 경고 로그 기록
  - 사용자에게 "역할 제외 작업이 실패했습니다. 관리자에게 문의해주세요." 에피머럴 메시지 전송
  - 필요 시 관리자 알림 채널에 브로드캐스트

## 7. 권한 및 보안 고려 사항
- 모든 응답은 에피머럴로 처리하여 요청자에게만 표시한다.
- 인증 해제 확정 시 `DiscordAuthRole` 제거와 동시 진행, 실패 시 관리자 알림 로그를 남긴다.
- 모달 입력값 및 버튼 ID는 고정 문자열을 사용하여 리플레이 공격을 방지하고, 5분 제한 메시지는 스케줄러로 삭제하거나 JDA 타임아웃을 활용한다.
- 부계정 연결 시 2계정 이상 묶는 시나리오는 지원하지 않으며, 필요 시 설계 변경이 요구된다.

## 8. 플레이타임 산출 기준
`PlayTimeManager`(`PlayTimeManager.kt`)를 `SupportSystem` 생성자에 주입받아 **하이브리드 방식**으로 플레이타임을 조회한다.

### 8.1 구현 방식
- **SupportSystem 생성자 수정** (`Main.kt:376` 참조):
  ```kotlin
  class SupportSystem(
      private val discordBot: DiscordBot,
      private val database: Database,
      private val playTimeManager: PlayTimeManager  // 추가
  )
  ```

- **플레이타임 조회 로직**:
  ```kotlin
  fun getPlayTime(uuid: UUID): Long {
      // 1. 플레이어가 온라인인지 확인
      val player = plugin.server.getPlayer(uuid)

      return if (player != null && playTimeManager.isPlayerOnline(uuid)) {
          // 온라인: 현재 세션 포함하여 조회 (메모리 기반)
          playTimeManager.getCurrentTotalPlayTime(player)
      } else {
          // 오프라인: DB에서만 조회
          playTimeManager.getSavedTotalPlayTime(uuid)
      }
  }
  ```

### 8.2 작동 원리
1. **온라인 플레이어**:
   - `PlayTimeManager.sessionStartTimes` (ConcurrentHashMap)에서 현재 세션 정보 확인
   - `getCurrentTotalPlayTime(player)` 호출로 현재 세션 시간 포함한 정확한 플레이타임 반환

2. **오프라인 플레이어**:
   - `getSavedTotalPlayTime(uuid)` 호출로 DB에 저장된 플레이타임만 반환
   - 현재 세션 정보가 없으므로 마지막 저장 시점의 플레이타임 사용

### 8.3 사용자 안내
임베드에 플레이타임 표시 시 다음과 같이 안내한다:
- 온라인: `누적 플레이타임: 10일 2시간 30분 *현재 세션 포함`
- 오프라인: `누적 플레이타임: 10일 2시간 30분`


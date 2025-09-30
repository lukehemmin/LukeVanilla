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
- `my_info` 이외 버튼 동작은 변하지 않으며, 신규 로직은 모두 `my_info` 하위에서 트리거된다.

### 3.2 내 정보 조회 플로우
| 단계 | 설명 |
| --- | --- |
| 1 | 버튼 인터랙션 수신 후 에피머럴 응답을 위해 `deferReply(true)` 수행 |
| 2 | `Player_Data`에서 `DiscordID = event.user.id` 인 행을 조회하여 기본 계정 UUID와 닉네임을 확보 |
| 3 | 기본 계정 UUID로 `Player_Auth`, `Player_NameTag`, `playtime_data`를 조회하여 인증 상태, 칭호, 누적 플레이타임을 계산 |
| 4 | 플레이어 스킨 이미지는 `https://mc-heads.net/avatar/{uuid}/128` 형식 URL을 사용 |
| 5 | 조회 결과를 임베드 형태로 반환하며, 하단에 인터랙션 버튼을 배치 |

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
#### 3.3.1 부계정 연결 모달
- 모달 ID: `link_secondary_account_modal`
- 입력 요소
  - `auth_code` (단일 라인, 라벨: `마인크래프트 인증코드`, 길이 6)
- 처리 로직
  1. 모달 제출 시 `Player_Auth.AuthCode`와 일치하는 UUID를 조회하고, `IsAuth=0`이며 `Player_Data.DiscordID IS NULL`인 계정만 허용
  2. 검증 통과 시 해당 UUID의 `Player_Data.DiscordID`를 호출자 디스코드 ID로 갱신하고 `Player_Auth.IsAuth`를 `1`로 설정
  3. `Discord_Account_Link` 테이블(§4.2)에서 `secondary_uuid` 칼럼을 갱신
  4. 성공 임베드: "부계정 연결이 완료되었습니다." 메시지와 함께 부계정 정보를 다시 로딩
- 실패 시 에피머럴 오류 메시지 반환 (예: "인증코드가 유효하지 않습니다.")

#### 3.3.2 부계정 정보 조회
- `show_secondary_account` 버튼 클릭 시 메인 계정 플로우와 동일한 형식으로 임베드를 작성하되, 제목을 `👥 부계정 정보`로 표기하고 `부계정 연결 해제`, `아이템 등록 보기` 버튼만 노출한다.
- 버튼 구성
  - `unlink_secondary_account` (Danger, 라벨: `부계정 연결 해제`)
  - `season_items_from_secondary` (Success, 라벨: `아이템 등록 보기`)

#### 3.3.3 부계정 연결 해제
- `unlink_secondary_account` 클릭 시 즉시 다음 작업 수행
  1. `Player_Data`에서 부계정 UUID의 `DiscordID`를 `NULL`로 업데이트
  2. `Player_Auth.IsAuth`를 `0`으로 설정하고 `AuthCode` 재발급 필요 시 새 코드 생성 (기존 로직 재사용)
  3. `Discord_Account_Link.secondary_uuid`를 `NULL`로 업데이트
  4. 완료 후 "부계정 연결이 해제되었습니다." 에피머럴 알림을 전송하고 메인 정보 임베드 새로 고침

### 3.4 인증 해제 플로우 (기본 계정)
1. `auth_unlink_primary` 클릭 시 아래 텍스트를 포함한 임베드를 에피머럴로 전송하고 5분 동안 유지한다.
   > "인증을 해제하는 경우 디스코드 인증 역할이 제외되어 다른 채팅방을 이용할 수 없게 되며 기존 계정으로 접속이 불가능합니다. 다시 인증이 가능하며 다른 마인크래프트 계정, 아이디로 본계를 전환할 때 이 기능을 사용할 수 있습니다. 마인크래프트 도전과제 진행 정보는 이동되지 않으며 아이템도 이동되지 않습니다. 이 상황을 이해했고 계속 진행하고 싶으면 아래 버튼을 클릭하여 연결을 해제하거나 취소할 수 있습니다."
2. 임베드 하단 버튼
   - `auth_unlink_confirm` (Success, 라벨: `계속합니다.`)
   - `auth_unlink_cancel` (Danger, 라벨: `취소`)
3. `auth_unlink_confirm` 처리
   - `Player_Data`에서 기본 UUID의 `DiscordID`를 `NULL`로 설정
   - 연결된 부계정이 있으면 부계정 UUID의 `DiscordID`도 `NULL`로 설정
   - `Player_Auth.IsAuth`를 두 계정 모두 `0`으로 갱신 및 `AuthCode` 재발급 (선택 사항)
   - `Discord_Account_Link` 행 삭제 혹은 `secondary_uuid` 초기화
   - `DiscordAuthRole` 제거 (JDA `Guild.removeRoleFromMember` 사용)
   - 완료 후 "인증이 해제되었습니다." 에피머럴 확인 메시지 전송
4. `auth_unlink_cancel` 버튼 클릭 시 경고 메시지를 즉시 삭제하거나 "작업이 취소되었습니다." 에피머럴 메시지를 전송

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
| 칼럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `discord_id` | VARCHAR(30) | PK | 디스코드 사용자 ID |
| `primary_uuid` | VARCHAR(36) | NOT NULL, UNIQUE | 기본 계정 UUID |
| `secondary_uuid` | VARCHAR(36) | NULL | 부계정 UUID (없을 경우 NULL) |
| `linked_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 최초 연동 일시 |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 마지막 갱신 일시 |

- `Player_Data.DiscordID`는 기본·부계정 모두에 동일한 `discord_id`를 저장한다.
- 하나의 디스코드 ID당 정확히 한 행만 존재한다.

### 4.3 데이터 상태 규칙
- 부계정 연결 시 `Discord_Account_Link.secondary_uuid`가 비어 있어야 하며, 이미 값이 존재하면 연결 요청을 거절한다.
- `Player_Data.DiscordID`가 다른 디스코드 ID로 설정된 계정은 부계정으로 연결할 수 없다.
- 인증 해제 시 `Discord_Account_Link` 행 전체를 삭제하거나 `secondary_uuid`를 초기화하여 재사용 가능 상태로 만든다.

## 5. 메시지 및 임베드 규격
| 구분 | 제목 | 색상 | 특징 |
| --- | --- | --- | --- |
| 기본 계정 정보 | `⚙ 내 계정 정보` | `Color.BLUE` | 버튼 4종 포함 |
| 부계정 정보 | `👥 부계정 정보` | `Color.CYAN` | 연결 해제·아이템 버튼만 |
| 인증 해제 경고 | `⚠ 인증 해제 안내` | `Color.ORANGE` | 사용자 전용, 5분 유지 |
| 성공/실패 알림 | `✅ 작업 완료` / `❌ 작업 실패` | 상황별 | 에피머럴 텍스트 또는 소형 임베드 |

## 6. 오류 및 예외 처리
- **미연동 사용자**: `Player_Data` 조회 결과가 없거나 `DiscordID`가 NULL이면 "연동된 마인크래프트 계정을 찾을 수 없습니다." 반환
- **인증 코드 오류**: 코드 미일치, 만료, 이미 다른 디스코드에 연결된 경우 각각 구체 메시지 표시
- **중복 요청**: 이미 부계정이 연결된 상태에서 다시 연결 시도 시 "부계정이 이미 연결되어 있습니다." 반환
- **DB 예외**: SQL 실패 시 내부 로그 기록 후 "일시적인 오류가 발생했습니다." 노출
- **역할 제거 실패**: JDA 예외 발생 시 로그 기록 및 사용자에게 재시도 안내

## 7. 권한 및 보안 고려 사항
- 모든 응답은 에피머럴로 처리하여 요청자에게만 표시한다.
- 인증 해제 확정 시 `DiscordAuthRole` 제거와 동시 진행, 실패 시 관리자 알림 로그를 남긴다.
- 모달 입력값 및 버튼 ID는 고정 문자열을 사용하여 리플레이 공격을 방지하고, 5분 제한 메시지는 스케줄러로 삭제하거나 JDA 타임아웃을 활용한다.
- 부계정 연결 시 2계정 이상 묶는 시나리오는 지원하지 않으며, 필요 시 설계 변경이 요구된다.

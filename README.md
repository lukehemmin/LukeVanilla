<div align="center">

# <img src="https://i.ibb.co/5WRSpzjQ/2.png" alt="아이콘" width="150" height="150"/></br> LukeVanilla
Paper 1.21.x + Velocity 통합 플러그인</br>

( 아이콘 제작자 Discord : whegrae )

</div>

## 소개
LukeVanilla는 Kotlin(Gradle, ShadowJar)로 개발된 Paper/Velocity 통합 플러그인입니다. Discord(JDA) 연동, 마이랜드(청크 보호), 시즌 아이템/통계, 서버 간 통신(로비↔야생), HMCCosmetics/Custom-Crops/Nexo 등 다양한 플러그인과의 연동을 통해 서버 운영 전반을 지원합니다.

[![LukeVanilla Server Status](https://api.loohpjames.com/serverbanner.png?ip=mine.lukehemmin.com)](https://ko.namemc.com/server/mine.lukehemmin.com)

## 주요 기능
- 디스코드 연동: 인증/역할 부여(DiscordAuth), 관리자 채팅 동기화, 티토커/음성채널 메시지 토글, 동적 음성채널, 고객지원 메시지/티켓, 아이템 복구 로그, 서버 상태 브로드캐스트, 관리자 어시스턴트(OpenAI 연동)
- 서버 통합: 로비/야생 간 상태 요청/응답(PluginMessage), 종료 임박 알림 전송, Velocity 플러그인 동봉
- 토지/보호: MyLand(청크 단위 보호/이력), FarmVillage(농사 특화 영역/상점/거래 GUI), 안전구역/폭발 차단, AntiVPN
- 아이템 시스템: 시즌 아이템 등록/조회/수령, 아이템 킬 통계(바닐라+Nexo), 투명 액자, 커스텀 아이템 유틸(스크롤/레벨 스틱), Oraxen 가구 배치 차단, Nexo 아이템 복구
- 경제/운영: 간단 송금 명령어(돈), 접속/퇴장 메시지 관리, 관리자 전용 브로드캐스트/도구

## 지원/의존 플러그인
- 필수(depend): HMCCosmetics, CustomCrops
- 권장/소프트: Nexo, Citizens, LuckPerms, Oraxen, ItemsAdder, GSit

## 요구 사항
- Java 21, Gradle(Wrapper 제공)
- Paper 1.21.4+ (코드 기준 1.21.6 API 사용), Velocity(선택)
- MySQL 8.x 권장

> 주의: 일부 SQL이 `lukevanilla` 스키마명을 하드코딩으로 참조합니다(예: AdminAssistant). `config.yml`의 `database.name`을 `lukevanilla`로 설정하거나, 필요 시 관련 쿼리를 환경에 맞게 수정하세요.

## 빠른 시작
1) 저장소 클론 후 설정 파일 편집
- `src/main/resources/config.yml`
  - `service.type`: `Vanilla` 또는 `Lobby` (서버 역할에 따라 기능 분기)
  - `database`: MySQL 접속 정보
  - `wardrobe`: HMCCosmetics 옷장 월드/위치/반경
  - `debug`: 시스템별 디버그 플래그

2) 데이터베이스 준비(최초 실행 시 테이블 자동 생성)
- 스키마 생성: `CREATE DATABASE lukevanilla CHARACTER SET utf8mb4;` (권장)
- Settings 테이블에 운영에 필요한 키를 저장하세요:
  - Discord 관련: `DiscordToken`, `DiscordServerID`, `AuthChannel`, `AuthLogChannel`, `DiscordAuthRole`, `AdminChatChannel`, `SystemChannel`, `SupportCategoryId`, `SupportArchiveCategoryId`, `RestoreItemInfo`, `TTS_Channel`
  - 음성채널 자동화: `TRIGGER_VOICE_CHANNEL_ID_1/2`, `VOICE_CATEGORY_ID_1/2`
  - 관리자 어시스턴트(OpenAI): `OpenAI_API_Token`, (옵션) `OpenAI_API_Endpoint`, `OpenAI_API_Model`
  - 기타: `Assistant_Channel`, `AssistantSecondaryChannel`, `SupportMessageId`(자동 저장)

3) 빌드 및 배포
- 빌드: `./gradlew shadowJar`
- 산출물: 기본적으로 ShadowJar를 생성합니다.
  - 현재 `build.gradle.kts`의 `shadowJar`는 로컬 환경일 때 산출물을 `/home/lukehemmin/LukeVanilla/run/plugins`로 복사하도록 설정되어 있습니다. 공용 환경에서는 이 경로를 수정하거나 주석 처리해 `build/libs`에 출력되도록 사용하세요.
- 배포: 생성된 JAR을 Paper 서버 `plugins/`에 배치합니다. Velocity를 사용하는 경우 동일 JAR을 Velocity `plugins/`에도 배치할 수 있습니다.

## 주요 명령어
- /아이템 [등록|조회|수령] [할로윈|크리스마스|발렌타인|봄]: 시즌 아이템 시스템
- /아이템정보: 손에 든 아이템의 킬 통계 조회(바닐라/Nexo 일부 아이템 지원)
- /아이템복구: 커스텀/Nexo 아이템 복구(로그 기반)
- /관리자채팅 <활성화|비활성화>: 관리자 채팅 토글(Discord와 동기화)
- /티토커메시지 <보기|끄기>, /음성채널메시지 <보기|끄기>: 채널 메시지 표시 설정
- /wleh, /지도: 지도 링크 출력, /블록위치: 블록 좌표 확인 모드
- /서버시간: 현재 서버 시간 표시, /refreshmessages: 접속/퇴장 메시지 즉시 갱신
- /경고 [주기|차감|확인|목록]: 경고 시스템(자동 차단 임계치 포함)
- (로비 전용) /서버연결 <status|test|clear|reset>: 야생 서버 연결 점검/초기화

주요 권한 노드(발췌)
- `lukevanilla.nametag`, `lukevanilla.item`, `lukevanilla.transparentframe`, `lukevanilla.reload`, `lukevanilla.admin`, `lukevanilla.adminchat`
- `advancedwarnings.*`(경고), `itemstats.admin`

## 설정 상세
- `service.type`에 따라 일부 기능이 분기됩니다.
  - Lobby: 서버 연결 관리, 일부 Discord 음성채널 자동화 등 활성화
  - Vanilla: 안전구역, 옷장 위치 시스템, MyLand/FarmVillage 등 활성화
- HMCCosmetics 옷장 위치 시스템: `wardrobe.world` 및 좌표/반경 설정 필요
- Nexo 연동: Nexo 플러그인이 서버에 설치되어 있어야 일부 기능(복구/아이템 인식 등)이 동작
- 주의: Nexo 작업대 제한 시스템은 현재 코드 상 주석 처리되어 비활성화입니다. 필요 시 `Main.kt`에서 등록 코드 주석을 해제하고 `plugin.yml`에 명령어 추가 후 사용하세요.

## 문서/참고
- 내부 문서: `src/main/kotlin/com/lukehemmin/lukeVanilla/System/MyLand/MyLand_System_Documentation.md`
- API 참고: `Docs/API/` 폴더(HMCCosmetics, Custom-Crops, Citizens, LuckPerms, Nexo)
- 주요 파일: `src/main/resources/plugin.yml`, `src/main/resources/velocity-plugin.json`, `src/main/resources/config.yml`, `build.gradle.kts`

## 라이선스
- 이 프로젝트는 MIT 라이선스로 배포됩니다. 자세한 내용은 `LICENSE`를 참조하세요.
- 주의: 아이콘/이미지의 무단 사용은 저작권법에 의해 금지됩니다.

## 기여와 문의
- 버그/제안은 이슈로 등록: https://github.com/lukehemmin/LukeVanilla/issues
- 풀 리퀘스트 환영: 기능 단위로 작게, 설명/테스트 포함 권장

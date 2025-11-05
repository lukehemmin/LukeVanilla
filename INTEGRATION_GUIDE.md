# 마을 시스템 통합 가이드

## 🎯 개요

이 가이드는 새롭게 구현된 마을 시스템과 GUI를 기존 플러그인에 통합하는 방법을 설명합니다.

## 📋 구현된 기능 요약

### ✅ 완성된 기능들

1. **명령어 변경**: `/땅 포기` → `/땅 반환` (환불 시스템 확장 가능)
2. **마을 생성**: `/땅 마을생성 <마을이름>` (개인땅을 연결된 마을땅으로 전환)
3. **마을 초대**: `/땅 마을초대 <플레이어>` (인터랙티브 수락/거절 시스템)
4. **마을 정보**: `/땅 마을정보` (현재 마을의 상세 정보 표시)
5. **마을 반환**: `/땅 마을반환` (마을 토지 반환, 확장 가능한 환불 시스템)
6. **마을 권한**: `/땅 마을권한 <플레이어> <역할>` (구성원 역할 관리)
7. **마을 설정 GUI**: `/땅 마을설정` (이장/부이장용 관리 GUI)

### 🔧 새로운 파일들

- `VillageInviteCommand.kt` - 마을 초대 수락/거절 처리
- `VillageSettingsGUI.kt` - 마을 설정 GUI 시스템

## 🚀 통합 단계

### 1단계: plugin.yml 확인

기존 plugin.yml에 `/땅` 명령어가 이미 등록되어 있습니다:

```yaml
땅:
  description: 개인 땅 정보를 확인합니다.
  usage: /<command> 정보
  aliases: [land, myland]
```

**✅ 추가 작업 필요 없음** - 모든 하위 명령어는 LandCommand.kt에서 처리됩니다.

### 2단계: 마을초대 명령어 등록 (plugin.yml)

다음 내용을 plugin.yml의 `commands:` 섹션에 추가하세요:

```yaml
마을초대:
  description: "마을 초대 수락/거절을 처리합니다."
  usage: "/마을초대 <수락|거절>"
```

### 3단계: Main.kt 통합

현재 Main.kt의 `onEnable()` 메서드에서 이미 통합이 잘 되어 있습니다. 
하지만 VillageSettingsGUI를 추가로 통합해야 합니다.

**Main.kt 수정사항:**

```kotlin
// 1. Import 추가
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.VillageSettingsGUI
import com.lukehemmin.lukeVanilla.System.MyLand.VillageInviteCommand

class Main : JavaPlugin() {
    // ... 기존 코드 ...
    
    override fun onEnable() {
        // ... 기존 초기화 코드 ...
        
        // AdvancedLandClaiming과 LandCommand 통합 부분 (line 632-642 근처)에서 수정:
        advancedLandSystem?.let { advancedLand ->
            val advancedLandManager = advancedLand.getAdvancedLandManager()
            if (advancedLandManager != null) {
                privateLand.setAdvancedLandManager(advancedLandManager)
                
                // VillageSettingsGUI 초기화 및 등록
                val villageSettingsGUI = VillageSettingsGUI(this, advancedLandManager)
                server.pluginManager.registerEvents(villageSettingsGUI, this)
                privateLand.setVillageSettingsGUI(villageSettingsGUI)
                
                // VillageInviteCommand 등록
                val villageInviteCommand = VillageInviteCommand(privateLand.getLandCommand())
                getCommand("마을초대")?.setExecutor(villageInviteCommand)
                getCommand("마을초대")?.tabCompleter = villageInviteCommand
                server.pluginManager.registerEvents(villageInviteCommand, this)
                
                logger.info("[AdvancedLandClaiming] LandCommand와 GUI 시스템 통합 완료")
            } else {
                logger.warning("[AdvancedLandClaiming] AdvancedLandManager가 null입니다. 통합을 건너뜁니다.")
            }
        }
    }
}
```

### 4단계: LandCommand.kt 추가 메서드

LandCommand.kt에 다음 메서드를 추가하세요:

```kotlin
// VillageSettingsGUI 참조를 위한 변수와 setter 메서드는 이미 추가되어 있습니다.
// 필요시 확인: setVillageSettingsGUI() 메서드
```

## 🎮 사용법 가이드

### 기본 명령어

```bash
# 개인 토지 클레이밍 (기존)
/땅 클레임 [철|다이아|네더라이트]

# 마을 생성 (연결된 개인 토지를 마을로 전환)
/땅 마을생성 <마을이름>

# 마을 초대
/땅 마을초대 <플레이어명>

# 마을 초대 수락/거절
/마을초대 수락
/마을초대 거절

# 마을 정보 확인
/땅 마을정보

# 마을 권한 관리 (이장만 가능)
/땅 마을권한 목록
/땅 마을권한 <플레이어> 부이장
/땅 마을권한 <플레이어> 구성원

# 마을 설정 GUI (이장/부이장만 가능)
/땅 마을설정

# 마을 토지 반환
/땅 마을반환
```

### 역할 시스템

- **👑 이장 (MAYOR)**: 모든 권한 (멤버 관리, 토지 관리, 권한 변경, 마을 해체)
- **🥈 부이장 (DEPUTY_MAYOR)**: 멤버 초대/추방, 토지 반환, GUI 접근
- **👤 구성원 (MEMBER)**: 마을 토지 사용 권한

## 🛡️ 권한 시스템

기존 권한을 그대로 사용합니다:

- `myland.admin.bypass`: MyLand 시스템 보호 우회 권한
- `myland.admin.unclaim`: MyLand 시스템 관리자 회수 권한

## 🗄️ 데이터베이스

기존 데이터베이스 테이블들이 이미 모든 기능을 지원합니다:

- `villages` - 마을 정보
- `village_members` - 마을 구성원 및 역할
- `myland_claims` - 토지 클레이밍 정보 (마을 ID 포함)
- `myland_members` - 토지 접근 권한

## 🔄 향후 확장 계획

### 환불 시스템 (TODO 주석으로 표시됨)

```kotlin
// 예시: 마을 반환 시 환불 계산
val refundItems = calculateVillageRefund(connectedChunks, villageInfo)
if (refundItems.isNotEmpty()) {
    giveRefundItems(player, refundItems)
}
```

### GUI 확장 기능

- 마을 해체 기능 (현재 placeholder)
- 멤버 개별 관리 기능 (역할 변경, 추방)
- 마을 권한 세부 설정 시스템

## ⚠️ 주의사항

1. **서버 타입 제한**: 마을 시스템은 `serviceType == "Vanilla"`인 서버에서만 활성화됩니다.

2. **의존성 확인**: AdvancedLandClaiming 시스템이 정상적으로 초기화되어야 마을 기능이 작동합니다.

3. **권한 검증**: 모든 마을 관리 기능은 적절한 역할 확인을 거칩니다.

4. **연결된 청크**: 마을은 연결된(인접한) 청크 그룹으로만 생성 가능합니다.

## 🐛 문제 해결

### 일반적인 문제들

1. **"고급 토지 시스템이 초기화되지 않았습니다"**
   - AdvancedLandSystem이 정상적으로 로드되었는지 확인
   - 서버 타입이 "Vanilla"인지 확인

2. **"마을 설정 GUI 시스템이 초기화되지 않았습니다"**
   - VillageSettingsGUI가 올바르게 등록되었는지 확인
   - Main.kt의 통합 코드 확인

3. **마을 초대가 작동하지 않음**
   - VillageInviteCommand가 올바르게 등록되었는지 확인
   - plugin.yml에 "마을초대" 명령어가 등록되었는지 확인

## 📝 로그 확인

통합이 성공적으로 완료되면 다음과 같은 로그가 출력됩니다:

```
[INFO] [AdvancedLandClaiming] LandCommand와 GUI 시스템 통합 완료
```

실패 시에는 구체적인 오류 메시지와 스택 트레이스가 출력됩니다.

---

## 🎉 완료!

이 가이드를 따라 통합을 완료하면, 플레이어들은 개인 토지에서 마을을 생성하고, 다른 플레이어들을 초대하여 협력적인 마을 공동체를 구축할 수 있습니다.

모든 기능은 기존 시스템과 완벽하게 호환되며, 향후 확장을 위한 구조도 갖추어져 있습니다.
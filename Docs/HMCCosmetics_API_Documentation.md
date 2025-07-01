# HMCCosmetics API 문서 (v2.7.9)

> **분석 기반:** HMCCosmetics-2.7.9-ff1addfd.jar  
> **분석 일자:** 2024년 6월 30일  
> **문서 작성자:** AI Assistant (코드 분석 기반)

---

## 📚 목차

1. [개요](#개요)
2. [핵심 API 클래스](#핵심-api-클래스)
3. [사용자 관리](#사용자-관리)
4. [옷장 시스템](#옷장-시스템)
5. [메뉴 시스템](#메뉴-시스템)
6. [코스메틱 관리](#코스메틱-관리)
7. [이벤트 시스템](#이벤트-시스템)
8. [설정 파일](#설정-파일)
9. [실전 예제](#실전-예제)
10. [문제 해결](#문제-해결)

---

## 🎯 개요

HMCCosmetics는 Minecraft 서버용 코스메틱 플러그인으로, 다음과 같은 기능을 제공합니다:

- **코스메틱 아이템**: 모자, 갑옷, 배낭, 풍선 등
- **옷장 시스템**: 3D 환경에서 코스메틱을 착용/해제
- **메뉴 GUI**: 코스메틱 선택 및 관리
- **권한 시스템**: 세밀한 권한 제어
- **이벤트 시스템**: 다른 플러그인과의 연동

---

## 🔧 핵심 API 클래스

### HMCCosmeticsAPI

**패키지:** `com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI`

메인 API 클래스로, 모든 주요 기능에 접근할 수 있습니다.

#### 주요 메서드

```java
// 사용자 관리
public static CosmeticUser getUser(UUID uuid)

// 코스메틱 관리
public static Cosmetic getCosmetic(String id)
public static List<Cosmetic> getAllCosmetics()
public static void equipCosmetic(CosmeticUser user, Cosmetic cosmetic)
public static void equipCosmetic(CosmeticUser user, Cosmetic cosmetic, Color color)
public static void unequipCosmetic(CosmeticUser user, CosmeticSlot slot)

// 메뉴 관리
public static Menu getMenu(String id)

// 슬롯 관리
public static Map<String, CosmeticSlot> getAllCosmeticSlots()
public static CosmeticSlot registerCosmeticSlot(String name)

// 사용자 목록
public static List<CosmeticUser> getAllCosmeticUsers()

// 프로바이더 관리
public static void registerCosmeticUserProvider(CosmeticUserProvider provider)
public static CosmeticUserProvider getCosmeticUserProvider()
public static void registerCosmeticProvider(CosmeticProvider provider)
public static CosmeticProvider getCosmeticProvider()

// 시스템 정보
public static String getNMSVersion()
```

#### 사용 예제

```java
// 플레이어에게 코스메틱 장착
UUID playerUUID = player.getUniqueId();
CosmeticUser user = HMCCosmeticsAPI.getUser(playerUUID);
Cosmetic hat = HMCCosmeticsAPI.getCosmetic("rainbow_hat");
HMCCosmeticsAPI.equipCosmetic(user, hat);

// 메뉴 열기
Menu menu = HMCCosmeticsAPI.getMenu("default");
if (menu != null) {
    menu.openMenu(user);
}
```

---

## 👥 사용자 관리

### CosmeticUser

**패키지:** `com.hibiscusmc.hmccosmetics.user.CosmeticUser`

플레이어의 코스메틱 상태를 관리하는 핵심 클래스입니다.

#### 주요 메서드

```java
// 기본 정보
public Player getPlayer()
public UUID getUniqueId()
public Entity getEntity()

// 코스메틱 관리
public void addCosmetic(Cosmetic cosmetic, Color color)
public void removeCosmetic(Cosmetic cosmetic)
public void removeCosmeticSlot(CosmeticSlot slot)
public boolean hasCosmetic(Cosmetic cosmetic)
public boolean canUseCosmetic(Cosmetic cosmetic, boolean ignoreWardrobe)
public Map<CosmeticSlot, Cosmetic> getCosmetics()

// 옷장 관리
public void enterWardrobe(Wardrobe wardrobe, boolean bypassDistance)
public void enterWardrobe(boolean bypassDistance, Wardrobe wardrobe) // Deprecated
public void leaveWardrobe()
public void leaveWardrobe(boolean instant)
public boolean isInWardrobe()
public UserWardrobeManager getWardrobeManager()

// 배낭 관리
public void spawnBackpack(CosmeticBackpackType backpack)
public void despawnBackpack()
public boolean isBackpackSpawned()
public UserBackpackManager getBackpackManager()

// 풍선 관리
public boolean isBalloonSpawned()
public UserBalloonManager getBalloonManager()

// 가시성 관리
public void hidePlayer()
public void showPlayer()
public boolean isHidden()
public void addHiddenReason(HiddenReason reason)
public void removeHiddenReason(HiddenReason reason)

// 색상 관리
public void setColor(CosmeticSlot slot, Color color)
public Color getColor(CosmeticSlot slot)
public Map<CosmeticSlot, Color> getColors()

// 데이터베이스
public void saveToDatabase()
```

#### HiddenReason 열거형

```java
public enum HiddenReason {
    DISABLED_WORLD,
    DISABLED_GAMEMODE,
    WORLDGUARD_REGION,
    CUSTOM
}
```

---

## 🏠 옷장 시스템

### Wardrobe

**패키지:** `com.hibiscusmc.hmccosmetics.config.Wardrobe`

옷장 설정을 나타내는 클래스입니다.

#### 주요 메서드

```java
public String getId()
public String getPermission()
public boolean hasPermission()
public WardrobeLocation getLocation()
public String getDefaultMenu()
public boolean canEnter(CosmeticUser user)
```

### WardrobeSettings

**패키지:** `com.hibiscusmc.hmccosmetics.config.WardrobeSettings`

옷장 설정을 관리하는 유틸리티 클래스입니다.

#### 주요 메서드

```java
// 옷장 관리
public static Wardrobe getWardrobe(String name)
public static Set<String> getWardrobeNames()
public static Collection<Wardrobe> getWardrobes()
public static void addWardrobe(Wardrobe wardrobe)
public static void removeWardrobe(String name)

// 설정 메서드들
public static void setNPCLocation(Wardrobe wardrobe, Location location)
public static void setViewerLocation(Wardrobe wardrobe, Location location)
public static void setLeaveLocation(Wardrobe wardrobe, Location location)
public static void setWardrobePermission(Wardrobe wardrobe, String permission)
public static void setWardrobeDistance(Wardrobe wardrobe, int distance)
public static void setWardrobeDefaultMenu(Wardrobe wardrobe, String menu)

// 전역 설정
public static boolean isEnabledTransition()
public static String getTransitionText()
public static int getTransitionFadeIn()
public static int getTransitionStay()
public static int getTransitionFadeOut()
public static int getTransitionDelay()
public static boolean isTryCosmeticsInWardrobe()
```

### UserWardrobeManager

**패키지:** `com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager`

개별 사용자의 옷장 상태를 관리합니다.

#### 주요 메서드

```java
public void start()
public void end()
public boolean isActive()
public WardrobeStatus getWardrobeStatus()
public void setWardrobeStatus(WardrobeStatus status)
public Menu getLastOpenMenu()
public void setLastOpenMenu(Menu menu)
```

#### WardrobeStatus 열거형

```java
public enum WardrobeStatus {
    SETUP,
    STARTING,
    RUNNING,
    STOPPING
}
```

### 옷장 사용 예제

```java
// 기본 옷장에 입장
CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
Wardrobe defaultWardrobe = WardrobeSettings.getWardrobe("default");

if (defaultWardrobe != null) {
    user.enterWardrobe(defaultWardrobe, true); // true = 거리 체크 우회
}

// 옷장 상태 확인
if (user.isInWardrobe()) {
    UserWardrobeManager manager = user.getWardrobeManager();
    if (manager.isActive()) {
        // 옷장이 활성 상태
    }
}

// 옷장에서 나가기
user.leaveWardrobe();
```

---

## 📋 메뉴 시스템

### Menu

**패키지:** `com.hibiscusmc.hmccosmetics.gui.Menu`

GUI 메뉴를 나타내는 클래스입니다.

#### 주요 메서드

```java
public String getId()
public void openMenu(CosmeticUser user)
public boolean isValidMenu()
```

### Menus

**패키지:** `com.hibiscusmc.hmccosmetics.gui.Menus`

메뉴 관리 유틸리티 클래스입니다.

#### 주요 메서드

```java
// 메뉴 관리
public static void addMenu(Menu menu)
public static Menu getMenu(String id)
public static Collection<Menu> getMenu()
public static boolean hasMenu(String id)
public static boolean hasMenu(Menu menu)

// 기본 메뉴
public static boolean hasDefaultMenu()
public static Menu getDefaultMenu()

// 유틸리티
public static List<String> getMenuNames()
public static Collection<Menu> values()

// 쿨다운 관리
public static void addCooldown(UUID uuid, long time)
public static Long getCooldown(UUID uuid)
public static void removeCooldown(UUID uuid)

// 시스템
public static void setup()
```

### 메뉴 사용 예제

```java
// 기본 메뉴 열기
CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
Menu defaultMenu = Menus.getDefaultMenu();
if (defaultMenu != null) {
    defaultMenu.openMenu(user);
}

// 특정 메뉴 열기
Menu hatMenu = Menus.getMenu("hat_menu");
if (hatMenu != null) {
    hatMenu.openMenu(user);
}

// 쿨다운 확인
UUID playerUUID = player.getUniqueId();
Long cooldown = Menus.getCooldown(playerUUID);
if (cooldown == 0 || System.currentTimeMillis() >= cooldown) {
    // 쿨다운 완료, 메뉴 열기 가능
    menu.openMenu(user);
    Menus.addCooldown(playerUUID, System.currentTimeMillis() + 5000); // 5초 쿨다운
}
```

---

## 🎨 코스메틱 관리

### Cosmetic

**패키지:** `com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic`

개별 코스메틱 아이템을 나타내는 클래스입니다.

#### 주요 메서드

```java
public String getId()
public String getPermission()
public CosmeticSlot getSlot()
public boolean isDyeable()
public boolean isEnabled()
```

### CosmeticSlot

**패키지:** `com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot`

코스메틱 슬롯을 나타내는 클래스입니다.

#### 기본 슬롯들

```java
// 갑옷 슬롯
public static final CosmeticSlot HELMET
public static final CosmeticSlot CHESTPLATE
public static final CosmeticSlot LEGGINGS
public static final CosmeticSlot BOOTS

// 기타 슬롯
public static final CosmeticSlot OFFHAND
public static final CosmeticSlot BACKPACK
public static final CosmeticSlot BALLOON
```

#### 주요 메서드

```java
public String getId()
public static CosmeticSlot register(String name)
public static Map<String, CosmeticSlot> values()
public static CosmeticSlot getSlot(String name)
```

### Cosmetics

**패키지:** `com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics`

코스메틱 관리 유틸리티 클래스입니다.

#### 주요 메서드

```java
public static Cosmetic getCosmetic(String id)
public static Set<Cosmetic> values()
public static void registerProvider(CosmeticProvider provider)
public static CosmeticProvider getProvider()
```

---

## 📡 이벤트 시스템

HMCCosmetics는 다양한 이벤트를 제공하여 다른 플러그인과의 연동을 지원합니다.

### 코스메틱 이벤트

```java
// 코스메틱 장착
public class PlayerCosmeticEquipEvent extends PlayerCosmeticEvent implements Cancellable

// 코스메틱 장착 완료
public class PlayerCosmeticPostEquipEvent extends PlayerCosmeticEvent

// 코스메틱 해제
public class PlayerCosmeticRemoveEvent extends PlayerCosmeticEvent implements Cancellable

// 코스메틱 숨김
public class PlayerCosmeticHideEvent extends PlayerEvent implements Cancellable

// 코스메틱 표시
public class PlayerCosmeticShowEvent extends PlayerEvent implements Cancellable
```

### 옷장 이벤트

```java
// 옷장 입장
public class PlayerWardrobeEnterEvent extends PlayerEvent implements Cancellable

// 옷장 퇴장
public class PlayerWardrobeLeaveEvent extends PlayerEvent implements Cancellable
```

### 메뉴 이벤트

```java
// 메뉴 열기
public class PlayerMenuOpenEvent extends PlayerMenuEvent implements Cancellable

// 메뉴 닫기
public class PlayerMenuCloseEvent extends PlayerMenuEvent implements Cancellable
```

### 사용자 이벤트

```java
// 사용자 로드 (전)
public class PlayerPreLoadEvent extends PlayerEvent implements Cancellable

// 사용자 로드 (후)
public class PlayerLoadEvent extends PlayerEvent

// 사용자 언로드 (전)
public class PlayerPreUnloadEvent extends PlayerEvent implements Cancellable

// 사용자 언로드 (후)
public class PlayerUnloadEvent extends PlayerEvent
```

### 시스템 이벤트

```java
// HMCCosmetics 설정 완료
public class HMCCosmeticSetupEvent extends Event

// 코스메틱 타입 등록
public class CosmeticTypeRegisterEvent extends Event implements Cancellable
```

### 이벤트 사용 예제

```java
@EventHandler
public void onPlayerWardrobeEnter(PlayerWardrobeEnterEvent event) {
    CosmeticUser user = event.getCosmeticUser();
    Wardrobe wardrobe = event.getWardrobe();
    Player player = user.getPlayer();
    
    player.sendMessage("§a옷장 " + wardrobe.getId() + "에 입장했습니다!");
    
    // 이벤트 취소 (조건부)
    if (!player.hasPermission("myserver.wardrobe.use")) {
        event.setCancelled(true);
        player.sendMessage("§c옷장 사용 권한이 없습니다!");
    }
}

@EventHandler
public void onCosmeticEquip(PlayerCosmeticEquipEvent event) {
    CosmeticUser user = event.getCosmeticUser();
    Cosmetic cosmetic = event.getCosmetic();
    Player player = user.getPlayer();
    
    player.sendMessage("§e코스메틱 " + cosmetic.getId() + "을(를) 장착했습니다!");
    
    // 특정 코스메틱 장착 금지
    if (cosmetic.getId().equals("admin_hat") && !player.isOp()) {
        event.setCancelled(true);
        player.sendMessage("§c관리자 전용 코스메틱입니다!");
    }
}
```

---

## ⚙️ 설정 파일

### config.yml 주요 설정

```yaml
# 기본 메뉴
default-menu: defaultmenu_hats

# 데이터베이스 설정
database-settings:
  type: sqlite # SQLite (기본), MYSQL, NONE
  mysql:
    database: database
    password: password
    port: 3306
    host: localhost
    user: username

# 코스메틱 설정
cosmetic-settings:
  tick-period: 20 # 틱 주기
  unapply-on-death: false # 죽음 시 해제 여부
  force-permission-join: true # 접속 시 권한 강제 확인
  
  # 비활성화된 월드들
  disabled-worlds:
    - "disabledworld"
  
  # 비활성화된 게임모드
  disabled-gamemode:
    enabled: true
    gamemodes:
      - "SPECTATOR"

# 옷장 설정
wardrobe:
  rotation-speed: 3 # 회전 속도
  equip-pumpkin: false # 호박 헬멧 착용
  return-last-location: false # 마지막 위치로 복귀
  unchecked-wardrobe-cosmetics: false # 권한 무시 여부
  prevent-damage: true # 데미지 방지
  damage-kicked: false # 데미지 시 퇴장
  
  # 메뉴 옵션
  menu-options:
    enter-open-menu: false # 입장 시 메뉴 자동 열기
  
  # 게임모드 옵션
  gamemode-options:
    exit-gamemode-enabled: false
    exit-gamemode: "SURVIVAL"
  
  # 보스바
  bossbar:
    enabled: false
    text: "Left-Click to open the menu!"
    progress: 1.0
    overlay: PROGRESS
    color: BLUE
  
  # 전환 효과
  transition:
    enabled: true
    text: "<black>"
    delay: 40
    title-fade-in: 1000
    title-stay: 500
    title-fade-out: 1000
  
  # 옷장 목록
  wardrobes:
    default:
      distance: -1 # 상호작용 거리 (-1 = 무제한)
      permission: "hmccosmetics.wardrobe.default"
      default-menu: defaultmenu
      npc-location:
        world: "world"
        x: 0
        y: 0
        z: 0
        yaw: 0
        pitch: 0
      viewer-location:
        world: "world"
        x: 5
        y: 0
        z: 5
        yaw: 0
        pitch: 0
      leave-location:
        world: "world"
        x: 5
        y: 5
        z: 5
        yaw: 0
        pitch: 0
```

---

## 🚀 실전 예제

### 1. 플레이어 접속 시 기본 코스메틱 장착

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    
    // 잠시 후 코스메틱 장착 (플레이어 로드 대기)
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        if (user != null && player.hasPermission("server.vip")) {
            Cosmetic vipHat = HMCCosmeticsAPI.getCosmetic("vip_crown");
            if (vipHat != null) {
                HMCCosmeticsAPI.equipCosmetic(user, vipHat);
                player.sendMessage("§6VIP 크라운이 장착되었습니다!");
            }
        }
    }, 20L); // 1초 후
}
```

### 2. 특정 위치에서 자동 옷장 열기

```java
@EventHandler
public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Location to = event.getTo();
    
    if (to == null) return;
    
    // 특정 좌표 근처인지 확인
    Location wardrobeLocation = new Location(to.getWorld(), 100, 64, 100);
    if (to.distance(wardrobeLocation) <= 3.0) {
        
        // 이미 옷장에 있는 플레이어는 무시
        if (playersInWardrobe.contains(player.getUniqueId())) return;
        
        CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        if (user != null && !user.isInWardrobe()) {
            Wardrobe wardrobe = WardrobeSettings.getWardrobe("spawn_wardrobe");
            if (wardrobe != null) {
                user.enterWardrobe(wardrobe, true);
                player.sendMessage("§a✨ 옷장에 입장했습니다! ✨");
                playersInWardrobe.add(player.getUniqueId());
            }
        }
    } else {
        // 범위를 벗어나면 추적에서 제거
        playersInWardrobe.remove(player.getUniqueId());
    }
}
```

### 3. 명령어로 다른 플레이어에게 코스메틱 지급

```java
@Command("givecosmetic")
@Permission("admin.cosmetic.give")
public void giveCosmeticCommand(CommandSender sender, @Arg Player target, @Arg String cosmeticId) {
    CosmeticUser user = HMCCosmeticsAPI.getUser(target.getUniqueId());
    if (user == null) {
        sender.sendMessage("§c플레이어를 찾을 수 없습니다.");
        return;
    }
    
    Cosmetic cosmetic = HMCCosmeticsAPI.getCosmetic(cosmeticId);
    if (cosmetic == null) {
        sender.sendMessage("§c코스메틱 '" + cosmeticId + "'를 찾을 수 없습니다.");
        return;
    }
    
    HMCCosmeticsAPI.equipCosmetic(user, cosmetic);
    sender.sendMessage("§a" + target.getName() + "에게 " + cosmeticId + " 코스메틱을 지급했습니다.");
    target.sendMessage("§a관리자로부터 " + cosmeticId + " 코스메틱을 받았습니다!");
}
```

### 4. 코스메틱 미리보기 시스템

```java
public class CosmeticPreview {
    private final Map<UUID, Cosmetic> previewing = new HashMap<>();
    
    public void startPreview(Player player, String cosmeticId) {
        CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        Cosmetic cosmetic = HMCCosmeticsAPI.getCosmetic(cosmeticId);
        
        if (user == null || cosmetic == null) return;
        
        // 기존 미리보기 종료
        stopPreview(player);
        
        // 새 코스메틱 장착
        HMCCosmeticsAPI.equipCosmetic(user, cosmetic);
        previewing.put(player.getUniqueId(), cosmetic);
        
        player.sendMessage("§e" + cosmeticId + " 미리보기를 시작합니다. (30초 후 자동 해제)");
        
        // 30초 후 자동 해제
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stopPreview(player);
        }, 600L);
    }
    
    public void stopPreview(Player player) {
        UUID uuid = player.getUniqueId();
        Cosmetic previewing = this.previewing.remove(uuid);
        
        if (previewing != null) {
            CosmeticUser user = HMCCosmeticsAPI.getUser(uuid);
            if (user != null) {
                HMCCosmeticsAPI.unequipCosmetic(user, previewing.getSlot());
                player.sendMessage("§c미리보기가 종료되었습니다.");
            }
        }
    }
}
```

### 5. 권한 기반 코스메틱 자동 관리

```java
@EventHandler
public void onPermissionChange(PlayerPermissionChangeEvent event) {
    Player player = event.getPlayer();
    CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
    
    if (user == null) return;
    
    // 모든 장착된 코스메틱 확인
    Map<CosmeticSlot, Cosmetic> equipped = user.getCosmetics();
    
    for (Map.Entry<CosmeticSlot, Cosmetic> entry : equipped.entrySet()) {
        Cosmetic cosmetic = entry.getValue();
        
        // 권한이 없는 코스메틱 해제
        if (!user.canUseCosmetic(cosmetic, false)) {
            HMCCosmeticsAPI.unequipCosmetic(user, entry.getKey());
            player.sendMessage("§c권한이 없어 " + cosmetic.getId() + " 코스메틱이 해제되었습니다.");
        }
    }
}
```

---

## 🔧 문제 해결

### 일반적인 문제들

#### 1. CosmeticUser가 null인 경우

```java
// 잘못된 방법
CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
user.addCosmetic(cosmetic, null); // NullPointerException 가능

// 올바른 방법
CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
if (user != null) {
    user.addCosmetic(cosmetic, null);
} else {
    // 플레이어가 아직 로드되지 않음
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        CosmeticUser delayedUser = HMCCosmeticsAPI.getUser(player.getUniqueId());
        if (delayedUser != null) {
            delayedUser.addCosmetic(cosmetic, null);
        }
    }, 20L);
}
```

#### 2. UserWardrobeManager가 null인 경우

```java
// 문제: enterWardrobe를 호출하지 않으면 null
CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
UserWardrobeManager manager = user.getWardrobeManager(); // null일 수 있음

// 해결: enterWardrobe 호출 후 사용
Wardrobe wardrobe = WardrobeSettings.getWardrobe("default");
if (wardrobe != null) {
    user.enterWardrobe(wardrobe, true);
    
    // 잠시 후 매니저 사용
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        UserWardrobeManager manager = user.getWardrobeManager();
        if (manager != null && manager.isActive()) {
            // 옷장이 활성 상태
        }
    }, 5L);
}
```

#### 3. 권한 문제

```java
// 권한 확인 후 코스메틱 장착
public void equipCosmeticSafely(Player player, String cosmeticId) {
    CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
    Cosmetic cosmetic = HMCCosmeticsAPI.getCosmetic(cosmeticId);
    
    if (user == null || cosmetic == null) {
        player.sendMessage("§c코스메틱을 장착할 수 없습니다.");
        return;
    }
    
    // 권한 확인
    if (!user.canUseCosmetic(cosmetic, false)) {
        player.sendMessage("§c이 코스메틱을 사용할 권한이 없습니다: " + cosmetic.getPermission());
        return;
    }
    
    HMCCosmeticsAPI.equipCosmetic(user, cosmetic);
    player.sendMessage("§a코스메틱이 장착되었습니다!");
}
```

### 성능 최적화

#### 1. 이벤트 리스너 최적화

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onPlayerMove(PlayerMoveEvent event) {
    // 불필요한 계산 방지
    if (event.getFrom().distanceSquared(event.getTo()) < 0.01) return;
    
    // 비동기로 처리 (좌표 계산만)
    Location to = event.getTo();
    Player player = event.getPlayer();
    
    // 무거운 작업은 다음 틱에
    Bukkit.getScheduler().runTask(plugin, () -> {
        checkWardrobeArea(player, to);
    });
}
```

#### 2. 캐싱 활용

```java
public class CosmeticCache {
    private final Map<String, Cosmetic> cosmeticCache = new HashMap<>();
    private final Map<String, Wardrobe> wardrobeCache = new HashMap<>();
    
    public Cosmetic getCachedCosmetic(String id) {
        return cosmeticCache.computeIfAbsent(id, HMCCosmeticsAPI::getCosmetic);
    }
    
    public Wardrobe getCachedWardrobe(String id) {
        return wardrobeCache.computeIfAbsent(id, WardrobeSettings::getWardrobe);
    }
    
    public void clearCache() {
        cosmeticCache.clear();
        wardrobeCache.clear();
    }
}
```

---

## 📝 버전 정보

- **HMCCosmetics 버전:** 2.7.9-ff1addfd
- **지원 Minecraft 버전:** 1.17.1 ~ 1.21.6
- **API 안정성:** Stable
- **문서 버전:** 1.0

---

## 🤝 기여 및 지원

이 문서는 HMCCosmetics jar 파일의 코드 분석을 통해 작성되었습니다. 

### 참고 링크

- **HMCCosmetics 공식:** [hibiscusmc.com](https://hibiscusmc.com)
- **SpigotMC:** [HMCCosmetics 페이지](https://www.spigotmc.org/resources/hmccosmetics.97479/)

### 라이센스

이 문서는 교육 및 개발 목적으로 작성되었습니다. HMCCosmetics 플러그인의 저작권은 HibiscusMC에 있습니다.

---

> **마지막 업데이트:** 2024년 6월 30일  
> **문서 작성:** AI Assistant (jar 파일 분석 기반) 
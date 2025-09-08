# Citizens-main API 문서

## 개요

Citizens는 Minecraft 서버에서 NPC(Non-Player Character)를 생성하고 관리할 수 있는 플러그인입니다. 이 문서는 Citizens-main 2.0.39 버전의 API 사용법을 다룹니다.

## 목차

1. [시작하기](#시작하기)
2. [메인 클래스](#메인-클래스)
3. [NPC 관리](#npc-관리)
4. [트레이트 시스템](#트레이트-시스템)
5. [네비게이션 시스템](#네비게이션-시스템)
6. [명령어 시스템](#명령어-시스템)
7. [에디터 시스템](#에디터-시스템)
8. [이벤트 시스템](#이벤트-시스템)
9. [유틸리티 클래스](#유틸리티-클래스)
10. [설정 시스템](#설정-시스템)

---

## 시작하기

### 의존성 추가

```xml
<dependency>
    <groupId>net.citizensnpcs</groupId>
    <artifactId>citizens-main</artifactId>
    <version>2.0.39</version>
    <scope>provided</scope>
</dependency>
```

### 기본 사용법

```java
// Citizens 플러그인 인스턴스 가져오기
Citizens citizensPlugin = (Citizens) Bukkit.getPluginManager().getPlugin("Citizens");

// NPC 레지스트리 가져오기
NPCRegistry npcRegistry = citizensPlugin.getNPCRegistry();

// NPC 생성
NPC npc = npcRegistry.createNPC(EntityType.PLAYER, "TestNPC");
```

---

## 메인 클래스

### Citizens.java

Citizens 플러그인의 메인 클래스입니다.

#### 주요 메서드

```java
public class Citizens extends JavaPlugin implements CitizensPlugin {
    
    // NPC 레지스트리 관련
    public NPCRegistry getNPCRegistry()
    public NPCRegistry createAnonymousNPCRegistry(NPCDataStore store)
    public NPCRegistry createNamedNPCRegistry(String name, NPCDataStore store)
    public NPCRegistry getNamedNPCRegistry(String name)
    public void removeNamedNPCRegistry(String name)
    
    // 트레이트 관련
    public TraitFactory getTraitFactory()
    
    // 기타 유틸리티
    public CommandManager getCommandManager()
    public NPCSelector getDefaultNPCSelector()
    public LocationLookup getLocationLookup()
    public NMSHelper getNMSHelper()
    public TemplateRegistry getTemplateRegistry()
    
    // 데이터 저장/로드
    public void storeNPCs()
    public void reload() throws NPCLoadException
}
```

#### 사용 예시

```java
Citizens citizens = (Citizens) Bukkit.getPluginManager().getPlugin("Citizens");

// 기본 NPC 레지스트리 사용
NPCRegistry registry = citizens.getNPCRegistry();

// 커스텀 레지스트리 생성
NPCRegistry customRegistry = citizens.createNamedNPCRegistry("MyCustomRegistry", 
    new YamlStorage("custom_npcs.yml"));

// 트레이트 팩토리 사용
TraitFactory traitFactory = citizens.getTraitFactory();
```

---

## NPC 관리

### CitizensNPC.java

NPC의 메인 구현체입니다.

#### 주요 메서드

```java
public class CitizensNPC extends AbstractNPC {
    
    // 스폰/디스폰 관련
    public boolean spawn(Location at, SpawnReason reason)
    public boolean spawn(Location at, SpawnReason reason, Consumer<Entity> callback)
    public boolean despawn(DespawnReason reason)
    public boolean isSpawned()
    
    // 네비게이션 관련
    public Navigator getNavigator()
    public void setMoveDestination(Location destination)
    public void faceLocation(Location location)
    
    // 엔티티 관련
    public Entity getEntity()
    public EntityController getEntityController()
    public void setBukkitEntityType(EntityType type)
    
    // 위치 관련
    public Location getStoredLocation()
    public void teleport(Location location, TeleportCause reason)
    
    // 상태 관련
    public void setSneaking(boolean sneaking)
    public void setFlyable(boolean flyable)
    public boolean isFlyable()
    
    // 블록 브레이커
    public BlockBreaker getBlockBreaker(Block targetBlock, BlockBreakerConfiguration config)
    
    // 업데이트 관련
    public void scheduleUpdate(NPCUpdate update)
    public boolean isUpdating(NPCUpdate update)
    public void update()
}
```

#### 사용 예시

```java
// NPC 생성 및 스폰
NPC npc = registry.createNPC(EntityType.PLAYER, "MyNPC");
Location spawnLoc = new Location(world, 0, 64, 0);
npc.spawn(spawnLoc);

// NPC 이동
npc.getNavigator().setTarget(targetLocation);

// NPC 방향 설정
npc.faceLocation(player.getLocation());

// NPC 디스폰
npc.despawn(DespawnReason.PLUGIN);
```

### CitizensNPCRegistry.java

NPC들을 관리하는 레지스트리입니다.

#### 주요 메서드

```java
public class CitizensNPCRegistry implements NPCRegistry {
    
    // NPC 생성
    public NPC createNPC(EntityType type, String name)
    public NPC createNPC(EntityType type, String name, Location loc)
    public NPC createNPC(EntityType type, UUID uuid, int id, String name)
    public NPC createNPCUsingItem(EntityType type, String name, ItemStack item)
    
    // NPC 검색
    public NPC getById(int id)
    public NPC getByUniqueId(UUID uuid)
    public NPC getByUniqueIdGlobal(UUID uuid)
    public NPC getNPC(Entity entity)
    public boolean isNPC(Entity entity)
    
    // NPC 관리
    public void deregister(NPC npc)
    public void deregisterAll()
    public void despawnNPCs(DespawnReason reason)
    
    // 데이터 저장
    public void saveToStore()
    
    // 반복자
    public Iterator<NPC> iterator()
    public Iterable<NPC> sorted()
}
```

#### 사용 예시

```java
NPCRegistry registry = citizens.getNPCRegistry();

// 다양한 방법으로 NPC 생성
NPC playerNPC = registry.createNPC(EntityType.PLAYER, "PlayerNPC");
NPC villagerNPC = registry.createNPC(EntityType.VILLAGER, "VillagerNPC", spawnLocation);

// 특정 아이템을 들고 있는 NPC 생성
ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
NPC itemNPC = registry.createNPCUsingItem(EntityType.PLAYER, "WarriorNPC", sword);

// NPC 검색
NPC foundNPC = registry.getById(1);
NPC entityNPC = registry.getNPC(someEntity);

// 모든 NPC 순회
for (NPC npc : registry) {
    // NPC 처리
}
```

---

## 트레이트 시스템

### CitizensTraitFactory.java

트레이트를 생성하고 관리하는 팩토리입니다.

#### 주요 메서드

```java
public class CitizensTraitFactory implements TraitFactory {
    
    // 트레이트 등록
    public void registerTrait(TraitInfo info)
    public void deregisterTrait(TraitInfo info)
    
    // 트레이트 생성
    public <T extends Trait> T getTrait(Class<T> clazz)
    public <T extends Trait> T getTrait(String name)
    public Class<? extends Trait> getTraitClass(String name)
    
    // 기본 트레이트 추가
    public void addDefaultTraits(NPC npc)
    
    // 등록된 트레이트 조회
    public Collection<TraitInfo> getRegisteredTraits()
}
```

### 주요 트레이트들

#### Age.java - 나이 트레이트

```java
@TraitName("age")
public class Age extends Trait {
    public int getAge()
    public void setAge(int age)
    public boolean isLocked()
    public void setLocked(boolean locked)
    public boolean toggle()
}
```

#### LookClose.java - 시선 추적 트레이트

```java
@TraitName("lookclose")
public class LookClose extends Trait {
    public void setRange(double range)
    public double getRange()
    public void setRealisticLooking(boolean realistic)
    public boolean isRealisticLooking()
    public void setRandomLook(boolean random)
    public boolean isRandomLook()
}
```

#### FollowTrait.java - 따라가기 트레이트

```java
@TraitName("follow")
public class FollowTrait extends Trait {
    public void follow(Entity entity)
    public void stop()
    public boolean isActive()
    public Entity getFollowingEntity()
}
```

#### 사용 예시

```java
// 트레이트 추가
npc.addTrait(Age.class);
npc.addTrait(LookClose.class);

// 트레이트 설정
Age ageTrait = npc.getOrAddTrait(Age.class);
ageTrait.setAge(25);
ageTrait.setLocked(true);

LookClose lookTrait = npc.getOrAddTrait(LookClose.class);
lookTrait.setRange(10.0);
lookTrait.setRealisticLooking(true);

// 트레이트 제거
npc.removeTrait(Age.class);
```

---

## 네비게이션 시스템

### CitizensNavigator.java

NPC의 이동을 관리하는 네비게이터입니다.

#### 주요 메서드

```java
public class CitizensNavigator implements Navigator {
    
    // 목표 설정
    public void setTarget(Location target)
    public void setTarget(Entity target, boolean aggressive)
    public void setTarget(Iterable<Vector> path)
    public void setTarget(Function<NavigatorParameters, PathStrategy> strategy)
    
    // 직선 이동
    public void setStraightLineTarget(Location target)
    public void setStraightLineTarget(Entity target, boolean aggressive)
    
    // 네비게이션 제어
    public void cancelNavigation()
    public void cancelNavigation(CancelReason reason)
    public boolean isNavigating()
    public void setPaused(boolean paused)
    public boolean isPaused()
    
    // 목표 정보
    public Location getTargetAsLocation()
    public EntityTarget getEntityTarget()
    public TargetType getTargetType()
    
    // 파라미터 관리
    public NavigatorParameters getDefaultParameters()
    public NavigatorParameters getLocalParameters()
    
    // 경로 전략
    public PathStrategy getPathStrategy()
    
    // 네비게이션 가능 여부 확인
    public boolean canNavigateTo(Location dest)
    public boolean canNavigateTo(Location dest, NavigatorParameters params)
}
```

#### 사용 예시

```java
Navigator navigator = npc.getNavigator();

// 특정 위치로 이동
navigator.setTarget(new Location(world, 10, 64, 10));

// 엔티티 따라가기
navigator.setTarget(player, false); // 공격하지 않음

// 직선 이동
navigator.setStraightLineTarget(targetLocation);

// 네비게이션 일시 정지
navigator.setPaused(true);

// 네비게이션 취소
navigator.cancelNavigation();

// 이동 상태 확인
if (navigator.isNavigating()) {
    // 이동 중
}
```

---

## 명령어 시스템

### NPCCommands.java

NPC 관련 명령어들을 처리합니다.

#### 주요 명령어 메서드들

```java
public class NPCCommands {
    
    // NPC 생성
    @Command(aliases = {"npc"}, modifiers = {"create"})
    public void create(CommandContext args, CommandSender sender, NPC npc)
    
    // NPC 제거
    @Command(aliases = {"npc"}, modifiers = {"remove"})
    public void remove(CommandContext args, CommandSender sender, NPC npc)
    
    // NPC 선택
    @Command(aliases = {"npc"}, modifiers = {"select"})
    public void select(CommandContext args, CommandSender sender, NPC npc)
    
    // NPC 스폰
    @Command(aliases = {"npc"}, modifiers = {"spawn"})
    public void spawn(CommandContext args, CommandSender sender, NPC npc)
    
    // NPC 디스폰
    @Command(aliases = {"npc"}, modifiers = {"despawn"})
    public void despawn(CommandContext args, CommandSender sender, NPC npc)
    
    // NPC 텔레포트
    @Command(aliases = {"npc"}, modifiers = {"tp"})
    public void tp(CommandContext args, Player player, NPC npc)
    
    // NPC 이름 설정
    @Command(aliases = {"npc"}, modifiers = {"rename"})
    public void rename(CommandContext args, CommandSender sender, NPC npc)
    
    // NPC 속도 설정
    @Command(aliases = {"npc"}, modifiers = {"speed"})
    public void speed(CommandContext args, CommandSender sender, NPC npc)
}
```

### TraitCommands.java

트레이트 관련 명령어들을 처리합니다.

```java
public class TraitCommands {
    
    // 트레이트 추가
    @Command(aliases = {"trait"}, modifiers = {"add"})
    public void add(CommandContext args, CommandSender sender, NPC npc)
    
    // 트레이트 제거
    @Command(aliases = {"trait"}, modifiers = {"remove"})
    public void remove(CommandContext args, CommandSender sender, NPC npc)
    
    // 트레이트 토글
    @Command(aliases = {"trait"}, modifiers = {"*"})
    public void toggle(CommandContext args, CommandSender sender, NPC npc)
}
```

---

## 에디터 시스템

### Editor.java

에디터의 기본 인터페이스입니다.

```java
public abstract class Editor implements Listener {
    
    public abstract void begin();
    public abstract void end();
    
    // 정적 메서드들
    public static void enterOrLeave(Player player, Editor editor)
    public static boolean hasEditor(Player player)
    public static void leave(Player player)
    public static void leaveAll()
}
```

### EquipmentEditor.java

장비 에디터입니다.

```java
public class EquipmentEditor extends Editor {
    
    public EquipmentEditor(Player player, NPC npc)
    
    @Override
    public void begin()
    
    @Override
    public void end()
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event)
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
}
```

#### 사용 예시

```java
// 장비 에디터 시작
EquipmentEditor editor = new EquipmentEditor(player, npc);
Editor.enterOrLeave(player, editor);

// 에디터 종료
Editor.leave(player);
```

---

## 이벤트 시스템

### EventListen.java

Citizens의 메인 이벤트 리스너입니다.

#### 주요 이벤트 핸들러들

```java
public class EventListen implements Listener {
    
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event)
    
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event)
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
    
    @EventHandler
    public void onNPCSpawn(NPCSpawnEvent event)
    
    @EventHandler
    public void onNPCDespawn(NPCDespawnEvent event)
    
    @EventHandler
    public void onNPCRemove(NPCRemoveEvent event)
}
```

### 커스텀 이벤트 사용

```java
// NPC 생성 이벤트 리스닝
@EventHandler
public void onNPCCreate(NPCCreateEvent event) {
    NPC npc = event.getNPC();
    // NPC 생성 후 처리
}

// NPC 우클릭 이벤트 리스닝
@EventHandler
public void onNPCRightClick(NPCRightClickEvent event) {
    NPC npc = event.getNPC();
    Player player = event.getClicker();
    // NPC 우클릭 처리
}
```

---

## 유틸리티 클래스

### Util.java

다양한 유틸리티 메서드들을 제공합니다.

```java
public class Util {
    
    // 위치 관련
    public static Location getCenterLocation(Location location)
    public static boolean isLoaded(Location location)
    
    // 문자열 관련
    public static String listify(Collection<?> collection)
    public static String prettyPrintLocation(Location location)
    
    // 엔티티 관련
    public static void sendPacketNearby(Location location, Packet<?> packet)
    public static void sendPacketNearby(Location location, Packet<?> packet, double radius)
    
    // 시간 관련
    public static int toTicks(Duration duration)
    public static Duration parseDuration(String input)
}
```

### StringHelper.java

문자열 처리 헬퍼 클래스입니다.

```java
public class StringHelper {
    
    public static String capitalize(String str)
    public static String wrap(String string, int length)
    public static List<String> wrap(String string, int length, String prefix)
    public static String parseColors(String input)
}
```

---

## 설정 시스템

### Settings.java

Citizens의 설정을 관리합니다.

#### 주요 설정들

```java
public class Settings {
    
    public enum Setting {
        // NPC 관련
        DEFAULT_NPC_LIMIT("npc.limits.default-limit", 10),
        DEFAULT_LOOK_CLOSE("npc.default.look-close.enabled", false),
        DEFAULT_LOOK_CLOSE_RANGE("npc.default.look-close.range", 10),
        
        // 채팅 관련
        CHAT_RANGE("npc.chat.options.range", 5),
        CHAT_FORMAT("npc.chat.format.no-targets", "[<npc>]: <text>"),
        
        // 경로 찾기 관련
        DEFAULT_PATHFINDING_RANGE("npc.pathfinding.default-range-blocks", 75F),
        PATHFINDER_TYPE("npc.pathfinding.pathfinder-type", "MINECRAFT"),
        
        // 스킨 관련
        NPC_SKIN_RETRY_DELAY("npc.skins.retry-delay", "5s"),
        NPC_SKIN_VIEW_DISTANCE("npc.skins.view-distance", 100),
        
        // 홀로그램 관련
        DEFAULT_NPC_HOLOGRAM_LINE_HEIGHT("npc.hologram.default-line-height", 0.4D),
        HOLOGRAM_UPDATE_RATE("npc.hologram.update-rate", "1s");
        
        // 설정 값 접근
        public boolean asBoolean()
        public double asDouble()
        public float asFloat()
        public int asInt()
        public String asString()
        public int asTicks()
        public int asSeconds()
    }
}
```

#### 사용 예시

```java
// 설정 값 읽기
int npcLimit = Settings.Setting.DEFAULT_NPC_LIMIT.asInt();
double lookRange = Settings.Setting.DEFAULT_LOOK_CLOSE_RANGE.asDouble();
String chatFormat = Settings.Setting.CHAT_FORMAT.asString();

// 시간 설정
int retryDelayTicks = Settings.Setting.NPC_SKIN_RETRY_DELAY.asTicks();
int updateRateSeconds = Settings.Setting.HOLOGRAM_UPDATE_RATE.asSeconds();
```

---

## 고급 사용법

### 커스텀 트레이트 생성

```java
@TraitName("customtrait")
public class CustomTrait extends Trait {
    
    @Persist
    private String customData;
    
    public CustomTrait() {
        super("customtrait");
    }
    
    @Override
    public void onSpawn() {
        // 스폰 시 실행될 코드
    }
    
    @Override
    public void onDespawn() {
        // 디스폰 시 실행될 코드
    }
    
    @Override
    public void run() {
        // 틱마다 실행될 코드
    }
    
    // 커스텀 메서드들
    public void setCustomData(String data) {
        this.customData = data;
    }
    
    public String getCustomData() {
        return customData;
    }
}

// 트레이트 등록
TraitInfo info = TraitInfo.create(CustomTrait.class);
citizens.getTraitFactory().registerTrait(info);
```

### 커스텀 네비게이션 전략

```java
public class CustomNavigationStrategy extends AbstractPathStrategy {
    
    private final Location target;
    private final NPC npc;
    
    public CustomNavigationStrategy(NPC npc, Location target) {
        this.npc = npc;
        this.target = target;
    }
    
    @Override
    public boolean update() {
        // 커스텀 네비게이션 로직
        return false; // 완료 시 false 반환
    }
    
    @Override
    public void stop() {
        // 정지 로직
    }
    
    @Override
    public Location getTargetAsLocation() {
        return target;
    }
    
    @Override
    public TargetType getTargetType() {
        return TargetType.LOCATION;
    }
}

// 사용
npc.getNavigator().setTarget(params -> new CustomNavigationStrategy(npc, targetLocation));
```

### 플레이스홀더 확장

```java
public class CitizensPlaceholders extends PlaceholderExpansion {
    
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // %citizens_npc_name%
        if (params.equals("npc_name")) {
            NPC npc = selector.getSelected(player.getPlayer());
            return npc != null ? npc.getName() : "None";
        }
        
        // %citizens_npc_id%
        if (params.equals("npc_id")) {
            NPC npc = selector.getSelected(player.getPlayer());
            return npc != null ? String.valueOf(npc.getId()) : "0";
        }
        
        return null;
    }
    
    @Override
    public String getIdentifier() {
        return "citizens";
    }
    
    @Override
    public String getAuthor() {
        return "Citizens";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
```

---

## 예제 코드

### 완전한 NPC 생성 예제

```java
public class NPCExample {
    
    public void createAdvancedNPC(Player player) {
        Citizens citizens = (Citizens) Bukkit.getPluginManager().getPlugin("Citizens");
        NPCRegistry registry = citizens.getNPCRegistry();
        
        // NPC 생성
        NPC npc = registry.createNPC(EntityType.PLAYER, "AdvancedNPC");
        
        // 위치 설정 및 스폰
        Location spawnLocation = player.getLocation();
        npc.spawn(spawnLocation);
        
        // 트레이트 추가
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.setRange(15.0);
        lookClose.setRealisticLooking(true);
        
        // 스킨 설정
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinName("Notch");
        
        // 홀로그램 설정
        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.addLine("§eAdvanced NPC");
        hologram.addLine("§7Click to interact");
        
        // 커맨드 설정
        CommandTrait commands = npc.getOrAddTrait(CommandTrait.class);
        commands.addCommand(new NPCCommand("say Hello %p!", CommandContext.CONSOLE));
        
        // 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(new NPCListener(npc), this);
    }
    
    private class NPCListener implements Listener {
        private final NPC npc;
        
        public NPCListener(NPC npc) {
            this.npc = npc;
        }
        
        @EventHandler
        public void onNPCRightClick(NPCRightClickEvent event) {
            if (event.getNPC() == npc) {
                Player player = event.getClicker();
                player.sendMessage("§aYou clicked the advanced NPC!");
            }
        }
    }
}
```

---

## 문제 해결

### 일반적인 문제들

1. **NPC가 스폰되지 않음**
   - 청크가 로드되었는지 확인
   - 유효한 위치인지 확인
   - 월드가 존재하는지 확인

2. **트레이트가 작동하지 않음**
   - 트레이트가 올바르게 등록되었는지 확인
   - NPC가 스폰된 상태인지 확인
   - 트레이트 설정이 올바른지 확인

3. **네비게이션 문제**
   - 목표 위치가 도달 가능한지 확인
   - 경로 찾기 범위 설정 확인
   - 장애물이 없는지 확인

### 디버깅 팁

```java
// NPC 상태 확인
if (npc.isSpawned()) {
    System.out.println("NPC is spawned at: " + npc.getEntity().getLocation());
} else {
    System.out.println("NPC is not spawned");
}

// 트레이트 확인
for (Trait trait : npc.getTraits()) {
    System.out.println("Trait: " + trait.getName());
}

// 네비게이션 상태 확인
Navigator nav = npc.getNavigator();
if (nav.isNavigating()) {
    System.out.println("Navigating to: " + nav.getTargetAsLocation());
}
```

---

## 마무리

이 문서는 Citizens-main API의 주요 기능들을 다루었습니다. 더 자세한 정보는 소스코드를 참조하시거나 공식 문서를 확인하시기 바랍니다.

### 참고 링크

- [Citizens GitHub](https://github.com/CitizensDev/Citizens2)
- [Citizens Wiki](https://wiki.citizensnpcs.co/)
- [Citizens JavaDocs](https://javadocs.citizensnpcs.co/)

### 버전 정보

- Citizens-main: 2.0.39
- 문서 작성일: 2025년 1월
- 대상 Minecraft 버전: 1.20+ 
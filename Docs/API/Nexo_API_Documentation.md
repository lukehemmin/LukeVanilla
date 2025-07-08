# Nexo API 문서

## 개요

Nexo는 사용자 정의 아이템, 블록, 가구를 생성하고 관리할 수 있는 Minecraft 플러그인입니다. 이 문서는 Nexo의 API를 사용하여 다른 플러그인에서 Nexo와 상호작용하는 방법을 설명합니다.

## 의존성 설정

### Repository & Dependencies

```gradle
repositories {
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    compileOnly("com.nexomc:nexo:<version>") //Nexo 1.X -> 1.X.0
}
```

Maven을 사용하는 경우:

```xml
<repositories>
    <repository>
        <id>nexo-repo</id>
        <url>https://repo.nexomc.com/releases</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.nexomc</groupId>
        <artifactId>nexo</artifactId>
        <version>1.8.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## 핵심 API 클래스

### 1. NexoItems

`com.nexomc.nexo.api.NexoItems`는 Nexo 아이템과 관련된 모든 기능을 제공하는 중앙 클래스입니다.

#### 주요 메소드들

##### 아이템 ID로 아이템 빌더 가져오기
```java
// 아이템 ID로 ItemBuilder 가져오기
ItemBuilder builder = NexoItems.itemFromId("my_custom_item");
if (builder != null) {
    ItemStack itemStack = builder.build();
}

// Optional 형태로 가져오기
Optional<ItemBuilder> optionalBuilder = NexoItems.optionalItemFromId("my_custom_item");
```

##### ItemStack에서 Nexo 아이템 ID 가져오기
```java
// ItemStack이 Nexo 아이템인지 확인하고 ID 가져오기
String itemId = NexoItems.idFromItem(itemStack);
if (itemId != null) {
    // 이 ItemStack은 Nexo 아이템입니다
    System.out.println("Nexo 아이템 ID: " + itemId);
}
```

##### ItemStack에서 ItemBuilder 가져오기
```java
ItemBuilder builder = NexoItems.builderFromItem(itemStack);
if (builder != null) {
    // ItemStack이 Nexo 아이템인 경우 빌더를 얻을 수 있습니다
}
```

##### 아이템 존재 확인
```java
// 아이템 ID 존재 확인
boolean exists = NexoItems.exists("my_custom_item");

// ItemStack이 Nexo 아이템인지 확인
boolean isNexoItem = NexoItems.exists(itemStack);
```

##### 메커닉 확인
```java
// 특정 아이템이 특정 메커닉을 가지고 있는지 확인
boolean hasMechanic = NexoItems.hasMechanic("my_custom_item", "furniture");
```

##### 모든 아이템 정보 가져오기
```java
// 모든 아이템 ID 가져오기
Set<String> allItemNames = NexoItems.itemNames();

// 모든 ItemBuilder 가져오기
Set<ItemBuilder> allItems = NexoItems.items();

// 모든 아이템 매핑 가져오기
Map<String, ItemBuilder> allEntries = NexoItems.entries();

// 명령어에서 제외되지 않은 아이템 이름들
String[] unexcludedNames = NexoItems.unexcludedItemNames();
```

### 2. NexoBlocks

`com.nexomc.nexo.api.NexoBlocks`는 Nexo 커스텀 블록과 관련된 기능을 제공합니다.

#### 주요 메소드들

##### 블록 배치
```java
// 특정 위치에 Nexo 블록 배치
Location location = new Location(world, x, y, z);
NexoBlocks.place("my_custom_block", location);
```

##### 블록 확인
```java
// 블록이 Nexo 커스텀 블록인지 확인
Block block = location.getBlock();
boolean isCustomBlock = NexoBlocks.isCustomBlock(block);

// 아이템 ID가 커스텀 블록인지 확인
boolean isCustomBlockItem = NexoBlocks.isCustomBlock("my_custom_block");

// 특정 타입 블록 확인
boolean isNoteBlock = NexoBlocks.isNexoNoteBlock(block);
boolean isStringBlock = NexoBlocks.isNexoStringBlock(block);
boolean isChorusBlock = NexoBlocks.isNexoChorusBlock(block);
```

##### 블록 제거
```java
// 블록 제거 (아이템 드롭 없음)
boolean removed = NexoBlocks.remove(location);

// 플레이어가 제거 (아이템 드롭)
boolean removed = NexoBlocks.remove(location, player);

// 강제 드롭
boolean removed = NexoBlocks.remove(location, player, true);
```

##### 블록 메커닉 가져오기
```java
// 일반 커스텀 블록 메커닉
CustomBlockMechanic mechanic = NexoBlocks.customBlockMechanic(location);

// 특정 타입 메커닉
NoteBlockMechanic noteBlockMechanic = NexoBlocks.noteBlockMechanic(block);
StringBlockMechanic stringMechanic = NexoBlocks.stringMechanic(block);
ChorusBlockMechanic chorusMechanic = NexoBlocks.chorusBlockMechanic(block);
```

##### 블록 데이터 가져오기
```java
// 아이템 ID로 BlockData 가져오기
BlockData blockData = NexoBlocks.blockData("my_custom_block");
```

##### 블록 ID 목록
```java
// 모든 커스텀 블록 ID
String[] allBlockIds = NexoBlocks.blockIDs();

// 노트 블록 ID들
String[] noteBlockIds = NexoBlocks.noteBlockIDs();

// 스트링 블록 ID들
String[] stringBlockIds = NexoBlocks.stringBlockIDs();

// 코러스 블록 ID들
String[] chorusBlockIds = NexoBlocks.chorusBlockIDs();
```

### 3. NexoFurniture

`com.nexomc.nexo.api.NexoFurniture`는 Nexo 가구와 관련된 기능을 제공합니다.

#### 주요 메소드들

##### 가구 배치
```java
// 회전값과 블록 면을 지정하여 가구 배치
ItemDisplay furniture = NexoFurniture.place("my_furniture", location, Rotation.CLOCKWISE, BlockFace.NORTH);

// Yaw 값으로 가구 배치
ItemDisplay furniture = NexoFurniture.place("my_furniture", location, 90.0f, BlockFace.NORTH);
```

##### 가구 확인
```java
// 위치에 가구가 있는지 확인
boolean hasFurniture = NexoFurniture.isFurniture(location);

// 아이템이 가구인지 확인
boolean isFurnitureItem = NexoFurniture.isFurniture("my_furniture");
boolean isFurnitureStack = NexoFurniture.isFurniture(itemStack);

// 엔티티가 가구인지 확인
boolean isFurnitureEntity = NexoFurniture.isFurniture(entity);
```

##### 가구 제거
```java
// 위치의 가구 제거
boolean removed = NexoFurniture.remove(location);

// 플레이어가 제거 (아이템 드롭)
boolean removed = NexoFurniture.remove(location, player);

// 특정 드롭으로 제거
Drop customDrop = // ... 커스텀 드롭 생성
boolean removed = NexoFurniture.remove(location, player, customDrop);

// 엔티티 기준 제거
boolean removed = NexoFurniture.remove(furnitureEntity, player);
```

##### 가구 메커닉 가져오기
```java
// 위치에서 가구 메커닉 가져오기
FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(location);

// 엔티티에서 가구 메커닉 가져오기
FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(entity);

// 아이템 ID로 가구 메커닉 가져오기
FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic("my_furniture");
```

##### 기타 가구 기능
```java
// 가구의 기본 엔티티 가져오기
ItemDisplay baseEntity = NexoFurniture.baseEntity(location);
ItemDisplay baseEntity = NexoFurniture.baseEntity(block);
ItemDisplay baseEntity = NexoFurniture.baseEntity(interactionId);

// 가구 업데이트
NexoFurniture.updateFurniture(baseEntity);

// 플레이어가 바라보는 가구 찾기
ItemDisplay targetFurniture = NexoFurniture.findTargetFurniture(player);

// 모든 가구 ID 가져오기
String[] furnitureIds = NexoFurniture.furnitureIDs();
```

### 4. NexoPack

`com.nexomc.nexo.api.NexoPack`는 리소스팩 관리와 관련된 기능을 제공합니다.

#### 주요 메소드들

##### 리소스팩 전송
```java
// 플레이어에게 리소스팩 전송
NexoPack.sendPack(player);
```

##### 리소스팩 관리
```java
// 현재 리소스팩 가져오기
ResourcePack resourcePack = NexoPack.resourcePack();

// 빌드된 리소스팩 가져오기
ResourcePack builtPack = NexoPack.builtResourcePack();
```

##### 리소스팩 병합
```java
// ZIP 파일에서 리소스팩 병합
File zipFile = new File("path/to/pack.zip");
NexoPack.mergePackFromZip(zipFile);

// 디렉토리에서 리소스팩 병합
File directory = new File("path/to/pack/");
NexoPack.mergePackFromDirectory(directory);

// 리소스팩 덮어쓰기
ResourcePack newPack = // ... 새로운 팩
NexoPack.overwritePack(resourcePack, newPack);

// 리소스팩 지우기
NexoPack.clearPack(resourcePack);

// 리소스팩 병합
NexoPack.mergePack(basePack, importPack);
```

## 이벤트 API

Nexo는 다양한 이벤트를 제공하여 플러그인에서 Nexo의 동작에 반응할 수 있습니다.

### 핵심 이벤트

#### NexoItemsLoadedEvent
```java
@EventHandler
public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
    // Nexo 아이템들이 로드/리로드되었을 때 호출됩니다
    System.out.println("Nexo 아이템들이 로드되었습니다!");
}
```

#### NexoMechanicsRegisteredEvent
```java
@EventHandler
public void onNexoMechanicsRegistered(NexoMechanicsRegisteredEvent event) {
    // Nexo 메커닉들이 등록되었을 때 호출됩니다
    // 사용자 정의 메커닉을 등록하기 좋은 시점입니다
    registerCustomMechanics();
}
```

### 블록 이벤트

#### NexoBlockPlaceEvent
```java
@EventHandler
public void onNexoBlockPlace(NexoBlockPlaceEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    Block block = event.getBlock();
    
    if (itemId.equals("my_special_block")) {
        player.sendMessage("특별한 블록을 설치했습니다!");
    }
}
```

#### NexoBlockBreakEvent
```java
@EventHandler
public void onNexoBlockBreak(NexoBlockBreakEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    
    if (itemId.equals("protected_block")) {
        event.setCancelled(true);
        player.sendMessage("이 블록은 파괴할 수 없습니다!");
    }
}
```

#### NexoBlockInteractEvent
```java
@EventHandler
public void onNexoBlockInteract(NexoBlockInteractEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    Action action = event.getAction();
    
    if (action == Action.RIGHT_CLICK_BLOCK && itemId.equals("interactive_block")) {
        player.sendMessage("인터랙티브 블록과 상호작용했습니다!");
    }
}
```

#### NexoBlockDamageEvent
```java
@EventHandler
public void onNexoBlockDamage(NexoBlockDamageEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    
    // 블록 손상 처리
}
```

### 가구 이벤트

#### NexoFurniturePlaceEvent
```java
@EventHandler
public void onNexoFurniturePlace(NexoFurniturePlaceEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    ItemDisplay baseEntity = event.getBaseEntity();
    
    if (itemId.equals("special_furniture")) {
        player.sendMessage("특별한 가구를 설치했습니다!");
    }
}
```

#### NexoFurnitureBreakEvent
```java
@EventHandler
public void onNexoFurnitureBreak(NexoFurnitureBreakEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    
    if (itemId.equals("protected_furniture")) {
        event.setCancelled(true);
        player.sendMessage("이 가구는 파괴할 수 없습니다!");
    }
}
```

#### NexoFurnitureInteractEvent
```java
@EventHandler
public void onNexoFurnitureInteract(NexoFurnitureInteractEvent event) {
    Player player = event.getPlayer();
    String itemId = event.getItemId();
    
    if (itemId.equals("storage_furniture")) {
        // 저장소 가구 GUI 열기
        openStorageGUI(player);
    }
}
```

## 커스텀 메커닉 생성

Nexo는 사용자 정의 메커닉을 추가할 수 있는 시스템을 제공합니다.

### 메커닉 클래스 생성

```java
public class MyCustomMechanic extends Mechanic {
    private final String customProperty;
    
    public MyCustomMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.customProperty = section.getString("custom_property", "default_value");
    }
    
    public String getCustomProperty() {
        return customProperty;
    }
}
```

### 메커닉 팩토리 생성

```java
public class MyCustomMechanicFactory extends MechanicFactory implements Listener {
    private static MyCustomMechanicFactory instance;
    
    public MyCustomMechanicFactory(ConfigurationSection section) {
        super(section);
        instance = this;
        registerListeners(this);
    }
    
    public static MyCustomMechanicFactory getInstance() {
        return instance;
    }
    
    @Override
    public Mechanic parse(ConfigurationSection section) {
        if (!section.contains("custom_property")) return null;
        
        MyCustomMechanic mechanic = new MyCustomMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        MyCustomMechanic mechanic = (MyCustomMechanic) getMechanic(item);
        
        if (mechanic != null) {
            // 커스텀 로직 처리
            event.getPlayer().sendMessage("커스텀 메커닉 작동: " + mechanic.getCustomProperty());
        }
    }
}
```

### 메커닉 등록

```java
public class MyPlugin extends JavaPlugin implements Listener {
    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    @EventHandler
    public void onNexoMechanicsRegistered(NexoMechanicsRegisteredEvent event) {
        // 메커닉 팩토리 등록
        MechanicsManager.registerMechanicFactory("my_custom_mechanic", 
                (section) -> new MyCustomMechanicFactory(section), 
                true);
    }
}
```

## 실제 사용 예제

### 예제 1: 특별한 아이템 감지 및 처리

```java
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    ItemStack item = event.getItem();
    if (item == null) return;
    
    String nexoId = NexoItems.idFromItem(item);
    if (nexoId != null) {
        switch (nexoId) {
            case "magic_wand":
                handleMagicWand(event.getPlayer(), event.getClickedBlock());
                break;
            case "teleport_scroll":
                handleTeleportScroll(event.getPlayer());
                break;
        }
    }
}

private void handleMagicWand(Player player, Block clickedBlock) {
    if (clickedBlock != null && NexoBlocks.isCustomBlock(clickedBlock)) {
        String blockId = NexoBlocks.customBlockMechanic(clickedBlock).getItemID();
        player.sendMessage("마법 지팡이로 " + blockId + " 블록을 클릭했습니다!");
    }
}
```

### 예제 2: 커스텀 블록으로 구조물 생성

```java
public void createCustomStructure(Location centerLocation) {
    // 3x3 크기의 커스텀 블록 구조물 생성
    for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
            Location blockLocation = centerLocation.clone().add(x, 0, z);
            
            if (x == 0 && z == 0) {
                // 중앙은 특별한 블록
                NexoBlocks.place("special_center_block", blockLocation);
            } else {
                // 외곽은 일반 커스텀 블록
                NexoBlocks.place("structure_block", blockLocation);
            }
        }
    }
}
```

### 예제 3: 가구 관리 시스템

```java
public class FurnitureManager {
    private final Map<UUID, List<ItemDisplay>> playerFurniture = new HashMap<>();
    
    @EventHandler
    public void onFurniturePlace(NexoFurniturePlaceEvent event) {
        Player player = event.getPlayer();
        ItemDisplay furniture = event.getBaseEntity();
        
        playerFurniture.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
                     .add(furniture);
        
        player.sendMessage("가구를 설치했습니다! 총 " + 
                         playerFurniture.get(player.getUniqueId()).size() + "개의 가구를 소유하고 있습니다.");
    }
    
    @EventHandler
    public void onFurnitureBreak(NexoFurnitureBreakEvent event) {
        Player player = event.getPlayer();
        ItemDisplay furniture = event.getBaseEntity();
        
        List<ItemDisplay> furnitureList = playerFurniture.get(player.getUniqueId());
        if (furnitureList != null) {
            furnitureList.remove(furniture);
        }
    }
    
    public void removeAllPlayerFurniture(Player player) {
        List<ItemDisplay> furnitureList = playerFurniture.get(player.getUniqueId());
        if (furnitureList != null) {
            for (ItemDisplay furniture : new ArrayList<>(furnitureList)) {
                NexoFurniture.remove(furniture, player);
            }
            furnitureList.clear();
        }
    }
}
```

### 예제 4: 아이템 변환 시스템

```java
public void convertVanillaToNexo(Player player, String vanillaMaterial, String nexoItemId) {
    Inventory inventory = player.getInventory();
    
    for (int i = 0; i < inventory.getSize(); i++) {
        ItemStack item = inventory.getItem(i);
        if (item != null && item.getType().name().equals(vanillaMaterial)) {
            ItemBuilder nexoBuilder = NexoItems.itemFromId(nexoItemId);
            if (nexoBuilder != null) {
                ItemStack nexoItem = nexoBuilder.build();
                nexoItem.setAmount(item.getAmount());
                inventory.setItem(i, nexoItem);
            }
        }
    }
    
    player.sendMessage("아이템이 Nexo 아이템으로 변환되었습니다!");
}
```

## 주의사항 및 팁

### 1. 성능 고려사항
- `NexoItems.exists()` 메소드는 자주 호출해도 성능상 문제가 없도록 최적화되어 있습니다.
- 많은 양의 블록이나 가구를 처리할 때는 배치 처리를 고려하세요.

### 2. 이벤트 취소
- Nexo 이벤트는 대부분 `Cancellable`을 구현하므로 필요에 따라 취소할 수 있습니다.

### 3. 리로드 대응
- `NexoItemsLoadedEvent`와 `NexoMechanicsRegisteredEvent`를 활용하여 리로드에 대응하세요.

### 4. 버전 호환성
- Nexo API는 버전 간 호환성을 유지하려고 하지만, 메이저 업데이트 시 변경사항을 확인하세요.

### 5. 데이터 지속성
- 가구나 블록의 위치 정보를 저장할 때는 월드 언로드/로드를 고려하세요.

## 소스코드 분석을 통한 추가 정보

### ItemBuilder 클래스 세부 사항

소스코드 분석을 통해 확인된 ItemBuilder의 주요 기능들:

```java
// ItemBuilder에서 커스텀 태그 가져오기
String customTag = itemBuilder.customTag(ITEM_ID, PersistentDataType.STRING);

// 아이템 리로드
NexoItems.reloadItem("item_id");

// 특정 파일의 제외되지 않은 아이템들
List<ItemBuilder> unexcludedItems = NexoItems.unexcludedItems(file);
```

### MechanicsManager 상세 사용법

```java
// 메커닉 팩토리 가져오기
MechanicFactory factory = MechanicsManager.getMechanicFactory("mechanic_id");

// 메커닉 가져오기
Mechanic mechanic = factory.getMechanic("item_id");

// 모든 메커닉 팩토리 등록 (실제 소스코드 방식)
MechanicsManager.registerMechanicFactory("custom_mechanic", 
    (section) -> new CustomMechanicFactory(section));
```

### 실제 ItemStack 처리

```java
// 1.21.1 버전에서 persistentDataView 사용
String itemId = itemStack.persistentDataView.get(NexoItems.ITEM_ID, PersistentDataType.STRING);

// ItemBuilder에서 ItemStack 빌드
ItemStack item = itemBuilder.build();
```

### 가구 변환 및 업데이트

```java
// 가구 변환 (레거시 가구를 새 형식으로)
NexoFurniture.convertFurniture(baseEntity);

// 가구 업데이트 (좌석, 침대 등 포함)
NexoFurniture.updateFurniture(baseEntity);
```

### 리소스팩 고급 기능

```java
// 특정 ResourceContainer 병합
private static void mergeContainers(ResourceContainer container, ResourceContainer importedContainer) {
    // 텍스처, 사운드, 폰트 등 병합
    importedContainer.textures().forEach(container::texture);
    importedContainer.sounds().forEach(container::sound);
    importedContainer.fonts().forEach { font ->
        font.toBuilder().apply {
            container.font(font.key())?.providers()?.forEach(::addProvider)
        }.build().addTo(container)
    }
}
```

### 커스텀 PackServer 구현

```java
public class CustomPackServer extends NexoPackServer {
    @Override
    public CompletableFuture<Void> uploadPack() {
        // 팩 업로드 로직
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void sendPack(Player player) {
        // 플레이어에게 팩 전송
    }
    
    @Override
    public void start() {
        // 서버 시작
    }
    
    @Override
    public void stop() {
        // 서버 중지
    }
    
    @Override
    public String packUrl() {
        return "http://your-server.com/pack.zip";
    }
    
    @Override
    public ResourcePackInfo packInfo() {
        return null; // 또는 적절한 ResourcePackInfo
    }
}

// PackServer 등록
PackServerRegistry.register("custom", new CustomPackServer());
```

### 음식 컴포넌트 처리 (1.21.1+)

```java
// 음식 아이템의 대체 아이템 설정
FoodComponent foodComponent = itemBuilder.getFoodComponent();
if (foodComponent != null) {
    ItemStack replacementItem = // ... 대체 아이템 생성
    ItemUtils.setUsingConvertsTo(foodComponent, replacementItem);
    itemBuilder.setFoodComponent(foodComponent).regenerateItem();
}
```

### 커스텀 블록 타입별 처리

```java
// 각 블록 타입별 메커닉 가져오기
NoteBlockMechanic noteBlockMechanic = NoteBlockMechanicFactory.instance()?.getMechanic(itemId);
StringBlockMechanic stringMechanic = StringBlockMechanicFactory.instance()?.getMechanic(itemId);
ChorusBlockMechanic chorusMechanic = ChorusBlockFactory.instance()?.getMechanic(itemId);

// 블록 타입 확인
boolean isNotImplemented = factory.isNotImplementedIn(itemId);
Set<String> implementedItems = factory.items();
```

### 가구 패킷 관리

```java
// 가구 패킷 매니저 사용
IFurniturePacketManager packetManager = FurnitureFactory.instance()?.packetManager();

// 히트박스 엔티티 패킷 전송
packetManager.sendHitboxEntityPacket(baseEntity, mechanic);

// 배리어 히트박스 패킷 전송
packetManager.sendBarrierHitboxPacket(baseEntity, mechanic);

// 조명 메커닉 패킷 전송
packetManager.sendLightMechanicPacket(baseEntity, mechanic);
```

### 디버깅 및 로깅

```java
// Nexo 로그 사용
import com.nexomc.nexo.utils.logs.Logs;

Logs.logError("에러 메시지");
Logs.logWarn("경고 메시지");
Logs.logSuccess("성공 메시지");
```

### 성능 최적화 팁

1. **ItemStack 확인 최적화**
   ```java
   // 이미 null 체크가 내장된 메소드 사용
   String itemId = NexoItems.idFromItem(itemStack); // null-safe
   if (itemId != null) {
       // Nexo 아이템 처리
   }
   ```

2. **배치 처리**
   ```java
   // 여러 블록을 한 번에 처리할 때
   List<Location> locations = Arrays.asList(loc1, loc2, loc3);
   locations.parallelStream().forEach(loc -> NexoBlocks.place("block_id", loc));
   ```

3. **캐시 활용**
   ```java
   // ItemConfigCache 활용 (내부적으로 사용됨)
   NexoItems.reloadItem(itemId); // 캐시된 설정 사용
   ```

이 문서는 Nexo 1.8.0 기준으로 작성되었으며, 실제 소스코드 분석을 통해 정확한 API 사용법과 내부 구현 세부사항을 제공합니다. 더 자세한 정보가 필요하면 Nexo의 공식 문서를 참조하거나 소스코드를 직접 확인하세요. 
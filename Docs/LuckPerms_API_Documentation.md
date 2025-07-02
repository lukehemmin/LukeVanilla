# LuckPerms API Documentation

## 목차
1. [개요](#개요)
2. [설정 및 초기화](#설정-및-초기화)
3. [핵심 API 클래스](#핵심-api-클래스)
4. [권한 관리](#권한-관리)
5. [그룹 관리](#그룹-관리)
6. [메타데이터 관리](#메타데이터-관리)
7. [이벤트 시스템](#이벤트-시스템)
8. [고급 기능](#고급-기능)
9. [모범 사례](#모범-사례)
10. [실제 구현 예제](#실제-구현-예제)

## 개요

LuckPerms는 Bukkit/Spigot 서버를 위한 고급 권한 관리 플러그인입니다. LuckPerms API를 사용하면 다른 플러그인에서 권한, 그룹, 메타데이터를 프로그래밍적으로 관리할 수 있습니다.

### 주요 특징
- **비동기 처리**: 모든 데이터베이스 작업은 비동기로 처리됩니다
- **캐시 시스템**: 성능 최적화를 위한 강력한 캐시 시스템
- **컨텍스트 시스템**: 서버, 월드별 권한 설정 지원
- **이벤트 시스템**: 권한 변경 사항을 실시간으로 감지

### API 버전
- **API 버전**: 5.2
- **지원 Bukkit 버전**: 1.8+
- **Java 버전**: 8+

## 설정 및 초기화

### Maven 의존성 추가

```xml
<dependency>
    <groupId>net.luckperms</groupId>
    <artifactId>api</artifactId>
    <version>5.2</version>
    <scope>provided</scope>
</dependency>
```

### plugin.yml 설정

```yaml
name: YourPlugin
version: 1.0.0
main: your.package.YourPlugin
api-version: 1.13
depend: [LuckPerms]  # LuckPerms가 먼저 로드되도록 설정
```

### 플러그인 초기화

```java
public class YourPlugin extends JavaPlugin {
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        // LuckPerms API 인스턴스를 Services Manager를 통해 로드
        this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        
        if (this.luckPerms == null) {
            getLogger().severe("LuckPerms API를 찾을 수 없습니다!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("LuckPerms API 초기화 완료!");
    }
}
```

## 핵심 API 클래스

### 1. LuckPerms (메인 API 클래스)

```java
// 기본 API 접근점
LuckPerms api = getServer().getServicesManager().load(LuckPerms.class);

// 주요 매니저 접근
UserManager userManager = api.getUserManager();
GroupManager groupManager = api.getGroupManager();
TrackManager trackManager = api.getTrackManager();
EventBus eventBus = api.getEventBus();
```

### 2. UserManager

```java
UserManager userManager = luckPerms.getUserManager();

// 온라인 사용자 조회
User user = userManager.getUser(uuid);
User user = userManager.getUser(username);

// 오프라인 사용자 로드 (비동기)
CompletableFuture<User> userFuture = userManager.loadUser(uuid);

// 사용자 데이터 수정
userManager.modifyUser(uuid, user -> {
    // 여기서 사용자 데이터 수정
});

// 모든 사용자 검색
CompletableFuture<Map<UUID, Collection<Node>>> searchResult = 
    userManager.searchAll(NodeMatcher.key("permission.example"));
```

### 3. GroupManager

```java
GroupManager groupManager = luckPerms.getGroupManager();

// 그룹 조회
Group group = groupManager.getGroup("groupName");

// 모든 그룹 조회
Collection<Group> groups = groupManager.getLoadedGroups();

// 그룹 생성
CompletableFuture<Group> createResult = groupManager.createAndLoadGroup("newGroup");

// 그룹 삭제
CompletableFuture<Void> deleteResult = groupManager.deleteGroup(group);
```

### 4. User 클래스

```java
// 기본 정보
UUID uuid = user.getUniqueId();
String username = user.getUsername();

// 노드 데이터 접근
NodeMap nodeMap = user.data();

// 캐시된 데이터 접근
CachedDataManager cachedData = user.getCachedData();

// 권한 확인
boolean hasPermission = user.getCachedData().getPermissionData().checkPermission("permission.example").asBoolean();

// 그룹 정보
Collection<Group> groups = user.getInheritedGroups(queryOptions);
```

### 5. Group 클래스

```java
// 기본 정보
String name = group.getName();
String displayName = group.getDisplayName();

// 노드 데이터 접근
NodeMap nodeMap = group.data();

// 캐시된 데이터 접근
CachedDataManager cachedData = group.getCachedData();
```

## 권한 관리

### 권한 추가

```java
public void addPermission(UUID playerUuid, String permission) {
    // 권한 노드 생성
    Node node = Node.builder(permission).build();
    
    // 사용자 데이터 수정
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        DataMutateResult result = user.data().add(node);
        
        if (result.wasSuccessful()) {
            // 권한 추가 성공
            getLogger().info("권한 " + permission + "을 " + user.getUsername() + "에게 추가했습니다.");
        } else {
            // 이미 권한이 있음
            getLogger().info(user.getUsername() + "은 이미 " + permission + " 권한을 가지고 있습니다.");
        }
    });
}
```

### 권한 제거

```java
public void removePermission(UUID playerUuid, String permission) {
    Node node = Node.builder(permission).build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        DataMutateResult result = user.data().remove(node);
        
        if (result.wasSuccessful()) {
            getLogger().info("권한 " + permission + "을 " + user.getUsername() + "에서 제거했습니다.");
        }
    });
}
```

### 권한 확인

```java
// 온라인 플레이어의 권한 확인
public boolean hasPermission(Player player, String permission) {
    User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
    QueryOptions queryOptions = luckPerms.getPlayerAdapter(Player.class).getQueryOptions(player);
    
    Tristate result = user.getCachedData().getPermissionData(queryOptions).checkPermission(permission);
    return result.asBoolean();
}

// 오프라인 플레이어의 권한 확인 (비동기)
public CompletableFuture<Boolean> hasPermissionAsync(UUID uuid, String permission) {
    return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
        Tristate result = user.getCachedData().getPermissionData().checkPermission(permission);
        return result.asBoolean();
    });
}
```

### 임시 권한 추가

```java
public void addTemporaryPermission(UUID playerUuid, String permission, Duration duration) {
    // 임시 권한 노드 생성
    Node node = Node.builder(permission)
        .expiry(Instant.now().plus(duration))
        .build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        user.data().add(node);
    });
}
```

### 컨텍스트별 권한 추가

```java
public void addWorldPermission(UUID playerUuid, String permission, String worldName) {
    // 특정 월드에서만 유효한 권한 노드 생성
    Node node = Node.builder(permission)
        .withContext(DefaultContextKeys.WORLD_KEY, worldName)
        .build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        user.data().add(node);
    });
}
```

## 그룹 관리

### 그룹에 플레이어 추가

```java
public void addPlayerToGroup(UUID playerUuid, String groupName) {
    // 그룹 존재 확인
    Group group = luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) {
        getLogger().warning("그룹 " + groupName + "을 찾을 수 없습니다!");
        return;
    }
    
    // 상속 노드 생성
    Node node = InheritanceNode.builder(group).build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        user.data().add(node);
    });
}
```

### 그룹에서 플레이어 제거

```java
public void removePlayerFromGroup(UUID playerUuid, String groupName) {
    Group group = luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) return;
    
    Node node = InheritanceNode.builder(group).build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        user.data().remove(node);
    });
}
```

### 플레이어의 기본 그룹 설정

```java
public void setPlayerPrimaryGroup(UUID playerUuid, String groupName) {
    Group group = luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) return;
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        // 기존 상속 그룹 모두 제거
        user.data().clear(NodeType.INHERITANCE::matches);
        
        // 새 그룹 추가
        Node node = InheritanceNode.builder(group).build();
        user.data().add(node);
    });
}
```

### 플레이어의 그룹 조회

```java
public Collection<String> getPlayerGroups(Player player) {
    PlayerAdapter<Player> playerAdapter = luckPerms.getPlayerAdapter(Player.class);
    User user = playerAdapter.getUser(player);
    QueryOptions queryOptions = playerAdapter.getQueryOptions(player);
    
    return user.getInheritedGroups(queryOptions)
        .stream()
        .map(Group::getName)
        .collect(Collectors.toList());
}
```

### 그룹 멤버 조회

```java
public CompletableFuture<Set<UUID>> getGroupMembers(String groupName) {
    Group group = luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) {
        return CompletableFuture.completedFuture(Collections.emptySet());
    }
    
    // 상속 노드 매처 생성
    NodeMatcher<InheritanceNode> matcher = NodeMatcher.key(InheritanceNode.builder(group).build());
    
    // 모든 사용자에서 해당 그룹을 상속받는 사용자 검색
    return luckPerms.getUserManager().searchAll(matcher)
        .thenApply(Map::keySet);
}
```

## 메타데이터 관리

### Prefix 설정

```java
public void setPlayerPrefix(UUID playerUuid, String prefix, int priority) {
    // Prefix 노드 생성
    Node node = PrefixNode.builder(prefix, priority).build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        // 기존 prefix 제거
        user.data().clear(NodeType.PREFIX::matches);
        
        // 새 prefix 추가
        user.data().add(node);
    });
}
```

### Suffix 설정

```java
public void setPlayerSuffix(UUID playerUuid, String suffix, int priority) {
    Node node = SuffixNode.builder(suffix, priority).build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        user.data().clear(NodeType.SUFFIX::matches);
        user.data().add(node);
    });
}
```

### Meta 설정

```java
public void setPlayerMeta(UUID playerUuid, String key, String value) {
    Node node = MetaNode.builder(key, value).build();
    
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        // 동일한 키의 메타 제거
        user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equals(key)));
        
        // 새 메타 추가
        user.data().add(node);
    });
}
```

### Prefix/Suffix 조회

```java
// 온라인 플레이어의 prefix 조회
public String getPlayerPrefix(Player player) {
    CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
    return metaData.getPrefix();
}

// 오프라인 플레이어의 prefix 조회
public CompletableFuture<String> getOfflinePlayerPrefix(UUID uuid) {
    return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
        CachedMetaData metaData = user.getCachedData().getMetaData();
        return metaData.getPrefix();
    });
}
```

### 우선순위를 고려한 Prefix 설정

```java
public void setPlayerPrefixWithPriority(UUID playerUuid, String prefix) {
    luckPerms.getUserManager().modifyUser(playerUuid, user -> {
        // 기존 prefix들의 최대 우선순위 찾기
        Map<Integer, String> inheritedPrefixes = user.getCachedData()
            .getMetaData(QueryOptions.nonContextual())
            .getPrefixes();
        
        // 기존 우선순위보다 10 높게 설정하여 오버라이드
        int priority = inheritedPrefixes.keySet().stream()
            .mapToInt(i -> i + 10)
            .max()
            .orElse(10);
        
        // 기존 prefix 제거
        user.data().clear(NodeType.PREFIX::matches);
        
        // 새 prefix 추가
        Node node = PrefixNode.builder(prefix, priority).build();
        user.data().add(node);
    });
}
```

## 이벤트 시스템

### 이벤트 리스너 등록

```java
public void registerEventListeners() {
    EventBus eventBus = luckPerms.getEventBus();
    
    // 노드 추가 이벤트
    eventBus.subscribe(this, NodeAddEvent.class, this::onNodeAdd);
    
    // 노드 제거 이벤트
    eventBus.subscribe(this, NodeRemoveEvent.class, this::onNodeRemove);
    
    // 사용자 로드 이벤트
    eventBus.subscribe(this, UserLoadEvent.class, this::onUserLoad);
    
    // 플레이어 데이터 저장 이벤트
    eventBus.subscribe(this, PlayerDataSaveEvent.class, this::onPlayerDataSave);
}
```

### 권한 부여 이벤트 처리

```java
private void onNodeAdd(NodeAddEvent event) {
    // 사용자 대상 이벤트만 처리
    if (!event.isUser()) {
        return;
    }
    
    Node node = event.getNode();
    User user = (User) event.getTarget();
    
    // 권한 노드인지 확인
    if (node.getType() == NodeType.PERMISSION) {
        // 비동기 이벤트이므로 메인 스레드에서 처리
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(user.getUniqueId());
            if (player != null) {
                player.sendMessage("권한 " + node.getKey() + "가 부여되었습니다!");
            }
        });
    }
}
```

### 그룹 변경 이벤트 처리

```java
private void onNodeAdd(NodeAddEvent event) {
    if (!event.isUser()) return;
    
    Node node = event.getNode();
    User user = (User) event.getTarget();
    
    if (node instanceof InheritanceNode) {
        InheritanceNode inheritanceNode = (InheritanceNode) node;
        String groupName = inheritanceNode.getGroupName();
        
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(user.getUniqueId());
            if (player != null) {
                player.sendMessage("그룹 " + groupName + "에 추가되었습니다!");
            }
        });
    }
}
```

### 첫 가입 감지

```java
private void onPlayerDataSave(PlayerDataSaveEvent event) {
    PlayerSaveResult result = event.getResult();
    Set<PlayerSaveResult.Outcome> outcomes = result.getOutcomes();
    
    // 첫 가입인지 확인
    if (outcomes.contains(PlayerSaveResult.Outcome.CLEAN_INSERT)) {
        String username = event.getUsername();
        getServer().broadcastMessage(username + "님이 서버에 처음 접속했습니다!");
        
        // 첫 가입자에게 기본 권한 부여
        UUID uuid = event.getUniqueId();
        addPlayerToGroup(uuid, "newbie");
    }
}
```

### 사용자명 변경 감지

```java
private void onPlayerDataSave(PlayerDataSaveEvent event) {
    PlayerSaveResult result = event.getResult();
    String previousUsername = result.getPreviousUsername();
    
    if (previousUsername != null) {
        String newUsername = event.getUsername();
        getServer().broadcastMessage(previousUsername + "님이 닉네임을 " + newUsername + "로 변경했습니다!");
    }
}
```

## 고급 기능

### 컨텍스트 시스템

```java
// 특정 월드에서만 유효한 권한
public void addWorldSpecificPermission(UUID uuid, String permission, String world) {
    Node node = Node.builder(permission)
        .withContext(DefaultContextKeys.WORLD_KEY, world)
        .build();
    
    luckPerms.getUserManager().modifyUser(uuid, user -> {
        user.data().add(node);
    });
}

// 특정 서버에서만 유효한 권한 (BungeeCord/Velocity 환경)
public void addServerSpecificPermission(UUID uuid, String permission, String server) {
    Node node = Node.builder(permission)
        .withContext(DefaultContextKeys.SERVER_KEY, server)
        .build();
    
    luckPerms.getUserManager().modifyUser(uuid, user -> {
        user.data().add(node);
    });
}
```

### 캐시 관리

```java
// 사용자 캐시 새로 고침
public void refreshUserCache(UUID uuid) {
    User user = luckPerms.getUserManager().getUser(uuid);
    if (user != null) {
        user.getCachedData().invalidate();
    }
}

// 그룹 캐시 새로 고침
public void refreshGroupCache(String groupName) {
    Group group = luckPerms.getGroupManager().getGroup(groupName);
    if (group != null) {
        group.getCachedData().invalidate();
    }
}
```

### 노드 필터링

```java
// 특정 타입의 노드만 조회
public Set<Node> getPermissionNodes(User user) {
    return user.data().toCollection().stream()
        .filter(NodeType.PERMISSION::matches)
        .collect(Collectors.toSet());
}

// 특정 패턴의 권한만 조회
public Set<String> getPermissionsStartingWith(User user, String prefix) {
    return user.data().toCollection().stream()
        .filter(NodeType.PERMISSION::matches)
        .map(Node::getKey)
        .filter(key -> key.startsWith(prefix))
        .collect(Collectors.toSet());
}
```

### Track 시스템

```java
// Track을 사용한 승급/강등
public void promotePlayer(UUID uuid, String trackName) {
    Track track = luckPerms.getTrackManager().getTrack(trackName);
    if (track == null) return;
    
    luckPerms.getUserManager().modifyUser(uuid, user -> {
        try {
            PromotionResult result = track.promote(user, luckPerms.getContextManager().getStaticContext());
            if (result.wasSuccessful()) {
                getLogger().info("플레이어가 " + result.getGroupTo().orElse("unknown") + "로 승급되었습니다.");
            }
        } catch (Exception e) {
            getLogger().warning("승급 중 오류 발생: " + e.getMessage());
        }
    });
}
```

## 모범 사례

### 1. 비동기 처리

```java
// ❌ 잘못된 방법 - 메인 스레드에서 사용자 로드
public void badExample(UUID uuid) {
    User user = luckPerms.getUserManager().loadUser(uuid).join(); // 메인 스레드 블록!
    // ...
}

// ✅ 올바른 방법 - 비동기 처리
public void goodExample(UUID uuid) {
    luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
        // 여기서 사용자 데이터 처리
        // UI 업데이트가 필요하면 메인 스레드로 전환
        getServer().getScheduler().runTask(this, () -> {
            // 메인 스레드에서 실행할 코드
        });
    });
}
```

### 2. 에러 처리

```java
public void safeUserModification(UUID uuid, String permission) {
    try {
        luckPerms.getUserManager().modifyUser(uuid, user -> {
            Node node = Node.builder(permission).build();
            DataMutateResult result = user.data().add(node);
            
            if (!result.wasSuccessful()) {
                getLogger().warning("권한 추가 실패: 이미 존재함");
            }
        }).exceptionally(throwable -> {
            getLogger().severe("사용자 데이터 수정 중 오류: " + throwable.getMessage());
            return null;
        });
    } catch (Exception e) {
        getLogger().severe("예상치 못한 오류: " + e.getMessage());
    }
}
```

### 3. 캐시 활용

```java
// 온라인 플레이어는 항상 캐시된 데이터 사용
public boolean hasPermissionCached(Player player, String permission) {
    PlayerAdapter<Player> adapter = luckPerms.getPlayerAdapter(Player.class);
    User user = adapter.getUser(player); // 캐시에서 즉시 반환
    QueryOptions options = adapter.getQueryOptions(player);
    
    return user.getCachedData().getPermissionData(options)
        .checkPermission(permission).asBoolean();
}
```

### 4. 리소스 정리

```java
@Override
public void onDisable() {
    // 이벤트 리스너 해제
    if (luckPerms != null) {
        luckPerms.getEventBus().unsubscribeAll(this);
    }
}
```

## api-cookbook 실제 구현 분석

### 명령어 구현 패턴 (AddPermissionCommand)

api-cookbook에서 보여주는 실제 권한 추가 명령어 구현:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 2) {
        sender.sendMessage(ChatColor.RED + "Please specify a player & a permission!");
        return true;
    }

    String playerName = args[0];
    String permission = args[1];

    // OfflinePlayer 객체로 플레이어 정보 가져오기
    OfflinePlayer player = this.plugin.getServer().getOfflinePlayer(playerName);

    // 플레이어가 서버에 접속한 적이 있는지 확인
    if (player == null || !player.hasPlayedBefore()) {
        sender.sendMessage(ChatColor.RED + playerName + " has never joined the server!");
        return true;
    }

    // 권한 노드 생성
    Node node = Node.builder(permission).build();

    // 사용자 데이터 수정 - cookbook의 핵심 패턴
    this.luckPerms.getUserManager().modifyUser(player.getUniqueId(), (User user) -> {
        DataMutateResult result = user.data().add(node);

        if (result.wasSuccessful()) {
            sender.sendMessage(ChatColor.GREEN + user.getUsername() + " now has permission " + permission + "!");
        } else {
            sender.sendMessage(ChatColor.RED + user.getUsername() + " already has that permission!");
        }
    });

    return true;
}
```

### 온라인 플레이어 그룹 조회 (GetGroupsCommand)

PlayerAdapter를 사용한 온라인 플레이어 처리:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    // 플레이어만 사용 가능한 명령어
    if (!(sender instanceof Player)) {
        sender.sendMessage(ChatColor.RED + "This command is only for players!");
        return true;
    }

    Player player = (Player) sender;

    // PlayerAdapter를 통한 Bukkit 플레이어 어댑터 가져오기
    PlayerAdapter<Player> playerAdapter = this.luckPerms.getPlayerAdapter(Player.class);

    // 캐시된 사용자 데이터 가져오기 (즉시 반환)
    User user = playerAdapter.getUser(player);

    // 현재 컨텍스트의 쿼리 옵션 가져오기
    Collection<Group> groups = user.getInheritedGroups(playerAdapter.getQueryOptions(player));

    // 그룹 이름을 콤마로 구분된 문자열로 변환
    String groupsString = groups.stream().map(Group::getName).collect(Collectors.joining(", "));

    sender.sendMessage(ChatColor.GREEN + "You inherit from: " + groupsString);
    return true;
}
```

### 오프라인 플레이어 Prefix 조회 (GetOfflinePrefixCommand)

온라인/오프라인 플레이어 처리의 모범 사례:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
        sender.sendMessage(ChatColor.RED + "Please specify a player name!");
        return true;
    }

    String playerName = args[0];

    // 1단계: 이미 로드된 온라인 사용자 확인
    User onlineUser = this.luckPerms.getUserManager().getUser(playerName);
    if (onlineUser != null) {
        // 온라인 사용자는 즉시 캐시된 데이터 사용
        CachedMetaData metaData = onlineUser.getCachedData().getMetaData();
        String prefix = metaData.getPrefix();
        
        sender.sendMessage(ChatColor.GREEN + onlineUser.getUsername() + "'s prefix is: " + ChatColor.RESET + prefix);
        return true;
    }

    // 2단계: 오프라인 플레이어 처리
    OfflinePlayer player = this.plugin.getServer().getOfflinePlayer(playerName);

    if (player == null || !player.hasPlayedBefore()) {
        sender.sendMessage(ChatColor.RED + playerName + " has never joined the server!");
        return true;
    }

    // 3단계: 비동기로 사용자 데이터 로드
    CompletableFuture<User> userLoadTask = this.luckPerms.getUserManager().loadUser(player.getUniqueId());

    // 4단계: 콜백으로 결과 처리
    userLoadTask.thenAccept((User user) -> {
        CachedMetaData metaData = user.getCachedData().getMetaData();
        String prefix = metaData.getPrefix();
        
        sender.sendMessage(ChatColor.GREEN + user.getUsername() + "'s prefix is: " + ChatColor.RESET + prefix);
    });

    return true;
}
```

### 그룹 설정 시 기존 그룹 제거 (SetGroupCommand)

그룹 변경 시의 모범 사례:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 2) {
        sender.sendMessage(ChatColor.RED + "Please specify a player & a group!");
        return true;
    }

    String playerName = args[0];
    String groupName = args[1];

    // 플레이어와 그룹 유효성 검사
    OfflinePlayer player = this.plugin.getServer().getOfflinePlayer(playerName);
    if (player == null || !player.hasPlayedBefore()) {
        sender.sendMessage(ChatColor.RED + playerName + " has never joined the server!");
        return true;
    }

    Group group = this.luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) {
        sender.sendMessage(ChatColor.RED + groupName + " does not exist!");
        return true;
    }

    // 사용자 데이터 수정
    this.luckPerms.getUserManager().modifyUser(player.getUniqueId(), (User user) -> {
        // 중요: 기존의 모든 상속 그룹 제거
        user.data().clear(NodeType.INHERITANCE::matches);

        // 새 그룹 추가
        Node node = InheritanceNode.builder(group).build();
        user.data().add(node);

        sender.sendMessage(ChatColor.GREEN + user.getUsername() + " is now in group " + group.getDisplayName());
    });

    return true;
}
```

### 우선순위 기반 Prefix 설정 (SetPrefixCommand)

상속받은 prefix보다 높은 우선순위로 설정하는 방법:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 2) {
        sender.sendMessage(ChatColor.RED + "Please specify a player & a prefix!");
        return true;
    }

    String playerName = args[0];
    String prefix = args[1];

    OfflinePlayer player = this.plugin.getServer().getOfflinePlayer(playerName);
    if (player == null || !player.hasPlayedBefore()) {
        sender.sendMessage(ChatColor.RED + playerName + " has never joined the server!");
        return true;
    }

    this.luckPerms.getUserManager().modifyUser(player.getUniqueId(), (User user) -> {
        // 기존 개인 prefix 제거
        user.data().clear(NodeType.PREFIX::matches);

        // 상속받은 prefix들의 최고 우선순위 찾기
        Map<Integer, String> inheritedPrefixes = user.getCachedData()
            .getMetaData(QueryOptions.nonContextual())
            .getPrefixes();
        
        // 상속받은 것보다 높은 우선순위 설정 (기본값은 10)
        int priority = inheritedPrefixes.keySet().stream()
            .mapToInt(i -> i + 10)
            .max()
            .orElse(10);

        // 새 prefix 노드 생성 및 추가
        Node node = PrefixNode.builder(prefix, priority).build();
        user.data().add(node);

        sender.sendMessage(ChatColor.GREEN + user.getUsername() + " now has the prefix " + ChatColor.RESET + prefix);
    });

    return true;
}
```

### 그룹 멤버 검색 (GroupMembersCommand)

NodeMatcher를 사용한 고급 검색:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length != 1) {
        sender.sendMessage(ChatColor.RED + "Please specify a group!");
        return true;
    }

    String groupName = args[0];

    Group group = this.luckPerms.getGroupManager().getGroup(groupName);
    if (group == null) {
        sender.sendMessage(ChatColor.RED + groupName + " does not exist!");
        return true;
    }

    // NodeMatcher 생성 - 특정 그룹 상속 노드와 매칭
    NodeMatcher<InheritanceNode> matcher = NodeMatcher.key(InheritanceNode.builder(group).build());

    // 모든 사용자에서 해당 그룹을 상속받는 사용자 검색
    this.luckPerms.getUserManager().searchAll(matcher).thenAccept((Map<UUID, Collection<InheritanceNode>> map) -> {
        Set<UUID> memberUniqueIds = map.keySet();

        sender.sendMessage(ChatColor.GREEN + group.getName() + " has " + memberUniqueIds.size() + " member(s).");
        sender.sendMessage(ChatColor.GREEN + memberUniqueIds.toString());
    });

    return true;
}
```

## 이벤트 시스템 실제 구현

### 노드 변경 통합 리스너 (PlayerNodeChangeListener)

다양한 노드 타입별 처리:

```java
public class PlayerNodeChangeListener {
    private final CookbookPlugin plugin;
    private final LuckPerms luckPerms;

    public void register() {
        EventBus eventBus = this.luckPerms.getEventBus();
        eventBus.subscribe(this.plugin, NodeAddEvent.class, this::onNodeAdd);
        eventBus.subscribe(this.plugin, NodeRemoveEvent.class, this::onNodeRemove);
    }

    private void onNodeAdd(NodeAddEvent e) {
        if (!e.isUser()) return;

        User target = (User) e.getTarget();
        Node node = e.getNode();

        // 중요: LuckPerms 이벤트는 비동기로 발생하므로 메인 스레드로 전환
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            Player player = this.plugin.getServer().getPlayer(target.getUniqueId());
            if (player == null) return; // 플레이어가 오프라인

            // 노드 타입별 처리
            if (node instanceof PermissionNode) {
                String permission = ((PermissionNode) node).getPermission();
                player.sendMessage(ChatColor.YELLOW + "You were given the " + permission + " permission!");

            } else if (node instanceof InheritanceNode) {
                String groupName = ((InheritanceNode) node).getGroupName();
                player.sendMessage(ChatColor.YELLOW + "You were added to the " + groupName + " group!");

            } else if (node instanceof PrefixNode) {
                String prefix = ((PrefixNode) node).getMetaValue();
                player.sendMessage(ChatColor.YELLOW + "You were given the " + prefix + " prefix!");

            } else if (node instanceof SuffixNode) {
                String suffix = ((SuffixNode) node).getMetaValue();
                player.sendMessage(ChatColor.YELLOW + "You were given the " + suffix + " suffix!");
            }
        });
    }

    private void onNodeRemove(NodeRemoveEvent e) {
        if (!e.isUser()) return;

        User target = (User) e.getTarget();
        Node node = e.getNode();

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            Player player = this.plugin.getServer().getPlayer(target.getUniqueId());
            if (player == null) return;

            if (node instanceof PermissionNode) {
                String permission = ((PermissionNode) node).getPermission();
                player.sendMessage(ChatColor.DARK_RED + "You no longer have the " + permission + " permission!");

            } else if (node instanceof InheritanceNode) {
                String groupName = ((InheritanceNode) node).getGroupName();
                player.sendMessage(ChatColor.DARK_RED + "You are no longer in the " + groupName + " group!");

            } else if (node instanceof PrefixNode) {
                String prefix = ((PrefixNode) node).getMetaValue();
                player.sendMessage(ChatColor.DARK_RED + "You no longer have the " + prefix + " prefix!");

            } else if (node instanceof SuffixNode) {
                String suffix = ((SuffixNode) node).getMetaValue();
                player.sendMessage(ChatColor.DARK_RED + "You no longer have the " + suffix + " suffix!");
            }
        });
    }
}
```

### 플레이어 첫 가입 및 닉네임 변경 감지

PlayerDataSaveEvent의 활용:

```java
public class PlayerFirstJoinListener {
    
    public void register() {
        EventBus eventBus = this.luckPerms.getEventBus();
        eventBus.subscribe(this.plugin, PlayerDataSaveEvent.class, this::onDataSave);
    }

    private void onDataSave(PlayerDataSaveEvent e) {
        PlayerSaveResult result = e.getResult();
        Set<Outcome> outcomes = result.getOutcomes();

        // 첫 가입 감지
        if (outcomes.contains(Outcome.CLEAN_INSERT)) {
            Bukkit.broadcastMessage(ChatColor.GREEN + e.getUsername() + " joined the network for the first time!");
            
            // 신규 플레이어에게 기본 권한 부여 로직 추가 가능
            UUID uuid = e.getUniqueId();
            // addDefaultPermissions(uuid);
        }

        // 닉네임 변경 감지
        String previousUsername = result.getPreviousUsername();
        if (previousUsername != null) {
            Bukkit.broadcastMessage(ChatColor.GREEN + previousUsername + " has updated their username to " + e.getUsername() + "!");
        }
    }
}
```

### 그룹별 이벤트 처리

그룹의 prefix 변경 감지:

```java
public class GroupPrefixChangedListener {
    
    public void register() {
        EventBus eventBus = this.luckPerms.getEventBus();
        eventBus.subscribe(this.plugin, NodeAddEvent.class, this::onNodeAdd);
        eventBus.subscribe(this.plugin, NodeRemoveEvent.class, this::onNodeRemove);
    }

    private void onNodeAdd(NodeAddEvent e) {
        // 그룹 대상 이벤트만 처리
        if (!e.isGroup()) return;

        Node node = e.getNode();
        if (node.getType() != NodeType.PREFIX) return;

        PrefixNode prefixNode = ((PrefixNode) node);
        Group group = (Group) e.getTarget();

        String newPrefix = prefixNode.getMetaValue();
        Bukkit.broadcastMessage(ChatColor.GREEN + "The prefix " + newPrefix + " was added to " + group.getName());
    }

    private void onNodeRemove(NodeRemoveEvent e) {
        if (!e.isGroup()) return;

        Node node = e.getNode();
        if (node.getType() != NodeType.PREFIX) return;

        PrefixNode prefixNode = ((PrefixNode) node);
        Group group = (Group) e.getTarget();

        String oldPrefix = prefixNode.getMetaValue();
        Bukkit.broadcastMessage(ChatColor.GREEN + "The prefix " + oldPrefix + " was removed from " + group.getName());
    }
}
```

### 간단한 로깅 시스템

개발 및 디버깅용 로깅:

```java
public class SimpleLoggingListener {
    
    public void register() {
        EventBus eventBus = this.luckPerms.getEventBus();
        eventBus.subscribe(this.plugin, UserLoadEvent.class, this::onUserLoad);
        eventBus.subscribe(this.plugin, NodeMutateEvent.class, this::onNodeMutate);
    }

    private void onUserLoad(UserLoadEvent e) {
        this.plugin.getLogger().info("UserLoadEvent fired! - " + e.getUser().getUniqueId());
    }

    private void onNodeMutate(NodeMutateEvent e) {
        this.plugin.getLogger().info("NodeMutateEvent fired! - " + e);
    }
}
```

## 실제 구현 예제

### 완전한 권한 관리 시스템

```java
public class PermissionManager {
    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    
    public PermissionManager(JavaPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        registerEvents();
    }
    
    private void registerEvents() {
        EventBus eventBus = luckPerms.getEventBus();
        eventBus.subscribe(plugin, NodeAddEvent.class, this::onPermissionAdd);
        eventBus.subscribe(plugin, PlayerDataSaveEvent.class, this::onPlayerJoin);
    }
    
    // 권한 추가 (임시/영구 지원)
    public CompletableFuture<Boolean> addPermission(UUID uuid, String permission, Duration duration) {
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            Node.Builder nodeBuilder = Node.builder(permission);
            
            if (duration != null) {
                nodeBuilder.expiry(Instant.now().plus(duration));
            }
            
            Node node = nodeBuilder.build();
            DataMutateResult result = user.data().add(node);
            
            return result.wasSuccessful();
        });
    }
    
    // 그룹 관리 (여러 그룹 지원)
    public CompletableFuture<Void> setPlayerGroups(UUID uuid, List<String> groupNames) {
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            // 기존 그룹 모두 제거
            user.data().clear(NodeType.INHERITANCE::matches);
            
            // 새 그룹들 추가
            for (String groupName : groupNames) {
                Group group = luckPerms.getGroupManager().getGroup(groupName);
                if (group != null) {
                    Node node = InheritanceNode.builder(group).build();
                    user.data().add(node);
                }
            }
        });
    }
    
    // 완전한 사용자 정보 조회
    public CompletableFuture<UserInfo> getUserInfo(UUID uuid) {
        return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
            CachedMetaData metaData = user.getCachedData().getMetaData();
            
            // 권한 목록
            Set<String> permissions = user.data().toCollection().stream()
                .filter(NodeType.PERMISSION::matches)
                .map(Node::getKey)
                .collect(Collectors.toSet());
            
            // 그룹 목록
            List<String> groups = user.getInheritedGroups(QueryOptions.nonContextual())
                .stream()
                .map(Group::getName)
                .collect(Collectors.toList());
            
            return new UserInfo(
                user.getUsername(),
                metaData.getPrefix(),
                metaData.getSuffix(),
                permissions,
                groups
            );
        });
    }
    
    private void onPermissionAdd(NodeAddEvent event) {
        if (!event.isUser() || event.getNode().getType() != NodeType.PERMISSION) {
            return;
        }
        
        User user = (User) event.getTarget();
        String permission = event.getNode().getKey();
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(user.getUniqueId());
            if (player != null) {
                player.sendMessage("§a권한 §f" + permission + "§a이 부여되었습니다!");
            }
        });
    }
    
    private void onPlayerJoin(PlayerDataSaveEvent event) {
        PlayerSaveResult result = event.getResult();
        
        // 첫 가입자 처리
        if (result.getOutcomes().contains(PlayerSaveResult.Outcome.CLEAN_INSERT)) {
            UUID uuid = event.getUniqueId();
            String username = event.getUsername();
            
            // 신규 플레이어에게 기본 그룹 부여
            setPlayerGroups(uuid, Arrays.asList("default", "newbie"))
                .thenRun(() -> {
                    plugin.getLogger().info("신규 플레이어 " + username + "에게 기본 권한을 부여했습니다.");
                });
        }
    }
    
    // 사용자 정보 데이터 클래스
    public static class UserInfo {
        private final String username;
        private final String prefix;
        private final String suffix;
        private final Set<String> permissions;
        private final List<String> groups;
        
        public UserInfo(String username, String prefix, String suffix, 
                       Set<String> permissions, List<String> groups) {
            this.username = username;
            this.prefix = prefix;
            this.suffix = suffix;
            this.permissions = permissions;
            this.groups = groups;
        }
        
        // getter 메소드들...
    }
}
```

### 명령어 구현 예제

```java
public class PermissionCommand implements CommandExecutor {
    private final PermissionManager permissionManager;
    
    public PermissionCommand(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /perm <user> <permission> [duration]");
            return true;
        }
        
        String username = args[0];
        String permission = args[1];
        Duration duration = null;
        
        if (args.length > 2) {
            try {
                duration = Duration.parse(args[2]); // PT1H (1시간), PT30M (30분) 등
            } catch (Exception e) {
                sender.sendMessage("§c잘못된 시간 형식입니다. 예: PT1H, PT30M");
                return true;
            }
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(username);
        if (!target.hasPlayedBefore()) {
            sender.sendMessage("§c해당 플레이어를 찾을 수 없습니다.");
            return true;
        }
        
        permissionManager.addPermission(target.getUniqueId(), permission, duration)
            .thenAccept(success -> {
                if (success) {
                    String message = duration != null ? 
                        "§a임시 권한 §f" + permission + "§a을 §f" + username + "§a에게 부여했습니다. (기간: " + duration + ")" :
                        "§a권한 §f" + permission + "§a을 §f" + username + "§a에게 부여했습니다.";
                    sender.sendMessage(message);
                } else {
                    sender.sendMessage("§c권한 부여에 실패했습니다. (이미 존재하는 권한)");
                }
            })
            .exceptionally(throwable -> {
                sender.sendMessage("§c권한 부여 중 오류가 발생했습니다: " + throwable.getMessage());
                return null;
            });
        
        return true;
    }
}
```

이 문서는 LuckPerms API의 주요 기능들을 실제 사용 가능한 예제와 함께 설명합니다. 각 예제는 api-cookbook 프로젝트의 실제 구현을 바탕으로 작성되었으며, 실제 플러그인 개발에서 바로 활용할 수 있습니다. 
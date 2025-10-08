# CustomFishing API Documentation v2.3.14

## Table of Contents
1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Core Concepts](#core-concepts)
4. [Manager APIs](#manager-apis)
5. [Events](#events)
6. [Integration System](#integration-system)
7. [Examples](#examples)

---

## Introduction

CustomFishing is a comprehensive Minecraft fishing plugin that provides a rich API for developers to create custom fishing mechanics, items, competitions, and more. This document provides detailed information about the CustomFishing API for AI assistants and developers.

**Package:** `net.momirealms.customfishing.api`
**Version:** 2.3.14
**Main Class:** `BukkitCustomFishingPlugin`

---

## Getting Started

### Accessing the Plugin Instance

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;

// Get the singleton instance
BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();
```

### Maven/Gradle Dependency

```xml
<dependency>
    <groupId>net.momirealms</groupId>
    <artifactId>CustomFishing</artifactId>
    <version>2.3.14</version>
    <scope>provided</scope>
</dependency>
```

### plugin.yml Dependency

```yaml
depend: [CustomFishing]
```

---

## Core Concepts

### 1. Context System

The Context system is the backbone of CustomFishing's mechanics. It stores and passes data between different components.

**Package:** `net.momirealms.customfishing.api.mechanic.context`

```java
// Create a player context
Context<Player> context = Context.player(player);

// Add arguments to context
context.arg(ContextKeys.LOOT, loot);
context.arg(ContextKeys.AMOUNT, 5);

// Retrieve arguments
Loot loot = context.arg(ContextKeys.LOOT);

// Get the holder (player)
Player player = context.holder();

// Combine contexts
context.combine(otherContext);

// Convert to placeholder map
Map<String, String> placeholders = context.placeholderMap();
```

**Key Classes:**
- `Context<T>`: Generic context interface
- `ContextKeys<?>`: Type-safe keys for storing data
- `PlayerContextImpl`: Implementation for player contexts

### 2. Actions

Actions are triggered operations that occur during fishing events.

**Package:** `net.momirealms.customfishing.api.mechanic.action`

```java
// Get the action manager
ActionManager<Player> actionManager = plugin.getActionManager();

// Register custom action
actionManager.registerAction(myActionFactory, "my_action_type");

// Parse actions from configuration
Action<Player>[] actions = actionManager.parseActions(configSection);

// Trigger actions
ActionManager.trigger(context, actions);

// Parse event-based actions
Map<ActionTrigger, Action<Player>[]> eventActions = actionManager.parseEventActions(section);
```

**ActionTrigger Enum Values:**
- `CAST`, `REEL`, `LAND`, `LOOT`, `BITE`, `ESCAPE`, `FAILURE`, `SUCCESS`, etc.

**Creating Custom Actions:**
```java
public class MyActionFactory implements ActionFactory<Player> {
    @Override
    public Action<Player> process(Object args, double chance) {
        return new MyAction(args, chance);
    }
}

public class MyAction implements Action<Player> {
    @Override
    public void trigger(Context<Player> context) {
        Player player = context.holder();
        // Your action logic here
    }
}
```

### 3. Requirements

Requirements are conditions that must be met for mechanics to work.

**Package:** `net.momirealms.customfishing.api.mechanic.requirement`

```java
// Get the requirement manager
RequirementManager<Player> requirementManager = plugin.getRequirementManager();

// Register custom requirement
requirementManager.registerRequirement(myRequirementFactory, "my_requirement_type");

// Parse requirements
Requirement<Player>[] requirements = requirementManager.parseRequirements(section, runActions);

// Check if requirements are satisfied
boolean satisfied = RequirementManager.isSatisfied(context, requirements);
```

**Creating Custom Requirements:**
```java
public class MyRequirementFactory implements RequirementFactory<Player> {
    @Override
    public Requirement<Player> process(Object args) {
        return new MyRequirement(args);
    }
}

public class MyRequirement implements Requirement<Player> {
    @Override
    public boolean isSatisfied(Context<Player> context) {
        Player player = context.holder();
        // Your requirement logic
        return true;
    }
}
```

### 4. Loot System

Loots are the items/rewards players can catch while fishing.

**Package:** `net.momirealms.customfishing.api.mechanic.loot`

```java
// Create a loot
Loot loot = Loot.builder()
    .id("my_loot")
    .nick("Custom Fish")
    .type(LootType.ITEM)
    .score(MathValue.plain(100))
    .disableGame(false)
    .build();

// Register loot
LootManager lootManager = plugin.getLootManager();
lootManager.registerLoot(loot);

// Get loot by ID
Optional<Loot> optionalLoot = lootManager.getLoot("my_loot");

// Get weighted loots
Map<String, Double> weightedLoots = lootManager.getWeightedLoots(effect, context);

// Get next loot based on effect
Loot nextLoot = lootManager.getNextLoot(effect, context);

// Get loot groups
List<String> groupMembers = lootManager.getGroupMembers("fish_group");
```

**LootType Enum:**
- `ITEM`: Regular item loot
- `BLOCK`: Block loot
- `ENTITY`: Entity loot

**Loot Properties:**
- `id()`: Unique identifier
- `nick()`: Display name
- `type()`: Loot type
- `score()`: Score value
- `lootGroup()`: Groups this loot belongs to
- `instantGame()`: Whether it triggers instant game
- `disableGame()`: Whether games are disabled
- `disableStats()`: Whether statistics are disabled
- `preventGrabbing()`: Whether players can't grab the loot

---

## Manager APIs

### ActionManager

Manages custom action types and action execution.

**Location:** `net.momirealms.customfishing.api.mechanic.action.ActionManager`

**Key Methods:**
```java
ActionManager<Player> manager = plugin.getActionManager();

// Register/Unregister actions
boolean registerAction(ActionFactory<T> factory, String... types);
boolean unregisterAction(String type);
boolean hasAction(String type);
ActionFactory<T> getActionFactory(String type);

// Parse actions from config
Action<T> parseAction(Section section);
Action<T>[] parseActions(Section section);
Action<T> parseAction(String type, Object args);

// Parse event-based actions
Map<ActionTrigger, Action<T>[]> parseEventActions(Section section);
TreeMap<Integer, Action<T>[]> parseTimesActions(Section section);

// Trigger actions
static void trigger(Context<T> context, List<Action<T>> actions);
static void trigger(Context<T> context, Action<T>[] actions);
```

### RequirementManager

Manages custom requirement types and requirement evaluation.

**Location:** `net.momirealms.customfishing.api.mechanic.requirement.RequirementManager`

**Key Methods:**
```java
RequirementManager<Player> manager = plugin.getRequirementManager();

// Register/Unregister requirements
boolean registerRequirement(RequirementFactory<T> factory, String... types);
boolean unregisterRequirement(String type);
boolean hasRequirement(String type);
RequirementFactory<T> getRequirementFactory(String type);

// Parse requirements
Requirement<T>[] parseRequirements(Section section, boolean runActions);
Requirement<T> parseRequirement(Section section, boolean runActions);
Requirement<T> parseRequirement(String type, Object value);

// Check satisfaction
static boolean isSatisfied(Context<T> context, Requirement<T>[] requirements);
static boolean isSatisfied(Context<T> context, List<Requirement<T>> requirements);
```

### LootManager

Manages loot registration and loot selection.

**Location:** `net.momirealms.customfishing.api.mechanic.loot.LootManager`

**Key Methods:**
```java
LootManager manager = plugin.getLootManager();

// Register loot
boolean registerLoot(Loot loot);
Collection<Loot> getRegisteredLoots();

// Get loot
Optional<Loot> getLoot(String key);
List<String> getGroupMembers(String groupKey);

// Loot selection
Map<String, Double> getWeightedLoots(Effect effect, Context<Player> context);
Loot getNextLoot(Effect effect, Context<Player> context);
```

### CompetitionManager

Manages fishing competitions.

**Location:** `net.momirealms.customfishing.api.mechanic.competition.CompetitionManager`

**Key Methods:**
```java
CompetitionManager manager = plugin.getCompetitionManager();

// Register competition
boolean registerCompetition(CompetitionConfig config);

// Start competition
boolean startCompetition(String name, boolean force, String serverGroup);
boolean startCompetition(CompetitionConfig config, boolean force, String serverGroup);

// Get competition info
FishingCompetition getOnGoingCompetition();
int getNextCompetitionInSeconds();
CompetitionConfig getCompetition(String key);
Collection<String> getCompetitionIDs();

// Player count
int onlinePlayerCountProvider();
void updatePlayerCount(UUID uuid, int count);
```

**CompetitionGoal Enum:**
- `MAX_SIZE`: Largest fish
- `TOTAL_SCORE`: Total score
- `TOTAL_SIZE`: Total size
- `MAX_SCORE`: Highest single score

### IntegrationManager

Manages integration with external plugins.

**Location:** `net.momirealms.customfishing.api.integration.IntegrationManager`

**Key Methods:**
```java
IntegrationManager manager = plugin.getIntegrationManager();

// Leveler providers (e.g., McMMO, AureliumSkills)
boolean registerLevelerProvider(LevelerProvider provider);
boolean unregisterLevelerProvider(String id);
LevelerProvider getLevelerProvider(String id);

// Enchantment providers
boolean registerEnchantmentProvider(EnchantmentProvider provider);
boolean unregisterEnchantmentProvider(String id);
EnchantmentProvider getEnchantmentProvider(String id);
List<Pair<String, Short>> getEnchantments(ItemStack itemStack);

// Season providers (e.g., RealisticSeasons)
boolean registerSeasonProvider(SeasonProvider provider);
boolean unregisterSeasonProvider();
SeasonProvider getSeasonProvider();

// Entity providers
boolean registerEntityProvider(EntityProvider provider);
boolean unregisterEntityProvider(String id);

// Item providers (e.g., ItemsAdder, Oraxen)
boolean registerItemProvider(ItemProvider provider);
boolean unregisterItemProvider(String id);

// Block providers
boolean registerBlockProvider(BlockProvider provider);
boolean unregisterBlockProvider(String id);
```

### StorageManager

Manages player data storage and retrieval.

**Location:** `net.momirealms.customfishing.api.storage.StorageManager`

**Key Methods:**
```java
StorageManager manager = plugin.getStorageManager();

// Server info
String getServerID();

// User data management
Optional<UserData> getOnlineUser(UUID uuid);
Collection<UserData> getOnlineUsers();
CompletableFuture<Optional<UserData>> getOfflineUserData(UUID uuid, boolean lock);
CompletableFuture<Boolean> saveUserData(UserData userData, boolean unlock);

// Data source
DataStorageProvider getDataSource();
boolean isRedisEnabled();

// Serialization
byte[] toBytes(PlayerData data);
String toJson(PlayerData data);
PlayerData fromJson(String json);
PlayerData fromBytes(byte[] data);
```

**StorageType Enum:**
- `MYSQL`, `MARIADB`, `POSTGRESQL`, `SQLITE`, `H2`, `MONGODB`

### ItemManager

Manages custom fishing items.

**Location:** `net.momirealms.customfishing.api.mechanic.item.ItemManager`

**Key Methods:**
```java
ItemManager manager = plugin.getItemManager();

// Register custom items
boolean registerItem(CustomFishingItem item);
Collection<String> getItemIDs();

// Build items
ItemStack build(Context<Player> context, CustomFishingItem item);
ItemStack buildAny(Context<Player> context, String id);

// Get item IDs
String getItemID(ItemStack itemStack);
String getCustomFishingItemID(ItemStack itemStack);

// Loot operations
ItemStack getItemLoot(Context<Player> context, ItemStack rod, FishHook hook);
Item dropItemLoot(Context<Player> context, ItemStack rod, FishHook hook);

// Durability management
boolean hasCustomMaxDamage(ItemStack itemStack);
int getMaxDamage(ItemStack itemStack);
void decreaseDamage(Player player, ItemStack itemStack, int amount);
void increaseDamage(Player player, ItemStack itemStack, int amount, boolean incorrectUsage);
void setDamage(Player player, ItemStack itemStack, int damage);

// Utilities
ItemFactory getFactory();
ItemProvider[] getItemProviders();
Item wrap(ItemStack itemStack);
```

### BagManager

Manages fishing bags (storage for caught fish).

**Location:** `net.momirealms.customfishing.api.mechanic.bag.BagManager`

**Key Methods:**
```java
BagManager manager = plugin.getBagManager();

// Open bag
CompletableFuture<Boolean> openBag(Player viewer, UUID owner);

// Static utility
int getBagInventoryRows(Player player); // Based on permissions
```

**Permissions:**
- `fishingbag.rows.1` to `fishingbag.rows.6`

### MarketManager

Manages the fishing market system.

**Location:** `net.momirealms.customfishing.api.mechanic.market.MarketManager`

**Key Methods:**
```java
MarketManager manager = plugin.getMarketManager();

// Open market
boolean openMarketGUI(Player player);

// Price calculation
double getItemPrice(Context<Player> context, ItemStack itemStack);
String getFormula();

// Earning limits
double earningLimit(Context<Player> context);
double earningsMultiplier(Context<Player> context);
```

### GameManager

Manages fishing mini-games.

**Location:** `net.momirealms.customfishing.api.mechanic.game.GameManager`

**Key Methods:**
```java
GameManager manager = plugin.getGameManager();

// Register game types
boolean registerGameType(String type, GameFactory factory);
boolean unregisterGameType(String type);
GameFactory getGameFactory(String type);

// Register game instances
boolean registerGame(Game game);
Optional<Game> getGame(String id);

// Game selection
Game getNextGame(Effect effect, Context<Player> context);
```

### TotemManager

Manages fishing totems (area effect boosters).

**Location:** `net.momirealms.customfishing.api.mechanic.totem.TotemManager`

**Key Methods:**
```java
TotemManager manager = plugin.getTotemManager();

// Register totem
boolean registerTotem(TotemConfig totem);

// Get totem info
Optional<TotemConfig> getTotem(String id);
Collection<String> getActivatedTotems(Location location);
```

### EffectManager

Manages effect modifiers for fishing mechanics.

**Location:** `net.momirealms.customfishing.api.mechanic.effect.EffectManager`

**Key Methods:**
```java
EffectManager manager = plugin.getEffectManager();

// Register effect modifiers
boolean registerEffectModifier(EffectModifier effect, MechanicType type);

// Get effect modifiers
Optional<EffectModifier> getEffectModifier(String id, MechanicType type);
```

**MechanicType Enum:**
- `ROD`, `BAIT`, `UTIL`, `HOOK`, `TOTEM`, `ENHANCEMENT`

### StatisticsManager

Manages player fishing statistics.

**Location:** `net.momirealms.customfishing.api.mechanic.statistic.StatisticsManager`

**Key Methods:**
```java
StatisticsManager manager = plugin.getStatisticsManager();

// Get player statistics
FishingStatistics getStatistics(UUID uuid);

// Statistic categories (via FishingStatistics interface)
// - getCatchAmount(StatisticsKeys key)
// - getCatchSize(StatisticsKeys key)
// - getCatchRecord(StatisticsKeys key)
```

### FishingManager

Manages core fishing mechanics.

**Location:** `net.momirealms.customfishing.api.mechanic.fishing.FishingManager`

Handles the main fishing mechanics including:
- Fishing hook states
- Anti-auto-fishing
- Bait animations
- Fishing gear management

### HookManager

Manages fishing hook configurations.

**Location:** `net.momirealms.customfishing.api.mechanic.hook.HookManager`

### BlockManager

Manages custom block mechanics for fishing.

**Location:** `net.momirealms.customfishing.api.mechanic.block.BlockManager`

**Key Methods:**
```java
BlockManager manager = plugin.getBlockManager();

// Register block configurations
boolean registerBlock(BlockConfig block);
Optional<BlockConfig> getBlock(String id);
```

### EntityManager

Manages custom entity mechanics for fishing.

**Location:** `net.momirealms.customfishing.api.mechanic.entity.EntityManager`

**Key Methods:**
```java
EntityManager manager = plugin.getEntityManager();

// Register entity configurations
boolean registerEntity(EntityConfig entity);
Optional<EntityConfig> getEntity(String id);
```

### EventManager

Manages custom fishing events.

**Location:** `net.momirealms.customfishing.api.mechanic.event.EventManager`

### ConfigManager

Manages configuration loading and parsing.

**Location:** `net.momirealms.customfishing.api.mechanic.config.ConfigManager`

### PlaceholderManager

Manages placeholder resolution for CustomFishing.

**Location:** `net.momirealms.customfishing.api.mechanic.misc.placeholder.PlaceholderManager`

Provides PlaceholderAPI integration and custom placeholder support.

### CoolDownManager

Manages cooldowns for fishing actions.

**Location:** `net.momirealms.customfishing.api.mechanic.misc.cooldown.CoolDownManager`

### HologramManager

Manages holograms for fishing displays.

**Location:** `net.momirealms.customfishing.api.mechanic.misc.hologram.HologramManager`

---

## Events

CustomFishing provides several Bukkit events for listening to fishing activities.

**Package:** `net.momirealms.customfishing.api.event`

### Available Events

#### FishingResultEvent
Triggered when a fishing result is determined.

```java
@EventHandler
public void onFishingResult(FishingResultEvent event) {
    Player player = event.getPlayer();
    Loot loot = event.getLoot();
    FishingResultEvent.Result result = event.getResult(); // SUCCESS or FAILURE
    FishHook hook = event.getFishHook();
    Context<Player> context = event.getContext();
    int amount = event.getAmount();

    // Modify score
    event.setScore(100.0);

    // Cancel event
    event.setCancelled(true);
}
```

#### RodCastEvent
Triggered when a player casts a fishing rod.

```java
@EventHandler
public void onRodCast(RodCastEvent event) {
    Player player = event.getPlayer();
    FishHook hook = event.getHook();
    // Event logic
}
```

#### FishingGameStartEvent
Triggered when a fishing mini-game starts.

```java
@EventHandler
public void onGameStart(FishingGameStartEvent event) {
    Player player = event.getPlayer();
    // Event logic
}
```

#### FishingGamePreStartEvent
Triggered before a fishing mini-game starts (cancellable).

```java
@EventHandler
public void onPreGameStart(FishingGamePreStartEvent event) {
    if (/* condition */) {
        event.setCancelled(true);
    }
}
```

#### FishingLootSpawnEvent
Triggered when loot is spawned.

```java
@EventHandler
public void onLootSpawn(FishingLootSpawnEvent event) {
    Player player = event.getPlayer();
    Loot loot = event.getLoot();
    Location location = event.getLocation();
}
```

#### FishingHookStateEvent
Triggered when fishing hook state changes.

```java
@EventHandler
public void onHookState(FishingHookStateEvent event) {
    Player player = event.getPlayer();
    // Handle hook state changes
}
```

#### FishingEffectApplyEvent
Triggered when fishing effects are applied.

```java
@EventHandler
public void onEffectApply(FishingEffectApplyEvent event) {
    Player player = event.getPlayer();
    Effect effect = event.getEffect();
}
```

#### FishingBagPreCollectEvent
Triggered before items are collected into the fishing bag (cancellable).

```java
@EventHandler
public void onBagCollect(FishingBagPreCollectEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    event.setCancelled(true);
}
```

#### CompetitionEvent
Triggered for competition-related events.

```java
@EventHandler
public void onCompetition(CompetitionEvent event) {
    FishingCompetition competition = event.getCompetition();
    // Handle competition events
}
```

#### TotemActivateEvent
Triggered when a totem is activated.

```java
@EventHandler
public void onTotemActivate(TotemActivateEvent event) {
    Location location = event.getLocation();
    TotemConfig totem = event.getTotem();
}
```

#### CustomFishingReloadEvent
Triggered when CustomFishing is reloaded.

```java
@EventHandler
public void onReload(CustomFishingReloadEvent event) {
    // Handle reload
}
```

---

## Integration System

CustomFishing provides a comprehensive integration system for external plugins.

### LevelerProvider

Integrate custom leveling systems.

```java
public interface LevelerProvider extends ExternalProvider {
    String id();
    void addXp(Player player, String target, double amount);
    int getLevel(Player player, String target);
}

// Register
plugin.getIntegrationManager().registerLevelerProvider(myLevelerProvider);
```

### EnchantmentProvider

Integrate custom enchantment systems.

```java
public interface EnchantmentProvider extends ExternalProvider {
    String id();
    List<Pair<String, Short>> getEnchantments(ItemStack itemStack);
}

// Register
plugin.getIntegrationManager().registerEnchantmentProvider(myEnchantProvider);
```

### SeasonProvider

Integrate season systems.

```java
public interface SeasonProvider extends ExternalProvider {
    String id();
    Season getSeason(Location location);
}

// Register
plugin.getIntegrationManager().registerSeasonProvider(mySeasonProvider);
```

### EntityProvider

Integrate custom entity systems.

```java
public interface EntityProvider extends ExternalProvider {
    String id();
    // Entity-related methods
}

// Register
plugin.getIntegrationManager().registerEntityProvider(myEntityProvider);
```

### ItemProvider

Integrate custom item plugins (like ItemsAdder, Oraxen).

```java
public interface ItemProvider extends ExternalProvider {
    String id();
    ItemStack buildItem(Context<Player> context, String id);
    String getItemID(ItemStack itemStack);
}

// Register
plugin.getIntegrationManager().registerItemProvider(myItemProvider);
```

### BlockProvider

Integrate custom block systems.

```java
public interface BlockProvider extends ExternalProvider {
    String id();
    // Block-related methods
}

// Register
plugin.getIntegrationManager().registerBlockProvider(myBlockProvider);
```

---

## Examples

### Example 1: Creating a Custom Loot

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootType;
import net.momirealms.customfishing.api.mechanic.misc.value.MathValue;

public class CustomLootExample {

    public void registerCustomLoot() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();

        Loot customLoot = Loot.builder()
            .id("legendary_fish")
            .nick("Legendary Golden Fish")
            .type(LootType.ITEM)
            .score(MathValue.plain(1000))
            .disableGame(false)
            .disableStatistics(false)
            .showInFinder(true)
            .groups(new String[]{"legendary", "rare_fish"})
            .build();

        plugin.getLootManager().registerLoot(customLoot);
    }
}
```

### Example 2: Creating a Custom Action

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.action.*;
import net.momirealms.customfishing.api.mechanic.context.Context;
import org.bukkit.entity.Player;

public class CustomActionExample {

    public void registerCustomAction() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();

        plugin.getActionManager().registerAction(
            new TeleportActionFactory(),
            "teleport"
        );
    }

    static class TeleportActionFactory implements ActionFactory<Player> {
        @Override
        public Action<Player> process(Object args, double chance) {
            return new TeleportAction((String) args, chance);
        }
    }

    static class TeleportAction implements Action<Player> {
        private final String worldName;
        private final double chance;

        public TeleportAction(String worldName, double chance) {
            this.worldName = worldName;
            this.chance = chance;
        }

        @Override
        public void trigger(Context<Player> context) {
            if (Math.random() > chance) return;

            Player player = context.holder();
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                player.teleport(world.getSpawnLocation());
                player.sendMessage("Teleported to " + worldName + "!");
            }
        }
    }
}
```

### Example 3: Creating a Custom Requirement

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.requirement.*;
import net.momirealms.customfishing.api.mechanic.context.Context;
import org.bukkit.entity.Player;

public class CustomRequirementExample {

    public void registerCustomRequirement() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();

        plugin.getRequirementManager().registerRequirement(
            new MinimumLevelRequirementFactory(),
            "min_level"
        );
    }

    static class MinimumLevelRequirementFactory implements RequirementFactory<Player> {
        @Override
        public Requirement<Player> process(Object args) {
            int minLevel = ((Number) args).intValue();
            return new MinimumLevelRequirement(minLevel);
        }
    }

    static class MinimumLevelRequirement implements Requirement<Player> {
        private final int minLevel;

        public MinimumLevelRequirement(int minLevel) {
            this.minLevel = minLevel;
        }

        @Override
        public boolean isSatisfied(Context<Player> context) {
            Player player = context.holder();
            return player.getLevel() >= minLevel;
        }
    }
}
```

### Example 4: Listening to Fishing Events

```java
import net.momirealms.customfishing.api.event.FishingResultEvent;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

public class FishingEventListener implements Listener {

    @EventHandler
    public void onFishingResult(FishingResultEvent event) {
        Player player = event.getPlayer();
        Loot loot = event.getLoot();

        if (event.getResult() == FishingResultEvent.Result.SUCCESS) {
            // Successful catch
            if (loot != null && loot.id().equals("legendary_fish")) {
                // Broadcast legendary fish catch
                Bukkit.broadcastMessage(player.getName() + " caught a legendary fish!");

                // Give bonus score
                event.setScore(event.getContext().arg(ContextKeys.SCORE) * 2);
            }
        } else {
            // Failed to catch
            player.sendMessage("The fish got away!");
        }
    }
}
```

### Example 5: Working with Storage

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.storage.StorageManager;
import net.momirealms.customfishing.api.storage.user.UserData;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class StorageExample {

    public void getPlayerData(UUID uuid) {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();
        StorageManager storage = plugin.getStorageManager();

        // Get online player data
        Optional<UserData> onlineData = storage.getOnlineUser(uuid);
        onlineData.ifPresent(data -> {
            // Work with online user data
        });

        // Get offline player data
        storage.getOfflineUserData(uuid, true).thenAccept(optionalData -> {
            optionalData.ifPresent(data -> {
                // Work with offline user data

                // Save changes
                storage.saveUserData(data, true);
            });
        });
    }
}
```

### Example 6: Opening Fishing Bag

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BagCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();

        // Open player's own bag
        plugin.getBagManager().openBag(player, player.getUniqueId())
            .thenAccept(success -> {
                if (!success) {
                    player.sendMessage("Failed to open fishing bag!");
                }
            });

        return true;
    }
}
```

### Example 7: Starting a Competition

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.competition.CompetitionManager;

public class CompetitionExample {

    public void startCompetition() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();
        CompetitionManager manager = plugin.getCompetitionManager();

        // Start a competition by name
        boolean started = manager.startCompetition("weekly_tournament", false, null);

        if (started) {
            Bukkit.broadcastMessage("Weekly fishing tournament has started!");
        }
    }
}
```

### Example 8: Registering a Custom Integration

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.integration.LevelerProvider;
import org.bukkit.entity.Player;

public class CustomLevelerIntegration {

    public void registerLeveler() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();

        plugin.getIntegrationManager().registerLevelerProvider(new LevelerProvider() {
            @Override
            public String id() {
                return "my_custom_leveler";
            }

            @Override
            public void addXp(Player player, String target, double amount) {
                // Add XP to player for the target skill
                // Integrate with your custom leveling system
            }

            @Override
            public int getLevel(Player player, String target) {
                // Get player's level for the target skill
                return 0; // Replace with actual level
            }
        });
    }
}
```

### Example 9: Working with Context

```java
import net.momirealms.customfishing.api.mechanic.context.Context;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import org.bukkit.entity.Player;

public class ContextExample {

    public void workWithContext(Player player, Loot loot) {
        // Create context
        Context<Player> context = Context.player(player);

        // Add data to context
        context.arg(ContextKeys.LOOT, loot);
        context.arg(ContextKeys.AMOUNT, 5);
        context.arg(ContextKeys.SCORE, 100.0);

        // Retrieve data from context
        Loot retrievedLoot = context.arg(ContextKeys.LOOT);
        Integer amount = context.arg(ContextKeys.AMOUNT);
        Double score = context.arg(ContextKeys.SCORE);

        // Get the holder
        Player holder = context.holder();

        // Convert to placeholders
        Map<String, String> placeholders = context.placeholderMap();

        // Combine with another context
        Context<Player> otherContext = Context.player(player);
        context.combine(otherContext);
    }
}
```

### Example 10: Custom Game Type

```java
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.game.*;
import net.momirealms.customfishing.api.mechanic.context.Context;
import org.bukkit.entity.Player;

public class CustomGameExample {

    public void registerCustomGame() {
        BukkitCustomFishingPlugin plugin = BukkitCustomFishingPlugin.getInstance();

        plugin.getGameManager().registerGameType("memory_game", new GameFactory() {
            @Override
            public Game create(Context<Player> context, GameBasics basics) {
                return new MemoryGame(context, basics);
            }
        });
    }

    static class MemoryGame implements Game {
        private final Context<Player> context;
        private final GameBasics basics;

        public MemoryGame(Context<Player> context, GameBasics basics) {
            this.context = context;
            this.basics = basics;
        }

        @Override
        public void start() {
            // Start the memory game
            Player player = context.holder();
            player.sendMessage("Memory game started! Memorize the pattern!");
        }

        @Override
        public void stop(boolean success) {
            // Stop the game
            Player player = context.holder();
            if (success) {
                player.sendMessage("You won the memory game!");
            } else {
                player.sendMessage("Game over!");
            }
        }

        // Implement other Game interface methods
    }
}
```

---

## Utility Classes

### MoonPhase Enum

**Location:** `net.momirealms.customfishing.api.util.MoonPhase`

Enum representing moon phases:
- `FULL_MOON`, `WANING_GIBBOUS`, `LAST_QUARTER`, `WANING_CRESCENT`
- `NEW_MOON`, `WAXING_CRESCENT`, `FIRST_QUARTER`, `WAXING_GIBBOUS`

### PlayerUtils

**Location:** `net.momirealms.customfishing.api.util.PlayerUtils`

Utility methods for player operations.

### InventoryUtils

**Location:** `net.momirealms.customfishing.api.util.InventoryUtils`

Utility methods for inventory operations.

### EventUtils

**Location:** `net.momirealms.customfishing.api.util.EventUtils`

Utility methods for event handling.

### TagUtils

**Location:** `net.momirealms.customfishing.api.util.TagUtils`

Utility methods for NBT tag operations.

### SimpleLocation

**Location:** `net.momirealms.customfishing.api.util.SimpleLocation`

Simplified location representation for storage.

---

## Best Practices

### 1. Always Use Context

Context is the backbone of CustomFishing mechanics. Always pass and use context when working with actions, requirements, and other mechanics.

```java
// Good
Context<Player> context = Context.player(player);
context.arg(ContextKeys.LOOT, loot);
action.trigger(context);

// Bad
// Passing null or creating new contexts unnecessarily
```

### 2. Check for Null

Many methods return `Optional` or nullable values. Always check before using.

```java
// Good
Optional<Loot> loot = lootManager.getLoot("fish_id");
loot.ifPresent(l -> {
    // Use loot
});

// Bad
Loot loot = lootManager.getLoot("fish_id").get(); // May throw NoSuchElementException
```

### 3. Use CompletableFuture Properly

Storage operations are asynchronous. Handle them correctly.

```java
// Good
storage.getOfflineUserData(uuid, true).thenAccept(data -> {
    // Handle on async thread
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Handle on main thread if needed
    });
});

// Bad
UserData data = storage.getOfflineUserData(uuid, true).join(); // Blocks the thread
```

### 4. Register Extensions Early

Register custom actions, requirements, and integrations during plugin enable.

```java
@Override
public void onEnable() {
    BukkitCustomFishingPlugin cfPlugin = BukkitCustomFishingPlugin.getInstance();
    cfPlugin.getActionManager().registerAction(myAction, "my_action");
    cfPlugin.getRequirementManager().registerRequirement(myReq, "my_requirement");
}
```

### 5. Unregister on Disable

Clean up your registrations when your plugin disables.

```java
@Override
public void onDisable() {
    BukkitCustomFishingPlugin cfPlugin = BukkitCustomFishingPlugin.getInstance();
    cfPlugin.getActionManager().unregisterAction("my_action");
    cfPlugin.getRequirementManager().unregisterRequirement("my_requirement");
}
```

---

## Common Patterns

### Pattern 1: Conditional Loot

```java
Requirement<Player>[] requirements = requirementManager.parseRequirements(configSection, false);
if (RequirementManager.isSatisfied(context, requirements)) {
    Loot loot = lootManager.getNextLoot(effect, context);
    // Give loot
}
```

### Pattern 2: Action Chains

```java
Map<ActionTrigger, Action<Player>[]> actions = actionManager.parseEventActions(section);
Action<Player>[] onSuccess = actions.get(ActionTrigger.SUCCESS);
ActionManager.trigger(context, onSuccess);
```

### Pattern 3: Data Persistence

```java
storage.getOnlineUser(uuid).ifPresent(userData -> {
    PlayerData playerData = userData.playerData();
    // Modify data
    storage.saveUserData(userData, false);
});
```

---

## Version Compatibility

- **Minecraft Version:** 1.17+ recommended
- **Java Version:** Java 8+
- **Spigot/Paper:** Paper recommended for best performance

---

## Support & Resources

- **GitHub:** [CustomFishing Repository](https://github.com/Xiao-MoMi/Custom-Fishing)
- **Discord:** Join the official CustomFishing Discord
- **Wiki:** Check the official wiki for configuration guides

---

## License

CustomFishing is licensed under the GNU General Public License v3.0.

```
Copyright (C) 2024 XiaoMoMi

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
any later version.
```

---

## Conclusion

This API documentation provides comprehensive information about CustomFishing's API. Use this guide to:

- Create custom fishing mechanics
- Integrate with external plugins
- Build custom loots, actions, and requirements
- Listen to fishing events
- Manage player data

For more examples and advanced usage, refer to the source code and configuration files.

**Happy Coding! 🎣**

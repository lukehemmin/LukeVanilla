package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.entity.Player

class FarmItemRestrictionListener(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager,
    private val debugManager: DebugManager
) : Listener {

    // 농사마을 아이템 목록
    private val RESTRICTED_FARM_ITEM_IDS = setOf(
        "dry_pot", "wet_pot", "soil_surveyor", "scarecrow", "crow_fly", "crow_stand",
        "greenhouse_glass",
        "watering_can_1", "watering_can_2", "watering_can_3", "watering_can_4",
        "hop_seeds", "hop", "hop_silver_star", "hop_golden_star",
        "tomato_seeds", "tomato", "golden_tomato", "tomato_silver_star", "tomato_golden_star", "gigantic_tomato",
        "redpacket_seeds", "redpacket",
        "pitaya_seeds", "pitaya", "pitaya_silver_star", "pitaya_golden_star",
        "pepper_seeds", "pepper", "pepper_silver_star", "pepper_golden_star",
        "grape_seeds", "grape", "grape_silver_star", "grape_golden_star",
        "pineapple_seeds", "pineapple", "pineapple_silver_star", "pineapple_golden_star", "gigantic_pineapple",
        "garlic_seeds", "garlic", "garlic_silver_star", "garlic_golden_star",
        "speed_grow_1", "speed_grow_2", "speed_grow_3",
        "soil_retain_1", "soil_retain_2", "soil_retain_3",
        "quality_1", "quality_2", "quality_3",
        "yield_increase_1", "yield_increase_2", "yield_increase_3",
        "variation_1", "variation_2", "variation_3",
        "eggplant_seeds", "eggplant", "eggplant_silver_star", "eggplant_golden_star"
    )

    private fun isFarmRestrictedItem(itemStack: ItemStack): Boolean {
        if (!itemStack.hasItemMeta()) return false
        val nexoId = NexoItems.idFromItem(itemStack)
        debugManager.log("FarmItemRestriction", "Checking item: ${itemStack.type}, Nexo ID: $nexoId")
        return nexoId != null && RESTRICTED_FARM_ITEM_IDS.contains(nexoId)
    }

    private fun sendMessage(player: Player, message: String) {
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    // --- 이벤트 핸들러 --- //

    // 1. 아이템 설치 제한
    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val item = event.itemInHand

        if (isFarmRestrictedItem(item)) {
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(event.blockPlaced.location)) {
                debugManager.log("FarmItemRestriction", "BlockPlaceEvent cancelled: ${player.name} tried to place ${item.type} outside farm plot.")
                sendMessage(player, "이 아이템은 농사마을 외부에서는 설치할 수 없습니다!")
                event.isCancelled = true
            }
        }
    }

    // 2. 인벤토리 클릭 (상자, 엔더상자 등) 제한
    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory
        val currentItem = event.currentItem // Item that was clicked
        val cursorItem = event.cursor // Item being held by cursor

        // No item involved
        if (currentItem == null && cursorItem == null) return

        val itemToCheck = if (event.isShiftClick) currentItem else cursorItem // Prioritize currentItem for shift-click
        if (itemToCheck == null || itemToCheck.type == Material.AIR) return
        
        if (!isFarmRestrictedItem(itemToCheck)) return

        // 엔더 상자 제한 (마을 내부/외부 불문)
        if (clickedInventory != null && clickedInventory.type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            debugManager.log("FarmItemRestriction", "InventoryClickEvent cancelled: ${player.name} tried to put ${itemToCheck.type} into Ender Chest.")
            sendMessage(player, "이 아이템은 엔더 상자에 넣을 수 없습니다!")
            event.isCancelled = true
            return
        }

        // 일반 상자, 화로 등에 넣기 제한 (마을 외부에서)
        if (clickedInventory != null && clickedInventory.type != org.bukkit.event.inventory.InventoryType.PLAYER && clickedInventory.type != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            val clickedLocation = clickedInventory.location
            if (clickedLocation != null && !farmVillageManager.isLocationWithinAnyClaimedFarmPlot(clickedLocation)) {
                // Check if the action is to put item into the inventory (e.g., PUT, SWAP_WITH_CURSOR, MOVE_TO_OTHER_INVENTORY)
                val isPuttingItem = when (event.action) {
                    InventoryClickEvent.Action.PLACE_ALL, 
                    InventoryClickEvent.Action.PLACE_ONE, 
                    InventoryClickEvent.Action.PLACE_SOME, 
                    InventoryClickEvent.Action.SWAP_WITH_CURSOR, 
                    InventoryClickEvent.Action.MOVE_TO_OTHER_INVENTORY -> true
                    else -> false
                }
                
                if (isPuttingItem) {
                    debugManager.log("FarmItemRestriction", "InventoryClickEvent cancelled: ${player.name} tried to put ${itemToCheck.type} into external inventory outside farm plot.")
                    sendMessage(player, "이 아이템은 농사마을 외부의 상자에 넣을 수 없습니다!")
                    event.isCancelled = true
                }
            }
        }
    }

    // 3. 인벤토리 드래그 제한 (상자, 엔더상자 등)
    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val draggedItem = event.oldCursor // Item being dragged
        if (draggedItem == null || draggedItem.type == Material.AIR) return
        if (!isFarmRestrictedItem(draggedItem)) return

        // Check affected slots (where the item is being dragged to)
        for (rawSlot in event.rawSlots) {
            val clickedInventory = event.view.getInventory(rawSlot)
            if (clickedInventory != null) {
                // 엔더 상자 제한
                if (clickedInventory.type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
                    debugManager.log("FarmItemRestriction", "InventoryDragEvent cancelled: ${player.name} tried to drag ${draggedItem.type} into Ender Chest.")
                    sendMessage(player, "이 아이템은 엔더 상자에 넣을 수 없습니다!")
                    event.isCancelled = true
                    return
                }
                
                // 일반 상자 등에 넣기 제한 (마을 외부에서)
                if (clickedInventory.type != org.bukkit.event.inventory.InventoryType.PLAYER && clickedInventory.type != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                    val clickedLocation = clickedInventory.location
                    if (clickedLocation != null && !farmVillageManager.isLocationWithinAnyClaimedFarmPlot(clickedLocation)) {
                        debugManager.log("FarmItemRestriction", "InventoryDragEvent cancelled: ${player.name} tried to drag ${draggedItem.type} into external inventory outside farm plot.")
                        sendMessage(player, "이 아이템은 농사마을 외부의 상자에 넣을 수 없습니다!")
                        event.isCancelled = true
                        return
                    }
                }
            }
        }
    }

    // 4. 아이템 드롭 제한
    @EventHandler(ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val item = event.itemDrop.itemStack

        if (isFarmRestrictedItem(item)) {
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(player.location)) {
                debugManager.log("FarmItemRestriction", "PlayerDropItemEvent cancelled: ${player.name} tried to drop ${item.type} outside farm plot.")
                sendMessage(player, "이 아이템은 농사마을 외부에서는 버릴 수 없습니다!")
                event.isCancelled = true
            }
        }
    }

    // 5. 자동 인벤토리 이동 제한 (호퍼, 디스펜서 등)
    @EventHandler(ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        val item = event.item
        if (!isFarmRestrictedItem(item)) return
        
        val sourceLocation = event.source.location
        val destinationLocation = event.destination.location

        // Prevent movement FROM outside TO inside, or FROM inside TO outside, or FROM outside TO outside
        // The rule is: if destination is NOT a farm plot, cancel.
        if (destinationLocation != null && !farmVillageManager.isLocationWithinAnyClaimedFarmPlot(destinationLocation)) {
            debugManager.log("FarmItemRestriction", "InventoryMoveItemEvent cancelled: Automatic movement of ${item.type} to non-farm plot location.")
            event.isCancelled = true
        }
    }
} 
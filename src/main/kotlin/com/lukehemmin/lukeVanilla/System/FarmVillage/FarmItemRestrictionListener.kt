package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack

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

    private fun isFarmRestrictedItem(itemStack: ItemStack?): Boolean {
        if (itemStack == null || !itemStack.hasItemMeta()) return false
        val nexoId = NexoItems.idFromItem(itemStack)
        debugManager.log("FarmItemRestriction", "Checking item: ${itemStack.type}, Nexo ID: $nexoId")
        return nexoId != null && RESTRICTED_FARM_ITEM_IDS.contains(nexoId)
    }

    private fun sendMessage(player: Player, message: String) {
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (isFarmRestrictedItem(event.itemInHand)) {
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(event.blockPlaced.location)) {
                debugManager.log("FarmItemRestriction", "BlockPlaceEvent cancelled: ${event.player.name} tried to place ${event.itemInHand.type} outside farm plot.")
                sendMessage(event.player, "이 아이템은 농사마을 외부에서는 설치할 수 없습니다!")
                event.isCancelled = true
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val itemToCheck = if (event.isShiftClick) event.currentItem else event.cursor
        if (!isFarmRestrictedItem(itemToCheck)) return

        val clickedInventory = event.clickedInventory
        
        if (clickedInventory?.type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            debugManager.log("FarmItemRestriction", "InventoryClickEvent cancelled: ${player.name} tried to put ${itemToCheck?.type} into Ender Chest.")
            sendMessage(player, "이 아이템은 엔더 상자에 넣을 수 없습니다!")
            event.isCancelled = true
            return
        }

        if (clickedInventory != null && clickedInventory.type != org.bukkit.event.inventory.InventoryType.PLAYER && clickedInventory.type != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            val clickedLocation = clickedInventory.location ?: return
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(clickedLocation)) {
                val isPuttingItem = when (event.action) {
                    InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE, InventoryAction.PLACE_SOME,
                    InventoryAction.SWAP_WITH_CURSOR, InventoryAction.MOVE_TO_OTHER_INVENTORY -> true
                    else -> false
                }
                if (isPuttingItem) {
                    debugManager.log("FarmItemRestriction", "InventoryClickEvent cancelled: ${player.name} tried to put ${itemToCheck?.type} into external inventory.")
                    sendMessage(player, "이 아이템은 농사마을 외부의 상자에 넣을 수 없습니다!")
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (!isFarmRestrictedItem(event.oldCursor)) return

        val player = event.whoClicked as? Player ?: return
        for (rawSlot in event.rawSlots) {
            val clickedInventory = event.view.getInventory(rawSlot) ?: continue
            
            if (clickedInventory.type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
                event.isCancelled = true
                sendMessage(player, "이 아이템은 엔더 상자에 넣을 수 없습니다!")
                return
            }
            
            val clickedLocation = clickedInventory.location ?: continue
            if (clickedInventory.type != org.bukkit.event.inventory.InventoryType.PLAYER && !farmVillageManager.isLocationWithinAnyClaimedFarmPlot(clickedLocation)) {
                event.isCancelled = true
                sendMessage(player, "이 아이템은 농사마을 외부의 상자에 넣을 수 없습니다!")
                return
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (isFarmRestrictedItem(event.itemDrop.itemStack)) {
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(event.player.location)) {
                sendMessage(event.player, "이 아이템은 농사마을 외부에서는 버릴 수 없습니다!")
                event.isCancelled = true
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        if (isFarmRestrictedItem(event.item)) {
            val destinationLocation = event.destination.location ?: return
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(destinationLocation)) {
                event.isCancelled = true
            }
        }
    }
}
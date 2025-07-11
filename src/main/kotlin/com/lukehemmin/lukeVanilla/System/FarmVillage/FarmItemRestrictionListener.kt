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
import org.bukkit.inventory.Inventory

class FarmItemRestrictionListener(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager,
    private val debugManager: DebugManager
) : Listener {

    // 농사마을 아이템 목록
    private val RESTRICTED_FARM_ITEM_IDS = setOf(
        // 작물 관련
        "cabbage_seeds", "cabbage", "cabbage_silver_star", "cabbage_golden_star", "gigantic_cabbage",
        "chinese_cabbage_seeds", "chinese_cabbage", "chinese_cabbage_silver_star", "chinese_cabbage_golden_star",
        "corn_seeds", "corn", "corn_silver_star", "corn_golden_star",
        "eggplant_seeds", "eggplant", "eggplant_silver_star", "eggplant_golden_star",
        "garlic_seeds", "garlic", "garlic_silver_star", "garlic_golden_star",
        "grape_seeds", "grape", "grape_silver_star", "grape_golden_star",
        "hop_seeds", "hop", "hop_silver_star", "hop_golden_star",
        "pepper_seeds", "pepper", "pepper_silver_star", "pepper_golden_star",
        "pineapple_seeds", "pineapple", "pineapple_silver_star", "pineapple_golden_star", "gigantic_pineapple",
        "pitaya_seeds", "pitaya", "pitaya_silver_star", "pitaya_golden_star",
        "redpacket_seeds", "redpacket", "redpacket_silver_star", "redpacket_golden_star",
        "tomato_seeds", "tomato", "golden_tomato", "tomato_silver_star", "tomato_golden_star", "gigantic_tomato",

        // 비료
        "speed_grow_1", "speed_grow_2", "speed_grow_3",
        "soil_retain_1", "soil_retain_2", "soil_retain_3",
        "quality_1", "quality_2", "quality_3",
        "yield_increase_1", "yield_increase_2", "yield_increase_3",
        "variation_1", "variation_2", "variation_3",

        // 농사 도구 및 시설
        "dry_pot", "wet_pot",
        "soil_surveyor", "scarecrow", "crow_fly", "crow_stand", "greenhouse_glass",
        "sprinkler_1", "sprinkler_1_item", "sprinkler_2", "sprinkler_2_item", "sprinkler_3", "sprinkler_3_item", "water_effect",
        "watering_can_1", "watering_can_2", "watering_can_3", "watering_can_4"
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

        // --- 아이템을 '넣는' 행위인지, '꺼내는' 행위인지 명확히 구분하여 처리 ---
        // 아이템을 '꺼내는' 행위(예: PICKUP_ALL, PICKUP_HALF)는 어떠한 경우에도 제한하지 않습니다.
        // 오직 아이템을 제한된 보관함에 '넣는' 행위만 선별하여 차단합니다.
        
        val action = event.action

        // 시나리오 1: 커서에 아이템을 들고 특정 슬롯에 '넣는' 경우
        if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME) {
            val placedItem = event.cursor
            if (isFarmRestrictedItem(placedItem)) {
                if (isRestrictedDestination(player, event.clickedInventory)) {
                    cancelAndSendMessage(event, player, event.clickedInventory)
                }
            }
        }

        // 시나리오 2: 아이템을 맞바꾸는 경우 (커서 아이템을 '넣고' 슬롯 아이템을 '꺼냄')
        if (action == InventoryAction.SWAP_WITH_CURSOR) {
            val placedItem = event.cursor // 인벤토리에 '들어갈' 아이템
            if (isFarmRestrictedItem(placedItem)) {
                if (isRestrictedDestination(player, event.clickedInventory)) {
                    cancelAndSendMessage(event, player, event.clickedInventory)
                }
            }
            // 이 로직은 들어갈 아이템만 검사하므로, 슬롯에서 꺼내지는 아이템은 제한 없이 가져올 수 있습니다.
        }

        // 시나리오 3: 쉬프트 클릭으로 인벤토리 간에 아이템을 '이동'하는 경우
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            val movedItem = event.currentItem
            if (isFarmRestrictedItem(movedItem)) {
                // 아이템이 '들어갈' 목적지 인벤토리를 정확히 계산합니다.
                val destinationInventory = if (event.clickedInventory === event.view.topInventory) {
                    event.view.bottomInventory // 상자 -> 플레이어 인벤토리 (꺼내는 행위이므로 제한 안함)
                } else {
                    event.view.topInventory // 플레이어 인벤토리 -> 상자 (넣는 행위이므로 제한 대상)
                }

                // 목적지가 제한된 장소일 경우에만 이벤트를 취소합니다.
                if (isRestrictedDestination(player, destinationInventory)) {
                    cancelAndSendMessage(event, player, destinationInventory)
                }
            }
        }
    }

    /**
     * 해당 인벤토리가 농사 아이템을 '넣을 수 없는' 제한된 장소인지 확인합니다.
     * @param player 작업을 수행하는 플레이어
     * @param inventory 아이템이 들어갈 목적지 인벤토리
     * @return 제한된 장소이면 true, 아니면 false
     */
    private fun isRestrictedDestination(player: Player, inventory: Inventory?): Boolean {
        if (inventory == null) return false

        // 엔더 상자 또는 셜커 상자는 항상 제한된 목적지입니다.
        if (inventory.type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST || inventory.type == org.bukkit.event.inventory.InventoryType.SHULKER_BOX) {
            return true
        }

        // 농사마을 외부의 외부 보관함은 제한된 목적지입니다. (플레이어 인벤토리, 제작창은 제외)
        if (inventory.type != org.bukkit.event.inventory.InventoryType.PLAYER && inventory.type != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            val location = inventory.location ?: return false // 위치가 없는 커스텀 GUI 등은 제외
            if (!farmVillageManager.isLocationWithinAnyClaimedFarmPlot(location)) {
                return true
            }
        }

        // 위의 조건에 해당하지 않으면 제한된 목적지가 아님
        return false
    }

    /**
     * 이벤트를 취소하고, 상황에 맞는 메시지를 플레이어에게 보냅니다.
     */
    private fun cancelAndSendMessage(event: InventoryClickEvent, player: Player, destination: Inventory?) {
        event.isCancelled = true
        val message = when (destination?.type) {
            org.bukkit.event.inventory.InventoryType.ENDER_CHEST -> "이 아이템은 엔더 상자에 넣을 수 없습니다!"
            org.bukkit.event.inventory.InventoryType.SHULKER_BOX -> "이 아이템은 셜커 상자에 넣을 수 없습니다!"
            else -> "이 아이템은 농사마을 외부의 상자에 넣을 수 없습니다!"
        }
        sendMessage(player, message)
        debugManager.log("FarmItemRestriction", "InventoryClickEvent cancelled: ${player.name} tried to move item into restricted inventory.")
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        // 드래그하는 아이템이 제한 대상이 아니면 로직 종료
        if (!isFarmRestrictedItem(event.oldCursor)) return

        val player = event.whoClicked as? Player ?: return

        // 드래그로 아이템이 놓이는 모든 슬롯에 대해 검사
        for (rawSlot in event.rawSlots) {
            // 슬롯이 인벤토리의 일부가 아니면 건너뜁니다 (예: GUI 외부).
            val view = event.view
            if (rawSlot >= view.countSlots()) continue

            val destinationInventory = view.getInventory(rawSlot)

            // 아이템이 들어갈 목적지가 제한된 장소인지 확인
            if (isRestrictedDestination(player, destinationInventory)) {
                event.isCancelled = true
                val message = when (destinationInventory?.type) {
                    org.bukkit.event.inventory.InventoryType.ENDER_CHEST -> "이 아이템은 엔더 상자에 넣을 수 없습니다!"
                    org.bukkit.event.inventory.InventoryType.SHULKER_BOX -> "이 아이템은 셜커 상자에 넣을 수 없습니다!"
                    else -> "이 아이템은 농사마을 외부의 상자에 넣을 수 없습니다!"
                }
                sendMessage(player, message)
                debugManager.log("FarmItemRestriction", "InventoryDragEvent cancelled: ${player.name} tried to drag item into restricted inventory.")
                return // 하나의 슬롯이라도 제한되면 전체 드래그를 취소하고 종료
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
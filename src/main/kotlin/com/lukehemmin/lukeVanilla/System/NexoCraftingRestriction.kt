package com.lukehemmin.lukeVanilla.System

import com.nexomc.nexo.api.NexoItems
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class NexoCraftingRestriction(private val plugin: JavaPlugin) : Listener {

    // 제한된 Nexo 아이템 ID 목록
    private val restrictedNexoItems = listOf(
        "axolotl_decoration",
        "crystalstick",
        "crystalsticksward",
        "daisywand",
        "dragontrident",
        "drill",
        "fogsword",
        "rabit",
        "rabit_pix",
        "sunflower_sword",
        "superpix",
        "wood_spatula",
        "experience_stick",
        "halloween_lentern",
        "halloween_sword",
        "halloween_pickaxe",
        "halloween_axe",
        "halloween_shovel",
        "halloween_hoe",
        "halloween_bow",
        "halloween_fishing_rod",
        "halloween_hammer",
        "halloween_scythe",
        "halloween_spear",
        "merry_christmas_sword",
        "merry_christmas_greatsword",
        "merry_christmas_pickaxe",
        "merry_christmas_axe",
        "merry_christmas_shovel",
        "merry_christmas_hoe",
        "merry_christmas_bow",
        "merry_christmas_crossbow",
        "merry_christmas_fishing_rod",
        "merry_christmas_hammer",
        "merry_christmas_scythe",
        "merry_christmas_shield",
        "merry_christmas_backpack",
        "merry_christmas_spear",
        "merrychristmas_helmet",
        "merrychristmas_chestplate",
        "merrychristmas_leggings",
        "merrychristmas_boots"
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
//        "",
        // 필요한 만큼 아이템 ID 추가
    )

    // 작업대 사용 권한이 있는 플레이어 목록
    private val allowedPlayers = mutableSetOf<UUID>()

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (event.view.topInventory.type in listOf(InventoryType.CRAFTING, InventoryType.WORKBENCH)) {
            // SHIFT + 클릭 처리
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                if (isRestrictedNexoItem(event.currentItem) && !allowedPlayers.contains(player.uniqueId)) {
                    event.isCancelled = true
                    notifyPlayer(player)
                    return
                }
            }

            // 일반 클릭 처리
            if (isRestrictedNexoItem(event.cursor) && !allowedPlayers.contains(player.uniqueId)) {
                if (event.rawSlot < event.view.topInventory.size) {
                    event.isCancelled = true
                    notifyPlayer(player)
                }
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val topInventory = event.view.topInventory

        if (topInventory.type in listOf(InventoryType.CRAFTING, InventoryType.WORKBENCH)) {
            if (isRestrictedNexoItem(event.oldCursor) && !allowedPlayers.contains(player.uniqueId)) {
                val hasWorkbenchSlot = event.rawSlots.any { it < topInventory.size }
                if (hasWorkbenchSlot) {
                    event.isCancelled = true
                    notifyPlayer(player)
                }
            }
        }
    }

    @EventHandler
    fun onPrepareItemCraft(event: PrepareItemCraftEvent) {
        val player = event.view.player as? Player ?: return

        event.inventory.matrix.forEach { item ->
            if (isRestrictedNexoItem(item) && !allowedPlayers.contains(player.uniqueId)) {
                event.inventory.result = null
                notifyPlayer(player)
                return
            }
        }
    }

    private fun isRestrictedNexoItem(item: ItemStack?): Boolean {
        if (item == null) return false
        val nexoItemId = NexoItems.idFromItem(item)
        return nexoItemId != null && restrictedNexoItems.contains(nexoItemId)
    }

    private fun notifyPlayer(player: Player) {
        player.sendMessage("§c이 아이템은 작업대에서 사용할 수 없습니다.")
        player.sendMessage("§e/craftallow 명령어로 사용 권한을 얻을 수 있습니다.")
    }

    fun allowPlayer(player: Player) {
        allowedPlayers.add(player.uniqueId)
        player.sendMessage("§a작업대에서 제한된 커스텀 아이템을 사용할 수 있습니다.")

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            disallowPlayer(player)
        }, 20L * 60 * 5)
    }

    private fun disallowPlayer(player: Player) {
        allowedPlayers.remove(player.uniqueId)
        if (player.isOnline) {
            player.sendMessage("§c커스텀 아이템을 더이상 작업대에 넣을 수 없습니다. /craftallow 를 입력하여 다시 활성화 하세요.")
        }
    }
}
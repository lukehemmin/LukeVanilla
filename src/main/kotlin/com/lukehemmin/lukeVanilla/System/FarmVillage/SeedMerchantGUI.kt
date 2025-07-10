package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class SeedMerchantGUI(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : Listener {

    private val inventoryTitle = "씨앗 상인"
    private val seedSlotMap = mapOf(
        11 to "cabbage_seeds",
        13 to "chinese_cabbage_seeds",
        15 to "garlic_seeds",
        29 to "corn_seeds",
        31 to "pineapple_seeds",
        33 to "eggplant_seeds"
    )

    fun open(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, Component.text(inventoryTitle))
        updateGuiItems(player, inventory)
        player.openInventory(inventory)
    }
    
    private fun updateGuiItems(player: Player, inventory: Inventory) {
        // Fill with black glass panes
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ")) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
        }

        // Place seed items with updated lore
        seedSlotMap.forEach { (slot, seedId) ->
            val seedItem = NexoItems.itemFromId(seedId)?.build() ?: return@forEach
            val canTrade = farmVillageManager.canTradeSeed(player, seedId)
            
            seedItem.editMeta { meta ->
                val lore = meta.lore() ?: mutableListOf()
                lore.add(Component.text(" "))
                lore.add(Component.text("교환 재료: ", NamedTextColor.GRAY).append(Component.text("다이아몬드 1개", NamedTextColor.AQUA)))
                lore.add(Component.text("지급 아이템: ", NamedTextColor.GRAY).append(Component.text("${seedItem.itemMeta.displayName} 4개", NamedTextColor.WHITE)))
                lore.add(Component.text(" "))
                if (canTrade) {
                    lore.add(Component.text("[클릭하여 교환]", NamedTextColor.GREEN, TextDecoration.BOLD))
                } else {
                    lore.add(Component.text("[오늘 교환 완료]", NamedTextColor.RED, TextDecoration.BOLD))
                }
                meta.lore(lore)
            }
            inventory.setItem(slot, seedItem)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text(inventoryTitle)) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedSlot = event.rawSlot

        val seedId = seedSlotMap[clickedSlot] ?: return

        if (!farmVillageManager.canTradeSeed(player, seedId)) {
            player.sendMessage(Component.text("이 씨앗은 오늘 이미 교환했습니다.", NamedTextColor.RED))
            return
        }

        val diamond = ItemStack(Material.DIAMOND)
        if (player.inventory.containsAtLeast(diamond, 1)) {
            player.inventory.removeItem(diamond)

            val seedItem = NexoItems.itemFromId(seedId)?.build() ?: return
            seedItem.amount = 4
            player.inventory.addItem(seedItem)

            farmVillageManager.recordSeedTrade(player, seedId)

            player.sendMessage(Component.text("씨앗을 성공적으로 교환했습니다!", NamedTextColor.GREEN))
            // Update the GUI to reflect the change
            updateGuiItems(player, event.inventory)
        } else {
            player.sendMessage(Component.text("교환에 필요한 다이아몬드가 부족합니다.", NamedTextColor.RED))
        }
    }
} 
package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class ExchangeMerchantGUI(private val plugin: Main) : Listener {

    private val mainGuiTitle = "교환 상인"
    private val silverGuiTitle = "교환상점 - 은별작물 교환"
    private val goldenGuiTitle = "교환상점 - 금별작물 교환"

    private val activeAnimators = mutableMapOf<UUID, BukkitTask>()
    private val animationIndexes = mutableMapOf<UUID, Triple<Int, Int, Int>>()

    private val silverStarItems = listOf("cabbage_silver_star", "chinese_cabbage_silver_star", "garlic_silver_star", "corn_silver_star", "pineapple_silver_star", "eggplant_silver_star")
    private val goldenStarItems = listOf("cabbage_golden_star", "chinese_cabbage_golden_star", "garlic_golden_star", "corn_golden_star", "pineapple_golden_star", "eggplant_golden_star")
    private val scrollItems = listOf("h_sword_scroll", "c_sword_scroll", "v_sword_scroll")

    fun openMainGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, Component.text(mainGuiTitle))
        fillWithBlackGlass(inventory)
        player.openInventory(inventory)
        startAnimation(player, inventory)
    }

    private fun openSilverGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, Component.text(silverGuiTitle))
        fillWithBlackGlass(inventory)
        inventory.setItem(11, NexoItems.itemFromId("cabbage_silver_star")?.build())
        inventory.setItem(13, NexoItems.itemFromId("chinese_cabbage_silver_star")?.build())
        inventory.setItem(15, NexoItems.itemFromId("garlic_silver_star")?.build())
        inventory.setItem(29, NexoItems.itemFromId("corn_silver_star")?.build())
        inventory.setItem(31, NexoItems.itemFromId("pineapple_silver_star")?.build())
        inventory.setItem(33, NexoItems.itemFromId("eggplant_silver_star")?.build())
        player.openInventory(inventory)
    }

    private fun openGoldenGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, Component.text(goldenGuiTitle))
        fillWithBlackGlass(inventory)
        inventory.setItem(11, NexoItems.itemFromId("cabbage_golden_star")?.build())
        inventory.setItem(13, NexoItems.itemFromId("chinese_cabbage_golden_star")?.build())
        inventory.setItem(15, NexoItems.itemFromId("garlic_golden_star")?.build())
        inventory.setItem(29, NexoItems.itemFromId("corn_golden_star")?.build())
        inventory.setItem(31, NexoItems.itemFromId("pineapple_golden_star")?.build())
        inventory.setItem(33, NexoItems.itemFromId("eggplant_golden_star")?.build())
        player.openInventory(inventory)
    }

    private fun startAnimation(player: Player, inventory: Inventory) {
        animationIndexes[player.uniqueId] = Triple(0, 0, 0)
        activeAnimators[player.uniqueId] = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val (silverIndex, goldenIndex, scrollIndex) = animationIndexes[player.uniqueId] ?: return@Runnable

            inventory.setItem(20, NexoItems.itemFromId(silverStarItems[silverIndex])?.build())
            inventory.setItem(22, NexoItems.itemFromId(goldenStarItems[goldenIndex])?.build())
            inventory.setItem(24, NexoItems.itemFromId(scrollItems[scrollIndex])?.build())

            animationIndexes[player.uniqueId] = Triple(
                (silverIndex + 1) % silverStarItems.size,
                (goldenIndex + 1) % goldenStarItems.size,
                (scrollIndex + 1) % scrollItems.size
            )
        }, 0L, 40L) // 0 tick delay, 40 tick (2 second) period
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        when (event.view.title()) {
            mainGuiTitle -> {
                event.isCancelled = true
                when (event.rawSlot) {
                    20 -> openSilverGui(player)
                    22 -> openGoldenGui(player)
                    24 -> {
                        player.sendMessage(Component.text("구현 준비중입니다.", NamedTextColor.YELLOW))
                        // TODO: 교환상점 - 커스텀 아이템 교환 GUI 구현해야함
                    }
                }
            }
            silverGuiTitle, goldenGuiTitle -> {
                event.isCancelled = true
                // TODO: Implement exchange logic
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == mainGuiTitle) {
            val player = event.player as? Player ?: return
            activeAnimators.remove(player.uniqueId)?.cancel()
            animationIndexes.remove(player.uniqueId)
        }
    }

    private fun fillWithBlackGlass(inventory: Inventory) {
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ")) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
        }
    }
} 
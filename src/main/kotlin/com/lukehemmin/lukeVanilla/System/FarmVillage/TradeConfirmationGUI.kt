package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class TradeConfirmationGUI(private val plugin: Main) : Listener {

    private val guiTitle = Component.text("거래 확인")
    private val pendingTrades = mutableMapOf<UUID, () -> Unit>()

    fun open(player: Player, rewardItem: ItemStack, costItemsDisplay: ItemStack, onConfirm: () -> Unit) {
        val inventory = Bukkit.createInventory(player, 27, guiTitle)

        // Layout
        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, background)
        }

        val arrow = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("→", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
            }
        }

        val confirmButton = ItemStack(Material.GREEN_WOOL).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("거래 확인", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                lore(listOf(Component.text("이 아이템들을 교환합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            }
        }
        val cancelButton = ItemStack(Material.RED_WOOL).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("거래 취소", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                lore(listOf(Component.text("이전 화면으로 돌아갑니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            }
        }

        inventory.setItem(11, costItemsDisplay)
        inventory.setItem(13, arrow)
        inventory.setItem(15, rewardItem)
        inventory.setItem(22, confirmButton)
        inventory.setItem(26, cancelButton)
        
        pendingTrades[player.uniqueId] = onConfirm
        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != guiTitle) return
        
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        when (event.rawSlot) {
            22 -> { // Confirm
                pendingTrades[player.uniqueId]?.invoke()
                player.closeInventory()
            }
            26 -> { // Cancel
                player.closeInventory() // This will trigger onInventoryClose to clean up
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == guiTitle) {
            pendingTrades.remove(event.player.uniqueId)
        }
    }
} 
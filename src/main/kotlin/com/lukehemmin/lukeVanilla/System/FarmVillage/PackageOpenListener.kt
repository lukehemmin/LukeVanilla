package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class PackageOpenListener(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : Listener {

    private val packageItemId = "farmvillage_storage_chest"

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // We only care about right-clicks (air or block)
        if (!event.action.isRightClick) {
            return
        }

        // Check if the event is for the main hand to avoid double-firing
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        val player = event.player
        val itemInHand = player.inventory.itemInMainHand

        // Check if the player is holding the package item
        val nexoId = NexoItems.idFromItem(itemInHand)
        if (nexoId != packageItemId) {
            return
        }

        event.isCancelled = true // Prevent any default action

        // Consume one package item
        itemInHand.amount -= 1

        // Give the configured package items
        val givenItemsCount = farmVillageManager.giveJoinPackageContents(player)

        if (givenItemsCount > 0) {
            player.sendMessage(Component.text("입주 패키지를 열어 내용물을 수령했습니다!", NamedTextColor.GREEN))
        } else {
            // This case might happen if the package is empty.
            player.sendMessage(Component.text("입주 패키지가 비어있습니다. 관리자에게 문의해주세요.", NamedTextColor.YELLOW))
        }
    }
} 
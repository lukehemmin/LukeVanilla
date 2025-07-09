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

        // 패키지를 열기 전에 내용물이 있는지 먼저 확인합니다.
        if (!farmVillageManager.hasPackageContents()) {
            player.sendMessage(Component.text("아직 관리자가 아이템을 지정하지 않았습니다.", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("다음에 다시 열어주세요.", NamedTextColor.YELLOW))
            return // 아이템을 소모하지 않고 이벤트를 종료합니다.
        }

        // 내용물이 확인되었으므로 패키지 아이템을 하나 소모합니다.
        itemInHand.amount -= 1

        // 설정된 패키지 아이템들을 지급합니다.
        farmVillageManager.giveJoinPackageContents(player)
        player.sendMessage(Component.text("입주 패키지를 열어 내용물을 수령했습니다!", NamedTextColor.GREEN))
    }
} 
package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Container
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class ChestProtectionListener(
    private val farmVillageManager: FarmVillageManager,
    private val debugManager: DebugManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val clickedBlock = event.clickedBlock ?: return
        val player = event.player

        // 보관함(상자, 통, 용광로 등) 블록인지 확인합니다.
        if (clickedBlock.state !is Container) {
            return
        }

        val location = clickedBlock.location

        // 해당 위치가 농사마을 부지이고, 소유자가 있는지 확인합니다.
        val ownerUUID = farmVillageManager.getFarmPlotOwner(location)

        if (ownerUUID != null) {
            // 소유가 된 농사마을 부지이지만, 현재 플레이어가 소유주가 아닌 경우
            if (player.uniqueId != ownerUUID) {
                event.isCancelled = true
                player.sendMessage(Component.text("자신의 농사마을 땅에 있는 보관함만 열 수 있습니다.", NamedTextColor.RED))
                debugManager.log(
                    "ChestProtection",
                    "Player ${player.name} was blocked from opening a container at ${location.blockX},${location.blockY},${location.blockZ}. Plot owner: $ownerUUID"
                )
            }
        }
    }
} 
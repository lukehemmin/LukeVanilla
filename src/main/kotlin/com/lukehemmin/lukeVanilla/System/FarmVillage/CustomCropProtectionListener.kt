package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.momirealms.customcrops.api.event.CropBreakEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * CustomCrops 작물 보호 리스너.
 * 농사마을 내에서 다른 플레이어의 작물을 수확하는 것을 방지합니다.
 */
class CustomCropProtectionListener(
    private val farmVillageManager: FarmVillageManager,
    private val debugManager: DebugManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onCropHarvest(event: CropBreakEvent) {
        // 작물을 부순 주체가 플레이어가 아니면 로직을 실행하지 않음 (예: 폭발)
        val player = event.entityBreaker() as? Player ?: return

        val location = event.location()

        // 해당 위치가 농사마을 부지이고, 소유자가 있는지 확인
        val ownerUUID = farmVillageManager.getFarmPlotOwner(location)

        // 농사마을 부지가 아니거나, 소유자가 없는 땅이면 보호 로직을 적용하지 않음
        if (ownerUUID == null) {
            return
        }
        
        // 소유주가 아니고, 우회 권한도 없는 경우
        if (player.uniqueId != ownerUUID && !player.hasPermission("farmvillage.admin.bypassharvest")) {
            event.isCancelled = true
            player.sendMessage(Component.text("다른 사람의 농사마을 땅에서는 작물을 수확할 수 없습니다.", NamedTextColor.RED))
            debugManager.log(
                "CustomCropProtection",
                "Player ${player.name} was blocked from harvesting a crop at ${location.blockX},${location.blockY},${location.blockZ}. Plot owner: $ownerUUID"
            )
        }
    }
} 
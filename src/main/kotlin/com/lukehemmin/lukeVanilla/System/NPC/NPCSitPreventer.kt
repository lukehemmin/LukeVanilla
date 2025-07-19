package com.lukehemmin.lukeVanilla.System.NPC

import net.citizensnpcs.api.CitizensAPI
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.plugin.java.JavaPlugin

class NPCSitPreventer(private val plugin: JavaPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val clickedEntity = event.rightClicked

        // Citizens API를 사용하여 NPC 확인
        val npc = CitizensAPI.getNPCRegistry().getNPC(clickedEntity)

        if (npc != null) {  // 클릭한 엔티티가 Citizens NPC인 경우
            // GSit API 호출 코드가 제거되어 현재는 아무 동작도 하지 않습니다.
            // TODO: GSit API 변경 사항 확인 후 앉기 방지 기능 재구현 필요
        }
    }
} 
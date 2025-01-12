package com.lukehemmin.lukeVanilla.System.NPC

import dev.geco.gsit.api.GSitAPI
import dev.geco.gsit.objects.GetUpReason
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

class NPCSitPreventer(private val plugin: JavaPlugin) : Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val clickedEntity = event.rightClicked
        val player = event.player
        
        // Citizens API를 사용하여 NPC 확인
        val npc: NPC? = CitizensAPI.getNPCRegistry().getNPC(clickedEntity)
        
        if (npc != null) {  // 클릭한 엔티티가 Citizens NPC인 경우
            // GSit의 앉기 기능 비활성화
            GSitAPI.setCanSit(player, false)
            
            // 이미 앉아있는 상태라면 일어나도록 처리
            if (GSitAPI.isSitting(player)) {
                GSitAPI.removeSeat(player, GetUpReason.PLUGIN)
            }
            
            // 다음 틱에 앉기 기능 다시 활성화
            object : BukkitRunnable() {
                override fun run() {
                    GSitAPI.setCanSit(player, true)
                }
            }.runTask(plugin)
        }
    }
} 
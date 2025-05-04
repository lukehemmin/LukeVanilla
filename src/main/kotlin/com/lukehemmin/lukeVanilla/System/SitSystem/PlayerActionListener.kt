package com.lukehemmin.lukeVanilla.System.SitSystem

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.event.player.PlayerToggleSneakEvent

/**
 * 앉기/눕기/기어다니기 시스템에서 플레이어 액션을 처리하는 리스너
 */
class PlayerActionListener(private val sitManager: SitManager) : Listener {

    /**
     * 플레이어가 점프할 때 앉기/눕기 상태 해제
     */
    @EventHandler
    fun onPlayerJump(event: PlayerMoveEvent) {
        val player = event.player
        
        // Y좌표 변화가 양수이면 점프로 간주
        if (event.to.y > event.from.y && 
            (sitManager.isPlayerSitting(player) || sitManager.isPlayerLaying(player))) {
            
            if (sitManager.isPlayerSitting(player)) {
                sitManager.unsitPlayer(player)
                player.sendMessage("§a일어났습니다.")
            }
            
            if (sitManager.isPlayerLaying(player)) {
                sitManager.unlayPlayer(player)
                player.sendMessage("§a일어났습니다.")
            }
        }
    }

    /**
     * 플레이어가 쉬프트를 누를 때 앉기/눕기/탑승 상태 해제
     */
    @EventHandler
    fun onPlayerSneak(event: PlayerToggleSneakEvent) {
        val player = event.player

        // 쉬프트를 누를 때 (스니킹 시작) 앉기/눕기/탑승 상태 해제
        if (event.isSneaking) {
            if (sitManager.isPlayerMounted(player)) {
                sitManager.dismountPlayer(player)
                player.sendMessage("§a탑승을 취소했습니다.")
            }
            else if (sitManager.isPlayerSitting(player)) {
                sitManager.unsitPlayer(player)
                player.sendMessage("§a일어났습니다.")
            }
            // 누워있는 경우
            else if (sitManager.isPlayerLaying(player)) {
                sitManager.unlayPlayer(player)
                player.sendMessage("§a일어났습니다.")
            }
        }
    }
} 
package com.lukehemmin.lukeVanilla.System.PlayTime

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * PlayTime 시스템의 이벤트 리스너
 */
class PlayTimeListener(private val playTimeManager: PlayTimeManager) : Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        playTimeManager.onPlayerJoin(event.player)
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playTimeManager.onPlayerQuit(event.player)
    }
}
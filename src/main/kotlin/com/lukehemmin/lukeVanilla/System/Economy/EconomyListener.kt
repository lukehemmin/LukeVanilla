package com.lukehemmin.lukeVanilla.System.Economy

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EconomyListener(private val economyManager: EconomyManager) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // 접속 시 비동기로 데이터 로드
        economyManager.service.loadPlayer(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // 접속 종료 시 메모리에서 데이터 제거 (저장은 실시간으로 됨)
        economyManager.service.unloadPlayer(event.player)
    }
}

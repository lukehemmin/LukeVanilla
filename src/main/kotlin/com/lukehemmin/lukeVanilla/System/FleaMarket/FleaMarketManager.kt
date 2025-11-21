package com.lukehemmin.lukeVanilla.System.FleaMarket

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * 플리마켓 시스템 Manager (진입점)
 */
class FleaMarketManager(
    private val plugin: JavaPlugin,
    database: Database,
    private val economyManager: EconomyManager
) : Listener {
    
    private val repository: FleaMarketRepository
    val service: FleaMarketService
    val gui: FleaMarketGUI
    private val command: FleaMarketCommand
    
    init {
        // Repository 초기화
        repository = FleaMarketRepository(database)
        
        // Service 초기화
        service = FleaMarketService(repository, economyManager)
        
        // GUI 초기화
        gui = FleaMarketGUI(service)
        
        // Command 초기화
        command = FleaMarketCommand(service, gui)
        
        // 초기화
        initialize()
    }
    
    /**
     * 플리마켓 시스템 초기화
     */
    private fun initialize() {
        // 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(gui, plugin)
        Bukkit.getPluginManager().registerEvents(this, plugin)
        
        // 명령어 등록
        plugin.getCommand("market")?.setExecutor(command)
        plugin.getCommand("market")?.tabCompleter = command
        plugin.getCommand("플마")?.setExecutor(command)
        plugin.getCommand("플마")?.tabCompleter = command
        
        // 캐시 로드 (비동기, 5초 후)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            service.loadCache()
        }, 100L)  // 5초 후 (100틱)
        
        println("[플리마켓] 플리마켓 시스템이 초기화되었습니다.")
    }
    
    /**
     * 인벤토리 닫기 이벤트 처리
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? org.bukkit.entity.Player ?: return
        gui.onInventoryClose(player)
    }
    
    /**
     * 플리마켓 시스템 종료
     */
    fun shutdown() {
        println("[플리마켓] 플리마켓 시스템을 종료합니다.")
    }
}

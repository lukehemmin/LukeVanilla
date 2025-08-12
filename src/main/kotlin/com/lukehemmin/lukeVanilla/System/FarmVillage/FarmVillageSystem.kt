package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.MyLand.PrivateLandSystem
import net.luckperms.api.LuckPerms

class FarmVillageSystem(
    private val plugin: Main,
    private val database: Database,
    private val privateLandSystem: PrivateLandSystem,
    private val debugManager: DebugManager,
    private val luckPerms: LuckPerms?
) {
    private lateinit var farmVillageData: FarmVillageData
    private lateinit var farmVillageManager: FarmVillageManager
    private lateinit var farmVillageCommand: FarmVillageCommand
    // private lateinit var weeklyScrollCommand: WeeklyScrollCommand // /농사마을 시스템 주차스크롤로 통합됨
    private lateinit var farmItemRestrictionListener: FarmItemRestrictionListener
    // private lateinit var shopInteractListener: ShopInteractListener // NPC 기반 시스템으로 변경됨
    private lateinit var chestProtectionListener: ChestProtectionListener
    private lateinit var packageOpenListener: PackageOpenListener
    private lateinit var customCropProtectionListener: CustomCropProtectionListener

    fun enable() {
        // Accessing the landManager from the privateLandSystem instance
        val landManager = privateLandSystem.getLandManager()
        
        farmVillageData = FarmVillageData(database)
        farmVillageManager = FarmVillageManager(plugin, farmVillageData, landManager, debugManager, luckPerms)
        farmVillageCommand = FarmVillageCommand(plugin, farmVillageManager)
        // weeklyScrollCommand = WeeklyScrollCommand(plugin, farmVillageManager) // /농사마을 시스템 주차스크롤로 통합됨
        farmItemRestrictionListener = FarmItemRestrictionListener(plugin, farmVillageManager, debugManager)
        // shopInteractListener 초기화 제거됨 - NPC 기반 시스템으로 변경됨
        chestProtectionListener = ChestProtectionListener(farmVillageManager, debugManager)
        packageOpenListener = PackageOpenListener(plugin, farmVillageManager)
        customCropProtectionListener = CustomCropProtectionListener(farmVillageManager, debugManager)

        plugin.getCommand("농사마을")?.setExecutor(farmVillageCommand)
        plugin.getCommand("농사마을")?.tabCompleter = farmVillageCommand
        // plugin.getCommand("주차스크롤")?.setExecutor(weeklyScrollCommand) // /농사마을 시스템 주차스크롤로 통합됨
        // plugin.getCommand("주차스크롤")?.tabCompleter = weeklyScrollCommand // /농사마을 시스템 주차스크롤로 통합됨
        plugin.server.pluginManager.registerEvents(farmItemRestrictionListener, plugin)
        // shopInteractListener 등록 제거됨 - NPC 기반 시스템으로 변경됨
        plugin.server.pluginManager.registerEvents(chestProtectionListener, plugin)
        plugin.server.pluginManager.registerEvents(packageOpenListener, plugin)
        plugin.server.pluginManager.registerEvents(customCropProtectionListener, plugin)
        
        plugin.logger.info("[FarmVillage] 농사마을 시스템이 활성화되었습니다.")
    }

    fun disable() {
        plugin.logger.info("[FarmVillage] 농사마을 시스템이 비활성화되었습니다.")
    }

    fun getFarmVillageManager(): FarmVillageManager {
        return farmVillageManager
    }
} 
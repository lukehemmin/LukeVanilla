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
    private lateinit var farmItemRestrictionListener: FarmItemRestrictionListener
    private lateinit var shopInteractListener: ShopInteractListener
    private lateinit var chestProtectionListener: ChestProtectionListener
    private lateinit var packageOpenListener: PackageOpenListener

    fun enable() {
        // Accessing the landManager from the privateLandSystem instance
        val landManager = privateLandSystem.getLandManager()
        
        farmVillageData = FarmVillageData(database)
        farmVillageManager = FarmVillageManager(plugin, farmVillageData, landManager, debugManager, luckPerms)
        farmVillageCommand = FarmVillageCommand(plugin, farmVillageManager)
        farmItemRestrictionListener = FarmItemRestrictionListener(plugin, farmVillageManager, debugManager)
        shopInteractListener = ShopInteractListener(farmVillageManager)
        chestProtectionListener = ChestProtectionListener(farmVillageManager, debugManager)
        packageOpenListener = PackageOpenListener(plugin, farmVillageManager)

        plugin.getCommand("농사마을")?.setExecutor(farmVillageCommand)
        plugin.getCommand("농사마을")?.tabCompleter = farmVillageCommand
        plugin.server.pluginManager.registerEvents(farmItemRestrictionListener, plugin)
        plugin.server.pluginManager.registerEvents(shopInteractListener, plugin)
        plugin.server.pluginManager.registerEvents(chestProtectionListener, plugin)
        plugin.server.pluginManager.registerEvents(packageOpenListener, plugin)
        
        plugin.logger.info("[FarmVillage] 농사마을 시스템이 활성화되었습니다.")
    }

    fun disable() {
        plugin.logger.info("[FarmVillage] 농사마을 시스템이 비활성화되었습니다.")
    }
} 
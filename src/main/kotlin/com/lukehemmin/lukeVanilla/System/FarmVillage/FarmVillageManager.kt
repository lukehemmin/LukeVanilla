package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.MyLand.LandManager
import com.lukehemmin.lukeVanilla.System.MyLand.ClaimResult
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.luckperms.api.LuckPerms
import net.luckperms.api.model.user.User
import net.luckperms.api.node.Node
import org.bukkit.OfflinePlayer
import java.util.concurrent.CompletableFuture
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.Bukkit.getOnlinePlayers
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import net.kyori.adventure.text.format.TextDecoration

import java.util.concurrent.ConcurrentHashMap

class FarmVillageManager(
    private val plugin: Main,
    private val farmVillageData: FarmVillageData,
    private val landManager: LandManager,
    private val debugManager: DebugManager,
    private val luckPerms: LuckPerms?
) : Listener {

    private val packageEditGUI = PackageEditGUI(plugin, farmVillageData)
    val seedMerchantGUI = SeedMerchantGUI(plugin, this)
    val exchangeMerchantGUI = ExchangeMerchantGUI(plugin)
    val equipmentMerchantGUI = EquipmentMerchantGUI(plugin, this)
    private val tradeConfirmationGUI = TradeConfirmationGUI(plugin)
    val soilReceiveGUI = SoilReceiveGUI(plugin, this)
    // NPCMerchantListener는 VillageMerchant 시스템으로 이전됨
    // private val npcMerchantListener = NPCMerchantListener(this)
    private val weeklyScrollRotationSystem = WeeklyScrollRotationSystem()
    private val weeklyScrollExchangeGUI = WeeklyScrollExchangeGUI(plugin, farmVillageData, weeklyScrollRotationSystem)
    private val gson = Gson()
    private var npcMerchants = listOf<NPCMerchant>()
    
    // 청크 좌표 기반 농사마을 땅 캐시 (성능 최적화: O(N) -> O(1))
    private val farmPlotChunkCache = ConcurrentHashMap<Pair<Int, Int>, PlotPartInfo>()
    
    // Area selection system
    private val playersSelecting = mutableMapOf<UUID, AreaSelection>()
    private val areaSelectionTool = createSelectionTool()

    init {
        plugin.server.pluginManager.registerEvents(packageEditGUI, plugin)
        plugin.server.pluginManager.registerEvents(seedMerchantGUI, plugin)
        plugin.server.pluginManager.registerEvents(exchangeMerchantGUI, plugin)
        plugin.server.pluginManager.registerEvents(equipmentMerchantGUI, plugin)
        plugin.server.pluginManager.registerEvents(tradeConfirmationGUI, plugin)
        plugin.server.pluginManager.registerEvents(soilReceiveGUI, plugin)
        // NPCMerchantListener는 VillageMerchant 시스템으로 이전됨
        // plugin.server.pluginManager.registerEvents(npcMerchantListener, plugin)
        plugin.server.pluginManager.registerEvents(weeklyScrollExchangeGUI, plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        exchangeMerchantGUI.setFarmVillageManager(this)
        weeklyScrollExchangeGUI.setFarmVillageManager(this)
        farmVillageData.setPlugin(plugin)
        weeklyScrollRotationSystem.setFarmVillageData(farmVillageData)
        loadNPCMerchants()
        loadFarmPlotCache()
    }

    private fun loadNPCMerchants() {
        npcMerchants = farmVillageData.getAllNPCMerchants()
        debugManager.log("FarmVillage", "${npcMerchants.size}개의 NPC 상인을 불러왔습니다.")
    }
    
    /**
     * 농사마을 땅 캐시 로딩 (성능 최적화)
     * DB에서 모든 농사마을 땅을 불러와 청크 좌표 기반 해시맵에 저장
     */
    private fun loadFarmPlotCache() {
        farmPlotChunkCache.clear()
        val allPlots = farmVillageData.getAllPlotParts()
        allPlots.forEach { plot ->
            farmPlotChunkCache[plot.chunkX to plot.chunkZ] = plot
        }
        debugManager.log("FarmVillage", "${farmPlotChunkCache.size}개의 농사마을 땅 청크를 캐시에 로드했습니다.")
    }
    
    /**
     * 농사마을 땅 캐시 재로드 (땅 추가/삭제 시 호출)
     */
    fun reloadFarmPlotCache() {
        loadFarmPlotCache()
    }

    fun openPackageEditor(player: Player) {
        packageEditGUI.open(player)
    }

    fun openSeedMerchantGUI(player: Player) {
        seedMerchantGUI.open(player)
    }

    fun openExchangeMerchantGUI(player: Player) {
        exchangeMerchantGUI.openMainGui(player)
    }

    fun openEquipmentMerchantGUI(player: Player) {
        equipmentMerchantGUI.open(player)
    }

    fun openTradeConfirmationGUI(player: Player, rewardItem: ItemStack, costItemsDisplay: ItemStack, onConfirm: () -> Unit) {
        tradeConfirmationGUI.open(player, rewardItem, costItemsDisplay, onConfirm)
    }

    fun openSoilReceiveGUI(player: Player) {
        soilReceiveGUI.open(player)
    }

    fun getRemainingLifetimePurchases(player: Player, itemId: String, limit: Int): Int {
        val purchasedAmount = farmVillageData.getLifetimePurchaseAmount(player.uniqueId, itemId)
        return limit - purchasedAmount
    }

    fun recordPurchase(player: Player, itemId: String, amount: Int) {
        farmVillageData.recordPurchase(player.uniqueId, itemId, amount)
    }

    fun getCurrentPurchaseAmount(player: Player, itemId: String): Int {
        return farmVillageData.getLifetimePurchaseAmount(player.uniqueId, itemId)
    }

    fun updatePlayerPurchaseAmount(player: Player, itemId: String, newAmount: Int) {
        farmVillageData.updatePurchaseAmount(player.uniqueId, itemId, newAmount)
    }

    fun getRemainingDailyTradeAmount(player: Player, seedId: String): Int {
        val tradedAmount = farmVillageData.getTodaysTradeAmount(player.uniqueId, seedId)
        val dailyLimit = 64 // You can make this configurable later
        return dailyLimit - tradedAmount
    }

    fun recordSeedTrade(player: Player, seedId: String, amount: Int) {
        farmVillageData.recordSeedTrade(player.uniqueId, seedId, amount)
    }

    // NPC 상인 관련 메서드들
    fun setNPCMerchant(shopId: String, npcId: Int) {
        farmVillageData.saveNPCMerchant(shopId, npcId)
        loadNPCMerchants() // Reload cache after update
    }

    fun getShopIdByNPC(npcId: Int): String? {
        return npcMerchants.firstOrNull { it.npcId == npcId }?.shopId
    }

    fun getNPCIdByShopId(shopId: String): Int? {
        return npcMerchants.firstOrNull { it.shopId == shopId }?.npcId
    }

    fun removeNPCMerchant(shopId: String) {
        farmVillageData.removeNPCMerchant(shopId)
        loadNPCMerchants() // Reload cache after update
    }

    fun grantShopPermission(admin: Player, target: OfflinePlayer): CompletableFuture<Boolean> {
        // 권한 검증
        if (!admin.hasPermission(FarmVillagePermissions.ADMIN_GRANT)) {
            admin.sendMessage(Component.text("권한 부여 권한이 없습니다.", NamedTextColor.RED))
            return CompletableFuture.completedFuture(false)
        }
        
        if (luckPerms == null) {
            debugManager.log("FarmVillage", "LuckPerms is not available. Cannot grant permission.")
            admin.sendMessage(Component.text("LuckPerms를 사용할 수 없습니다.", NamedTextColor.RED))
            return CompletableFuture.completedFuture(false)
        }
        
        val permission = FarmVillagePermissions.SHOP_USE
        val node = Node.builder(permission).build()

        debugManager.log("FarmVillage", "${admin.name} attempting to grant permission '$permission' to ${target.name}.")

        return luckPerms.userManager.modifyUser(target.uniqueId) { user: User ->
            val result = user.data().add(node)
            if (result.wasSuccessful()) {
                debugManager.log("FarmVillage", "Successfully granted permission to ${target.name} by ${admin.name}.")
                admin.sendMessage(Component.text("${target.name}에게 상점 사용 권한을 부여했습니다.", NamedTextColor.GREEN))
            } else {
                debugManager.log("FarmVillage", "Permission was already present for ${target.name}.")
                admin.sendMessage(Component.text("${target.name}은 이미 권한을 보유하고 있습니다.", NamedTextColor.YELLOW))
            }
        }.thenApply { true }.exceptionally { e ->
            plugin.logger.severe("Error while granting permission to ${target.name}: ${e.message}")
            admin.sendMessage(Component.text("권한 부여 중 오류가 발생했습니다: ${e.message}", NamedTextColor.RED))
            false
        }
    }

    fun setPlot(plotNumber: Int, plotPart: Int, location: Location) {
        farmVillageData.setPlotLocation(plotNumber, plotPart, location)
        // 캐시 재로드
        reloadFarmPlotCache()
    }

    fun getPlotPart(plotNumber: Int, plotPart: Int): PlotPartInfo? {
        return farmVillageData.getPlotPart(plotNumber, plotPart)
    }

    fun getFarmPlotOwner(location: Location): UUID? {
        val chunk = location.chunk
        val isFarmPlotChunk = farmVillageData.getAllPlotParts().any {
            it.world == chunk.world.name && it.chunkX == chunk.x && it.chunkZ == chunk.z
        }

        return if (isFarmPlotChunk) {
            landManager.getOwnerOfChunk(chunk)
        } else {
            null
        }
    }

    /**
     * 주어진 위치(청크)가 농사마을 땅 중 하나인지 확인합니다.
     * O(1) 성능으로 최적화됨 (캐시 사용)
     */
    fun isLocationWithinAnyClaimedFarmPlot(location: Location): Boolean {
        val currentChunk = location.chunk
        val plotInfo = farmPlotChunkCache[currentChunk.x to currentChunk.z] ?: return false
        
        val plotWorld = Bukkit.getWorld(plotInfo.world) ?: return false
        val plotChunk = plotWorld.getChunkAt(plotInfo.chunkX, plotInfo.chunkZ)
        
        val isClaimed = landManager.isChunkClaimed(plotChunk)
        
        debugManager.log("FarmVillage", 
            "Location (${location.blockX}, ${location.blockY}, ${location.blockZ}) " +
            "is within ${if (isClaimed) "claimed" else "unclaimed"} farm plot #${plotInfo.plotNumber} part ${plotInfo.plotPart}.")
        
        return isClaimed
    }

    fun confiscatePlot(plotNumber: Int, admin: Player): ConfiscateResult {
        val plotParts = farmVillageData.getAllPlotParts().filter { it.plotNumber == plotNumber }
        if (plotParts.isEmpty()) {
            return ConfiscateResult.PLOT_NOT_FOUND
        }

        var unclaimCount = 0
        for (part in plotParts) {
            val world = Bukkit.getWorld(part.world) ?: continue
            val chunk = world.getChunkAt(part.chunkX, part.chunkZ)
            
            debugManager.log("FarmVillage", "Confiscating plot #$plotNumber, part #${part.plotPart} at chunk (${chunk.x}, ${chunk.z}).")

            if (landManager.isChunkClaimed(chunk)) {
                val result = landManager.unclaimChunk(chunk, admin, "농사마을 규칙 위반으로 인한 관리자 회수")
                if (result == com.lukehemmin.lukeVanilla.System.MyLand.UnclaimResult.SUCCESS) {
                    unclaimCount++
                }
            } else {
                // If one part is already unclaimed, we can count it as "success" for this operation's purpose
                unclaimCount++
            }
        }

        return if (unclaimCount == plotParts.size) {
            ConfiscateResult.SUCCESS
        } else if (unclaimCount > 0) {
            ConfiscateResult.PARTIAL_SUCCESS
        } else {
            ConfiscateResult.FAILURE
        }
    }

    fun assignNextAvailablePlot(player: Player): Pair<AssignResult, Int?> {
        debugManager.log("FarmVillage", "Starting to assign next available plot to ${player.name}.")
        val allPlotParts = farmVillageData.getAllPlotParts()
        if (allPlotParts.isEmpty()) {
            debugManager.log("FarmVillage", "Assignment failed: No plots defined in the database.")
            return AssignResult.NO_PLOTS_DEFINED to null
        }

        // Group parts by plot number
        val plotsGrouped = allPlotParts.groupBy { it.plotNumber }

        // Iterate through sorted plot numbers
        for (plotNumber in plotsGrouped.keys.sorted()) {
            debugManager.log("FarmVillage", "Checking plot #$plotNumber...")
            val plotParts = plotsGrouped[plotNumber]!!

            // We are looking for plots with exactly 2 parts
            if (plotParts.size == 2) {
                val part1 = plotParts[0]
                val part2 = plotParts[1]

                val world = Bukkit.getWorld(part1.world)
                if (world == null) {
                    plugin.logger.warning("[FarmVillage] 월드를 찾을 수 없습니다: ${part1.world}")
                    continue
                }

                val chunk1 = world.getChunkAt(part1.chunkX, part1.chunkZ)
                val chunk2 = world.getChunkAt(part2.chunkX, part2.chunkZ)
                debugManager.log("FarmVillage", "Plot #$plotNumber consists of chunks (${chunk1.x}, ${chunk1.z}) and (${chunk2.x}, ${chunk2.z}).")

                if (!landManager.isChunkClaimed(chunk1) && !landManager.isChunkClaimed(chunk2)) {
                    debugManager.log("FarmVillage", "Plot #$plotNumber is available. Attempting to claim...")
                    // Both chunks are available. Let's claim them.
                    val result1 = landManager.claimChunk(chunk1, player, "FARM_VILLAGE")
                    if (result1 == ClaimResult.SUCCESS) {
                        debugManager.log("FarmVillage", "Successfully claimed first chunk for plot #$plotNumber.")
                        val result2 = landManager.claimChunk(chunk2, player, "FARM_VILLAGE")
                        if (result2 == ClaimResult.SUCCESS) {
                            debugManager.log("FarmVillage", "Successfully claimed second chunk for plot #$plotNumber. Assignment complete.")
                            // Give package to player
                            giveJoinPackage(player)
                            return AssignResult.SUCCESS to plotNumber
                        } else {
                            // Rollback the first claim if the second one fails
                            debugManager.log("FarmVillage", "Failed to claim second chunk for plot #$plotNumber. Rolling back.")
                            landManager.unclaimChunk(chunk1, null, "두 번째 청크 지급 실패로 인한 시스템 롤백")
                            plugin.logger.severe("[FarmVillage] ${plotNumber}번 땅의 두 번째 청크 지급에 실패하여 롤백합니다.")
                            return AssignResult.FAILURE to plotNumber
                        }
                    } else {
                        debugManager.log("FarmVillage", "Failed to claim first chunk for plot #$plotNumber. Result: $result1")
                        return AssignResult.FAILURE to plotNumber
                    }
                } else {
                    debugManager.log("FarmVillage", "Plot #$plotNumber is already claimed. Checking next plot.")
                }
            } else {
                debugManager.log("FarmVillage", "Skipping plot #$plotNumber, as it does not have exactly 2 parts defined (found ${plotParts.size}).")
            }
        }

        debugManager.log("FarmVillage", "No available plots found after checking all defined plots.")
        return AssignResult.ALL_PLOTS_TAKEN to null
    }

    /**
     * 특정 플레이어에게 특정 땅번호를 지급
     */
    fun assignSpecificPlot(player: Player, plotNumber: Int): AssignResult {
        debugManager.log("FarmVillage", "Starting to assign specific plot #$plotNumber to ${player.name}.")
        
        val plotParts = farmVillageData.getAllPlotParts().filter { it.plotNumber == plotNumber }
        if (plotParts.isEmpty()) {
            debugManager.log("FarmVillage", "Assignment failed: Plot #$plotNumber not found.")
            return AssignResult.PLOT_NOT_FOUND
        }

        // 정확히 2개의 청크가 있는지 확인
        if (plotParts.size != 2) {
            debugManager.log("FarmVillage", "Assignment failed: Plot #$plotNumber does not have exactly 2 parts (found ${plotParts.size}).")
            return AssignResult.FAILURE
        }

        val part1 = plotParts[0]
        val part2 = plotParts[1]

        val world = Bukkit.getWorld(part1.world)
        if (world == null) {
            plugin.logger.warning("[FarmVillage] 월드를 찾을 수 없습니다: ${part1.world}")
            return AssignResult.FAILURE
        }

        val chunk1 = world.getChunkAt(part1.chunkX, part1.chunkZ)
        val chunk2 = world.getChunkAt(part2.chunkX, part2.chunkZ)
        debugManager.log("FarmVillage", "Plot #$plotNumber consists of chunks (${chunk1.x}, ${chunk1.z}) and (${chunk2.x}, ${chunk2.z}).")

        // 이미 청크가 클레임되어 있는지 확인
        if (landManager.isChunkClaimed(chunk1) || landManager.isChunkClaimed(chunk2)) {
            debugManager.log("FarmVillage", "Assignment failed: Plot #$plotNumber is already claimed.")
            return AssignResult.PLOT_ALREADY_CLAIMED
        }

        // 두 청크 모두 클레임 시도
        val result1 = landManager.claimChunk(chunk1, player, "FARM_VILLAGE")
        if (result1 == ClaimResult.SUCCESS) {
            debugManager.log("FarmVillage", "Successfully claimed first chunk for plot #$plotNumber.")
            val result2 = landManager.claimChunk(chunk2, player, "FARM_VILLAGE")
            if (result2 == ClaimResult.SUCCESS) {
                debugManager.log("FarmVillage", "Successfully claimed second chunk for plot #$plotNumber. Assignment complete.")
                // 입주 패키지 지급
                giveJoinPackage(player)
                return AssignResult.SUCCESS
            } else {
                // 두 번째 청크 클레임 실패 시 첫 번째 청크 롤백
                debugManager.log("FarmVillage", "Failed to claim second chunk for plot #$plotNumber. Rolling back.")
                landManager.unclaimChunk(chunk1, null, "두 번째 청크 지급 실패로 인한 시스템 롤백")
                plugin.logger.severe("[FarmVillage] ${plotNumber}번 땅의 두 번째 청크 지급에 실패하여 롤백합니다.")
                return AssignResult.FAILURE
            }
        } else {
            debugManager.log("FarmVillage", "Failed to claim first chunk for plot #$plotNumber. Result: $result1")
            return AssignResult.FAILURE
        }
    }

    /**
     * 특정 플레이어로부터 특정 땅번호를 회수
     */
    fun confiscateSpecificPlotFromPlayer(targetPlayer: OfflinePlayer, plotNumber: Int, admin: Player?): ConfiscateResult {
        debugManager.log("FarmVillage", "Starting to confiscate plot #$plotNumber from ${targetPlayer.name}.")
        
        val plotParts = farmVillageData.getAllPlotParts().filter { it.plotNumber == plotNumber }
        if (plotParts.isEmpty()) {
            return ConfiscateResult.PLOT_NOT_FOUND
        }

        var unclaimCount = 0
        var playerOwnedChunks = 0
        
        for (part in plotParts) {
            val world = Bukkit.getWorld(part.world) ?: continue
            val chunk = world.getChunkAt(part.chunkX, part.chunkZ)
            
            debugManager.log("FarmVillage", "Checking plot #$plotNumber, part #${part.plotPart} at chunk (${chunk.x}, ${chunk.z}).")

            if (landManager.isChunkClaimed(chunk)) {
                val owner = landManager.getOwnerOfChunk(chunk)
                if (owner != null && owner == targetPlayer.uniqueId) {
                    playerOwnedChunks++
                    val result = landManager.unclaimChunk(chunk, admin, "관리자에 의한 특정 플레이어 땅 회수")
                    if (result == com.lukehemmin.lukeVanilla.System.MyLand.UnclaimResult.SUCCESS) {
                        unclaimCount++
                        debugManager.log("FarmVillage", "Successfully confiscated chunk (${chunk.x}, ${chunk.z}) from ${targetPlayer.name}.")
                    }
                } else {
                    debugManager.log("FarmVillage", "Chunk (${chunk.x}, ${chunk.z}) is not owned by ${targetPlayer.name}.")
                }
            } else {
                debugManager.log("FarmVillage", "Chunk (${chunk.x}, ${chunk.z}) is not claimed.")
            }
        }

        return when {
            playerOwnedChunks == 0 -> {
                debugManager.log("FarmVillage", "Player ${targetPlayer.name} does not own any chunks in plot #$plotNumber.")
                ConfiscateResult.FAILURE
            }
            unclaimCount == playerOwnedChunks -> {
                debugManager.log("FarmVillage", "Successfully confiscated all ${unclaimCount} chunks from ${targetPlayer.name} in plot #$plotNumber.")
                ConfiscateResult.SUCCESS
            }
            unclaimCount > 0 -> {
                debugManager.log("FarmVillage", "Partially confiscated ${unclaimCount}/${playerOwnedChunks} chunks from ${targetPlayer.name} in plot #$plotNumber.")
                ConfiscateResult.PARTIAL_SUCCESS
            }
            else -> {
                debugManager.log("FarmVillage", "Failed to confiscate any chunks from ${targetPlayer.name} in plot #$plotNumber.")
                ConfiscateResult.FAILURE
            }
        }
    }

    private fun giveJoinPackage(player: Player) {
        val packageChest = NexoItems.itemFromId("farmvillage_storage_chest")?.build()

        if (packageChest == null) {
            plugin.logger.severe("[FarmVillage] 입주 패키지 아이템(farmvillage_storage_chest)을 찾을 수 없습니다!")
            player.sendMessage(Component.text("오류: 입주 패키지 아이템을 찾을 수 없습니다. 관리자에게 문의해주세요.", NamedTextColor.RED))
            return
        }

        val droppedItems = player.inventory.addItem(packageChest)
        if (droppedItems.isNotEmpty()) {
            player.world.dropItemNaturally(player.location, packageChest)
            player.sendMessage(Component.text("인벤토리가 가득 차서 입주 패키지 상자를 땅에 드롭했습니다.", NamedTextColor.YELLOW))
        }
        player.sendMessage(Component.text("농사마을 입주 패키지가 지급되었습니다! 우클릭하여 열어보세요.", NamedTextColor.GREEN))
        debugManager.log("FarmVillage", "Gave farmvillage_storage_chest package to ${player.name}.")
    }

    fun giveJoinPackageContents(player: Player): Int {
        val packageItemsInfo = farmVillageData.getPackageItems()
        if (packageItemsInfo.isEmpty()) {
            debugManager.log("FarmVillage", "No package items found in DB for ${player.name}.")
            return 0
        }

        val itemsToGive = packageItemsInfo.mapNotNull { deserializeItem(it) }

        val droppedItems = player.inventory.addItem(*itemsToGive.toTypedArray())
        if (droppedItems.isNotEmpty()) {
            player.sendMessage(Component.text("인벤토리가 가득 차서 일부 입주 선물을 땅에 드롭했습니다.", NamedTextColor.YELLOW))
            droppedItems.values.forEach { item ->
                player.world.dropItemNaturally(player.location, item)
            }
        }
        debugManager.log("FarmVillage", "Gave ${itemsToGive.size} package items to ${player.name} from package.")
        return itemsToGive.size
    }

    fun hasPackageContents(): Boolean {
        return farmVillageData.getPackageItems().isNotEmpty()
    }

    // Deserialization logic moved here to be accessible by giveJoinPackage
    private fun deserializeItem(itemInfo: PackageItem): ItemStack? {
        val itemData: Map<String, Any> = try {
            gson.fromJson(itemInfo.itemData, object : TypeToken<Map<String, Any>>() {}.type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val baseItem = when (itemInfo.itemType) {
            "NEXO" -> NexoItems.itemFromId(itemInfo.identifier)?.build()
            "VANILLA" -> ItemStack(Material.getMaterial(itemInfo.identifier) ?: Material.AIR)
            else -> null
        } ?: return null
        
        baseItem.amount = (itemData["amount"] as? Double)?.toInt() ?: 1

        baseItem.itemMeta = baseItem.itemMeta?.apply {
            (itemData["name"] as? String)?.let { displayName(gson.fromJson(it, Component::class.java)) }
            (itemData["lore"] as? List<*>)?.let { loreComponents ->
                val loreList = loreComponents.mapNotNull { line ->
                    try {
                        gson.fromJson(line as String, Component::class.java)
                    } catch (e: Exception) {
                        Component.text(line.toString())
                    }
                }
                lore(loreList)
            }
            (itemData["enchants"] as? Map<String, Double>)?.forEach { (key, level) ->
                val enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(key.substringAfter(":")))
                if (enchantment != null) {
                    addEnchant(enchantment, level.toInt(), true)
                }
            }
        }
        return baseItem
    }

    fun getWeeklyScrollExchangeGUI(): WeeklyScrollExchangeGUI {
        return weeklyScrollExchangeGUI
    }
    
    fun getWeeklyScrollRotationSystem(): WeeklyScrollRotationSystem {
        return weeklyScrollRotationSystem
    }

    private fun createSelectionTool(): ItemStack {
        val tool = ItemStack(Material.GOLDEN_AXE)
        val meta = tool.itemMeta
        meta?.displayName(Component.text("농사마을 구역 선택 도구", NamedTextColor.GOLD, TextDecoration.BOLD))
        meta?.lore(listOf(
            Component.text("좌클릭: 첫 번째 모서리 선택", NamedTextColor.YELLOW),
            Component.text("우클릭: 두 번째 모서리 선택", NamedTextColor.YELLOW),
            Component.text("설정이 완료되면 자동으로 회수됩니다", NamedTextColor.GRAY)
        ))
        tool.itemMeta = meta
        return tool
    }

    fun startAreaSelection(player: Player) {
        if (playersSelecting.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("이미 구역 선택 모드가 활성화되어 있습니다.", NamedTextColor.RED))
            return
        }

        playersSelecting[player.uniqueId] = AreaSelection()
        val tool = areaSelectionTool.clone()
        player.inventory.addItem(tool)
        
        player.sendMessage(Component.text("=== 농사마을 구역 선택 모드 시작 ===", NamedTextColor.GOLD, TextDecoration.BOLD))
        player.sendMessage(Component.text("황금 도끼로 첫 번째 모서리를 좌클릭하세요.", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("취소하려면: /농사마을 시스템 농사마을구역지정 취소", NamedTextColor.GRAY))
    }

    fun cancelAreaSelection(player: Player) {
        if (!playersSelecting.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("구역 선택 모드가 활성화되지 않았습니다.", NamedTextColor.RED))
            return
        }

        playersSelecting.remove(player.uniqueId)
        removeSelectionTool(player)
        
        player.sendMessage(Component.text("농사마을 구역 선택을 취소했습니다.", NamedTextColor.YELLOW))
    }

    private fun removeSelectionTool(player: Player) {
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i)
            if (item != null && isSelectionTool(item)) {
                inventory.setItem(i, null)
                break
            }
        }
    }

    private fun isSelectionTool(item: ItemStack): Boolean {
        if (item.type != Material.GOLDEN_AXE) return false
        val meta = item.itemMeta ?: return false
        val displayName = meta.displayName() ?: return false
        return displayName == Component.text("농사마을 구역 선택 도구", NamedTextColor.GOLD, TextDecoration.BOLD)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        
        if (item == null || !isSelectionTool(item)) return
        if (!playersSelecting.containsKey(player.uniqueId)) return
        
        event.isCancelled = true
        val clickedBlock = event.clickedBlock ?: return
        val location = clickedBlock.location
        val selection = playersSelecting[player.uniqueId]!!
        
        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                selection.pos1 = location
                player.sendMessage(Component.text("첫 번째 모서리 설정: ${location.blockX}, ${location.blockY}, ${location.blockZ}", NamedTextColor.GREEN))
                player.sendMessage(Component.text("이제 황금 도끼로 두 번째 모서리를 우클릭하세요.", NamedTextColor.YELLOW))
            }
            Action.RIGHT_CLICK_BLOCK -> {
                if (selection.pos1 == null) {
                    player.sendMessage(Component.text("먼저 첫 번째 모서리를 좌클릭하세요.", NamedTextColor.RED))
                    return
                }
                
                selection.pos2 = location
                player.sendMessage(Component.text("두 번째 모서리 설정: ${location.blockX}, ${location.blockY}, ${location.blockZ}", NamedTextColor.GREEN))
                
                // 구역 저장
                saveAreaToConfig(player, selection)
            }
            else -> {}
        }
    }

    private fun saveAreaToConfig(player: Player, selection: AreaSelection) {
        val pos1 = selection.pos1!!
        val pos2 = selection.pos2!!
        
        // 두 좌표가 같은 월드인지 확인
        if (pos1.world != pos2.world) {
            player.sendMessage(Component.text("두 모서리가 다른 월드에 있습니다!", NamedTextColor.RED))
            return
        }
        
        val minX = minOf(pos1.blockX, pos2.blockX)
        val maxX = maxOf(pos1.blockX, pos2.blockX)
        val minZ = minOf(pos1.blockZ, pos2.blockZ)
        val maxZ = maxOf(pos1.blockZ, pos2.blockZ)
        
        // Config 업데이트
        val config = plugin.config
        config.set("myland.use-area-restriction", true)
        config.set("myland.area.world", pos1.world?.name)
        config.set("myland.area.x1", minX)
        config.set("myland.area.z1", minZ)
        config.set("myland.area.x2", maxX)
        config.set("myland.area.z2", maxZ)
        
        plugin.saveConfig()
        
        // LandManager에 새 설정 로드
        landManager.loadConfig()
        
        // 선택 모드 종료
        playersSelecting.remove(player.uniqueId)
        removeSelectionTool(player)
        
        player.sendMessage(Component.text("=== 농사마을 구역 설정 완료 ===", NamedTextColor.GREEN, TextDecoration.BOLD))
        player.sendMessage(Component.text("월드: ${pos1.world?.name}", NamedTextColor.AQUA))
        player.sendMessage(Component.text("구역: ($minX, $minZ) ~ ($maxX, $maxZ)", NamedTextColor.AQUA))
        player.sendMessage(Component.text("config.yml이 업데이트되었습니다.", NamedTextColor.GRAY))
    }
}

data class AreaSelection(
    var pos1: Location? = null,
    var pos2: Location? = null
)

enum class ConfiscateResult {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE,
    PLOT_NOT_FOUND
}

enum class PlotDirection {
    NORTH, SOUTH, EAST, WEST
}

enum class AssignResult {
    SUCCESS,
    FAILURE,
    ALL_PLOTS_TAKEN,
    NO_PLOTS_DEFINED,
    PLOT_NOT_FOUND,
    PLOT_ALREADY_CLAIMED
} 
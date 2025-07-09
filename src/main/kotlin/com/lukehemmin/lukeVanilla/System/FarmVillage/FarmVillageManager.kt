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

class FarmVillageManager(
    private val plugin: Main,
    private val farmVillageData: FarmVillageData,
    private val landManager: LandManager,
    private val debugManager: DebugManager,
    private val luckPerms: LuckPerms?
) {

    private val packageEditGUI = PackageEditGUI(plugin, farmVillageData)
    private val gson = Gson()
    private var shopLocations = listOf<ShopLocation>()

    init {
        plugin.server.pluginManager.registerEvents(packageEditGUI, plugin)
        loadShopLocations()
    }

    private fun loadShopLocations() {
        shopLocations = farmVillageData.getAllShopLocations()
        debugManager.log("FarmVillage", "${shopLocations.size}개의 상점 위치를 불러왔습니다.")
    }
    
    fun setShopLocation(shopId: String, world: String, x: Int, y: Int, z: Int) {
        farmVillageData.saveShopLocation(shopId, world, x, y, z)
        loadShopLocations() // Reload cache after update
    }

    fun isShopLocation(location: Location): Boolean {
        return shopLocations.any { 
            (location.blockX == it.topBlockX && location.blockY == it.topBlockY && location.blockZ == it.topBlockZ && location.world.name == it.world) ||
            (location.blockX == it.bottomBlockX && location.blockY == it.bottomBlockY && location.blockZ == it.bottomBlockZ && location.world.name == it.world)
        }
    }

    fun openPackageEditor(player: Player) {
        packageEditGUI.open(player)
    }

    fun grantShopPermission(player: OfflinePlayer): CompletableFuture<Boolean> {
        if (luckPerms == null) {
            debugManager.log("FarmVillage", "LuckPerms is not available. Cannot grant permission.")
            return CompletableFuture.completedFuture(false)
        }
        
        val permission = "farmvillage.shop.use" // TODO: Make this configurable
        val node = Node.builder(permission).build()

        debugManager.log("FarmVillage", "Attempting to grant permission '$permission' to ${player.name}.")

        return luckPerms.userManager.modifyUser(player.uniqueId) { user: User ->
            val result = user.data().add(node)
            if (result.wasSuccessful()) {
                debugManager.log("FarmVillage", "Successfully granted permission to ${player.name}.")
            } else {
                debugManager.log("FarmVillage", "Permission was already present for ${player.name}.")
            }
        }.thenApply { true }.exceptionally { e ->
            plugin.logger.severe("Error while granting permission to ${player.name}: ${e.message}")
            false
        }
    }

    fun setPlot(plotNumber: Int, plotPart: Int, location: Location) {
        farmVillageData.setPlotLocation(plotNumber, plotPart, location)
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

    // 주어진 위치(청크)가 농사마을 땅 중 하나인지 확인합니다.
    fun isLocationWithinAnyClaimedFarmPlot(location: Location): Boolean {
        val currentChunk = location.chunk
        val allPlotParts = farmVillageData.getAllPlotParts()

        for (plotPartInfo in allPlotParts) {
            val plotWorld = Bukkit.getWorld(plotPartInfo.world) ?: continue
            val plotChunk = plotWorld.getChunkAt(plotPartInfo.chunkX, plotPartInfo.chunkZ)
            
            if (plotChunk == currentChunk && landManager.isChunkClaimed(plotChunk)) {
                debugManager.log("FarmVillage", "Location (${location.blockX}, ${location.blockY}, ${location.blockZ}) is within claimed farm plot # ${plotPartInfo.plotNumber} part ${plotPartInfo.plotPart}.")
                return true
            }
        }
        debugManager.log("FarmVillage", "Location (${location.blockX}, ${location.blockY}, ${location.blockZ}) is NOT within any claimed farm plot.")
        return false
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
}

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
    NO_PLOTS_DEFINED
} 
package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.MyLand.LandManager
import com.lukehemmin.lukeVanilla.System.MyLand.ClaimResult
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager

class FarmVillageManager(
    private val plugin: Main,
    private val farmVillageData: FarmVillageData,
    private val landManager: LandManager,
    private val debugManager: DebugManager
) {

    fun setPlot(plotNumber: Int, plotPart: Int, location: Location) {
        farmVillageData.setPlotLocation(plotNumber, plotPart, location)
    }

    fun getPlotPart(plotNumber: Int, plotPart: Int): PlotPartInfo? {
        return farmVillageData.getPlotPart(plotNumber, plotPart)
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
                    val result1 = landManager.claimChunk(chunk1, player)
                    if (result1 == ClaimResult.SUCCESS) {
                        debugManager.log("FarmVillage", "Successfully claimed first chunk for plot #$plotNumber.")
                        val result2 = landManager.claimChunk(chunk2, player)
                        if (result2 == ClaimResult.SUCCESS) {
                            debugManager.log("FarmVillage", "Successfully claimed second chunk for plot #$plotNumber. Assignment complete.")
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
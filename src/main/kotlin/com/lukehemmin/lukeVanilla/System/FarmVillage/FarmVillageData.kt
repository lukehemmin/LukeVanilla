package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Location

data class PlotPartInfo(val plotNumber: Int, val plotPart: Int, val world: String, val chunkX: Int, val chunkZ: Int)

class FarmVillageData(private val database: Database) {

    /**
     * 특정 번호의 농사 땅 위치 정보를 데이터베이스에 저장하거나 업데이트합니다.
     */
    fun setPlotLocation(plotNumber: Int, plotPart: Int, location: Location) {
        val query = """
            INSERT INTO farmvillage_plots (plot_number, plot_part, world, chunk_x, chunk_z) 
            VALUES (?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE world = VALUES(world), chunk_x = VALUES(chunk_x), chunk_z = VALUES(chunk_z)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.setString(3, location.world.name)
                statement.setInt(4, location.chunk.x)
                statement.setInt(5, location.chunk.z)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 특정 땅 조각의 정보를 불러옵니다.
     */
    fun getPlotPart(plotNumber: Int, plotPart: Int): PlotPartInfo? {
        val query = "SELECT world, chunk_x, chunk_z FROM farmvillage_plots WHERE plot_number = ? AND plot_part = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        PlotPartInfo(
                            plotNumber = plotNumber,
                            plotPart = plotPart,
                            world = resultSet.getString("world"),
                            chunkX = resultSet.getInt("chunk_x"),
                            chunkZ = resultSet.getInt("chunk_z")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * 모든 농사 땅의 정보를 번호 순서대로 불러옵니다.
     * @return List<PlotPartInfo>
     */
    fun getAllPlotParts(): List<PlotPartInfo> {
        val plots = mutableListOf<PlotPartInfo>()
        val query = "SELECT plot_number, plot_part, world, chunk_x, chunk_z FROM farmvillage_plots ORDER BY plot_number ASC, plot_part ASC"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        plots.add(PlotPartInfo(
                            plotNumber = resultSet.getInt("plot_number"),
                            plotPart = resultSet.getInt("plot_part"),
                            world = resultSet.getString("world"),
                            chunkX = resultSet.getInt("chunk_x"),
                            chunkZ = resultSet.getInt("chunk_z")
                        ))
                    }
                }
            }
        }
        return plots
    }
} 
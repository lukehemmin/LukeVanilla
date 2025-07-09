package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.google.gson.Gson
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Location
import org.bukkit.inventory.ItemStack

data class PlotPartInfo(val plotNumber: Int, val plotPart: Int, val world: String, val chunkX: Int, val chunkZ: Int)
data class PackageItem(val slot: Int, val itemType: String, val identifier: String, val itemData: String?)
data class ShopLocation(
    val shopId: String,
    val world: String,
    val topBlockX: Int,
    val topBlockY: Int,
    val topBlockZ: Int,
    val bottomBlockX: Int,
    val bottomBlockY: Int,
    val bottomBlockZ: Int
)

class FarmVillageData(private val database: Database) {

    private val gson = Gson()
    private val TABLE_PLOTS = "farmvillage_plots"
    private val TABLE_SHOP_LOCATIONS = "farmvillage_shop_locations"
    private val TABLE_PACKAGE_ITEMS = "farmvillage_package_items"

    init {
        createTables()
    }

    private fun createTables() {
        val sqlPlots = """
            CREATE TABLE IF NOT EXISTS $TABLE_PLOTS (
                plot_number INT NOT NULL,
                plot_part INT NOT NULL,
                world VARCHAR(255) NOT NULL,
                x INT NOT NULL,
                y INT NOT NULL,
                z INT NOT NULL,
                chunk_x INT NOT NULL,
                chunk_z INT NOT NULL,
                PRIMARY KEY (plot_number, plot_part)
            );
        """.trimIndent()

        val sqlShopLocations = """
            CREATE TABLE IF NOT EXISTS $TABLE_SHOP_LOCATIONS (
                shop_id VARCHAR(255) NOT NULL PRIMARY KEY,
                world VARCHAR(255) NOT NULL,
                top_block_x INT NOT NULL,
                top_block_y INT NOT NULL,
                top_block_z INT NOT NULL,
                bottom_block_x INT NOT NULL,
                bottom_block_y INT NOT NULL,
                bottom_block_z INT NOT NULL
            );
        """.trimIndent()
        
        val sqlPackageItems = """
            CREATE TABLE IF NOT EXISTS $TABLE_PACKAGE_ITEMS (
                slot INT NOT NULL,
                item_type VARCHAR(255) NOT NULL,
                item_identifier VARCHAR(255) NOT NULL,
                item_data TEXT,
                PRIMARY KEY (slot)
            );
        """.trimIndent()

        database.execute(sqlPlots)
        database.execute(sqlShopLocations)
        database.execute(sqlPackageItems)
    }
    
    /**
     * 특정 번호의 농사 땅 위치 정보를 데이터베이스에 저장하거나 업데이트합니다.
     */
    fun setPlotLocation(plotNumber: Int, plotPart: Int, location: Location) {
        val query = """
            INSERT INTO $TABLE_PLOTS (plot_number, plot_part, world, x, y, z, chunk_x, chunk_z) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), chunk_x = VALUES(chunk_x), chunk_z = VALUES(chunk_z)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.setString(3, location.world.name)
                statement.setInt(4, location.x)
                statement.setInt(5, location.y)
                statement.setInt(6, location.z)
                statement.setInt(7, location.chunk.x)
                statement.setInt(8, location.chunk.z)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 입주 패키지 아이템 목록 전체를 DB에 저장합니다 (기존 목록은 삭제 후 덮어쓰기).
     */
    fun savePackageItems(items: List<PackageItem>) {
        val deleteQuery = "DELETE FROM $TABLE_PACKAGE_ITEMS"
        val insertQuery = "INSERT INTO $TABLE_PACKAGE_ITEMS (slot, item_type, item_identifier, item_data) VALUES (?, ?, ?, ?)"
        
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                // Clear existing items
                connection.prepareStatement(deleteQuery).use { it.executeUpdate() }

                // Insert new items
                connection.prepareStatement(insertQuery).use { statement ->
                    for (item in items) {
                        statement.setInt(1, item.slot)
                        statement.setString(2, item.itemType)
                        statement.setString(3, item.identifier)
                        statement.setString(4, item.itemData)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * DB에 저장된 모든 입주 패키지 아이템을 불러옵니다.
     */
    fun getPackageItems(): List<PackageItem> {
        val items = mutableListOf<PackageItem>()
        val query = "SELECT slot, item_type, item_identifier, item_data FROM $TABLE_PACKAGE_ITEMS"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        items.add(PackageItem(
                            slot = resultSet.getInt("slot"),
                            itemType = resultSet.getString("item_type"),
                            identifier = resultSet.getString("item_identifier"),
                            itemData = resultSet.getString("item_data")
                        ))
                    }
                }
            }
        }
        return items
    }

    fun saveShopLocation(shopId: String, world: String, x: Int, y: Int, z: Int) {
        val sql = "REPLACE INTO $TABLE_SHOP_LOCATIONS (shop_id, world, top_block_x, top_block_y, top_block_z, bottom_block_x, bottom_block_y, bottom_block_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        database.update(sql, shopId, world, x, y, z, x, y - 1, z)
    }
    
    fun getAllShopLocations(): List<ShopLocation> {
        val locations = mutableListOf<ShopLocation>()
        val sql = "SELECT * FROM $TABLE_SHOP_LOCATIONS"
        database.query(sql) { rs ->
            while (rs.next()) {
                locations.add(ShopLocation(
                    shopId = rs.getString("shop_id"),
                    world = rs.getString("world"),
                    topBlockX = rs.getInt("top_block_x"),
                    topBlockY = rs.getInt("top_block_y"),
                    topBlockZ = rs.getInt("top_block_z"),
                    bottomBlockX = rs.getInt("bottom_block_x"),
                    bottomBlockY = rs.getInt("bottom_block_y"),
                    bottomBlockZ = rs.getInt("bottom_block_z")
                ))
            }
        }
        return locations
    }

    /**
     * 특정 땅 조각의 정보를 불러옵니다.
     */
    fun getPlotPart(plotNumber: Int, plotPart: Int): PlotPartInfo? {
        val query = "SELECT world, chunk_x, chunk_z FROM $TABLE_PLOTS WHERE plot_number = ? AND plot_part = ?"
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
        val query = "SELECT plot_number, plot_part, world, chunk_x, chunk_z FROM $TABLE_PLOTS ORDER BY plot_number ASC, plot_part ASC"
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
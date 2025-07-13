package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.google.gson.Gson
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.ZoneId
import java.util.UUID

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
    private val TABLE_SEED_TRADES = "farmvillage_seed_trades"
    private val TABLE_PURCHASE_HISTORY = "farmvillage_purchase_history"

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

        val sqlSeedTrades = """
            CREATE TABLE IF NOT EXISTS $TABLE_SEED_TRADES (
                player_uuid VARCHAR(36) NOT NULL,
                seed_id VARCHAR(255) NOT NULL,
                traded_amount INT NOT NULL DEFAULT 0,
                trade_date DATE NOT NULL,
                PRIMARY KEY (player_uuid, seed_id)
            );
        """.trimIndent()

        val sqlPurchaseHistory = """
            CREATE TABLE IF NOT EXISTS $TABLE_PURCHASE_HISTORY (
                player_uuid VARCHAR(36) NOT NULL,
                item_id VARCHAR(255) NOT NULL,
                total_purchased INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, item_id)
            );
        """.trimIndent()

        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(sqlPlots)
                statement.executeUpdate(sqlShopLocations)
                statement.executeUpdate(sqlPackageItems)
                statement.executeUpdate(sqlSeedTrades)
                statement.executeUpdate(sqlPurchaseHistory)
            }
        }
    }
    
    fun setPlotLocation(plotNumber: Int, plotPart: Int, location: Location) {
        val query = """
            REPLACE INTO $TABLE_PLOTS (plot_number, plot_part, world, x, y, z, chunk_x, chunk_z) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.setString(3, location.world.name)
                statement.setInt(4, location.blockX)
                statement.setInt(5, location.blockY)
                statement.setInt(6, location.blockZ)
                statement.setInt(7, location.chunk.x)
                statement.setInt(8, location.chunk.z)
                statement.executeUpdate()
            }
        }
    }

    fun saveShopLocation(shopId: String, world: String, x: Int, y: Int, z: Int) {
        val sql = "REPLACE INTO $TABLE_SHOP_LOCATIONS (shop_id, world, top_block_x, top_block_y, top_block_z, bottom_block_x, bottom_block_y, bottom_block_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, shopId)
                statement.setString(2, world)
                statement.setInt(3, x)
                statement.setInt(4, y)
                statement.setInt(5, z)
                statement.setInt(6, x)
                statement.setInt(7, y - 1)
                statement.setInt(8, z)
                statement.executeUpdate()
            }
        }
    }

    fun getAllShopLocations(): List<ShopLocation> {
        val locations = mutableListOf<ShopLocation>()
        val sql = "SELECT * FROM $TABLE_SHOP_LOCATIONS"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs ->
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
            }
        }
        return locations
    }

    fun savePackageItems(items: List<PackageItem>) {
        val deleteQuery = "DELETE FROM $TABLE_PACKAGE_ITEMS"
        val insertQuery = "INSERT INTO $TABLE_PACKAGE_ITEMS (slot, item_type, item_identifier, item_data) VALUES (?, ?, ?, ?)"
        
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(deleteQuery).use { it.executeUpdate() }
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

    fun getPackageItems(): List<PackageItem> {
        val items = mutableListOf<PackageItem>()
        val query = "SELECT slot, item_type, item_identifier, item_data FROM $TABLE_PACKAGE_ITEMS"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        items.add(
                            PackageItem(
                                rs.getInt("slot"),
                                rs.getString("item_type"),
                                rs.getString("item_identifier"),
                                rs.getString("item_data")
                            )
                        )
                    }
                }
            }
        }
        return items
    }

    fun getPlotPart(plotNumber: Int, plotPart: Int): PlotPartInfo? {
        val query = "SELECT world, chunk_x, chunk_z FROM $TABLE_PLOTS WHERE plot_number = ? AND plot_part = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return PlotPartInfo(
                            plotNumber,
                            plotPart,
                            rs.getString("world"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z")
                        )
                    }
                    }
                }
            }
        return null
    }

    fun getAllPlotParts(): List<PlotPartInfo> {
        val plots = mutableListOf<PlotPartInfo>()
        val query = "SELECT plot_number, plot_part, world, chunk_x, chunk_z FROM $TABLE_PLOTS ORDER BY plot_number ASC, plot_part ASC"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        plots.add(
                            PlotPartInfo(
                                rs.getInt("plot_number"),
                                rs.getInt("plot_part"),
                                rs.getString("world"),
                                rs.getInt("chunk_x"),
                                rs.getInt("chunk_z")
                            )
                        )
                    }
                }
            }
        }
        return plots
    }

    fun getTodaysTradeAmount(playerUUID: UUID, seedId: String): Int {
        val query = "SELECT traded_amount, trade_date FROM $TABLE_SEED_TRADES WHERE player_uuid = ? AND seed_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, seedId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        val lastTradeDate = rs.getDate("trade_date").toLocalDate()
                        // Always use KST (Asia/Seoul) for today's date
                        val today = java.time.LocalDate.now(ZoneId.of("Asia/Seoul"))
                        // If the record is from today, return the amount. Otherwise, it's effectively 0 for today.
                        if (lastTradeDate.isEqual(today)) {
                            return rs.getInt("traded_amount")
                        }
                    }
                }
            }
        }
        return 0 // No record for today, so 0 traded
    }

    fun recordSeedTrade(playerUUID: UUID, seedId: String, amount: Int) {
        val query = """
            INSERT INTO $TABLE_SEED_TRADES (player_uuid, seed_id, traded_amount, trade_date)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            traded_amount = IF(trade_date = VALUES(trade_date), traded_amount + VALUES(traded_amount), VALUES(traded_amount)),
            trade_date = VALUES(trade_date)
        """.trimIndent()
        // Always use KST for the trade date
        val kstDate = java.sql.Date.valueOf(java.time.LocalDate.now(ZoneId.of("Asia/Seoul")))
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, seedId)
                statement.setInt(3, amount)
                statement.setDate(4, kstDate)
                statement.executeUpdate()
            }
        }
    }

    fun getLifetimePurchaseAmount(playerUUID: UUID, itemId: String): Int {
        val query = "SELECT total_purchased FROM $TABLE_PURCHASE_HISTORY WHERE player_uuid = ? AND item_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, itemId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt("total_purchased")
                    }
                }
            }
        }
        return 0
    }
    
    fun recordPurchase(playerUUID: UUID, itemId: String, amount: Int) {
        val query = """
            INSERT INTO $TABLE_PURCHASE_HISTORY (player_uuid, item_id, total_purchased)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE total_purchased = total_purchased + VALUES(total_purchased)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, itemId)
                statement.setInt(3, amount)
                statement.executeUpdate()
            }
        }
    }
    
    fun updatePurchaseAmount(playerUUID: UUID, itemId: String, newAmount: Int) {
        val query = """
            INSERT INTO $TABLE_PURCHASE_HISTORY (player_uuid, item_id, total_purchased)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE total_purchased = VALUES(total_purchased)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, itemId)
                statement.setInt(3, newAmount)
                statement.executeUpdate()
            }
        }
    }
} 
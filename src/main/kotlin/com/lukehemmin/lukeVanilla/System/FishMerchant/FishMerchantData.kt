package com.lukehemmin.lukeVanilla.System.FishMerchant

import com.lukehemmin.lukeVanilla.System.Database.Database

data class FishPrice(
    val itemProvider: String,  // VANILLA, CUSTOMFISHING, NEXO
    val fishType: String,       // 아이템 ID
    val price: Double
)

class FishMerchantData(private val database: Database) {

    private val TABLE_NPC = "fish_merchant_npc"
    private val TABLE_PRICES = "fish_prices"

    // NPC 관련 메서드
    fun saveNPCMerchant(npcId: Int) {
        val query = "INSERT INTO $TABLE_NPC (npc_id) VALUES (?) ON DUPLICATE KEY UPDATE npc_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, npcId)
                statement.setInt(2, npcId)
                statement.executeUpdate()
            }
        }
    }

    fun getNPCMerchant(): Int? {
        val query = "SELECT npc_id FROM $TABLE_NPC LIMIT 1"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt("npc_id")
                    }
                }
            }
        }
        return null
    }

    fun removeNPCMerchant() {
        val query = "DELETE FROM $TABLE_NPC"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeUpdate()
            }
        }
    }

    // 가격 관련 메서드
    fun getFishPrice(itemProvider: String, fishType: String): Double? {
        val query = "SELECT price FROM $TABLE_PRICES WHERE item_provider = ? AND fish_type = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, itemProvider)
                statement.setString(2, fishType)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getDouble("price")
                    }
                }
            }
        }
        return null
    }

    fun setFishPrice(itemProvider: String, fishType: String, price: Double) {
        val query = """
            INSERT INTO $TABLE_PRICES (item_provider, fish_type, price) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE price = ?
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, itemProvider)
                statement.setString(2, fishType)
                statement.setDouble(3, price)
                statement.setDouble(4, price)
                statement.executeUpdate()
            }
        }
    }

    fun getAllFishPrices(): List<FishPrice> {
        val prices = mutableListOf<FishPrice>()
        val query = "SELECT item_provider, fish_type, price FROM $TABLE_PRICES"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        prices.add(
                            FishPrice(
                                rs.getString("item_provider"),
                                rs.getString("fish_type"),
                                rs.getDouble("price")
                            )
                        )
                    }
                }
            }
        }
        return prices
    }
}

package com.lukehemmin.lukeVanilla.System.FishMerchant

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.util.*

data class FishPrice(
    val itemProvider: String,  // VANILLA, CUSTOMFISHING, NEXO
    val fishType: String,       // 아이템 ID
    val basePrice: Double,      // 기본 가격
    val pricePerCm: Double      // cm당 가격
)

data class FishSellRecord(
    val playerUuid: UUID,
    val playerName: String,
    val itemsSold: Map<String, Int>,  // "VANILLA:COD" -> 개수
    val totalAmount: Double
)

class FishMerchantData(private val database: Database) {

    private val TABLE_NPC = "fish_merchant_npc"
    private val TABLE_PRICES = "fish_prices"
    private val TABLE_SELL_HISTORY = "fish_sell_history"

    // NPC 관련 메서드
    /**
     * 낚시 상인 NPC 저장 (기존 낚시 상인은 자동 삭제됨)
     * @return 이전에 등록된 NPC ID (없으면 null)
     */
    fun saveNPCMerchant(npcId: Int): Int? {
        database.getConnection().use { connection ->
            // 기존 낚시 상인 ID 조회
            val previousNpcId = connection.prepareStatement("SELECT npc_id FROM $TABLE_NPC LIMIT 1").use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("npc_id") else null
                }
            }

            // 기존 데이터 모두 삭제 (깔끔하게 정리)
            connection.prepareStatement("DELETE FROM $TABLE_NPC").use { statement ->
                statement.executeUpdate()
            }

            // 새 NPC 등록
            connection.prepareStatement("INSERT INTO $TABLE_NPC (npc_id) VALUES (?)").use { statement ->
                statement.setInt(1, npcId)
                statement.executeUpdate()
            }

            return previousNpcId
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
    /**
     * 물고기 가격 정보 조회
     */
    fun getFishPriceInfo(itemProvider: String, fishType: String): FishPrice? {
        val query = "SELECT base_price, price_per_cm FROM $TABLE_PRICES WHERE item_provider = ? AND fish_type = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, itemProvider)
                statement.setString(2, fishType)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        val basePrice = rs.getDouble("base_price")
                        val pricePerCm = rs.getDouble("price_per_cm")
                        return FishPrice(itemProvider, fishType, basePrice, pricePerCm)
                    }
                }
            }
        }
        return null
    }

    /**
     * 물고기 기본 가격 조회
     */
    fun getFishPrice(itemProvider: String, fishType: String): Double? {
        val priceInfo = getFishPriceInfo(itemProvider, fishType)
        return priceInfo?.basePrice
    }

    /**
     * 물고기 가격 설정 (단순 가격)
     */
    fun setFishPrice(itemProvider: String, fishType: String, price: Double) {
        setFishPriceWithSize(itemProvider, fishType, price, 0.0)
    }

    /**
     * 물고기 가격 설정 (크기 기반 가격)
     */
    fun setFishPriceWithSize(itemProvider: String, fishType: String, basePrice: Double, pricePerCm: Double) {
        val query = """
            INSERT INTO $TABLE_PRICES (item_provider, fish_type, base_price, price_per_cm)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                base_price = ?,
                price_per_cm = ?
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, itemProvider)
                statement.setString(2, fishType)
                statement.setDouble(3, basePrice)
                statement.setDouble(4, pricePerCm)
                statement.setDouble(5, basePrice) // UPDATE
                statement.setDouble(6, pricePerCm) // UPDATE
                statement.executeUpdate()
            }
        }
    }

    fun getAllFishPrices(): List<FishPrice> {
        val prices = mutableListOf<FishPrice>()
        val query = "SELECT item_provider, fish_type, base_price, price_per_cm FROM $TABLE_PRICES"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        prices.add(
                            FishPrice(
                                rs.getString("item_provider"),
                                rs.getString("fish_type"),
                                rs.getDouble("base_price"),
                                rs.getDouble("price_per_cm")
                            )
                        )
                    }
                }
            }
        }
        return prices
    }

    // 판매 기록 관련 메서드

    /**
     * 판매 기록 저장
     * @param record 판매 기록 데이터
     */
    fun saveSellHistory(record: FishSellRecord) {
        val query = """
            INSERT INTO $TABLE_SELL_HISTORY (player_uuid, player_name, items_sold, total_amount)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        // Map을 JSON 문자열로 변환
        val itemsSoldJson = record.itemsSold.entries.joinToString(",", "{", "}") { (key, value) ->
            "\"${key.replace("\"", "\\\"")}\":$value"
        }

        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, record.playerUuid.toString())
                statement.setString(2, record.playerName)
                statement.setString(3, itemsSoldJson)
                statement.setDouble(4, record.totalAmount)
                statement.executeUpdate()
            }
        }
    }
}

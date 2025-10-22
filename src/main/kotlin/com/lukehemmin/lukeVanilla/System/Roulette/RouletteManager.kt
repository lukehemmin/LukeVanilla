package com.lukehemmin.lukeVanilla.System.Roulette

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Timestamp
import kotlin.random.Random

/**
 * 룰렛 시스템 관리 클래스
 * - DB에서 설정 및 아이템 로드
 * - 확률 기반 아이템 선택
 * - 히스토리 저장
 */
class RouletteManager(
    private val plugin: JavaPlugin,
    private val database: Database
) {
    private var config: RouletteConfig? = null
    private var items: List<RouletteItem> = emptyList()

    companion object {
        // DB 쿼리 상수 (재사용 및 유지보수 편의성 향상)
        private const val QUERY_SELECT_CONFIG = "SELECT * FROM roulette_config LIMIT 1"
        private const val QUERY_SELECT_ITEMS = "SELECT * FROM roulette_items WHERE enabled = true ORDER BY weight DESC"
        private const val QUERY_UPDATE_NPC = "UPDATE roulette_config SET npc_id = ? WHERE id = 1"
        private const val QUERY_REMOVE_NPC = "UPDATE roulette_config SET npc_id = NULL WHERE id = 1"
        private const val QUERY_UPDATE_ENABLED = "UPDATE roulette_config SET enabled = ? WHERE id = 1"
        private const val QUERY_UPDATE_COST = "UPDATE roulette_config SET cost_type = ?, cost_amount = ? WHERE id = 1"
        private const val QUERY_INSERT_HISTORY = """
            INSERT INTO roulette_history
            (player_uuid, player_name, item_id, item_provider, item_identifier, cost_paid, probability, played_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
        """
        private const val QUERY_COUNT_PLAYS = "SELECT COUNT(*) FROM roulette_history WHERE player_uuid = ?"
    }

    init {
        loadConfig()
        loadItems()
    }

    /**
     * DB에서 룰렛 설정 로드
     */
    fun loadConfig() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            val rs = statement.executeQuery(QUERY_SELECT_CONFIG)

            if (rs.next()) {
                config = RouletteConfig(
                    id = rs.getInt("id"),
                    npcId = rs.getObject("npc_id") as? Int,
                    costType = CostType.valueOf(rs.getString("cost_type")),
                    costAmount = rs.getDouble("cost_amount"),
                    costItemType = rs.getString("cost_item_type"),
                    costItemAmount = rs.getInt("cost_item_amount"),
                    animationDuration = rs.getInt("animation_duration"),
                    enabled = rs.getBoolean("enabled"),
                    updatedAt = rs.getTimestamp("updated_at")
                )
            }
        }
    }

    /**
     * DB에서 룰렛 아이템 목록 로드
     */
    fun loadItems() {
        val loadedItems = mutableListOf<RouletteItem>()

        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            val rs = statement.executeQuery(QUERY_SELECT_ITEMS)

            while (rs.next()) {
                val item = RouletteItem(
                    id = rs.getInt("id"),
                    itemProvider = ItemProvider.valueOf(rs.getString("item_provider")),
                    itemIdentifier = rs.getString("item_identifier"),
                    itemDisplayName = rs.getString("item_display_name"),
                    itemAmount = rs.getInt("item_amount"),
                    itemData = rs.getString("item_data"),
                    weight = rs.getInt("weight"),
                    enabled = rs.getBoolean("enabled"),
                    createdAt = rs.getTimestamp("created_at"),
                    updatedAt = rs.getTimestamp("updated_at")
                )
                loadedItems.add(item)
            }
        }

        items = loadedItems
        plugin.logger.info("[Roulette] ${items.size}개의 룰렛 아이템을 로드했습니다.")
    }

    /**
     * 설정 및 아이템 리로드
     */
    fun reload() {
        loadConfig()
        loadItems()
        plugin.logger.info("[Roulette] 룰렛 설정이 리로드되었습니다.")
    }

    /**
     * 현재 설정 가져오기
     */
    fun getConfig(): RouletteConfig? = config

    /**
     * 활성화된 아이템 목록 가져오기
     */
    fun getItems(): List<RouletteItem> = items

    /**
     * 가중치 기반 랜덤 아이템 선택
     */
    fun selectRandomItem(): RouletteItem? {
        if (items.isEmpty()) return null

        val totalWeight = items.sumOf { it.weight }
        if (totalWeight <= 0) return null

        val randomValue = Random.nextInt(totalWeight)
        var currentWeight = 0

        for (item in items) {
            currentWeight += item.weight
            if (randomValue < currentWeight) {
                return item
            }
        }

        // 만약을 위한 폴백
        return items.firstOrNull()
    }

    /**
     * NPC ID 설정
     */
    fun setNpcId(npcId: Int): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_UPDATE_NPC)
                stmt.setInt(1, npcId)
                stmt.executeUpdate()

                // 메모리에도 반영
                config = config?.copy(npcId = npcId)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] NPC ID 설정 실패: ${e.message}")
            false
        }
    }

    /**
     * NPC ID 제거
     */
    fun removeNpcId(): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_REMOVE_NPC)
                stmt.executeUpdate()

                // 메모리에도 반영
                config = config?.copy(npcId = null)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] NPC ID 제거 실패: ${e.message}")
            false
        }
    }

    /**
     * 룰렛 활성화/비활성화 설정
     */
    fun setEnabled(enabled: Boolean): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_UPDATE_ENABLED)
                stmt.setBoolean(1, enabled)
                stmt.executeUpdate()

                // 메모리에도 반영
                config = config?.copy(enabled = enabled)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 활성화 설정 실패: ${e.message}")
            false
        }
    }

    /**
     * 비용 설정
     */
    fun setCost(costType: CostType, amount: Double): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_UPDATE_COST)
                stmt.setString(1, costType.name)
                stmt.setDouble(2, amount)
                stmt.executeUpdate()

                // 메모리에도 반영
                config = config?.copy(costType = costType, costAmount = amount)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 비용 설정 실패: ${e.message}")
            false
        }
    }

    /**
     * 룰렛 플레이 히스토리 저장
     */
    fun saveHistory(
        playerUuid: String,
        playerName: String,
        itemId: Int,
        itemProvider: String,
        itemIdentifier: String,
        costPaid: Double,
        probability: Double
    ): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_HISTORY)
                stmt.setString(1, playerUuid)
                stmt.setString(2, playerName)
                stmt.setInt(3, itemId)
                stmt.setString(4, itemProvider)
                stmt.setString(5, itemIdentifier)
                stmt.setDouble(6, costPaid)
                stmt.setDouble(7, probability)
                stmt.executeUpdate()
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 히스토리 저장 실패: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 플레이어의 룰렛 플레이 횟수 조회
     */
    fun getPlayerPlayCount(playerUuid: String): Int {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_COUNT_PLAYS)
                stmt.setString(1, playerUuid)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    rs.getInt(1)
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 플레이 횟수 조회 실패: ${e.message}")
            0
        }
    }

    /**
     * 룰렛이 활성화되어 있는지 확인
     */
    fun isEnabled(): Boolean = config?.enabled ?: false

    /**
     * NPC ID가 설정되어 있는지 확인
     */
    fun hasNpc(): Boolean = config?.npcId != null

    /**
     * 현재 NPC ID 가져오기
     */
    fun getNpcId(): Int? = config?.npcId
}

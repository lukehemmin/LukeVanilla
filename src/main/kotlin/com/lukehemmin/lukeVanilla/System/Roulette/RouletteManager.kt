package com.lukehemmin.lukeVanilla.System.Roulette

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Timestamp
import kotlin.random.Random

/**
 * 룰렛 시스템 관리 클래스 (다중 룰렛 지원)
 * - DB에서 여러 룰렛 설정 및 아이템 로드
 * - 확률 기반 아이템 선택
 * - 히스토리 저장
 * - NPC와 룰렛 매핑 관리
 */
class RouletteManager(
    private val plugin: JavaPlugin,
    private val database: Database
) {
    // 다중 룰렛 관리
    private val configs: MutableMap<Int, RouletteConfig> = mutableMapOf()
    private val itemsMap: MutableMap<Int, List<RouletteItem>> = mutableMapOf()
    private val npcRouletteMap: MutableMap<Int, Int> = mutableMapOf() // NPC ID -> Roulette ID

    companion object {
        // DB 쿼리 상수
        private const val QUERY_SELECT_ALL_CONFIGS = "SELECT * FROM roulette_config"
        private const val QUERY_SELECT_CONFIG_BY_ID = "SELECT * FROM roulette_config WHERE id = ?"
        private const val QUERY_SELECT_CONFIG_BY_NAME = "SELECT * FROM roulette_config WHERE roulette_name = ?"
        private const val QUERY_SELECT_ITEMS_BY_ROULETTE = "SELECT * FROM roulette_items WHERE roulette_id = ? AND enabled = true ORDER BY weight DESC"
        private const val QUERY_SELECT_ALL_NPC_MAPPINGS = "SELECT * FROM roulette_npc_mapping"
        private const val QUERY_SELECT_NPC_MAPPING = "SELECT * FROM roulette_npc_mapping WHERE npc_id = ?"
        private const val QUERY_INSERT_NPC_MAPPING = "INSERT INTO roulette_npc_mapping (npc_id, roulette_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE roulette_id = ?"
        private const val QUERY_DELETE_NPC_MAPPING = "DELETE FROM roulette_npc_mapping WHERE npc_id = ?"
        private const val QUERY_INSERT_CONFIG = "INSERT INTO roulette_config (roulette_name, cost_type, cost_amount, animation_duration, enabled) VALUES (?, ?, ?, ?, ?)"
        private const val QUERY_UPDATE_CONFIG = "UPDATE roulette_config SET cost_type = ?, cost_amount = ?, animation_duration = ?, enabled = ? WHERE id = ?"
        private const val QUERY_DELETE_CONFIG = "DELETE FROM roulette_config WHERE id = ?"
        private const val QUERY_UPDATE_ENABLED = "UPDATE roulette_config SET enabled = ? WHERE id = ?"
        private const val QUERY_INSERT_ITEM = "INSERT INTO roulette_items (roulette_id, item_provider, item_identifier, item_display_name, item_amount, weight, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)"
        private const val QUERY_UPDATE_ITEM_WEIGHT = "UPDATE roulette_items SET weight = ? WHERE id = ? AND roulette_id = ?"
        private const val QUERY_DELETE_ITEM = "DELETE FROM roulette_items WHERE id = ? AND roulette_id = ?"
        private const val QUERY_INSERT_HISTORY = """
            INSERT INTO roulette_history
            (roulette_id, player_uuid, player_name, item_id, item_provider, item_identifier, cost_paid, probability, played_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """
        private const val QUERY_COUNT_PLAYS = "SELECT COUNT(*) FROM roulette_history WHERE player_uuid = ? AND roulette_id = ?"
    }

    init {
        loadAllConfigs()
        loadAllItems()
        loadAllNPCMappings()
    }

    /**
     * DB에서 모든 룰렛 설정 로드
     */
    fun loadAllConfigs() {
        configs.clear()

        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            val rs = statement.executeQuery(QUERY_SELECT_ALL_CONFIGS)

            while (rs.next()) {
                val config = RouletteConfig(
                    id = rs.getInt("id"),
                    rouletteName = rs.getString("roulette_name"),
                    costType = CostType.valueOf(rs.getString("cost_type")),
                    costAmount = rs.getDouble("cost_amount"),
                    costItemType = rs.getString("cost_item_type"),
                    costItemAmount = rs.getInt("cost_item_amount"),
                    animationDuration = rs.getInt("animation_duration"),
                    enabled = rs.getBoolean("enabled"),
                    createdAt = rs.getTimestamp("created_at"),
                    updatedAt = rs.getTimestamp("updated_at")
                )
                configs[config.id] = config
            }
        }

        plugin.logger.info("[Roulette] ${configs.size}개의 룰렛 설정을 로드했습니다.")
    }

    /**
     * DB에서 모든 룰렛의 아이템 목록 로드
     */
    fun loadAllItems() {
        itemsMap.clear()

        configs.keys.forEach { rouletteId ->
            loadItems(rouletteId)
        }
    }

    /**
     * 특정 룰렛의 아이템 목록 로드
     */
    fun loadItems(rouletteId: Int) {
        val loadedItems = mutableListOf<RouletteItem>()

        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement(QUERY_SELECT_ITEMS_BY_ROULETTE)
            stmt.setInt(1, rouletteId)
            val rs = stmt.executeQuery()

            while (rs.next()) {
                val item = RouletteItem(
                    id = rs.getInt("id"),
                    rouletteId = rs.getInt("roulette_id"),
                    itemProvider = ItemProvider.valueOf(rs.getString("item_provider")),
                    itemIdentifier = rs.getString("item_identifier"),
                    itemDisplayName = rs.getString("item_display_name"),
                    itemAmount = rs.getInt("item_amount"),
                    itemData = rs.getString("item_data"),
                    weight = rs.getDouble("weight"),
                    enabled = rs.getBoolean("enabled"),
                    createdAt = rs.getTimestamp("created_at"),
                    updatedAt = rs.getTimestamp("updated_at")
                )
                loadedItems.add(item)
            }
        }

        itemsMap[rouletteId] = loadedItems
        plugin.logger.info("[Roulette] 룰렛 ID $rouletteId: ${loadedItems.size}개의 아이템을 로드했습니다.")
    }

    /**
     * DB에서 모든 NPC 매핑 로드
     */
    fun loadAllNPCMappings() {
        npcRouletteMap.clear()

        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            val rs = statement.executeQuery(QUERY_SELECT_ALL_NPC_MAPPINGS)

            while (rs.next()) {
                val npcId = rs.getInt("npc_id")
                val rouletteId = rs.getInt("roulette_id")
                npcRouletteMap[npcId] = rouletteId
            }
        }

        plugin.logger.info("[Roulette] ${npcRouletteMap.size}개의 NPC 매핑을 로드했습니다.")
    }

    /**
     * 설정 및 아이템 리로드
     */
    fun reload() {
        loadAllConfigs()
        loadAllItems()
        loadAllNPCMappings()
        plugin.logger.info("[Roulette] 모든 룰렛 설정이 리로드되었습니다.")
    }

    // ==================== 룰렛 CRUD ====================

    /**
     * 새로운 룰렛 생성
     */
    fun createRoulette(
        name: String,
        costType: CostType = CostType.MONEY,
        costAmount: Double = 1000.0,
        animationDuration: Int = 100,
        enabled: Boolean = true
    ): Int? {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_CONFIG, java.sql.Statement.RETURN_GENERATED_KEYS)
                stmt.setString(1, name)
                stmt.setString(2, costType.name)
                stmt.setDouble(3, costAmount)
                stmt.setInt(4, animationDuration)
                stmt.setBoolean(5, enabled)
                stmt.executeUpdate()

                val rs = stmt.generatedKeys
                if (rs.next()) {
                    val newId = rs.getInt(1)
                    loadAllConfigs()
                    newId
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 룰렛 생성 실패: ${e.message}")
            null
        }
    }

    /**
     * 룰렛 삭제
     */
    fun deleteRoulette(rouletteId: Int): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_DELETE_CONFIG)
                stmt.setInt(1, rouletteId)
                stmt.executeUpdate()

                configs.remove(rouletteId)
                itemsMap.remove(rouletteId)
                npcRouletteMap.entries.removeIf { it.value == rouletteId }
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 룰렛 삭제 실패: ${e.message}")
            false
        }
    }

    /**
     * 룰렛 설정 업데이트
     */
    fun updateRouletteConfig(
        rouletteId: Int,
        costType: CostType,
        costAmount: Double,
        animationDuration: Int,
        enabled: Boolean
    ): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_UPDATE_CONFIG)
                stmt.setString(1, costType.name)
                stmt.setDouble(2, costAmount)
                stmt.setInt(3, animationDuration)
                stmt.setBoolean(4, enabled)
                stmt.setInt(5, rouletteId)
                stmt.executeUpdate()

                loadAllConfigs()
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 룰렛 설정 업데이트 실패: ${e.message}")
            false
        }
    }

    // ==================== 룰렛 조회 ====================

    /**
     * 모든 룰렛 가져오기
     */
    fun getAllRoulettes(): List<RouletteConfig> = configs.values.toList()

    /**
     * ID로 룰렛 가져오기
     */
    fun getRouletteById(rouletteId: Int): RouletteConfig? = configs[rouletteId]

    /**
     * 이름으로 룰렛 가져오기
     */
    fun getRouletteByName(name: String): RouletteConfig? {
        return configs.values.find { it.rouletteName.equals(name, ignoreCase = true) }
    }

    /**
     * NPC ID로 룰렛 가져오기
     */
    fun getRouletteByNPC(npcId: Int): RouletteConfig? {
        val rouletteId = npcRouletteMap[npcId] ?: return null
        return configs[rouletteId]
    }

    /**
     * 특정 룰렛의 아이템 목록 가져오기
     */
    fun getItems(rouletteId: Int): List<RouletteItem> = itemsMap[rouletteId] ?: emptyList()

    // ==================== NPC 매핑 ====================

    /**
     * NPC를 룰렛에 매핑
     */
    fun setNPCMapping(npcId: Int, rouletteId: Int): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_NPC_MAPPING)
                stmt.setInt(1, npcId)
                stmt.setInt(2, rouletteId)
                stmt.setInt(3, rouletteId)
                stmt.executeUpdate()

                npcRouletteMap[npcId] = rouletteId
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] NPC 매핑 실패: ${e.message}")
            false
        }
    }

    /**
     * NPC 매핑 제거
     */
    fun removeNPCMapping(npcId: Int): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_DELETE_NPC_MAPPING)
                stmt.setInt(1, npcId)
                stmt.executeUpdate()

                npcRouletteMap.remove(npcId)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] NPC 매핑 제거 실패: ${e.message}")
            false
        }
    }

    /**
     * NPC에 매핑된 룰렛 ID 가져오기
     */
    fun getRouletteIdByNPC(npcId: Int): Int? = npcRouletteMap[npcId]

    /**
     * 모든 NPC 매핑 가져오기
     */
    fun getAllNPCMappings(): Map<Int, Int> = npcRouletteMap.toMap()

    // ==================== 아이템 관리 ====================

    /**
     * 아이템 추가
     */
    fun addItem(
        rouletteId: Int,
        itemProvider: ItemProvider,
        itemIdentifier: String,
        displayName: String?,
        amount: Int,
        weight: Double,
        enabled: Boolean = true
    ): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_ITEM)
                stmt.setInt(1, rouletteId)
                stmt.setString(2, itemProvider.name)
                stmt.setString(3, itemIdentifier)
                stmt.setString(4, displayName)
                stmt.setInt(5, amount)
                stmt.setDouble(6, weight)
                stmt.setBoolean(7, enabled)
                stmt.executeUpdate()

                loadItems(rouletteId)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 아이템 추가 실패: ${e.message}")
            false
        }
    }

    /**
     * 아이템 가중치 수정
     */
    fun updateItemWeight(itemId: Int, rouletteId: Int, weight: Double): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_UPDATE_ITEM_WEIGHT)
                stmt.setDouble(1, weight)
                stmt.setInt(2, itemId)
                stmt.setInt(3, rouletteId)
                stmt.executeUpdate()

                loadItems(rouletteId)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 아이템 가중치 수정 실패: ${e.message}")
            false
        }
    }

    /**
     * 아이템 삭제
     */
    fun deleteItem(itemId: Int, rouletteId: Int): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_DELETE_ITEM)
                stmt.setInt(1, itemId)
                stmt.setInt(2, rouletteId)
                stmt.executeUpdate()

                loadItems(rouletteId)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 아이템 삭제 실패: ${e.message}")
            false
        }
    }

    // ==================== 확률 계산 ====================

    /**
     * 가중치 기반 랜덤 아이템 선택 (소수점 가중치 지원)
     */
    fun selectRandomItem(rouletteId: Int): RouletteItem? {
        val items = getItems(rouletteId)
        if (items.isEmpty()) return null

        val totalWeight = items.sumOf { it.weight }
        if (totalWeight <= 0.0) return null

        val randomValue = Random.nextDouble(totalWeight)
        var currentWeight = 0.0

        for (item in items) {
            currentWeight += item.weight
            if (randomValue < currentWeight) {
                return item
            }
        }

        // 만약을 위한 폴백
        return items.firstOrNull()
    }

    // ==================== 히스토리 ====================

    /**
     * 룰렛 플레이 히스토리 저장
     */
    fun saveHistory(
        rouletteId: Int,
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
                stmt.setInt(1, rouletteId)
                stmt.setString(2, playerUuid)
                stmt.setString(3, playerName)
                stmt.setInt(4, itemId)
                stmt.setString(5, itemProvider)
                stmt.setString(6, itemIdentifier)
                stmt.setDouble(7, costPaid)
                stmt.setDouble(8, probability)
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
     * 플레이어의 특정 룰렛 플레이 횟수 조회
     */
    fun getPlayerPlayCount(playerUuid: String, rouletteId: Int): Int {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_COUNT_PLAYS)
                stmt.setString(1, playerUuid)
                stmt.setInt(2, rouletteId)
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

    // ==================== 유틸리티 ====================

    /**
     * 특정 룰렛이 활성화되어 있는지 확인
     */
    fun isEnabled(rouletteId: Int): Boolean = configs[rouletteId]?.enabled ?: false

    /**
     * 룰렛 활성화/비활성화
     */
    fun setEnabled(rouletteId: Int, enabled: Boolean): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_UPDATE_ENABLED)
                stmt.setBoolean(1, enabled)
                stmt.setInt(2, rouletteId)
                stmt.executeUpdate()

                loadAllConfigs()
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 활성화 설정 실패: ${e.message}")
            false
        }
    }
}

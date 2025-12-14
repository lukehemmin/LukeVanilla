package com.lukehemmin.lukeVanilla.System.ScrollRoulette

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Roulette.ItemProvider
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

/**
 * 스크롤 룰렛 시스템 관리 클래스
 * - DB에서 스크롤 룰렛 설정 및 아이템 로드
 * - 확률 기반 아이템 선택
 * - 히스토리 저장
 * - 플레이어별 세션 관리 (중복 실행 방지)
 */
class ScrollRouletteManager(
    private val plugin: JavaPlugin,
    private val database: Database
) {
    // 스크롤 룰렛 설정 관리
    private val configs: MutableMap<Int, ScrollRouletteConfig> = mutableMapOf()
    private val itemsMap: MutableMap<Int, List<ScrollRouletteItem>> = mutableMapOf()

    // 스크롤 아이템 코드 → 룰렛 ID 매핑
    private val scrollItemMap: MutableMap<String, Int> = mutableMapOf()

    // 플레이어별 활성 세션 관리 (중복 실행 방지)
    private val activeSessions: MutableMap<UUID, ScrollRouletteGUI> = mutableMapOf()

    companion object {
        // DB 쿼리 상수
        private const val QUERY_SELECT_ALL_CONFIGS = "SELECT * FROM scroll_roulette_config WHERE enabled = true"
        private const val QUERY_SELECT_CONFIG_BY_ID = "SELECT * FROM scroll_roulette_config WHERE id = ?"
        private const val QUERY_SELECT_ITEMS_BY_ROULETTE = "SELECT * FROM scroll_roulette_items WHERE roulette_id = ? AND enabled = true ORDER BY probability DESC"
        
        private const val QUERY_INSERT_CONFIG = """
            INSERT INTO scroll_roulette_config (roulette_name, item_provider, item_code, enabled) 
            VALUES (?, ?, ?, ?)
        """
        private const val QUERY_DELETE_CONFIG = "DELETE FROM scroll_roulette_config WHERE id = ?"
        private const val QUERY_UPDATE_ENABLED = "UPDATE scroll_roulette_config SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        
        private const val QUERY_INSERT_ITEM = """
            INSERT INTO scroll_roulette_items (roulette_id, item_provider, item_code, item_display_name, item_amount, probability, enabled) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """
        private const val QUERY_DELETE_ITEM = "DELETE FROM scroll_roulette_items WHERE id = ? AND roulette_id = ?"
        private const val QUERY_UPDATE_ITEM = """
            UPDATE scroll_roulette_items 
            SET item_provider = ?, item_code = ?, item_display_name = ?, item_amount = ?, probability = ?, enabled = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ? AND roulette_id = ?
        """
        
        private const val QUERY_INSERT_HISTORY = """
            INSERT INTO scroll_roulette_history
            (roulette_id, player_uuid, player_name, item_id, item_provider, item_code, win_probability, played_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
        """
    }

    init {
        createTables()
        loadAllConfigs()
        loadAllItems()
    }

    /**
     * DB 테이블 생성
     */
    private fun createTables() {
        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                // scroll_roulette_config 테이블
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS scroll_roulette_config (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        roulette_name VARCHAR(100) NOT NULL,
                        item_provider VARCHAR(20) NOT NULL DEFAULT 'NEXO',
                        item_code VARCHAR(100) NOT NULL,
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY unique_item (item_provider, item_code)
                    )
                """.trimIndent())

                // scroll_roulette_items 테이블
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS scroll_roulette_items (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        roulette_id INT NOT NULL,
                        item_provider VARCHAR(20) NOT NULL DEFAULT 'NEXO',
                        item_code VARCHAR(100) NOT NULL,
                        item_display_name VARCHAR(100),
                        item_amount INT NOT NULL DEFAULT 1,
                        probability DOUBLE NOT NULL DEFAULT 10.0,
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (roulette_id) REFERENCES scroll_roulette_config(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // scroll_roulette_history 테이블
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS scroll_roulette_history (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        roulette_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        item_id INT NOT NULL,
                        item_provider VARCHAR(20) NOT NULL,
                        item_code VARCHAR(100) NOT NULL,
                        win_probability DOUBLE NOT NULL,
                        played_at DATETIME NOT NULL,
                        INDEX idx_player_uuid (player_uuid),
                        INDEX idx_roulette_id (roulette_id)
                    )
                """.trimIndent())
            }
        }
        plugin.logger.info("[ScrollRoulette] 데이터베이스 테이블 초기화 완료")
    }

    /**
     * DB에서 모든 스크롤 룰렛 설정 로드
     */
    fun loadAllConfigs() {
        configs.clear()
        scrollItemMap.clear()

        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(QUERY_SELECT_ALL_CONFIGS).use { rs ->
                    while (rs.next()) {
                        val config = ScrollRouletteConfig(
                            id = rs.getInt("id"),
                            rouletteName = rs.getString("roulette_name"),
                            itemProvider = ItemProvider.valueOf(rs.getString("item_provider")),
                            itemCode = rs.getString("item_code"),
                            enabled = rs.getBoolean("enabled"),
                            createdAt = rs.getTimestamp("created_at"),
                            updatedAt = rs.getTimestamp("updated_at")
                        )
                        configs[config.id] = config
                        
                        // 스크롤 아이템 매핑 등록
                        val key = "${config.itemProvider.name}:${config.itemCode}"
                        scrollItemMap[key] = config.id
                    }
                }
            }
        }

        plugin.logger.info("[ScrollRoulette] ${configs.size}개의 스크롤 룰렛 설정을 로드했습니다.")
    }

    /**
     * DB에서 모든 스크롤 룰렛의 아이템 목록 로드
     */
    fun loadAllItems() {
        itemsMap.clear()

        configs.keys.forEach { rouletteId ->
            loadItems(rouletteId)
        }
    }

    /**
     * 특정 스크롤 룰렛의 아이템 목록 로드
     */
    fun loadItems(rouletteId: Int) {
        val loadedItems = mutableListOf<ScrollRouletteItem>()

        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_SELECT_ITEMS_BY_ROULETTE).use { stmt ->
                stmt.setInt(1, rouletteId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val item = ScrollRouletteItem(
                            id = rs.getInt("id"),
                            rouletteId = rs.getInt("roulette_id"),
                            itemProvider = ItemProvider.valueOf(rs.getString("item_provider")),
                            itemCode = rs.getString("item_code"),
                            itemDisplayName = rs.getString("item_display_name"),
                            itemAmount = rs.getInt("item_amount"),
                            probability = rs.getDouble("probability"),
                            enabled = rs.getBoolean("enabled"),
                            createdAt = rs.getTimestamp("created_at"),
                            updatedAt = rs.getTimestamp("updated_at")
                        )
                        loadedItems.add(item)
                    }
                }
            }
        }

        itemsMap[rouletteId] = loadedItems
        plugin.logger.info("[ScrollRoulette] 룰렛 ID $rouletteId: ${loadedItems.size}개의 아이템을 로드했습니다.")
    }

    /**
     * 설정 및 아이템 리로드
     */
    fun reload() {
        plugin.logger.info("[ScrollRoulette] ========== 리로드 시작 ==========")
        
        loadAllConfigs()
        plugin.logger.info("[ScrollRoulette] 설정 로드 완료 - configs 개수: ${configs.size}")
        
        loadAllItems()
        plugin.logger.info("[ScrollRoulette] 아이템 로드 완료 - itemsMap 개수: ${itemsMap.size}")
        
        plugin.logger.info("[ScrollRoulette] ========== 리로드 완료 ==========")
    }

    // ==================== 스크롤 룰렛 CRUD ====================

    /**
     * 새로운 스크롤 룰렛 생성
     */
    fun createRoulette(
        name: String,
        itemProvider: ItemProvider,
        itemCode: String,
        enabled: Boolean = true
    ): Int? {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_CONFIG, java.sql.Statement.RETURN_GENERATED_KEYS)
                stmt.setString(1, name)
                stmt.setString(2, itemProvider.name)
                stmt.setString(3, itemCode)
                stmt.setBoolean(4, enabled)
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
            plugin.logger.warning("[ScrollRoulette] 스크롤 룰렛 생성 실패: ${e.message}")
            null
        }
    }

    /**
     * 스크롤 룰렛 삭제
     */
    fun deleteRoulette(rouletteId: Int): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_DELETE_CONFIG)
                stmt.setInt(1, rouletteId)
                stmt.executeUpdate()

                configs.remove(rouletteId)
                itemsMap.remove(rouletteId)
                scrollItemMap.entries.removeIf { it.value == rouletteId }
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ScrollRoulette] 스크롤 룰렛 삭제 실패: ${e.message}")
            false
        }
    }

    // ==================== 스크롤 룰렛 조회 ====================

    /**
     * 모든 스크롤 룰렛 가져오기
     */
    fun getAllRoulettes(): List<ScrollRouletteConfig> = configs.values.toList()

    /**
     * ID로 스크롤 룰렛 가져오기
     */
    fun getRouletteById(rouletteId: Int): ScrollRouletteConfig? = configs[rouletteId]

    /**
     * 스크롤 아이템으로 룰렛 ID 가져오기
     */
    fun getRouletteIdByScrollItem(provider: ItemProvider, itemCode: String): Int? {
        val key = "${provider.name}:$itemCode"
        return scrollItemMap[key]
    }

    /**
     * 특정 스크롤 룰렛의 아이템 목록 가져오기
     */
    fun getItems(rouletteId: Int): List<ScrollRouletteItem> = itemsMap[rouletteId] ?: emptyList()

    // ==================== 아이템 관리 ====================

    /**
     * 당첨 아이템 추가
     */
    fun addItem(
        rouletteId: Int,
        itemProvider: ItemProvider,
        itemCode: String,
        displayName: String?,
        amount: Int,
        probability: Double,
        enabled: Boolean = true
    ): Boolean {
        return try {
            // Nexo 아이템이고 displayName이 비어있으면 자동으로 가져오기
            val finalDisplayName = if (itemProvider == ItemProvider.NEXO && displayName.isNullOrBlank()) {
                getNexoItemDisplayName(itemCode) ?: displayName
            } else {
                displayName
            }

            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_ITEM)
                stmt.setInt(1, rouletteId)
                stmt.setString(2, itemProvider.name)
                stmt.setString(3, itemCode)
                stmt.setString(4, finalDisplayName)
                stmt.setInt(5, amount)
                stmt.setDouble(6, probability)
                stmt.setBoolean(7, enabled)
                stmt.executeUpdate()

                loadItems(rouletteId)
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ScrollRoulette] 아이템 추가 실패: ${e.message}")
            false
        }
    }

    /**
     * Nexo 아이템의 display name 가져오기
     */
    private fun getNexoItemDisplayName(itemId: String): String? {
        return try {
            val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val method = nexoClass.getMethod("itemFromId", String::class.java)
            val itemBuilder = method.invoke(null, itemId)

            if (itemBuilder != null) {
                val buildMethod = itemBuilder.javaClass.getMethod("build")
                val itemStack = buildMethod.invoke(itemBuilder) as? org.bukkit.inventory.ItemStack
                itemStack?.itemMeta?.displayName
            } else {
                null
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ScrollRoulette] Nexo 아이템 이름 가져오기 실패 ($itemId): ${e.message}")
            null
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
            plugin.logger.warning("[ScrollRoulette] 아이템 삭제 실패: ${e.message}")
            false
        }
    }

    // ==================== 확률 계산 ====================

    /**
     * 확률 기반 랜덤 아이템 선택
     */
    fun selectRandomItem(rouletteId: Int): ScrollRouletteItem? {
        val items = getItems(rouletteId)
        if (items.isEmpty()) return null

        val totalProbability = items.sumOf { it.probability }
        if (totalProbability <= 0.0) return null

        val randomValue = Random.nextDouble(totalProbability)
        var currentProbability = 0.0

        for (item in items) {
            currentProbability += item.probability
            if (randomValue < currentProbability) {
                return item
            }
        }

        // 만약을 위한 폴백
        return items.firstOrNull()
    }

    /**
     * 실제 당첨 확률 계산 (전체 확률 대비)
     */
    fun calculateWinProbability(rouletteId: Int, item: ScrollRouletteItem): Double {
        val items = getItems(rouletteId)
        val totalProbability = items.sumOf { it.probability }
        
        return if (totalProbability > 0.0) {
            (item.probability / totalProbability) * 100.0
        } else {
            0.0
        }
    }

    // ==================== 히스토리 ====================

    /**
     * 스크롤 룰렛 플레이 히스토리 저장
     */
    fun saveHistory(
        rouletteId: Int,
        playerUuid: String,
        playerName: String,
        item: ScrollRouletteItem,
        winProbability: Double
    ): Boolean {
        return try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(QUERY_INSERT_HISTORY)
                stmt.setInt(1, rouletteId)
                stmt.setString(2, playerUuid)
                stmt.setString(3, playerName)
                stmt.setInt(4, item.id)
                stmt.setString(5, item.itemProvider.name)
                stmt.setString(6, item.itemCode)
                stmt.setDouble(7, winProbability)
                stmt.executeUpdate()
                true
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ScrollRoulette] 히스토리 저장 실패: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // ==================== 세션 관리 (중복 실행 방지) ====================

    /**
     * 플레이어의 활성 세션 시작
     */
    fun startSession(player: Player, gui: ScrollRouletteGUI): Boolean {
        if (activeSessions.containsKey(player.uniqueId)) {
            plugin.logger.warning("[ScrollRoulette] 플레이어 ${player.name}는 이미 스크롤 룰렛을 진행 중입니다.")
            return false
        }

        activeSessions[player.uniqueId] = gui
        plugin.logger.info("[ScrollRoulette] 플레이어 ${player.name}의 스크롤 룰렛 세션 시작")
        return true
    }

    /**
     * 플레이어의 활성 세션 가져오기
     */
    fun getSession(player: Player): ScrollRouletteGUI? {
        return activeSessions[player.uniqueId]
    }

    /**
     * 플레이어의 활성 세션 종료
     */
    fun endSession(player: Player) {
        activeSessions.remove(player.uniqueId)
        plugin.logger.info("[ScrollRoulette] 플레이어 ${player.name}의 스크롤 룰렛 세션 종료")
    }

    /**
     * 플레이어가 진행 중인 세션이 있는지 확인
     */
    fun hasActiveSession(player: Player): Boolean {
        return activeSessions.containsKey(player.uniqueId)
    }

    /**
     * 모든 활성 세션 정리 (플러그인 비활성화 시)
     */
    fun cleanupAllSessions() {
        activeSessions.values.forEach { gui ->
            gui.forceStop()
        }
        activeSessions.clear()
    }
}

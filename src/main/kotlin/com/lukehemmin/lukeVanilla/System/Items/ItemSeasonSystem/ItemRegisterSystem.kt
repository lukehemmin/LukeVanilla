package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.sql.SQLException
import java.util.*

class ItemRegisterSystem {
    private lateinit var plugin: Main
    private lateinit var database: Database
    private lateinit var ownerKey: NamespacedKey
    
    // 시즌별 아이템 활성화 여부 설정
    private var isHalloweenEnabled = false
    private var isChristmasEnabled = true
    private var isValentineEnabled = true
    
    // 이벤트 타입 목록
    private val eventTypes = listOf("할로윈", "크리스마스", "발렌타인")
    
    // 할로윈 아이템 목록
    private val halloweenItems = mapOf(
        "halloween_sword" to "호박의 빛 검",
        "halloween_pickaxe" to "호박 곡괭이",
        "halloween_axe" to "호박 도끼",
        "halloween_shovel" to "호박 삽",
        "halloween_hoe" to "호박 괭이",
        "halloween_bow" to "호박의 마법 활",
        "halloween_fishing_rod" to "호박의 낚시대",
        "halloween_hammer" to "호박의 철퇴",
        "halloween_hat" to "호박의 마법 모자",
        "halloween_scythe" to "호박의 낫",
        "halloween_spear" to "호박의 창"
    )
    
    // 크리스마스 아이템 목록
    private val christmasItems = mapOf(
        "merry_christmas_sword" to "크리스마스 검",
        "merry_christmas_pickaxe" to "크리스마스 곡괭이",
        "merry_christmas_axe" to "크리스마스 도끼",
        "merry_christmas_shovel" to "크리스마스 삽",
        "merry_christmas_hoe" to "크리스마스 괭이",
        "merry_christmas_bow" to "크리스마스 활",
        "merry_christmas_crossbow" to "크리스마스 석궁",
        "merry_christmas_fishing_rod" to "크리스마스 낚시대",
        "merry_christmas_hammer" to "크리스마스 철퇴",
        "merry_christmas_shield" to "크리스마스 방패",
        "merry_christmas_head" to "크리스마스 모자",
        "merrychristmas_helmet" to "크리스마스 투구",
        "merrychristmas_chestplate" to "크리스마스 흉갑",
        "merrychristmas_leggings" to "크리스마스 레깅스",
        "merrychristmas_boots" to "크리스마스 부츠"
    )
    
    // 발렌타인 아이템 목록
    private val valentineItems = mapOf(
        "valentine_sword" to "발렌타인 러브 블레이드",
        "valentine_pickaxe" to "발렌타인 러브 마이너",
        "valentine_axe" to "발렌타인 하트 크래셔",
        "valentine_shovel" to "발렌타인 러브 디거",
        "valentine_hoe" to "발렌타인 가드너의 도구",
        "valentine_fishing_rod" to "발렌타인 큐피드 낚싯대",
        "valentine_bow" to "발렌타인 큐피드의 활",
        "valentine_crossbow" to "발렌타인 하트 석궁",
        "valentine_hammer" to "발렌타인 핑크 크래셔",
        "valentine_helmet" to "발렌타인 하트 가디언 헬멧",
        "valentine_chestplate" to "발렌타인 체스트 오브 러브",
        "valentine_leggings" to "발렌타인 로맨틱 레깅스",
        "valentine_boots" to "발렌타인 러브 트레커",
        "valentine_head" to "발렌타인 러버스 캡",
        "valentine_shield" to "발렌타인 방패"
    )
    
    init {
        val pluginManager = Bukkit.getPluginManager()
        val pluginInstance = pluginManager.getPlugin("LukeVanilla")
        if (pluginInstance is Main) {
            plugin = pluginInstance
            database = plugin.database
            ownerKey = NamespacedKey(plugin, "owner")
        }
    }
    
    // 아이템 코드에서 DB 컬럼 이름 추출
    private fun getColumnName(itemId: String): String {
        return when {
            itemId.startsWith("halloween_") -> itemId.removePrefix("halloween_")
            itemId.startsWith("merry_christmas_") -> itemId.removePrefix("merry_christmas_")
            itemId.startsWith("valentine_") -> itemId.removePrefix("valentine_")
            else -> ""
        }
    }
    
    // 아이템 코드에서 이벤트 타입 추출
    private fun getEventType(itemId: String): String {
        return when {
            itemId.startsWith("halloween_") -> "할로윈"
            itemId.startsWith("merry_christmas_") || itemId.startsWith("merrychristmas_") -> "크리스마스"
            itemId.startsWith("valentine_") -> "발렌타인"
            else -> ""
        }
    }
    
    // 아이템 표시 이름 가져오기
    private fun getItemDisplayName(itemId: String): String {
        return when {
            halloweenItems.containsKey(itemId) -> halloweenItems[itemId] ?: "알 수 없는 할로윈 아이템"
            christmasItems.containsKey(itemId) -> christmasItems[itemId] ?: "알 수 없는 크리스마스 아이템"
            valentineItems.containsKey(itemId) -> valentineItems[itemId] ?: "알 수 없는 발렌타인 아이템"
            else -> "알 수 없는 아이템"
        }
    }
    
    // 이벤트 타입 테이블 이름 가져오기
    private fun getTableName(eventType: String): String {
        return when (eventType) {
            "할로윈" -> "Halloween_Item_Owner"
            "크리스마스" -> "Christmas_Item_Owner"
            "발렌타인" -> "Valentine_Item_Owner"
            else -> ""
        }
    }
    
    // 아이템을 데이터베이스에 등록
    private fun registerItemToDatabase(player: Player, itemId: String): Boolean {
        plugin.logger.info("[DEBUG] registerItemToDatabase 호출됨. itemId: '$itemId'") // 원본 itemId 로그
        return try {
            val eventType = getEventType(itemId)
            plugin.logger.info("[DEBUG] eventType: '$eventType'") // eventType 로그

            val tableName = getTableName(eventType)
            plugin.logger.info("[DEBUG] tableName: '$tableName'") // tableName 로그

            var columnName = itemId // 기본적으로 전체 itemId를 사용
            
            // 시즌 이벤트 아이템의 경우, 접두사를 제거하고 실제 아이템 타입만 컬럼명으로 사용
            val seasonPrefixes = listOf("halloween_", "merry_christmas_", "merrychristmas_", "valentine_")
            for (prefix in seasonPrefixes) {
                if (itemId.startsWith(prefix)) {
                    columnName = itemId.substring(prefix.length)
                    break
                }
            }
            plugin.logger.info("[DEBUG] 최종 columnName: '$columnName'") // 최종 columnName 로그
            
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                
                val selectStmt = connection.prepareStatement("SELECT * FROM $tableName WHERE UUID = ?")
                selectStmt.setString(1, uuid)
                val resultSet = selectStmt.executeQuery()
                
                if (resultSet.next()) {
                    val sql = "UPDATE $tableName SET `$columnName` = 1 WHERE UUID = ?"
                    plugin.logger.info("[DEBUG] 실행될 UPDATE SQL: $sql") // 실행될 SQL 로그
                    val updateStmt = connection.prepareStatement(sql)
                    updateStmt.setString(1, uuid)
                    updateStmt.executeUpdate()
                } else {
                    val sql = "INSERT INTO $tableName (UUID, `$columnName`) VALUES (?, 1)"
                    plugin.logger.info("[DEBUG] 실행될 INSERT SQL: $sql") // 실행될 SQL 로그
                    val insertStmt = connection.prepareStatement(sql)
                    insertStmt.setString(1, uuid)
                    insertStmt.executeUpdate()
                }
            }
            true
        } catch (e: Exception) {
            plugin.logger.warning("데이터베이스 작업 중 오류 발생: ${e.message}")
            false
        }
    }
    
    // 아이템 소유권 DB 확인
    private fun checkItemOwnershipInDatabase(playerUuid: String, itemId: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                // Player_Items_State 테이블에서 확인 (registerItemToDatabase와 일관성 유지)
                val query = "SELECT State FROM Player_Items_State WHERE UUID = ? AND ItemID = ?"
                connection.prepareStatement(query).use { pstmt ->
                    pstmt.setString(1, playerUuid)
                    pstmt.setString(2, itemId)
                    val rs = pstmt.executeQuery()
                    if (rs.next()) {
                        return rs.getString("State") == "OWNED"
                    }
                }
            }
            false // 레코드가 없으면 소유하지 않은 것으로 간주
        } catch (e: SQLException) {
            plugin.logger.severe("DB에서 아이템 소유권 확인 중 오류 발생 (UUID: $playerUuid, ItemID: $itemId): ${e.message}")
            false // 오류 발생 시 안전하게 false 반환
        }
    }
    
    // 아이템이 DB에 전역적으로 등록되어 있는지 확인하고 소유자 UUID 반환
    private fun isItemGloballyRegisteredInDatabase(itemId: String): String? {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT UUID FROM Player_Items_State WHERE ItemID = ? AND State = 'OWNED' LIMIT 1"
                connection.prepareStatement(query).use { pstmt ->
                    pstmt.setString(1, itemId)
                    val rs = pstmt.executeQuery()
                    if (rs.next()) {
                        return rs.getString("UUID")
                    }
                }
            }
            null // 등록된 기록이 없음
        } catch (e: SQLException) {
            plugin.logger.severe("DB에서 아이템 전역 등록 상태 확인 중 오류 발생 (ItemID: $itemId): ${e.message}")
            null // 오류 발생 시 null 반환하여 등록 시도 가능하게 둘 수도 있으나, 여기서는 등록 안된 것으로 처리
        }
    }
    
    // 아이템 등록 메인 메서드
    fun registerItem(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage("§c사용법: /아이템 등록 [시즌명(할로윈/크리스마스/발렌타인) 또는 전체]")
            return true
        }

        val targetSeasonInput = args[0].lowercase()
        val targetSeasonName = when (targetSeasonInput) {
            "할로윈" -> "할로윈"
            "크리스마스" -> "크리스마스"
            "발렌타인" -> "발렌타인"
            "전체" -> "" // 전체 시즌 아이템을 대상으로 함
            else -> {
                player.sendMessage("§c올바르지 않은 시즌명입니다. [할로윈/크리스마스/발렌타인/전체] 중에서 선택해주세요.")
                return true
            }
        }

        if (targetSeasonName.isNotEmpty() && !isSeasonEnabled(targetSeasonName)) {
            player.sendMessage("§c현재 §e${targetSeasonName} §c시즌 아이템은 등록이 비활성화되어 있습니다.")
            return true
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val registeredItems = mutableListOf<String>()
                val alreadyOwnItems = mutableListOf<String>()
                val newlyDbRegisteredForSelfItems = mutableListOf<String>()
                val alreadyRegisteredItems = mutableMapOf<String, String>()
                val processedNexoIdsThisSession = mutableSetOf<String>()

                for (item in player.inventory.contents) {
                    if (item == null || item.type == Material.AIR) continue

                    val nexoId = NexoItems.idFromItem(item) ?: continue // 수정된 부분
                    if (processedNexoIdsThisSession.contains(nexoId)) continue // 이미 이번 세션에서 처리된 아이템

                    val eventType = getEventType(nexoId)
                    if (eventType.isEmpty()) continue // 시즌 아이템이 아니면 건너뜀

                    // 1. targetSeasonName이 설정되어 있고, 현재 아이템(nexoId)이 해당 시즌 아이템인지 확인
                    if (targetSeasonName.isNotEmpty() && getTableName(eventType).lowercase() != getTableName(targetSeasonName).lowercase()) {
                        continue // 현재 아이템이 선택된 시즌 아이템이 아니면 건너뜀
                    }

                    // 2. 시즌 활성화 여부 확인 (선택된 시즌이 없다면 개별 아이템의 시즌으로 확인)
                    val currentItemSeasonToEnableCheck = if (targetSeasonName.isNotEmpty()) targetSeasonName else eventType
                    if (!isSeasonEnabled(currentItemSeasonToEnableCheck)) {
                        continue
                    }

                    // 3. 전역 등록 확인 (Player_Items_State)
                    val globalOwnerUUID = isItemGloballyRegisteredInDatabase(nexoId)
                    if (globalOwnerUUID != null) {
                        if (globalOwnerUUID == player.uniqueId.toString()) {
                            alreadyOwnItems.add(nexoId)
                        } else {
                            val ownerName = Bukkit.getOfflinePlayer(UUID.fromString(globalOwnerUUID)).name ?: "알 수 없는 플레이어"
                            alreadyRegisteredItems.put(nexoId, ownerName)
                        }
                        processedNexoIdsThisSession.add(nexoId)
                        continue
                    }

                    // 4. 전역 등록 안됨 -> NBT 및 시즌별 DB 등록 시도
                    val itemMeta = item.itemMeta
                    if (itemMeta != null && itemMeta.persistentDataContainer.has(ownerKey, PersistentDataType.STRING)) {
                        val ownerNbtUuid = itemMeta.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
                        if (ownerNbtUuid == player.uniqueId.toString()) { // NBT 소유자가 현재 플레이어
                            // 전역 DB에는 없고, NBT는 내 소유. 시즌별 DB 상태 확인
                            if (checkItemOwnershipInDatabase(player.uniqueId.toString(), nexoId)) {
                                // 시즌별 DB에도 이미 소유 중 -> 'alreadyOwnItems' 처리
                                updatePlayerItemState(player.uniqueId, nexoId, "OWNED") // Player_Items_State 일관성 보장
                                alreadyOwnItems.add(nexoId)
                            } else {
                                // 시즌별 DB에는 없음 -> 신규 등록 절차 진행
                                if (registerItemToDatabase(player, nexoId)) { // 시즌별 DB에 등록
                                    if (updatePlayerItemState(player.uniqueId, nexoId, "OWNED")) { // Player_Items_State에 등록
                                        newlyDbRegisteredForSelfItems.add(nexoId)
                                    } else {
                                        plugin.logger.warning("Player_Items_State 업데이트 실패 (NBT 내꺼, 시즌DB 신규등록 후): $nexoId")
                                    }
                                } else {
                                    plugin.logger.warning("시즌DB 등록 실패 (NBT 내꺼, 시즌DB에 없었음): $nexoId")
                                }
                            }
                        } else { // NBT 소유자가 다른 플레이어
                            val ownerName = Bukkit.getOfflinePlayer(UUID.fromString(ownerNbtUuid)).name ?: "알 수 없는 플레이어"
                            alreadyRegisteredItems.put(nexoId, ownerName) // NBT는 다른 사람 것, 전역DB에는 없음
                        }
                    } else {
                        // NBT 없음 (새 아이템), 전역 DB에도 없음 -> 신규 등록 시도
                        if (registerItemToDatabase(player, nexoId)) {
                            if (updatePlayerItemState(player.uniqueId, nexoId, "OWNED")) {
                                val mutableMeta = item.itemMeta // 작업할 수 있는 메타데이터 복사본 가져오기
                                mutableMeta?.persistentDataContainer?.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
                                item.itemMeta = mutableMeta // 변경된 메타데이터 다시 설정
                                registeredItems.add(nexoId)
                            } else {
                                plugin.logger.warning("Player_Items_State 업데이트 실패 (신규 등록): $nexoId")
                            }
                        } else {
                            plugin.logger.warning("시즌DB 등록 실패 (신규 등록): $nexoId")
                        }
                    }
                    processedNexoIdsThisSession.add(nexoId)
                }
                
                // 모든 처리가 완료된 후 결과 메시지 출력 (메인 스레드에서 실행)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    var messageSent = false
                    if (registeredItems.isNotEmpty()) {
                        val displayNames = registeredItems.map { getItemDisplayName(it) }
                        player.sendMessage("§a다음 아이템들이 소유자로 등록되었습니다: §f${displayNames.joinToString(", ")}")
                        messageSent = true
                    }
                    if (alreadyOwnItems.isNotEmpty()) {
                        val displayNames = alreadyOwnItems.map { getItemDisplayName(it) }
                        player.sendMessage("§e이미 자신이 등록한 아이템입니다: §f${displayNames.joinToString(", ")}")
                        messageSent = true
                    }
                    if (newlyDbRegisteredForSelfItems.isNotEmpty()) {
                        val displayNames = newlyDbRegisteredForSelfItems.map { getItemDisplayName(it) }
                        player.sendMessage("§b다음 아이템들이 데이터베이스에 추가로 등록되었습니다: §f${displayNames.joinToString(", ")}")
                        messageSent = true
                    }
                    if (alreadyRegisteredItems.isNotEmpty()) {
                        alreadyRegisteredItems.forEach { (itemId, ownerName) ->
                            player.sendMessage("§c아이템 §f${getItemDisplayName(itemId)} §c은(는) §f$ownerName §c님이 이미 등록하였습니다.")
                        }
                        messageSent = true
                    }

                    if (!messageSent) {
                        player.sendMessage("§c등록할 수 있는 아이템이 없거나, 조건에 맞는 시즌 아이템이 인벤토리에 없습니다.")
                    }
                })
            } catch (e: Exception) {
                plugin.logger.warning("아이템 등록 처리 중 예외 발생: ${e.message}")
                e.printStackTrace()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("§c아이템 등록 중 오류가 발생했습니다. 콘솔 로그를 확인해주세요.")
                })
            }
        })
        return true
    }

    // 시즌별 아이템 활성화 상태 설정 메서드
    fun setSeasonEnabled(season: String, enabled: Boolean): Boolean {
        return when (season.lowercase()) {
            "할로윈" -> { isHalloweenEnabled = enabled; true }
            "크리스마스" -> { isChristmasEnabled = enabled; true }
            "발렌타인" -> { isValentineEnabled = enabled; true }
            else -> false
        }
    }
    
    // 시즌별 아이템 활성화 상태 확인 메서드
    fun isSeasonEnabled(season: String): Boolean {
        return when (season.lowercase()) {
            "할로윈" -> isHalloweenEnabled
            "크리스마스" -> isChristmasEnabled
            "발렌타인" -> isValentineEnabled
            else -> false
        }
    }

    private fun updatePlayerItemState(playerUUID: UUID, itemId: String, state: String): Boolean {
        plugin.logger.info("[DEBUG] updatePlayerItemState 호출됨. playerUUID: $playerUUID, itemId: '$itemId', state: '$state'")
        val sql = "INSERT INTO Player_Items_State (UUID, ItemID, State) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE State = VALUES(State)"
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    stmt.setString(2, itemId)
                    stmt.setString(3, state)
                    stmt.executeUpdate()
                    plugin.logger.info("[DEBUG] Player_Items_State 업데이트 성공.")
                    true
                }
            }
        } catch (e: SQLException) {
            plugin.logger.warning("[LukeVanilla] Player_Items_State 업데이트 중 데이터베이스 오류 발생: ${e.message}")
            plugin.logger.warning("[LukeVanilla] 오류 발생 SQL: $sql, UUID: $playerUUID, ItemID: $itemId, State: $state")
            false
        }
    }
}

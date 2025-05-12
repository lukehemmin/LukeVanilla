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
    
    // 아이템이 DB에 전역적으로 등록되어 있는지 확인하고 소유자 UUID와 이름을 반환
    private fun isItemGloballyRegisteredInDatabase(itemId: String): Pair<String?, String?> {
        val eventType = getEventType(itemId)
        val tableName = getTableName(eventType)
        if (tableName.isEmpty()) {
            plugin.logger.warning("알 수 없는 이벤트 타입으로 테이블 이름을 가져올 수 없습니다: $eventType (아이템 ID: $itemId)")
            return Pair(null, null)
        }

        val sql = "SELECT OwnerUUID, OwnerName FROM $tableName WHERE ItemID = ? LIMIT 1"
        try {
            database.getConnection().use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, itemId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val ownerUUID = rs.getString("OwnerUUID")
                            val ownerName = rs.getString("OwnerName")
                            return Pair(ownerUUID, ownerName)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.warning("[$tableName] 테이블에서 아이템 전역 등록 확인 중 데이터베이스 오류 발생: ${e.message}")
        }
        return Pair(null, null)
    }
    
    // 아이템 등록 메인 메서드
    fun registerItem(player: Player): Boolean {
        val itemsInInventory = player.inventory.contents.filterNotNull().filter { it.type != Material.AIR }
        if (itemsInInventory.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("§c인벤토리에 아이템이 없습니다.")
            })
            return true
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { 
            var foundProcessableItem = false // 처리 가능한 아이템을 찾았는지 여부
            val processedNexoIdsThisSession = mutableSetOf<String>() // 이번 등록 세션에서 처리한 Nexo ID (중복 처리 방지)

            try {
                itemLoop@ for (itemStack in itemsInInventory) { // 변수명을 itemStack으로 변경
                    val initialItemMeta = itemStack.itemMeta ?: continue // 아이템 메타데이터 가져오기, 없으면 건너뛰기
                    val nexoId = NexoItems.idFromItem(itemStack) ?: continue // Nexo ID 가져오기, 없으면 건너뛰기
                    
                    if (processedNexoIdsThisSession.contains(nexoId)) continue // 이미 이 세션에서 처리된 ID면 건너뛰기

                    val eventType = getEventType(nexoId)
                    if (eventType.isEmpty() || !isSeasonEnabled(eventType)) {
                        continue // 유효하지 않은 이벤트 타입이거나 비활성화된 시즌이면 건너뛰기
                    }

                    foundProcessableItem = true // 처리 가능한 아이템 발견
                    val currentItemDisplayName = getItemDisplayName(nexoId) // 메시지용 아이템 이름

                    val pdc = initialItemMeta.persistentDataContainer
                    if (pdc.has(ownerKey, PersistentDataType.STRING)) {
                        // NBT에 ownerKey가 존재함
                        val ownerUuidFromNbtString = pdc.get(ownerKey, PersistentDataType.STRING)
                        if (ownerUuidFromNbtString == player.uniqueId.toString()) {
                            // NBT 소유자가 현재 플레이어
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                player.sendMessage("§e아이템 §f$currentItemDisplayName§e 은(는) 이미 내가 등록한 아이템입니다. (아이템 정보 기준)")
                            })
                            // (선택적) DB 일관성 확인 및 업데이트 로직 (예: Player_Items_State)
                            if (!checkItemOwnershipInDatabase(player.uniqueId.toString(), nexoId)) {
                                updatePlayerItemState(player.uniqueId, nexoId, "OWNED")
                            }
                        } else {
                            // NBT 소유자가 다른 플레이어
                            try {
                                val ownerUuidFromNbt = UUID.fromString(ownerUuidFromNbtString)
                                val ownerNameFromNbt = Bukkit.getOfflinePlayer(ownerUuidFromNbt).name ?: "알 수 없는 플레이어"
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    player.sendMessage("§c아이템 §f$currentItemDisplayName§c 은(는) 이미 §f$ownerNameFromNbt§c 님이 등록한 아이템입니다. (아이템 정보 기준)")
                                })
                            } catch (e: IllegalArgumentException) {
                                plugin.logger.warning("아이템 $nexoId NBT에 잘못된 UUID 형식($ownerUuidFromNbtString)이 있습니다.")
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    player.sendMessage("§c아이템 §f$currentItemDisplayName§c 의 등록자 정보(NBT)가 손상되었습니다.")
                                })
                            }
                        }
                    } else {
                        // NBT에 ownerKey가 없음 (새 아이템)
                        // 데이터베이스에서 이 nexoId가 이미 다른 사람에 의해 등록되었는지 확인
                        val (globalOwnerUUID, globalOwnerName) = isItemGloballyRegisteredInDatabase(nexoId)
                        if (globalOwnerUUID != null) {
                            // DB에 이미 등록 정보가 있음
                            if (globalOwnerUUID == player.uniqueId.toString()) {
                                // DB 소유자가 현재 플레이어 (NBT가 사라진 내 아이템일 가능성)
                                val mutableMeta = itemStack.itemMeta // 아이템 메타 다시 가져오기
                                mutableMeta?.persistentDataContainer?.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
                                itemStack.itemMeta = mutableMeta // 변경된 메타 아이템에 적용
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    player.sendMessage("§a아이템 §f$currentItemDisplayName§a 에 당신의 등록 정보를 다시 기록했습니다. (DB 소유자 일치)")
                                })
                                // Player_Items_State 일관성 보장
                                if (!checkItemOwnershipInDatabase(player.uniqueId.toString(), nexoId)) {
                                   updatePlayerItemState(player.uniqueId, nexoId, "OWNED")
                                }
                            } else {
                                // DB 소유자가 다른 플레이어
                                val finalOwnerName = globalOwnerName ?: "알 수 없는 플레이어"
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    player.sendMessage("§c아이템 §f$currentItemDisplayName§c 은(는) §f$finalOwnerName§c 님이 이미 시스템에 등록한 아이템입니다. (새 아이템, 등록 불가)")
                                })
                            }
                        } else {
                            // NBT도 없고, DB에도 없는 완전한 새 아이템 -> 등록 시도
                            val mutableMetaToSet = itemStack.itemMeta // 아이템 메타 다시 가져오기
                            if (mutableMetaToSet != null) {
                                mutableMetaToSet.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
                                itemStack.itemMeta = mutableMetaToSet // NBT 설정

                                if (registerItemToDatabase(player, nexoId)) {
                                    // 시즌별 테이블 등록 성공
                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                        player.sendMessage("§a아이템 §f$currentItemDisplayName§a 이(가) 성공적으로 등록되고, 아이템에 당신의 정보가 기록되었습니다.")
                                    })
                                    if (!updatePlayerItemState(player.uniqueId, nexoId, "OWNED")) {
                                        plugin.logger.warning("플레이어 ${player.name}의 아이템 $nexoId 상태를 OWNED로 업데이트 실패 (신규 등록).")
                                    }
                                } else {
                                    // 시즌별 테이블 등록 실패 (DB 오류 또는 동시성 문제)
                                    plugin.logger.warning("아이템 $nexoId 전역 등록 실패 (플레이어: ${player.name}). DB 오류 또는 이미 등록됨.")
                                    // NBT 변경 롤백
                                    val metaToRevert = itemStack.itemMeta
                                    metaToRevert?.persistentDataContainer?.remove(ownerKey)
                                    itemStack.itemMeta = metaToRevert
                                    Bukkit.getScheduler().runTask(plugin, Runnable {
                                        player.sendMessage("§c아이템 §f$currentItemDisplayName§c 등록 중 시스템 오류가 발생했습니다. (아이템 정보 변경 안됨)")
                                    })
                                }
                            } else {
                                // 아이템 메타가 null이 되어버린 드문 경우 (보통 발생 안 함)
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    player.sendMessage("§c아이템 §f$currentItemDisplayName§c 처리 중 예상치 못한 오류가 발생했습니다. (메타데이터 손실)")
                                })
                            }
                        }
                    }
                    processedNexoIdsThisSession.add(nexoId) // 이 세션에서 nexoId 처리 완료
                } // End of itemLoop

                // 최종 메시지 (메인 스레드에서 실행)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!foundProcessableItem) {
                        player.sendMessage("§c인벤토리에서 등록할 수 있는 시즌 아이템을 찾지 못했습니다.")
                    } else {
                        player.sendMessage("§a아이템 등록 확인이 완료되었습니다.")
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

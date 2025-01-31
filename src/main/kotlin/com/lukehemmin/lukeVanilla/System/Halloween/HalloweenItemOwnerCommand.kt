package com.lukehemmin.lukeVanilla.System.Halloween

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

class HalloweenItemOwnerCommand(private val plugin: Main) : CommandExecutor {

    companion object {
        private val VALID_HALLOWEEN_ITEMS = listOf(
            "halloween_sword",
            "halloween_pickaxe",
            "halloween_axe",
            "halloween_shovel",
            "halloween_hoe",
            "halloween_bow",
            "halloween_fishing_rod",
            "halloween_hammer",
            "halloween_hat",
            "halloween_scythe",
            "halloween_spear"
        )
    }

    private fun getColumnName(oraxenId: String): String {
        return when (oraxenId) {
            "halloween_sword" -> "sword"
            "halloween_pickaxe" -> "pickaxe"
            "halloween_axe" -> "axe"
            "halloween_shovel" -> "shovel"
            "halloween_hoe" -> "hoe"
            "halloween_bow" -> "bow"
            "halloween_fishing_rod" -> "fishing_rod"
            "halloween_hammer" -> "hammer"
            "halloween_hat" -> "hat"
            "halloween_scythe" -> "scythe"
            "halloween_spear" -> "spear"
            else -> ""
        }
    }

    // 클래스 내에 아이템 이름 변환 함수 추가
    private fun getItemDisplayName(oraxenId: String): String {
        return when (oraxenId) {
            "halloween_sword" -> "호박의 빛검"
            "halloween_pickaxe" -> "호박 곡괭이"
            "halloween_axe" -> "호박 도끼"
            "halloween_shovel" -> "호박 삽"
            "halloween_hoe" -> "호박 괭이"
            "halloween_bow" -> "호박 활"
            "halloween_fishing_rod" -> "호박 낚싯대"
            "halloween_hammer" -> "호박 망치"
            "halloween_hat" -> "호박 모자"
            "halloween_scythe" -> "호박 낫"
            "halloween_spear" -> "호박 창"
            else -> "알 수 없는 아이템"
        }
    }

    private val database: Database = plugin.database
    private val ownerKey = NamespacedKey(plugin, "owner")

    private fun registerItemToDatabase(player: Player, oraxenId: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                val columnName = getColumnName(oraxenId)

                val selectStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Owner WHERE UUID = ?")
                selectStmt.setString(1, uuid)
                val resultSet = selectStmt.executeQuery()

                if (resultSet.next()) {
                    val updateStmt = connection.prepareStatement("UPDATE Halloween_Item_Owner SET $columnName = 1 WHERE UUID = ?")
                    updateStmt.setString(1, uuid)
                    updateStmt.executeUpdate()
                } else {
                    val insertStmt = connection.prepareStatement("INSERT INTO Halloween_Item_Owner (UUID, $columnName) VALUES (?, 1)")
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



    private fun registerItem(player: Player, oraxenId: String, item: ItemStack, meta: ItemMeta) {
        // 데이터베이스 업데이트 (비동기 작업)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val success = registerItemToDatabase(player, oraxenId)

            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (success) {
                    meta.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
                    item.itemMeta = meta
                    player.sendMessage("아이템에 소유자로 등록되었습니다.")
                } else {
                    player.sendMessage("데이터베이스 작업 중 오류가 발생하였습니다.")
                }
            })
        })
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 인자가 "아이템"과 "소유"인지 확인
        // args 배열이 비어있어야 합니다.
        if (args.isNotEmpty()) {
            player.sendMessage("사용법: /할로윈 아이템 소유")
            return true
        }

        // 처리한 아이템 종류를 추적하기 위한 집합
        val processedItems = mutableSetOf<String>()

        // 등록 결과를 저장할 리스트
        val registeredItems = mutableListOf<String>()
        val alreadyRegisteredItems = mutableListOf<Pair<String, String>>()

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                player.inventory.contents?.filterNotNull()?.forEach { item ->
                    if (NexoItems.idFromItem(item) != null) {
                        val meta = item.itemMeta ?: return@forEach
                        val oraxenId = NexoItems.idFromItem(item) ?: return@forEach

                        if (!VALID_HALLOWEEN_ITEMS.contains(oraxenId)) {
                            return@forEach
                        }

                        // 이미 처리한 아이템이면 건너뜀
                        if (processedItems.contains(oraxenId)) {
                            return@forEach
                        }

                        // 처리한 아이템으로 추가
                        processedItems.add(oraxenId)

                        // 이미 소유자가 있는 경우
                        if (meta.persistentDataContainer.has(ownerKey, PersistentDataType.STRING)) {
                            val ownerUuid = meta.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)

                            // 내 아이템인지 확인
                            if (ownerUuid == player.uniqueId.toString()) {
                                database.getConnection().use { connection ->
                                    val columnName = getColumnName(oraxenId)
                                    val checkItemStmt = connection.prepareStatement(
                                        "SELECT 1 FROM Halloween_Item_Owner WHERE UUID = ? AND $columnName = 1"
                                    )
                                    checkItemStmt.setString(1, ownerUuid)
                                    val itemExists = checkItemStmt.executeQuery().next()

                                    if (itemExists) {
                                        // 이미 DB에 등록된 경우
                                        registeredItems.add("ALREADY_" + oraxenId)
                                    } else {
                                        // DB에 미등록된 경우 재등록
                                        if (registerItemToDatabase(player, oraxenId)) {
                                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                                meta.persistentDataContainer.set(
                                                    ownerKey,
                                                    PersistentDataType.STRING,
                                                    player.uniqueId.toString()
                                                )
                                                item.itemMeta = meta
                                            })
                                            registeredItems.add(oraxenId)
                                        } else {
                                            plugin.logger.warning("아이템 등록 중 오류 발생: $oraxenId")
                                        }
                                    }
                                }
                            } else {
                                // 다른 사람의 아이템인 경우
                                database.getConnection().use { connection ->
                                    val selectStmt = connection.prepareStatement("SELECT NickName FROM Player_Data WHERE UUID = ?")
                                    selectStmt.setString(1, ownerUuid)
                                    val resultSet = selectStmt.executeQuery()
                                    val ownerName = if (resultSet.next()) resultSet.getString("NickName") else "알 수 없는 플레이어"
                                    alreadyRegisteredItems.add(Pair(oraxenId, ownerName))
                                }
                            }
                        } else {
                            // 소유자가 없는 새로운 아이템 등록
                            if (registerItemToDatabase(player, oraxenId)) {
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    meta.persistentDataContainer.set(
                                        ownerKey,
                                        PersistentDataType.STRING,
                                        player.uniqueId.toString()
                                    )
                                    item.itemMeta = meta
                                })
                                registeredItems.add(oraxenId)
                            } else {
                                plugin.logger.warning("아이템 등록 중 오류 발생: $oraxenId")
                            }
                        }
                    }
                }

                // 모든 처리가 완료된 후 결과 메시지 출력
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    // 메시지 출력 부분을 위해 registeredItems를 새로 등록된 아이템과 이미 등록된 아이템으로 분리합니다.
                    val newlyRegisteredItems = registeredItems.filter { !it.startsWith("ALREADY_") }
                    val alreadyOwnItems = registeredItems.filter { it.startsWith("ALREADY_") }.map { it.substring(8) }

                    // 새로 등록된 아이템 메시지 출력
                    if (newlyRegisteredItems.isNotEmpty()) {
                        val displayNames = newlyRegisteredItems.map { getItemDisplayName(it) }
                        player.sendMessage("다음 아이템들이 소유자로 등록되었습니다: ${displayNames.joinToString(", ")}")
                    }

                    // 이미 등록된 내 아이템 메시지 출력
                    if (alreadyOwnItems.isNotEmpty()) {
                        alreadyOwnItems.forEach { itemId ->
                            player.sendMessage("아이템 ${getItemDisplayName(itemId)} 은(는) 이미 등록되어있습니다.")
                        }
                    }

                    // 다른 사람의 아이템 메시지 출력
                    if (alreadyRegisteredItems.isNotEmpty()) {
                        alreadyRegisteredItems.forEach { (itemId, ownerName) ->
                            player.sendMessage("아이템 ${getItemDisplayName(itemId)} 은(는) $ownerName 님이 이미 등록하였습니다.")
                        }
                    }

                    // 등록된 아이템이 없는 경우 메시지 출력
                    if (newlyRegisteredItems.isEmpty() && alreadyOwnItems.isEmpty() && alreadyRegisteredItems.isEmpty()) {
                        player.sendMessage("등록 가능한 할로윈 아이템이 없습니다.")
                    }
                })
            } catch (e: Exception) {
                plugin.logger.warning("처리 중 오류 발생: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("처리 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }
}
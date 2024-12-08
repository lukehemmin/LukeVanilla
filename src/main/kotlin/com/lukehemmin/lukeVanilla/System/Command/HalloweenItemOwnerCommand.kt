package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import io.th0rgal.oraxen.api.OraxenItems
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

    private val database: Database = plugin.database
    private val ownerKey = NamespacedKey(plugin, "owner")

    private fun registerItemToDatabase(player: Player, oraxenId: String) {
        try {
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
        } catch (e: Exception) {
            plugin.logger.warning("데이터베이스 작업 중 오류 발생: ${e.message}")
            throw e
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 인자가 "아이템"과 "소유"인지 확인
        if (args.size != 2 || args[0] != "아이템" || args[1] != "소유") {
            player.sendMessage("사용법: /할로윈 아이템 소유")
            return true
        }

        val item = player.inventory.itemInMainHand

        // 아이템이 Oraxen 아이템인지 확인
        if (!OraxenItems.exists(item)) {
            player.sendMessage("이 아이템은 등록 가능한 할로윈 아이템이 아닙니다.")
            return true
        }

        val meta = item.itemMeta ?: return true

        // 아이템의 Oraxen ID 가져오기
        val oraxenId = OraxenItems.getIdByItem(item)

        if (!VALID_HALLOWEEN_ITEMS.contains(oraxenId)) {
            player.sendMessage("이 아이템은 등록 가능한 할로윈 아이템이 아닙니다")
            return true
        }

        // 이미 소유자 데이터가 있는지 확인
        if (meta.persistentDataContainer.has(ownerKey, PersistentDataType.STRING)) {
            val ownerUuid = meta.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)

            // 데이터베이스에서 아이템 등록 여부와 소유자 정보 확인 (비동기 작업)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                database.getConnection().use { connection ->
                    val columnName = getColumnName(oraxenId)

                    // 아이템 등록 여부 확인
                    val checkItemStmt = connection.prepareStatement("SELECT 1 FROM Halloween_Item_Owner WHERE UUID = ? AND $columnName = 1")
                    checkItemStmt.setString(1, ownerUuid)
                    val itemExists = checkItemStmt.executeQuery().next()

                    // 소유자 닉네임 확인
                    val selectStmt = connection.prepareStatement("SELECT NickName FROM Player_Data WHERE UUID = ?")
                    selectStmt.setString(1, ownerUuid)
                    val resultSet = selectStmt.executeQuery()
                    val ownerName = if (resultSet.next()) resultSet.getString("NickName") else "알 수 없는 플레이어"

                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (itemExists) {
                            // 데이터베이스에 등록된 경우
                            player.sendMessage("이 아이템은 $ownerName 님이 이미 등록하였습니다.")
                        } else {
                            // 데이터베이스에 등록되지 않은 경우 재등록 허용
                            registerItem(player, oraxenId, item, meta)
                            player.sendMessage("아이템에 소유자로 등록되었습니다.")
                        }
                    })
                }
            })
            return true
        }

        // 할로윈 아이템 목록
        val validIds = listOf(
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

        if (!validIds.contains(oraxenId)) {
            player.sendMessage("이 아이템은 등록 가능한 할로윈 아이템이 아닙니다.")
            return true
        }

        // 데이터베이스 업데이트 (비동기 작업)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                val columnName = getColumnName(oraxenId)

                // UUID로 행 검색
                val selectStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Owner WHERE UUID = ?")
                selectStmt.setString(1, uuid)
                val resultSet = selectStmt.executeQuery()

                if (resultSet.next()) {
                    // 행이 존재하면 컬럼 값 업데이트
                    val updateStmt = connection.prepareStatement("UPDATE Halloween_Item_Owner SET $columnName = 1 WHERE UUID = ?")
                    updateStmt.setString(1, uuid)
                    updateStmt.executeUpdate()
                } else {
                    // 행이 없으면 새로 생성
                    val insertStmt = connection.prepareStatement("INSERT INTO Halloween_Item_Owner (UUID, $columnName) VALUES (?, 1)")
                    insertStmt.setString(1, uuid)
                    insertStmt.executeUpdate()
                }
            }
        })

        // 아이템에 소유자 정보 추가 (NBT 데이터)
        meta.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
        item.itemMeta = meta

        player.sendMessage("아이템에 소유자로 등록되었습니다.")
        return true
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

    private fun registerItem(player: Player, oraxenId: String, item: ItemStack, meta: ItemMeta) {
        // 데이터베이스 업데이트 (비동기 작업)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
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
        })

        // 아이템에 소유자 정보 추가
        meta.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
        item.itemMeta = meta
    }
}
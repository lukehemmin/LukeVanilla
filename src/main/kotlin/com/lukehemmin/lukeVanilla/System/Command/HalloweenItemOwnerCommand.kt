package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import io.th0rgal.oraxen.api.OraxenItems
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class HalloweenItemOwnerCommand(private val plugin: Main) : CommandExecutor {

    private val database: Database = plugin.database

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 인자가 "아이템 소유" 인지 확인
        if (args.size != 2 || args[0] != "아이템" || args[1] != "소유") {
            player.sendMessage("사용법: /할로윈 아이템 소유")
            return true
        }


        val item = player.inventory.itemInMainHand

        // 아이템이 Oraxen 아이템인지 확인
        if (!OraxenItems.exists(item)) {
            player.sendMessage("들고 있는 아이템은 Oraxen 아이템이 아닙니다.")
            return true
        }

        val meta = item.itemMeta
        val lore = meta?.lore ?: mutableListOf()
        val ownershipText = "§f§l${player.name} 의 소유입니다."

        // 아이템 설명에 소유자 문구가 있는지 확인
        if (lore.contains(ownershipText)) {
            player.sendMessage("이 아이템은 이미 소유자로 등록되어 있습니다.")
            return true
        }

        // 아이템의 Oraxen ID 가져오기
        val oraxenId = OraxenItems.getIdByItem(item)

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

        // 데이터베이스 업데이트
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

        // 아이템 설명에 소유자 문구 추가
        lore.add(ownershipText)
        meta.lore = lore
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
}
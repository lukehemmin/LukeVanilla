package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ValentineShieldCommand(private val plugin: Main) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}${ChatColor.BOLD}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // DB에서 수령 여부 확인
        plugin.database.getConnection().use { connection ->
            val checkQuery = "SELECT received FROM Valentine_Shield WHERE UUID = ?"
            connection.prepareStatement(checkQuery).use { checkStmt ->
                checkStmt.setString(1, player.uniqueId.toString())
                val resultSet = checkStmt.executeQuery()

                if (resultSet.next() && resultSet.getBoolean("received")) {
                    player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}이미 발렌타인 방패를 받으셨습니다!")
                    return true
                }

                // Nexo 커스텀 방패 아이템 생성
                val valentineShield = NexoItems.itemFromId("valentine_shield")
                if (valentineShield == null) {
                    player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}아이템을 생성하는 중 오류가 발생했습니다.")
                    return true
                }

                // 인벤토리에 아이템 지급
                player.inventory.addItem(valentineShield.build())  // create() -> build() 로 변경
                player.sendMessage("${ChatColor.GREEN}${ChatColor.BOLD}발렌타인 방패를 받았습니다!")

                // DB에 수령 기록
                val insertQuery = """
                    INSERT INTO Valentine_Shield (UUID, received) 
                    VALUES (?, 1) 
                    ON DUPLICATE KEY UPDATE received = 1
                """
                connection.prepareStatement(insertQuery).use { insertStmt ->
                    insertStmt.setString(1, player.uniqueId.toString())
                    insertStmt.executeUpdate()
                }
            }
        }

        return true
    }
}
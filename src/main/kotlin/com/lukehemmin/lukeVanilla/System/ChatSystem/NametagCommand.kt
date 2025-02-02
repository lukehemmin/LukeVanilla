package com.lukehemmin.lukeVanilla.System.ChatSystem

import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class NametagCommand(private val database: Database, private val nametagManager: NametagManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player || !sender.isOp) {
            sender.sendMessage("§c§l이 명령어는 관리자만 사용할 수 있습니다.")
            return true
        }

        when (command.name.lowercase()) {
            "nametag" -> handleNametag(sender, args)
            "delnametag" -> handleDelNametag(sender, args)
        }
        return true
    }

    private fun handleNametag(sender: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("/nametag <닉네임> <칭호> - 원하는 유저의 칭호를 변경합니다.")
            return true
        }

        val playerName = args[0]
        val newNametag = args.slice(1 until args.size).joinToString(" ")

        val player = Bukkit.getPlayer(playerName)
        if (player == null) {
            sender.sendMessage("§c§l플레이어가 오프라인이거나 찾을 수 없습니다.")
            return true
        }

        val uuid = player.uniqueId
        val connection = database.getConnection()

        // Player_NameTag 테이블에서 해당 플레이어의 행이 있는지 확인
        val checkStatement = connection.prepareStatement("SELECT * FROM Player_NameTag WHERE UUID = ?")
        checkStatement.setString(1, uuid.toString())
        val resultSet = checkStatement.executeQuery()

        if (resultSet.next()) {
            // 행이 있으면 업데이트
            val updateStatement = connection.prepareStatement("UPDATE Player_NameTag SET Tag = ? WHERE UUID = ?")
            updateStatement.setString(1, newNametag)
            updateStatement.setString(2, uuid.toString())
            updateStatement.executeUpdate()
            updateStatement.close()
        } else {
            // 행이 없으면 삽입
            val insertStatement = connection.prepareStatement("INSERT INTO Player_NameTag (UUID, Tag) VALUES (?, ?)")
            insertStatement.setString(1, uuid.toString())
            insertStatement.setString(2, newNametag)
            insertStatement.executeUpdate()
            insertStatement.close()
        }

        resultSet.close()
        checkStatement.close()
        connection.close()

        // 플레이어의 Nametag 즉시 업데이트
        nametagManager.updatePlayerNametag(player, newNametag)

        sender.sendMessage("§f§l${playerName}§f의 칭호가 ${newNametag.translateColorCodes()}§f 으로 변경되었습니다.")
        return true
    }

    private fun handleDelNametag(sender: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("/delnametag <닉네임> - 원하는 유저의 칭호를 제거합니다.")
            return true
        }

        val playerName = args[0]
        val player = Bukkit.getPlayer(playerName)
        if (player == null) {
            sender.sendMessage("§c§l플레이어가 오프라인이거나 찾을 수 없습니다.")
            return true
        }

        val uuid = player.uniqueId
        val connection = database.getConnection()

        try {
            val deleteStatement = connection.prepareStatement("DELETE FROM Player_NameTag WHERE UUID = ?")
            deleteStatement.setString(1, uuid.toString())
            val rowsAffected = deleteStatement.executeUpdate()
            deleteStatement.close()

            if (rowsAffected > 0) {
                // 플레이어의 네임태그 초기화
                nametagManager.updatePlayerNametag(player, "")
                sender.sendMessage("§f§l${playerName}§f의 칭호가 제거되었습니다.")
            } else {
                sender.sendMessage("§c§l${playerName}의 칭호가 존재하지 않습니다.")
            }
        } finally {
            connection.close()
        }
        return true
    }
}
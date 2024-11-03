package com.lukehemmin.lukeVanilla.System.NameTag

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
        val statement = connection.prepareStatement("UPDATE Player_Data SET NameTag = ? WHERE UUID = ?")
        statement.setString(1, newNametag)
        statement.setString(2, uuid.toString())
        statement.executeUpdate()

        statement.close()
        connection.close()

        // Update the player's nametag immediately
        nametagManager.updatePlayerNametag(player, newNametag)

        sender.sendMessage("§f§l${playerName}§f의 칭호가 ${newNametag.translateColorCodes()}§f 으로 변경되었습니다.")
        return true
    }
}
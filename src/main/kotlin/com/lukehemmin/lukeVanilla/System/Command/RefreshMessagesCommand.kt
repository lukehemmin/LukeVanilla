package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Player_Join_And_Quit_Message_Listener

class RefreshMessagesCommand(private val plugin: Main) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("lukevanilla.refreshmessages")) {
            sender.sendMessage("§c이 명령어를 실행할 권한이 없습니다.")
            return true
        }

        Player_Join_And_Quit_Message_Listener.updateMessages(plugin.database)
        sender.sendMessage("§a접속 및 퇴장 메시지가 성공적으로 갱신되었습니다.")
        return true
    }
}
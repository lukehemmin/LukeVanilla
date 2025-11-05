package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class YeonhongMessageCommand(private val plugin: Main) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("&e&l사용법: /연홍메시지 <보기|끄기>")
            return true
        }

        when (args[0]) {
            "보기" -> {
                plugin.database.setYeonhongMessageEnabled(sender.uniqueId.toString(), true)
                sender.sendMessage("&a&l연홍 메시지를 활성화했습니다.".translateColorCodes())
            }
            "끄기" -> {
                plugin.database.setYeonhongMessageEnabled(sender.uniqueId.toString(), false)
                sender.sendMessage("&c&l연홍 메시지를 비활성화했습니다.".translateColorCodes())
            }
            else -> {
                sender.sendMessage("&e&l사용법: /연홍메시지 <보기|끄기>")
            }
        }
        return true
    }
}

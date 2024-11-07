package com.lukehemmin.lukeVanlia.velocity

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender

class infomessage : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender is ConsoleCommandSender) {
            if (args.isNotEmpty()) {
                val message = args.joinToString(" ")
                val coloredMessage = ChatColor.translateAlternateColorCodes('&', message)
                Bukkit.broadcastMessage(" $coloredMessage")
                return true
            } else {
                sender.sendMessage("§c§l콘솔에서만 사용가능합니다.")
                return false
            }
        }
        sender.sendMessage("§c§l이 명령어는 아무나 사용할 수 없습니다.")
        return false
    }
}
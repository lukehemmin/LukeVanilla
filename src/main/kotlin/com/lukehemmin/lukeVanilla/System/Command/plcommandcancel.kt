package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class plcommandcancel : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            when (label.lowercase()) {
                "pl", "plugins" -> {
                    sender.sendMessage("§fServer Plugins (1):")
                    sender.sendMessage("§6Bukkit Plugins:")

                    val component = net.md_5.bungee.api.chat.TextComponent(" §7- §aLukeVanilla")
                    component.clickEvent = net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                        "/lukeplugininfo"
                    )
                    sender.spigot().sendMessage(component)
                    return true
                }
                "lukeplugininfo" -> {
                    sender.sendMessage("§aLukeVanilla §fversion §a5.2")
                    sender.sendMessage("§fWebsite: §ahttps://github.com/lukehemmin/LukeVanilla")
                    sender.sendMessage("§fAuthor: §aLukehemmin")
                    return true
                }
            }
        }
        return false
    }
}
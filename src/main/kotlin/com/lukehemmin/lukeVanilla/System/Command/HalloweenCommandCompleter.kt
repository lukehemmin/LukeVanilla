package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class HalloweenCommandCompleter : TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): MutableList<String>? {
        if (args.size == 1) {
            val subCommands = listOf("아이템")
            return subCommands.filter { it.startsWith(args[0]) }.toMutableList()
        } else if (args.size == 2 && args[0] == "아이템") {
            val actions = listOf("소유")
            return actions.filter { it.startsWith(args[1]) }.toMutableList()
        }
        return null
    }
}
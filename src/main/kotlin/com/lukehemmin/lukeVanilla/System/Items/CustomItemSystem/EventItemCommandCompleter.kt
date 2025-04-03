package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class EventItemCommandCompleter : TabCompleter {

    private val subCommands = listOf("등록", "조회", "수령")
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (args.size == 1) {
            return subCommands.filter { it.startsWith(args[0], ignoreCase = true) }
        } else if (args.size == 2 && (args[0] == "조회" || args[0] == "수령")) {
            return EventType.getTabCompletions().filter { it.startsWith(args[1], ignoreCase = true) }
        }
        
        return emptyList()
    }
} 
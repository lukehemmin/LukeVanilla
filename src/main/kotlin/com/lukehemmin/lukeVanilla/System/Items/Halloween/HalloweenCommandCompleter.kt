package com.lukehemmin.lukeVanilla.System.Items.Halloween

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class HalloweenCommandCompleter : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): MutableList<String>? {
        val completions = mutableListOf<String>()
        if (args.size == 1) {
            listOf("아이템").forEach {
                if (it.startsWith(args[0])) completions.add(it)
            }
        } else if (args.size == 2 && args[0] == "아이템") {
            listOf("받기", "목록", "소유").forEach {
                if (it.startsWith(args[1])) completions.add(it)
            }
        }
        return completions
    }
}
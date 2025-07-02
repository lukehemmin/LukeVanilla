package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class VoiceChannelMessageCommandCompleter : TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): MutableList<String>? {
        if (args.size == 1) {
            val completions = mutableListOf("보기", "끄기")
            return completions.filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }

        return mutableListOf()
    }
}
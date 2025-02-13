package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.System.NexoCraftingRestriction
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CraftAllowCommand(private val restriction: NexoCraftingRestriction) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        restriction.allowPlayer(sender)
        return true
    }
}
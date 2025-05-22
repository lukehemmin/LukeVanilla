package com.lukehemmin.lukeVanilla.Lobby

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class SnowGameCommand(private val snowMinigame: SnowMinigame) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player || !sender.isOp) {
            sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("사용법: /snowgame <forcestart|forcereset>", NamedTextColor.YELLOW))
            return true
        }

        when (args[0].lowercase()) {
            "forcestart" -> {
                snowMinigame.forceStartGame()
                sender.sendMessage(Component.text("눈싸움 미니게임을 강제로 시작했습니다.", NamedTextColor.GREEN))
            }
            "forcereset", "forcestop" -> { 
                snowMinigame.resetGame("관리자에 의해 게임이 강제 초기화되었습니다.")
                sender.sendMessage(Component.text("눈싸움 미니게임을 강제로 초기화했습니다.", NamedTextColor.GREEN))
            }
            else -> {
                sender.sendMessage(Component.text("알 수 없는 명령어입니다. 사용법: /snowgame <forcestart|forcereset>", NamedTextColor.RED))
            }
        }

        return true
    }
}

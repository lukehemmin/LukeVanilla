package com.lukehemmin.lukeVanilla.System.SitSystem

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * /crawl 명령어 처리 클래스
 * 플레이어가 기어다닐 수 있게 합니다.
 */
class CrawlCommand(private val sitManager: SitManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 플레이어만 명령어 사용 가능
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 기어다니기 토글
        sitManager.toggleCrawl(player)

        return true
    }
} 
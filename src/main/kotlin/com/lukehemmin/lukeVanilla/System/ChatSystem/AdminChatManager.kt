// AdminChatManager.kt
package com.lukehemmin.lukeVanilla.System.ChatSystem

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class AdminChatManager(private val plugin: JavaPlugin) : CommandExecutor, Listener {
    private val adminChatEnabled = mutableMapOf<UUID, Boolean>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (!sender.hasPermission("lukevanilla.adminchat")) {
            sender.sendMessage("${ChatColor.RED}이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}/관리자채팅 <활성화|비활성화>")
            return true
        }

        when (args[0]) {
            "활성화" -> {
                sender.setMetadata("adminChatEnabled", FixedMetadataValue(plugin, true))
                sender.sendMessage("${ChatColor.GREEN}${ChatColor.BOLD}관리자 채팅이 활성화되었습니다.")
            }
            "비활성화" -> {
                sender.removeMetadata("adminChatEnabled", plugin)
                sender.sendMessage("${ChatColor.RED}${ChatColor.BOLD}관리자 채팅이 비활성화되었습니다.")
            }
            else -> {
                sender.sendMessage("${ChatColor.RED}${ChatColor.BOLD}/관리자채팅 <활성화|비활성화>")
            }
        }
        return true
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player

        if (player.hasMetadata("adminChatEnabled")) {
            event.isCancelled = true

            val nameTag = getNameTag(player)
            val adminMessage = "${ChatColor.RED}${ChatColor.BOLD}[관리자 채팅] $nameTag${ChatColor.WHITE}${player.name}: ${event.message}"

            // 콘솔에 로그 출력
            plugin.logger.info("[관리자 채팅] ${player.name}: ${event.message}")

            // 관리자 권한이 있는 플레이어에게만 메시지 전송
            Bukkit.getOnlinePlayers()
                .filter { it.hasPermission("lukevanilla.adminchat") }
                .forEach { it.sendMessage(adminMessage) }
        }
    }

    private fun getNameTag(player: Player): String {
        val team = Bukkit.getScoreboardManager().mainScoreboard.getEntryTeam(player.name)
        return team?.prefix ?: ""
    }
}
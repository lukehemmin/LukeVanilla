package com.lukehemmin.lukeVanilla.System.ChatSystem

import com.lukehemmin.lukeVanilla.Main
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

class AdminChatSync(private val plugin: Main) : ListenerAdapter(), Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.discordBot.jda.addEventListener(this)
    }

    // 디스코드 메시지를 받아서 게임 내 관리자 채팅으로 전송
    override fun onMessageReceived(event: MessageReceivedEvent) {
        // 봇 메시지는 무시
        if (event.author.isBot) return

        // 관리자 채팅 채널 ID 확인
        val adminChatChannelId = plugin.database.getSettingValue("AdminChatChannel") ?: return

        // 메시지가 관리자 채팅 채널에서 온 것인지 확인
        if (event.channel.id != adminChatChannelId) return

        // 게임 내 관리자들에게 메시지 전송
        val message = "${ChatColor.RED}${ChatColor.BOLD}[디스코드] ${event.author.name}: ${ChatColor.WHITE}${event.message.contentDisplay}"

        Bukkit.getScheduler().runTask(plugin) { _ ->
            Bukkit.getOnlinePlayers()
                .filter { it.hasPermission("lukevanilla.adminchat") }
                .forEach { it.sendMessage(message) }

            // 콘솔에도 로그 출력
            plugin.logger.info("[디스코드 관리자 채팅] ${event.author.name}: ${event.message.contentDisplay}")
        }
    }

    // 게임 내 관리자 채팅을 디스코드로 전송
    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player

        // 관리자 채팅이 활성화되어 있는지 확인
        if (player.hasMetadata("adminChatEnabled")) {
            val adminChatChannelId = plugin.database.getSettingValue("AdminChatChannel") ?: return
            val channel = plugin.discordBot.jda.getTextChannelById(adminChatChannelId) ?: return

            // 디스코드로 메시지 전송
            channel.sendMessage("[${player.name}] ${event.message}").queue()
        }
    }
}
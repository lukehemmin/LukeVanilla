package com.lukehemmin.lukeVanlia.velocity

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender

class infomessage : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender is ConsoleCommandSender) {
            if (args.isNotEmpty()) {
                val message = args.joinToString(" ")
                val components = parseMessage(message)

                // 하나의 메인 컴포넌트 생성
                val mainComponent = TextComponent()
                components.forEach { mainComponent.addExtra(it) }

                // 한 번에 전송
                Bukkit.getOnlinePlayers().forEach { player ->
                    player.spigot().sendMessage(mainComponent)
                }
                return true
            } else {
                sender.sendMessage("§c§l콘솔에서만 사용가능합니다.")
                return false
            }
        }
        sender.sendMessage("§c§l이 명령어는 아무나 사용할 수 없습니다.")
        return false
    }

    private fun parseMessage(message: String): List<TextComponent> {
        val components = mutableListOf<TextComponent>()
        val regex = "\\[([^\\]]+)\\]\\(([^)]+)\\)".toRegex()
        var lastIndex = 0

        for (match in regex.findAll(message)) {
            // 링크 이전의 일반 텍스트 추가
            if (match.range.first > lastIndex) {
                val textBefore = message.substring(lastIndex, match.range.first)
                components.add(TextComponent(ChatColor.translateAlternateColorCodes('&', textBefore)))
            }

            // 링크 컴포넌트 생성
            val text = match.groupValues[1]
            val url = match.groupValues[2]
            val linkComponent = TextComponent(ChatColor.translateAlternateColorCodes('&', text))
            linkComponent.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, url)
            linkComponent.hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("§7클릭하여 링크 열기: §f$url").create()
            )
            components.add(linkComponent)

            lastIndex = match.range.last + 1
        }

        // 마지막 링크 이후의 텍스트 추가
        if (lastIndex < message.length) {
            val remainingText = message.substring(lastIndex)
            components.add(TextComponent(ChatColor.translateAlternateColorCodes('&', remainingText)))
        }

        return components
    }
}
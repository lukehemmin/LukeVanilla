package com.lukehemmin.lukeVanlia.commands

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class mapcommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender
        player.sendMessage("")
        val mapLink = TextComponent("§a§l[클릭하여 지도사이트로 이동]")
        mapLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, "https://map.mine.lukehemmin.com/")
        player.spigot().sendMessage(mapLink)
        player.sendMessage("")
        return true
    }
}
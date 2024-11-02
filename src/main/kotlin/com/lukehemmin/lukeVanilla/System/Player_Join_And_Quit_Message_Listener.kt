package com.lukehemmin.lukeVanilla.System

import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateHexColorCodes
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class Player_Join_And_Quit_Message_Listener(private val serviceType: String, private val plugin: JavaPlugin) : Listener {
    companion object {
        private var joinMessages = mutableMapOf<String, String>()
        private var quitMessages = mutableMapOf<String, String>()

        fun updateMessages(database: Database) {
            joinMessages["VanillaServerFirstJoin"] = cleanMessage(database.getJoinQuitMessage("Vanilla", "VanillaServerFirstJoin")?.translateHexColorCodes() ?: "{playerName} 님이 서버에 처음 접속했습니다.".translateHexColorCodes())
            joinMessages["VanillaServerJoin"] = cleanMessage(database.getJoinQuitMessage("Vanilla", "VanillaServerJoin")?.translateHexColorCodes() ?: "{playerName} 님이 서버에 접속했습니다.".translateHexColorCodes())
            joinMessages["VanillaJoinMessage"] = cleanMessage(database.getJoinQuitMessage("Vanilla", "VanillaJoinMessage")?.translateHexColorCodes() ?: "메시지가 설정되지 않음.".translateHexColorCodes())
            quitMessages["VanillaServerQuit"] = cleanMessage(database.getJoinQuitMessage("Vanilla", "VanillaServerQuit")?.translateHexColorCodes() ?: "{playerName} 님이 서버에서 나갔습니다.".translateHexColorCodes())

            joinMessages["LobbyServerJoin"] = cleanMessage(database.getJoinQuitMessage("Lobby", "LobbyServerJoin")?.translateHexColorCodes() ?: "{playerName} 님이 서버에 접속했습니다.".translateHexColorCodes())
            joinMessages["LobbyJoinMessage"] = cleanMessage(database.getJoinQuitMessage("Lobby", "LobbyJoinMessage")?.translateHexColorCodes() ?: "메시지가 설정되지 않음.".translateHexColorCodes())
            quitMessages["LobbyServerQuit"] = cleanMessage(database.getJoinQuitMessage("Lobby", "LobbyServerQuit")?.translateHexColorCodes() ?: "{playerName} 님이 서버에서 나갔습니다.".translateHexColorCodes())
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (serviceType == "Vanilla") {
            // Vanilla Server Join
            val message = if (!player.hasPlayedBefore()) {
                joinMessages["VanillaServerFirstJoin"]?.replace("{playerName}", player.name)
            } else {
                joinMessages["VanillaServerJoin"]?.replace("{playerName}", player.name)
            }
            event.joinMessage = message

            val playerName = player.name
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                for (i in 1..100) {
                    player.sendMessage("")
                }
                val joinMessage = joinMessages["VanillaJoinMessage"]?.replace("{playerName}", playerName)
                joinMessage?.split("\n")?.forEach { line ->
                    player.sendMessage(line.translateHexColorCodes())
                }
                val mapLink = TextComponent("             §a§l[클릭하여 지도사이트로 이동]")
                mapLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, "https://map.mine.lukehemmin.com/")
                player.spigot().sendMessage(mapLink)
                player.sendMessage("")
            }, 60L)
        } else if (serviceType == "Lobby") {
            // Lobby Server Join
            joinMessages["VanillaServerJoin"]?.replace("{playerName}", player.name)

            val player = event.player
            val playerName = player.name
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                for (i in 1..100) {
                    player.sendMessage("")
                }
                val joinMessage = joinMessages["LobbyJoinMessage"]?.replace("{playerName}", playerName)
                joinMessage?.split("\n")?.forEach { line ->
                    player.sendMessage(line.translateHexColorCodes())
                }
            }, 60L)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        val message = if (serviceType == "Vanilla") {
            quitMessages["VanillaServerQuit"]?.replace("{playerName}", player.name)
        } else {
            quitMessages["LobbyServerQuit"]?.replace("{playerName}", player.name)
        }
        event.quitMessage = message
    }
}

private fun cleanMessage(message: String): String {
    return message.replace("\r", "")
}
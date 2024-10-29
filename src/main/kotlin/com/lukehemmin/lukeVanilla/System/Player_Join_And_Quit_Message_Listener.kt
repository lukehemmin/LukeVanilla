package com.lukehemmin.lukeVanilla.System

import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateHexColorCodes
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class Player_Join_And_Quit_Message_Listener(private val serviceType: String, private val plugin: JavaPlugin) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (serviceType == "Vanilla") {
            // Vanilla Server Join
            if (!event.player.hasPlayedBefore()) {
                event.joinMessage = "       §f§l[§e§l++§f§l] §f§l${event.player.name} 님이 처음으로 서버에 접속했습니다!"
            } else {
                event.joinMessage = "       §f§l[§a§l+§f§l] §f§l${event.player.name} 님이 서버에 접속했습니다!"
            }

            val playerName = player.name
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                for (i in 1..100) {
                    player.sendMessage("")
                }
                //player.sendMessage("§b§l${player.name} §a§l님, 오늘도 서버에 오셨네요! 반가워요!")
                val message = "            &#FFA500$playerName &#82E0AA님 서버에 어서와요."
                val message1 = "&#FFA500할로윈 기간동안 제작가능한 아이템을 만들어 보세요!"
                player.sendMessage(message.translateHexColorCodes())
                player.sendMessage("")
                player.sendMessage("§fꐘ " + message1.translateHexColorCodes() + " §fꐘ")
                player.sendMessage("")
                val mapLink = TextComponent("             §a§l[클릭하여 지도사이트로 이동]")
                mapLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, "https://map.mine.lukehemmin.com/")
                player.spigot().sendMessage(mapLink)
                player.sendMessage("")
            }, 60L)
        } else if (serviceType == "Lobby") {
            // Lobby Server Join
            event.joinMessage = "       §f§l[§a§l+§f§l] §f§l${event.player.name} 님이 로비 서버에 접속했습니다!"

            val player = event.player
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                for (i in 1..100) {
                    player.sendMessage("")
                }
                player.sendMessage("§b§l${player.name} §a§l님, 이곳은 로비서버에요!")
                player.sendMessage("")
                player.sendMessage("§a§l이곳은 서버 점검, 패치가 있을때 서버가 다시 열릴때까지 기다리는 서버에요!")
                player.sendMessage("§a§l서버가 열리면 다시 원래 서버, 원래 위치로 돌아가게 되요!")
                player.sendMessage("§a§l잠시만 기다려주세요! ( 예정시간은 공지사항이나 패치노트에 있어요! )")
                player.sendMessage("")
            }, 60L)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (serviceType == "Vanilla") {
            event.quitMessage = "       §f§l[§c§l-§f§l] §f§l${event.player.name} 님이 서버에서 나갔습니다!"
        } else if (serviceType == "Lobby") {
            event.quitMessage = "       §f§l[§c§l-§f§l] §f§l${event.player.name} 님이 로비 서버에서 나갔습니다!"
        }
    }
}
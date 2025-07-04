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

class Player_Join_And_Quit_Message_Listener(private val serviceType: String, private val plugin: JavaPlugin, private val database: Database) : Listener {
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
        val uuid = player.uniqueId.toString()

        // 야생 서버에서만 처음 접속한 플레이어를 특정 위치로 텔레포트
        if (serviceType == "Vanilla" && !player.hasPlayedBefore()) {
            // 다음 틱에 텔레포트 실행 (안정적인 텔레포트를 위해)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                // 스폰 위치 설정 (안전 구역 내 위치로 설정)
                val spawnLocation = org.bukkit.Location(
                    Bukkit.getWorld("world"), // 월드 이름 설정
                    0.5, // x 좌표
                    64.0, // y 좌표
                    70.0, // z 좌표
                    180f, // yaw (수평 회전)
                    1.2f  // pitch (수직 회전)
                )
                player.teleport(spawnLocation)
            }, 20L) // 1초(20틱) 후 텔레포트 실행
        }

        // Player_Auth 테이블에서 IsAuth 확인
        var isAuth = true
        database.getConnection().use { connection ->
            val checkAuth = connection.prepareStatement("SELECT IsAuth FROM Player_Auth WHERE UUID = ?")
            checkAuth.setString(1, uuid)
            val authResult = checkAuth.executeQuery()
            if (authResult.next()) {
                isAuth = authResult.getInt("IsAuth") != 0
            }
            authResult.close()
            checkAuth.close()
        }

        // 인증되지 않은 플레이어는 접속 메시지 표시 안 함
        if (!isAuth) {
            event.joinMessage = null
            return
        }
        
        // 특정 권한(lukevanilla.hidemessages)을 가진 플레이어는 접속 메시지 표시 안 함
        if (player.hasPermission("lukevanilla.hidemessages")) {
            event.joinMessage = null
            return
        }

        if (serviceType == "Vanilla") {
            // Vanilla Server Join
            val message = if (!player.hasPlayedBefore()) {
                joinMessages["VanillaServerFirstJoin"]?.replace("{playerName}", player.name)

                // 처음 접속한 플레이어에게 제목(title)과 부제목(subtitle) 메시지 표시
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    player.sendTitle(
                        "§a§l서버에 오신 것을 환영합니다!", // 메인 타이틀
                        "§f§l우리 서버를 즐겨주세요", // 서브 타이틀
                        20, // 페이드 인 시간(틱)
                        100, // 표시 시간(틱)
                        20 // 페이드 아웃 시간(틱)
                    )

                    // 특수 효과 재생
                    player.world.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                    player.world.spawnParticle(org.bukkit.Particle.valueOf("VILLAGER_HAPPY"), player.location.add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5, 0.1)
                }, 40L)

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

                // 열려있는 문의가 있는지 확인하고 메시지 전송
                val openCases = database.getOpenSupportCases(uuid)
                if (openCases.isNotEmpty()) {
                    openCases.forEach { case ->
                        val supportId = case.supportId  // 예: "#000006"
                        val messageLink = case.messageLink

                        val messageText = "      §a§l[문의] §f§l$supportId 케이스가 §a§l열려있습니다. §f§l[디스코드로 보러가기]"
                        val totalLength = messageText.length

                        // "[디스코드로 보러가기]" 부분의 인덱스 찾기
                        val linkText = "[디스코드로 보러가기]"
                        val startIndex = messageText.indexOf(linkText)
                        val endIndex = startIndex + linkText.length

                        if (startIndex == -1) {
                            // 링크 텍스트가 없으면 전체 메시지를 그냥 보냄
                            player.sendMessage(messageText.translateHexColorCodes())
                        } else {
                            // "디스코드로 보러가기" 부분만 클릭 이벤트 설정
                            val beforeLink = messageText.substring(0, startIndex)
                            val clickableLink = TextComponent(messageText.substring(startIndex, endIndex)).apply {
                                clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, messageLink)
                                hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
                                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                    arrayOf(TextComponent("§a§l클릭하여 디스코드 채널로 이동"))
                                )
                            }
                            val afterLink = messageText.substring(endIndex)

                            // 메시지 구성
                            player.spigot().sendMessage(
                                TextComponent(beforeLink),
                                clickableLink,
                                TextComponent(afterLink)
                            )
                        }
                    }
                }

//                val nextSeasonItemMessage = TextComponent("                     §a§l[다음 시즌에 가져갈 아이템 넣기]")
//                nextSeasonItemMessage.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/opennextseasongui")
//                nextSeasonItemMessage.hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
//                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
//                    arrayOf(TextComponent("§a§l클릭하여 아이템을 추가하세요"))
//                )
//                player.spigot().sendMessage(nextSeasonItemMessage)
//
//                player.sendMessage("")
//
//                val halloweenItemRegisterMessage = TextComponent("                            §6§l[할로윈 아이템 등록]")
//                halloweenItemRegisterMessage.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/할로윈 아이템 소유")
//                halloweenItemRegisterMessage.hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
//                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
//                    arrayOf(TextComponent("§a§l인벤토리에 아이템을 넣고 클릭하여 등록하세요"))
//                )
//                player.spigot().sendMessage(halloweenItemRegisterMessage)
//
//                player.sendMessage("")

//                val halloweenItemRegisterMessage = TextComponent("                       §6§l[할로윈 아이템 스크롤 받기]")
//                halloweenItemRegisterMessage.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/할로윈 아이템 받기")
//                halloweenItemRegisterMessage.hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
//                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
//                    arrayOf(TextComponent("§a§l클릭하여 할로윈 아이템 스크롤을 받으세요"))
//                )
//                player.spigot().sendMessage(halloweenItemRegisterMessage)
//
//                player.sendMessage("")

//                val ValentineShieldItemMessage = TextComponent("                       §d§l[발렌타인 방패 아이템 받기]")
//                ValentineShieldItemMessage.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/발렌타인방패받기")
//                ValentineShieldItemMessage.hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
//                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
//                    arrayOf(TextComponent("§f§l클릭하여 발렌타인 방패 아이템 받으세요"))
//                )
//                player.spigot().sendMessage(ValentineShieldItemMessage)
//
//                player.sendMessage("")

                // 할로윈 아이템 스크롤 받기 버튼과 동일한 형식으로 아이템 복구 버튼 추가
                val itemRestoreMessage = TextComponent("                     §a§l[클릭하여 인벤토리 아이템 복구]")
                itemRestoreMessage.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/아이템복구")
                itemRestoreMessage.hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    arrayOf(TextComponent("§a§l클릭하여 인벤토리의 깨진 아이템을 복구하세요"))
                )
                player.spigot().sendMessage(itemRestoreMessage)

                player.sendMessage("")

                val mapLink = TextComponent("                       §a§l[클릭하여 지도사이트로 이동]")
                mapLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, "https://map.mine.lukehemmin.com/")
                player.spigot().sendMessage(mapLink)
                player.sendMessage("")
            }, 60L)
        } else if (serviceType == "Lobby") {
            // Lobby Server Join
            val message = joinMessages["LobbyServerJoin"]?.replace("{playerName}", player.name)
            event.joinMessage = message

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
        val uuid = player.uniqueId.toString()

        // Player_Auth 테이블에서 IsAuth 확인
        var isAuth = true
        database.getConnection().use { connection ->
            val checkAuth = connection.prepareStatement("SELECT IsAuth FROM Player_Auth WHERE UUID = ?")
            checkAuth.setString(1, uuid)
            val authResult = checkAuth.executeQuery()
            if (authResult.next()) {
                isAuth = authResult.getInt("IsAuth") != 0
            }
            authResult.close()
            checkAuth.close()
        }

        // 인증되지 않은 플레이어는 퇴장 메시지 표시 안 함
        if (!isAuth) {
            event.quitMessage = null
            return
        }
        
        // 특정 권한(lukevanilla.hidemessages)을 가진 플레이어는 퇴장 메시지 표시 안 함
        if (player.hasPermission("lukevanilla.hidemessages")) {
            event.quitMessage = null
            return
        }

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
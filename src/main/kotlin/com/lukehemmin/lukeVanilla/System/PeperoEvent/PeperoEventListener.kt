package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.Main
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * 빼빼로 이벤트 리스너
 * 플레이어 접속 시 이벤트 메시지 표시
 */
class PeperoEventListener(
    private val plugin: Main,
    private val repository: PeperoEventRepository,
    private val gui: PeperoEventGUI,
    private val logger: Logger
) : Listener {

    private val externalUrl = plugin.config.getString("pepero_event.external_protocol", "https") +
            "://" + plugin.config.getString("pepero_event.external_domain", "peperoday2025.lukehemmin.com")

    private val eventStartDate = parseDateTime(plugin.config.getString("pepero_event.event_start_date", "2025-11-09 00:00:00"))
    private val eventEndDate = parseDateTime(plugin.config.getString("pepero_event.event_end_date", "2025-11-11 23:59:59"))
    private val itemGiveDate = LocalDate.parse(plugin.config.getString("pepero_event.item_give_date", "2025-11-11"))

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()

        // 100틱(5초) 후 메시지 표시 (채팅 청소 및 기본 메시지 이후)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val now = LocalDateTime.now()

            // 11월 11일 당일인지 확인
            val today = LocalDate.now()
            if (today == itemGiveDate) {
                showItemGiveMessage(player, uuid)
                return@Runnable
            }

            // 웹 이벤트 기간 확인 (11/9 ~ 11/11)
            if (now.isAfter(eventStartDate) && now.isBefore(eventEndDate)) {
                showWebEventMessage(player, uuid)
            }
        }, 100L)
    }

    /**
     * 웹 이벤트 메시지 표시 (11/9 ~ 11/11 기간, 미참여자만)
     */
    private fun showWebEventMessage(player: org.bukkit.entity.Player, uuid: String) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            // 이미 참여했는지 확인
            val hasParticipated = repository.hasParticipated(uuid)
            if (hasParticipated) return@Runnable

            // 원타임 토큰 생성
            val token = repository.createOneTimeToken(uuid, player.name)
            val eventUrl = "$externalUrl/pepero?t=$token"
            val infoUrl = "$externalUrl/info"

            // 메인 스레드에서 메시지 전송
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("")
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                player.sendMessage("")
                player.sendMessage("  §f혹시 서버 유저 중에서 §6빼빼로§f를")
                player.sendMessage("  §f선물로 주고 싶은 유저가 있나요!?")
                player.sendMessage("")

                // 클릭 가능한 링크
                val eventLink = TextComponent("  §e§l[여기를 클릭]")
                eventLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, eventUrl)
                eventLink.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    arrayOf(TextComponent("§a§l클릭하여 이벤트 참여하기"))
                )
                val eventText = TextComponent("§f 해서 어떤 유저인지 알려주세요!")
                player.spigot().sendMessage(eventLink, eventText)

                player.sendMessage("")
                player.sendMessage("  §7( 내용관리와 익명의 메시지 관리에 대한 내용은")

                val infoLink = TextComponent("  §7[§e§l여기를 클릭§7]")
                infoLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, infoUrl)
                infoLink.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    arrayOf(TextComponent("§a§l클릭하여 정책 확인하기"))
                )
                val infoText = TextComponent(" §7하여 확인하세요. )")
                player.spigot().sendMessage(infoLink, infoText)

                player.sendMessage("")
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                player.sendMessage("")
            })
        })
    }

    /**
     * 아이템 지급 메시지 표시 (11월 11일 당일, 미수령자만)
     */
    private fun showItemGiveMessage(player: org.bukkit.entity.Player, uuid: String) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            // 이미 받았는지 확인
            val hasReceived = repository.hasReceivedItem(uuid)
            if (hasReceived) return@Runnable

            // 현재 시각이 11월 11일 범위 내인지 확인 (KST 기준)
            val now = LocalDateTime.now()
            val itemDate = itemGiveDate.atStartOfDay()
            val itemDateEnd = itemDate.plusDays(1)

            if (now.isBefore(itemDate) || now.isAfter(itemDateEnd)) {
                return@Runnable // 시간이 지나면 표시 안 함
            }

            // 메인 스레드에서 메시지 전송
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("")
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                player.sendMessage("")
                player.sendMessage("  §f이번 §6빼빼로 무기§f는 무료로 드릴게요!")
                player.sendMessage("  §f오늘 접속해주셔서 감사해요!")
                player.sendMessage("")

                // 클릭 가능한 링크 (명령어 실행)
                val receiveLink = TextComponent("  §e§l[여기를 클릭하여 빼빼로 아이템 받기]")
                receiveLink.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/빼빼로받기")
                receiveLink.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    arrayOf(TextComponent("§a§l클릭하여 빼빼로 아이템 받기"))
                )
                player.spigot().sendMessage(receiveLink)

                player.sendMessage("")
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                player.sendMessage("")
            })
        })
    }

    /**
     * 문자열을 LocalDateTime으로 파싱
     */
    private fun parseDateTime(dateTimeStr: String?): LocalDateTime {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            LocalDateTime.parse(dateTimeStr, formatter)
        } catch (e: Exception) {
            logger.warning("[PeperoEventListener] 날짜 파싱 실패: $dateTimeStr")
            LocalDateTime.now()
        }
    }
}

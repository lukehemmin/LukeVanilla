package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.ChatColor
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ServerTimeCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 서버의 기본 시간대
        val serverZoneId = ZoneId.systemDefault()
        val serverTime = ZonedDateTime.now(serverZoneId)

        // UTC 시간
        val utcTime = serverTime.withZoneSameInstant(ZoneId.of("UTC"))

        // 포매터 설정 (한국어 로케일 사용)
        val formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분 ss초 EEEE", Locale.KOREAN)

        // 메시지 생성
        sender.sendMessage("${ChatColor.GREEN}===== 서버 시간 정보 =====")
        sender.sendMessage("${ChatColor.AQUA}현재 서버 시간 (${serverZoneId}):")
        sender.sendMessage(" ${ChatColor.WHITE}${serverTime.format(formatter)}")
        sender.sendMessage("${ChatColor.AQUA}UTC 시간:")
        sender.sendMessage(" ${ChatColor.WHITE}${utcTime.format(formatter)}")
        sender.sendMessage("${ChatColor.GREEN}========================")

        return true
    }
} 
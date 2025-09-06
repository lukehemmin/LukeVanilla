package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.ChatColor
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

class ServerTimeCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 서버의 실제 시간대 설정 정보 수집
        val systemTimeZone = TimeZone.getDefault()
        val systemZoneId = ZoneId.systemDefault()
        val userTimezone = System.getProperty("user.timezone") ?: "시스템 기본값"
        
        // 현재 시간 계산
        val serverTime = ZonedDateTime.now(systemZoneId)
        val utcTime = serverTime.withZoneSameInstant(ZoneId.of("UTC"))
        val kstTime = serverTime.withZoneSameInstant(ZoneId.of("Asia/Seoul"))

        // 포매터 설정
        val formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분 ss초 EEEE", Locale.KOREAN)
        val shortFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREAN)

        // 메시지 생성
        sender.sendMessage("${ChatColor.GREEN}========== 서버 시간대 정보 ==========")
        sender.sendMessage("")
        
        // 서버의 실제 시간대 설정
        sender.sendMessage("${ChatColor.YELLOW}⚙️ 서버 시스템 시간대 설정:")
        sender.sendMessage("${ChatColor.WHITE}  • 시스템 기본 시간대: ${ChatColor.AQUA}${systemZoneId}")
        sender.sendMessage("${ChatColor.WHITE}  • 시간대 ID: ${ChatColor.AQUA}${systemTimeZone.id}")
        sender.sendMessage("${ChatColor.WHITE}  • 시간대 표시명: ${ChatColor.AQUA}${systemTimeZone.displayName}")
        sender.sendMessage("${ChatColor.WHITE}  • user.timezone 속성: ${ChatColor.AQUA}${userTimezone}")
        sender.sendMessage("")
        
        // 현재 시간 표시 (서버 기본 시간대 강조)  
        sender.sendMessage("${ChatColor.GOLD}🕐 현재 시간 (서버 기본 시간대):")
        sender.sendMessage("${ChatColor.WHITE}  ${ChatColor.YELLOW}▶ ${systemZoneId}: ${ChatColor.WHITE}${serverTime.format(formatter)}")
        sender.sendMessage("")
        
        // 참고용 다른 시간대들
        sender.sendMessage("${ChatColor.GRAY}📍 참고용 다른 시간대:")
        
        // UTC 시간 표시
        sender.sendMessage("${ChatColor.GRAY}  • UTC: ${utcTime.format(shortFormatter)} (${utcTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))})")
        
        // KST 시간 표시 (서버 시간대가 KST가 아닌 경우에만)
        if (systemZoneId.id != "Asia/Seoul") {
            sender.sendMessage("${ChatColor.GRAY}  • KST: ${kstTime.format(shortFormatter)} (${kstTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))})")
        }
        
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GREEN}====================================")
        
        // 요약 정보
        sender.sendMessage("${ChatColor.YELLOW}💡 요약: 이 서버는 ${ChatColor.AQUA}${systemZoneId}${ChatColor.YELLOW} 시간대로 설정되어 있습니다.")

        return true
    }
} 
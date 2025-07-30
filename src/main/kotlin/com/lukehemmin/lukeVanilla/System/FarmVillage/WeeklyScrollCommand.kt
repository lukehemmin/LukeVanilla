package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class WeeklyScrollCommand(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : CommandExecutor, TabCompleter {
    
    private val weeklyScrollRotationSystem: WeeklyScrollRotationSystem
        get() = farmVillageManager.getWeeklyScrollRotationSystem()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lukevanilla.admin.weeklyscroll")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "상태", "status" -> {
                showStatus(sender)
            }
            
            "디버그", "debug" -> {
                showDebugInfo(sender)
            }
            
            "다음주", "next" -> {
                val nextWeek = weeklyScrollRotationSystem.forceNextWeek()
                if (nextWeek != null) {
                    sender.sendMessage(Component.text("주차별 스크롤을 다음주로 강제 변경했습니다: $nextWeek", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("주차 변경에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "이전주", "previous", "prev" -> {
                val prevWeek = weeklyScrollRotationSystem.forcePreviousWeek()
                if (prevWeek != null) {
                    sender.sendMessage(Component.text("주차별 스크롤을 이전주로 강제 변경했습니다: $prevWeek", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("주차 변경에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "설정", "set" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("사용법: /주차스크롤 설정 <주차> (예: 2025-W30)", NamedTextColor.YELLOW))
                    return true
                }
                
                val weekString = args[1]
                if (!isValidWeekFormat(weekString)) {
                    sender.sendMessage(Component.text("잘못된 주차 형식입니다. 형식: YYYY-WXX (예: 2025-W30)", NamedTextColor.RED))
                    return true
                }
                
                if (weeklyScrollRotationSystem.forceSetWeek(weekString)) {
                    sender.sendMessage(Component.text("주차별 스크롤을 강제로 설정했습니다: $weekString", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("주차 설정에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "해제", "disable", "auto" -> {
                if (weeklyScrollRotationSystem.disableForceMode()) {
                    sender.sendMessage(Component.text("강제 주차 설정을 해제했습니다. 자동 계산으로 복귀합니다.", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("설정 해제에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "gui" -> {
                if (sender !is Player) {
                    sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED))
                    return true
                }
                
                farmVillageManager.getWeeklyScrollExchangeGUI().openGUI(sender)
            }
            
            else -> {
                showUsage(sender)
            }
        }
        
        return true
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("===== 주차별 스크롤 관리 명령어 =====", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/주차스크롤 상태 - 현재 주차 및 강제 설정 상태 확인", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/주차스크롤 디버그 - 상세 디버깅 정보 확인", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/주차스크롤 다음주 - 다음주로 강제 변경", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/주차스크롤 이전주 - 이전주로 강제 변경", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/주차스크롤 설정 <주차> - 특정 주차로 강제 설정", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/주차스크롤 해제 - 강제 설정 해제 (자동 계산 복귀)", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/주차스크롤 gui - GUI 테스트 열기", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("예: /주차스크롤 설정 2025-W30", NamedTextColor.GRAY))
    }

    private fun showStatus(sender: CommandSender) {
        val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
        val currentRotation = weeklyScrollRotationSystem.getCurrentRotation()
        val (forceWeek, forceEnabled) = weeklyScrollRotationSystem.getForceStatus()
        val debugInfo = weeklyScrollRotationSystem.getDebugInfo()
        
        sender.sendMessage(Component.text("===== 주차별 스크롤 상태 =====", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("현재 날짜: ${debugInfo["currentDate"]} (${debugInfo["dayOfWeek"]})", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("현재 주차: $currentWeek (ISO 8601)", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("주기반년도: ${debugInfo["weekBasedYear"]}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("주차번호: ${debugInfo["weekOfWeekBasedYear"]}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("현재 시즌: ${currentRotation.displayName}", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("스크롤 개수: ${currentRotation.scrollIds.size}개", NamedTextColor.YELLOW))
        
        if (forceEnabled && forceWeek != null) {
            sender.sendMessage(Component.text("⚠ 강제 설정 모드: $forceWeek", NamedTextColor.RED))
        } else {
            sender.sendMessage(Component.text("✓ 자동 계산 모드 (KST 기준, ISO 8601)", NamedTextColor.GREEN))
        }
        
        val nextRotation = weeklyScrollRotationSystem.getNextRotation()
        sender.sendMessage(Component.text("다음 시즌: ${nextRotation.displayName}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("다음 로테이션까지: ${debugInfo["timeUntilNext"]}", NamedTextColor.GRAY))
    }
    
    private fun showDebugInfo(sender: CommandSender) {
        val debugInfo = weeklyScrollRotationSystem.getDebugInfo()
        
        sender.sendMessage(Component.text("===== 주차별 스크롤 디버깅 정보 =====", NamedTextColor.DARK_AQUA))
        sender.sendMessage(Component.text("📅 날짜 정보:", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  - 현재 날짜: ${debugInfo["currentDate"]}", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  - 요일: ${debugInfo["dayOfWeek"]}", NamedTextColor.WHITE))
        
        sender.sendMessage(Component.text("📊 ISO 8601 주차 계산:", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  - 주차 문자열: ${debugInfo["currentWeekString"]}", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  - 주기반년도: ${debugInfo["weekBasedYear"]}", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  - 주차번호: ${debugInfo["weekOfWeekBasedYear"]}", NamedTextColor.WHITE))
        
        sender.sendMessage(Component.text("🔄 로테이션 정보:", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  - 현재 시즌: ${debugInfo["currentRotationDisplay"]} (${debugInfo["currentRotation"]})", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  - 다음 시즌: ${debugInfo["nextRotation"]}", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  - 다음 변경까지: ${debugInfo["timeUntilNext"]}", NamedTextColor.WHITE))
        
        sender.sendMessage(Component.text("⚙️ 강제 설정 상태:", NamedTextColor.YELLOW))
        val (forceWeek, forceEnabled) = debugInfo["forceStatus"] as Pair<*, *>
        if (forceEnabled as Boolean && forceWeek != null) {
            sender.sendMessage(Component.text("  - 강제 모드: 활성 ($forceWeek)", NamedTextColor.RED))
        } else {
            sender.sendMessage(Component.text("  - 강제 모드: 비활성 (자동 계산)", NamedTextColor.GREEN))
        }
        
        sender.sendMessage(Component.text("ℹ️ 참고: ISO 8601 표준은 월요일을 주의 시작으로 하며,", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("    첫 번째 목요일이 포함된 주를 그 해의 1주차로 계산합니다.", NamedTextColor.GRAY))
    }

    private fun isValidWeekFormat(weekString: String): Boolean {
        val pattern = Regex("^\\d{4}-W\\d{2}$")
        return pattern.matches(weekString)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("lukevanilla.admin.weeklyscroll")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("상태", "디버그", "다음주", "이전주", "설정", "해제", "gui").filter { 
                it.startsWith(args[0], ignoreCase = true) 
            }
            2 -> if (args[0].equals("설정", ignoreCase = true)) {
                val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
                listOf(currentWeek, "2025-W30", "2025-W31")
            } else emptyList()
            else -> emptyList()
        }
    }
}

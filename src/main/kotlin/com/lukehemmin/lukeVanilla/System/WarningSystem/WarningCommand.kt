package com.lukehemmin.lukeVanilla.System.WarningSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Logger

/**
 * 경고 시스템 명령어 처리 클래스
 */
class WarningCommand(database: Database, jda: JDA) : CommandExecutor, TabCompleter {
    private val logger = Logger.getLogger(WarningCommand::class.java.name)
    private val warningService = WarningService(database, jda)
    private val warningNotifier = WarningNotifier()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    companion object {
        const val WARN_PERMISSION = "advancedwarnings.warn"
        const val PARDON_PERMISSION = "advancedwarnings.pardon"
        const val CHECK_PERMISSION = "advancedwarnings.check"
        const val LIST_PERMISSION = "advancedwarnings.list"
        const val NOTIFY_WARN_PERMISSION = "advancedwarnings.notify.warn"
        const val NOTIFY_PARDON_PERMISSION = "advancedwarnings.notify.pardon"
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true) || args[0].equals("도움말", ignoreCase = true)) {
            sendHelpMessage(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "주기" -> handleWarnCommand(sender, args)
            "차감" -> handlePardonCommand(sender, args)
            "확인" -> handleCheckCommand(sender, args)
            "목록" -> handleListCommand(sender, args)
            else -> sendHelpMessage(sender)
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.isEmpty()) return emptyList()
        
        val completions = mutableListOf<String>()
        
        when (args.size) {
            1 -> {
                completions.addAll(listOf("주기", "차감", "확인", "목록", "help", "도움말"))
            }
            2 -> {
                // 플레이어 이름 자동완성
                completions.addAll(Bukkit.getOnlinePlayers().map { it.name })
            }
            3 -> {
                if (args[0].equals("차감", ignoreCase = true)) {
                    completions.add("<경고ID 또는 횟수>")
                }
            }
        }
        
        return completions.filter { it.startsWith(args[args.size - 1], ignoreCase = true) }
    }
    
    /**
     * 경고 부여 명령어 처리
     */
    private fun handleWarnCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(WARN_PERMISSION)) {
            sender.sendMessage(createErrorMessage("이 명령어를 사용할 권한이 없습니다."))
            return
        }
        
        if (args.size < 3) {
            sender.sendMessage(createErrorMessage("사용법: /경고 주기 <플레이어 이름> <사유>"))
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetPlayerName)
        
        if (targetPlayer == null) {
            sender.sendMessage(createErrorMessage("플레이어 '$targetPlayerName'를 찾을 수 없습니다."))
            return
        }
        
        val reason = args.copyOfRange(2, args.size).joinToString(" ")
        val adminUuid: UUID
        val adminName: String
        
        if (sender is Player) {
            adminUuid = sender.uniqueId
            adminName = sender.name
        } else {
            adminUuid = UUID(0, 0) // 콘솔용 더미 UUID
            adminName = "콘솔"
        }
        
        val result = warningService.addWarning(
            targetPlayer = targetPlayer,
            adminUuid = adminUuid,
            adminName = adminName,
            reason = reason
        )
        
        if (result.first) {
            val warningCount = result.second
            val autoBanned = result.third
            
            // 성공 메시지 전송
            val successMessage = if (autoBanned) {
                "${targetPlayer.name}님에게 경고를 부여했습니다. (현재 ${warningCount}회) - 경고 횟수 초과로 자동 차단되었습니다."
            } else {
                "${targetPlayer.name}님에게 경고를 부여했습니다. (현재 ${warningCount}회)"
            }
            sender.sendMessage(createSuccessMessage(successMessage))
            
            // 대상 플레이어에게 알림 (자동 차단되지 않은 경우에만)
            if (!autoBanned) {
                warningNotifier.notifyPlayerWarned(
                    player = targetPlayer,
                    adminName = adminName,
                    reason = reason,
                    warningCount = warningCount
                )
            }
            
            // 알림 권한이 있는 관리자들에게 알림
            warningNotifier.notifyAdmins(
                targetPlayer = targetPlayer,
                adminName = adminName,
                reason = reason,
                warningCount = warningCount,
                notifyPermission = NOTIFY_WARN_PERMISSION,
                autoBanned = autoBanned
            )
        } else {
            sender.sendMessage(createErrorMessage("경고 부여 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 경고 차감 명령어 처리
     */
    private fun handlePardonCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PARDON_PERMISSION)) {
            sender.sendMessage(createErrorMessage("이 명령어를 사용할 권한이 없습니다."))
            return
        }
        
        if (args.size < 4) {
            sender.sendMessage(createErrorMessage("사용법: /경고 차감 <플레이어 이름> <경고ID 또는 횟수> <사유>"))
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = Bukkit.getPlayerExact(targetPlayerName)
        val targetUuid: UUID
        
        if (targetPlayer != null) {
            targetUuid = targetPlayer.uniqueId
        } else {
            // 오프라인 플레이어 지원 (UUID 조회가 필요)
            val offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerName)
            if (!offlinePlayer.hasPlayedBefore()) {
                sender.sendMessage(createErrorMessage("플레이어 '$targetPlayerName'를 찾을 수 없습니다."))
                return
            }
            targetUuid = offlinePlayer.uniqueId
        }
        
        val warningIdOrCount = args[2]
        val reason = args.copyOfRange(3, args.size).joinToString(" ")
        val adminUuid: UUID
        val adminName: String
        
        if (sender is Player) {
            adminUuid = sender.uniqueId
            adminName = sender.name
        } else {
            adminUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
            adminName = "콘솔"
        }
        
        // 경고 ID로 차감인지 횟수로 차감인지 확인
        if (warningIdOrCount.all { it.isDigit() }) {
            val number = warningIdOrCount.toInt()
            
            if (number <= 0) {
                sender.sendMessage(createErrorMessage("경고 ID 또는 횟수는 0보다 커야 합니다."))
                return
            }
            
            // 경고 목록 조회
            val warnings = warningService.getPlayerWarnings(targetUuid)
            
            // 경고 ID 기반 차감
            if (warnings.any { it.warningId == number && it.isActive }) {
                val success = warningService.pardonWarningById(
                    targetPlayerUuid = targetUuid,
                    warningId = number,
                    adminUuid = adminUuid,
                    adminName = adminName,
                    reason = reason
                )
                
                if (success) {
                    sender.sendMessage(createSuccessMessage("'${targetPlayerName}'의 경고 ID ${number}를 차감했습니다."))
                    
                    // 플레이어가 온라인이면 알림
                    targetPlayer?.let {
                        warningNotifier.notifyPlayerPardoned(
                            player = it,
                            adminName = adminName,
                            reason = reason,
                            count = 1,
                            isIdBased = true
                        )
                    }
                    
                    // 권한 있는 관리자들에게 알림
                    warningNotifier.notifyAdminsPardoned(adminName, targetPlayerName, number.toString(), reason, true, NOTIFY_PARDON_PERMISSION)
                } else {
                    sender.sendMessage(createErrorMessage("경고 차감 중 오류가 발생했습니다."))
                }
            } 
            // 횟수 기반 차감
            else {
                val (success, actualPardoned) = warningService.pardonWarningsByCount(
                    targetPlayerUuid = targetUuid,
                    count = number,
                    adminUuid = adminUuid,
                    adminName = adminName,
                    reason = reason
                )
                
                if (success) {
                    sender.sendMessage(createSuccessMessage("'${targetPlayerName}'의 경고 ${actualPardoned}회를 차감했습니다."))
                    
                    // 플레이어가 온라인이면 알림
                    targetPlayer?.let {
                        warningNotifier.notifyPlayerPardoned(
                            player = it,
                            adminName = adminName,
                            reason = reason,
                            count = actualPardoned,
                            isIdBased = false
                        )
                    }
                    
                    // 권한 있는 관리자들에게 알림
                    warningNotifier.notifyAdminsPardoned(adminName, targetPlayerName, actualPardoned.toString(), reason, false, NOTIFY_PARDON_PERMISSION)
                } else {
                    sender.sendMessage(createErrorMessage("차감할 유효 경고가 없거나 오류가 발생했습니다."))
                }
            }
        } else {
            sender.sendMessage(createErrorMessage("경고 ID 또는 횟수는 숫자여야 합니다."))
        }
    }
    
    /**
     * 경고 확인 명령어 처리
     */
    private fun handleCheckCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(CHECK_PERMISSION)) {
            sender.sendMessage(createErrorMessage("이 명령어를 사용할 권한이 없습니다."))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(createErrorMessage("사용법: /경고 확인 <플레이어 이름>"))
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = Bukkit.getPlayerExact(targetPlayerName)
        val targetUuid: UUID
        
        if (targetPlayer != null) {
            targetUuid = targetPlayer.uniqueId
        } else {
            // 오프라인 플레이어 지원
            val offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerName)
            if (!offlinePlayer.hasPlayedBefore()) {
                sender.sendMessage(createErrorMessage("플레이어 '$targetPlayerName'를 찾을 수 없습니다."))
                return
            }
            targetUuid = offlinePlayer.uniqueId
        }
        
        val warnings = warningService.getPlayerWarnings(targetUuid)
        val activeWarningsCount = warnings.count { it.isActive }
        
        sender.sendMessage(createInfoHeader("'$targetPlayerName'의 경고 내역 (현재 경고: $activeWarningsCount)"))
        
        if (warnings.isEmpty()) {
            sender.sendMessage(createInfoMessage("경고 내역이 없습니다."))
            return
        }
        
        warnings.forEach { warning ->
            sender.sendMessage(createWarningDetailMessage(warning))
        }
    }
    
    /**
     * 경고 목록 명령어 처리
     */
    private fun handleListCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(LIST_PERMISSION)) {
            sender.sendMessage(createErrorMessage("이 명령어를 사용할 권한이 없습니다."))
            return
        }
        
        val page = if (args.size > 1 && args[1].all { it.isDigit() }) args[1].toInt() else 1
        val playersPerPage = 10
        
        val players = warningService.getWarnedPlayers(page, playersPerPage)
        val totalPlayers = warningService.getWarnedPlayersCount()
        val totalPages = (totalPlayers + playersPerPage - 1) / playersPerPage
        
        sender.sendMessage(createInfoHeader("경고 받은 플레이어 목록 (페이지 $page/$totalPages)"))
        
        if (players.isEmpty()) {
            sender.sendMessage(createInfoMessage("이 페이지에 표시할 플레이어가 없습니다."))
            return
        }
        
        players.forEach { player ->
            val component = Component.text()
                .append(Component.text("${player.username} - 현재 경고: ${player.activeWarningsCount}", NamedTextColor.YELLOW))
                .append(Component.text(" [상세보기]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/경고 확인 ${player.username}")))
                .build()
                
            sender.sendMessage(component)
        }
        
        // 페이지 이동 버튼
        if (totalPages > 1) {
            val pageNavComponent = Component.text()
            
            if (page > 1) {
                pageNavComponent.append(Component.text("[이전 페이지] ", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/경고 목록 ${page - 1}")))
            }
            
            if (page < totalPages) {
                pageNavComponent.append(Component.text("[다음 페이지]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/경고 목록 ${page + 1}")))
            }
            
            sender.sendMessage(pageNavComponent.build())
        }
    }
    
    /**
     * 도움말 메시지 전송
     */    private fun sendHelpMessage(sender: CommandSender) {
        // 일반 유저인 경우 (관리자 권한이 없는 경우)
        if (!sender.hasPermission(WARN_PERMISSION) && !sender.hasPermission(PARDON_PERMISSION) && 
            !sender.hasPermission(CHECK_PERMISSION) && !sender.hasPermission(LIST_PERMISSION)) {
            
            if (sender is Player) {
                // 플레이어 자신의 경고 현황 표시
                val playerWarning = warningService.getPlayerWarning(sender.uniqueId, sender.name)
                
                sender.sendMessage(createInfoHeader("내 경고 현황"))
                sender.sendMessage(createInfoMessage("현재 누적 경고 횟수: ${playerWarning.activeWarningsCount}회"))
                
                if (playerWarning.activeWarningsCount > 0) {
                    val remainingWarnings = WarningService.AUTO_BAN_THRESHOLD - playerWarning.activeWarningsCount
                    if (remainingWarnings > 0) {
                        sender.sendMessage(createWarningMessage("⚠️ ${remainingWarnings}회 더 경고를 받으면 자동으로 차단됩니다."))
                    } else {
                        sender.sendMessage(createErrorMessage("⚠️ 경고 한계치에 도달했습니다. 추가 경고 시 즉시 차단됩니다."))
                    }
                } else {
                    sender.sendMessage(createSuccessMessage("✅ 현재 경고가 없습니다."))
                }
                
                sender.sendMessage(createInfoMessage(""))
                sender.sendMessage(createInfoMessage("📋 경고 시스템 안내:"))
                sender.sendMessage(createInfoMessage("• 누적 ${WarningService.AUTO_BAN_THRESHOLD}회 경고 시 자동 차단됩니다"))
                sender.sendMessage(createInfoMessage("• 경고는 서버 규칙 위반 시 부여됩니다"))
                sender.sendMessage(createInfoMessage("• 경고에 대해 이의가 있으시면 관리자에게 문의하세요"))
            } else {
                sender.sendMessage(createInfoMessage("경고 시스템은 플레이어만 사용할 수 있습니다."))
            }
            return
        }
        
        // 관리자인 경우 기존 명령어 도움말 표시
        sender.sendMessage(createInfoHeader("경고 시스템 명령어 도움말"))
        sender.sendMessage(createInfoMessage("/경고 또는 /경고 도움말 - 이 도움말을 표시합니다."))
        
        if (sender.hasPermission(WARN_PERMISSION)) {
            sender.sendMessage(createInfoMessage("/경고 주기 <플레이어> <사유> - 플레이어에게 경고를 부여합니다. ${WarningService.AUTO_BAN_THRESHOLD}회 이상 누적 시 자동 차단됩니다."))
        }
        
        if (sender.hasPermission(PARDON_PERMISSION)) {
            sender.sendMessage(createInfoMessage("/경고 차감 <플레이어> <경고ID 또는 횟수> <사유> - 플레이어의 경고를 차감합니다."))
        }
        
        if (sender.hasPermission(CHECK_PERMISSION)) {
            sender.sendMessage(createInfoMessage("/경고 확인 <플레이어> - 플레이어의 경고 내역을 확인합니다."))
        }
        
        if (sender.hasPermission(LIST_PERMISSION)) {
            sender.sendMessage(createInfoMessage("/경고 목록 [페이지] - 경고 받은 플레이어 목록을 확인합니다."))
        }
    }
    
    /**
     * 경고 상세 정보 메시지 생성
     */
    private fun createWarningDetailMessage(warning: WarningRecord): TextComponent {
        val component = Component.text()
        
        val warningId = warning.warningId ?: 0
        val dateStr = warning.createdAt.format(dateFormatter)
        
        component.append(Component.text("ID: $warningId | 관리자: ${warning.adminName} | 시각: $dateStr", NamedTextColor.YELLOW))
            .append(Component.newline())
        
        // 경고 사유 표시 (차감 여부에 따라 다르게)
        if (warning.isActive) {
            component.append(Component.text("사유: ${warning.reason}", NamedTextColor.WHITE))
        } else {
            // 차감된 경고 - 취소선 처리 및 차감 정보 표시
            component.append(Component.text("사유: ", NamedTextColor.WHITE))
                .append(Component.text(warning.reason, NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH))
                
            val pardonedAt = warning.pardonedAt?.format(dateFormatter) ?: "알 수 없음"
            val pardonedBy = warning.pardonedByName ?: "알 수 없음"
            
            component.append(Component.text(" (차감됨)", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("차감자: $pardonedBy | 차감 시각: $pardonedAt", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("차감 사유: ${warning.pardonReason ?: "없음"}", NamedTextColor.RED))
        }
        
        return component.append(Component.newline())
            .append(Component.text("------------------------", NamedTextColor.DARK_GRAY))
            .build()
    }
    
    /**
     * 정보 헤더 메시지 생성
     */
    private fun createInfoHeader(message: String): TextComponent {
        return Component.text()
            .append(Component.text("=== ", NamedTextColor.GOLD))
            .append(Component.text(message, NamedTextColor.YELLOW))
            .append(Component.text(" ===", NamedTextColor.GOLD))
            .build()
    }
    
    /**
     * 정보 메시지 생성
     */
    private fun createInfoMessage(message: String): TextComponent {
        return Component.text(message, NamedTextColor.YELLOW)
    }
    
    /**
     * 성공 메시지 생성
     */
    private fun createSuccessMessage(message: String): TextComponent {
        return Component.text(message, NamedTextColor.GREEN)
    }
      /**
     * 오류 메시지 생성
     */
    private fun createErrorMessage(message: String): TextComponent {
        return Component.text(message, NamedTextColor.RED)
    }
    
    /**
     * 경고 메시지 생성
     */
    private fun createWarningMessage(message: String): TextComponent {
        return Component.text(message, NamedTextColor.GOLD)
    }
}

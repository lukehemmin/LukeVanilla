package com.lukehemmin.lukeVanilla.System.WarningSystem

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * 경고 시스템 알림 처리를 담당하는 클래스
 */
class WarningNotifier {

    /**
     * 플레이어에게 경고를 받았음을 알림
     */
    fun notifyPlayerWarned(player: Player, adminName: String, reason: String, warningCount: Int) {
        val message = Component.text()
            .append(Component.text("경고를 받았습니다!", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text("관리자: ", NamedTextColor.GOLD))
            .append(Component.text(adminName, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("사유: ", NamedTextColor.GOLD))
            .append(Component.text(reason, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("현재 누적 경고 횟수: ", NamedTextColor.GOLD))
            .append(Component.text("$warningCount", NamedTextColor.RED, TextDecoration.BOLD))
            .build()
        
        player.sendMessage(message)
    }
    
    /**
     * 플레이어에게 경고가 차감되었음을 알림
     */
    fun notifyPlayerPardoned(player: Player, adminName: String, reason: String, count: Int, isIdBased: Boolean) {
        val countText = if (isIdBased) "ID가" else "${count}회가"
        
        val message = Component.text()
            .append(Component.text("경고가 차감되었습니다!", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text("관리자: ", NamedTextColor.GOLD))
            .append(Component.text(adminName, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("차감된 경고: ", NamedTextColor.GOLD))
            .append(Component.text(countText, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("사유: ", NamedTextColor.GOLD))
            .append(Component.text(reason, NamedTextColor.YELLOW))
            .build()
        
        player.sendMessage(message)
    }
    
    /**
     * 권한이 있는 관리자들에게 경고 부여 알림
     * 
     * @param targetPlayer 경고 대상 플레이어
     * @param adminName 경고를 부여한 관리자 이름
     * @param reason 경고 사유
     * @param warningCount 현재 경고 횟수
     * @param notifyPermission 알림을 받을 권한
     * @param autoBanned 자동 차단 여부
     */
    fun notifyAdmins(
        targetPlayer: Player, 
        adminName: String, 
        reason: String, 
        warningCount: Int,
        notifyPermission: String,
        autoBanned: Boolean = false
    ) {
        val message = Component.text()
            .append(Component.text("[경고 시스템] ", NamedTextColor.GOLD))
            .append(Component.text(adminName, NamedTextColor.YELLOW))
            .append(Component.text("님이 ", NamedTextColor.WHITE))
            .append(Component.text(targetPlayer.name, NamedTextColor.YELLOW))
            .append(Component.text("님에게 경고를 부여했습니다. ", NamedTextColor.WHITE))
            .append(Component.text("(현재 ${warningCount}회)", NamedTextColor.RED, TextDecoration.BOLD))
            
        if (autoBanned) {
            message.append(Component.newline())
                .append(Component.text("⚠ 경고 횟수 초과로 자동 차단되었습니다. ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("(${WarningService.AUTO_BAN_THRESHOLD}회 이상)", NamedTextColor.GOLD))
        }
            
        message.append(Component.newline())
            .append(Component.text("사유: ", NamedTextColor.GOLD))
            .append(Component.text(reason, NamedTextColor.YELLOW))
        
        broadcastToAdmins(message.build(), notifyPermission)
    }
    
    /**
     * 권한이 있는 관리자들에게 경고 부여 알림 (이전 버전 호환용)
     */
    @Deprecated("notifyAdmins 메서드로 대체되었습니다", ReplaceWith("notifyAdmins(targetPlayer, adminName, reason, 0, permission)"))
    fun notifyAdminsWarned(adminName: String, playerName: String, reason: String, permission: String) {
        val message = Component.text()
            .append(Component.text("[경고 시스템] ", NamedTextColor.GOLD))
            .append(Component.text(adminName, NamedTextColor.YELLOW))
            .append(Component.text("님이 ", NamedTextColor.WHITE))
            .append(Component.text(playerName, NamedTextColor.YELLOW))
            .append(Component.text("님에게 경고를 부여했습니다.", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("사유: ", NamedTextColor.GOLD))
            .append(Component.text(reason, NamedTextColor.YELLOW))
            .build()
        
        broadcastToAdmins(message, permission)
    }
    
    /**
     * 권한이 있는 관리자들에게 경고 차감 알림
     */
    fun notifyAdminsPardoned(
        adminName: String, 
        playerName: String, 
        warningIdOrCount: String, 
        reason: String, 
        isIdBased: Boolean,
        permission: String
    ) {
        val targetText = if (isIdBased) "경고 ID $warningIdOrCount" else "경고 ${warningIdOrCount}회"
        
        val message = Component.text()
            .append(Component.text("[경고 시스템] ", NamedTextColor.GOLD))
            .append(Component.text(adminName, NamedTextColor.YELLOW))
            .append(Component.text("님이 ", NamedTextColor.WHITE))
            .append(Component.text(playerName, NamedTextColor.YELLOW))
            .append(Component.text("님의 ${targetText}를 차감했습니다.", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("사유: ", NamedTextColor.GOLD))
            .append(Component.text(reason, NamedTextColor.YELLOW))
            .build()
        
        broadcastToAdmins(message, permission)
    }
    
    /**
     * 특정 권한을 가진 관리자들에게 메시지 전송
     */
    private fun broadcastToAdmins(message: Component, permission: String) {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.hasPermission(permission)) {
                player.sendMessage(message)
            }
        }
    }
}

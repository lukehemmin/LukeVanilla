package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * 마을 초대 수락/거절을 처리하는 명령어 클래스
 */
class VillageInviteCommand(private val landCommand: LandCommand) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return true
        }
        
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "수락", "accept" -> handleAcceptInvitation(sender)
            "거절", "decline", "reject" -> handleDeclineInvitation(sender)
            else -> sendUsage(sender)
        }
        
        return true
    }
    
    /**
     * 마을 초대 수락 처리
     */
    private fun handleAcceptInvitation(player: Player) {
        val invitation = landCommand.getPendingInvitation(player.uniqueId)
        if (invitation == null) {
            player.sendMessage(Component.text("진행 중인 마을 초대가 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 만료 확인
        if (System.currentTimeMillis() > invitation.expiresAt) {
            landCommand.removePendingInvitation(player.uniqueId)
            player.sendMessage(Component.text("마을 초대가 만료되었습니다.", NamedTextColor.RED))
            return
        }
        
        // 마을 가입 처리
        val result = landCommand.acceptVillageInvitation(player, invitation)
        if (result.success) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("🎉 ", NamedTextColor.GREEN))
                    .append(Component.text("마을 '", NamedTextColor.WHITE))
                    .append(Component.text(invitation.villageName, NamedTextColor.YELLOW))
                    .append(Component.text("'에 가입했습니다!", NamedTextColor.WHITE))
            )
            
            // 초대자에게 알림
            val inviter = org.bukkit.Bukkit.getPlayer(invitation.inviterUuid)
            if (inviter != null) {
                inviter.sendMessage(
                    Component.text()
                        .append(Component.text("✅ ", NamedTextColor.GREEN))
                        .append(Component.text("${player.name}님이 마을 초대를 수락했습니다!", NamedTextColor.WHITE))
                )
            }
            
            landCommand.removePendingInvitation(player.uniqueId)
        } else {
            player.sendMessage(Component.text(result.message, NamedTextColor.RED))
        }
    }
    
    /**
     * 마을 초대 거절 처리
     */
    private fun handleDeclineInvitation(player: Player) {
        val invitation = landCommand.getPendingInvitation(player.uniqueId)
        if (invitation == null) {
            player.sendMessage(Component.text("진행 중인 마을 초대가 없습니다.", NamedTextColor.RED))
            return
        }
        
        player.sendMessage(
            Component.text()
                .append(Component.text("❌ ", NamedTextColor.RED))
                .append(Component.text("마을 '", NamedTextColor.WHITE))
                .append(Component.text(invitation.villageName, NamedTextColor.YELLOW))
                .append(Component.text("' 초대를 거절했습니다.", NamedTextColor.WHITE))
        )
        
        // 초대자에게 알림
        val inviter = org.bukkit.Bukkit.getPlayer(invitation.inviterUuid)
        if (inviter != null) {
            inviter.sendMessage(
                Component.text()
                    .append(Component.text("❌ ", NamedTextColor.RED))
                    .append(Component.text("${player.name}님이 마을 초대를 거절했습니다.", NamedTextColor.GRAY))
            )
        }
        
        landCommand.removePendingInvitation(player.uniqueId)
    }
    
    /**
     * 사용법 안내
     */
    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("마을 초대 명령어", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text()
                .append(Component.text("/마을초대 수락", NamedTextColor.GREEN))
                .append(Component.text(" - 마을 초대를 수락합니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text()
                .append(Component.text("/마을초대 거절", NamedTextColor.RED))
                .append(Component.text(" - 마을 초대를 거절합니다.", NamedTextColor.GRAY))
        )
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            return mutableListOf("수락", "거절").filter { 
                it.startsWith(args[0], ignoreCase = true) 
            }.toMutableList()
        }
        return mutableListOf()
    }
}


package com.lukehemmin.lukeVanilla.System.SitSystem

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * /lay 명령어 처리 클래스
 * 플레이어가 현재 위치에 눕게 합니다.
 */
class LayCommand(private val sitManager: SitManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 플레이어만 명령어 사용 가능
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 다른 플레이어 위에 탑승 중인 경우 처리
        if (sitManager.isPlayerMounted(player)) {
            sitManager.dismountPlayer(player)
            player.sendMessage("§a탑승을 취소했습니다.")
            return true
        }

        // 이미 앉아있거나 누워있는 경우 처리
        if (sitManager.isPlayerSitting(player)) {
            sitManager.unsitPlayer(player)
            player.sendMessage("§a자리에서 일어났습니다.")
            return true
        }

        if (sitManager.isPlayerLaying(player)) {
            sitManager.unlayPlayer(player)
            player.sendMessage("§a일어났습니다.")
            return true
        }

        // 눕기 실행
        sitManager.lay(player)

        return true
    }
} 
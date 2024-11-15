package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.System.Items.TransparentFrame
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TransparentFrameCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c§l이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (!sender.hasPermission("lukevanilla.transparentframe")) {
            sender.sendMessage("§c§l명령어를 사용할 권한이 없습니다.")
            return true
        }

        val frame = TransparentFrame.createTransparentFrame()
        sender.inventory.addItem(frame)
        sender.sendMessage("§a§l투명한 프레임을 지급했습니다.")

        return true
    }
}
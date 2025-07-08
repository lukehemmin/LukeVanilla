package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class LandCommand(private val landManager: LandManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return true
        }

        if (args.isEmpty() || (args[0].lowercase() != "정보" && args[0].lowercase() != "info")) {
            sender.sendMessage(Component.text("사용법: /땅 정보", NamedTextColor.RED))
            return true
        }
        
        val chunk = sender.location.chunk
        val ownerId = landManager.getOwnerOfChunk(chunk)

        if (ownerId != null) {
            val ownerName = sender.server.getOfflinePlayer(ownerId).name
            sender.sendMessage(Component.text("이 청크는 ${ownerName}님의 땅입니다.", NamedTextColor.YELLOW))
        } else {
            if (landManager.isChunkInClaimableArea(chunk)) {
                sender.sendMessage(Component.text("이 청크는 주인이 없으며, 보호받지 않는 상태입니다.", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(Component.text("이 청크는 주인이 없으며, 보호받을 수 없는 지역입니다.", NamedTextColor.GRAY))
            }
        }
        
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            return mutableListOf("정보").filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        return mutableListOf()
    }
} 
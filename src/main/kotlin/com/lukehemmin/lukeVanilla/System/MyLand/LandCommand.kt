package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.text.SimpleDateFormat

class LandCommand(private val landManager: LandManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return true
        }

        if (args.isNotEmpty() && args[0].lowercase() != "정보" && args[0].lowercase() != "info") {
            sender.sendMessage(Component.text("사용법: /땅 정보", NamedTextColor.RED))
            return true
        }
        
        val chunk = sender.location.chunk
        val claimInfo = landManager.getClaimInfo(chunk)

        if (claimInfo != null) {
            val ownerName = sender.server.getOfflinePlayer(claimInfo.ownerUuid).name ?: "알 수 없음"
            val worldName = chunk.world.name
            val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss")
            val claimedDate = dateFormat.format(claimInfo.claimedAt)

            sender.sendMessage(
                Component.text()
                    .append(Component.text(" "))
                    .append(Component.text("■", NamedTextColor.GOLD))
                    .append(Component.text(" 현재 청크 정보 ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("■", NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(Component.text("   소유자: ", NamedTextColor.GRAY))
                    .append(Component.text(ownerName, NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text("   위치: ", NamedTextColor.GRAY))
                    .append(Component.text("$worldName ", NamedTextColor.WHITE))
                    .append(Component.text("(${chunk.x}, ${chunk.z})", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("   소유 시작일: ", NamedTextColor.GRAY))
                    .append(Component.text(claimedDate, NamedTextColor.WHITE))
            )
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
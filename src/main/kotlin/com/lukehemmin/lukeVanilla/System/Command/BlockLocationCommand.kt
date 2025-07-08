package com.lukehemmin.lukeVanilla.System.Command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID

class BlockLocationCommand : CommandExecutor, Listener {

    companion object {
        private val enabledPlayers = mutableSetOf<UUID>()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return true
        }

        if (!sender.hasPermission("lukevanilla.admin.getblocklocation")) {
            sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        val playerId = sender.uniqueId
        if (enabledPlayers.contains(playerId)) {
            enabledPlayers.remove(playerId)
            sender.sendMessage(Component.text("블록 위치 확인 모드가 비활성화되었습니다.", NamedTextColor.YELLOW))
        } else {
            enabledPlayers.add(playerId)
            sender.sendMessage(Component.text("블록 위치 확인 모드가 활성화되었습니다. 블록을 우클릭하여 좌표를 확인하세요.", NamedTextColor.GREEN))
        }

        return true
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!enabledPlayers.contains(player.uniqueId)) return

        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            event.isCancelled = true
            val block = event.clickedBlock ?: return
            val location = block.location
            val locationString = "${location.world.name} ${location.blockX} ${location.blockY} ${location.blockZ}"
            
            val message = Component.text()
                .append(Component.text("블록 좌표: ", NamedTextColor.AQUA))
                .append(
                    Component.text(locationString, NamedTextColor.WHITE, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("클릭하여 복사")))
                        .clickEvent(ClickEvent.copyToClipboard(locationString))
                )
            player.sendMessage(message)
        }
    }
} 
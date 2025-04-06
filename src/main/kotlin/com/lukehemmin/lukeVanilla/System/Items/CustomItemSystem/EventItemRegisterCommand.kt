package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class EventItemRegisterCommand(
    private val plugin: Main,
    private val eventItemSystem: EventItemSystem
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 인벤토리에서 이벤트 아이템 찾아 등록
        val registeredItems = eventItemSystem.registerItemsFromInventory(player)

        if (registeredItems.isEmpty()) {
            player.sendMessage("${ChatColor.RED}인벤토리에서 등록 가능한 이벤트 아이템을 찾을 수 없습니다.")
            return true
        }

        // 등록 결과 메시지 출력
        player.sendMessage("${ChatColor.GREEN}아이템 등록이 완료되었습니다:")

        for ((eventType, items) in registeredItems) {
            if (items.isNotEmpty()) {
                player.sendMessage("${ChatColor.GOLD}[${eventType.displayName} 아이템]")
                
                for (itemColumn in items) {
                    val itemName = eventItemSystem.getItemDisplayName(eventType, itemColumn)
                    player.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}$itemName")
                }
            }
        }

        return true
    }
} 
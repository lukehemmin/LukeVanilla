package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class ItemCommand : CommandExecutor, TabCompleter {
    
    private val itemRegisterSystem = ItemRegisterSystem()
    private val itemReceiveSystem = ItemReceiveSystem()
    private val itemViewSystem = ItemViewSystem()
    
    // 이벤트 타입 목록
    private val eventTypes = listOf("할로윈", "크리스마스", "발렌타인", "봄")
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }
        
        val player = sender
        
        if (args.isEmpty()) {
            sendHelpMessage(player)
            return true
        }
        
        when (args[0].lowercase(Locale.getDefault())) {
            "등록" -> {
                return itemRegisterSystem.registerItem(player, args)
            }
            "수령" -> {
                if (args.size < 2) {
                    player.sendMessage("§c사용법: /아이템 수령 [이벤트타입]")
                    return true
                }
                return itemReceiveSystem.receiveItem(player, args[1])
            }
            "조회" -> {
                if (args.size < 2) {
                    player.sendMessage("§c사용법: /아이템 조회 [이벤트타입]")
                    return true
                }
                return itemViewSystem.viewItems(player, args[1])
            }
            else -> {
                sendHelpMessage(player)
                return true
            }
        }
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()
        
        val completions = ArrayList<String>()
        
        if (args.size == 1) {
            completions.addAll(listOf("등록", "수령", "조회"))
        } else if (args.size == 2) {
            when (args[0].lowercase(Locale.getDefault())) {
                "수령", "조회" -> {
                    completions.addAll(eventTypes)
                }
            }
        }
        
        return completions.filter { it.startsWith(args[args.size - 1].lowercase(Locale.getDefault())) }
    }
    
    private fun sendHelpMessage(player: Player) {
        player.sendMessage("§e===== 시즌 아이템 시스템 =====")
        player.sendMessage("§f/아이템 등록 §7- 손에 든 아이템을 시즌 아이템으로 등록합니다.")
        player.sendMessage("§f/아이템 수령 [이벤트타입] §7- 지정된 이벤트의 아이템을 수령합니다.")
        player.sendMessage("§f/아이템 조회 [이벤트타입] §7- 지정된 이벤트의 아이템을 조회합니다.")
    }
}

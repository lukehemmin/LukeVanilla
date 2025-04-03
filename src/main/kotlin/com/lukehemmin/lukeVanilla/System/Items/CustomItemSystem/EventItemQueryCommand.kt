package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class EventItemQueryCommand(
    private val plugin: Main,
    private val eventItemSystem: EventItemSystem
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        if (args.isEmpty()) {
            player.sendMessage("${ChatColor.RED}사용법: /아이템 조회 <이벤트타입>")
            return true
        }

        val eventTypeStr = args[0]
        val eventType = EventType.fromString(eventTypeStr)

        if (eventType == null) {
            player.sendMessage("${ChatColor.RED}유효하지 않은 이벤트 타입입니다. 다음 중 하나를 선택하세요: ${EventType.getTabCompletions().joinToString(", ")}")
            return true
        }

        // 비동기적으로 데이터베이스에서 플레이어의 아이템 정보를 가져옵니다
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val uuid = player.uniqueId
                val ownedItems = eventItemSystem.getOwnedItems(uuid, eventType)
                
                // 메인 스레드에서 결과 표시
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (ownedItems.isNotEmpty()) {
                        player.sendMessage("${eventType.guiColor}[${eventType.displayName} 아이템 목록]${ChatColor.WHITE} 소유한 아이템:")
                        
                        for (itemColumn in ownedItems) {
                            val displayName = eventItemSystem.getItemDisplayName(eventType, itemColumn)
                            player.sendMessage("${ChatColor.GRAY}- ${ChatColor.GREEN}$displayName")
                        }
                    } else {
                        player.sendMessage("${ChatColor.YELLOW}소유한 ${eventType.displayName} 아이템이 없습니다.")
                    }
                })
            } catch (e: Exception) {
                plugin.logger.warning("아이템 조회 중 오류 발생: ${e.message}")
                
                // 메인 스레드에서 오류 메시지 표시
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("${ChatColor.RED}처리 중 오류가 발생했습니다.")
                })
            }
        })

        return true
    }
} 
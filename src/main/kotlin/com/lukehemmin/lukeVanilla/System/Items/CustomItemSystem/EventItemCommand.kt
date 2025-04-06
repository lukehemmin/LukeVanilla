package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class EventItemCommand(private val plugin: Main) : CommandExecutor {

    private val eventItemSystem = EventItemSystem(plugin)
    private val registerCommand = EventItemRegisterCommand(plugin, eventItemSystem)
    private val queryCommand = EventItemQueryCommand(plugin, eventItemSystem)
    private val getCommand = EventItemGetCommand(plugin, eventItemSystem)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§c사용법: /아이템 [등록|조회|수령] [이벤트타입]")
            return true
        }

        when (args[0]) {
            "등록" -> {
                return registerCommand.onCommand(sender, command, label, args.copyOfRange(1, args.size))
            }
            "조회" -> {
                return queryCommand.onCommand(sender, command, label, args.copyOfRange(1, args.size))
            }
            "수령" -> {
                return getCommand.onCommand(sender, command, label, args.copyOfRange(1, args.size))
            }
            else -> {
                sender.sendMessage("§c사용법: /아이템 [등록|조회|수령] [이벤트타입]")
                return true
            }
        }
    }
    
    // 시스템 초기화 메소드 (Main 클래스에서 호출)
    fun initialize() {
        // 데이터베이스 초기화
        eventItemSystem.initializeDatabase()
    }
    
    // EventItemSystem 인스턴스 반환 (GUI 리스너 등록용)
    fun getEventItemSystem(): EventItemSystem {
        return eventItemSystem
    }
} 
package com.lukehemmin.lukeVanilla.System.Items.Halloween

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class HalloweenCommand(private val plugin: Main) : CommandExecutor {

    private val halloweenItemOwnerCommand = HalloweenItemOwnerCommand(plugin)
    private val halloweenItemGetCommand = HalloweenItemGetCommand(plugin)
    private val halloweenItemListCommand = HalloweenItemListCommand(plugin)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isNotEmpty()) {
            when (args[0]) {
                "아이템" -> {
                    if (args.size >= 2) {
                        when (args[1]) {
                            "소유" -> {
                                // '아이템 소유' 명령어 처리
                                // return halloweenItemOwnerCommand.onCommand(sender, command, label, args.copyOfRange(2, args.size))
                                // 비활성화된 명령어이므로 안내 메시지를 출력하고 종료합니다.
                                sender.sendMessage("해당 명령어는 현재 사용할 수 없습니다.")
                                return true
                            }
                            "받기" -> {
                                // '아이템 받기' 명령어 처리
                                return halloweenItemGetCommand.onCommand(sender, command, label, args.copyOfRange(2, args.size))
                            }
                            "목록" -> {
                                // '아이템 목록' 명령어 처리
                                return halloweenItemListCommand.onCommand(sender, command, label, args.copyOfRange(2, args.size))
                            }
                            else -> {
                                sender.sendMessage("사용법: /할로윈 아이템 [소유|받기|목록]")
                                return true
                            }
                        }
                    } else {
                        sender.sendMessage("사용법: /할로윈 아이템 [소유|받기|목록]")
                        return true
                    }
                }
                else -> {
                    sender.sendMessage("사용법: /할로윈 아이템 [소유|받기|목록]")
                    return true
                }
            }
        } else {
            sender.sendMessage("사용법: /할로윈 아이템 [소유|받기|목록]")
            return true
        }
    }
}
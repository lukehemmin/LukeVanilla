package com.lukehemmin.lukeVanilla.System.Economy

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.text.DecimalFormat

class MoneyCommand(private val economyManager: EconomyManager) : CommandExecutor {
    private val formatter = DecimalFormat("#,###")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        when {
            args.isEmpty() -> {
                val balance = economyManager.getBalance(sender)
                sender.sendMessage("§f현재 소지금: §e${formatter.format(balance)}원")
            }

            args[0] == "보내기" && args.size == 3 -> {
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("§c존재하지 않는 플레이어입니다.")
                    return true
                }

                val amount = args[2].toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage("§c올바른 금액을 입력해주세요.")
                    return true
                }

                // EconomyService의 transfer 메서드 사용 (트랜잭션 안전)
                val success = economyManager.service.transfer(
                    sender, 
                    target, 
                    amount, 
                    "${sender.name} -> ${target.name} 송금"
                )

                if (success) {
                    sender.sendMessage("§f${target.name}님께 §e${formatter.format(amount)}원§f을 보냈습니다.")
                    target.sendMessage("§f${sender.name}님으로부터 §e${formatter.format(amount)}원§f을 받았습니다.")
                } else {
                    sender.sendMessage("§c소지금이 부족하거나 송금 중 오류가 발생했습니다.")
                }
            }

            args[0] == "로그" || args[0] == "내역" -> {
                sender.sendMessage("§e===== 최근 거래 내역 (로딩 중...) =====")
                economyManager.service.getRecentLogs(sender).thenAccept { logs ->
                    if (logs.isEmpty()) {
                        sender.sendMessage("§7거래 내역이 없습니다.")
                    } else {
                        logs.forEach { log ->
                            val amountColor = if (log.amount >= 0) "§a+" else "§c"
                            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(log.date)
                            val desc = log.description ?: log.type.name
                            
                            sender.sendMessage("§7[$dateStr] $amountColor${formatter.format(log.amount)}원 §7($desc)")
                            sender.sendMessage("§8  └ 잔액: ${formatter.format(log.balanceAfter)}원")
                        }
                    }
                    sender.sendMessage("§e================================")
                }
            }

            args[0] == "도움말" -> {
                showHelp(sender)
            }

            else -> {
                showHelp(sender)
            }
        }
        return true
    }

    private fun showHelp(player: Player) {
        player.sendMessage("""
            §e===== 돈 명령어 도움말 =====
            §f/돈 §7- 현재 소지금을 확인합니다.
            §f/돈 보내기 <플레이어> <금액> §7- 다른 플레이어에게 돈을 보냅니다.
            §f/돈 내역 §7- 최근 거래 내역을 확인합니다.
            §f/돈 도움말 §7- 이 도움말을 표시합니다.
            §e========================
        """.trimIndent())
    }
}
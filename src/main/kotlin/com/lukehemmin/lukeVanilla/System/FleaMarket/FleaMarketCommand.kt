package com.lukehemmin.lukeVanilla.System.FleaMarket

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * 플리마켓 명령어 핸들러
 */
class FleaMarketCommand(
    private val service: FleaMarketService,
    private val gui: FleaMarketGUI,
    private val manager: FleaMarketManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }
        
        when (args.getOrNull(0)?.lowercase()) {
            null -> {
                // /market 또는 /플마 - GUI 열기
                gui.openMarket(sender)
            }
            
            "sell", "판매" -> {
                // /market sell <가격>
                if (args.size < 2) {
                    sender.sendMessage("§c사용법: /market sell <가격>")
                    return true
                }
                
                val price = args[1].toDoubleOrNull()
                if (price == null) {
                    sender.sendMessage("§c가격은 숫자여야 합니다.")
                    return true
                }
                
                val itemInHand = sender.inventory.itemInMainHand
                if (itemInHand.type == Material.AIR) {
                    sender.sendMessage("§c손에 아이템을 들고 사용하세요.")
                    return true
                }
                
                service.registerItem(sender, itemInHand, price)
            }
            
            "history", "내역" -> {
                // /market history [유형]
                if (args.size == 1) {
                    // 전체 내역
                    gui.openTransactionHistory(sender)
                } else {
                    // 특정 유형 내역
                    val typeStr = args[1].lowercase()
                    val type = when (typeStr) {
                        "sell", "판매" -> MarketTransactionType.SELL
                        "buy", "구매" -> MarketTransactionType.BUY
                        "withdraw", "회수" -> MarketTransactionType.WITHDRAW
                        "register", "등록" -> MarketTransactionType.REGISTER
                        else -> {
                            sender.sendMessage("§c알 수 없는 거래 유형입니다. (sell/buy/withdraw/register)")
                            return true
                        }
                    }
                    
                    val logs = service.getPlayerLogsByType(sender.uniqueId, type, 10)
                    
                    if (logs.isEmpty()) {
                        sender.sendMessage("§e해당 유형의 거래 내역이 없습니다.")
                        return true
                    }
                    
                    sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    sender.sendMessage("§a§l               [거래 내역 - ${getTypeKorean(type)}]")
                    sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    
                    logs.take(10).forEach { log ->
                        val counterpart = if (log.counterpartName != null) " (상대: ${log.counterpartName})" else ""
                        sender.sendMessage("  §f${log.itemName} §7- §f${log.price.toLong()}원$counterpart")
                    }
                    
                    sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            }
            
            "npc" -> {
                if (!sender.isOp) {
                    sender.sendMessage("§c권한이 없습니다.")
                    return true
                }
                
                if (args.size < 3) {
                    sender.sendMessage("§c사용법: /market npc <add|remove> <npcId>")
                    return true
                }
                
                val action = args[1].lowercase()
                val npcId = args[2].toIntOrNull()
                
                if (npcId == null) {
                    sender.sendMessage("§cNPC ID는 숫자여야 합니다.")
                    return true
                }
                
                when (action) {
                    "add", "추가" -> {
                        if (manager.isMarketNPC(npcId)) {
                            sender.sendMessage("§c이미 등록된 NPC입니다.")
                        } else {
                            manager.addNPC(npcId)
                            sender.sendMessage("§aNPC(ID: $npcId)가 플리마켓 NPC로 등록되었습니다.")
                        }
                    }
                    "remove", "제거" -> {
                        if (!manager.isMarketNPC(npcId)) {
                            sender.sendMessage("§c등록되지 않은 NPC입니다.")
                        } else {
                            manager.removeNPC(npcId)
                            sender.sendMessage("§aNPC(ID: $npcId)가 플리마켓 NPC에서 제거되었습니다.")
                        }
                    }
                    else -> {
                        sender.sendMessage("§c사용법: /market npc <add|remove> <npcId>")
                    }
                }
            }
            
            "help", "도움말" -> {
                sendHelpMessage(sender)
            }
            
            else -> {
                sendHelpMessage(sender)
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (args.size) {
            1 -> {
                val commands = mutableListOf("sell", "history", "help", "판매", "내역", "도움말")
                if (sender.isOp) {
                    commands.add("npc")
                }
                commands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                if (args[0].lowercase() in listOf("history", "내역")) {
                    listOf("sell", "buy", "withdraw", "register", "판매", "구매", "회수", "등록")
                        .filter { it.startsWith(args[1].lowercase()) }
                } else if (args[0].lowercase() == "npc" && sender.isOp) {
                    listOf("add", "remove", "추가", "제거")
                        .filter { it.startsWith(args[1].lowercase()) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
    
    /**
     * 도움말 메시지
     */
    private fun sendHelpMessage(player: Player) {
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        player.sendMessage("§a§l               [플리마켓 명령어]")
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        player.sendMessage("  §e/market §f또는 §e/플마 §7- 마켓 GUI 열기")
        player.sendMessage("  §e/market sell <가격> §7- 손에 든 아이템 등록")
        player.sendMessage("  §e/market history §7- 거래 내역 조회")
        player.sendMessage("  §e/market history <유형> §7- 특정 유형 거래 내역")
        player.sendMessage("  §7  유형: sell(판매), buy(구매), withdraw(회수)")
        if (player.isOp) {
            player.sendMessage("  §c/market npc <add|remove> <npcId> §7- NPC 설정")
        }
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    /**
     * 거래 유형 한글명
     */
    private fun getTypeKorean(type: MarketTransactionType): String {
        return when (type) {
            MarketTransactionType.REGISTER -> "등록"
            MarketTransactionType.SELL -> "판매"
            MarketTransactionType.BUY -> "구매"
            MarketTransactionType.WITHDRAW -> "회수"
        }
    }
}

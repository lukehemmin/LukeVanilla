package com.lukehemmin.lukeVanilla.System.Shop

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ShopCommand(
    private val database: Database,
    private val shopManager: ShopManager,
    private val priceEditManager: PriceEditManager
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        if (!player.hasPermission("lukevanilla.shop.admin")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            // 명령어 도움말 표시
            player.sendMessage("§a=== 상점 명령어 도움말 ===")
            player.sendMessage("§7/상점 생성 <상점 이름> - 새로운 상점을 생성합니다.")
            player.sendMessage("§7/상점 수정 줄 <상점 이름> <1~6> - 상점의 GUI 줄 수를 수정합니다.")
            player.sendMessage("§7/상점 수정 아이템 <상점 이름> - 상점의 아이템을 수정합니다.")
            player.sendMessage("§7/상점 수정 가격 <상점 이름> - 상점의 아이템 가격을 수정합니다.")
            return true
        }

        when (args[0].toLowerCase()) {
            "생성" -> {
                if (args.size < 2) {
                    player.sendMessage("§c사용법: /상점 생성 <상점 이름>")
                    return true
                }
                val shopName = args[1]
                shopManager.createShop(player, shopName)
                return true
            }
            "수정" -> {
                if (args.size < 3) {
                    player.sendMessage("§c사용법: /상점 수정 <수정 항목> <인자>")
                    return true
                }
                when (args[1].toLowerCase()) {
                    "줄" -> {
                        if (args.size < 4) {
                            player.sendMessage("§c사용법: /상점 수정 줄 <상점 이름> <1~6>")
                            return true
                        }
                        val shopName = args[2]
                        val lines = args[3].toIntOrNull()
                        if (lines == null || lines !in 1..6) {
                            player.sendMessage("§c줄 수는 1부터 6 사이의 숫자여야 합니다.")
                            return true
                        }
                        shopManager.setShopLines(shopName, lines)
                        player.sendMessage("§a상점 §f$shopName §a의 줄 수를 §f$lines §a로 수정했습니다.")
                        return true
                    }
                    "아이템" -> {
                        if (args.size < 3) {
                            player.sendMessage("§c사용법: /상점 수정 아이템 <상점 이름>")
                            return true
                        }
                        val shopName = args[2]
                        shopManager.openItemEditGUI(player, shopName)
                        return true
                    }
                    "가격" -> {
                        if (args.size < 3) {
                            player.sendMessage("§c사용법: /상점 수정 가격 <상점 이름>")
                            return true
                        }
                        val shopName = args[2]
                        // 가격 수정 GUI를 여는 로직 추가 (예: 전체 아이템의 가격 설정)
                        // 현재 예시는 특정 슬롯을 지정하지 않으므로, 모든 아이템의 가격을 순차적으로 설정하도록 구현 필요
                        player.sendMessage("§a가격 설정을 위해 상점의 아이템을 GUI에서 클릭해주세요.")
                        // 추가적인 구현 필요
                        return true
                    }
                    else -> {
                        player.sendMessage("§c알 수 없는 수정 항목입니다: ${args[1]}")
                        return true
                    }
                }
            }
            else -> {
                player.sendMessage("§c알 수 없는 명령어입니다.")
                return true
            }
        }
    }
}
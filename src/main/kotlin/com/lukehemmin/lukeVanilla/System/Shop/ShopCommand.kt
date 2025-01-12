// ShopCommand.kt
package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ShopCommand(private val shopManager: ShopManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lukevanilla.shop")) {
            sender.sendMessage("${ChatColor.RED}이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].toLowerCase()) {
            "생성" -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}/상점 생성 <상점 이름>")
                    return true
                }
                // 상점 이름을 모든 인자를 합쳐서 처리 (띄어쓰기 포함)
                val shopName = args.slice(1 until args.size).joinToString(" ")
                val location = sender.location
                if (shopManager.createShop(shopName, location, sender)) {
                    sender.sendMessage("${ChatColor.GREEN}상점 '$shopName'이(가) 생성되었습니다.")
                } else {
                    sender.sendMessage("${ChatColor.RED}상점 '$shopName'을(를) 생성할 수 없습니다. 이름이 이미 존재하거나 NPC를 생성할 수 없습니다.")
                }
            }

            "수정" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}/상점 수정 <줄|아이템|가격수정> <상점 이름> [추가 인자]")
                    return true
                }
                val subCommand = args[1].toLowerCase()
                // 상점 이름을 모든 인자를 합쳐서 처리 (띄어쓰기 포함)
                val shopName = args.slice(2 until args.size).joinToString(" ")
                when (subCommand) {
                    "줄" -> {
                        if (args.size < 4) {
                            sender.sendMessage("${ChatColor.RED}/상점 수정 줄 <상점 이름> <숫자 1~6>")
                            return true
                        }
                        val rows = args[3].toIntOrNull()
                        if (rows == null || rows !in 1..6) {
                            sender.sendMessage("${ChatColor.RED}숫자 1부터 6 사이의 값을 입력해주세요.")
                            return true
                        }
                        if (shopManager.setShopRows(shopName, rows)) {
                            sender.sendMessage("${ChatColor.GREEN}상점 '$shopName'의 줄 수가 $rows 로 설정되었습니다.")
                        } else {
                            sender.sendMessage("${ChatColor.RED}상점 '$shopName'을(를) 찾을 수 없습니다.")
                        }
                    }

                    "아이템" -> {
                        val shop = shopManager.getShop(shopName)
                        if (shop == null) {
                            sender.sendMessage("${ChatColor.RED}상점 '$shopName'을(를) 찾을 수 없습니다.")
                            return true
                        }
                        // 상점 아이템 설정 GUI 열기
                        ShopGUI(plugin = shopManager.plugin, shop = shop, player = sender).openItemSettingGUI()
                        sender.sendMessage("${ChatColor.GREEN}상점 '$shopName'의 아이템 설정 GUI를 엽니다.")
                    }

                    "가격수정" -> {
                        val shop = shopManager.getShop(shopName)
                        if (shop == null) {
                            sender.sendMessage("${ChatColor.RED}상점 '$shopName'을(를) 찾을 수 없습니다.")
                            return true
                        }
                        // 가격 설정 GUI 열기
                        ShopGUI(plugin = shopManager.plugin, shop = shop, player = sender).openPriceSettingGUI()
                        sender.sendMessage("${ChatColor.GREEN}상점 '$shopName'의 가격 설정 GUI를 엽니다.")
                    }

                    else -> {
                        sender.sendMessage("${ChatColor.RED}알 수 없는 하위 명령어입니다.")
                        showHelp(sender)
                    }
                }
            }

            else -> {
                showHelp(sender)
            }
        }

        return true
    }

    private fun showHelp(player: Player) {
        player.sendMessage("""
            ${ChatColor.YELLOW}===== 상점 명령어 도움말 =====
            ${ChatColor.GREEN}/상점 생성 <상점 이름> ${ChatColor.GRAY}- 새로운 상점을 생성하고 NPC를 스폰합니다.
            ${ChatColor.GREEN}/상점 수정 줄 <상점 이름> <숫자 1~6> ${ChatColor.GRAY}- 상점의 GUI 줄 수를 수정합니다.
            ${ChatColor.GREEN}/상점 수정 아이템 <상점 이름> ${ChatColor.GRAY}- 상점의 아이템을 설정합니다.
            ${ChatColor.GREEN}/상점 수정 가격수정 <상점 이름> ${ChatColor.GRAY}- 상점 아이템의 가격을 설정합니다.
            ${ChatColor.YELLOW}==============================
        """.trimIndent())
    }
}

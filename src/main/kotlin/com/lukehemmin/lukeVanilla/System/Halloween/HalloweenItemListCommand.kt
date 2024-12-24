package com.lukehemmin.lukeVanilla.System.Halloween

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.Player

class HalloweenItemListCommand(private val plugin: Main) : CommandExecutor {

    private val validHalloweenItems = mapOf(
        "sword" to "호박의 빛검",
        "pickaxe" to "호박 곡괭이",
        "axe" to "호박 도끼",
        "shovel" to "호박 삽",
        "hoe" to "호박 괭이",
        "bow" to "호박 활",
        "fishing_rod" to "호박 낚싯대",
        "hammer" to "호박 망치",
        "hat" to "호박 모자",
        "scythe" to "호박 낫",
        "spear" to "호박 창"
    )

    private val database: Database = plugin.database

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        // 인자가 없어야 올바른 사용법입니다.
        if (args.isNotEmpty()) {
            player.sendMessage("사용법: /할로윈 아이템 목록")
            return true
        }

        // 비동기적으로 데이터베이스에서 플레이어의 아이템 정보를 가져옵니다.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val uuid = player.uniqueId.toString()

                    val selectStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Owner WHERE UUID = ?")
                    selectStmt.setString(1, uuid)
                    val resultSet = selectStmt.executeQuery()

                    if (resultSet.next()) {
                        val ownedItems = mutableListOf<String>()
                        for ((column, displayName) in validHalloweenItems) {
                            val hasItem = resultSet.getBoolean(column)
                            if (hasItem) {
                                ownedItems.add(displayName)
                            }
                        }

                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            if (ownedItems.isNotEmpty()) {
                                player.sendMessage("§6[할로윈 아이템 목록]§f 소유한 할로윈 아이템:")
                                for (itemName in ownedItems) {
                                    player.sendMessage("§7- §a$itemName")
                                }
                            } else {
                                player.sendMessage("소유한 할로윈 아이템이 없습니다.")
                            }
                        })
                    } else {
                        // 데이터베이스에 플레이어 정보가 없는 경우
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            player.sendMessage("소유한 할로윈 아이템이 없습니다.")
                        })
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("처리 중 오류 발생: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("처리 중 오류가 발생했습니다.")
                })
            }
        })

        return true
    }
}
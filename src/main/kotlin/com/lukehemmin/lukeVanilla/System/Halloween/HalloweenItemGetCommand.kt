// HalloweenItemGetCommand.kt
package com.lukehemmin.lukeVanilla.System.Halloween

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import io.th0rgal.oraxen.api.OraxenItems
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class HalloweenItemGetCommand(private val plugin: Main) : CommandExecutor, Listener {

    private val guiTitle = "${ChatColor.DARK_PURPLE}할로윈 아이템 받기"
    private val scrollItems = mapOf(
        10 to "h_sword_scroll",
        12 to "h_pickaxe_scroll",
        14 to "h_axe_scroll",
        16 to "h_shovel_scroll",
        20 to "h_hoe_scroll",
        22 to "h_bow_scroll",
        24 to "h_rod_scroll",
        28 to "h_hammer_scroll",
        30 to "h_hat_scroll",
        32 to "h_scythe_scroll",
        34 to "h_spear_scroll"
    )

    private val itemMappings = mapOf(
        "sword" to "halloween_sword",
        "pickaxe" to "halloween_pickaxe",
        "axe" to "halloween_axe",
        "shovel" to "halloween_shovel",
        "hoe" to "halloween_hoe",
        "bow" to "halloween_bow",
        "fishing_rod" to "halloween_fishing_rod",
        "hammer" to "halloween_hammer",
        "hat" to "halloween_hat",
        "scythe" to "halloween_scythe",
        "spear" to "halloween_spear"
    )

    private val database: Database = plugin.database
    private val guiKey = NamespacedKey(plugin, "halloween_gui")
    private val ownerKey = NamespacedKey(plugin, "owner")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }
        val player = sender

        // 인자가 없어야 올바른 사용법입니다.
        if (args.isNotEmpty()) {
            player.sendMessage("사용법: /할로윈 아이템 받기")
            return true
        }

        // 비동기적으로 데이터베이스에서 플레이어의 아이템 정보를 가져옵니다.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val uuid = player.uniqueId.toString()

                    // 플레이어가 등록한 아이템 정보 가져오기
                    val ownerStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Owner WHERE UUID = ?")
                    ownerStmt.setString(1, uuid)
                    val ownerResult = ownerStmt.executeQuery()

                    // 플레이어가 수령한 아이템 정보 가져오기
                    val receiveStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Receive WHERE UUID = ?")
                    receiveStmt.setString(1, uuid)
                    val receiveResult = receiveStmt.executeQuery()

                    val registeredItems = mutableSetOf<String>()
                    val receivedItems = mutableSetOf<String>()

                    if (ownerResult.next()) {
                        for (column in itemMappings.keys) {
                            val hasItem = ownerResult.getBoolean(column)
                            if (hasItem) {
                                registeredItems.add(column)
                            }
                        }
                    }

                    if (receiveResult.next()) {
                        for (column in itemMappings.keys) {
                            val hasReceived = receiveResult.getBoolean(column)
                            if (hasReceived) {
                                receivedItems.add(column)
                            }
                        }
                    }

                    // 메인 스레드에서 GUI 생성 및 표시
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        val gui = Bukkit.createInventory(player, 4 * 9, guiTitle)

                        // 주황색 색유리판으로 배경 채우기
                        val orangePane = ItemStack(Material.ORANGE_STAINED_GLASS_PANE)
                        val orangeMeta = orangePane.itemMeta
                        orangeMeta!!.setDisplayName(" ")
                        orangePane.itemMeta = orangeMeta
                        for (i in 0 until gui.size) {
                            gui.setItem(i, orangePane)
                        }

                        // 각 슬롯에 아이템 배치
                        for ((slot, scrollId) in scrollItems) {
                            val columnName = getColumnNameByScrollId(scrollId)
                            when {
                                // 아이템을 등록하지 않은 경우 - 검은색 유리판
                                !registeredItems.contains(columnName) -> {
                                    val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                                    val blackMeta = blackPane.itemMeta
                                    blackMeta!!.setDisplayName("${ChatColor.GRAY}아이템이 등록되지 않았습니다.")
                                    blackPane.itemMeta = blackMeta
                                    gui.setItem(slot, blackPane)
                                }
                                // 이미 수령한 경우 - 빨간색 유리판
                                receivedItems.contains(columnName) -> {
                                    val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                                    val redMeta = redPane.itemMeta
                                    redMeta!!.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                                    redPane.itemMeta = redMeta
                                    gui.setItem(slot, redPane)
                                }
                                // 수령 가능 - 스크롤 아이템 배치
                                else -> {
                                    val scrollItem = OraxenItems.getItemById(scrollId).build()
                                    val scrollMeta = scrollItem.itemMeta
                                    scrollMeta!!.persistentDataContainer.set(guiKey, PersistentDataType.STRING, scrollId)
                                    scrollItem.itemMeta = scrollMeta
                                    gui.setItem(slot, scrollItem)
                                }
                            }
                        }

                        // GUI 열기
                        player.openInventory(gui)
                    })
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

    private fun getColumnNameByScrollId(scrollId: String): String {
        return when (scrollId) {
            "h_sword_scroll" -> "sword"
            "h_pickaxe_scroll" -> "pickaxe"
            "h_axe_scroll" -> "axe"
            "h_shovel_scroll" -> "shovel"
            "h_hoe_scroll" -> "hoe"
            "h_bow_scroll" -> "bow"
            "h_rod_scroll" -> "fishing_rod"
            "h_hammer_scroll" -> "hammer"
            "h_hat_scroll" -> "hat"
            "h_scythe_scroll" -> "scythe"
            "h_spear_scroll" -> "spear"
            else -> ""
        }
    }
}
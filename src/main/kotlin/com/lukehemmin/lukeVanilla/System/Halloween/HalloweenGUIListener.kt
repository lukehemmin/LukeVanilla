// HalloweenGUIListener.kt
package com.lukehemmin.lukeVanilla.System.Halloween

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class HalloweenGUIListener(private val plugin: Main) : Listener {

    private val guiTitle = "${ChatColor.DARK_PURPLE}할로윈 아이템 받기"
    private val guiKey = NamespacedKey(plugin, "halloween_gui")
    private val ownerKey = NamespacedKey(plugin, "owner")
    private val database: Database = plugin.database

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

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // GUI 확인
        if (event.view.title != guiTitle) return

        event.isCancelled = true // 아이템 이동 방지

        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return

        // 클릭한 아이템이 스크롤 아이템인지 확인
        val scrollId = meta.persistentDataContainer.get(guiKey, PersistentDataType.STRING) ?: return

        val columnName = getColumnNameByScrollId(scrollId)
        val oraxenId = itemMappings[columnName] ?: return

        // 이미 수령한 아이템인지 확인
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val uuid = player.uniqueId.toString()

                    val receiveStmt = connection.prepareStatement("SELECT $columnName FROM Halloween_Item_Receive WHERE UUID = ?")
                    receiveStmt.setString(1, uuid)
                    val receiveResult = receiveStmt.executeQuery()

                    var hasReceived = false

                    if (receiveResult.next()) {
                        hasReceived = receiveResult.getBoolean(columnName)
                    }

                    if (hasReceived) {
                        // 이미 수령한 경우 메시지 출력
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            player.sendMessage("${ChatColor.RED}이미 수령한 아이템입니다.")
                        })
                    } else {
                        // 아이템 지급 및 DB 업데이트
                        // 아이템 지급은 메인 스레드에서 수행
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            // 플레이어에게 스크롤 아이템 지급
                            val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: run {
                                player.sendMessage("${ChatColor.RED}아이템을 생성하는 중 오류가 발생했습니다.")
                                return@Runnable
                            }
                            player.inventory.addItem(scrollItem)
                            player.sendMessage("${ChatColor.GREEN}스크롤 아이템을 받았습니다.")

                            // GUI 업데이트: 해당 슬롯을 빨간색 유리판으로 변경
                            val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                            val redMeta = redPane.itemMeta!!
                            redMeta.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                            redPane.itemMeta = redMeta

                            event.inventory.setItem(event.slot, redPane)
                        })

                        // 데이터베이스 업데이트는 비동기로 수행
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                            try {
                                database.getConnection().use { updateConnection ->
                                    // Halloween_Item_Receive 테이블에 업데이트
                                    val updateStmt = updateConnection.prepareStatement(
                                        "INSERT INTO Halloween_Item_Receive (UUID, $columnName) VALUES (?, 1) ON DUPLICATE KEY UPDATE $columnName = 1"
                                    )
                                    updateStmt.setString(1, uuid)
                                    updateStmt.executeUpdate()
                                }
                            } catch (e: Exception) {
                                plugin.logger.warning("데이터베이스 업데이트 중 오류 발생: ${e.message}")
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("데이터베이스 조회 중 오류 발생: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("${ChatColor.RED}처리 중 오류가 발생했습니다.")
                })
            }
        })
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
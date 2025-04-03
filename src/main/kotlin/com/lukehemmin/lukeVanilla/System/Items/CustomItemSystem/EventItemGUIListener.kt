package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
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

class EventItemGUIListener(
    private val plugin: Main,
    private val eventItemSystem: EventItemSystem
) : Listener {

    private val guiKey = NamespacedKey(plugin, "event_item_gui")
    private val eventTypeKey = NamespacedKey(plugin, "event_type")

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory
        
        // GUI 확인 - 제목에 "받기"가 포함되어 있는지 확인
        if (!event.view.title.contains("받기")) return

        // 이벤트 타입 확인
        var eventType: EventType? = null
        for (type in EventType.values()) {
            if (event.view.title.contains(type.guiTitle)) {
                eventType = type
                break
            }
        }

        if (eventType == null) return
        
        event.isCancelled = true // 아이템 이동 방지

        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return

        // 클릭한 아이템이 스크롤 아이템인지 확인
        val scrollId = meta.persistentDataContainer.get(guiKey, PersistentDataType.STRING) ?: return
        val storedEventType = meta.persistentDataContainer.get(eventTypeKey, PersistentDataType.STRING)?.let {
            try {
                EventType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        } ?: eventType // 이전 버전 호환성을 위해 GUI 제목에서 추출한 이벤트 타입 사용

        val columnName = eventItemSystem.getColumnNameFromScrollId(storedEventType, scrollId) ?: return

        // 이미 수령한 아이템인지 비동기적으로 확인
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val uuid = player.uniqueId
                val receivedItems = eventItemSystem.getReceivedItems(uuid, storedEventType)
                
                if (receivedItems.contains(columnName)) {
                    // 이미 수령한 경우 메시지 출력
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        player.sendMessage("${ChatColor.RED}이미 수령한 아이템입니다.")
                    })
                    return@Runnable
                }
                
                // 메인 스레드에서 아이템 지급
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    // 스크롤 아이템 생성 시도
                    try {
                        val scrollItem = NexoItems.itemFromId(scrollId)?.build()
                        
                        if (scrollItem != null) {
                            // 아이템 지급
                            player.inventory.addItem(scrollItem)
                            player.sendMessage("${ChatColor.GREEN}스크롤 아이템을 받았습니다: ${eventItemSystem.getItemDisplayName(storedEventType, columnName)}")
                            
                            // GUI 업데이트: 해당 슬롯을 빨간색 유리판으로 변경
                            val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                            val redMeta = redPane.itemMeta!!
                            redMeta.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                            redPane.itemMeta = redMeta
                            
                            event.inventory.setItem(event.slot, redPane)
                            
                            // 비동기적으로 데이터베이스 업데이트
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                try {
                                    eventItemSystem.markItemAsReceived(uuid, storedEventType, columnName)
                                } catch (e: Exception) {
                                    plugin.logger.warning("데이터베이스 업데이트 중 오류 발생: ${e.message}")
                                }
                            })
                        } else {
                            player.sendMessage("${ChatColor.RED}아이템을 생성하는 중 오류가 발생했습니다.")
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("아이템 수령 중 오류 발생: ${e.message}")
                        player.sendMessage("${ChatColor.RED}아이템을 생성하는 중 오류가 발생했습니다.")
                    }
                })
            } catch (e: Exception) {
                plugin.logger.warning("데이터베이스 확인 중 오류 발생: ${e.message}")
                
                // 메인 스레드에서 오류 메시지 표시
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("${ChatColor.RED}처리 중 오류가 발생했습니다.")
                })
            }
        })
    }
} 
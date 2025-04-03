package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class EventItemGetCommand(
    private val plugin: Main,
    private val eventItemSystem: EventItemSystem
) : CommandExecutor {

    // 각 이벤트 유형별 GUI 슬롯 매핑
    private val guiSlots = mapOf(
        EventType.HALLOWEEN to listOf(10, 12, 14, 16, 20, 22, 24, 28, 30, 32, 34),
        EventType.CHRISTMAS to listOf(10, 14, 18, 22, 30),
        EventType.VALENTINE to listOf(12, 22, 32)
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender

        if (args.isEmpty()) {
            player.sendMessage("${ChatColor.RED}사용법: /아이템 수령 <이벤트타입>")
            return true
        }

        val eventTypeStr = args[0]
        val eventType = EventType.fromString(eventTypeStr)

        if (eventType == null) {
            player.sendMessage("${ChatColor.RED}유효하지 않은 이벤트 타입입니다. 다음 중 하나를 선택하세요: ${EventType.getTabCompletions().joinToString(", ")}")
            return true
        }

        // 비동기적으로 데이터베이스에서 플레이어의 아이템 정보를 가져옵니다
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val uuid = player.uniqueId
                val ownedItems = eventItemSystem.getOwnedItems(uuid, eventType)
                val receivedItems = eventItemSystem.getReceivedItems(uuid, eventType)
                
                // 메인 스레드에서 GUI 생성
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    openEventItemGUI(player, eventType, ownedItems, receivedItems)
                })
            } catch (e: Exception) {
                plugin.logger.warning("아이템 수령 GUI 생성 중 오류 발생: ${e.message}")
                
                // 메인 스레드에서 오류 메시지 표시
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("${ChatColor.RED}처리 중 오류가 발생했습니다.")
                })
            }
        })

        return true
    }

    private fun openEventItemGUI(player: Player, eventType: EventType, ownedItems: List<String>, receivedItems: List<String>) {
        val guiTitle = "${eventType.guiColor}${eventType.guiTitle} 받기"
        val gui = Bukkit.createInventory(player, 4 * 9, guiTitle)
        
        // GUI 키
        val guiKey = NamespacedKey(plugin, "event_item_gui")
        val eventTypeKey = NamespacedKey(plugin, "event_type")

        // 배경 유리판 색상 설정
        val backgroundColor = getBackgroundColor(eventType)
        val backgroundPane = ItemStack(backgroundColor)
        val backgroundMeta = backgroundPane.itemMeta
        backgroundMeta!!.setDisplayName(" ")
        backgroundPane.itemMeta = backgroundMeta
        
        // 배경 채우기
        for (i in 0 until gui.size) {
            gui.setItem(i, backgroundPane)
        }

        // 이벤트 타입별 아이템 슬롯
        val slots = guiSlots[eventType] ?: emptyList()
        val itemColumns = eventItemSystem.getItemColumns(eventType)
        
        // 슬롯과 아이템 매핑
        for ((index, itemColumn) in itemColumns.withIndex()) {
            if (index >= slots.size) break
            
            val slot = slots[index]
            
            when {
                // 아이템을 등록하지 않은 경우 - 검은색 유리판
                !ownedItems.contains(itemColumn) -> {
                    val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                    val blackMeta = blackPane.itemMeta
                    blackMeta!!.setDisplayName("${ChatColor.GRAY}아이템이 등록되지 않았습니다.")
                    blackPane.itemMeta = blackMeta
                    gui.setItem(slot, blackPane)
                }
                // 이미 수령한 경우 - 빨간색 유리판
                receivedItems.contains(itemColumn) -> {
                    val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                    val redMeta = redPane.itemMeta
                    redMeta!!.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                    redPane.itemMeta = redMeta
                    gui.setItem(slot, redPane)
                }
                // 수령 가능 - 스크롤 아이템 배치
                else -> {
                    val scrollId = eventItemSystem.getScrollId(eventType, itemColumn)
                    if (scrollId != null) {
                        try {
                            // Nexo에서 아이템 생성
                            val scrollItem = com.nexomc.nexo.api.NexoItems.itemFromId(scrollId)?.build()
                            
                            if (scrollItem != null) {
                                val scrollMeta = scrollItem.itemMeta
                                scrollMeta!!.persistentDataContainer.set(guiKey, PersistentDataType.STRING, scrollId)
                                scrollMeta.persistentDataContainer.set(eventTypeKey, PersistentDataType.STRING, eventType.name)
                                scrollItem.itemMeta = scrollMeta
                                gui.setItem(slot, scrollItem)
                            } else {
                                // 아이템 생성 실패 - 회색 유리판
                                val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                                val grayMeta = grayPane.itemMeta
                                grayMeta!!.setDisplayName("${ChatColor.GRAY}아이템을 생성할 수 없습니다.")
                                grayPane.itemMeta = grayMeta
                                gui.setItem(slot, grayPane)
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("스크롤 아이템 생성 실패: $scrollId")
                            
                            // 아이템 생성 실패 - 회색 유리판
                            val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                            val grayMeta = grayPane.itemMeta
                            grayMeta!!.setDisplayName("${ChatColor.GRAY}아이템을 생성할 수 없습니다.")
                            grayPane.itemMeta = grayMeta
                            gui.setItem(slot, grayPane)
                        }
                    }
                }
            }
        }

        // GUI 열기
        player.openInventory(gui)
    }

    private fun getBackgroundColor(eventType: EventType): Material {
        return when (eventType) {
            EventType.HALLOWEEN -> Material.ORANGE_STAINED_GLASS_PANE
            EventType.CHRISTMAS -> Material.GREEN_STAINED_GLASS_PANE
            EventType.VALENTINE -> Material.PINK_STAINED_GLASS_PANE
        }
    }
} 
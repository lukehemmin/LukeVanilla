package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.logging.Logger

/**
 * 빼빼로 선택 GUI
 */
class PeperoEventGUI(
    private val plugin: Main,
    private val repository: PeperoEventRepository,
    private val logger: Logger
) : Listener {

    private val guiViewers = mutableSetOf<String>() // UUID 저장

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * 빼빼로 선택 GUI 열기
     */
    fun openPeperoSelectionGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 27, "§6§l빼빼로 선택하기")

        // Nexo 아이템 가져오기
        val originalPepero = getNexoItem("original_pepero")
        val almondPepero = getNexoItem("almond_pepero")
        val strawberryPepero = getNexoItem("strawberry_pepero")

        // 아이템이 null이면 기본 아이템으로 대체
        inv.setItem(11, originalPepero ?: createFallbackItem(Material.STICK, "§6오리지널 빼빼로"))
        inv.setItem(13, almondPepero ?: createFallbackItem(Material.STICK, "§e아몬드 빼빼로"))
        inv.setItem(15, strawberryPepero ?: createFallbackItem(Material.STICK, "§d스트로베리 빼빼로"))

        // 장식용 유리판
        val glassPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(" ")
            }
        }
        for (i in 0 until 27) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glassPane)
            }
        }

        guiViewers.add(player.uniqueId.toString())
        player.openInventory(inv)
    }

    /**
     * Nexo 아이템 가져오기
     */
    private fun getNexoItem(itemId: String): ItemStack? {
        return try {
            val builder = NexoItems.itemFromId(itemId)
            builder?.build()
        } catch (e: Exception) {
            logger.warning("[PeperoEventGUI] Nexo 아이템 '$itemId' 가져오기 실패: ${e.message}")
            null
        }
    }

    /**
     * Nexo 아이템 실패 시 대체 아이템 생성
     */
    private fun createFallbackItem(material: Material, displayName: String): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(displayName)
            }
        }
    }

    /**
     * 인벤토리 클릭 이벤트 처리
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val uuid = player.uniqueId.toString()

        if (!guiViewers.contains(uuid)) return
        if (event.view.title != "§6§l빼빼로 선택하기") return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return

        // 빼빼로 타입 확인
        val peperoType = when (event.slot) {
            11 -> "original"
            13 -> "almond"
            15 -> "strawberry"
            else -> return
        }

        // 비동기로 DB 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val success = repository.recordItemReceive(uuid, player.name, peperoType)

            // 메인 스레드에서 아이템 지급 및 메시지
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (success) {
                    giveNexoItem(player, peperoType)
                    player.sendMessage("§a빼빼로를 받았습니다! 즐거운 빼빼로 데이 되세요!")
                    player.closeInventory()
                    guiViewers.remove(uuid)
                } else {
                    player.sendMessage("§c아이템 수령 중 오류가 발생했습니다.")
                }
            })
        })
    }

    /**
     * Nexo 빼빼로 아이템 지급
     */
    private fun giveNexoItem(player: Player, peperoType: String) {
        val itemId = when (peperoType) {
            "original" -> "original_pepero"
            "almond" -> "almond_pepero"
            "strawberry" -> "strawberry_pepero"
            else -> return
        }

        val item = getNexoItem(itemId)
        if (item != null) {
            player.inventory.addItem(item)
        } else {
            logger.warning("[PeperoEventGUI] Nexo 아이템 '$itemId' 지급 실패")
            // 실패 시 대체 아이템
            player.inventory.addItem(createFallbackItem(Material.STICK, "§6빼빼로"))
        }
    }

    /**
     * GUI 뷰어 목록에서 제거 (플레이어가 GUI를 닫을 때)
     */
    fun removeViewer(player: Player) {
        guiViewers.remove(player.uniqueId.toString())
    }
}

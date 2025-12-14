package com.lukehemmin.lukeVanilla.System.ScrollRoulette

import com.lukehemmin.lukeVanilla.System.Roulette.ItemProvider
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/**
 * 스크롤 룰렛 아이템 우클릭 리스너
 * - 등록된 스크롤 아이템 우클릭 시 룰렛 실행
 * - GUI 클릭 이벤트 처리
 * - GUI 닫기 이벤트 처리
 */
class ScrollRouletteListener(
    private val plugin: JavaPlugin,
    private val manager: ScrollRouletteManager
) : Listener {

    companion object {
        private const val GUI_TITLE = "§6§l[ 스크롤 룰렛 ]"
        private const val SLOT_SKIP_BUTTON = 22
    }

    /**
     * 아이템 우클릭 이벤트 처리
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // 우클릭만 처리
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        // 메인 핸드만 처리 (중복 이벤트 방지)
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        val player = event.player
        val item = event.item ?: return

        // 아이템이 등록된 스크롤인지 확인
        val rouletteId = getRouletteIdFromItem(item) ?: return

        // 이벤트 취소 (블록 상호작용 방지)
        event.isCancelled = true

        // 룰렛 설정 확인
        val config = manager.getRouletteById(rouletteId)
        if (config == null) {
            player.sendMessage("§c[스크롤 룰렛] 해당 스크롤 룰렛 설정을 찾을 수 없습니다.")
            return
        }

        // 활성화 여부 확인
        if (!config.enabled) {
            player.sendMessage("§c[스크롤 룰렛] 현재 이 스크롤 룰렛은 비활성화되어 있습니다.")
            return
        }

        // 당첨 아이템이 있는지 확인
        if (manager.getItems(rouletteId).isEmpty()) {
            player.sendMessage("§c[스크롤 룰렛] 등록된 당첨 아이템이 없습니다.")
            return
        }

        // 이미 룰렛을 플레이 중인지 확인
        if (manager.hasActiveSession(player)) {
            val existingGUI = manager.getSession(player)!!
            player.openInventory(existingGUI.getInventory())
            player.sendMessage("§e[스크롤 룰렛] 진행 중인 룰렛 화면을 다시 열었습니다!")
            return
        }

        // 스크롤 아이템 1개 소모
        if (item.amount > 1) {
            item.amount--
        } else {
            player.inventory.setItemInMainHand(null)
        }

        player.sendMessage("§a[스크롤 룰렛] §f${config.rouletteName}§a을(를) 사용합니다!")

        // 룰렛 실행
        openScrollRoulette(player, rouletteId)
    }

    /**
     * 아이템에서 룰렛 ID를 가져오기
     */
    private fun getRouletteIdFromItem(item: ItemStack): Int? {
        // Vanilla 아이템 확인
        val vanillaRouletteId = manager.getRouletteIdByScrollItem(ItemProvider.VANILLA, item.type.name)
        if (vanillaRouletteId != null) {
            return vanillaRouletteId
        }

        // Nexo 아이템 확인
        val nexoItemId = getNexoItemId(item)
        if (nexoItemId != null) {
            val nexoRouletteId = manager.getRouletteIdByScrollItem(ItemProvider.NEXO, nexoItemId)
            if (nexoRouletteId != null) {
                return nexoRouletteId
            }
        }

        return null
    }

    /**
     * Nexo 아이템 ID 가져오기
     */
    private fun getNexoItemId(item: ItemStack): String? {
        return try {
            val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val method = nexoClass.getMethod("idFromItem", ItemStack::class.java)
            method.invoke(null, item) as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 스크롤 룰렛 GUI 열기
     */
    private fun openScrollRoulette(player: Player, rouletteId: Int) {
        val gui = ScrollRouletteGUI(plugin, manager, player, rouletteId)

        // 세션 등록
        if (!manager.startSession(player, gui)) {
            player.sendMessage("§c[스크롤 룰렛] 이미 룰렛을 진행 중입니다!")
            return
        }

        gui.open()
    }

    /**
     * GUI 클릭 이벤트 처리
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        if (view.title != GUI_TITLE) return

        val player = event.whoClicked as? Player ?: return

        // 모든 클릭 취소 (아이템 이동 방지)
        event.isCancelled = true

        // 활성 세션 확인
        val gui = manager.getSession(player) ?: return

        // 클릭한 슬롯 확인
        val clickedSlot = event.rawSlot

        // 건너뛰기 버튼 클릭
        if (clickedSlot == SLOT_SKIP_BUTTON && gui.isAnimating()) {
            gui.skipAnimation()
            player.closeInventory()
        }
    }

    /**
     * GUI 닫기 이벤트 처리
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val view = event.view
        if (view.title != GUI_TITLE) return

        val player = event.player as? Player ?: return

        // 활성 세션 확인
        val gui = manager.getSession(player) ?: return

        // GUI 닫기 처리 (애니메이션 중이면 즉시 결과 처리)
        gui.onClose()
    }

    /**
     * 모든 활성 세션 정리 (플러그인 비활성화 시)
     */
    fun cleanup() {
        manager.cleanupAllSessions()
    }
}

package com.lukehemmin.lukeVanilla.System.Roulette

import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * 룰렛 NPC 우클릭 이벤트 리스너
 * - Citizens NPC 우클릭 감지
 * - 비용 확인 및 차감
 * - 룰렛 GUI 열기
 */
class RouletteNPCListener(
    private val plugin: JavaPlugin,
    private val manager: RouletteManager,
    private val economyManager: EconomyManager
) : Listener {

    // 현재 열려있는 룰렛 GUI 추적
    private val activeGUIs = mutableMapOf<Player, RouletteGUI>()

    /**
     * NPC 우클릭 이벤트 처리
     */
    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val player = event.clicker
        val npc = event.npc
        val npcId = npc.id

        // NPC ID로 룰렛 조회
        val rouletteId = manager.getRouletteIdByNPC(npcId) ?: return

        // 룰렛 설정 조회
        val roulette = manager.getRouletteById(rouletteId) ?: return

        // 룰렛이 활성화되어 있는지 확인
        if (!roulette.enabled) {
            player.sendMessage("§c현재 룰렛이 비활성화되어 있습니다.")
            return
        }

        // 룰렛 아이템이 있는지 확인
        if (manager.getItems(rouletteId).isEmpty()) {
            player.sendMessage("§c룰렛에 등록된 아이템이 없습니다.")
            return
        }

        // 이미 룰렛을 플레이 중인지 확인
        if (activeGUIs.containsKey(player)) {
            // 진행 중인 GUI를 다시 열어줌
            val existingGUI = activeGUIs[player]!!
            player.openInventory(existingGUI.getInventory())
            player.sendMessage("§e진행 중인 룰렛 화면을 다시 열었습니다!")
            return
        }

        // 룰렛 GUI 열기 (비용은 나중에 차감)
        openRoulette(player, rouletteId)
    }

    /**
     * 비용 확인 및 차감
     */
    private fun checkAndPayCost(player: Player, rouletteId: Int): Boolean {
        val config = manager.getRouletteById(rouletteId) ?: return false

        return when (config.costType) {
            CostType.MONEY -> {
                val balance = economyManager.getBalance(player)

                if (balance < config.costAmount) {
                    player.sendMessage("§c돈이 부족합니다. (필요: ${config.costAmount}원, 보유: ${balance}원)")
                    return false
                }

                // 돈 차감
                economyManager.removeBalance(player, config.costAmount)
                player.sendMessage("§e${config.costAmount}원이 차감되었습니다.")
                true
            }

            CostType.ITEM -> {
                // 아이템 비용 처리 (추후 구현 가능)
                player.sendMessage("§c아이템 비용은 아직 지원하지 않습니다.")
                false
            }

            CostType.FREE -> {
                // 무료
                true
            }

            else -> false
        }
    }

    /**
     * 룰렛 GUI 열기
     */
    private fun openRoulette(player: Player, rouletteId: Int) {
        val gui = RouletteGUI(plugin, manager, player, rouletteId)
        activeGUIs[player] = gui
        gui.open()
    }

    /**
     * 인벤토리 클릭 이벤트 (룰렛 GUI 내부 클릭 처리)
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val gui = activeGUIs[player] ?: return

        // 룰렛 GUI의 제목 확인
        if (event.view.title == "§6§l[ 룰렛 ]") {
            event.isCancelled = true // 모든 클릭 차단

            // 클릭한 슬롯이 22번(중앙)이고, 네더별인지 확인
            if (event.slot == 22) {
                val clickedItem = event.currentItem
                if (clickedItem?.type == org.bukkit.Material.NETHER_STAR) {
                    // 비용 확인 및 차감
                    if (!checkAndPayCost(player, gui.getRouletteId())) {
                        return
                    }

                    // 네더별 클릭 시 룰렛 시작
                    gui.startAnimation()
                }
            }
        }
    }

    /**
     * 인벤토리 닫기 이벤트
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val gui = activeGUIs[player] ?: return

        // 룰렛 GUI의 제목 확인
        if (event.view.title == "§6§l[ 룰렛 ]") {
            // GUI 닫힐 때 처리
            gui.onClose()

            // 애니메이션이 진행 중이면 activeGUIs에서 제거하지 않음
            // 나중에 NPC를 다시 우클릭하면 진행 중인 화면을 다시 볼 수 있음
            if (!gui.isAnimating()) {
                // 애니메이션이 끝났거나 시작하지 않았으면 제거
                activeGUIs.remove(player)
            } else {
                // 애니메이션 중이면 메시지 출력
                player.sendMessage("§7룰렛이 백그라운드에서 계속 돌아가고 있습니다.")
                player.sendMessage("§7NPC를 다시 우클릭하면 화면을 볼 수 있습니다!")
            }
        }
    }

    /**
     * 활성 GUI 개수 조회 (디버깅용)
     */
    fun getActiveGUICount(): Int = activeGUIs.size

    /**
     * 플레이어의 활성 GUI 강제 제거 (플러그인 비활성화 시 등)
     */
    fun removeActiveGUI(player: Player) {
        activeGUIs[player]?.forceStop()
        activeGUIs.remove(player)
        player.closeInventory()
    }
}

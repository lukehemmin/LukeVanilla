package com.lukehemmin.lukeVanilla.System.Roulette

import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import com.lukehemmin.lukeVanilla.System.Economy.TransactionType
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Nexo 가구 우클릭 룰렛 리스너
 * - Nexo 가구를 우클릭했을 때 룰렛 GUI 열기
 * - RouletteManager에서 Nexo 아이템 ID로 룰렛 조회
 */
class RouletteNexoListener(
    private val plugin: JavaPlugin,
    private val manager: RouletteManager,
    private val economyManager: EconomyManager
) : Listener {

    /**
     * Nexo 가구 우클릭 이벤트 처리
     */
    @EventHandler
    fun onNexoFurnitureInteract(event: NexoFurnitureInteractEvent) {
        val player = event.player
        val mechanic = event.mechanic
        val itemId = mechanic?.itemID ?: return

        // Nexo 아이템 ID로 룰렛 조회
        val rouletteId = manager.getRouletteIdByNexo(itemId) ?: return

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

        // 이미 룰렛을 플레이 중인지 확인 (중앙 세션)
        if (manager.hasActiveSession(player)) {
            // 진행 중인 GUI를 다시 열어줌
            val existingGUI = manager.getSession(player)!!
            player.openInventory(existingGUI.getInventory())
            player.sendMessage("§e진행 중인 룰렛 화면을 다시 열었습니다!")
            return
        }

        // 플레이어가 손에 들고 있는 아이템 확인
        val itemInHand = player.inventory.itemInMainHand
        var isPaidWithKey = false

        // 열쇠 아이템이 설정되어 있는지 확인
        val keyItemInfo = manager.getKeyItemInfo(rouletteId)
        if (keyItemInfo != null && keyItemInfo.first != null && keyItemInfo.second != null) {
            val (keyProvider, keyType) = keyItemInfo

            // 손에 든 아이템이 열쇠인지 확인
            if (isKeyItem(itemInHand, keyProvider!!, keyType!!)) {
                // 열쇠 1개 소모
                if (itemInHand.amount > 1) {
                    itemInHand.amount--
                } else {
                    player.inventory.setItemInMainHand(null)
                }

                player.sendMessage("§a열쇠를 사용하여 무료로 룰렛을 이용합니다!")
                isPaidWithKey = true
            }
        }

        // 룰렛 GUI 열기 (비용 지불 여부 플래그 전달)
        openRoulette(player, rouletteId, isPaidWithKey)
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
                economyManager.withdraw(
                    player, 
                    config.costAmount, 
                    TransactionType.ROULETTE, 
                    "룰렛 플레이 비용 (ID: ${config.id})"
                )
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
     * 아이템이 룰렛 열쇠인지 확인
     */
    private fun isKeyItem(item: org.bukkit.inventory.ItemStack, provider: ItemProvider, itemType: String): Boolean {
        if (item.type == org.bukkit.Material.AIR) return false

        return when (provider) {
            ItemProvider.VANILLA -> {
                try {
                    val material = org.bukkit.Material.valueOf(itemType.uppercase())
                    item.type == material
                } catch (e: IllegalArgumentException) {
                    false
                }
            }

            ItemProvider.NEXO -> {
                try {
                    val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
                    val method = nexoClass.getMethod("itemFromId", String::class.java)
                    val itemBuilder = method.invoke(null, itemType)

                    if (itemBuilder != null) {
                        val buildMethod = itemBuilder.javaClass.getMethod("build")
                        val nexoItem = buildMethod.invoke(itemBuilder) as? org.bukkit.inventory.ItemStack

                        // Nexo 아이템인지 확인 (isSimilar 사용)
                        nexoItem != null && item.isSimilar(nexoItem)
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[Roulette] Nexo 열쇠 아이템 확인 오류: ${e.message}")
                    false
                }
            }

            else -> false
        }
    }

    /**
     * 룰렛 GUI 열기
     */
    private fun openRoulette(player: Player, rouletteId: Int, isPaidWithKey: Boolean = false) {
        val gui = RouletteGUI(plugin, manager, player, rouletteId, isPaidWithKey)
        
        // 중앙 세션 관리에 등록
        if (!manager.startSession(player, gui)) {
            player.sendMessage("§c이미 룰렛을 진행 중입니다!")
            return
        }

        gui.open()
    }



    /**
     * 플레이어의 활성 GUI 강제 제거 (플러그인 비활성화 시 등)
     */
    fun removeActiveGUI(player: Player) {
        manager.getSession(player)?.forceStop()
        manager.endSession(player)
        player.closeInventory()
    }

    /**
     * 모든 활성 GUI 정리
     */
    fun cleanup() {
        manager.cleanupAllSessions()
    }
}

package com.lukehemmin.lukeVanilla.System.FishMerchant

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class FishMerchantGUI(
    private val plugin: JavaPlugin,
    private val fishMerchantManager: FishMerchantManager
) : Listener {

    companion object {
        private const val GUI_SIZE = 54
        private const val PRICE_INFO_SLOT = 47
        private const val SELECT_SELL_SLOT = 49
        private const val SELL_ALL_SLOT = 51
    }

    private val openGUIs = mutableMapOf<Player, Inventory>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun openGUI(player: Player) {
        val inventory = Bukkit.createInventory(null, GUI_SIZE, Component.text("낚시 상인", NamedTextColor.DARK_GREEN))

        // 가격 정보 아이템
        inventory.setItem(PRICE_INFO_SLOT, createPriceInfoItem(0.0, 0))

        // 선택 판매 버튼
        inventory.setItem(SELECT_SELL_SLOT, createSelectSellButton())

        // 모두 판매 버튼
        inventory.setItem(SELL_ALL_SLOT, createSellAllButton())

        // 장식 아이템 (유리판)
        val glassPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" "))
            }
        }
        for (slot in listOf(45, 46, 48, 50, 52, 53)) {
            inventory.setItem(slot, glassPane)
        }

        openGUIs[player] = inventory
        player.openInventory(inventory)
    }

    private fun createPriceInfoItem(totalPrice: Double, fishCount: Int): ItemStack {
        return ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(
                    Component.text("총 판매 정보", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("물고기 수량: ", NamedTextColor.GRAY)
                            .append(Component.text("${fishCount}개", NamedTextColor.AQUA))
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("예상 금액: ", NamedTextColor.GRAY)
                            .append(Component.text("${totalPrice}원", NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("물고기를 GUI에 올려보세요!", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createSelectSellButton(): ItemStack {
        return ItemStack(Material.LIME_WOOL).apply {
            editMeta { meta ->
                meta.displayName(
                    Component.text("선택 판매", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("GUI에 올린 물고기만 판매합니다", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("✓ 원하는 물고기만 선택 가능", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("✓ 가격이 없는 물고기는 자동 제외", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("▶ 클릭하여 판매", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createSellAllButton(): ItemStack {
        return ItemStack(Material.GOLD_BLOCK).apply {
            editMeta { meta ->
                meta.displayName(
                    Component.text("모두 판매", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
                )
                meta.lore(
                    listOf(
                        Component.empty(),
                        Component.text("인벤토리의 모든 물고기를 판매합니다", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("✓ 전체 인벤토리에서 물고기 검색", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("✓ 가격이 설정된 물고기만 판매", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("✓ 즉시 판매 및 GUI 닫기", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("▶ 클릭하여 전체 판매", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory ?: return
        val inventory = openGUIs[player] ?: return

        if (event.view.topInventory != inventory) return

        // 하단 컨트롤 영역 클릭 방지
        if (clickedInventory == inventory && event.slot >= 45) {
            event.isCancelled = true

            when (event.slot) {
                SELECT_SELL_SLOT -> handleSelectSell(player, inventory)
                SELL_ALL_SLOT -> handleSellAll(player)
            }
            return
        }

        // 물고기 슬롯 (0-44)에서의 일반 아이템 조작은 허용
        if (clickedInventory == inventory && event.slot in 0..44) {
            // 가격 업데이트는 다음 틱에 수행 (인벤토리 변경 후)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                updatePriceInfo(inventory)
            }, 1L)
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = openGUIs[player] ?: return

        if (event.view.topInventory != inventory) return

        // 하단 컨트롤 영역에 드래그 방지
        if (event.rawSlots.any { it >= 45 && it < GUI_SIZE }) {
            event.isCancelled = true
            return
        }

        // 가격 업데이트
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            updatePriceInfo(inventory)
        }, 1L)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val inventory = openGUIs[player] ?: return

        if (event.inventory != inventory) return

        // GUI 닫을 때 물고기 슬롯의 아이템 반환
        for (slot in 0..44) {
            val item = inventory.getItem(slot)
            if (item != null && item.type != Material.AIR) {
                player.inventory.addItem(item).values.forEach { leftover ->
                    player.world.dropItem(player.location, leftover)
                }
            }
        }

        openGUIs.remove(player)
    }

    private fun updatePriceInfo(inventory: Inventory) {
        var totalPrice = 0.0
        var totalFishCount = 0

        for (slot in 0..44) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type == Material.AIR) continue

            val fishInfo = fishMerchantManager.identifyFish(item) ?: continue
            val price = fishMerchantManager.calculateFishPrice(fishInfo) ?: continue

            totalPrice += price * item.amount
            totalFishCount += item.amount
        }

        inventory.setItem(PRICE_INFO_SLOT, createPriceInfoItem(totalPrice, totalFishCount))
    }

    private fun handleSelectSell(player: Player, inventory: Inventory) {
        val fishItems = mutableListOf<Pair<ItemStack, FishIdentificationResult>>()
        var totalPrice = 0.0

        // 판매할 물고기 수집
        for (slot in 0..44) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type == Material.AIR) continue

            val fishInfo = fishMerchantManager.identifyFish(item)
            if (fishInfo == null) {
                player.sendMessage(
                    Component.text("물고기가 아닌 아이템이 포함되어 있습니다: ", NamedTextColor.RED)
                        .append(Component.text(item.type.name, NamedTextColor.YELLOW))
                )
                continue
            }

            val price = fishMerchantManager.calculateFishPrice(fishInfo)
            if (price == null) {
                player.sendMessage(
                    Component.text("구매하지 않는 물고기: [${fishInfo.provider}] ${fishInfo.displayName}", NamedTextColor.RED)
                )
                continue
            }

            fishItems.add(item.clone() to fishInfo)
            totalPrice += price * item.amount
            inventory.setItem(slot, null)
        }

        if (fishItems.isEmpty()) {
            player.sendMessage(Component.text("판매할 물고기가 없습니다.", NamedTextColor.RED))
            return
        }

        // 경제 시스템에 돈 추가
        val economyManager = (plugin as com.lukehemmin.lukeVanilla.Main).economyManager
        economyManager.addBalance(player, totalPrice)

        // 판매 완료 메시지
        player.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.text("물고기 판매 완료!", NamedTextColor.GREEN))
        fishItems.groupBy { it.second }.forEach { (fishInfo, items) ->
            val totalAmount = items.sumOf { it.first.amount }
            val price = fishMerchantManager.calculateFishPrice(fishInfo)!!
            val sizeText = if (fishInfo.size != null) " (${fishInfo.size}cm)" else ""
            player.sendMessage(
                Component.text("  - [${fishInfo.provider}] ${fishInfo.displayName}${sizeText} x${totalAmount} = ", NamedTextColor.WHITE)
                    .append(Component.text("${price * totalAmount}원", NamedTextColor.YELLOW))
            )
        }
        player.sendMessage(
            Component.text("총 획득 금액: ", NamedTextColor.GREEN)
                .append(Component.text("${totalPrice}원", NamedTextColor.GOLD))
        )
        player.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))

        updatePriceInfo(inventory)
    }

    private fun handleSellAll(player: Player) {
        val fishItems = mutableListOf<Pair<ItemStack, FishIdentificationResult>>()
        var totalPrice = 0.0

        // 플레이어 인벤토리에서 판매 가능한 물고기 찾기
        for (item in player.inventory.contents) {
            if (item == null || item.type == Material.AIR) continue

            val fishInfo = fishMerchantManager.identifyFish(item) ?: continue
            val price = fishMerchantManager.calculateFishPrice(fishInfo) ?: continue

            fishItems.add(item to fishInfo)
            totalPrice += price * item.amount
        }

        if (fishItems.isEmpty()) {
            player.sendMessage(Component.text("인벤토리에 판매 가능한 물고기가 없습니다.", NamedTextColor.RED))
            return
        }

        // 확인 메시지
        player.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.text("모든 물고기 판매 완료!", NamedTextColor.GREEN))

        val groupedFish = fishItems.groupBy { it.second }
        groupedFish.forEach { (fishInfo, items) ->
            val totalAmount = items.sumOf { it.first.amount }
            val price = fishMerchantManager.calculateFishPrice(fishInfo)!!
            val sizeText = if (fishInfo.size != null) " (${fishInfo.size}cm)" else ""
            player.sendMessage(
                Component.text("  - [${fishInfo.provider}] ${fishInfo.displayName}${sizeText} x${totalAmount} = ", NamedTextColor.WHITE)
                    .append(Component.text("${price * totalAmount}원", NamedTextColor.YELLOW))
            )
        }
        player.sendMessage(
            Component.text("총 획득 금액: ", NamedTextColor.GREEN)
                .append(Component.text("${totalPrice}원", NamedTextColor.GOLD))
        )
        player.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))

        // 물고기 제거 및 돈 지급
        fishItems.forEach { (item, _) ->
            player.inventory.remove(item)
        }

        val economyManager = (plugin as com.lukehemmin.lukeVanilla.Main).economyManager
        economyManager.addBalance(player, totalPrice)

        player.closeInventory()
    }
}

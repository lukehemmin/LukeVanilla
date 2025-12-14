package com.lukehemmin.lukeVanilla.System.ScrollRoulette

import com.lukehemmin.lukeVanilla.System.Roulette.ItemProvider
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.min

/**
 * 스크롤 룰렛 GUI 및 애니메이션 시스템
 * - 3줄 인벤토리로 룰렛 구현
 * - 좌→우 스크롤 애니메이션 (아이템이 왼쪽으로 밀리며 새 아이템이 오른쪽에서 등장)
 * - 점점 느려지는 효과
 * - 중앙(슬롯 13)에 멈춘 아이템이 당첨
 */
class ScrollRouletteGUI(
    private val plugin: JavaPlugin,
    private val manager: ScrollRouletteManager,
    private val player: Player,
    private val rouletteId: Int
) {
    private lateinit var inventory: Inventory
    private var animationTask: BukkitTask? = null
    private var currentTick = 0
    private var winningItem: ScrollRouletteItem? = null
    private var isAnimating = false
    private var awarded = false

    // 아이템 순환 리스트
    private val itemCycle = mutableListOf<ItemStack>()
    private var currentOffset = 0

    companion object {
        // GUI 설정 상수
        private const val GUI_TITLE = "§6§l[ 스크롤 룰렛 ]"
        private const val GUI_SIZE = 27 // 3줄

        // 슬롯 위치 상수
        private val DECORATION_ROW_TOP = 0..8      // 상단 장식 줄 (슬롯 0-8)
        private val ROULETTE_ROW = 9..17           // 룰렛 아이템 줄 (슬롯 9-17)
        private val DECORATION_ROW_BOTTOM = 18..26 // 하단 장식 줄 (슬롯 18-26)
        private const val SLOT_WINNING = 13        // 당첨 위치 (중앙)
        private const val SLOT_SKIP_BUTTON = 22    // 건너뛰기 버튼 위치

        // 애니메이션 설정
        private const val ANIMATION_DURATION = 120 // 틱 수
        private const val ITEM_CYCLE_MULTIPLIER = 10 // 아이템 반복 횟수
    }

    /**
     * GUI 열기 및 애니메이션 자동 시작
     */
    fun open() {
        // 당첨 아이템 미리 결정
        winningItem = manager.selectRandomItem(rouletteId)
        if (winningItem == null) {
            player.sendMessage("§c[스크롤 룰렛] 등록된 당첨 아이템이 없습니다.")
            manager.endSession(player)
            return
        }

        // 인벤토리 생성
        inventory = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE)

        // 아이템 순환 리스트 생성
        createItemCycle()

        // 초기 GUI 설정
        setupInitialGUI()

        // GUI 열기
        player.openInventory(inventory)

        // 애니메이션 시작
        startAnimation()
    }

    /**
     * 아이템 순환 리스트 생성
     * - 등록된 아이템을 반복 배치
     * - 당첨 아이템이 최종적으로 중앙에 오도록 설정
     */
    private fun createItemCycle() {
        val items = manager.getItems(rouletteId)
        if (items.isEmpty()) return

        // 등록된 아이템이 9개 미만이면 반복하여 채움
        val displayItems = mutableListOf<ScrollRouletteItem>()
        while (displayItems.size < 9) {
            displayItems.addAll(items)
        }

        // 아이템 순환 리스트 생성 (충분히 많이)
        for (cycle in 0 until ITEM_CYCLE_MULTIPLIER) {
            for (item in displayItems) {
                val itemStack = item.toItemStack() ?: continue
                // 아이템에 확률 정보 추가
                val meta = itemStack.itemMeta
                val lore = meta?.lore?.toMutableList() ?: mutableListOf()
                val totalProb = manager.getItems(rouletteId).sumOf { it.probability }
                val actualProb = if (totalProb > 0) (item.probability / totalProb) * 100 else 0.0
                lore.add("")
                lore.add("§7확률: §e${String.format("%.2f", actualProb)}%")
                meta?.lore = lore
                itemStack.itemMeta = meta
                itemCycle.add(itemStack.clone())
            }
        }

        // 당첨 아이템의 위치를 계산하여 애니메이션 종료 시 중앙에 오도록 조정
        val totalMoves = calculateTotalMoves()
        val targetPosition = (totalMoves + 4) % itemCycle.size // 중앙(인덱스 4)에 오도록

        // 당첨 아이템을 해당 위치에 배치
        val winItemStack = winningItem?.toItemStack()
        if (winItemStack != null && itemCycle.isNotEmpty()) {
            val meta = winItemStack.itemMeta
            val lore = meta?.lore?.toMutableList() ?: mutableListOf()
            val totalProb = manager.getItems(rouletteId).sumOf { it.probability }
            val actualProb = if (totalProb > 0) (winningItem!!.probability / totalProb) * 100 else 0.0
            lore.add("")
            lore.add("§7확률: §e${String.format("%.2f", actualProb)}%")
            meta?.lore = lore
            winItemStack.itemMeta = meta
            
            val safePosition = targetPosition.coerceIn(0, itemCycle.size - 1)
            itemCycle[safePosition] = winItemStack.clone()
        }

        plugin.logger.info("[ScrollRoulette] 당첨 아이템 배치 - totalMoves: $totalMoves, targetPosition: $targetPosition")
    }

    /**
     * 애니메이션의 총 이동 횟수를 계산
     */
    private fun calculateTotalMoves(): Int {
        var moves = 0
        for (tick in 0 until ANIMATION_DURATION) {
            val progress = tick.toDouble() / ANIMATION_DURATION
            val shouldMove = when {
                progress < 0.3 -> tick % 1 == 0   // 처음 30%: 매 틱마다 이동
                progress < 0.5 -> tick % 2 == 0  // 30-50%: 2틱마다 이동
                progress < 0.7 -> tick % 3 == 0  // 50-70%: 3틱마다 이동
                progress < 0.85 -> tick % 5 == 0 // 70-85%: 5틱마다 이동
                progress < 0.95 -> tick % 8 == 0 // 85-95%: 8틱마다 이동
                else -> tick % 12 == 0           // 95-100%: 12틱마다 이동
            }
            if (shouldMove) moves++
        }
        return moves
    }

    /**
     * 초기 GUI 설정
     */
    private fun setupInitialGUI() {
        // 상단 장식 (회색 유리판)
        val grayPane = createGrayPane()
        for (slot in DECORATION_ROW_TOP) {
            inventory.setItem(slot, grayPane)
        }

        // 하단 장식 (회색 유리판 + 건너뛰기 버튼)
        for (slot in DECORATION_ROW_BOTTOM) {
            if (slot == SLOT_SKIP_BUTTON) {
                inventory.setItem(slot, createSkipButton())
            } else {
                inventory.setItem(slot, grayPane)
            }
        }

        // 중앙 상단에 당첨 표시 화살표
        val arrowItem = ItemStack(Material.YELLOW_STAINED_GLASS_PANE)
        val arrowMeta = arrowItem.itemMeta
        arrowMeta?.setDisplayName("§e§l▼ 당첨 ▼")
        arrowItem.itemMeta = arrowMeta
        inventory.setItem(4, arrowItem) // 상단 중앙

        // 룰렛 아이템 초기 배치
        updateRouletteItems()
    }

    /**
     * 회색 유리판 생성
     */
    private fun createGrayPane(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    /**
     * 건너뛰기 버튼 생성
     */
    private fun createSkipButton(): ItemStack {
        val item = ItemStack(Material.LIME_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.setDisplayName("§a§l[ 건너뛰기 ]")
        meta?.lore = listOf(
            "",
            "§7클릭하면 결과가 바로 공개됩니다!",
            ""
        )
        item.itemMeta = meta
        return item
    }

    /**
     * 룰렛 아이템 배치 (좌→우 스크롤)
     */
    private fun updateRouletteItems() {
        for (i in 0..8) {
            val cycleIndex = (currentOffset + i) % itemCycle.size
            val item = itemCycle.getOrNull(cycleIndex)
            inventory.setItem(ROULETTE_ROW.first + i, item)
        }
    }

    /**
     * 애니메이션 시작
     */
    private fun startAnimation() {
        if (isAnimating) return

        isAnimating = true
        currentTick = 0
        currentOffset = 0

        animationTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (currentTick >= ANIMATION_DURATION) {
                // 애니메이션 종료
                stopAnimation()
                return@Runnable
            }

            // 속도 계산 (처음엔 빠르게, 점점 느려짐)
            val progress = currentTick.toDouble() / ANIMATION_DURATION
            val shouldMove = when {
                progress < 0.3 -> currentTick % 1 == 0   // 처음 30%: 매 틱마다 이동
                progress < 0.5 -> currentTick % 2 == 0  // 30-50%: 2틱마다 이동
                progress < 0.7 -> currentTick % 3 == 0  // 50-70%: 3틱마다 이동
                progress < 0.85 -> currentTick % 5 == 0 // 70-85%: 5틱마다 이동
                progress < 0.95 -> currentTick % 8 == 0 // 85-95%: 8틱마다 이동
                else -> currentTick % 12 == 0           // 95-100%: 12틱마다 이동
            }

            if (shouldMove) {
                currentOffset++
                updateRouletteItems()

                // 사운드 효과 (점점 낮은 음으로)
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.3f, 1.5f - (progress * 0.5f).toFloat())
            }

            currentTick++
        }, 0L, 1L)
    }

    /**
     * 애니메이션 중지 및 결과 처리
     */
    private fun stopAnimation() {
        animationTask?.cancel()
        animationTask = null
        isAnimating = false

        // 최종 당첨 아이템 표시
        showWinningItem()

        // 파티클 효과 (clone()으로 원본 Location 보호)
        player.spawnParticle(Particle.END_ROD, player.location.clone().add(0.0, 2.0, 0.0), 50, 0.5, 0.5, 0.5, 0.1)

        // 당첨 사운드
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        // 1초 후 아이템 지급 및 GUI 닫기
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) {
                plugin.logger.warning("[ScrollRoulette] 플레이어가 로그아웃하여 아이템 지급을 건너뜁니다. (플레이어: ${player.name})")
                manager.endSession(player)
                return@Runnable
            }

            giveWinningItem()
            player.closeInventory()
            manager.endSession(player)
        }, 20L)
    }

    /**
     * 최종 당첨 아이템 강조 표시
     */
    private fun showWinningItem() {
        val winning = winningItem ?: return
        val winItem = winning.toItemStack() ?: return

        // 당첨 아이템에 효과 추가
        // 꽝 체크 (VANILLA + BARRIER)
        val isLose = winning.itemProvider == ItemProvider.VANILLA && winning.itemCode == "BARRIER"

        val meta = winItem.itemMeta
        val lore = meta?.lore?.toMutableList() ?: mutableListOf()
        val totalProb = manager.getItems(rouletteId).sumOf { it.probability }
        val actualProb = if (totalProb > 0) (winningItem!!.probability / totalProb) * 100 else 0.0
        
        lore.add("")
        if (isLose) {
            lore.add("§c§l✕ 꽝! ✕")
        } else {
            lore.add("§e§l★ 당첨! ★")
        }
        lore.add("§7확률: §e${String.format("%.2f", actualProb)}%")
        lore.add("")
        meta?.lore = lore
        winItem.itemMeta = meta

        // 중앙에 당첨 아이템 배치
        inventory.setItem(SLOT_WINNING, winItem)

        // 양쪽 아이템을 회색 유리판으로 교체하여 당첨 아이템 강조
        val grayPane = createGrayPane()
        for (slot in ROULETTE_ROW) {
            if (slot != SLOT_WINNING) {
                inventory.setItem(slot, grayPane)
            }
        }

        // 플레이어에게 메시지 전송
        val itemName = winning.itemDisplayName ?: winItem.type.name
        val probability = manager.calculateWinProbability(rouletteId, winning)
        
        if (isLose) {
            player.sendMessage("§e§l[ 스크롤 룰렛 ] §c꽝! §7아쉽지만 다음 기회에! §7(${String.format("%.2f", probability)}%)")
        } else {
            player.sendMessage("§e§l[ 스크롤 룰렛 ] §a당첨! §f$itemName §ax${winning.itemAmount} §7(${String.format("%.2f", probability)}%)")
        }
    }

    /**
     * 당첨 아이템 지급
     */
    private fun giveWinningItem() {
        // 중복 지급 방지
        if (awarded) {
            plugin.logger.warning("[ScrollRoulette] 아이템이 이미 지급되었습니다. (플레이어: ${player.name})")
            return
        }

        val winning = winningItem ?: return
        
        // 꽝 체크 (VANILLA + BARRIER)
        if (winning.itemProvider == ItemProvider.VANILLA && winning.itemCode == "BARRIER") {
            player.sendMessage("§c§l[ 꽝 ] §7아쉽지만 다음 기회에!")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            awarded = true
            
            // 히스토리 저장 (아이템 지급 완료 후)
            saveHistoryAsync(winning)
            
            winningItem = null
            return
        }
        
        val winItem = winning.toItemStack() ?: return

        // 인벤토리에 공간이 있는지 확인
        val emptySlot = player.inventory.firstEmpty()
        if (emptySlot == -1) {
            player.sendMessage("§c인벤토리에 공간이 없어 아이템이 바닥에 떨어졌습니다!")
            // 위치 복사하여 드롭 (플레이어 이동 시 문제 방지)
            player.world.dropItem(player.location.clone(), winItem)
        } else {
            player.inventory.addItem(winItem)
        }

        // 지급 완료 플래그 설정
        awarded = true
        
        // 히스토리 저장 (아이템 지급 완료 후)
        saveHistoryAsync(winning)
        
        winningItem = null
    }
    
    /**
     * 히스토리 저장 (비동기)
     */
    private fun saveHistoryAsync(item: ScrollRouletteItem) {
        val probability = manager.calculateWinProbability(rouletteId, item)
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            manager.saveHistory(
                rouletteId = rouletteId,
                playerUuid = player.uniqueId.toString(),
                playerName = player.name,
                item = item,
                winProbability = probability
            )
        })
    }

    /**
     * GUI가 닫힐 때 처리 (건너뛰기와 동일하게 처리)
     */
    fun onClose() {
        if (isAnimating) {
            // 애니메이션 중이면 즉시 결과 처리
            // skipAnimation 내부에서 아이템 지급 및 세션 종료까지 처리
            skipAnimation()
        }
        // awarded == true인 경우: 정상 종료
        // awarded == false && !isAnimating: stopAnimation의 runTaskLater가 아직 실행 중
        // 이 경우 runTaskLater에서 세션 종료 처리됨
    }

    /**
     * 애니메이션 건너뛰기
     */
    fun skipAnimation() {
        if (!isAnimating) return

        // 애니메이션 작업 취소
        animationTask?.cancel()
        animationTask = null
        isAnimating = false

        player.sendMessage("§e[스크롤 룰렛] 결과를 건너뛰었습니다!")

        // 최종 당첨 아이템 표시
        showWinningItem()

        // 파티클 효과 (clone()으로 원본 Location 보호)
        player.spawnParticle(Particle.END_ROD, player.location.clone().add(0.0, 2.0, 0.0), 50, 0.5, 0.5, 0.5, 0.1)

        // 당첨 사운드
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        // 즉시 아이템 지급
        giveWinningItem()
        
        // 세션 종료
        manager.endSession(player)
    }

    /**
     * 강제로 애니메이션 중지 및 즉시 당첨 처리 (플러그인 비활성화 시 등)
     */
    fun forceStop() {
        if (isAnimating) {
            animationTask?.cancel()
            animationTask = null
            isAnimating = false

            // 중복 지급 방지
            if (awarded) {
                plugin.logger.warning("[ScrollRoulette] 아이템이 이미 지급되었습니다. (플레이어: ${player.name}, forceStop)")
                return
            }

            val winning = winningItem

            // 플레이어가 온라인인지 확인
            if (winning != null && player.isOnline) {
                player.sendMessage("§e[스크롤 룰렛] 서버 리로드로 인해 룰렛이 중단되었습니다.")

                // 지급 완료 플래그 먼저 설정 (중복 방지)
                awarded = true

                // 꽝 체크 (VANILLA + BARRIER)
                if (winning.itemProvider == ItemProvider.VANILLA && winning.itemCode == "BARRIER") {
                    player.sendMessage("§c§l[ 꽝 ] §7아쉽지만 다음 기회에!")
                    // 히스토리 저장 (꽝도 기록)
                    saveHistoryAsync(winning)
                    winningItem = null
                    return
                }

                // 당첨 아이템 즉시 지급
                val winItem = winning.toItemStack()
                if (winItem != null) {
                    val emptySlot = player.inventory.firstEmpty()
                    if (emptySlot == -1) {
                        player.sendMessage("§c인벤토리에 공간이 없어 아이템이 바닥에 떨어졌습니다!")
                        // 위치 복사하여 드롭 (플레이어 이동 시 문제 방지)
                        player.world.dropItem(player.location.clone(), winItem)
                    } else {
                        player.inventory.addItem(winItem)
                        val itemName = winning.itemDisplayName ?: winItem.type.name
                        player.sendMessage("§a당첨 아이템이 지급되었습니다! §f$itemName §ax${winItem.amount}")
                    }

                    // 히스토리 저장 (아이템 지급 완료 후)
                    saveHistoryAsync(winning)
                }

                winningItem = null
            }
        }
    }

    /**
     * 현재 애니메이션 중인지 확인
     */
    fun isAnimating(): Boolean = isAnimating

    /**
     * 인벤토리 가져오기
     */
    fun getInventory(): Inventory = inventory

    /**
     * 룰렛 ID 가져오기
     */
    fun getRouletteId(): Int = rouletteId
}

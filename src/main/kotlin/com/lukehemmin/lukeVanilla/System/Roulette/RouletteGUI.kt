package com.lukehemmin.lukeVanilla.System.Roulette

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.min

/**
 * 룰렛 GUI 및 애니메이션 시스템
 * - 6줄 인벤토리로 룰렛 구현
 * - 좌→우 스크롤 애니메이션
 * - 점점 느려지는 효과
 */
class RouletteGUI(
    private val plugin: JavaPlugin,
    private val manager: RouletteManager,
    private val player: Player,
    private val rouletteId: Int
) {
    private lateinit var inventory: Inventory
    private var animationTask: BukkitTask? = null
    private var currentTick = 0
    private var winningItem: RouletteItem? = null
    private var isAnimating = false

    // 애니메이션 설정
    private val totalDuration = 150 // 전체 애니메이션 지속 시간 (틱) - 12개 슬롯이라 더 길게
    private val winningSlot = 4 // 12시 방향 (선택 포인트)

    // 아이템 순환 리스트
    private val itemCycle = mutableListOf<ItemStack>()
    private var currentRotation = 0

    companion object {
        // 원형 슬롯 배치 (시계방향으로 12시부터 시작) - 12개 슬롯으로 큰 원형
        private val CIRCLE_SLOTS = listOf(
            4,   // 12시 방향 (맨 위 중앙) - 당첨 포인트
            5,   // 1시 방향 (맨 위 우측)
            15,  // 2시 방향 (우측 위 대각선)
            24,  // 3시 방향 (우측)
            33,  // 4시 방향 (우측 아래 대각선)
            41,  // 5시 방향 (맨 아래 우측)
            40,  // 6시 방향 (맨 아래 중앙)
            39,  // 7시 방향 (맨 아래 좌측)
            29,  // 8시 방향 (좌측 아래 대각선)
            20,  // 9시 방향 (좌측)
            11,  // 10시 방향 (좌측 위 대각선)
            3    // 11시 방향 (맨 위 좌측)
        )

        // 장식용 유리판 재질
        private val BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE
        private val HIGHLIGHT_MATERIAL = Material.YELLOW_STAINED_GLASS_PANE
        private val CENTER_INFO_MATERIAL = Material.NETHER_STAR
    }

    /**
     * GUI 열기 (애니메이션은 시작하지 않음)
     */
    fun open() {
        // 인벤토리 생성
        inventory = Bukkit.createInventory(null, 54, "§6§l[ 룰렛 ]")

        // 초기 GUI 설정
        setupInitialGUI()

        // GUI 열기
        player.openInventory(inventory)
    }

    /**
     * 애니메이션의 총 이동 횟수를 계산
     */
    private fun calculateTotalMoves(): Int {
        var moves = 0
        for (tick in 0 until totalDuration) {
            val progress = tick.toDouble() / totalDuration
            val shouldMove = when {
                progress < 0.3 -> tick % 1 == 0  // 처음 30%: 매 틱마다 이동
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
     * 아이템 순환 리스트 생성
     * - 정확히 12개의 아이템을 순서대로 반복 배치
     */
    private fun createItemCycle() {
        val items = manager.getItems(rouletteId)
        if (items.isEmpty()) return

        // 12개 미만일 경우 경고 (12개 정확히 등록되어야 함)
        if (items.size < CIRCLE_SLOTS.size) {
            player.sendMessage("§c[룰렛] 아이템이 ${items.size}개만 등록되어 있습니다. 정확히 12개를 등록해주세요.")
            plugin.logger.warning("[Roulette] 아이템이 ${items.size}개만 등록되어 있습니다. 정확히 12개 필요!")
            return
        }

        // 정확히 12개만 사용 (12개 초과로 등록된 경우 상위 12개만 사용)
        val displayItems = items.take(CIRCLE_SLOTS.size)

        // 12개를 순서대로 25바퀴 반복 (12 * 25 = 300개)
        val cycles = 25
        for (cycle in 0 until cycles) {
            for (item in displayItems) {
                val itemStack = item.toItemStack() ?: continue
                itemCycle.add(itemStack.clone())
            }
        }

        // 당첨 아이템을 정확한 위치에 배치 (애니메이션 종료 시 12시 방향에 오도록)
        val winningItemStack = winningItem?.toItemStack()
        if (winningItemStack != null && itemCycle.isNotEmpty()) {
            // 총 이동 횟수를 계산하여 정확히 12시 방향(슬롯 0)에 오도록 배치
            val totalMoves = calculateTotalMoves()
            val targetPosition = totalMoves % itemCycle.size

            // 해당 위치에 당첨 아이템 배치
            itemCycle[targetPosition] = winningItemStack.clone()

            plugin.logger.info("[Roulette] 당첨 아이템 배치 - totalMoves: $totalMoves, targetPosition: $targetPosition")
        }
    }

    /**
     * 초기 GUI 설정
     */
    private fun setupInitialGUI() {
        // 테두리 설정
        setupBorders()

        // 선택 포인트 강조 및 시작 버튼
        highlightCenterSlot()

        // 하단 버튼
        setupBottomButtons()
    }

    /**
     * 테두리 및 배경 설정
     */
    private fun setupBorders() {
        val borderItem = createBorderItem()

        // 모든 슬롯을 테두리로 채움
        for (i in 0..53) {
            inventory.setItem(i, borderItem)
        }
    }

    /**
     * 선택 포인트 강조 (12시 방향)
     */
    private fun highlightCenterSlot() {
        // 12시 방향 위쪽에 당첨 표시 (슬롯 13 - 2번째 줄 중앙)
        val highlightItem = ItemStack(HIGHLIGHT_MATERIAL)
        val meta = highlightItem.itemMeta
        meta?.setDisplayName("§e§l▲ 당첨 ▲")
        meta?.lore = listOf("§7위쪽 아이템이 당첨됩니다!")
        highlightItem.itemMeta = meta
        inventory.setItem(13, highlightItem) // 슬롯 4 아래 (화살표가 위를 가리킴)

        // 중앙에 시작 버튼 아이템 배치
        val centerItem = ItemStack(CENTER_INFO_MATERIAL)
        val centerMeta = centerItem.itemMeta
        centerMeta?.setDisplayName("§6§l§n룰렛 시작")
        val config = manager.getRouletteById(rouletteId)
        centerMeta?.lore = listOf(
            "§7비용: §f${config?.costAmount ?: 0}원",
            "§7아이템 종류: §f${manager.getItems(rouletteId).size}개",
            "",
            "§e§l[ 클릭하여 룰렛 시작! ]"
        )
        centerItem.itemMeta = centerMeta
        inventory.setItem(22, centerItem) // 중앙 슬롯
    }

    /**
     * 룰렛 아이템 배치 (원형)
     */
    private fun updateRouletteItems() {
        // 원형 슬롯에 아이템 배치
        for (i in CIRCLE_SLOTS.indices) {
            val cycleIndex = (currentRotation + i) % itemCycle.size
            val item = itemCycle.getOrNull(cycleIndex)
            inventory.setItem(CIRCLE_SLOTS[i], item)
        }
    }

    /**
     * 하단 정보 설정
     */
    private fun setupBottomButtons() {
        // 중앙 하단에 정보 표시 (슬롯 49는 이미 테두리로 채워져 있음)
        // 필요시 추가 정보 표시 가능
    }

    /**
     * 테두리 아이템 생성
     */
    private fun createBorderItem(): ItemStack {
        val item = ItemStack(BORDER_MATERIAL)
        val meta = item.itemMeta
        meta?.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    /**
     * 애니메이션 시작 (네더별 클릭 시 호출됨)
     */
    fun startAnimation() {
        // 이미 애니메이션 중이면 무시
        if (isAnimating) {
            player.sendMessage("§c이미 룰렛이 돌아가고 있습니다!")
            return
        }

        // 당첨 아이템 결정
        winningItem = manager.selectRandomItem(rouletteId)
        if (winningItem == null) {
            player.sendMessage("§c룰렛에 등록된 아이템이 없습니다.")
            return
        }

        // 확률 계산
        val totalWeight = manager.getItems(rouletteId).sumOf { it.weight }
        val probability = if (totalWeight > 0.0) {
            (winningItem!!.weight / totalWeight) * 100.0
        } else {
            0.0
        }

        // 즉시 DB에 히스토리 기록 (비용 차감 직후)
        val config = manager.getRouletteById(rouletteId)
        val costPaid = config?.costAmount ?: 0.0
        manager.saveHistory(
            rouletteId = rouletteId,
            playerUuid = player.uniqueId.toString(),
            playerName = player.name,
            itemId = winningItem!!.id,
            itemProvider = winningItem!!.itemProvider.name,
            itemIdentifier = winningItem!!.itemIdentifier,
            costPaid = costPaid,
            probability = probability
        )

        // 아이템 순환 리스트 생성
        createItemCycle()

        // 중앙 아이템을 "돌아가는 중" 표시로 변경
        val centerItem = ItemStack(CENTER_INFO_MATERIAL)
        val centerMeta = centerItem.itemMeta
        centerMeta?.setDisplayName("§6§l룰렛 돌아가는 중...")
        centerMeta?.lore = listOf(
            "",
            "§e잠시만 기다려주세요!",
            ""
        )
        centerItem.itemMeta = centerMeta
        inventory.setItem(22, centerItem)

        isAnimating = true
        currentTick = 0
        currentRotation = 0

        animationTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (currentTick >= totalDuration) {
                // 애니메이션 종료
                stopAnimation()
                return@Runnable
            }

            // 속도 계산 (처음엔 빠르게, 점점 느려짐)
            val progress = currentTick.toDouble() / totalDuration
            val shouldMove = when {
                progress < 0.3 -> currentTick % 1 == 0  // 처음 30%: 매 틱마다 이동
                progress < 0.5 -> currentTick % 2 == 0  // 30-50%: 2틱마다 이동
                progress < 0.7 -> currentTick % 3 == 0  // 50-70%: 3틱마다 이동
                progress < 0.85 -> currentTick % 5 == 0 // 70-85%: 5틱마다 이동
                progress < 0.95 -> currentTick % 8 == 0 // 85-95%: 8틱마다 이동
                else -> currentTick % 12 == 0           // 95-100%: 12틱마다 이동
            }

            if (shouldMove) {
                currentRotation++
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

        // 파티클 효과
        player.spawnParticle(Particle.FIREWORK, player.location.add(0.0, 2.0, 0.0), 50, 0.5, 0.5, 0.5, 0.1)

        // 당첨 사운드
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        // 2초 후 아이템 지급 및 GUI 닫기
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            giveWinningItem()
            player.closeInventory()
        }, 40L)
    }

    /**
     * 최종 당첨 아이템 강조 표시
     */
    private fun showWinningItem() {
        val winItem = winningItem?.toItemStack() ?: return

        // 당첨 아이템에 반짝이는 효과 추가
        val meta = winItem.itemMeta
        val originalLore = meta?.lore?.toMutableList() ?: mutableListOf()
        originalLore.add("")
        originalLore.add("§e§l★ 당첨! ★")
        originalLore.add("")
        meta?.lore = originalLore
        winItem.itemMeta = meta

        // 12시 방향(슬롯 4)에 당첨 아이템 배치
        inventory.setItem(winningSlot, winItem)

        // 주변 원형 슬롯을 비워서 당첨 아이템 강조
        for (i in 1 until CIRCLE_SLOTS.size) {
            inventory.setItem(CIRCLE_SLOTS[i], null)
        }

        // 플레이어에게 메시지 전송
        val itemName = winningItem?.itemDisplayName ?: winItem.type.name
        player.sendMessage("§e§l[ 룰렛 ] §a당첨! §f$itemName §ax${winItem.amount}")
    }

    /**
     * 당첨 아이템 지급
     */
    private fun giveWinningItem() {
        val winItem = winningItem?.toItemStack() ?: return

        // 인벤토리에 공간이 있는지 확인
        val emptySlot = player.inventory.firstEmpty()
        if (emptySlot == -1) {
            player.sendMessage("§c인벤토리에 공간이 없어 아이템이 바닥에 떨어졌습니다!")
            player.world.dropItem(player.location, winItem)
        } else {
            player.inventory.addItem(winItem)
        }

        // 히스토리는 이미 네더별 클릭 시점에 기록되었으므로 여기서는 생략
    }

    /**
     * GUI가 닫힐 때 처리 (애니메이션 중이면 계속 진행)
     */
    fun onClose() {
        // 애니메이션 중이면 GUI만 닫고 애니메이션은 계속 진행
        // NPC를 다시 우클릭하면 진행 중인 화면을 다시 볼 수 있음
    }

    /**
     * 강제로 애니메이션 중지 및 즉시 당첨 처리 (플러그인 비활성화 시 등)
     */
    fun forceStop() {
        if (isAnimating) {
            animationTask?.cancel()
            animationTask = null
            isAnimating = false

            // 당첨 아이템 즉시 지급 (히스토리는 이미 네더별 클릭 시 기록됨)
            val winItem = winningItem?.toItemStack()
            if (winItem != null) {
                // 플레이어가 온라인인지 확인
                if (player.isOnline) {
                    // 인벤토리에 공간이 있는지 확인
                    val emptySlot = player.inventory.firstEmpty()
                    if (emptySlot == -1) {
                        player.sendMessage("§c[룰렛] 서버 리로드로 인해 룰렛이 중단되었습니다.")
                        player.sendMessage("§c인벤토리에 공간이 없어 아이템이 바닥에 떨어졌습니다!")
                        player.world.dropItem(player.location, winItem)
                    } else {
                        player.inventory.addItem(winItem)
                        player.sendMessage("§e[룰렛] 서버 리로드로 인해 룰렛이 중단되었습니다.")
                        val itemName = winningItem?.itemDisplayName ?: winItem.type.name
                        player.sendMessage("§a당첨 아이템이 지급되었습니다! §f$itemName §ax${winItem.amount}")
                    }
                }
            }
        }
    }

    /**
     * 현재 애니메이션 중인지 확인
     */
    fun isAnimating(): Boolean = isAnimating

    /**
     * 인벤토리 가져오기 (GUI를 다시 열기 위해)
     */
    fun getInventory(): Inventory = inventory

    /**
     * 룰렛 ID 가져오기
     */
    fun getRouletteId(): Int = rouletteId
}

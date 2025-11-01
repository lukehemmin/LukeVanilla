package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

/**
 * 랜덤 스크롤 룰렛 시스템
 * 
 * Nexo 아이템을 우클릭하면 3행 GUI에서 룰렛이 작동하여
 * 랜덤으로 아이템을 지급하는 시스템
 * 
 * DB 기반으로 작동하며 /랜덤스크롤 리로드 명령어로 데이터 갱신 가능
 */
class RandomScrollRoulette(
    private val plugin: JavaPlugin,
    private val database: Database
) : Listener, CommandExecutor, TabCompleter {

    // 룰렛 진행 중인 플레이어 및 GUI 추적
    private val activeRoulettes = mutableSetOf<Player>()
    private val playerGuis = mutableMapOf<Player, Inventory>()
    private val playerScrollIds = mutableMapOf<Player, String>() // 플레이어별 사용 중인 스크롤 ID 추적

    // 스크롤 설정 캐시 (scroll_id -> display_name)
    private var scrollConfigs = mutableMapOf<String, ScrollConfig>()
    
    // 스크롤 보상 캐시 (scroll_id -> List<RewardItem>)
    private var scrollRewards = mutableMapOf<String, List<RewardItem>>()

    init {
        // 초기 데이터 로드
        loadScrollDataFromDB()
    }

    /**
     * DB에서 스크롤 데이터 로드
     */
    fun loadScrollDataFromDB() {
        scrollConfigs.clear()
        scrollRewards.clear()

        database.getConnection().use { connection ->
            // 1. 스크롤 설정 로드
            val configQuery = "SELECT scroll_id, display_name, enabled FROM random_scroll_config WHERE enabled = 1"
            val configStmt = connection.prepareStatement(configQuery)
            val configRs = configStmt.executeQuery()

            while (configRs.next()) {
                val scrollId = configRs.getString("scroll_id")
                val displayName = configRs.getString("display_name")
                val enabled = configRs.getBoolean("enabled")
                
                scrollConfigs[scrollId] = ScrollConfig(scrollId, displayName, enabled)
            }
            configRs.close()
            configStmt.close()

            // 2. 보상 아이템 로드
            val rewardQuery = """
                SELECT scroll_id, item_provider, item_code, display_name, probability 
                FROM random_scroll_rewards 
                ORDER BY scroll_id, probability DESC
            """.trimIndent()
            val rewardStmt = connection.prepareStatement(rewardQuery)
            val rewardRs = rewardStmt.executeQuery()

            val tempRewards = mutableMapOf<String, MutableList<RewardItem>>()
            while (rewardRs.next()) {
                val scrollId = rewardRs.getString("scroll_id")
                val itemProvider = rewardRs.getString("item_provider")
                val itemCode = rewardRs.getString("item_code")
                val displayName = rewardRs.getString("display_name")
                val probability = rewardRs.getDouble("probability")

                val rewardItem = RewardItem(itemProvider, itemCode, displayName, probability)
                tempRewards.getOrPut(scrollId) { mutableListOf() }.add(rewardItem)
            }
            rewardRs.close()
            rewardStmt.close()

            scrollRewards.putAll(tempRewards)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // 우클릭이 아니면 무시
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = event.item ?: return

        // Nexo 아이템 ID 확인
        val itemId = NexoItems.idFromItem(item) ?: return

        // 등록된 스크롤이 아니면 무시
        if (!scrollRewards.containsKey(itemId)) return

        // 이미 룰렛이 진행 중이면 무시
        if (activeRoulettes.contains(player)) {
            player.sendMessage(
                Component.text("이미 룰렛이 진행 중입니다!", NamedTextColor.RED)
            )
            return
        }

        event.isCancelled = true

        // 아이템 개수 감소
        if (item.amount > 1) {
            item.amount--
        } else {
            player.inventory.setItemInMainHand(null)
        }

        // 룰렛 시작
        startRoulette(player, itemId)
    }

    /**
     * 룰렛 시작
     */
    private fun startRoulette(player: Player, scrollId: String) {
        activeRoulettes.add(player)
        playerScrollIds[player] = scrollId // 스크롤 ID 저장

        val rewards = scrollRewards[scrollId] ?: return
        val selectedReward = selectRewardByProbability(rewards)

        // GUI 생성
        val gui = createRouletteGUI(player, scrollId)
        
        // 플레이어별 GUI 저장
        playerGuis[player] = gui

        // GUI 오픈
        player.openInventory(gui)

        // 룰렛 애니메이션 시작
        startRouletteAnimation(player, gui, rewards, selectedReward)
    }

    /**
     * 3행 룰렛 GUI 생성
     */
    private fun createRouletteGUI(player: Player, scrollId: String): Inventory {
        val config = scrollConfigs[scrollId]
        val title = config?.displayName ?: "랜덤 룰렛"

        val gui = Bukkit.createInventory(null, 27, Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD))

        // 1행과 3행을 유리판으로 채우기
        val glassPane = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" "))
            }
        }

        for (i in 0..8) {
            gui.setItem(i, glassPane) // 1행
            gui.setItem(i + 18, glassPane) // 3행
        }

        return gui
    }

    /**
     * 룰렛 애니메이션 실행
     */
    private fun startRouletteAnimation(
        player: Player,
        gui: Inventory,
        rewards: List<RewardItem>,
        finalReward: RewardItem
    ) {
        var tickCount = 0
        val maxTicks = 60 // 3초 (60 ticks)
        val slot13 = 13 // 2행 중앙 슬롯 (당첨 위치)

        object : BukkitRunnable() {
            override fun run() {
                // 플레이어가 오프라인이면 취소
                if (!player.isOnline) {
                    activeRoulettes.remove(player)
                    playerGuis.remove(player)
                    playerScrollIds.remove(player)
                    cancel()
                    return
                }

                // GUI가 닫혔어도 계속 진행 (InventoryCloseEvent에서 다시 열어줌)
                
                tickCount++

                // 속도 조절 (시간이 지날수록 느려짐)
                val speed = when {
                    tickCount < 20 -> 2 // 빠르게
                    tickCount < 40 -> 4 // 중간
                    tickCount < 55 -> 6 // 느리게
                    else -> 8 // 매우 느리게
                }

                // 지정된 속도마다만 이동
                if (tickCount % speed != 0) return

                // 2행 아이템들을 오른쪽으로 이동
                shiftItemsRight(gui, rewards, tickCount >= maxTicks, finalReward)

                // 사운드 효과
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)

                // 애니메이션 종료
                if (tickCount >= maxTicks) {
                    finishRoulette(player, gui, finalReward)
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    /**
     * 2행 아이템을 오른쪽으로 이동
     */
    private fun shiftItemsRight(
        gui: Inventory,
        rewards: List<RewardItem>,
        isFinal: Boolean,
        finalReward: RewardItem
    ) {
        // 2행 슬롯 (9~17)
        val row2Start = 9
        val row2End = 17

        // 기존 아이템들을 오른쪽으로 이동
        val temp = gui.getItem(row2End)
        for (i in row2End downTo row2Start + 1) {
            gui.setItem(i, gui.getItem(i - 1))
        }

        // 새로운 아이템을 왼쪽 끝에 추가
        val newItem = if (isFinal) {
            // 마지막에는 당첨 아이템 배치
            createDisplayItem(finalReward)
        } else {
            // 랜덤 아이템 표시
            createDisplayItem(rewards.random())
        }

        gui.setItem(row2Start, newItem)
    }

    /**
     * 표시용 아이템 생성
     */
    private fun createDisplayItem(reward: RewardItem): ItemStack {
        val itemStack = when (reward.itemProvider.uppercase()) {
            "NEXO" -> {
                val nexoItem = NexoItems.itemFromId(reward.itemCode)
                nexoItem?.build() ?: ItemStack(Material.PAPER)
            }
            "VANILLA" -> {
                try {
                    ItemStack(Material.valueOf(reward.itemCode.uppercase()))
                } catch (e: IllegalArgumentException) {
                    ItemStack(Material.PAPER)
                }
            }
            else -> ItemStack(Material.PAPER)
        }
        
        itemStack.editMeta { meta ->
            meta.displayName(
                Component.text(reward.displayName, NamedTextColor.YELLOW, TextDecoration.BOLD)
            )
        }
        
        return itemStack
    }

    /**
     * 룰렛 종료 및 보상 지급
     */
    private fun finishRoulette(player: Player, gui: Inventory, reward: RewardItem) {
        // DB에 히스토리 기록
        savePlayHistory(player, reward)
        
        // 당첨 사운드
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        // 당첨 메시지
        player.sendMessage(
            Component.text("━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD)
        )
        player.sendMessage(
            Component.text("  🎉 ", NamedTextColor.YELLOW)
                .append(Component.text("당첨!", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" 🎉", NamedTextColor.YELLOW))
        )
        player.sendMessage(
            Component.text("  ➤ ", NamedTextColor.GRAY)
                .append(Component.text(reward.displayName, NamedTextColor.YELLOW, TextDecoration.BOLD))
        )
        player.sendMessage(
            Component.text("━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD)
        )

        // 보상 아이템 지급
        val rewardItem = when (reward.itemProvider.uppercase()) {
            "NEXO" -> {
                val nexoItem = NexoItems.itemFromId(reward.itemCode)
                nexoItem?.build()
            }
            "VANILLA" -> {
                try {
                    ItemStack(Material.valueOf(reward.itemCode.uppercase()))
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            else -> null
        }
        
        if (rewardItem != null) {
            // 인벤토리에 공간이 있는지 확인
            if (player.inventory.firstEmpty() != -1) {
                player.inventory.addItem(rewardItem)
            } else {
                // 인벤토리가 가득 차면 드롭
                player.world.dropItem(player.location, rewardItem)
                player.sendMessage(
                    Component.text("인벤토리가 가득 차서 아이템이 바닥에 떨어졌습니다!", NamedTextColor.RED)
                )
            }
        } else {
            player.sendMessage(
                Component.text("아이템을 지급하는 중 오류가 발생했습니다!", NamedTextColor.RED)
            )
        }

        // 1초 후 GUI 닫기
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.openInventory.topInventory.equals(gui)) {
                player.closeInventory()
            }
            activeRoulettes.remove(player)
            playerGuis.remove(player) // GUI 맵에서도 제거
            playerScrollIds.remove(player) // 스크롤 ID도 제거
        }, 20L)
    }

    /**
     * 플레이 히스토리를 DB에 저장
     */
    private fun savePlayHistory(player: Player, reward: RewardItem) {
        try {
            val scrollId = playerScrollIds[player] ?: return
            val scrollConfig = scrollConfigs[scrollId] ?: return
            val allRewards = scrollRewards[scrollId] ?: return
            
            val totalWeight = allRewards.sumOf { it.probability }
            val actualChance = (reward.probability / totalWeight) * 100.0
            
            database.getConnection().use { connection ->
                val insertQuery = """
                    INSERT INTO random_scroll_history 
                    (player_uuid, player_name, scroll_id, scroll_name, 
                     reward_provider, reward_code, reward_name, probability, 
                     total_weight, actual_chance) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                val stmt = connection.prepareStatement(insertQuery)
                stmt.setString(1, player.uniqueId.toString())
                stmt.setString(2, player.name)
                stmt.setString(3, scrollId)
                stmt.setString(4, scrollConfig.displayName)
                stmt.setString(5, reward.itemProvider)
                stmt.setString(6, reward.itemCode)
                stmt.setString(7, reward.displayName)
                stmt.setDouble(8, reward.probability)
                stmt.setDouble(9, totalWeight)
                stmt.setDouble(10, actualChance)
                stmt.executeUpdate()
                stmt.close()
            }
        } catch (e: Exception) {
            plugin.logger.warning("랜덤 스크롤 히스토리 저장 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 플레이 히스토리를 DB에 저장
     */
    private fun savePlayHistory(player: Player, reward: RewardItem) {
        try {
            database.getConnection().use { connection ->
                // 사용한 스크롤 정보 가져오기
                val scrollId = playerGuis[player]?.let { gui ->
                    scrollConfigs.entries.find { it.value.displayName == gui.viewers.firstOrNull()?.let { 
                        (it as? Player)?.openInventory?.title()?.let { title ->
                            // Title에서 스크롤 이름 추출하여 매칭
                            scrollConfigs.values.find { config -> 
                                gui.viewers.isNotEmpty() 
                            }?.scrollId
                        }
                    } }?.key
                }
                
                // 현재 진행 중인 스크롤의 모든 보상 가져오기 (전체 확률 계산용)
                val allRewards = scrollRewards.entries.find { entry ->
                    entry.value.contains(reward)
                }?.let { it.key to it.value } ?: return@use
                
                val currentScrollId = allRewards.first
                val currentRewards = allRewards.second
                val totalWeight = currentRewards.sumOf { it.probability }
                val actualChance = (reward.probability / totalWeight) * 100.0
                
                val scrollConfig = scrollConfigs[currentScrollId]
                
                val insertQuery = """
                    INSERT INTO random_scroll_history 
                    (player_uuid, player_name, scroll_id, scroll_name, 
                     reward_provider, reward_code, reward_name, probability, 
                     total_weight, actual_chance) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                val stmt = connection.prepareStatement(insertQuery)
                stmt.setString(1, player.uniqueId.toString())
                stmt.setString(2, player.name)
                stmt.setString(3, currentScrollId)
                stmt.setString(4, scrollConfig?.displayName ?: "알 수 없음")
                stmt.setString(5, reward.itemProvider)
                stmt.setString(6, reward.itemCode)
                stmt.setString(7, reward.displayName)
                stmt.setDouble(8, reward.probability)
                stmt.setDouble(9, totalWeight)
                stmt.setDouble(10, actualChance)
                stmt.executeUpdate()
                stmt.close()
            }
        } catch (e: Exception) {
            plugin.logger.warning("랜덤 스크롤 히스토리 저장 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 확률에 따라 보상 선택
     */
    private fun selectRewardByProbability(rewards: List<RewardItem>): RewardItem {
        // 전체 확률 합계 계산
        val totalWeight = rewards.sumOf { it.probability }
        
        // 랜덤 값 생성 (0.0 ~ totalWeight)
        val randomValue = Random.nextDouble(totalWeight)
        
        // 누적 확률로 아이템 선택
        var cumulativeWeight = 0.0
        for (reward in rewards) {
            cumulativeWeight += reward.probability
            if (randomValue <= cumulativeWeight) {
                return reward
            }
        }
        
        // 만약을 위한 기본 반환 (첫 번째 아이템)
        return rewards.first()
    }

    /**
     * GUI 클릭 방지
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // 룰렛 진행 중인 플레이어의 GUI 클릭 방지
        if (activeRoulettes.contains(player)) {
            event.isCancelled = true
        }
    }

    /**
     * GUI 닫기 방지 (룰렛 진행 중)
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        
        // 룰렛 진행 중인 플레이어가 GUI를 닫으려고 하면 다시 열기
        if (activeRoulettes.contains(player)) {
            // 1 tick 후에 다시 GUI 열기 (즉시 열면 무한 루프 발생)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                // 여전히 룰렛이 진행 중이면 저장된 GUI 다시 열기
                if (activeRoulettes.contains(player)) {
                    val gui = playerGuis[player]
                    if (gui != null) {
                        player.openInventory(gui)
                        player.sendMessage(
                            Component.text("룰렛이 진행 중입니다! 잠시만 기다려주세요.", NamedTextColor.YELLOW)
                        )
                    }
                }
            }, 1L)
        }
    }

    /**
     * 명령어 처리
     */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("사용법: /랜덤스크롤 리로드", NamedTextColor.RED))
            return true
        }

        when (args[0].lowercase()) {
            "리로드", "reload" -> {
                if (!sender.hasPermission("lukevanilla.randomscroll.reload")) {
                    sender.sendMessage(Component.text("권한이 없습니다!", NamedTextColor.RED))
                    return true
                }

                try {
                    loadScrollDataFromDB()
                    sender.sendMessage(
                        Component.text("━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD)
                    )
                    sender.sendMessage(
                        Component.text("  ✓ ", NamedTextColor.GREEN)
                            .append(Component.text("랜덤 스크롤 데이터 리로드 완료!", NamedTextColor.GOLD, TextDecoration.BOLD))
                    )
                    sender.sendMessage(
                        Component.text("  • 스크롤 설정: ${scrollConfigs.size}개", NamedTextColor.GRAY)
                    )
                    sender.sendMessage(
                        Component.text("  • 보상 아이템: ${scrollRewards.values.sumOf { it.size }}개", NamedTextColor.GRAY)
                    )
                    sender.sendMessage(
                        Component.text("━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD)
                    )
                } catch (e: Exception) {
                    sender.sendMessage(
                        Component.text("리로드 중 오류 발생: ${e.message}", NamedTextColor.RED)
                    )
                    e.printStackTrace()
                }
            }
            else -> {
                sender.sendMessage(Component.text("알 수 없는 명령어입니다. /랜덤스크롤 리로드", NamedTextColor.RED))
            }
        }

        return true
    }

    /**
     * 탭 완성
     */
    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("리로드", "reload").filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }

    /**
     * 스크롤 설정 데이터 클래스
     */
    data class ScrollConfig(
        val scrollId: String,
        val displayName: String,
        val enabled: Boolean
    )

    /**
     * 보상 아이템 데이터 클래스
     */
    data class RewardItem(
        val itemProvider: String,  // NEXO, VANILLA 등
        val itemCode: String,       // 아이템 코드
        val displayName: String,    // 표시될 아이템 이름
        val probability: Double     // 확률 (가중치)
    )
}

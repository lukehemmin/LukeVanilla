package com.lukehemmin.lukeVanilla.System.Items

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.metadata.FixedMetadataValue

/**
 * 특정 아이템들을 킬 수에 따라 업그레이드하는 시스템을 관리하는 클래스
 * - 크리스마스 검 (merry_christmas_sword) -> 크리스마스 대검 (merry_christmas_greatsword): 5,000킬
 * - 발렌타인 데이 검 (valentine_sword) -> 발렌타인 데이 대검 (valentine_greatsword): 15,000킬
 */
class UpgradeItem(private val plugin: Main) : Listener, CommandExecutor {
    
    private val statsSystem = plugin.statsSystem
    private val AUTO_UPGRADE_KEY = NamespacedKey(plugin, "lukestats_auto_upgrade")
    private val ITEM_ID_KEY = NamespacedKey(plugin, "lukestats_item_id")
    
    // 중복 카운트 방지를 위한 메타데이터 키
    private val KILL_PROCESSED_META = "luke_kill_processed"
    
    // 업그레이드 아이템 정보를 저장하는 클래스
    data class UpgradeInfo(
        val sourceItemId: String,      // 원본 아이템 ID
        val targetItemId: String,      // 업그레이드 아이템 ID
        val killsRequired: Int,        // 필요한 킬 수
        val upgradeMessage: String     // 업그레이드 메시지
    )
    
    // 이미 업그레이드된 아이템 목록
    private val upgradedItems = setOf(
        "merry_christmas_greatsword",
        "valentine_greatsword",
        "firework_greatsword"
    )
    
    // 업그레이드 가능한 아이템 맵
    private val upgradeMap = mapOf(
        "merry_christmas_sword" to UpgradeInfo(
            "merry_christmas_sword", 
            "merry_christmas_greatsword", 
            5000,
            "${ChatColor.GREEN}${ChatColor.BOLD}크리스마스 검이 대검으로 바뀌었어요!"
        ),
        "valentine_sword" to UpgradeInfo(
            "valentine_sword", 
            "valentine_greatsword",
            15000,
            "${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}발렌타인 데이 검이 대검으로 바뀌었어요!"
        ),
        "firework_sword" to UpgradeInfo(
            "firework_sword",
            "firework_greatsword",
            5000,
            "${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}폭죽 검이 대검으로 바뀌었어요!"
        )
    )
    
    /**
     * Nexo 아이템 관련 작업을 위한 헬퍼 객체
     * 리플렉션 로직을 분리하여 코드 가독성 향상
     */
    private object NexoHelper {
        /**
         * Nexo ID로 아이템 생성
         * @param id Nexo 아이템 ID
         * @param amount 생성할 아이템 수량
         * @return 생성된 아이템 또는 null (실패시)
         */
        fun createItemFromId(id: String, amount: Int = 1): ItemStack? {
            // 1. 먼저 표준 API로 시도
            NexoItems.itemFromId(id)?.build()?.let { return it }
            
            // 2. 실패 시 리플렉션으로 시도
            try {
                val nexoPlugin = Bukkit.getServer().pluginManager.getPlugin("Nexo") ?: return null
                
                // Nexo 인스턴스 가져오기
                val nexoInstance = nexoPlugin.javaClass.getDeclaredMethod("getNexoInstance").apply {
                    isAccessible = true 
                }.invoke(nexoPlugin)
                
                // 아이템 매니저 가져오기
                val itemManager = nexoInstance.javaClass.getDeclaredMethod("getItemManager").apply {
                    isAccessible = true
                }.invoke(nexoInstance)
                
                // 아이템 생성 메서드 호출
                return itemManager.javaClass.getDeclaredMethod(
                    "createItem", 
                    String::class.java, 
                    Int::class.java
                ).apply {
                    isAccessible = true
                }.invoke(itemManager, id, amount) as? ItemStack
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[NexoHelper] 아이템 생성 중 오류: ${e.message}")
                return null
            }
        }
    }
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        plugin.logger.info("아이템 업그레이드 시스템이 초기화되었습니다.")

        // 명령어 등록
        plugin.getCommand("자동성장켜기")?.setExecutor(this)
        plugin.getCommand("자동성장끄기")?.setExecutor(this)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}${ChatColor.BOLD}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender
        val item = player.inventory.itemInMainHand

        if (item.type.isAir) {
            player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}명령어를 사용하려면 손에 아이템을 들고 있어야 합니다.")
            return true
        }

        val itemId = getItemId(item)
        if(itemId == null || !upgradeMap.containsKey(itemId)) {
            player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}이 아이템은 업그레이드할 수 없습니다.")
            return true
        }

        when (command.name.lowercase()) {
            "자동성장켜기" -> {
                setAutoUpgrade(item, true)
                player.sendMessage("${ChatColor.GREEN}${ChatColor.BOLD}자동 업그레이드가 활성화되었습니다.")
                return true
            }
            "자동성장끄기" -> {
                setAutoUpgrade(item, false)
                player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}자동 업그레이드가 비활성화되었습니다.")
                return true
            }
        }
        return false
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true) // 우선순위를 NORMAL로 변경
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val item = killer.inventory.itemInMainHand
        
        // 이미 처리된 이벤트인지 확인
        if (event.entity.hasMetadata(KILL_PROCESSED_META)) {
            return
        }
        
        // 메타데이터 설정하여 중복 처리 방지
        event.entity.setMetadata(KILL_PROCESSED_META, FixedMetadataValue(plugin, true))
        
        val itemId = getItemId(item)
        
        // Nexo 아이템이 아니면 무시 (타 시스템에서 처리)
        if (itemId == null) return
        
        // Nexo 아이템의 킬 카운트 증가 (모든 Nexo 아이템에 적용)
        statsSystem.getStatsManager().incrementMobsKilled(item)
        
        // 업그레이드 가능한 아이템인 경우 추가 처리
        val upgradeInfo = upgradeMap[itemId]
        if (upgradeInfo != null) {
            // 현재 몹 킬 수 가져오기
            val kills = statsSystem.getStatsManager().getMobsKilled(item)
            
            // 자동 성장 기능이 활성화되어 있는지 확인
            if (isAutoUpgradeEnabled(item)) {
                // 업그레이드 조건 확인
                if (kills >= upgradeInfo.killsRequired) {
                    upgradeItem(killer, item, upgradeInfo)
                }
            } else {
                // 자동 성장이 비활성화되어 있지만 조건 충족 시 메시지 표시
                if (kills == upgradeInfo.killsRequired) {
                    killer.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}아이템이 성장 조건을 충족했습니다! ${ChatColor.YELLOW}${ChatColor.BOLD}/자동성장켜기${ChatColor.GOLD}${ChatColor.BOLD} 명령어를 사용하여 성장시킬 수 있습니다.")
                } else if (kills > upgradeInfo.killsRequired && kills % 100 == 0) {
                    // 100킬마다 한 번씩 알림 메시지 표시
                    killer.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}아이템이 이미 성장 조건을 충족했습니다! ${ChatColor.YELLOW}${ChatColor.BOLD}/자동성장켜기${ChatColor.GOLD}${ChatColor.BOLD} 명령어를 사용하여 성장시킬 수 있습니다.")
                }
            }
        }
    }
    
    /**
     * 아이템 ID를 가져오는 메서드 (NBT 태그에서 먼저 확인하고, 없으면 Nexo API 사용)
     */
    private fun getItemId(item: ItemStack): String? {
        val meta = item.itemMeta ?: return NexoItems.idFromItem(item)
        
        // NBT 태그에서 먼저 확인
        if (meta.persistentDataContainer.has(ITEM_ID_KEY, PersistentDataType.STRING)) {
            return meta.persistentDataContainer.get(ITEM_ID_KEY, PersistentDataType.STRING)
        }
        
        // NBT 태그에 없으면 Nexo API 사용
        val nexoId = NexoItems.idFromItem(item)
        
        // Nexo API로 ID를 찾은 경우 NBT 태그에 저장
        if (nexoId != null) {
            meta.persistentDataContainer.set(ITEM_ID_KEY, PersistentDataType.STRING, nexoId)
            item.itemMeta = meta
        }
        
        return nexoId
    }
    
    /**
     * 아이템 업그레이드 과정의 메인 메서드
     * 여러 작은 메서드로 나누어 책임 분리
     */
    private fun upgradeItem(player: Player, originalItem: ItemStack, upgradeInfo: UpgradeInfo) {
        // 1. 업그레이드 전 유효성 검사
        if (!validateUpgrade(player, originalItem, upgradeInfo)) return
        
        // 2. 업그레이드 아이템 생성
        val upgradedItem = createUpgradedItem(originalItem, upgradeInfo) ?: return
        
        // 3. 플레이어 인벤토리에 적용
        player.inventory.setItemInMainHand(upgradedItem)
        
        // 4. 업그레이드 완료 알림
        notifyUpgrade(player, upgradeInfo)
        
        plugin.logger.info("[UpgradeItem] ${player.name}의 ${upgradeInfo.sourceItemId}가 ${upgradeInfo.targetItemId}로 업그레이드되었습니다.")
    }
    
    /**
     * 업그레이드 전 유효성 검사
     */
    private fun validateUpgrade(player: Player, originalItem: ItemStack, upgradeInfo: UpgradeInfo): Boolean {
        val originalItemId = getItemId(originalItem)
        
        // 이미 업그레이드된 아이템인지 확인
        if (originalItemId != null && isAlreadyUpgraded(originalItemId)) {
            plugin.logger.info("[UpgradeItem] 이미 업그레이드된 아이템입니다: ${player.name}, ${originalItemId}")
            return false
        }
        
        // 올바른 소스 아이템인지 확인
        if (originalItemId != upgradeInfo.sourceItemId) {
            plugin.logger.info("[UpgradeItem] 아이템 ID 불일치: ${originalItemId} != ${upgradeInfo.sourceItemId}")
            return false
        }
        
        return true
    }
    
    /**
     * 새 업그레이드 아이템 생성
     */
    private fun createUpgradedItem(originalItem: ItemStack, upgradeInfo: UpgradeInfo): ItemStack? {
        // Nexo 헬퍼를 사용하여 새 아이템 생성
        val newItem = NexoHelper.createItemFromId(upgradeInfo.targetItemId) ?: return null
        
        // 기존 아이템의 메타와 새 아이템의 메타 가져오기
        val originalMeta = originalItem.itemMeta ?: return null
        val newMeta = newItem.itemMeta ?: return null
        
        // 인첸트 복사
        originalMeta.enchants.forEach { (enchant, level) ->
            newMeta.addEnchant(enchant, level, true)
        }
        
        // NBT 데이터 복사
        copyNBTData(originalMeta, newMeta)
        
        // 업그레이드된 아이템 ID를 NBT에 저장
        newMeta.persistentDataContainer.set(ITEM_ID_KEY, PersistentDataType.STRING, upgradeInfo.targetItemId)
        
        // 메타 적용
        newItem.itemMeta = newMeta
        
        // 검증
        if (getItemId(newItem) != upgradeInfo.targetItemId) {
            plugin.logger.warning("[UpgradeItem] 아이템 ID가 올바르게 설정되지 않았습니다.")
            return null
        }
        
        return newItem
    }
    
    /**
     * 업그레이드 알림 메시지 전송
     */
    private fun notifyUpgrade(player: Player, upgradeInfo: UpgradeInfo) {
        // 플레이어에게 업그레이드 메시지 전송
        player.sendMessage(upgradeInfo.upgradeMessage)
        
        // 서버 전체에 업그레이드 알림
        val itemDisplayName = getItemDisplayName(upgradeInfo.targetItemId)
        Bukkit.broadcastMessage(
            "${ChatColor.GOLD}${ChatColor.BOLD}${player.name}${ChatColor.YELLOW}${ChatColor.BOLD} 님이 " +
            "${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}${itemDisplayName}${ChatColor.YELLOW}${ChatColor.BOLD}을(를) 얻었습니다!"
        )
    }
    
    /**
     * 아이템 ID에 따른 표시 이름을 반환하는 메서드
     */
    private fun getItemDisplayName(itemId: String): String {
        return when (itemId) {
            "merry_christmas_greatsword" -> "크리스마스 대검"
            "valentine_greatsword" -> "발렌타인 대검"
            "firework_greatsword" -> "폭죽 대검"
            else -> itemId
        }
    }
    
    /**
     * 아이템이 이미 최종 업그레이드 상태인지 확인하는 메서드
     */
    private fun isAlreadyUpgraded(itemId: String): Boolean {
        return upgradedItems.contains(itemId)
    }
    
    /**
     * 아이템의 NBT 데이터를 복사하는 메서드 - 단순화된 버전
     */
    private fun copyNBTData(source: ItemMeta, target: ItemMeta) {
        // 복사에서 제외할 키
        val excludedKeys = setOf("nexo:id")
        
        // 모든 키를 순회하며 필요한 데이터 복사
        for (key in source.persistentDataContainer.keys) {
            // 제외 키 확인
            if (excludedKeys.any { excluded -> key.toString().contains(excluded) }) {
                continue
            }
            
            // 데이터 타입에 따라 복사
            copyDataByType(key, source.persistentDataContainer, target.persistentDataContainer)
        }
    }
    
    /**
     * NBT 데이터 타입을 자동 감지하여 복사
     */
    private fun copyDataByType(key: NamespacedKey, source: PersistentDataContainer, target: PersistentDataContainer) {
        try {
            // 각 데이터 타입을 명시적으로 처리
            if (source.has(key, PersistentDataType.STRING)) {
                source.get(key, PersistentDataType.STRING)?.let { 
                    target.set(key, PersistentDataType.STRING, it)
                }
                return
            }
            
            if (source.has(key, PersistentDataType.INTEGER)) {
                source.get(key, PersistentDataType.INTEGER)?.let { 
                    target.set(key, PersistentDataType.INTEGER, it)
                }
                return
            }
            
            if (source.has(key, PersistentDataType.DOUBLE)) {
                source.get(key, PersistentDataType.DOUBLE)?.let { 
                    target.set(key, PersistentDataType.DOUBLE, it)
                }
                return
            }
            
            if (source.has(key, PersistentDataType.BYTE)) {
                source.get(key, PersistentDataType.BYTE)?.let { 
                    target.set(key, PersistentDataType.BYTE, it)
                }
                return
            }
            
            if (source.has(key, PersistentDataType.LONG)) {
                source.get(key, PersistentDataType.LONG)?.let { 
                    target.set(key, PersistentDataType.LONG, it)
                }
                return
            }
            
            if (source.has(key, PersistentDataType.FLOAT)) {
                source.get(key, PersistentDataType.FLOAT)?.let { 
                    target.set(key, PersistentDataType.FLOAT, it)
                }
                return
            }
        } catch (e: Exception) {
            // 오류가 발생해도 계속 진행
            plugin.logger.warning("[UpgradeItem] NBT 데이터 복사 실패 (키: $key): ${e.message}")
        }
    }
    
    /**
     * 아이템의 자동 성장 기능 활성화 여부를 설정하는 메서드
     */
    private fun setAutoUpgrade(item: ItemStack, enabled: Boolean) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(AUTO_UPGRADE_KEY, PersistentDataType.BYTE, if (enabled) 1.toByte() else 0.toByte())
        item.itemMeta = meta
    }
    
    /**
     * 아이템의 자동 성장 기능이 활성화되어 있는지 확인하는 메서드
     */
    private fun isAutoUpgradeEnabled(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return true // 기본값은 활성화
        val container = meta.persistentDataContainer
        
        return if (container.has(AUTO_UPGRADE_KEY, PersistentDataType.BYTE)) {
            container.get(AUTO_UPGRADE_KEY, PersistentDataType.BYTE) == 1.toByte()
        } else {
            true // 데이터가 없으면 기본값은 활성화
        }
    }
}

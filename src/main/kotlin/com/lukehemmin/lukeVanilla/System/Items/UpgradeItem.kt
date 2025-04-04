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
import org.bukkit.persistence.PersistentDataType

/**
 * 특정 아이템들을 킬 수에 따라 업그레이드하는 시스템을 관리하는 클래스
 * - 크리스마스 검 (merry_christmas_sword) -> 크리스마스 대검 (merry_christmas_greatsword): 5,000킬
 * - 발렌타인 데이 검 (valentine_sword) -> 발렌타인 데이 대검 (valentine_greatsword): 15,000킬
 */
class UpgradeItem(private val plugin: Main) : Listener, CommandExecutor {
    
    private val statsSystem = plugin.statsSystem
    private val AUTO_UPGRADE_KEY = NamespacedKey(plugin, "lukestats_auto_upgrade")
    private val ITEM_ID_KEY = NamespacedKey(plugin, "lukestats_item_id")
    
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
        "valentine_greatsword"
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
        )
    )
    
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

        when (command.name.toLowerCase()) {
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
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val item = killer.inventory.itemInMainHand
        val itemId = getItemId(item)
        
        // 아이템 ID가 없으면 처리하지 않음
        if (itemId == null) return
        
        // 디버그 로그 추가
//        plugin.logger.info("[UpgradeItem] EntityDeath 이벤트 발생: 플레이어=${killer.name}, 아이템ID=${itemId}, 아이템타입=${item.type}, 이미업그레이드=${isAlreadyUpgraded(itemId)}, upgradedItems=${upgradedItems}")
        
        // 이미 업그레이드된 아이템인지 확인
        if (isAlreadyUpgraded(itemId)) {
            // 이미 업그레이드된 아이템이면 킬 수만 증가시키고 리턴
            statsSystem.getStatsManager().incrementMobsKilled(item)
//            plugin.logger.info("[UpgradeItem] 이미 업그레이드된 아이템입니다. 킬 수만 증가시키고 리턴합니다.")
            return
        }
        
        // 업그레이드 가능한 아이템인지 확인
        val upgradeInfo = upgradeMap[itemId] ?: return
        
        // 재확인: 아이템이 정말로 소스 아이템인지 확인
        if (itemId != upgradeInfo.sourceItemId) {
            // plugin.logger.info("[UpgradeItem] 아이템 ID(${itemId})가 소스 아이템 ID(${upgradeInfo.sourceItemId})와 일치하지 않습니다. 처리를 중단합니다.")
            return
        }
        
        // StatsSystem을 사용하여 몹 킬 수 증가
        statsSystem.getStatsManager().incrementMobsKilled(item)
        
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
     * 아이템을 업그레이드하는 메서드
     */
    private fun upgradeItem(player: Player, originalItem: ItemStack, upgradeInfo: UpgradeInfo) {
        val originalItemId = getItemId(originalItem)
        
        // 이미 업그레이드된 아이템인지 다시 한번 확인
        if (originalItemId != null && isAlreadyUpgraded(originalItemId)) {
            plugin.logger.info("[UpgradeItem] 이미 업그레이드된 아이템입니다. 업그레이드를 건너뜁니다: ${player.name}, ${originalItemId}")
            return
        }
        
        // 소스 아이템 ID 확인
        if (originalItemId != upgradeInfo.sourceItemId) {
            plugin.logger.info("[UpgradeItem] 아이템 ID(${originalItemId})가 소스 아이템 ID(${upgradeInfo.sourceItemId})와 일치하지 않습니다. 업그레이드를 건너뜁니다.")
            return
        }
        
        // 새 아이템 생성 (다른 방식으로 아이템 생성)
        try {
            // Nexo 플러그인에서 직접 아이템 생성 (리플렉션 사용)
            val nexoPlugin = Bukkit.getServer().pluginManager.getPlugin("Nexo")
            if (nexoPlugin != null) {
                // 리플렉션을 통해 Nexo API에 접근
                val nexoInstanceMethod = nexoPlugin.javaClass.getDeclaredMethod("getNexoInstance")
                nexoInstanceMethod.isAccessible = true
                val nexoInstance = nexoInstanceMethod.invoke(nexoPlugin)
                
                val getItemManagerMethod = nexoInstance.javaClass.getDeclaredMethod("getItemManager")
                getItemManagerMethod.isAccessible = true
                val itemManager = getItemManagerMethod.invoke(nexoInstance)
                
                val createItemMethod = itemManager.javaClass.getDeclaredMethod("createItem", String::class.java, Int::class.java)
                createItemMethod.isAccessible = true
                val newItemBuilder = createItemMethod.invoke(itemManager, upgradeInfo.targetItemId, 1) as ItemStack
                
                // 기존 아이템의 메타와 새 아이템의 메타 가져오기
                val originalMeta = originalItem.itemMeta ?: return
                val newMeta = newItemBuilder.itemMeta ?: return
                
                // 인첸트 복사
                originalMeta.enchants.forEach { (enchant, level) ->
                    newMeta.addEnchant(enchant, level, true)
                }
                
                // 원본 아이템의 통계 정보 가져오기
                val mobsKilled = statsSystem.getStatsManager().getMobsKilled(originalItem)
                val playersKilled = statsSystem.getStatsManager().getPlayersKilled(originalItem)
                val damageDealt = statsSystem.getStatsManager().getDamageDealt(originalItem)
                
                // plugin.logger.info("[UpgradeItem] 통계 정보: 몹킬=${mobsKilled}, 플레이어킬=${playersKilled}, 데미지=${damageDealt}")
                
                // NBT 데이터 복사 (StatsSystem에서 사용하는 데이터 복사)
                copyNBTData(originalMeta, newMeta)
                
                // 아이템 ID 저장 (중요: 업그레이드된 아이템의 ID를 NBT에 저장)
                newMeta.persistentDataContainer.set(ITEM_ID_KEY, PersistentDataType.STRING, upgradeInfo.targetItemId)
                
                // 업데이트된 메타 적용
                newItemBuilder.itemMeta = newMeta
                
                // 아이템 ID가 제대로 설정되었는지 확인
                val newItemId = getItemId(newItemBuilder)
                // plugin.logger.info("[UpgradeItem] 생성된 새 아이템 ID: ${newItemId}, 타겟 아이템 ID: ${upgradeInfo.targetItemId}")
                
                if (newItemId != upgradeInfo.targetItemId) {
                    plugin.logger.warning("[UpgradeItem] 아이템 ID가 올바르게 설정되지 않았습니다. 업그레이드를 건너뜁니다.")
                    return
                }
                
                // 플레이어 인벤토리에서 아이템 교체
                player.inventory.setItemInMainHand(newItemBuilder)
                
                // 업그레이드 메시지 전송
                player.sendMessage(upgradeInfo.upgradeMessage)
                plugin.logger.info("[UpgradeItem] ${player.name}의 ${upgradeInfo.sourceItemId}가 ${upgradeInfo.targetItemId}로 업그레이드되었습니다.")
                
                // 서버 전체에 업그레이드 알림 메시지 전송
                val itemDisplayName = getItemDisplayName(upgradeInfo.targetItemId)
                Bukkit.broadcastMessage("${ChatColor.GOLD}${ChatColor.BOLD}${player.name}${ChatColor.YELLOW}${ChatColor.BOLD} 님이 ${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}${itemDisplayName}${ChatColor.YELLOW}${ChatColor.BOLD}을(를) 얻었습니다!")
                
                return
            }
        } catch (e: Exception) {
            plugin.logger.severe("[UpgradeItem] 리플렉션을 통한 아이템 생성 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
        
        // 위 방법이 실패할 경우 기존 방식으로 시도
        val newItemBuilder = NexoItems.itemFromId(upgradeInfo.targetItemId)?.build() ?: run {
            plugin.logger.warning("[UpgradeItem] ${upgradeInfo.targetItemId} 아이템이 존재하지 않습니다.")
            return
        }
        
        // 기존 아이템의 메타와 새 아이템의 메타 가져오기
        val originalMeta = originalItem.itemMeta ?: return
        val newMeta = newItemBuilder.itemMeta ?: return
        
        // 인첸트 복사
        originalMeta.enchants.forEach { (enchant, level) ->
            newMeta.addEnchant(enchant, level, true)
        }
        
        // 원본 아이템의 통계 정보 가져오기
        val mobsKilled = statsSystem.getStatsManager().getMobsKilled(originalItem)
        val playersKilled = statsSystem.getStatsManager().getPlayersKilled(originalItem)
        val damageDealt = statsSystem.getStatsManager().getDamageDealt(originalItem)
        
        plugin.logger.info("[UpgradeItem] 통계 정보: 몹킬=${mobsKilled}, 플레이어킬=${playersKilled}, 데미지=${damageDealt}")
        
        // NBT 데이터 복사 (StatsSystem에서 사용하는 데이터 복사)
        copyNBTData(originalMeta, newMeta)
        
        // 아이템 ID 저장 (중요: 업그레이드된 아이템의 ID를 NBT에 저장)
        newMeta.persistentDataContainer.set(ITEM_ID_KEY, PersistentDataType.STRING, upgradeInfo.targetItemId)
        
        // 업데이트된 메타 적용
        newItemBuilder.itemMeta = newMeta
        
        // 플레이어 인벤토리에서 아이템 교체
        player.inventory.setItemInMainHand(newItemBuilder)
        
        // 업그레이드 메시지 전송
        player.sendMessage(upgradeInfo.upgradeMessage)
        plugin.logger.info("[UpgradeItem] ${player.name}의 ${upgradeInfo.sourceItemId}가 ${upgradeInfo.targetItemId}로 업그레이드되었습니다.")
        
        // 서버 전체에 업그레이드 알림 메시지 전송
        val itemDisplayName = getItemDisplayName(upgradeInfo.targetItemId)
        Bukkit.broadcastMessage("${ChatColor.GOLD}${ChatColor.BOLD}${player.name}${ChatColor.YELLOW}${ChatColor.BOLD} 님이 ${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}${itemDisplayName}${ChatColor.YELLOW}${ChatColor.BOLD}을(를) 얻었습니다!")
    }
    
    /**
     * 아이템 ID에 따른 표시 이름을 반환하는 메서드
     */
    private fun getItemDisplayName(itemId: String): String {
        return when (itemId) {
            "merry_christmas_greatsword" -> "크리스마스 대검"
            "valentine_greatsword" -> "발렌타인 대검"
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
     * 아이템의 NBT 데이터를 복사하는 메서드
     */
    private fun copyNBTData(source: ItemMeta, target: ItemMeta) {
        val sourceContainer = source.persistentDataContainer
        val targetContainer = target.persistentDataContainer
        
        // 모든 네임스페이스 키를 복사
        for (key in sourceContainer.keys) {
            // INTEGER 타입 데이터
            if (sourceContainer.has(key, PersistentDataType.INTEGER)) {
                val value = sourceContainer.get(key, PersistentDataType.INTEGER)
                if (value != null) {
                    targetContainer.set(key, PersistentDataType.INTEGER, value)
                }
            }
            
            // DOUBLE 타입 데이터
            else if (sourceContainer.has(key, PersistentDataType.DOUBLE)) {
                val value = sourceContainer.get(key, PersistentDataType.DOUBLE)
                if (value != null) {
                    targetContainer.set(key, PersistentDataType.DOUBLE, value)
                }
            }
            
            // STRING 타입 데이터
            else if (sourceContainer.has(key, PersistentDataType.STRING)) {
                val value = sourceContainer.get(key, PersistentDataType.STRING)
                if (value != null) {
                    targetContainer.set(key, PersistentDataType.STRING, value)
                }
            }
            
            // BYTE 타입 데이터 (자동 성장 설정 등)
            else if (sourceContainer.has(key, PersistentDataType.BYTE)) {
                val value = sourceContainer.get(key, PersistentDataType.BYTE)
                if (value != null) {
                    targetContainer.set(key, PersistentDataType.BYTE, value)
                }
            }
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

package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerItemMendEvent
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

/**
 * 아이템 통계 시스템의 메인 클래스
 * 이 클래스는 다음과 같은 기능을 제공합니다:
 * 1. 아이템 통계 관리자 초기화
 * 2. 이벤트 리스너 등록
 * 3. 아이템 생성 이벤트 처리 (제작, 수리 등)
 */
class StatsSystem(val plugin: Main) : Listener {
    
    // ===== 로그 설정 =====
    // 로그 활성화 여부 설정
    // true로 설정하면 로그가 활성화되고, false로 설정하면 비활성화됩니다.
    // 로그를 활성화하면 아이템 통계 관련 모든 작업이 서버 콘솔에 출력됩니다.
    // 문제 해결이 필요할 때만 true로 설정하고, 평소에는 false로 설정하는 것이 좋습니다.
    var isLoggingEnabled: Boolean = true // 여기서 true 또는 false 로 설정하세요
    // ===================
    
    // 추적할 Nexo 아이템 ID 목록
    private val trackableNexoItems = listOf(
        "plny_springset_sword", 
        "plny_springset_pickaxe", 
        "valentine_pickaxe",
        // 추가 Nexo 아이템 ID를 여기에 추가할 수 있습니다
    )
    
    // 통계 관리자 및 리스너 초기화
    private val statsManager = ItemStatsManager(plugin)
    private val statsListener = ItemStatsListener(plugin, statsManager)
    
    init {
        // 이벤트 리스너 등록
        plugin.server.pluginManager.registerEvents(statsListener, plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        
        plugin.logger.info("아이템 통계 시스템이 초기화되었습니다.")
        plugin.logger.info("로그 상태: ${if (isLoggingEnabled) "활성화됨" else "비활성화됨"}")
    }
    
    // 로그 출력 메서드 (로깅 활성화 상태일 때만 출력)
    fun logDebug(message: String) {
        if (isLoggingEnabled) {
            plugin.logger.info("[StatsSystem] $message")
        }
    }
    
    // 모루에서 아이템 수리/개조 시 통계 유지
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAnvilUse(event: PrepareAnvilEvent) {
        val result = event.result ?: return
        val inventory = event.inventory
        val firstItem = inventory.getItem(0) ?: return
        
        // 첫 번째 슬롯의 아이템에서 통계 복사
        if (isTrackableItem(result) && isTrackableItem(firstItem)) {
            copyStats(firstItem, result)
        }
    }
    
    // 경험치로 아이템 수리 시 통계 유지 (모루 사용 안 함)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemMend(event: PlayerItemMendEvent) {
        // 이 이벤트에서는 아이템이 이미 수리된 상태이므로 추가 작업 필요 없음
        // 자체적으로 같은 아이템 참조를 유지하므로 통계도 유지됨
    }
    
    // 아이템 통계 정보 복사 (아이템 수리나 합성 시 사용)
    private fun copyStats(source: ItemStack, target: ItemStack) {
        // 도구인 경우
        if (isTool(source.type) && isTool(target.type)) {
            val creator = statsManager.getCreator(source)
            val creationDate = statsManager.getCreationDate(source)
            val blocksMined = statsManager.getBlocksMined(source)
            val mobsKilled = statsManager.getMobsKilled(source)
            val playersKilled = statsManager.getPlayersKilled(source)
            val damageDealt = statsManager.getDamageDealt(source)
            
            creator?.let {
                val player = plugin.server.getPlayer(it)
                if (player != null) {
                    statsManager.initializeTool(target, player)
                    
                    // 기존 통계 복원
                    if (blocksMined > 0) {
                        for (i in 1..blocksMined) {
                            statsManager.incrementBlocksMined(target)
                        }
                    }
                    if (mobsKilled > 0) {
                        for (i in 1..mobsKilled) {
                            statsManager.incrementMobsKilled(target)
                        }
                    }
                    if (playersKilled > 0) {
                        for (i in 1..playersKilled) {
                            statsManager.incrementPlayersKilled(target)
                        }
                    }
                    if (damageDealt > 0) {
                        statsManager.addDamageDealt(target, damageDealt.toDouble())
                    }
                }
            }
        } 
        // 방어구인 경우
        else if (isArmor(source.type) && isArmor(target.type)) {
            val creator = statsManager.getCreator(source)
            val damageBlocked = statsManager.getDamageBlocked(source)
            
            creator?.let {
                val player = plugin.server.getPlayer(it)
                if (player != null) {
                    statsManager.initializeArmor(target, player)
                    
                    // 기존 통계 복원
                    if (damageBlocked > 0) {
                        statsManager.addDamageBlocked(target, damageBlocked.toDouble())
                    }
                }
            }
        }
        // 엘리트라인 경우
        else if (source.type == Material.ELYTRA && target.type == Material.ELYTRA) {
            val firstOwner = statsManager.getFirstOwner(source)
            val distanceFlown = statsManager.getDistanceFlown(source)
            
            firstOwner?.let {
                val player = plugin.server.getPlayer(it)
                if (player != null) {
                    statsManager.initializeElytra(target, player)
                    
                    // 기존 통계 복원
                    if (distanceFlown > 0) {
                        statsManager.addDistanceFlown(target, distanceFlown)
                    }
                }
            }
        }
    }
    
    // 추적 가능한 아이템인지 확인 (바닐라 아이템과 Nexo 커스텀 아이템 모두 체크)
    fun isTrackableItem(item: ItemStack): Boolean {
        return !item.type.isAir && (isTool(item.type) || isArmor(item.type) || 
              item.type == Material.ELYTRA || isTrackableNexoItem(item))
    }
    
    // 도구인지 확인
    private fun isTool(type: Material): Boolean {
        return type.name.endsWith("_PICKAXE") ||
               type.name.endsWith("_AXE") ||
               type.name.endsWith("_SHOVEL") ||
               type.name.endsWith("_HOE")
    }
    
    // 방어구인지 확인
    private fun isArmor(type: Material): Boolean {
        return type.name.endsWith("_HELMET") ||
               type.name.endsWith("_CHESTPLATE") ||
               type.name.endsWith("_LEGGINGS") ||
               type.name.endsWith("_BOOTS") ||
               type == Material.SHIELD
    }
    
    // 외부에서 StatsManager 접근을 위한 getter
    fun getStatsManager(): ItemStatsManager {
        return statsManager
    }
    
    // ToolStats 스타일: 특정 아이템이 통계를 추적할 대상인지 확인
    fun isTrackableSpecificItem(type: Material): Boolean {
        return when (type) {
            // 다이아몬드 도구
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE,
            Material.DIAMOND_SWORD,
            // 네더라이트 도구
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL,
            Material.NETHERITE_HOE,
            Material.NETHERITE_SWORD,
            // 다이아몬드 방어구
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            // 네더라이트 방어구
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            // 특수 아이템
            Material.ELYTRA,
            Material.SHIELD -> true
            else -> false
        }
    }
    
    // 아이템이 Nexo 커스텀 아이템인지 확인
    fun isTrackableNexoItem(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        
        try {
            // 아이템에 이미 nexo:id가 있는지 확인
            val meta = item.itemMeta
            if (meta != null) {
                for (key in meta.persistentDataContainer.keys) {
                    if (key.toString() == "nexo:id" || key.toString().contains("nexo:id")) {
                        logDebug("기존 nexo:id 태그가 있는 아이템 - 추적하지 않음")
                        return false // 이미 nexo:id가 있으면 건드리지 않음
                    }
                }
            }
            
            // Nexo API를 사용하여 아이템 ID 확인
            val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val method = nexoClass.getDeclaredMethod("idFromItem", ItemStack::class.java)
            val itemId = method.invoke(null, item) as? String ?: return false
            
            // 추적 대상 Nexo 아이템인지 확인
            val shouldTrack = trackableNexoItems.contains(itemId) || 
                   (itemId.contains("_pickaxe") || 
                    itemId.contains("_sword") || 
                    itemId.contains("_axe") || 
                    itemId.contains("_shovel") || 
                    itemId.contains("_hoe"))
                    
            if (shouldTrack) {
                logDebug("추적 가능한 Nexo 아이템 감지: $itemId")
            }
            
            return shouldTrack
        } catch (e: Exception) {
            // 예외 발생 시 일반 아이템으로 처리
            logDebug("Nexo 아이템 확인 중 오류: ${e.message}")
            return false
        }
    }
}
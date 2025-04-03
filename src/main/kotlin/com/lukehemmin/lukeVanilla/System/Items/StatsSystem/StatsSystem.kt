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

/**
 * 아이템 통계 시스템의 메인 클래스
 * 이 클래스는 다음과 같은 기능을 제공합니다:
 * 1. 아이템 통계 관리자 초기화
 * 2. 이벤트 리스너 등록
 * 3. 아이템 생성 이벤트 처리 (제작, 수리 등)
 */
class StatsSystem(private val plugin: Main) : Listener {
    
    // 통계 관리자 및 리스너 초기화
    private val statsManager = ItemStatsManager(plugin)
    private val statsListener = ItemStatsListener(plugin, statsManager)
    
    init {
        // 이벤트 리스너 등록
        plugin.server.pluginManager.registerEvents(statsListener, plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        
        plugin.logger.info("아이템 통계 시스템이 초기화되었습니다.")
    }
    
    // 아이템 제작 시 통계 초기화
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemCraft(event: CraftItemEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player
        val item = event.currentItem ?: return
        
        if (isTrackableItem(item)) {
            statsListener.initializeNewItem(item, player)
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
    
    // 추적 가능한 아이템인지 확인
    private fun isTrackableItem(item: ItemStack): Boolean {
        return isTool(item.type) || isArmor(item.type) || item.type == Material.ELYTRA
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
} 
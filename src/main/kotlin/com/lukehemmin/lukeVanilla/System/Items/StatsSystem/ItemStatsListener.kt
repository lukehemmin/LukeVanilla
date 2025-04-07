package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import org.bukkit.Bukkit

class ItemStatsListener(private val plugin: Main, private val statsManager: ItemStatsManager) : Listener {

    // StatsSystem 참조 가져오기
    private val statsSystem: StatsSystem
        get() = plugin.statsSystem
    
    // 엔티티 데미지 입힐 때 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player) {
            val item = damager.inventory.itemInMainHand
            
            // 무기로 데미지를 입힌 경우 (바닐라 또는 Nexo) - 킬 카운트와 관련 없으므로 제거
            if (item.type.isAir) return
        }
    }
    
    // 엔티티 킬 시 통계 업데이트
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer ?: return
        val item = killer.inventory.itemInMainHand
        
        if (item.type.isAir) return
        
        // 이미 처리된 이벤트인지 확인 (UpgradeItem에서 처리한 경우)
        if (event.entity.hasMetadata("luke_kill_processed")) {
            return
        }
        
        // 바닐라 또는 Nexo 아이템 체크
        val isNexoItem = statsSystem.isTrackableNexoItem(item)
        val isVanillaWeapon = ItemUtils.isWeapon(item.type)
        
        if (isNexoItem || isVanillaWeapon) {
            // Nexo 아이템인 경우 추가 확인 (이미 처리됐는지)
            if (isNexoItem && NexoItems.idFromItem(item) != null) {
                // 이미 UpgradeItem이 처리했다면 건너뛰기
                return
            }
            
            // 플레이어 킬 또는 몹 킬 통계 증가
            if (entity is Player) {
                statsManager.incrementPlayersKilled(item)
                statsSystem.logDebug("플레이어 킬 증가: ${killer.name} (${if(isNexoItem) "Nexo아이템" else item.type.name})")
            } else {
                statsManager.incrementMobsKilled(item)
                statsSystem.logDebug("몹 킬 증가: ${killer.name} (${if(isNexoItem) "Nexo아이템" else item.type.name})")
            }
        }
    }
    
    /**
     * Nexo 커스텀 아이템인지 확인하는 메서드 (NexoAPI 활용)
     */
    private fun isNexoCustomItem(item: ItemStack): Boolean {
        try {
            // API 호출 및 태그 확인을 한 번에 최적화
            val nexoId = NexoItems.idFromItem(item)
            if (nexoId != null) {
                return true
            }
            
            // 메타데이터가 있는 경우만 추가 확인
            val meta = item.itemMeta ?: return false
            
            // PersistentDataContainer에서 nexo:id 태그 검색
            return meta.persistentDataContainer.keys.any { 
                it.toString() == "nexo:id" || it.toString().contains("nexo:id") 
            }
        } catch (e: Exception) {
            statsSystem.logDebug("Nexo 아이템 확인 중 오류: ${e.message}")
            return false
        }
    }
}
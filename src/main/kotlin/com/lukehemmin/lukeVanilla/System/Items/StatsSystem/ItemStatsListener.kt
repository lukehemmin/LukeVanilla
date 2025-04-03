package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey

class ItemStatsListener(private val plugin: Main, private val statsManager: ItemStatsManager) : Listener {

    private val lastElytraPositions = mutableMapOf<Player, Vector>()
    private val elytraUpdateThreshold = 10.0 // 10블록마다 업데이트

    // 도구로 블록 파괴 시 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        if (isTool(item.type)) {
            statsManager.incrementBlocksMined(item)
        }
    }
    
    // 엔티티 데미지 입힐 때 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player) {
            val item = damager.inventory.itemInMainHand
            
            // 무기로 데미지를 입힌 경우
            if (isWeapon(item.type)) {
                statsManager.addDamageDealt(item, event.finalDamage)
            }
        }
        
        // 방어구로 데미지를 감소시킨 경우
        val entity = event.entity
        if (entity is Player) {
            // 피해 감소량 계산 (대략적으로 계산, 실제로는 더 복잡함)
            val originalDamage = event.damage
            val finalDamage = event.finalDamage
            val blockedDamage = originalDamage - finalDamage
            
            if (blockedDamage > 0) {
                // 장착된 모든 방어구에 통계 업데이트
                entity.inventory.armorContents?.forEach { armorItem ->
                    if (armorItem != null && isArmor(armorItem.type)) {
                        // 각 방어구 아이템에 대해 방어한 데미지를 균등하게 분배
                        val armorCount = entity.inventory.armorContents.count { it != null && isArmor(it.type) }
                        if (armorCount > 0) {
                            statsManager.addDamageBlocked(armorItem, blockedDamage / armorCount)
                        }
                    }
                }
            }
        }
    }
    
    // 엔티티 킬 시 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer
        
        if (killer != null) {
            val item = killer.inventory.itemInMainHand
            
            // 아이템 ID를 가져오는 방식 수정 (NBT 태그에서 확인 추가)
            val itemId = try {
                // 먼저 NBT 태그에서 확인 (UpgradeItem에서 저장한 ID)
                val meta = item.itemMeta
                if (meta != null) {
                    val customIdKey = NamespacedKey(plugin, "lukestats_item_id")
                    if (meta.persistentDataContainer.has(customIdKey, PersistentDataType.STRING)) {
                        val customId = meta.persistentDataContainer.get(customIdKey, PersistentDataType.STRING)
                        plugin.logger.info("[ItemStatsListener] NBT에서 아이템 ID 찾음: ${customId}")
                        customId
                    } else {
                        // NBT에 없으면 Nexo API 사용
                        val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
                        val method = nexoClass.getDeclaredMethod("idFromItem", ItemStack::class.java)
                        method.invoke(null, item) as? String
                    }
                } else {
                    // 메타데이터가 없으면 Nexo API 사용
                    val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
                    val method = nexoClass.getDeclaredMethod("idFromItem", ItemStack::class.java)
                    method.invoke(null, item) as? String
                }
            } catch (e: Exception) {
                plugin.logger.warning("[ItemStatsListener] 아이템 ID 가져오기 실패: ${e.message}")
                null
            }
            
            plugin.logger.info("[ItemStatsListener] EntityDeath 이벤트 발생: 플레이어=${killer.name}, 아이템ID=${itemId}, 아이템타입=${item.type}")
            
            // Nexo 커스텀 아이템은 UpgradeItem에서 처리하므로 일반 무기만 처리
            if (itemId == null && isWeapon(item.type)) {
                // 플레이어 킬인 경우
                if (entity is Player) {
                    statsManager.incrementPlayersKilled(item)
                    plugin.logger.info("[ItemStatsListener] 플레이어 킬 증가: ${killer.name}")
                } else {
                    // 몹 킬인 경우
                    statsManager.incrementMobsKilled(item)
                    plugin.logger.info("[ItemStatsListener] 몹 킬 증가: ${killer.name}")
                }
            }
        }
    }
    
    // 엘리트라로 날때 거리 추적
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        
        // 플레이어가 엘리트라로 날고 있는지 확인
        if (player.isGliding) {
            val chestplate = player.inventory.chestplate
            if (chestplate != null && chestplate.type == Material.ELYTRA) {
                // 현재 위치 가져오기
                val currentPos = player.location.toVector()
                
                // 마지막 위치가 있으면 거리 계산
                val lastPos = lastElytraPositions[player]
                if (lastPos != null) {
                    val distance = currentPos.distance(lastPos)
                    
                    // 일정 거리 이상 이동했을 때만 업데이트
                    if (distance >= elytraUpdateThreshold) {
                        statsManager.addDistanceFlown(chestplate, distance)
                        lastElytraPositions[player] = currentPos
                    }
                } else {
                    // 처음 기록하는 경우
                    lastElytraPositions[player] = currentPos
                }
            }
        } else {
            // 엘리트라로 날지 않는 경우 마지막 위치 제거
            lastElytraPositions.remove(player)
        }
    }
    
    // 엔드에서 아이템 프레임에서 떨어진 엘리트라 처음 주운 사람 기록
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (event.entity is Player && event.item.itemStack.type == Material.ELYTRA) {
            val player = event.entity as Player
            val elytra = event.item.itemStack
            
            // 끝 세계에서 획득한 경우만 처리
            if (player.world.name.endsWith("_the_end") || player.world.name == "world_the_end") {
                // 이미 초기화된 엘리트라인지 확인
                if (statsManager.getFirstOwner(elytra) == null) {
                    statsManager.initializeElytra(elytra, player)
                }
            }
        }
    }
    
    // 아이템 생성시 초기화 (다른 플러그인이나 시스템에서 호출해야 함)
    fun initializeNewItem(item: ItemStack, creator: Player) {
        when {
            isTool(item.type) -> statsManager.initializeTool(item, creator)
            isArmor(item.type) -> statsManager.initializeArmor(item, creator)
            item.type == Material.ELYTRA -> statsManager.initializeElytra(item, creator)
        }
    }
    
    // 도구인지 확인
    private fun isTool(type: Material): Boolean {
        return type.name.endsWith("_PICKAXE") ||
               type.name.endsWith("_AXE") ||
               type.name.endsWith("_SHOVEL") ||
               type.name.endsWith("_HOE")
    }
    
    // 무기인지 확인
    private fun isWeapon(type: Material): Boolean {
        return type.name.endsWith("_SWORD") ||
               type.name.endsWith("_AXE") ||
               type == Material.BOW ||
               type == Material.CROSSBOW ||
               type == Material.TRIDENT
    }
    
    // 방어구인지 확인
    private fun isArmor(type: Material): Boolean {
        return type.name.endsWith("_HELMET") ||
               type.name.endsWith("_CHESTPLATE") ||
               type.name.endsWith("_LEGGINGS") ||
               type.name.endsWith("_BOOTS") ||
               type == Material.SHIELD
    }
} 
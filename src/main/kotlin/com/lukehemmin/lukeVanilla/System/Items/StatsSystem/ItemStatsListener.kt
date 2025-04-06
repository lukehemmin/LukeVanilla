package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
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
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.event.inventory.SmithItemEvent
import org.bukkit.inventory.SmithingInventory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.inventory.CraftingInventory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.bukkit.Bukkit

class ItemStatsListener(private val plugin: Main, private val statsManager: ItemStatsManager) : Listener {

    // StatsSystem 참조 가져오기
    private val statsSystem: StatsSystem
        get() = plugin.statsSystem

    private val lastElytraPositions = mutableMapOf<Player, Vector>()
    private val elytraUpdateThreshold = 10.0 // 10블록마다 업데이트

    // 제작 세션 및 아이템 식별을 위한 맵
    private val craftingSessions = mutableMapOf<Player, Long>() // 플레이어 -> 세션 시작 시간
    
    // 작업대 결과물이 준비될 때 메타데이터 미리 설정
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareItemCraft(event: PrepareItemCraftEvent) {
        val result = event.inventory.result ?: return
        
        // 추적 가능한 아이템인지 확인 (statsSystem의 메서드 사용)
        if (!statsSystem.isTrackableItem(result)) return
        
        // 제작자 확인
        val viewers = event.viewers
        if (viewers.isEmpty() || viewers[0] !is Player) return
        val player = viewers[0] as Player
        
        statsSystem.logDebug("제작 준비 단계에서 결과물 검사: ${result.type.name}")
        
        // 이미 메타데이터가 있는지 확인
        if (statsManager.getCreator(result) != null) {
            statsSystem.logDebug("이미 메타데이터가 설정되어 있음: ${result.type.name}")
            return
        }
        
        // 제작 세션 기록
        craftingSessions[player] = System.currentTimeMillis()
        
        // 결과물에 메타데이터 설정
        try {
            // 새 결과물 생성 및 메타데이터 설정
            val newResult = result.clone()
            statsManager.initializeTool(newResult, player)
            event.inventory.result = newResult
            
            // 아이템 식별자 생성 (타입 + 시간)
            val itemSignature = "${result.type.name}_${System.currentTimeMillis()}"
            
            // 최근 제작 아이템 목록에 추가
            statsSystem.recentlyCraftedItems
                .computeIfAbsent(player.uniqueId) { mutableListOf() }
                .add(itemSignature)
                
            statsSystem.logDebug("제작 준비 완료: ${player.name}님이 ${result.type.name} 준비, 식별자: $itemSignature")
        } catch (e: Exception) {
            statsSystem.logDebug("제작 준비 단계에서 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 아이템 크래프팅 감지 (완료 시점)
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onItemCraft(event: CraftItemEvent) {
        // 플레이어 확인
        val player = event.whoClicked as? Player ?: return
        
        try {
            val recipe = event.recipe
            val resultType = recipe.result.type
            
            // 추적 가능한 아이템인지 확인
            if (!statsSystem.isTrackableItem(recipe.result)) {
                return
            }
            
            statsSystem.logDebug("아이템 제작 이벤트: ${resultType.name}")
            
            // 현재 아이템 가져오기 (null일 수 있음)
            val currentItem = event.currentItem
            
            // 아이템이 null이거나 AIR인 경우 레시피 결과로 새로 생성
            if (currentItem == null || currentItem.type.isAir) {
                statsSystem.logDebug("현재 아이템이 null 또는 AIR - 레시피 결과로 초기화")
                val newItem = recipe.result.clone()
                initializeNewItem(newItem, player)
                
                // 나중에 인벤토리 확인 스케줄링
                checkInventoryLater(player, resultType)
                return
            }
            
            // 메타데이터가 없는 경우 초기화
            try {
                if (statsManager.getCreator(currentItem) == null) {
                    statsSystem.logDebug("메타데이터가 없는 제작 아이템 감지 - 초기화 중")
                    val clone = currentItem.clone()
                    statsManager.initializeTool(clone, player)
                    statsSystem.logDebug("메타데이터 설정 완료")
                    
                    // 성공했는데 메타데이터가 적용되지 않은 경우 나중에 인벤토리 확인
                    if (statsManager.getCreator(currentItem) == null) {
                        statsSystem.logDebug("메타데이터가 즉시 적용되지 않음 - 나중에 인벤토리 확인")
                        checkInventoryLater(player, resultType)
                    }
                } else {
                    statsSystem.logDebug("제작 아이템에 이미 메타데이터가 있음")
                }
            } catch (e: Exception) {
                statsSystem.logDebug("제작 아이템 메타데이터 설정 중 오류: ${e.message}")
                // 오류 발생시 나중에 인벤토리 확인
                checkInventoryLater(player, resultType)
            }
        } catch (e: Exception) {
            statsSystem.logDebug("제작 이벤트 처리 중 일반 오류: ${e.message}")
        }
    }
    
    // 지연된 인벤토리 검사
    private fun checkInventoryLater(player: Player, type: Material) {
        statsSystem.logDebug("${player.name}님의 인벤토리 지연 검사 예약: ${type.name}")
        
        // 1틱 후에 인벤토리 검사 실행
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                if (player.isOnline) {
                    findAndInitializeNewItems(player, type)
                }
            } catch (e: Exception) {
                statsSystem.logDebug("지연된 인벤토리 검사 오류: ${e.message}")
            }
        }, 1L)
    }
    
    // 인벤토리 클릭 이벤트 처리 (ToolStats 방식)
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // 크래프팅 테이블 인벤토리만 처리
        if (event.view.type != InventoryType.CRAFTING && event.view.type != InventoryType.WORKBENCH) {
            return
        }
        
        try {
            // 클릭된 아이템 확인
            val clickedItem = event.currentItem
            
            // 커서에 있는 아이템 확인
            val cursorItem = event.cursor
            
            // 쉬프트 클릭 처리
            if (event.isShiftClick && clickedItem != null && !clickedItem.type.isAir) {
                if (statsSystem.isTrackableItem(clickedItem) && !isNexoCustomItem(clickedItem)) {
                    statsSystem.logDebug("쉬프트 클릭 감지: ${clickedItem.type.name}")
                    
                    try {
                        if (statsManager.getCreator(clickedItem) == null) {
                            statsSystem.logDebug("쉬프트 클릭 아이템에 메타데이터 설정")
                            initializeNewItem(clickedItem, player)
                        }
                    } catch (e: Exception) {
                        statsSystem.logDebug("쉬프트 클릭 아이템 메타데이터 설정 오류: ${e.message}")
                        // 쉬프트 클릭 후 인벤토리 확인 스케줄링
                        checkInventoryLater(player, clickedItem.type)
                    }
                }
                return
            }
            
            // 일반 클릭 처리
            if (clickedItem != null && !clickedItem.type.isAir && statsSystem.isTrackableItem(clickedItem) && !isNexoCustomItem(clickedItem)) {
                statsSystem.logDebug("클릭된 아이템: ${clickedItem.type.name}")
                
                try {
                    if (statsManager.getCreator(clickedItem) == null) {
                        statsSystem.logDebug("클릭된 아이템에 메타데이터 설정")
                        initializeNewItem(clickedItem, player)
                    }
                } catch (e: Exception) {
                    statsSystem.logDebug("클릭된 아이템 메타데이터 설정 오류: ${e.message}")
                }
            }
            
            // 커서 아이템 처리
            if (cursorItem != null && !cursorItem.type.isAir && statsSystem.isTrackableItem(cursorItem) && !isNexoCustomItem(cursorItem)) {
                statsSystem.logDebug("커서 아이템: ${cursorItem.type.name}")
                
                try {
                    if (statsManager.getCreator(cursorItem) == null) {
                        statsSystem.logDebug("커서 아이템에 메타데이터 설정")
                        initializeNewItem(cursorItem, player)
                    }
                } catch (e: Exception) {
                    statsSystem.logDebug("커서 아이템 메타데이터 설정 오류: ${e.message}")
                }
            }
        } catch (e: Exception) {
            statsSystem.logDebug("인벤토리 클릭 이벤트 처리 중 오류: ${e.message}")
        }
    }
    
    // 인벤토리에서 새로 생성된 아이템 찾아 초기화 (ToolStats 방식)
    private fun findAndInitializeNewItems(player: Player, type: Material) {
        statsSystem.logDebug("인벤토리에서 새 아이템 검색: ${type.name}")
        var found = false
        
        // 전체 인벤토리 확인
        for (i in 0 until player.inventory.size) {
            val item = player.inventory.getItem(i) ?: continue
            
            if (item.type != type || item.type.isAir) continue
            
            try {
                if (statsManager.getCreator(item) == null) {
                    statsSystem.logDebug("인벤토리에서 메타데이터가 없는 새 아이템 발견: 슬롯 $i")
                    initializeNewItem(item, player)
                    found = true
                }
            } catch (e: Exception) {
                statsSystem.logDebug("인벤토리 아이템 메타데이터 설정 오류: ${e.message}")
            }
        }
        
        // 인벤토리 외부에 있는 아이템 확인 (커서 등)
        val cursor = player.itemOnCursor
        if (!cursor.type.isAir && cursor.type == type) {
            try {
                if (statsManager.getCreator(cursor) == null) {
                    statsSystem.logDebug("커서에서 메타데이터가 없는 새 아이템 발견")
                    initializeNewItem(cursor, player)
                    found = true
                }
            } catch (e: Exception) {
                statsSystem.logDebug("커서 아이템 메타데이터 설정 오류: ${e.message}")
            }
        }
        
        if (!found) {
            statsSystem.logDebug("인벤토리에서 메타데이터가 없는 새 아이템을 찾지 못함")
        }
    }
    
    // 크래프팅 인벤토리에서 최대 제작 가능 아이템 수량 계산
    private fun calculateMaxCraftable(inventory: CraftingInventory): Int {
        val matrix = inventory.matrix
        var maxCraftable = 64 // 기본값
        
        // 제작 재료들을 확인하여 가능한 최대 제작 수량 계산
        for (item in matrix) {
            if (item != null && item.type != Material.AIR) {
                val amount = item.amount
                if (amount < maxCraftable) {
                    maxCraftable = amount
                }
            }
        }
        
        return maxCraftable
    }
    
    // 네더라이트 업그레이드 시 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmithItem(event: SmithItemEvent) {
        if (event.whoClicked !is Player) return
        
        val player = event.whoClicked as Player
        
        // SmithingInventory에서 결과 아이템 가져오기
        val inventory = event.inventory as? SmithingInventory ?: return
        val resultItem = inventory.result ?: return
        
        statsSystem.logDebug("스미싱 감지: ${player.name}님이 ${resultItem.type.name} 아이템 제작/업그레이드")
        
        // 네더라이트 업그레이드인 경우에만 처리
        if (isNetheriteUpgrade(resultItem.type)) {
            statsSystem.logDebug("네더라이트 업그레이드 감지: ${player.name}님이 ${resultItem.type.name}으로 업그레이드")
            
            // 결과 아이템에 새 제작자 및 제작일 설정
            statsManager.updateCreator(resultItem, player)
            
            // 업데이트 후 확인
            val postCreator = statsManager.getCreator(resultItem)
            statsSystem.logDebug("업데이트 후 제작자 정보: ${postCreator?.toString() ?: "업데이트 실패"}")
        }
    }
    
    // 도구로 블록 파괴 시 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        if (isTool(item.type) || statsSystem.isTrackableNexoItem(item)) {
            statsManager.incrementBlocksMined(item)
            statsSystem.logDebug("블록 파괴 통계 증가: ${player.name}")
        }
    }
    
    // 엔티티 데미지 입힐 때 통계 업데이트
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player) {
            val item = damager.inventory.itemInMainHand
            
            // 무기로 데미지를 입힌 경우 (바닐라 또는 Nexo)
            if (isWeapon(item.type) || statsSystem.isTrackableNexoItem(item)) {
                statsManager.addDamageDealt(item, event.finalDamage)
                statsSystem.logDebug("데미지 통계 추가: ${damager.name}, 데미지: ${event.finalDamage}")
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
        val killer = entity.killer ?: return
        val item = killer.inventory.itemInMainHand
        
        if (item.type.isAir) return
        
        // 바닐라 또는 Nexo 아이템 체크
        val isNexoItem = statsSystem.isTrackableNexoItem(item)
        val isVanillaWeapon = isWeapon(item.type)
        
        if (isNexoItem || isVanillaWeapon) {
            // 플레이어 킬인 경우
            if (entity is Player) {
                statsManager.incrementPlayersKilled(item)
                statsSystem.logDebug("플레이어 킬 증가: ${killer.name} (${if(isNexoItem) "Nexo아이템" else item.type.name})")
            } else {
                // 몹 킬인 경우
                statsManager.incrementMobsKilled(item)
                statsSystem.logDebug("몹 킬 증가: ${killer.name} (${if(isNexoItem) "Nexo아이템" else item.type.name})")
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
    fun initializeNewItem(item: ItemStack, player: Player) {
        if (item.type.isAir) {
            statsSystem.logDebug("AIR 타입 아이템에 메타데이터 설정 시도 - 무시됨")
            return
        }
        
        try {
            // Nexo 커스텀 아이템인 경우 초기화하지 않음
            if (isNexoCustomItem(item)) {
                statsSystem.logDebug("Nexo 커스텀 아이템 감지됨 - 메타데이터 설정 생략: ${item.type.name}")
                return
            }
            
            // 아이템이 추적 가능한지 확인 (statsSystem의 메서드 사용)
            if (statsSystem.isTrackableItem(item)) {
                statsSystem.logDebug("새 아이템 초기화: ${item.type.name}")
                statsManager.initializeTool(item, player)
                statsSystem.logDebug("메타데이터 설정 완료")
            } else {
                statsSystem.logDebug("추적 불가능한 아이템 타입: ${item.type.name}")
            }
        } catch (e: Exception) {
            statsSystem.logDebug("아이템 초기화 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * Nexo 커스텀 아이템인지 확인하는 메서드
     */
    private fun isNexoCustomItem(item: ItemStack): Boolean {
        try {
            // 아이템에 이미 nexo:id가 있는지 확인
            val meta = item.itemMeta
            if (meta != null) {
                for (key in meta.persistentDataContainer.keys) {
                    if (key.toString() == "nexo:id" || key.toString().contains("nexo:id")) {
                        statsSystem.logDebug("기존 nexo:id 태그가 있는 아이템 감지")
                        return true
                    }
                }
            }
            
            // Nexo API를 사용하여 아이템 ID 확인
            val nexoId = NexoItems.idFromItem(item)
            return nexoId != null
        } catch (e: Exception) {
            statsSystem.logDebug("Nexo 아이템 확인 중 오류: ${e.message}")
            return false
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
    
    // 네더라이트 업그레이드인지 확인
    private fun isNetheriteUpgrade(type: Material): Boolean {
        return type == Material.NETHERITE_SWORD ||
               type == Material.NETHERITE_PICKAXE ||
               type == Material.NETHERITE_AXE ||
               type == Material.NETHERITE_SHOVEL ||
               type == Material.NETHERITE_HOE ||
               type == Material.NETHERITE_HELMET ||
               type == Material.NETHERITE_CHESTPLATE ||
               type == Material.NETHERITE_LEGGINGS ||
               type == Material.NETHERITE_BOOTS
    }
}
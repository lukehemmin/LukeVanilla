package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.System.Discord.ItemRestoreLogger
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.Bukkit

class ItemRestoreCommand(private val itemRestoreLogger: ItemRestoreLogger) : CommandExecutor, Listener {
    
    private val ITEM_ID_KEY = NamespacedKey("lukevanilla", "lukestats_item_id")
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        return restorePlayerItems(sender)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        restorePlayerItems(event.player)
    }

    private fun restorePlayerItems(player: Player): Boolean {
        var restoredCount = 0
        itemRestoreLogger.startNewLog()

        for (i in 0 until 36) {
            val item = player.inventory.getItem(i) ?: continue
            if (item.type.isAir) continue

            if (tryRestoreItem(item, player, i)) {
                restoredCount++
            } else if (tryRestoreItemIdMismatch(item, player, i)) {
                restoredCount++
            }
        }

        if (restoredCount > 0) {
            player.sendMessage(Component.text("총 ${restoredCount}개의 아이템이 복구되었습니다.").color(NamedTextColor.GREEN))
            itemRestoreLogger.sendLog(restoredCount)
        }

        return true
    }

    private fun tryRestoreItem(item: ItemStack, player: Player, slot: Int): Boolean {
        val meta = item.itemMeta ?: return false

        if (meta.hasItemModel()) {
            val modelData = meta.itemModel.toString()
            if (modelData.indexOf("oraxen") != -1) {
                val oraxenId = modelData.split(":").lastOrNull() ?: return false

                val nexoItem = NexoItems.itemFromId(oraxenId)?.build() ?: return false
                // 기존 이름 복사
                val newMeta = nexoItem.itemMeta
                if (meta.hasDisplayName()) {
                    newMeta.displayName(meta.displayName())
                }

                // 인첸트 복사
                meta.enchants.forEach { (enchant, level) ->
                    newMeta.addEnchant(enchant, level, true)
                }

                // 대장장이 형판 업그레이드 복사
                if (meta is org.bukkit.inventory.meta.ArmorMeta) {
                    val newArmorMeta = newMeta as? org.bukkit.inventory.meta.ArmorMeta
                    if (newArmorMeta != null && meta.hasTrim()) {
                        newArmorMeta.trim = meta.trim
                    }
                }

                nexoItem.itemMeta = newMeta
                nexoItem.amount = item.amount

                player.inventory.setItem(slot, nexoItem)
                // 로그 전송
                itemRestoreLogger.logRestoredItem(player, item, nexoItem, oraxenId)

                return true
            }
        }
        return false
    }
    
    /**
     * NBT 데이터의 아이템 ID 불일치를 복구하는 메서드
     */
    private fun tryRestoreItemIdMismatch(item: ItemStack, player: Player, slot: Int): Boolean {
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        // NBT에서 lukestats_item_id 값을 확인
        val savedItemId = getSavedItemId(pdc) ?: return false
        
        // Nexo ID 확인
        val nexoId = NexoItems.idFromItem(item)
        
        // 두 ID가 다르면 복구 필요
        if (nexoId != savedItemId) {
            // 특정 업그레이드된 아이템만 처리
            if (!isUpgradableItem(savedItemId)) {
                return false
            }
            
            Bukkit.getLogger().info("[아이템 복구] 아이템 ID 불일치 발견: ${player.name}의 아이템 - nexo:id=$nexoId, saved_id=$savedItemId")
            
            // 새 아이템 생성
            val newItem = createItemFromId(savedItemId) ?: return false
            
            // 기존 아이템 데이터 복사
            copyItemData(item, newItem)
            
            // 아이템 교체
            player.inventory.setItem(slot, newItem)
            
            // 로그 전송
            itemRestoreLogger.logRestoredItem(player, item, newItem, savedItemId)
            
            // 콘솔 로그
            Bukkit.getLogger().info("[아이템 복구] ${player.name}의 아이템이 복구됨: $nexoId -> $savedItemId")
            
            return true
        }
        
        return false
    }
    
    /**
     * NBT에서 저장된 아이템 ID를 가져오는 메서드
     */
    private fun getSavedItemId(pdc: PersistentDataContainer): String? {
        return try {
            pdc.get(ITEM_ID_KEY, PersistentDataType.STRING)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 업그레이드 가능한 아이템인지 확인하는 메서드
     */
    private fun isUpgradableItem(itemId: String): Boolean {
        val upgradedItems = setOf(
            "merry_christmas_greatsword",
            "valentine_greatsword",
            "firework_greatsword"
        )
        
        return upgradedItems.contains(itemId)
    }
    
    /**
     * ID로 아이템을 생성하는 메서드
     */
    private fun createItemFromId(id: String): ItemStack? {
        return try {
            val item = NexoItems.itemFromId(id)?.build()
            Bukkit.getLogger().info("[아이템 복구] '$id' ID로 새 아이템 생성 시도: ${item != null}")
            item
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[아이템 복구] 아이템 생성 실패 ($id): ${e.message}")
            null
        }
    }
    
    /**
     * 기존 아이템에서 새 아이템으로 데이터를 복사하는 메서드
     */
    private fun copyItemData(source: ItemStack, target: ItemStack) {
        // 아이템 메타 가져오기
        val sourceMeta = source.itemMeta ?: return
        val targetMeta = target.itemMeta ?: return
        
        // 1. 인첸트 복사
        sourceMeta.enchants.forEach { (enchant, level) ->
            targetMeta.addEnchant(enchant, level, true)
        }
        
        // 2. 이름 복사
        if (sourceMeta.hasDisplayName()) {
            targetMeta.displayName(sourceMeta.displayName())
        }
        
        // 3. 대장장이 형판 복사
        if (sourceMeta is org.bukkit.inventory.meta.ArmorMeta) {
            val targetArmorMeta = targetMeta as? org.bukkit.inventory.meta.ArmorMeta
            if (targetArmorMeta != null && sourceMeta.hasTrim()) {
                targetArmorMeta.trim = sourceMeta.trim
            }
        }
        
        // 4. NBT 데이터 복사 (nexo:id 제외)
        copyNBTData(sourceMeta.persistentDataContainer, targetMeta.persistentDataContainer)
        
        // 로깅 추가: 복구 후 아이템 ID 확인
        val nexoId = NexoItems.idFromItem(target)
        val savedId = getSavedItemId(targetMeta.persistentDataContainer)
        Bukkit.getLogger().info("[아이템 복구] 복사 후 아이템 ID: nexo:id=$nexoId, saved_id=$savedId")
        
        // 메타 적용
        target.itemMeta = targetMeta
        
        // 수량 복사
        target.amount = source.amount
    }
    
    /**
     * 아이템의 NBT 데이터를 복사하는 메서드
     */
    private fun copyNBTData(source: PersistentDataContainer, target: PersistentDataContainer) {
        // 모든 키를 순회하며 필요한 데이터 복사
        for (key in source.keys) {
            // "nexo:id" 키는 명시적으로 제외
            if (key.namespace == "nexo" && key.key == "id") {
                Bukkit.getLogger().info("[아이템 복구] nexo:id 키 제외됨")
                continue
            }
            
            // 데이터 타입에 따라 복사
            copyDataByType(key, source, target)
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
                    Bukkit.getLogger().info("[아이템 복구] NBT 복사: ${key.namespace}:${key.key}=$it (문자열)")
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
            Bukkit.getLogger().warning("[아이템 복구] NBT 데이터 복사 실패 (키: $key): ${e.message}")
        }
    }
}
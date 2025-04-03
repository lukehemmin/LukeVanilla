package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class ItemStatsManager(private val plugin: Main) : Listener {
    
    companion object {
        private const val NAMESPACE = "lukestats"
        private val ISO_DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME
        
        // Keys for all items
        private const val CREATOR_KEY = "creator"
        private const val CREATED_AT_KEY = "created_at"
        
        // Keys for tools
        private const val BLOCKS_MINED_KEY = "blocks_mined"
        private const val MOBS_KILLED_KEY = "mobs_killed"
        private const val PLAYERS_KILLED_KEY = "players_killed"
        private const val DAMAGE_DEALT_KEY = "damage_dealt"
        
        // Keys for armor
        private const val DAMAGE_BLOCKED_KEY = "damage_blocked"
        
        // Keys for elytra
        private const val FIRST_OWNER_KEY = "first_owner"
        private const val OBTAINED_AT_KEY = "obtained_at"
        private const val DISTANCE_FLOWN_KEY = "distance_flown"
    }
    
    // 아이템에 NBT 데이터 설정 메서드
    private fun setItemNBTData(item: ItemStack, key: String, value: String) {
        val meta = item.itemMeta ?: return
        val namespacedKey = NamespacedKey(plugin, key)
        meta.persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, value)
        item.itemMeta = meta
    }
    
    private fun setItemNBTData(item: ItemStack, key: String, value: Int) {
        val meta = item.itemMeta ?: return
        val namespacedKey = NamespacedKey(plugin, key)
        meta.persistentDataContainer.set(namespacedKey, PersistentDataType.INTEGER, value)
        item.itemMeta = meta
    }
    
    private fun setItemNBTData(item: ItemStack, key: String, value: Double) {
        val meta = item.itemMeta ?: return
        val namespacedKey = NamespacedKey(plugin, key)
        meta.persistentDataContainer.set(namespacedKey, PersistentDataType.DOUBLE, value)
        item.itemMeta = meta
    }
    
    // 아이템에서 NBT 데이터 가져오는 메서드
    private fun getItemNBTString(item: ItemStack, key: String): String? {
        val meta = item.itemMeta ?: return null
        val namespacedKey = NamespacedKey(plugin, key)
        return if (meta.persistentDataContainer.has(namespacedKey, PersistentDataType.STRING)) {
            meta.persistentDataContainer.get(namespacedKey, PersistentDataType.STRING)
        } else null
    }
    
    private fun getItemNBTInt(item: ItemStack, key: String): Int {
        val meta = item.itemMeta ?: return 0
        val namespacedKey = NamespacedKey(plugin, key)
        return if (meta.persistentDataContainer.has(namespacedKey, PersistentDataType.INTEGER)) {
            meta.persistentDataContainer.get(namespacedKey, PersistentDataType.INTEGER) ?: 0
        } else 0
    }
    
    private fun getItemNBTDouble(item: ItemStack, key: String): Double {
        val meta = item.itemMeta ?: return 0.0
        val namespacedKey = NamespacedKey(plugin, key)
        return if (meta.persistentDataContainer.has(namespacedKey, PersistentDataType.DOUBLE)) {
            meta.persistentDataContainer.get(namespacedKey, PersistentDataType.DOUBLE) ?: 0.0
        } else 0.0
    }
    
    // 새로운 도구 아이템 초기화
    fun initializeTool(item: ItemStack, creator: Player) {
        val currentTime = LocalDateTime.now().format(ISO_DATE_FORMATTER)
        
        setItemNBTData(item, "${NAMESPACE}_${CREATOR_KEY}", creator.uniqueId.toString())
        setItemNBTData(item, "${NAMESPACE}_${CREATED_AT_KEY}", currentTime)
        setItemNBTData(item, "${NAMESPACE}_${BLOCKS_MINED_KEY}", 0)
        setItemNBTData(item, "${NAMESPACE}_${MOBS_KILLED_KEY}", 0)
        setItemNBTData(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}", 0)
        setItemNBTData(item, "${NAMESPACE}_${DAMAGE_DEALT_KEY}", 0)
    }
    
    // 새로운 방어구 초기화
    fun initializeArmor(item: ItemStack, creator: Player) {
        val currentTime = LocalDateTime.now().format(ISO_DATE_FORMATTER)
        
        setItemNBTData(item, "${NAMESPACE}_${CREATOR_KEY}", creator.uniqueId.toString())
        setItemNBTData(item, "${NAMESPACE}_${CREATED_AT_KEY}", currentTime)
        setItemNBTData(item, "${NAMESPACE}_${DAMAGE_BLOCKED_KEY}", 0)
    }
    
    // 엘리트라 초기화 (처음 주운 플레이어가 있을 때)
    fun initializeElytra(item: ItemStack, firstOwner: Player) {
        val currentTime = LocalDateTime.now().format(ISO_DATE_FORMATTER)
        
        setItemNBTData(item, "${NAMESPACE}_${FIRST_OWNER_KEY}", firstOwner.uniqueId.toString())
        setItemNBTData(item, "${NAMESPACE}_${OBTAINED_AT_KEY}", currentTime)
        setItemNBTData(item, "${NAMESPACE}_${DISTANCE_FLOWN_KEY}", 0.0)
    }
    
    // 아이템 통계 업데이트 메서드들
    fun incrementBlocksMined(item: ItemStack) {
        val currentValue = getItemNBTInt(item, "${NAMESPACE}_${BLOCKS_MINED_KEY}")
        setItemNBTData(item, "${NAMESPACE}_${BLOCKS_MINED_KEY}", currentValue + 1)
    }
    
    fun incrementMobsKilled(item: ItemStack) {
        val currentValue = getItemNBTInt(item, "${NAMESPACE}_${MOBS_KILLED_KEY}")
        setItemNBTData(item, "${NAMESPACE}_${MOBS_KILLED_KEY}", currentValue + 1)
    }
    
    fun incrementPlayersKilled(item: ItemStack) {
        val currentValue = getItemNBTInt(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}")
        setItemNBTData(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}", currentValue + 1)
    }
    
    fun addDamageDealt(item: ItemStack, damage: Double) {
        val currentValue = getItemNBTInt(item, "${NAMESPACE}_${DAMAGE_DEALT_KEY}")
        setItemNBTData(item, "${NAMESPACE}_${DAMAGE_DEALT_KEY}", currentValue + damage.toInt())
    }
    
    fun addDamageBlocked(item: ItemStack, damage: Double) {
        val currentValue = getItemNBTInt(item, "${NAMESPACE}_${DAMAGE_BLOCKED_KEY}")
        setItemNBTData(item, "${NAMESPACE}_${DAMAGE_BLOCKED_KEY}", currentValue + damage.toInt())
    }
    
    fun addDistanceFlown(item: ItemStack, distance: Double) {
        val currentValue = getItemNBTDouble(item, "${NAMESPACE}_${DISTANCE_FLOWN_KEY}")
        setItemNBTData(item, "${NAMESPACE}_${DISTANCE_FLOWN_KEY}", currentValue + distance)
    }
    
    // 통계 가져오기 메서드들
    fun getCreator(item: ItemStack): UUID? {
        val creatorString = getItemNBTString(item, "${NAMESPACE}_${CREATOR_KEY}") ?: return null
        return UUID.fromString(creatorString)
    }
    
    fun getCreationDate(item: ItemStack): LocalDateTime? {
        val dateString = getItemNBTString(item, "${NAMESPACE}_${CREATED_AT_KEY}") ?: return null
        return LocalDateTime.parse(dateString, ISO_DATE_FORMATTER)
    }
    
    fun getBlocksMined(item: ItemStack): Int {
        return getItemNBTInt(item, "${NAMESPACE}_${BLOCKS_MINED_KEY}")
    }
    
    fun getMobsKilled(item: ItemStack): Int {
        return getItemNBTInt(item, "${NAMESPACE}_${MOBS_KILLED_KEY}")
    }
    
    fun getPlayersKilled(item: ItemStack): Int {
        return getItemNBTInt(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}")
    }
    
    fun getDamageDealt(item: ItemStack): Int {
        return getItemNBTInt(item, "${NAMESPACE}_${DAMAGE_DEALT_KEY}")
    }
    
    fun getDamageBlocked(item: ItemStack): Int {
        return getItemNBTInt(item, "${NAMESPACE}_${DAMAGE_BLOCKED_KEY}")
    }
    
    fun getFirstOwner(item: ItemStack): UUID? {
        val ownerString = getItemNBTString(item, "${NAMESPACE}_${FIRST_OWNER_KEY}") ?: return null
        return UUID.fromString(ownerString)
    }
    
    fun getObtainedDate(item: ItemStack): LocalDateTime? {
        val dateString = getItemNBTString(item, "${NAMESPACE}_${OBTAINED_AT_KEY}") ?: return null
        return LocalDateTime.parse(dateString, ISO_DATE_FORMATTER)
    }
    
    fun getDistanceFlown(item: ItemStack): Double {
        return getItemNBTDouble(item, "${NAMESPACE}_${DISTANCE_FLOWN_KEY}")
    }
} 
package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class ItemStatsManager(private val plugin: Main) : Listener {
    
    // 로깅을 위한 StatsSystem 참조
    private val statsSystem: StatsSystem
        get() = plugin.statsSystem
    
    companion object {
        private const val NAMESPACE = "lukestats"
        private val DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        
        // Keys for tools
        private const val MOBS_KILLED_KEY = "mobs_killed"
        private const val PLAYERS_KILLED_KEY = "players_killed"
    }
    
    // 도구인지 확인
    private fun isTrackableTool(item: ItemStack): Boolean {
        return when (item.type) {
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
            Material.NETHERITE_SWORD -> true
            else -> false
        }
    }
    
    // 방어구인지 확인
    private fun isTrackableArmor(item: ItemStack): Boolean {
        return when (item.type) {
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
            // 기타
            Material.SHIELD -> true
            else -> false
        }
    }
    
    // 아이템에 NBT 데이터 설정 메서드
    fun setItemNBTData(item: ItemStack, key: String, value: Any) {
        try {
            statsSystem.logDebug("아이템 NBT 데이터 설정 시작: ${item.type.name}, 키: $key")

            // 기본 검사
            val meta = item.itemMeta
            if (meta == null) {
                statsSystem.logDebug("메타데이터가 없어 새로 생성합니다")
                return
            }

            // 대상 키 생성
            val namespacedKey = NamespacedKey(plugin, "${NAMESPACE}_$key")

            // 값 타입에 따라 다른 처리
            when (value) {
                is String -> meta.persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, value)
                is Int -> meta.persistentDataContainer.set(namespacedKey, PersistentDataType.INTEGER, value)
                is Double -> meta.persistentDataContainer.set(namespacedKey, PersistentDataType.DOUBLE, value)
                is Float -> meta.persistentDataContainer.set(namespacedKey, PersistentDataType.FLOAT, value)
                is Long -> meta.persistentDataContainer.set(namespacedKey, PersistentDataType.LONG, value)
                is Boolean -> meta.persistentDataContainer.set(namespacedKey, PersistentDataType.INTEGER, if (value) 1 else 0)
                else -> {
                    statsSystem.logDebug("지원하지 않는 데이터 타입: ${value.javaClass.name}")
                    return
                }
            }

            // 메타데이터 적용
            val success = item.setItemMeta(meta)
            
            // 메타데이터 적용 실패 시 대체 방법 시도
            if (!success) {
                statsSystem.logDebug("기본 메타데이터 적용 실패, 대체 방법 시도")
                try {
                    val itemMethod = item.javaClass.getDeclaredMethod("setItemMeta", org.bukkit.inventory.meta.ItemMeta::class.java)
                    itemMethod.isAccessible = true
                    val result = itemMethod.invoke(item, meta)
                    statsSystem.logDebug("리플렉션을 통한 메타데이터 적용 결과: $result")
                } catch (e: Exception) {
                    statsSystem.logDebug("리플렉션을 통한 메타데이터 적용 실패: ${e.message}")
                }
            }

            // 성공 여부 확인
            if (getItemNBTData(item, key, value.javaClass) != null) {
                statsSystem.logDebug("아이템 NBT 데이터 설정 성공: $key")
            } else {
                statsSystem.logDebug("아이템 NBT 데이터 설정 실패: $key")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[ItemStatsManager] NBT 데이터 설정 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 아이템에서 NBT 데이터 가져오는 메서드
    private fun getItemNBTData(item: ItemStack, key: String, dataType: Class<*>): Any? {
        try {
            // 안전 체크 - AIR 아이템이나 null 메타데이터인 경우 바로 처리
            if (item.type.isAir) {
                statsSystem.logDebug("AIR 타입 아이템에서 NBT 데이터를 가져올 수 없습니다")
                return null
            }
            
            val meta = item.itemMeta
            if (meta == null) {
                statsSystem.logDebug("아이템에 메타데이터가 없습니다: ${item.type}")
                return null
            }
            
            val namespacedKey = NamespacedKey(plugin, "${NAMESPACE}_$key")
            
            // 키가 존재하는지 확인
            val hasData = when (dataType) {
                String::class.java -> meta.persistentDataContainer.has(namespacedKey, PersistentDataType.STRING)
                Int::class.java -> meta.persistentDataContainer.has(namespacedKey, PersistentDataType.INTEGER)
                Double::class.java -> meta.persistentDataContainer.has(namespacedKey, PersistentDataType.DOUBLE)
                Float::class.java -> meta.persistentDataContainer.has(namespacedKey, PersistentDataType.FLOAT)
                Long::class.java -> meta.persistentDataContainer.has(namespacedKey, PersistentDataType.LONG)
                Boolean::class.java -> meta.persistentDataContainer.has(namespacedKey, PersistentDataType.INTEGER)
                else -> false
            }
            
            // 키가 없으면 null 반환
            if (!hasData) {
                return null
            }
            
            // 데이터 반환
            return when (dataType) {
                String::class.java -> meta.persistentDataContainer.get(namespacedKey, PersistentDataType.STRING)
                Int::class.java -> meta.persistentDataContainer.get(namespacedKey, PersistentDataType.INTEGER)
                Double::class.java -> meta.persistentDataContainer.get(namespacedKey, PersistentDataType.DOUBLE)
                Float::class.java -> meta.persistentDataContainer.get(namespacedKey, PersistentDataType.FLOAT)
                Long::class.java -> meta.persistentDataContainer.get(namespacedKey, PersistentDataType.LONG)
                Boolean::class.java -> meta.persistentDataContainer.get(namespacedKey, PersistentDataType.INTEGER) != null
                else -> null
            }
        } catch (e: Exception) {
            statsSystem.logDebug("NBT 데이터 읽기 오류(${key}): ${e.message}")
            return null
        }
    }
    
    // 새로운 도구 초기화 - 킬 카운트만 초기화
    fun initializeTool(item: ItemStack) {
        try {
            statsSystem.logDebug("도구 킬 카운트 초기화 시작: ${item.type.name}")
            
            // 1. 먼저 아이템 메타데이터 가져오기
            val meta = item.itemMeta?.clone() ?: plugin.server.itemFactory.getItemMeta(item.type)
            if (meta == null) {
                plugin.logger.warning("[ItemStatsManager] 메타데이터를 생성할 수 없음: ${item.type}")
                return
            }
            
            // 3. 네임스페이스 키 생성
            val mobsKilledKey = NamespacedKey(plugin, "${NAMESPACE}_${MOBS_KILLED_KEY}")
            val playersKilledKey = NamespacedKey(plugin, "${NAMESPACE}_${PLAYERS_KILLED_KEY}")
            
            // 4. 메타데이터 설정 (킬 카운트만)
            meta.persistentDataContainer.set(mobsKilledKey, PersistentDataType.INTEGER, 0)
            meta.persistentDataContainer.set(playersKilledKey, PersistentDataType.INTEGER, 0)
            
            // 5. 최종 메타데이터 적용
            val success = item.setItemMeta(meta)
            
            // 6. 성공 여부 확인
            statsSystem.logDebug("메타데이터 설정 ${if(success) "성공" else "실패"}")
            
            if (!success) {
                statsSystem.logDebug("킬 카운트 초기화 실패, 다른 방법으로 시도")
                
                // 7. 실패 시 아예 새로운 아이템 생성
                try {
                    val newItem = ItemStack(item.type, item.amount)
                    val newMeta = newItem.itemMeta
                    
                    if (newMeta != null) {
                        // 메타데이터 설정 (킬 카운트만)
                        newMeta.persistentDataContainer.set(mobsKilledKey, PersistentDataType.INTEGER, 0)
                        newMeta.persistentDataContainer.set(playersKilledKey, PersistentDataType.INTEGER, 0)
                        
                        // 메타데이터 적용
                        newItem.itemMeta = newMeta
                        
                        // 원본 인챈트 복사
                        for (enchant in item.enchantments.keys) {
                            val level = item.getEnchantmentLevel(enchant)
                            newItem.addUnsafeEnchantment(enchant, level)
                        }
                        
                        // 아이템 내용 복사 (필드별 대입)
                        val itemMethod = item.javaClass.getDeclaredMethod("setItemMeta", org.bukkit.inventory.meta.ItemMeta::class.java)
                        itemMethod.isAccessible = true
                        itemMethod.invoke(item, newItem.itemMeta)
                        
                        statsSystem.logDebug("최종 메타데이터 설정 상태: 성공")
                    }
                } catch (e: Exception) {
                    statsSystem.logDebug("도구 완전 새 아이템 생성 시도 중 오류: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("[ItemStatsManager] 도구 초기화 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 아이템 통계 업데이트 메서드들 (킬 카운트만 유지)
    fun incrementMobsKilled(item: ItemStack) {
        val currentValue = getItemNBTData(item, "${NAMESPACE}_${MOBS_KILLED_KEY}", Int::class.java) as? Int ?: 0
        setItemNBTData(item, "${NAMESPACE}_${MOBS_KILLED_KEY}", currentValue + 1)
    }
    
    fun incrementPlayersKilled(item: ItemStack) {
        val currentValue = getItemNBTData(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}", Int::class.java) as? Int ?: 0
        setItemNBTData(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}", currentValue + 1)
    }
    
    // 통계 가져오기 메서드들 (킬 카운트만 유지)
    fun getMobsKilled(item: ItemStack): Int {
        return getItemNBTData(item, "${NAMESPACE}_${MOBS_KILLED_KEY}", Int::class.java) as? Int ?: 0
    }
    
    fun getPlayersKilled(item: ItemStack): Int {
        return getItemNBTData(item, "${NAMESPACE}_${PLAYERS_KILLED_KEY}", Int::class.java) as? Int ?: 0
    }

    // 아이템의 모든 통계 정보 가져오기
    fun getItemStatsInfo(item: ItemStack): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        val meta = item.itemMeta ?: return stats

        try {
            // 아이템 종류별 통계
            if (isTrackableTool(item)) {
                stats["몹 처치 수"] = getMobsKilled(item)
                stats["플레이어 처치 수"] = getPlayersKilled(item)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ItemStatsManager] 아이템 통계 정보 가져오기 실패: ${e.message}")
        }

        return stats
    }
}
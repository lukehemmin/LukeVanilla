package com.lukehemmin.lukeVanilla.System.Roulette

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.sql.Timestamp

/**
 * 비용 타입 (다른 시스템에서도 재사용 가능)
 */
enum class CostType {
    MONEY,
    ITEM,
    FREE
}

/**
 * 아이템 제공자 타입 (다른 시스템에서도 재사용 가능)
 */
enum class ItemProvider {
    VANILLA,
    NEXO,
    ORAXEN,
    ITEMSADDER
}

/**
 * 룰렛 설정 데이터 클래스 (다중 룰렛 지원)
 */
data class RouletteConfig(
    val id: Int,
    val rouletteName: String,
    val costType: CostType,
    val costAmount: Double,
    val costItemType: String?,
    val costItemAmount: Int,
    val animationDuration: Int,
    val enabled: Boolean,
    val createdAt: Timestamp,
    val updatedAt: Timestamp
)

/**
 * 룰렛 아이템 데이터 클래스 (다중 룰렛 지원)
 */
data class RouletteItem(
    val id: Int,
    val rouletteId: Int,
    val itemProvider: ItemProvider,
    val itemIdentifier: String,
    val itemDisplayName: String?,
    val itemAmount: Int,
    val itemData: String?, // JSON 형태
    val weight: Double, // 가중치 (소수점 2자리까지 지원)
    val enabled: Boolean,
    val createdAt: Timestamp,
    val updatedAt: Timestamp
) {
    /**
     * ItemStack으로 변환
     */
    fun toItemStack(): ItemStack? {
        return when (itemProvider) {
            ItemProvider.VANILLA -> {
                try {
                    val material = Material.valueOf(itemIdentifier)
                    val itemStack = ItemStack(material, itemAmount)

                    // 표시 이름 설정
                    if (itemDisplayName != null) {
                        val meta = itemStack.itemMeta
                        meta?.setDisplayName(itemDisplayName)
                        itemStack.itemMeta = meta
                    }

                    itemStack
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            ItemProvider.NEXO -> {
                // Nexo 아이템 생성 (Nexo API 사용)
                try {
                    val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
                    val method = nexoClass.getMethod("itemFromId", String::class.java)
                    val itemBuilder = method.invoke(null, itemIdentifier)

                    if (itemBuilder != null) {
                        val buildMethod = itemBuilder.javaClass.getMethod("build")
                        val item = buildMethod.invoke(itemBuilder) as? ItemStack
                        item?.amount = itemAmount
                        item
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            ItemProvider.ORAXEN -> {
                // Oraxen 아이템 생성 (Oraxen API 사용)
                try {
                    val oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems")
                    val method = oraxenClass.getMethod("getItemById", String::class.java)
                    val item = method.invoke(null, itemIdentifier) as? ItemStack
                    item?.amount = itemAmount
                    item
                } catch (e: Exception) {
                    null
                }
            }
            ItemProvider.ITEMSADDER -> {
                // ItemsAdder 아이템 생성
                try {
                    val iaClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
                    val method = iaClass.getMethod("getInstance", String::class.java)
                    val customStack = method.invoke(null, itemIdentifier)

                    if (customStack != null) {
                        val getItemStackMethod = customStack.javaClass.getMethod("getItemStack")
                        val item = getItemStackMethod.invoke(customStack) as? ItemStack
                        item?.amount = itemAmount
                        item
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

/**
 * 룰렛 플레이 히스토리 데이터 클래스 (다중 룰렛 지원)
 */
data class RouletteHistory(
    val id: Long,
    val rouletteId: Int,
    val playerUuid: String,
    val playerName: String,
    val itemId: Int,
    val itemProvider: String,
    val itemIdentifier: String,
    val costPaid: Double,
    val probability: Double,
    val playedAt: Timestamp
)

/**
 * 트리거 타입 (NPC 또는 Nexo 가구)
 */
enum class TriggerType {
    NPC,
    NEXO
}

/**
 * 룰렛 트리거 매핑 데이터 클래스 (NPC + Nexo 통합)
 */
data class RouletteTriggerMapping(
    val id: Int,
    val type: TriggerType,
    val identifier: String, // NPC ID(숫자 문자열) 또는 Nexo 아이템 코드
    val rouletteId: Int,
    val createdAt: Timestamp
)

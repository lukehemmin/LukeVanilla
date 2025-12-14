package com.lukehemmin.lukeVanilla.System.ScrollRoulette

import com.lukehemmin.lukeVanilla.System.Roulette.ItemProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.sql.Timestamp

/**
 * 스크롤 룰렛 설정 데이터 클래스
 */
data class ScrollRouletteConfig(
    val id: Int,
    val rouletteName: String,
    val itemProvider: ItemProvider,
    val itemCode: String,
    val enabled: Boolean,
    val createdAt: Timestamp,
    val updatedAt: Timestamp
)

/**
 * 스크롤 룰렛 당첨 아이템 데이터 클래스
 */
data class ScrollRouletteItem(
    val id: Int,
    val rouletteId: Int,
    val itemProvider: ItemProvider,
    val itemCode: String,
    val itemDisplayName: String?,
    val itemAmount: Int,
    val probability: Double, // 확률 (%)
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
                    val material = Material.valueOf(itemCode.uppercase())
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
                    val itemBuilder = method.invoke(null, itemCode)

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
                    val item = method.invoke(null, itemCode) as? ItemStack
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
                    val customStack = method.invoke(null, itemCode)

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
 * 스크롤 룰렛 플레이 히스토리 데이터 클래스
 */
data class ScrollRouletteHistory(
    val id: Long,
    val rouletteId: Int,
    val playerUuid: String,
    val playerName: String,
    val itemId: Int,
    val itemProvider: String,
    val itemCode: String,
    val winProbability: Double, // 실제 당첨 확률 (%)
    val playedAt: Timestamp
)

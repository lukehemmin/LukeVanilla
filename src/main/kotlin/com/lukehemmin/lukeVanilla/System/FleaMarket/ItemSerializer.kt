package com.lukehemmin.lukeVanilla.System.FleaMarket

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * 아이템 직렬화/역직렬화 유틸리티
 */
object ItemSerializer {
    
    /**
     * ItemStack을 Base64 문자열로 직렬화
     */
    fun serialize(item: ItemStack): String {
        try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            dataOutput.writeObject(item)
            dataOutput.close()
            return Base64.getEncoder().encodeToString(outputStream.toByteArray())
        } catch (e: Exception) {
            throw RuntimeException("아이템 직렬화 실패", e)
        }
    }
    
    /**
     * Base64 문자열을 ItemStack으로 역직렬화
     */
    fun deserialize(data: String): ItemStack {
        try {
            val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(data))
            val dataInput = BukkitObjectInputStream(inputStream)
            val item = dataInput.readObject() as ItemStack
            dataInput.close()
            return item
        } catch (e: Exception) {
            throw RuntimeException("아이템 역직렬화 실패", e)
        }
    }
    
    /**
     * Material 이름을 사람이 읽을 수 있는 형태로 변환
     * 예: "NETHERITE_SWORD" → "Netherite Sword"
     */
    fun formatMaterialName(material: Material): String {
        return material.name
            .lowercase()
            .split('_')
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
    
    /**
     * ItemStack의 표시 이름 추출
     * 커스텀 displayName이 있으면 그것을 사용하고, 없으면 포맷된 Material 이름 반환
     */
    fun getDisplayName(itemStack: ItemStack): String {
        return if (itemStack.hasItemMeta() && itemStack.itemMeta?.hasDisplayName() == true) {
            itemStack.itemMeta?.displayName ?: formatMaterialName(itemStack.type)
        } else {
            formatMaterialName(itemStack.type)
        }
    }
}

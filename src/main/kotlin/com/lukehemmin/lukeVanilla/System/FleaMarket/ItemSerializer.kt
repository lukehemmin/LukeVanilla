package com.lukehemmin.lukeVanilla.System.FleaMarket

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
}

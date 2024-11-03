package com.lukehemmin.lukeVanilla.System.Items

import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerItemMendEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class DurabilityListener(private val plugin: JavaPlugin) : Listener {
    @EventHandler
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val player = event.player
        val item = event.item
        val maxDurability = item.type.maxDurability
        val currentDurability = item.durability

        val meta = item.itemMeta ?: return
        val key10 = NamespacedKey(plugin, "durability_warned_10")
        val key5 = NamespacedKey(plugin, "durability_warned_5")
        val key2 = NamespacedKey(plugin, "durability_warned_2")

        // 내구도가 10% 이하일 때 경고 메시지와 사운드 재생
        if (currentDurability >= maxDurability * 0.9 && meta.persistentDataContainer.get(key10, PersistentDataType.BYTE) == null) {
            player.sendTitle("§c§l내구도 부족", "§c§l아이템의 내구도가 부족합니다!", 10, 70, 20)
            player.playSound(player.location, "minecraft:block.bell.use", 1.0f, 1.0f)
            meta.persistentDataContainer.set(key10, PersistentDataType.BYTE, 1)
        }

        // 내구도가 5% 이하일 때 경고 메시지와 사운드 재생
        if (currentDurability >= maxDurability * 0.95 && meta.persistentDataContainer.get(key5, PersistentDataType.BYTE) == null) {
            player.sendTitle("§c§l내구도 매우 부족", "§c§l아이템의 내구도가 매우 부족합니다!", 10, 70, 20)
            player.playSound(player.location, "minecraft:block.bell.use", 1.0f, 1.0f)
            meta.persistentDataContainer.set(key5, PersistentDataType.BYTE, 1)
        }

        // 내구도가 2% 이하일 때 경고 메시지와 사운드 재생
        if (currentDurability >= maxDurability * 0.98 && meta.persistentDataContainer.get(key2, PersistentDataType.BYTE) == null) {
            player.sendTitle("§c§l내구도 심각", "§c§l아이템의 내구도가 심각하게 부족합니다!", 10, 70, 20)
            player.playSound(player.location, "minecraft:block.bell.use", 1.0f, 1.0f)
            meta.persistentDataContainer.set(key2, PersistentDataType.BYTE, 1)
        }

        item.itemMeta = meta
    }

    @EventHandler
    fun onItemMend(event: PlayerItemMendEvent) {
        val item = event.item
        val meta = item.itemMeta ?: return
        val key10 = NamespacedKey(plugin, "durability_warned_10")
        val key5 = NamespacedKey(plugin, "durability_warned_5")
        val key2 = NamespacedKey(plugin, "durability_warned_2")

        // 아이템이 수리되었을 때 메타데이터 초기화
        if (item.durability < item.type.maxDurability * 0.98) {
            meta.persistentDataContainer.remove(key2)
        }
        if (item.durability < item.type.maxDurability * 0.95) {
            meta.persistentDataContainer.remove(key5)
        }
        if (item.durability < item.type.maxDurability * 0.9) {
            meta.persistentDataContainer.remove(key10)
        }

        item.itemMeta = meta
    }

    @EventHandler
    fun onItemBreak(event: PlayerItemBreakEvent) {
        val player = event.player
        player.sendTitle("§c§l아이템이 부서졌습니다.", "", 10, 70, 20)
        player.playSound(player.location, "minecraft:block.anvil.break", 1.0f, 1.0f)
    }
}
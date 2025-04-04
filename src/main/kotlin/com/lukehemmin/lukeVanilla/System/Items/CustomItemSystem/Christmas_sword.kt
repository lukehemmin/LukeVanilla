package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.persistence.PersistentDataType

class Christmas_sword(val plugin: JavaPlugin) : Listener {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val item = killer.inventory.itemInMainHand
        val itemId = NexoItems.idFromItem(item) ?: return

        if (itemId != "merry_christmas_sword") return

        val meta: ItemMeta = item.itemMeta ?: return
        val nbt = meta.persistentDataContainer
        val kills = nbt.get(namespacedKey("toolstats", "mob-kills"), PersistentDataType.INTEGER) ?: 0

        //plugin.logger.info("Player ${killer.name} has $kills kills with merry_christmas_sword.")

        if (kills >= 5000) {
            val newItemBuilder = NexoItems.itemFromId("merry_christmas_greatsword")?.build() ?: run{
                plugin.logger.warning("merry_christmas_greatsword 아이템이 존재하지 않습니다.")
                return
            }

            val newMeta: ItemMeta = newItemBuilder.itemMeta ?: return

            // 인첸트 복사
            meta.enchants.forEach { (ench, level) ->
                newMeta.addEnchant(ench, level, true)
            }

            // 기존 NBT 복사
            newMeta.persistentDataContainer.set(namespacedKey("toolstats", "mob-kills"), PersistentDataType.INTEGER, kills)
            newItemBuilder.itemMeta = newMeta

            // 아이템 교체
            killer.inventory.setItemInMainHand(newItemBuilder)
            killer.sendMessage("크리스마스 검이 대검으로 바뀌었어요!")
            plugin.logger.info("merry_christmas_sword가 merry_christmas_greatsword로 업그레이드되었습니다.")
        }
    }

    private fun namespacedKey(namespace: String, key: String) =
        org.bukkit.NamespacedKey(namespace, key)
}
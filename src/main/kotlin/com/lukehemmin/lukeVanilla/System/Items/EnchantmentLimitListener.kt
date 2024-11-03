package com.lukehemmin.lukeVanilla.System.Items

import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.entity.Player

class EnchantmentLimitListener : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return

        // 아이템의 모든 인챈트에 대해 반복
        for (enchantment in item.enchantments.keys) {
            val level = item.getEnchantmentLevel(enchantment)
            // 인챈트 레벨이 10을 초과하면
            if (level > 10) {
                event.isCancelled = true
                event.player.inventory.remove(item)
                event.player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}The item has exceeded the calculable range of the server. This error has been reported to the developer.")
                return
            }
        }
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val item = player.inventory.getItem(event.newSlot) ?: return

        for (enchantment in item.enchantments.keys) {
            val level = item.getEnchantmentLevel(enchantment)
            if (level > 10) {
                event.isCancelled = true
                player.inventory.remove(item)
                player.sendMessage("${ChatColor.RED}${ChatColor.BOLD}The item has exceeded the calculable range of the server. This error has been reported to the developer.")
                return
            }
        }
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player) {
            val item = damager.inventory.itemInMainHand

            for (enchantment in item.enchantments.keys) {
                val level = item.getEnchantmentLevel(enchantment)
                if (level > 10) {
                    event.isCancelled = true
                    damager.inventory.remove(item)
                    damager.sendMessage("${ChatColor.RED}${ChatColor.BOLD}The item has exceeded the calculable range of the server. This error has been reported to the developer.")
                    return
                }
            }
        }
    }
}
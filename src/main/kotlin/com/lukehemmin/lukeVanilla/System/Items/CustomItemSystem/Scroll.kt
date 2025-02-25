package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable

class Scroll : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val cursor = event.cursor ?: return
        val current = event.currentItem ?: return

        // 커서의 아이템이 scroll인지 확인
        val nexoId = NexoItems.idFromItem(cursor) ?: return
        if (nexoId != "scroll") return

        // 현재 클릭된 아이템이 수리 가능한지 확인
        if (!isRepairable(current)) return

        event.isCancelled = true

        // 아이템 수리
        repairItem(current)

        // 스크롤 아이템 1개 감소
        if (cursor.amount > 1) {
            cursor.amount = cursor.amount - 1
        } else {
            event.view.setCursor(null) // 수정된 부분
        }

        event.whoClicked.sendMessage("§a아이템이 성공적으로 수리되었습니다.")
    }

    private fun isRepairable(item: ItemStack): Boolean {
        if (item.type == Material.AIR) return false

        val meta = item.itemMeta
        return meta is Damageable && (meta as Damageable).damage > 0
    }

    private fun repairItem(item: ItemStack) {
        val meta = item.itemMeta as Damageable
        meta.damage = 0
        item.itemMeta = meta
    }
}
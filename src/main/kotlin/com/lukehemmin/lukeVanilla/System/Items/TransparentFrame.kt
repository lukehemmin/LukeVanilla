package com.lukehemmin.lukeVanilla.System.Items

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class TransparentFrame : Listener {
    companion object {
        private const val TRANSPARENT_FRAME_KEY = "transparent_frame"

        fun createTransparentFrame(): ItemStack {
            return ItemStack(Material.ITEM_FRAME).apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("${ChatColor.AQUA}투명 아이템 액자")
                    meta.lore = listOf(
                        "${ChatColor.GRAY}아이템을 넣으면 액자가 투명해집니다.",
                        "${ChatColor.GRAY}아이템을 제거하면 액자가 다시 보입니다."
                    )
                    // 커스텀 태그 추가
                    meta.persistentDataContainer.set(
                        org.bukkit.NamespacedKey.fromString(TRANSPARENT_FRAME_KEY)!!,
                        PersistentDataType.STRING,
                        "true"
                    )
                }
            }
        }
    }

    @EventHandler
    fun onFramePlace(event: HangingPlaceEvent) {
        val itemFrame = event.entity as? ItemFrame ?: return
        val player = event.player ?: return  // null이면 함수 종료
        val item = player.inventory.itemInMainHand  // 이제 안전하게 접근 가능

        if (isTransparentFrame(item)) {
            itemFrame.isVisible = true
            itemFrame.persistentDataContainer.set(
                org.bukkit.NamespacedKey.fromString(TRANSPARENT_FRAME_KEY)!!,
                PersistentDataType.STRING,
                "true"
            )
        }
    }

    @EventHandler
    fun onFrameItemChange(event: PlayerInteractEntityEvent) {
        val itemFrame = event.rightClicked as? ItemFrame ?: return
        if (isTransparentItemFrame(itemFrame)) {
            // 다음 틱에 상태 확인
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TransparentFrame::class.java),
                Runnable {
//                    println("[디버그] 액자 상태 변경:")
//                    println("- 아이템: ${itemFrame.item.type}")
//                    println("- 보이기 상태: ${itemFrame.isVisible}")

                    val hasItem = itemFrame.item.type != Material.AIR
                    itemFrame.isVisible = !hasItem
                },
                1L
            )
        }
    }

    @EventHandler
    fun onFrameItemRemove(event: EntityDamageByEntityEvent) {
        val itemFrame = event.entity as? ItemFrame ?: return
        val player = event.damager as? Player ?: return

        if (isTransparentItemFrame(itemFrame)) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TransparentFrame::class.java),
                Runnable {
                    itemFrame.isVisible = itemFrame.item.type == Material.AIR
                },
                1L
            )
        }
    }

    @EventHandler
    fun onFrameBreak(event: HangingBreakByEntityEvent) {
        val itemFrame = event.entity as? ItemFrame ?: return

        // 투명 액자인지 확인
        if (isTransparentItemFrame(itemFrame)) {
            event.isCancelled = true
            itemFrame.remove()

            // 아이템 드롭
            itemFrame.world.dropItemNaturally(itemFrame.location, createTransparentFrame())
            // 액자 안의 아이템도 드롭
            if (itemFrame.item.type != Material.AIR) {
                itemFrame.world.dropItemNaturally(itemFrame.location, itemFrame.item.clone())
            }
        }
    }

    @EventHandler
    fun onFrameBreakByBlock(event: HangingBreakEvent) {
        if (event is HangingBreakByEntityEvent) return

        val itemFrame = event.entity as? ItemFrame ?: return

        if (isTransparentItemFrame(itemFrame)) {
            event.isCancelled = true
            itemFrame.remove()

            // 수정된 부분: 투명 액자 아이템을 드롭
            itemFrame.world.dropItemNaturally(itemFrame.location, createTransparentFrame())
            // 액자 안의 아이템도 드롭
            if (itemFrame.item.type != Material.AIR) {
                itemFrame.world.dropItemNaturally(itemFrame.location, itemFrame.item.clone())
            }
        }
    }

    private fun isTransparentFrame(item: ItemStack): Boolean {
        return item.itemMeta?.persistentDataContainer?.has(
            org.bukkit.NamespacedKey.fromString(TRANSPARENT_FRAME_KEY)!!,
            PersistentDataType.STRING
        ) == true
    }

    private fun isTransparentItemFrame(itemFrame: ItemFrame): Boolean {
        return itemFrame.persistentDataContainer.has(
            org.bukkit.NamespacedKey.fromString(TRANSPARENT_FRAME_KEY)!!,
            PersistentDataType.STRING
        )
    }
}
package com.lukehemmin.lukeVanilla.System.LockSystem

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class LockListener(private val plugin: Main, private val lockManager: BlockLockManager) : Listener {
    
    // Shift + 우클릭 이벤트 처리
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!event.player.isSneaking) return
        
        val block = event.clickedBlock ?: return
        if (!lockManager.isLockableBlock(block)) return

        event.isCancelled = true
        LockMenuGUI(plugin, lockManager, block).openGUI(event.player)
    }

    // 권한 추가를 위한 채팅 입력 처리
    fun startAddingPermission(block: Block) {
        // TODO: 채팅 입력 리스너 구현
    }
}

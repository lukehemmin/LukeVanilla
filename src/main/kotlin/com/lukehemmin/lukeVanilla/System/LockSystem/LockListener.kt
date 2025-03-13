package com.lukehemmin.lukeVanilla.System.LockSystem

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent

class LockListener(private val lockManager: BlockLockManager) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        val player = event.player

        if (lockManager.isLockableBlock(block)) {
            lockManager.lockBlock(block, player)
            player.sendMessage("블록이 자동으로 잠겼습니다.") // 메시지 한국어로 변경
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        val player = event.player

        if (lockManager.isBlockLocked(block)) {
            if (!lockManager.canPlayerAccessBlock(block, player)) {
                event.isCancelled = true
                player.sendMessage("잠긴 블록입니다.") // 메시지 한국어로 변경
                return
            }

            if (player.isSneaking) {
                // TODO: 잠금 메뉴 GUI 표시 (상자 열기/권한 설정)
                player.sendMessage("잠금 메뉴 GUI 표시") // 메시지 한국어로 변경
            }
        }
    }
}

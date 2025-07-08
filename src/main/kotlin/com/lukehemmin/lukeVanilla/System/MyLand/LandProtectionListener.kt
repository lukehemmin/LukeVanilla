package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class LandProtectionListener(private val landManager: LandManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val chunk = event.block.chunk
        val ownerId = landManager.getOwnerOfChunk(chunk)

        if (ownerId != null && ownerId != player.uniqueId && !player.hasPermission("lukevanilla.land.admin")) {
            event.isCancelled = true
            player.sendMessage(Component.text("다른 사람의 땅을 수정할 수 없습니다.", NamedTextColor.RED))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val chunk = event.block.chunk
        val ownerId = landManager.getOwnerOfChunk(chunk)

        if (ownerId != null && ownerId != player.uniqueId && !player.hasPermission("lukevanilla.land.admin")) {
            event.isCancelled = true
            player.sendMessage(Component.text("다른 사람의 땅을 수정할 수 없습니다.", NamedTextColor.RED))
        }
    }
} 
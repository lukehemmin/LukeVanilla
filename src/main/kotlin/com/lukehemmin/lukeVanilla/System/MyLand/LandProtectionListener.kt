package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent

class LandProtectionListener(private val landManager: LandManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val chunk = event.block.chunk
        val ownerId = landManager.getOwnerOfChunk(chunk) ?: return
        val claimType = landManager.getClaimType(chunk)

        if (claimType == "FARM_VILLAGE") {
            event.isCancelled = true
            player.sendMessage(Component.text("농사마을 땅에서는 블록을 파괴할 수 없습니다.", NamedTextColor.RED))
            return
        }

        if (ownerId != player.uniqueId && !landManager.isMember(chunk, player) && !player.hasPermission("myland.admin.bypass")) {
            event.isCancelled = true
            player.sendMessage(Component.text("다른 사람의 땅을 수정할 수 없습니다.", NamedTextColor.RED))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val chunk = event.block.chunk
        val ownerId = landManager.getOwnerOfChunk(chunk) ?: return
        val claimType = landManager.getClaimType(chunk)

        if (claimType == "FARM_VILLAGE") {
            event.isCancelled = true
            player.sendMessage(Component.text("농사마을 땅에서는 블록을 설치할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        if (ownerId != player.uniqueId && !landManager.isMember(chunk, player) && !player.hasPermission("myland.admin.bypass")) {
            event.isCancelled = true
            player.sendMessage(Component.text("다른 사람의 땅을 수정할 수 없습니다.", NamedTextColor.RED))
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock ?: return
        val chunk = block.chunk
        val ownerId = landManager.getOwnerOfChunk(chunk) ?: return
        val claimType = landManager.getClaimType(chunk)

        if (claimType == "FARM_VILLAGE") {
             // Allow interaction with specific farm-related blocks, otherwise cancel.
             // This part can be expanded later. For now, we cancel general interactions.
             if (!player.isSneaking) { // Example condition
                event.isCancelled = true
                player.sendMessage(Component.text("농사마을 땅에서는 상호작용이 제한됩니다.", NamedTextColor.RED))
             }
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        val startChunk = event.block.chunk
        val claimTypeStart = landManager.getClaimType(startChunk)

        for (block in event.blocks) {
            val endChunk = block.location.add(event.direction.modX, event.direction.modY, event.direction.modZ).chunk
            if (startChunk == endChunk) continue

            val claimTypeEnd = landManager.getClaimType(endChunk)

            if (claimTypeStart == "FARM_VILLAGE" || claimTypeEnd == "FARM_VILLAGE") {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        val startChunk = event.block.chunk
        val claimTypeStart = landManager.getClaimType(startChunk)

        for (block in event.blocks) {
            val endChunk = block.chunk
            if (startChunk == endChunk) continue

            val claimTypeEnd = landManager.getClaimType(endChunk)

            if (claimTypeStart == "FARM_VILLAGE" || claimTypeEnd == "FARM_VILLAGE") {
                 event.isCancelled = true
                 return
            }
        }
    }
} 
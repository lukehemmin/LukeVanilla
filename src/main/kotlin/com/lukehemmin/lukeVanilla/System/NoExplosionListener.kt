package com.lukehemmin.lukeVanilla.System

import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.plugin.java.JavaPlugin

class NoExplosionListener(private val plugin: JavaPlugin) : Listener {

    private fun isExplosionAllowedWorld(world: World): Boolean {
        return world.environment == World.Environment.NETHER || world.environment == World.Environment.THE_END
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (isExplosionAllowedWorld(event.location.world!!)) return

        val entity = event.entity
        // Wither와 WitherSkull 추가
        if (entity is Creeper || entity is TNTPrimed || entity is Wither || entity is WitherSkull) {
            event.isCancelled = true
            event.blockList().clear()
            plugin.logger.info("Cancelled explosion from ${entity.type} in ${event.location.world?.name}")
        }
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (isExplosionAllowedWorld(event.block.world)) return // 네더와 엔더에서는 폭발 허용

        event.isCancelled = true
        event.blockList().clear()
        plugin.logger.info("Cancelled block explosion at ${event.block.location}")
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (isExplosionAllowedWorld(event.entity.world)) return

        val damager = event.damager
        // Wither와 WitherSkull에 의한 데미지 취소
        if (damager is TNTPrimed || damager is Creeper || damager is Wither || damager is WitherSkull) {
            event.isCancelled = true
            plugin.logger.info("Cancelled damage to ${event.entityType} from ${damager.type} in ${event.entity.world.name}")
        }
    }

    @EventHandler
    fun onHangingBreak(event: HangingBreakEvent) {
        if (isExplosionAllowedWorld(event.entity.world)) return // 네더와 엔더에서는 파괴 허용

        if (event.cause == HangingBreakEvent.RemoveCause.EXPLOSION) {
            event.isCancelled = true
            plugin.logger.info("Cancelled hanging entity break due to explosion at ${event.entity.location}")
        }
    }

    @EventHandler
    fun onHangingBreakByEntity(event: HangingBreakByEntityEvent) {
        if (isExplosionAllowedWorld(event.entity.world)) return

        val remover = event.remover
        // Wither와 WitherSkull 추가
        if (remover is TNTPrimed || remover is Creeper || remover is Wither || remover is WitherSkull) {
            event.isCancelled = true
            plugin.logger.info("Cancelled hanging entity break by ${remover.type} at ${event.entity.location}")
        }
    }

    @EventHandler
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val vehicle = event.vehicle
        val attacker = event.attacker

        if (!isExplosionAllowedWorld(vehicle.world)) {
            if (attacker == null) {
                if (vehicle is Boat) {
                    event.isCancelled = true
                    plugin.logger.info("Cancelled boat destruction due to explosion at ${vehicle.location}")
                }
            } else if (attacker is TNTPrimed || attacker is Creeper || attacker is Wither || attacker is WitherSkull) {
                event.isCancelled = true
                plugin.logger.info("Cancelled vehicle destruction by ${attacker.type} at ${vehicle.location}")
            }
        }
    }

    @EventHandler
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (isExplosionAllowedWorld(event.block.world)) return

        if (event.entity is Wither) {
            event.isCancelled = true
            plugin.logger.info("Cancelled Wither block destruction at ${event.block.location}")
        }
    }
}
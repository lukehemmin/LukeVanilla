package com.lukehemmin.lukeVanilla.System

import org.bukkit.World
import org.bukkit.entity.Boat
import org.bukkit.entity.Creeper
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.block.BlockExplodeEvent
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
        if (isExplosionAllowedWorld(event.location.world!!)) return // 네더와 엔더에서는 폭발 허용

        val entity = event.entity
        if (entity is Creeper || entity is TNTPrimed) {
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
        if (isExplosionAllowedWorld(event.entity.world)) return // 네더와 엔더에서는 데미지 허용

        val damager = event.damager
        if (damager is TNTPrimed || damager is Creeper) {
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
        if (isExplosionAllowedWorld(event.entity.world)) return // 네더와 엔더에서는 파괴 허용

        val remover = event.remover
        if (remover is TNTPrimed || remover is Creeper) {
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
                // TNT나 크리퍼의 폭발로 인한 간접적인 파괴
                if (vehicle is Boat) {
                    event.isCancelled = true
                    plugin.logger.info("Cancelled boat destruction due to explosion at ${vehicle.location}")
                }
            } else if (attacker is TNTPrimed || attacker is Creeper) {
                // TNT 엔티티나 크리퍼에 의한 직접적인 파괴
                event.isCancelled = true
                plugin.logger.info("Cancelled vehicle destruction by ${attacker.type} at ${vehicle.location}")
            }
        }
    }
}
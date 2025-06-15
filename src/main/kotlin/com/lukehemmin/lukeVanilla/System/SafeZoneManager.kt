package com.lukehemmin.lukeVanilla.System

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector

class SafeZoneManager(private val plugin: JavaPlugin) : Listener {

    // 안전 구역 좌표 정의 (최소 X,Y,Z 및 최대 X,Y,Z)
    private val safeZoneMin = Vector(-16, 55, 55)
    private val safeZoneMax = Vector(16, 88, 79)

    init {
        plugin.logger.info("[SafeZoneManager] 좌표 범위 (-16, 55, 55) ~ (16, 88, 79)에 안전 구역이 설정되었습니다.")
    }

    // 관리자 권한 확인을 위한 퍼미션
    private val adminPermission = "lukevanilla.admin.safezone"

    // 특정 위치가 안전 구역 내에 있는지 확인
    fun isInSafeZone(location: Location): Boolean {
        // 3D 좌표 검사
        val pos = location.toVector()
        return pos.x >= safeZoneMin.x && pos.x <= safeZoneMax.x &&
               pos.y >= safeZoneMin.y && pos.y <= safeZoneMax.y &&
               pos.z >= safeZoneMin.z && pos.z <= safeZoneMax.z
    }

    // 블록 파괴 이벤트 처리
    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        // 안전 구역 내에서 발생한 이벤트인지 확인
        if (isInSafeZone(block.location)) {
            // 관리자 권한이 없는 경우 이벤트 취소
            if (!player.hasPermission(adminPermission)) {
                event.isCancelled = true
                player.sendMessage("§c이 구역에서는 블록을 파괴할 수 없습니다.")
            }
        }
    }

    // 블록 설치 이벤트 처리
    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val block = event.block

        // 안전 구역 내에서 발생한 이벤트인지 확인
        if (isInSafeZone(block.location)) {
            // 관리자 권한이 없는 경우 이벤트 취소
            if (!player.hasPermission(adminPermission)) {
                event.isCancelled = true
                player.sendMessage("§c이 구역에서는 블록을 설치할 수 없습니다.")
            }
        }
    }

    // 엔티티 데미지 이벤트 처리
    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity        // 플레이어만 처리하고 안전 구역 내에 있는지 확인
        if (entity is Player && isInSafeZone(entity.location)) {
            // 모든 데미지 취소
            event.isCancelled = true
        }
    }
}

package com.lukehemmin.lukeVanilla.System.SitSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.util.Vector
import java.util.*

class SitManager(private val plugin: Main) : Listener {

    // 플레이어 상태 관리
    private val sittingPlayers = mutableMapOf<UUID, ArmorStand>()
    private val layingPlayers = mutableMapOf<UUID, ArmorStand>()
    private val crawlingPlayers = mutableMapOf<UUID, Boolean>()
    // 플레이어 탑승 관계를 추적하는 맵 (탑승자 UUID -> 대상 UUID)
    private val mountedPlayers = mutableMapOf<UUID, UUID>()

    // 앉을 수 있는 블록 목록
    private val sittableBlocks = listOf(
        Material.STONE_STAIRS, Material.OAK_STAIRS, Material.SPRUCE_STAIRS, 
        Material.BIRCH_STAIRS, Material.JUNGLE_STAIRS, Material.ACACIA_STAIRS, 
        Material.DARK_OAK_STAIRS, Material.CRIMSON_STAIRS, Material.WARPED_STAIRS,
        Material.STONE_SLAB, Material.OAK_SLAB, Material.SPRUCE_SLAB, 
        Material.BIRCH_SLAB, Material.JUNGLE_SLAB, Material.ACACIA_SLAB, 
        Material.DARK_OAK_SLAB, Material.CRIMSON_SLAB, Material.WARPED_SLAB
    )

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * 계단이나 반블럭에 우클릭 시 앉기 처리
     */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || !event.player.isSneaking) return
        
        val block = event.clickedBlock ?: return
        if (!isSittableBlock(block)) return
        
        val player = event.player
        if (isPlayerSitting(player)) {
            event.isCancelled = true
            return
        }

        // 플레이어가 앉아있지 않은 경우 앉기 실행
        event.isCancelled = true
        makeSitOnBlock(player, block)
    }

    /**
     * 플레이어 클릭 시 머리 위에 탑승 처리
     */
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val clickedEntity = event.rightClicked
        
        // 클릭한 엔티티가 플레이어인 경우만 처리
        if (clickedEntity !is Player) return
        
        val targetPlayer = clickedEntity
        val rider = event.player
        
        // 탑승자가 쉬프트 키를 누르고 있을 때만 작동
        if (!rider.isSneaking) return
        
        // 자신은 탑승할 수 없음
        if (targetPlayer.uniqueId == rider.uniqueId) return
        
        // 이미 앉아있거나 누워있는 경우 처리하지 않음
        if (isPlayerSitting(rider) || isPlayerLaying(rider)) return
        
        mountPlayer(rider, targetPlayer)
    }

    /**
     * 플레이어가 앉을 수 있는 블록인지 확인
     */
    private fun isSittableBlock(block: Block): Boolean {
        return sittableBlocks.contains(block.type)
    }

    /**
     * 지정된 블록 위에 앉기
     */
    private fun makeSitOnBlock(player: Player, block: Block) {
        val adjustedY = if (block.type.toString().contains("SLAB")) {
            block.y + 0.5 
        } else {
            block.y + 0.5
        }

        val sitLoc = Location(block.world, 
            block.x + 0.5, 
            adjustedY, 
            block.z + 0.5)

        sit(player, sitLoc)
    }

    /**
     * 플레이어를 특정 위치에 앉히기
     */
    fun sit(player: Player, location: Location) {
        if (isPlayerSitting(player) || isPlayerLaying(player)) {
            return
        }

        val armorStand = createSeatArmorStand(location)
        player.teleport(location)
        
        // 아머스탠드에 탑승
        armorStand.addPassenger(player)
        
        // 상태 저장
        sittingPlayers[player.uniqueId] = armorStand
        
        // 메타데이터 설정
        player.setMetadata("sitting", FixedMetadataValue(plugin, true))
        
        player.sendMessage("§a앉았습니다. 쉬프트 키를 눌러 일어나세요.")
    }

    /**
     * 플레이어를 눕히기
     */
    fun lay(player: Player) {
        if (isPlayerSitting(player) || isPlayerLaying(player)) {
            return
        }

        // 플레이어 위치 조정 (바닥에 눕히기)
        val layLocation = player.location.clone().add(0.0, -0.7, 0.0)
        val armorStand = createLayArmorStand(layLocation)
        
        // 아머스탠드에 탑승
        armorStand.addPassenger(player)
        
        // 상태 저장
        layingPlayers[player.uniqueId] = armorStand
        
        // 메타데이터 설정
        player.setMetadata("laying", FixedMetadataValue(plugin, true))
        
        player.sendMessage("§a누웠습니다. 쉬프트 키를 눌러 일어나세요.")
    }

    /**
     * 플레이어 머리 위에 탑승 (직접 Entity의 addPassenger 사용)
     */
    fun mountPlayer(rider: Player, target: Player) {
        // 중첩 크기에 따라 높이 조정: 타겟 위에 탑승한 다른 플레이어 수 계산
        val stackHeight = countStackedPlayers(target.uniqueId)
        
        // 중첩된 경우 시각적 표시 - 높이 차이
        if (stackHeight > 0) {
            rider.sendMessage("§a이미 ${stackHeight}명이 탑승 중입니다. 위에 추가로 탑승합니다.")
        }
        
        // 이전 탑승 상태 해제
        if (rider.isInsideVehicle)
            rider.leaveVehicle()
        
        // 탑승
        target.addPassenger(rider)
        
        // 탑승 관계 기록
        mountedPlayers[rider.uniqueId] = target.uniqueId
        
        // 메타데이터 설정
        rider.setMetadata("mounted", FixedMetadataValue(plugin, true))
        
        rider.sendMessage("§a플레이어 ${target.name}의 머리 위에 탑승했습니다. 쉬프트 키를 눌러 내릴 수 있습니다.")
    }

    private fun countStackedPlayers(targetUuid: UUID): Int {
        // 타겟 위에 올라탄 플레이어 수 계산
        return mountedPlayers.count { it.value == targetUuid }
    }

    /**
     * 플레이어 기어다니기 모드 설정
     */
    fun toggleCrawl(player: Player) {
        if (isPlayerSitting(player) || isPlayerLaying(player)) {
            return
        }

        if (crawlingPlayers.containsKey(player.uniqueId)) {
            // 기어다니기 해제
            crawlingPlayers.remove(player.uniqueId)
            player.removeMetadata("crawling", plugin)
            player.sendMessage("§a기어다니기를 해제했습니다.")
        } else {
            // 기어다니기 설정
            crawlingPlayers[player.uniqueId] = true
            player.setMetadata("crawling", FixedMetadataValue(plugin, true))
            
            // 수영 애니메이션 적용 (기어다니기 효과)
            player.isSwimming = true
            
            player.sendMessage("§a기어다니기를 시작했습니다. 다시 명령어를 입력하여 해제할 수 있습니다.")
        }
    }

    /**
     * 앉기/눕기용 아머스탠드 생성
     */
    private fun createSeatArmorStand(location: Location): ArmorStand {
        val armorStand = location.world!!.spawnEntity(location, EntityType.ARMOR_STAND) as ArmorStand
        armorStand.isVisible = false
        armorStand.isSmall = true
        armorStand.setGravity(false)
        armorStand.isMarker = true
        armorStand.isInvulnerable = true
        armorStand.setMetadata("seat", FixedMetadataValue(plugin, true))
        return armorStand
    }

    /**
     * 눕기용 아머스탠드 생성 (회전 적용)
     */
    private fun createLayArmorStand(location: Location): ArmorStand {
        val armorStand = createSeatArmorStand(location)
        // 수평으로 누워있는 효과를 위한 설정
        armorStand.setRotation(location.yaw, 90f)
        return armorStand
    }

    /**
     * 플레이어 상태 확인 함수들
     */
    fun isPlayerSitting(player: Player): Boolean = sittingPlayers.containsKey(player.uniqueId)
    fun isPlayerLaying(player: Player): Boolean = layingPlayers.containsKey(player.uniqueId)
    fun isPlayerCrawling(player: Player): Boolean = crawlingPlayers.containsKey(player.uniqueId)
    fun isPlayerMounted(player: Player): Boolean = mountedPlayers.containsKey(player.uniqueId)

    /**
     * 플레이어 앉기/눕기 상태 해제
     */
    fun unsitPlayer(player: Player) {
        sittingPlayers[player.uniqueId]?.remove()
        sittingPlayers.remove(player.uniqueId)
        player.removeMetadata("sitting", plugin)
    }

    fun unlayPlayer(player: Player) {
        layingPlayers[player.uniqueId]?.remove()
        layingPlayers.remove(player.uniqueId)
        player.removeMetadata("laying", plugin)
    }

    /**
     * 플레이어 탑승 상태 해제
     */
    fun dismountPlayer(player: Player) {
        // 플레이어가 다른 엔티티에 탑승 중인 경우 체크
        if (player.isInsideVehicle) {
            player.leaveVehicle()
        }
        
        mountedPlayers.remove(player.uniqueId)
        player.removeMetadata("mounted", plugin)
    }

    /**
     * 플레이어 나갈 때 모든 상태 정리
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        unsitPlayer(player)
        unlayPlayer(player)
        dismountPlayer(player)
        
        val ridersOnThis = mountedPlayers.entries.filter { it.value == player.uniqueId }
        for (entry in ridersOnThis) {
            val riderId = entry.key
            val rider = Bukkit.getPlayer(riderId)
            if (rider != null && rider.isOnline) {
                // 탑승자가 온라인이면 탑승 취소 처리
                rider.leaveVehicle()
                rider.sendMessage("§c탑승 중이던 플레이어가 나갔습니다.")
                mountedPlayers.remove(riderId)
            }
        }
        
        crawlingPlayers.remove(player.uniqueId)
    }
} 
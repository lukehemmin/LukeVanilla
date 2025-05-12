package com.lukehemmin.lukeVanilla.System.SitSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Pose
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
import org.bukkit.event.player.PlayerToggleSneakEvent // 추가: 쉬프트 이벤트 처리를 위해
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
        // 1. 우클릭 액션이 아니면 반환
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        
        val player = event.player
        val block = event.clickedBlock ?: return

        // 2. 앉을 수 없는 블록이면 반환
        if (!isSittableBlock(block)) return
        
        // 3. 이미 앉아있는 상태에서는 다시 앉지 않도록 함
        if (isPlayerSitting(player)) {
            event.isCancelled = true // 기존 코드와 동일하게, 앉아있을 때 우클릭하면 다른 상호작용도 막음.
            return
        }

        // 4. 앉기 조건 검사
        //    - 쉬프트 키를 누르고 있거나 (기존 기능 유지, 아이템 유무 상관 없음)
        //    - 또는 손에 아이템을 들고 있지 않은 경우 (새로운 기능, 쉬프트 없이)
        val mainHandIsEmpty = player.inventory.itemInMainHand.type == Material.AIR
        // val offHandIsEmpty = player.inventory.itemInOffHand.type == Material.AIR // 필요시 오프핸드도 검사

        if (player.isSneaking || mainHandIsEmpty) {
            // 앉기 실행
            event.isCancelled = true // 블록과의 기본 상호작용(예: 아이템 사용, 블록 설치 등) 방지
            makeSitOnBlock(player, block)
        }
        // 손에 아이템을 들고 있고, 쉬프트도 누르지 않은 경우에는 event.isCancelled는 기본적으로 false이므로
        // 바닐라 블록 상호작용 (예: 상자 열기, 버튼 누르기 등) 또는 아이템 사용이 일어나도록 함.
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
        
        mountPlayer(rider, targetPlayer) // 플레이어 탑승 로직 호출
    }

    // 플레이어가 쉬프트 키를 누를 때의 동작 처리
    @EventHandler
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (event.isSneaking) { // 쉬프트 키를 누르기 시작했을 때
            // 1. 다른 플레이어 위에 탑승 중인 경우 해제
            if (isPlayerMounted(player)) {
                dismountPlayer(player) // 탑승 해제 로직 호출
                player.sendMessage("§c탑승 상태에서 내렸습니다.")
                return // 다른 동작과 중복되지 않도록 여기서 종료
            }

            // 2. 앉아있는 경우 일어나기
            if (isPlayerSitting(player)) {
                unsitPlayer(player)
                // 필요하다면, 플레이어를 약간 위로 텔레포트하여 끼임 방지
                // player.teleport(player.location.add(0.0, 0.2, 0.0))
                player.sendMessage("§a일어났습니다.")
                return
            }

            // 3. 누워있는 경우 일어나기
            if (isPlayerLaying(player)) {
                unlayPlayer(player)
                // player.teleport(player.location.add(0.0, 0.2, 0.0))
                player.sendMessage("§a일어났습니다.")
                return
            }
        }
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
        if (isPlayerSitting(player) || isPlayerLaying(player)) { // 이미 앉거나 누워있으면 중복 실행 방지
            player.sendMessage("§c이미 다른 동작을 하고 있어 누울 수 없습니다.")
            return
        }

        // 플레이어 위치 조정 (바닥에 눕히기)
        // 아머스탠드를 플레이어보다 약간 아래에 위치시켜 플레이어가 땅에 누운 것처럼 보이게 함.
        // 이 값은 실험을 통해 미세 조정이 필요할 수 있음. (GSit 플러그인 참고: -1.625 또는 -1.7)
        val layLocation = player.location.clone().add(0.0, -1.65, 0.0)
        val armorStand = createLayArmorStand(layLocation)

        // 아머스탠드에 탑승
        armorStand.addPassenger(player)

        // 플레이어 Pose 변경 (Spigot 1.16+ 에서 사용 가능)
        // 이 코드는 다른 플레이어에게도 플레이어가 누워있는 것처럼 보이게 함.
        try {
            player.pose = Pose.SLEEPING
        } catch (e: NoSuchMethodError) {
            // 이전 버전 Spigot에서는 Pose API가 없을 수 있음.
            plugin.logger.warning("Entity Pose API not available on this server version. Lay animation might not be fully effective.")
        }

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
        // 이미 다른 엔티티에 탑승 중이라면 먼저 내리도록 함 (중복 탑승 방지)
        if (rider.isInsideVehicle) {
            rider.leaveVehicle()
        }

        // 타겟 플레이어 위에 다른 플레이어가 이미 탑승 중인지 확인 (정보 제공용)
        val passengersOnTarget = target.passengers.filterIsInstance<Player>()
        if (passengersOnTarget.isNotEmpty()) {
            rider.sendMessage("§e${target.name}님 위에는 이미 ${passengersOnTarget.size}명이 탑승 중입니다. 그 위에 탑승합니다.")
        }

        // 대상 플레이어에게 탑승자를 추가 (Bukkit API가 중첩 및 이동을 처리)
        target.addPassenger(rider)
        
        // 탑승 관계 기록 (탑승자 UUID -> 대상 UUID)
        mountedPlayers[rider.uniqueId] = target.uniqueId
        
        // 메타데이터 설정
        rider.setMetadata("mounted", FixedMetadataValue(plugin, true))
        
        rider.sendMessage("§a${target.name}님의 머리 위에 탑승했습니다. 쉬프트 키를 눌러 내릴 수 있습니다.")
    }

    // 이 함수는 mountPlayer에서 직접 target.passengers를 사용하도록 변경되어 현재는 사용되지 않음.
    // 필요시 주석 해제 또는 다른 용도로 사용 가능.
    // private fun countStackedPlayers(targetUuid: UUID): Int {
    //     // 타겟 위에 올라탄 플레이어 수 계산 (자체 관리 맵 기준)
    //     return mountedPlayers.count { it.value == targetUuid }
    // }

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
        // 플레이어 Pose를 원래대로 복구 (Spigot 1.16+)
        try {
            if (player.pose == Pose.SLEEPING) {
                player.pose = Pose.STANDING
            }
        } catch (e: NoSuchMethodError) {
            // 이전 버전 Spigot
            plugin.logger.warning("Entity Pose API not available on this server version. Player pose might not reset correctly.")
        }
        layingPlayers[player.uniqueId]?.remove()
        layingPlayers.remove(player.uniqueId)
        player.removeMetadata("laying", plugin)
    }

    /**
     * 플레이어 탑승 상태 해제
     */
    fun dismountPlayer(player: Player) {
        // 플레이어가 실제로 다른 엔티티에 탑승 중인지 확인 후 내리도록 함
        if (player.isInsideVehicle) {
            val vehicle = player.vehicle
            player.leaveVehicle() // 현재 탑승 중인 vehicle에서 내림

            // 만약 내린 vehicle이 플레이어였고, 그 플레이어 위에 다른 플레이어가 있었다면,
            // 그들은 vehicle이었던 플레이어가 움직이지 않게 되므로 자연스럽게 분리됨.
            // GSit과 유사하게, 내린 플레이어 위의 스택이 무너지는 효과는 Bukkit 기본 동작에 따름.
        }
        
        // 관리 맵에서 탑승 정보 제거 및 메타데이터 제거
        mountedPlayers.remove(player.uniqueId)
        player.removeMetadata("mounted", plugin)
        // player.sendMessage("§c탑승 상태에서 내렸습니다.") // 이 메시지는 onPlayerToggleSneak에서 이미 처리
    }

    /**
     * 플레이어 나갈 때 모든 상태 정리
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        unsitPlayer(player)
        unlayPlayer(player)
        dismountPlayer(player) // 플레이어가 누군가에게 탑승 중이었다면 해제

        // 나가는 플레이어(player)를 vehicle로 삼아 탑승하고 있던 다른 플레이어들(riders) 처리
        val ridersOnThisPlayer = mutableListOf<UUID>()
        mountedPlayers.entries.forEach { entry ->
            if (entry.value == player.uniqueId) {
                ridersOnThisPlayer.add(entry.key)
            }
        }

        ridersOnThisPlayer.forEach { riderId ->
            val rider = Bukkit.getPlayer(riderId)
            if (rider != null && rider.isOnline) {
                if (rider.vehicle == player) { // 실제로 해당 플레이어 위에 탑승 중인지 재확인
                    rider.leaveVehicle()
                }
                rider.sendMessage("§c탑승 중이던 플레이어(${player.name})가 나갔습니다.")
                mountedPlayers.remove(riderId) // 우리 맵에서도 제거
                rider.removeMetadata("mounted", plugin)
            } else {
                // 오프라인이거나 없는 플레이어면 맵에서만 제거
                mountedPlayers.remove(riderId)
            }
        }
        
        crawlingPlayers.remove(player.uniqueId)
        player.removeMetadata("crawling", plugin) // 크롤링 메타데이터도 제거
    }
} 
package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.Material

class AdvancedLandProtectionListener(private val advancedLandManager: AdvancedLandManager) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        val chunk = block.chunk
        val worldName = chunk.world.name
        
        // 관리자 권한 체크
        if (player.hasPermission("advancedland.admin.bypass")) {
            return
        }
        
        // 클레이밍된 청크인지 확인
        val claimInfo = advancedLandManager.getClaimOwner(worldName, chunk.x, chunk.z) ?: return
        
        // 소유자인지 확인
        if (claimInfo.ownerUuid == player.uniqueId) {
            return // 소유자는 자유롭게 행동 가능
        }
        
        // 개인 토지인 경우 친구 권한 체크
        if (claimInfo.claimType == ClaimType.PERSONAL) {
            val representativeChunk = advancedLandManager.getRepresentativeChunk(claimInfo.ownerUuid, chunk)
            val landManager = advancedLandManager.getLandManager()
            
            if (representativeChunk != null && landManager != null) {
                if (landManager.isMember(representativeChunk, player)) {
                    return // 친구로 등록되어 있음
                }
            }
        }
        
        // 마을 청크인 경우 권한 체크
        if (claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId != null) {
            if (hasVillagePermission(player.uniqueId, claimInfo.villageId!!, VillagePermissionType.BREAK_BLOCKS)) {
                return // 마을 구성원이고 블록 파괴 권한이 있음
            }
            
            // 마을 멤버 권한 체크 (대표 청크 기반)
            val representativeChunk = advancedLandManager.getRepresentativeChunk(claimInfo.ownerUuid, chunk)
            val landManager = advancedLandManager.getLandManager()
            
            if (representativeChunk != null && landManager != null) {
                if (landManager.isMember(representativeChunk, player)) {
                    return // 마을 멤버로 등록되어 있음
                }
            }
        }
        
        // 권한이 없으므로 이벤트 취소
        event.isCancelled = true
        
        val ownerName = claimInfo.ownerName
        val message = if (claimInfo.claimType == ClaimType.VILLAGE) {
            "이 마을 토지에서는 블록을 파괴할 수 없습니다. (소유: $ownerName)"
        } else {
            "이 토지는 ${ownerName}님이 소유하고 있습니다. 블록을 파괴할 수 없습니다."
        }
        
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val block = event.block
        val chunk = block.chunk
        val worldName = chunk.world.name
        
        // 관리자 권한 체크
        if (player.hasPermission("advancedland.admin.bypass")) {
            return
        }
        
        // 클레이밍된 청크인지 확인
        val claimInfo = advancedLandManager.getClaimOwner(worldName, chunk.x, chunk.z) ?: return
        
        // 소유자인지 확인
        if (claimInfo.ownerUuid == player.uniqueId) {
            return // 소유자는 자유롭게 행동 가능
        }
        
        // 개인 토지인 경우 친구 권한 체크
        if (claimInfo.claimType == ClaimType.PERSONAL) {
            val representativeChunk = advancedLandManager.getRepresentativeChunk(claimInfo.ownerUuid, chunk)
            val landManager = advancedLandManager.getLandManager()
            
            if (representativeChunk != null && landManager != null) {
                if (landManager.isMember(representativeChunk, player)) {
                    return // 친구로 등록되어 있음
                }
            }
        }
        
        // 마을 청크인 경우 권한 체크
        if (claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId != null) {
            if (hasVillagePermission(player.uniqueId, claimInfo.villageId!!, VillagePermissionType.BUILD)) {
                return // 마을 구성원이고 건설 권한이 있음
            }
            
            // 마을 멤버 권한 체크 (대표 청크 기반)
            val representativeChunk = advancedLandManager.getRepresentativeChunk(claimInfo.ownerUuid, chunk)
            val landManager = advancedLandManager.getLandManager()
            
            if (representativeChunk != null && landManager != null) {
                if (landManager.isMember(representativeChunk, player)) {
                    return // 마을 멤버로 등록되어 있음
                }
            }
        }
        
        // 권한이 없으므로 이벤트 취소
        event.isCancelled = true
        
        val ownerName = claimInfo.ownerName
        val message = if (claimInfo.claimType == ClaimType.VILLAGE) {
            "이 마을 토지에서는 블록을 설치할 수 없습니다. (소유: $ownerName)"
        } else {
            "이 토지는 ${ownerName}님이 소유하고 있습니다. 블록을 설치할 수 없습니다."
        }
        
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock ?: return
        val action = event.action
        
        // 컨테이너 사용, 레버/버튼 등의 상호작용만 체크
        if (action != Action.RIGHT_CLICK_BLOCK) return
        
        val material = block.type
        
        // 컨테이너나 상호작용 가능한 블록인지 확인
        if (!isInteractableBlock(material)) return
        
        val chunk = block.chunk
        val worldName = chunk.world.name
        
        // 관리자 권한 체크
        if (player.hasPermission("advancedland.admin.bypass")) {
            return
        }
        
        // 클레이밍된 청크인지 확인
        val claimInfo = advancedLandManager.getClaimOwner(worldName, chunk.x, chunk.z) ?: return
        
        // 소유자인지 확인
        if (claimInfo.ownerUuid == player.uniqueId) {
            return // 소유자는 자유롭게 행동 가능
        }
        
        // 개인 토지인 경우 친구 권한 체크
        if (claimInfo.claimType == ClaimType.PERSONAL) {
            val representativeChunk = advancedLandManager.getRepresentativeChunk(claimInfo.ownerUuid, chunk)
            val landManager = advancedLandManager.getLandManager()
            
            if (representativeChunk != null && landManager != null) {
                if (landManager.isMember(representativeChunk, player)) {
                    return // 친구로 등록되어 있음
                }
            }
        }
        
        // 마을 청크인 경우 권한 체크
        if (claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId != null) {
            val requiredPermission = when {
                isContainer(material) -> VillagePermissionType.USE_CONTAINERS
                isRedstoneDevice(material) -> VillagePermissionType.USE_REDSTONE
                else -> return
            }
            
            if (hasVillagePermission(player.uniqueId, claimInfo.villageId!!, requiredPermission)) {
                return // 권한이 있음
            }
            
            // 마을 멤버 권한 체크 (대표 청크 기반)
            val representativeChunk = advancedLandManager.getRepresentativeChunk(claimInfo.ownerUuid, chunk)
            val landManager = advancedLandManager.getLandManager()
            
            if (representativeChunk != null && landManager != null) {
                if (landManager.isMember(representativeChunk, player)) {
                    return // 마을 멤버로 등록되어 있음
                }
            }
        }
        
        // 권한이 없으므로 이벤트 취소
        event.isCancelled = true
        
        val ownerName = claimInfo.ownerName
        val interactionType = when {
            isContainer(material) -> "컨테이너를 사용"
            isRedstoneDevice(material) -> "레드스톤 장치를 사용"
            else -> "상호작용"
        }
        
        val message = if (claimInfo.claimType == ClaimType.VILLAGE) {
            "이 마을 토지에서는 ${interactionType}할 수 없습니다. (소유: $ownerName)"
        } else {
            "이 토지는 ${ownerName}님이 소유하고 있습니다. ${interactionType}할 수 없습니다."
        }
        
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }
    
    /**
     * 상호작용 가능한 블록인지 확인합니다.
     */
    private fun isInteractableBlock(material: Material): Boolean {
        return isContainer(material) || isRedstoneDevice(material) || isDoor(material)
    }
    
    /**
     * 컨테이너인지 확인합니다.
     */
    private fun isContainer(material: Material): Boolean {
        return when (material) {
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.SHULKER_BOX, Material.BARREL, Material.HOPPER,
            Material.DROPPER, Material.DISPENSER, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.BREWING_STAND,
            Material.ENCHANTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL -> true
            else -> material.name.contains("SHULKER_BOX")
        }
    }
    
    /**
     * 레드스톤 장치인지 확인합니다.
     */
    private fun isRedstoneDevice(material: Material): Boolean {
        return when (material) {
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON,
            Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON, Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON, Material.STONE_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE,
            Material.SPRUCE_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE,
            Material.ACACIA_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE, Material.CRIMSON_PRESSURE_PLATE,
            Material.WARPED_PRESSURE_PLATE, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.COMPARATOR,
            Material.REPEATER -> true
            else -> false
        }
    }
    
    /**
     * 문인지 확인합니다.
     */
    private fun isDoor(material: Material): Boolean {
        return material.name.contains("_DOOR") || 
               material.name.contains("_GATE") ||
               material.name.contains("TRAPDOOR")
    }
    
    /**
     * 마을 구성원이 특정 권한을 가지고 있는지 확인합니다.
     * (추후 VillageManager 구현 시 실제 권한 체크로 교체)
     */
    private fun hasVillagePermission(playerUuid: java.util.UUID, villageId: Int, permission: VillagePermissionType): Boolean {
        // 임시로 false 반환 (추후 VillageManager 구현 시 실제 권한 체크)
        return false
    }
}
package com.lukehemmin.lukeVanilla.System.LockSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class LockMenuGUI(private val plugin: Main, private val lockManager: BlockLockManager, private val block: Block) : org.bukkit.inventory.InventoryHolder { // plugin 인스턴스 추가
    private lateinit var inventory: Inventory

    override fun getInventory(): Inventory {
        return inventory
    }

    fun createGUI(player: Player): Inventory {
        inventory = Bukkit.createInventory(this, 27, "잠금 메뉴")

        // 잠금/해제 아이템
        val lockItem = ItemStack(if (lockManager.isBlockLocked(block)) Material.IRON_DOOR else Material.OAK_DOOR).apply {
            itemMeta = itemMeta?.also {
                it.setDisplayName(if (lockManager.isBlockLocked(block)) "§c§l잠금 해제" else "§a§l잠금")
                it.lore = listOf(
                    "§7블록을 ${if (lockManager.isBlockLocked(block)) "해제" else "잠금"}합니다",
                    "§e소유자: §f${lockManager.getLockPermissions(lockManager.getLockIdFromBlock(block)!!)?.allowedPlayers?.firstOrNull()?.let { Bukkit.getOfflinePlayer(it).name } ?: "없음"}"
                )
            }
        }
        inventory.setItem(0, lockItem)

        // 권한 추가 아이템
        val addPermissionItem = ItemStack(Material.LIME_DYE)
        val addPermissionMeta = addPermissionItem.itemMeta
        if (addPermissionMeta != null) {
            addPermissionMeta.setDisplayName("§a§l권한 추가")
            addPermissionItem.itemMeta = addPermissionMeta
        }
        inventory.setItem(2, addPermissionItem)

        // 권한 제거 아이템
        val removePermissionItem = ItemStack(Material.RED_DYE)
        val removePermissionMeta = removePermissionItem.itemMeta
        if (removePermissionMeta != null) {
            removePermissionMeta.setDisplayName("§c§l권한 제거")
            removePermissionItem.itemMeta = removePermissionMeta
        }
        inventory.setItem(4, removePermissionItem)

        // 잠금 설정 아이템 (토글)
        val lockConfigItem = ItemStack(Material.LEVER)
        val lockConfigMeta = lockConfigItem.itemMeta
        if (lockConfigMeta != null) {
            lockConfigMeta.setDisplayName("§6§l잠금 설정")
            lockConfigItem.itemMeta = lockConfigMeta
        }
        inventory.setItem(6, lockConfigItem)

        // 레드스톤 설정 아이템
        val redstoneConfigItem = ItemStack(Material.REDSTONE_TORCH)
        val redstoneConfigMeta = redstoneConfigItem.itemMeta
        if (redstoneConfigMeta != null) {
            redstoneConfigMeta.setDisplayName("§c§l레드스톤 설정")
            redstoneConfigItem.itemMeta = redstoneConfigMeta
        }
        inventory.setItem(7, redstoneConfigItem)

        // GUI 닫기 아이템
        val closeItem = ItemStack(Material.BARRIER)
        val closeMeta = closeItem.itemMeta
        if (closeMeta != null) {
            closeMeta.setDisplayName("§7§l닫기")
            closeItem.itemMeta = closeMeta
        }
        inventory.setItem(8, closeItem)

        // 권한 목록 표시 (최대 18명)
        val lockId = lockManager.getLockIdFromBlock(block)
        if (lockId != null) {
            val lockPermissions = lockManager.getLockPermissions(lockId)
            lockPermissions?.allowedPlayers?.take(18)?.forEachIndexed { index, playerId ->
                val playerSkinItem = ItemStack(Material.PLAYER_HEAD)
                val playerSkinMeta = playerSkinItem.itemMeta
                if (playerSkinMeta != null) {
                    val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
                    playerSkinMeta.setDisplayName(offlinePlayer.name ?: "알 수 없는 플레이어")
                    
                    // 플레이어 스킨 적용
                    if (playerSkinMeta is org.bukkit.inventory.meta.SkullMeta) {
                        playerSkinMeta.owningPlayer = offlinePlayer
                    }
                    
                    playerSkinItem.itemMeta = playerSkinMeta
                }
                inventory.setItem(index + 9, playerSkinItem)
            }
        }

        return inventory
    }

    fun openGUI(player: Player) {
        player.openInventory(createGUI(player))
    }

    fun handleItemClick(player: Player, itemStack: ItemStack?) {
        if (itemStack == null) return

        when (itemStack.type) {
            Material.IRON_DOOR -> {
                // 잠금/해제 기능
                if (lockManager.isBlockLocked(block)) {
                    lockManager.unlockBlock(block, player)
                    player.sendMessage("§a§l블록 잠금 해제됨")
                } else {
                    lockManager.lockBlock(block, player)
                    player.sendMessage("§c§l블록 잠금")
                }
            }
            Material.LIME_DYE -> {
            // 권한 추가 기능
            player.sendMessage("§a§l추가할 플레이어 이름을 채팅창에 입력해주세요.")
            player.closeInventory()
            plugin.getLockSystemInstance().lockListener.startAddingPermission(block)
            }
            Material.RED_DYE -> {
                // 권한 제거 GUI 열기
                PermissionRemoveGUI(plugin, lockManager, block).openGUI(player) // PermissionRemoveGUI 열 때 plugin 인스턴스 전달
            }
            Material.LEVER -> {
                // 잠금 설정 (토글) 기능
                val lockId = lockManager.getLockIdFromBlock(block) ?: return
                val lockPermissions = lockManager.getLockPermissions(lockId) ?: return
                lockPermissions.isLocked = !lockPermissions.isLocked
                plugin.database.saveLockPermissions(lockPermissions)
                player.sendMessage("§6§l잠금 설정이 ${if (lockPermissions.isLocked) "활성화" else "비활성화"}되었습니다.")
                openGUI(player)
            }
            Material.REDSTONE_TORCH -> {
                // 레드스톤 설정 토글
                val lockId = lockManager.getLockIdFromBlock(block) ?: return
                val lockPermissions = lockManager.getLockPermissions(lockId) ?: return
                lockPermissions.allowRedstone = !lockPermissions.allowRedstone
                plugin.database.saveLockPermissions(lockPermissions)
                
                // 아이템 상태 실시간 업데이트
                val newItem = ItemStack(Material.REDSTONE_TORCH).apply {
                    itemMeta = itemMeta?.also {
                        it.setDisplayName("§c§l레드스톤: ${if (lockPermissions.allowRedstone) "ON" else "OFF"}")
                        it.lore = listOf(
                            "§7레드스톤 신호에 반응하는지 설정",
                            "§e현재 상태: §f${if (lockPermissions.allowRedstone) "활성화" else "비활성화"}"
                        )
                    }
                }
                inventory.setItem(7, newItem)
                
                player.updateInventory()
                player.sendMessage("§c§l레드스톤 설정이 ${if (lockPermissions.allowRedstone) "활성화" else "비활성화"}되었습니다.")
            }
            Material.BARRIER -> {
                // GUI 닫기
                player.closeInventory()
            }
            Material.PLAYER_HEAD -> {
                // 권한 제거 기능 (플레이어 아이콘 클릭)
                val playerName = itemStack.itemMeta?.displayName ?: return
                val targetPlayer = Bukkit.getOfflinePlayer(playerName)
                val lockId = lockManager.getLockIdFromBlock(block) ?: return
                
                // 오프라인 플레이어도 UUID로 권한 제거가 가능하도록 수정
                val targetUUID = targetPlayer.uniqueId
                val lockPermissions = lockManager.getLockPermissions(lockId) ?: return
                lockPermissions.removePlayer(targetUUID)
                plugin.database.saveLockPermissions(lockPermissions)
                
                player.sendMessage("§c§l${playerName}님의 권한이 제거되었습니다.")
                openGUI(player)
            }
            else -> return
        }
        if (itemStack.type != Material.BARRIER && itemStack.type != Material.PLAYER_HEAD && itemStack.type != Material.REDSTONE_TORCH) {
            player.closeInventory()
        }
    }
}

package com.lukehemmin.lukeVanilla.System.LockSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class PermissionRemoveGUI(private val plugin: Main, private val lockManager: BlockLockManager, private val block: Block) : org.bukkit.inventory.InventoryHolder { // plugin 인스턴스 추가
    private lateinit var inventory: Inventory

    override fun getInventory(): Inventory {
        return inventory
    }

    fun createGUI(player: Player): Inventory {
        inventory = Bukkit.createInventory(this, 27, "권한 제거")

        // 권한 목록 표시 (최대 27명)
        val lockId = lockManager.getLockIdFromBlock(block)
        if (lockId != null) {
            val lockPermissions = lockManager.getLockPermissions(lockId)
            lockPermissions?.allowedPlayers?.take(27)?.forEachIndexed { index, playerId ->
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

        // GUI 닫기 아이템
        val closeItem = ItemStack(Material.BARRIER)
        val closeMeta = closeItem.itemMeta
        if (closeMeta != null) {
            closeMeta.setDisplayName("§7§l닫기")
            closeItem.itemMeta = closeMeta
        }
        inventory.setItem(26, closeItem)

        return inventory
    }

    fun openGUI(player: Player) {
        player.openInventory(createGUI(player))
    }

    fun handleItemClick(player: Player, itemStack: ItemStack?) {
        if (itemStack == null) return

        when (itemStack.type) {
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
    }
}

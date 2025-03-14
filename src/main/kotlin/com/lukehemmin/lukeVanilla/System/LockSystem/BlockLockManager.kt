package com.lukehemmin.lukeVanilla.System.LockSystem

import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.*

import com.lukehemmin.lukeVanilla.Main

class BlockLockManager(private val plugin: Main) {
    private val lockableBlocks: List<LockableBlock> = listOf(
        LockableDoor(plugin),
        LockableChest(plugin)
    )
    private val lockedBlocks: MutableMap<LockID, LockPermissions> = mutableMapOf()

    fun isLockableBlock(block: Block): Boolean {
        return lockableBlocks.any { it.isLockable(block) }
    }

    fun lockBlock(block: Block, player: Player) {
        if (!isLockableBlock(block)) return

        val lockableBlock = lockableBlocks.find { it.isLockable(block) } ?: return
        lockableBlock.lock(block, player)
        
        // 블록이 잠기면 소유자에게 권한 자동 부여
        val lockId = lockableBlock.getLockId(block) ?: return
        val lockPermissions = LockPermissions(lockId)
        lockPermissions.addPlayer(player.uniqueId)
        plugin.database.saveLockPermissions(lockPermissions)
    }

    fun unlockBlock(block: Block, player: Player) {
        if (!isLockableBlock(block)) return

        val lockableBlock = lockableBlocks.find { it.isLockable(block) } ?: return
        lockableBlock.unlock(block, player)
    }

    fun getLockIdFromBlock(block: Block): LockID? {
        return lockableBlocks.find { it.isLockable(block) }?.getLockId(block) ?: return null
    }

    fun getLockPermissions(lockId: LockID): LockPermissions? {
        return plugin.database.getLockPermissions(lockId)
    }

    fun addLockPermission(lockId: LockID, player: Player) {
        val lockPermissions = getLockPermissions(lockId) ?: return
        lockPermissions.addPlayer(player.uniqueId)
        plugin.database.saveLockPermissions(lockPermissions)
    }

    fun removeLockPermission(lockId: LockID, player: Player) {
        val lockPermissions = getLockPermissions(lockId) ?: return
        lockPermissions.removePlayer(player.uniqueId)
        plugin.database.saveLockPermissions(lockPermissions)
    }

    fun isBlockLocked(block: Block): Boolean {
        return getLockIdFromBlock(block) != null
    }

    fun canPlayerAccessBlock(block: Block, player: Player): Boolean {
        val lockId = getLockIdFromBlock(block) ?: return true
        val lockPermissions = getLockPermissions(lockId) ?: return true

        return lockPermissions.isAllowed(player.uniqueId)
    }
}

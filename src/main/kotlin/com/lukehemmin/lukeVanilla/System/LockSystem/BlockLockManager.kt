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

        val lockId = LockID(UUID.randomUUID())
        val lockPermissions = LockPermissions(lockId, mutableSetOf(player.uniqueId))

        lockedBlocks[lockId] = lockPermissions

        // TODO: 블록에 LockID NBT 태그 저장
    }

    fun unlockBlock(block: Block, player: Player) {
        val lockId = getLockIdFromBlock(block) ?: return
        val lockPermissions = lockedBlocks[lockId] ?: return

        if (!lockPermissions.isAllowed(player.uniqueId)) return

        // TODO: 블록에서 LockID NBT 태그 제거
        lockedBlocks.remove(lockId)
    }

    fun getLockIdFromBlock(block: Block): LockID? {
        // TODO: 블록에서 LockID NBT 태그 조회
        return null
    }

    fun getLockPermissions(lockId: LockID): LockPermissions? {
        return lockedBlocks[lockId]
    }

    fun addLockPermission(lockId: LockID, player: Player) {
        val lockPermissions = lockedBlocks[lockId] ?: return
        lockPermissions.addPlayer(player.uniqueId)
    }

    fun removeLockPermission(lockId: LockID, player: Player) {
        val lockPermissions = lockedBlocks[lockId] ?: return
        lockPermissions.removePlayer(player.uniqueId)
    }

    fun isBlockLocked(block: Block): Boolean {
        return getLockIdFromBlock(block) != null
    }

    fun canPlayerAccessBlock(block: Block, player: Player): Boolean {
        val lockId = getLockIdFromBlock(block) ?: return true // 잠금되지 않은 블록은 접근 가능
        val lockPermissions = lockedBlocks[lockId] ?: return true // 잠금 정보가 없으면 접근 가능 (예외 상황)

        return lockPermissions.isAllowed(player.uniqueId)
    }
}

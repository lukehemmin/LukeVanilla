package com.lukehemmin.lukeVanilla.System.LockSystem

import org.bukkit.block.Block
import org.bukkit.entity.Player

interface LockableBlock {
    fun isLockable(block: Block): Boolean
    fun lock(block: Block, player: Player)
    fun unlock(block: Block, player: Player)
    fun getLockId(block: Block): LockID?
    fun getLockPermissions(lockId: LockID): LockPermissions?
    fun addLockPermission(lockId: LockID, player: Player)
    fun removeLockPermission(lockId: LockID, player: Player)
}

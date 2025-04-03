package com.lukehemmin.lukeVanilla.System.LockSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.UUID

interface DoubleBlockHandler {
    fun handleDoubleBlock(primary: Block, secondary: Block, player: Player) {
        // 대형 블록 동기화 처리
        if (primary.type != secondary.type) return
        
        val lockId = LockID(UUID.randomUUID())
        setLockIdTag(primary, lockId)
        setLockIdTag(secondary, lockId)
        
        val lockPermissions = LockPermissions(lockId, player.uniqueId).apply {
            addPlayer(player.uniqueId)
        }
        plugin.database.saveLockPermissionsAsync(lockPermissions)
    }

    fun setLockIdTag(block: Block, lockId: LockID)
    fun getLockIdTag(block: Block): LockID?
    fun removeLockIdTag(block: Block)
    val plugin: Main
}

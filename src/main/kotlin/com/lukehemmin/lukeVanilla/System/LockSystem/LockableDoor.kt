package com.lukehemmin.lukeVanilla.System.LockSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import com.lukehemmin.lukeVanilla.System.LockSystem.UUIDDataType

class LockableDoor(private val plugin: Main) : LockableBlock {
    private val lockableMaterials = listOf(
        Material.OAK_DOOR,
        Material.SPRUCE_DOOR,
        Material.BIRCH_DOOR,
        Material.JUNGLE_DOOR,
        Material.ACACIA_DOOR,
        Material.IRON_DOOR
        // TODO: 다른 문 종류 추가
    )

    override fun isLockable(block: Block): Boolean {
        return lockableMaterials.contains(block.type)
    }

    override fun lock(block: Block, player: Player) {
        val lockId = LockID(UUID.randomUUID())
        setLockIdTag(block, lockId)
        
        val lockPermissions = LockPermissions(lockId, player.uniqueId).apply {
            addPlayer(player.uniqueId)
        }
        plugin.database.saveLockPermissions(lockPermissions)
    }

    override fun unlock(block: Block, player: Player) {
        removeLockIdTag(block)
    }

    override fun getLockId(block: Block): LockID? {
        return getLockIdTag(block)
    }

    override fun getLockPermissions(lockId: LockID): LockPermissions? {
        return plugin.getLockSystemInstance().blockLockManager.getLockPermissions(lockId)
    }

    override fun addLockPermission(lockId: LockID, player: Player) {
        plugin.getLockSystemInstance().blockLockManager.addLockPermission(lockId, player)
    }

    override fun removeLockPermission(lockId: LockID, player: Player) {
        plugin.getLockSystemInstance().blockLockManager.removeLockPermission(lockId, player)
    }

    private fun setLockIdTag(block: Block, lockId: LockID) {
        val lockIdKey = NamespacedKey(plugin, "lockId")
        val blockState = block.state
        if (blockState is org.bukkit.block.TileState) {
            blockState.persistentDataContainer.set(lockIdKey, UUIDDataType, lockId.id)
            blockState.update()
        }
    }

    private fun getLockIdTag(block: Block): LockID? {
        val lockIdKey = NamespacedKey(plugin, "lockId")
        val blockState = block.state
        if (blockState is org.bukkit.block.TileState) {
            val uuid = blockState.persistentDataContainer.get(lockIdKey, UUIDDataType) ?: return null
            return LockID(uuid)
        }
        return null
    }

    private fun removeLockIdTag(block: Block) {
        val lockIdKey = NamespacedKey(plugin, "lockId")
        val blockState = block.state
        if (blockState is org.bukkit.block.TileState) {
            blockState.persistentDataContainer.remove(lockIdKey)
            blockState.update()
        }
    }
}

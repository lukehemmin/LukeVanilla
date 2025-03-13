package com.lukehemmin.lukeVanilla.System.LockSystem

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

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
    }

    override fun unlock(block: Block, player: Player) {
        removeLockIdTag(block)
    }

    override fun getLockId(block: Block): LockID? {
        return getLockIdTag(block)
    }

    override fun getLockPermissions(lockId: LockID): LockPermissions? {
        // TODO: DB 연동하여 LockPermissions 조회
        return null
    }

    override fun addLockPermission(lockId: LockID, player: Player) {
        // TODO: DB 연동하여 LockPermissions 업데이트 (플레이어 추가)
    }

    override fun removeLockPermission(lockId: LockID, player: Player) {
        // TODO: DB 연동하여 LockPermissions 업데이트 (플레이어 제거)
    }

    private fun setLockIdTag(block: Block, lockId: LockID) {
        val persistentDataContainer = block.persistentDataContainer
        val lockIdKey = NamespacedKey(plugin, "lockId")
        persistentDataContainer.set(lockIdKey, UUIDDataType(), lockId.id)
    }

    private fun getLockIdTag(block: Block): LockID? {
        val persistentDataContainer = block.persistentDataContainer
        val lockIdKey = NamespacedKey(plugin, "lockId")
        val uuid = persistentDataContainer.get(lockIdKey, UUIDDataType.UUIDDataType) ?: return null
        return LockID(uuid)
    }

    private fun removeLockIdTag(block: Block) {
        val persistentDataContainer = block.persistentDataContainer
        val lockIdKey = NamespacedKey(plugin, "lockId")
        persistentDataContainer.remove(lockIdKey)
    }
}

package com.lukehemmin.lukeVanilla.System.LockSystem

import java.util.*

data class LockPermissions(
    val lockId: LockID,
    val allowedPlayers: MutableSet<UUID> = mutableSetOf(),
    var isLocked: Boolean = true,
    var allowRedstone: Boolean = false
) {
    fun isAllowed(playerId: UUID): Boolean {
        return isLocked && allowedPlayers.contains(playerId) || !isLocked
    }

    fun addPlayer(playerId: UUID) {
        allowedPlayers.add(playerId)
    }

    fun removePlayer(playerId: UUID) {
        allowedPlayers.remove(playerId)
    }
}

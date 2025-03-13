package com.lukehemmin.lukeVanilla.System.LockSystem

import java.util.*

data class LockPermissions(
    val lockId: LockID,
    val allowedPlayers: MutableSet<UUID>
) {
    fun isAllowed(playerId: UUID): Boolean {
        return allowedPlayers.contains(playerId)
    }

    fun addPlayer(playerId: UUID) {
        allowedPlayers.add(playerId)
    }

    fun removePlayer(playerId: UUID) {
        allowedPlayers.remove(playerId)
    }
}

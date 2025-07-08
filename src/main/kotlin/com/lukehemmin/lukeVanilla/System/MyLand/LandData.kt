package com.lukehemmin.lukeVanilla.System.MyLand

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.Timestamp
import java.util.UUID

data class ClaimInfo(val ownerUuid: UUID, val claimedAt: Timestamp, val claimType: String)
data class ClaimHistory(val previousOwnerUuid: UUID, val actorUuid: UUID?, val reason: String, val unclaimedAt: Timestamp)

class LandData(private val database: Database) {

    /**
     * 데이터베이스에서 모든 소유된 청크 정보를 불러옵니다.
     * @return Map<월드 이름, Map<청크 좌표, 소유자 UUID>>
     */
    fun loadAllClaims(): MutableMap<String, MutableMap<Pair<Int, Int>, UUID>> {
        val claims = mutableMapOf<String, MutableMap<Pair<Int, Int>, UUID>>()
        val query = "SELECT world, chunk_x, chunk_z, owner_uuid FROM myland_claims"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val worldName = resultSet.getString("world")
                        val chunkX = resultSet.getInt("chunk_x")
                        val chunkZ = resultSet.getInt("chunk_z")
                        val ownerUUID = UUID.fromString(resultSet.getString("owner_uuid"))
                        
                        claims.computeIfAbsent(worldName) { mutableMapOf() }[chunkX to chunkZ] = ownerUUID
                    }
                }
            }
        }
        return claims
    }

    /**
     * 특정 청크의 소유권을 데이터베이스에 저장합니다.
     */
    fun saveClaim(worldName: String, chunkX: Int, chunkZ: Int, owner: UUID, claimType: String) {
        val query = "INSERT INTO myland_claims (world, chunk_x, chunk_z, owner_uuid, claim_type) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner_uuid = ?, claim_type = ?, claimed_at = CURRENT_TIMESTAMP"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, worldName)
                statement.setInt(2, chunkX)
                statement.setInt(3, chunkZ)
                statement.setString(4, owner.toString())
                statement.setString(5, claimType)
                statement.setString(6, owner.toString())
                statement.setString(7, claimType)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 특정 청크의 소유권을 데이터베이스에서 삭제합니다.
     */
    fun deleteClaim(worldName: String, chunkX: Int, chunkZ: Int) {
        val query = "DELETE FROM myland_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, worldName)
                statement.setInt(2, chunkX)
                statement.setInt(3, chunkZ)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 특정 청크의 소유 정보를 불러옵니다.
     */
    fun getClaimInfo(worldName: String, chunkX: Int, chunkZ: Int): ClaimInfo? {
        val query = "SELECT owner_uuid, claimed_at, claim_type FROM myland_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, worldName)
                statement.setInt(2, chunkX)
                statement.setInt(3, chunkZ)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        ClaimInfo(
                            ownerUuid = UUID.fromString(resultSet.getString("owner_uuid")),
                            claimedAt = resultSet.getTimestamp("claimed_at"),
                            claimType = resultSet.getString("claim_type")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }
    
    fun getMembers(worldName: String, chunkX: Int, chunkZ: Int): List<UUID> {
        val members = mutableListOf<UUID>()
        val query = "SELECT member_uuid FROM myland_members WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        database.getConnection().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, worldName)
                stmt.setInt(2, chunkX)
                stmt.setInt(3, chunkZ)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        members.add(UUID.fromString(rs.getString("member_uuid")))
                    }
                }
            }
        }
        return members
    }

    fun addMember(worldName: String, chunkX: Int, chunkZ: Int, memberUuid: UUID): Boolean {
        val query = "INSERT IGNORE INTO myland_members (world, chunk_x, chunk_z, member_uuid) VALUES (?, ?, ?, ?)"
        database.getConnection().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, worldName)
                stmt.setInt(2, chunkX)
                stmt.setInt(3, chunkZ)
                stmt.setString(4, memberUuid.toString())
                return stmt.executeUpdate() > 0
            }
        }
    }
    
    fun removeMember(worldName: String, chunkX: Int, chunkZ: Int, memberUuid: UUID): Boolean {
        val query = "DELETE FROM myland_members WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND member_uuid = ?"
        database.getConnection().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, worldName)
                stmt.setInt(2, chunkX)
                stmt.setInt(3, chunkZ)
                stmt.setString(4, memberUuid.toString())
                return stmt.executeUpdate() > 0
            }
        }
    }

    /**
     * 토지 소유권 해제 시 관련 멤버 정보도 모두 삭제합니다.
     */
    fun deleteAllMembers(worldName: String, chunkX: Int, chunkZ: Int) {
        val query = "DELETE FROM myland_members WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        database.getConnection().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, worldName)
                stmt.setInt(2, chunkX)
                stmt.setInt(3, chunkZ)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * 토지 소유권 해제 이력을 기록합니다.
     */
    fun logClaimHistory(worldName: String, chunkX: Int, chunkZ: Int, previousOwner: UUID, actor: UUID?, reason: String) {
        val query = "INSERT INTO myland_claim_history (world, chunk_x, chunk_z, previous_owner_uuid, actor_uuid, reason) VALUES (?, ?, ?, ?, ?, ?)"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, worldName)
                statement.setInt(2, chunkX)
                statement.setInt(3, chunkZ)
                statement.setString(4, previousOwner.toString())
                if (actor != null) {
                    statement.setString(5, actor.toString())
                } else {
                    statement.setNull(5, java.sql.Types.VARCHAR)
                }
                statement.setString(6, reason)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 특정 청크의 소유권 해제 이력 전체를 불러옵니다.
     */
    fun getClaimHistory(worldName: String, chunkX: Int, chunkZ: Int): List<ClaimHistory> {
        val historyList = mutableListOf<ClaimHistory>()
        val query = "SELECT previous_owner_uuid, actor_uuid, reason, unclaimed_at FROM myland_claim_history WHERE world = ? AND chunk_x = ? AND chunk_z = ? ORDER BY unclaimed_at DESC"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, worldName)
                statement.setInt(2, chunkX)
                statement.setInt(3, chunkZ)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        historyList.add(
                            ClaimHistory(
                                previousOwnerUuid = UUID.fromString(resultSet.getString("previous_owner_uuid")),
                                actorUuid = resultSet.getString("actor_uuid")?.let { UUID.fromString(it) },
                                reason = resultSet.getString("reason"),
                                unclaimedAt = resultSet.getTimestamp("unclaimed_at")
                            )
                        )
                    }
                }
            }
        }
        return historyList
    }
} 
package com.lukehemmin.lukeVanilla.System.MyLand

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.util.UUID

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
    fun saveClaim(worldName: String, chunkX: Int, chunkZ: Int, owner: UUID) {
        val query = "INSERT INTO myland_claims (world, chunk_x, chunk_z, owner_uuid) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner_uuid = ?, claimed_at = CURRENT_TIMESTAMP"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, worldName)
                statement.setInt(2, chunkX)
                statement.setInt(3, chunkZ)
                statement.setString(4, owner.toString())
                statement.setString(5, owner.toString())
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
} 
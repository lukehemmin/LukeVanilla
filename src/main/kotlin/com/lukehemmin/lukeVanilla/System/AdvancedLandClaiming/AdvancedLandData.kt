package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.Timestamp
import java.util.*

class AdvancedLandData(private val database: Database) {

    // ===== 클레이밍 관련 메서드 =====
    
    /**
     * 데이터베이스에서 모든 클레이밍 정보를 불러옵니다.
     * @return Map<월드 이름, Map<청크 좌표, 클레이밍 정보>>
     */
    fun loadAllClaims(): MutableMap<String, MutableMap<Pair<Int, Int>, AdvancedClaimInfo>> {
        val claims = mutableMapOf<String, MutableMap<Pair<Int, Int>, AdvancedClaimInfo>>()
        val query = """
            SELECT world, chunk_x, chunk_z, owner_uuid, owner_name, claim_type, 
                   resource_type, resource_amount, used_free_slots, village_id,
                   UNIX_TIMESTAMP(created_at) as created_at,
                   UNIX_TIMESTAMP(last_updated) as last_updated
            FROM advanced_claims
        """.trimIndent()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val worldName = resultSet.getString("world")
                        val chunkX = resultSet.getInt("chunk_x")
                        val chunkZ = resultSet.getInt("chunk_z")
                        val ownerUuid = UUID.fromString(resultSet.getString("owner_uuid"))
                        val ownerName = resultSet.getString("owner_name")
                        val claimType = ClaimType.valueOf(resultSet.getString("claim_type"))
                        val resourceType = ClaimResourceType.valueOf(resultSet.getString("resource_type"))
                        val resourceAmount = resultSet.getInt("resource_amount")
                        val usedFreeSlots = resultSet.getInt("used_free_slots")
                        val villageId = resultSet.getObject("village_id") as? Int
                        val createdAt = resultSet.getLong("created_at") * 1000
                        val lastUpdated = resultSet.getLong("last_updated") * 1000
                        
                        val claimCost = if (resourceType != ClaimResourceType.FREE) {
                            ClaimCost(resourceType, resourceAmount, usedFreeSlots)
                        } else null
                        
                        val claimInfo = AdvancedClaimInfo(
                            chunkX, chunkZ, worldName, ownerUuid, ownerName,
                            claimType, createdAt, lastUpdated, villageId, claimCost
                        )
                        
                        claims.computeIfAbsent(worldName) { mutableMapOf() }[chunkX to chunkZ] = claimInfo
                    }
                }
            }
        }
        return claims
    }
    
    /**
     * 새로운 클레이밍을 데이터베이스에 저장합니다.
     */
    fun saveClaim(claimInfo: AdvancedClaimInfo): Boolean {
        val query = """
            INSERT INTO advanced_claims 
            (world, chunk_x, chunk_z, owner_uuid, owner_name, claim_type, 
             resource_type, resource_amount, used_free_slots, village_id) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            owner_uuid = ?, owner_name = ?, claim_type = ?, 
            resource_type = ?, resource_amount = ?, used_free_slots = ?, village_id = ?,
            last_updated = CURRENT_TIMESTAMP
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    val resourceType = claimInfo.claimCost?.resourceType ?: ClaimResourceType.FREE
                    val resourceAmount = claimInfo.claimCost?.amount ?: 0
                    val usedFreeSlots = claimInfo.claimCost?.usedFreeSlots ?: 0
                    
                    statement.setString(1, claimInfo.worldName)
                    statement.setInt(2, claimInfo.chunkX)
                    statement.setInt(3, claimInfo.chunkZ)
                    statement.setString(4, claimInfo.ownerUuid.toString())
                    statement.setString(5, claimInfo.ownerName)
                    statement.setString(6, claimInfo.claimType.name)
                    statement.setString(7, resourceType.name)
                    statement.setInt(8, resourceAmount)
                    statement.setInt(9, usedFreeSlots)
                    statement.setObject(10, claimInfo.villageId)
                    
                    // ON DUPLICATE KEY UPDATE 부분
                    statement.setString(11, claimInfo.ownerUuid.toString())
                    statement.setString(12, claimInfo.ownerName)
                    statement.setString(13, claimInfo.claimType.name)
                    statement.setString(14, resourceType.name)
                    statement.setInt(15, resourceAmount)
                    statement.setInt(16, usedFreeSlots)
                    statement.setObject(17, claimInfo.villageId)
                    
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 클레이밍을 데이터베이스에서 삭제하고 히스토리에 기록합니다.
     */
    fun removeClaim(
        worldName: String, chunkX: Int, chunkZ: Int,
        actorUuid: UUID?, actorName: String?, reason: String
    ): Boolean {
        return try {
            database.getConnection().use { connection ->
                connection.autoCommit = false
                
                // 기존 클레이밍 정보 조회
                val selectQuery = "SELECT owner_uuid, owner_name FROM advanced_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
                var previousOwnerUuid: UUID? = null
                var previousOwnerName: String? = null
                
                connection.prepareStatement(selectQuery).use { statement ->
                    statement.setString(1, worldName)
                    statement.setInt(2, chunkX)
                    statement.setInt(3, chunkZ)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            previousOwnerUuid = UUID.fromString(resultSet.getString("owner_uuid"))
                            previousOwnerName = resultSet.getString("owner_name")
                        }
                    }
                }
                
                if (previousOwnerUuid != null && previousOwnerName != null) {
                    // 히스토리에 기록
                    val historyQuery = """
                        INSERT INTO advanced_claim_history 
                        (world, chunk_x, chunk_z, previous_owner_uuid, previous_owner_name, 
                         actor_uuid, actor_name, action_type, reason) 
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'UNCLAIMED', ?)
                    """.trimIndent()
                    
                    connection.prepareStatement(historyQuery).use { statement ->
                        statement.setString(1, worldName)
                        statement.setInt(2, chunkX)
                        statement.setInt(3, chunkZ)
                        statement.setString(4, previousOwnerUuid.toString())
                        statement.setString(5, previousOwnerName)
                        statement.setString(6, actorUuid?.toString())
                        statement.setString(7, actorName)
                        statement.setString(8, reason)
                        statement.executeUpdate()
                    }
                    
                    // 클레이밍 삭제
                    val deleteQuery = "DELETE FROM advanced_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
                    connection.prepareStatement(deleteQuery).use { statement ->
                        statement.setString(1, worldName)
                        statement.setInt(2, chunkX)
                        statement.setInt(3, chunkZ)
                        statement.executeUpdate()
                    }
                    
                    connection.commit()
                    true
                } else {
                    connection.rollback()
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 특정 플레이어가 소유한 클레이밍 수를 조회합니다.
     */
    fun getPlayerClaimCount(playerUuid: UUID): Int {
        val query = "SELECT COUNT(*) as count FROM advanced_claims WHERE owner_uuid = ? AND claim_type = 'PERSONAL'"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("count")
                    }
                }
            }
        }
        return 0
    }
    
    /**
     * 특정 플레이어가 사용한 무료 슬롯 수를 조회합니다.
     */
    fun getPlayerUsedFreeSlots(playerUuid: UUID): Int {
        val query = "SELECT COALESCE(MAX(used_free_slots), 0) as max_free_slots FROM advanced_claims WHERE owner_uuid = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("max_free_slots")
                    }
                }
            }
        }
        return 0
    }
    
    /**
     * 특정 플레이어의 연결된 청크 그룹을 조회합니다.
     */
    fun getPlayerConnectedChunks(playerUuid: UUID): List<ConnectedChunks> {
        val query = "SELECT world, chunk_x, chunk_z FROM advanced_claims WHERE owner_uuid = ? AND claim_type = 'PERSONAL'"
        val chunks = mutableListOf<ChunkCoordinate>()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid.toString())
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        chunks.add(ChunkCoordinate(
                            resultSet.getInt("chunk_x"),
                            resultSet.getInt("chunk_z"),
                            resultSet.getString("world")
                        ))
                    }
                }
            }
        }
        
        return findConnectedGroups(chunks, playerUuid)
    }
    
    /**
     * 청크들을 연결된 그룹으로 분류합니다.
     */
    private fun findConnectedGroups(chunks: List<ChunkCoordinate>, ownerUuid: UUID): List<ConnectedChunks> {
        val visited = mutableSetOf<ChunkCoordinate>()
        val groups = mutableListOf<ConnectedChunks>()
        
        for (chunk in chunks) {
            if (chunk !in visited) {
                val group = mutableSetOf<ChunkCoordinate>()
                dfs(chunk, chunks, visited, group)
                groups.add(ConnectedChunks(group, ownerUuid))
            }
        }
        
        return groups
    }
    
    /**
     * DFS를 사용하여 연결된 청크를 찾습니다.
     */
    private fun dfs(current: ChunkCoordinate, allChunks: List<ChunkCoordinate>, 
                   visited: MutableSet<ChunkCoordinate>, group: MutableSet<ChunkCoordinate>) {
        visited.add(current)
        group.add(current)
        
        for (chunk in allChunks) {
            if (chunk !in visited && isAdjacent(current, chunk)) {
                dfs(chunk, allChunks, visited, group)
            }
        }
    }
    
    /**
     * 두 청크가 인접한지 확인합니다 (상하좌우만).
     */
    private fun isAdjacent(chunk1: ChunkCoordinate, chunk2: ChunkCoordinate): Boolean {
        if (chunk1.worldName != chunk2.worldName) return false
        
        val dx = Math.abs(chunk1.x - chunk2.x)
        val dz = Math.abs(chunk1.z - chunk2.z)
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1)
    }
    
    // ===== 마을 관련 메서드 (추후 구현) =====
    
    /**
     * 새로운 마을을 생성합니다.
     */
    fun createVillage(villageName: String, mayorUuid: UUID, mayorName: String): Int? {
        val query = """
            INSERT INTO villages (village_name, mayor_uuid, mayor_name) 
            VALUES (?, ?, ?)
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query, 1).use { statement -> // 1 = RETURN_GENERATED_KEYS
                    statement.setString(1, villageName)
                    statement.setString(2, mayorUuid.toString())
                    statement.setString(3, mayorName)
                    
                    if (statement.executeUpdate() > 0) {
                        statement.generatedKeys.use { keys ->
                            if (keys.next()) {
                                keys.getInt(1)
                            } else null
                        }
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 마을 정보를 조회합니다.
     */
    fun getVillageInfo(villageId: Int): VillageInfo? {
        val query = """
            SELECT village_id, village_name, mayor_uuid, mayor_name, 
                   UNIX_TIMESTAMP(created_at) as created_at, 
                   UNIX_TIMESTAMP(last_updated) as last_updated, 
                   is_active
            FROM villages 
            WHERE village_id = ? AND is_active = TRUE
        """.trimIndent()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, villageId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return VillageInfo(
                            resultSet.getInt("village_id"),
                            resultSet.getString("village_name"),
                            UUID.fromString(resultSet.getString("mayor_uuid")),
                            resultSet.getString("mayor_name"),
                            resultSet.getLong("created_at") * 1000,
                            resultSet.getLong("last_updated") * 1000,
                            resultSet.getBoolean("is_active")
                        )
                    }
                }
            }
        }
        return null
    }
}
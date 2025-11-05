package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.Timestamp
import java.util.*

class AdvancedLandData(private val database: Database) {

    // ===== 공통 쿼리 상수 =====
    companion object {
        private const val TABLE_NAME = "myland_claims"
        private const val ADVANCED_FILTER = "resource_type IS NOT NULL"
        private const val PERSONAL_CLAIMS_FILTER = "$ADVANCED_FILTER AND claim_type = 'PERSONAL'"
        
        // 공통 쿼리 베이스
        private const val SELECT_BASE = "SELECT * FROM $TABLE_NAME WHERE $ADVANCED_FILTER"
        private const val SELECT_PERSONAL_BASE = "SELECT * FROM $TABLE_NAME WHERE $PERSONAL_CLAIMS_FILTER"
        private const val DELETE_BASE = "DELETE FROM $TABLE_NAME WHERE $ADVANCED_FILTER"
        private const val COUNT_BASE = "SELECT COUNT(*) as count FROM $TABLE_NAME WHERE $PERSONAL_CLAIMS_FILTER"
    }

    // ===== 공통 데이터베이스 유틸리티 메서드 =====
    
    /**
     * 단일 결과를 반환하는 쿼리 실행
     */
    private fun <T> executeQuerySingle(query: String, params: List<Any> = emptyList(), mapper: (java.sql.ResultSet) -> T): T? {
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    params.forEachIndexed { index, param ->
                        statement.setObject(index + 1, param)
                    }
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) mapper(resultSet) else null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 업데이트/삭제 쿼리 실행
     */
    private fun executeUpdate(query: String, params: List<Any> = emptyList()): Boolean {
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    params.forEachIndexed { index, param ->
                        statement.setObject(index + 1, param)
                    }
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

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
                   UNIX_TIMESTAMP(claimed_at) as claimed_at,
                   UNIX_TIMESTAMP(last_updated) as last_updated
            FROM $TABLE_NAME 
            WHERE $ADVANCED_FILTER
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
                        val createdAt = resultSet.getLong("claimed_at") * 1000
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
            INSERT INTO myland_claims 
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
                val selectQuery = "SELECT owner_uuid FROM $TABLE_NAME WHERE $ADVANCED_FILTER AND world = ? AND chunk_x = ? AND chunk_z = ?"
                var previousOwnerUuid: UUID? = null
                
                connection.prepareStatement(selectQuery).use { statement ->
                    statement.setString(1, worldName)
                    statement.setInt(2, chunkX)
                    statement.setInt(3, chunkZ)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            previousOwnerUuid = UUID.fromString(resultSet.getString("owner_uuid"))
                        }
                    }
                }
                
                if (previousOwnerUuid != null) {
                    // 히스토리에 기록
                    val historyQuery = """
                        INSERT INTO myland_claim_history 
                        (world, chunk_x, chunk_z, previous_owner_uuid, actor_uuid, reason) 
                        VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                    
                    connection.prepareStatement(historyQuery).use { statement ->
                        statement.setString(1, worldName)
                        statement.setInt(2, chunkX)
                        statement.setInt(3, chunkZ)
                        statement.setString(4, previousOwnerUuid.toString())
                        statement.setString(5, actorUuid?.toString())
                        statement.setString(6, reason)
                        statement.executeUpdate()
                    }
                    
                    // 클레이밍 삭제
                    val deleteQuery = "$DELETE_BASE AND world = ? AND chunk_x = ? AND chunk_z = ?"
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
        val query = "$COUNT_BASE AND owner_uuid = ?"
        return executeQuerySingle(query, listOf(playerUuid.toString())) { resultSet ->
            resultSet.getInt("count")
        } ?: 0
    }
    
    /**
     * 특정 플레이어가 사용한 무료 슬롯 수를 조회합니다.
     */
    fun getPlayerUsedFreeSlots(playerUuid: UUID): Int {
        val query = "SELECT COALESCE(MAX(used_free_slots), 0) as max_free_slots FROM $TABLE_NAME WHERE $ADVANCED_FILTER AND owner_uuid = ?"
        return executeQuerySingle(query, listOf(playerUuid.toString())) { resultSet ->
            resultSet.getInt("max_free_slots")
        } ?: 0
    }
    
    /**
     * 특정 플레이어의 연결된 청크 그룹을 조회합니다.
     */
    fun getPlayerConnectedChunks(playerUuid: UUID): List<ConnectedChunks> {
        val query = "SELECT world, chunk_x, chunk_z FROM $TABLE_NAME WHERE $PERSONAL_CLAIMS_FILTER AND owner_uuid = ?"
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
    
    // ===== 마을 관련 메서드들 =====
    
    /**
     * 마을 이름 중복 확인
     */
    fun isVillageNameExists(villageName: String): Boolean {
        val query = "SELECT COUNT(*) as count FROM villages WHERE village_name = ? AND is_active = TRUE"
        return executeQuerySingle(query, listOf(villageName)) { resultSet ->
            resultSet.getInt("count") > 0
        } ?: false
    }
    
    /**
     * 새로운 마을을 생성하고 마을 ID를 반환합니다.
     */
    fun createVillage(villageName: String, mayorUuid: UUID, mayorName: String): Int? {
        val query = """
            INSERT INTO villages (village_name, mayor_uuid, mayor_name, is_active) 
            VALUES (?, ?, ?, TRUE)
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                    statement.setString(1, villageName)
                    statement.setString(2, mayorUuid.toString())
                    statement.setString(3, mayorName)
                    
                    val affectedRows = statement.executeUpdate()
                    if (affectedRows > 0) {
                        statement.generatedKeys.use { generatedKeys ->
                            if (generatedKeys.next()) {
                                generatedKeys.getInt(1)
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
     * 마을에 멤버를 추가합니다.
     */
    fun addVillageMember(villageId: Int, memberUuid: UUID, memberName: String, role: VillageRole): Boolean {
        val query = """
            INSERT INTO village_members (village_id, member_uuid, member_name, role, is_active) 
            VALUES (?, ?, ?, ?, TRUE)
            ON DUPLICATE KEY UPDATE 
            member_name = ?, role = ?, is_active = TRUE, last_seen = CURRENT_TIMESTAMP
        """.trimIndent()
        
        return executeUpdate(query, listOf(
            villageId, memberUuid.toString(), memberName, role.name,
            memberName, role.name
        ))
    }
    
    /**
     * 클레이밍 정보를 마을 토지로 업데이트합니다.
     */
    fun updateClaimToVillage(claimInfo: AdvancedClaimInfo): Boolean {
        val query = """
            UPDATE $TABLE_NAME 
            SET claim_type = ?, village_id = ?, last_updated = CURRENT_TIMESTAMP
            WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND $ADVANCED_FILTER
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, claimInfo.claimType.name)
                    if (claimInfo.villageId != null) {
                        statement.setInt(2, claimInfo.villageId)
                    } else {
                        statement.setNull(2, java.sql.Types.INTEGER)
                    }
                    statement.setString(3, claimInfo.worldName)
                    statement.setInt(4, claimInfo.chunkX)
                    statement.setInt(5, claimInfo.chunkZ)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 마을의 모든 멤버를 조회합니다.
     */
    fun getVillageMembers(villageId: Int): List<VillageMember> {
        val members = mutableListOf<VillageMember>()
        val query = """
            SELECT village_id, member_uuid, member_name, role, 
                   UNIX_TIMESTAMP(joined_at) as joined_at,
                   UNIX_TIMESTAMP(last_seen) as last_seen, is_active
            FROM village_members 
            WHERE village_id = ? AND is_active = TRUE
        """.trimIndent()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, villageId)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        members.add(
                            VillageMember(
                                resultSet.getInt("village_id"),
                                UUID.fromString(resultSet.getString("member_uuid")),
                                resultSet.getString("member_name"),
                                VillageRole.valueOf(resultSet.getString("role")),
                                resultSet.getLong("joined_at") * 1000,
                                resultSet.getLong("last_seen") * 1000,
                                resultSet.getBoolean("is_active")
                            )
                        )
                    }
                }
            }
        }
        return members
    }
    
    /**
     * 마을을 비활성화합니다.
     */
    fun deactivateVillage(villageId: Int): Boolean {
        val query = "UPDATE villages SET is_active = FALSE WHERE village_id = ?"
        return executeUpdate(query, listOf(villageId))
    }
    
    /**
     * 마을 멤버의 역할을 변경합니다.
     */
    fun updateVillageMemberRole(villageId: Int, memberUuid: UUID, newRole: VillageRole): Boolean {
        val query = """
            UPDATE village_members 
            SET role = ?, last_seen = CURRENT_TIMESTAMP
            WHERE village_id = ? AND member_uuid = ? AND is_active = TRUE
        """.trimIndent()
        
        return executeUpdate(query, listOf(
            newRole.name,
            villageId,
            memberUuid.toString()
        ))
    }
    
    /**
     * 마을 구성원을 추방합니다 (비활성화).
     */
    fun removeVillageMember(villageId: Int, memberUuid: UUID): Boolean {
        val query = """
            UPDATE village_members 
            SET is_active = FALSE, last_seen = CURRENT_TIMESTAMP
            WHERE village_id = ? AND member_uuid = ? AND is_active = TRUE
        """.trimIndent()
        
        return executeUpdate(query, listOf(
            villageId,
            memberUuid.toString()
        ))
    }
    
    /**
     * 플레이어가 속한 마을 정보를 조회합니다.
     * @param playerUuid 플레이어 UUID
     * @return 마을 구성원 정보, 없으면 null
     */
    fun getPlayerVillageMembership(playerUuid: UUID): VillageMember? {
        val query = """
            SELECT vm.village_id, vm.member_uuid, vm.member_name, vm.role, 
                   UNIX_TIMESTAMP(vm.joined_at) as joined_at,
                   UNIX_TIMESTAMP(vm.last_seen) as last_seen, vm.is_active
            FROM village_members vm
            INNER JOIN villages v ON vm.village_id = v.village_id
            WHERE vm.member_uuid = ? AND vm.is_active = TRUE AND v.is_active = TRUE
            LIMIT 1
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            VillageMember(
                                resultSet.getInt("village_id"),
                                UUID.fromString(resultSet.getString("member_uuid")),
                                resultSet.getString("member_name"),
                                VillageRole.valueOf(resultSet.getString("role")),
                                resultSet.getLong("joined_at") * 1000, // 초 → 밀리초
                                resultSet.getLong("last_seen") * 1000,   // 초 → 밀리초
                                resultSet.getBoolean("is_active")
                            )
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in getPlayerVillageMembership: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 플레이어가 특정 마을에서 특정 권한을 가지고 있는지 확인합니다.
     * @param playerUuid 플레이어 UUID
     * @param villageId 마을 ID
     * @param permissionType 확인할 권한 타입
     * @return 권한이 있으면 true
     */
    fun hasVillagePermission(playerUuid: UUID, villageId: Int, permissionType: VillagePermissionType): Boolean {
        val query = """
            SELECT COUNT(*) as count
            FROM village_permissions vp
            INNER JOIN village_members vm ON vp.village_id = vm.village_id AND vp.member_uuid = vm.member_uuid
            INNER JOIN villages v ON vp.village_id = v.village_id
            WHERE vp.member_uuid = ? AND vp.village_id = ? AND vp.permission_type = ? 
              AND vp.is_active = TRUE AND vm.is_active = TRUE AND v.is_active = TRUE
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    statement.setInt(2, villageId)
                    statement.setString(3, permissionType.name)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("count") > 0
                        } else false
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in hasVillagePermission: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    // ===== 마을 해체 및 이장 양도 관련 메서드들 =====
    
    /**
     * 청크를 개인 토지로 변환합니다.
     */
    fun updateClaimToPersonal(worldName: String, chunkX: Int, chunkZ: Int, ownerUuid: UUID, ownerName: String): Boolean {
        val query = """
            UPDATE $TABLE_NAME
            SET claim_type = 'PERSONAL', village_id = NULL, owner_uuid = ?, owner_name = ?
            WHERE world = ? AND chunk_x = ? AND chunk_z = ?
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, ownerUuid.toString())
                    statement.setString(2, ownerName)
                    statement.setString(3, worldName)
                    statement.setInt(4, chunkX)
                    statement.setInt(5, chunkZ)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in updateClaimToPersonal: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 마을장 정보를 업데이트합니다.
     */
    fun updateVillageMayor(villageId: Int, newMayorUuid: UUID, newMayorName: String): Boolean {
        val query = """
            UPDATE villages 
            SET mayor_uuid = ?, mayor_name = ?, last_updated = NOW()
            WHERE village_id = ? AND is_active = TRUE
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, newMayorUuid.toString())
                    statement.setString(2, newMayorName)
                    statement.setInt(3, villageId)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in updateVillageMayor: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 청크의 소유자 정보를 업데이트합니다.
     */
    fun updateClaimOwner(worldName: String, chunkX: Int, chunkZ: Int, ownerUuid: UUID, ownerName: String): Boolean {
        val query = """
            UPDATE $TABLE_NAME
            SET owner_uuid = ?, owner_name = ?
            WHERE world = ? AND chunk_x = ? AND chunk_z = ?
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, ownerUuid.toString())
                    statement.setString(2, ownerName)
                    statement.setString(3, worldName)
                    statement.setInt(4, chunkX)
                    statement.setInt(5, chunkZ)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in updateClaimOwner: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // ===== 마을 권한 관리 시스템 =====

    /**
     * 마을 구성원에게 권한을 부여합니다.
     */
    fun grantVillagePermission(
        villageId: Int,
        memberUuid: UUID,
        permissionType: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType,
        grantedByUuid: UUID,
        grantedByName: String
    ): Boolean {
        val query = """
            INSERT INTO village_permissions
            (village_id, member_uuid, permission_type, granted_by_uuid, granted_by_name, is_active)
            VALUES (?, ?, ?, ?, ?, TRUE)
            ON DUPLICATE KEY UPDATE
            is_active = TRUE, granted_at = CURRENT_TIMESTAMP, granted_by_uuid = ?, granted_by_name = ?
        """.trimIndent()

        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, villageId)
                    statement.setString(2, memberUuid.toString())
                    statement.setString(3, permissionType.name)
                    statement.setString(4, grantedByUuid.toString())
                    statement.setString(5, grantedByName)
                    statement.setString(6, grantedByUuid.toString())
                    statement.setString(7, grantedByName)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in grantVillagePermission: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 마을 구성원의 권한을 해제합니다.
     */
    fun revokeVillagePermission(
        villageId: Int,
        memberUuid: UUID,
        permissionType: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType
    ): Boolean {
        val query = """
            UPDATE village_permissions
            SET is_active = FALSE
            WHERE village_id = ? AND member_uuid = ? AND permission_type = ?
        """.trimIndent()

        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, villageId)
                    statement.setString(2, memberUuid.toString())
                    statement.setString(3, permissionType.name)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in revokeVillagePermission: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 마을 구성원의 모든 권한 목록을 가져옵니다.
     */
    fun getMemberPermissions(villageId: Int, memberUuid: UUID): Set<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType> {
        val query = """
            SELECT permission_type
            FROM village_permissions
            WHERE village_id = ? AND member_uuid = ? AND is_active = TRUE
        """.trimIndent()

        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, villageId)
                    statement.setString(2, memberUuid.toString())
                    statement.executeQuery().use { resultSet ->
                        val permissions = mutableSetOf<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType>()
                        while (resultSet.next()) {
                            try {
                                val permissionType = com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType.valueOf(resultSet.getString("permission_type"))
                                permissions.add(permissionType)
                            } catch (e: IllegalArgumentException) {
                                // 잘못된 권한 타입은 무시
                                System.err.println("[AdvancedLandData] Invalid permission type: ${resultSet.getString("permission_type")}")
                            }
                        }
                        permissions
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in getMemberPermissions: ${e.message}")
            e.printStackTrace()
            emptySet()
        }
    }

    /**
     * 마을의 모든 구성원과 그들의 권한 목록을 가져옵니다.
     */
    fun getAllMemberPermissions(villageId: Int): Map<UUID, Set<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType>> {
        val query = """
            SELECT member_uuid, permission_type
            FROM village_permissions
            WHERE village_id = ? AND is_active = TRUE
        """.trimIndent()

        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, villageId)
                    statement.executeQuery().use { resultSet ->
                        val memberPermissions = mutableMapOf<UUID, MutableSet<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType>>()
                        while (resultSet.next()) {
                            try {
                                val memberUuid = UUID.fromString(resultSet.getString("member_uuid"))
                                val permissionType = com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillagePermissionType.valueOf(resultSet.getString("permission_type"))

                                memberPermissions.computeIfAbsent(memberUuid) { mutableSetOf() }.add(permissionType)
                            } catch (e: Exception) {
                                // 잘못된 UUID나 권한 타입은 무시
                                System.err.println("[AdvancedLandData] Invalid member or permission: ${e.message}")
                            }
                        }
                        memberPermissions
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdvancedLandData] ERROR in getAllMemberPermissions: ${e.message}")
            e.printStackTrace()
            emptyMap()
        }
    }
}
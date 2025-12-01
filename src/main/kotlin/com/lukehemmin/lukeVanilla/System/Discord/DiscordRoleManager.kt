package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import org.bukkit.entity.Player
import java.util.UUID

class DiscordRoleManager(private val database: Database, private val jda: JDA) {
    
    private val logger = java.util.logging.Logger.getLogger("DiscordRoleManager")

    fun checkAndGrantAuthRole(uuid: UUID, name: String) {
        val uuidString = uuid.toString()

        // Check if player is authenticated
        database.getConnection().use { conn ->
            val authStmt = conn.prepareStatement(
                "SELECT IsAuth FROM Player_Auth WHERE UUID = ?"
            )
            authStmt.setString(1, uuidString)
            val authResult = authStmt.executeQuery()

            if (!authResult.next() || !authResult.getBoolean("IsAuth")) {
                return // Not authenticated, do nothing
            }
        }

        // Get Discord server ID
        val serverId = database.getSettingValue("DiscordServerID") ?: run {
            println("Discord server ID not found in settings")
            return
        }

        // Get auth role ID
        val roleId = database.getSettingValue("DiscordAuthRole") ?: run {
            println("Discord auth role ID not found in settings")
            return
        }

        // Get Discord ID for the player
        val playerData = database.getPlayerDataByUuid(uuidString)
        val discordId = playerData?.discordId ?: run {
            println("Discord ID not found for player $name")
            return
        }

        try {
            val guild: Guild = jda.getGuildById(serverId) ?: run {
                println("Could not find Discord server with ID $serverId")
                return
            }

            val role: Role = guild.getRoleById(roleId) ?: run {
                println("Could not find role with ID $roleId")
                return
            }

            // 동기적으로 멤버 조회로 변경
            val member = try {
                guild.retrieveMemberById(discordId).complete()
            } catch (e: Exception) {
                println("Could not find Discord member with ID $discordId")
                return
            }

            // Check if user already has the role
            if (!member.roles.contains(role)) {
                guild.addRoleToMember(member, role).queue(
                    { println("Successfully added auth role to $name") },
                    { error -> println("Failed to add auth role: ${error.message}") }
                )
            }
        } catch (e: Exception) {
            println("Error while managing Discord role: ${e.message}")
        }
    }
    
    /**
     * 디스코드 인증 역할을 제거합니다
     * @param discordId 디스코드 사용자 ID
     */
    fun removeAuthRole(discordId: String) {
        val serverId = database.getSettingValue("DiscordServerID") ?: run {
            logger.warning("Discord 서버 ID를 찾을 수 없습니다.")
            return
        }
        
        val roleId = database.getSettingValue("DiscordAuthRole") ?: run {
            logger.warning("Discord 인증 역할 ID를 찾을 수 없습니다.")
            return
        }

        val guild = jda.getGuildById(serverId) ?: run {
            logger.warning("Discord 서버를 찾을 수 없습니다: $serverId")
            return
        }
        
        val role = guild.getRoleById(roleId) ?: run {
            logger.warning("역할을 찾을 수 없습니다: $roleId")
            return
        }

        val member = try {
            guild.retrieveMemberById(discordId).complete()
        } catch (e: Exception) {
            logger.warning("멤버 조회 실패: ${e.message}")
            return
        }

        if (member.roles.contains(role)) {
            guild.removeRoleFromMember(member, role).queue(
                { logger.info("역할 제거 성공: $discordId") },
                { error -> logger.severe("역할 제거 실패: ${error.message}") }
            )
        } else {
            logger.info("멤버가 해당 역할을 가지고 있지 않습니다: $discordId")
        }
    }
}
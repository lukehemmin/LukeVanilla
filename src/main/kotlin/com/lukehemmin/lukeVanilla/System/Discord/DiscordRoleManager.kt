package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import org.bukkit.entity.Player

class DiscordRoleManager(private val database: Database, private val jda: JDA) {

    fun checkAndGrantAuthRole(player: Player) {
        val uuid = player.uniqueId.toString()

        // Check if player is authenticated
        database.getConnection().use { conn ->
            val authStmt = conn.prepareStatement(
                "SELECT IsAuth FROM Player_Auth WHERE UUID = ?"
            )
            authStmt.setString(1, uuid)
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
        val playerData = database.getPlayerDataByUuid(uuid)
        val discordId = playerData?.discordId ?: run {
            println("Discord ID not found for player ${player.name}")
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
                    { println("Successfully added auth role to ${player.name}") },
                    { error -> println("Failed to add auth role: ${error.message}") }
                )
            }
        } catch (e: Exception) {
            println("Error while managing Discord role: ${e.message}")
        }
    }
}
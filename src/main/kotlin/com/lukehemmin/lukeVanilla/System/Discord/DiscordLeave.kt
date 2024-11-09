package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class DiscordLeave(
    private val database: Database,
    private val plugin: JavaPlugin,
    private val jda: JDA
) : ListenerAdapter(), Listener {

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        plugin.logger.info("GuildMemberRemoveEvent triggered for user ID: ${event.user.id}")

        val discordUserId = event.user.id
        val uuidStr = getUuidByDiscordId(discordUserId) ?: run {
            plugin.logger.info("No UUID found for Discord ID: $discordUserId")
            return
        }

        val uuid = try {
            UUID.fromString(uuidStr)
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid UUID format: $uuidStr")
            return
        }

        val player = Bukkit.getPlayer(uuid)
        if (player != null && player.isOnline) {
            plugin.logger.info("Player found online: ${player.name}, proceeding to kick.")

            // Runnable을 명시적으로 사용하여 오버로드 문제 해결
            val kickRunnable = object : Runnable {
                override fun run() {
                    player.kickPlayer(
                        "디스코드 서버에서 나간 경우 서버에서 자동으로 퇴장됩니다.\n서버에 다시 접속하려는 경우 디스코드에 다시 들어와주세요."
                    )
                    plugin.logger.info("Kicked player ${player.name} because they left the Discord server.")
                }
            }

            plugin.server.scheduler.runTask(plugin, kickRunnable)
        } else {
            plugin.logger.info("Player with UUID $uuidStr is not online.")
        }
    }

    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val playerData = database.getPlayerDataByUuid(uuid) ?: return

        val discordId = playerData.discordId
        if (discordId.isNullOrEmpty()) {
            return
        }

        val guildId = database.getSettingValue("DiscordServerID") ?: return
        val guild = jda.getGuildById(guildId) ?: return

        try {
            // 동기적으로 멤버를 조회
            val member = guild.retrieveMemberById(discordId).complete()

            if (member != null) {
                plugin.logger.info("멤버 ${member.user.name}이(가) Discord 서버에 존재합니다.")
                // 추가적인 처리가 필요하다면 여기에 작성
            }
        } catch (e: ErrorResponseException) {
            if (e.errorCode == 10007) { // Unknown Member
                // 접속 차단
                event.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    "서버에 접속하려면 디스코드에 다시 들어와야 합니다.\n디스코드 서버에 먼저 들어온 후 서버에 다시 접속을 시도해 주세요."
                )
                plugin.logger.info("Prevented player ${player.name} from joining because they are not in the Discord server.")
            } else {
                plugin.logger.warning("Error retrieving member with Discord ID $discordId: ${e.message}")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Unexpected error retrieving member with Discord ID $discordId: ${e.message}")
        }
    }

    private fun getUuidByDiscordId(discordId: String): String? {
        return database.getPlayerDataByDiscordId(discordId)?.uuid
    }
}
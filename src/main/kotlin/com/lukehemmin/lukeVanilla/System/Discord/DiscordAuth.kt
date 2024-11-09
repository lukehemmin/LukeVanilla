package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.bukkit.plugin.java.JavaPlugin
import java.util.regex.Pattern

class DiscordAuth(
    private val database: Database,
    private val plugin: JavaPlugin
) : ListenerAdapter() {

    private val codePattern = Pattern.compile("^[A-Z0-9]{6}$")
    private val serverId: String

    init {
        serverId = database.getSettingValue("DiscordServerID") ?: ""
        if (serverId.isEmpty()) {
            plugin.logger.warning("DiscordServerID 설정이 올바르지 않습니다. DiscordAuth 리스너가 작동하지 않습니다.")
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        // 특정 서버에서만 작동
        if (serverId.isEmpty() || event.guild.id != serverId) return

        val authChannelId = database.getSettingValue("AuthChannel") ?: return
        val logChannelId = database.getSettingValue("AuthLogChannel") ?: return
        val authRoleId = database.getSettingValue("DiscordAuthRole") ?: return

        // 메시지가 인증 채널인지 확인
        if (event.channel.id != authChannelId) return

        val message = event.message.contentRaw.trim()

        // 인증 코드 패턴 확인
        if (!codePattern.matcher(message).matches()) return

        // 사용자의 메시지를 삭제
        event.message.delete().queue()

        val authCode = message
        val authRecord = database.getAuthRecord(authCode) ?: run {
            event.channel.sendMessage("인증코드가 올바르지 않습니다.")
                .queue { reply ->
                    // 1분 후 메시지 삭제
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        reply.delete().queue()
                    }, 20 * 60L)
                }
            return
        }

        if (authRecord.isAuth) {
            event.channel.sendMessage("이미 인증이 완료되었습니다.")
                .queue { reply ->
                    // 1분 후 메시지 삭제
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        reply.delete().queue()
                    }, 20 * 60L)
                }
            return
        }

        try {
            // 인증 상태 업데이트
            database.updateAuthStatus(authCode, true)

            // 플레이어 데이터 가져오기
            val playerData = database.getPlayerDataByUuid(authRecord.uuid) ?: run {
                event.channel.sendMessage("플레이어 데이터를 찾을 수 없습니다. 관리자에게 문의하세요.").queue()
                return
            }

            // DiscordID 업데이트
            database.updateDiscordId(playerData.uuid, event.author.id)

            // 역할 부여
            event.guild.retrieveMember(event.author).queue({ member ->
                val role = event.guild.getRoleById(authRoleId) ?: run {
                    event.channel.sendMessage("역할을 찾을 수 없습니다. 관리자에게 문의하세요.").queue()
                    return@queue
                }

                event.guild.addRoleToMember(member, role).queue({
                    // 인증 완료 메시지 전송 및 삭제
                    event.channel.sendMessage("인증이 완료되었습니다.")
                        .queue { confirmationMessage ->
                            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                                confirmationMessage.delete().queue()
                            }, 20 * 60L)
                        }

                    // 인증 로그 메시지 전송 (멘션 포함)
                    val logChannel = event.guild.getTextChannelById(logChannelId) ?: return@queue
                    val logMessage = "${event.author.asMention} 님이 ${playerData.nickname} (${playerData.uuid}) 로 인증하였습니다."
                    logChannel.sendMessage(logMessage).queue()
                }, { error ->
                    error.printStackTrace()
                })
            }, { error ->
                error.printStackTrace()
            })

        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage("인증 처리 중 오류가 발생했습니다.").queue()
        }
    }
}
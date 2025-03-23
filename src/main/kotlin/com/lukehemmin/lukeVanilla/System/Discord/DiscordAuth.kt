package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.regex.Pattern

class DiscordAuth(
    private val database: Database,
    private val plugin: Main
) : ListenerAdapter() {

    private val codePattern = Pattern.compile("^[A-Z0-9]{6}$")
    private val serverId: String
    private val useApi: Boolean

    init {
        serverId = database.getSettingValue("DiscordServerID") ?: ""
        useApi = plugin.config.getBoolean("api.enabled", false)
        
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
        if (!codePattern.matcher(message).matches()) {
            // 사용자의 메시지를 삭제
            event.message.delete().queue()

            event.channel.sendMessage(":x: 인증코드가 올바르지 않습니다.")
                .queue { reply ->
                    // 1분 후 메시지 삭제
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        reply.delete().queue()
                    }, 20 * 60L)
                }
            return
        }

        // DiscordID로 이미 인증된 플레이어가 있는지 확인 (API 또는 DB)
        val existingPlayer = if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            plugin.apiClient.getPlayerByDiscordId(event.author.id) != null
        } else {
            database.getPlayerDataByDiscordId(event.author.id) != null
        }
        
        if (existingPlayer) {
            // 사용자의 메시지를 삭제
            event.message.delete().queue()

            event.channel.sendMessage(":x: 이미 다른 마인크래프트 계정으로 인증되었습니다.")
                .queue { reply ->
                    // 1분 후 메시지 삭제
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        reply.delete().queue()
                    }, 20 * 60L)
                }
            return
        }

        // 사용자의 메시지를 삭제
        event.message.delete().queue()

        val authCode = message
        
        // API 또는 DB로 인증 코드 검증
        val authRecord = if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            // API 호출 결과를 임시 데이터 클래스로 변환
            val valid = plugin.apiClient.validateAuthCode(authCode, event.author.id)
            if (valid) {
                Database.AuthRecord(uuid = "placeholder", isAuth = true) // 실제 UUID는 API 측에서 처리
            } else {
                null
            }
        } else {
            database.getAuthRecord(authCode)
        }
        
        if (authRecord == null) {
            event.channel.sendMessage(":x: 인증코드가 올바르지 않습니다.")
                .queue { reply ->
                    // 1분 후 메시지 삭제
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        reply.delete().queue()
                    }, 20 * 60L)
                }
            return
        }

        if (authRecord.isAuth) {
            event.channel.sendMessage(":white_check_mark: 이미 인증이 완료되었습니다.")
                .queue { reply ->
                    // 1분 후 메시지 삭제
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        reply.delete().queue()
                    }, 20 * 60L)
                }
            return
        }

        try {
            // API가 아니라면 DB에서 인증 상태 업데이트
            if (!useApi || !::plugin.isInitialized || !plugin::apiClient.isInitialized) {
                database.updateAuthStatus(authCode, true)
                
                // 플레이어 데이터 가져오기
                val playerData = database.getPlayerDataByUuid(authRecord.uuid) ?: run {
                    event.channel.sendMessage(":x: 플레이어 데이터를 찾을 수 없습니다. 관리자에게 문의하세요.").queue()
                    return
                }
                
                // DiscordID 업데이트
                database.updateDiscordId(playerData.uuid, event.author.id)
            }
            
            // 역할 부여 (API와 DB 모두 동일하게 적용)
            event.guild.retrieveMember(event.author).queue({ member ->
                val role = event.guild.getRoleById(authRoleId) ?: run {
                    event.channel.sendMessage(":x: 역할을 찾을 수 없습니다. 관리자에게 문의하세요.").queue()
                    return@queue
                }

                event.guild.addRoleToMember(member, role).queue({
                    // 인증 완료 메시지 전송 및 삭제
                    event.channel.sendMessage(":white_check_mark: 인증이 완료되었습니다.")
                        .queue { confirmationMessage ->
                            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                                confirmationMessage.delete().queue()
                            }, 20 * 60L)
                        }

                    // 인증 로그 메시지 전송 (멘션 포함)
                    val logChannel = event.guild.getTextChannelById(logChannelId) ?: return@queue
                    val playerName = if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
                        // API에서 플레이어 정보 가져오기
                        plugin.apiClient.getPlayerByDiscordId(event.author.id)?.get("nickname")?.asString ?: "Unknown"
                    } else {
                        database.getPlayerDataByUuid(authRecord.uuid)?.nickname ?: "Unknown"
                    }
                    
                    val playerUuid = if (!useApi || !::plugin.isInitialized || !plugin::apiClient.isInitialized) {
                        authRecord.uuid
                    } else {
                        "UUID managed by API"
                    }
                    
                    val logMessage = "${event.author.asMention} 님이 ${playerName} (${playerUuid}) 로 인증하였습니다."
                    logChannel.sendMessage(logMessage).queue()
                }, { error ->
                    error.printStackTrace()
                })
            }, { error ->
                error.printStackTrace()
            })

        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage(":x: 인증 처리 중 오류가 발생했습니다.").queue()
        }
    }
}
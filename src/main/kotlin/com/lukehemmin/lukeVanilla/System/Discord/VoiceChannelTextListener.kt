package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.Member
import org.bukkit.Bukkit

class VoiceChannelTextListener(private val plugin: Main) : ListenerAdapter() {
    
    override fun onMessageReceived(event: MessageReceivedEvent) {
        try {
            if (plugin.database.isDataSourceClosed()) {
                return
            }

            // 봇 메시지는 무시
            if (event.author.isBot) return
            
            // 음성 채널 타입인지 확인
            if (event.channel.type == ChannelType.VOICE) {
                val voiceChannel = event.channel.asVoiceChannel()
                val senderMember = event.member ?: return
                
                // 메시지 발신자의 표시 이름 가져오기
                val displayName = getDisplayName(senderMember)
                
                // 메시지에서 이모지 변환
                val parsedMessage = EmojiUtil.replaceEmojis(event.message.contentDisplay)
                
                // 메시지 전송
                val formattedMessage = "&7&l[음성채널 채팅] &f$displayName &7&l: &f$parsedMessage".translateColorCodes()
                
                // 같은 음성 채널에 있는 플레이어들에게만 메시지 전송
                voiceChannel.members.forEach { voiceChannelMember ->
                    // 메시지 발신자는 제외
                    if (voiceChannelMember.id != senderMember.id) {
                        // 해당 Discord 유저와 연결된 마인크래프트 플레이어 찾기
                        val playerData = plugin.database.getPlayerDataByDiscordId(voiceChannelMember.id)
                        if (playerData != null) {
                            val player = Bukkit.getPlayer(java.util.UUID.fromString(playerData.uuid))
                            if (player != null && player.isOnline) {
                                // 음성채널 메시지 기능이 활성화된 플레이어에게만 전송
                                if (plugin.database.isVoiceChannelMessageEnabled(player.uniqueId.toString())) {
                                    player.sendMessage(formattedMessage)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error in VoiceChannelTextListener: ${e.message}")
        }
    }


    /**
     * 플레이어의 표시 이름을 가져오는 함수
     */
    private fun getDisplayName(member: Member): String {
        val discordId = member.id
        val playerData = plugin.database.getPlayerDataByDiscordId(discordId)

        // Tag 값 가져오기
        val tag = playerData?.uuid?.let { plugin.database.getPlayerNameTag(it) }

        return if (!tag.isNullOrBlank()) {
            // 태그에서 [와 ] 제거하고 색상 코드 변환
            val cleanTag = tag.replace("[", "").replace("]", "")
            cleanTag.translateColorCodes()
        } else {
            // 태그가 없을 경우 Discord 서버 별명 또는 프로필 별명 사용
            member.nickname ?: member.user.name
        }
    }
}
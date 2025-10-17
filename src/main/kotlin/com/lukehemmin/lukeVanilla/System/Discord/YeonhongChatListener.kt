package com.lukehemmin.lukeVanilla.System.Discord

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.entities.Member
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import org.bukkit.Bukkit

class YeonhongChatListener(private val plugin: Main) : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        try {
            if (plugin.database.isDataSourceClosed()) {
                return
            }

            // 연홍 채널 ID 가져오기
            val yeonhongChannelId = plugin.database.getSettingValue("Yeonhong_Channel") ?: return

            // 메시지가 연홍 채널에서 온 것이 아니면 무시
            if (event.channel.id != yeonhongChannelId) return

            // 연홍 본인의 메시지면 무시, 미니쿠다 무시
            if (event.author.id == "1241383865478807582" || event.author.id == "595258464729825290") return

            // 멤버의 표시 이름 가져오기
            val displayName = event.member?.let { getDisplayName(it) } ?: event.author.name

            // 메시지에서 이모지 변환
            val parsedMessage = EmojiUtil.replaceEmojis(event.message.contentDisplay)

            // 메시지 전송
            val formattedMessage = "&7&l[연홍 채팅] &f$displayName &7&l: &f$parsedMessage".translateColorCodes()

            // 활성화된 플레이어들에게만 메시지 전송
            Bukkit.getOnlinePlayers().forEach { player ->
                if (plugin.database.isYeonhongMessageEnabled(player.uniqueId.toString())) {
                    // 해당 플레이어가 연홍과 같은 음성채널에 있는지 확인
                    val playerDiscordId = plugin.database.getDiscordIDByUUID(player.uniqueId.toString())
                    if (playerDiscordId != null) {
                        val member = event.guild.getMemberById(playerDiscordId)
                        if (member?.voiceState?.channel?.members?.any { it.id == "1241383865478807582" } == true) {
                            player.sendMessage(formattedMessage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error in YeonhongChatListener: ${e.message}")
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

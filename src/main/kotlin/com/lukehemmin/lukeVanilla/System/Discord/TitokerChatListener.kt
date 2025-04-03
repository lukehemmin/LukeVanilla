package com.lukehemmin.lukeVanilla.System.Discord

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.entities.Member
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import org.bukkit.Bukkit
import java.util.regex.Pattern

class TitokerChatListener(private val plugin: Main) : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        try {
            if (plugin.database.isDataSourceClosed()) {
                return
            }

            // 티토커 채널 ID 가져오기
            val titokerChannelId = plugin.database.getSettingValue("TTS_Channel") ?: return

            // 메시지가 티토커 채널에서 온 것이 아니면 무시
            if (event.channel.id != titokerChannelId) return

            // 티토커 본인의 메시지면 무시, 미니쿠다 무시
            if (event.author.id == "941683178920378439" || event.author.id == "595258464729825290") return

            // 멤버의 표시 이름 가져오기
            val displayName = event.member?.let { getDisplayName(it) } ?: event.author.name

            // 메시지에서 이모지 변환
            val parsedMessage = replaceEmojis(event.message.contentDisplay)

            // 메시지 전송
            val formattedMessage = "&7&l[티토커 채팅] &f$displayName &7&l: &f$parsedMessage".translateColorCodes()

            // 활성화된 플레이어들에게만 메시지 전송
            Bukkit.getOnlinePlayers().forEach { player ->
                if (plugin.database.isTitokerMessageEnabled(player.uniqueId.toString())) {
                    // 해당 플레이어가 티토커와 같은 음성채널에 있는지 확인
                    val playerDiscordId = plugin.database.getDiscordIDByUUID(player.uniqueId.toString())
                    if (playerDiscordId != null) {
                        val member = event.guild.getMemberById(playerDiscordId)
                        if (member?.voiceState?.channel?.members?.any { it.id == "941683178920378439" } == true) {
                            player.sendMessage(formattedMessage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error in TitokerChatListener: ${e.message}")
        }
    }

    /**
     * 이모티콘을 사용자 정의 포맷으로 변환하는 함수
     */
    private fun replaceEmojis(text: String): String {
        // 텍스트 기반 이모지 패턴 (예: :_l:, :_g: 등)
        val textEmojiPattern = Pattern.compile(":[_a-z]+:|:naanhae:|:hi:|:notme:|:ing:")
        var result = text

        // 텍스트 기반 이모지 변환
        val matcher = textEmojiPattern.matcher(result)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val emoji = matcher.group()
            val replacement = convertEmojiToAlias(emoji)
            matcher.appendReplacement(buffer, replacement)
        }
        matcher.appendTail(buffer)

        return buffer.toString()
    }

    /**
     * 이모티콘을 사용자 정의 이름으로 매핑하는 함수
     */
    private fun convertEmojiToAlias(emoji: String): String {
        return when (emoji) {
            ":_gi:" -> "௛" // no_entry
            ":_p:" -> "௕" // ppeotti_emoji
            ":_yu:" -> "௘" // face_with_symbols_on_mouth
            ":_pa:" -> "௖" // pangdoro_emoji
            ":_n:" -> "௔" // numbora_emoji
            ":_g:" -> "ௗ" // gam_emoji
            ":_y:" -> "௙" // yousu_emoji
            ":_s:" -> "௚" // soonback_emoji
            ":_m:" -> "௓" // maolo_emoji
            ":_z:" -> "௒" // zuri_emoji
            ":_f:" -> "௑" // fish_emoji
            ":_l:" -> "ௐ" // hemmin_emoji
            ":d_:" -> "ꐤ" // dolbe_emoji
            ":_pu:" -> "ꐥ" // pumkin_emoji
            ":naanhae:" -> "ꐠ" // hyeok_emoji
            ":hi:" -> "ꐡ" // yeong_emoji
            ":notme:" -> "ꐢ" // dmddoo_emoji
            ":ing:" -> "ꐣ" // kimjeokhan_emoji
            ":kkk:" -> "ꑨ" // dorahee_emoji
            ":luckini:" -> "ꑪ" // luckini_emoji
            ":no:" -> "ꑫ" // karon_emoji
            ":yes:" -> "ꑬ" // nlris_emoji
            ":mahot:" -> "ꑭ" // dubu_emoji
            else -> emoji
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
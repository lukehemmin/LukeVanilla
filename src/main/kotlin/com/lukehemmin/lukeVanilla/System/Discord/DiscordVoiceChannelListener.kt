package com.lukehemmin.lukeVanilla.System.Discord

import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class DiscordVoiceChannelListener(private val plugin: Main) : ListenerAdapter() {

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        val member: Member = event.member
        val channelJoined = event.channelJoined
        val channelLeft = event.channelLeft
        val guild: Guild = event.guild

        // 데이터베이스에서 DiscordServerID 가져오기
        val discordServerId = plugin.database.getSettingValue("DiscordServerID")

        // 이벤트가 설정된 서버에서 발생한 경우에만 진행
        if (discordServerId == guild.id) {
            val displayName = getDisplayName(member) // 디스코드 유저의 표시 이름을 가져옴

            // 로그 추가
            //plugin.logger.info("Voice Update - Member: ${member.user.name} (${member.id}), Joined: ${channelJoined?.name}, Left: ${channelLeft?.name}")

            // 채널을 변경하는 경우 (이동)
            if (channelLeft != null && channelJoined != null) {
                handleChannelLeft(member, channelLeft, displayName)
                handleChannelJoined(member, channelJoined, displayName)
                return
            }

            // 채널에 들어간 경우
            if (channelJoined != null) {
                handleChannelJoined(member, channelJoined, displayName)
                return
            }

            // 채널에서 나간 경우
            if (channelLeft != null) {
                handleChannelLeft(member, channelLeft, displayName)
                return
            }
        }
    }

    private fun handleChannelJoined(member: Member, channelJoined: AudioChannelUnion, displayName: String) {
        val voiceChannelName = channelJoined.name
        val parsedChannelName = EmojiUtil.replaceEmojis(voiceChannelName)

        // 통화방에 들어온 유저에게 보내는 메시지
        val joinMessageForUser = "&f&l$parsedChannelName &a&l통화방에 들어왔습니다.".translateColorCodes()

        // 이미 통화방에 있는 유저들에게 보내는 메시지
        val joinMessageForOthers = "&a&l[+] &f&l$displayName &f&l님이 $parsedChannelName 통화방에 들어오셨습니다.".translateColorCodes()

        // 티토커 알림 메시지
        val titokerMessage = "&e&l/티토커메시지 보기 - 명령어를 입력하여 티토커 채팅을 볼 수 있어요.".translateColorCodes()

        // 들어온 유저의 마인크래프트 플레이어 가져오기
        val joiningPlayerUUID = plugin.database.getPlayerUUIDByDiscordID(member.id)
        val joiningPlayer = joiningPlayerUUID?.let { Bukkit.getPlayer(java.util.UUID.fromString(it)) }

        // 현재 통화방에 있는 디스코드 멤버들 가져오기
        val channelMembers = channelJoined.members

        // 통화방에 티토커가 있는지 확인
        val titokerPresent = channelMembers.any { it.id == "941683178920378439" }

        // 들어온 유저에게 메시지 전송
        joiningPlayer?.sendMessage(joinMessageForUser)

        // 일반 유저가 들어왔고 통화방에 티토커가 있는 경우
        if (member.id != "941683178920378439" && titokerPresent) {
            joiningPlayer?.let { player ->
                player.sendMessage(titokerMessage)
                val isEnabled = plugin.database.isTitokerMessageEnabled(player.uniqueId.toString())
                val statusMessage = "&f&l티토커메시지 보기 활성화 여부 : ${if (isEnabled) "&a&l활성화" else "&c&l비활성화"}".translateColorCodes()
                player.sendMessage(statusMessage)
            }
        }

        // 같은 통화방에 있는 유저들에게만 메시지 전송
        channelMembers.forEach { channelMember ->
            if (channelMember.id != member.id) { // 자기 자신 제외
                val currentUUID = plugin.database.getPlayerUUIDByDiscordID(channelMember.id)
                if (currentUUID != null) {
                    val currentPlayer = Bukkit.getPlayer(java.util.UUID.fromString(currentUUID))
                    currentPlayer?.let { player ->
                        player.sendMessage(joinMessageForOthers)

                        // 특정 ID가 들어왔을 때 추가 메시지 전송
                        if (member.id == "941683178920378439") {
                            player.sendMessage(titokerMessage)
                            // 티토커메시지 활성화 상태 확인 및 메시지 전송
                            val isEnabled = plugin.database.isTitokerMessageEnabled(player.uniqueId.toString())
                            val statusMessage = "&f&l티토커메시지 보기 활성화 여부 : ${if (isEnabled) "&a&l활성화" else "&c&l비활성화"}".translateColorCodes()
                            player.sendMessage(statusMessage)
                        }
                    }

//                    // 특정 ID가 들어왔을 때 추가 메시지 전송
//                    if (member.id == "941683178920378439") {
//                        currentPlayer?.sendMessage(titokerMessage)
//                    }
                }
            }
        }

        // 들어온 유저가 특정 ID일 경우 자신에게도 티토커 메시지 전송
        if (member.id == "941683178920378439") {
            joiningPlayer?.sendMessage(titokerMessage)
        }
    }

    private fun handleChannelLeft(member: Member, channelLeft: AudioChannelUnion, displayName: String) {
        val voiceChannelName = channelLeft.name
        val parsedChannelName = EmojiUtil.replaceEmojis(voiceChannelName)

        // 통화방에서 나간 유저에게 보내는 메시지
        val leaveMessageForUser = "&f&l$parsedChannelName &c&l통화방에서 나갔습니다.".translateColorCodes()

        // 이미 통화방에 있는 유저들에게 보내는 메시지
        val leaveMessageForOthers = "&c&l[-] &f&l$displayName &f&l님이 $parsedChannelName 통화방에서 나가셨습니다.".translateColorCodes()

        // 나간 유저의 마인크래프트 플레이어 가져오기
        val leavingPlayerUUID = plugin.database.getPlayerUUIDByDiscordID(member.id)
        val leavingPlayer = leavingPlayerUUID?.let { Bukkit.getPlayer(java.util.UUID.fromString(it)) }

        // 현재 통화방에 남아있는 디스코드 멤버들 가져오기
        val channelMembers = channelLeft.members

        // 나간 유저에게 메시지 전송
        leavingPlayer?.sendMessage(leaveMessageForUser)

        // 같은 통화방에 있는 유저들에게만 메시지 전송
        channelMembers.forEach { channelMember ->
            if (channelMember.id != member.id) { // 자기 자신 제외
                val currentUUID = plugin.database.getPlayerUUIDByDiscordID(channelMember.id)
                if (currentUUID != null) {
                    val currentPlayer = Bukkit.getPlayer(java.util.UUID.fromString(currentUUID))
                    currentPlayer?.sendMessage(leaveMessageForOthers)
                }
            }
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

        return when {
            // 1. Tag가 있으면 Tag 사용
            !tag.isNullOrBlank() -> {
                val cleanTag = tag.replace("[", "").replace("]", "")
                cleanTag.translateColorCodes()
            }
            // 2. 서버 별명이 있으면 서버 별명 사용
            !member.nickname.isNullOrBlank() -> member.nickname!!
            // 3. 디스코드 별명이 있으면 디스코드 별명 사용
            !member.user.effectiveName.isNullOrBlank() -> member.user.effectiveName
            // 4. 모두 없으면 디스코드 사용자명 사용
            else -> member.user.name
        }
    }
}
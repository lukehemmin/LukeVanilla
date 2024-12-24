package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.awt.Color

class SupportSystem(private val discordBot: DiscordBot, private val database: Database) : ListenerAdapter() {

    fun setupSupportChannel() {
        val channelId = database.getSettingValue("SystemChannel")
        if (channelId != null) {
            val channel = discordBot.jda.getTextChannelById(channelId)
            channel?.let {
                // 기존 메시지 ID 확인
                val messageId = database.getSettingValue("SupportMessageId")
                if (messageId != null) {
                    try {
                        // 기존 메시지가 있는지 확인
                        channel.retrieveMessageById(messageId).queue(
                            { /* 메시지가 존재하면 아무것도 하지 않음 */ },
                            {
                                // 메시지가 없으면 새로 생성
                                createAndSaveSupportMessage(channel)
                            }
                        )
                    } catch (e: Exception) {
                        createAndSaveSupportMessage(channel)
                    }
                } else {
                    // 저장된 메시지 ID가 없으면 새로 생성
                    createAndSaveSupportMessage(channel)
                }
            }
        }
    }

    private fun createAndSaveSupportMessage(channel: TextChannel) {
        val embed = EmbedBuilder()
            .setTitle("고객지원")
            .setDescription("아래 버튼을 눌러 원하는 지원을 선택하세요.")
            .setColor(Color.BLUE)
            .build()

        val buttons = listOf(
            Button.primary("my_info", "내 정보"),
            Button.secondary("admin_support", "관리자 문의"),
            Button.success("halloween_info", "할로윈 아이템 등록 정보")
        )

        channel.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(buttons))
            .queue { message ->
                // 새 메시지 ID를 데이터베이스에 저장
                database.setSetting("SupportMessageId", message.id)
            }
    }

//    override fun onButtonInteraction(event: ButtonInteractionEvent) {
//        when (event.componentId) {
//            "my_info" -> {
//                // 내 정보 버튼 처리
//                event.reply("내 정보 기능은 아직 구현 중입니다.").setEphemeral(true).queue()
//            }
//            "halloween_info" -> {
//                val halloweenViewer = HalloweenItemViewer(database)
//                halloweenViewer.createItemInfoEmbed(event.user.id)
//                    .onSuccess { embed ->
//                        event.replyEmbeds(embed).setEphemeral(true).queue()
//                    }
//                    .onFailure { error ->
//                        event.reply(error.message ?: "오류가 발생했습니다.").setEphemeral(true).queue()
//                    }
//            }
//        }
//    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        event.deferReply(true).queue() // 먼저 응답 대기 상태를 전송

        when (event.componentId) {
            "my_info" -> {
                // 실제 로직 수행 후
                event.hook.sendMessage("내 정보 기능은 아직 구현 중입니다.")
                    .setEphemeral(true)
                    .queue()
            }
            "halloween_info" -> {
                val halloweenViewer = HalloweenItemViewer(database)
                halloweenViewer.createItemInfoEmbed(event.user.id)
                    .onSuccess { embed ->
                        event.hook.sendMessageEmbeds(embed)
                            .setEphemeral(true)
                            .queue()
                    }
                    .onFailure { error ->
                        event.hook.sendMessage(error.message ?: "오류가 발생했습니다.")
                            .setEphemeral(true)
                            .queue()
                    }
            }
        }
    }
}
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

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId == "my_info") {
            event.deferReply(true).queue()
            event.hook.sendMessage("내 정보 기능은 아직 구현 중입니다.")
                .setEphemeral(true)
                .queue()
        }
        // 다른 버튼은 여기서 처리하지 않음
    }

    fun setupSupportChannel() {
        val channelId = database.getSettingValue("SystemChannel")
        if (channelId != null) {
            val channel = discordBot.jda.getTextChannelById(channelId)
            channel?.let {
                val messageId = database.getSettingValue("SupportMessageId")
                if (messageId != null) {
                    try {
                        channel.retrieveMessageById(messageId).queue(
                            { /* 메시지가 존재하면 아무것도 하지 않음 */ },
                            {
                                createAndSaveSupportMessage(channel)
                            }
                        )
                    } catch (e: Exception) {
                        createAndSaveSupportMessage(channel)
                    }
                } else {
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
            Button.success("halloween_info", "아이템 등록 정보 보기")
        )

        channel.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(buttons))
            .queue { message ->
                database.setSetting("SupportMessageId", message.id)
            }
    }
}
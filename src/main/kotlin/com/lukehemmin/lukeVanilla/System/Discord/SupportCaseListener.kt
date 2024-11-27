package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal

class SupportCaseListener(private val database: Database, private val discordBot: DiscordBot) : ListenerAdapter() {

    private val supportCaseManager = SupportCaseManager(database, discordBot)

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when {
            event.componentId == "admin_support" -> {
                val modal = SupportCaseModal.create()
                event.replyModal(modal).queue()
            }
            event.componentId.startsWith("close_case:") -> {
                val caseId = event.componentId.substringAfter("close_case:")
                val channel = event.channel.asTextChannel()

                try {
                    supportCaseManager.closeSupportCase(channel, caseId)
                    event.reply("문의가 종료되었습니다.")
                        .setEphemeral(true)
                        .queue()
                } catch (e: Exception) {
                    event.reply("문의 종료 중 오류가 발생했습니다: ${e.message}")
                        .setEphemeral(true)
                        .queue()
                }
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId == "support_case_modal") {
            val title = event.getValue("support_title")?.asString
            val description = event.getValue("support_description")?.asString

            if (title == null || description == null) {
                event.reply("문의 제목과 내용을 모두 입력해주세요.").setEphemeral(true).queue()
                return
            }

            // 사용자의 Discord ID 조회
            val discordId = event.user.id
            val playerData = database.getPlayerDataByDiscordId(discordId)

            if (playerData == null) {
                event.reply("플레이어 정보를 찾을 수 없습니다.").setEphemeral(true).queue()
                return
            }

            // 문의 케이스 생성
            val supportChannelLink = supportCaseManager.createSupportCase(
                uuid = playerData.uuid,
                discordId = discordId,
                supportTitle = title,
                supportDescription = description
            )

            if (supportChannelLink != null) {
                event.reply("문의가 접수되었습니다. 채널 링크: $supportChannelLink")
                    .setEphemeral(true)
                    .queue()
            } else {
                event.reply("문의 생성 중 오류가 발생했습니다.")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }
}
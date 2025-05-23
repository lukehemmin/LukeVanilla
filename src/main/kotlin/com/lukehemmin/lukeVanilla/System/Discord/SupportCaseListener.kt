package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.concurrent.TimeUnit

class SupportCaseListener(private val database: Database, private val discordBot: DiscordBot) : ListenerAdapter() {

    private val supportCaseManager = SupportCaseManager(database, discordBot)
    private val seasonItemViewer = SeasonItemViewer(database)

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
            event.componentId == "halloween_info" -> {
                // 시즌 선택 버튼 표시 (할로윈, 크리스마스, 발렌타인)
                seasonItemViewer.showSeasonSelectionButtons(event)
            }
            event.componentId == "season_halloween" -> {
                event.deferReply(true).queue()
                seasonItemViewer.createHalloweenItemInfoEmbed(event.user.id)
                    .onSuccess { embed ->
                        event.hook.sendMessageEmbeds(embed)
                            .setEphemeral(true) // 에피머럴 메시지는 사용자에게만 표시됨
                            .queue()
                    }
                    .onFailure { error ->
                        event.hook.sendMessage(error.message ?: "오류가 발생했습니다.")
                            .setEphemeral(true) // 에피머럴 메시지는 사용자에게만 표시됨
                            .queue()
                    }
            }
            event.componentId == "season_christmas" -> {
                event.deferReply(true).queue()
                seasonItemViewer.createChristmasItemInfoEmbed(event.user.id)
                    .onSuccess { embed ->
                        event.hook.sendMessageEmbeds(embed)
                            .setEphemeral(true) // 에피머럴 메시지는 사용자에게만 표시됨
                            .queue()
                    }
                    .onFailure { error ->
                        event.hook.sendMessage(error.message ?: "오류가 발생했습니다.")
                            .setEphemeral(true) // 에피머럴 메시지는 사용자에게만 표시됨
                            .queue()
                    }
            }
            event.componentId == "season_valentine" -> {
                event.deferReply(true).queue()
                seasonItemViewer.createValentineItemInfoEmbed(event.user.id)
                    .onSuccess { embed ->
                        event.hook.sendMessageEmbeds(embed)
                            .setEphemeral(true) // 에피머럴 메시지는 사용자에게만 표시됨
                            .queue()
                    }
                    .onFailure { error ->
                        event.hook.sendMessage(error.message ?: "오류가 발생했습니다.")
                            .setEphemeral(true) // 에피머럴 메시지는 사용자에게만 표시됨
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

            val discordId = event.user.id
            val playerData = database.getPlayerDataByDiscordId(discordId)

            if (playerData == null) {
                event.reply("플레이어 정보를 찾을 수 없습니다.").setEphemeral(true).queue()
                return
            }

            val supportChannelLink = supportCaseManager.createSupportCase(
                uuid = playerData.uuid,
                discordId = discordId,
                supportTitle = title,
                supportDescription = description
            )

            if (supportChannelLink != null) {
                event.reply("문의가 접수되었습니다. 채널 링크: $supportChannelLink")
                    .setEphemeral(true)
                    // 1분 후 자동 삭제 (에피머럴로 설정되어 있으므로 별도로 삭제할 필요 없음)
                    .queue()
            } else {
                event.reply("열려 있는 문의가 최대 3개입니다.\n기존 문의를 종료한 후 다시 시도해주세요.")
                    .setEphemeral(true)
                    // 1분 후 자동 삭제 (에피머럴로 설정되어 있으므로 별도로 삭제할 필요 없음)
                    .queue()
            }
        }
    }
}
package com.lukehemmin.lukeVanilla.System.PeperoGifticon

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.awt.Color
import java.time.Instant
import java.util.logging.Logger

/**
 * 빼빼로 기프티콘 Discord 버튼 인터랙션 리스너
 */
class PeperoGifticonDiscordListener(
    private val repository: PeperoGifticonRepository,
    private val logger: Logger
) : ListenerAdapter() {

    companion object {
        const val BUTTON_ORIGINAL = "pepero_gifticon_original"
        const val BUTTON_ALMOND = "pepero_gifticon_almond"
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val buttonId = event.componentId

        // 빼빼로 기프티콘 버튼이 아니면 무시
        if (buttonId != BUTTON_ORIGINAL && buttonId != BUTTON_ALMOND) {
            return
        }

        // DM에서만 동작
        if (!event.isFromGuild && event.channel.type.isMessage) {
            handleGifticonSelection(event, buttonId)
        }
    }

    /**
     * 기프티콘 선택 처리
     */
    private fun handleGifticonSelection(event: ButtonInteractionEvent, buttonId: String) {
        event.deferReply(true).queue()

        val discordId = event.user.id
        val gifticonType = when (buttonId) {
            BUTTON_ORIGINAL -> "original"
            BUTTON_ALMOND -> "almond"
            else -> {
                event.hook.sendMessage("❌ 잘못된 요청입니다.").setEphemeral(true).queue()
                return
            }
        }

        val gifticonName = when (gifticonType) {
            "original" -> "오리지널 빼빼로"
            "almond" -> "아몬드 빼빼로"
            else -> "빼빼로"
        }

        try {
            // 1. 수령 대상자인지 확인
            val recipient = repository.getRecipientByDiscordId(discordId)
            if (recipient == null) {
                event.hook.sendMessage("❌ 기프티콘 수령 대상자가 아닙니다.").setEphemeral(true).queue()
                logger.warning("[PeperoGifticon] 수령 대상자 아님: Discord ID = $discordId")
                return
            }

            // 2. 이미 받았는지 확인
            if (recipient.hasReceived) {
                event.hook.sendMessage("❌ 이미 기프티콘을 받으셨습니다. (받은 종류: ${recipient.gifticonType})").setEphemeral(true).queue()
                logger.warning("[PeperoGifticon] 이미 수령함: ${recipient.playerName}")
                return
            }

            // 3. 사용 가능한 기프티콘 코드 확인
            val availableCode = repository.getAvailableGifticonCode(gifticonType)
            if (availableCode == null) {
                // 재고 부족
                event.hook.sendMessage("❌ **$gifticonName** 기프티콘이 소진되었습니다.\n다른 종류를 선택해주세요.").setEphemeral(true).queue()
                logger.warning("[PeperoGifticon] 재고 소진: $gifticonType")

                // 재고 부족 알림 (관리자 로그)
                val originalCount = repository.getAvailableGifticonCount("original")
                val almondCount = repository.getAvailableGifticonCount("almond")
                logger.warning("[PeperoGifticon] 현재 재고 - 오리지널: $originalCount, 아몬드: $almondCount")

                return
            }

            // 4. 기프티콘 사용 처리 (트랜잭션)
            val success = repository.markGifticonAsUsed(
                codeId = availableCode.id,
                uuid = recipient.uuid,
                discordId = discordId,
                gifticonType = gifticonType
            )

            if (!success) {
                event.hook.sendMessage("❌ 기프티콘 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.").setEphemeral(true).queue()
                logger.severe("[PeperoGifticon] 트랜잭션 실패: ${recipient.playerName}")
                return
            }

            // 5. 기프티콘 이미지 전송
            sendGifticonImage(event, recipient.playerName, gifticonName, availableCode.imageUrl)

            // 6. 성공 로그
            logger.info("[PeperoGifticon] 기프티콘 발급 성공: ${recipient.playerName} ($discordId) - $gifticonName")

            // 7. 재고 확인 및 경고
            val remainingCount = repository.getAvailableGifticonCount(gifticonType)
            if (remainingCount <= 5) {
                logger.warning("[PeperoGifticon] ⚠️ 재고 부족 경고: $gifticonName 남은 개수 = $remainingCount")
            }

        } catch (e: Exception) {
            logger.severe("[PeperoGifticon] 기프티콘 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
            event.hook.sendMessage("❌ 오류가 발생했습니다. 관리자에게 문의해주세요.").setEphemeral(true).queue()
        }
    }

    /**
     * 기프티콘 이미지를 DM으로 전송
     */
    private fun sendGifticonImage(
        event: ButtonInteractionEvent,
        playerName: String,
        gifticonName: String,
        imageUrl: String
    ) {
        val embed = EmbedBuilder()
            .setTitle("🎁 빼빼로 기프티콘")
            .setDescription("**${playerName}**님께 **${gifticonName}** 기프티콘을 지급해드립니다!")
            .setColor(Color.ORANGE)
            .setImage(imageUrl)
            .addField(
                "📌 사용 안내",
                "위 이미지를 편의점에서 보여주시면 됩니다.\n이미지는 따로 저장해주세요!",
                false
            )
            .setFooter("LukeVanilla 빼빼로 보상 시스템")
            .setTimestamp(Instant.now())
            .build()

        // 원본 메시지 업데이트 (버튼 비활성화)
        event.message.editMessageComponents().queue()

        // Ephemeral 응답
        event.hook.sendMessage("✅ **${gifticonName}** 기프티콘이 발급되었습니다!").setEphemeral(true).queue()

        // 기프티콘 이미지를 새 메시지로 전송
        event.user.openPrivateChannel().queue { channel ->
            channel.sendMessageEmbeds(embed).queue(
                {
                    logger.info("[PeperoGifticon] DM 전송 성공: $playerName")
                },
                { error ->
                    logger.warning("[PeperoGifticon] DM 전송 실패: $playerName - ${error.message}")
                }
            )
        }
    }

    /**
     * 초기 DM 메시지 생성 (버튼 포함)
     */
    fun createInitialDM(): Pair<MessageEmbed, List<Button>> {
        val embed = EmbedBuilder()
            .setTitle("🍫 빼빼로 기프티콘 보상")
            .setDescription(
                """
                11월 10일 오후 11시 08분 부터 진행된 빼빼로 웹 이벤트 오류로 인해
                지급 예정이었던 빼빼로 기프티콘 입니다.

                어떤 빼빼로를 받으시겠어요?
                """.trimIndent()
            )
            .setColor(Color.ORANGE)
            .addField(
                "⚠️ 주의사항",
                "• 한 번만 선택할 수 있습니다.\n• 선택 후 변경이 불가능합니다.\n• 재고가 소진되면 다른 종류를 선택해야 합니다.",
                false
            )
            .setFooter("LukeVanilla 빼빼로 보상 시스템")
            .setTimestamp(Instant.now())
            .build()

        val buttons = listOf(
            Button.primary(BUTTON_ORIGINAL, "🍫 오리지널 빼빼로"),
            Button.secondary(BUTTON_ALMOND, "🥜 아몬드 빼빼로")
        )

        return Pair(embed, buttons)
    }
}

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

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when (event.componentId) {
            "my_info" -> {
                // 내 정보 버튼 처리
                event.reply("내 정보 기능은 아직 구현 중입니다.").setEphemeral(true).queue()
            }
            "halloween_info" -> {
                // 할로윈 아이템 등록 정보 버튼 처리
                val userId = event.user.id
                val playerData = database.getPlayerDataByDiscordId(userId)

                if (playerData == null) {
                    event.reply("연동된 마인크래프트 계정을 찾을 수 없습니다.").setEphemeral(true).queue()
                    return
                }
                
                // 할로윈 아이템 이름 매핑
                val itemNames = mapOf(
                    "sword" to "호박의 빛 검",
                    "pickaxe" to "호박 곡괭이",
                    "axe" to "호박 도끼",
                    "shovel" to "호박 삽",
                    "hoe" to "호박 괭이",
                    "bow" to "호박의 마법 활",
                    "fishing_rod" to "호박의 낚싯대",
                    "hammer" to "호박의 철퇴",
                    "hat" to "호박의 마법 모자",
                    "scythe" to "호박의 낫",
                    "spear" to "호박의 창"
                )

                database.getConnection().use { connection ->
                    val statement = connection.prepareStatement(
                        """SELECT * FROM Halloween_Item_Owner WHERE UUID = ?"""
                    )
                    statement.setString(1, playerData.uuid)
                    val resultSet = statement.executeQuery()

                    // 결과를 저장할 StringBuilder
                    val itemsList = StringBuilder()
                    itemsList.appendLine("등록된 할로윈 아이템")

                    if (resultSet.next()) {
                        // 모든 아이템을 순회하면서 상태 표시
                        itemNames.forEach{ (columnName, itemName) ->
                            val hasItem = resultSet.getBoolean(columnName)
                            itemsList.appendLine("$itemName: ${if (hasItem) "✅" else "❌"}")
                        }
                    } else {
                        // 계정은 있지만 아이템 데이터가 없는 경우
                        itemNames.forEach { (_, itemName) ->
                            itemsList.appendLine("$itemName: ❌")
                        }
                    }

                    val embed = EmbedBuilder()
                        .setTitle("🎃 할로윈 아이템 등록 정보")
                        .setDescription("현재 등록된 할로윈 아이템 목록입니다.")
                        .addField("닉네임", playerData.nickname, false)
                        .addField("아이템 목록", itemsList.toString(), false)
                        .setColor(Color.ORANGE)
                        .setTimestamp(java.time.Instant.now())
                        .build()

                    event.replyEmbeds(embed).setEphemeral(true).queue()
                }
            }
        }
    }
}
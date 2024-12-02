package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

class SupportCaseManager(
    private val database: Database,
    private val discordBot: DiscordBot
) {
    fun generateSupportCaseId(): String {
        // 마지막 문의 번호 조회
        val lastCaseId = getLastSupportCaseId()

        // 새 문의 번호 생성 (000001부터 시작)
        val newCaseId = if (lastCaseId.isNullOrEmpty()) {
            "000001"
        } else {
            val nextNumber = lastCaseId.toInt() + 1
            "%06d".format(nextNumber)
        }

        return newCaseId
    }

    private fun getLastSupportCaseId(): String? {
        return database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT SupportID FROM SupportChatLink ORDER BY SupportID DESC LIMIT 1"
            )
            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                // #을 제외한 숫자 부분만 반환
                resultSet.getString("SupportID")?.replace("#", "")
            } else {
                null
            }
        }
    }

    // SupportCaseManager.kt 수정
    fun createSupportCase(
        uuid: String,
        discordId: String,  // discordId 파라미터 사용
        supportTitle: String,
        supportDescription: String
    ): String? {
        // 해당 사용자의 열려 있는 문의 개수 확인
        val openCasesCount = database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM SupportChatLink WHERE UUID = ? AND CaseClose = 0"
            )
            statement.setString(1, uuid)
            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                resultSet.getInt(1)
            } else {
                0
            }
        }

        // 열려 있는 문의 개수가 3개 이상이면 문의 생성 중단
        if (openCasesCount >= 3) {
            // 문의 생성 불가
            return null
        }

        // 문의 케이스 ID 생성
        val supportCaseId = generateSupportCaseId()
        val fullSupportCaseId = "#$supportCaseId"
        val supportCategory = getSupportCategory(discordBot.jda)
        val guild = supportCategory.guild

        // 채널 생성
        val supportChannel = supportCategory.createTextChannel("문의-케이스-$fullSupportCaseId")
            .addMemberPermissionOverride(
                discordId.toLong(),
                listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                null
            )
            .addRolePermissionOverride(
                guild.publicRole.idLong,  // @everyone 역할
                null,
                listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
            )
            .complete()

        val buttons = ActionRow.of(
            Button.danger("close_case:$fullSupportCaseId", "문의 종료")
        )

        val embedMessage = supportChannel.sendMessageEmbeds(
            EmbedBuilder()
                .setTitle("문의 케이스 $fullSupportCaseId")
                .addField("제목", supportTitle, false)
                .addField("설명", supportDescription, false)
                .setTimestamp(java.time.Instant.now())
                .build()
        ).setComponents(buttons).complete()

        // 데이터베이스에 정보 저장
        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                """
            INSERT INTO SupportChatLink 
            (UUID, SupportID, CaseClose, MessageLink) 
            VALUES (?, ?, ?, ?)
            """
            )
            statement.setString(1, uuid)
            statement.setString(2, fullSupportCaseId)
            statement.setInt(3, 0)
            statement.setString(4, supportChannel.jumpUrl)
            statement.executeUpdate()
        }

        return supportChannel.jumpUrl
    }

    private fun getSupportCategory(jda: JDA): Category {
        // 서버의 Support 카테고리 ID 조회
        val supportCategoryId = database.getSettingValue("SupportCategoryId")
            ?: throw IllegalStateException("Support 카테고리 ID를 찾을 수 없습니다.")

        // 카테고리 조회
        return jda.getCategoryById(supportCategoryId)
            ?: throw IllegalStateException("ID: $supportCategoryId 에 해당하는 Support 카테고리를 찾을 수 없습니다.")
    }

    fun closeSupportCase(channel: TextChannel, caseId: String) {
        // 아카이브 카테고리 ID 조회
        val archiveCategoryId = database.getSettingValue("SupportArchiveCategoryId")
            ?: throw IllegalStateException("아카이브 카테고리 ID를 찾을 수 없습니다.")

        val archiveCategory = discordBot.jda.getCategoryById(archiveCategoryId)
            ?: throw IllegalStateException("아카이브 카테고리를 찾을 수 없습니다.")

        // 모든 멤버의 권한 제거
        channel.memberPermissionOverrides.forEach { override ->
            override.delete().queue()
        }

        // 채널을 아카이브 카테고리로 이동
        channel.manager.setParent(archiveCategory).queue()

        // DB 상태 업데이트
        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "UPDATE SupportChatLink SET CaseClose = 1 WHERE SupportID = ?"
            )
            statement.setString(1, caseId)
            statement.executeUpdate()
        }
    }
}
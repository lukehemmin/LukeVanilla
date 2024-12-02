package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color

class HalloweenItemViewer(private val database: Database) {
    private val itemNames = mapOf(
        "sword" to "호박의 빛 검",
        "pickaxe" to "호박 곡괭이",
        "axe" to "호박 도끼",
        "shovel" to "호박 삽",
        "hoe" to "호박 괭이",
        "bow" to "호박의 마법 활",
        "fishing_rod" to "호박의 낚시대",
        "hammer" to "호박의 철퇴",
        "hat" to "호박의 마법 모자",
        "scythe" to "호박의 낫",
        "spear" to "호박의 창"
    )

    fun createItemInfoEmbed(discordId: String): Result<MessageEmbed> {
        val playerData = database.getPlayerDataByDiscordId(discordId)
            ?: return Result.failure(IllegalStateException("연동된 마인크래프트 계정을 찾을 수 없습니다."))

        val itemsList = StringBuilder()
        itemsList.appendLine("등록된 할로윈 아이템")

        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                """SELECT * FROM Halloween_Item_Owner WHERE UUID = ?"""
            )
            statement.setString(1, playerData.uuid)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                itemNames.forEach { (columnName, itemName) ->
                    val hasItem = resultSet.getBoolean(columnName)
                    itemsList.appendLine("$itemName ${if (hasItem) "✅" else "❌"}")
                }
            } else {
                itemNames.forEach { (_, itemName) ->
                    itemsList.appendLine("$itemName ❌")
                }
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

        return Result.success(embed)
    }
}
package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color
import java.time.Instant
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

class SeasonItemViewer(private val database: Database) {
    // 할로윈 아이템 매핑
    private val halloweenItems = mapOf(
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
    
    // 크리스마스 아이템 매핑
    private val christmasItems = mapOf(
        "sword" to listOf("크리스마스 검", "크리스마스 대검"),  // DB 컬럼은 sword 하나지만 두 아이템 매핑
        "pickaxe" to "크리스마스 곡괭이",
        "axe" to "크리스마스 도끼",
        "shovel" to "크리스마스 삽",
        "hoe" to "크리스마스 괭이",
        "bow" to "크리스마스 활",
        "crossbow" to "크리스마스 석궁",
        "fishing_rod" to "크리스마스 낚싯대",
        "hammer" to "크리스마스 철퇴",
        "shield" to "크리스마스 방패",
        "head" to "크리스마스 모자",
        "helmet" to "크리스마스 투구",
        "chestplate" to "크리스마스 흉갑",
        "leggings" to "크리스마스 레깅스",
        "boots" to "크리스마스 부츠"
    )
    
    // 발렌타인 아이템 매핑
    private val valentineItems = mapOf(
        "sword" to listOf("ꐵ 발렌타인 러브 블레이드", "ꐩ 발렌타인 로즈 슬래셔"),  // DB 컬럼은 sword 하나지만 두 아이템 매핑
        "pickaxe" to "ꑥ 발렌타인 러브 마이너",
        "axe" to "ꐵ 발렌타인 하트 크래셔",
        "shovel" to "ꐩ 발렌타인 러브 디거",
        "hoe" to "ꐩ 발렌타인 가드너의 도구",
        "fishing_rod" to "ꐦ 발렌타인 큐피드 낚싯대",
        "bow" to "ꐦ 발렌타인 큐피드의 활",
        "crossbow" to "ꐦ 발렌타인 하트 석궁",
        "hammer" to "ꐵ 발렌타인 핑크 크래셔",
        "helmet" to "ꐵ 발렌타인 하트 가디언 헬멧",
        "chestplate" to "ꐵ 발렌타인 체스트 오브 러브",
        "leggings" to "ꐵ 발렌타인 로맨틱 레깅스",
        "boots" to "ꐵ 발렌타인 러브 트레커",
        "head" to "ꐵ 발렌타인 러버스 캡",
        "shield" to "ꐭ 발렌타인 러브 쉴드"
    )

    /**
     * 시즌 선택 버튼을 보여주는 메소드
     */
    fun showSeasonSelectionButtons(event: ButtonInteractionEvent) {
        val buttons = listOf(
            Button.primary("season_halloween", "할로윈 아이템 등록 정보"),
            Button.success("season_christmas", "크리스마스 아이템 등록 정보"),
            Button.danger("season_valentine", "발렌타인 아이템 등록 정보")
        )

        // 사용자에게만 보이는 메시지로 버튼 전송
        event.reply("확인하실 시즌 아이템 정보를 선택해주세요:")
            .setEphemeral(true) // 사용자에게만 표시
            .addComponents(ActionRow.of(buttons))
            .queue()
        // 에피머럴 메시지이므로 삭제 코드 제거 (JDA 5.2.1에서 지원하지 않음)
    }

    /**
     * 할로윈 아이템 정보 조회
     */
    fun createHalloweenItemInfoEmbed(discordId: String): Result<MessageEmbed> {
        val playerData = database.getPlayerDataByDiscordId(discordId)
            ?: return Result.failure(IllegalStateException("연동된 마인크래프트 계정을 찾을 수 없습니다."))

        val itemsList = StringBuilder()
        itemsList.appendLine("등록된 할로윈 아이템:")

        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                """SELECT * FROM Halloween_Item_Owner WHERE UUID = ?"""
            )
            statement.setString(1, playerData.uuid)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                halloweenItems.forEach { (columnName, itemName) ->
                    val hasItem = resultSet.getBoolean(columnName)
                    itemsList.appendLine("$itemName ${if (hasItem) "✅" else "❌"}")
                }
            } else {
                halloweenItems.forEach { (_, itemName) ->
                    itemsList.appendLine("$itemName ❌")
                }
            }
        }

        return Result.success(
            EmbedBuilder()
                .setTitle("🎃 할로윈 아이템 등록 정보")
                .setDescription(itemsList.toString())
                .setColor(Color.ORANGE)
                .setTimestamp(Instant.now())
                .build()
        )
    }

    /**
     * 크리스마스 아이템 정보 조회
     */
    fun createChristmasItemInfoEmbed(discordId: String): Result<MessageEmbed> {
        val playerData = database.getPlayerDataByDiscordId(discordId)
            ?: return Result.failure(IllegalStateException("연동된 마인크래프트 계정을 찾을 수 없습니다."))

        val itemsList = StringBuilder()
        itemsList.appendLine("등록된 크리스마스 아이템:")

        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                """SELECT * FROM Christmas_Item_Owner WHERE UUID = ?"""
            )
            statement.setString(1, playerData.uuid)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                christmasItems.forEach { (columnName, itemName) ->
                    val hasItem = resultSet.getBoolean(columnName)
                    
                    // sword 칼럼이지만 두 아이템이 매핑된 경우
                    if (itemName is List<*>) {
                        itemName.forEach { name ->
                            itemsList.appendLine("$name ${if (hasItem) "✅" else "❌"}")
                        }
                    } else {
                        itemsList.appendLine("$itemName ${if (hasItem) "✅" else "❌"}")
                    }
                }
            } else {
                christmasItems.forEach { (_, itemName) ->
                    if (itemName is List<*>) {
                        itemName.forEach { name ->
                            itemsList.appendLine("$name ❌")
                        }
                    } else {
                        itemsList.appendLine("$itemName ❌")
                    }
                }
            }
        }

        return Result.success(
            EmbedBuilder()
                .setTitle("🎄 크리스마스 아이템 등록 정보")
                .setDescription(itemsList.toString())
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now())
                .build()
        )
    }

    /**
     * 발렌타인 아이템 정보 조회
     */
    fun createValentineItemInfoEmbed(discordId: String): Result<MessageEmbed> {
        val playerData = database.getPlayerDataByDiscordId(discordId)
            ?: return Result.failure(IllegalStateException("연동된 마인크래프트 계정을 찾을 수 없습니다."))

        val itemsList = StringBuilder()
        itemsList.appendLine("등록된 발렌타인 아이템:")

        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                """SELECT * FROM Valentine_Item_Owner WHERE UUID = ?"""
            )
            statement.setString(1, playerData.uuid)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                valentineItems.forEach { (columnName, itemName) ->
                    val hasItem = resultSet.getBoolean(columnName)
                    
                    // sword 칼럼이지만 두 아이템이 매핑된 경우
                    if (itemName is List<*>) {
                        itemName.forEach { name ->
                            itemsList.appendLine("$name ${if (hasItem) "✅" else "❌"}")
                        }
                    } else {
                        itemsList.appendLine("$itemName ${if (hasItem) "✅" else "❌"}")
                    }
                }
            } else {
                valentineItems.forEach { (_, itemName) ->
                    if (itemName is List<*>) {
                        itemName.forEach { name ->
                            itemsList.appendLine("$name ❌")
                        }
                    } else {
                        itemsList.appendLine("$itemName ❌")
                    }
                }
            }
        }

        return Result.success(
            EmbedBuilder()
                .setTitle("💕 발렌타인 아이템 등록 정보")
                .setDescription(itemsList.toString())
                .setColor(Color.PINK)
                .setTimestamp(Instant.now())
                .build()
        )
    }
}

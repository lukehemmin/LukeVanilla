package com.lukehemmin.lukeVanilla.System.Discord.AIassistant

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Discord.ServerStatusProvider
import com.lukehemmin.lukeVanilla.System.WarningSystem.RiskLevel
import com.lukehemmin.lukeVanilla.System.WarningSystem.WarningService
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.awt.Color
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID


class AdminAssistant(
    private val dbConnectionProvider: () -> Connection,
    private val openAIApiKey: String? = null, // 생성자로 API 키를 전달받음
    private val database: Database,
    private val warningService: WarningService
) : ListenerAdapter() {
    private var openAIBaseUrl: String? = null
    private var openAIModel: String? = null

    private var assistantChannelId: String? = null
    private var assistantSecondaryChannelId: String? = null
    
    // MCP 스타일 도구 시스템
    private val toolManager = ToolManager()
    private val promptManager = PromptManager(toolManager)

    // 플레이어 정보를 담을 데이터 클래스
    data class PlayerInfo(
        val uuid: String,
        val nickname: String,
        val discordId: String?,
        val ip: String?,
        val isBanned: Boolean,
        val isAuth: Boolean,
        val isFirst: Boolean,
        val nameTag: String?
    )

    private val openAIClient: OpenAIClient by lazy {
        // API 키가 이미 주입되었으므로 다른 세팅만 로드
        loadOpenAISettings()
        val apiKeyLocal = openAIApiKey
        val baseUrlLocal = openAIBaseUrl
        OpenAIOkHttpClient.Companion.builder()
            .apply {
                if (!apiKeyLocal.isNullOrBlank()) apiKey(apiKeyLocal)
                if (!baseUrlLocal.isNullOrBlank()) baseUrl(baseUrlLocal)
                fromEnv()
            }
            .build()
    }

    init {
        assistantChannelId = fetchAssistantChannelId("Assistant_Channel")
        assistantSecondaryChannelId = fetchAssistantChannelId("AssistantSecondaryChannel")

        if (assistantChannelId == null && assistantSecondaryChannelId == null) {
            System.err.println("[AdminAssistant] 오류: 데이터베이스에서 채널 ID를 찾을 수 없습니다. AdminAssistant가 작동하지 않습니다.")
        } else {
            val channelInfo = buildString {
                if (assistantChannelId != null) append("채널 ID: $assistantChannelId")
                if (assistantChannelId != null && assistantSecondaryChannelId != null) append(", ")
                if (assistantSecondaryChannelId != null) append("보조 채널 ID: $assistantSecondaryChannelId")
            }
            println("[AdminAssistant] 초기화 완료. $channelInfo 에서 수신 대기 중...")
        }
    }

    /**
     * DB에서 OpenAI API 세팅값을 불러와 멤버 변수에 저장
     * (API 키는 이미 Main에서 설정되었으므로 나머지 세팅만 로드)
     */
    private fun loadOpenAISettings() {
        // API 키는 생성자에서 받으므로 여기서는 로드하지 않음
        openAIBaseUrl = fetchSettingValue("OpenAI_API_Endpoint")
        openAIModel = fetchSettingValue("OpenAI_API_Model")
    }

    /**
     * UUID 또는 닉네임으로 플레이어 정보를 조회
     */
    fun findPlayerInfo(identifier: String): PlayerInfo? {
        // UUID 또는 닉네임으로 플레이어 정보를 조회하는 쿼리
        val isUUID = identifier.length >= 32 // 간단히 UUID인지 확인 (정확한 검증은 아님)
        val query = if (isUUID) {
            val formattedUUID = if (identifier.contains("-")) identifier else
                identifier.replaceFirst(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5")
            "SELECT pd.UUID, pd.NickName, pd.DiscordID, pd.Lastest_IP, pd.IsBanned, pa.IsAuth, pa.IsFirst, pt.Tag " +
            "FROM lukevanilla.Player_Data pd " +
            "LEFT JOIN lukevanilla.Player_Auth pa ON pd.UUID = pa.UUID " +
            "LEFT JOIN lukevanilla.Player_NameTag pt ON pd.UUID = pt.UUID " +
            "WHERE pd.UUID = '$formattedUUID';"
        } else {
            "SELECT pd.UUID, pd.NickName, pd.DiscordID, pd.Lastest_IP, pd.IsBanned, pa.IsAuth, pa.IsFirst, pt.Tag " +
            "FROM lukevanilla.Player_Data pd " +
            "LEFT JOIN lukevanilla.Player_Auth pa ON pd.UUID = pa.UUID " +
            "LEFT JOIN lukevanilla.Player_NameTag pt ON pd.UUID = pt.UUID " +
            "WHERE pd.NickName = '$identifier';"
        }

        try {
            dbConnectionProvider().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { resultSet ->
                        if (resultSet.next()) {
                            return PlayerInfo(
                                uuid = resultSet.getString("UUID") ?: "",
                                nickname = resultSet.getString("NickName") ?: "",
                                discordId = resultSet.getString("DiscordID"),
                                ip = resultSet.getString("Lastest_IP"),
                                isBanned = resultSet.getBoolean("IsBanned"),
                                isAuth = resultSet.getBoolean("IsAuth"),
                                isFirst = resultSet.getBoolean("IsFirst"),
                                nameTag = resultSet.getString("Tag")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdminAssistant] 플레이어 정보 조회 중 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * 디스코드 ID로 플레이어 정보를 조회
     */
    private fun findPlayerInfoByDiscordId(discordId: String): PlayerInfo? {
        val query = "SELECT pd.UUID, pd.NickName, pd.DiscordID, pd.Lastest_IP, pd.IsBanned, pa.IsAuth, pa.IsFirst, pt.Tag " +
                   "FROM lukevanilla.Player_Data pd " +
                   "LEFT JOIN lukevanilla.Player_Auth pa ON pd.UUID = pa.UUID " +
                   "LEFT JOIN lukevanilla.Player_NameTag pt ON pd.UUID = pt.UUID " +
                   "WHERE pd.DiscordID = '$discordId';"

        try {
            dbConnectionProvider().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { resultSet ->
                        if (resultSet.next()) {
                            return PlayerInfo(
                                uuid = resultSet.getString("UUID") ?: "",
                                nickname = resultSet.getString("NickName") ?: "",
                                discordId = resultSet.getString("DiscordID"),
                                ip = resultSet.getString("Lastest_IP"),
                                isBanned = resultSet.getBoolean("IsBanned"),
                                isAuth = resultSet.getBoolean("IsAuth"),
                                isFirst = resultSet.getBoolean("IsFirst"),
                                nameTag = resultSet.getString("Tag")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdminAssistant] 디스코드 ID로 플레이어 정보 조회 중 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * 플레이어가 소유한 아이템 정보를 조회
     */
    fun getPlayerItems(uuid: String): Map<String, List<String>> {
        val itemCategories = mutableMapOf<String, List<String>>()

        try {
            dbConnectionProvider().use { connection ->
                // 할로윈 아이템
                val halloweenItems = getSeasonalItems(connection, uuid, "Halloween_Item_Owner")
                if (halloweenItems.isNotEmpty()) {
                    itemCategories["할로윈 아이템"] = halloweenItems
                }

                // 크리스마스 아이템
                val christmasItems = getSeasonalItems(connection, uuid, "Christmas_Item_Owner")
                if (christmasItems.isNotEmpty()) {
                    itemCategories["크리스마스 아이템"] = christmasItems
                }

                // 발렌타인 아이템
                val valentineItems = getSeasonalItems(connection, uuid, "Valentine_Item_Owner")
                if (valentineItems.isNotEmpty()) {
                    itemCategories["발렌타인 아이템"] = valentineItems
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdminAssistant] 아이템 조회 중 오류: ${e.message}")
            e.printStackTrace()
        }

        return itemCategories
    }

    /**
     * 시즌별 아이템 소유 정보 조회
     */
    private fun getSeasonalItems(connection: Connection, uuid: String, tableName: String): List<String> {
        val items = mutableListOf<String>()
        try {
            val columnQuery = "SHOW COLUMNS FROM lukevanilla.$tableName WHERE Field != 'UUID' AND Field != 'registered_at' AND Field != 'last_received_at';"
            val columns = mutableListOf<String>()

            connection.createStatement().use { stmt ->
                stmt.executeQuery(columnQuery).use { rs ->
                    while (rs.next()) {
                        columns.add(rs.getString("Field"))
                    }
                }
            }

            if (columns.isNotEmpty()) {
                val itemQuery = "SELECT * FROM lukevanilla.$tableName WHERE UUID = '$uuid';"
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(itemQuery).use { rs ->
                        if (rs.next()) {
                            for (column in columns) {
                                if (rs.getBoolean(column)) {
                                    items.add(formatItemName(column))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            System.err.println("[AdminAssistant] $tableName 테이블 조회 중 오류: ${e.message}")
        }
        return items
    }

    /**
     * 아이템 이름을 보기 좋게 포맷팅
     */
    private fun formatItemName(itemName: String): String {
        return itemName.replace("_", " ").split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
    }

    /**
     * 지정된 setting_type의 값을 lukevanilla.Settings에서 조회
     */
    private fun fetchSettingValue(type: String): String? {
        val query = "SELECT setting_value FROM lukevanilla.Settings WHERE setting_type='${type}';"
        try {
            dbConnectionProvider().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getString("setting_value")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdminAssistant] $type 조회 중 데이터베이스 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * 지정된 setting_type의 채널 ID를 lukevanilla.Settings에서 조회
     */
    private fun fetchAssistantChannelId(settingType: String): String? {
        val query = "SELECT setting_value FROM lukevanilla.Settings WHERE setting_type='$settingType';"
        try {
            dbConnectionProvider().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getString("setting_value")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdminAssistant] $settingType 채널 ID 조회 중 데이터베이스 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    // 채널별 최근 8개 대화 저장 (질문/답변 쌍)
    private val channelContextMap = mutableMapOf<String, ArrayDeque<Pair<String, String>>>()

    override fun onMessageReceived(event: MessageReceivedEvent) {
        // 봇 메시지 무시
        if (event.author.isBot) return

        // 관리자 채팅 채널이 설정되어 있지 않으면 무시
        if (assistantChannelId == null && assistantSecondaryChannelId == null) return

        // 지정된 채널에서의 메시지만 처리
        if (event.channel.id != assistantChannelId && event.channel.id != assistantSecondaryChannelId) return

        // 관리자 권한 체크는 사용자가 직접 삭제했다고 가정
        // if (!event.member?.hasPermission(Permission.ADMINISTRATOR) ?: false) {
        //     event.channel.sendMessage("이 명령어는 관리자 권한이 필요합니다.").queue()
        //     return
        // }

        val messageContent = event.message.contentRaw

        // 특정 플레이어 경고 조회 요청 패턴 감지
        val warningQueryPattern = Regex("(.+?)\\s*(?:유저|플레이어)?의?\\s*경고\\s*(?:내역|기록|목록|을?\\s*(?:보고\\s*싶|보여|확인))")
        val warningMatch = warningQueryPattern.find(messageContent.trim())

        if (warningMatch != null) {
            val playerName = warningMatch.groupValues[1].trim()
            processPlayerWarningQuery(event, playerName)
            return
        }

        // AI에게 보낼 메시지 컨텍스트 구성
        if (messageContent.isBlank()) return

        try {
            event.channel.sendTyping().queue()

            val builder = ChatCompletionCreateParams.Companion.builder()
            // 모델 타입 분기 처리
            val modelValue = openAIModel
            if (!modelValue.isNullOrBlank()) {
                builder.model(modelValue)
            } else {
                builder.model(ChatModel.Companion.GPT_3_5_TURBO)
            }

            // 채널별 최근 8개 대화(질문/답변) context system prompt에 추가
            val channelId = event.channel.id
            val contextQueue = channelContextMap.getOrPut(channelId) { ArrayDeque() }
            val contextPrompt = if (contextQueue.isNotEmpty()) {
                buildString {
                    append("# 최근 대화 기록 (최대 8개)\n")
                    contextQueue.forEachIndexed { idx, (q, a) ->
                        append("${idx+1}. [관리자] $q\n   [AI] $a\n")
                    }
                    append("\n이전 대화 맥락을 참고해 답변이 필요한 경우 자연스럽게 이어서 답변해.")
                }
            } else ""
            if (contextPrompt.isNotBlank()) {
                builder.addSystemMessage(contextPrompt)
            }

            // 새로운 MCP 스타일 시스템 프롬프트 사용
            val systemPrompt = promptManager.buildSystemPrompt()
            builder.addSystemMessage(systemPrompt)
            // system 메시지 제거, 유저 메시지만 추가
            builder.addUserMessage(messageContent)
            val params = builder.build()

            // 동기 호출
            val chatCompletion: ChatCompletion = openAIClient.chat().completions().create(params)
            val aiResponseContent = chatCompletion.choices().firstOrNull()
                ?.message()
                ?.content()
                ?.orElse(null)

                        // 최근 8개 context에 현재 대화 추가 (질문/답변)
            if (!aiResponseContent.isNullOrEmpty()) {
                if (contextQueue.size >= 8) contextQueue.removeFirst()
                contextQueue.addLast(messageContent to aiResponseContent)
                
                // 새로운 MCP 스타일 도구 시스템을 사용하여 AI 응답 처리
                val context = ToolExecutionContext(event, this)
                val toolResults = toolManager.detectAndExecuteTools(aiResponseContent, context)
                
                if (toolResults.isNotEmpty()) {
                    // 도구가 실행되었을 경우 결과 처리
                    toolResults.forEach { result ->
                        if (!result.success && result.shouldShowToUser) {
                            event.channel.sendMessage("❌ ${result.message}").queue()
                        } else if (result.success && result.shouldShowToUser) {
                            event.channel.sendMessage("✅ ${result.message}").queue()
                        }
                    }
                } else {
                    // 도구 호출이 없었으면 일반 AI 응답 표시
                    displayTypingResponse(aiResponseContent, event)
                }
            } else {
                event.channel.sendMessage("AI로부터 응답을 받았으나 내용이 비어있습니다.").queue()
            }

        } catch (e: Exception) {
            System.err.println("[AdminAssistant] 요청 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
            event.channel.sendMessage("AI 요청 처리 중 예외가 발생했습니다.").queue()
        }
    }
    
    /**
     * 타이핑 효과로 AI 응답 표시
     */
    private fun displayTypingResponse(content: String, event: MessageReceivedEvent) {
        val initialMsg = event.channel.sendMessage("AI 답변 생성 중...").complete()
        val chunkSize = 10
        var current = ""
        for (i in content.indices step chunkSize) {
            val nextChunk = content.substring(i, minOf(i + chunkSize, content.length))
            current += nextChunk
            initialMsg.editMessage(current).queue()
            Thread.sleep(300) // 레이트리밋 보호
        }
        // 마지막 완성 메시지로 한 번 더 갱신
        initialMsg.editMessage(current).queue()
    }

    companion object {
        /**
         * Splits content into chunks of specified size.
         */
        fun splitMessage(content: String, chunkSize: Int = 2000): List<String> {
            val chunks = mutableListOf<String>()
            var startIndex = 0
            while (startIndex < content.length) {
                val endIndex = if (startIndex + chunkSize < content.length) startIndex + chunkSize else content.length
                chunks.add(content.substring(startIndex, endIndex))
                startIndex = endIndex
            }
            return chunks
        }
    }
      /**
     * 메시지에서 플레이어 식별자(닉네임 또는 UUID)를 추출
     */
    private fun extractPlayerIdentifier(message: String): String? {
        // 디스코드 사용자 멘션 패턴 먼저 확인 (<@사용자ID>)
        val mentionPattern = "<@(\\d+)>".toRegex()
        mentionPattern.find(message)?.let { match ->
            val discordId = match.groupValues[1]
            // 디스코드 ID로 플레이어 정보 조회
            val playerInfo = findPlayerInfoByDiscordId(discordId)
            if (playerInfo != null) {
                return playerInfo.nickname
            }
        }

        // UUID 패턴 (대시 포함 또는 미포함)
        val uuidPattern = "\\b([0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}|[0-9a-f]{32})\\b".toRegex(RegexOption.IGNORE_CASE)
        uuidPattern.find(message)?.let {
            return it.value
        }

        // 닉네임 패턴 (3-16자의 알파벳, 숫자, 언더스코어)
        val nicknamePatterns = listOf(
            "플레이어\\s+([\\w가-힣]{3,16})".toRegex(),
            "유저\\s+([\\w가-힣]{3,16})".toRegex(),
            "([\\w가-힣]{3,16})\\s+(?:플레이어|유저)".toRegex(),
            "([\\w가-힣]{3,16})(?:의|\\s+의)\\s+정보".toRegex(),
            "([\\w가-힣]{3,16})\\s+정보".toRegex()
        )

        for (pattern in nicknamePatterns) {
            pattern.find(message)?.groups?.get(1)?.value?.let {
                return it
            }
        }

        // 특정 단어 앞의 닉네임 패턴 찾기
        val wordBeforeKeywords = "([\\w가-힣]{3,16})(?:\\s+(?:정보|조회|보여줘|알려줘))".toRegex()
        wordBeforeKeywords.find(message)?.groups?.get(1)?.value?.let {
            return it
        }

        return null
    }

    /**
     * 플레이어 정보 요청 처리
     */
    private fun processPlayerInfoRequest(event: MessageReceivedEvent, identifier: String) {
        event.channel.sendTyping().queue()
        val playerInfo = findPlayerInfo(identifier)

        if (playerInfo != null) {
            // 플레이어 정보를 디스코드 임베드로 표시
            val embed = createPlayerInfoEmbed(playerInfo)

            // 아이템 조회 버튼 추가
            val viewItemsButton = Button.primary("view_items:${playerInfo.uuid}", "아이템 보기")

            event.channel.sendMessageEmbeds(embed)
                .addActionRow(viewItemsButton)
                .queue()
        } else {
            event.channel.sendMessage("플레이어 `$identifier`의 정보를 찾을 수 없습니다.").queue()
        }
    }

    /**
     * 플레이어 정보 임베드 생성
     */
    fun createPlayerInfoEmbed(playerInfo: PlayerInfo): MessageEmbed {
        val embed = EmbedBuilder().apply {
            setTitle("${playerInfo.nickname} 플레이어 정보")
            setColor(Color.BLUE)
            setDescription("마인크래프트 플레이어 정보입니다.")

            // 기본 정보
            addField("닉네임", playerInfo.nickname, true)
            addField("칭호", playerInfo.nameTag ?: "없음", true)
            addField("UUID", playerInfo.uuid, false)

            // 계정 상태
            addField("인증 여부", if (playerInfo.isAuth) "✅ 인증됨" else "❌ 미인증", true)
            addField("밴 여부", if (playerInfo.isBanned) "⛔ 밴 상태" else "✅ 정상", true)
            addField("신규 여부", if (playerInfo.isFirst) "🆕 신규" else "🔄 기존", true)

            // 접속 정보
            if (!playerInfo.ip.isNullOrBlank()) {
                addField("마지막 접속 IP", playerInfo.ip, false)
            }

            // 디스코드 정보
            if (!playerInfo.discordId.isNullOrBlank()) {
                val discordMention = "<@${playerInfo.discordId}>"
                addField("디스코드", discordMention, false)
            }

            // 푸터 - 현재 시간 표시
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            setFooter("조회 시간: ${sdf.format(Date())}")
        }

        return embed.build()
    }

    /**
     * 아이템 정보 임베드 생성
     */
    fun createItemInfoEmbed(playerInfo: PlayerInfo, items: Map<String, List<String>>): MessageEmbed {
        val embed = EmbedBuilder().apply {
            setTitle("${playerInfo.nickname}의 등록된 아이템")
            setColor(Color.GREEN)
            setDescription("플레이어가 등록한 아이템 목록입니다.")

            if (items.isEmpty()) {
                addField("등록된 아이템 없음", "이 플레이어는 등록된 아이템이 없습니다.", false)
            } else {
                items.forEach { (category, itemList) ->
                    if (itemList.isNotEmpty()) {
                        addField(category, itemList.joinToString("\n"), false)
                    }
                }
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            setFooter("조회 시간: ${sdf.format(Date())}")
        }

        return embed.build()
    }
    
    /**
     * 아이템 보기 버튼 생성
     */
    fun createItemsButton(uuid: String): Button {
        return Button.primary("view_items:${uuid}", "아이템 보기")
    }

    /**
     * 특정 플레이어의 경고 내역 조회 처리
     */
    fun processPlayerWarningQuery(event: MessageReceivedEvent, playerName: String) {
        try {
            // 플레이어 정보 먼저 조회
            val playerInfo = findPlayerInfo(playerName)
            if (playerInfo == null) {
                event.channel.sendMessage("플레이어 '${playerName}'을(를) 찾을 수 없습니다.").queue()
                return
            }            // WarningService를 통해 특정 플레이어의 경고 내역 조회
            val playerUuid = UUID.fromString(playerInfo.uuid)
            val playerWarnings = warningService.getPlayerWarnings(playerUuid)
            val playerWarning = warningService.getPlayerWarning(playerUuid, playerInfo.nickname)

            val embed = EmbedBuilder().apply {
                setTitle("${playerInfo.nickname}의 경고 내역")
                setColor(Color.YELLOW)

                if (playerWarnings.isEmpty()) {
                    addField("경고 내역", "해당 플레이어는 경고 내역이 없습니다.", false)
                } else {
                    // 현재 활성 경고 횟수 표시
                    addField("현재 경고 횟수", "${playerWarning.activeWarningsCount}회", true)

                    // 전체 경고 기록 수
                    addField("전체 기록 수", "${playerWarnings.size}회", true)

                    // 최근 경고들 (최대 5개)
                    val recentWarnings = playerWarnings.take(5)
                    val warningList = recentWarnings.mapIndexed { index, warning ->
                        val dateStr = warning.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val reason = if (warning.isActive) {
                            "**사유**: ${warning.reason}"
                        } else {
                            "**사유**: ~~${warning.reason}~~ (차감됨)"
                        }
                        "${index + 1}. **[ID: ${warning.warningId}]** $reason\n   **일시**: $dateStr\n   **관리자**: ${warning.adminName ?: "시스템"}"
                    }.joinToString("\n\n")

                    addField("최근 경고 내역", warningList, false)

                    if (playerWarnings.size > 5) {
                        addField("", "※ ${playerWarnings.size - 5}개의 추가 경고 내역이 있습니다.", false)
                    }
                }

                // 플레이어 기본 정보
                addField("플레이어 정보",
                    "**UUID**: ${playerInfo.uuid}\n" +
                    "**밴 상태**: ${if (playerInfo.isBanned) "차단됨" else "정상"}\n" +
                    "**인증 상태**: ${if (playerInfo.isAuth) "인증됨" else "미인증"}",
                    false)

                setFooter("조회 시간: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}")
            }.build()

            event.channel.sendMessageEmbeds(embed).queue()

        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage("경고 내역 조회 중 오류가 발생했습니다: ${e.message}").queue()
        }
    }
      /**
     * 플레이어 경고 요청 처리
     */
    fun processPlayerWarningRequest(event: MessageReceivedEvent, playerName: String, reason: String, adminDiscordId: String) {
        try {
            // 사유가 비어있는지 확인
            if (reason.isBlank()) {
                event.channel.sendMessage("경고 사유를 반드시 입력해야 합니다. '경고 <유저닉네임> <사유>' 형식으로 입력해주세요.").queue()
                return
            }

            // 플레이어 이름이 디스코드 멘션인지 확인
            val actualPlayerName = if (playerName.startsWith("<@") && playerName.endsWith(">")) {
                // 디스코드 멘션에서 ID 추출
                val discordId = playerName.substring(2, playerName.length - 1)
                val playerInfo = findPlayerInfoByDiscordId(discordId)
                if (playerInfo != null) {
                    playerInfo.nickname
                } else {
                    event.channel.sendMessage("해당 디스코드 사용자의 마인크래프트 계정을 찾을 수 없습니다.").queue()
                    return
                }
            } else {
                playerName
            }

            // 서버에 접속한 플레이어 정보 찾기
            val onlinePlayer = Bukkit.getPlayer(actualPlayerName)

            if (onlinePlayer != null) {
                // 온라인 플레이어에게 경고 부여
                processOnlinePlayerWarning(event, onlinePlayer, reason, adminDiscordId)
            } else {
                // 오프라인 플레이어의 UUID 조회 후 처리
                dbConnectionProvider().use { connection ->
                    val selectUuidQuery = "SELECT UUID FROM Player_Data WHERE NickName = ?"
                    var playerUuid: String? = null

                    connection.prepareStatement(selectUuidQuery).use { statement ->
                        statement.setString(1, actualPlayerName)
                        statement.executeQuery().use { resultSet ->
                            if (resultSet.next()) {
                                playerUuid = resultSet.getString("UUID")
                            }
                        }
                    }
                      if (playerUuid == null) {
                        event.channel.sendMessage("플레이어 '$actualPlayerName'의 정보를 찾을 수 없습니다.").queue()
                        return
                    }

                    processOfflinePlayerWarning(event, actualPlayerName, UUID.fromString(playerUuid), reason, adminDiscordId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage("경고 처리 중 오류가 발생했습니다: ${e.message}").queue()
        }
    }

    /**
     * 온라인 플레이어 경고 처리
     */
    private fun processOnlinePlayerWarning(event: MessageReceivedEvent, player: Player, reason: String, adminDiscordId: String) {
        // 관리자 정보 생성
        val adminName = event.author.name
        // Discord ID로 실제 마인크래프트 UUID 조회
        val adminUuid = getMinecraftUuidByDiscordId(adminDiscordId)
            ?: UUID.nameUUIDFromBytes("discord_$adminDiscordId".toByteArray()) // 백업용

        // WarningService를 통해 경고 부여
        val result = warningService.addWarning(
            targetPlayer = player,
            adminUuid = adminUuid,
            adminName = adminName,
            reason = reason
        )

        if (result.first) {
            val warningCount = result.second
            val autoBanned = result.third

            // 경고 성공 메시지
            val resultMessage = if (autoBanned) {
                "'${player.name}'님에게 경고가 부여되었습니다. (현재 ${warningCount}회) - 경고 횟수 초과로 자동 차단되었습니다."
            } else {
                "'${player.name}'님에게 경고가 부여되었습니다. (현재 ${warningCount}회)"
            }

            event.channel.sendMessage(resultMessage).queue()
        } else {
            event.channel.sendMessage("경고 부여 중 오류가 발생했습니다.").queue()
        }
    }

    /**
     * 오프라인 플레이어 경고 처리
     */
    private fun processOfflinePlayerWarning(event: MessageReceivedEvent, playerName: String, playerUuid: UUID, reason: String, adminDiscordId: String) {
        // 경고 정보를 DB에 직접 추가 (오프라인 플레이어에게는 WarningService.addWarning 사용 불가)
        try {
            dbConnectionProvider().use { connection ->
                // 관리자 정보 생성
                val adminName = event.author.name

                // 플레이어 ID 가져오기
                val selectPlayerIdQuery = "SELECT player_id, active_warnings_count FROM warnings_players WHERE uuid = ?"
                var playerId: Int? = null
                var currentWarnings = 0

                connection.prepareStatement(selectPlayerIdQuery).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            playerId = resultSet.getInt("player_id")
                            currentWarnings = resultSet.getInt("active_warnings_count")
                        }
                    }
                }

                if (playerId == null) {
                    // 플레이어 정보가 없으면 생성
                    val insertPlayerQuery = "INSERT INTO warnings_players (uuid, username) VALUES (?, ?)"
                    connection.prepareStatement(insertPlayerQuery, Statement.RETURN_GENERATED_KEYS).use { statement ->
                        statement.setString(1, playerUuid.toString())
                        statement.setString(2, playerName)
                        statement.executeUpdate()

                        statement.generatedKeys.use { keys ->
                            if (keys.next()) {
                                playerId = keys.getInt(1)
                            }
                        }
                    }
                }

                if (playerId == null) {
                    event.channel.sendMessage("플레이어 정보 처리 중 오류가 발생했습니다.").queue()
                    return
                }

                // 경고 추가
                val insertWarningQuery = """
                    INSERT INTO warnings_records 
                    (player_id, admin_uuid, admin_name, reason)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()

                // Discord ID로 실제 마인크래프트 UUID 조회
                val adminUuid = getMinecraftUuidByDiscordId(adminDiscordId)
                    ?: UUID.nameUUIDFromBytes("discord_$adminDiscordId".toByteArray()) // 백업용

                connection.prepareStatement(insertWarningQuery).use { statement ->
                    statement.setInt(1, playerId)
                    statement.setString(2, adminUuid.toString())
                    statement.setString(3, adminName)
                    statement.setString(4, reason)
                    statement.executeUpdate()
                }

                // 경고 횟수 업데이트
                currentWarnings++
                val updateCountQuery = """
                    UPDATE warnings_players 
                    SET active_warnings_count = ?,
                        last_warning_date = CURRENT_TIMESTAMP
                    WHERE player_id = ?
                """.trimIndent()

                connection.prepareStatement(updateCountQuery).use { statement ->
                    statement.setInt(1, currentWarnings)
                    statement.setInt(2, playerId)
                    statement.executeUpdate()
                }

                // 경고 처리 결과 메시지
                val resultMessage = "'$playerName'님에게 경고가 부여되었습니다. (현재 ${currentWarnings}회) - 현재 오프라인 상태입니다."
                event.channel.sendMessage(resultMessage).queue()

                // 경고 횟수가 5회 이상이면 자동 차단 처리
                if (currentWarnings >= WarningService.Companion.AUTO_BAN_THRESHOLD) {
                    processBan(event, playerName, playerUuid.toString(), "경고 누적 ${currentWarnings}회 (자동 차단)")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage("경고 처리 중 오류가 발생했습니다: ${e.message}").queue()
        }
    }

    /**
     * 차단 처리 - BanManager를 사용하여 처리
     */
    private fun processBan(event: MessageReceivedEvent, playerName: String, playerUuid: String, reason: String) {
        try {
            // BanManager를 사용하여 차단 처리
            val uuid = UUID.fromString(playerUuid)
            val banResult = warningService.getBanManager().banWithFullDetails(
                playerName = playerName,
                playerUuid = uuid,
                reason = reason,
                source = "디스코드 경고 시스템"
            )

            // 차단 결과 메시지 전송
            val banMessage = StringBuilder()
                .append("⛔ '$playerName'님이 $reason 으로 차단되었습니다.\n")

            if (banResult.first) {
                // 성공적으로 차단됨
                if (banResult.second > 0) {
                    banMessage.append("차단된 IP 주소: ${banResult.second}개\n")
                }

                if (banResult.third) {
                    banMessage.append("디스코드 사용자도 차단되었습니다.")
                }
            } else {
                // 차단 실패
                banMessage.append("차단 처리 중 오류가 발생했습니다.")
            }

            event.channel.sendMessage(banMessage.toString()).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage("차단 처리 중 오류가 발생했습니다: ${e.message}").queue()
        }
    }

    /**
     * 최근 경고 내역 조회 및 분석
     */
    fun processRecentWarningsRequest(event: MessageReceivedEvent) {
        try {
            // WarningService를 통해 경고 내역 분석 수행
            val analysisResult = warningService.analyzeWarnings()

            // 분석 결과를 기반으로 풍부한 정보를 포함한 메시지 구성
            val embed = EmbedBuilder().apply {
                setTitle("경고 시스템 분석 리포트")
                setColor(Color.ORANGE)

                // 최근 경고 내역
                if (analysisResult.recentWarnings.isEmpty()) {
                    addField("최근 경고 내역", "최근 경고를 받은 플레이어가 없습니다.", false)
                } else {
                    val formattedWarnings = analysisResult.recentWarnings.map { warning ->
                        val dateStr = warning.lastWarningDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        "${warning.username}: ${warning.warningCount}회 (마지막 경고: $dateStr)"
                    }.joinToString("\n")

                    addField("최근 경고 내역", formattedWarnings, false)
                }

                // 반복 위반자 정보
                if (analysisResult.repeatedOffenders.isNotEmpty()) {
                    val formattedOffenders = analysisResult.repeatedOffenders.map { offender ->
                        val riskSymbol = when(offender.riskLevel) {
                            RiskLevel.HIGH -> "🔴" // 빨강 원
                            RiskLevel.MEDIUM -> "🟡" // 노란 원
                            RiskLevel.LOW -> "🟢" // 초록 원
                        }
                        "$riskSymbol ${offender.username}: 총 ${offender.totalWarnings}회 경고"
                    }.joinToString("\n")

                    addField("주의 필요 사용자", formattedOffenders, false)
                }

                // 위험도 평가
                addField("경고 시스템 현황", analysisResult.riskAssessment, false)

                // 추천 조치
                if (analysisResult.recommendedActions.isNotEmpty()) {
                    addField("추천 조치", analysisResult.recommendedActions.joinToString("\n"), false)
                }

                setFooter("분석 시간: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}")
            }.build()

            event.channel.sendMessageEmbeds(embed).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.channel.sendMessage("경고 내역 조회 중 오류가 발생했습니다: ${e.message}").queue()
        }
    }

    /**
     * 버튼 상호작용 이벤트 처리
     */
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val componentId = event.componentId

        // 아이템 보기 버튼 처리
        if (componentId.startsWith("view_items:")) {
            val uuid = componentId.substring("view_items:".length)
            handleViewItemsButton(event, uuid)
        }
    }

    /**
     * 아이템 보기 버튼 클릭 처리
     */
    private fun handleViewItemsButton(event: ButtonInteractionEvent, uuid: String) {
        // 먼저 응답 지연을 알림 (처리 시간이 오래 걸릴 수 있음)
        event.deferReply(true).queue()

        // 플레이어 정보 조회
        val playerInfo = findPlayerInfo(uuid)
        if (playerInfo != null) {
            // 아이템 정보 조회
            val items = getPlayerItems(uuid)
            val embed = createItemInfoEmbed(playerInfo, items)

            // 조회 결과 표시
            event.hook.sendMessageEmbeds(embed).queue()
        } else {
            event.hook.sendMessage("플레이어 정보를 찾을 수 없습니다. (UUID: $uuid)").queue()
        }
    }

    /**
     * Discord ID로 Player_Data 테이블에서 마인크래프트 UUID 조회
     */
    private fun getMinecraftUuidByDiscordId(discordId: String): UUID? {
        return try {
            dbConnectionProvider().use { connection ->
                val query = "SELECT UUID FROM Player_Data WHERE DiscordID = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, discordId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            val uuidString = resultSet.getString("UUID")
                            UUID.fromString(uuidString)
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[AdminAssistant] Discord ID로 UUID 조회 중 오류: ${e.message}")
            null
        }
    }

    /**
     * 경고 차감(사면) 요청 처리
     */
    fun processWarningPardonRequest(event: MessageReceivedEvent, playerName: String, warningId: Int, pardonReason: String, adminDiscordId: String) {
        try {
            // 플레이어 정보 조회
            val playerInfo = findPlayerInfo(playerName)
            if (playerInfo == null) {
                event.channel.sendMessage("'$playerName' 플레이어를 찾을 수 없습니다.").queue()
                return
            }

            // 관리자 UUID 조회
            val adminUuid = getMinecraftUuidByDiscordId(adminDiscordId)
                ?: UUID.nameUUIDFromBytes("discord_$adminDiscordId".toByteArray())
            val adminName = event.author.name

            // 경고 차감 처리
            val success = warningService.pardonWarningById(
                targetPlayerUuid = UUID.fromString(playerInfo.uuid),
                warningId = warningId,
                adminUuid = adminUuid,
                adminName = adminName,
                reason = pardonReason
            )

            if (success) {
                // 업데이트된 플레이어 정보 조회
                val updatedPlayerWarning = warningService.getPlayerWarnings(UUID.fromString(playerInfo.uuid))
                val currentWarnings = updatedPlayerWarning.count { it.isActive }

                event.channel.sendMessage(
                    "'$playerName'님의 경고 ID $warningId 가 차감되었습니다. " +
                    "(현재 활성 경고: ${currentWarnings}회)\n" +
                    "차감 사유: $pardonReason"
                ).queue()
            } else {
                event.channel.sendMessage(
                    "경고 차감에 실패했습니다. 경고 ID $warningId 가 존재하지 않거나 이미 차감된 경고일 수 있습니다."
                ).queue()
            }

        } catch (e: Exception) {
            System.err.println("[AdminAssistant] 경고 차감 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
            event.channel.sendMessage("경고 차감 처리 중 오류가 발생했습니다: ${e.message}").queue()
        }
    }
}
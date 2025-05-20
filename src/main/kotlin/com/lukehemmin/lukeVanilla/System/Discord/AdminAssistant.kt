package com.lukehemmin.lukeVanilla.System.Discord

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletion.Choice

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.sql.Connection
import java.util.Collections

class AdminAssistant(
    private val dbConnectionProvider: () -> Connection,
    private val openAIApiKey: String? = null // 생성자로 API 키를 전달받음
) : ListenerAdapter() {
    private var openAIBaseUrl: String? = null
    private var openAIModel: String? = null

    private var assistantChannelId: String? = null
    private val openAIClient: OpenAIClient by lazy {
        // API 키가 이미 주입되었으므로 다른 세팅만 로드
        loadOpenAISettings()
        val apiKeyLocal = openAIApiKey
        val baseUrlLocal = openAIBaseUrl
        OpenAIOkHttpClient.builder()
            .apply {
                if (!apiKeyLocal.isNullOrBlank()) apiKey(apiKeyLocal)
                if (!baseUrlLocal.isNullOrBlank()) baseUrl(baseUrlLocal)
                fromEnv()
            }
            .build()
    }

    init {
        assistantChannelId = fetchAssistantChannelId()
        if (assistantChannelId == null) {
            System.err.println("[AdminAssistant] 오류: 데이터베이스에서 Assistant_Channel ID를 찾을 수 없습니다. AdminAssistant가 작동하지 않습니다.")
        } else {
            println("[AdminAssistant] 초기화 완료. 채널 ID: $assistantChannelId 에서 수신 대기 중...")
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

    private fun fetchAssistantChannelId(): String? {
        val query = "SELECT setting_value FROM lukevanilla.Settings WHERE setting_type='Assistant_Channel';"
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
            System.err.println("[AdminAssistant] 채널 ID 조회 중 데이터베이스 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    // 채널별 최근 8개 대화 저장 (질문/답변 쌍)
    private val channelContextMap = mutableMapOf<String, ArrayDeque<Pair<String, String>>>()

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        if (assistantChannelId == null || event.channel.id != assistantChannelId) return

        val member = event.member
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            return
        }

        val messageContent = event.message.contentRaw
        if (messageContent.isBlank()) return

        try {
            event.channel.sendTyping().queue()

            val builder = ChatCompletionCreateParams.builder()
            // 모델 타입 분기 처리
            val modelValue = openAIModel
            if (!modelValue.isNullOrBlank()) {
                builder.model(modelValue)
            } else {
                builder.model(ChatModel.GPT_3_5_TURBO)
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

            // 항상 시스템 프롬프트에 서버 상태 및 관리 명령어 정보 포함 (AI가 필요할 때만 답변에 사용)
            val serverStatus = ServerStatusProvider.getServerStatusString()
            val commandTable = """
# 역할
- 넌 Minecraft 서버의 관리자 어시스턴트야.
- 서버 상태, TPS, 렉, mspt, ping 등과 관련된 질문이 오면 아래 실시간 서버 상태 정보를 참고해서 답변해.
- 관리자가 서버 명령어(특히 로그조사/복구 등)에 대해 궁금해하면 아래 표와 설명을 바탕으로 친절하게 안내해.
- 마인크래프트 서버와 무관한 명령어나, 허용되지 않은 명령어는 안내하지 마.

# 실시간 서버 상태
- $serverStatus

> 참고: TPS/렉이 낮고, 접속 플레이어 수가 많을 경우 '플레이어가 많아서 렉이 발생할 수 있음'을 안내해줘.

# 관리 명령어 안내 (CoreProtect 및 기타 관리자 명령어)
아래 명령어만 안내해야 하며, 표와 설명을 참고해. (다른 명령어는 알려주지 마)

| 명령어 | 설명 |
| --- | --- |
| /co help | 명령어 목록을 보여줌 |
| /co inspect | 인스펙터 토글 |
| /co lookup | 블록 데이터 조회 |
| /co rollback | 블록 데이터 롤백 |
| /co restore | 블록 데이터 복구 |
| /co purge | 오래된 데이터 삭제 |
| /co reload | 설정 리로드 |
| /co status | 플러그인 상태 조회 |
| /co consumer | Consumer 처리 토글 |
| /co near | 반경 5칸 내 조회 |
| /co undo | 롤백/복구 취소 |
| /inventorysee <닉네임> | 해당 유저의 인벤토리 조사 (GUI로 아이템 확인/추가/회수 가능) |
| /enderchestsee <닉네임> | 해당 유저의 엔더상자 조사 (GUI로 아이템 확인/추가/회수 가능) |
| /inventoryrestore view <닉네임> | 백업된 플레이어 인벤토리 조회 및 아이템 복구 (복구하고자 하는 아이템만 꺼내서 사용, 전체 대체 금지) |
| /nametag [닉네임] [칭호] | 플레이어에게 칭호 부여 (채팅/머리위/Tab에 칭호 표시, 색상코드 사용 가능) |


## 주요 사용 예시
- `/co lookup u:플레이어명 t:1h a:-block` : 최근 1시간 동안 특정 플레이어가 부순 블록 조회
- `/co rollback u:플레이어명 t:1h` : 최근 1시간 동안 특정 플레이어가 한 행동 롤백
- `/co restore u:플레이어명 t:1h` : 최근 1시간 동안 특정 플레이어가 한 행동 복구
- `/co purge t:30d` : 30일 이전 데이터 삭제
- `/co help` : 전체 명령어 도움말

## 주요 사용 예시 (심화)
- /co i - 입력 후 해당 블럭 왼클릭 시 파괴 및 설치 로그, 우클릭 시 상자의 경우 아이템을 넣거나 뺀 기록 등.
- /co l user:유저이름 time:시간 radius:거리 ( 블럭 수, #global 등. ) action:행동 필터 kill, item 등. include:특정 아이템 필터 exclude:특정아이템 제외
- /co l user:lukehemmin time:3h include:white_shulker_box ( 특정 아이템을 필터하려는 경우 아이템 코드를 입력 ) (예시) 루크해민이 약 3시간전에 화이트셜커상자를 잃어버렸다고 하는 경우. 로그 검색.)
- /co l time:시간 radius:100 (선택) 추가로 include:tnt 로 특정 블럭이나 엔티티를 지정하여 검색 가능. (유저가 블럭을 부순게 아닌 엔티티가 블럭을 제거했거나 주변에서 발생한 로그를 확인하려는 경우.)

## 파라미터 설명
- `u:<user>` : 유저 지정
- `t:<time>` : 시간 지정 (예: 1h, 2d)
- `r:<radius>` : 반경 지정
- `a:<action>` : 액션 종류 (예: -block, +block 등)
- `i:<include>` : 포함할 블록/엔티티
- `e:<exclude>` : 제외할 블록/엔티티
- `#<hashtag>` : 추가 기능 (예: #preview)

더 자세한 명령어 설명이나 예시가 필요하면, 위 표와 설명을 바탕으로 안내해줘.
""".trimIndent()
            builder.addSystemMessage(commandTable)
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

                val initialMsg = event.channel.sendMessage("AI 답변 생성 중...").complete()
                val chunkSize = 2
                val content = aiResponseContent
                var current = ""
                for (i in content.indices step chunkSize) {
                    val nextChunk = content.substring(i, minOf(i + chunkSize, content.length))
                    current += nextChunk
                    initialMsg.editMessage(current).queue()
                    Thread.sleep(300) // 레이트리밋 보호
                }
                // 마지막 완성 메시지로 한 번 더 갱신
                initialMsg.editMessage(current).queue()
            } else {
                event.channel.sendMessage("AI로부터 응답을 받았으나 내용이 비어있습니다.").queue()
            }

        } catch (e: Exception) {
            System.err.println("[AdminAssistant] 요청 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
            event.channel.sendMessage("AI 요청 처리 중 예외가 발생했습니다.").queue()
        }
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
}
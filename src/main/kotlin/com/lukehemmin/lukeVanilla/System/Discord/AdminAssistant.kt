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
            // system 메시지 제거, 유저 메시지만 추가
            builder.addUserMessage(messageContent)
            val params = builder.build()

            // 동기 호출
            val chatCompletion: ChatCompletion = openAIClient.chat().completions().create(params)
            val aiResponseContent = chatCompletion.choices().firstOrNull()
                ?.message()
                ?.content()
                ?.orElse(null)

            if (!aiResponseContent.isNullOrEmpty()) {
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
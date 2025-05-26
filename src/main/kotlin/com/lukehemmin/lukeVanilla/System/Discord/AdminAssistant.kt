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
    private var assistantSecondaryChannelId: String? = null
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
        if (event.author.isBot) return
        
        // 두 채널 중 하나라도 일치하면 처리
        val channelMatches = (assistantChannelId != null && event.channel.id == assistantChannelId) || 
                            (assistantSecondaryChannelId != null && event.channel.id == assistantSecondaryChannelId)
        if (!channelMatches) return

        // 관리자 권한 체크 제거: 해당 채널에서 메시지를 보낼 수 있는 유저라면 누구나 가능
        val member = event.member
        if (member == null) {
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
# 언어 지시
- 반드시 **모든 답변을 한국어**(높임말, 자연스러운 표현)로 한다.

# 역할
- 넌 Minecraft 서버의 관리자 어시스턴트야.
- 서버 상태, TPS, 렉, mspt, ping 등과 관련된 질문이 오면 아래 실시간 서버 상태 정보를 참고해서 답변해.
- 관리자가 서버 명령어(특히 로그조사/복구 등)에 대해 궁금해하면 아래 표와 설명을 바탕으로 친절하게 안내해.
- 마인크래프트 서버와 무관한 명령어나, 허용되지 않은 명령어는 안내하지 마.

# 실시간 서버 상태
- $serverStatus

> 참고: TPS가 낮고(예: 15 미만), mspt가 높으며(예: 50 이상), 접속 플레이어 수가 많을 경우 '서버에 접속 중인 플레이어가 많거나 특정 활동으로 인해 일시적인 부하가 발생했을 수 있습니다. TPS와 mspt 수치를 지속적으로 확인해주세요.' 와 같이 안내해줘. 핑(ping)은 네트워크 상태에 따라 달라질 수 있음을 언급해줘.

# 관리 명령어 안내 (CoreProtect 및 기타 관리자 명령어)
아래 명령어만 안내해야 하며, 표와 설명을 참고해. (다른 명령어는 알려주지 마)

| 명령어 | 설명 | 플러그인 |
| --- | --- | --- |
| /co help | CoreProtect 명령어 목록을 보여줌 | CoreProtect |
| /co inspect | CoreProtect 조사 도구(인스펙터)를 켜고 끔 | CoreProtect |
| /co lookup | CoreProtect를 사용하여 블록 및 활동 데이터 조회 | CoreProtect |
| /co rollback | CoreProtect를 사용하여 블록 및 활동 데이터 롤백 (되돌리기) | CoreProtect |
| /co restore | CoreProtect를 사용하여 롤백된 블록 및 활동 데이터 복구 | CoreProtect |
| /co purge | CoreProtect의 오래된 로그 데이터 삭제 | CoreProtect |
| /co reload | CoreProtect 플러그인 설정 리로드 | CoreProtect |
| /co status | CoreProtect 플러그인의 현재 상태 조회 (버전, DB연결 등) | CoreProtect |
| /co consumer | CoreProtect의 로그 기록 Consumer 처리 기능을 켜고 끔 | CoreProtect |
| /co near | 현재 위치 반경 5칸 내의 최근 CoreProtect 기록 조회 | CoreProtect |
| /co undo | 마지막으로 실행한 CoreProtect 롤백/복구 명령 취소 | CoreProtect |
| /inventorysee <닉네임> | 해당 유저의 인벤토리 조사 (GUI로 아이템 확인/추가/회수 가능) |
| /enderchestsee <닉네임> | 해당 유저의 엔더상자 조사 (GUI로 아이템 확인/추가/회수 가능) |
| /inventoryrestore view <닉네임> | 백업된 플레이어 인벤토리 조회 및 아이템 복구 (복구하고자 하는 아이템만 꺼내서 사용, 전체 대체 금지) |
| /nametag [닉네임] [칭호] | 플레이어에게 칭호 부여 (채팅/머리위/Tab에 칭호 표시, 색상코드 사용 가능) |

## CoreProtect 명령어 상세 가이드

위 표에 언급된 CoreProtect 명령어에 대한 자세한 사용법, 파라미터, 심화 예제는 다음과 같습니다.

### 1. CoreProtect 플러그인 개요

CoreProtect는 서버 내 블록 변경, 아이템 이동, 플레이어 활동 등을 기록하고, 문제 발생 시 조회하거나 이전 상태로 되돌리는 기능을 제공합니다.

### 2. 주요 CoreProtect 명령어 사용법 (상세)

#### 2.1. 조사 도구 (Inspector) - `/co inspect` 또는 `/co i`
-   **기능:** 조사 모드를 활성화/비활성화합니다.
-   **사용 방법:**
    1.  `/co inspect` 입력하여 조사 모드 활성화.
    2.  **블록 좌클릭:** 해당 블록 위치에 마지막으로 발생한 액션(설치/파괴자, 시간) 표시.
    3.  **블록 우클릭:** 일반 블록은 해당 위치의 모든 변경 기록, 상자 등 컨테이너는 아이템 넣고 꺼낸 기록 표시.
    4.  다시 `/co inspect` 입력하여 조사 모드 비활성화.
-   **활용:** "문이 사라졌어요!" -> `/co inspect` 켜고 문이 있던 자리 우클릭 안내.

#### 2.2. 기록 조회 (Lookup) - `/co lookup <파라미터>` 또는 `/co l <파라미터>`
-   **기능:** 지정된 조건에 맞는 과거 활동 기록을 조회합니다. (서버 데이터 변경 없음)
-   **예시:**
    * `/co lookup user:Steve time:2h action:block` (Steve가 2시간 동안 변경한 블록 조회)
    * `/co lookup time:30m radius:10 action:container` (현재 위치 반경 10블록 내 30분간 상자 기록 조회)
    * `/co lookup user:Herobrine time:1d blocks:tnt action:+block` (Herobrine이 하루 동안 tnt를 설치한 기록 조회)

#### 2.3. 되돌리기 (Rollback) - `/co rollback <파라미터>` 또는 `/co rb <파라미터>`
-   **기능:** 지정된 조건에 맞는 활동 내역을 이전 상태로 되돌립니다.
-   **경고:** **데이터 변경 명령어이므로 신중하게 사용하고, `#preview` 옵션을 먼저 사용하도록 안내.**
-   **예시:**
    * `/co rollback user:BadPlayer time:1h radius:20 action:-block #preview` (BadPlayer가 1시간 동안 반경 20에서 파괴한 블록 롤백 미리보기)
    * (WorldEdit 영역 선택 후) `/co rollback radius:#we time:30m exclude:water,lava` (선택 영역 30분 롤백, 물/용암 제외)

#### 2.4. 복원 (Restore) - `/co restore <파라미터>` 또는 `/co rs <파라미터>`
-   **기능:** `rollback` 명령으로 되돌렸던 내용을 다시 원래 상태(롤백 전 상태)로 복원합니다. (롤백 취소)
-   **사용법:** 일반적으로 `rollback` 시 사용했던 것과 동일한 파라미터를 사용합니다.
-   **예시:** `/co restore user:Mistake time:1h` (이전에 `user:Mistake time:1h`로 롤백한 것을 복원)

#### 2.5. 데이터 관리 (Purge) - `/co purge time:<시간>`
-   **기능:** 지정된 시간 이전의 오래된 로그 데이터를 영구적으로 삭제합니다.
-   **경고:** **매우 위험! 삭제된 데이터는 복구 불가. 실행 전 반드시 서버 전체 백업 강력 권고.**
-   **예시:** `/co purge time:90d` (90일 이전 데이터 삭제 - 위험성 반드시 고지)

#### 2.6. 기타 CoreProtect 명령어
-   `/co help`: CoreProtect 명령어 전체 목록과 간단한 설명을 보여줍니다.
-   `/co reload`: CoreProtect 플러그인의 설정 파일(config.yml)을 다시 불러옵니다.
-   `/co status`: 플러그인 버전, 데이터베이스 연결 상태, 대기 중인 로그(pending) 수 등을 보여줍니다. 서버가 정상적으로 로그를 기록하고 있는지 확인할 때 유용합니다.
-   `/co consumer`: CoreProtect가 백그라운드에서 로그를 데이터베이스에 기록하는 Consumer 프로세스의 작동을 일시적으로 켜거나 끌 수 있습니다. (일반적인 상황에서는 사용 권장 안 함, 문제 해결 시 사용)
-   `/co near [radius:<반경>]`: 현재 위치 반경 (기본 5블록, 지정 가능) 내의 최근 블록 변경 기록을 간략하게 보여줍니다.
-   `/co undo`: 현재 세션에서 마지막으로 실행한 `/co rollback` 또는 `/co restore` 명령의 결과를 취소합니다. (다른 유저의 행동이나 오래전 명령은 취소 불가)

### 3. CoreProtect 주요 파라미터 상세 설명

CoreProtect 명령어의 효과를 정밀하게 제어하기 위해 다음 파라미터들을 적절히 조합하여 사용하도록 안내하십시오.

-   `user:<유저명>` 또는 `users:<유저명1,유저명2,...>`: 특정 유저(들) 지정. (예: `user:Steve`)
-   `time:<시간>`: 기간 지정 (s, m, h, d, w 단위 사용). (예: `time:2h30m`, `time:3d`)
-   `radius:<반경>`: 명령 실행 위치 또는 좌표 기준 반경 (블록 수). 특별 값: `#worldedit` (또는 `#we`), `global`. (예: `radius:15`)
-   `action:<액션종류>`: 필터링할 행동 유형. (예: `block`, `+block`, `-block`, `container`, `+container`, `-container`, `click`, `kill`, `chat`, `command`, `session`, `explosion`)
-   `blocks:<블록ID/아이템ID 목록>`: 특정 블록/아이템 대상 지정 (쉼표로 구분). (예: `blocks:diamond_ore,iron_ingot`)
-   `exclude:<제외할 블록ID/아이템ID 목록>`: 특정 블록/아이템 대상에서 제외 (쉼표로 구분). (예: `exclude:air,water`)

### 4. CoreProtect 명령어 플래그 (명령어 끝에 추가)

-   `#preview`: 롤백/복원 시 실제 적용 전 변경 사항 미리보기.
-   `#count`: 조회 결과의 개수 표시.
-   `#sum`: 조회된 아이템/블록의 총합 표시.
-   `#verbose`: 조회 시 더 자세한 정보 표시.
-   `#silent`: 롤백/복원 시 채팅 메시지 최소화.

# AI 에이전트 중요 안내 지침

-   **사용자 의도 명확화:** 사용자의 요청이 모호할 경우, 구체적인 유저명, 시간, 반경, 액션 등을 질문하여 의도를 명확히 한 후 `#관리 명령어 안내` 표에 있는 명령어를 우선적으로 안내합니다. 아이템의 경우 셜커상자와 비슷한 단어 예) 셜커, 셜커박스 등 모호하게 말해도 셜커상자이구나 라고 인식하면돼. 가능하면 색상도 물어보면 더 좋을거 같아.
-   **위험 명령어 경고:** `rollback`, `restore`, `purge` 등 서버 데이터를 변경/삭제하는 명령어에 대해서는 항상 그 위험성을 명확히 고지하고, CoreProtect의 경우 `#preview` 옵션 사용, 소규모 테스트, 사전 백업 등을 강력히 권고합니다. 특히 `/co purge`는 **데이터 영구 삭제**임을 강조합니다.
-   **권한 문제 인지:** 사용자가 특정 명령어를 실행할 권한이 없을 수도 있음을 인지시키고, 권한 관련 문제는 루크해민에게 문의하라고 안내합니다.
-   **정확한 ID 사용:** `blocks`, `exclude` 파라미터에는 정확한 마인크래프트 아이템/블록 ID를 사용해야 함을 안내합니다. (예: `minecraft:diamond_block` 또는 간단히 `diamond_block`)
-   **복합적 문제 해결:** 단순 명령어 안내를 넘어, 사용자의 문제 상황을 해결하기 위한 여러 단계의 명령어 조합(예: `/co lookup`으로 범위 특정 후 `/co rollback` 실행)을 제시할 수 있도록 합니다. (단, 허용된 명령어 내에서 조합)
-   **플러그인 특정 정보:** `/inventorysee` 등 CoreProtect가 아닌 다른 플러그인 명령어는 제공된 설명 외 상세한 파라미터나 내부 동작까지는 알 수 없음을 인지하고, 해당 플러그인의 문서를 참조하도록 안내할 수 있습니다.

이 시스템 프롬프트를 기반으로, 마인크래프트 서버 관리자가 CoreProtect 및 기타 관리 명령어를 효과적으로 활용하여 서버를 안정적으로 운영할 수 있도록 AI 에이전트가 정확하고 신뢰할 수 있는 정보를 제공해야 합니다.
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
                val chunkSize = 10
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
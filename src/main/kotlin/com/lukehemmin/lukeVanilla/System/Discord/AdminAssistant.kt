package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.WarningSystem.BanManager
import com.lukehemmin.lukeVanilla.System.WarningSystem.RiskLevel
import com.lukehemmin.lukeVanilla.System.WarningSystem.WarningService
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletion.Choice

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.awt.Color
import java.sql.Connection
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*

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
     * UUID 또는 닉네임으로 플레이어 정보를 조회
     */
    private fun findPlayerInfo(identifier: String): PlayerInfo? {
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
    private fun getPlayerItems(uuid: String): Map<String, List<String>> {
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
        val warningQueryPattern = Regex("(.+?)\\s*(?:유저|플레이어)?의?\\s*경고\\s*(?:내역|기록|목록)")
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
- 관리자가 특정 유저의 정보(닉네임, UUID, IP, 밴 여부, 인증 여부, 경고 횟수, 마지막 접속일, 아이템 소유 현황 등)를 궁금해하거나 조회를 요청하는 경우, 아래 '# 플레이어 정보 조회 안내' 지침에 따라 응답하고 필요한 정보를 추출해야 해.

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

# 플레이어 정보 조회 안내
- 관리자가 특정 플레이어의 정보(예: 'Luke 유저 정보 보여줘', '123e4567-e89b-12d3-a456-426614174000 UUID 가진 애 밴 됐어?')를 조회하려고 하면, 너는 해당 요청을 이해하고 다음 형식으로 응답해야 해.
- 응답 형식: `ACTION_PLAYER_INFO_SEARCH: [플레이어 닉네임 또는 UUID]`
- 예시:
    - 관리자: 'Luke 유저 정보 좀 알려줘.'
    - 너의 응답: `ACTION_PLAYER_INFO_SEARCH: Luke`
    - 관리자: '123e4567-e89b-12d3-a456-426614174000 이 UUID 가진 유저 밴 여부 확인해줘.'
    - 너의 응답: `ACTION_PLAYER_INFO_SEARCH: 123e4567-e89b-12d3-a456-426614174000`
- 만약 닉네임이나 UUID가 명확하지 않으면, 사용자에게 다시 질문해서 명확한 식별자를 받아내야 해.
- 너는 직접 데이터베이스를 조회하거나 유저 정보를 표시하지 않아. 단지 위 형식으로 응답하여 시스템이 해당 정보를 조회하도록 지시해야 해.

# 경고 시스템 안내
- 관리자가 경고 시스템에 대해 질문하거나 경고 명령을 사용하려고 할 때, 아래 내용을 참고하여 친절히 안내해야 해.
- 경고 명령어 형식: `경고 <유저닉네임> <사유>`
- 경고 5회 누적 시 자동으로 차단됩니다. (IP 차단 포함)
- 경고 조회 명령어: `경고 조회 <유저닉네임>`
- 예시:
    - 관리자: '경고 어떻게 주나요?'
    - 너의 응답: '특정 유저에게 경고를 하길 원하신다면 경고 <유저닉네임> <사유> 를 보내주시면 대신 경고를 처리해드리겠습니다. 경고가 5회 누적되면 자동으로 서버와 디스코드에서 차단됩니다.'
    - 관리자: '경고 lukehemmin 테러범'
    - 너의 응답: `ACTION_PLAYER_WARNING: lukehemmin;테러범`
    - 관리자: '저번에 경고 받은 사람 누구지?'
    - 너의 응답: `ACTION_RECENT_WARNINGS`
- 만약 경고 명령이 모호하거나 닉네임이 명확하지 않으면, 사용자에게 다시 질문해서 명확한 정보를 받아내야 해.
- 명령 처리 후 시스템이 자동으로 결과를 알려줄 거야. 너는 직접 처리 결과를 알려주지 않아도 돼.

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
                contextQueue.addLast(messageContent to aiResponseContent)                // AI 응답에서 액션 확인 및 처리
                when {
                    // 플레이어 정보 조회 액션
                    aiResponseContent.startsWith("ACTION_PLAYER_INFO_SEARCH:") -> {
                        val playerIdentifier = aiResponseContent.substring("ACTION_PLAYER_INFO_SEARCH:".length).trim()
                        if (playerIdentifier.isNotEmpty()) {
                            processPlayerInfoRequest(event, playerIdentifier)
                            return // 플레이어 정보 조회를 처리했으므로 추가 AI 답변 표시는 생략
                        }
                    }
                    // 플레이어 경고 액션
                    aiResponseContent.startsWith("ACTION_PLAYER_WARNING:") -> {
                        val warningData = aiResponseContent.substring("ACTION_PLAYER_WARNING:".length).trim()
                        if (warningData.contains(";")) {
                            val parts = warningData.split(";", limit = 2)
                            if (parts.size == 2) {
                                val playerName = parts[0].trim()
                                val reason = parts[1].trim()
                                processPlayerWarningRequest(event, playerName, reason, event.author.id)
                                return // 경고 처리를 했으므로 추가 AI 답변 표시는 생략
                            }
                        }
                    }
                    // 최근 경고 내역 조회 액션
                    aiResponseContent.startsWith("ACTION_RECENT_WARNINGS") -> {
                        processRecentWarningsRequest(event)
                        return // 경고 내역 조회를 처리했으므로 추가 AI 답변 표시는 생략
                    }
                }

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
    private fun createPlayerInfoEmbed(playerInfo: PlayerInfo): MessageEmbed {
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
    private fun createItemInfoEmbed(playerInfo: PlayerInfo, items: Map<String, List<String>>): MessageEmbed {
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
     * 특정 플레이어의 경고 내역 조회 처리
     */
    private fun processPlayerWarningQuery(event: MessageReceivedEvent, playerName: String) {
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
                        "${index + 1}. $reason\n   **일시**: $dateStr\n   **관리자**: ${warning.adminName ?: "시스템"}"
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
    private fun processPlayerWarningRequest(event: MessageReceivedEvent, playerName: String, reason: String, adminDiscordId: String) {
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
        val adminUuid = UUID.fromString(adminDiscordId) // 디스코드 ID를 UUID로 변환
        
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
                    connection.prepareStatement(insertPlayerQuery, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
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
                
                connection.prepareStatement(insertWarningQuery).use { statement ->
                    statement.setInt(1, playerId)
                    statement.setString(2, adminDiscordId)
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
                if (currentWarnings >= WarningService.AUTO_BAN_THRESHOLD) {
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
    private fun processRecentWarningsRequest(event: MessageReceivedEvent) {
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
}
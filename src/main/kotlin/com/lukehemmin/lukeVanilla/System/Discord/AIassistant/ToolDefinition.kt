package com.lukehemmin.lukeVanilla.System.Discord.AIassistant

import net.dv8tion.jda.api.events.message.MessageReceivedEvent

/**
 * 도구 매개변수 정의
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null,
    val example: String? = null
)

/**
 * 도구 정의
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val handler: ToolHandler,
    val category: String = "general"
)

/**
 * 도구 실행 결과
 */
data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val shouldShowToUser: Boolean = true,
    val embedData: Any? = null
)

/**
 * 도구 실행 컨텍스트
 */
data class ToolExecutionContext(
    val event: MessageReceivedEvent,
    val adminAssistant: AdminAssistant,
    val userId: String = event.author.id,
    val channelId: String = event.channel.id
)

/**
 * 도구 핸들러 인터페이스
 */
interface ToolHandler {
    fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult
    
    /**
     * 매개변수 검증 (선택적 구현)
     */
    fun validateParameters(parameters: Map<String, Any>): ToolResult {
        return ToolResult(true, "검증 성공")
    }
}

/**
 * 도구 호출 요청 데이터
 */
data class ToolCallRequest(
    val tool: String,
    val parameters: Map<String, Any> = emptyMap()
) 
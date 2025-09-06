package com.lukehemmin.lukeVanilla.System.Discord.AIassistant.tools

import com.lukehemmin.lukeVanilla.System.Discord.AIassistant.*


/**
 * 플레이어 경고 부여 도구
 */
class AddWarningTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val playerName = parameters["player_name"] as? String
            ?: return ToolResult(false, "플레이어 이름이 필요합니다.")
        val reason = parameters["reason"] as? String
            ?: return ToolResult(false, "경고 사유가 필요합니다.")
        
        try {
            // 기존 AdminAssistant의 processPlayerWarningRequest 메서드 활용
            context.adminAssistant.processPlayerWarningRequest(
                context.event, 
                playerName, 
                reason, 
                context.userId
            )
            
            return ToolResult(
                success = true,
                message = "플레이어 '${playerName}'에게 경고를 부여했습니다. 사유: ${reason}",
                shouldShowToUser = false
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "경고 부여 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ToolResult {
        val playerName = parameters["player_name"] as? String
        val reason = parameters["reason"] as? String
        
        if (playerName.isNullOrBlank()) {
            return ToolResult(false, "플레이어 이름이 비어있거나 누락되었습니다.")
        }
        
        if (reason.isNullOrBlank()) {
            return ToolResult(false, "경고 사유가 비어있거나 누락되었습니다.")
        }
        
        if (reason.length < 5) {
            return ToolResult(false, "경고 사유는 최소 5글자 이상 입력해주세요.")
        }
        
        return ToolResult(true, "검증 성공")
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "add_player_warning",
            description = "플레이어에게 경고를 부여합니다. 5회 누적 시 자동 차단됩니다.",
            parameters = listOf(
                ToolParameter(
                    name = "player_name",
                    type = "string",
                    description = "경고를 부여할 플레이어의 닉네임 (디스코드 멘션도 가능)",
                    example = "Luke"
                ),
                ToolParameter(
                    name = "reason",
                    type = "string", 
                    description = "경고 사유 (최소 5글자 이상)",
                    example = "욕설 사용"
                )
            ),
            handler = AddWarningTool(),
            category = "warning"
        )
    }
}

/**
 * 플레이어 경고 조회 도구
 */
class GetWarningsTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val playerName = parameters["player_name"] as? String
            ?: return ToolResult(false, "플레이어 이름이 필요합니다.")
        
        try {
            // 기존 AdminAssistant의 processPlayerWarningQuery 메서드 활용
            context.adminAssistant.processPlayerWarningQuery(context.event, playerName)
            
            return ToolResult(
                success = true,
                message = "플레이어 '${playerName}'의 경고 내역을 조회했습니다.",
                shouldShowToUser = false
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "경고 조회 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "get_player_warnings",
            description = "특정 플레이어의 경고 내역을 상세하게 조회합니다 (경고 ID, 날짜, 사유, 관리자 등)",
            parameters = listOf(
                ToolParameter(
                    name = "player_name",
                    type = "string",
                    description = "경고 내역을 조회할 플레이어의 닉네임",
                    example = "Luke"
                )
            ),
            handler = GetWarningsTool(),
            category = "warning"
        )
    }
}

/**
 * 경고 차감(사면) 도구
 */
class PardonWarningTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val playerName = parameters["player_name"] as? String
            ?: return ToolResult(false, "플레이어 이름이 필요합니다.")
        val warningId = parameters["warning_id"] as? Number
            ?: return ToolResult(false, "경고 ID가 필요합니다.")
        val pardonReason = parameters["pardon_reason"] as? String ?: "관리자 판단"
        
        try {
            // 기존 AdminAssistant의 processWarningPardonRequest 메서드 활용
            context.adminAssistant.processWarningPardonRequest(
                context.event,
                playerName,
                warningId.toInt(),
                pardonReason,
                context.userId
            )
            
            return ToolResult(
                success = true,
                message = "플레이어 '${playerName}'의 경고 ID ${warningId}를 차감했습니다. 사유: ${pardonReason}",
                shouldShowToUser = false
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "경고 차감 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ToolResult {
        val playerName = parameters["player_name"] as? String
        val warningId = parameters["warning_id"] as? Number
        
        if (playerName.isNullOrBlank()) {
            return ToolResult(false, "플레이어 이름이 비어있거나 누락되었습니다.")
        }
        
        if (warningId == null) {
            return ToolResult(false, "경고 ID가 누락되었습니다.")
        }
        
        if (warningId.toInt() <= 0) {
            return ToolResult(false, "경고 ID는 1 이상의 양수여야 합니다.")
        }
        
        return ToolResult(true, "검증 성공")
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "pardon_player_warning",
            description = "특정 경고 ID를 통해 플레이어의 경고를 차감(사면)합니다",
            parameters = listOf(
                ToolParameter(
                    name = "player_name",
                    type = "string",
                    description = "경고를 차감할 플레이어의 닉네임",
                    example = "Luke"
                ),
                ToolParameter(
                    name = "warning_id",
                    type = "number",
                    description = "차감할 경고의 고유 ID (경고 조회 시 확인 가능)",
                    example = "123"
                ),
                ToolParameter(
                    name = "pardon_reason",
                    type = "string",
                    description = "경고 차감 사유",
                    required = false,
                    example = "오해로 인한 경고였음"
                )
            ),
            handler = PardonWarningTool(),
            category = "warning"
        )
    }
}

/**
 * 최근 경고 분석 도구
 */
class RecentWarningsTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        try {
            // 기존 AdminAssistant의 processRecentWarningsRequest 메서드 활용
            context.adminAssistant.processRecentWarningsRequest(context.event)
            
            return ToolResult(
                success = true,
                message = "최근 경고 내역 및 분석 리포트를 생성했습니다.",
                shouldShowToUser = false
            )
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "최근 경고 분석 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "get_recent_warnings_analysis",
            description = "최근 경고 내역을 분석하여 위험 사용자, 경고 패턴, 추천 조치사항 등을 제공합니다",
            parameters = emptyList(),
            handler = RecentWarningsTool(),
            category = "warning"
        )
    }
} 
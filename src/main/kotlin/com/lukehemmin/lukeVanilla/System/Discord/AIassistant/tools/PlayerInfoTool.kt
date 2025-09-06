package com.lukehemmin.lukeVanilla.System.Discord.AIassistant.tools

import com.lukehemmin.lukeVanilla.System.Discord.AIassistant.*


/**
 * 플레이어 정보 조회 도구
 */
class PlayerInfoTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val identifier = parameters["identifier"] as? String
            ?: return ToolResult(false, "플레이어 식별자(닉네임 또는 UUID)가 필요합니다.")
        
        try {
            val playerInfo = context.adminAssistant.findPlayerInfo(identifier)
            
            if (playerInfo != null) {
                // 임베드 생성 및 전송
                val embed = context.adminAssistant.createPlayerInfoEmbed(playerInfo)
                val viewItemsButton = context.adminAssistant.createItemsButton(playerInfo.uuid)
                
                context.event.channel.sendMessageEmbeds(embed)
                    .addActionRow(viewItemsButton)
                    .queue()
                
                return ToolResult(
                    success = true,
                    message = "플레이어 '${playerInfo.nickname}' 정보를 조회했습니다.",
                    data = playerInfo,
                    shouldShowToUser = false
                )
            } else {
                return ToolResult(
                    success = false,
                    message = "플레이어 '${identifier}'를 찾을 수 없습니다. 닉네임이나 UUID가 올바른지 확인해주세요."
                )
            }
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "플레이어 정보 조회 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ToolResult {
        val identifier = parameters["identifier"] as? String
        
        if (identifier.isNullOrBlank()) {
            return ToolResult(false, "플레이어 식별자가 비어있거나 누락되었습니다.")
        }
        
        if (identifier.length < 3) {
            return ToolResult(false, "플레이어 식별자는 최소 3글자 이상이어야 합니다.")
        }
        
        return ToolResult(true, "검증 성공")
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "get_player_info",
            description = "플레이어의 상세 정보를 조회합니다 (UUID, 닉네임, 밴 여부, 인증 상태, 칭호, IP 주소 등)",
            parameters = listOf(
                ToolParameter(
                    name = "identifier",
                    type = "string",
                    description = "플레이어 닉네임 또는 UUID (대시 포함/미포함 모두 가능)",
                    example = "Luke 또는 123e4567-e89b-12d3-a456-426614174000"
                )
            ),
            handler = PlayerInfoTool(),
            category = "player"
        )
    }
}

/**
 * 플레이어 아이템 조회 도구
 */
class PlayerItemsTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val identifier = parameters["identifier"] as? String
            ?: return ToolResult(false, "플레이어 식별자가 필요합니다.")
        
        try {
            val playerInfo = context.adminAssistant.findPlayerInfo(identifier)
            
            if (playerInfo != null) {
                val items = context.adminAssistant.getPlayerItems(playerInfo.uuid)
                val embed = context.adminAssistant.createItemInfoEmbed(playerInfo, items)
                
                context.event.channel.sendMessageEmbeds(embed).queue()
                
                return ToolResult(
                    success = true,
                    message = "플레이어 '${playerInfo.nickname}'의 아이템 정보를 조회했습니다.",
                    data = items,
                    shouldShowToUser = false
                )
            } else {
                return ToolResult(
                    success = false,
                    message = "플레이어 '${identifier}'를 찾을 수 없습니다."
                )
            }
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "플레이어 아이템 조회 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "get_player_items",
            description = "플레이어가 소유한 시즌 아이템(할로윈, 크리스마스, 발렌타인 등)을 조회합니다",
            parameters = listOf(
                ToolParameter(
                    name = "identifier",
                    type = "string",
                    description = "플레이어 닉네임 또는 UUID",
                    example = "Luke"
                )
            ),
            handler = PlayerItemsTool(),
            category = "player"
        )
    }
} 
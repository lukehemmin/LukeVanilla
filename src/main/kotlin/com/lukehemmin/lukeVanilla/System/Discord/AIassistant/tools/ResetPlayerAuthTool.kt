package com.lukehemmin.lukeVanilla.System.Discord.AIassistant.tools

import com.lukehemmin.lukeVanilla.System.Discord.AIassistant.*
import net.dv8tion.jda.api.EmbedBuilder
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*

/**
 * 플레이어 디스코드 인증 해제 도구
 * 관리자가 특정 플레이어의 디스코드 연동을 해제하여 다른 디스코드 계정으로 재인증할 수 있도록 함
 */
class ResetPlayerAuthTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val identifier = parameters["identifier"] as? String
            ?: return ToolResult(false, "플레이어 식별자(닉네임 또는 UUID)가 필요합니다.")
        val reason = parameters["reason"] as? String ?: "관리자 요청"
        
        try {
            // 플레이어 정보 조회
            val playerInfo = context.adminAssistant.findPlayerInfo(identifier)
            if (playerInfo == null) {
                return ToolResult(
                    success = false,
                    message = "플레이어 '${identifier}'를 찾을 수 없습니다. 닉네임이나 UUID가 올바른지 확인해주세요."
                )
            }
            
            // 현재 디스코드 연동 상태 확인
            if (playerInfo.discordId.isNullOrBlank()) {
                return ToolResult(
                    success = false,
                    message = "플레이어 '${playerInfo.nickname}'는 현재 디스코드와 연동되어 있지 않습니다."
                )
            }
            
            // 현재 연동된 디스코드 정보 저장 (로그용)
            val previousDiscordId = playerInfo.discordId
            
            // 디스코드 연동 해제 실행
            val resetSuccess = resetPlayerDiscordAuth(playerInfo.uuid, context)
            
            if (resetSuccess) {
                // 성공 임베드 생성 및 전송
                val embed = createResetSuccessEmbed(playerInfo, previousDiscordId, reason, context.event.author.name)
                context.event.channel.sendMessageEmbeds(embed).queue()
                
                return ToolResult(
                    success = true,
                    message = "플레이어 '${playerInfo.nickname}'의 디스코드 인증을 성공적으로 해제했습니다.",
                    shouldShowToUser = false
                )
            } else {
                return ToolResult(
                    success = false,
                    message = "플레이어 '${playerInfo.nickname}'의 디스코드 인증 해제 중 오류가 발생했습니다."
                )
            }
            
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "디스코드 인증 해제 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    /**
     * 플레이어의 디스코드 인증을 실제로 해제하는 메서드
     */
    private fun resetPlayerDiscordAuth(playerUuid: String, context: ToolExecutionContext): Boolean {
        return try {
            context.adminAssistant.dbConnectionProvider().use { connection ->
                // 트랜잭션 시작
                connection.autoCommit = false
                
                try {
                    // 1. Player_Data 테이블에서 DiscordID 제거
                    val updatePlayerDataQuery = "UPDATE lukevanilla.Player_Data SET DiscordID = NULL WHERE UUID = ?"
                    var rowsAffected = 0
                    
                    connection.prepareStatement(updatePlayerDataQuery).use { statement ->
                        statement.setString(1, playerUuid)
                        rowsAffected += statement.executeUpdate()
                    }
                    
                    // 2. Player_Auth 테이블에서 IsAuth를 FALSE로 설정
                    val updatePlayerAuthQuery = "UPDATE lukevanilla.Player_Auth SET IsAuth = FALSE WHERE UUID = ?"
                    
                    connection.prepareStatement(updatePlayerAuthQuery).use { statement ->
                        statement.setString(1, playerUuid)
                        rowsAffected += statement.executeUpdate()
                    }
                    
                    // 트랜잭션 커밋
                    connection.commit()
                    
                    println("[ResetPlayerAuthTool] 인증 해제 완료 - Player_Data.DiscordID=NULL, Player_Auth.IsAuth=FALSE (UUID: $playerUuid)")
                    rowsAffected > 0
                    
                } catch (e: Exception) {
                    // 오류 발생 시 롤백
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: Exception) {
            System.err.println("[ResetPlayerAuthTool] 디스코드 인증 해제 중 데이터베이스 오류: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 인증 해제 성공 임베드 생성
     */
    private fun createResetSuccessEmbed(
        playerInfo: AdminAssistant.PlayerInfo,
        previousDiscordId: String,
        reason: String,
        adminName: String
    ): net.dv8tion.jda.api.entities.MessageEmbed {
        val embed = EmbedBuilder().apply {
            setTitle("🔓 디스코드 인증 해제 완료")
            setColor(Color.ORANGE)
            setDescription("플레이어의 디스코드 연동이 성공적으로 해제되었습니다.")
            
            // 플레이어 정보
            addField("플레이어", playerInfo.nickname, true)
            addField("UUID", playerInfo.uuid, false)
            
            // 해제된 디스코드 정보
            addField("해제된 디스코드", "<@${previousDiscordId}>", true)
            addField("해제 사유", reason, true)
            addField("처리 관리자", adminName, true)
            
            // 안내 메시지
            addField(
                "📋 안내사항", 
                "• 해당 플레이어가 서버에 접속하면 새로운 인증코드를 받을 수 있습니다.\n" +
                "• 플레이어가 서버 접속을 시도하면 킥 메시지에 인증코드가 표시됩니다.\n" +
                "• 플레이어에게 킥 메시지의 인증코드를 디스코드 인증채널에 입력하도록 안내해주세요.\n" +
                "• 인증 과정에서 문제가 발생하면 관리자에게 문의하도록 안내해주세요.",
                false
            )
            
            // 푸터 - 처리 시간
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            setFooter("처리 시간: ${sdf.format(Date())}")
        }
        
        return embed.build()
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ToolResult {
        val identifier = parameters["identifier"] as? String
        
        if (identifier.isNullOrBlank()) {
            return ToolResult(false, "플레이어 식별자가 비어있거나 누락되었습니다.")
        }
        
        if (identifier.length < 3) {
            return ToolResult(false, "플레이어 식별자는 최소 3글자 이상이어야 합니다.")
        }
        
        // reason은 선택사항이므로 검증하지 않음
        
        return ToolResult(true, "검증 성공")
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "reset_player_discord_auth",
            description = "플레이어의 디스코드 인증을 해제하여 다른 디스코드 계정으로 재인증할 수 있도록 합니다",
            parameters = listOf(
                ToolParameter(
                    name = "identifier",
                    type = "string",
                    description = "플레이어 닉네임 또는 UUID",
                    example = "lukehemmin 또는 123e4567-e89b-12d3-a456-426614174000"
                ),
                ToolParameter(
                    name = "reason",
                    type = "string",
                    description = "인증 해제 사유 (선택사항)",
                    required = false,
                    example = "디스코드 계정 변경으로 인한 재인증 요청"
                )
            ),
            handler = ResetPlayerAuthTool(),
            category = "player"
        )
    }
}
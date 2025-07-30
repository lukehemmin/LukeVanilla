package com.lukehemmin.lukeVanilla.System.Discord.AIassistant.tools

import com.lukehemmin.lukeVanilla.System.Discord.AIassistant.*
import com.lukehemmin.lukeVanilla.System.Discord.ServerStatusProvider

import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.Bukkit
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*

/**
 * 서버 상태 조회 도구
 */
class ServerStatusTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val format = parameters["format"] as? String ?: "embed"
        
        try {
            val serverStatus = ServerStatusProvider.getServerStatusString()
            
            when (format.lowercase()) {
                "embed" -> {
                    val embed = createServerStatusEmbed(serverStatus)
                    context.event.channel.sendMessageEmbeds(embed).queue()
                    
                    return ToolResult(
                        success = true,
                        message = "서버 상태를 임베드 형태로 조회했습니다.",
                        shouldShowToUser = false
                    )
                }
                "text", "plain" -> {
                    return ToolResult(
                        success = true,
                        message = "**현재 서버 상태**\n```\n$serverStatus\n```",
                        shouldShowToUser = true
                    )
                }
                else -> {
                    return ToolResult(
                        success = false,
                        message = "지원하지 않는 형식입니다. 'embed' 또는 'text'를 사용하세요."
                    )
                }
            }
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "서버 상태 조회 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    private fun createServerStatusEmbed(statusString: String): net.dv8tion.jda.api.entities.MessageEmbed {
        val embed = EmbedBuilder().apply {
            setTitle("🖥️ 서버 상태 정보")
            setColor(Color.GREEN)
            setDescription("실시간 서버 상태 정보입니다.")
            
            // 상태 문자열 파싱
            val lines = statusString.split("\n")
            lines.forEach { line ->
                if (line.contains("로비:")) {
                    val lobbyInfo = line.substring(line.indexOf("로비:") + 3).trim()
                    addField("🏠 로비 서버", formatServerInfo(lobbyInfo), false)
                } else if (line.contains("야생:")) {
                    val survivalInfo = line.substring(line.indexOf("야생:") + 3).trim()
                    addField("🌲 야생 서버", formatServerInfo(survivalInfo), false)
                }
            }
            
            // 현재 시간 추가
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            setFooter("조회 시간: ${sdf.format(Date())}")
            setTimestamp(Date().toInstant())
        }
        
        return embed.build()
    }
    
    private fun formatServerInfo(info: String): String {
        return info.split(", ")
            .joinToString("\n") { part ->
                when {
                    part.startsWith("TPS:") -> "📈 $part"
                    part.startsWith("MSPT:") -> "⏱️ $part" 
                    part.startsWith("Ping:") -> "📡 $part"
                    part.startsWith("Players:") -> "👥 $part"
                    else -> "ℹ️ $part"
                }
            }
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "get_server_status",
            description = "현재 서버의 상태를 조회합니다 (TPS, MSPT, 플레이어 수, 핑 등)",
            parameters = listOf(
                ToolParameter(
                    name = "format",
                    type = "string",
                    description = "결과 표시 형식",
                    required = false,
                    enum = listOf("embed", "text"),
                    example = "embed"
                )
            ),
            handler = ServerStatusTool(),
            category = "server"
        )
    }
}

/**
 * 온라인 플레이어 목록 조회 도구
 */
class OnlinePlayersTool : ToolHandler {
    
    override fun execute(
        parameters: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val includeDetails = parameters["include_details"] as? Boolean ?: false
        
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            
            if (onlinePlayers.isEmpty()) {
                return ToolResult(
                    success = true,
                    message = "현재 접속 중인 플레이어가 없습니다."
                )
            } else {
                val embed = createOnlinePlayersEmbed(onlinePlayers.toList(), includeDetails)
                context.event.channel.sendMessageEmbeds(embed).queue()
                
                return ToolResult(
                    success = true,
                    message = "현재 접속 중인 플레이어 목록을 조회했습니다. (총 ${onlinePlayers.size}명)",
                    shouldShowToUser = false
                )
            }
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "온라인 플레이어 조회 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    private fun createOnlinePlayersEmbed(
        players: List<org.bukkit.entity.Player>, 
        includeDetails: Boolean
    ): net.dv8tion.jda.api.entities.MessageEmbed {
        val embed = EmbedBuilder().apply {
            setTitle("👥 접속 중인 플레이어")
            setColor(Color.BLUE)
            setDescription("현재 서버에 접속 중인 플레이어 목록입니다.")
            
            if (includeDetails) {
                players.chunked(10).forEachIndexed { pageIndex, chunk ->
                    val playerList = chunk.joinToString("\n") { player ->
                        val health = String.format("%.1f", player.health)
                        val level = player.level
                        "• **${player.name}** (❤️ $health, Lv.$level)"
                    }
                    
                    addField(
                        "플레이어 목록 ${if (players.size > 10) "(${pageIndex + 1})" else ""}", 
                        playerList, 
                        false
                    )
                }
            } else {
                val playerNames = players.map { it.name }
                playerNames.chunked(15).forEachIndexed { pageIndex, chunk ->
                    val playerList = chunk.joinToString(", ")
                    addField(
                        "플레이어 ${if (playerNames.size > 15) "(${pageIndex + 1})" else ""}", 
                        playerList, 
                        false
                    )
                }
            }
            
            addField("총 접속자 수", "${players.size}명", true)
            addField("최대 접속자 수", "${Bukkit.getMaxPlayers()}명", true)
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            setFooter("조회 시간: ${sdf.format(Date())}")
        }
        
        return embed.build()
    }
    
    companion object {
        val definition = ToolDefinition(
            name = "get_online_players",
            description = "현재 서버에 접속 중인 플레이어 목록을 조회합니다",
            parameters = listOf(
                ToolParameter(
                    name = "include_details",
                    type = "boolean",
                    description = "플레이어의 상세 정보(체력, 레벨 등)를 포함할지 여부",
                    required = false,
                    example = "false"
                )
            ),
            handler = OnlinePlayersTool(),
            category = "server"
        )
    }
} 
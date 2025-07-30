package com.lukehemmin.lukeVanilla.System.Discord.AIassistant

import com.lukehemmin.lukeVanilla.System.Discord.AIassistant.tools.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException


/**
 * MCP 스타일 도구 관리자
 * AI가 JSON 형식으로 도구를 호출할 수 있도록 지원
 */
class ToolManager {
    private val tools = mutableMapOf<String, ToolDefinition>()
    private val gson = Gson()
    
    init {
        registerAllTools()
    }
    
    /**
     * 모든 기본 도구들을 등록
     */
    private fun registerAllTools() {
        // 플레이어 관련 도구
        registerTool(PlayerInfoTool.definition)
        registerTool(PlayerItemsTool.definition)
        
        // 경고 시스템 도구
        registerTool(AddWarningTool.definition)
        registerTool(GetWarningsTool.definition)
        registerTool(PardonWarningTool.definition)
        registerTool(RecentWarningsTool.definition)
        
        // 서버 관리 도구
        registerTool(ServerStatusTool.definition)
        registerTool(OnlinePlayersTool.definition)
    }
    
    /**
     * 새로운 도구 등록
     */
    fun registerTool(tool: ToolDefinition) {
        tools[tool.name] = tool
        println("[ToolManager] 도구 등록: ${tool.name} (${tool.category})")
    }
    
    /**
     * 등록된 도구 목록 조회
     */
    fun getRegisteredTools(): Map<String, ToolDefinition> = tools.toMap()
    
    /**
     * AI에게 제공할 도구 설명 프롬프트 생성
     */
    fun generateToolsPrompt(): String {
        return buildString {
            appendLine("# 🛠️ 사용 가능한 도구들")
            appendLine()
            appendLine("다음 JSON 형식으로 도구를 호출할 수 있습니다:")
            appendLine("```json")
            appendLine("""{"tool": "도구이름", "parameters": {"매개변수": "값"}}""")
            appendLine("```")
            appendLine()
            
            // 카테고리별로 도구 분류
            val categories = tools.values.groupBy { it.category }
            
            categories.forEach { (category, categoryTools) ->
                val categoryName = when (category) {
                    "player" -> "👤 플레이어 관리"
                    "warning" -> "⚠️ 경고 시스템"
                    "server" -> "🖥️ 서버 관리"
                    else -> "📋 기타"
                }
                
                appendLine("## $categoryName")
                appendLine()
                
                categoryTools.forEach { tool ->
                    appendLine("### `${tool.name}`")
                    appendLine("**설명**: ${tool.description}")
                    
                    if (tool.parameters.isNotEmpty()) {
                        appendLine("**매개변수**:")
                        tool.parameters.forEach { param ->
                            val required = if (param.required) " ✅" else " 🔘"
                            appendLine("- `${param.name}` (${param.type})${required}: ${param.description}")
                            
                            param.enum?.let { enumValues ->
                                appendLine("  - 가능한 값: ${enumValues.joinToString(", ")}")
                            }
                            
                            param.example?.let { example ->
                                appendLine("  - 예시: `${example}`")
                            }
                        }
                    } else {
                        appendLine("**매개변수**: 없음")
                    }
                    appendLine()
                }
            }
            
            appendLine("## 📝 사용 예시")
            appendLine("```json")
            appendLine("""{"tool": "get_player_info", "parameters": {"identifier": "Luke"}}""")
            appendLine("```")
            appendLine("```json")
            appendLine("""{"tool": "add_player_warning", "parameters": {"player_name": "BadPlayer", "reason": "규칙 위반"}}""")
            appendLine("```")
            appendLine("```json")
            appendLine("""{"tool": "get_server_status", "parameters": {"format": "embed"}}""")
            appendLine("```")
        }
    }
    
    /**
     * AI 응답에서 도구 호출 감지 및 실행
     */
    fun detectAndExecuteTools(
        aiResponse: String,
        context: ToolExecutionContext
    ): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        
        // JSON 코드 블록 패턴 감지
        val jsonPattern = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = jsonPattern.findAll(aiResponse)
        
        for (match in matches) {
            val jsonContent = match.groupValues[1].trim()
            try {
                val result = executeToolFromJson(jsonContent, context)
                results.add(result)
            } catch (e: Exception) {
                results.add(
                    ToolResult(
                        success = false,
                        message = "도구 실행 중 예외 발생: ${e.message}"
                    )
                )
            }
        }
        
        // JSON 패턴이 없으면 직접 JSON 파싱 시도
        if (results.isEmpty() && aiResponse.trim().startsWith("{") && aiResponse.trim().endsWith("}")) {
            try {
                val result = executeToolFromJson(aiResponse.trim(), context)
                results.add(result)
            } catch (e: Exception) {
                // 일반 AI 응답으로 처리 (도구 호출이 아님)
            }
        }
        
        return results
    }
    
    /**
     * JSON 문자열에서 도구 호출 정보를 파싱하고 실행
     */
    fun executeToolFromJson(
        jsonString: String, 
        context: ToolExecutionContext
    ): ToolResult {
        return try {
            // JSON 파싱
            val request = gson.fromJson(jsonString, Map::class.java) as Map<String, Any>
            
            val toolName = request["tool"] as? String
                ?: return ToolResult(false, "도구 이름이 지정되지 않았습니다.")
            
            val parameters = request["parameters"] as? Map<String, Any> ?: emptyMap()
            
            // 도구 찾기
            val tool = tools[toolName]
                ?: return ToolResult(false, "알 수 없는 도구: '$toolName'. 사용 가능한 도구: ${tools.keys.joinToString(", ")}")
            
            // 매개변수 검증
            val validationResult = validateToolParameters(tool, parameters)
            if (!validationResult.success) {
                return validationResult
            }
            
            // 도구 실행
            println("[ToolManager] 도구 실행: $toolName with params: $parameters")
            tool.handler.execute(parameters, context)
            
        } catch (e: JsonSyntaxException) {
            ToolResult(false, "JSON 형식이 올바르지 않습니다: ${e.message}")
        } catch (e: Exception) {
            ToolResult(false, "도구 실행 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 도구 매개변수 검증
     */
    private fun validateToolParameters(
        tool: ToolDefinition,
        parameters: Map<String, Any>
    ): ToolResult {
        // 필수 매개변수 확인
        val missingRequired = tool.parameters
            .filter { it.required && !parameters.containsKey(it.name) }
            .map { it.name }
        
        if (missingRequired.isNotEmpty()) {
            return ToolResult(
                false, 
                "필수 매개변수가 누락되었습니다: ${missingRequired.joinToString(", ")}"
            )
        }
        
        // enum 값 검증
        tool.parameters.forEach { param ->
            val value = parameters[param.name]
            if (value != null && param.enum != null) {
                val stringValue = value.toString()
                if (!param.enum.contains(stringValue)) {
                    return ToolResult(
                        false,
                        "매개변수 '${param.name}'의 값이 유효하지 않습니다. 가능한 값: ${param.enum.joinToString(", ")}"
                    )
                }
            }
        }
        
        // 도구별 커스텀 검증 실행
        return tool.handler.validateParameters(parameters)
    }
    
    /**
     * 도구 사용법 도움말 생성
     */
    fun getToolHelp(toolName: String): String? {
        val tool = tools[toolName] ?: return null
        
        return buildString {
            appendLine("## ${tool.name}")
            appendLine("**설명**: ${tool.description}")
            appendLine("**카테고리**: ${tool.category}")
            appendLine()
            
            if (tool.parameters.isNotEmpty()) {
                appendLine("**매개변수**:")
                tool.parameters.forEach { param ->
                    val required = if (param.required) " (필수)" else " (선택)"
                    appendLine("- `${param.name}` (${param.type})${required}: ${param.description}")
                    
                    param.enum?.let { 
                        appendLine("  - 가능한 값: ${it.joinToString(", ")}")
                    }
                    
                    param.example?.let {
                        appendLine("  - 예시: `${it}`")
                    }
                }
            }
            
            appendLine()
            appendLine("**사용 예시**:")
            appendLine("```json")
            val exampleParams = tool.parameters.associate { param ->
                param.name to (param.example ?: when (param.type) {
                    "string" -> "example_value"
                    "number" -> 123
                    "boolean" -> true
                    else -> "value"
                })
            }
            val exampleJson = mapOf("tool" to tool.name, "parameters" to exampleParams)
            appendLine(gson.toJson(exampleJson))
            appendLine("```")
        }
    }
    
    /**
     * 도구 실행 통계
     */
    fun getToolStats(): String {
        return buildString {
            appendLine("## 🛠️ 도구 시스템 정보")
            appendLine("- 등록된 도구 수: ${tools.size}개")
            
            val categories = tools.values.groupBy { it.category }
            categories.forEach { (category, toolsInCategory) ->
                val categoryName = when (category) {
                    "player" -> "플레이어 관리"  
                    "warning" -> "경고 시스템"
                    "server" -> "서버 관리"
                    else -> category
                }
                appendLine("- ${categoryName}: ${toolsInCategory.size}개")
            }
        }
    }
} 
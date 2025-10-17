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
        registerTool(ResetPlayerAuthTool.definition)
        
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
     * @return Pair<List<ToolResult>, String> - 실행 결과와 JSON을 제거한 남은 텍스트
     */
    fun detectAndExecuteTools(
        aiResponse: String,
        context: ToolExecutionContext
    ): Pair<List<ToolResult>, String> {
        val results = mutableListOf<ToolResult>()
        var remainingText = aiResponse

        // 1. JSON 코드 블록 패턴 감지 (```json {...} ```)
        val codeBlockPattern = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val codeBlockMatches = codeBlockPattern.findAll(aiResponse).toList()

        if (codeBlockMatches.isNotEmpty()) {
            // 코드 블록이 있으면 모두 실행하고 제거
            for (match in codeBlockMatches) {
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
            // 모든 JSON 코드 블록을 제거
            remainingText = remainingText.replace(codeBlockPattern, "").trim()
        } else {
            // 2. 코드 블록이 없으면 일반 JSON 패턴 감지
            // 중괄호 카운팅 방식으로 JSON 추출
            val jsonMatches = extractJsonObjects(aiResponse)

            if (jsonMatches.isNotEmpty()) {
                // 일반 JSON이 있으면 모두 실행하고 제거
                for (jsonContent in jsonMatches) {
                    try {
                        // "tool" 키워드가 있는 JSON만 처리
                        if (jsonContent.contains("\"tool\"")) {
                            val result = executeToolFromJson(jsonContent, context)
                            results.add(result)
                            // 성공적으로 파싱된 JSON만 제거
                            remainingText = remainingText.replace(jsonContent, "").trim()
                        }
                    } catch (e: Exception) {
                        // JSON 파싱 실패시 무시
                    }
                }
            } else if (aiResponse.trim().startsWith("{") && aiResponse.trim().endsWith("}")) {
                // 3. 전체 응답이 JSON인 경우
                try {
                    val result = executeToolFromJson(aiResponse.trim(), context)
                    results.add(result)
                    remainingText = "" // 전체가 JSON이므로 남은 텍스트 없음
                } catch (e: Exception) {
                    // 일반 AI 응답으로 처리 (도구 호출이 아님)
                }
            }
        }

        return Pair(results, remainingText)
    }
    
    /**
     * 문자열에서 중괄호 카운팅 방식으로 JSON 객체들을 추출
     */
    private fun extractJsonObjects(text: String): List<String> {
        val jsonObjects = mutableListOf<String>()
        var braceCount = 0
        var jsonStart = -1
        var inString = false
        var escapeNext = false

        for (i in text.indices) {
            val char = text[i]

            // 문자열 내부 처리
            if (char == '\\' && !escapeNext) {
                escapeNext = true
                continue
            }

            if (char == '"' && !escapeNext) {
                inString = !inString
            }

            escapeNext = false

            // 문자열 외부에서만 중괄호 카운팅
            if (!inString) {
                if (char == '{') {
                    if (braceCount == 0) {
                        jsonStart = i
                    }
                    braceCount++
                } else if (char == '}') {
                    braceCount--
                    if (braceCount == 0 && jsonStart != -1) {
                        // 완전한 JSON 객체 발견
                        val jsonObject = text.substring(jsonStart, i + 1)
                        jsonObjects.add(jsonObject)
                        jsonStart = -1
                    }
                }
            }
        }

        return jsonObjects
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
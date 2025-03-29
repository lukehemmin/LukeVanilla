package com.lukehemmin.lukeVanilla.System.API

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApiClient(private val plugin: Main) {
    private val client: OkHttpClient
    private val gson = Gson()
    private val baseUrl: String
    private var apiToken: String? = null
    
    init {
        // 설정에서 API URL 읽기
        baseUrl = plugin.config.getString("api.baseUrl") ?: "http://localhost:8080"
        
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // 토큰 초기화
        refreshToken()
    }
    
    // 토큰 갱신
    private fun refreshToken() {
        // 필요한 경우 API에서 토큰 가져오기
        // 현재는 인증 없이 API 사용
    }
    
    // 인증 코드 생성 API 호출
    fun generateAuthCode(uuid: String): String? {
        val jsonObject = JsonObject()
        jsonObject.addProperty("uuid", uuid)
        
        val request = Request.Builder()
            .url("$baseUrl/api/auth/code")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    plugin.logger.warning("Auth code generation failed: ${response.code}")
                    return null
                }
                
                val body = response.body?.string() ?: return null
                val result = gson.fromJson(body, JsonObject::class.java)
                result.get("authCode")?.asString
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error generating auth code: ${e.message}")
            null
        }
    }
    
    // 인증 코드 검증 API 호출
    fun validateAuthCode(authCode: String, discordId: String): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("authCode", authCode)
        jsonObject.addProperty("discordId", discordId)
        
        val request = Request.Builder()
            .url("$baseUrl/api/auth/validate")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error validating auth code: ${e.message}")
            false
        }
    }
    
    // 디스코드 ID로 플레이어 조회
    fun getPlayerByDiscordId(discordId: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/player/discord/$discordId")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting player by discord ID: ${e.message}")
            null
        }
    }
    
    // 플레이어 잔액 조회
    fun getPlayerBalance(uuid: String): BigDecimal? {
        val request = Request.Builder()
            .url("$baseUrl/api/economy/balance/$uuid")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                BigDecimal(body)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting player balance: ${e.message}")
            null
        }
    }
    
    // 플레이어 잔액 추가
    fun addPlayerBalance(uuid: String, amount: BigDecimal): BigDecimal? {
        val request = Request.Builder()
            .url("$baseUrl/api/economy/balance/$uuid/add?amount=$amount")
            .post("".toRequestBody())
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                BigDecimal(body)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error adding player balance: ${e.message}")
            null
        }
    }
    
    // 플레이어 잔액 차감
    fun removePlayerBalance(uuid: String, amount: BigDecimal): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/api/economy/balance/$uuid/remove?amount=$amount")
            .post("".toRequestBody())
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error removing player balance: ${e.message}")
            false
        }
    }
    
    // === 티켓 시스템 API ===
    
    // 티켓 생성
    fun createSupportTicket(playerUuid: String, title: String, description: String): JsonObject? {
        val jsonObject = JsonObject()
        jsonObject.addProperty("playerUuid", playerUuid)
        jsonObject.addProperty("title", title)
        jsonObject.addProperty("description", description)
        
        val request = Request.Builder()
            .url("$baseUrl/api/support/tickets")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    plugin.logger.warning("Failed to create ticket: ${response.code}")
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error creating support ticket: ${e.message}")
            null
        }
    }
    
    // 티켓 조회
    fun getSupportTicket(ticketId: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/support/tickets/$ticketId")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting support ticket: ${e.message}")
            null
        }
    }
    
    // 티켓 상태 업데이트
    fun updateTicketStatus(ticketId: String, status: String, message: String? = null): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("status", status)
        message?.let { jsonObject.addProperty("message", it) }
        
        val request = Request.Builder()
            .url("$baseUrl/api/support/tickets/$ticketId/status")
            .patch(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error updating ticket status: ${e.message}")
            false
        }
    }
    
    // 플레이어 티켓 목록 조회
    fun getPlayerTickets(playerUuid: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/support/tickets/player/$playerUuid")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting player tickets: ${e.message}")
            null
        }
    }
    
    // === 네임태그 확장 기능 ===
    
    // 사용 가능한 네임태그 스타일 조회
    fun getAvailableNametagStyles(): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/nametag/styles")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting nametag styles: ${e.message}")
            null
        }
    }
    
    // 네임태그 구매
    fun purchaseNametagStyle(playerUuid: String, styleId: String): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("playerUuid", playerUuid)
        jsonObject.addProperty("styleId", styleId)
        
        val request = Request.Builder()
            .url("$baseUrl/api/nametag/purchase")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error purchasing nametag style: ${e.message}")
            false
        }
    }
    
    // 네임태그 적용
    fun applyNametagStyle(playerUuid: String, styleId: String): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("styleId", styleId)
        
        val request = Request.Builder()
            .url("$baseUrl/api/nametag/player/$playerUuid/apply")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error applying nametag style: ${e.message}")
            false
        }
    }
    
    // 플레이어의 네임태그 조회
    fun getPlayerNametag(playerUuid: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/nametag/player/$playerUuid")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting player nametag: ${e.message}")
            null
        }
    }
    
    // === 서버 로깅 및 모니터링 ===
    
    // 서버 이벤트 로깅
    fun logServerEvent(eventType: String, data: JsonObject): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("eventType", eventType)
        jsonObject.add("data", data)
        
        val request = Request.Builder()
            .url("$baseUrl/api/logs/events")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error logging server event: ${e.message}")
            false
        }
    }
    
    // 서버 성능 데이터 보고
    fun reportServerPerformance(metrics: JsonObject): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/api/monitoring/performance")
            .post(gson.toJson(metrics).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error reporting server performance: ${e.message}")
            false
        }
    }
    
    // 서버 통계 조회
    fun getServerStats(period: String = "day"): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/monitoring/stats?period=$period")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting server stats: ${e.message}")
            null
        }
    }
    
    // === 채팅 관리 기능 ===
    
    // 채팅 메시지 로깅
    fun logChatMessage(playerUuid: String, message: String, channel: String): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("playerUuid", playerUuid)
        jsonObject.addProperty("message", message)
        jsonObject.addProperty("channel", channel)
        
        val request = Request.Builder()
            .url("$baseUrl/api/chat/logs")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error logging chat message: ${e.message}")
            false
        }
    }
    
    // 채팅 로그 조회
    fun getChatLogs(playerUuid: String? = null, channel: String? = null, limit: Int = 100): JsonObject? {
        val urlBuilder = StringBuilder("$baseUrl/api/chat/logs?limit=$limit")
        playerUuid?.let { urlBuilder.append("&playerUuid=$it") }
        channel?.let { urlBuilder.append("&channel=$it") }
        
        val request = Request.Builder()
            .url(urlBuilder.toString())
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting chat logs: ${e.message}")
            null
        }
    }
    
    // === 이벤트 관리 시스템 ===
    
    // 게임 이벤트 생성
    fun createGameEvent(title: String, description: String, startTime: Long, endTime: Long, maxParticipants: Int? = null): JsonObject? {
        val jsonObject = JsonObject()
        jsonObject.addProperty("title", title)
        jsonObject.addProperty("description", description)
        jsonObject.addProperty("startTime", startTime)
        jsonObject.addProperty("endTime", endTime)
        maxParticipants?.let { jsonObject.addProperty("maxParticipants", it) }
        
        val request = Request.Builder()
            .url("$baseUrl/api/events")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    plugin.logger.warning("Failed to create game event: ${response.code}")
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error creating game event: ${e.message}")
            null
        }
    }
    
    // 이벤트 참가 등록
    fun registerEventParticipant(eventId: String, playerUuid: String): Boolean {
        val jsonObject = JsonObject()
        jsonObject.addProperty("playerUuid", playerUuid)
        
        val request = Request.Builder()
            .url("$baseUrl/api/events/$eventId/participants")
            .post(gson.toJson(jsonObject).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error registering event participant: ${e.message}")
            false
        }
    }
    
    // 이벤트 보상 지급
    fun distributeEventRewards(eventId: String, rewards: JsonObject): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/api/events/$eventId/rewards")
            .post(gson.toJson(rewards).toRequestBody("application/json".toMediaType()))
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error distributing event rewards: ${e.message}")
            false
        }
    }
    
    // 현재 진행 중인 이벤트 조회
    fun getActiveEvents(): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/events/active")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting active events: ${e.message}")
            null
        }
    }
    
    // 이벤트 상세 정보 조회
    fun getEventDetails(eventId: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/events/$eventId")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting event details: ${e.message}")
            null
        }
    }
    
    fun getAuthRecord(authCode: String): Database.AuthRecord? {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/code/validate?authCode=$authCode")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                val result = gson.fromJson(body, JsonObject::class.java)
                
                Database.AuthRecord(
                    uuid = result.get("uuid").asString,
                    isAuth = result.get("isAuth").asBoolean
                )
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting auth record: ${e.message}")
            null
        }
    }
    
    fun getPlayerByUuid(uuid: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/players/$uuid")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting player by UUID: ${e.message}")
            null
        }
    }
    
    fun updatePlayerDiscordId(uuid: String, discordId: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/players/$uuid/discord?discordId=$discordId")
            .put("".toRequestBody())
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error updating player discord ID: ${e.message}")
            null
        }
    }
    
    fun getSettingValue(settingType: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/settings/$settingType")
            .get()
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error getting setting value: ${e.message}")
            null
        }
    }
    
    fun updateSetting(settingType: String, settingValue: String): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/settings/$settingType?settingValue=$settingValue")
            .put("".toRequestBody())
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                
                val body = response.body?.string() ?: return null
                gson.fromJson(body, JsonObject::class.java)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error updating setting: ${e.message}")
            null
        }
    }
}

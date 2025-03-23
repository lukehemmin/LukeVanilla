package com.lukehemmin.lukeVanilla.System.API

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lukehemmin.lukeVanilla.Main
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
}

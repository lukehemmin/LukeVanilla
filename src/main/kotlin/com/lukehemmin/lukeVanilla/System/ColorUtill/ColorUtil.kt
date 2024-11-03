package com.lukehemmin.lukeVanilla.System.ColorUtill

import java.awt.Color
import java.util.regex.Pattern

object ColorUtil {
    private val hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val colorCodePattern = Pattern.compile("&([0-9a-fk-or])")

    /**
     * HEX 컬러 코드를 마인크래프트 채팅 형식으로 변환합니다.
     * 예시: &#FFFFFF -> §x§f§f§f§f§f§f
     */
    fun String.translateHexColorCodes(): String {
        val matcher = hexPattern.matcher(this)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val hexCode = matcher.group(1)
            val replacement = buildString {
                append("§x")
                hexCode.forEach { append("§$it") }
            }
            matcher.appendReplacement(buffer, replacement)
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * RGB 값을 HEX 컬러 코드로 변환합니다.
     */
    fun rgbToHex(r: Int, g: Int, b: Int): String =
        "&#%02X%02X%02X".format(r, g, b)

    /**
     * Color 객체를 HEX 컬러 코드로 변환합니다.
     */
    fun Color.toHex(): String =
        rgbToHex(red, green, blue)

    /**
     * 표준 마인크래프트 색상 코드를 변환합니다.
     * 예시: &c -> §c
     */
    fun String.translateColorCodes(): String {
        return this.replace(colorCodePattern.toRegex(), "§$1")
    }

    /**
     * 그라데이션 효과를 만듭니다.
     */
    fun createGradient(text: String, from: Color, to: Color): String =
        buildString {
            text.forEachIndexed { index, char ->
                val ratio = index.toFloat() / (text.length - 1)
                val r = (from.red * (1 - ratio) + to.red * ratio).toInt()
                val g = (from.green * (1 - ratio) + to.green * ratio).toInt()
                val b = (from.blue * (1 - ratio) + to.blue * ratio).toInt()
                append(rgbToHex(r, g, b))
                append(char)
            }
        }

    /**
     * 채팅 메시지에서 플레이스홀더를 변환합니다.
     */
    fun formatMessage(
        format: String,
        colors: Map<String, String>,
        vararg args: Any?
    ): String {
        var message = format.format(*args)

        colors.forEach { (key, value) ->
            val colorCode = if (value.startsWith("#")) "&#${value.substring(1)}" else value
            message = message.replace("{$key}", colorCode)
        }

        return message.translateHexColorCodes()
    }

    /**
     * 간단한 메시지 포맷팅을 위한 편의 메소드
     */
    fun formatColoredMessage(
        message: String,
        vararg colorAndText: Pair<String, String>
    ): String {
        var result = message

        colorAndText.forEachIndexed { index, (color, text) ->
            val placeholder = "{$index}"
            if (message.contains(placeholder)) {
                val colorCode = if (color.startsWith("#")) "&#${color.substring(1)}" else color
                result = result.replace(placeholder, "$colorCode$text")
            }
        }

        return result.translateHexColorCodes()
    }
}

/**
 * // 기본 사용법
 * val message = "&#FFA500$playerName &#82E0AA님 반가워요!"
 * player.sendMessage(message.translateHexColorCodes())
 *
 * // RGB를 HEX로 변환
 * val hexColor = ColorUtil.rgbToHex(255, 165, 0) // &#FFA500 반환
 *
 * // 그라데이션 효과
 * val gradientText = ColorUtil.createGradient(
 *     text = "그라데이션 텍스트",
 *     from = Color(255, 0, 0),  // 빨강
 *     to = Color(0, 0, 255)     // 파랑
 * )
 *
 * // formatColoredMessage 사용 (Pair 활용)
 * val formattedMessage = ColorUtil.formatColoredMessage(
 *     "{0} {1}님 반가워요!",
 *     "#FFA500" to playerName,
 *     "#82E0AA" to "님 반가워요"
 * )
 *
 * // formatMessage 사용
 * val colors = mapOf(
 *     "nameColor" to "#FFA500",
 *     "textColor" to "#82E0AA"
 * )
 *
 * val formattedMessage = ColorUtil.formatMessage(
 *     "{nameColor}%s {textColor}님 반가워요!",
 *     colors,
 *     playerName
 * )
 */
package com.lukehemmin.lukeVanilla.System.Discord

import java.util.regex.Pattern

object EmojiUtil {
    
    /**
     * 이모티콘을 사용자 정의 포맷으로 변환하는 함수
     */
    fun replaceEmojis(text: String): String {
        // 텍스트 기반 이모지 패턴 (예: :_l:, :_g: 등)
        val textEmojiPattern = Pattern.compile(":[_a-z]+:|:naanhae:|:hi:|:notme:|:ing:")
        var result = text

        // 텍스트 기반 이모지 변환
        val matcher = textEmojiPattern.matcher(result)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val emoji = matcher.group()
            val replacement = convertEmojiToAlias(emoji)
            matcher.appendReplacement(buffer, replacement)
        }
        matcher.appendTail(buffer)

        return buffer.toString()
    }

    /**
     * 이모티콘을 사용자 정의 이름으로 매핑하는 함수
     */
    fun convertEmojiToAlias(emoji: String): String {
        return when (emoji) {
            ":_gi:" -> "௛" // no_entry
            ":_p:" -> "௕" // ppeotti_emoji
            ":_yu:" -> "௘" // face_with_symbols_on_mouth
            ":_pa:" -> "௖" // pangdoro_emoji
            ":_n:" -> "௔" // numbora_emoji
            ":_g:" -> "ௗ" // gam_emoji
            ":_y:" -> "௙" // yousu_emoji
            ":_s:" -> "௚" // soonback_emoji
            ":_m:" -> "௓" // maolo_emoji
            ":_z:" -> "௒" // zuri_emoji
            ":_f:" -> "௑" // fish_emoji
            ":_l:" -> "ௐ" // hemmin_emoji
            ":d_:" -> "ꐤ" // dolbe_emoji
            ":_pu:" -> "ꐥ" // pumkin_emoji
            ":naanhae:" -> "ꐠ" // hyeok_emoji
            ":hi:" -> "ꐡ" // yeong_emoji
            ":notme:" -> "ꐢ" // dmddoo_emoji
            ":ing:" -> "ꐣ" // kimjeokhan_emoji
            ":kkk:" -> "ꑨ" // dorahee_emoji
            ":luckini:" -> "ꑪ" // luckini_emoji
            ":no:" -> "ꑫ" // karon_emoji
            ":yes:" -> "ꑬ" // nlris_emoji
            ":mahot:" -> "ꑭ" // dubu_emoji
            ":mong:" -> "ꑳ" // mongle_emoji
            ":meong:" -> "꒐" // meongbyeol_emoji
            else -> emoji
        }
    }
}
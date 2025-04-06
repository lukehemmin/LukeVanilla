package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import org.bukkit.ChatColor

enum class EventType(
    val displayName: String,
    val dbTablePrefix: String,
    val guiColor: ChatColor,
    val guiTitle: String
) {
    HALLOWEEN("할로윈", "Halloween_Item", ChatColor.DARK_PURPLE, "할로윈 아이템"),
    CHRISTMAS("크리스마스", "Christmas_Item", ChatColor.DARK_GREEN, "크리스마스 아이템"),
    VALENTINE("발렌타인", "Valentine_Item", ChatColor.LIGHT_PURPLE, "발렌타인 아이템");

    companion object {
        fun fromString(type: String): EventType? {
            return values().find { it.name.equals(type, ignoreCase = true) || it.displayName == type }
        }
        
        fun getTabCompletions(): List<String> {
            return values().map { it.displayName }
        }
    }
} 
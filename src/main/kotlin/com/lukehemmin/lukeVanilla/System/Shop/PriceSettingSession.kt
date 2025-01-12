// PriceSettingSession.kt
package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.entity.Player

data class PriceSettingSession(
    val player: Player,
    val shop: Shop,
    val slot: Int,
    val settingBuyPrice: Boolean
)

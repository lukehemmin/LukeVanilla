package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class ItemRestoreLogger(
    private val database: Database,
    private val plugin: JavaPlugin,
    private val jda: JDA
) {
    private val channelId: String
    private val logMessages = mutableListOf<String>()

    init {
        channelId = database.getSettingValue("RestoreItemInfo") ?: ""
        if (channelId.isEmpty()) {
            plugin.logger.warning("RestoreItemInfo 설정이 올바르지 않습니다. 아이템 복구 로그가 전송되지 않습니다.")
        }
    }

    fun startNewLog() {
        logMessages.clear()
    }

    fun logRestoredItem(player: Player, oldItem: ItemStack, newItem: ItemStack, oraxenId: String) {
        val itemName = newItem.itemMeta?.let { meta ->
            if (meta.hasDisplayName()) {
                meta.displayName()?.let { displayName ->
                    PlainTextComponentSerializer.plainText().serialize(displayName)
                } ?: newItem.type.toString()
            } else {
                newItem.type.toString()
            }
        } ?: newItem.type.toString()

        val enchants = newItem.itemMeta?.enchants?.map { (enchant, level) ->
            "${enchant.key.key} ${level}"
        }?.joinToString(", ") ?: ""

        val itemLog = StringBuilder()
        itemLog.append("플레이어: ${player.name} (${player.uniqueId})\n")
        itemLog.append("복구된 아이템: $itemName\n")
        itemLog.append("Oraxen ID: $oraxenId\n")

        if (enchants.isNotEmpty()) {
            itemLog.append("인첸트: $enchants\n")
        }

        // ItemRestoreLogger.kt의 형판 정보 추가 부분 수정
        if (newItem.itemMeta is org.bukkit.inventory.meta.ArmorMeta) {
            val armorMeta = newItem.itemMeta as org.bukkit.inventory.meta.ArmorMeta
            if (armorMeta.hasTrim()) {
                val trim = armorMeta.trim
                trim?.let {
                    itemLog.append("형판: ${it.getPattern().toString().lowercase()} (재료: ${it.getMaterial().toString().lowercase()})\n")
                }
            }
        }

        itemLog.append("수량: ${newItem.amount}\n")
        itemLog.append("─────────────")

        logMessages.add(itemLog.toString())
    }

    fun sendLog(count: Int) {
        if (channelId.isEmpty() || logMessages.isEmpty()) return

        val channel = jda.getTextChannelById(channelId) ?: return

        val finalLog = StringBuilder()
        finalLog.append("--------총 ${count}개의 아이템이 복구되었습니다 --------\n")
        finalLog.append(logMessages.joinToString("\n"))

        channel.sendMessage(finalLog.toString()).queue()
        logMessages.clear()
    }
}
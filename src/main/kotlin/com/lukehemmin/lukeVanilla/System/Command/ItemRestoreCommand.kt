package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.System.Discord.ItemRestoreLogger
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemRestoreCommand(private val itemRestoreLogger: ItemRestoreLogger) : CommandExecutor, Listener {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        return restorePlayerItems(sender)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        restorePlayerItems(event.player)
    }

    private fun restorePlayerItems(player: Player): Boolean {
        var restoredCount = 0
        itemRestoreLogger.startNewLog()

        for (i in 0 until 36) {
            val item = player.inventory.getItem(i) ?: continue
            if (item.type.isAir) continue

            if (tryRestoreItem(item, player, i)) {
                restoredCount++
            }
        }

        if (restoredCount > 0) {
            player.sendMessage(Component.text("총 ${restoredCount}개의 아이템이 복구되었습니다.").color(NamedTextColor.GREEN))
            itemRestoreLogger.sendLog(restoredCount)
        }

        return true
    }

    private fun tryRestoreItem(item: ItemStack, player: Player, slot: Int): Boolean {
        val meta = item.itemMeta ?: return false

        if (meta.hasItemModel()) {
            val modelData = meta.itemModel.toString()
            if (modelData.indexOf("oraxen") != -1) {
                val oraxenId = modelData.split(":").lastOrNull() ?: return false

                val nexoItem = NexoItems.itemFromId(oraxenId)?.build() ?: return false
                // 기존 이름 복사
                val newMeta = nexoItem.itemMeta
                if (meta.hasDisplayName()) {
                    newMeta.displayName(meta.displayName())
                }

                // 인첸트 복사
                meta.enchants.forEach { (enchant, level) ->
                    newMeta.addEnchant(enchant, level, true)
                }

                // 대장장이 형판 업그레이드 복사
                if (meta is org.bukkit.inventory.meta.ArmorMeta) {
                    val newArmorMeta = newMeta as? org.bukkit.inventory.meta.ArmorMeta
                    if (newArmorMeta != null && meta.hasTrim()) {
                        newArmorMeta.trim = meta.trim
                    }
                }

                nexoItem.itemMeta = newMeta
                nexoItem.amount = item.amount

                player.inventory.setItem(slot, nexoItem)
                // 로그 전송
                itemRestoreLogger.logRestoredItem(player, item, nexoItem, oraxenId)

                return true
            }
        }
        return false
    }
}
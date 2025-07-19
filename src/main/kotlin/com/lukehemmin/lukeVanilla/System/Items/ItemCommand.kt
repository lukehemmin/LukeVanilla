package com.lukehemmin.lukeVanilla.System.Items

import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ItemCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return true
        }

        val player = sender
        
        // 인수가 없을 경우 처리
        if (args.isEmpty()) {
            player.sendMessage("Usage: /item <setname|adddesc|removedesc|cleardesc|info> [args]")
            return true
        }
        
        val item = player.inventory.itemInMainHand
        
        // AIR 타입 아이템 체크
        if (item.type == Material.AIR) {
            player.sendMessage("You must be holding an item to use this command.")
            return true
        }

        val meta = item.itemMeta ?: run {
            player.sendMessage("This item doesn't support metadata operations.")
            return true
        }

        when (args[0].lowercase()) {
            "setname" -> {
                if (args.size < 2) {
                    player.sendMessage("Usage: /item setname <name>")
                    return true
                }
                val name = args.drop(1).joinToString(" ").translateColorCodes()
                meta.setDisplayName(name)
                player.sendMessage("Item name set to $name")
            }
            "adddesc" -> {
                if (args.size < 2) {
                    player.sendMessage("Usage: /item adddesc <description>")
                    return true
                }
                val description = args.drop(1).joinToString(" ").translateColorCodes()
                val lore = meta.lore ?: mutableListOf()
                lore.add(description)
                meta.lore = lore
                player.sendMessage("Added description: $description")
            }
            "removedesc" -> {
                if (args.size != 2) {
                    player.sendMessage("Usage: /item removedesc <line>")
                    return true
                }
                val line = args[1].toIntOrNull()
                if (line == null || line < 1 || meta.lore == null || line > meta.lore!!.size) {
                    player.sendMessage("Invalid line number.")
                    return true
                }
                val lore = meta.lore!!
                lore.removeAt(line - 1)
                meta.lore = lore
                player.sendMessage("Removed description at line $line")
            }
            "cleardesc" -> {
                meta.lore = null
                player.sendMessage("Cleared all descriptions.")
            }
            "info" -> {
                player.performCommand("data get entity ${player.name} SelectedItem")
            }
            else -> {
                player.sendMessage("Unknown subcommand. Use /item setname, /item adddesc, /item removedesc, /item cleardesc, or /item info.")
            }
        }

        item.itemMeta = meta
        return true
    }
}
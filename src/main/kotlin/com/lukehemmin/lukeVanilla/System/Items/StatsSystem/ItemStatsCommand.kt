package com.lukehemmin.lukeVanilla.System.Items.StatsSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.text.DecimalFormat

/**
 * 아이템 통계 명령어 처리기
 * /아이템정보 - 현재 들고 있는 아이템의 킬 카운트 정보를 조회합니다.
 */
class ItemStatsCommand(private val plugin: Main) : CommandExecutor, TabCompleter {

    private val statsManager = plugin.statsSystem.getStatsManager()
    private val formatter = DecimalFormat("#,###.##")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            player.sendMessage("${ChatColor.RED}통계 정보를 확인하려면 아이템을 손에 들고 있어야 합니다.")
            return true
        }

        // 통계 지원 아이템 확인 (바닐라 또는 Nexo)
        if (isTrackableSpecificItem(item.type) || plugin.statsSystem.isTrackableNexoItem(item)) {
            showItemStats(player, item)
        } else {
            player.sendMessage("${ChatColor.RED}이 아이템은 통계 정보를 지원하지 않습니다.")
            player.sendMessage("${ChatColor.YELLOW}통계 지원 아이템: 다이아몬드/네더라이트 도구 및 무기, 특정 Nexo 커스텀 아이템")
        }

        return true
    }

    private fun showItemStats(player: Player, item: ItemStack) {
        val mobsKilled = statsManager.getMobsKilled(item)
        val playersKilled = statsManager.getPlayersKilled(item)

        // Nexo 아이템 여부 확인 및 ID 가져오기
        val nexoItemId = try {
            val nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val method = nexoClass.getDeclaredMethod("idFromItem", ItemStack::class.java)
            method.invoke(null, item) as? String
        } catch (e: Exception) {
            null
        }

        player.sendMessage("${ChatColor.GOLD}=== ${ChatColor.WHITE}아이템 킬 통계 정보${ChatColor.GOLD} ===")
        
        // Nexo 아이템인 경우 ID 표시
        if (nexoItemId != null) {
            player.sendMessage("${ChatColor.LIGHT_PURPLE}Nexo 아이템 ID: ${ChatColor.WHITE}$nexoItemId")
        }
        
        player.sendMessage("${ChatColor.YELLOW}처치한 몹: ${ChatColor.WHITE}${formatter.format(mobsKilled)}")
        player.sendMessage("${ChatColor.YELLOW}처치한 플레이어: ${ChatColor.WHITE}${formatter.format(playersKilled)}")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        // 이 명령어는 탭 완성이 없음
        return listOf()
    }

    // 도구인지 확인
    private fun isTool(type: Material): Boolean {
        return type.name.endsWith("_PICKAXE") ||
               type.name.endsWith("_AXE") ||
               type.name.endsWith("_SHOVEL") ||
               type.name.endsWith("_HOE")
    }
    
    // 무기인지 확인
    private fun isWeapon(type: Material): Boolean {
        return type.name.endsWith("_SWORD") ||
               type.name.endsWith("_AXE") ||
               type == Material.BOW ||
               type == Material.CROSSBOW ||
               type == Material.TRIDENT
    }
    
    // 방어구인지 확인
    private fun isArmor(type: Material): Boolean {
        return type.name.endsWith("_HELMET") ||
               type.name.endsWith("_CHESTPLATE") ||
               type.name.endsWith("_LEGGINGS") ||
               type.name.endsWith("_BOOTS") ||
               type == Material.SHIELD
    }

    // 특정 추적 가능 아이템인지 확인 (다이아몬드 및 네더라이트 도구/무기만)
    private fun isTrackableSpecificItem(type: Material): Boolean {
        return plugin.statsSystem.isTrackableSpecificItem(type)
    }
}
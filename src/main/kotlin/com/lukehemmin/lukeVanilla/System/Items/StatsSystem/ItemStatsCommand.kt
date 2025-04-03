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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 아이템 통계 명령어 처리기
 * /아이템정보 - 현재 들고 있는 아이템의 통계 정보를 조회합니다.
 */
class ItemStatsCommand(private val plugin: Main) : CommandExecutor, TabCompleter {

    private val statsManager = plugin.statsSystem.getStatsManager()
    private val formatter = DecimalFormat("#,###.##")
    private val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

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

        // 아이템 종류에 따라 다른 정보 표시
        if (isTool(item.type) || isWeapon(item.type)) {
            showToolStats(player, item)
        } else if (isArmor(item.type)) {
            showArmorStats(player, item)
        } else if (item.type == Material.ELYTRA) {
            showElytraStats(player, item)
        } else {
            player.sendMessage("${ChatColor.RED}이 아이템에는 통계 정보가 없습니다.")
        }

        return true
    }

    private fun showToolStats(player: Player, item: ItemStack) {
        val creator = statsManager.getCreator(item)
        val creationDate = statsManager.getCreationDate(item)
        val blocksMined = statsManager.getBlocksMined(item)
        val mobsKilled = statsManager.getMobsKilled(item)
        val playersKilled = statsManager.getPlayersKilled(item)
        val damageDealt = statsManager.getDamageDealt(item)

        player.sendMessage("${ChatColor.GOLD}=== ${ChatColor.WHITE}아이템 통계 정보${ChatColor.GOLD} ===")
        
        if (creator != null) {
            val creatorName = Bukkit.getOfflinePlayer(creator).name ?: "알 수 없음"
            player.sendMessage("${ChatColor.YELLOW}제작자: ${ChatColor.WHITE}$creatorName")
        }
        
        if (creationDate != null) {
            player.sendMessage("${ChatColor.YELLOW}제작일: ${ChatColor.WHITE}${creationDate.format(dateFormatter)}")
        }
        
        player.sendMessage("${ChatColor.YELLOW}채굴한 블록: ${ChatColor.WHITE}${formatter.format(blocksMined)}")
        player.sendMessage("${ChatColor.YELLOW}처치한 몹: ${ChatColor.WHITE}${formatter.format(mobsKilled)}")
        player.sendMessage("${ChatColor.YELLOW}처치한 플레이어: ${ChatColor.WHITE}${formatter.format(playersKilled)}")
        player.sendMessage("${ChatColor.YELLOW}입힌 데미지: ${ChatColor.WHITE}${formatter.format(damageDealt)}")
    }

    private fun showArmorStats(player: Player, item: ItemStack) {
        val creator = statsManager.getCreator(item)
        val creationDate = statsManager.getCreationDate(item)
        val damageBlocked = statsManager.getDamageBlocked(item)

        player.sendMessage("${ChatColor.GOLD}=== ${ChatColor.WHITE}방어구 통계 정보${ChatColor.GOLD} ===")
        
        if (creator != null) {
            val creatorName = Bukkit.getOfflinePlayer(creator).name ?: "알 수 없음"
            player.sendMessage("${ChatColor.YELLOW}제작자: ${ChatColor.WHITE}$creatorName")
        }
        
        if (creationDate != null) {
            player.sendMessage("${ChatColor.YELLOW}제작일: ${ChatColor.WHITE}${creationDate.format(dateFormatter)}")
        }
        
        player.sendMessage("${ChatColor.YELLOW}방어한 데미지: ${ChatColor.WHITE}${formatter.format(damageBlocked)}")
    }

    private fun showElytraStats(player: Player, item: ItemStack) {
        val firstOwner = statsManager.getFirstOwner(item)
        val obtainedDate = statsManager.getObtainedDate(item)
        val distanceFlown = statsManager.getDistanceFlown(item)

        player.sendMessage("${ChatColor.GOLD}=== ${ChatColor.WHITE}겉날개 통계 정보${ChatColor.GOLD} ===")
        
        if (firstOwner != null) {
            val ownerName = Bukkit.getOfflinePlayer(firstOwner).name ?: "알 수 없음"
            player.sendMessage("${ChatColor.YELLOW}최초 소유자: ${ChatColor.WHITE}$ownerName")
        }
        
        if (obtainedDate != null) {
            player.sendMessage("${ChatColor.YELLOW}획득일: ${ChatColor.WHITE}${obtainedDate.format(dateFormatter)}")
        }
        
        // 1000m 이상이면 km 단위로 표시
        if (distanceFlown >= 1000) {
            val distanceKm = distanceFlown / 1000
            player.sendMessage("${ChatColor.YELLOW}비행 거리: ${ChatColor.WHITE}${formatter.format(distanceKm)} km")
        } else {
            player.sendMessage("${ChatColor.YELLOW}비행 거리: ${ChatColor.WHITE}${formatter.format(distanceFlown)} m")
        }
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
} 
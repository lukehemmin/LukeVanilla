package com.lukehemmin.lukeVanilla.System.PlayTime

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

/**
 * 플레이타임 관련 명령어를 처리하는 클래스
 */
class PlayTimeCommand(
    private val playTimeManager: PlayTimeManager
) : CommandExecutor, TabCompleter {
    
    private val plugin = playTimeManager.plugin
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) {
                showPlayerPlayTime(sender, sender)
            } else {
                sender.sendMessage("콘솔에서는 플레이어 이름을 지정해야 합니다.")
            }
            return true
        }
        
        when (args[0].lowercase()) {
            "확인", "조회" -> {
                if (args.size < 2) {
                    if (sender is Player) {
                        showPlayerPlayTime(sender, sender)
                    } else {
                        sender.sendMessage("사용법: /플레이타임 확인 <플레이어>")
                    }
                } else {
                    // 오프라인 플레이어 조회를 비동기로 처리
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        val targetPlayer = Bukkit.getPlayer(args[1]) ?: Bukkit.getOfflinePlayer(args[1])
                        if (targetPlayer.hasPlayedBefore() || targetPlayer.isOnline) {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                showPlayerPlayTime(sender, targetPlayer as? Player ?: targetPlayer)
                            })
                        } else {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                sender.sendMessage(Component.text("${args[1]} 플레이어를 찾을 수 없습니다.", NamedTextColor.RED))
                            })
                        }
                    })
                }
            }
            
            "순위", "랭킹" -> {
                if (!sender.hasPermission("playtime.admin.ranking")) {
                    sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED))
                    return true
                }
                // 순위 조회를 비동기로 처리
                sender.sendMessage(Component.text("플레이타임 순위를 조회하는 중...", NamedTextColor.YELLOW))
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    try {
                        val topPlayers = playTimeManager.getTopPlayTimeInfo(10)
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            showTopPlayTimesAsync(sender, topPlayers)
                        })
                    } catch (e: Exception) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            sender.sendMessage(Component.text("순위 조회 중 오류가 발생했습니다.", NamedTextColor.RED))
                        })
                    }
                })
            }
            
            
            "통계" -> {
                if (!sender.hasPermission("playtime.admin.stats")) {
                    sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED))
                    return true
                }
                // 통계 조회를 비동기로 처리
                sender.sendMessage(Component.text("플레이타임 통계를 조회하는 중...", NamedTextColor.YELLOW))
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    try {
                        val newPlayerCount = playTimeManager.getPlayerCountAboveDays(0) - playTimeManager.getPlayerCountAboveDays(7)
                        val veteranPlayerCount = playTimeManager.getPlayerCountAboveDays(7)
                        val totalPlayerCount = playTimeManager.getPlayerCountAboveDays(0)
                        
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            showStatsAsync(sender, totalPlayerCount, newPlayerCount, veteranPlayerCount)
                        })
                    } catch (e: Exception) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            sender.sendMessage(Component.text("통계 조회 중 오류가 발생했습니다.", NamedTextColor.RED))
                        })
                    }
                })
            }
            
            else -> {
                showUsage(sender)
            }
        }
        
        return true
    }
    
    private fun showPlayerPlayTime(sender: CommandSender, target: Any) {
        when (target) {
            is Player -> {
                val totalPlayTime = playTimeManager.getCurrentTotalPlayTime(target)
                val sessionTime = playTimeManager.getCurrentSessionTime(target)
                val isNewPlayer = playTimeManager.isNewPlayer(target)
                
                val message = Component.text()
                    .append(Component.text("■ ", NamedTextColor.GOLD))
                    .append(Component.text("${target.name}님의 플레이타임", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" ■", NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(Component.text("총 플레이타임: ", NamedTextColor.GRAY))
                    .append(Component.text(playTimeManager.formatPlayTime(totalPlayTime), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text("현재 세션: ", NamedTextColor.GRAY))
                    .append(Component.text(playTimeManager.formatPlayTime(sessionTime), NamedTextColor.GREEN))
                    .append(Component.newline())
                    .append(Component.text("상태: ", NamedTextColor.GRAY))
                    .append(
                        if (isNewPlayer) {
                            Component.text("신규 플레이어 (7일 미만)", NamedTextColor.YELLOW)
                        } else {
                            Component.text("베테랑 플레이어 (7일 이상)", NamedTextColor.GREEN)
                        }
                    )
                
                sender.sendMessage(message)
            }
            
            else -> {
                val offlinePlayer = target as org.bukkit.OfflinePlayer
                val totalPlayTime = playTimeManager.getSavedTotalPlayTime(offlinePlayer.uniqueId)
                val isNewPlayer = playTimeManager.isNewPlayer(offlinePlayer.uniqueId)
                
                val message = Component.text()
                    .append(Component.text("■ ", NamedTextColor.GOLD))
                    .append(Component.text("${offlinePlayer.name}님의 플레이타임", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" ■", NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(Component.text("총 플레이타임: ", NamedTextColor.GRAY))
                    .append(Component.text(playTimeManager.formatPlayTime(totalPlayTime), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text("상태: ", NamedTextColor.GRAY))
                    .append(Component.text("오프라인", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("분류: ", NamedTextColor.GRAY))
                    .append(
                        if (isNewPlayer) {
                            Component.text("신규 플레이어 (7일 미만)", NamedTextColor.YELLOW)
                        } else {
                            Component.text("베테랑 플레이어 (7일 이상)", NamedTextColor.GREEN)
                        }
                    )
                
                sender.sendMessage(message)
            }
        }
    }
    
    private fun showTopPlayTimes(sender: CommandSender) {
        val topPlayers = playTimeManager.getAllPlayTimeInfo().take(10)
        
        val message = Component.text()
            .append(Component.text("===== ", NamedTextColor.GOLD))
            .append(Component.text("플레이타임 순위", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GOLD))
        
        sender.sendMessage(message)
        
        topPlayers.forEachIndexed { index, playTimeInfo ->
            val playerName = Bukkit.getOfflinePlayer(playTimeInfo.playerUuid).name ?: "알 수 없음"
            val rankMessage = Component.text()
                .append(Component.text("${index + 1}. ", NamedTextColor.GOLD))
                .append(Component.text(playerName, NamedTextColor.AQUA))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(playTimeManager.formatPlayTime(playTimeInfo.totalPlaytimeSeconds), NamedTextColor.WHITE))
            
            sender.sendMessage(rankMessage)
        }
    }
    
    private fun showTopPlayTimesAsync(sender: CommandSender, topPlayers: List<PlayTimeInfo>) {
        val message = Component.text()
            .append(Component.text("===== ", NamedTextColor.GOLD))
            .append(Component.text("플레이타임 순위", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GOLD))
        
        sender.sendMessage(message)
        
        topPlayers.forEachIndexed { index, playTimeInfo ->
            val playerName = Bukkit.getOfflinePlayer(playTimeInfo.playerUuid).name ?: "알 수 없음"
            val rankMessage = Component.text()
                .append(Component.text("${index + 1}. ", NamedTextColor.GOLD))
                .append(Component.text(playerName, NamedTextColor.AQUA))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(playTimeManager.formatPlayTime(playTimeInfo.totalPlaytimeSeconds), NamedTextColor.WHITE))
            
            sender.sendMessage(rankMessage)
        }
    }
    
    
    private fun showStats(sender: CommandSender) {
        val newPlayerCount = playTimeManager.getPlayerCountAboveDays(0) - playTimeManager.getPlayerCountAboveDays(7)
        val veteranPlayerCount = playTimeManager.getPlayerCountAboveDays(7)
        val totalPlayerCount = playTimeManager.getPlayerCountAboveDays(0)
        
        val message = Component.text()
            .append(Component.text("===== ", NamedTextColor.GOLD))
            .append(Component.text("플레이타임 통계", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("총 플레이어 수: ", NamedTextColor.GRAY))
            .append(Component.text("$totalPlayerCount", NamedTextColor.AQUA))
            .append(Component.text("명", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("신규 플레이어 (7일 미만): ", NamedTextColor.GRAY))
            .append(Component.text("$newPlayerCount", NamedTextColor.YELLOW))
            .append(Component.text("명", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("베테랑 플레이어 (7일 이상): ", NamedTextColor.GRAY))
            .append(Component.text("$veteranPlayerCount", NamedTextColor.GREEN))
            .append(Component.text("명", NamedTextColor.GRAY))
        
        sender.sendMessage(message)
    }
    
    private fun showStatsAsync(sender: CommandSender, totalPlayerCount: Int, newPlayerCount: Int, veteranPlayerCount: Int) {
        val message = Component.text()
            .append(Component.text("===== ", NamedTextColor.GOLD))
            .append(Component.text("플레이타임 통계", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("총 플레이어 수: ", NamedTextColor.GRAY))
            .append(Component.text("$totalPlayerCount", NamedTextColor.AQUA))
            .append(Component.text("명", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("신규 플레이어 (7일 미만): ", NamedTextColor.GRAY))
            .append(Component.text("$newPlayerCount", NamedTextColor.YELLOW))
            .append(Component.text("명", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("베테랑 플레이어 (7일 이상): ", NamedTextColor.GRAY))
            .append(Component.text("$veteranPlayerCount", NamedTextColor.GREEN))
            .append(Component.text("명", NamedTextColor.GRAY))
        
        sender.sendMessage(message)
    }
    
    private fun showUsage(sender: CommandSender) {
        val message = Component.text()
            .append(Component.text("===== ", NamedTextColor.GOLD))
            .append(Component.text("플레이타임 명령어", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("/플레이타임", NamedTextColor.AQUA))
            .append(Component.text(" - 자신의 플레이타임 확인", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/플레이타임 확인 <플레이어>", NamedTextColor.AQUA))
            .append(Component.text(" - 특정 플레이어의 플레이타임 확인", NamedTextColor.GRAY))
        
        if (sender.hasPermission("playtime.admin")) {
            message
                .append(Component.newline())
                .append(Component.text("/플레이타임 순위", NamedTextColor.AQUA))
                .append(Component.text(" - 플레이타임 순위 확인", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("/플레이타임 통계", NamedTextColor.AQUA))
                .append(Component.text(" - 서버 플레이타임 통계", NamedTextColor.GRAY))
        }
        
        sender.sendMessage(message)
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            val suggestions = mutableListOf("확인", "조회")
            if (sender.hasPermission("playtime.admin")) {
                suggestions.addAll(listOf("순위", "랭킹", "통계"))
            }
            return suggestions.filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        
        if (args.size == 2 && args[0].lowercase() in listOf("확인", "조회")) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        
        return mutableListOf()
    }
}
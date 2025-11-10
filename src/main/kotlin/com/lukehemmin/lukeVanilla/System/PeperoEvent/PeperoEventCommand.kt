package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.logging.Logger

/**
 * 빼빼로 이벤트 명령어
 */
class PeperoEventCommand(
    private val plugin: Main,
    private val repository: PeperoEventRepository,
    private val gui: PeperoEventGUI,
    private val logger: Logger
) : CommandExecutor, TabCompleter {

    private val itemGiveDate = LocalDate.parse(plugin.config.getString("pepero_event.item_give_date", "2025-11-11"))

    /**
     * /빼빼로받기 명령어 처리
     */
    fun handlePeperoReceive(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val player = sender
        val uuid = player.uniqueId.toString()

        // 날짜 확인 (KST 기준 11월 11일만 가능)
        val today = LocalDate.now()
        if (today != itemGiveDate) {
            player.sendMessage("§c빼빼로 아이템은 11월 11일에만 받을 수 있습니다!")
            return true
        }

        // 비동기로 DB 확인
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val hasReceived = repository.hasReceivedItem(uuid)

            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (hasReceived) {
                    player.sendMessage("§c이미 빼빼로 아이템을 받으셨습니다!")
                } else {
                    // GUI 오픈
                    gui.openPeperoSelectionGUI(player)
                }
            })
        })

        return true
    }

    /**
     * /빼빼로관리 명령어 처리
     */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lukevanilla.pepero.admin")) {
            sender.sendMessage("§c권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "교환권추가" -> {
                if (args.size < 3) {
                    sender.sendMessage("§c사용법: /빼빼로관리 교환권추가 <교환권이름> <이미지URL>")
                    return true
                }
                val voucherName = args[1]
                val imageUrl = args[2]
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val success = repository.addVoucher(voucherName, imageUrl)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (success) {
                            sender.sendMessage("§a교환권이 추가되었습니다: $voucherName")
                        } else {
                            sender.sendMessage("§c교환권 추가 실패")
                        }
                    })
                })
            }

            "교환권확인" -> {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val count = repository.getUnsentVoucherCount()
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage("§a미발송 교환권 개수: §f$count§a개")
                    })
                })
            }

            "득표확인" -> {
                val limit = if (args.size > 1) args[1].toIntOrNull() ?: 10 else 10
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val topVoters = repository.getTopVoters(limit)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage("§6§l========== 득표 상위 ${limit}명 ==========")
                        topVoters.forEachIndexed { index, vote ->
                            sender.sendMessage("§f${index + 1}. §e${vote.playerName} §7- §a${vote.voteCount}표")
                        }
                    })
                })
            }

            "테스트gui" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c플레이어만 사용 가능합니다.")
                    return true
                }
                gui.openPeperoSelectionGUI(sender)
            }

            else -> {
                sendHelp(sender)
            }
        }

        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§6§l========== 빼빼로 관리 명령어 ==========")
        sender.sendMessage("§e/빼빼로관리 교환권추가 <이름> <이미지URL> §7- 교환권 추가")
        sender.sendMessage("§e/빼빼로관리 교환권확인 §7- 미발송 교환권 개수 확인")
        sender.sendMessage("§e/빼빼로관리 득표확인 [개수] §7- 득표 상위권 확인")
        sender.sendMessage("§e/빼빼로관리 테스트gui §7- GUI 테스트")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission("lukevanilla.pepero.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("교환권추가", "교환권확인", "득표확인", "테스트gui").filter {
                it.startsWith(args[0], ignoreCase = true)
            }
            else -> emptyList()
        }
    }
}

package com.lukehemmin.lukeVanilla.System.Roulette

import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * 룰렛 관리자 명령어
 * - /룰렛설정 npc - NPC 지정
 * - /룰렛설정 npc제거 - NPC 제거
 * - /룰렛설정 비용 <금액> - 비용 설정
 * - /룰렛설정 활성화 - 룰렛 활성화
 * - /룰렛설정 비활성화 - 룰렛 비활성화
 * - /룰렛설정 리로드 - 설정 리로드
 * - /룰렛설정 정보 - 현재 설정 확인
 */
class RouletteCommand(
    private val plugin: JavaPlugin,
    private val manager: RouletteManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // 권한 확인
        if (!sender.hasPermission("lukevanilla.roulette.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "npc지정" -> handleNpcCommand(sender)
            "npc제거" -> handleNpcRemoveCommand(sender)
            "비용" -> handleCostCommand(sender, args)
            "활성화" -> handleEnableCommand(sender, true)
            "비활성화" -> handleEnableCommand(sender, false)
            "리로드" -> handleReloadCommand(sender)
            "정보" -> handleInfoCommand(sender)
            else -> sendHelp(sender)
        }

        return true
    }

    /**
     * NPC 지정 명령어
     */
    private fun handleNpcCommand(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return
        }

        // Citizens 플러그인이 있는지 확인
        val citizensPlugin = plugin.server.pluginManager.getPlugin("Citizens")
        if (citizensPlugin == null || !citizensPlugin.isEnabled) {
            sender.sendMessage("§cCitizens 플러그인이 필요합니다.")
            return
        }

        try {
            // 플레이어가 바라보고 있는 엔티티 찾기 (레이캐스팅)
            val targetEntity = sender.getTargetEntity(5) // 5블록 이내

            if (targetEntity == null) {
                sender.sendMessage("§c바라보고 있는 NPC가 없습니다.")
                sender.sendMessage("§7NPC를 바라본 상태로 다시 시도해주세요.")
                return
            }

            // Citizens API를 통해 NPC 레지스트리 가져오기
            val npcRegistry = CitizensAPI.getNPCRegistry()

            // NPC인지 확인
            if (!npcRegistry.isNPC(targetEntity)) {
                sender.sendMessage("§c바라보고 있는 대상이 NPC가 아닙니다.")
                return
            }

            // NPC 객체 가져오기
            val npc: NPC = npcRegistry.getNPC(targetEntity) ?: run {
                sender.sendMessage("§cNPC 정보를 가져올 수 없습니다.")
                return
            }

            // NPC ID 및 이름 가져오기
            val npcId = npc.id
            val npcName = npc.name

            // 기존 NPC가 있는지 확인
            val oldNpcId = manager.getNpcId()

            // DB에 저장 (기존 NPC 대체)
            if (manager.setNpcId(npcId)) {
                if (oldNpcId != null) {
                    sender.sendMessage("§e기존 룰렛 NPC(ID: $oldNpcId)가 제거되고,")
                    sender.sendMessage("§a새로운 룰렛 NPC가 설정되었습니다!")
                } else {
                    sender.sendMessage("§a룰렛 NPC가 설정되었습니다!")
                }
                sender.sendMessage("§7- NPC 이름: §f$npcName")
                sender.sendMessage("§7- NPC ID: §f$npcId")
            } else {
                sender.sendMessage("§cNPC 설정에 실패했습니다.")
            }

        } catch (e: Exception) {
            sender.sendMessage("§cNPC 설정 중 오류가 발생했습니다: ${e.message}")
            plugin.logger.warning("[Roulette] NPC 설정 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * NPC 제거 명령어
     */
    private fun handleNpcRemoveCommand(sender: CommandSender) {
        if (manager.removeNpcId()) {
            sender.sendMessage("§a룰렛 NPC가 제거되었습니다.")
        } else {
            sender.sendMessage("§cNPC 제거에 실패했습니다.")
        }
    }

    /**
     * 비용 설정 명령어
     */
    private fun handleCostCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛설정 비용 <금액>")
            return
        }

        val amount = args[1].toDoubleOrNull()
        if (amount == null || amount < 0) {
            sender.sendMessage("§c올바른 금액을 입력해주세요.")
            return
        }

        if (manager.setCost(CostType.MONEY, amount)) {
            sender.sendMessage("§a룰렛 비용이 ${amount}원으로 설정되었습니다.")
        } else {
            sender.sendMessage("§c비용 설정에 실패했습니다.")
        }
    }

    /**
     * 활성화/비활성화 명령어
     */
    private fun handleEnableCommand(sender: CommandSender, enable: Boolean) {
        if (manager.setEnabled(enable)) {
            val status = if (enable) "활성화" else "비활성화"
            sender.sendMessage("§a룰렛이 ${status}되었습니다.")
        } else {
            sender.sendMessage("§c설정 변경에 실패했습니다.")
        }
    }

    /**
     * 리로드 명령어
     */
    private fun handleReloadCommand(sender: CommandSender) {
        manager.reload()
        sender.sendMessage("§a룰렛 설정이 리로드되었습니다.")
        sender.sendMessage("§7- 활성화된 아이템: §f${manager.getItems().size}개")
        sender.sendMessage("§7- NPC ID: §f${manager.getNpcId() ?: "미설정"}")
        sender.sendMessage("§7- 비용: §f${manager.getConfig()?.costAmount ?: 0}원")
    }

    /**
     * 정보 명령어
     */
    private fun handleInfoCommand(sender: CommandSender) {
        val config = manager.getConfig()

        sender.sendMessage("§e§l========== [ 룰렛 정보 ] ==========")
        sender.sendMessage("§7활성화: ${if (manager.isEnabled()) "§a예" else "§c아니오"}")
        sender.sendMessage("§7NPC ID: §f${manager.getNpcId() ?: "§c미설정"}")
        sender.sendMessage("§7비용 타입: §f${config?.costType?.name ?: "알 수 없음"}")
        sender.sendMessage("§7비용: §f${config?.costAmount ?: 0}원")
        sender.sendMessage("§7애니메이션 시간: §f${config?.animationDuration ?: 0}틱")
        sender.sendMessage("§7등록된 아이템: §f${manager.getItems().size}개")
        sender.sendMessage("§e§l===================================")

        // 아이템 목록 표시 (상위 5개)
        val items = manager.getItems().take(5)
        if (items.isNotEmpty()) {
            sender.sendMessage("§7아이템 목록 (상위 5개):")
            items.forEach { item ->
                val probability = (item.weight.toDouble() / manager.getItems().sumOf { it.weight } * 100)
                sender.sendMessage("  §f- ${item.itemDisplayName ?: item.itemIdentifier} " +
                        "§7x${item.itemAmount} (확률: §e${String.format("%.2f", probability)}%§7)")
            }
            if (manager.getItems().size > 5) {
                sender.sendMessage("  §7... 외 ${manager.getItems().size - 5}개")
            }
        }
    }

    /**
     * 도움말 표시
     */
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§e§l========== [ 룰렛 명령어 ] ==========")
        sender.sendMessage("§7/룰렛설정 npc지정 §f- NPC 지정 (NPC를 바라본 상태로 입력)")
        sender.sendMessage("§7/룰렛설정 npc제거 §f- NPC 제거")
        sender.sendMessage("§7/룰렛설정 비용 <금액> §f- 비용 설정")
        sender.sendMessage("§7/룰렛설정 활성화 §f- 룰렛 활성화")
        sender.sendMessage("§7/룰렛설정 비활성화 §f- 룰렛 비활성화")
        sender.sendMessage("§7/룰렛설정 리로드 §f- 설정 리로드")
        sender.sendMessage("§7/룰렛설정 정보 §f- 현재 설정 확인")
        sender.sendMessage("§e§l====================================")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission("lukevanilla.roulette.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("npc지정", "npc제거", "비용", "활성화", "비활성화", "리로드", "정보")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "비용" -> listOf("1000", "5000", "10000")
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

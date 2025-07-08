package com.lukehemmin.lukeVanilla.System.FarmVillage

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class FarmVillageCommand(private val manager: FarmVillageManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("farmvillage.admin")) {
            sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "땅설정" -> handleSetPlot(sender, args)
            "땅주기" -> handleAssignPlot(sender, args)
            "땅뺏기" -> handleConfiscatePlot(sender, args)
            else -> sendUsage(sender)
        }
        return true
    }
    
    private fun handleSetPlot(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return
        }
        if (args.size < 3) {
            sender.sendMessage(Component.text("사용법: /농사마을 땅설정 <땅번호> <청크번호>", NamedTextColor.RED))
            return
        }
        val plotNumber = args[1].toIntOrNull()
        val plotPart = args[2].toIntOrNull()

        if (plotNumber == null || plotPart == null) {
            sender.sendMessage(Component.text("땅 번호와 청크 번호는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        // confirmation flow
        if (args.size == 4 && args[3].equals("confirm", ignoreCase = true)) {
            manager.setPlot(plotNumber, plotPart, sender.location)
            sender.sendMessage(Component.text("✅ $plotNumber-$plotPart 번 농사 땅을 현재 위치로 덮어썼습니다.", NamedTextColor.GREEN))
            return
        }

        val existingPlot = manager.getPlotPart(plotNumber, plotPart)
        if (existingPlot != null) {
            sender.sendMessage(Component.text("⚠️ 이미 해당 위치에 설정된 땅이 있습니다. 덮어쓰시겠습니까?", NamedTextColor.YELLOW))
            
            val confirmCommand = "/${"농사마을"} 땅설정 $plotNumber $plotPart confirm"
            val cancelMessage = Component.text(" [취소]", NamedTextColor.GRAY, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("작업을 취소합니다.")))

            val confirmMessage = Component.text("[수정]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("클릭 시 현재 위치로 덮어씁니다.")))
                .clickEvent(ClickEvent.runCommand(confirmCommand))
            
            sender.sendMessage(confirmMessage.append(cancelMessage))
        } else {
            manager.setPlot(plotNumber, plotPart, sender.location)
            sender.sendMessage(Component.text("✅ $plotNumber-$plotPart 번 농사 땅을 현재 위치로 설정했습니다.", NamedTextColor.GREEN))
        }
    }

    private fun handleConfiscatePlot(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return
        }
        if (args.size < 2) {
            sender.sendMessage(Component.text("사용법: /농사마을 땅뺏기 <땅번호>", NamedTextColor.RED))
            return
        }
        val plotNumber = args[1].toIntOrNull()
        if (plotNumber == null) {
            sender.sendMessage(Component.text("땅 번호는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        val result = manager.confiscatePlot(plotNumber, sender)
        
        when (result) {
            ConfiscateResult.SUCCESS -> sender.sendMessage(Component.text("$plotNumber 번 농사 땅을 성공적으로 회수했습니다.", NamedTextColor.GREEN))
            ConfiscateResult.PARTIAL_SUCCESS -> sender.sendMessage(Component.text("$plotNumber 번 농사 땅의 일부만 회수했습니다. 서버 로그를 확인해주세요.", NamedTextColor.YELLOW))
            ConfiscateResult.FAILURE -> sender.sendMessage(Component.text("$plotNumber 번 농사 땅 회수에 실패했습니다. 해당 땅의 소유자가 없거나 권한을 확인해주세요.", NamedTextColor.RED))
            ConfiscateResult.PLOT_NOT_FOUND -> sender.sendMessage(Component.text("$plotNumber 번으로 설정된 농사 땅을 찾을 수 없습니다.", NamedTextColor.RED))
        }
    }

    private fun handleAssignPlot(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("사용법: /농사마을 땅주기 <플레이어>", NamedTextColor.RED))
            return
        }
        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("${args[1]} 플레이어를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }

        val (result, plotNumber) = manager.assignNextAvailablePlot(targetPlayer)
        
        when (result) {
            AssignResult.SUCCESS -> {
                val message = Component.text("${targetPlayer.name}님에게 ${plotNumber}번 농사 땅(청크 2개)을 지급했습니다.", NamedTextColor.GREEN)
                sender.sendMessage(message)
                targetPlayer.sendMessage(message)
            }
            AssignResult.ALL_PLOTS_TAKEN -> sender.sendMessage(Component.text("지급할 수 있는 빈 농사 땅이 없습니다.", NamedTextColor.YELLOW))
            AssignResult.NO_PLOTS_DEFINED -> sender.sendMessage(Component.text("설정된 농사 땅이 하나도 없습니다. 먼저 땅을 설정해주세요.", NamedTextColor.RED))
            AssignResult.FAILURE -> sender.sendMessage(Component.text("${plotNumber}번 땅 지급에 실패했습니다. 이미 소유주가 있는지 확인해주세요.", NamedTextColor.RED))
        }
    }
    
    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("--- 농사마을 관리 명령어 ---", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/농사마을 땅설정 <땅번호> <청크번호>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 땅주기 <플레이어>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 땅뺏기 <땅번호>", NamedTextColor.AQUA))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (!sender.hasPermission("farmvillage.admin")) return mutableListOf()

        if (args.size == 1) {
            return mutableListOf("땅설정", "땅주기", "땅뺏기").filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        
        if (args.size == 2 && args[0].lowercase() == "땅주기") {
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
        }

        return mutableListOf()
    }
} 
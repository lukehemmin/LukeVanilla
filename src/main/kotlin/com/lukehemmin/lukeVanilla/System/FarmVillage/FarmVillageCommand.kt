package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
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

class FarmVillageCommand(private val plugin: Main, private val manager: FarmVillageManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("farmvillage.admin")) {
            sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sendGeneralUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "땅주기" -> handleAssignPlot(sender, args)
            "땅뺏기" -> handleConfiscatePlot(sender, args)
            "시스템" -> handleSystemCommands(sender, args)
            "상점이용권한지급" -> handleGrantShopPermission(sender, args)
            else -> sendGeneralUsage(sender)
        }
        return true
    }

    private fun handleGrantShopPermission(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("사용법: /농사마을 상점이용권한지급 <플레이어>", NamedTextColor.RED))
            return
        }
        val playerName = args[1]
        val targetPlayer = Bukkit.getOfflinePlayer(playerName)

        if (!targetPlayer.hasPlayedBefore()) {
            sender.sendMessage(Component.text("플레이어 '$playerName'를 찾을 수 없거나, 서버에 접속한 기록이 없습니다.", NamedTextColor.RED))
            return
        }

        manager.grantShopPermission(targetPlayer).thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    sender.sendMessage(Component.text("${targetPlayer.name}님에게 농사마을 상점 이용 권한을 지급했습니다.", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("권한 지급 중 오류가 발생했습니다. 서버 로그를 확인해주세요.", NamedTextColor.RED))
                }
            })
        }
    }

    private fun handleSystemCommands(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sendSystemUsage(sender)
            return
        }
        when (args[1].lowercase()) {
            "땅설정" -> handleSetPlot(sender, args)
            "입주패키지수정" -> {
                if (sender is Player) {
                    manager.openPackageEditor(sender)
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "농사아이템교환상점위치지정" -> handleSetShopLocation(sender, args)
            "씨앗상인위치지정" -> {
                if (sender is Player) {
                    setShopLocation(sender, "seed_merchant", "씨앗 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "교환상인위치지정" -> {
                if (sender is Player) {
                    setShopLocation(sender, "exchange_merchant", "교환 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "장비상인위치지정" -> {
                if (sender is Player) {
                    setShopLocation(sender, "equipment_merchant", "장비 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "토양받기상인위치지정" -> {
                if (sender is Player) {
                    setShopLocation(sender, "soil_receive_merchant", "토양받기 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            else -> sendSystemUsage(sender)
        }
    }
    
    private fun handleSetPlot(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return
        }
        // args are now shifted, e.g., /farmvillage system setplot <num> <part>
        // args[0]=system, args[1]=setplot, args[2]=<num>, args[3]=<part>
        if (args.size < 4) {
            sender.sendMessage(Component.text("사용법: /농사마을 시스템 땅설정 <땅번호> <청크번호>", NamedTextColor.RED))
            return
        }
        val plotNumber = args[2].toIntOrNull()
        val plotPart = args[3].toIntOrNull()

        if (plotNumber == null || plotPart == null) {
            sender.sendMessage(Component.text("땅 번호와 청크 번호는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        // confirmation flow
        if (args.size == 5 && args[4].equals("confirm", ignoreCase = true)) {
            manager.setPlot(plotNumber, plotPart, sender.location)
            sender.sendMessage(Component.text("✅ $plotNumber-$plotPart 번 농사 땅을 현재 위치로 덮어썼습니다.", NamedTextColor.GREEN))
            return
        }

        val existingPlot = manager.getPlotPart(plotNumber, plotPart)
        if (existingPlot != null) {
            sender.sendMessage(Component.text("⚠️ 이미 해당 위치에 설정된 땅이 있습니다. 덮어쓰시겠습니까?", NamedTextColor.YELLOW))
            
            val confirmCommand = "/${"농사마을"} 시스템 땅설정 $plotNumber $plotPart confirm"
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

    private fun handleSetShopLocation(sender: CommandSender, args: Array<out String>) {
        // /farmvillage system setshop <world> <x> <y> <z>
        if (args.size < 6) {
            sender.sendMessage(Component.text("사용법: /농사마을 시스템 농사아이템교환상점위치지정 <world> <x> <y> <z>", NamedTextColor.RED))
            return
        }
        val world = args[2]
        val x = args[3].toIntOrNull()
        val y = args[4].toIntOrNull()
        val z = args[5].toIntOrNull()

        if (x == null || y == null || z == null) {
            sender.sendMessage(Component.text("좌표는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }
        
        // For now, we use a fixed shopId. This could be an argument later.
        val shopId = "farm_item_exchange"
        manager.setShopLocation(shopId, world, x, y, z)
        sender.sendMessage(Component.text("농사 아이템 교환 상점 위치를 ($world, $x, $y, $z)로 설정했습니다.", NamedTextColor.GREEN))
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
                // The giving of the package is handled inside the manager
            }
            AssignResult.ALL_PLOTS_TAKEN -> sender.sendMessage(Component.text("지급할 수 있는 빈 농사 땅이 없습니다.", NamedTextColor.YELLOW))
            AssignResult.NO_PLOTS_DEFINED -> sender.sendMessage(Component.text("설정된 농사 땅이 하나도 없습니다. 먼저 땅을 설정해주세요.", NamedTextColor.RED))
            AssignResult.FAILURE -> sender.sendMessage(Component.text("${plotNumber}번 땅 지급에 실패했습니다. 이미 소유주가 있는지 확인해주세요.", NamedTextColor.RED))
        }
    }
    
    private fun sendGeneralUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("--- 농사마을 관리 명령어 ---", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/농사마을 땅주기 <플레이어>", NamedTextColor.AQUA).append(Component.text(" - 입주 선물이 지급됩니다.", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사마을 땅뺏기 <땅번호>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 상점이용권한지급 <플레이어>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템", NamedTextColor.GRAY).append(Component.text(" - 시스템 설정 보기")))
    }
    
    private fun sendSystemUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("--- 농사마을 시스템 설정 ---", NamedTextColor.RED))
        sender.sendMessage(Component.text("/농사마을 시스템 땅설정 <땅번호> <청크번호>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 입주패키지수정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 농사아이템교환상점위치지정 <world> <x> <y> <z>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 씨앗상인위치지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 교환상인위치지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 장비상인위치지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 토양받기상인위치지정", NamedTextColor.AQUA))
    }

    private fun setShopLocation(player: Player, shopId: String, shopName: String) {
        val targetBlock = player.getTargetBlockExact(5)
        if (targetBlock == null || targetBlock.type.isAir) {
            player.sendMessage(Component.text("블록을 바라보고 명령어를 사용해주세요.", NamedTextColor.RED))
            return
        }
        val location = targetBlock.location
        manager.setShopLocation(shopId, location.world.name, location.blockX, location.blockY, location.blockZ)
        player.sendMessage(Component.text("$shopName 위치를 현재 바라보는 블록으로 지정했습니다.", NamedTextColor.GREEN))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (!sender.hasPermission("farmvillage.admin")) return mutableListOf()

        if (args.size == 1) {
            return mutableListOf("땅주기", "땅뺏기", "시스템", "상점이용권한지급").filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        
        if (args.size == 2) {
            when (args[0].lowercase()) {
                "땅주기", "상점이용권한지급" -> return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                "시스템" -> return mutableListOf("땅설정", "입주패키지수정", "농사아이템교환상점위치지정", "씨앗상인위치지정", "교환상인위치지정", "장비상인위치지정", "토양받기상인위치지정").filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
            }
        }
        
        if (args.size == 3 && args[0].lowercase() == "시스템" && args[1].lowercase() == "땅설정") {
            // No suggestions for plot number
            return mutableListOf()
        }

        return mutableListOf()
    }
} 
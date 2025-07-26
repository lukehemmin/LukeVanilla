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
            // "농사아이템교환상점위치지정" -> handleSetShopLocation(sender, args) // NPC 기반 시스템으로 변경됨
            "씨앗상인지정" -> {
                if (sender is Player) {
                    setNPCMerchant(sender, "seed_merchant", "씨앗 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "교환상인지정" -> {
                if (sender is Player) {
                    setNPCMerchant(sender, "exchange_merchant", "교환 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "장비상인지정" -> {
                if (sender is Player) {
                    setNPCMerchant(sender, "equipment_merchant", "장비 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "토양받기상인지정" -> {
                if (sender is Player) {
                    setNPCMerchant(sender, "soil_receive_merchant", "토양받기 상인")
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
                }
            }
            "주차스크롤" -> handleWeeklyScrollCommands(sender, args)
            "땅뺏기" -> handleSystemConfiscatePlot(sender, args)
            "땅주기" -> handleSystemAssignPlot(sender, args)
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

    // handleSetShopLocation 메서드 삭제됨 - NPC 기반 시스템으로 변경

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
            AssignResult.PLOT_NOT_FOUND -> sender.sendMessage(Component.text("해당 번호의 땅을 찾을 수 없습니다.", NamedTextColor.RED))
            AssignResult.PLOT_ALREADY_CLAIMED -> sender.sendMessage(Component.text("해당 땅은 이미 소유자가 있습니다.", NamedTextColor.RED))
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
        sender.sendMessage(Component.text("/농사마을 시스템 땅뺏기 <닉네임> <땅번호>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 땅주기 <닉네임> <땅번호>", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 입주패키지수정", NamedTextColor.AQUA))
        // sender.sendMessage(Component.text("/농사마을 시스템 농사아이템교환상점위치지정 <world> <x> <y> <z>", NamedTextColor.AQUA)) // NPC 기반 시스템으로 변경됨
        sender.sendMessage(Component.text("/농사마을 시스템 씨앗상인지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 교환상인지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 장비상인지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 토양받기상인지정", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤", NamedTextColor.GRAY).append(Component.text(" - 주차별 스크롤 관리")))
    }

    private fun setNPCMerchant(player: Player, shopId: String, shopName: String) {
        val targetEntity = player.getTargetEntity(5)
        if (targetEntity == null) {
            player.sendMessage(Component.text("NPC를 바라보고 명령어를 사용해주세요.", NamedTextColor.RED))
            return
        }
        
        // Citizens NPC인지 확인
        val npcRegistry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry()
        val npc = npcRegistry.getNPC(targetEntity)
        if (npc == null) {
            player.sendMessage(Component.text("선택된 엔티티는 NPC가 아닙니다.", NamedTextColor.RED))
            return
        }
        
        manager.setNPCMerchant(shopId, npc.id)
        player.sendMessage(Component.text("${shopName}을 NPC '${npc.name}' (ID: ${npc.id})로 지정했습니다.", NamedTextColor.GREEN))
    }

    private fun handleWeeklyScrollCommands(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("lukevanilla.admin.weeklyscroll")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED))
            return
        }

        if (args.size < 3) {
            showWeeklyScrollUsage(sender)
            return
        }

        val weeklyScrollRotationSystem = manager.getWeeklyScrollRotationSystem()

        when (args[2].lowercase()) {
            "상태", "status" -> {
                showWeeklyScrollStatus(sender, weeklyScrollRotationSystem)
            }
            
            "다음주", "next" -> {
                val nextWeek = weeklyScrollRotationSystem.forceNextWeek()
                if (nextWeek != null) {
                    sender.sendMessage(Component.text("주차별 스크롤을 다음주로 강제 변경했습니다: $nextWeek", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("주차 변경에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "이전주", "previous", "prev" -> {
                val prevWeek = weeklyScrollRotationSystem.forcePreviousWeek()
                if (prevWeek != null) {
                    sender.sendMessage(Component.text("주차별 스크롤을 이전주로 강제 변경했습니다: $prevWeek", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("주차 변경에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "설정", "set" -> {
                if (args.size < 4) {
                    sender.sendMessage(Component.text("사용법: /농사마을 시스템 주차스크롤 설정 <주차> (예: 2025-W30)", NamedTextColor.YELLOW))
                    return
                }
                
                val weekString = args[3]
                if (!isValidWeekFormat(weekString)) {
                    sender.sendMessage(Component.text("잘못된 주차 형식입니다. 형식: YYYY-WXX (예: 2025-W30)", NamedTextColor.RED))
                    return
                }
                
                if (weeklyScrollRotationSystem.forceSetWeek(weekString)) {
                    sender.sendMessage(Component.text("주차별 스크롤을 강제로 설정했습니다: $weekString", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("주차 설정에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "해제", "disable", "auto" -> {
                if (weeklyScrollRotationSystem.disableForceMode()) {
                    sender.sendMessage(Component.text("강제 주차 설정을 해제했습니다. 자동 계산으로 복귀합니다.", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("설정 해제에 실패했습니다.", NamedTextColor.RED))
                }
            }
            
            "gui" -> {
                if (sender !is Player) {
                    sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED))
                    return
                }
                
                manager.getWeeklyScrollExchangeGUI().openGUI(sender)
            }
            
            else -> {
                showWeeklyScrollUsage(sender)
            }
        }
    }

    private fun showWeeklyScrollUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("===== 주차별 스크롤 관리 명령어 =====", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤 상태 - 현재 주차 및 강제 설정 상태 확인", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤 다음주 - 다음주로 강제 변경", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤 이전주 - 이전주로 강제 변경", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤 설정 <주차> - 특정 주차로 강제 설정", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤 해제 - 강제 설정 해제 (자동 계산 복귀)", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/농사마을 시스템 주차스크롤 gui - GUI 테스트 열기", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("예: /농사마을 시스템 주차스크롤 설정 2025-W30", NamedTextColor.GRAY))
    }

    private fun showWeeklyScrollStatus(sender: CommandSender, weeklyScrollRotationSystem: WeeklyScrollRotationSystem) {
        val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
        val currentRotation = weeklyScrollRotationSystem.getCurrentRotation()
        val (forceWeek, forceEnabled) = weeklyScrollRotationSystem.getForceStatus()
        
        sender.sendMessage(Component.text("===== 주차별 스크롤 상태 =====", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("현재 주차: $currentWeek", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("현재 시즌: ${currentRotation.displayName}", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("스크롤 개수: ${currentRotation.scrollIds.size}개", NamedTextColor.YELLOW))
        
        if (forceEnabled && forceWeek != null) {
            sender.sendMessage(Component.text("⚠ 강제 설정 모드: $forceWeek", NamedTextColor.RED))
        } else {
            sender.sendMessage(Component.text("✓ 자동 계산 모드 (KST 기준)", NamedTextColor.GREEN))
        }
        
        val nextRotation = weeklyScrollRotationSystem.getNextRotation()
        sender.sendMessage(Component.text("다음 시즌: ${nextRotation.displayName}", NamedTextColor.GRAY))
    }

    private fun isValidWeekFormat(weekString: String): Boolean {
        val pattern = Regex("^\\d{4}-W\\d{2}$")
        return pattern.matches(weekString)
    }

    private fun handleSystemConfiscatePlot(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage(Component.text("사용법: /농사마을 시스템 땅뺏기 <닉네임> <땅번호>", NamedTextColor.RED))
            return
        }

        val playerName = args[2]
        val plotNumber = args[3].toIntOrNull()
        
        if (plotNumber == null) {
            sender.sendMessage(Component.text("땅 번호는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        val targetPlayer = Bukkit.getOfflinePlayer(playerName)
        if (!targetPlayer.hasPlayedBefore()) {
            sender.sendMessage(Component.text("플레이어 '$playerName'를 찾을 수 없거나, 서버에 접속한 기록이 없습니다.", NamedTextColor.RED))
            return
        }

        val result = manager.confiscateSpecificPlotFromPlayer(targetPlayer, plotNumber, sender as? Player)
        
        when (result) {
            ConfiscateResult.SUCCESS -> {
                sender.sendMessage(Component.text("${targetPlayer.name}님으로부터 ${plotNumber}번 농사 땅을 성공적으로 회수했습니다.", NamedTextColor.GREEN))
            }
            ConfiscateResult.PARTIAL_SUCCESS -> {
                sender.sendMessage(Component.text("${targetPlayer.name}님의 ${plotNumber}번 농사 땅 중 일부만 회수했습니다. 서버 로그를 확인해주세요.", NamedTextColor.YELLOW))
            }
            ConfiscateResult.FAILURE -> {
                sender.sendMessage(Component.text("${plotNumber}번 농사 땅 회수에 실패했습니다. 해당 플레이어가 소유자가 아닐 수 있습니다.", NamedTextColor.RED))
            }
            ConfiscateResult.PLOT_NOT_FOUND -> {
                sender.sendMessage(Component.text("${plotNumber}번으로 설정된 농사 땅을 찾을 수 없습니다.", NamedTextColor.RED))
            }
        }
    }

    private fun handleSystemAssignPlot(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage(Component.text("사용법: /농사마을 시스템 땅주기 <닉네임> <땅번호>", NamedTextColor.RED))
            return
        }

        val playerName = args[2]
        val plotNumber = args[3].toIntOrNull()

        if (plotNumber == null) {
            sender.sendMessage(Component.text("땅 번호는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("${playerName} 플레이어를 찾을 수 없습니다. (온라인 상태인 플레이어만 가능)", NamedTextColor.RED))
            return
        }

        val result = manager.assignSpecificPlot(targetPlayer, plotNumber)
        
        when (result) {
            AssignResult.SUCCESS -> {
                val message = Component.text("${targetPlayer.name}님에게 ${plotNumber}번 농사 땅을 성공적으로 지급했습니다.", NamedTextColor.GREEN)
                sender.sendMessage(message)
                targetPlayer.sendMessage(message)
            }
            AssignResult.PLOT_NOT_FOUND -> {
                sender.sendMessage(Component.text("${plotNumber}번으로 설정된 농사 땅을 찾을 수 없습니다.", NamedTextColor.RED))
            }
            AssignResult.PLOT_ALREADY_CLAIMED -> {
                sender.sendMessage(Component.text("${plotNumber}번 농사 땅은 이미 소유자가 있습니다.", NamedTextColor.RED))
            }
            AssignResult.FAILURE -> {
                sender.sendMessage(Component.text("${plotNumber}번 땅 지급에 실패했습니다. 서버 로그를 확인해주세요.", NamedTextColor.RED))
            }
            else -> {
                sender.sendMessage(Component.text("예상치 못한 오류가 발생했습니다.", NamedTextColor.RED))
            }
        }
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
                "시스템" -> return mutableListOf("땅설정", "땅뺏기", "땅주기", "입주패키지수정", "농사아이템교환상점위치지정", "씨앗상인지정", "교환상인지정", "장비상인지정", "토양받기상인지정", "주차스크롤").filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
            }
        }
        
        if (args.size == 3) {
            when {
                args[0].lowercase() == "시스템" && args[1].lowercase() == "땅설정" -> {
                    // No suggestions for plot number
                    return mutableListOf()
                }
                args[0].lowercase() == "시스템" && args[1].lowercase() == "주차스크롤" -> {
                    if (sender.hasPermission("lukevanilla.admin.weeklyscroll")) {
                        return mutableListOf("상태", "다음주", "이전주", "설정", "해제", "gui").filter { 
                            it.startsWith(args[2], ignoreCase = true) 
                        }.toMutableList()
                    }
                }
                args[0].lowercase() == "시스템" && args[1].lowercase() == "땅뺏기" -> {
                    // 플레이어 이름 자동완성 (오프라인 플레이어 포함)
                    return Bukkit.getOfflinePlayers().map { it.name ?: "Unknown" }.filter { 
                        it != "Unknown" && it.startsWith(args[2], ignoreCase = true) 
                    }.take(10).toMutableList() // 성능상 10개로 제한
                }
                args[0].lowercase() == "시스템" && args[1].lowercase() == "땅주기" -> {
                    // 온라인 플레이어 이름 자동완성
                    return Bukkit.getOnlinePlayers().map { it.name }.filter { 
                        it.startsWith(args[2], ignoreCase = true) 
                    }.toMutableList()
                }
            }
        }

        if (args.size == 4) {
            when {
                args[0].lowercase() == "시스템" && args[1].lowercase() == "주차스크롤" && args[2].equals("설정", ignoreCase = true) -> {
                    if (sender.hasPermission("lukevanilla.admin.weeklyscroll")) {
                        val weeklyScrollRotationSystem = manager.getWeeklyScrollRotationSystem()
                        val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
                        return listOf(currentWeek, "2025-W30", "2025-W31").toMutableList()
                    }
                }
                args[0].lowercase() == "시스템" && args[1].lowercase() == "땅뺏기" -> {
                    // 땅 번호 자동완성 (예시로 1-20번까지)
                    return (1..20).map { it.toString() }.filter { 
                        it.startsWith(args[3], ignoreCase = true) 
                    }.toMutableList()
                }
                args[0].lowercase() == "시스템" && args[1].lowercase() == "땅주기" -> {
                    // 땅 번호 자동완성 (예시로 1-20번까지)
                    return (1..20).map { it.toString() }.filter { 
                        it.startsWith(args[3], ignoreCase = true) 
                    }.toMutableList()
                }
            }
        }

        return mutableListOf()
    }
} 
package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.math.ceil
import org.bukkit.Bukkit

class LandCommand(private val landManager: LandManager) : CommandExecutor, TabCompleter {
    
    // FarmVillageManager 참조를 위한 변수 (나중에 설정됨)
    private var farmVillageManager: com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageManager? = null
    
    // AdvancedLandManager 참조를 위한 변수 (나중에 설정됨)
    private var advancedLandManager: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.AdvancedLandManager? = null
    
    fun setFarmVillageManager(manager: com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageManager) {
        this.farmVillageManager = manager
    }
    
    fun setAdvancedLandManager(manager: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.AdvancedLandManager) {
        this.advancedLandManager = manager
    }
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.")
            return true
        }

        if (args.isNotEmpty()) {
            when (args[0].lowercase()) {
                // 기존 MyLand 명령어들
                "정보" -> showClaimInfo(sender)
                "기록" -> showClaimHistory(sender, args.getOrNull(1)?.toIntOrNull() ?: 1)
                "친구추가" -> handleAddMember(sender, args)
                "친구삭제" -> handleRemoveMember(sender, args)
                "친구목록" -> handleListMembers(sender)
                
                // 새로운 AdvancedLandClaiming 명령어들
                "클레임" -> handleAdvancedClaim(sender, args)
                "포기" -> handleAdvancedUnclaim(sender)
                "목록" -> handleAdvancedList(sender)
                "비용" -> handleAdvancedCost(sender)
                "요약" -> handleAdvancedSummary(sender)
                
                // 마을 관련 명령어들 (추후 구현)
                "마을생성" -> handleVillageCreate(sender, args)
                "마을초대" -> handleVillageInvite(sender, args)
                "마을추방" -> handleVillageKick(sender, args)
                "마을정보" -> handleVillageInfo(sender)
                "마을권한" -> handleVillagePermissions(sender, args)
                
                else -> sendUsage(sender)
            }
        } else {
            sendUsage(sender)
        }
        
        return true
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("땅 시스템 명령어", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("/땅 정보", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/땅 정보"))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크의 정보를 봅니다.")))
                .append(Component.text(" - 현재 청크의 소유 정보를 봅니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 친구추가 <플레이어>", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/땅 친구추가 "))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크에 친구를 추가합니다.")))
                .append(Component.text(" - 현재 청크에 친구를 추가합니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 친구삭제 <플레이어>", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/땅 친구삭제 "))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크에서 친구를 삭제합니다.")))
                .append(Component.text(" - 현재 청크에서 친구를 삭제합니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 친구목록", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/땅 친구목록"))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크의 친구 목록을 봅니다.")))
                .append(Component.text(" - 현재 청크의 친구 목록을 봅니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 기록", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/땅 기록"))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크의 이전 기록을 봅니다.")))
                .append(Component.text(" - 현재 청크의 이전 소유 기록을 봅니다.", NamedTextColor.GRAY))
        )
        
        // AdvancedLandClaiming 명령어들
        sender.sendMessage(Component.text("", NamedTextColor.WHITE))
        sender.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("고급 토지 클레이밍", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("/땅 클레임 [자원타입]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 클레임 "))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크를 클레이밍합니다.")))
                .append(Component.text(" - 현재 청크를 클레이밍합니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 포기", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 포기"))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크의 클레이밍을 포기합니다.")))
                .append(Component.text(" - 현재 청크의 클레이밍을 포기합니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 목록", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 목록"))
                .hoverEvent(HoverEvent.showText(Component.text("내가 소유한 토지 목록을 봅니다.")))
                .append(Component.text(" - 내가 소유한 토지 목록을 봅니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 비용", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 비용"))
                .hoverEvent(HoverEvent.showText(Component.text("토지 클레이밍 비용을 확인합니다.")))
                .append(Component.text(" - 토지 클레이밍 비용을 확인합니다.", NamedTextColor.GRAY))
        )
        sender.sendMessage(
            Component.text("/땅 요약", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 요약"))
                .hoverEvent(HoverEvent.showText(Component.text("내 토지 정보 요약을 봅니다.")))
                .append(Component.text(" - 내 토지 정보 요약을 봅니다.", NamedTextColor.GRAY))
        )
        
        // 마을 시스템 명령어들 (추후 구현)
        sender.sendMessage(Component.text("", NamedTextColor.WHITE))
        sender.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("마을 시스템 (개발 중)", NamedTextColor.YELLOW))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        sender.sendMessage(Component.text("/땅 마을생성 <이름> - 마을을 생성합니다.", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/땅 마을초대 <플레이어> - 마을에 플레이어를 초대합니다.", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/땅 마을정보 - 마을 정보를 확인합니다.", NamedTextColor.YELLOW))
    }

    private fun handleAddMember(player: Player, args: Array<out String>) {
        val chunk = player.location.chunk
        if (landManager.getClaimType(chunk) != "GENERAL") {
            player.sendMessage(Component.text("농사마을 땅에는 친구를 추가할 수 없습니다.", NamedTextColor.RED))
            return
        }
        if (landManager.getOwnerOfChunk(chunk) != player.uniqueId) {
            player.sendMessage(Component.text("당신의 땅이 아닙니다.", NamedTextColor.RED))
            return
        }
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /땅 친구추가 <플레이어>", NamedTextColor.RED))
            return
        }
        val member = Bukkit.getOfflinePlayer(args[1])
        if (landManager.addMember(chunk, player, member)) {
            player.sendMessage(Component.text("${member.name}님을 친구로 추가했습니다.", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("친구 추가에 실패했거나, 이미 추가된 친구입니다.", NamedTextColor.RED))
        }
    }

    private fun handleRemoveMember(player: Player, args: Array<out String>) {
        val chunk = player.location.chunk
         if (landManager.getClaimType(chunk) != "GENERAL") {
            player.sendMessage(Component.text("농사마을 땅에는 친구를 관리할 수 없습니다.", NamedTextColor.RED))
            return
        }
        if (landManager.getOwnerOfChunk(chunk) != player.uniqueId) {
            player.sendMessage(Component.text("당신의 땅이 아닙니다.", NamedTextColor.RED))
            return
        }
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /땅 친구삭제 <플레이어>", NamedTextColor.RED))
            return
        }
        val member = Bukkit.getOfflinePlayer(args[1])
        if (landManager.removeMember(chunk, player, member)) {
            player.sendMessage(Component.text("${member.name}님을 친구에서 삭제했습니다.", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("친구 삭제에 실패했습니다.", NamedTextColor.RED))
        }
    }

    private fun handleListMembers(player: Player) {
        val chunk = player.location.chunk
        if (landManager.getClaimType(chunk) != "GENERAL") {
            player.sendMessage(Component.text("농사마을 땅에는 친구 목록이 없습니다.", NamedTextColor.RED))
            return
        }
        if (landManager.getOwnerOfChunk(chunk) != player.uniqueId && !landManager.isMember(chunk, player)) {
             player.sendMessage(Component.text("당신이 소유하거나 멤버로 등록된 땅이 아닙니다.", NamedTextColor.RED))
             return
        }
        val members = landManager.getMembers(chunk)
        if (members.isEmpty()) {
            player.sendMessage(Component.text("이 땅에 추가된 친구가 없습니다.", NamedTextColor.YELLOW))
        } else {
            val memberNames = members.joinToString(", ") { Bukkit.getOfflinePlayer(it).name ?: "알 수 없음" }
            player.sendMessage(Component.text("친구 목록: $memberNames", NamedTextColor.GREEN))
        }
    }

    private fun showClaimInfo(player: Player) {
        val chunk = player.location.chunk
        val claimInfo = landManager.getClaimInfo(chunk)

        if (claimInfo != null) {
            val ownerName = player.server.getOfflinePlayer(claimInfo.ownerUuid).name ?: "알 수 없음"
            val worldName = chunk.world.name
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
            val claimedDate = dateFormat.format(claimInfo.claimedAt)

            val infoMessage = Component.text()
                .append(Component.text(" "))
                .append(Component.text("■", NamedTextColor.GOLD))
                .append(Component.text(" 현재 청크 정보 ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("■", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("   소유자: ", NamedTextColor.GRAY))
                .append(Component.text(ownerName, NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("   위치: ", NamedTextColor.GRAY))
                .append(Component.text("$worldName ", NamedTextColor.WHITE))
                .append(Component.text("(${chunk.x}, ${chunk.z})", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("   소유 시작일: ", NamedTextColor.GRAY))
                .append(Component.text(claimedDate, NamedTextColor.WHITE))

            // 농사마을 땅인지 확인하여 땅 번호 표시
            farmVillageManager?.let { manager ->
                val farmPlotNumber = getFarmPlotNumber(chunk, manager)
                if (farmPlotNumber != null) {
                    infoMessage.append(Component.newline())
                        .append(Component.text("   농사마을 땅 번호: ", NamedTextColor.GRAY))
                        .append(Component.text("${farmPlotNumber}번", NamedTextColor.YELLOW))
                }
            }

            val historyCommand = "/${"땅"} 기록 1"
            val historyButton = Component.text()
                .append(Component.newline())
                .append(Component.text("     "))
                .append(
                    Component.text("[이전 소유자 기록 보기]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("클릭하여 이 청크의 소유권 변경 이력을 봅니다.")))
                        .clickEvent(ClickEvent.runCommand(historyCommand))
                )

            player.sendMessage(infoMessage.append(historyButton))
        } else {
            // 주인이 없는 경우에도 농사마을 땅인지 확인
            farmVillageManager?.let { manager ->
                val farmPlotNumber = getFarmPlotNumber(chunk, manager)
                if (farmPlotNumber != null) {
                    player.sendMessage(Component.text("이 청크는 농사마을 ${farmPlotNumber}번 땅이지만, 아직 주인이 없습니다.", NamedTextColor.YELLOW))
                    return
                }
            }

            if (landManager.isChunkInClaimableArea(chunk)) {
                player.sendMessage(Component.text("이 청크는 주인이 없으며, 보호받지 않는 상태입니다.", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("이 청크는 주인이 없으며, 보호받을 수 없는 지역입니다.", NamedTextColor.GRAY))
            }
        }
    }

    private fun getFarmPlotNumber(chunk: org.bukkit.Chunk, farmVillageManager: com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageManager): Int? {
        try {
            // FarmVillageData에 접근하여 농사마을 땅 정보 확인
            val farmVillageDataField = farmVillageManager::class.java.getDeclaredField("farmVillageData")
            farmVillageDataField.isAccessible = true
            val farmVillageData = farmVillageDataField.get(farmVillageManager) as com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageData
            
            val allPlotParts = farmVillageData.getAllPlotParts()
            for (plotPart in allPlotParts) {
                if (plotPart.world == chunk.world.name && 
                    plotPart.chunkX == chunk.x && 
                    plotPart.chunkZ == chunk.z) {
                    return plotPart.plotNumber
                }
            }
        } catch (e: Exception) {
            // 리플렉션 실패 시 무시
        }
        return null
    }

    private fun showClaimHistory(player: Player, page: Int) {
        val chunk = player.location.chunk
        val historyList = landManager.getClaimHistory(chunk)

        if (historyList.isEmpty()) {
            player.sendMessage(Component.text("이 청크의 소유권 변경 기록이 없습니다.", NamedTextColor.YELLOW))
            return
        }

        val itemsPerPage = 5
        val maxPage = ceil(historyList.size.toDouble() / itemsPerPage).toInt()
        val currentPage = page.coerceIn(1, maxPage)

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (startIndex + itemsPerPage - 1).coerceAtMost(historyList.size - 1)
        val pageItems = historyList.subList(startIndex, endIndex + 1)
        
        val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm")

        val header = Component.text()
            .append(Component.text("---", NamedTextColor.GOLD))
            .append(Component.text(" 청크(${chunk.x}, ${chunk.z}) 소유권 기록 ", NamedTextColor.WHITE))
            .append(Component.text("($currentPage/$maxPage) ", NamedTextColor.GRAY))
            .append(Component.text("---", NamedTextColor.GOLD))
        player.sendMessage(header)

        for (history in pageItems) {
            val prevOwnerName = player.server.getOfflinePlayer(history.previousOwnerUuid).name ?: "알 수 없음"
            val actorName = history.actorUuid?.let { player.server.getOfflinePlayer(it).name } ?: "시스템"
            val date = dateFormat.format(history.unclaimedAt)
            
            val entry = Component.text()
                .append(Component.text("[$date] ", NamedTextColor.GRAY))
                .append(Component.text(prevOwnerName, NamedTextColor.AQUA))
                .append(Component.text(" 님의 소유권이 ", NamedTextColor.WHITE))
                .append(Component.text(actorName, NamedTextColor.YELLOW))
                .append(Component.text("에 의해 해제됨", NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(Component.text("사유: ${history.reason}")))
            player.sendMessage(entry)
        }
        
        // Pagination buttons
        val prevButton = if (currentPage > 1) {
            Component.text("[이전]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/땅 기록 ${currentPage - 1}"))
        } else {
            Component.text("[이전]", NamedTextColor.DARK_GRAY)
        }

        val nextButton = if (currentPage < maxPage) {
            Component.text("[다음]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/땅 기록 ${currentPage + 1}"))
        } else {
            Component.text("[다음]", NamedTextColor.DARK_GRAY)
        }
        
        val pageInfo = Component.text("------- ", NamedTextColor.DARK_GRAY)
            .append(prevButton)
            .append(Component.text(" | ", NamedTextColor.GRAY))
            .append(nextButton)
            .append(Component.text(" -------", NamedTextColor.DARK_GRAY))
        player.sendMessage(pageInfo)
    }

    // ===== AdvancedLandClaiming 명령어 핸들러들 =====
    
    private fun handleAdvancedClaim(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val resourceType = if (args.size > 1) {
            when (args[1].lowercase()) {
                "철", "iron" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.IRON_INGOT
                "다이아", "diamond" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.DIAMOND
                "네더라이트", "netherite" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.NETHERITE_INGOT
                else -> null
            }
        } else null
        
        val result = advancedManager.claimChunk(player, chunk, resourceType)
        
        if (result.success) {
            player.sendMessage(Component.text(result.message, NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text(result.message, NamedTextColor.RED))
        }
    }
    
    private fun handleAdvancedUnclaim(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val result = advancedManager.unclaimChunk(player, chunk)
        
        if (result.success) {
            player.sendMessage(Component.text(result.message, NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text(result.message, NamedTextColor.RED))
        }
    }
    
    private fun handleAdvancedList(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val claimCount = advancedManager.getPlayerClaimCount(player.uniqueId)
        if (claimCount == 0) {
            player.sendMessage(Component.text("소유한 청크가 없습니다.", NamedTextColor.YELLOW))
            return
        }
        
        player.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("내 토지 목록", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        
        player.sendMessage(Component.text("총 ${claimCount}개의 청크를 소유하고 있습니다.", NamedTextColor.GRAY))
        // 추후 상세 목록 구현 예정
    }
    
    private fun handleAdvancedCost(player: Player) {
        player.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("토지 클레이밍 비용", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        
        player.sendMessage(Component.text("무료 슬롯: 4개 (최초 4개 청크)", NamedTextColor.GREEN))
        player.sendMessage(Component.text("철괴: 64개 (스택 1개)", NamedTextColor.GRAY))
        player.sendMessage(Component.text("다이아몬드: 8개", NamedTextColor.AQUA))
        player.sendMessage(Component.text("네더라이트 주괴: 2개", NamedTextColor.DARK_PURPLE))
        
        player.sendMessage(Component.text("사용법: /땅 클레임 [자원타입]", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("예시: /땅 클레임 철, /땅 클레임 다이아", NamedTextColor.YELLOW))
    }
    
    private fun handleAdvancedSummary(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val summary = advancedManager.getPlayerClaimSummary(player.uniqueId)
        player.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("토지 요약", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        player.sendMessage(Component.text(summary, NamedTextColor.GRAY))
    }
    
    // ===== 마을 관련 핸들러들 (추후 구현) =====
    
    private fun handleVillageCreate(player: Player, args: Array<out String>) {
        player.sendMessage(Component.text("마을 시스템은 아직 구현 중입니다.", NamedTextColor.YELLOW))
    }
    
    private fun handleVillageInvite(player: Player, args: Array<out String>) {
        player.sendMessage(Component.text("마을 시스템은 아직 구현 중입니다.", NamedTextColor.YELLOW))
    }
    
    private fun handleVillageKick(player: Player, args: Array<out String>) {
        player.sendMessage(Component.text("마을 시스템은 아직 구현 중입니다.", NamedTextColor.YELLOW))
    }
    
    private fun handleVillageInfo(player: Player) {
        player.sendMessage(Component.text("마을 시스템은 아직 구현 중입니다.", NamedTextColor.YELLOW))
    }
    
    private fun handleVillagePermissions(player: Player, args: Array<out String>) {
        player.sendMessage(Component.text("마을 시스템은 아직 구현 중입니다.", NamedTextColor.YELLOW))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            return mutableListOf(
                "정보", "기록", "친구추가", "친구삭제", "친구목록", // 기존 명령어
                "클레임", "포기", "목록", "비용", "요약", // 새로운 명령어
                "마을생성", "마을초대", "마을추방", "마을정보", "마을권한" // 마을 명령어
            ).filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        if (args.size == 2) {
            when (args[0].lowercase()) {
                "친구추가", "친구삭제" -> {
                    return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                }
                "클레임" -> {
                    return mutableListOf("철", "다이아", "네더라이트").filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                }
            }
        }
        return mutableListOf()
    }
} 
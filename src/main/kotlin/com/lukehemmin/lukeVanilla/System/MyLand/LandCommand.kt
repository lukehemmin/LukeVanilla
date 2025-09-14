package com.lukehemmin.lukeVanilla.System.MyLand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import com.lukehemmin.lukeVanilla.System.Utils.CoordinateDisplayUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.math.ceil
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentHashMap

/**
 * 마을 초대 정보를 저장하는 데이터 클래스
 */
data class VillageInvitation(
    val villageId: Int,
    val villageName: String,
    val inviterUuid: UUID,
    val inviterName: String,
    val inviteeUuid: UUID,
    val inviteTime: Long,
    val expiresAt: Long = inviteTime + 300000 // 5분 후 만료
)

/**
 * 마을 초대 결과를 나타내는 데이터 클래스
 */
data class VillageInviteResult(
    val success: Boolean,
    val message: String
)

/**
 * 이장 양도 정보를 저장하는 데이터 클래스
 */
data class MayorTransferInvitation(
    val villageId: Int,
    val villageName: String,
    val currentMayorUuid: UUID,
    val currentMayorName: String,
    val newMayorUuid: UUID,
    val transferTime: Long,
    val expiresAt: Long = transferTime + 300000 // 5분 후 만료
)

class LandCommand(private val landManager: LandManager) : CommandExecutor, TabCompleter {
    
    // 진행 중인 마을 초대들을 관리하는 맵
    private val pendingInvitations = ConcurrentHashMap<UUID, VillageInvitation>()

    // 진행 중인 이장 양도들을 관리하는 맵
    private val pendingMayorTransfers = ConcurrentHashMap<UUID, MayorTransferInvitation>()
    
    // FarmVillageManager 참조를 위한 변수 (나중에 설정됨)
    private var farmVillageManager: com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageManager? = null
    
    // AdvancedLandManager 참조를 위한 변수 (나중에 설정됨)
    private var advancedLandManager: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.AdvancedLandManager? = null
    
    // VillageSettingsGUI 참조를 위한 변수 (나중에 설정됨)
    private var villageSettingsGUI: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.VillageSettingsGUI? = null
    
    fun setFarmVillageManager(manager: com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageManager) {
        this.farmVillageManager = manager
    }
    
    fun setAdvancedLandManager(manager: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.AdvancedLandManager) {
        this.advancedLandManager = manager
    }
    
    fun setVillageSettingsGUI(gui: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.VillageSettingsGUI) {
        this.villageSettingsGUI = gui
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
                "반환" -> handleAdvancedReturn(sender)
                "목록" -> handleAdvancedList(sender)
                "비용" -> handleAdvancedCost(sender)
                "환불정보" -> handleRefundInfo(sender)
                "환불내역" -> handleRefundHistory(sender)
                "상태" -> handleAdvancedSummary(sender)
                
                // 마을 관련 명령어들
                "마을생성" -> handleVillageCreate(sender, args)
                "마을초대" -> handleVillageInvite(sender, args)
                "마을추방" -> handleVillageKick(sender, args)
                "마을정보" -> handleVillageInfo(sender)
                "마을권한" -> handleVillagePermissions(sender, args)
                "마을반환" -> handleVillageReturn(sender)
                "마을설정" -> handleVillageSettings(sender)
                "마을클레임" -> handleVillageClaim(sender, args)
                "마을해체확정" -> handleVillageDisbandConfirm(sender)
                "이장양도" -> handleMayorTransfer(sender, args)
                "이장양도수락" -> handleMayorTransferAccept(sender)
                "이장양도거절" -> handleMayorTransferReject(sender)
                
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
            Component.text("/땅 반환", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 반환"))
                .hoverEvent(HoverEvent.showText(Component.text("현재 청크의 클레이밍을 반환합니다. (고급 토지는 50% 환불)")))
                .append(Component.text(" - 현재 청크의 클레이밍을 반환합니다. (고급 토지는 50% 환불)", NamedTextColor.GRAY))
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
            Component.text("/땅 상태", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/땅 상태"))
                .hoverEvent(HoverEvent.showText(Component.text("내 토지 정보 상태를 봅니다.")))
                .append(Component.text(" - 내 토지 정보 상태를 봅니다.", NamedTextColor.GRAY))
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
        sender.sendMessage(Component.text("/땅 마을추방 <플레이어> - 마을에서 플레이어를 추방합니다.", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/땅 마을정보 - 마을 정보를 확인합니다.", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/땅 마을클레임 [자원타입] - 마을 토지를 확장합니다.", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/땅 이장양도수락 - 받은 이장 양도 요청을 수락합니다.", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/땅 이장양도거절 - 받은 이장 양도 요청을 거절합니다.", NamedTextColor.YELLOW))
    }

    private fun handleAddMember(player: Player, args: Array<out String>) {
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // AdvancedLandClaiming 시스템 먼저 확인
        val advancedManager = advancedLandManager
        val advancedClaimInfo = advancedManager?.getClaimOwner(worldName, chunk.x, chunk.z)
        
        if (advancedClaimInfo != null) {
            // AdvancedLandClaiming 토지: 개인 토지인지 확인
            if (advancedClaimInfo.claimType == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
                player.sendMessage(Component.text("마을 토지에는 '/땅 마을초대' 명령어를 사용하세요.", NamedTextColor.YELLOW))
                return
            }
            
            // 개인 토지: 소유자 확인
            if (!advancedManager.isPlayerOwner(player.uniqueId, chunk)) {
                player.sendMessage(Component.text("당신의 땅이 아닙니다.", NamedTextColor.RED))
                return
            }
            
            if (args.size < 2) {
                player.sendMessage(Component.text("사용법: /땅 친구추가 <플레이어>", NamedTextColor.RED))
                return
            }
            
            val member = Bukkit.getOfflinePlayer(args[1])
            
            // 대표 청크를 찾아서 친구 추가
            val representativeChunk = advancedManager.getRepresentativeChunk(player.uniqueId, chunk)
            if (representativeChunk == null) {
                player.sendMessage(Component.text("연결된 청크 그룹을 찾을 수 없습니다.", NamedTextColor.RED))
                return
            }
            
            if (landManager.addMemberBypass(representativeChunk, member)) {
                val groupSize = advancedManager.getGroupMemberChunks(player.uniqueId, chunk).size
                player.sendMessage(Component.text("${member.name}님을 연결된 땅 전체(${groupSize}개 청크)에 친구로 추가했습니다.", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("친구 추가에 실패했거나, 이미 추가된 친구입니다.", NamedTextColor.RED))
            }
            return
        }
        
        // 기존 MyLand 시스템 처리
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
        val worldName = chunk.world.name
        
        // AdvancedLandClaiming 시스템 먼저 확인
        val advancedManager = advancedLandManager
        val advancedClaimInfo = advancedManager?.getClaimOwner(worldName, chunk.x, chunk.z)
        
        if (advancedClaimInfo != null) {
            // AdvancedLandClaiming 토지: 개인 토지인지 확인
            if (advancedClaimInfo.claimType == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
                player.sendMessage(Component.text("마을 토지에는 '/땅 마을추방' 명령어를 사용하세요.", NamedTextColor.YELLOW))
                return
            }
            
            // 개인 토지: 소유자 확인
            if (!advancedManager.isPlayerOwner(player.uniqueId, chunk)) {
                player.sendMessage(Component.text("당신의 땅이 아닙니다.", NamedTextColor.RED))
                return
            }
            
            if (args.size < 2) {
                player.sendMessage(Component.text("사용법: /땅 친구삭제 <플레이어>", NamedTextColor.RED))
                return
            }
            
            val member = Bukkit.getOfflinePlayer(args[1])
            
            // 대표 청크를 찾아서 친구 제거
            val representativeChunk = advancedManager.getRepresentativeChunk(player.uniqueId, chunk)
            if (representativeChunk == null) {
                player.sendMessage(Component.text("연결된 청크 그룹을 찾을 수 없습니다.", NamedTextColor.RED))
                return
            }
            
            if (landManager.removeMemberBypass(representativeChunk, member)) {
                val groupSize = advancedManager.getGroupMemberChunks(player.uniqueId, chunk).size
                player.sendMessage(Component.text("${member.name}님을 연결된 땅 전체(${groupSize}개 청크)에서 친구 삭제했습니다.", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("친구 삭제에 실패했습니다.", NamedTextColor.RED))
            }
            return
        }
        
        // 기존 MyLand 시스템 처리
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
        val worldName = chunk.world.name
        
        // AdvancedLandClaiming 시스템 먼저 확인
        val advancedManager = advancedLandManager
        val advancedClaimInfo = advancedManager?.getClaimOwner(worldName, chunk.x, chunk.z)
        
        if (advancedClaimInfo != null) {
            // AdvancedLandClaiming 토지: 개인 토지인지 확인
            if (advancedClaimInfo.claimType == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
                player.sendMessage(Component.text("마을 토지에는 '/땅 마을정보' 명령어를 사용하세요.", NamedTextColor.YELLOW))
                return
            }
            
            // 개인 토지: 소유자이거나 멤버인지 확인
            val representativeChunk = advancedManager.getRepresentativeChunk(advancedClaimInfo.ownerUuid, chunk)
            if (representativeChunk == null) {
                player.sendMessage(Component.text("연결된 청크 그룹을 찾을 수 없습니다.", NamedTextColor.RED))
                return
            }
            
            val isOwner = advancedManager.isPlayerOwner(player.uniqueId, chunk)
            val isMember = landManager.isMember(representativeChunk, player)
            
            if (!isOwner && !isMember) {
                player.sendMessage(Component.text("당신이 소유하거나 멤버로 등록된 땅이 아닙니다.", NamedTextColor.RED))
                return
            }
            
            // 대표 청크에서 멤버 목록 조회
            val members = landManager.getMembers(representativeChunk)
            val groupSize = advancedManager.getGroupMemberChunks(advancedClaimInfo.ownerUuid, chunk).size
            
            if (members.isEmpty()) {
                player.sendMessage(Component.text("연결된 땅 전체(${groupSize}개 청크)에 추가된 친구가 없습니다.", NamedTextColor.YELLOW))
            } else {
                val memberNames = members.joinToString(", ") { Bukkit.getOfflinePlayer(it).name ?: "알 수 없음" }
                player.sendMessage(Component.text("연결된 땅 전체(${groupSize}개 청크) 친구 목록: $memberNames", NamedTextColor.GREEN))
            }
            return
        }
        
        // 기존 MyLand 시스템 처리
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
        val worldName = chunk.world.name
        
        // 먼저 AdvancedLandClaiming 시스템에서 확인
        val advancedManager = advancedLandManager
        val advancedClaimInfo = advancedManager?.getClaimOwner(worldName, chunk.x, chunk.z)
        
        if (advancedClaimInfo != null) {
            // AdvancedLandClaiming으로 클레이밍된 땅
            showAdvancedClaimInfo(player, chunk, advancedClaimInfo)
            return
        }
        
        // 기존 MyLand 시스템에서 확인
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
                .append(CoordinateDisplayUtils.formatClickableCoordinates(chunk, includeWorld = true))
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
            .append(Component.text(" 📜 ", NamedTextColor.YELLOW))
            .append(CoordinateDisplayUtils.formatCompactCoordinates(chunk))
            .append(Component.text(" 소유권 기록 ", NamedTextColor.WHITE))
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
    
    /**
     * AdvancedLandClaiming으로 클레이밍된 땅 정보를 표시합니다.
     */
    private fun showAdvancedClaimInfo(player: Player, chunk: org.bukkit.Chunk, claimInfo: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.AdvancedClaimInfo) {
        val worldName = chunk.world.name
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
        val claimedDate = dateFormat.format(java.util.Date(claimInfo.createdAt))

        val claimTypeText = when (claimInfo.claimType) {
            com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.PERSONAL -> "개인 토지"
            com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE -> "마을 토지"
        }

        // 클레이밍 비용 표시 개선 - 서버 재시작 후에도 정보 유지
        val costText = claimInfo.claimCost?.let { cost ->
            when (cost.resourceType) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.FREE -> "무료 슬롯 사용"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.IRON_INGOT -> "철괴 ${cost.amount}개"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.DIAMOND -> "다이아몬드 ${cost.amount}개"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.NETHERITE_INGOT -> "네더라이트 주괴 ${cost.amount}개"
            }
        } ?: "과거 클레이밍 정보 (기록 없음)"

        // 기본 정보 메시지 구성
        val infoMessage = Component.text()
            .append(Component.text(" "))
            .append(Component.text("■", NamedTextColor.GOLD))
            .append(Component.text(" 토지 정보 ", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text("■", NamedTextColor.GOLD))
            .append(Component.newline())

        // 마을 토지와 개인 토지에 따른 소유자 정보 표시
        when (claimInfo.claimType) {
            com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE -> {
                // 마을 토지인 경우
                val advancedManager = advancedLandManager
                if (advancedManager != null && claimInfo.villageId != null) {
                    val villageInfo = advancedManager.getVillageInfo(claimInfo.villageId)
                    if (villageInfo != null) {
                        // 마을 이름과 이장 정보 표시
                        infoMessage
                            .append(Component.text("   마을 이름: ", NamedTextColor.GRAY))
                            .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                            .append(Component.newline())
                            .append(Component.text("   마을 이장: ", NamedTextColor.GRAY))
                            .append(Component.text(villageInfo.mayorName, NamedTextColor.AQUA))
                    } else {
                        // 마을 정보를 찾을 수 없는 경우
                        infoMessage
                            .append(Component.text("   소유자: ", NamedTextColor.GRAY))
                            .append(Component.text("${claimInfo.ownerName} (마을)", NamedTextColor.AQUA))
                    }
                } else {
                    // AdvancedManager가 없거나 villageId가 없는 경우
                    infoMessage
                        .append(Component.text("   소유자: ", NamedTextColor.GRAY))
                        .append(Component.text("${claimInfo.ownerName} (마을)", NamedTextColor.AQUA))
                }
            }
            com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.PERSONAL -> {
                // 개인 토지인 경우
                infoMessage
                    .append(Component.text("   소유자: ", NamedTextColor.GRAY))
                    .append(Component.text(claimInfo.ownerName, NamedTextColor.AQUA))
            }
        }

        // 나머지 정보 추가
        infoMessage
            .append(Component.newline())
            .append(Component.text("   위치: ", NamedTextColor.GRAY))
            .append(CoordinateDisplayUtils.formatClickableCoordinates(chunk, includeWorld = true))
            .append(Component.newline())
            .append(Component.text("   유형: ", NamedTextColor.GRAY))
            .append(Component.text(claimTypeText, NamedTextColor.GREEN))
            .append(Component.newline())
            .append(Component.text("   클레이밍 비용: ", NamedTextColor.GRAY))
            .append(Component.text(costText, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("   소유 시작일: ", NamedTextColor.GRAY))
            .append(Component.text(claimedDate, NamedTextColor.WHITE))

        player.sendMessage(infoMessage)
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
    
    /**
     * 토지 반환 처리 - MyLand 및 AdvancedLandClaiming 통합 지원
     * AdvancedLandClaiming: 50% 환불 시스템
     * MyLand: 기본 반환 (비용 없는 시스템)
     */
    private fun handleAdvancedReturn(player: Player) {
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        val chunkX = chunk.x
        val chunkZ = chunk.z
        
        // 1. AdvancedLandClaiming 청크 확인 및 처리
        val advancedManager = advancedLandManager
        if (advancedManager != null) {
            val advancedClaimInfo = advancedManager.getClaimOwner(worldName, chunkX, chunkZ)
            if (advancedClaimInfo != null) {
                // AdvancedLandClaiming 시스템으로 처리 (50% 환불 포함)
                val result = advancedManager.unclaimChunk(player, chunk)
                if (result.success) {
                    player.sendMessage(Component.text(result.message, NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text(result.message, NamedTextColor.RED))
                }
                return
            }
        }
        
        // 2. MyLand 청크 확인 및 처리
        val mylandOwner = landManager.getOwnerOfChunk(chunk)
        if (mylandOwner != null) {
            // 소유자 확인
            if (mylandOwner != player.uniqueId && !player.hasPermission("myland.admin.unclaim")) {
                player.sendMessage(Component.text("본인의 토지만 반환할 수 있습니다.", NamedTextColor.RED))
                return
            }
            
            // MyLand 반환 처리
            val result = landManager.unclaimChunk(chunk, player, "자발적 반환")
            when (result) {
                com.lukehemmin.lukeVanilla.System.MyLand.UnclaimResult.SUCCESS -> {
                    player.sendMessage(
                        Component.text()
                            .append(Component.text("청크 ", NamedTextColor.GREEN))
                            .append(Component.text("($chunkX, $chunkZ)", NamedTextColor.YELLOW))
                            .append(Component.text("을 성공적으로 반환했습니다.", NamedTextColor.GREEN))
                    )
                }
                com.lukehemmin.lukeVanilla.System.MyLand.UnclaimResult.NOT_CLAIMED -> {
                    player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
                }
                com.lukehemmin.lukeVanilla.System.MyLand.UnclaimResult.NO_PERMISSION -> {
                    player.sendMessage(Component.text("이 청크를 반환할 권한이 없습니다.", NamedTextColor.RED))
                }
            }
            return
        }
        
        // 3. 클레이밍되지 않은 청크
        player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
    }
    
    // ===== 환불 시스템 확장 =====

    /**
     * 개별 청크 환불 계산 (고급 토지 시스템용)
     */
    private fun calculateRefund(claimInfo: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.AdvancedClaimInfo): List<org.bukkit.inventory.ItemStack> {
        val advancedManager = advancedLandManager ?: return emptyList()
        return advancedManager.calculateRefundItems(claimInfo.claimCost)
    }

    /**
     * 환불 아이템 지급
     */
    private fun giveRefundItems(player: Player, items: List<org.bukkit.inventory.ItemStack>) {
        val advancedManager = advancedLandManager ?: return
        advancedManager.giveRefundItemsSafely(player, items)
    }

    /**
     * 환불 정책 열거형
     */
    enum class RefundPolicy(
        val displayName: String,
        val refundRate: Double,
        val description: String
    ) {
        FULL("100% 환불", 1.0, "처음 24시간 내 반환 시"),
        HALF("50% 환불", 0.5, "일반적인 경우"),
        QUARTER("25% 환불", 0.25, "장기간 사용 후 반환"),
        NONE("환불 없음", 0.0, "특수 상황 또는 무료 토지")
    }

    /**
     * 환불 정책 결정
     */
    private fun determineRefundPolicy(
        claimInfo: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.AdvancedClaimInfo,
        currentTime: Long
    ): RefundPolicy {
        val claimDuration = currentTime - claimInfo.createdAt
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        val oneWeekInMillis = 7 * oneDayInMillis

        return when {
            claimInfo.claimCost?.resourceType == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.FREE -> RefundPolicy.NONE
            claimDuration <= oneDayInMillis -> RefundPolicy.FULL
            claimDuration <= oneWeekInMillis -> RefundPolicy.HALF
            else -> RefundPolicy.QUARTER
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
        player.sendMessage(Component.text(""))
        
        // 플레이어가 소유한 연결된 청크 그룹들 조회
        val connectedGroups = advancedManager.getPlayerConnectedChunks(player.uniqueId)
        
        connectedGroups.forEachIndexed { groupIndex, group ->
            val chunks = group.chunks.toList().sortedWith(
                compareBy<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ChunkCoordinate> 
                    { it.worldName }.thenBy { it.x }.thenBy { it.z }
            )
            
            // 청크 리스트를 실제 Chunk 객체로 변환
            val actualChunks = chunks.mapNotNull { chunkData ->
                player.server.getWorld(chunkData.worldName)?.getChunkAt(chunkData.x, chunkData.z)
            }

            if (actualChunks.isNotEmpty()) {
                player.sendMessage(
                    Component.text()
                        .append(Component.text("📍 그룹 ${groupIndex + 1} ", NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(CoordinateDisplayUtils.formatAreaCoordinates(actualChunks))
                )
            } else {
                player.sendMessage(
                    Component.text()
                        .append(Component.text("📍 그룹 ${groupIndex + 1} ", NamedTextColor.AQUA))
                        .append(Component.text("(${group.size}개 청크 - 월드 로드 실패)", NamedTextColor.RED))
                )
            }

            // 상세 청크 목록은 영역 표시로 대체됨
            
            if (groupIndex < connectedGroups.size - 1) {
                player.sendMessage(Component.text(""))
            }
        }
    }
    
    private fun handleAdvancedCost(player: Player) {
        val advancedManager = advancedLandManager
        
        player.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("토지 클레이밍 비용", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        
        // AdvancedLand가 활성화된 경우 개인별 정보 표시
        if (advancedManager != null) {
            val isVeteran = advancedManager.isVeteranPlayer(player.uniqueId)
            val currentClaims = advancedManager.getPlayerClaimCount(player.uniqueId)
            
            player.sendMessage(Component.text("=== 개인 정보 ===", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("현재 클레이밍: ${currentClaims}개", NamedTextColor.WHITE))
            
            if (!isVeteran) {
                val maxClaims = 9  // NEWBIE_MAX_CLAIMS
                player.sendMessage(Component.text("최대 클레이밍: ${maxClaims}개 (신규 플레이어)", NamedTextColor.GRAY))
            } else {
                player.sendMessage(Component.text("최대 클레이밍: 무제한 (베테랑 플레이어)", NamedTextColor.GREEN))
            }
            
            // 무료 슬롯 정보는 AdvancedLand에서만 확인 가능
            // MyLand에는 해당 기능이 없으므로 기본 정보만 표시
        }
        
        player.sendMessage(Component.text("=== 비용 정보 ===", NamedTextColor.YELLOW))
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
    
    /**
     * 마을 생성 기능 - 개인 토지를 마을 토지로 전환
     */
    private fun handleVillageCreate(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        // 마을 이름 확인
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /땅 마을생성 <마을이름>", NamedTextColor.RED))
            return
        }
        
        val villageName = args[1]
        if (villageName.length < 2 || villageName.length > 20) {
            player.sendMessage(Component.text("마을 이름은 2~20자 사이여야 합니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 개인 소유인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다. 먼저 '/땅 클레임'으로 소유하세요.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.ownerUuid != player.uniqueId) {
            player.sendMessage(Component.text("이 청크의 소유자가 아닙니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.PERSONAL) {
            player.sendMessage(Component.text("이 청크는 이미 마을 토지입니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 연결된 모든 청크가 같은 소유자인지 확인
        val connectedChunks = advancedManager.getGroupMemberChunks(player.uniqueId, chunk)
        if (connectedChunks.isEmpty()) {
            player.sendMessage(Component.text("연결된 청크 그룹을 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 연결된 모든 청크가 개인 토지인지 확인
        for (connectedChunk in connectedChunks) {
            val connectedClaimInfo = advancedManager.getClaimOwner(worldName, connectedChunk.x, connectedChunk.z)
            if (connectedClaimInfo == null || 
                connectedClaimInfo.ownerUuid != player.uniqueId ||
                connectedClaimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.PERSONAL) {
                player.sendMessage(Component.text("연결된 청크 중 일부가 개인 토지가 아닙니다. 모든 연결된 청크가 본인 소유여야 합니다.", NamedTextColor.RED))
                return
            }
        }
        
        // 3. ChunkCoordinate를 Chunk로 변환
        val chunkSet = connectedChunks.mapNotNull { chunkCoord ->
            val world = org.bukkit.Bukkit.getWorld(chunkCoord.worldName)
            world?.getChunkAt(chunkCoord.x, chunkCoord.z)
        }.toSet()
        
        // 4. 마을 이름 중복 확인 및 생성
        val createResult = advancedManager.createVillage(player, villageName, chunkSet)
        if (createResult.success) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("✅ ", NamedTextColor.GREEN))
                    .append(Component.text("마을 '", NamedTextColor.WHITE))
                    .append(Component.text(villageName, NamedTextColor.YELLOW))
                    .append(Component.text("'이(가) 성공적으로 생성되었습니다!", NamedTextColor.WHITE))
            )
            player.sendMessage(
                Component.text()
                    .append(Component.text("🏘️ ", NamedTextColor.GOLD))
                    .append(Component.text("${connectedChunks.size}개의 청크가 마을 토지로 전환되었습니다.", NamedTextColor.GRAY))
            )
            player.sendMessage(
                Component.text()
                    .append(Component.text("👑 ", NamedTextColor.GOLD))
                    .append(Component.text("당신은 이제 이장입니다!", NamedTextColor.GREEN))
            )
        } else {
            player.sendMessage(Component.text(createResult.message, NamedTextColor.RED))
        }
    }
    
    /**
     * 마을 초대 기능 - 마을 토지에서 플레이어를 초대
     */
    private fun handleVillageInvite(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        // 초대할 플레이어 확인
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /땅 마을초대 <플레이어>", NamedTextColor.RED))
            return
        }
        
        val targetPlayerName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetPlayerName)
        if (targetPlayer == null) {
            player.sendMessage(Component.text("온라인 상태인 플레이어만 초대할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        if (targetPlayer.uniqueId == player.uniqueId) {
            player.sendMessage(Component.text("자신을 초대할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 마을 토지인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("마을 토지에서만 초대할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 마을 정보 조회
        val villageInfo = advancedManager.getVillageInfo(villageId)
        if (villageInfo == null) {
            player.sendMessage(Component.text("마을 정보를 조회할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 3. 초대 권한 확인 (이장 또는 부이장만 초대 가능)
        val villageMembers = advancedManager.getVillageMembers(villageId)
        val inviterMember = villageMembers.find { it.memberUuid == player.uniqueId }
        
        if (inviterMember == null) {
            player.sendMessage(Component.text("마을 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        if (inviterMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR &&
            inviterMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR) {
            player.sendMessage(Component.text("이장 또는 부이장만 초대할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        // 4. 이미 마을 구성원인지 확인
        val targetMember = villageMembers.find { it.memberUuid == targetPlayer.uniqueId }
        if (targetMember != null) {
            player.sendMessage(Component.text("이미 마을 구성원입니다.", NamedTextColor.RED))
            return
        }
        
        // 5. 이미 초대가 진행 중인지 확인
        val existingInvitation = pendingInvitations[targetPlayer.uniqueId]
        if (existingInvitation != null) {
            if (System.currentTimeMillis() < existingInvitation.expiresAt) {
                player.sendMessage(Component.text("해당 플레이어에게 이미 진행 중인 초대가 있습니다.", NamedTextColor.YELLOW))
                return
            } else {
                // 만료된 초대는 제거
                pendingInvitations.remove(targetPlayer.uniqueId)
            }
        }
        
        // 6. 새로운 초대 생성
        val invitation = VillageInvitation(
            villageId = villageId,
            villageName = villageInfo.villageName,
            inviterUuid = player.uniqueId,
            inviterName = player.name,
            inviteeUuid = targetPlayer.uniqueId,
            inviteTime = System.currentTimeMillis()
        )
        
        pendingInvitations[targetPlayer.uniqueId] = invitation
        
        // 7. 초대자에게 확인 메시지
        player.sendMessage(
            Component.text()
                .append(Component.text("✉️ ", NamedTextColor.GREEN))
                .append(Component.text("${targetPlayer.name}님에게 마을 '", NamedTextColor.WHITE))
                .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                .append(Component.text("' 초대를 보냈습니다!", NamedTextColor.WHITE))
        )
        
        // 8. 피초대자에게 초대 메시지 전송
        sendInvitationMessage(targetPlayer, invitation)
    }
    
    /**
     * 인터랙티브한 초대 메시지를 전송합니다.
     */
    private fun sendInvitationMessage(targetPlayer: Player, invitation: VillageInvitation) {
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
        )
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("🏘️ ", NamedTextColor.YELLOW))
                .append(Component.text("마을 초대", NamedTextColor.WHITE, TextDecoration.BOLD))
        )
        
        targetPlayer.sendMessage(Component.text(""))
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("마을: ", NamedTextColor.GRAY))
                .append(Component.text(invitation.villageName, NamedTextColor.YELLOW, TextDecoration.BOLD))
        )
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("초대자: ", NamedTextColor.GRAY))
                .append(Component.text(invitation.inviterName, NamedTextColor.AQUA))
        )
        
        targetPlayer.sendMessage(Component.text(""))
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("마을 '", NamedTextColor.WHITE))
                .append(Component.text(invitation.villageName, NamedTextColor.YELLOW))
                .append(Component.text("'으로부터 초대를 받았습니다.", NamedTextColor.WHITE))
        )
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("수락하시겠습니까?", NamedTextColor.WHITE))
        )
        
        targetPlayer.sendMessage(Component.text(""))
        
        // 수락 버튼
        val acceptButton = Component.text()
            .append(Component.text("  [", NamedTextColor.WHITE))
            .append(Component.text("✓ 수락", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text("]", NamedTextColor.WHITE))
            .clickEvent(ClickEvent.runCommand("/마을초대 수락"))
            .hoverEvent(HoverEvent.showText(Component.text("마을 초대를 수락합니다")))
        
        // 거절 버튼
        val declineButton = Component.text()
            .append(Component.text("  [", NamedTextColor.WHITE))
            .append(Component.text("✗ 거절", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("]", NamedTextColor.WHITE))
            .clickEvent(ClickEvent.runCommand("/마을초대 거절"))
            .hoverEvent(HoverEvent.showText(Component.text("마을 초대를 거절합니다")))
        
        targetPlayer.sendMessage(
            Component.text()
                .append(acceptButton)
                .append(Component.text("    ", NamedTextColor.WHITE))
                .append(declineButton)
        )
        
        targetPlayer.sendMessage(Component.text(""))
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("또는 다음 명령어를 사용하세요:", NamedTextColor.GRAY))
        )
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("/마을초대 수락", NamedTextColor.GREEN))
                .append(Component.text(" 또는 ", NamedTextColor.GRAY))
                .append(Component.text("/마을초대 거절", NamedTextColor.RED))
        )
        
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
        )
        
        // 5분 후 만료 안내
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("⏰ ", NamedTextColor.YELLOW))
                .append(Component.text("이 초대는 5분 후 자동으로 만료됩니다.", NamedTextColor.GRAY))
        )
    }
    
    // ===== 마을 초대 관리 메서드들 (VillageInviteCommand에서 사용) =====
    
    /**
     * 플레이어의 진행 중인 초대를 조회합니다.
     */
    fun getPendingInvitation(playerUuid: UUID): VillageInvitation? {
        return pendingInvitations[playerUuid]
    }
    
    /**
     * 진행 중인 초대를 제거합니다.
     */
    fun removePendingInvitation(playerUuid: UUID) {
        pendingInvitations.remove(playerUuid)
    }
    
    /**
     * 마을 초대를 수락 처리합니다.
     */
    fun acceptVillageInvitation(player: Player, invitation: VillageInvitation): VillageInviteResult {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            return VillageInviteResult(false, "고급 토지 시스템이 초기화되지 않았습니다.")
        }
        
        try {
            // 마을이 여전히 존재하는지 확인
            val villageInfo = advancedManager.getVillageInfo(invitation.villageId)
            if (villageInfo == null || !villageInfo.isActive) {
                return VillageInviteResult(false, "마을이 존재하지 않거나 비활성화되었습니다.")
            }
            
            // 이미 마을 구성원인지 재확인
            val villageMembers = advancedManager.getVillageMembers(invitation.villageId)
            val existingMember = villageMembers.find { it.memberUuid == player.uniqueId }
            if (existingMember != null) {
                return VillageInviteResult(false, "이미 마을 구성원입니다.")
            }
            
            // 마을 멤버로 추가
            val addResult = advancedManager.addVillageMember(
                invitation.villageId, 
                player.uniqueId, 
                player.name,
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER
            )
            
            if (addResult) {
                return VillageInviteResult(true, "마을에 성공적으로 가입했습니다!")
            } else {
                return VillageInviteResult(false, "마을 가입 중 데이터베이스 오류가 발생했습니다.")
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            return VillageInviteResult(false, "마을 가입 중 예상치 못한 오류가 발생했습니다.")
        }
    }
    
    /**
     * 마을 추방 기능 - 이장과 부이장이 마을 구성원을 추방할 수 있습니다.
     * 사용법: /땅 마을추방 <플레이어>
     */
    private fun handleVillageKick(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /땅 마을추방 <플레이어>", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 마을 토지인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("이 곳은 마을 토지가 아닙니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 추방할 대상 플레이어 검증
        val targetPlayerName = args[1]
        val targetPlayer = org.bukkit.Bukkit.getOfflinePlayer(targetPlayerName)
        
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
            player.sendMessage(Component.text("'$targetPlayerName'은(는) 존재하지 않는 플레이어입니다.", NamedTextColor.RED))
            return
        }
        
        if (targetPlayer.uniqueId == player.uniqueId) {
            player.sendMessage(Component.text("자신을 추방할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 3. 마을 구성원 목록 및 권한 확인
        val villageMembers = advancedManager.getVillageMembers(villageId)
        val playerMember = villageMembers.find { it.memberUuid == player.uniqueId }
        val targetMember = villageMembers.find { it.memberUuid == targetPlayer.uniqueId }
        
        if (playerMember == null) {
            player.sendMessage(Component.text("마을 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        if (targetMember == null) {
            player.sendMessage(Component.text("'$targetPlayerName'님은 이 마을의 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        // 4. 추방 권한 확인 (이장 또는 부이장만 가능)
        if (playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR &&
            playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR) {
            player.sendMessage(Component.text("이장 또는 부이장만 마을 구성원을 추방할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        // 5. 이장 추방 제한
        if (targetMember.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR) {
            player.sendMessage(Component.text("이장을 추방할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 6. 부이장이 다른 부이장을 추방하는 것 제한 (이장만 가능)
        if (playerMember.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR &&
            targetMember.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR) {
            player.sendMessage(Component.text("부이장은 다른 부이장을 추방할 수 없습니다. 이장만 가능합니다.", NamedTextColor.RED))
            return
        }
        
        // 7. 마을 정보 조회 (알림용)
        val villageInfo = advancedManager.getVillageInfo(villageId)
        if (villageInfo == null) {
            player.sendMessage(Component.text("마을 정보를 조회할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 8. 추방 처리 (실제 구현은 AdvancedLandManager에 위임)
        val kickResult = advancedManager.kickVillageMember(villageId, targetPlayer.uniqueId)
        
        if (kickResult) {
            // 추방 성공 메시지
            val targetRoleText = when (targetMember.role) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR -> "부이장"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER -> "구성원"
                else -> "구성원"
            }
            
            player.sendMessage(
                Component.text()
                    .append(Component.text("👢 ", NamedTextColor.RED))
                    .append(Component.text("${targetMember.memberName}님($targetRoleText)을 마을에서 추방했습니다.", NamedTextColor.WHITE))
            )
            
            // 대상 플레이어에게 알림 (온라인인 경우)
            val onlineTarget = org.bukkit.Bukkit.getPlayer(targetPlayer.uniqueId)
            if (onlineTarget != null) {
                onlineTarget.sendMessage(
                    Component.text()
                        .append(Component.text("👢 ", NamedTextColor.RED))
                        .append(Component.text("마을 '", NamedTextColor.WHITE))
                        .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                        .append(Component.text("'에서 추방되었습니다.", NamedTextColor.WHITE))
                )
            }
            
            // 다른 마을 구성원들에게 알림
            villageMembers.forEach { member ->
                if (member.memberUuid != player.uniqueId && member.memberUuid != targetPlayer.uniqueId) {
                    val onlineMember = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                    if (onlineMember != null) {
                        onlineMember.sendMessage(
                            Component.text()
                                .append(Component.text("📢 ", NamedTextColor.YELLOW))
                                .append(Component.text("${targetMember.memberName}님이 마을에서 추방되었습니다.", NamedTextColor.GRAY))
                        )
                    }
                }
            }
        } else {
            player.sendMessage(Component.text("추방 처리 중 오류가 발생했습니다.", NamedTextColor.RED))
        }
    }
    
    /**
     * 마을 정보 조회 - 현재 위치한 마을의 상세 정보 표시
     */
    private fun handleVillageInfo(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 마을 토지인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("이 곳은 마을 토지가 아닙니다. 개인 토지입니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 마을 정보 조회
        val villageInfo = advancedManager.getVillageInfo(villageId)
        if (villageInfo == null) {
            player.sendMessage(Component.text("마을 정보를 조회할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 3. 마을 구성원 목록 조회
        val villageMembers = advancedManager.getVillageMembers(villageId)
        
        // 4. 마을 토지 개수 계산
        val villageChunkCount = advancedManager.getVillageChunkCount(villageId)
        
        // 5. 마을 정보 표시
        displayVillageInfo(player, villageInfo, villageMembers, villageChunkCount)
    }
    
    /**
     * 마을 정보를 예쁘게 표시합니다.
     */
    private fun displayVillageInfo(
        player: Player, 
        villageInfo: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageInfo,
        members: List<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageMember>,
        chunkCount: Int
    ) {
        // 헤더
        player.sendMessage(
            Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("🏘️ ", NamedTextColor.YELLOW))
                .append(Component.text("마을 정보", NamedTextColor.WHITE, TextDecoration.BOLD))
        )
        
        player.sendMessage(Component.text(""))
        
        // 마을 기본 정보
        player.sendMessage(
            Component.text()
                .append(Component.text("마을 이름: ", NamedTextColor.GRAY))
                .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW, TextDecoration.BOLD))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("이장: ", NamedTextColor.GRAY))
                .append(Component.text(villageInfo.mayorName, NamedTextColor.AQUA))
        )
        
        val createdDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(villageInfo.createdAt))
        player.sendMessage(
            Component.text()
                .append(Component.text("설립일: ", NamedTextColor.GRAY))
                .append(Component.text(createdDate, NamedTextColor.WHITE))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("토지 개수: ", NamedTextColor.GRAY))
                .append(Component.text("${chunkCount}개 청크", NamedTextColor.GREEN))
        )
        
        player.sendMessage(Component.text(""))
        
        // 구성원 정보
        player.sendMessage(
            Component.text()
                .append(Component.text("👥 ", NamedTextColor.AQUA))
                .append(Component.text("구성원 (${members.size}명)", NamedTextColor.WHITE, TextDecoration.BOLD))
        )
        
        // 역할별로 구성원 분류
        val mayor = members.find { it.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR }
        val deputyMayors = members.filter { it.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR }
        val regularMembers = members.filter { it.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER }
        
        // 이장 표시
        if (mayor != null) {
            val mayorStatus = if (org.bukkit.Bukkit.getPlayer(mayor.memberUuid) != null) "🟢" else "🔴"
            player.sendMessage(
                Component.text()
                    .append(Component.text("  👑 이장: ", NamedTextColor.GOLD))
                    .append(Component.text(mayor.memberName, NamedTextColor.YELLOW))
                    .append(Component.text(" $mayorStatus", NamedTextColor.WHITE))
            )
        }
        
        // 부이장들 표시
        if (deputyMayors.isNotEmpty()) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("  🥈 부이장:", NamedTextColor.AQUA))
            )
            deputyMayors.forEach { deputy ->
                val deputyStatus = if (org.bukkit.Bukkit.getPlayer(deputy.memberUuid) != null) "🟢" else "🔴"
                player.sendMessage(
                    Component.text()
                        .append(Component.text("    - ", NamedTextColor.GRAY))
                        .append(Component.text(deputy.memberName, NamedTextColor.AQUA))
                        .append(Component.text(" $deputyStatus", NamedTextColor.WHITE))
                )
            }
        }
        
        // 일반 구성원들 표시
        if (regularMembers.isNotEmpty()) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("  👤 구성원:", NamedTextColor.GREEN))
            )
            regularMembers.forEach { member ->
                val memberStatus = if (org.bukkit.Bukkit.getPlayer(member.memberUuid) != null) "🟢" else "🔴"
                player.sendMessage(
                    Component.text()
                        .append(Component.text("    - ", NamedTextColor.GRAY))
                        .append(Component.text(member.memberName, NamedTextColor.WHITE))
                        .append(Component.text(" $memberStatus", NamedTextColor.WHITE))
                )
            }
        }
        
        player.sendMessage(Component.text(""))
        
        // 푸터 - 상태 표시 설명
        player.sendMessage(
            Component.text()
                .append(Component.text("🟢 온라인  🔴 오프라인", NamedTextColor.GRAY))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
        )
    }
    
    /**
     * 마을 반환 기능 - 현재 위치한 마을 토지를 반환 (확장 가능한 환불 시스템 포함)
     */
    private fun handleVillageReturn(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 마을 토지인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("이 곳은 마을 토지가 아닙니다. '/땅 반환'을 사용하세요.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 마을 반환 권한 확인
        val villageMembers = advancedManager.getVillageMembers(villageId)
        val playerMember = villageMembers.find { it.memberUuid == player.uniqueId }
        
        if (playerMember == null) {
            player.sendMessage(Component.text("마을 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        // 이장 또는 부이장만 반환 가능
        if (playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR &&
            playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR) {
            player.sendMessage(Component.text("이장 또는 부이장만 마을 토지를 반환할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        // 3. 연결된 청크들 조회
        val connectedChunks = advancedManager.getGroupMemberChunks(claimInfo.ownerUuid, chunk)
        val villageInfo = advancedManager.getVillageInfo(villageId)
        
        if (connectedChunks.isEmpty() || villageInfo == null) {
            player.sendMessage(Component.text("마을 정보를 조회할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 3-1. ChunkCoordinate를 Chunk로 변환
        val chunkSet = connectedChunks.mapNotNull { chunkCoord ->
            val world = org.bukkit.Bukkit.getWorld(chunkCoord.worldName)
            world?.getChunkAt(chunkCoord.x, chunkCoord.z)
        }.toSet()

        // 3-2. 환불 시스템 처리
        val refundResult = calculateVillageRefund(chunkSet, villageInfo)

        // 3-3. 환불 상세 정보 표시
        showRefundDetails(player, refundResult, connectedChunks.size)
        
        // 4. 마을 반환 처리
        val returnResult = advancedManager.returnVillageChunks(player, villageId, chunkSet, "이장에 의한 마을 반환")
        
        if (returnResult.success) {
            // 마을 구성원들에게 알림
            notifyVillageMembersAboutReturn(villageMembers, villageInfo.villageName, connectedChunks.size)
            
            player.sendMessage(
                Component.text()
                    .append(Component.text("🏘️ ", NamedTextColor.GREEN))
                    .append(Component.text("마을 '", NamedTextColor.WHITE))
                    .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                    .append(Component.text("'의 토지 ${connectedChunks.size}개 청크가 성공적으로 반환되었습니다!", NamedTextColor.WHITE))
            )
            
            // 환불 지급 처리
            if (refundResult.refundItems.isNotEmpty()) {
                giveRefundItems(player, refundResult.refundItems)
                player.sendMessage(
                    Component.text()
                        .append(Component.text("🎁 ", NamedTextColor.GOLD))
                        .append(Component.text("환불된 아이템이 인벤토리에 지급되었습니다.", NamedTextColor.YELLOW))
                )
            }
        } else {
            player.sendMessage(Component.text(returnResult.message, NamedTextColor.RED))
        }
    }
    
    /**
     * 마을 구성원들에게 마을 반환 알림을 보냅니다.
     */
    private fun notifyVillageMembersAboutReturn(
        members: List<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageMember>,
        villageName: String,
        chunkCount: Int
    ) {
        members.forEach { member ->
            val onlinePlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(
                    Component.text()
                        .append(Component.text("🏘️ ", NamedTextColor.YELLOW))
                        .append(Component.text("마을 '", NamedTextColor.WHITE))
                        .append(Component.text(villageName, NamedTextColor.YELLOW))
                        .append(Component.text("'의 토지 ${chunkCount}개 청크가 반환되었습니다.", NamedTextColor.WHITE))
                )
            }
        }
    }
    
    // ===== 마을 환불 시스템 =====

    /**
     * 마을 토지 환불 계산
     */
    private fun calculateVillageRefund(
        chunks: Set<org.bukkit.Chunk>,
        villageInfo: com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageInfo
    ): VillageRefundResult {
        val advancedManager = advancedLandManager ?: return VillageRefundResult(emptyList(), RefundPolicy.NONE, emptyMap())

        val refundItems = mutableListOf<org.bukkit.inventory.ItemStack>()
        val refundDetails = mutableMapOf<RefundPolicy, Int>()
        val currentTime = System.currentTimeMillis()

        chunks.forEach { chunk ->
            val claimInfo = advancedManager.getClaimOwner(chunk.world.name, chunk.x, chunk.z)
            if (claimInfo != null && claimInfo.claimType == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
                val policy = determineRefundPolicy(claimInfo, currentTime)
                val originalRefund = advancedManager.calculateRefundItems(claimInfo.claimCost)

                originalRefund.forEach { item ->
                    val adjustedAmount = (item.amount * policy.refundRate).toInt()
                    if (adjustedAmount > 0) {
                        val adjustedItem = item.clone()
                        adjustedItem.amount = adjustedAmount
                        refundItems.add(adjustedItem)
                    }
                }

                refundDetails[policy] = refundDetails.getOrDefault(policy, 0) + 1
            }
        }

        // 동일 아이템 들을 합침
        val consolidatedItems = consolidateItems(refundItems)
        val primaryPolicy = refundDetails.maxByOrNull { it.value }?.key ?: RefundPolicy.HALF

        return VillageRefundResult(consolidatedItems, primaryPolicy, refundDetails)
    }

    /**
     * 동일한 아이템들을 합침
     */
    private fun consolidateItems(items: List<org.bukkit.inventory.ItemStack>): List<org.bukkit.inventory.ItemStack> {
        val itemMap = mutableMapOf<org.bukkit.Material, Int>()

        items.forEach { item ->
            itemMap[item.type] = itemMap.getOrDefault(item.type, 0) + item.amount
        }

        return itemMap.map { (material, amount) ->
            org.bukkit.inventory.ItemStack(material, amount)
        }
    }

    /**
     * 마을 환불 결과 데이터 클래스
     */
    data class VillageRefundResult(
        val refundItems: List<org.bukkit.inventory.ItemStack>,
        val primaryPolicy: RefundPolicy,
        val policyBreakdown: Map<RefundPolicy, Int>
    )

    /**
     * 환불 상세 정보 표시
     */
    private fun showRefundDetails(player: Player, refundResult: VillageRefundResult, chunkCount: Int) {
        if (refundResult.refundItems.isEmpty()) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("💰 ", NamedTextColor.YELLOW))
                    .append(Component.text("환불 아이템이 없습니다. (무료 토지 또는 환불 불가)", NamedTextColor.GRAY))
            )
            return
        }

        player.sendMessage(
            Component.text()
                .append(Component.text("💰 ", NamedTextColor.GOLD))
                .append(Component.text("환불 정보", NamedTextColor.WHITE, TextDecoration.BOLD))
        )

        player.sendMessage(
            Component.text()
                .append(Component.text("   전체 정책: ", NamedTextColor.GRAY))
                .append(Component.text(refundResult.primaryPolicy.displayName, NamedTextColor.GREEN))
                .append(Component.text(" (${refundResult.primaryPolicy.description})", NamedTextColor.DARK_GRAY))
        )

        if (refundResult.policyBreakdown.size > 1) {
            player.sendMessage(Component.text("   상세 정책 분류:", NamedTextColor.GRAY))
            refundResult.policyBreakdown.forEach { (policy, count) ->
                player.sendMessage(
                    Component.text()
                        .append(Component.text("     • ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(policy.displayName, NamedTextColor.YELLOW))
                        .append(Component.text(": ${count}개 청크", NamedTextColor.WHITE))
                )
            }
        }

        player.sendMessage(Component.text("   환불 아이템:", NamedTextColor.GRAY))
        refundResult.refundItems.forEach { item ->
            val itemName = when (item.type) {
                org.bukkit.Material.IRON_INGOT -> "철괴"
                org.bukkit.Material.DIAMOND -> "다이아몴드"
                org.bukkit.Material.NETHERITE_INGOT -> "네더라이트 주괴"
                else -> item.type.name
            }
            player.sendMessage(
                Component.text()
                    .append(Component.text("     • ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("${itemName} ", NamedTextColor.AQUA))
                    .append(Component.text("x${item.amount}", NamedTextColor.WHITE))
            )
        }
    }
    
    /**
     * 마을 권한 관리 기능 - 구성원의 역할 변경
     * 사용법: 
     * - /땅 마을권한 <플레이어> 부이장  - 플레이어를 부이장으로 승진
     * - /땅 마을권한 <플레이어> 구성원  - 플레이어를 구성원으로 변경 (부이장 해임)
     * - /땅 마을권한 목록            - 현재 마을의 권한 구조 확인
     */
    private fun handleVillagePermissions(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 마을 토지인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("이 곳은 마을 토지가 아닙니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 사용법 확인
        if (args.isEmpty()) {
            sendVillagePermissionsUsage(player)
            return
        }
        
        // 3. 마을 구성원 및 권한 확인
        val villageMembers = advancedManager.getVillageMembers(villageId)
        val playerMember = villageMembers.find { it.memberUuid == player.uniqueId }
        
        if (playerMember == null) {
            player.sendMessage(Component.text("마을 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        // 이장만 권한 관리 가능
        if (playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR) {
            player.sendMessage(Component.text("이장만 마을 구성원의 권한을 관리할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        // 4. 하위 명령어 처리
        when (args[0].lowercase()) {
            "목록", "list" -> showVillagePermissionsList(player, villageMembers, villageId)
            else -> handleRoleChange(player, args, villageMembers, villageId)
        }
    }
    
    /**
     * 마을 권한 사용법 안내
     */
    private fun sendVillagePermissionsUsage(player: Player) {
        player.sendMessage(
            Component.text()
                .append(Component.text("--- ", NamedTextColor.GOLD))
                .append(Component.text("마을 권한 관리", NamedTextColor.WHITE))
                .append(Component.text(" ---", NamedTextColor.GOLD))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("/땅 마을권한 목록", NamedTextColor.AQUA))
                .append(Component.text(" - 현재 마을 권한 구조 확인", NamedTextColor.GRAY))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("/땅 마을권한 <플레이어> 부이장", NamedTextColor.GREEN))
                .append(Component.text(" - 구성원을 부이장으로 승진", NamedTextColor.GRAY))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("/땅 마을권한 <플레이어> 구성원", NamedTextColor.YELLOW))
                .append(Component.text(" - 부이장을 구성원으로 변경", NamedTextColor.GRAY))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("💡 ", NamedTextColor.YELLOW))
                .append(Component.text("이장만 권한을 관리할 수 있습니다.", NamedTextColor.GRAY))
        )
    }
    
    /**
     * 마을 권한 구조 표시
     */
    private fun showVillagePermissionsList(
        player: Player,
        members: List<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageMember>,
        villageId: Int
    ) {
        val villageInfo = advancedLandManager?.getVillageInfo(villageId)
        if (villageInfo == null) {
            player.sendMessage(Component.text("마을 정보를 조회할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        player.sendMessage(
            Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("👑 ", NamedTextColor.GOLD))
                .append(Component.text("마을 '${villageInfo.villageName}' 권한 구조", NamedTextColor.WHITE, TextDecoration.BOLD))
        )
        
        player.sendMessage(Component.text(""))
        
        // 권한별 설명
        player.sendMessage(
            Component.text()
                .append(Component.text("📋 ", NamedTextColor.AQUA))
                .append(Component.text("권한 설명", NamedTextColor.WHITE, TextDecoration.BOLD))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("  👑 이장: ", NamedTextColor.GOLD))
                .append(Component.text("모든 권한 (구성원 관리, 토지 관리, 권한 변경)", NamedTextColor.GRAY))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("  🥈 부이장: ", NamedTextColor.AQUA))
                .append(Component.text("구성원 초대/추방, 토지 반환", NamedTextColor.GRAY))
        )
        
        player.sendMessage(
            Component.text()
                .append(Component.text("  👤 구성원: ", NamedTextColor.GREEN))
                .append(Component.text("마을 토지 사용", NamedTextColor.GRAY))
        )
        
        player.sendMessage(Component.text(""))
        
        // 현재 구성원 목록
        player.sendMessage(
            Component.text()
                .append(Component.text("👥 ", NamedTextColor.AQUA))
                .append(Component.text("현재 구성원 (${members.size}명)", NamedTextColor.WHITE, TextDecoration.BOLD))
        )
        
        val sortedMembers = members.sortedWith(compareBy<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageMember> { 
            when (it.role) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR -> 0
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR -> 1
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER -> 2
            }
        }.thenBy { it.memberName })
        
        sortedMembers.forEach { member ->
            val roleIcon = when (member.role) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR -> "👑"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR -> "🥈"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER -> "👤"
            }
            
            val roleColor = when (member.role) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR -> NamedTextColor.GOLD
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR -> NamedTextColor.AQUA
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER -> NamedTextColor.GREEN
            }
            
            val onlineStatus = if (org.bukkit.Bukkit.getPlayer(member.memberUuid) != null) "🟢" else "🔴"
            
            player.sendMessage(
                Component.text()
                    .append(Component.text("  $roleIcon ", roleColor))
                    .append(Component.text(member.memberName, NamedTextColor.WHITE))
                    .append(Component.text(" $onlineStatus", NamedTextColor.WHITE))
            )
        }
        
        player.sendMessage(
            Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
        )
    }
    
    /**
     * 역할 변경 처리
     */
    private fun handleRoleChange(
        player: Player,
        args: Array<out String>,
        members: List<com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageMember>,
        villageId: Int
    ) {
        if (args.size < 3) {
            player.sendMessage(Component.text("사용법: /땅 마을권한 <플레이어> <부이장|구성원>", NamedTextColor.RED))
            return
        }
        
        val targetPlayerName = args[1]
        val newRoleString = args[2]
        
        // 새로운 역할 파싱
        val newRole = when (newRoleString.lowercase()) {
            "부이장", "deputy", "deputy_mayor" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR
            "구성원", "member" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER
            else -> {
                player.sendMessage(Component.text("올바른 역할을 입력하세요: 부이장, 구성원", NamedTextColor.RED))
                return
            }
        }
        
        // 대상 구성원 찾기
        val targetMember = members.find { it.memberName.equals(targetPlayerName, ignoreCase = true) }
        if (targetMember == null) {
            player.sendMessage(Component.text("'$targetPlayerName'은(는) 마을 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        // 이장은 역할 변경 불가
        if (targetMember.role == com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR) {
            player.sendMessage(Component.text("이장의 역할은 변경할 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 이미 같은 역할인지 확인
        if (targetMember.role == newRole) {
            val roleText = when (newRole) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR -> "부이장"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER -> "구성원"
                else -> "알 수 없음"
            }
            player.sendMessage(Component.text("${targetMember.memberName}님은 이미 ${roleText}입니다.", NamedTextColor.YELLOW))
            return
        }
        
        // 역할 변경 실행
        val changeResult = advancedLandManager?.changeVillageMemberRole(villageId, targetMember.memberUuid, newRole)
        if (changeResult == true) {
            val roleText = when (newRole) {
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR -> "부이장"
                com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MEMBER -> "구성원"
                else -> "알 수 없음"
            }
            
            player.sendMessage(
                Component.text()
                    .append(Component.text("✅ ", NamedTextColor.GREEN))
                    .append(Component.text("${targetMember.memberName}님의 역할을 ${roleText}으로 변경했습니다.", NamedTextColor.WHITE))
            )
            
            // 대상 플레이어에게 알림
            val targetPlayer = org.bukkit.Bukkit.getPlayer(targetMember.memberUuid)
            if (targetPlayer != null) {
                targetPlayer.sendMessage(
                    Component.text()
                        .append(Component.text("👑 ", NamedTextColor.GOLD))
                        .append(Component.text("당신의 마을 역할이 ${roleText}으로 변경되었습니다!", NamedTextColor.YELLOW))
                )
            }
        } else {
            player.sendMessage(Component.text("역할 변경 중 오류가 발생했습니다.", NamedTextColor.RED))
        }
    }

    /**
     * 마을 설정 GUI 열기 - 이장과 부이장이 마을을 관리할 수 있는 GUI 인터페이스
     */
    private fun handleVillageSettings(player: Player) {
        val advancedManager = advancedLandManager
        val settingsGUI = villageSettingsGUI
        
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (settingsGUI == null) {
            player.sendMessage(Component.text("마을 설정 GUI 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        
        // 1. 현재 청크가 마을 토지인지 확인
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("이 곳은 마을 토지가 아닙니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 2. 플레이어가 마을 구성원인지 확인하고 역할 조회
        val villageMembers = advancedManager.getVillageMembers(villageId)
        val playerMember = villageMembers.find { it.memberUuid == player.uniqueId }
        
        if (playerMember == null) {
            player.sendMessage(Component.text("마을 구성원이 아닙니다.", NamedTextColor.RED))
            return
        }
        
        // 3. 권한 확인 (이장 또는 부이장만 설정 GUI 이용 가능)
        if (playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.MAYOR &&
            playerMember.role != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.VillageRole.DEPUTY_MAYOR) {
            player.sendMessage(Component.text("이장 또는 부이장만 마을 설정을 이용할 수 있습니다.", NamedTextColor.RED))
            return
        }
        
        // 4. GUI 열기
        settingsGUI.open(player, villageId, playerMember.role)
        
        player.sendMessage(
            Component.text()
                .append(Component.text("🏛️ ", NamedTextColor.GREEN))
                .append(Component.text("마을 설정 GUI를 열었습니다.", NamedTextColor.WHITE))
        )
    }
    
    /**
     * 마을 토지 클레이밍 처리
     * @param player 명령을 실행한 플레이어
     * @param args 명령어 인자들
     */
    private fun handleVillageClaim(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        
        // 자원 타입 파싱
        val resourceType = if (args.size > 1) {
            when (args[1].lowercase()) {
                "철", "iron" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.IRON_INGOT
                "다이아", "diamond" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.DIAMOND
                "네더라이트", "netherite" -> com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimResourceType.NETHERITE_INGOT
                else -> {
                    player.sendMessage(Component.text("올바르지 않은 자원 타입입니다. 사용 가능: 철, 다이아, 네더라이트", NamedTextColor.RED))
                    return
                }
            }
        } else null
        
        val result = advancedManager.claimChunkForVillage(player, chunk, resourceType)
        
        if (result.success) {
            player.sendMessage(Component.text(result.message, NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text(result.message, NamedTextColor.RED))
        }
    }
    
    // ===== 마을 해체 및 이장 양도 시스템 =====
    
    /**
     * 마을 해체 확정 처리
     */
    private fun handleVillageDisbandConfirm(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        // VillageSettingsGUI에서 해체 확정 대기 상태 확인
        val villageGUI = villageSettingsGUI
        if (villageGUI == null) {
            player.sendMessage(Component.text("마을 설정 GUI가 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        // 해체 확정 대기 상태인지 확인 (VillageSettingsGUI의 pendingDisbandVillages 맵 확인)
        // 임시로 현재 위치 청크의 마을 정보를 통해 해체 진행
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("마을 토지에서만 마을 해체가 가능합니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 마을 해체 실행
        val result = advancedManager.disbandVillage(player, villageId)
        if (result.success) {
            player.sendMessage(Component.text(result.message, NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text(result.message, NamedTextColor.RED))
        }
    }
    
    /**
     * 이장 양도 처리
     * 사용법: /땅 이장양도 <플레이어>
     */
    private fun handleMayorTransfer(player: Player, args: Array<out String>) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /땅 이장양도 <플레이어>", NamedTextColor.RED))
            return
        }
        
        val chunk = player.location.chunk
        val worldName = chunk.world.name
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)
        
        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }
        
        if (claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("마을 토지에서만 이장 양도가 가능합니다.", NamedTextColor.RED))
            return
        }
        
        val villageId = claimInfo.villageId
        if (villageId == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 새로운 이장 대상 플레이어 검증
        val targetPlayerName = args[1]
        val targetOfflinePlayer = org.bukkit.Bukkit.getOfflinePlayer(targetPlayerName)
        
        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline) {
            player.sendMessage(Component.text("'$targetPlayerName'은(는) 존재하지 않는 플레이어입니다.", NamedTextColor.RED))
            return
        }
        
        // 대상자가 온라인인지 확인
        val targetPlayer = targetOfflinePlayer.player
        if (targetPlayer == null) {
            player.sendMessage(Component.text("대상 플레이어가 현재 온라인이 아닙니다. 이장 양도는 온라인 플레이어에게만 가능합니다.", NamedTextColor.RED))
            return
        }

        // 마을 정보 조회
        val villageInfo = advancedManager.getVillageInfo(villageId)
        if (villageInfo == null) {
            player.sendMessage(Component.text("마을 정보를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }

        // 기존 이장 양도 요청이 있는지 확인
        if (pendingMayorTransfers.containsKey(targetPlayer.uniqueId)) {
            player.sendMessage(Component.text("해당 플레이어에게 이미 보낸 이장 양도 요청이 있습니다.", NamedTextColor.RED))
            return
        }

        // 이장 양도 요청 생성 및 전송
        val transferInvitation = MayorTransferInvitation(
            villageId = villageId,
            villageName = villageInfo.villageName,
            currentMayorUuid = player.uniqueId,
            currentMayorName = player.name,
            newMayorUuid = targetPlayer.uniqueId,
            transferTime = System.currentTimeMillis()
        )

        pendingMayorTransfers[targetPlayer.uniqueId] = transferInvitation

        // 요청자에게 알림
        player.sendMessage(
            Component.text()
                .append(Component.text("👑 ", NamedTextColor.GOLD))
                .append(Component.text("이장 양도 요청을 ", NamedTextColor.WHITE))
                .append(Component.text(targetPlayerName, NamedTextColor.AQUA))
                .append(Component.text("님에게 보냈습니다.", NamedTextColor.WHITE))
        )

        // 대상자에게 이장 양도 요청 알림
        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("👑 ", NamedTextColor.GOLD))
                .append(Component.text("마을 '", NamedTextColor.WHITE))
                .append(Component.text(villageInfo.villageName, NamedTextColor.YELLOW))
                .append(Component.text("'의 ", NamedTextColor.WHITE))
                .append(Component.text(player.name, NamedTextColor.AQUA))
                .append(Component.text("님이 이장을 양도하려고 합니다.", NamedTextColor.WHITE))
        )

        val acceptButton = Component.text()
            .append(Component.text("[수락]", NamedTextColor.GREEN, TextDecoration.BOLD))
            .clickEvent(ClickEvent.runCommand("/땅 이장양도수락"))
            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 이장 양도를 수락합니다.")))

        val rejectButton = Component.text()
            .append(Component.text("[거절]", NamedTextColor.RED, TextDecoration.BOLD))
            .clickEvent(ClickEvent.runCommand("/땅 이장양도거절"))
            .hoverEvent(HoverEvent.showText(Component.text("클릭하여 이장 양도를 거절합니다.")))

        targetPlayer.sendMessage(
            Component.text()
                .append(Component.text("    ", NamedTextColor.WHITE))
                .append(acceptButton)
                .append(Component.text("  ", NamedTextColor.WHITE))
                .append(rejectButton)
                .append(Component.text("  (5분 내에 응답하세요)", NamedTextColor.GRAY))
        )
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
                "클레임", "반환", "목록", "비용", "환불정보", "환불내역", "상태", // 새로운 명령어
                "마을생성", "마을초대", "마을추방", "마을정보", "마을권한", "마을반환", "마을설정", "마을클레임", "마을해체확정", "이장양도", "이장양도수락", "이장양도거절" // 마을 명령어
            ).filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        if (args.size == 2) {
            when (args[0].lowercase()) {
                "친구추가", "친구삭제", "마을초대", "마을추방", "이장양도" -> {
                    return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                }
                "클레임", "마을클레임" -> {
                    return mutableListOf("철", "다이아", "네더라이트").filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                }
            }
        }
        return mutableListOf()
    }

    /**
     * 환불 정보 조회 - 현재 위치 토지의 예상 환불 정보 표시
     */
    private fun handleRefundInfo(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }

        val chunk = player.location.chunk
        val worldName = chunk.world.name
        val claimInfo = advancedManager.getClaimOwner(worldName, chunk.x, chunk.z)

        if (claimInfo == null) {
            player.sendMessage(Component.text("이 청크는 클레이밍되지 않았습니다.", NamedTextColor.RED))
            return
        }

        if (claimInfo.ownerUuid != player.uniqueId && claimInfo.claimType != com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.ClaimType.VILLAGE) {
            player.sendMessage(Component.text("이 청크의 소유자가 아닙니다.", NamedTextColor.RED))
            return
        }

        // 현재 정책 결정
        val currentTime = System.currentTimeMillis()
        val policy = determineRefundPolicy(claimInfo, currentTime)
        val refundItems = calculateRefund(claimInfo)

        // 환부 정책 적용
        val adjustedRefundItems = refundItems.map { item ->
            val adjustedItem = item.clone()
            adjustedItem.amount = (item.amount * policy.refundRate).toInt()
            adjustedItem
        }.filter { it.amount > 0 }

        // 현재 소유 기간 계산
        val ownershipDuration = currentTime - claimInfo.createdAt
        val days = ownershipDuration / (24 * 60 * 60 * 1000L)
        val hours = (ownershipDuration % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L)

        // 정보 표시
        player.sendMessage(
            Component.text()
                .append(Component.text("💰 ", NamedTextColor.GOLD))
                .append(Component.text("토지 환불 정보", NamedTextColor.WHITE, TextDecoration.BOLD))
        )

        player.sendMessage(
            Component.text()
                .append(Component.text("   위치: ", NamedTextColor.GRAY))
                .append(CoordinateDisplayUtils.formatCompactCoordinates(chunk))
        )

        player.sendMessage(
            Component.text()
                .append(Component.text("   소유기간: ", NamedTextColor.GRAY))
                .append(Component.text("${days}일 ${hours}시간", NamedTextColor.WHITE))
        )

        player.sendMessage(
            Component.text()
                .append(Component.text("   현재 정책: ", NamedTextColor.GRAY))
                .append(Component.text(policy.displayName, NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" (${policy.description})", NamedTextColor.DARK_GRAY))
        )

        if (adjustedRefundItems.isNotEmpty()) {
            player.sendMessage(Component.text("   예상 환불:", NamedTextColor.GRAY))
            adjustedRefundItems.forEach { item ->
                val itemName = when (item.type) {
                    org.bukkit.Material.IRON_INGOT -> "철괴"
                    org.bukkit.Material.DIAMOND -> "다이아몬드"
                    org.bukkit.Material.NETHERITE_INGOT -> "네더라이트 주괴"
                    else -> item.type.name
                }
                player.sendMessage(
                    Component.text()
                        .append(Component.text("     • ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("${itemName} ", NamedTextColor.AQUA))
                        .append(Component.text("x${item.amount}", NamedTextColor.WHITE))
                )
            }
        } else {
            player.sendMessage(
                Component.text()
                    .append(Component.text("   ⚠️ ", NamedTextColor.YELLOW))
                    .append(Component.text("환불 아이템이 없습니다.", NamedTextColor.GRAY))
            )
        }

        player.sendMessage(
            Component.text()
                .append(Component.text("💡 ", NamedTextColor.YELLOW))
                .append(Component.text("팁: '/땅 반환' 명령어로 실제 반환할 수 있습니다.", NamedTextColor.GOLD))
        )
    }

    /**
     * 환불 내역 조회 - 최근 환불 내역 표시
     */
    private fun handleRefundHistory(player: Player) {
        // 최근 환불은 청크 소유권 내역에서 추출
        val recentReturns = landManager.getClaimHistory(player.location.chunk)
            .filter {
                it.previousOwnerUuid == player.uniqueId &&
                (it.reason.contains("자발적 포기") ||
                it.reason.contains("마을 반환"))
            }
            .take(10) // 최근 10개

        if (recentReturns.isEmpty()) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("📄 ", NamedTextColor.GRAY))
                    .append(Component.text("최근 환불 내역이 없습니다.", NamedTextColor.GRAY))
            )
            return
        }

        player.sendMessage(
            Component.text()
                .append(Component.text("📄 ", NamedTextColor.GOLD))
                .append(Component.text("최근 환불 내역", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" (최근 ${recentReturns.size}건)", NamedTextColor.GRAY))
        )

        val dateFormat = java.text.SimpleDateFormat("MM-dd HH:mm")
        recentReturns.forEach { history ->
            val formattedDate = dateFormat.format(java.util.Date(history.unclaimedAt.time))
            // 청크 좌표는 현재 위치를 사용 (개선 가능)
            val currentChunk = player.location.chunk

            player.sendMessage(
                Component.text()
                    .append(Component.text("  • ", NamedTextColor.YELLOW))
                    .append(Component.text("$formattedDate ", NamedTextColor.GRAY))
                    .append(Component.text("청크 (", NamedTextColor.WHITE))
                    .append(Component.text("${currentChunk.x}, ${currentChunk.z}", NamedTextColor.AQUA))
                    .append(Component.text(")", NamedTextColor.WHITE))
                    .append(Component.text(" - ${history.reason}", NamedTextColor.GRAY))
            )
        }

        player.sendMessage(
            Component.text()
                .append(Component.text("💡 ", NamedTextColor.YELLOW))
                .append(Component.text("자세한 내역은 '/땅 기록' 명령어를 사용하세요.", NamedTextColor.GOLD))
        )
    }

    // ===== 이장 양도 수락/거절 시스템 =====

    /**
     * 이장 양도 수락 처리
     */
    private fun handleMayorTransferAccept(player: Player) {
        val advancedManager = advancedLandManager
        if (advancedManager == null) {
            player.sendMessage(Component.text("고급 토지 시스템이 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }

        // 대기 중인 이장 양도 요청 확인
        val transferInvitation = pendingMayorTransfers[player.uniqueId]
        if (transferInvitation == null) {
            player.sendMessage(Component.text("받은 이장 양도 요청이 없습니다.", NamedTextColor.RED))
            return
        }

        // 요청 만료 확인
        if (System.currentTimeMillis() > transferInvitation.expiresAt) {
            pendingMayorTransfers.remove(player.uniqueId)
            player.sendMessage(Component.text("이장 양도 요청이 만료되었습니다.", NamedTextColor.RED))
            return
        }

        // 이장 양도 실행
        val currentMayorPlayer = org.bukkit.Bukkit.getPlayer(transferInvitation.currentMayorUuid)
        if (currentMayorPlayer == null) {
            player.sendMessage(Component.text("현재 이장이 온라인이 아닙니다.", NamedTextColor.RED))
            pendingMayorTransfers.remove(player.uniqueId)
            return
        }

        val result = advancedManager.transferVillageMayorship(
            currentMayorPlayer,
            transferInvitation.villageId,
            player.uniqueId,
            player.name
        )

        // 대기 목록에서 제거
        pendingMayorTransfers.remove(player.uniqueId)

        if (result.success) {
            player.sendMessage(Component.text(result.message, NamedTextColor.GREEN))

            // 이전 이장에게 알림
            currentMayorPlayer.sendMessage(
                Component.text()
                    .append(Component.text("✅ ", NamedTextColor.GREEN))
                    .append(Component.text(player.name, NamedTextColor.AQUA))
                    .append(Component.text("님이 이장 양도를 수락했습니다!", NamedTextColor.WHITE))
            )
        } else {
            player.sendMessage(Component.text(result.message, NamedTextColor.RED))
        }
    }

    /**
     * 이장 양도 거절 처리
     */
    private fun handleMayorTransferReject(player: Player) {
        // 대기 중인 이장 양도 요청 확인
        val transferInvitation = pendingMayorTransfers[player.uniqueId]
        if (transferInvitation == null) {
            player.sendMessage(Component.text("받은 이장 양도 요청이 없습니다.", NamedTextColor.RED))
            return
        }

        // 대기 목록에서 제거
        pendingMayorTransfers.remove(player.uniqueId)

        // 거절 알림
        player.sendMessage(
            Component.text()
                .append(Component.text("❌ ", NamedTextColor.RED))
                .append(Component.text("마을 '", NamedTextColor.WHITE))
                .append(Component.text(transferInvitation.villageName, NamedTextColor.YELLOW))
                .append(Component.text("'의 이장 양도를 거절했습니다.", NamedTextColor.WHITE))
        )

        // 이장에게 거절 알림
        val currentMayor = org.bukkit.Bukkit.getPlayer(transferInvitation.currentMayorUuid)
        currentMayor?.sendMessage(
            Component.text()
                .append(Component.text("❌ ", NamedTextColor.RED))
                .append(Component.text(player.name, NamedTextColor.AQUA))
                .append(Component.text("님이 이장 양도를 거절했습니다.", NamedTextColor.WHITE))
        )
    }
} 
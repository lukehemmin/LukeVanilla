package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * 마을 설정 GUI 시스템
 * 마을장과 부마을장이 마을을 관리할 수 있는 인터페이스를 제공합니다.
 */
class VillageSettingsGUI(
    private val plugin: Main,
    private val advancedManager: AdvancedLandManager
) : Listener {

    private val openInventories = mutableMapOf<UUID, VillageGUISession>()
    private val inventoryTitle = "마을 설정"
    
    // 마을 해체 확정 대기 중인 플레이어들 (플레이어 UUID -> 만료 시간)
    private val pendingDisbandVillages = mutableMapOf<UUID, Long>()

    /**
     * GUI 세션 정보를 담는 데이터 클래스
     */
    private data class VillageGUISession(
        val villageId: Int,
        val playerRole: VillageRole,
        val currentPage: GUIPage = GUIPage.MAIN,
        val selectedMember: VillageMember? = null,
        val permissionsPage: Int = 0  // 권한 페이지 번호 (페이지네이션용)
    )

    /**
     * GUI 페이지 타입
     */
    private enum class GUIPage {
        MAIN,           // 메인 페이지
        MEMBER_MANAGE,  // 멤버 관리
        MEMBER_DETAIL,  // 개별 멤버 상세 관리
        VILLAGE_INFO,   // 마을 정보
        PERMISSIONS,    // 권한 설정
        MEMBER_PERMISSIONS  // 개별 멤버 권한 설정
    }

    /**
     * 마을 설정 GUI를 엽니다.
     */
    fun open(player: Player, villageId: Int, playerRole: VillageRole) {
        val inventory = Bukkit.createInventory(player, 54, Component.text(inventoryTitle))
        val session = VillageGUISession(villageId, playerRole)
        
        openInventories[player.uniqueId] = session
        updateGUI(player, inventory, session)
        player.openInventory(inventory)
    }

    /**
     * GUI 내용을 업데이트합니다.
     */
    private fun updateGUI(player: Player, inventory: Inventory, session: VillageGUISession) {
        when (session.currentPage) {
            GUIPage.MAIN -> renderMainPage(inventory, session)
            GUIPage.MEMBER_MANAGE -> renderMemberManagePage(player, inventory, session)
            GUIPage.MEMBER_DETAIL -> renderMemberDetailPage(inventory, session)
            GUIPage.VILLAGE_INFO -> renderVillageInfoPage(inventory, session)
            GUIPage.PERMISSIONS -> renderPermissionsPage(inventory, session)
            GUIPage.MEMBER_PERMISSIONS -> renderMemberPermissionsPage(inventory, session)
        }
    }

    /**
     * 메인 페이지를 렌더링합니다.
     */
    private fun renderMainPage(inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)
        
        val villageInfo = advancedManager.getVillageInfo(session.villageId)
        if (villageInfo == null) {
            setErrorItem(inventory, 22, "마을 정보를 불러올 수 없습니다.")
            return
        }

        // 마을 정보 아이템 (11번 슬롯)
        val villageInfoItem = ItemStack(Material.EMERALD).apply {
            editMeta { meta ->
                meta.displayName(Component.text("📋 마을 정보", NamedTextColor.GREEN, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("마을 이름: ${villageInfo.villageName}", NamedTextColor.WHITE))
                lore.add(Component.text("마을장: ${villageInfo.mayorName}", NamedTextColor.GRAY))
                lore.add(Component.text("설립일: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(Date(villageInfo.createdAt))}", NamedTextColor.GRAY))
                lore.add(Component.text(""))
                lore.add(Component.text("클릭하여 자세한 정보 보기", NamedTextColor.YELLOW))
                meta.lore(lore)
            }
        }
        inventory.setItem(11, villageInfoItem)

        // 멤버 관리 아이템 (15번 슬롯)
        val memberManageItem = ItemStack(Material.PLAYER_HEAD).apply {
            editMeta { meta ->
                meta.displayName(Component.text("👥 멤버 관리", NamedTextColor.BLUE, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("마을 멤버를 관리합니다.", NamedTextColor.GRAY))
                lore.add(Component.text(""))
                if (session.playerRole == VillageRole.MAYOR) {
                    lore.add(Component.text("• 멤버 역할 변경", NamedTextColor.WHITE))
                    lore.add(Component.text("• 멤버 추방", NamedTextColor.WHITE))
                } else {
                    lore.add(Component.text("• 멤버 목록 조회", NamedTextColor.WHITE))
                }
                lore.add(Component.text(""))
                lore.add(Component.text("클릭하여 멤버 관리하기", NamedTextColor.YELLOW))
                meta.lore(lore)
            }
        }
        inventory.setItem(15, memberManageItem)

        // 마을 통계 아이템 (29번 슬롯)
        val statsItem = ItemStack(Material.BOOK).apply {
            editMeta { meta ->
                meta.displayName(Component.text("📊 마을 통계", NamedTextColor.AQUA, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                
                val memberCount = advancedManager.getVillageMembers(session.villageId).size
                val chunkCount = advancedManager.getVillageChunkCount(session.villageId)
                
                lore.add(Component.text("총 멤버 수: ${memberCount}명", NamedTextColor.WHITE))
                lore.add(Component.text("소유 청크: ${chunkCount}개", NamedTextColor.WHITE))
                lore.add(Component.text(""))
                lore.add(Component.text("클릭하여 자세한 통계 보기", NamedTextColor.YELLOW))
                meta.lore(lore)
            }
        }
        inventory.setItem(29, statsItem)

        // 마을장 전용 기능들
        if (session.playerRole == VillageRole.MAYOR) {
            // 마을 해체 아이템 (33번 슬롯)
            val disbandItem = ItemStack(Material.TNT).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("💥 마을 해체", NamedTextColor.RED, TextDecoration.BOLD))
                    val lore = mutableListOf<Component>()
                    lore.add(Component.text("⚠️ 위험한 작업입니다!", NamedTextColor.RED, TextDecoration.BOLD))
                    lore.add(Component.text(""))
                    lore.add(Component.text("마을을 완전히 해체합니다.", NamedTextColor.GRAY))
                    lore.add(Component.text("모든 마을 토지는 개인 토지로 변환됩니다.", NamedTextColor.GRAY))
                    lore.add(Component.text(""))
                    lore.add(Component.text("Shift+클릭으로 해체하기", NamedTextColor.DARK_RED))
                    meta.lore(lore)
                }
            }
            inventory.setItem(33, disbandItem)

            // 권한 관리 아이템 (31번 슬롯)
            val permissionsItem = ItemStack(Material.COMMAND_BLOCK).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("🔐 권한 관리", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                    val lore = mutableListOf<Component>()
                    lore.add(Component.text("마을 멤버들의 권한을 관리합니다.", NamedTextColor.GRAY))
                    lore.add(Component.text(""))
                    lore.add(Component.text("• 건설/파괴 권한", NamedTextColor.WHITE))
                    lore.add(Component.text("• 토지 관리 권한", NamedTextColor.WHITE))
                    lore.add(Component.text("• 멤버 관리 권한", NamedTextColor.WHITE))
                    lore.add(Component.text(""))
                    lore.add(Component.text("클릭하여 권한 관리하기", NamedTextColor.YELLOW))
                    meta.lore(lore)
                }
            }
            inventory.setItem(31, permissionsItem)
        } else if (session.playerRole == VillageRole.DEPUTY_MAYOR) {
            // 부마을장도 권한 관리 가능
            val permissionsItem = ItemStack(Material.COMMAND_BLOCK).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("🔐 권한 관리", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                    val lore = mutableListOf<Component>()
                    lore.add(Component.text("마을 멤버들의 권한을 관리합니다.", NamedTextColor.GRAY))
                    lore.add(Component.text(""))
                    lore.add(Component.text("• 건설/파괴 권한", NamedTextColor.WHITE))
                    lore.add(Component.text("• 컨테이너 사용 권한", NamedTextColor.WHITE))
                    lore.add(Component.text("• 레드스톤 사용 권한", NamedTextColor.WHITE))
                    lore.add(Component.text(""))
                    lore.add(Component.text("클릭하여 권한 관리하기", NamedTextColor.YELLOW))
                    meta.lore(lore)
                }
            }
            inventory.setItem(31, permissionsItem)
        }

        // 닫기 버튼 (49번 슬롯)
        val closeButton = ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("❌ 닫기", NamedTextColor.RED, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("GUI를 닫습니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(49, closeButton)

        // 빈 공간을 유리판으로 채우기
        fillEmptySlots(inventory)
    }

    /**
     * 멤버 관리 페이지를 렌더링합니다.
     */
    private fun renderMemberManagePage(player: Player, inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)
        
        val members = advancedManager.getVillageMembers(session.villageId)
        
        // 타이틀 아이템
        val titleItem = ItemStack(Material.PLAYER_HEAD).apply {
            editMeta { meta ->
                meta.displayName(Component.text("👥 멤버 관리", NamedTextColor.BLUE, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text("총 ${members.size}명의 멤버", NamedTextColor.GRAY)
                ))
            }
        }
        inventory.setItem(4, titleItem)

        // 멤버 목록 표시 (10-34번 슬롯, 2x7 그리드)
        members.forEachIndexed { index, member ->
            if (index >= 25) return@forEachIndexed // 최대 25명까지만 표시
            
            val slot = when {
                index < 7 -> 10 + index
                index < 14 -> 19 + (index - 7)
                index < 21 -> 28 + (index - 14)
                else -> 37 + (index - 21)
            }
            
            val memberItem = createMemberItem(member, session.playerRole == VillageRole.MAYOR)
            inventory.setItem(slot, memberItem)
        }

        // 뒤로가기 버튼
        val backButton = ItemStack(Material.ARROW).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⬅ 뒤로가기", NamedTextColor.YELLOW, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("메인 페이지로 돌아갑니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(45, backButton)

        // 새로고침 버튼
        val refreshButton = ItemStack(Material.LIME_DYE).apply {
            editMeta { meta ->
                meta.displayName(Component.text("🔄 새로고침", NamedTextColor.GREEN, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("멤버 목록을 새로고침합니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(49, refreshButton)

        // 닫기 버튼
        val closeButton = ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("❌ 닫기", NamedTextColor.RED, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("GUI를 닫습니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(53, closeButton)

        fillEmptySlots(inventory)
    }

    /**
     * 마을 정보 페이지를 렌더링합니다.
     */
    private fun renderVillageInfoPage(inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)
        
        val villageInfo = advancedManager.getVillageInfo(session.villageId)
        if (villageInfo == null) {
            setErrorItem(inventory, 22, "마을 정보를 불러올 수 없습니다.")
            return
        }

        val members = advancedManager.getVillageMembers(session.villageId)
        val chunkCount = advancedManager.getVillageChunkCount(session.villageId)

        // 메인 정보 아이템
        val mainInfoItem = ItemStack(Material.EMERALD_BLOCK).apply {
            editMeta { meta ->
                meta.displayName(Component.text("🏛️ ${villageInfo.villageName}", NamedTextColor.GREEN, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text(""))
                lore.add(Component.text("📅 설립일: ${java.text.SimpleDateFormat("yyyy년 MM월 dd일").format(Date(villageInfo.createdAt))}", NamedTextColor.GRAY))
                lore.add(Component.text("👑 마을장: ${villageInfo.mayorName}", NamedTextColor.GOLD))
                lore.add(Component.text("👥 총 멤버: ${members.size}명", NamedTextColor.BLUE))
                lore.add(Component.text("🗺️ 소유 청크: ${chunkCount}개", NamedTextColor.AQUA))
                lore.add(Component.text(""))
                
                // 역할별 멤버 수 계산
                val mayorCount = members.count { it.role == VillageRole.MAYOR }
                val deputyCount = members.count { it.role == VillageRole.DEPUTY_MAYOR }
                val memberCount = members.count { it.role == VillageRole.MEMBER }
                
                lore.add(Component.text("구성원 현황:", NamedTextColor.WHITE, TextDecoration.BOLD))
                lore.add(Component.text("  👑 마을장: ${mayorCount}명", NamedTextColor.GOLD))
                lore.add(Component.text("  🏅 부마을장: ${deputyCount}명", NamedTextColor.YELLOW))
                lore.add(Component.text("  👤 구성원: ${memberCount}명", NamedTextColor.GREEN))
                
                meta.lore(lore)
            }
        }
        inventory.setItem(22, mainInfoItem)

        // 뒤로가기 버튼
        val backButton = ItemStack(Material.ARROW).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⬅ 뒤로가기", NamedTextColor.YELLOW, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("메인 페이지로 돌아갑니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(49, backButton)

        fillEmptySlots(inventory)
    }

    /**
     * 권한 설정 페이지를 렌더링합니다.
     */
    private fun renderPermissionsPage(inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)

        val members = advancedManager.getVillageMembers(session.villageId).filter {
            it.role != VillageRole.MAYOR // 마을장 제외
        }

        if (members.isEmpty()) {
            // 권한을 설정할 멤버가 없는 경우
            val noMembersItem = ItemStack(Material.BARRIER).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("❌ 설정 가능한 멤버 없음", NamedTextColor.RED, TextDecoration.BOLD))
                    val lore = mutableListOf<Component>()
                    lore.add(Component.text("권한을 설정할 멤버가 없습니다.", NamedTextColor.GRAY))
                    lore.add(Component.text("마을장의 권한은 변경할 수 없습니다.", NamedTextColor.GRAY))
                    meta.lore(lore)
                }
            }
            inventory.setItem(22, noMembersItem)
        } else {
            // 타이틀 아이템
            val titleItem = ItemStack(Material.COMMAND_BLOCK).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("🔐 멤버 권한 관리", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                    meta.lore(listOf(
                        Component.text("멤버를 선택하여 권한을 설정하세요", NamedTextColor.GRAY)
                    ))
                }
            }
            inventory.setItem(4, titleItem)

            // 멤버 목록 표시 (10-34번 슬롯)
            members.forEachIndexed { index, member ->
                if (index >= 25) return@forEachIndexed // 최대 25명까지만 표시

                val slot = when {
                    index < 7 -> 10 + index
                    index < 14 -> 19 + (index - 7)
                    index < 21 -> 28 + (index - 14)
                    else -> 37 + (index - 21)
                }

                val memberItem = createPermissionMemberItem(member, session.villageId)
                inventory.setItem(slot, memberItem)
            }
        }

        // 뒤로가기 버튼
        val backButton = ItemStack(Material.ARROW).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⬅ 뒤로가기", NamedTextColor.YELLOW, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("메인 페이지로 돌아갑니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(49, backButton)

        fillEmptySlots(inventory)
    }

    /**
     * 개별 멤버 권한 설정 페이지를 렌더링합니다.
     */
    private fun renderMemberPermissionsPage(inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)

        val member = session.selectedMember ?: return

        // 멤버 정보 아이템 (4번 슬롯)
        val memberInfoItem = ItemStack(Material.PLAYER_HEAD).apply {
            editMeta { meta ->
                meta.displayName(Component.text("👤 ${member.memberName}의 권한 설정", NamedTextColor.AQUA, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text(""))
                lore.add(Component.text("역할: ${getRoleDisplayName(member.role)}", NamedTextColor.WHITE))
                lore.add(Component.text(""))
                lore.add(Component.text("아래 버튼들을 클릭하여", NamedTextColor.GRAY))
                lore.add(Component.text("권한을 설정/해제하세요", NamedTextColor.GRAY))
                meta.lore(lore)
            }
        }
        inventory.setItem(4, memberInfoItem)

        // 현재 멤버의 권한 조회
        val currentPermissions = advancedManager.getMemberPermissions(session.villageId, member.memberUuid)

        // 권한 버튼들 배치
        val permissionSlots = mapOf(
            VillagePermissionType.BUILD to 10,
            VillagePermissionType.BREAK_BLOCKS to 11,
            VillagePermissionType.USE_CONTAINERS to 12,
            VillagePermissionType.USE_REDSTONE to 13,
            VillagePermissionType.INVITE_MEMBERS to 16,
            VillagePermissionType.KICK_MEMBERS to 19,
            VillagePermissionType.MANAGE_LAND to 20,
            VillagePermissionType.EXPAND_LAND to 21,
            VillagePermissionType.REDUCE_LAND to 22,
            VillagePermissionType.MANAGE_ROLES to 25,
            VillagePermissionType.MANAGE_PERMISSIONS to 28,
            VillagePermissionType.RENAME_VILLAGE to 29
        )

        permissionSlots.forEach { (permission, slot) ->
            val hasPermission = currentPermissions.contains(permission)
            val permissionItem = createPermissionToggleItem(permission, hasPermission, member.role, session.playerRole)
            inventory.setItem(slot, permissionItem)
        }

        // 뒤로가기 버튼
        val backButton = ItemStack(Material.ARROW).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⬅ 뒤로가기", NamedTextColor.YELLOW, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("권한 관리 페이지로 돌아갑니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(49, backButton)

        fillEmptySlots(inventory)
    }

    /**
     * 개별 멤버 상세 관리 페이지를 렌더링합니다.
     */
    private fun renderMemberDetailPage(inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)

        val member = session.selectedMember ?: return
        val isManager = session.playerRole == VillageRole.MAYOR || session.playerRole == VillageRole.DEPUTY_MAYOR

        // 멤버 정보 아이템 (13번 슬롯)
        val memberItem = ItemStack(Material.PLAYER_HEAD).apply {
            editMeta { meta ->
                meta.displayName(Component.text("👤 ${member.memberName}", NamedTextColor.AQUA, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text(""))
                lore.add(Component.text("역할: ${getRoleDisplayName(member.role)}", NamedTextColor.WHITE))
                lore.add(Component.text("가입일: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(member.joinedAt))}", NamedTextColor.GRAY))
                lore.add(Component.text("최근 접속: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(member.lastSeen))}", NamedTextColor.GRAY))
                val onlineStatus = if (org.bukkit.Bukkit.getPlayer(member.memberUuid) != null) "🟢 온라인" else "🔴 오프라인"
                lore.add(Component.text("상태: $onlineStatus", NamedTextColor.WHITE))
                meta.lore(lore)
            }
        }
        inventory.setItem(13, memberItem)

        if (isManager && session.playerRole == VillageRole.MAYOR) {
            // 마을장만 가능한 관리 옵션들

            // 역할 변경 아이템 (19번 슬롯)
            val roleItem = when (member.role) {
                VillageRole.MEMBER -> {
                    // 일반 멤버를 부이장으로 승진
                    ItemStack(Material.GOLD_INGOT).apply {
                        editMeta { meta ->
                            meta.displayName(Component.text("⬆️ 부이장으로 승진", NamedTextColor.GOLD, TextDecoration.BOLD))
                            val lore = mutableListOf<Component>()
                            lore.add(Component.text(""))
                            lore.add(Component.text("${member.memberName}님을", NamedTextColor.WHITE))
                            lore.add(Component.text("부이장으로 승진시킵니다.", NamedTextColor.WHITE))
                            lore.add(Component.text(""))
                            lore.add(Component.text("클릭하여 승진시키기", NamedTextColor.YELLOW))
                            meta.lore(lore)
                        }
                    }
                }
                VillageRole.DEPUTY_MAYOR -> {
                    // 부이장을 일반 멤버로 강등
                    ItemStack(Material.IRON_INGOT).apply {
                        editMeta { meta ->
                            meta.displayName(Component.text("⬇️ 일반 멤버로 강등", NamedTextColor.GRAY, TextDecoration.BOLD))
                            val lore = mutableListOf<Component>()
                            lore.add(Component.text(""))
                            lore.add(Component.text("${member.memberName}님을", NamedTextColor.WHITE))
                            lore.add(Component.text("일반 멤버로 강등시킵니다.", NamedTextColor.WHITE))
                            lore.add(Component.text(""))
                            lore.add(Component.text("클릭하여 강등시키기", NamedTextColor.YELLOW))
                            meta.lore(lore)
                        }
                    }
                }
                VillageRole.MAYOR -> {
                    // 이장은 변경 불가
                    ItemStack(Material.BARRIER).apply {
                        editMeta { meta ->
                            meta.displayName(Component.text("❌ 변경 불가", NamedTextColor.RED, TextDecoration.BOLD))
                            val lore = mutableListOf<Component>()
                            lore.add(Component.text(""))
                            lore.add(Component.text("마을장의 역할은", NamedTextColor.GRAY))
                            lore.add(Component.text("변경할 수 없습니다.", NamedTextColor.GRAY))
                            lore.add(Component.text(""))
                            lore.add(Component.text("이장 양도 기능을 사용하세요.", NamedTextColor.YELLOW))
                            meta.lore(lore)
                        }
                    }
                }
            }
            inventory.setItem(19, roleItem)

            // 멤버 추방 아이템 (25번 슬롯) - 마을장은 추방 불가
            if (member.role != VillageRole.MAYOR) {
                val kickItem = ItemStack(Material.TNT).apply {
                    editMeta { meta ->
                        meta.displayName(Component.text("🚫 멤버 추방", NamedTextColor.RED, TextDecoration.BOLD))
                        val lore = mutableListOf<Component>()
                        lore.add(Component.text("⚠️ 위험한 작업입니다!", NamedTextColor.RED, TextDecoration.BOLD))
                        lore.add(Component.text(""))
                        lore.add(Component.text("${member.memberName}님을", NamedTextColor.WHITE))
                        lore.add(Component.text("마을에서 추방합니다.", NamedTextColor.WHITE))
                        lore.add(Component.text(""))
                        lore.add(Component.text("Shift+클릭으로 추방하기", NamedTextColor.DARK_RED))
                        meta.lore(lore)
                    }
                }
                inventory.setItem(25, kickItem)
            }
        } else {
            // 관리 권한이 없는 경우 안내 메시지
            val noPermItem = ItemStack(Material.BARRIER).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("❌ 관리 권한 없음", NamedTextColor.RED, TextDecoration.BOLD))
                    val lore = mutableListOf<Component>()
                    lore.add(Component.text(""))
                    lore.add(Component.text("멤버 관리는 마을장만", NamedTextColor.GRAY))
                    lore.add(Component.text("할 수 있습니다.", NamedTextColor.GRAY))
                    meta.lore(lore)
                }
            }
            inventory.setItem(22, noPermItem)
        }

        // 뒤로가기 버튼 (49번 슬롯)
        val backButton = ItemStack(Material.ARROW).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⬅ 뒤로가기", NamedTextColor.YELLOW, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("멤버 관리 페이지로 돌아갑니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(49, backButton)

        fillEmptySlots(inventory)
    }

    /**
     * 역할 표시명을 반환합니다.
     */
    private fun getRoleDisplayName(role: VillageRole): String {
        return when (role) {
            VillageRole.MAYOR -> "👑 마을장"
            VillageRole.DEPUTY_MAYOR -> "🏅 부마을장"
            VillageRole.MEMBER -> "👤 구성원"
        }
    }

    /**
     * 멤버 아이템을 생성합니다.
     */
    private fun createMemberItem(member: VillageMember, canManage: Boolean): ItemStack {
        val material = when (member.role) {
            VillageRole.MAYOR -> Material.GOLDEN_HELMET
            VillageRole.DEPUTY_MAYOR -> Material.IRON_HELMET
            VillageRole.MEMBER -> Material.LEATHER_HELMET
        }
        
        return ItemStack(material).apply {
            editMeta { meta ->
                val roleText = when (member.role) {
                    VillageRole.MAYOR -> "👑 마을장"
                    VillageRole.DEPUTY_MAYOR -> "🏅 부마을장"
                    VillageRole.MEMBER -> "👤 구성원"
                }

                val roleColor = when (member.role) {
                    VillageRole.MAYOR -> NamedTextColor.GOLD
                    VillageRole.DEPUTY_MAYOR -> NamedTextColor.YELLOW
                    VillageRole.MEMBER -> NamedTextColor.GREEN
                }

                meta.displayName(Component.text("${member.memberName}", roleColor, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("역할: $roleText", roleColor))
                lore.add(Component.text("가입일: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(Date(member.joinedAt))}", NamedTextColor.GRAY))
                lore.add(Component.text("최근 접속: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(member.lastSeen))}", NamedTextColor.GRAY))
                lore.add(Component.text(""))

                if (canManage && member.role != VillageRole.MAYOR) {
                    lore.add(Component.text("좌클릭: 역할 변경", NamedTextColor.YELLOW))
                    lore.add(Component.text("Shift+우클릭: 추방", NamedTextColor.RED))
                } else {
                    lore.add(Component.text("클릭하여 정보 보기", NamedTextColor.GRAY))
                }

                meta.lore(lore)
            }
        }
    }

    /**
     * 권한 설정용 멤버 아이템을 생성합니다.
     */
    private fun createPermissionMemberItem(member: VillageMember, villageId: Int): ItemStack {
        val material = when (member.role) {
            VillageRole.MAYOR -> Material.GOLDEN_HELMET
            VillageRole.DEPUTY_MAYOR -> Material.IRON_HELMET
            VillageRole.MEMBER -> Material.LEATHER_HELMET
        }

        return ItemStack(material).apply {
            editMeta { meta ->
                val roleText = when (member.role) {
                    VillageRole.MAYOR -> "👑 마을장"
                    VillageRole.DEPUTY_MAYOR -> "🏅 부마을장"
                    VillageRole.MEMBER -> "👤 구성원"
                }

                val roleColor = when (member.role) {
                    VillageRole.MAYOR -> NamedTextColor.GOLD
                    VillageRole.DEPUTY_MAYOR -> NamedTextColor.YELLOW
                    VillageRole.MEMBER -> NamedTextColor.GREEN
                }

                meta.displayName(Component.text("${member.memberName}", roleColor, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("역할: $roleText", roleColor))
                lore.add(Component.text(""))

                // 현재 권한 개수 표시
                val currentPermissions = advancedManager.getMemberPermissions(villageId, member.memberUuid)
                lore.add(Component.text("현재 권한: ${currentPermissions.size}개", NamedTextColor.AQUA))
                lore.add(Component.text(""))
                lore.add(Component.text("클릭하여 권한 설정하기", NamedTextColor.YELLOW))

                meta.lore(lore)
            }
        }
    }

    /**
     * 권한 토글 아이템을 생성합니다.
     */
    private fun createPermissionToggleItem(
        permission: VillagePermissionType,
        hasPermission: Boolean,
        memberRole: VillageRole,
        managerRole: VillageRole
    ): ItemStack {
        val (name, description, category) = getPermissionInfo(permission)
        val material = if (hasPermission) Material.LIME_DYE else Material.GRAY_DYE
        val status = if (hasPermission) "✅ 활성화" else "❌ 비활성화"
        val statusColor = if (hasPermission) NamedTextColor.GREEN else NamedTextColor.RED

        // 권한 설정 가능 여부 확인
        val canToggle = canTogglePermission(permission, memberRole, managerRole)

        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text("$name", NamedTextColor.WHITE, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text(""))
                lore.add(Component.text("분류: $category", NamedTextColor.GRAY))
                lore.add(Component.text("설명: $description", NamedTextColor.GRAY))
                lore.add(Component.text(""))
                lore.add(Component.text("상태: $status", statusColor, TextDecoration.BOLD))
                lore.add(Component.text(""))

                if (canToggle) {
                    if (hasPermission) {
                        lore.add(Component.text("클릭하여 권한 해제하기", NamedTextColor.RED))
                    } else {
                        lore.add(Component.text("클릭하여 권한 부여하기", NamedTextColor.GREEN))
                    }
                } else {
                    lore.add(Component.text("⚠️ 이 권한은 변경할 수 없습니다.", NamedTextColor.YELLOW))
                    lore.add(Component.text("마을장만 설정 가능하거나", NamedTextColor.GRAY))
                    lore.add(Component.text("해당 역할에 기본 제공됩니다.", NamedTextColor.GRAY))
                }

                meta.lore(lore)
            }
        }
    }

    /**
     * 권한 정보를 반환합니다.
     */
    private fun getPermissionInfo(permission: VillagePermissionType): Triple<String, String, String> {
        return when (permission) {
            VillagePermissionType.BUILD -> Triple("🔨 건설 허용", "마을 내에서 블록을 설치할 수 있습니다.", "건설")
            VillagePermissionType.BREAK_BLOCKS -> Triple("⛏️ 블록 파괴", "마을 내에서 블록을 파괴할 수 있습니다.", "건설")
            VillagePermissionType.USE_CONTAINERS -> Triple("📦 컨테이너 사용", "상자, 화로 등을 사용할 수 있습니다.", "건설")
            VillagePermissionType.USE_REDSTONE -> Triple("⚡ 레드스톤 사용", "레드스톤 장치를 조작할 수 있습니다.", "건설")
            VillagePermissionType.INVITE_MEMBERS -> Triple("📨 멤버 초대", "새로운 멤버를 마을에 초대할 수 있습니다.", "멤버 관리")
            VillagePermissionType.KICK_MEMBERS -> Triple("🚫 멤버 추방", "마을 멤버를 추방할 수 있습니다.", "멤버 관리")
            VillagePermissionType.MANAGE_ROLES -> Triple("👥 역할 관리", "멤버의 역할을 변경할 수 있습니다.", "멤버 관리")
            VillagePermissionType.EXPAND_LAND -> Triple("📈 토지 확장", "마을 토지를 확장할 수 있습니다.", "토지 관리")
            VillagePermissionType.REDUCE_LAND -> Triple("📉 토지 축소", "마을 토지를 축소할 수 있습니다.", "토지 관리")
            VillagePermissionType.MANAGE_LAND -> Triple("🗺️ 토지 관리", "마을 토지를 종합적으로 관리할 수 있습니다.", "토지 관리")
            VillagePermissionType.MANAGE_PERMISSIONS -> Triple("🔐 권한 관리", "다른 멤버의 권한을 설정할 수 있습니다.", "마을 관리")
            VillagePermissionType.RENAME_VILLAGE -> Triple("✏️ 마을 이름 변경", "마을의 이름을 변경할 수 있습니다.", "마을 관리")
            VillagePermissionType.DISSOLVE_VILLAGE -> Triple("💥 마을 해체", "마을을 해체할 수 있습니다.", "마을 관리")
        }
    }

    /**
     * 특정 권한을 토글할 수 있는지 확인합니다.
     */
    private fun canTogglePermission(
        permission: VillagePermissionType,
        memberRole: VillageRole,
        managerRole: VillageRole
    ): Boolean {
        // 마을장은 모든 권한을 설정할 수 있음
        if (managerRole == VillageRole.MAYOR) {
            return true
        }

        // 부마을장이 설정할 수 없는 권한들
        if (managerRole == VillageRole.DEPUTY_MAYOR) {
            val restrictedPermissions = setOf(
                VillagePermissionType.KICK_MEMBERS,
                VillagePermissionType.MANAGE_ROLES,
                VillagePermissionType.EXPAND_LAND,
                VillagePermissionType.REDUCE_LAND,
                VillagePermissionType.MANAGE_PERMISSIONS,
                VillagePermissionType.RENAME_VILLAGE,
                VillagePermissionType.DISSOLVE_VILLAGE
            )
            return !restrictedPermissions.contains(permission)
        }

        return false
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text(inventoryTitle)) return
        
        val player = event.whoClicked as? Player ?: return
        val session = openInventories[player.uniqueId] ?: return
        
        event.isCancelled = true
        
        when (session.currentPage) {
            GUIPage.MAIN -> handleMainPageClick(player, event, session)
            GUIPage.MEMBER_MANAGE -> handleMemberManageClick(player, event, session)
            GUIPage.MEMBER_DETAIL -> handleMemberDetailClick(player, event, session)
            GUIPage.VILLAGE_INFO -> handleBackButtonClick(player, event, session)
            GUIPage.PERMISSIONS -> handlePermissionsPageClick(player, event, session)
            GUIPage.MEMBER_PERMISSIONS -> handleMemberPermissionsClick(player, event, session)
        }
    }

    /**
     * 메인 페이지 클릭 처리
     */
    private fun handleMainPageClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        when (event.slot) {
            11 -> {
                // 마을 정보 보기
                val newSession = session.copy(currentPage = GUIPage.VILLAGE_INFO)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
            15 -> {
                // 멤버 관리
                val newSession = session.copy(currentPage = GUIPage.MEMBER_MANAGE)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
            29 -> {
                // 통계 (현재는 정보 페이지로 이동)
                val newSession = session.copy(currentPage = GUIPage.VILLAGE_INFO)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
            31 -> {
                // 권한 관리
                if (session.playerRole == VillageRole.MAYOR || session.playerRole == VillageRole.DEPUTY_MAYOR) {
                    val newSession = session.copy(currentPage = GUIPage.PERMISSIONS)
                    openInventories[player.uniqueId] = newSession
                    updateGUI(player, event.inventory, newSession)
                } else {
                    player.sendMessage(Component.text("권한 관리는 마을장과 부마을장만 사용할 수 있습니다.", NamedTextColor.RED))
                }
            }
            33 -> {
                // 마을 해체 (마을장만 가능, Shift+클릭)
                if (session.playerRole == VillageRole.MAYOR && event.isShiftClick) {
                    handleVillageDisband(player, session)
                }
            }
            49 -> {
                // 닫기
                player.closeInventory()
            }
        }
    }

    /**
     * 멤버 관리 페이지 클릭 처리
     */
    private fun handleMemberManageClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        when (event.slot) {
            45 -> {
                // 뒤로가기
                val newSession = session.copy(currentPage = GUIPage.MAIN)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
            49 -> {
                // 새로고침
                updateGUI(player, event.inventory, session)
            }
            53 -> {
                // 닫기
                player.closeInventory()
            }
            else -> {
                // 멤버 클릭 처리
                handleMemberClick(player, event, session)
            }
        }
    }

    /**
     * 뒤로가기 버튼 클릭 처리
     */
    private fun handleBackButtonClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        if (event.slot == 49) {
            val newSession = session.copy(currentPage = GUIPage.MAIN)
            openInventories[player.uniqueId] = newSession
            updateGUI(player, event.inventory, newSession)
        }
    }

    /**
     * 멤버 클릭 처리
     */
    private fun handleMemberClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        val clickedItem = event.currentItem ?: return
        val displayName = clickedItem.itemMeta?.displayName()
        val memberName = if (displayName != null) {
            // Component의 텍스트 내용 추출
            displayName.examinableName().replace("👤 ", "").replace("👑 ", "").replace("🏅 ", "")
        } else {
            return
        }

        // 마을 멤버 목록에서 해당 멤버 찾기
        val members = advancedManager.getVillageMembers(session.villageId)
        val selectedMember = members.find { it.memberName == memberName } ?: return

        // 멤버 상세 관리 페이지로 이동
        val newSession = session.copy(currentPage = GUIPage.MEMBER_DETAIL, selectedMember = selectedMember)
        openInventories[player.uniqueId] = newSession
        updateGUI(player, event.inventory, newSession)
    }

    /**
     * 멤버 상세 관리 페이지 클릭 처리
     */
    private fun handleMemberDetailClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        val member = session.selectedMember ?: return

        when (event.slot) {
            19 -> {
                // 역할 변경 (마을장만 가능)
                if (session.playerRole != VillageRole.MAYOR) {
                    player.sendMessage(Component.text("마을장만 역할을 변경할 수 있습니다.", NamedTextColor.RED))
                    return
                }

                if (member.role == VillageRole.MAYOR) {
                    player.sendMessage(Component.text("마을장의 역할은 변경할 수 없습니다. 이장 양도 기능을 사용하세요.", NamedTextColor.RED))
                    return
                }

                val newRole = when (member.role) {
                    VillageRole.MEMBER -> VillageRole.DEPUTY_MAYOR
                    VillageRole.DEPUTY_MAYOR -> VillageRole.MEMBER
                    VillageRole.MAYOR -> return // 이미 위에서 체크함
                }

                val result = advancedManager.changeVillageMemberRole(session.villageId, member.memberUuid, newRole)
                if (result) {
                    val roleMsg = when (newRole) {
                        VillageRole.DEPUTY_MAYOR -> "부이장으로 승진"
                        VillageRole.MEMBER -> "일반 멤버로 강등"
                        VillageRole.MAYOR -> "이장으로 변경" // 일어나지 않음
                    }

                    player.sendMessage(
                        Component.text()
                            .append(Component.text("✅ ", NamedTextColor.GREEN))
                            .append(Component.text("${member.memberName}님을 ${roleMsg}시켰습니다.", NamedTextColor.WHITE))
                    )

                    // 대상 플레이어에게 알림 (온라인인 경우)
                    val targetPlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(
                            Component.text()
                                .append(Component.text("🎉 ", NamedTextColor.GOLD))
                                .append(Component.text("마을에서 역할이 변경되었습니다: ", NamedTextColor.WHITE))
                                .append(Component.text(getRoleDisplayName(newRole), NamedTextColor.YELLOW))
                        )
                    }

                    // GUI 새로고침 (업데이트된 멤버 정보로)
                    val updatedMembers = advancedManager.getVillageMembers(session.villageId)
                    val updatedMember = updatedMembers.find { it.memberUuid == member.memberUuid }
                    if (updatedMember != null) {
                        val updatedSession = session.copy(selectedMember = updatedMember)
                        openInventories[player.uniqueId] = updatedSession
                        updateGUI(player, event.inventory, updatedSession)
                    }
                } else {
                    player.sendMessage(Component.text("역할 변경 중 오류가 발생했습니다.", NamedTextColor.RED))
                }
            }

            25 -> {
                // 멤버 추방 (Shift+클릭으로만 가능, 마을장만 가능)
                if (!event.isShiftClick) {
                    player.sendMessage(Component.text("추방하려면 Shift+클릭하세요.", NamedTextColor.YELLOW))
                    return
                }

                if (session.playerRole != VillageRole.MAYOR) {
                    player.sendMessage(Component.text("마을장만 멤버를 추방할 수 있습니다.", NamedTextColor.RED))
                    return
                }

                if (member.role == VillageRole.MAYOR) {
                    player.sendMessage(Component.text("마을장은 추방할 수 없습니다.", NamedTextColor.RED))
                    return
                }

                val result = advancedManager.kickVillageMember(session.villageId, member.memberUuid)
                if (result) {
                    player.sendMessage(
                        Component.text()
                            .append(Component.text("🚫 ", NamedTextColor.RED))
                            .append(Component.text("${member.memberName}님을 마을에서 추방했습니다.", NamedTextColor.WHITE))
                    )

                    // 추방된 플레이어에게 알림 (온라인인 경우)
                    val targetPlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                    if (targetPlayer != null) {
                        val villageInfo = advancedManager.getVillageInfo(session.villageId)
                        targetPlayer.sendMessage(
                            Component.text()
                                .append(Component.text("📢 ", NamedTextColor.RED))
                                .append(Component.text("마을 '", NamedTextColor.WHITE))
                                .append(Component.text(villageInfo?.villageName ?: "알 수 없음", NamedTextColor.YELLOW))
                                .append(Component.text("'에서 추방되었습니다.", NamedTextColor.WHITE))
                        )
                    }

                    // 멤버 관리 페이지로 돌아가기
                    val newSession = session.copy(currentPage = GUIPage.MEMBER_MANAGE, selectedMember = null)
                    openInventories[player.uniqueId] = newSession
                    updateGUI(player, event.inventory, newSession)
                } else {
                    player.sendMessage(Component.text("멤버 추방 중 오류가 발생했습니다.", NamedTextColor.RED))
                }
            }

            49 -> {
                // 뒤로가기 (멤버 관리 페이지로)
                val newSession = session.copy(currentPage = GUIPage.MEMBER_MANAGE, selectedMember = null)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
        }
    }

    /**
     * 권한 관리 페이지 클릭 처리
     */
    private fun handlePermissionsPageClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        when (event.slot) {
            49 -> {
                // 뒤로가기
                val newSession = session.copy(currentPage = GUIPage.MAIN)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
            in 10..44 -> {
                // 멤버 클릭 처리
                handlePermissionMemberClick(player, event, session)
            }
        }
    }

    /**
     * 권한 설정용 멤버 클릭 처리
     */
    private fun handlePermissionMemberClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        val clickedItem = event.currentItem ?: return
        val displayName = clickedItem.itemMeta?.displayName()
        val memberName = if (displayName != null) {
            displayName.examinableName().replace("👤 ", "").replace("👑 ", "").replace("🏅 ", "")
        } else {
            return
        }

        // 마을 멤버 목록에서 해당 멤버 찾기
        val members = advancedManager.getVillageMembers(session.villageId)
        val selectedMember = members.find { it.memberName == memberName && it.role != VillageRole.MAYOR } ?: return

        // 멤버 권한 설정 페이지로 이동
        val newSession = session.copy(currentPage = GUIPage.MEMBER_PERMISSIONS, selectedMember = selectedMember)
        openInventories[player.uniqueId] = newSession
        updateGUI(player, event.inventory, newSession)
    }

    /**
     * 개별 멤버 권한 설정 페이지 클릭 처리
     */
    private fun handleMemberPermissionsClick(player: Player, event: InventoryClickEvent, session: VillageGUISession) {
        val member = session.selectedMember ?: return

        when (event.slot) {
            49 -> {
                // 뒤로가기 (권한 관리 페이지로)
                val newSession = session.copy(currentPage = GUIPage.PERMISSIONS, selectedMember = null)
                openInventories[player.uniqueId] = newSession
                updateGUI(player, event.inventory, newSession)
            }
            10, 11, 12, 13, 16, 19, 20, 21, 22, 25, 28, 29 -> {
                // 권한 토글
                handlePermissionToggle(player, event, session, member)
            }
        }
    }

    /**
     * 권한 토글 처리
     */
    private fun handlePermissionToggle(player: Player, event: InventoryClickEvent, session: VillageGUISession, member: VillageMember) {
        val permissionSlots = mapOf(
            10 to VillagePermissionType.BUILD,
            11 to VillagePermissionType.BREAK_BLOCKS,
            12 to VillagePermissionType.USE_CONTAINERS,
            13 to VillagePermissionType.USE_REDSTONE,
            16 to VillagePermissionType.INVITE_MEMBERS,
            19 to VillagePermissionType.KICK_MEMBERS,
            20 to VillagePermissionType.MANAGE_LAND,
            21 to VillagePermissionType.EXPAND_LAND,
            22 to VillagePermissionType.REDUCE_LAND,
            25 to VillagePermissionType.MANAGE_ROLES,
            28 to VillagePermissionType.MANAGE_PERMISSIONS,
            29 to VillagePermissionType.RENAME_VILLAGE
        )

        val permission = permissionSlots[event.slot] ?: return

        // 권한 설정 가능 여부 확인
        if (!canTogglePermission(permission, member.role, session.playerRole)) {
            player.sendMessage(Component.text("이 권한은 변경할 수 없습니다.", NamedTextColor.RED))
            return
        }

        // 현재 권한 상태 확인
        val currentPermissions = advancedManager.getMemberPermissions(session.villageId, member.memberUuid)
        val hasPermission = currentPermissions.contains(permission)

        if (hasPermission) {
            // 권한 해제
            val result = advancedManager.revokeMemberPermission(
                player,
                session.villageId,
                member.memberUuid,
                permission
            )

            if (result.success) {
                val (name, _, _) = getPermissionInfo(permission)
                player.sendMessage(
                    Component.text()
                        .append(Component.text("❌ ", NamedTextColor.RED))
                        .append(Component.text("${member.memberName}님의 '$name' 권한을 해제했습니다.", NamedTextColor.WHITE))
                )

                // 대상 플레이어에게 알림
                val targetPlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(
                        Component.text()
                            .append(Component.text("📢 ", NamedTextColor.YELLOW))
                            .append(Component.text("'$name' 권한이 해제되었습니다.", NamedTextColor.WHITE))
                    )
                }
            } else {
                player.sendMessage(Component.text("권한 해제 중 오류가 발생했습니다: ${result.message}", NamedTextColor.RED))
            }
        } else {
            // 권한 부여
            val result = advancedManager.grantMemberPermission(
                player,
                session.villageId,
                member.memberUuid,
                permission
            )

            if (result.success) {
                val (name, _, _) = getPermissionInfo(permission)
                player.sendMessage(
                    Component.text()
                        .append(Component.text("✅ ", NamedTextColor.GREEN))
                        .append(Component.text("${member.memberName}님에게 '$name' 권한을 부여했습니다.", NamedTextColor.WHITE))
                )

                // 대상 플레이어에게 알림
                val targetPlayer = org.bukkit.Bukkit.getPlayer(member.memberUuid)
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(
                        Component.text()
                            .append(Component.text("🎉 ", NamedTextColor.GOLD))
                            .append(Component.text("'$name' 권한이 부여되었습니다!", NamedTextColor.WHITE))
                    )
                }
            } else {
                player.sendMessage(Component.text("권한 부여 중 오류가 발생했습니다: ${result.message}", NamedTextColor.RED))
            }
        }

        // GUI 새로고침
        updateGUI(player, event.inventory, session)
    }

    /**
     * 마을 해체 처리
     */
    private fun handleVillageDisband(player: Player, session: VillageGUISession) {
        val villageId = session.villageId
        
        // 마을 해체 확인
        player.sendMessage(
            Component.text()
                .append(Component.text("⚠️ ", NamedTextColor.RED))
                .append(Component.text("정말로 마을을 해체하시겠습니까?", NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("모든 마을 토지가 개인 토지로 변환됩니다.", NamedTextColor.GRAY))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("이 작업은 되돌릴 수 없습니다.", NamedTextColor.RED))
        )
        player.sendMessage(
            Component.text()
                .append(Component.text("해체를 확정하려면 ", NamedTextColor.WHITE))
                .append(Component.text("'/땅 마을해체확정'", NamedTextColor.YELLOW))
                .append(Component.text("을 입력하세요.", NamedTextColor.WHITE))
        )
        
        // 해체 확정 대기 상태로 설정 (5분 후 만료)
        pendingDisbandVillages[player.uniqueId] = System.currentTimeMillis() + 300000 // 5분
        
        player.closeInventory()
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == Component.text(inventoryTitle)) {
            openInventories.remove(event.player.uniqueId)
        }
    }

    // === GUI 유틸리티 메서드들 ===

    /**
     * 인벤토리의 모든 슬롯을 지웁니다.
     */
    private fun clearInventory(inventory: Inventory) {
        inventory.clear()
    }

    /**
     * 빈 슬롯을 검은색 유리판으로 채웁니다.
     */
    private fun fillEmptySlots(inventory: Inventory) {
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" "))
            }
        }
        
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, blackGlass)
            }
        }
    }

    /**
     * 에러 아이템을 설정합니다.
     */
    private fun setErrorItem(inventory: Inventory, slot: Int, message: String) {
        val errorItem = ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("❌ 오류", NamedTextColor.RED, TextDecoration.BOLD))
                meta.lore(listOf(Component.text(message, NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(slot, errorItem)
    }
}
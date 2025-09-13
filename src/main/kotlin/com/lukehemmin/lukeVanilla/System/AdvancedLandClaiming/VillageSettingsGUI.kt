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

    /**
     * GUI 세션 정보를 담는 데이터 클래스
     */
    private data class VillageGUISession(
        val villageId: Int,
        val playerRole: VillageRole,
        val currentPage: GUIPage = GUIPage.MAIN
    )

    /**
     * GUI 페이지 타입
     */
    private enum class GUIPage {
        MAIN,           // 메인 페이지
        MEMBER_MANAGE,  // 멤버 관리
        VILLAGE_INFO,   // 마을 정보
        PERMISSIONS     // 권한 설정 (향후 확장용)
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
            GUIPage.VILLAGE_INFO -> renderVillageInfoPage(inventory, session)
            GUIPage.PERMISSIONS -> renderPermissionsPage(inventory, session)
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
     * 권한 설정 페이지를 렌더링합니다. (향후 확장용)
     */
    private fun renderPermissionsPage(inventory: Inventory, session: VillageGUISession) {
        clearInventory(inventory)
        
        // TODO: 향후 마을 권한 시스템 확장 시 구현
        val comingSoonItem = ItemStack(Material.CLOCK).apply {
            editMeta { meta ->
                meta.displayName(Component.text("🚧 개발 예정", NamedTextColor.YELLOW, TextDecoration.BOLD))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("마을 권한 설정 기능은", NamedTextColor.GRAY))
                lore.add(Component.text("향후 업데이트에서 추가될 예정입니다.", NamedTextColor.GRAY))
                meta.lore(lore)
            }
        }
        inventory.setItem(22, comingSoonItem)

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

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text(inventoryTitle)) return
        
        val player = event.whoClicked as? Player ?: return
        val session = openInventories[player.uniqueId] ?: return
        
        event.isCancelled = true
        
        when (session.currentPage) {
            GUIPage.MAIN -> handleMainPageClick(player, event, session)
            GUIPage.MEMBER_MANAGE -> handleMemberManageClick(player, event, session)
            GUIPage.VILLAGE_INFO, GUIPage.PERMISSIONS -> handleBackButtonClick(player, event, session)
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
        // TODO: 멤버 관리 기능 구현 (역할 변경, 추방 등)
        val clickedItem = event.currentItem ?: return
        val memberName = clickedItem.itemMeta?.displayName()?.examinableName() ?: return
        
        if (session.playerRole == VillageRole.MAYOR) {
            player.sendMessage(Component.text("${memberName}의 관리 기능은 향후 업데이트에서 추가될 예정입니다.", NamedTextColor.YELLOW))
        } else {
            player.sendMessage(Component.text("${memberName}의 정보를 조회했습니다.", NamedTextColor.GREEN))
        }
    }

    /**
     * 마을 해체 처리
     */
    private fun handleVillageDisband(player: Player, session: VillageGUISession) {
        // TODO: 마을 해체 기능 구현
        player.sendMessage(Component.text("마을 해체 기능은 향후 업데이트에서 추가될 예정입니다.", NamedTextColor.YELLOW))
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
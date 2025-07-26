package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
// Kyori API는 지금 적용하지 않습니다 - 추후 별도 작업으로 진행
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.inventory.Inventory

class ItemReceiveSystem : Listener {
    
    companion object {
        // 전역적으로 중복 클릭을 추적하기 위한 스레드 안전한 정적 맵
        private val lastClickTimes = ConcurrentHashMap<String, Long>()
        
        // 이 메서드는 특정 플레이어의 특정 슬롯 클릭이 최근에 발생했는지 확인
        fun isRecentClick(playerUuid: String, slot: Int): Boolean {
            val key = "${playerUuid}_${slot}"
            val lastClick = lastClickTimes[key] ?: 0L
            val currentTime = System.currentTimeMillis()
            val isRecent = currentTime - lastClick < 2000 // 2초 이내 클릭은 중복으로 간주
            
            if (!isRecent) {
                // 최근 클릭이 아닌 경우에만 시간 업데이트
                lastClickTimes[key] = currentTime
            }
            
            return isRecent
        }
        
        // 일정 시간 후 만료된 항목 정리 (서버 성능 최적화)
        fun cleanupExpiredEntries() {
            val currentTime = System.currentTimeMillis()
            val expiredKeys = lastClickTimes.entries
                .filter { (currentTime - it.value) > 60000 } // 1분 이상 지난 항목은 제거
                .map { it.key }
                
            for (key in expiredKeys) {
                lastClickTimes.remove(key)
            }
        }
    }
    
    lateinit var plugin: Main
    lateinit var database: Database
    
    // 시즌별 아이템 수령 가능 여부 설정
    private var isHalloweenReceivable = true
    private var isChristmasReceivable = true
    private var isValentineReceivable = true
    private var isSpringReceivable = true
    
    // 이벤트 타입 목록
    private val eventTypes = listOf("할로윈", "크리스마스", "발렌타인", "봄")
    
    // GUI 관련 상수
    private val halloweenGuiTitle = "${ChatColor.DARK_PURPLE}할로윈 아이템 받기"
    private val christmasGuiTitle = "${ChatColor.RED}크리스마스 아이템 받기"
    private val valentineGuiTitle = "${ChatColor.LIGHT_PURPLE}발렌타인 아이템 받기"
    private val springGuiTitle = "${ChatColor.GREEN}봄 아이템 받기"
    
    // GUI 키
    private lateinit var halloweenGuiKey: NamespacedKey
    private lateinit var christmasGuiKey: NamespacedKey
    private lateinit var valentineGuiKey: NamespacedKey
    private lateinit var springGuiKey: NamespacedKey
    private lateinit var ownerKey: NamespacedKey
    private lateinit var pageKey: NamespacedKey
    private lateinit var navKey: NamespacedKey
    
    // 할로윈 아이템 매핑
    private val halloweenScrollItems = mapOf(
        10 to "h_sword_scroll",
        12 to "h_pickaxe_scroll",
        14 to "h_axe_scroll",
        16 to "h_shovel_scroll",
        20 to "h_hoe_scroll",
        22 to "h_bow_scroll",
        24 to "h_rod_scroll",
        28 to "h_hammer_scroll",
        30 to "h_hat_scroll",
        32 to "h_scythe_scroll",
        34 to "h_spear_scroll"
    )
    
    private val halloweenItemMappings = mapOf(
        "sword" to "halloween_sword",
        "pickaxe" to "halloween_pickaxe",
        "axe" to "halloween_axe",
        "shovel" to "halloween_shovel",
        "hoe" to "halloween_hoe",
        "bow" to "halloween_bow",
        "fishing_rod" to "halloween_fishing_rod",
        "hammer" to "halloween_hammer",
        "hat" to "halloween_hat",
        "scythe" to "halloween_scythe",
        "spear" to "halloween_spear"
    )
    
    // 크리스마스 아이템 매핑 (페이지별)
    private val christmasScrollItemsPage1 = mapOf(
        10 to "c_sword_scroll",
        12 to "c_pickaxe_scroll",
        14 to "c_axe_scroll",
        16 to "c_shovel_scroll",
        20 to "c_hoe_scroll",
        22 to "c_bow_scroll",
        24 to "c_crossbow_scroll",
        28 to "c_fishing_rod_scroll",
        30 to "c_hammer_scroll",
        32 to "c_shield_scroll",
        34 to "c_head_scroll"
    )
    
    private val christmasScrollItemsPage2 = mapOf(
        10 to "c_helmet_scroll",
        12 to "c_chestplate_scroll",
        14 to "c_leggings_scroll",
        16 to "c_boots_scroll"
    )
    
    private val christmasItemMappings = mapOf(
        "sword" to "merry_christmas_sword",
        "pickaxe" to "merry_christmas_pickaxe",
        "axe" to "merry_christmas_axe",
        "shovel" to "merry_christmas_shovel",
        "hoe" to "merry_christmas_hoe",
        "bow" to "merry_christmas_bow",
        "crossbow" to "merry_christmas_crossbow",
        "fishing_rod" to "merry_christmas_fishing_rod",
        "hammer" to "merry_christmas_hammer",
        "shield" to "merry_christmas_shield",
        "head" to "merry_christmas_head",
        "helmet" to "merry_christmas_helmet",
        "chestplate" to "merry_christmas_chestplate",
        "leggings" to "merry_christmas_leggings",
        "boots" to "merry_christmas_boots"
    )
    
    // 발렌타인 아이템 매핑 (페이지별)
    private val valentineScrollItemsPage1 = mapOf(
        10 to "v_sword_scroll",
        12 to "v_pickaxe_scroll",
        14 to "v_axe_scroll",
        16 to "v_shovel_scroll",
        20 to "v_hoe_scroll",
        22 to "v_bow_scroll",
        24 to "v_crossbow_scroll",
        28 to "v_fishing_rod_scroll",
        30 to "v_hammer_scroll",
        32 to "v_helmet_scroll",
        34 to "v_chestplate_scroll"
    )
    
    private val valentineScrollItemsPage2 = mapOf(
        10 to "v_leggings_scroll",
        12 to "v_boots_scroll",
        14 to "v_head_scroll",
        16 to "v_shield_scroll"
    )
    
    private val valentineItemMappings = mapOf(
        "sword" to "valentine_sword",
        "pickaxe" to "valentine_pickaxe",
        "axe" to "valentine_axe",
        "shovel" to "valentine_shovel",
        "hoe" to "valentine_hoe",
        "fishing_rod" to "valentine_fishing_rod",
        "bow" to "valentine_bow",
        "crossbow" to "valentine_crossbow",
        "hammer" to "valentine_hammer",
        "helmet" to "valentine_helmet",
        "chestplate" to "valentine_chestplate",
        "leggings" to "valentine_leggings",
        "boots" to "valentine_boots",
        "head" to "valentine_head",
        "shield" to "valentine_shield"
    )
    
    // 봄 아이템 매핑 (페이지별)
    private val springScrollItemsPage1 = mapOf(
        10 to "sp_helmet_scroll",
        12 to "sp_chestplate_scroll",
        14 to "sp_leggings_scroll",
        16 to "sp_boots_scroll",
        20 to "sp_sword_scroll",
        22 to "sp_pickaxe_scroll",
        24 to "sp_axe_scroll",
        28 to "sp_hoe_scroll",
        30 to "sp_shovel_scroll",
        32 to "sp_bow_scroll",
        34 to "sp_crossbow_scroll"
    )
    
    private val springScrollItemsPage2 = mapOf(
        10 to "sp_shield_scroll",
        12 to "sp_hammer_scroll"
    )
    
    private val springItemMappings = mapOf(
        "helmet" to "plnyspringset_helmet",
        "chestplate" to "plnyspringset_chestplate",
        "leggings" to "plnyspringset_leggings",
        "boots" to "plnyspringset_boots",
        "sword" to "plny_springset_sword",
        "pickaxe" to "plny_springset_pickaxe",
        "axe" to "plny_springset_axe",
        "hoe" to "plny_springset_hoe",
        "shovel" to "plny_springset_shovel",
        "bow" to "plny_springset_bow",
        "crossbow" to "plny_springset_crossbow",
        "shield" to "plny_springset_shield",
        "hammer" to "plny_springset_hammer"
    )
    
    init {
        val pluginManager = Bukkit.getPluginManager()
        val pluginInstance = pluginManager.getPlugin("LukeVanilla")
        if (pluginInstance is Main) {
            plugin = pluginInstance
            database = plugin.database
            
            // NamespacedKey 초기화
            halloweenGuiKey = NamespacedKey(plugin, "halloween_gui")
            christmasGuiKey = NamespacedKey(plugin, "christmas_gui")
            valentineGuiKey = NamespacedKey(plugin, "valentine_gui")
            springGuiKey = NamespacedKey(plugin, "spring_gui")
            ownerKey = NamespacedKey(plugin, "owner")
            pageKey = NamespacedKey(plugin, "page")
            navKey = NamespacedKey(plugin, "nav")
            
            // 이벤트 리스너 등록
            Bukkit.getPluginManager().registerEvents(this, plugin)
        }
    }
    
    fun receiveItem(player: Player, eventTypeArg: String): Boolean {
        // 권한 체크
        if (!player.hasPermission("lukevanilla.item.receive")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }
        
        // 이벤트 타입 확인
        if (!eventTypes.contains(eventTypeArg)) {
            player.sendMessage("§c유효하지 않은 이벤트 타입입니다. 사용 가능한 이벤트: ${eventTypes.joinToString(", ")}")
            return true
        }
        
        // 이벤트 활성화 여부 확인
        if (!isSeasonReceivable(eventTypeArg)) {
            player.sendMessage("§c아직 ${eventTypeArg} 아이템을 수령할 수 있는 기간이 아닙니다.")
            return true
        }
        
        // 비동기적으로 데이터베이스 작업 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            when (eventTypeArg) {
                "할로윈" -> openHalloweenGui(player)
                "크리스마스" -> openChristmasGui(player, 1)
                "발렌타인" -> openValentineGui(player, 1)
                "봄" -> openSpringGui(player, 1)
                else -> {
                    player.sendMessage("§c지원하지 않는 이벤트 타입입니다.")
                    return@Runnable
                }
            }
        })
        
        return true
    }
    
    // 할로윈 GUI 열기
    private fun openHalloweenGui(player: Player) {
        try {
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                
                // 플레이어가 등록한 아이템 정보 가져오기
                val ownerStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Owner WHERE UUID = ?")
                ownerStmt.setString(1, uuid)
                val ownerResult = ownerStmt.executeQuery()
                
                // 플레이어가 수령한 아이템 정보 가져오기
                val receiveStmt = connection.prepareStatement("SELECT * FROM Halloween_Item_Receive WHERE UUID = ?")
                receiveStmt.setString(1, uuid)
                val receiveResult = receiveStmt.executeQuery()
                
                val registeredItems = mutableSetOf<String>()
                val receivedItems = mutableSetOf<String>()
                
                if (ownerResult.next()) {
                    for (column in halloweenItemMappings.keys) {
                        val hasItem = ownerResult.getBoolean(column)
                        if (hasItem) {
                            registeredItems.add(column)
                        }
                    }
                }
                
                if (receiveResult.next()) {
                    for (column in halloweenItemMappings.keys) {
                        val hasReceived = receiveResult.getBoolean(column)
                        if (hasReceived) {
                            receivedItems.add(column)
                        }
                    }
                }
                
                // GUI 생성 및 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val gui = Bukkit.createInventory(player, 5 * 9, halloweenGuiTitle)
                    
                    // 주황색 색유리판으로 배경 채우기
                    val orangePane = ItemStack(Material.ORANGE_STAINED_GLASS_PANE)
                    val orangeMeta = orangePane.itemMeta
                    orangeMeta!!.setDisplayName(" ")
                    orangePane.itemMeta = orangeMeta
                    for (i in 0 until gui.size) {
                        gui.setItem(i, orangePane)
                    }
                    
                    // 각 슬롯에 아이템 배치
                    for ((slot, scrollId) in halloweenScrollItems) {
                        val columnName = getColumnNameByScrollId(scrollId)
                        when {
                            // 아이템을 등록하지 않은 경우 - 검은색 유리판
                            !registeredItems.contains(columnName) -> {
                                val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                                val blackMeta = blackPane.itemMeta
                                blackMeta!!.setDisplayName("${ChatColor.GRAY}아이템이 등록되지 않았습니다.")
                                blackPane.itemMeta = blackMeta
                                gui.setItem(slot, blackPane)
                            }
                            // 이미 수령한 경우 - 빨간색 유리판
                            receivedItems.contains(columnName) -> {
                                val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                                val redMeta = redPane.itemMeta
                                redMeta!!.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                                redPane.itemMeta = redMeta
                                gui.setItem(slot, redPane)
                            }
                            // 수령 가능 - 스크롤 아이템 배치
                            else -> {
                                val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: run {
                                    plugin.logger.warning("스크롤 아이템 생성 실패: $scrollId")
                                    return@Runnable
                                }
                                val scrollMeta = scrollItem.itemMeta
                                scrollMeta!!.persistentDataContainer.set(halloweenGuiKey, PersistentDataType.STRING, scrollId)
                                scrollItem.itemMeta = scrollMeta
                                gui.setItem(slot, scrollItem)
                            }
                        }
                    }
                    
                    // GUI 열기
                    player.openInventory(gui)
                })
            }
        } catch (e: Exception) {
            plugin.logger.warning("할로윈 GUI 생성 중 오류 발생: ${e.message}")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("§c아이템 정보를 불러오는 중 오류가 발생했습니다.")
            })
        }
    }
    
    // 크리스마스 GUI 열기
    private fun openChristmasGui(player: Player, page: Int = 1) {
        try {
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                
                // 플레이어가 등록한 아이템 정보 가져오기
                val ownerStmt = connection.prepareStatement("SELECT * FROM Christmas_Item_Owner WHERE UUID = ?")
                ownerStmt.setString(1, uuid)
                val ownerResult = ownerStmt.executeQuery()
                
                // 플레이어가 수령한 아이템 정보 가져오기
                val receiveStmt = connection.prepareStatement("SELECT * FROM Christmas_Item_Receive WHERE UUID = ?")
                receiveStmt.setString(1, uuid)
                val receiveResult = receiveStmt.executeQuery()
                
                val registeredItems = mutableSetOf<String>()
                val receivedItems = mutableSetOf<String>()
                
                if (ownerResult.next()) {
                    for (column in christmasItemMappings.keys) {
                        val hasItem = ownerResult.getBoolean(column)
                        if (hasItem) {
                            registeredItems.add(column)
                        }
                    }
                }
                
                if (receiveResult.next()) {
                    for (column in christmasItemMappings.keys) {
                        val hasReceived = receiveResult.getBoolean(column)
                        if (hasReceived) {
                            receivedItems.add(column)
                        }
                    }
                }
                
                // GUI 생성 및 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val gui = Bukkit.createInventory(player, 5 * 9, "$christmasGuiTitle - ${page}페이지")
                    
                    // 초록색 색유리판으로 배경 채우기
                    val greenPane = ItemStack(Material.GREEN_STAINED_GLASS_PANE)
                    val greenMeta = greenPane.itemMeta
                    greenMeta!!.setDisplayName(" ")
                    greenPane.itemMeta = greenMeta
                    for (i in 0 until gui.size) {
                        gui.setItem(i, greenPane)
                    }
                    
                    // 페이지별 아이템 배치
                    val currentPageItems = if (page == 1) christmasScrollItemsPage1 else christmasScrollItemsPage2
                    
                    for ((slot, scrollId) in currentPageItems) {
                        val columnName = getColumnNameByScrollId(scrollId)
                        when {
                            // 아이템을 등록하지 않은 경우 - 검은색 유리판
                            !registeredItems.contains(columnName) -> {
                                val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                                val blackMeta = blackPane.itemMeta
                                blackMeta!!.setDisplayName("${ChatColor.GRAY}아이템이 등록되지 않았습니다.")
                                blackPane.itemMeta = blackMeta
                                gui.setItem(slot, blackPane)
                            }
                            // 이미 수령한 경우 - 빨간색 유리판
                            receivedItems.contains(columnName) -> {
                                val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                                val redMeta = redPane.itemMeta
                                redMeta!!.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                                redPane.itemMeta = redMeta
                                gui.setItem(slot, redPane)
                            }
                            // 수령 가능 - 스크롤 아이템 배치
                            else -> {
                                val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: run {
                                    plugin.logger.warning("스크롤 아이템 생성 실패: $scrollId")
                                    return@Runnable
                                }
                                val scrollMeta = scrollItem.itemMeta
                                scrollMeta!!.persistentDataContainer.set(christmasGuiKey, PersistentDataType.STRING, scrollId)
                                scrollMeta.persistentDataContainer.set(pageKey, PersistentDataType.INTEGER, page)
                                scrollItem.itemMeta = scrollMeta
                                gui.setItem(slot, scrollItem)
                            }
                        }
                    }
                    
                    // 네비게이션 버튼 추가
                    addNavigationButtons(gui, page, 2, "christmas")
                    
                    // GUI 열기
                    player.openInventory(gui)
                })
            }
        } catch (e: Exception) {
            plugin.logger.warning("크리스마스 GUI 생성 중 오류 발생: ${e.message}")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("§c아이템 정보를 불러오는 중 오류가 발생했습니다.")
            })
        }
    }
    
    // 발렌타인 GUI 열기
    private fun openValentineGui(player: Player, page: Int = 1) {
        try {
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                
                // 플레이어가 등록한 아이템 정보 가져오기
                val ownerStmt = connection.prepareStatement("SELECT * FROM Valentine_Item_Owner WHERE UUID = ?")
                ownerStmt.setString(1, uuid)
                val ownerResult = ownerStmt.executeQuery()
                
                // 플레이어가 수령한 아이템 정보 가져오기
                val receiveStmt = connection.prepareStatement("SELECT * FROM Valentine_Item_Receive WHERE UUID = ?")
                receiveStmt.setString(1, uuid)
                val receiveResult = receiveStmt.executeQuery()
                
                val registeredItems = mutableSetOf<String>()
                val receivedItems = mutableSetOf<String>()
                
                if (ownerResult.next()) {
                    for (column in valentineItemMappings.keys) {
                        val hasItem = ownerResult.getBoolean(column)
                        if (hasItem) {
                            registeredItems.add(column)
                        }
                    }
                }
                
                if (receiveResult.next()) {
                    for (column in valentineItemMappings.keys) {
                        val hasReceived = receiveResult.getBoolean(column)
                        if (hasReceived) {
                            receivedItems.add(column)
                        }
                    }
                }
                
                // GUI 생성 및 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val gui = Bukkit.createInventory(player, 5 * 9, "$valentineGuiTitle - ${page}페이지")
                    
                    // 분홍색 색유리판으로 배경 채우기
                    val pinkPane = ItemStack(Material.PINK_STAINED_GLASS_PANE)
                    val pinkMeta = pinkPane.itemMeta
                    pinkMeta!!.setDisplayName(" ")
                    pinkPane.itemMeta = pinkMeta
                    for (i in 0 until gui.size) {
                        gui.setItem(i, pinkPane)
                    }
                    
                    // 페이지별 아이템 배치
                    val currentPageItems = if (page == 1) valentineScrollItemsPage1 else valentineScrollItemsPage2
                    
                    for ((slot, scrollId) in currentPageItems) {
                        val columnName = getColumnNameByScrollId(scrollId)
                        when {
                            // 아이템을 등록하지 않은 경우 - 검은색 유리판
                            !registeredItems.contains(columnName) -> {
                                val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                                val blackMeta = blackPane.itemMeta
                                blackMeta!!.setDisplayName("${ChatColor.GRAY}아이템이 등록되지 않았습니다.")
                                blackPane.itemMeta = blackMeta
                                gui.setItem(slot, blackPane)
                            }
                            // 이미 수령한 경우 - 빨간색 유리판
                            receivedItems.contains(columnName) -> {
                                val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                                val redMeta = redPane.itemMeta
                                redMeta!!.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                                redPane.itemMeta = redMeta
                                gui.setItem(slot, redPane)
                            }
                            // 수령 가능 - 스크롤 아이템 배치
                            else -> {
                                val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: run {
                                    plugin.logger.warning("스크롤 아이템 생성 실패: $scrollId")
                                    return@Runnable
                                }
                                val scrollMeta = scrollItem.itemMeta
                                scrollMeta!!.persistentDataContainer.set(valentineGuiKey, PersistentDataType.STRING, scrollId)
                                scrollMeta.persistentDataContainer.set(pageKey, PersistentDataType.INTEGER, page)
                                scrollItem.itemMeta = scrollMeta
                                gui.setItem(slot, scrollItem)
                            }
                        }
                    }
                    
                    // 네비게이션 버튼 추가
                    addNavigationButtons(gui, page, 2, "valentine")
                    
                    // GUI 열기
                    player.openInventory(gui)
                })
            }
        } catch (e: Exception) {
            plugin.logger.warning("발렌타인 GUI 생성 중 오류 발생: ${e.message}")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("§c아이템 정보를 불러오는 중 오류가 발생했습니다.")
            })
        }
    }
    
    // 봄 GUI 열기
    private fun openSpringGui(player: Player, page: Int = 1) {
        try {
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                
                // 플레이어가 등록한 아이템 정보 가져오기
                val ownerStmt = connection.prepareStatement("SELECT * FROM Spring_Item_Owner WHERE UUID = ?")
                ownerStmt.setString(1, uuid)
                val ownerResult = ownerStmt.executeQuery()
                
                // 플레이어가 수령한 아이템 정보 가져오기
                val receiveStmt = connection.prepareStatement("SELECT * FROM Spring_Item_Receive WHERE UUID = ?")
                receiveStmt.setString(1, uuid)
                val receiveResult = receiveStmt.executeQuery()
                
                val registeredItems = mutableSetOf<String>()
                val receivedItems = mutableSetOf<String>()
                
                if (ownerResult.next()) {
                    for (column in springItemMappings.keys) {
                        val hasItem = ownerResult.getBoolean(column)
                        if (hasItem) {
                            registeredItems.add(column)
                        }
                    }
                }
                
                if (receiveResult.next()) {
                    for (column in springItemMappings.keys) {
                        val hasReceived = receiveResult.getBoolean(column)
                        if (hasReceived) {
                            receivedItems.add(column)
                        }
                    }
                }
                
                // GUI 생성 및 열기
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val gui = Bukkit.createInventory(player, 5 * 9, "$springGuiTitle - ${page}페이지")
                    
                    // 초록색 색유리판으로 배경 채우기
                    val greenPane = ItemStack(Material.GREEN_STAINED_GLASS_PANE)
                    val greenMeta = greenPane.itemMeta
                    greenMeta!!.setDisplayName(" ")
                    greenPane.itemMeta = greenMeta
                    for (i in 0 until gui.size) {
                        gui.setItem(i, greenPane)
                    }
                    
                    // 페이지별 아이템 배치
                    val currentPageItems = if (page == 1) springScrollItemsPage1 else springScrollItemsPage2
                    
                    for ((slot, scrollId) in currentPageItems) {
                        val columnName = getColumnNameByScrollId(scrollId)
                        when {
                            // 아이템을 등록하지 않은 경우 - 검은색 유리판
                            !registeredItems.contains(columnName) -> {
                                val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                                val blackMeta = blackPane.itemMeta
                                blackMeta!!.setDisplayName("${ChatColor.GRAY}아이템이 등록되지 않았습니다.")
                                blackPane.itemMeta = blackMeta
                                gui.setItem(slot, blackPane)
                            }
                            // 이미 수령한 경우 - 빨간색 유리판
                            receivedItems.contains(columnName) -> {
                                val redPane = ItemStack(Material.RED_STAINED_GLASS_PANE)
                                val redMeta = redPane.itemMeta
                                redMeta!!.setDisplayName("${ChatColor.RED}이미 수령한 아이템입니다.")
                                redPane.itemMeta = redMeta
                                gui.setItem(slot, redPane)
                            }
                            // 수령 가능 - 스크롤 아이템 배치
                            else -> {
                                val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: run {
                                    plugin.logger.warning("스크롤 아이템 생성 실패: $scrollId")
                                    return@Runnable
                                }
                                val scrollMeta = scrollItem.itemMeta
                                scrollMeta!!.persistentDataContainer.set(springGuiKey, PersistentDataType.STRING, scrollId)
                                scrollMeta.persistentDataContainer.set(pageKey, PersistentDataType.INTEGER, page)
                                scrollItem.itemMeta = scrollMeta
                                gui.setItem(slot, scrollItem)
                            }
                        }
                    }
                    
                    // 네비게이션 버튼 추가
                    addNavigationButtons(gui, page, 2, "spring")
                    
                    // GUI 열기
                    player.openInventory(gui)
                })
            }
        } catch (e: Exception) {
            plugin.logger.warning("봄 GUI 생성 중 오류 발생: ${e.message}")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("§c아이템 정보를 불러오는 중 오류가 발생했습니다.")
            })
        }
    }
    
    // 네비게이션 처리
    private fun handleNavigation(player: Player, navAction: String) {
        val parts = navAction.split("_")
        if (parts.size != 2) return
        
        val action = parts[0]
        val eventType = parts[1]
        
        when (eventType) {
            "christmas" -> {
                when (action) {
                    "prev" -> openChristmasGui(player, 1)
                    "next" -> openChristmasGui(player, 2)
                }
            }
            "valentine" -> {
                when (action) {
                    "prev" -> openValentineGui(player, 1)
                    "next" -> openValentineGui(player, 2)
                }
            }
            "spring" -> {
                when (action) {
                    "prev" -> openSpringGui(player, 1)
                    "next" -> openSpringGui(player, 2)
                }
            }
        }
    }
    
    // 네비게이션 버튼 추가
    private fun addNavigationButtons(gui: Inventory, currentPage: Int, totalPages: Int, eventType: String) {
        // 페이지 표시 (40번 슬롯)
        val pageDisplay = ItemStack(Material.PAPER)
        val pageMeta = pageDisplay.itemMeta
        pageMeta!!.setDisplayName("${ChatColor.YELLOW}${currentPage}페이지 / ${totalPages}페이지")
        pageDisplay.itemMeta = pageMeta
        gui.setItem(40, pageDisplay)
        
        // 이전 페이지 버튼 (39번 슬롯) - 2페이지에서만 표시
        if (currentPage > 1) {
            val prevButton = ItemStack(Material.ARROW)
            val prevMeta = prevButton.itemMeta
            prevMeta!!.setDisplayName("${ChatColor.GREEN}이전 페이지")
            prevMeta.persistentDataContainer.set(navKey, PersistentDataType.STRING, "prev_$eventType")
            prevButton.itemMeta = prevMeta
            gui.setItem(39, prevButton)
        }
        
        // 다음 페이지 버튼 (41번 슬롯) - 1페이지에서만 표시  
        if (currentPage < totalPages) {
            val nextButton = ItemStack(Material.ARROW)
            val nextMeta = nextButton.itemMeta
            nextMeta!!.setDisplayName("${ChatColor.GREEN}다음 페이지")
            nextMeta.persistentDataContainer.set(navKey, PersistentDataType.STRING, "next_$eventType")
            nextButton.itemMeta = nextMeta
            gui.setItem(41, nextButton)
        }
    }
    
    // 인벤토리 클릭 이벤트 처리
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory ?: return
        val currentInventory = event.view.topInventory ?: return
        
        // 해당 GUI가 아닌 경우 무시
        val title = event.view.title
        if (!title.startsWith(halloweenGuiTitle) && !title.startsWith(christmasGuiTitle) && !title.startsWith(valentineGuiTitle) && !title.startsWith(springGuiTitle)) {
            return
        }
        
        // 클릭한 인벤토리가 상단 GUI가 아닌 경우(플레이어 인벤토리) 무시
        if (clickedInventory != currentInventory) {
            return
        }
        
        // 항상 클릭 이벤트 취소
        event.isCancelled = true
        
        // 중복 클릭 감지 - 정적 맵 사용
        if (isRecentClick(player.uniqueId.toString(), event.slot)) {
            plugin.logger.info("버그 방지: 중복 클릭 감지 - 플레이어 ${player.name}, 슬롯 ${event.slot}")
            return
        }
        
        // 클릭한 아이템 확인
        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return
        
        // 네비게이션 버튼 처리
        val container = meta.persistentDataContainer
        val navAction = container.get(navKey, PersistentDataType.STRING)
        if (navAction != null) {
            handleNavigation(player, navAction)
            return
        }
        
        // 스크롤 아이템 여부 확인
        val scrollId: String? = when {
            title.startsWith(halloweenGuiTitle) -> container.get(halloweenGuiKey, PersistentDataType.STRING)
            title.startsWith(christmasGuiTitle) -> container.get(christmasGuiKey, PersistentDataType.STRING)
            title.startsWith(valentineGuiTitle) -> container.get(valentineGuiKey, PersistentDataType.STRING)
            title.startsWith(springGuiTitle) -> container.get(springGuiKey, PersistentDataType.STRING)
            else -> null
        }
        
        // 스크롤 아이템이 아닐 경우 무시
        if (scrollId == null) {
            return
        }
        
        // 스크롤 아이템 지급 및 DB 업데이트
        val columnName = getColumnNameByScrollId(scrollId)
        if (columnName.isEmpty()) {
            return
        }
        
        // 이벤트 타입 확인
        val eventType = when {
            scrollId.startsWith("h_") -> "Halloween"
            scrollId.startsWith("c_") -> "Christmas"
            scrollId.startsWith("v_") -> "Valentine"
            scrollId.startsWith("sp_") -> "Spring"
            else -> return
        }
        
        // 비동기적으로 DB 업데이트
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val uuid = player.uniqueId.toString()
                    
                    // 이미 수령했는지 확인
                    val checkStmt = connection.prepareStatement("SELECT $columnName FROM ${eventType}_Item_Receive WHERE UUID = ?")
                    checkStmt.setString(1, uuid)
                    val checkResult = checkStmt.executeQuery()
                    
                    if (checkResult.next() && checkResult.getBoolean(columnName)) {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            player.sendMessage("${ChatColor.RED}이미 수령한 아이템입니다.")
                        })
                        return@Runnable
                    }
                    
                    // 아이템 소유 여부 확인
                    val ownerStmt = connection.prepareStatement("SELECT $columnName FROM ${eventType}_Item_Owner WHERE UUID = ?")
                    ownerStmt.setString(1, uuid)
                    val ownerResult = ownerStmt.executeQuery()
                    
                    if (!ownerResult.next() || !ownerResult.getBoolean(columnName)) {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            player.sendMessage("${ChatColor.RED}등록하지 않은 아이템입니다.")
                        })
                        return@Runnable
                    }
                    
                    // 수령 테이블에 데이터 업데이트
                    val updateStmt = connection.prepareStatement(
                        "INSERT INTO ${eventType}_Item_Receive (UUID, $columnName) VALUES (?, true) " +
                        "ON DUPLICATE KEY UPDATE $columnName = true"
                    )
                    updateStmt.setString(1, uuid)
                    updateStmt.executeUpdate()
                    
                    // 스크롤 아이템 지급
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        // 인벤토리 공간 확인
                        if (player.inventory.firstEmpty() == -1) {
                            player.sendMessage("${ChatColor.RED}인벤토리가 가득 찼습니다.")
                            return@Runnable
                        }
                        
                        // GUI 닫기
                        player.closeInventory()
                        
                        // 스크롤 지급
                        val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: run {
                            player.sendMessage("${ChatColor.RED}아이템 생성 중 오류가 발생했습니다.")
                            return@Runnable
                        }
                        
                        // 소유자 태그 추가
                        val scrollMeta = scrollItem.itemMeta
                        scrollMeta!!.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, uuid)
                        scrollItem.itemMeta = scrollMeta
                        
                        // 플레이어에게 한 번만 지급
                        // 인벤토리에 이미 같은 종류의 스크롤이 있는지 철저히 확인
                        for (item in player.inventory.contents) {
                            if (item == null) continue
                            if (!item.hasItemMeta()) continue
                            
                            val meta = item.itemMeta ?: continue
                            val pdc = meta.persistentDataContainer
                            
                            val existingScrollId = when (eventType) {
                                "Halloween" -> pdc.get(halloweenGuiKey, PersistentDataType.STRING)
                                "Christmas" -> pdc.get(christmasGuiKey, PersistentDataType.STRING)
                                "Valentine" -> pdc.get(valentineGuiKey, PersistentDataType.STRING)
                                "Spring" -> pdc.get(springGuiKey, PersistentDataType.STRING)
                                else -> null
                            }
                            
                            if (existingScrollId == scrollId) {
                                player.sendMessage("${ChatColor.RED}이 스크롤은 이미 인벤토리에 있습니다.")
                                return@Runnable
                            }
                        }
                        
                        // 플레이어에게 지급
                        player.inventory.addItem(scrollItem)
                        
                        // 메시지 표시
                        val itemDisplayName = when (eventType) {
                            "Halloween" -> "할로윈"
                            "Christmas" -> "크리스마스"
                            "Valentine" -> "발렌타인"
                            "Spring" -> "봄"
                            else -> ""
                        }
                        
                        // 아이템 이름 한글로 변환
                        val koreanItemName = when (columnName) {
                            "sword" -> "검"
                            "pickaxe" -> "곡괭이"
                            "axe" -> "도끼"
                            "shovel" -> "삽"
                            "hoe" -> "괭이"
                            "bow" -> "활"
                            "crossbow" -> "쇠뇌"
                            "fishing_rod" -> "낚싯대"
                            "hammer" -> "망치"
                            "shield" -> "방패"
                            "helmet" -> "투구"
                            "chestplate" -> "갑옷"
                            "leggings" -> "바지"
                            "boots" -> "부츠"
                            "head" -> "머리장식"
                            "hat" -> "모자"
                            "scythe" -> "낫"
                            "spear" -> "창"
                            else -> columnName
                        }
                        player.sendMessage("${ChatColor.GREEN}$itemDisplayName $koreanItemName 스크롤을 수령했습니다.")
                        
                        // 이벤트 처리 완료 - 추가 처리 필요 없음
                        // 디버그 로깅
                        plugin.logger.info("아이템 지급 성공: 플레이어 ${player.name}, 아이템 $scrollId")
                    })
                }
            } catch (e: Exception) {
                plugin.logger.warning("아이템 수령 중 오류 발생: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("${ChatColor.RED}아이템 수령 중 오류가 발생했습니다.")
                })
            }
        })
    }
    
    private fun getColumnNameByScrollId(scrollId: String): String {
        // 할로윈 스크롤
        if (scrollId.startsWith("h_")) {
            return when (scrollId) {
                "h_sword_scroll" -> "sword"
                "h_pickaxe_scroll" -> "pickaxe"
                "h_axe_scroll" -> "axe"
                "h_shovel_scroll" -> "shovel"
                "h_hoe_scroll" -> "hoe"
                "h_bow_scroll" -> "bow"
                "h_rod_scroll" -> "fishing_rod"
                "h_hammer_scroll" -> "hammer"
                "h_hat_scroll" -> "hat"
                "h_scythe_scroll" -> "scythe"
                "h_spear_scroll" -> "spear"
                else -> ""
            }
        }
        
        // 크리스마스 스크롤
        if (scrollId.startsWith("c_")) {
            return when (scrollId) {
                "c_sword_scroll" -> "sword"
                "c_pickaxe_scroll" -> "pickaxe"
                "c_axe_scroll" -> "axe"
                "c_shovel_scroll" -> "shovel"
                "c_hoe_scroll" -> "hoe"
                "c_bow_scroll" -> "bow"
                "c_crossbow_scroll" -> "crossbow"
                "c_fishing_rod_scroll" -> "fishing_rod"
                "c_hammer_scroll" -> "hammer"
                "c_shield_scroll" -> "shield"
                "c_head_scroll" -> "head"
                "c_helmet_scroll" -> "helmet"
                "c_chestplate_scroll" -> "chestplate"
                "c_leggings_scroll" -> "leggings"
                "c_boots_scroll" -> "boots"
                else -> ""
            }
        }
        
        // 발렌타인 스크롤
        if (scrollId.startsWith("v_")) {
            return when (scrollId) {
                "v_sword_scroll" -> "sword"
                "v_pickaxe_scroll" -> "pickaxe"
                "v_axe_scroll" -> "axe"
                "v_shovel_scroll" -> "shovel"
                "v_hoe_scroll" -> "hoe"
                "v_bow_scroll" -> "bow"
                "v_crossbow_scroll" -> "crossbow"
                "v_fishing_rod_scroll" -> "fishing_rod"
                "v_hammer_scroll" -> "hammer"
                "v_helmet_scroll" -> "helmet"
                "v_chestplate_scroll" -> "chestplate"
                "v_leggings_scroll" -> "leggings"
                "v_boots_scroll" -> "boots"
                "v_head_scroll" -> "head"
                "v_shield_scroll" -> "shield"
                else -> ""
            }
        }
        
        // 봄 스크롤
        if (scrollId.startsWith("sp_")) {
            return when (scrollId) {
                "sp_helmet_scroll" -> "helmet"
                "sp_chestplate_scroll" -> "chestplate"
                "sp_leggings_scroll" -> "leggings"
                "sp_boots_scroll" -> "boots"
                "sp_sword_scroll" -> "sword"
                "sp_pickaxe_scroll" -> "pickaxe"
                "sp_axe_scroll" -> "axe"
                "sp_hoe_scroll" -> "hoe"
                "sp_shovel_scroll" -> "shovel"
                "sp_bow_scroll" -> "bow"
                "sp_crossbow_scroll" -> "crossbow"
                "sp_shield_scroll" -> "shield"
                "sp_hammer_scroll" -> "hammer"
                else -> ""
            }
        }
        
        return ""
    }
    
    // 시즌별 아이템 수령 가능 여부 설정 메서드
    fun setSeasonReceivable(season: String, receivable: Boolean): Boolean {
        return when (season.lowercase()) {
            "할로윈" -> { isHalloweenReceivable = receivable; true }
            "크리스마스" -> { isChristmasReceivable = receivable; true }
            "발렌타인" -> { isValentineReceivable = receivable; true }
            "봄" -> { isSpringReceivable = receivable; true }
            else -> false
        }
    }
    
    // 시즌별 아이템 수령 가능 여부 확인 메서드
    fun isSeasonReceivable(season: String): Boolean {
        return when (season.lowercase()) {
            "할로윈" -> isHalloweenReceivable
            "크리스마스" -> isChristmasReceivable
            "발렌타인" -> isValentineReceivable
            "봄" -> isSpringReceivable
            else -> false
        }
    }
}

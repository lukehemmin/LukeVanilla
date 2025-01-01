package com.lukehemmin.lukeVanilla.System.Shop

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import net.citizensnpcs.api.CitizensAPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.text.DecimalFormat

class ShopListener(
    private val database: Database,
    private val shopManager: ShopManager,
    private val economyManager: EconomyManager,
    private val priceEditManager: PriceEditManager
) : Listener {

    private val formatter = DecimalFormat("#,###.##")

    @EventHandler
    fun onNPCClick(event: PlayerInteractEntityEvent) {
        val player = event.player
        val entity = event.rightClicked
        val npc = CitizensAPI.getNPCRegistry().getNPC(entity)
        if (npc != null) {
            // NPC가 상점 NPC인지 확인
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement("SELECT name FROM shops WHERE npc_id = ?")
                stmt.setInt(1, npc.id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val shopName = rs.getString("name")
                    openShopGUI(player, shopName)
                }
                rs.close()
                stmt.close()
            }
        }
    }

    fun openShopGUI(player: Player, shopName: String) {
        val shopData = getShopData(shopName) ?: return

        val inventoryTitle = "§6$shopName"
        val inventory: Inventory = Bukkit.createInventory(null, shopData.guiLines * 9, inventoryTitle)

        // DB에서 아이템 정보 불러오기
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("SELECT slot, item_uuid, buy_price, sell_price FROM shop_items WHERE shop_name = ?")
            stmt.setString(1, shopName)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                val slot = rs.getInt("slot")
                val itemUUID = rs.getString("item_uuid")
                val buyPrice = rs.getDouble("buy_price")
                val sellPrice = rs.getDouble("sell_price")

                val material = try {
                    Material.valueOf(itemUUID)
                } catch (e: IllegalArgumentException) {
                    null
                }

                if (material != null) {
                    val itemStack = ItemStack(material)
                    val meta = itemStack.itemMeta
                    val lore = mutableListOf<String>()
                    if (buyPrice > 0) {
                        lore.add("§a구매 가격: §f${formatter.format(buyPrice)} 원")
                    }
                    if (sellPrice > 0) {
                        lore.add("§c판매 가격: §f${formatter.format(sellPrice)} 원")
                    }
                    meta?.lore = if (lore.isNotEmpty()) lore else null
                    itemStack.itemMeta = meta
                    inventory.setItem(slot, itemStack)
                }
            }
            rs.close()
            stmt.close()
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        val inventoryTitle = event.view.title
        if (inventoryTitle.startsWith("§6")) {
            event.isCancelled = true
            val strippedTitle = ChatColor.stripColor(inventoryTitle)
            if (strippedTitle == null) return
            val shopName = strippedTitle.substringAfter("§6")
            val slot = event.rawSlot
            val clickType = event.click

            handleItemClick(player, shopName, slot, clickedItem, clickType)
        }
    }

    private fun handleItemClick(
        player: Player,
        shopName: String,
        slot: Int,
        item: ItemStack,
        clickType: org.bukkit.event.inventory.ClickType
    ) {
        // 구매
        if (clickType.isRightClick) {
            val buyPrice = getBuyPrice(shopName, slot)
            if (buyPrice > 0) {
                if (economyManager.getBalance(player) >= buyPrice) {
                    if (economyManager.removeBalance(player, buyPrice)) {
                        player.inventory.addItem(item.clone())
                        player.sendMessage("§a${item.type}을(를) §f${formatter.format(buyPrice)} 원 §a에 구매하였습니다.")
                    } else {
                        player.sendMessage("§c소지금이 부족합니다.")
                    }
                } else {
                    player.sendMessage("§c소지금이 부족합니다.")
                }
            }
        }

        // 판매
        if (clickType.isShiftClick) {
            val sellPrice = getSellPrice(shopName, slot)
            if (sellPrice > 0) {
                if (player.inventory.contains(item)) {
                    economyManager.addBalance(player, sellPrice)
                    player.inventory.removeItem(item.clone())
                    player.sendMessage("§a${item.type}을(를) §f${formatter.format(sellPrice)} 원 §a에 판매하였습니다.")
                } else {
                    player.sendMessage("§c해당 아이템이 인벤토리에 없습니다.")
                }
            }
        }

        // GUI 수정 모드인지 확인
        if (player.hasPermission("lukevanilla.shop.admin")) {
            if (clickType == org.bukkit.event.inventory.ClickType.LEFT) {
                // 아이템 삭제 로직
                deleteItem(shopName, slot)
                player.sendMessage("§c아이템이 삭제되었습니다.")
                shopManager.openItemEditGUI(player, shopName) // 수정된 부분
                return
            }

            // 가격 수정 모드
            if (clickType.isRightClick || clickType.isShiftClick) { // 수정된 부분
                val priceType = if (clickType.isRightClick) "buy" else "sell"
                // 가격 설정 상태 등록
                priceEditManager.startPriceEdit(player, shopName, slot, priceType)
            }
        }
    }

    private fun deleteItem(shopName: String, slot: Int) {
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("DELETE FROM shop_items WHERE shop_name = ? AND slot = ?")
            stmt.setString(1, shopName)
            stmt.setInt(2, slot)
            stmt.executeUpdate()
            stmt.close()
        }
    }

    private fun getBuyPrice(shopName: String, slot: Int): Double {
        var price = 0.0
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("SELECT buy_price FROM shop_items WHERE shop_name = ? AND slot = ?")
            stmt.setString(1, shopName)
            stmt.setInt(2, slot)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                price = rs.getDouble("buy_price")
            }
            rs.close()
            stmt.close()
        }
        return price
    }

    private fun getSellPrice(shopName: String, slot: Int): Double {
        var price = 0.0
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("SELECT sell_price FROM shop_items WHERE shop_name = ? AND slot = ?")
            stmt.setString(1, shopName)
            stmt.setInt(2, slot)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                price = rs.getDouble("sell_price")
            }
            rs.close()
            stmt.close()
        }
        return price
    }

    private fun getShopData(shopName: String): ShopData? {
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("SELECT gui_lines FROM shops WHERE name = ?")
            stmt.setString(1, shopName)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val guiLines = rs.getInt("gui_lines")
                rs.close()
                stmt.close()
                return ShopData(shopName, guiLines)
            }
            rs.close()
            stmt.close()
        }
        return null
    }

    data class ShopData(val name: String, val guiLines: Int)
}
package com.lukehemmin.lukeVanilla.System.Shop

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.LookClose
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.Location

class ShopManager(private val database: Database) {

    fun createShop(player: Player, shopName: String) {
        val location = player.location

        // NPC 생성: EntityType과 이름을 전달
        val npc: NPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, shopName)

        // LookClose 트레이트 추가
        npc.addTrait(LookClose::class.java)

        // NPC 스폰
        npc.spawn(location)
        val npcId = npc.id

        // 상점 정보 DB에 저장
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement(
                """
                INSERT INTO shops (name, npc_id, world, x, y, z, gui_lines) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE 
                    npc_id = VALUES(npc_id),
                    world = VALUES(world),
                    x = VALUES(x),
                    y = VALUES(y),
                    z = VALUES(z),
                    gui_lines = VALUES(gui_lines)
                """
            )
            stmt.setString(1, shopName)
            stmt.setInt(2, npcId)
            stmt.setString(3, location.world?.name)
            stmt.setDouble(4, location.x)
            stmt.setDouble(5, location.y)
            stmt.setDouble(6, location.z)
            stmt.setInt(7, 3) // 기본 GUI 줄 수
            stmt.executeUpdate()
            stmt.close()
        }

        player.sendMessage("§a상점 §f$shopName §a이 생성되었고 NPC가 배치되었습니다.")
    }

    fun setShopLines(shopName: String, lines: Int) {
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("UPDATE shops SET gui_lines = ? WHERE name = ?")
            stmt.setInt(1, lines)
            stmt.setString(2, shopName)
            val rows = stmt.executeUpdate()
            stmt.close()

            if (rows > 0) {
                // 상점의 줄 수가 성공적으로 업데이트됨을 알림
                // 필요 시 더 많은 로직 추가 가능
                // 예: player.sendMessage("§a상점의 줄 수가 성공적으로 업데이트되었습니다.")
            } else {
                // 상점 이름이 존재하지 않을 때 처리
                // 예: player.sendMessage("§c상점 이름을 찾을 수 없습니다.")
            }
        }
    }

    fun openItemEditGUI(player: Player, shopName: String) {
        val inventoryTitle = "§6$shopName 아이템 수정"
        val inventory: Inventory = Bukkit.createInventory(null, 54, inventoryTitle)

        // DB에서 아이템 정보 불러오기
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("SELECT slot, item_uuid FROM shop_items WHERE shop_name = ?")
            stmt.setString(1, shopName)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                val slot = rs.getInt("slot")
                val itemUUID = rs.getString("item_uuid")

                val material = try {
                    Material.valueOf(itemUUID)
                } catch (e: IllegalArgumentException) {
                    null
                }

                if (material != null) {
                    val itemStack = ItemStack(material)
                    val meta: ItemMeta? = itemStack.itemMeta
                    meta?.setDisplayName("§f$shopName 아이템")
                    itemStack.itemMeta = meta
                    inventory.setItem(slot, itemStack)
                }
            }
            rs.close()
            stmt.close()
        }

        player.openInventory(inventory)
    }

    fun startPriceEdit(player: Player, shopName: String, priceEditManager: PriceEditManager) {
        player.sendMessage("§a가격 설정을 시작합니다. 아이템을 클릭하여 설정할 가격 유형을 선택하세요.")
        // 가격 설정 상태를 관리하기 위한 로직은 PriceEditManager에서 처리
    }

    fun setPrice(shopName: String, slot: Int, priceType: String, price: Double) {
        val column = when (priceType.lowercase()) {
            "buy" -> "buy_price"
            "sell" -> "sell_price"
            else -> return
        }

        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("UPDATE shop_items SET $column = ? WHERE shop_name = ? AND slot = ?")
            stmt.setDouble(1, price)
            stmt.setString(2, shopName)
            stmt.setInt(3, slot)
            stmt.executeUpdate()
            stmt.close()
        }
    }

    fun openPriceEditGUI(player: Player, shopName: String) {
        // 가격 설정을 위한 GUI 구현 (추가적인 구현 필요)
        // 예: 아이템을 다시 클릭하여 다른 아이템의 가격을 설정할 수 있도록 함
    }
}
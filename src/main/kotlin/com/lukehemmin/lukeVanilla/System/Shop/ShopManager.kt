// ShopManager.kt
package com.lukehemmin.lukeVanilla.System.Shop

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.trait.Trait
import net.citizensnpcs.trait.LookClose
import net.citizensnpcs.trait.SkinTrait
import net.citizensnpcs.api.ai.Navigator
import net.citizensnpcs.api.ai.NavigatorParameters
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

class ShopManager(
    val plugin: Plugin, // 변경: private -> public
    private val database: Database,
    val economyManager: EconomyManager
) {

    private val shops = ConcurrentHashMap<String, Shop>()

    init {
        loadShopsFromDatabase()
    }

    private fun loadShopsFromDatabase() {
    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
        try {
            database.getConnection().use { connection ->
                // 상점 기본 정보 로드
                val stmt = connection.prepareStatement("SELECT * FROM shops")
                val rs = stmt.executeQuery()
                
                while (rs.next()) {
                    val name = rs.getString("name")
                    val npcId = rs.getInt("npc_id")
                    val rows = rs.getInt("rows")
                    
                    val shop = Shop(name, npcId, rows)
                    shops[name] = shop
                    
                    // 상점 아이템 로드
                    loadShopItems(shop)
                }
                
                rs.close()
                stmt.close()
                
                plugin.logger.info("데이터베이스에서 ${shops.size}개의 상점을 로드했습니다.")
            }
        } catch (e: Exception) {
            plugin.logger.severe("상점 로드 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    })
}

    private fun loadShopItems(shop: Shop) {
        try {
            database.getConnection().use { connection ->
                val stmt = connection.prepareStatement(
                    "SELECT * FROM shop_items WHERE shop_name = ?"
                )
                stmt.setString(1, shop.name)
                val rs = stmt.executeQuery()

                while (rs.next()) {
                    val slot = rs.getInt("slot")
                    val itemType = Material.valueOf(rs.getString("item_type"))
                    val itemMeta = rs.getString("item_meta")
                    val buyPrice = rs.getBigDecimal("buy_price")?.toDouble()
                    val sellPrice = rs.getBigDecimal("sell_price")?.toDouble()

                    val item = ItemStack(itemType)
                    val meta = item.itemMeta
                    meta?.setDisplayName(itemMeta)
                    item.itemMeta = meta

                    val shopItem = ShopItem(
                        slot = slot,
                        item = item,
                        buyPrice = buyPrice,
                        sellPrice = sellPrice
                    )
                    shop.items[slot] = shopItem
                }

                rs.close()
                stmt.close()
            }
        } catch (e: Exception) {
            plugin.logger.severe("상점 ${shop.name}의 아이템 로드 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    fun createShop(name: String, location: Location, player: Player): Boolean {
        if (shops.containsKey(name)) {
            return false
        }

        val citizensPlugin = Bukkit.getServer().pluginManager.getPlugin("Citizens") ?: return false
        val npcRegistry = CitizensAPI.getNPCRegistry()
        val npc = npcRegistry.createNPC(EntityType.PLAYER, name)

        // NPC에 스킨 설정
        try {
            val skinTrait = npc.getOrAddTrait(SkinTrait::class.java)
            skinTrait.setTexture(player.name, player.name)
        } catch (e: Exception) {
            plugin.logger.warning("스킨 설정 중 오류 발생: ${e.message}")
        }

        // NPC가 플레이어를 바라보도록 설정
        try {
            val lookTrait = npc.getOrAddTrait(LookClose::class.java)
            lookTrait.toggle()
            lookTrait.lookClose(true)
        } catch (e: Exception) {
            plugin.logger.warning("LookClose 설정 중 오류 발생: ${e.message}")
        }

        // NPC가 움직이지 않도록 설정
        try {
            val navigator = npc.navigator
            val params = NavigatorParameters()
            params.apply {
                speedModifier(1.0f)
                range(1.0f)
                attackRange(0.0)
                stationaryTicks(Integer.MAX_VALUE)
                distanceMargin(0.0)
            }
            
            // 파라미터 직접 설정
            navigator.defaultParameters.speedModifier(params.speedModifier())
            navigator.defaultParameters.range(params.range())
            navigator.defaultParameters.attackRange(params.attackRange())
            navigator.defaultParameters.stationaryTicks(params.stationaryTicks())
            navigator.defaultParameters.distanceMargin(params.distanceMargin())
            
            // 로컬 파라미터도 동일하게 설정
            navigator.localParameters.speedModifier(params.speedModifier())
            navigator.localParameters.range(params.range())
            navigator.localParameters.attackRange(params.attackRange())
            navigator.localParameters.stationaryTicks(params.stationaryTicks())
            navigator.localParameters.distanceMargin(params.distanceMargin())
        } catch (e: Exception) {
            plugin.logger.warning("Navigator 설정 중 오류 발생: ${e.message}")
        }

        npc.spawn(location)
        val npcId = npc.id

        val shop = Shop(name, npcId)
        shops[name] = shop

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val stmt = connection.prepareStatement(
                        "INSERT INTO shops (name, npc_id, `rows`) VALUES (?, ?, ?)"
                    )
                    stmt.setString(1, name)
                    stmt.setInt(2, npcId)
                    stmt.setInt(3, shop.rows)
                    stmt.executeUpdate()
                    stmt.close()
                }
                plugin.logger.info("Shop '$name' created by ${player.name} with NPC ID $npcId")
            } catch (e: Exception) {
                plugin.logger.severe("상점 생성 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        })

        return true
    }

    fun getShop(name: String): Shop? {
        return shops[name]
    }

    fun getShopByNPCId(npcId: Int): Shop? {
        return shops.values.find { it.npcId == npcId }
    }

    fun setShopRows(name: String, rows: Int): Boolean {
        val shop = shops[name] ?: return false
        shop.rows = rows

        // 데이터베이스 업데이트
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val stmt = connection.prepareStatement("UPDATE shops SET `rows` = ? WHERE name = ?")
                    stmt.setInt(1, rows)
                    stmt.setString(2, name)
                    stmt.executeUpdate()
                    stmt.close()
                }
                plugin.logger.info("Shop '$name' rows set to $rows.")
            } catch (e: Exception) {
                plugin.logger.severe("상점 줄 수 설정 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        })

        return true
    }

    fun addItemToShop(shopName: String, slot: Int, shopItem: ShopItem): Boolean {
        val shop = shops[shopName] ?: return false
        shop.items[slot] = shopItem

        // 데이터베이스에 저장
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val stmt = connection.prepareStatement(
                        "INSERT INTO shop_items (shop_name, slot, item_type, item_meta, buy_price, sell_price) VALUES (?, ?, ?, ?, ?, ?)"
                    )
                    stmt.setString(1, shopName)
                    stmt.setInt(2, slot)
                    stmt.setString(3, shopItem.item.type.name)
                    stmt.setString(4, shopItem.item.itemMeta?.displayName ?: "Unnamed Item")
                    stmt.setDouble(5, shopItem.buyPrice ?: 0.0)
                    stmt.setDouble(6, shopItem.sellPrice ?: 0.0)
                    stmt.executeUpdate()
                    stmt.close()
                }
                plugin.logger.info("Added item to shop '$shopName' at slot $slot.")
            } catch (e: Exception) {
                plugin.logger.severe("상점 아이템 추가 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        })

        return true
    }

    fun updateShopItemPrice(shop: Shop, shopItem: ShopItem) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    val stmt = connection.prepareStatement(
                        "UPDATE shop_items SET buy_price = ?, sell_price = ? WHERE shop_name = ? AND slot = ?"
                    )
                    stmt.setDouble(1, shopItem.buyPrice ?: 0.0)
                    stmt.setDouble(2, shopItem.sellPrice ?: 0.0)
                    stmt.setString(3, shop.name)
                    stmt.setInt(4, shopItem.slot)
                    stmt.executeUpdate()
                    stmt.close()
                }
                plugin.logger.info("Updated prices for shop '${shop.name}' slot ${shopItem.slot}.")
            } catch (e: Exception) {
                plugin.logger.severe("상점 가격 업데이트 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    fun saveShopItems(shop: Shop) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                database.getConnection().use { connection ->
                    // 기존 아이템 삭제
                    val deleteStmt = connection.prepareStatement(
                        "DELETE FROM shop_items WHERE shop_name = ?"
                    )
                    deleteStmt.setString(1, shop.name)
                    deleteStmt.executeUpdate()
                    deleteStmt.close()

                    // 새 아이템 추가
                    val insertStmt = connection.prepareStatement(
                        "INSERT INTO shop_items (shop_name, slot, item_type, item_meta, buy_price, sell_price) VALUES (?, ?, ?, ?, ?, ?)"
                    )
                    
                    shop.items.forEach { (slot, item) ->
                        insertStmt.setString(1, shop.name)
                        insertStmt.setInt(2, slot)
                        insertStmt.setString(3, item.item.type.name)
                        insertStmt.setString(4, item.item.itemMeta?.displayName ?: "")
                        insertStmt.setObject(5, item.buyPrice)
                        insertStmt.setObject(6, item.sellPrice)
                        insertStmt.executeUpdate()
                    }
                    insertStmt.close()
                }
            } catch (e: Exception) {
                plugin.logger.severe("상점 아이템 저장 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    // 추가적인 상점 수정, 삭제 메서드 구현 가능
}

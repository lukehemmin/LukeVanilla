// NextSeasonItemGUI.kt (변경된 serializeItem 및 deserializeItem 메서드 사용)
package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.util.Base64
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

class NextSeasonItemGUI(private val plugin: JavaPlugin, private val database: Database) : Listener, CommandExecutor {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val playerUUID = sender.uniqueId.toString()
            if (isItemRegistered(playerUUID)) {
                sender.sendMessage("이미 아이템이 등록되어있습니다.")
            } else {
                openInventory(sender)
            }
        }
        return true
    }

    fun openInventory(player: Player) {
        val inventory: Inventory = Bukkit.createInventory(null, 27, "다음 시즌 아이템 추가")

        // 검은색 유리 판 아이템 생성
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta: ItemMeta = blackGlass.itemMeta!!
        meta.setDisplayName(" ")
        blackGlass.itemMeta = meta

        // 모든 슬롯에 검은색 유리 판 삽입
        for (i in 0 until 27) {
            inventory.setItem(i, blackGlass)
        }

        // 중앙 슬롯에 아이템 추가를 위한 공간 제공 (비어있음)
        val centerSlot = 13
        inventory.setItem(centerSlot, null) // 빈 슬롯

        // 중앙 아래 슬롯(인덱스 22)에 연두색 양털 추가
        val confirmItem = ItemStack(Material.LIME_WOOL)
        val confirmMeta: ItemMeta = confirmItem.itemMeta!!
        confirmMeta.setDisplayName("§a§l다음 시즌에 가져가기!")
        confirmMeta.lore = listOf(
            "§7이 아이템을 클릭하면 다음 시즌에 받을 수 있습니다.",
            "§7클릭 이후에는 변경할 수 없습니다.",
            "§7신중하게 결정해주세요!"
        )
        confirmItem.itemMeta = confirmMeta
        inventory.setItem(22, confirmItem)

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // GUI의 제목이 아닌 경우 무시
        if (event.view.title != "다음 시즌 아이템 추가") return

        val clickedInventory = event.clickedInventory
        val topInventory = event.view.topInventory

        // 클릭한 인벤토리가 상단 GUI인지 확인
        if (clickedInventory != topInventory) {
            // 하단 플레이어 인벤토리 클릭 시 허용
            return
        }

        val clickedSlot = event.rawSlot

        // 중앙 슬롯(인덱스 13)만 상호작용 가능
        if (clickedSlot == 13) {
            val player = event.whoClicked as Player
            when (event.action) {
                InventoryAction.PLACE_ALL,
                InventoryAction.PLACE_SOME,
                InventoryAction.PLACE_ONE -> {
                    val currentItem = event.cursor
                    if (currentItem != null && currentItem.type != Material.AIR) {
                        // 특정 아이템(셸커 상자) 방지
                        if (currentItem.type.toString().endsWith("_SHULKER_BOX")) { // 모든 색상의 셸커 상자 포함
                            player.sendMessage("§c셸커 상자는 이 슬롯에 넣을 수 없습니다.")
                            event.isCancelled = true
                            return
                        }

                        // 한 개의 아이템만 허용
                        if (topInventory.getItem(13) != null && topInventory.getItem(13)?.type != Material.AIR) {
                            player.sendMessage("이미 아이템이 설정되어 있습니다.")
                            event.isCancelled = true
                            return
                        }

                        // 아이템을 중앙 슬롯에 배치
                        val itemToPlace = currentItem.clone().apply { amount = 1 }
                        topInventory.setItem(13, itemToPlace)

                        // 커서에서 아이템 하나 제거
                        if (currentItem.amount > 1) {
                            event.setCursor(currentItem.clone().apply { amount = currentItem.amount - 1 })
                        } else {
                            event.setCursor(ItemStack(Material.AIR))
                        }

                        // 메시지 변경
                        player.sendMessage("아이템이 GUI에 추가되었습니다. 저장하려면 초록색 버튼을 클릭해주세요.")
                        event.isCancelled = true
                    }
                }
                InventoryAction.PICKUP_ALL,
                InventoryAction.PICKUP_SOME,
                InventoryAction.PICKUP_ONE -> {
                    val item = topInventory.getItem(13)
                    if (item != null && item.type != Material.AIR) {
                        // 아이템을 플레이어 인벤토리로 되돌리기
                        player.inventory.addItem(item.clone())
                        topInventory.setItem(13, ItemStack(Material.AIR))
                        player.sendMessage("다음 시즌에 가져갈 아이템이 제거되었습니다.")
                        event.isCancelled = true
                    }
                }
                else -> {}
            }
        } else if (clickedSlot == 22) { // 연두색 양털 클릭 시
            event.isCancelled = true
            val player = event.whoClicked as Player
            val itemToTake = topInventory.getItem(13)

            if (itemToTake == null || itemToTake.type == Material.AIR) {
                player.sendMessage("아이템을 넣지 않았습니다. 아이템을 넣어주세요.")
            } else {
                // 데이터베이스에 아이템 저장
                if (saveNextSeasonItem(player.uniqueId.toString(), itemToTake)) {
                    player.sendMessage("다음 시즌에 가져가기 선택이 완료되었습니다.")
                    player.closeInventory()
                } else {
                    player.sendMessage("아이템을 저장하는 데 실패했습니다. 나중에 다시 시도해주세요.")
                }
            }
        } else {
            // 중앙 슬롯 외의 상호작용은 취소
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title != "다음 시즌 아이템 추가") return

        val player = event.player as Player
        val inventory = event.inventory
        val itemInCenter = inventory.getItem(13)

        if (itemInCenter != null && itemInCenter.type != Material.AIR) {
            // 플레이어 인벤토리에 아이템을 추가 시도
            val remaining = player.inventory.addItem(itemInCenter)
            if (remaining.isNotEmpty()) {
                // 인벤토리가 가득 찬 경우, 플레이어 주변에 아이템 드롭
                remaining.values.forEach { item ->
                    player.world.dropItemNaturally(player.location, item)
                }
                player.sendMessage("인벤토리가 가득 찼습니다. 아이템을 주변에 드롭했습니다.")
            } else {
                player.sendMessage("GUI를 닫으면서 중앙 슬롯의 아이템이 인벤토리로 반환되었습니다.")
            }
            // 중앙 슬롯 비우기
            inventory.setItem(13, ItemStack(Material.AIR))
        }
    }

    private fun isItemRegistered(uuid: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT COUNT(*) AS count FROM Nextseason_Item WHERE UUID = ?"
                val preparedStatement = connection.prepareStatement(query)
                preparedStatement.setString(1, uuid)
                val resultSet = preparedStatement.executeQuery()
                if (resultSet.next()) {
                    resultSet.getInt("count") > 0
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveNextSeasonItem(uuid: String, item: ItemStack): Boolean {
        val serializedItem = serializeItem(item)
        return try {
            database.getConnection().use { connection ->
                val query = """
                    INSERT INTO Nextseason_Item (UUID, Item_Type, Item_Data) 
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        Item_Type = VALUES(Item_Type),
                        Item_Data = VALUES(Item_Data)
                """
                val preparedStatement = connection.prepareStatement(query)
                preparedStatement.setString(1, uuid)
                preparedStatement.setString(2, item.type.name)
                preparedStatement.setString(3, serializedItem)
                preparedStatement.executeUpdate()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun deserializeItem(data: String): ItemStack? {
        return try {
            val bytes = Base64.getDecoder().decode(data)
            val byteStream = ByteArrayInputStream(bytes)
            val objectStream = ObjectInputStream(byteStream)
            val map = objectStream.readObject() as Map<String, Any>
            objectStream.close()
            ItemStack.deserialize(map)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun serializeItem(item: ItemStack): String {
        return try {
            val serializedMap = item.serialize()
            val byteStream = ByteArrayOutputStream()
            val objectStream = ObjectOutputStream(byteStream)
            objectStream.writeObject(serializedMap)
            objectStream.close()
            Base64.getEncoder().encodeToString(byteStream.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
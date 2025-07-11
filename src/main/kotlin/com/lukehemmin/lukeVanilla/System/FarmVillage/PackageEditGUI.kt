package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID

class PackageEditGUI(private val plugin: Main, private val farmVillageData: FarmVillageData) : Listener {

    private val gson = Gson()
    private val inventories = mutableMapOf<UUID, Inventory>()
    private val inventoryTitle = "입주 패키지 수정"

    fun open(player: Player) {
        val inventory = Bukkit.createInventory(player, 54, Component.text(inventoryTitle))
        
        // Load items from DB
        val packageItems = farmVillageData.getPackageItems()
        packageItems.forEach { item ->
            val itemStack = deserializeItem(item)
            inventory.setItem(item.slot, itemStack)
        }

        // Set up GUI buttons
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ")) }
        }
        for (i in 45..53) {
            if (i != 49) inventory.setItem(i, blackGlass)
        }
        
        val saveButton = ItemStack(Material.GREEN_WOOL).apply {
            itemMeta = itemMeta.apply { 
                displayName(Component.text("클릭하여 저장하기", NamedTextColor.GREEN, TextDecoration.BOLD))
                lore(listOf(Component.text("현재 아이템을 입주 패키지로 저장합니다.", NamedTextColor.GRAY)))
            }
        }
        val closeButton = ItemStack(Material.RED_WOOL).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("닫기", NamedTextColor.RED, TextDecoration.BOLD))
                lore(listOf(Component.text("변경사항을 저장하지 않고 닫습니다.", NamedTextColor.GRAY)))
            }
        }
        inventory.setItem(48, saveButton)
        inventory.setItem(50, closeButton)

        inventories[player.uniqueId] = inventory
        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text(inventoryTitle)) return
        val player = event.whoClicked as? Player ?: return

        val clickedInventory = event.clickedInventory
        val topInventory = event.view.topInventory

        // 클릭된 인벤토리가 GUI(상단 인벤토리)인 경우에만 로직을 처리합니다.
        if (clickedInventory == topInventory) {
            // 관리 버튼 영역(45-53번 슬롯)을 클릭한 경우
            if (event.slot >= 45) {
                event.isCancelled = true // 관리 영역의 아이템을 가져가지 못하도록 막습니다.

                when (event.slot) {
                    48 -> { // 저장 버튼
                        val inventory = inventories[player.uniqueId] ?: return
                        savePackage(inventory)
                        player.sendMessage(Component.text("입주 패키지 아이템을 성공적으로 저장했습니다.", NamedTextColor.GREEN))
                        player.closeInventory()
                    }
                    50 -> { // 닫기 버튼
                        player.closeInventory()
                    }
                }
            }
            // 0-44번 슬롯 클릭은 취소되지 않으므로 아이템을 자유롭게 놓을 수 있습니다.
        }
        // 플레이어 인벤토리(하단 인벤토리)를 클릭한 경우, 이 리스너는 아무 작업도 하지 않습니다.
        // 따라서 플레이어는 자유롭게 자기 인벤토리의 아이템을 집거나 내려놓을 수 있습니다.
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == Component.text(inventoryTitle)) {
            inventories.remove(event.player.uniqueId)
        }
    }

    private fun savePackage(inventory: Inventory) {
        val itemsToSave = mutableListOf<PackageItem>()
        for (slot in 0..44) {
            val itemStack = inventory.getItem(slot)
            if (itemStack != null && itemStack.type != Material.AIR) {
                val packageItem = serializeItem(itemStack, slot)
                itemsToSave.add(packageItem)
            }
        }
        farmVillageData.savePackageItems(itemsToSave)
    }

    private fun serializeItem(item: ItemStack, slot: Int): PackageItem {
        val nexoId = NexoItems.idFromItem(item)
        val itemDataMap = mutableMapOf<String, Any>()
        itemDataMap["amount"] = item.amount
        item.itemMeta?.let { meta ->
            if (meta.hasDisplayName()) itemDataMap["name"] = gson.toJson(meta.displayName())
            if (meta.hasLore()) itemDataMap["lore"] = gson.toJson(meta.lore())
            if (meta.hasEnchants()) {
                itemDataMap["enchants"] = meta.enchants.mapKeys { it.key.key.toString() }
            }
        }

        return if (nexoId != null) {
            PackageItem(slot, "NEXO", nexoId, gson.toJson(itemDataMap))
        } else {
            PackageItem(slot, "VANILLA", item.type.name, gson.toJson(itemDataMap))
        }
    }

    private fun deserializeItem(itemInfo: PackageItem): ItemStack? {
        val itemData: Map<String, Any> = try {
            gson.fromJson(itemInfo.itemData, object : TypeToken<Map<String, Any>>() {}.type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val baseItem = when (itemInfo.itemType) {
            "NEXO" -> NexoItems.itemFromId(itemInfo.identifier)?.build()
            "VANILLA" -> ItemStack(Material.getMaterial(itemInfo.identifier) ?: Material.AIR)
            else -> null
        } ?: return null
        
        baseItem.amount = (itemData["amount"] as? Double)?.toInt() ?: 1

        baseItem.itemMeta = baseItem.itemMeta?.apply {
            (itemData["name"] as? String)?.let { setDisplayName(it) }
            (itemData["lore"] as? String)?.let { lore = gson.fromJson(it, object : TypeToken<List<String>>() {}.type) }
            (itemData["enchants"] as? Map<String, Double>)?.forEach { (key, level) ->
                val enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(key.substringAfter(":")))
                if (enchantment != null) {
                    addEnchant(enchantment, level.toInt(), true)
                }
            }
        }
        return baseItem
    }
    
    // Helper to set display name from json
    private fun ItemMeta.setDisplayName(json: String) {
        try {
            val component = gson.fromJson(json, Component::class.java)
            this.displayName(component)
        } catch (e: Exception) {
            // Fallback for old string names
            this.setDisplayName(json)
        }
    }
} 
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

        if (event.rawSlot >= 45) {
            event.isCancelled = true // Prevent taking items from the bottom bar
        }
        
        when (event.rawSlot) {
            48 -> { // Save button
                val inventory = inventories[player.uniqueId] ?: return
                savePackage(inventory)
                player.sendMessage(Component.text("입주 패키지 아이템을 성공적으로 저장했습니다.", NamedTextColor.GREEN))
                player.closeInventory()
            }
            50 -> { // Close button
                player.closeInventory()
            }
        }
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
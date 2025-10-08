package com.lukehemmin.lukeVanilla.System.FishMerchant

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

data class FishIdentificationResult(
    val provider: String,  // VANILLA, CUSTOMFISHING, NEXO
    val fishId: String,
    val displayName: String
)

class FishMerchantManager(
    private val plugin: JavaPlugin,
    database: Database,
    private val economyManager: EconomyManager
) {
    private val data = FishMerchantData(database)
    private val gui: FishMerchantGUI by lazy { FishMerchantGUI(plugin, this) }

    /**
     * NPC를 낚시 상인으로 설정
     */
    fun setFishMerchant(npcId: Int): Boolean {
        val npc = CitizensAPI.getNPCRegistry().getById(npcId)
        if (npc == null) {
            return false
        }
        data.saveNPCMerchant(npcId)
        return true
    }

    /**
     * 낚시 상인 NPC 해제
     */
    fun removeFishMerchant() {
        data.removeNPCMerchant()
    }

    /**
     * 낚시 상인 NPC ID 조회
     */
    fun getFishMerchantNPC(): NPC? {
        val npcId = data.getNPCMerchant() ?: return null
        return CitizensAPI.getNPCRegistry().getById(npcId)
    }

    /**
     * NPC ID로 낚시 상인인지 확인
     */
    fun isFishMerchant(npcId: Int): Boolean {
        return data.getNPCMerchant() == npcId
    }

    /**
     * 물고기 식별 (CustomFishing → Nexo → Vanilla 순서)
     */
    fun identifyFish(itemStack: ItemStack): FishIdentificationResult? {
        // 1. CustomFishing 확인 (리플렉션 사용)
        try {
            val customFishingPlugin = plugin.server.pluginManager.getPlugin("CustomFishing")
            if (customFishingPlugin != null && customFishingPlugin.isEnabled) {
                // 리플렉션으로 BukkitCustomFishingPlugin.getInstance() 호출
                val pluginClass = Class.forName("net.momirealms.customfishing.api.BukkitCustomFishingPlugin")
                val getInstanceMethod = pluginClass.getMethod("getInstance")
                val cfPluginInstance = getInstanceMethod.invoke(null)

                // ItemManager 가져오기
                val getItemManagerMethod = pluginClass.getMethod("getItemManager")
                val itemManager = getItemManagerMethod.invoke(cfPluginInstance)

                // getCustomFishingItemID 호출
                val getItemIdMethod = itemManager.javaClass.getMethod("getCustomFishingItemID", ItemStack::class.java)
                val itemId = getItemIdMethod.invoke(itemManager, itemStack) as? String

                if (itemId != null) {
                    val displayName = itemStack.itemMeta?.displayName()
                    val displayNameText = if (displayName != null) {
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)
                    } else {
                        itemId
                    }
                    return FishIdentificationResult(
                        "CUSTOMFISHING",
                        itemId,
                        displayNameText
                    )
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("CustomFishing API 사용 중 오류: ${e.message}")
        }

        // 2. Nexo 확인
        try {
            val nexoPlugin = plugin.server.pluginManager.getPlugin("Nexo")
            if (nexoPlugin != null && nexoPlugin.isEnabled) {
                val nexoId = com.nexomc.nexo.api.NexoItems.idFromItem(itemStack)
                if (nexoId != null) {
                    val displayName = itemStack.itemMeta?.displayName()
                    val displayNameText = if (displayName != null) {
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)
                    } else {
                        nexoId
                    }
                    return FishIdentificationResult(
                        "NEXO",
                        nexoId,
                        displayNameText
                    )
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Nexo API 사용 중 오류: ${e.message}")
        }

        // 3. 바닐라 물고기 확인
        val vanillaFish = listOf(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH)
        if (itemStack.type in vanillaFish) {
            return FishIdentificationResult(
                "VANILLA",
                itemStack.type.name,
                itemStack.type.name.lowercase().replace('_', ' ')
            )
        }

        return null
    }

    /**
     * 물고기 가격 설정
     */
    fun setFishPrice(provider: String, fishType: String, price: Double): Boolean {
        data.setFishPrice(provider, fishType, price)
        return true
    }

    /**
     * 물고기 가격 조회
     */
    fun getFishPrice(provider: String, fishType: String): Double? {
        return data.getFishPrice(provider, fishType)
    }

    /**
     * 모든 물고기 가격 조회
     */
    fun getAllFishPrices(): List<FishPrice> {
        return data.getAllFishPrices()
    }

    /**
     * 플레이어가 낚시 상인에게 물고기 판매
     */
    fun sellFish(player: Player, itemStack: ItemStack, amount: Int): Boolean {
        // 물고기 식별
        val fishInfo = identifyFish(itemStack)
        if (fishInfo == null) {
            player.sendMessage(Component.text("이 아이템은 물고기가 아닙니다.", NamedTextColor.RED))
            return false
        }

        // 가격 확인
        val price = data.getFishPrice(fishInfo.provider, fishInfo.fishId)
        if (price == null) {
            player.sendMessage(Component.text("이 물고기는 구매하지 않습니다.", NamedTextColor.RED))
            return false
        }

        // 플레이어 인벤토리에서 물고기 확인
        val fishCount = countItems(player, itemStack)
        if (fishCount < amount) {
            player.sendMessage(Component.text("물고기가 부족합니다. (보유: ${fishCount}개)", NamedTextColor.RED))
            return false
        }

        // 물고기 제거
        removeItems(player, itemStack, amount)

        // 돈 지급
        val totalPrice = price * amount
        economyManager.addBalance(player, totalPrice)

        player.sendMessage(
            Component.text("[${fishInfo.provider}] ${fishInfo.displayName} ${amount}개를 ", NamedTextColor.GREEN)
                .append(Component.text("${totalPrice}원", NamedTextColor.GOLD))
                .append(Component.text("에 판매했습니다.", NamedTextColor.GREEN))
        )

        return true
    }

    /**
     * 인벤토리에서 특정 아이템과 동일한 아이템 개수 세기
     */
    private fun countItems(player: Player, targetItem: ItemStack): Int {
        var count = 0
        player.inventory.contents.forEach { item ->
            if (item != null && item.isSimilar(targetItem)) {
                count += item.amount
            }
        }
        return count
    }

    /**
     * 인벤토리에서 특정 아이템 제거
     */
    private fun removeItems(player: Player, targetItem: ItemStack, amount: Int) {
        var remaining = amount
        val inventory = player.inventory

        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.isSimilar(targetItem)) {
                val itemAmount = item.amount
                if (itemAmount <= remaining) {
                    inventory.setItem(i, null)
                    remaining -= itemAmount
                } else {
                    item.amount = itemAmount - remaining
                    remaining = 0
                }

                if (remaining == 0) break
            }
        }
    }

    /**
     * 낚시 상인 GUI 열기
     */
    fun openFishMerchantGUI(player: Player) {
        gui.openGUI(player)
    }
}

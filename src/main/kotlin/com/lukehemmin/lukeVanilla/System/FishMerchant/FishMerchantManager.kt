package com.lukehemmin.lukeVanilla.System.FishMerchant

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import com.lukehemmin.lukeVanilla.System.Economy.TransactionType
import com.lukehemmin.lukeVanilla.System.NPC.NPCInteractionRouter
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
    val displayName: String,
    val size: Double? = null  // CustomFishing 물고기 크기 (cm)
)

class FishMerchantManager(
    private val plugin: JavaPlugin,
    database: Database,
    private val economyManager: EconomyManager,
    private val npcRouter: NPCInteractionRouter
) {
    private val data = FishMerchantData(database)
    private val gui: FishMerchantGUI by lazy { FishMerchantGUI(plugin, this) }

    init {
        // 서버 시작 시 등록된 낚시 상인 NPC를 라우터에 등록
        val registeredNpcId = data.getNPCMerchant()
        if (registeredNpcId != null) {
            npcRouter.register(registeredNpcId) { player ->
                openFishMerchantGUI(player)
            }
            plugin.logger.info("[FishMerchant] 낚시 상인 NPC(ID: $registeredNpcId)를 라우터에 등록했습니다.")
        }
    }

    /**
     * NPC를 낚시 상인으로 설정
     * @return Pair<성공 여부, 이전 NPC ID>
     */
    fun setFishMerchant(npcId: Int): Pair<Boolean, Int?> {
        val npc = CitizensAPI.getNPCRegistry().getById(npcId)
        if (npc == null) {
            return Pair(false, null)
        }
        val previousNpcId = data.saveNPCMerchant(npcId)
        
        // 이전 NPC가 있다면 라우터에서 해제
        if (previousNpcId != null) {
            npcRouter.unregister(previousNpcId)
        }
        
        // 새 NPC 라우터에 등록
        npcRouter.register(npcId) { player ->
            openFishMerchantGUI(player)
        }
        
        return Pair(true, previousNpcId)
    }

    /**
     * 낚시 상인 NPC 해제
     */
    fun removeFishMerchant() {
        val npcId = data.getNPCMerchant()
        if (npcId != null) {
            npcRouter.unregister(npcId)
        }
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
        val registeredNpcId = data.getNPCMerchant()
        return registeredNpcId == npcId
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

                    // 크기 정보 추출 (displayName 또는 lore에서)
                    val size = extractSizeFromItem(itemStack)

                    return FishIdentificationResult(
                        "CUSTOMFISHING",
                        itemId,
                        displayNameText,
                        size
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
     * 물고기 가격 설정 (단순 가격)
     */
    fun setFishPrice(provider: String, fishType: String, price: Double): Boolean {
        data.setFishPrice(provider, fishType, price)
        return true
    }

    /**
     * 물고기 가격 설정 (크기 기반)
     */
    fun setFishPriceWithSize(provider: String, fishType: String, basePrice: Double, pricePerCm: Double): Boolean {
        data.setFishPriceWithSize(provider, fishType, basePrice, pricePerCm)
        return true
    }

    /**
     * 물고기 가격 조회 (기본 가격만)
     */
    fun getFishPrice(provider: String, fishType: String): Double? {
        return data.getFishPrice(provider, fishType)
    }

    /**
     * 물고기 실제 판매 가격 계산 (크기 포함)
     */
    fun calculateFishPrice(fishInfo: FishIdentificationResult): Double? {
        val priceInfo = data.getFishPriceInfo(fishInfo.provider, fishInfo.fishId) ?: return null

        // 크기 정보가 있고 cm당 가격이 설정되어 있으면 크기 기반 계산
        return if (fishInfo.size != null && priceInfo.pricePerCm > 0) {
            priceInfo.basePrice + (fishInfo.size * priceInfo.pricePerCm)
        } else {
            // 크기 정보가 없거나 cm당 가격이 0이면 기본 가격만
            priceInfo.basePrice
        }
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
        economyManager.deposit(
            player, 
            totalPrice, 
            TransactionType.SHOP_SELL, 
            "물고기 판매: [${fishInfo.provider}] ${fishInfo.displayName} x$amount"
        )

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
     * ItemStack에서 물고기 크기 추출 (displayName 또는 lore에서)
     * 예:
     *   - displayName: "참치 (150.5cm)" → 150.5
     *   - lore: "크기: 150.5cm" → 150.5
     *   - lore: "Size: 150.5cm" → 150.5
     */
    private fun extractSizeFromItem(itemStack: ItemStack): Double? {
        return try {
            val meta = itemStack.itemMeta ?: return null

            // 1. displayName에서 크기 찾기
            val displayName = meta.displayName()
            if (displayName != null) {
                val displayNameText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)
                val sizeFromName = extractSizeFromText(displayNameText)
                if (sizeFromName != null) return sizeFromName
            }

            // 2. lore에서 크기 찾기
            val lore = meta.lore()
            if (lore != null && lore.isNotEmpty()) {
                for (loreLine in lore) {
                    val loreText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(loreLine)
                    val sizeFromLore = extractSizeFromText(loreText)
                    if (sizeFromLore != null) return sizeFromLore
                }
            }

            null
        } catch (e: Exception) {
            plugin.logger.warning("크기 추출 중 오류: ${e.message}")
            null
        }
    }

    /**
     * 텍스트에서 크기 추출
     * 다양한 패턴 지원:
     *   - "크기: 150.5cm"
     *   - "(150.5cm)"
     *   - "150.5cm"
     */
    private fun extractSizeFromText(text: String): Double? {
        return try {
            // 패턴 1: "크기: 숫자cm" 또는 "Size: 숫자cm" (lore 전용)
            val pattern1 = Regex("""(?:크기|Size|size)\s*[:：]\s*(\d+(?:\.\d+)?)\s*cm""", RegexOption.IGNORE_CASE)
            val match1 = pattern1.find(text)
            if (match1 != null) {
                return match1.groupValues[1].toDoubleOrNull()
            }

            // 패턴 2: "(숫자cm)" 형태 (displayName 전용)
            val pattern2 = Regex("""[(（](\d+(?:\.\d+)?)\s*cm[)）]""")
            val match2 = pattern2.find(text)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull()
            }

            // 패턴 3: "숫자cm" 형태 (일반)
            val pattern3 = Regex("""(\d+(?:\.\d+)?)\s*cm""")
            val match3 = pattern3.find(text)
            if (match3 != null) {
                return match3.groupValues[1].toDoubleOrNull()
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 낚시 상인 GUI 열기
     */
    fun openFishMerchantGUI(player: Player) {
        gui.openGUI(player)
    }

    /**
     * 판매 기록 저장
     * @param player 판매자 플레이어
     * @param itemsSold 판매한 아이템 맵 (key: "PROVIDER:FISH_ID", value: 개수)
     * @param totalAmount 총 판매 금액
     */
    fun saveSellHistory(player: Player, itemsSold: Map<String, Int>, totalAmount: Double) {
        val record = FishSellRecord(
            playerUuid = player.uniqueId,
            playerName = player.name,
            itemsSold = itemsSold,
            totalAmount = totalAmount
        )
        data.saveSellHistory(record)
    }
}

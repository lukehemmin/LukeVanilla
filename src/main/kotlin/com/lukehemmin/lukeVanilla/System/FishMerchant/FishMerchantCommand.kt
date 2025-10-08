package com.lukehemmin.lukeVanilla.System.FishMerchant

import net.citizensnpcs.api.CitizensAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class FishMerchantCommand(private val fishMerchantManager: FishMerchantManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lukevanilla.fishmerchant")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0]) {
            "설정" -> handleSet(sender, args)
            "제거" -> handleRemove(sender)
            "가격설정" -> handlePrice(sender, args)
            "가격목록" -> handlePrices(sender)
            "정보" -> handleInfo(sender)
            else -> sendHelp(sender)
        }

        return true
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("사용법: /낚시상인 설정 <NPC아이디>", NamedTextColor.RED))
            return
        }

        val npcId = args[1].toIntOrNull()
        if (npcId == null) {
            sender.sendMessage(Component.text("NPC ID는 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        val npc = CitizensAPI.getNPCRegistry().getById(npcId)
        if (npc == null) {
            sender.sendMessage(Component.text("해당 ID의 NPC를 찾을 수 없습니다.", NamedTextColor.RED))
            return
        }

        if (fishMerchantManager.setFishMerchant(npcId)) {
            sender.sendMessage(
                Component.text("NPC '", NamedTextColor.GREEN)
                    .append(Component.text(npc.name, NamedTextColor.YELLOW))
                    .append(Component.text("'을(를) 낚시 상인으로 설정했습니다.", NamedTextColor.GREEN))
            )
        } else {
            sender.sendMessage(Component.text("NPC 설정에 실패했습니다.", NamedTextColor.RED))
        }
    }

    private fun handleRemove(sender: CommandSender) {
        val npc = fishMerchantManager.getFishMerchantNPC()
        if (npc == null) {
            sender.sendMessage(Component.text("설정된 낚시 상인이 없습니다.", NamedTextColor.RED))
            return
        }

        fishMerchantManager.removeFishMerchant()
        sender.sendMessage(Component.text("낚시 상인 설정을 제거했습니다.", NamedTextColor.GREEN))
    }

    private fun handlePrice(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage(Component.text("사용법: /낚시상인 가격설정 <VANILLA|CUSTOMFISHING|NEXO> <물고기ID> <가격>", NamedTextColor.RED))
            sender.sendMessage(Component.text("예시:", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("  /낚시상인 가격설정 VANILLA COD 10.0", NamedTextColor.GRAY))
            sender.sendMessage(Component.text("  /낚시상인 가격설정 CUSTOMFISHING legendary_fish 1000.0", NamedTextColor.GRAY))
            sender.sendMessage(Component.text("  /낚시상인 가격설정 NEXO custom_fish 500.0", NamedTextColor.GRAY))
            return
        }

        val provider = args[1].uppercase()
        if (provider !in listOf("VANILLA", "CUSTOMFISHING", "NEXO")) {
            sender.sendMessage(Component.text("잘못된 제공자입니다. VANILLA, CUSTOMFISHING, NEXO 중 하나를 입력하세요.", NamedTextColor.RED))
            return
        }

        val fishType = args[2]
        val price = args[3].toDoubleOrNull()
        if (price == null || price < 0) {
            sender.sendMessage(Component.text("가격은 0 이상의 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        if (fishMerchantManager.setFishPrice(provider, fishType, price)) {
            sender.sendMessage(
                Component.text("[$provider] ${fishType}의 가격을 ", NamedTextColor.GREEN)
                    .append(Component.text("${price}원", NamedTextColor.GOLD))
                    .append(Component.text("으로 설정했습니다.", NamedTextColor.GREEN))
            )
        } else {
            sender.sendMessage(Component.text("가격 설정에 실패했습니다.", NamedTextColor.RED))
        }
    }

    private fun handlePrices(sender: CommandSender) {
        val prices = fishMerchantManager.getAllFishPrices()
        if (prices.isEmpty()) {
            sender.sendMessage(Component.text("설정된 물고기 가격이 없습니다.", NamedTextColor.RED))
            return
        }

        sender.sendMessage(Component.text("=== 물고기 가격 목록 ===", NamedTextColor.GOLD))

        // 제공자별로 그룹화
        val groupedPrices = prices.groupBy { it.itemProvider }
        groupedPrices.forEach { (provider, fishList) ->
            sender.sendMessage(Component.text("[$provider]", NamedTextColor.AQUA))
            fishList.forEach { fishPrice ->
                sender.sendMessage(
                    Component.text("  - ${fishPrice.fishType}: ", NamedTextColor.WHITE)
                        .append(Component.text("${fishPrice.price}원", NamedTextColor.YELLOW))
                )
            }
        }
    }

    private fun handleInfo(sender: CommandSender) {
        val npc = fishMerchantManager.getFishMerchantNPC()
        if (npc == null) {
            sender.sendMessage(Component.text("설정된 낚시 상인이 없습니다.", NamedTextColor.RED))
            return
        }

        sender.sendMessage(Component.text("=== 낚시 상인 정보 ===", NamedTextColor.GOLD))
        sender.sendMessage(
            Component.text("NPC ID: ", NamedTextColor.WHITE)
                .append(Component.text(npc.id.toString(), NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("NPC 이름: ", NamedTextColor.WHITE)
                .append(Component.text(npc.name, NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("스폰 상태: ", NamedTextColor.WHITE)
                .append(Component.text(if (npc.isSpawned) "스폰됨" else "디스폰됨", if (npc.isSpawned) NamedTextColor.GREEN else NamedTextColor.RED))
        )
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("=== 낚시 상인 명령어 ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/낚시상인 설정 <NPC아이디> - NPC를 낚시 상인으로 설정", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/낚시상인 제거 - 낚시 상인 설정 제거", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/낚시상인 가격설정 <제공자> <물고기ID> <가격> - 물고기 가격 설정", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  제공자: VANILLA, CUSTOMFISHING, NEXO", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/낚시상인 가격목록 - 모든 물고기 가격 조회", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("/낚시상인 정보 - 낚시 상인 정보 조회", NamedTextColor.WHITE))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("설정", "제거", "가격설정", "가격목록", "정보").filter {
                it.startsWith(args[0])
            }
        }

        if (args.size == 2 && args[0] == "가격설정") {
            return listOf("VANILLA", "CUSTOMFISHING", "NEXO").filter {
                it.startsWith(args[1].uppercase())
            }
        }

        if (args.size == 3 && args[0] == "가격설정") {
            val provider = args[1].uppercase()
            return when (provider) {
                "VANILLA" -> listOf("COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH")
                "CUSTOMFISHING" -> listOf("<customfishing_id>")
                "NEXO" -> listOf("<nexo_item_id>")
                else -> emptyList()
            }.filter { it.startsWith(args[2].uppercase()) }
        }

        return emptyList()
    }
}

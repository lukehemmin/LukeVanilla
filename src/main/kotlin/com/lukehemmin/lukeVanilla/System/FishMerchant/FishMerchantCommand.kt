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
        // 플레이어만 사용 가능 (시선 방향 감지를 위해)
        if (sender !is org.bukkit.entity.Player) {
            sender.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED))
            return
        }

        val npc = if (args.size < 2) {
            // NPC ID가 지정되지 않은 경우: 플레이어가 보고 있는 NPC 자동 감지
            val targetNPC = getTargetNPC(sender)
            if (targetNPC == null) {
                sender.sendMessage(Component.text("보고 있는 방향에 NPC가 없습니다.", NamedTextColor.RED))
                sender.sendMessage(Component.text("사용법: /낚시상인 설정 [NPC아이디]", NamedTextColor.GRAY))
                return
            }
            targetNPC
        } else {
            // NPC ID가 지정된 경우: 기존 방식
            val npcId = args[1].toIntOrNull()
            if (npcId == null) {
                sender.sendMessage(Component.text("NPC ID는 숫자여야 합니다.", NamedTextColor.RED))
                return
            }

            val foundNPC = CitizensAPI.getNPCRegistry().getById(npcId)
            if (foundNPC == null) {
                sender.sendMessage(Component.text("해당 ID의 NPC를 찾을 수 없습니다.", NamedTextColor.RED))
                return
            }
            foundNPC
        }

        val (success, previousNpcId) = fishMerchantManager.setFishMerchant(npc.id)

        if (success) {
            // 교체된 경우
            if (previousNpcId != null && previousNpcId != npc.id) {
                val previousNpc = CitizensAPI.getNPCRegistry().getById(previousNpcId)
                val previousName = previousNpc?.name ?: "알 수 없음 (ID: $previousNpcId)"

                sender.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))
                sender.sendMessage(
                    Component.text("⚠ 기존 낚시 상인 '", NamedTextColor.YELLOW)
                        .append(Component.text(previousName, NamedTextColor.RED))
                        .append(Component.text("'의 설정이 해제되었습니다.", NamedTextColor.YELLOW))
                )
                sender.sendMessage(
                    Component.text("✓ 새 낚시 상인 '", NamedTextColor.GREEN)
                        .append(Component.text(npc.name, NamedTextColor.AQUA))
                        .append(Component.text("' (ID: ${npc.id})이(가) 설정되었습니다.", NamedTextColor.GREEN))
                )
                sender.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))
            }
            // 처음 설정하는 경우
            else {
                sender.sendMessage(
                    Component.text("✓ NPC '", NamedTextColor.GREEN)
                        .append(Component.text(npc.name, NamedTextColor.YELLOW))
                        .append(Component.text("' (ID: ${npc.id})을(를) 낚시 상인으로 설정했습니다.", NamedTextColor.GREEN))
                )
            }
        } else {
            sender.sendMessage(Component.text("NPC 설정에 실패했습니다.", NamedTextColor.RED))
        }
    }

    /**
     * 플레이어가 보고 있는 방향의 NPC를 감지합니다.
     * Ray tracing을 사용하여 시선 방향 5블록 이내의 NPC를 찾습니다.
     */
    private fun getTargetNPC(player: org.bukkit.entity.Player): net.citizensnpcs.api.npc.NPC? {
        val maxDistance = 5.0
        val raySize = 0.5 // NPC 히트박스 크기

        // 플레이어 눈 위치에서 시작
        val eyeLocation = player.eyeLocation
        val direction = eyeLocation.direction.normalize()

        // Ray tracing으로 0.2블록씩 증가하며 NPC 검색
        for (i in 0..((maxDistance / 0.2).toInt())) {
            val distance = i * 0.2
            val checkLocation = eyeLocation.clone().add(direction.clone().multiply(distance))

            // 해당 위치 근처의 엔티티들 확인
            checkLocation.world?.getNearbyEntities(checkLocation, raySize, raySize, raySize)?.forEach { entity ->
                val npc = CitizensAPI.getNPCRegistry().getNPC(entity)
                if (npc != null) {
                    return npc
                }
            }
        }

        return null
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
            sender.sendMessage(Component.text("사용법: /낚시상인 가격설정 <VANILLA|CUSTOMFISHING|NEXO> <물고기ID> <기본가격> [cm당가격]", NamedTextColor.RED))
            sender.sendMessage(Component.text("예시:", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("  /낚시상인 가격설정 VANILLA COD 10.0", NamedTextColor.GRAY))
            sender.sendMessage(Component.text("  /낚시상인 가격설정 CUSTOMFISHING tuna 100.0 2.0", NamedTextColor.GRAY))
            sender.sendMessage(Component.text("    → 참치 기본 100원 + 크기×2원 (150cm = 100 + 150×2 = 400원)", NamedTextColor.DARK_GRAY))
            sender.sendMessage(Component.text("  /낚시상인 가격설정 NEXO custom_fish 500.0", NamedTextColor.GRAY))
            return
        }

        val provider = args[1].uppercase()
        if (provider !in listOf("VANILLA", "CUSTOMFISHING", "NEXO")) {
            sender.sendMessage(Component.text("잘못된 제공자입니다. VANILLA, CUSTOMFISHING, NEXO 중 하나를 입력하세요.", NamedTextColor.RED))
            return
        }

        val fishType = args[2]
        val basePrice = args[3].toDoubleOrNull()
        if (basePrice == null || basePrice < 0) {
            sender.sendMessage(Component.text("기본 가격은 0 이상의 숫자여야 합니다.", NamedTextColor.RED))
            return
        }

        // cm당 가격 (선택적)
        val pricePerCm = if (args.size >= 5) {
            val pcm = args[4].toDoubleOrNull()
            if (pcm == null || pcm < 0) {
                sender.sendMessage(Component.text("cm당 가격은 0 이상의 숫자여야 합니다.", NamedTextColor.RED))
                return
            }
            pcm
        } else {
            0.0
        }

        if (fishMerchantManager.setFishPriceWithSize(provider, fishType, basePrice, pricePerCm)) {
            if (pricePerCm > 0) {
                sender.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))
                sender.sendMessage(
                    Component.text("[$provider] ${fishType} 가격 설정 완료!", NamedTextColor.GREEN)
                )
                sender.sendMessage(
                    Component.text("  기본 가격: ", NamedTextColor.WHITE)
                        .append(Component.text("${basePrice}원", NamedTextColor.YELLOW))
                )
                sender.sendMessage(
                    Component.text("  cm당 가격: ", NamedTextColor.WHITE)
                        .append(Component.text("${pricePerCm}원", NamedTextColor.AQUA))
                )
                sender.sendMessage(Component.text("  예시:", NamedTextColor.GRAY))
                val example50 = basePrice + (50 * pricePerCm)
                val example100 = basePrice + (100 * pricePerCm)
                val example150 = basePrice + (150 * pricePerCm)
                sender.sendMessage(Component.text("    50cm = ${example50}원", NamedTextColor.DARK_GRAY))
                sender.sendMessage(Component.text("    100cm = ${example100}원", NamedTextColor.DARK_GRAY))
                sender.sendMessage(Component.text("    150cm = ${example150}원", NamedTextColor.DARK_GRAY))
                sender.sendMessage(Component.text("════════════════════════════", NamedTextColor.GOLD))
            } else {
                sender.sendMessage(
                    Component.text("[$provider] ${fishType}의 가격을 ", NamedTextColor.GREEN)
                        .append(Component.text("${basePrice}원", NamedTextColor.GOLD))
                        .append(Component.text("으로 설정했습니다.", NamedTextColor.GREEN))
                )
            }
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
                if (fishPrice.pricePerCm > 0) {
                    // 크기 기반 가격
                    sender.sendMessage(
                        Component.text("  - ${fishPrice.fishType}: ", NamedTextColor.WHITE)
                            .append(Component.text("${fishPrice.basePrice}원", NamedTextColor.YELLOW))
                            .append(Component.text(" + ", NamedTextColor.GRAY))
                            .append(Component.text("${fishPrice.pricePerCm}원/cm", NamedTextColor.AQUA))
                    )
                } else {
                    // 단순 가격
                    sender.sendMessage(
                        Component.text("  - ${fishPrice.fishType}: ", NamedTextColor.WHITE)
                            .append(Component.text("${fishPrice.basePrice}원", NamedTextColor.YELLOW))
                    )
                }
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
        sender.sendMessage(Component.text("/낚시상인 설정 [NPC아이디] - NPC를 낚시 상인으로 설정", NamedTextColor.WHITE))
        sender.sendMessage(Component.text("  ID 없이 사용: 보고 있는 NPC 자동 감지", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  ID 지정: 해당 ID의 NPC 설정", NamedTextColor.GRAY))
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

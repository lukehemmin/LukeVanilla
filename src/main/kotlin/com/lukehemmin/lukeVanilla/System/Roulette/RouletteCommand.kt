package com.lukehemmin.lukeVanilla.System.Roulette

import net.citizensnpcs.api.CitizensAPI
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * 룰렛 명령어 핸들러 (서브커맨드 구조)
 * /룰렛 <서브커맨드> [인자들...]
 */
class RouletteCommand(
    private val plugin: JavaPlugin,
    private val manager: RouletteManager
) : CommandExecutor, TabCompleter {

    companion object {
        private const val PERMISSION = "lukevanilla.roulette.admin"
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        // 일반 사용자 명령어 (권한 불필요)
        val publicCommands = listOf("확률", "probability", "내기록", "myhistory")

        // 관리자 명령어가 아닌 경우 권한 체크 스킵
        if (args[0].lowercase() !in publicCommands) {
            if (!sender.hasPermission(PERMISSION)) {
                sender.sendMessage("§c권한이 없습니다.")
                return true
            }
        }

        when (args[0].lowercase()) {
            "생성" -> handleCreate(sender, args)
            "삭제" -> handleDelete(sender, args)
            "목록" -> handleList(sender)
            "정보" -> handleInfo(sender, args)
            "아이템" -> handleItem(sender, args)
            "npc지정" -> handleNPCSet(sender, args)
            "npc제거" -> handleNPCRemove(sender, args)
            "npc목록" -> handleNPCList(sender)
            "nexo지정" -> handleNexoSet(sender, args)
            "nexo제거" -> handleNexoRemove(sender, args)
            "nexo목록" -> handleNexoList(sender)
            "설정" -> handleSettings(sender, args)
            "리로드" -> handleReload(sender)
            "확률", "probability" -> handleProbability(sender, args)
            "내기록", "myhistory" -> handleMyHistory(sender, args)
            else -> sendUsage(sender)
        }

        return true
    }

    // ==================== 룰렛 CRUD ====================

    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 생성 <이름> [비용]")
            return
        }

        val name = args[1]
        val cost = args.getOrNull(2)?.toDoubleOrNull() ?: 1000.0

        // 중복 확인
        if (manager.getRouletteByName(name) != null) {
            sender.sendMessage("§c'$name'은(는) 이미 존재하는 룰렛입니다.")
            return
        }

        val newId = manager.createRoulette(name, CostType.MONEY, cost)
        if (newId != null) {
            sender.sendMessage("§a룰렛 '$name'을(를) 생성했습니다. (ID: $newId, 비용: ${cost}원)")
        } else {
            sender.sendMessage("§c룰렛 생성에 실패했습니다.")
        }
    }

    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 삭제 <이름>")
            return
        }

        val name = args[1]
        val roulette = manager.getRouletteByName(name)
        if (roulette == null) {
            sender.sendMessage("§c'$name' 룰렛을 찾을 수 없습니다.")
            return
        }

        if (manager.deleteRoulette(roulette.id)) {
            sender.sendMessage("§a룰렛 '$name'을(를) 삭제했습니다.")
        } else {
            sender.sendMessage("§c룰렛 삭제에 실패했습니다.")
        }
    }

    private fun handleList(sender: CommandSender) {
        val roulettes = manager.getAllRoulettes()

        if (roulettes.isEmpty()) {
            sender.sendMessage("§c등록된 룰렛이 없습니다.")
            return
        }

        sender.sendMessage("§e§l=== 룰렛 목록 (${roulettes.size}개) ===")
        roulettes.forEach { roulette ->
            val status = if (roulette.enabled) "§a활성화" else "§c비활성화"
            val itemCount = manager.getItems(roulette.id).size
            sender.sendMessage("§f[${roulette.id}] §b${roulette.rouletteName} §7- $status §7| 비용: §e${roulette.costAmount}원 §7| 아이템: §e${itemCount}개")
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 정보 <이름>")
            return
        }

        val name = args[1]
        val roulette = manager.getRouletteByName(name)
        if (roulette == null) {
            sender.sendMessage("§c'$name' 룰렛을 찾을 수 없습니다.")
            return
        }

        val items = manager.getItems(roulette.id)
        val status = if (roulette.enabled) "§a활성화" else "§c비활성화"
        val totalWeight = items.sumOf { it.weight }

        sender.sendMessage("§e§l=== 룰렛 정보: ${roulette.rouletteName} ===")
        sender.sendMessage("§f  ID: §e${roulette.id}")
        sender.sendMessage("§f  상태: $status")
        sender.sendMessage("§f  비용: §e${roulette.costAmount}원")
        sender.sendMessage("§f  애니메이션: §e${roulette.animationDuration}틱")
        sender.sendMessage("§f  아이템 개수: §e${items.size}개")

        if (items.isNotEmpty()) {
            sender.sendMessage("§f  상위 5개 아이템:")
            items.take(5).forEach { item ->
                val probability = if (totalWeight > 0) (item.weight / totalWeight * 100) else 0.0
                sender.sendMessage("§f    - §b${item.itemDisplayName ?: item.itemIdentifier} §7x${item.itemAmount} §7(가중치: §e${item.weight}§7, 확률: §e${"%.2f".format(probability)}%§7)")
            }
        }
    }

    // ==================== 아이템 관리 ====================

    private fun handleItem(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 아이템 <목록|추가|수정|삭제> [인자들...]")
            return
        }

        when (args[1].lowercase()) {
            "목록" -> handleItemList(sender, args)
            "추가" -> handleItemAdd(sender, args)
            "수정" -> handleItemUpdate(sender, args)
            "삭제" -> handleItemDelete(sender, args)
            else -> sender.sendMessage("§c사용법: /룰렛 아이템 <목록|추가|수정|삭제> [인자들...]")
        }
    }

    private fun handleItemList(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§c사용법: /룰렛 아이템 목록 <룰렛이름>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val items = manager.getItems(roulette.id)
        if (items.isEmpty()) {
            sender.sendMessage("§c'$rouletteName' 룰렛에 등록된 아이템이 없습니다.")
            return
        }

        val totalWeight = items.sumOf { it.weight }
        sender.sendMessage("§e§l=== $rouletteName 아이템 목록 (${items.size}개) ===")
        items.forEach { item ->
            val probability = if (totalWeight > 0) (item.weight / totalWeight * 100) else 0.0
            sender.sendMessage("§f[${item.id}] §b${item.itemDisplayName ?: item.itemIdentifier} §7x${item.itemAmount} §7| 가중치: §e${item.weight} §7| 확률: §e${"%.2f".format(probability)}%")
        }
    }

    private fun handleItemAdd(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            sender.sendMessage("§c사용법: /룰렛 아이템 추가 <룰렛이름> <제공자> <식별자> [개수] [가중치]")
            sender.sendMessage("§c제공자: VANILLA, NEXO, ORAXEN, ITEMSADDER")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val providerStr = args[3].uppercase()
        val provider = try {
            ItemProvider.valueOf(providerStr)
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§c잘못된 제공자입니다. (VANILLA, NEXO, ORAXEN, ITEMSADDER)")
            return
        }

        val identifier = args[4]
        val amount = args.getOrNull(5)?.toIntOrNull() ?: 1
        val weight = args.getOrNull(6)?.toDoubleOrNull() ?: 10.0

        // 입력 검증
        if (amount !in 1..64) {
            sender.sendMessage("§c아이템 개수는 1에서 64 사이의 값이어야 합니다. (입력값: $amount)")
            return
        }

        if (weight < 0.0 || weight > 1000000.0) {
            sender.sendMessage("§c가중치는 0 이상 1,000,000 이하의 값이어야 합니다. (입력값: $weight)")
            return
        }

        if (manager.addItem(roulette.id, provider, identifier, identifier, amount, weight)) {
            sender.sendMessage("§a아이템을 추가했습니다: $identifier x$amount (가중치: $weight)")
        } else {
            sender.sendMessage("§c아이템 추가에 실패했습니다.")
        }
    }

    private fun handleItemUpdate(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            sender.sendMessage("§c사용법: /룰렛 아이템 수정 <룰렛이름> <아이템ID> <가중치>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val itemId = args[3].toIntOrNull()
        if (itemId == null) {
            sender.sendMessage("§c아이템 ID는 숫자여야 합니다.")
            return
        }

        val weight = args[4].toDoubleOrNull()
        if (weight == null) {
            sender.sendMessage("§c가중치는 숫자여야 합니다.")
            return
        }

        if (weight < 0.0 || weight > 1000000.0) {
            sender.sendMessage("§c가중치는 0 이상 1,000,000 이하의 값이어야 합니다. (입력값: $weight)")
            return
        }

        if (manager.updateItemWeight(itemId, roulette.id, weight)) {
            sender.sendMessage("§a아이템 ID ${itemId}의 가중치를 $weight(으)로 변경했습니다.")
        } else {
            sender.sendMessage("§c아이템 수정에 실패했습니다.")
        }
    }

    private fun handleItemDelete(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage("§c사용법: /룰렛 아이템 삭제 <룰렛이름> <아이템ID>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val itemId = args[3].toIntOrNull()
        if (itemId == null) {
            sender.sendMessage("§c아이템 ID는 숫자여야 합니다.")
            return
        }

        if (manager.deleteItem(itemId, roulette.id)) {
            sender.sendMessage("§a아이템 ID ${itemId}을(를) 삭제했습니다.")
        } else {
            sender.sendMessage("§c아이템 삭제에 실패했습니다.")
        }
    }

    // ==================== NPC 관리 ====================

    private fun handleNPCSet(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 npc지정 <룰렛이름>")
            sender.sendMessage("§c  NPC를 바라본 상태에서 입력하세요.")
            return
        }

        val rouletteName = args[1]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        // Citizens NPC 감지
        try {
            val targetEntity = sender.getTargetEntity(5)
            if (targetEntity == null) {
                sender.sendMessage("§c5블록 이내의 NPC를 바라보고 입력하세요.")
                return
            }

            val npcRegistry = CitizensAPI.getNPCRegistry()
            val npc = npcRegistry.getNPC(targetEntity)
            if (npc == null) {
                sender.sendMessage("§c대상이 Citizens NPC가 아닙니다.")
                return
            }

            if (manager.setNPCMapping(npc.id, roulette.id)) {
                sender.sendMessage("§aNPC '${npc.name}' (ID: ${npc.id})을(를) '$rouletteName' 룰렛에 연결했습니다.")
            } else {
                sender.sendMessage("§cNPC 연결에 실패했습니다.")
            }
        } catch (e: Exception) {
            sender.sendMessage("§cNPC 감지 실패: ${e.message}")
        }
    }

    private fun handleNPCRemove(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.")
            return
        }

        var npcId: Int? = null

        if (args.size >= 2) {
            npcId = args[1].toIntOrNull()
            if (npcId == null) {
                sender.sendMessage("§cNPC ID는 숫자여야 합니다.")
                return
            }
        } else {
            // NPC ID가 입력되지 않은 경우, 바라보고 있는 NPC 감지
            try {
                val targetEntity = sender.getTargetEntity(5)
                if (targetEntity == null) {
                    sender.sendMessage("§c5블록 이내의 NPC를 바라보거나 NPC ID를 입력하세요.")
                    return
                }

                val npcRegistry = CitizensAPI.getNPCRegistry()
                val npc = npcRegistry.getNPC(targetEntity)
                if (npc == null) {
                    sender.sendMessage("§c대상이 Citizens NPC가 아닙니다.")
                    return
                }
                npcId = npc.id
            } catch (e: Exception) {
                sender.sendMessage("§cNPC 감지 실패: ${e.message}")
                return
            }
        }

        if (manager.removeNPCMapping(npcId!!)) {
            sender.sendMessage("§aNPC ID ${npcId}의 룰렛 연결을 제거했습니다.")
        } else {
            sender.sendMessage("§cNPC 연결 제거에 실패했습니다. (등록되지 않은 NPC일 수 있습니다)")
        }
    }

    private fun handleNPCList(sender: CommandSender) {
        val mappings = manager.getAllNPCMappings()

        if (mappings.isEmpty()) {
            sender.sendMessage("§c연결된 NPC가 없습니다.")
            return
        }

        sender.sendMessage("§e§l=== NPC 룰렛 매핑 목록 (${mappings.size}개) ===")
        mappings.forEach { (npcId, rouletteId) ->
            val roulette = manager.getRouletteById(rouletteId)
            val rouletteName = roulette?.rouletteName ?: "알 수 없음"
            sender.sendMessage("§f  NPC ID §e$npcId §f→ §b$rouletteName §7(ID: $rouletteId)")
        }
    }

    // ==================== Nexo 매핑 ====================

    private fun handleNexoSet(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§c사용법: /룰렛 nexo지정 <룰렛이름> <Nexo아이템ID>")
            sender.sendMessage("§7예시: /룰렛 nexo지정 할로윈룰렛 plny_halloween_chest")
            return
        }

        val rouletteName = args[1]
        val nexoItemId = args[2]

        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        if (manager.setNexoMapping(nexoItemId, roulette.id)) {
            sender.sendMessage("§aNexo 아이템 '§e$nexoItemId§a'을(를) '§b$rouletteName§a' 룰렛에 연결했습니다.")
            sender.sendMessage("§7이제 이 Nexo 가구를 우클릭하면 룰렛이 열립니다.")
        } else {
            sender.sendMessage("§cNexo 매핑에 실패했습니다.")
        }
    }

    private fun handleNexoRemove(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 nexo제거 <Nexo아이템ID>")
            return
        }

        val nexoItemId = args[1]

        if (manager.removeNexoMapping(nexoItemId)) {
            sender.sendMessage("§aNexo 아이템 '§e$nexoItemId§a'의 연결을 제거했습니다.")
        } else {
            sender.sendMessage("§cNexo 매핑 제거에 실패했습니다.")
        }
    }

    private fun handleNexoList(sender: CommandSender) {
        val mappings = manager.getAllNexoMappings()

        if (mappings.isEmpty()) {
            sender.sendMessage("§c연결된 Nexo 아이템이 없습니다.")
            return
        }

        sender.sendMessage("§e§l=== Nexo 룰렛 매핑 목록 (${mappings.size}개) ===")
        mappings.forEach { (nexoItemId, rouletteId) ->
            val roulette = manager.getRouletteById(rouletteId)
            val rouletteName = roulette?.rouletteName ?: "알 수 없음"
            sender.sendMessage("§f  Nexo §e$nexoItemId §f→ §b$rouletteName §7(ID: $rouletteId)")
        }
    }

    // ==================== 설정 ====================

    private fun handleSettings(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 설정 <비용|활성화|비활성화> [인자들...]")
            return
        }

        when (args[1].lowercase()) {
            "비용" -> handleSettingsCost(sender, args)
            "활성화" -> handleSettingsEnable(sender, args, true)
            "비활성화" -> handleSettingsEnable(sender, args, false)
            "열쇠" -> handleSettingsKey(sender, args)
            "열쇠제거" -> handleSettingsKeyRemove(sender, args)
            else -> sender.sendMessage("§c사용법: /룰렛 설정 <비용|활성화|비활성화|열쇠|열쇠제거> [인자들...]")
        }
    }

    private fun handleSettingsCost(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage("§c사용법: /룰렛 설정 비용 <룰렛이름> <금액>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val cost = args[3].toDoubleOrNull()
        if (cost == null) {
            sender.sendMessage("§c금액은 숫자여야 합니다.")
            return
        }

        if (cost < 0.0 || cost > 1000000000.0) {
            sender.sendMessage("§c금액은 0 이상 10억 이하의 값이어야 합니다. (입력값: $cost)")
            return
        }

        if (manager.updateRouletteConfig(roulette.id, roulette.costType, cost, roulette.animationDuration, roulette.enabled)) {
            sender.sendMessage("§a'$rouletteName' 룰렛의 비용을 ${cost}원으로 변경했습니다.")
        } else {
            sender.sendMessage("§c비용 변경에 실패했습니다.")
        }
    }

    private fun handleSettingsEnable(sender: CommandSender, args: Array<out String>, enabled: Boolean) {
        if (args.size < 3) {
            val action = if (enabled) "활성화" else "비활성화"
            sender.sendMessage("§c사용법: /룰렛 설정 $action <룰렛이름>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val action = if (enabled) "활성화" else "비활성화"
        if (manager.setEnabled(roulette.id, enabled)) {
            sender.sendMessage("§a'$rouletteName' 룰렛을 ${action}했습니다.")
        } else {
            sender.sendMessage("§c룰렛 ${action}에 실패했습니다.")
        }
    }

    private fun handleSettingsKey(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            sender.sendMessage("§c사용법: /룰렛 설정 열쇠 <룰렛이름> <VANILLA|NEXO> <아이템ID>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val providerStr = args[3].uppercase()
        val provider = try {
            ItemProvider.valueOf(providerStr)
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§c잘못된 아이템 제공자입니다. (VANILLA, NEXO 중 선택)")
            return
        }

        val itemType = args[4]

        if (manager.setKeyItem(roulette.id, provider, itemType)) {
            sender.sendMessage("§a'$rouletteName' 룰렛의 열쇠 아이템을 설정했습니다.")
            sender.sendMessage("§7- 제공자: §f$providerStr")
            sender.sendMessage("§7- 아이템: §f$itemType")
        } else {
            sender.sendMessage("§c열쇠 아이템 설정에 실패했습니다.")
        }
    }

    private fun handleSettingsKeyRemove(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§c사용법: /룰렛 설정 열쇠제거 <룰렛이름>")
            return
        }

        val rouletteName = args[2]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        if (manager.setKeyItem(roulette.id, null, null)) {
            sender.sendMessage("§a'$rouletteName' 룰렛의 열쇠 아이템을 제거했습니다.")
        } else {
            sender.sendMessage("§c열쇠 아이템 제거에 실패했습니다.")
        }
    }

    // ==================== 리로드 ====================

    private fun handleReload(sender: CommandSender) {
        manager.reload()
        sender.sendMessage("§a모든 룰렛 설정이 리로드되었습니다.")
    }

    // ==================== 확률 표시 ====================

    private fun handleProbability(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 확률 <룰렛이름>")
            return
        }

        val rouletteName = args[1]
        val roulette = manager.getRouletteByName(rouletteName)
        if (roulette == null) {
            sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
            return
        }

        val items = manager.getItems(roulette.id)
        if (items.isEmpty()) {
            sender.sendMessage("§c'$rouletteName' 룰렛에 등록된 아이템이 없습니다.")
            return
        }

        val totalWeight = items.sumOf { it.weight }

        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§e§l  ${roulette.rouletteName} 확률표")
        sender.sendMessage("§7  전체 아이템: ${items.size}개")
        sender.sendMessage("")

        // 확률 높은 순으로 정렬
        val sortedItems = items.sortedByDescending { it.weight }

        sortedItems.forEachIndexed { index, item ->
            val probability = if (totalWeight > 0) (item.weight / totalWeight * 100) else 0.0
            val percentStr = "%.4f".format(probability)
            val displayName = item.itemDisplayName ?: item.itemIdentifier

            sender.sendMessage("§7  ${index + 1}. §e$displayName §fx${item.itemAmount} §7- §a${percentStr}%")
        }

        sender.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━")
    }

    // ==================== 개인 기록 ====================

    private fun handleMyHistory(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§c플레이어만 사용 가능한 명령어입니다.")
            return
        }

        val rouletteName = args.getOrNull(1)

        if (rouletteName != null) {
            // 특정 룰렛의 기록 조회
            val roulette = manager.getRouletteByName(rouletteName)
            if (roulette == null) {
                sender.sendMessage("§c'$rouletteName' 룰렛을 찾을 수 없습니다.")
                return
            }

            showRouletteHistory(sender, roulette.id, roulette.rouletteName)
        } else {
            // 전체 룰렛 플레이 통계
            showAllRoulettesHistory(sender)
        }
    }

    /**
     * 특정 룰렛의 플레이 히스토리 표시
     */
    private fun showRouletteHistory(player: Player, rouletteId: Int, rouletteName: String) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val query = """
                    SELECT item_identifier, item_provider, probability, COUNT(*) as count
                    FROM roulette_history
                    WHERE player_uuid = ? AND roulette_id = ?
                    GROUP BY item_identifier, item_provider, probability
                    ORDER BY count DESC
                """.trimIndent()

                val results = mutableListOf<HistoryEntry>()
                var totalCount = 0

                manager.getConnection().use { connection ->
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, player.uniqueId.toString())
                        stmt.setInt(2, rouletteId)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                val count = rs.getInt("count")
                                totalCount += count
                                results.add(
                                    HistoryEntry(
                                        rs.getString("item_identifier"),
                                        rs.getDouble("probability"),
                                        count
                                    )
                                )
                            }
                        }
                    }
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (results.isEmpty()) {
                        player.sendMessage("§e'$rouletteName' 룰렛 플레이 기록이 없습니다.")
                        return@Runnable
                    }

                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━")
                    player.sendMessage("§e§l  $rouletteName 내 기록")
                    player.sendMessage("§7  총 플레이 횟수: ${totalCount}회")
                    player.sendMessage("")

                    results.forEach { entry ->
                        val percentStr = "%.4f".format(entry.probability)
                        player.sendMessage("§7  • §e${entry.itemName} §fx${entry.count}회 §7(당첨확률: §a${percentStr}%§7)")
                    }

                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━")
                })
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c기록 조회 중 오류가 발생했습니다: ${e.message}")
                })
                e.printStackTrace()
            }
        })
    }

    /**
     * 모든 룰렛의 플레이 통계 표시
     */
    private fun showAllRoulettesHistory(player: Player) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val query = """
                    SELECT rh.roulette_id, rc.roulette_name, COUNT(*) as count
                    FROM roulette_history rh
                    JOIN roulette_config rc ON rh.roulette_id = rc.id
                    WHERE rh.player_uuid = ?
                    GROUP BY rh.roulette_id, rc.roulette_name
                    ORDER BY count DESC
                """.trimIndent()

                val results = mutableListOf<Pair<String, Int>>()
                var totalCount = 0

                manager.getConnection().use { connection ->
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, player.uniqueId.toString())
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                val rouletteName = rs.getString("roulette_name")
                                val count = rs.getInt("count")
                                totalCount += count
                                results.add(rouletteName to count)
                            }
                        }
                    }
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (results.isEmpty()) {
                        player.sendMessage("§e룰렛 플레이 기록이 없습니다.")
                        return@Runnable
                    }

                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━")
                    player.sendMessage("§e§l  룰렛 전체 기록")
                    player.sendMessage("§7  총 플레이 횟수: ${totalCount}회")
                    player.sendMessage("")

                    results.forEach { (rouletteName, count) ->
                        player.sendMessage("§7  • §e$rouletteName§f: ${count}회")
                    }

                    player.sendMessage("")
                    player.sendMessage("§7  💡 특정 룰렛의 상세 기록을 보려면:")
                    player.sendMessage("§7     /룰렛 내기록 <룰렛이름>")
                    player.sendMessage("§b§l━━━━━━━━━━━━━━━━━━━━━━━")
                })
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c기록 조회 중 오류가 발생했습니다: ${e.message}")
                })
                e.printStackTrace()
            }
        })
    }

    /**
     * 히스토리 엔트리 데이터 클래스
     */
    private data class HistoryEntry(
        val itemName: String,
        val probability: Double,
        val count: Int
    )

    // ==================== 유틸리티 ====================

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§e§l=== 룰렛 명령어 사용법 ===")

        // 일반 사용자용 명령어
        sender.sendMessage("§a§l[일반 명령어]")
        sender.sendMessage("§f/룰렛 확률 <룰렛이름> §7- 확률표 보기")
        sender.sendMessage("§f/룰렛 내기록 [룰렛이름] §7- 내 플레이 기록")

        // 관리자 전용
        if (sender.hasPermission(PERMISSION)) {
            sender.sendMessage("")
            sender.sendMessage("§c§l[관리자 명령어]")
            sender.sendMessage("§f/룰렛 생성 <이름> [비용] §7- 새 룰렛 생성")
            sender.sendMessage("§f/룰렛 삭제 <이름> §7- 룰렛 삭제")
            sender.sendMessage("§f/룰렛 목록 §7- 모든 룰렛 보기")
            sender.sendMessage("§f/룰렛 정보 <이름> §7- 룰렛 정보 보기")
            sender.sendMessage("§f/룰렛 아이템 목록 <룰렛이름> §7- 아이템 목록")
            sender.sendMessage("§f/룰렛 아이템 추가 <룰렛> <제공자> <식별자> [개수] [가중치]")
            sender.sendMessage("§f/룰렛 아이템 수정 <룰렛> <아이템ID> <가중치>")
            sender.sendMessage("§f/룰렛 아이템 삭제 <룰렛> <아이템ID>")
            sender.sendMessage("§f/룰렛 npc지정 <룰렛이름> §7- NPC에 룰렛 연결 (바라보기)")
            sender.sendMessage("§f/룰렛 npc제거 <NPC_ID> §7- NPC 연결 제거")
            sender.sendMessage("§f/룰렛 npc목록 §7- NPC 매핑 목록")
            sender.sendMessage("§f/룰렛 nexo지정 <룰렛이름> <Nexo아이템ID> §7- Nexo 가구에 룰렛 연결")
            sender.sendMessage("§f/룰렛 nexo제거 <Nexo아이템ID> §7- Nexo 연결 제거")
            sender.sendMessage("§f/룰렛 nexo목록 §7- Nexo 매핑 목록")
            sender.sendMessage("§f/룰렛 설정 비용 <룰렛> <금액> §7- 비용 설정")
            sender.sendMessage("§f/룰렛 설정 활성화 <룰렛> §7- 룰렛 활성화")
            sender.sendMessage("§f/룰렛 설정 비활성화 <룰렛> §7- 룰렛 비활성화")
            sender.sendMessage("§f/룰렛 리로드 §7- 설정 리로드")
        }
    }

    // ==================== Tab Completion ====================

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        return when (args.size) {
            1 -> {
                val commands = mutableListOf("확률", "probability", "내기록", "myhistory")
                if (sender.hasPermission(PERMISSION)) {
                    commands.addAll(listOf("생성", "삭제", "목록", "정보", "아이템", "npc지정", "npc제거", "npc목록", "nexo지정", "nexo제거", "nexo목록", "설정", "리로드"))
                }
                commands.filter { it.startsWith(args[0], ignoreCase = true) }
            }

            2 -> when (args[0].lowercase()) {
                "삭제", "정보", "확률", "probability", "내기록", "myhistory" -> getRouletteNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "아이템" -> listOf("목록", "추가", "수정", "삭제").filter { it.startsWith(args[1], ignoreCase = true) }
                "npc지정", "nexo지정" -> getRouletteNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "설정" -> listOf("비용", "활성화", "비활성화", "열쇠", "열쇠제거").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "아이템" -> when (args[1].lowercase()) {
                    "목록", "추가", "수정", "삭제" -> getRouletteNames().filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
                "설정" -> when (args[1].lowercase()) {
                    "비용", "활성화", "비활성화", "열쇠", "열쇠제거" -> getRouletteNames().filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
                else -> emptyList()
            }

            4 -> when (args[0].lowercase()) {
                "아이템" -> when (args[1].lowercase()) {
                    "추가" -> listOf("VANILLA", "NEXO", "ORAXEN", "ITEMSADDER").filter { it.startsWith(args[3], ignoreCase = true) }
                    else -> emptyList()
                }
                "설정" -> when (args[1].lowercase()) {
                    "열쇠" -> listOf("VANILLA", "NEXO").filter { it.startsWith(args[3], ignoreCase = true) }
                    else -> emptyList()
                }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }

    private fun getRouletteNames(): List<String> {
        return manager.getAllRoulettes().map { it.rouletteName }
    }
}

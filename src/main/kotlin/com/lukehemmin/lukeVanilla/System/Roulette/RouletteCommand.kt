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
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return true
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
            "설정" -> handleSettings(sender, args)
            "리로드" -> handleReload(sender)
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
        if (weight == null || weight < 0) {
            sender.sendMessage("§c가중치는 0 이상의 숫자여야 합니다.")
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
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /룰렛 npc제거 <NPC_ID>")
            return
        }

        val npcId = args[1].toIntOrNull()
        if (npcId == null) {
            sender.sendMessage("§cNPC ID는 숫자여야 합니다.")
            return
        }

        if (manager.removeNPCMapping(npcId)) {
            sender.sendMessage("§aNPC ID ${npcId}의 룰렛 연결을 제거했습니다.")
        } else {
            sender.sendMessage("§cNPC 연결 제거에 실패했습니다.")
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
            else -> sender.sendMessage("§c사용법: /룰렛 설정 <비용|활성화|비활성화> [인자들...]")
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
        if (cost == null || cost < 0) {
            sender.sendMessage("§c금액은 0 이상의 숫자여야 합니다.")
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

    // ==================== 리로드 ====================

    private fun handleReload(sender: CommandSender) {
        manager.reload()
        sender.sendMessage("§a모든 룰렛 설정이 리로드되었습니다.")
    }

    // ==================== 유틸리티 ====================

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§e§l=== 룰렛 명령어 사용법 ===")
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
        sender.sendMessage("§f/룰렛 설정 비용 <룰렛> <금액> §7- 비용 설정")
        sender.sendMessage("§f/룰렛 설정 활성화 <룰렛> §7- 룰렛 활성화")
        sender.sendMessage("§f/룰렛 설정 비활성화 <룰렛> §7- 룰렛 비활성화")
        sender.sendMessage("§f/룰렛 리로드 §7- 설정 리로드")
    }

    // ==================== Tab Completion ====================

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission(PERMISSION)) return emptyList()

        return when (args.size) {
            1 -> listOf("생성", "삭제", "목록", "정보", "아이템", "npc지정", "npc제거", "npc목록", "설정", "리로드")
                .filter { it.startsWith(args[0], ignoreCase = true) }

            2 -> when (args[0].lowercase()) {
                "삭제", "정보" -> getRouletteNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "아이템" -> listOf("목록", "추가", "수정", "삭제").filter { it.startsWith(args[1], ignoreCase = true) }
                "npc지정" -> getRouletteNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "설정" -> listOf("비용", "활성화", "비활성화").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "아이템" -> when (args[1].lowercase()) {
                    "목록", "추가", "수정", "삭제" -> getRouletteNames().filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
                "설정" -> when (args[1].lowercase()) {
                    "비용", "활성화", "비활성화" -> getRouletteNames().filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
                else -> emptyList()
            }

            4 -> when (args[0].lowercase()) {
                "아이템" -> when (args[1].lowercase()) {
                    "추가" -> listOf("VANILLA", "NEXO", "ORAXEN", "ITEMSADDER").filter { it.startsWith(args[3], ignoreCase = true) }
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

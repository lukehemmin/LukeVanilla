package com.lukehemmin.lukeVanilla.System.ScrollRoulette

import com.lukehemmin.lukeVanilla.System.Roulette.ItemProvider
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * 스크롤 룰렛 관리 명령어
 * /스크롤룰렛 생성 <이름> <VANILLA|NEXO> <아이템코드>
 * /스크롤룰렛 목록
 * /스크롤룰렛 아이템추가 <룰렛ID> <VANILLA|NEXO> <아이템코드> <수량> <확률>
 * /스크롤룰렛 아이템목록 <룰렛ID>
 * /스크롤룰렛 아이템삭제 <룰렛ID> <아이템ID>
 * /스크롤룰렛 삭제 <룰렛ID>
 * /스크롤룰렛 리로드
 */
class ScrollRouletteCommand(
    private val plugin: JavaPlugin,
    private val manager: ScrollRouletteManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 권한 확인
        if (!sender.hasPermission("lukevanilla.scrollroulette.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "생성", "create" -> handleCreate(sender, args)
            "목록", "list" -> handleList(sender)
            "아이템추가", "additem" -> handleAddItem(sender, args)
            "아이템목록", "items" -> handleItemList(sender, args)
            "아이템삭제", "removeitem" -> handleRemoveItem(sender, args)
            "삭제", "delete" -> handleDelete(sender, args)
            "리로드", "reload" -> handleReload(sender)
            else -> sendHelp(sender)
        }

        return true
    }

    /**
     * 도움말 출력
     */
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§e§l===== 스크롤 룰렛 관리 명령어 =====")
        sender.sendMessage("§6/스크롤룰렛 생성 <이름> <VANILLA|NEXO> <아이템코드>")
        sender.sendMessage("  §7- 새로운 스크롤 룰렛을 생성합니다.")
        sender.sendMessage("§6/스크롤룰렛 목록")
        sender.sendMessage("  §7- 등록된 스크롤 룰렛 목록을 표시합니다.")
        sender.sendMessage("§6/스크롤룰렛 아이템추가 <룰렛ID> <VANILLA|NEXO> <아이템코드> <수량> <확률>")
        sender.sendMessage("  §7- 스크롤 룰렛에 당첨 아이템을 추가합니다.")
        sender.sendMessage("§6/스크롤룰렛 아이템목록 <룰렛ID>")
        sender.sendMessage("  §7- 스크롤 룰렛의 당첨 아이템 목록을 표시합니다.")
        sender.sendMessage("§6/스크롤룰렛 아이템삭제 <룰렛ID> <아이템ID>")
        sender.sendMessage("  §7- 스크롤 룰렛에서 당첨 아이템을 삭제합니다.")
        sender.sendMessage("§6/스크롤룰렛 삭제 <룰렛ID>")
        sender.sendMessage("  §7- 스크롤 룰렛을 삭제합니다.")
        sender.sendMessage("§6/스크롤룰렛 리로드")
        sender.sendMessage("  §7- 설정을 다시 불러옵니다.")
    }

    /**
     * 스크롤 룰렛 생성
     */
    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) {
            sender.sendMessage("§c사용법: /스크롤룰렛 생성 <이름> <VANILLA|NEXO> <아이템코드>")
            return
        }

        val name = args[1]
        val providerStr = args[2].uppercase()
        val itemCode = args[3]

        val provider = try {
            ItemProvider.valueOf(providerStr)
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§c올바르지 않은 아이템 제공자입니다. VANILLA 또는 NEXO를 사용하세요.")
            return
        }

        val newId = manager.createRoulette(name, provider, itemCode)
        if (newId != null) {
            sender.sendMessage("§a스크롤 룰렛이 생성되었습니다!")
            sender.sendMessage("§7ID: §f$newId")
            sender.sendMessage("§7이름: §f$name")
            sender.sendMessage("§7스크롤: §f[$providerStr] $itemCode")
        } else {
            sender.sendMessage("§c스크롤 룰렛 생성에 실패했습니다. 이미 등록된 아이템일 수 있습니다.")
        }
    }

    /**
     * 스크롤 룰렛 목록
     */
    private fun handleList(sender: CommandSender) {
        val roulettes = manager.getAllRoulettes()

        if (roulettes.isEmpty()) {
            sender.sendMessage("§e등록된 스크롤 룰렛이 없습니다.")
            return
        }

        sender.sendMessage("§e§l===== 스크롤 룰렛 목록 (${roulettes.size}개) =====")
        for (roulette in roulettes) {
            val status = if (roulette.enabled) "§a활성" else "§c비활성"
            val itemCount = manager.getItems(roulette.id).size
            sender.sendMessage("§6#${roulette.id} §f${roulette.rouletteName}")
            sender.sendMessage("  §7스크롤: [${roulette.itemProvider}] ${roulette.itemCode}")
            sender.sendMessage("  §7당첨 아이템: ${itemCount}개 | 상태: $status")
        }
    }

    /**
     * 당첨 아이템 추가
     */
    private fun handleAddItem(sender: CommandSender, args: Array<out String>) {
        if (args.size < 6) {
            sender.sendMessage("§c사용법: /스크롤룰렛 아이템추가 <룰렛ID> <VANILLA|NEXO> <아이템코드> <수량> <확률>")
            sender.sendMessage("§7예시: /스크롤룰렛 아이템추가 1 VANILLA DIAMOND 1 10.0")
            return
        }

        val rouletteId = args[1].toIntOrNull()
        if (rouletteId == null) {
            sender.sendMessage("§c룰렛 ID는 숫자여야 합니다.")
            return
        }

        val roulette = manager.getRouletteById(rouletteId)
        if (roulette == null) {
            sender.sendMessage("§c해당 ID의 스크롤 룰렛을 찾을 수 없습니다.")
            return
        }

        val providerStr = args[2].uppercase()
        val provider = try {
            ItemProvider.valueOf(providerStr)
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§c올바르지 않은 아이템 제공자입니다. VANILLA 또는 NEXO를 사용하세요.")
            return
        }

        val itemCode = args[3]

        val amount = args[4].toIntOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage("§c수량은 1 이상의 숫자여야 합니다.")
            return
        }

        val probability = args[5].toDoubleOrNull()
        if (probability == null || probability <= 0) {
            sender.sendMessage("§c확률은 0보다 큰 숫자여야 합니다.")
            return
        }

        if (manager.addItem(rouletteId, provider, itemCode, null, amount, probability)) {
            sender.sendMessage("§a당첨 아이템이 추가되었습니다!")
            sender.sendMessage("§7아이템: [$providerStr] $itemCode x$amount")
            sender.sendMessage("§7확률: ${probability}%")
        } else {
            sender.sendMessage("§c당첨 아이템 추가에 실패했습니다.")
        }
    }

    /**
     * 당첨 아이템 목록
     */
    private fun handleItemList(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /스크롤룰렛 아이템목록 <룰렛ID>")
            return
        }

        val rouletteId = args[1].toIntOrNull()
        if (rouletteId == null) {
            sender.sendMessage("§c룰렛 ID는 숫자여야 합니다.")
            return
        }

        val roulette = manager.getRouletteById(rouletteId)
        if (roulette == null) {
            sender.sendMessage("§c해당 ID의 스크롤 룰렛을 찾을 수 없습니다.")
            return
        }

        val items = manager.getItems(rouletteId)
        if (items.isEmpty()) {
            sender.sendMessage("§e등록된 당첨 아이템이 없습니다.")
            return
        }

        val totalProb = items.sumOf { it.probability }

        sender.sendMessage("§e§l===== ${roulette.rouletteName} 당첨 아이템 (${items.size}개) =====")
        for (item in items) {
            val actualProb = if (totalProb > 0) (item.probability / totalProb) * 100 else 0.0
            val displayName = item.itemDisplayName ?: item.itemCode
            sender.sendMessage("§6#${item.id} §f$displayName §7x${item.itemAmount}")
            sender.sendMessage("  §7아이템: [${item.itemProvider}] ${item.itemCode}")
            sender.sendMessage("  §7확률: ${item.probability}% §8(실제: ${String.format("%.2f", actualProb)}%)")
        }
    }

    /**
     * 당첨 아이템 삭제
     */
    private fun handleRemoveItem(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§c사용법: /스크롤룰렛 아이템삭제 <룰렛ID> <아이템ID>")
            return
        }

        val rouletteId = args[1].toIntOrNull()
        val itemId = args[2].toIntOrNull()

        if (rouletteId == null || itemId == null) {
            sender.sendMessage("§cID는 숫자여야 합니다.")
            return
        }

        if (manager.deleteItem(itemId, rouletteId)) {
            sender.sendMessage("§a당첨 아이템이 삭제되었습니다.")
        } else {
            sender.sendMessage("§c당첨 아이템 삭제에 실패했습니다.")
        }
    }

    /**
     * 스크롤 룰렛 삭제
     */
    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /스크롤룰렛 삭제 <룰렛ID>")
            return
        }

        val rouletteId = args[1].toIntOrNull()
        if (rouletteId == null) {
            sender.sendMessage("§c룰렛 ID는 숫자여야 합니다.")
            return
        }

        val roulette = manager.getRouletteById(rouletteId)
        if (roulette == null) {
            sender.sendMessage("§c해당 ID의 스크롤 룰렛을 찾을 수 없습니다.")
            return
        }

        if (manager.deleteRoulette(rouletteId)) {
            sender.sendMessage("§a스크롤 룰렛 '${roulette.rouletteName}'이(가) 삭제되었습니다.")
        } else {
            sender.sendMessage("§c스크롤 룰렛 삭제에 실패했습니다.")
        }
    }

    /**
     * 설정 리로드
     */
    private fun handleReload(sender: CommandSender) {
        manager.reload()
        sender.sendMessage("§a스크롤 룰렛 설정이 리로드되었습니다.")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("lukevanilla.scrollroulette.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("생성", "목록", "아이템추가", "아이템목록", "아이템삭제", "삭제", "리로드")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "생성", "create" -> emptyList() // 이름 직접 입력
                "아이템추가", "additem", "아이템목록", "items", "아이템삭제", "removeitem", "삭제", "delete" -> {
                    manager.getAllRoulettes().map { it.id.toString() }
                        .filter { it.startsWith(args[1]) }
                }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "생성", "create", "아이템추가", "additem" -> listOf("VANILLA", "NEXO")
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                "아이템삭제", "removeitem" -> {
                    val rouletteId = args[1].toIntOrNull() ?: return emptyList()
                    manager.getItems(rouletteId).map { it.id.toString() }
                        .filter { it.startsWith(args[2]) }
                }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "아이템추가", "additem" -> {
                    if (args[2].uppercase() == "VANILLA") {
                        listOf("DIAMOND", "GOLD_INGOT", "IRON_INGOT", "EMERALD", "BARRIER")
                            .filter { it.startsWith(args[3], ignoreCase = true) }
                    } else {
                        emptyList() // Nexo 아이템은 직접 입력
                    }
                }
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "아이템추가", "additem" -> listOf("1", "5", "10", "64")
                    .filter { it.startsWith(args[4]) }
                else -> emptyList()
            }
            6 -> when (args[0].lowercase()) {
                "아이템추가", "additem" -> listOf("10", "20", "30", "50", "100")
                    .filter { it.startsWith(args[5]) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

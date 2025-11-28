package com.lukehemmin.lukeVanilla.System.VillageMerchant

import com.lukehemmin.lukeVanilla.Main
import net.citizensnpcs.api.CitizensAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * 마을 상인 명령어 핸들러
 * 농사마을과 독립적으로 작동
 * 비동기 DB 호출을 사용하여 서버 성능 최적화
 */
class VillageMerchantCommand(
    private val plugin: Main,
    private val manager: VillageMerchantManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("villagemerchant.admin")) {
            sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
            return true
        }

        if (sender !is Player) {
            sender.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "씨앗상인지정" -> handleSetNPCMerchant(sender, "seed_merchant", "씨앗 상인")
            "교환상인지정" -> handleSetNPCMerchant(sender, "exchange_merchant", "교환 상인")
            "장비상인지정" -> handleSetNPCMerchant(sender, "equipment_merchant", "장비 상인")
            "토양받기상인지정" -> handleSetNPCMerchant(sender, "soil_receive_merchant", "토양받기 상인")
            "상인삭제" -> handleRemoveNPCMerchant(sender, args)
            "목록" -> handleListMerchants(sender)
            else -> sendUsage(sender)
        }

        return true
    }

    private fun handleSetNPCMerchant(player: Player, shopId: String, shopName: String) {
        val targetEntity = player.getTargetEntity(5)
        if (targetEntity == null) {
            player.sendMessage(Component.text("NPC를 바라보고 명령어를 사용해주세요.", NamedTextColor.RED))
            return
        }

        // Citizens NPC인지 확인
        val npcRegistry = CitizensAPI.getNPCRegistry()
        val npc = npcRegistry.getNPC(targetEntity)
        if (npc == null) {
            player.sendMessage(Component.text("선택된 엔티티는 NPC가 아닙니다.", NamedTextColor.RED))
            return
        }

        // 비동기로 NPC 상인 저장
        manager.setNPCMerchantAsync(shopId, npc.id).thenAccept { success ->
            // 메인 스레드에서 메시지 전송
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    player.sendMessage(
                        Component.text("${shopName}을 NPC '", NamedTextColor.GREEN)
                            .append(Component.text(npc.name, NamedTextColor.YELLOW))
                            .append(Component.text("' (ID: ${npc.id})로 지정했습니다.", NamedTextColor.GREEN))
                    )
                } else {
                    player.sendMessage(Component.text("NPC 상인 지정 중 오류가 발생했습니다.", NamedTextColor.RED))
                }
            })
        }.exceptionally { throwable ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(Component.text("NPC 상인 지정 중 오류가 발생했습니다: ${throwable.message}", NamedTextColor.RED))
                throwable.printStackTrace()
            })
            null
        }
    }

    private fun handleRemoveNPCMerchant(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(Component.text("사용법: /농사상점 상인삭제 <상인타입>", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("상인타입: seed, exchange, equipment, soil", NamedTextColor.GRAY))
            return
        }

        val shopId = when (args[1].lowercase()) {
            "seed", "씨앗" -> "seed_merchant"
            "exchange", "교환" -> "exchange_merchant"
            "equipment", "장비" -> "equipment_merchant"
            "soil", "토양" -> "soil_receive_merchant"
            else -> {
                player.sendMessage(Component.text("알 수 없는 상인 타입입니다.", NamedTextColor.RED))
                return
            }
        }

        // 비동기로 NPC 상인 삭제
        manager.removeNPCMerchantAsync(shopId).thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    player.sendMessage(Component.text("'$shopId' 상인이 삭제되었습니다.", NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text("해당 상인을 찾을 수 없습니다.", NamedTextColor.YELLOW))
                }
            })
        }.exceptionally { throwable ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(Component.text("상인 삭제 중 오류가 발생했습니다: ${throwable.message}", NamedTextColor.RED))
                throwable.printStackTrace()
            })
            null
        }
    }

    private fun handleListMerchants(player: Player) {
        // 비동기로 상인 목록 조회
        manager.getAllNPCMerchantsAsync().thenAccept { merchants ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (merchants.isEmpty()) {
                    player.sendMessage(Component.text("등록된 상인이 없습니다.", NamedTextColor.YELLOW))
                    return@Runnable
                }

                player.sendMessage(Component.text("=== 마을 상인 목록 ===", NamedTextColor.GOLD))
                for (merchant in merchants) {
                    val shopName = when (merchant.shopId) {
                        "seed_merchant" -> "씨앗 상인"
                        "exchange_merchant" -> "교환 상인"
                        "equipment_merchant" -> "장비 상인"
                        "soil_receive_merchant" -> "토양받기 상인"
                        else -> merchant.shopId
                    }
                    player.sendMessage(
                        Component.text("- ", NamedTextColor.GRAY)
                            .append(Component.text(shopName, NamedTextColor.AQUA))
                            .append(Component.text(" (NPC ID: ${merchant.npcId})", NamedTextColor.GRAY))
                    )
                }
            })
        }.exceptionally { throwable ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(Component.text("상인 목록 조회 중 오류가 발생했습니다: ${throwable.message}", NamedTextColor.RED))
                throwable.printStackTrace()
            })
            null
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("=== 농사 상점 명령어 ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/농사상점 씨앗상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 씨앗 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 교환상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 교환 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 장비상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 장비 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 토양받기상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 토양받기 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 상인삭제 <타입>", NamedTextColor.AQUA)
            .append(Component.text(" - 등록된 상인 삭제", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 목록", NamedTextColor.AQUA)
            .append(Component.text(" - 등록된 상인 목록 보기", NamedTextColor.GRAY)))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (!sender.hasPermission("villagemerchant.admin")) return mutableListOf()

        if (args.size == 1) {
            return mutableListOf("씨앗상인지정", "교환상인지정", "장비상인지정", "토양받기상인지정", "상인삭제", "목록")
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .toMutableList()
        }

        if (args.size == 2 && args[0].equals("상인삭제", ignoreCase = true)) {
            return mutableListOf("seed", "씨앗", "exchange", "교환", "equipment", "장비", "soil", "토양")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }

        return mutableListOf()
    }
}

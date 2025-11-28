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
            "농산물판매상인지정" -> handleSetNPCMerchant(sender, "crop_sell_merchant", "농산물 판매 상인")
            "비료상인지정" -> handleSetNPCMerchant(sender, "fertilizer_merchant", "비료 상인")
            "토양및물품상인지정" -> handleSetNPCMerchant(sender, "soil_goods_merchant", "토양 및 물품 상인")
            "상인삭제" -> handleRemoveNPCMerchant(sender, args)
            "목록" -> handleListMerchants(sender)
            "리로드", "reload" -> {
                manager.reload()
                sender.sendMessage(Component.text("농사 상점 시스템을 리로드했습니다.", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("- 아이템 캐시가 초기화되었습니다.", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("- NPC 상인 데이터가 갱신되었습니다.", NamedTextColor.GRAY))
            }
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
            player.sendMessage(Component.text("상인타입: seed, crop, fertilizer, soil", NamedTextColor.GRAY))
            return
        }

        val shopId = when (args[1].lowercase()) {
            "seed", "씨앗" -> "seed_merchant"
            "crop", "농산물" -> "crop_sell_merchant"
            "fertilizer", "비료" -> "fertilizer_merchant"
            "soil", "토양", "물품" -> "soil_goods_merchant"
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

                player.sendMessage(Component.text("=== 농사 상점 목록 ===", NamedTextColor.GOLD))
                for (merchant in merchants) {
                    val shopName = when (merchant.shopId) {
                        "seed_merchant" -> "씨앗 상인"
                        "crop_sell_merchant" -> "농산물 판매 상인"
                        "fertilizer_merchant" -> "비료 상인"
                        "soil_goods_merchant" -> "토양 및 물품 상인"
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
        sender.sendMessage(Component.text("/농사상점 농산물판매상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 농산물 판매 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 비료상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 비료 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 토양및물품상인지정", NamedTextColor.AQUA)
            .append(Component.text(" - 바라보는 NPC를 토양 및 물품 상인으로 지정", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 상인삭제 <타입>", NamedTextColor.AQUA)
            .append(Component.text(" - 등록된 상인 삭제", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 목록", NamedTextColor.AQUA)
            .append(Component.text(" - 등록된 상인 목록 보기", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/농사상점 리로드", NamedTextColor.AQUA)
            .append(Component.text(" - 데이터 및 캐시 리로드", NamedTextColor.GRAY)))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (!sender.hasPermission("villagemerchant.admin")) return mutableListOf()

        if (args.size == 1) {
            return mutableListOf("씨앗상인지정", "농산물판매상인지정", "비료상인지정", "토양및물품상인지정", "상인삭제", "목록", "리로드")
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .toMutableList()
        }

        if (args.size == 2 && args[0].equals("상인삭제", ignoreCase = true)) {
            return mutableListOf("seed", "씨앗", "crop", "농산물", "fertilizer", "비료", "soil", "토양", "물품")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }

        return mutableListOf()
    }
}

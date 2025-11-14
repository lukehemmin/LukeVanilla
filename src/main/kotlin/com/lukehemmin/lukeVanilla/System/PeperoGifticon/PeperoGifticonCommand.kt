package com.lukehemmin.lukeVanilla.System.PeperoGifticon

import net.dv8tion.jda.api.JDA
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.logging.Logger

/**
 * 빼빼로 기프티콘 보상 명령어
 */
class PeperoGifticonCommand(
    private val repository: PeperoGifticonRepository,
    private val discordListener: PeperoGifticonDiscordListener,
    private val jda: JDA,
    private val logger: Logger
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("lukevanilla.pepero.gifticon.admin")) {
            sender.sendMessage("§c권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "추가" -> handleAddRecipient(sender, args)
            "등록" -> handleRegisterCode(sender, args)
            "재고" -> handleCheckStock(sender)
            "목록" -> handleListRecipients(sender)
            "제거" -> handleRemoveRecipient(sender, args)
            "초기화" -> handleResetRecipient(sender, args)
            else -> sendHelp(sender)
        }

        return true
    }

    /**
     * 수령 대상자 추가 + DM 발송
     */
    private fun handleAddRecipient(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /빼빼로보상 추가 <닉네임>")
            return
        }

        val nickname = args[1]
        val addedBy = sender.name

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LukeVanilla")!!, Runnable {
            try {
                // 1. Player_Data에서 UUID와 DiscordID 조회
                val playerInfo = repository.getPlayerInfoByNickname(nickname)
                if (playerInfo == null) {
                    sender.sendMessage("§c플레이어 '$nickname'을(를) 찾을 수 없습니다.")
                    return@Runnable
                }

                val (uuid, discordId) = playerInfo

                if (discordId == null) {
                    sender.sendMessage("§c플레이어 '$nickname'은(는) 디스코드 연동이 되어있지 않습니다.")
                    return@Runnable
                }

                // 2. 수령 대상자로 등록
                val success = repository.addRecipient(uuid, nickname, discordId, addedBy)
                if (!success) {
                    sender.sendMessage("§c데이터베이스 오류가 발생했습니다.")
                    return@Runnable
                }

                // 3. Discord DM 발송
                sendInitialDM(discordId, nickname, sender)

                sender.sendMessage("§a플레이어 '$nickname'을(를) 기프티콘 수령 대상자로 추가하고 DM을 발송했습니다.")
                logger.info("[PeperoGifticon] 수령 대상자 추가: $nickname (UUID: $uuid, Discord: $discordId) by $addedBy")

            } catch (e: Exception) {
                sender.sendMessage("§c오류 발생: ${e.message}")
                logger.severe("[PeperoGifticon] 수령 대상자 추가 중 오류: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    /**
     * Discord DM 발송
     */
    private fun sendInitialDM(discordId: String, playerName: String, sender: CommandSender) {
        try {
            val user = jda.retrieveUserById(discordId).complete()
            val privateChannel = user.openPrivateChannel().complete()

            val (embed, buttons) = discordListener.createInitialDM()

            privateChannel.sendMessageEmbeds(embed)
                .setActionRow(buttons)
                .queue(
                    {
                        sender.sendMessage("§a'$playerName'에게 DM을 성공적으로 발송했습니다.")
                        logger.info("[PeperoGifticon] DM 발송 성공: $playerName")
                    },
                    { error ->
                        sender.sendMessage("§c'$playerName'에게 DM을 발송하지 못했습니다: ${error.message}")
                        logger.warning("[PeperoGifticon] DM 발송 실패: $playerName - ${error.message}")
                    }
                )

        } catch (e: Exception) {
            sender.sendMessage("§cDM 발송 중 오류 발생: ${e.message}")
            logger.warning("[PeperoGifticon] DM 발송 예외: $playerName - ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 기프티콘 코드 등록
     */
    private fun handleRegisterCode(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§c사용법: /빼빼로보상 등록 <종류> <이미지URL>")
            sender.sendMessage("§c종류: original 또는 almond")
            return
        }

        val type = args[1].lowercase()
        val imageUrl = args[2]

        if (type != "original" && type != "almond") {
            sender.sendMessage("§c잘못된 종류입니다. original 또는 almond를 입력하세요.")
            return
        }

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LukeVanilla")!!, Runnable {
            val success = repository.addGifticonCode(type, imageUrl)
            if (success) {
                val typeName = if (type == "original") "오리지널" else "아몬드"
                sender.sendMessage("§a$typeName 빼빼로 기프티콘 코드가 등록되었습니다.")
                logger.info("[PeperoGifticon] 기프티콘 코드 등록: $type")
            } else {
                sender.sendMessage("§c기프티콘 코드 등록에 실패했습니다.")
            }
        })
    }

    /**
     * 재고 확인
     */
    private fun handleCheckStock(sender: CommandSender) {
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LukeVanilla")!!, Runnable {
            val originalCount = repository.getAvailableGifticonCount("original")
            val almondCount = repository.getAvailableGifticonCount("almond")

            sender.sendMessage("§6§l========== 빼빼로 기프티콘 재고 ==========")
            sender.sendMessage("§e오리지널 빼빼로: §f${originalCount}개")
            sender.sendMessage("§e아몬드 빼빼로: §f${almondCount}개")

            if (originalCount <= 5 || almondCount <= 5) {
                sender.sendMessage("§c⚠️ 재고가 부족합니다!")
            }
        })
    }

    /**
     * 수령 대상자 목록
     */
    private fun handleListRecipients(sender: CommandSender) {
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LukeVanilla")!!, Runnable {
            val recipients = repository.getAllRecipients()

            if (recipients.isEmpty()) {
                sender.sendMessage("§c수령 대상자가 없습니다.")
                return@Runnable
            }

            sender.sendMessage("§6§l========== 기프티콘 수령 대상자 (${recipients.size}명) ==========")
            recipients.forEach { recipient ->
                val status = if (recipient.hasReceived) {
                    val type = when (recipient.gifticonType) {
                        "original" -> "오리지널"
                        "almond" -> "아몬드"
                        else -> recipient.gifticonType
                    }
                    "§a✓ 받음 ($type)"
                } else {
                    "§7대기 중"
                }
                sender.sendMessage("§f${recipient.playerName} §7- $status")
            }
        })
    }

    /**
     * 수령 대상자 제거
     */
    private fun handleRemoveRecipient(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /빼빼로보상 제거 <닉네임>")
            return
        }

        val nickname = args[1]

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LukeVanilla")!!, Runnable {
            val playerInfo = repository.getPlayerInfoByNickname(nickname)
            if (playerInfo == null) {
                sender.sendMessage("§c플레이어 '$nickname'을(를) 찾을 수 없습니다.")
                return@Runnable
            }

            val (uuid, _) = playerInfo
            val success = repository.removeRecipient(uuid)

            if (success) {
                sender.sendMessage("§a플레이어 '$nickname'을(를) 수령 대상자에서 제거했습니다.")
                logger.info("[PeperoGifticon] 수령 대상자 제거: $nickname")
            } else {
                sender.sendMessage("§c제거에 실패했습니다.")
            }
        })
    }

    /**
     * 수령 상태 초기화
     */
    private fun handleResetRecipient(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§c사용법: /빼빼로보상 초기화 <닉네임>")
            return
        }

        val nickname = args[1]

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LukeVanilla")!!, Runnable {
            val playerInfo = repository.getPlayerInfoByNickname(nickname)
            if (playerInfo == null) {
                sender.sendMessage("§c플레이어 '$nickname'을(를) 찾을 수 없습니다.")
                return@Runnable
            }

            val (uuid, _) = playerInfo
            val success = repository.resetRecipientStatus(uuid)

            if (success) {
                sender.sendMessage("§a플레이어 '$nickname'의 수령 상태를 초기화했습니다.")
                logger.info("[PeperoGifticon] 수령 상태 초기화: $nickname")
            } else {
                sender.sendMessage("§c초기화에 실패했습니다.")
            }
        })
    }

    /**
     * 도움말 출력
     */
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§6§l========== 빼빼로 기프티콘 보상 명령어 ==========")
        sender.sendMessage("§e/빼빼로보상 추가 <닉네임> §7- 수령 대상자 추가 및 DM 발송")
        sender.sendMessage("§e/빼빼로보상 등록 <종류> <이미지URL> §7- 기프티콘 등록")
        sender.sendMessage("§e/빼빼로보상 재고 §7- 남은 기프티콘 개수 확인")
        sender.sendMessage("§e/빼빼로보상 목록 §7- 수령 대상자 목록 확인")
        sender.sendMessage("§e/빼빼로보상 제거 <닉네임> §7- 수령 대상자 제거")
        sender.sendMessage("§e/빼빼로보상 초기화 <닉네임> §7- 수령 상태 초기화")
        sender.sendMessage("§7종류: original (오리지널), almond (아몬드)")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission("lukevanilla.pepero.gifticon.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("추가", "등록", "재고", "목록", "제거", "초기화").filter {
                it.startsWith(args[0], ignoreCase = true)
            }
            2 -> {
                when (args[0].lowercase()) {
                    "등록" -> listOf("original", "almond")
                    "추가", "제거", "초기화" -> {
                        // 온라인 플레이어 닉네임 자동완성
                        Bukkit.getOnlinePlayers().map { it.name }.filter {
                            it.startsWith(args[1], ignoreCase = true)
                        }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}

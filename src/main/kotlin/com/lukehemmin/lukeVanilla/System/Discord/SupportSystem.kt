package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.PlayTime.PlayTimeManager
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.bukkit.Bukkit

class SupportSystem(
    private val plugin: Main,
    private val discordBot: DiscordBot,
    private val database: Database,
    private val playTimeManager: PlayTimeManager,
    private val discordRoleManager: DiscordRoleManager
) : ListenerAdapter() {

    private val logger = java.util.logging.Logger.getLogger("SupportSystem")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul")
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when (event.componentId) {
            "my_info" -> handleMyInfo(event)
            "season_items" -> handleSeasonItems(event)
            "auth_unlink_primary" -> handleAuthUnlinkPrimary(event)
            "link_secondary_account" -> handleLinkSecondaryAccount(event)
            "show_secondary_account" -> handleShowSecondaryAccount(event)
            "season_items_from_profile", "season_items_from_secondary" -> handleSeasonItemsFromProfile(event)
            "unlink_secondary_account" -> handleUnlinkSecondaryAccount(event)
            "auth_unlink_confirm" -> handleAuthUnlinkConfirm(event)
            "auth_unlink_cancel" -> handleAuthUnlinkCancel(event)
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId == "link_secondary_account_modal") {
            handleLinkSecondaryAccountModal(event)
        }
    }

    fun setupSupportChannel() {
        val channelId = database.getSettingValue("SystemChannel")
        if (channelId != null) {
            val channel = discordBot.jda.getTextChannelById(channelId)
            channel?.let {
                val messageId = database.getSettingValue("SupportMessageId")
                if (messageId != null) {
                    try {
                        channel.retrieveMessageById(messageId).queue(
                            { /* 메시지가 존재하면 아무것도 하지 않음 */ },
                            { createAndSaveSupportMessage(channel) }
                        )
                    } catch (e: Exception) {
                        createAndSaveSupportMessage(channel)
                    }
                } else {
                    createAndSaveSupportMessage(channel)
                }
            }
        }
    }

    private fun createAndSaveSupportMessage(channel: TextChannel) {
        val embed = EmbedBuilder()
            .setTitle("고객지원")
            .setDescription("아래 버튼을 눌러 원하는 지원을 선택하세요.")
            .setColor(Color.BLUE)
            .build()

        val buttons = listOf(
            Button.primary("my_info", "내 정보"),
            Button.secondary("admin_support", "관리자 문의"),
            Button.success("season_items", "아이템 등록 정보 보기")
        )

        channel.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(buttons))
            .queue { message ->
                database.setSetting("SupportMessageId", message.id)
            }
    }

    private fun handleSeasonItems(event: ButtonInteractionEvent) {
        val seasonItemViewer = SeasonItemViewer(database)
        seasonItemViewer.showSeasonSelectionButtons(event)
    }

    private fun handleMyInfo(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()

        try {
            val discordId = event.user.id
            val primaryUuid = database.getPrimaryUuidByDiscordId(discordId)

            if (primaryUuid == null) {
                event.hook.sendMessage("연동된 마인크래프트 계정을 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 기본 계정 정보 조회
            val playerInfo = database.getPlayerInfo(primaryUuid)
            if (playerInfo == null) {
                event.hook.sendMessage("플레이어 정보를 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 플레이타임 조회 (비동기, Bukkit 메인 스레드에서 실행)
            val uuid = UUID.fromString(primaryUuid)
            getPlayTimeAsync(uuid).thenAccept { (playTimeSeconds, isOnline) ->
                try {
                    val playTimeFormatted = formatPlayTime(playTimeSeconds)

                    // 부계정 정보 확인
                    val accountLink = database.getAccountLinkByPrimaryUuid(primaryUuid)
                    val hasSecondary = accountLink?.secondaryUuid != null

                    // 임베드 생성
                    val embed = EmbedBuilder()
                        .setTitle("⚙ 내 계정 정보")
                        .addField("닉네임", playerInfo.nickname, true)
                        .addField("UUID", primaryUuid, false)
                        .addField("칭호", playerInfo.tag ?: "없음", true)
                        .addField("누적 플레이타임", if (isOnline) "$playTimeFormatted *현재 세션 포함" else playTimeFormatted, true)
                        .addField("마지막 접속 IP", playerInfo.lastestIp ?: "없음", true)
                        .addField("인증 상태", if (playerInfo.isAuth) "✅ 인증됨" else "❌ 미인증", true)
                        .setThumbnail("https://mc-heads.net/avatar/$primaryUuid/128")
                        .setFooter("최근 갱신: ${dateFormat.format(Date())}")
                        .setColor(Color.BLUE)
                        .build()

                    // 버튼 구성
                    val buttons = mutableListOf<Button>()
                    buttons.add(Button.danger("auth_unlink_primary", "인증 해제"))

                    if (hasSecondary) {
                        buttons.add(Button.secondary("show_secondary_account", "부계정 정보"))
                    } else {
                        buttons.add(Button.primary("link_secondary_account", "부계정 연결"))
                    }

                    buttons.add(Button.success("season_items_from_profile", "아이템 등록 보기"))

                    event.hook.sendMessageEmbeds(embed)
                        .setComponents(ActionRow.of(buttons))
                        .setEphemeral(true)
                        .queue()

                } catch (e: Exception) {
                    logger.severe("내 정보 표시 중 오류: ${e.message}")
                    e.printStackTrace()
                    event.hook.sendMessage("정보를 조회하는 중 오류가 발생했습니다.")
                        .setEphemeral(true)
                        .queue()
                }
            }.exceptionally { e ->
                logger.severe("플레이타임 조회 중 오류: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage("정보를 조회하는 중 오류가 발생했습니다.")
                    .setEphemeral(true)
                    .queue()
                null
            }

        } catch (e: Exception) {
            logger.severe("내 정보 조회 중 오류: ${e.message}")
            e.printStackTrace()
            event.hook.sendMessage("정보를 조회하는 중 오류가 발생했습니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun handleLinkSecondaryAccount(event: ButtonInteractionEvent) {
        // 이미 부계정이 연결되어 있는지 체크
        try {
            val discordId = event.user.id
            val primaryUuid = database.getPrimaryUuidByDiscordId(discordId)

            if (primaryUuid != null) {
                val accountLink = database.getAccountLinkByPrimaryUuid(primaryUuid)
                if (accountLink?.secondaryUuid != null) {
                    event.reply("이미 부계정이 연결되어 있습니다.")
                        .setEphemeral(true)
                        .queue()
                    return
                }
            }
        } catch (e: Exception) {
            logger.warning("부계정 체크 중 오류: ${e.message}")
        }

        val modal = Modal.create("link_secondary_account_modal", "부계정 연결")
            .addActionRow(
                TextInput.create("auth_code", "마인크래프트 인증코드", TextInputStyle.SHORT)
                    .setRequired(true)
                    .setMinLength(6)
                    .setMaxLength(6)
                    .setPlaceholder("6자리 인증코드를 입력하세요")
                    .build()
            )
            .build()

        event.replyModal(modal).queue()
    }

    private fun handleLinkSecondaryAccountModal(event: ModalInteractionEvent) {
        event.deferReply(true).queue()

        try {
            val authCode = event.getValue("auth_code")?.asString
            if (authCode == null || authCode.length != 6) {
                event.hook.sendMessage("유효하지 않은 인증코드입니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            val discordId = event.user.id

            // 트랜잭션 처리
            database.getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    // 1. 인증코드 검증
                    val authRecord = database.getAuthRecord(authCode)
                    if (authRecord == null) {
                        event.hook.sendMessage("인증코드가 유효하지 않습니다.")
                            .setEphemeral(true)
                            .queue()
                        conn.rollback()
                        return
                    }

                    // 2. 검증 조건 확인
                    if (authRecord.isAuth) {
                        event.hook.sendMessage("이미 인증된 계정입니다.")
                            .setEphemeral(true)
                            .queue()
                        conn.rollback()
                        return
                    }

                    val playerData = database.getPlayerDataByUuid(authRecord.uuid)
                    if (playerData?.discordId != null && playerData.discordId.isNotEmpty()) {
                        event.hook.sendMessage("이미 다른 디스코드에 연결된 계정입니다.")
                            .setEphemeral(true)
                            .queue()
                        conn.rollback()
                        return
                    }

                    if (database.isSecondaryAccount(authRecord.uuid)) {
                        event.hook.sendMessage("이미 부계정으로 등록된 계정입니다.")
                            .setEphemeral(true)
                            .queue()
                        conn.rollback()
                        return
                    }

                    // 3. 기본 계정 UUID 조회
                    val primaryUuid = database.getPrimaryUuidByDiscordId(discordId)
                    if (primaryUuid == null) {
                        event.hook.sendMessage("기본 계정을 찾을 수 없습니다.")
                            .setEphemeral(true)
                            .queue()
                        conn.rollback()
                        return
                    }

                    // 4. 부계정 연결 처리
                    database.updateDiscordId(authRecord.uuid, discordId)
                    database.updateAuthStatusByUuid(authRecord.uuid, true)
                    database.updateSecondaryUuid(primaryUuid, authRecord.uuid)

                    conn.commit()

                    // 5. 완료 메시지와 함께 업데이트된 내 정보 표시
                    sendUpdatedMyInfo(event.hook, primaryUuid, "✅ 부계정 연결이 완료되었습니다!\n\n")

                } catch (e: Exception) {
                    conn.rollback()
                    logger.severe("부계정 연결 중 오류: ${e.message}")
                    e.printStackTrace()
                    event.hook.sendMessage("작업 중 오류가 발생했습니다. 다시 시도해주세요.")
                        .setEphemeral(true)
                        .queue()
                }
            }

        } catch (e: Exception) {
            logger.severe("부계정 연결 처리 중 오류: ${e.message}")
            e.printStackTrace()
            event.hook.sendMessage("작업 중 오류가 발생했습니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun sendUpdatedMyInfo(hook: InteractionHook, primaryUuid: String, prefixMessage: String = "") {
        try {
            // 기본 계정 정보 조회
            val playerInfo = database.getPlayerInfo(primaryUuid)
            if (playerInfo == null) {
                hook.sendMessage("${prefixMessage}플레이어 정보를 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 플레이타임 조회 (비동기, Bukkit 메인 스레드에서 실행)
            val uuid = UUID.fromString(primaryUuid)
            getPlayTimeAsync(uuid).thenAccept { (playTimeSeconds, _) ->
                try {
                    // 부계정 확인
                    val accountLink = database.getAccountLinkByPrimaryUuid(primaryUuid)
                    val hasSecondary = accountLink?.secondaryUuid != null

                    // 임베드 생성
                    val embed = EmbedBuilder()
                        .setTitle("📊 내 정보")
                        .setColor(Color.GREEN)
                        .addField("닉네임", playerInfo.nickname, true)
                        .addField("UUID", primaryUuid, false)
                        .addField("플레이 시간", formatPlayTime(playTimeSeconds), true)
                        .addField("부계정", if (hasSecondary) "연결됨" else "미연결", true)
                        .setFooter("디스코드 ID: ${playerInfo.discordId ?: "없음"}")
                        .build()

                    // 버튼 구성
                    val buttons = mutableListOf<Button>()
                    buttons.add(Button.danger("auth_unlink_primary", "인증 해제"))

                    if (hasSecondary) {
                        buttons.add(Button.secondary("show_secondary_account", "부계정 정보"))
                    } else {
                        buttons.add(Button.primary("link_secondary_account", "부계정 연결"))
                    }

                    buttons.add(Button.success("season_items_from_profile", "아이템 등록 보기"))

                    hook.sendMessage(prefixMessage)
                        .setEmbeds(embed)
                        .setComponents(ActionRow.of(buttons))
                        .setEphemeral(true)
                        .queue()

                } catch (e: Exception) {
                    logger.severe("내 정보 표시 중 오류: ${e.message}")
                    e.printStackTrace()
                    hook.sendMessage("${prefixMessage}정보를 조회하는 중 오류가 발생했습니다.")
                        .setEphemeral(true)
                        .queue()
                }
            }.exceptionally { e ->
                logger.severe("플레이타임 조회 중 오류: ${e.message}")
                e.printStackTrace()
                hook.sendMessage("${prefixMessage}정보를 조회하는 중 오류가 발생했습니다.")
                    .setEphemeral(true)
                    .queue()
                null
            }

        } catch (e: Exception) {
            logger.severe("내 정보 표시 중 오류: ${e.message}")
            e.printStackTrace()
            hook.sendMessage("${prefixMessage}정보를 조회하는 중 오류가 발생했습니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun handleShowSecondaryAccount(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()

        try {
            val discordId = event.user.id
            val primaryUuid = database.getPrimaryUuidByDiscordId(discordId)

            if (primaryUuid == null) {
                event.hook.sendMessage("기본 계정을 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            val accountLink = database.getAccountLinkByPrimaryUuid(primaryUuid)
            val secondaryUuid = accountLink?.secondaryUuid

            if (secondaryUuid == null) {
                event.hook.sendMessage("부계정이 연결되어 있지 않습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 부계정 정보 조회
            val playerInfo = database.getPlayerInfo(secondaryUuid)
            if (playerInfo == null) {
                event.hook.sendMessage("부계정 정보를 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 플레이타임 조회 (비동기, Bukkit 메인 스레드에서 실행)
            val uuid = UUID.fromString(secondaryUuid)
            getPlayTimeAsync(uuid).thenAccept { (playTimeSeconds, isOnline) ->
                try {
                    val playTimeFormatted = formatPlayTime(playTimeSeconds)

                    // 임베드 생성
                    val embed = EmbedBuilder()
                        .setTitle("👥 부계정 정보")
                        .addField("닉네임", playerInfo.nickname, true)
                        .addField("UUID", secondaryUuid, false)
                        .addField("칭호", playerInfo.tag ?: "없음", true)
                        .addField("누적 플레이타임", if (isOnline) "$playTimeFormatted *현재 세션 포함" else playTimeFormatted, true)
                        .addField("마지막 접속 IP", playerInfo.lastestIp ?: "없음", true)
                        .addField("인증 상태", if (playerInfo.isAuth) "✅ 인증됨" else "❌ 미인증", true)
                        .setThumbnail("https://mc-heads.net/avatar/$secondaryUuid/128")
                        .setFooter("최근 갱신: ${dateFormat.format(Date())}")
                        .setColor(Color.CYAN)
                        .build()

                    val buttons = listOf(
                        Button.danger("unlink_secondary_account", "부계정 연결 해제"),
                        Button.success("season_items_from_secondary", "아이템 등록 보기")
                    )

                    event.hook.sendMessageEmbeds(embed)
                        .setComponents(ActionRow.of(buttons))
                        .setEphemeral(true)
                        .queue()

                } catch (e: Exception) {
                    logger.severe("부계정 정보 표시 중 오류: ${e.message}")
                    e.printStackTrace()
                    event.hook.sendMessage("정보를 조회하는 중 오류가 발생했습니다.")
                        .setEphemeral(true)
                        .queue()
                }
            }.exceptionally { e ->
                logger.severe("플레이타임 조회 중 오류: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage("정보를 조회하는 중 오류가 발생했습니다.")
                    .setEphemeral(true)
                    .queue()
                null
            }

        } catch (e: Exception) {
            logger.severe("부계정 정보 조회 중 오류: ${e.message}")
            e.printStackTrace()
            event.hook.sendMessage("정보를 조회하는 중 오류가 발생했습니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun handleUnlinkSecondaryAccount(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()

        try {
            val discordId = event.user.id
            val primaryUuid = database.getPrimaryUuidByDiscordId(discordId)

            if (primaryUuid == null) {
                event.hook.sendMessage("기본 계정을 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            val accountLink = database.getAccountLinkByPrimaryUuid(primaryUuid)
            val secondaryUuid = accountLink?.secondaryUuid

            if (secondaryUuid == null) {
                event.hook.sendMessage("부계정이 연결되어 있지 않습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 트랜잭션 처리
            database.getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    // 1. Player_Data DiscordID NULL로 설정
                    database.clearDiscordId(secondaryUuid)

                    // 2. Player_Auth IsAuth 0으로 설정
                    database.updateAuthStatusByUuid(secondaryUuid, false)

                    // 3. Discord_Account_Link secondary_uuid NULL로 설정
                    database.clearSecondaryUuid(primaryUuid)

                    conn.commit()

                    // 4. 부계정이 본서버에 접속중이면 킥 처리
                    try {
                        val secondaryPlayerInfo = database.getPlayerInfo(secondaryUuid)
                        if (secondaryPlayerInfo != null) {
                            val currentServer = database.getPlayerCurrentServer(secondaryUuid)
                            if (currentServer == "vanilla") {
                                database.addCrossServerCommand(
                                    commandType = "kick",
                                    targetPlayerUuid = secondaryUuid,
                                    targetPlayerName = secondaryPlayerInfo.nickname,
                                    sourceServer = "lobby",
                                    targetServer = "vanilla",
                                    commandData = mapOf("reason" to "부계정 연결이 해제되었습니다."),
                                    commandReason = "부계정 연결 해제",
                                    issuedBy = "System"
                                )
                                logger.info("본서버 킥 명령 전송 (부계정 연결 해제): ${secondaryPlayerInfo.nickname}")
                            }
                        }
                    } catch (e: Exception) {
                        logger.warning("부계정 킥 처리 실패: ${e.message}")
                    }

                    event.hook.sendMessage("부계정 연결이 해제되었습니다.")
                        .setEphemeral(true)
                        .queue()

                } catch (e: Exception) {
                    conn.rollback()
                    logger.severe("부계정 연결 해제 중 오류: ${e.message}")
                    e.printStackTrace()
                    event.hook.sendMessage("작업 중 오류가 발생했습니다. 다시 시도해주세요.")
                        .setEphemeral(true)
                        .queue()
                }
            }

        } catch (e: Exception) {
            logger.severe("부계정 연결 해제 처리 중 오류: ${e.message}")
            e.printStackTrace()
            event.hook.sendMessage("작업 중 오류가 발생했습니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun handleAuthUnlinkPrimary(event: ButtonInteractionEvent) {
        val embed = EmbedBuilder()
            .setTitle("⚠ 인증 해제 안내")
            .setDescription("""
                인증을 해제하는 경우 디스코드 인증 역할이 제외되어 다른 채팅방을 이용할 수 없게 되며 기존 계정으로 접속이 불가능합니다.
                
                다시 인증이 가능하며 다른 마인크래프트 계정, 아이디로 본계를 전환할 때 이 기능을 사용할 수 있습니다.
                
                마인크래프트 도전과제 진행 정보는 이동되지 않으며 아이템도 이동되지 않습니다.
                
                이 상황을 이해했고 계속 진행하고 싶으면 아래 버튼을 클릭하여 연결을 해제하거나 취소할 수 있습니다.
            """.trimIndent())
            .setColor(Color.ORANGE)
            .setFooter("이 메시지는 5분 후 자동으로 삭제됩니다")
            .build()

        val buttons = listOf(
            Button.success("auth_unlink_confirm", "계속합니다."),
            Button.danger("auth_unlink_cancel", "취소")
        )

        event.reply("")
            .setEmbeds(embed)
            .setComponents(ActionRow.of(buttons))
            .setEphemeral(true)
            .queue { hook ->
                hook.deleteOriginal().queueAfter(5, TimeUnit.MINUTES)
            }
    }

    private fun handleAuthUnlinkConfirm(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()

        try {
            val discordId = event.user.id
            val primaryUuid = database.getPrimaryUuidByDiscordId(discordId)

            if (primaryUuid == null) {
                event.hook.sendMessage("기본 계정을 찾을 수 없습니다.")
                    .setEphemeral(true)
                    .queue()
                return
            }

            // 트랜잭션 처리
            database.getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    // 부계정 UUID 확인
                    val accountLink = database.getAccountLinkByPrimaryUuid(primaryUuid)
                    val secondaryUuid = accountLink?.secondaryUuid

                    // 1. 기본 계정 DiscordID NULL로 설정
                    database.clearDiscordId(primaryUuid)

                    // 2. 기본 계정 IsAuth 0으로 설정
                    database.updateAuthStatusByUuid(primaryUuid, false)

                    // 3. 부계정이 있으면 부계정도 처리
                    if (secondaryUuid != null) {
                        database.clearDiscordId(secondaryUuid)
                        database.updateAuthStatusByUuid(secondaryUuid, false)
                    }

                    // 4. Discord_Account_Link 행 삭제
                    database.deleteAccountLink(primaryUuid)

                    conn.commit()

                    // 5. 본서버에 접속중이면 킥 처리 (기본 계정)
                    try {
                        val playerInfo = database.getPlayerInfo(primaryUuid)
                        if (playerInfo != null) {
                            val currentServer = database.getPlayerCurrentServer(primaryUuid)
                            if (currentServer == "vanilla") {
                                database.addCrossServerCommand(
                                    commandType = "kick",
                                    targetPlayerUuid = primaryUuid,
                                    targetPlayerName = playerInfo.nickname,
                                    sourceServer = "lobby",
                                    targetServer = "vanilla",
                                    commandData = mapOf("reason" to "인증이 해제되었습니다."),
                                    commandReason = "인증 해제",
                                    issuedBy = "System"
                                )
                                logger.info("본서버 킥 명령 전송 (기본 계정): ${playerInfo.nickname}")
                            }
                        }

                        // 부계정도 본서버에 접속중이면 킥 처리
                        if (secondaryUuid != null) {
                            val secondaryPlayerInfo = database.getPlayerInfo(secondaryUuid)
                            if (secondaryPlayerInfo != null) {
                                val secondaryCurrentServer = database.getPlayerCurrentServer(secondaryUuid)
                                if (secondaryCurrentServer == "vanilla") {
                                    database.addCrossServerCommand(
                                        commandType = "kick",
                                        targetPlayerUuid = secondaryUuid,
                                        targetPlayerName = secondaryPlayerInfo.nickname,
                                        sourceServer = "lobby",
                                        targetServer = "vanilla",
                                        commandData = mapOf("reason" to "인증이 해제되었습니다."),
                                        commandReason = "인증 해제",
                                        issuedBy = "System"
                                    )
                                    logger.info("본서버 킥 명령 전송 (부계정): ${secondaryPlayerInfo.nickname}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warning("본서버 킥 처리 실패: ${e.message}")
                    }

                    // 6. 디스코드 역할 제거
                    try {
                        discordRoleManager.removeAuthRole(discordId)
                    } catch (e: Exception) {
                        logger.warning("역할 제거 실패: ${e.message}")
                        event.hook.sendMessage("인증이 해제되었으나 역할 제거에 실패했습니다. 관리자에게 문의해주세요.")
                            .setEphemeral(true)
                            .queue()
                        return
                    }

                    event.hook.sendMessage("인증이 해제되었습니다.")
                        .setEphemeral(true)
                        .queue()

                } catch (e: Exception) {
                    conn.rollback()
                    logger.severe("인증 해제 중 오류: ${e.message}")
                    e.printStackTrace()
                    event.hook.sendMessage("작업 중 오류가 발생했습니다. 다시 시도해주세요.")
                        .setEphemeral(true)
                        .queue()
                }
            }

        } catch (e: Exception) {
            logger.severe("인증 해제 처리 중 오류: ${e.message}")
            e.printStackTrace()
            event.hook.sendMessage("작업 중 오류가 발생했습니다.")
                .setEphemeral(true)
                .queue()
        }
    }

    private fun handleAuthUnlinkCancel(event: ButtonInteractionEvent) {
        event.reply("작업이 취소되었습니다.")
            .setEphemeral(true)
            .queue()
    }

    private fun handleSeasonItemsFromProfile(event: ButtonInteractionEvent) {
        val seasonItemViewer = SeasonItemViewer(database)
        seasonItemViewer.showSeasonSelectionButtons(event)
    }

    private fun formatPlayTime(totalSeconds: Long): String {
        val days = TimeUnit.SECONDS.toDays(totalSeconds)
        val hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60

        return buildString {
            if (days > 0) append("${days}일 ")
            if (hours > 0) append("${hours}시간 ")
            if (minutes > 0 || isEmpty()) append("${minutes}분")
        }.trim()
    }

    /**
     * Bukkit 메인 스레드에서 플레이타임을 안전하게 조회하는 비동기 헬퍼 함수
     * @param uuid 조회할 플레이어의 UUID
     * @return CompletableFuture<Pair<플레이타임(초), 온라인 여부>>
     */
    private fun getPlayTimeAsync(uuid: UUID): CompletableFuture<Pair<Long, Boolean>> {
        val future = CompletableFuture<Pair<Long, Boolean>>()

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val isOnline = playTimeManager.isPlayerOnline(uuid)
                val playTimeSeconds = if (isOnline) {
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null) {
                        playTimeManager.getCurrentTotalPlayTime(player)
                    } else {
                        playTimeManager.getSavedTotalPlayTime(uuid)
                    }
                } else {
                    playTimeManager.getSavedTotalPlayTime(uuid)
                }
                future.complete(playTimeSeconds to isOnline)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        })

        return future
    }
}
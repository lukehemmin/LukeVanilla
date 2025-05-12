package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger

class LobbyCommand(
    private val server: ProxyServer,
    private val logger: Logger,
    private val redirectManager: PlayerRedirectManager
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val player = invocation.source() as? Player ?: return
        val lobbyServer = server.getServer("lobby")

        if (lobbyServer.isPresent) {
            player.createConnectionRequest(lobbyServer.get()).fireAndForget()
            player.sendMessage(Component.text("로비 서버로 이동합니다.").color(NamedTextColor.GREEN))
            redirectManager.markPlayerExplicitlyChooseLobby(player.uniqueId) // 로비 명시적 선택
            logger.info("${player.username} executed /로비서버 and is connecting to lobby.")
        } else {
            player.sendMessage(Component.text("로비 서버를 찾을 수 없습니다.").color(NamedTextColor.RED))
            logger.warn("Lobby server not found when ${player.username} executed /로비서버.")
        }
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return true // 모든 플레이어가 사용 가능
    }
}

class WildServerCommand(
    private val server: ProxyServer,
    private val logger: Logger,
    private val redirectManager: PlayerRedirectManager // 명시적 선택을 위해 추가
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val player = invocation.source() as? Player ?: return
        // "야생서버"는 "vanilla" 서버로 가정합니다. 실제 서버 이름에 따라 수정 필요.
        val wildServer = server.getServer("vanilla")

        if (wildServer.isPresent) {
            player.createConnectionRequest(wildServer.get()).fireAndForget()
            player.sendMessage(Component.text("야생 서버로 이동합니다.").color(NamedTextColor.GREEN))
            // 야생 서버 명시적 선택에 대한 처리 (PlayerRedirectManager에 관련 로직 추가 필요 시)
            // 예: redirectManager.markPlayerExplicitlyChooseWild(player.uniqueId)
            // 현재는 로비 선택과 유사하게 처리하거나, 별도 플래그 없이 진행
            // 만약 야생 서버로 이동 후 자동 로비 이동을 막고 싶다면 PlayerRedirectManager 수정 필요
            logger.info("${player.username} executed /야생서버 and is connecting to vanilla (wild) server.")
        } else {
            player.sendMessage(Component.text("야생 서버를 찾을 수 없습니다.").color(NamedTextColor.RED))
            logger.warn("Vanilla (wild) server not found when ${player.username} executed /야생서버.")
        }
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return true // 모든 플레이어가 사용 가능
    }
}
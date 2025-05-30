package com.lukehemmin.lukeVanilla.System.Command

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Discord.ServerStatusRequester
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * 서버 간 연결 관리를 위한 명령어
 * 로비서버에서 야생서버와의 연결 상태를 관리할 수 있는 기능 제공
 */
class ServerConnectionCommand(private val plugin: Main) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // 권한 확인
        if (!sender.hasPermission("lukevanilla.admin")) {
            sender.sendMessage("${ChatColor.RED}이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        // 로비서버에서만 실행 가능
        if (plugin.getServerType() != "Lobby") {
            sender.sendMessage("${ChatColor.RED}이 명령어는 로비서버에서만 사용할 수 있습니다.")
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "status" -> handleStatus(sender)
            "reset" -> handleReset(sender)
            "test" -> handleTest(sender)
            "clear" -> handleClear(sender)
            else -> showHelp(sender)
        }

        return true
    }

    /**
     * 도움말 표시
     */
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}=== 서버 연결 관리 명령어 ===")
        sender.sendMessage("${ChatColor.YELLOW}/서버연결 status ${ChatColor.WHITE}- 현재 연결 상태 확인")
        sender.sendMessage("${ChatColor.YELLOW}/서버연결 test ${ChatColor.WHITE}- 야생서버 연결 테스트")
        sender.sendMessage("${ChatColor.YELLOW}/서버연결 clear ${ChatColor.WHITE}- 대기 중인 요청 강제 제거")
        sender.sendMessage("${ChatColor.YELLOW}/서버연결 reset ${ChatColor.WHITE}- 연결 상태 완전 초기화")
    }

    /**
     * 현재 연결 상태 확인
     */
    private fun handleStatus(sender: CommandSender) {
        try {
            val requester = ServerStatusRequester.getInstance(plugin)
            val pendingCount = requester.getPendingRequestsCount()
            
            sender.sendMessage("${ChatColor.GREEN}=== 서버 연결 상태 ===")
            sender.sendMessage("${ChatColor.AQUA}서버 타입: ${plugin.getServerType()}")
            sender.sendMessage("${ChatColor.AQUA}대기 중인 요청: ${pendingCount}개")
            
            // 채널 등록 상태 확인
            val isRequestChannelRegistered = plugin.server.messenger.isOutgoingChannelRegistered(plugin, Main.CHANNEL_SERVER_STATUS_REQUEST)
            val isResponseChannelRegistered = plugin.server.messenger.isIncomingChannelRegistered(plugin, Main.CHANNEL_SERVER_STATUS_RESPONSE)
            
            sender.sendMessage("${ChatColor.AQUA}요청 채널 등록: ${if (isRequestChannelRegistered) "${ChatColor.GREEN}등록됨" else "${ChatColor.RED}미등록"}")
            sender.sendMessage("${ChatColor.AQUA}응답 채널 등록: ${if (isResponseChannelRegistered) "${ChatColor.GREEN}등록됨" else "${ChatColor.RED}미등록"}")
            
        } catch (e: Exception) {
            sender.sendMessage("${ChatColor.RED}상태 확인 중 오류 발생: ${e.message}")
        }
    }

    /**
     * 야생서버 연결 테스트
     */
    private fun handleTest(sender: CommandSender) {
        sender.sendMessage("${ChatColor.YELLOW}야생서버 연결 테스트 중...")
        
        try {
            val requester = ServerStatusRequester.getInstance(plugin)
            val future = requester.requestSurvivalServerStatus()
            
            // 비동기로 결과 처리
            future.thenAccept { result ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val hasError = result.containsKey("error")
                    
                    if (hasError) {
                        sender.sendMessage("${ChatColor.RED}연결 테스트 실패: ${result["error"]}")
                    } else {
                        sender.sendMessage("${ChatColor.GREEN}연결 테스트 성공!")
                        sender.sendMessage("${ChatColor.AQUA}TPS: ${result["tps"]}")
                        sender.sendMessage("${ChatColor.AQUA}MSPT: ${result["mspt"]}")
                        sender.sendMessage("${ChatColor.AQUA}플레이어: ${result["onlinePlayers"]}/${result["maxPlayers"]}")
                    }
                })
            }.exceptionally { throwable ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage("${ChatColor.RED}연결 테스트 중 예외 발생: ${throwable.message}")
                })
                null
            }
            
        } catch (e: Exception) {
            sender.sendMessage("${ChatColor.RED}연결 테스트 시작 실패: ${e.message}")
        }
    }

    /**
     * 대기 중인 요청 강제 제거
     */
    private fun handleClear(sender: CommandSender) {
        try {
            val requester = ServerStatusRequester.getInstance(plugin)
            val clearedCount = requester.clearPendingRequests()
            
            sender.sendMessage("${ChatColor.GREEN}대기 중인 요청 ${clearedCount}개를 정리했습니다.")
            
        } catch (e: Exception) {
            sender.sendMessage("${ChatColor.RED}요청 정리 중 오류 발생: ${e.message}")
        }
    }

    /**
     * 연결 상태 완전 초기화
     */
    private fun handleReset(sender: CommandSender) {
        sender.sendMessage("${ChatColor.YELLOW}서버 연결 상태를 초기화하는 중...")
        
        try {
            // 1. 대기 중인 요청 모두 제거
            val requester = ServerStatusRequester.getInstance(plugin)
            val clearedCount = requester.clearPendingRequests()
            
            // 2. 채널 재등록
            plugin.server.scheduler.runTask(plugin, Runnable {
                try {
                    // 기존 채널 해제
                    if (plugin.server.messenger.isIncomingChannelRegistered(plugin, Main.CHANNEL_SERVER_STATUS_RESPONSE)) {
                        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, Main.CHANNEL_SERVER_STATUS_RESPONSE)
                    }
                    if (plugin.server.messenger.isOutgoingChannelRegistered(plugin, Main.CHANNEL_SERVER_STATUS_REQUEST)) {
                        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, Main.CHANNEL_SERVER_STATUS_REQUEST)
                    }
                    
                    // 잠시 대기
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        try {
                            // 채널 재등록
                            if (!plugin.server.messenger.isOutgoingChannelRegistered(plugin, Main.CHANNEL_SERVER_STATUS_REQUEST)) {
                                plugin.server.messenger.registerOutgoingPluginChannel(plugin, Main.CHANNEL_SERVER_STATUS_REQUEST)
                            }
                            if (!plugin.server.messenger.isIncomingChannelRegistered(plugin, Main.CHANNEL_SERVER_STATUS_RESPONSE)) {
                                plugin.server.messenger.registerIncomingPluginChannel(plugin, Main.CHANNEL_SERVER_STATUS_RESPONSE, requester)
                            }
                            
                            sender.sendMessage("${ChatColor.GREEN}연결 상태 초기화 완료!")
                            sender.sendMessage("${ChatColor.GREEN}정리된 요청: ${clearedCount}개")
                            
                        } catch (e: Exception) {
                            sender.sendMessage("${ChatColor.RED}채널 재등록 실패: ${e.message}")
                        }
                    }, 20L) // 1초 대기
                    
                } catch (e: Exception) {
                    sender.sendMessage("${ChatColor.RED}채널 해제 실패: ${e.message}")
                }
            })
            
        } catch (e: Exception) {
            sender.sendMessage("${ChatColor.RED}초기화 중 오류 발생: ${e.message}")
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("status", "test", "clear", "reset").filter { it.startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
}

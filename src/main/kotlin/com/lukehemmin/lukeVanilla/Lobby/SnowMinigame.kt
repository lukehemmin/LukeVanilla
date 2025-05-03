package com.lukehemmin.lukeVanilla.Lobby

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*

class SnowMinigame(
    private val plugin: Main
) : Listener {

    private val waitingPlayers = mutableSetOf<UUID>()
    private var gameState: GameState = GameState.WAITING
    private var countdownTask: BukkitTask? = null
    private var countdownSeconds: Int = 30

    private val minPlayers = 2
    private val maxPlayers = 8

    private lateinit var normalizedCorner1: Location
    private lateinit var normalizedCorner2: Location
    private lateinit var arenaWorld: World

    init {
        initializeArena("world")
        plugin.server.pluginManager.registerEvents(this, plugin)
        println("[LukeVanilla] 눈 미니게임이 초기화되었습니다. 설정된 영역: ${normalizedCorner1.toVector()}에서 ${normalizedCorner2.toVector()}까지")
    }

    private fun initializeArena(worldName: String) {
        arenaWorld = Bukkit.getWorld(worldName)
            ?: throw IllegalStateException("눈 미니게임 월드 '$worldName'을 찾을 수 없거나 로드되지 않았습니다!")

        val point1 = Location(arenaWorld, 21.0, 12.0, 57.0)
        val point2 = Location(arenaWorld, -21.0, 0.0, 98.0)

        val minX = minOf(point1.x, point2.x)
        val minY = minOf(point1.y, point2.y)
        val minZ = minOf(point1.z, point2.z)
        val maxX = maxOf(point1.x, point2.x)
        val maxY = maxOf(point1.y, point2.y)
        val maxZ = maxOf(point1.z, point2.z)

        normalizedCorner1 = Location(arenaWorld, minX, minY, minZ)
        normalizedCorner2 = Location(arenaWorld, maxX, maxY, maxZ)
    }

    enum class GameState {
        WAITING, COUNTING_DOWN, PAUSED, IN_GAME, FINISHED
    }

    private fun isInsideArena(location: Location): Boolean {
        if (location.world != arenaWorld) return false
        return location.x >= normalizedCorner1.x && location.x <= normalizedCorner2.x &&
               location.y >= normalizedCorner1.y && location.y <= normalizedCorner2.y &&
               location.z >= normalizedCorner1.z && location.z <= normalizedCorner2.z
    }

    private fun addPlayer(player: Player) {
        if (gameState == GameState.IN_GAME || gameState == GameState.FINISHED) {
            player.sendMessage("${ChatColor.RED}게임이 이미 진행 중이거나 종료되었습니다.")
            return
        }

        if (gameState == GameState.PAUSED && waitingPlayers.size >= maxPlayers) {
            if (!waitingPlayers.contains(player.uniqueId)) {
                player.sendMessage("${ChatColor.RED}게임 시작 대기열이 가득 찼습니다.")
                return
            }
        }

        val added = waitingPlayers.add(player.uniqueId)

        if (added) {
            println("[LukeVanilla] 플레이어 ${player.name}이(가) 대기열에 추가되었습니다 (현재: ${waitingPlayers.size})")
            broadcastWaitingMessage("${ChatColor.AQUA}${player.name}${ChatColor.YELLOW}님이 게임에 참가했습니다! (${waitingPlayers.size}/${maxPlayers})")
            checkPlayerCountAndManageCountdown()
        } else {
            println("[LukeVanilla] 플레이어 ${player.name}이(가) 참가하려 했으나 이미 대기 중입니다.")
        }
    }

    private fun removePlayer(player: Player) {
        removePlayer(player, true)
    }

    private fun removePlayer(player: Player, performStateCheck: Boolean) {
        if (waitingPlayers.remove(player.uniqueId)) {
            println("[LukeVanilla] 플레이어 ${player.name}이(가) 대기열에서 제외되었습니다 (현재: ${waitingPlayers.size})")
            if (performStateCheck) {
                val currentCount = waitingPlayers.size
                var message = "${ChatColor.AQUA}${player.name}${ChatColor.YELLOW}님이 게임에서 나갔습니다. (${currentCount}/${maxPlayers})"

                if ((gameState == GameState.PAUSED || gameState == GameState.COUNTING_DOWN)) {
                    if (currentCount < minPlayers) {
                        message += " 최소 인원 미달로 카운트다운이 취소됩니다."
                    } else if (currentCount <= maxPlayers && gameState == GameState.PAUSED) {
                        message += " 인원이 충족되어 카운트다운을 재개합니다."
                    }
                }

                broadcastWaitingMessage(message)
                checkPlayerCountAndManageCountdown()
            } else {
                println("[LukeVanilla] 플레이어 ${player.name}이(가) 상태 확인 없이 제거되었습니다.")
            }
        }
    }

    private fun checkPlayerCountAndManageCountdown() {
        val currentCount = waitingPlayers.size
        val previousState = gameState
        println("[LukeVanilla] 상태 확인 중: ${gameState}, 플레이어: ${currentCount}, 작업: ${countdownTask != null && !countdownTask!!.isCancelled}")

        when (gameState) {
            GameState.WAITING -> {
                if (currentCount >= minPlayers && currentCount <= maxPlayers) {
                    startCountdown()
                } else if (currentCount > maxPlayers) {
                    println("[LukeVanilla] 경고: 대기 상태에 ${currentCount} 명의 플레이어가 있습니다. 일시정지 상태로 강제 전환합니다.")
                    gameState = GameState.PAUSED
                    broadcastWaitingMessage("${ChatColor.YELLOW}인원이 최대치를 초과하여 대기합니다. (${currentCount}/${maxPlayers})")
                }
            }
            GameState.COUNTING_DOWN -> {
                if (currentCount > maxPlayers) {
                    pauseCountdown()
                } else if (currentCount < minPlayers) {
                    cancelCountdown("${ChatColor.RED}최소 인원 미달로 카운트다운이 취소되었습니다.")
                }
            }
            GameState.PAUSED -> {
                if (currentCount <= maxPlayers && currentCount >= minPlayers) {
                    resumeCountdown()
                } else if (currentCount < minPlayers) {
                    cancelCountdown("${ChatColor.RED}최소 인원 미달로 카운트다운이 취소되었습니다.")
                }
            }
            GameState.IN_GAME, GameState.FINISHED -> { /* Handle later */ }
        }
        if (previousState != gameState) {
            println("[LukeVanilla] 상태가 ${previousState} 에서 ${gameState} 로 변경되었습니다.")
        }
    }

    private fun startCountdown() {
        if (gameState != GameState.WAITING) {
            println("[LukeVanilla] 카운트다운을 시작할 수 없습니다. 현재 상태: ${gameState}")
            return
        }
        if (waitingPlayers.size < minPlayers || waitingPlayers.size > maxPlayers) {
            println("[LukeVanilla] 카운트다운을 시작할 수 없습니다. 유효하지 않은 플레이어 수: ${waitingPlayers.size}")
            if (waitingPlayers.size > maxPlayers) gameState = GameState.PAUSED
            return
        }

        gameState = GameState.COUNTING_DOWN
        countdownSeconds = 30
        broadcastWaitingMessage("${ChatColor.GREEN}최소 인원이 모여 ${countdownSeconds}초 후 게임을 시작합니다!")
        println("[LukeVanilla] 카운트다운을 시작합니다.")

        countdownTask?.cancel()
        countdownTask = object : BukkitRunnable() {
            override fun run() {
                if (gameState != GameState.COUNTING_DOWN) {
                    println("[LukeVanilla] 상태가 ${gameState} 로 변경되어 카운트다운 작업이 취소되었습니다.")
                    this.cancel()
                    countdownTask = null
                    return
                }
                if (countdownSeconds <= 0) {
                    if (waitingPlayers.size < minPlayers) cancelCountdown("${ChatColor.RED}시작 직전 최소 인원 미달!")
                    else if (waitingPlayers.size > maxPlayers) pauseCountdown()
                    else startGame()
                    this.cancel()
                    countdownTask = null
                    return
                }
                if (countdownSeconds % 10 == 0 || countdownSeconds <= 5) {
                    broadcastWaitingMessage("${ChatColor.GREEN}게임 시작까지 ${countdownSeconds}초...")
                }
                countdownSeconds--
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    private fun pauseCountdown() {
        if (gameState != GameState.COUNTING_DOWN) {
            println("[LukeVanilla] 카운트다운을 일시 중지할 수 없습니다. 현재 상태: ${gameState}")
            return
        }
        countdownTask?.cancel()
        countdownTask = null
        gameState = GameState.PAUSED
        broadcastWaitingMessage("${ChatColor.YELLOW}최대 인원을 초과하여 카운트다운을 일시 중지합니다. (${waitingPlayers.size}/${maxPlayers})")
        println("[LukeVanilla] ${countdownSeconds}초 남은 상태에서 카운트다운이 일시 중지되었습니다.")
    }

    private fun resumeCountdown() {
        if (gameState != GameState.PAUSED) {
            println("[LukeVanilla] 카운트다운을 재개할 수 없습니다. 현재 상태: ${gameState}")
            return
        }
        if (waitingPlayers.size < minPlayers || waitingPlayers.size > maxPlayers) {
            println("[LukeVanilla] 카운트다운을 재개할 수 없습니다. 유효하지 않은 플레이어 수: ${waitingPlayers.size}")
            if (waitingPlayers.size < minPlayers) cancelCountdown("${ChatColor.RED}최소 인원 미달!")
            return
        }

        gameState = GameState.COUNTING_DOWN
        broadcastWaitingMessage("${ChatColor.GREEN}인원이 충족되어 카운트다운을 재개합니다! (${countdownSeconds}초 남음)")
        println("[LukeVanilla] ${countdownSeconds}초에서 카운트다운을 재개합니다.")

        countdownTask?.cancel()
        countdownTask = object : BukkitRunnable() {
            override fun run() {
                if (gameState != GameState.COUNTING_DOWN) {
                    println("[LukeVanilla] 상태가 ${gameState} 로 변경되어 카운트다운 작업이 취소되었습니다.")
                    this.cancel()
                    countdownTask = null
                    return
                }
                if (countdownSeconds <= 0) {
                    if (waitingPlayers.size < minPlayers) cancelCountdown("${ChatColor.RED}시작 직전 최소 인원 미달!")
                    else if (waitingPlayers.size > maxPlayers) pauseCountdown()
                    else startGame()
                    this.cancel()
                    countdownTask = null
                    return
                }
                if (countdownSeconds % 10 == 0 || countdownSeconds <= 5) {
                    broadcastWaitingMessage("${ChatColor.GREEN}게임 시작까지 ${countdownSeconds}초...")
                }
                countdownSeconds--
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    private fun cancelCountdown(reason: String) {
        if (gameState != GameState.COUNTING_DOWN && gameState != GameState.PAUSED) {
            println("[LukeVanilla] 카운트다운을 취소할 수 없습니다. 현재 상태: ${gameState}")
            return
        }
        val previousState = gameState
        countdownTask?.cancel()
        countdownTask = null
        gameState = GameState.WAITING
        val remainingSeconds = countdownSeconds
        countdownSeconds = 30
        broadcastWaitingMessage(reason)
        println("[LukeVanilla] ${previousState} 상태에서 ${remainingSeconds}초 남은 카운트다운이 취소되었습니다. 이유: ${reason}")
    }

    private fun startGame() {
        gameState = GameState.IN_GAME
        broadcastWaitingMessage("${ChatColor.GOLD}게임 시작! 행운을 빕니다!")
        println("[LukeVanilla] 게임 시작. 참가자: ${waitingPlayers.joinToString { Bukkit.getOfflinePlayer(it).name ?: it.toString() }}")

        waitingPlayers.mapNotNull { Bukkit.getPlayer(it) }.forEach { player ->
            player.sendMessage("${ChatColor.BLUE}눈 블록을 부숴 최후의 승자가 되세요!")
        }

        object : BukkitRunnable() {
            override fun run() {
                if (gameState == GameState.IN_GAME) {
                    println("[LukeVanilla] 임시 게임 타이머가 종료되었습니다. 초기화 중...")
                    resetGame("${ChatColor.YELLOW}테스트 게임 시간이 종료되었습니다.")
                }
            }
        }.runTaskLater(plugin, 20L * 60)
    }

    private fun resetGame(resetMessage: String) {
        println("[LukeVanilla] 게임 상태를 초기화합니다. 이유: ${resetMessage}")
        waitingPlayers.clear()
        gameState = GameState.WAITING
        countdownTask?.cancel()
        countdownTask = null
        countdownSeconds = 30
        println("[LukeVanilla] 게임 초기화가 완료되었습니다. 현재 상태: WAITING")
    }

    private fun broadcastWaitingMessage(message: String) {
        waitingPlayers.mapNotNull { Bukkit.getPlayer(it) }.forEach { it.sendMessage(message) }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to ?: return

        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val wasInArenaZone = isInsideArena(from)
        val isInArenaZoneNow = isInsideArena(to)

        if (isInArenaZoneNow != wasInArenaZone) {
            if (isInArenaZoneNow) {
                println("[LukeVanilla] 플레이어 ${player.name}이(가) 경기장 영역에 들어왔습니다.")
                addPlayer(player)
            } else {
                println("[LukeVanilla] 플레이어 ${player.name}이(가) 경기장 영역을 나갔습니다.")
                removePlayer(player)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (waitingPlayers.contains(player.uniqueId)) {
            println("[LukeVanilla] 플레이어 ${player.name}이(가) 대기 중 서버를 나갔습니다.")
            removePlayer(player)
        }
    }
}
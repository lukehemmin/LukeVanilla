package com.lukehemmin.lukeVanilla.Lobby

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.*
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.attribute.Attribute
import java.util.*
import kotlin.math.*

data class TeamData(val name: String, val color: ChatColor, val spawn: Location)

class SnowMinigame(private val plugin: JavaPlugin) : Listener {

    private val waitingPlayers = mutableSetOf<UUID>()
    private val playerInventories = mutableMapOf<UUID, Array<ItemStack?>>()
    private val playerTeams = mutableMapOf<UUID, TeamData>()
    private val spectatingPlayers = mutableSetOf<UUID>()
    private var gameState: GameState = GameState.WAITING
    private var countdownTask: BukkitTask? = null
    private var gameStartCountdownTask: BukkitTask? = null
    private var gameMovementAllowed = false
    private var countdownSeconds: Int = 30

    private val minPlayers = 2
    private val maxPlayers = 8

    private lateinit var normalizedCorner1: Location
    private lateinit var normalizedCorner2: Location
    private lateinit var arenaWorld: World

    private val teams = mutableListOf<TeamData>()

    init {
        initializeArena("world")
        initializeTeams()
        plugin.server.pluginManager.registerEvents(this, plugin)
        println("[LukeVanilla] 눈 미니게임이 초기화되었습니다. 설정된 영역: ${normalizedCorner1.toVector()}에서 ${normalizedCorner2.toVector()}까지")
    }

    private fun initializeTeams() {
        if (!::arenaWorld.isInitialized) {
            println("[LukeVanilla] 오류: 팀 초기화 시 월드가 아직 로드되지 않았습니다.")
            return
        }
        teams.add(TeamData("빨강팀", ChatColor.RED, Location(arenaWorld, 0.0, 5.0, 58.0)))
        teams.add(TeamData("주황팀", ChatColor.GOLD, Location(arenaWorld, 13.0, 5.0, 64.0)))
        teams.add(TeamData("흰색팀", ChatColor.WHITE, Location(arenaWorld, 19.0, 5.0, 77.0)))
        teams.add(TeamData("노랑팀", ChatColor.YELLOW, Location(arenaWorld, 13.0, 5.0, 90.0)))
        teams.add(TeamData("검정팀", ChatColor.BLACK, Location(arenaWorld, 0.0, 5.0, 96.0)))
        teams.add(TeamData("연두팀", ChatColor.GREEN, Location(arenaWorld, -13.0, 5.0, 90.0)))
        teams.add(TeamData("갈색팀", ChatColor.DARK_RED, Location(arenaWorld, -19.0, 5.0, 77.0)))
        teams.add(TeamData("파란팀", ChatColor.BLUE, Location(arenaWorld, -13.0, 5.0, 64.0)))
        println("[LukeVanilla] ${teams.size}개의 팀 정보 및 스폰 위치가 초기화되었습니다.")
    }

    private fun initializeArena(worldName: String) {
        arenaWorld = Bukkit.getWorld(worldName)
            ?: throw IllegalStateException("눈 미니게임 월드 '$worldName'을 찾을 수 없거나 로드되지 않았습니다!")

        val point1 = Location(arenaWorld, 21.0, 12.0, 56.0)
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
        WAITING, COUNTING_DOWN, PAUSED, IN_GAME, FINISHED, RESETTING
    }

    private fun isInsideArena(location: Location): Boolean {
        if (location.world != arenaWorld) return false
        return location.x >= normalizedCorner1.x && location.x <= normalizedCorner2.x &&
               location.y >= normalizedCorner1.y && location.y <= normalizedCorner2.y &&
               location.z >= normalizedCorner1.z && location.z <= normalizedCorner2.z
    }

    private fun addPlayer(player: Player) {
        when (gameState) {
            GameState.WAITING -> {
                if (waitingPlayers.size >= maxPlayers) {
                    player.sendMessage("${ChatColor.RED}게임 시작 대기열이 가득 찼습니다.")
                    return
                }
            }
            GameState.IN_GAME, GameState.FINISHED -> {
                player.sendMessage("${ChatColor.RED}게임이 이미 진행 중이거나 종료되었습니다.")
                return
            }
            GameState.RESETTING -> {
                player.sendMessage("${ChatColor.RED}게임이 재설정 중입니다. 잠시 후 다시 시도해주세요.")
                return
            }
            else -> {
                player.sendMessage("${ChatColor.RED}현재 게임에 참여할 수 없는 상태입니다 (Unknown State: $gameState).")
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
            playerTeams.remove(player.uniqueId)
            if (spectatingPlayers.contains(player.uniqueId)) {
                spectatingPlayers.remove(player.uniqueId)
                player.gameMode = GameMode.SURVIVAL
                // 필요시 로비 위치로 텔레포트
                // player.teleport(lobbyLocation)
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
            GameState.IN_GAME -> {
                // 게임 진행 중 주기적 체크 (필요하다면)
            }
            GameState.FINISHED -> {
                // 게임 종료 후 상태
            }
            GameState.RESETTING -> {
                // 리셋 중 상태
            }
            else -> {
                println("[LukeVanilla] checkPlayerCountAndManageCountdown: 예상치 못한 게임 상태: $gameState")
            }
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

        val participatingPlayers = waitingPlayers.mapNotNull { Bukkit.getPlayer(it) }.shuffled()
        playerTeams.clear()
        val holdingLocation = Location(arenaWorld, 0.0, 5.0, 54.0) // 초과 인원 대기 위치

        participatingPlayers.forEachIndexed { index, player ->
            if (index < teams.size) {
                val assignedTeam = teams[index]
                playerTeams[player.uniqueId] = assignedTeam

                playerInventories[player.uniqueId] = player.inventory.contents.clone()
                player.inventory.clear()

                player.inventory.addItem(ItemStack(Material.IRON_SHOVEL, 1))
                player.inventory.addItem(ItemStack(Material.COOKED_BEEF, 64))

                player.teleport(assignedTeam.spawn)
                player.sendMessage("${assignedTeam.color}${assignedTeam.name}${ChatColor.WHITE}으로 배정되었습니다!")
                player.sendMessage("${ChatColor.GREEN}인벤토리를 저장하고 게임 아이템을 지급했습니다!")
            } else {
                // 팀 정원 초과 시
                println("[LukeVanilla] 경고: 플레이어 ${player.name}에게 배정할 팀이 부족하여 대기 위치로 이동시킵니다.")
                player.teleport(holdingLocation) // 지정된 대기 위치로 텔레포트
                player.sendMessage("${ChatColor.YELLOW}자리가 부족하여 이번 게임에는 참여할 수 없습니다.")
                // 이 플레이어에게는 인벤토리 저장/초기화, 아이템 지급, 팀 배정 안 함
            }
        }

        // 게임 시작 카운트다운 시작
        startPregameCountdown()
    }

    private fun resetGame(resetMessage: String) {
        println("[LukeVanilla] 게임 상태를 초기화합니다. 이유: ${resetMessage}")
        gameState = GameState.RESETTING // 리셋 중 상태 추가 (동시 접근 방지 목적)

        // 1. 아레나 정리
        // 드롭된 아이템 제거
        arenaWorld.entities.filterIsInstance<Item>().forEach { item: Item ->
            if (isInsideArena(item.location)) { // item.location 사용 가능
                item.remove() // 엔티티 자체의 remove() 메소드 사용
            }
        }
        println("[LukeVanilla] Arena 내 드롭된 아이템 제거 완료.")

        // 눈 블록 다시 채우기
        println("[LukeVanilla] Arena 눈 블록 재생성 시작...")
        refillSnowArea(arenaWorld, 4, 58, -4, 96)
        refillSnowArea(arenaWorld, -5, 95, -7, 59)
        refillSnowArea(arenaWorld, -8, 60, -9, 94)
        refillSnowArea(arenaWorld, -10, 93, -11, 61)
        refillSnowArea(arenaWorld, -12, 62, -12, 92)
        refillSnowArea(arenaWorld, -13, 91, -13, 63)
        refillSnowArea(arenaWorld, -14, 64, -14, 90)
        refillSnowArea(arenaWorld, -15, 89, -15, 65)
        refillSnowArea(arenaWorld, -16, 66, -16, 88)
        refillSnowArea(arenaWorld, -17, 86, -17, 68)
        refillSnowArea(arenaWorld, -18, 70, -18, 84)
        refillSnowArea(arenaWorld, -19, 81, -19, 73)
        refillSnowArea(arenaWorld, 5, 59, 7, 95)
        refillSnowArea(arenaWorld, 8, 94, 9, 60)
        refillSnowArea(arenaWorld, 10, 61, 11, 93)
        refillSnowArea(arenaWorld, 12, 92, 12, 62)
        refillSnowArea(arenaWorld, 13, 63, 13, 91)
        refillSnowArea(arenaWorld, 14, 90, 14, 64)
        refillSnowArea(arenaWorld, 15, 65, 15, 89)
        refillSnowArea(arenaWorld, 16, 88, 16, 66)
        refillSnowArea(arenaWorld, 17, 68, 17, 86)
        refillSnowArea(arenaWorld, 18, 84, 18, 70)
        refillSnowArea(arenaWorld, 19, 73, 19, 81)
        println("[LukeVanilla] Arena 눈 블록 재생성 완료.")

        // 2. 플레이어 상태 초기화
        val holdingLocation = Location(arenaWorld, 0.0, 5.0, 54.0) // 대기 위치
        val allPlayersInGame = (waitingPlayers + spectatingPlayers).toSet() // 중복 제거 및 처리 중 목록 변경 방지

        allPlayersInGame.mapNotNull { Bukkit.getPlayer(it) }.forEach { player ->
            // 인벤토리 복원 (게임 아이템 자동 제거됨)
            playerInventories[player.uniqueId]?.let {
                player.inventory.contents = it
                playerInventories.remove(player.uniqueId)
            } ?: player.inventory.clear()

            // 게임 모드 서바이벌로 변경
            player.gameMode = GameMode.SURVIVAL

            // 기본 상태로 리셋
            player.walkSpeed = 0.2f
            player.flySpeed = 0.1f
            player.isFlying = false
            player.allowFlight = false
            player.foodLevel = 20 // 배고픔 채우기
            // L420 오류 해결 최종 시도: Attribute의 전체 경로를 명시적으로 사용합니다!
            player.health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            player.fireTicks = 0 // 불 끄기
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) } // 포션 효과 제거

            // 대기 위치로 텔레포트
            player.teleport(holdingLocation)
            player.sendMessage(resetMessage) // 리셋 사유 메시지 전송
        }

        // 3. 게임 데이터 초기화
        playerInventories.clear() // 남은 인벤토리 데이터 삭제 (에러 대비)
        playerTeams.clear()
        spectatingPlayers.clear()
        gameMovementAllowed = false
        gameStartCountdownTask?.cancel()
        gameStartCountdownTask = null

        waitingPlayers.clear()
        gameState = GameState.WAITING
        println("[LukeVanilla] 게임 상태 초기화 완료. 대기 상태로 전환.")
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

        // 게임 중인 플레이어 이동 제한 (좌표 이동 시) + 시작 카운트다운 중 이동 제한
        if (gameState == GameState.IN_GAME && waitingPlayers.contains(player.uniqueId) && !gameMovementAllowed) {
            event.isCancelled = true
            return
        }

        // 관전자가 영역 밖으로 나가는 것 방지
        if (spectatingPlayers.contains(player.uniqueId) && !isInsideArena(to)) {
            event.isCancelled = true
            player.sendMessage("${ChatColor.RED}게임 영역 밖으로 나갈 수 없습니다.")
            return
        }

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
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        val location = block.location

        // 게임 영역 내부인지 확인
        if (isInsideArena(location)) {
            // 게임 중 상태인지 확인
            if (gameState == GameState.IN_GAME) {
                // 게임 참가자인지 확인
                if (waitingPlayers.contains(player.uniqueId)) {
                    // 눈 블록인지 확인하고, 이동이 허용된 상태인지 확인
                    if (block.type == Material.SNOW_BLOCK && gameMovementAllowed) {
                        // 게임 참가자가 이동 가능 상태에서 눈 블록을 부수는 것은 허용
                        return
                    } else {
                        // 다른 블록이거나 아직 이동 불가 상태면 부술 수 없음
                        event.isCancelled = true
                        if (!gameMovementAllowed) {
                            player.sendMessage("${ChatColor.RED}게임 시작 전에는 블록을 부술 수 없습니다.")
                        } else {
                            player.sendMessage("${ChatColor.RED}게임 중에는 눈 블록 외에는 부술 수 없습니다.")
                        }
                    }
                } else {
                    // 게임 참가자가 아니면 부술 수 없음
                    event.isCancelled = true
                }
            } else {
                // 게임 중이 아니면 영역 내 어떤 블록도 부술 수 없음
                event.isCancelled = true
                if (player.isOp) { // OP에게는 안내 메시지 표시 (선택 사항)
                    player.sendMessage("${ChatColor.YELLOW}게임 중이 아닐 때는 이 영역의 블록을 부술 수 없습니다.")
                }
            }
        }
        // 게임 영역 외부에서는 기본 동작 허용
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        // 게임 참여자가 게임 중에 죽었는지 확인
        if (gameState == GameState.IN_GAME && waitingPlayers.contains(player.uniqueId)) {
            println("[LukeVanilla] 게임 참가자 ${player.name}이(가) 사망했습니다.")

            event.drops.clear() // 아이템 드롭 방지
            event.keepInventory = true // 인벤토리 유지 (어차피 spectator 모드 되면 안 보임)

            // 플레이어를 관전자로 전환
            waitingPlayers.remove(player.uniqueId)
            spectatingPlayers.add(player.uniqueId)
            playerTeams.remove(player.uniqueId)

            // 즉시 리스폰 요청 (리스폰 위치는 onPlayerRespawn에서 처리)
            // 약간의 딜레이 후 spectator 모드 변경 및 메시지 전송
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (player.isOnline) { // 플레이어가 온라인 상태인지 확인
                    player.gameMode = GameMode.SPECTATOR
                    player.sendMessage("${ChatColor.RED}탈락했습니다! 이제 관전 모드입니다.")
                    broadcastGameMessage("${ChatColor.YELLOW}${player.name}님이 탈락했습니다! 남은 인원: ${waitingPlayers.size}명")
                    checkWinCondition() // 승리 조건 확인
                }
            }, 1L) // 1틱 딜레이
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        // 관전자로 지정된 플레이어인지 확인
        if (spectatingPlayers.contains(player.uniqueId)) {
            // 리스폰 위치를 게임 중앙 상공 같은 관전하기 좋은 위치로 설정
            val spectateLocation = Location(arenaWorld, 0.0, 15.0, 77.0) // 예시 좌표, 조정 필요
            event.respawnLocation = spectateLocation
            println("[LukeVanilla] 관전자 ${player.name}을(를) 관전 위치로 리스폰시킵니다.")
            // GameMode 설정은 Death 이벤트 후 딜레이로 처리됨
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        // 관전자가 다른 엔티티를 관전하려고 할 때 (보통 우클릭)
        if (event.cause == PlayerTeleportEvent.TeleportCause.SPECTATE && spectatingPlayers.contains(player.uniqueId)) {
            val targetEntity = player.spectatorTarget
            if (targetEntity is Player) {
                // 타겟이 게임 참여자가 아니면 텔레포트 취소
                if (!waitingPlayers.contains(targetEntity.uniqueId)) {
                    event.isCancelled = true
                    player.sendMessage("${ChatColor.RED}게임에 참여 중인 플레이어만 관전할 수 있습니다.")
                }
            } else {
                // 플레이어가 아닌 대상 관전 시도 시 (선택적 처리)
                // event.isCancelled = true
                // player.sendMessage("${ChatColor.RED}플레이어만 관전할 수 있습니다.")
            }
        }
        // 다른 원인(COMMAND, PLUGIN 등)의 텔레포트는 영향을 받지 않음
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (waitingPlayers.contains(player.uniqueId)) {
            println("[LukeVanilla] 플레이어 ${player.name}이(가) 대기 중 서버를 나갔습니다.")
            playerTeams.remove(player.uniqueId)
            removePlayer(player)
        }
        if (spectatingPlayers.contains(player.uniqueId)) {
            println("[LukeVanilla] 관전자 ${player.name}이(가) 서버를 나갔습니다.")
            spectatingPlayers.remove(player.uniqueId)
        }
    }

    private fun broadcastGameMessage(message: String) {
        (waitingPlayers + spectatingPlayers).mapNotNull { Bukkit.getPlayer(it) }.forEach { it.sendMessage(message) }
    }

    private fun startPregameCountdown() {
        gameMovementAllowed = false
        var remainingSeconds = 4
        val subtitle = "${ChatColor.YELLOW}다른 플레이어를 탈락시키고 최후의 1인이 되세요!"

        gameStartCountdownTask = object : BukkitRunnable() {
            override fun run() {
                if (gameState != GameState.IN_GAME) {
                    cancel()
                    return
                }

                val currentPlayers = waitingPlayers.mapNotNull { Bukkit.getPlayer(it) }
                if (currentPlayers.isEmpty()) {
                    cancel()
                    resetGame("게임 시작 카운트다운 중 플레이어가 모두 나갔습니다.")
                    return
                }

                when (remainingSeconds) {
                    4 -> currentPlayers.forEach { it.sendTitle("${ChatColor.GREEN}3", "", 10, 20, 10) }
                    3 -> currentPlayers.forEach { it.sendTitle("${ChatColor.GREEN}2", "", 10, 20, 10) }
                    2 -> currentPlayers.forEach { it.sendTitle("${ChatColor.GREEN}1", "", 10, 20, 10) }
                    1 -> {
                        currentPlayers.forEach { it.sendTitle("${ChatColor.GREEN}게임 시작!", subtitle, 10, 40, 10) }
                        gameMovementAllowed = true
                        broadcastGameMessage("${ChatColor.GREEN}이동이 허용되었습니다! 게임을 시작하세요!")
                        println("[LukeVanilla] 게임 시작 카운트다운 종료. 이동 허용.")
                        cancel()
                    }
                }
                remainingSeconds--
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun checkWinCondition() {
        if (gameState == GameState.IN_GAME && waitingPlayers.size <= 1) {
            val winner = if (waitingPlayers.isNotEmpty()) Bukkit.getPlayer(waitingPlayers.first()) else null
            val winnerMessage = winner?.let { "${ChatColor.GOLD}${it.name}님이 최종 승리했습니다!" } ?: "${ChatColor.YELLOW}승자 없이 게임이 종료되었습니다."
            println("[LukeVanilla] 게임 종료. 승자: ${winner?.name ?: "없음"}")
            // 모든 참가자 + 관전자에게 승리 메시지 전송
            (waitingPlayers + spectatingPlayers).mapNotNull{ Bukkit.getPlayer(it)}.forEach { it.sendMessage(winnerMessage) }
            resetGame("승자가 결정되어 게임을 초기화합니다.")
        }
    }

    // 지정된 영역을 눈 블록으로 채우는 헬퍼 함수
    private fun refillSnowArea(world: World, x1: Int, z1: Int, x2: Int, z2: Int) {
        val y = 4 // 눈 블록 높이
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val minZ = minOf(z1, z2)
        val maxZ = maxOf(z1, z2)
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                world.getBlockAt(x, y, z).type = Material.SNOW_BLOCK
            }
        }
    }
}
package com.lukehemmin.lukeVanlia.lobby

import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class SnowMinigame(private val plugin: JavaPlugin) : Listener {
    private val minX = -11.0
    private val maxX = 8.0
    private val minY = 5.0
    private val maxY = 20.0
    private val minZ = 64.0
    private val maxZ = 84.0
    private val playersInRegion = mutableSetOf<Player>()
    private var gameStarted = false
    private var countdownTaskId: Int? = null
    private val originalPlayers = mutableSetOf<Player>()
    private var countdownActive = false

    private val teamLocations = listOf(
        Location(Bukkit.getWorld("world"), 7.5, 11.0, 65.5, 90f, 0f),  // 빨강
        Location(Bukkit.getWorld("world"), 7.5, 11.0, 83.5, 90f, 0f),  // 노랑
        Location(Bukkit.getWorld("world"), -9.5, 11.0, 83.5, -90f, 0f), // 녹색
        Location(Bukkit.getWorld("world"), -9.5, 11.0, 65.5, -90f, 0f), // 보라
        Location(Bukkit.getWorld("world"), 7.5, 11.0, 74.5, 90f, 0f),  // 주황
        Location(Bukkit.getWorld("world"), -9.5, 11.0, 74.5, -90f, 0f) // 파랑
    )

    private fun startGame() {
        gameStarted = true
        originalPlayers.clear()
        originalPlayers.addAll(playersInRegion) // 게임 시작 시 참여자 목록 저장

        // 게임에 참여한 사람만 서바이벌 모드로 변경
        for (player in playersInRegion) {
            player.gameMode = GameMode.SURVIVAL
        }

        assignTeams()

        // 지정된 좌표 범위 내의 모든 블럭을 눈블럭으로 채우기
        fillRegionWithSnow(7, 10, 65, -10, 9, 83)

        // 지정된 좌표 범위 내의 모든 블럭을 유리로 채우기
        fillRegionWithGlass(5, 13, 64, 1, 11, 64)

        countdownActive = true // 카운트다운 시작

        // 3, 2, 1 카운트 타이틀 표시
        for (i in 3 downTo 1) {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                for (player in playersInRegion) {
                    player.sendTitle("$i", "초 후 게임이 시작됩니다.", 10, 20, 10)
                }
            }, (3 - i) * 20L) // 1초 간격으로 타이틀 표시
        }

        // 게임 시작 메시지
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            Bukkit.broadcastMessage("게임이 시작되었습니다!")
            countdownActive = false // 카운트다운 종료
        }, 60L) // 3초 후 게임 시작 메시지
    }

    private fun fillRegionWithGlass(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
        val world = Bukkit.getWorld("world")
        if (world != null) {
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxZ = maxOf(z1, z2)

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val block = world.getBlockAt(x, y, z)
                        block.type = Material.GLASS
                    }
                }
            }
        }
    }

    fun stopGame() {
        gameStarted = false
        resetPlayers() // 게임 종료 시 플레이어 초기화

        // 모든 플레이어의 인벤토리 비우기
        for (player in originalPlayers) {
            player.inventory.clear()
        }

        // 지정된 좌표 범위 내의 모든 블럭을 눈블럭으로 채우기
        fillRegionWithSnow(7, 10, 65, -10, 9, 83)

        // 바닥을 제외한 블럭 청소
        clearRegionExceptFloor(7, 10, 65, -10, 19, 83)

        // 지정된 영역 내의 모든 드랍된 아이템 제거
        clearDroppedItemsInRegion()

        // 지정된 좌표 범위 내의 모든 유리 블럭 제거
        clearGlassInRegion(5, 13, 64, 1, 11, 64)
    }

    private fun clearDroppedItemsInRegion() {
        val world = Bukkit.getWorld("world")
        if (world != null) {
            val entities = world.entities
            for (entity in entities) {
                if (entity is Item) {
                    val loc = entity.location
                    val inRegion = loc.x in minX..maxX && loc.y in minY..maxY && loc.z in minZ..maxZ
                    if (inRegion) {
                        entity.remove()
                    }
                }
            }
        }
    }

    private fun clearGlassInRegion(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
        val world = Bukkit.getWorld("world")
        if (world != null) {
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxZ = maxOf(z1, z2)

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val block = world.getBlockAt(x, y, z)
                        if (block.type == Material.GLASS) {
                            block.type = Material.AIR
                        }
                    }
                }
            }
        }
    }

    private fun clearRegionExceptFloor(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
        val world = Bukkit.getWorld("world")
        if (world != null) {
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxZ = maxOf(z1, z2)

            for (x in minX..maxX) {
                for (y in (minY + 1)..maxY) {
                    for (z in minZ..maxZ) {
                        val block = world.getBlockAt(x, y, z)
                        block.type = Material.AIR
                    }
                }
            }
        }
    }

    private fun startCountdown() {
        Bukkit.broadcastMessage("게임이 곧 시작됩니다! 15초 후에 시작됩니다.")
        countdownTaskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (playersInRegion.size in 4..6) {
                startGame()
            } else {
                Bukkit.broadcastMessage("게임 시작에 필요한 인원이 부족합니다.")
            }
        }, 300L).taskId // 30 seconds countdown (20 ticks per second)600L
    }

    private fun cancelCountdown() {
        countdownTaskId?.let { Bukkit.getScheduler().cancelTask(it) }
        countdownTaskId = null
        countdownActive = false
        Bukkit.broadcastMessage("카운트다운이 취소되었습니다. 인원이 부족하거나 초과합니다.")
    }

    private fun assignTeams() {
        val shuffledPlayers = playersInRegion.shuffled()
        for ((index, player) in shuffledPlayers.withIndex()) {
            val teamLocation = teamLocations.getOrNull(index)
            teamLocation?.let {
                player.teleport(it)
                val color = getColor(index)
                player.setDisplayName("${color}${player.name}")
                player.setPlayerListName("${color}${player.name}")
                player.setCustomName("${color}${player.name}") // 머리 위 닉네임 색상 변경
                player.isCustomNameVisible = true // 머리 위 닉네임 보이도록 설정
                Bukkit.broadcastMessage("${player.name} 님이 ${getColorName(index)} 팀에 배정되었습니다.")
            }
        }

        // 모든 플레이어의 인벤토리 비우기 및 아이템 지급
        for (player in playersInRegion) {
            player.inventory.clear()

            // 철삽과 스테이크 지급
            val ironShovel = ItemStack(Material.IRON_SHOVEL)
            val meta = ironShovel.itemMeta
            meta.addEnchant(Enchantment.UNBREAKING, 5, true)
            ironShovel.itemMeta = meta
            player.inventory.addItem(ironShovel)
            player.inventory.addItem(ItemStack(Material.COOKED_BEEF, 64))
        }
    }

    private fun getColor(index: Int): ChatColor {
        return when (index) {
            0 -> ChatColor.RED
            1 -> ChatColor.YELLOW
            2 -> ChatColor.GREEN
            3 -> ChatColor.LIGHT_PURPLE
            4 -> ChatColor.GOLD
            5 -> ChatColor.BLUE
            else -> ChatColor.WHITE
        }
    }

    private fun getColorName(index: Int): String {
        return when (index) {
            0 -> "빨강"
            1 -> "노랑"
            2 -> "녹색"
            3 -> "보라"
            4 -> "주황"
            5 -> "파랑"
            else -> "알 수 없음"
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val loc = player.location
        val inRegion = loc.x in minX..maxX && loc.y in minY..maxY && loc.z in minZ..maxZ

        if (countdownActive && playersInRegion.contains(player)) {
            event.isCancelled = true // 카운트다운 중일 때 게임 참여자만 움직임 취소
            return
        }

        if (!gameStarted) {
            if (inRegion && !playersInRegion.contains(player)) {
                playersInRegion.add(player)
                Bukkit.broadcastMessage("${player.name} 님이 게임에 참가했습니다. (${playersInRegion.size} / 4 - 6)")
                if (playersInRegion.size == 4) {
                    startCountdown()
                } else if (playersInRegion.size >= 7) {
                    cancelCountdown()
                }
            } else if (!inRegion && playersInRegion.contains(player)) {
                playersInRegion.remove(player)
                Bukkit.broadcastMessage("${player.name} 님이 게임에서 나갔습니다. (${playersInRegion.size} / 4 - 6)")
                if (playersInRegion.size < 4 && countdownTaskId != null) {
                    cancelCountdown()
                }
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        if (playersInRegion.contains(player)) {
            player.gameMode = GameMode.SPECTATOR
            playersInRegion.remove(player)
            Bukkit.broadcastMessage("${player.name} 님이 사망하여 관전자 모드로 전환되었습니다.")
            checkForWinner()
        }
    }

    @EventHandler
    fun onPlayerDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Player && event.cause == EntityDamageEvent.DamageCause.LAVA) {
            if (playersInRegion.contains(entity)) {
                entity.fireTicks = 0 // 불 끄기
                entity.gameMode = GameMode.SPECTATOR
                playersInRegion.remove(entity)
                Bukkit.broadcastMessage("${entity.name} 님이 용암에 빠져 관전자 모드로 전환되었습니다.")
                checkForWinner()
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        val loc = block.location
        val inRegion = loc.x in minX..maxX && loc.y in minY..maxY && loc.z in minZ..maxZ

        if (inRegion && gameStarted == true) {
            if (block.type == Material.SNOW_BLOCK) {
                return
            } else {
                event.isCancelled = true
                player.sendMessage("눈 블럭 외의 다른 블럭은 부술 수 없습니다.")
            }
        } else if (inRegion && gameStarted == false) {
            event.isCancelled = true
            player.sendMessage("게임이 시작되지 않았습니다. 블럭을 부술 수 없습니다.")
        } else if (inRegion && player.isOp) {
            return
        } else if (!inRegion) {
            return
        }

//        // 게임이 시작하지 않은 경우 inRegion 공간 안의 블럭 파괴 이벤트 취소
//        if (!gameStarted && inRegion) {
//            event.isCancelled = true
//            player.sendMessage("게임이 시작되지 않았습니다. 블럭을 부술 수 없습니다.")
//            return
//        }
//
//        // 게임이 시작된 경우 눈 블럭이 부서질 수 있도록 조건 수정
//        if (gameStarted && inRegion && block.type == Material.SNOW_BLOCK) {
//            return // 눈 블럭은 부서질 수 있음
//        }
//
//        // inRegion 공간 안에 있고 눈 블럭이 아닌 경우 이벤트 취소
//        if (inRegion && block.type != Material.SNOW_BLOCK) {
//            event.isCancelled = true
//            player.sendMessage("눈 블럭 외의 다른 블럭은 부술 수 없습니다.")
//        }
    }

    private fun checkForWinner() {
        if (playersInRegion.size == 1) {
            val winner = playersInRegion.first()
            Bukkit.broadcastMessage("${winner.name} 님이 게임에서 승리하였습니다!")
            stopGame()
            resetPlayers() // 게임 종료 후 플레이어 초기화
        }
        // 모든 플레이어의 불 끄기
        for (player in originalPlayers) {
            player.fireTicks = 0
        }
    }

    private fun resetPlayers() {
        val resetLocation = Location(Bukkit.getWorld("world"), 3.0, 11.0, 61.0)
        for (player in originalPlayers) {
            player.setDisplayName(player.name) // 닉네임 색상 원래대로
            player.setPlayerListName(player.name) // 플레이어 리스트 닉네임 색상 원래대로
            player.setCustomName(player.name) // 머리 위 닉네임 색상 원래대로
            player.isCustomNameVisible = false // 머리 위 닉네임 숨기기
            player.teleport(resetLocation) // 특정 위치로 이동
            player.gameMode = GameMode.SURVIVAL // 서바이벌 모드로 변경
            player.inventory.clear() // 인벤토리 초기화
        }
        originalPlayers.clear() // 원래 플레이어 목록 초기화
        playersInRegion.clear() // 게임 참여자 목록 초기화
    }

    @EventHandler
    fun onPlayerDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val entity = event.entity

        if (entity is Player) {
            val player = entity
            val loc = player.location
            val inRegion = loc.x in minX..maxX && loc.y in minY..maxY && loc.z in minZ..maxZ

            if (inRegion) {
                if (damager is Player) {
                    // 플레이어가 눈덩이로 공격받는 경우를 제외한 모든 공격 차단
                    if (event.cause != EntityDamageEvent.DamageCause.PROJECTILE || (damager.inventory.itemInMainHand.type != Material.SNOWBALL && damager.inventory.itemInOffHand.type != Material.SNOWBALL)) {
                        event.isCancelled = true
                        damager.sendMessage("눈덩이로만 공격할 수 있습니다.")
                    }
                }
            }
        }
    }

    private fun fillRegionWithSnow(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
        val world = Bukkit.getWorld("world")
        if (world != null) {
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxZ = maxOf(z1, z2)

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val block = world.getBlockAt(x, y, z)
                        if (y == minY) {
                            block.type = Material.SNOW_BLOCK
                        } else {
                            block.type = Material.AIR
                        }
                    }
                }
            }
        }
    }
}
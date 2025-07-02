package com.lukehemmin.lukeVanilla.System

import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI
import com.hibiscusmc.hmccosmetics.user.CosmeticUser
import com.hibiscusmc.hmccosmetics.gui.Menu
import com.hibiscusmc.hmccosmetics.config.Wardrobe
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * 특정 위치에서 HMCCosmetics 옷장을 열 수 있는 시스템
 */
class WardrobeLocationSystem(private val plugin: Plugin) : Listener {
    
    // 옷장 위치 설정 (config에서 읽어옴)
    private val wardrobeLocation: Location
    private val activationRadius: Double // 활성화 반경
    
    init {
        // config에서 설정값 읽어오기
        val config = plugin.config
        val x = config.getDouble("wardrobe.location.x", 31.494)
        val y = config.getDouble("wardrobe.location.y", 64.0)
        val z = config.getDouble("wardrobe.location.z", 78.7)
        activationRadius = config.getDouble("wardrobe.radius", 2.0)
        
        wardrobeLocation = Location(null, x, y, z)
        
        plugin.logger.info("[WardrobeLocationSystem] 옷장 위치: ($x, $y, $z), 활성화 반경: $activationRadius")
    }
    
    // 플레이어가 이미 옷장 범위에 있는지 추적 (중복 실행 방지)
    private val playersInRange = mutableSetOf<UUID>()
    
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to ?: return
        
        // 같은 월드가 아니면 무시
        if (to.world != wardrobeLocation.world) return
        
        // 위치가 크게 변하지 않았으면 무시 (성능 최적화)
        if (from.distanceSquared(to) < 0.01) return
        
        val playerUUID = player.uniqueId
        val distanceToWardrobe = to.distance(wardrobeLocation)
        
        // 플레이어가 옷장 범위에 진입했는지 확인
        if (distanceToWardrobe <= activationRadius) {
            // 이미 범위에 있던 플레이어라면 무시
            if (playersInRange.contains(playerUUID)) return
            
            playersInRange.add(playerUUID)
            openWardrobe(player)
        } else {
            // 범위에서 벗어났다면 추적에서 제거
            playersInRange.remove(playerUUID)
        }
    }
    
    /**
     * 플레이어에게 HMCCosmetics 옷장을 열어줍니다
     */
    private fun openWardrobe(player: Player) {
        try {
            plugin.logger.info("옷장 열기 시도: ${player.name}")
            
            // HMCCosmetics API를 통해 CosmeticUser 가져오기
            val cosmeticUser: CosmeticUser? = HMCCosmeticsAPI.getUser(player.uniqueId)
            
            if (cosmeticUser == null) {
                plugin.logger.warning("CosmeticUser를 찾을 수 없습니다: ${player.name}")
                player.sendMessage("§c옷장을 열 수 없습니다. 코스메틱 데이터를 로드하는 중입니다...")
                return
            }
            
            plugin.logger.info("CosmeticUser 찾음: ${player.name}")
            
            // 1. 가장 간단한 방법: UserWardrobeManager를 직접 시작
            if (tryStartWardrobeDirectly(player, cosmeticUser)) {
                return
            }
            
            // 2. 기본 명령어 실행 (백업)
            if (tryExecuteSimpleCommand(player)) {
                return
            }
            
            // 3. Menu API 시도 (백업)
            if (tryOpenMenuWithAPI(player, cosmeticUser)) {
                return
            }
            
            // 4. 모든 방법이 실패한 경우
            player.sendMessage("§c옷장을 여는 중 오류가 발생했습니다. 관리자에게 문의하세요.")
            plugin.logger.warning("HMCCosmetics 옷장을 여는 모든 방법이 실패했습니다.")
            
        } catch (e: Exception) {
            plugin.logger.warning("옷장을 여는 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
            player.sendMessage("§c옷장을 여는 중 오류가 발생했습니다.")
        }
    }

    /**
     * 가장 간단한 방법: UserWardrobeManager를 직접 시작
     */
    private fun tryStartWardrobeDirectly(player: Player, cosmeticUser: CosmeticUser): Boolean {
        return try {
            plugin.logger.info("실제 Wardrobe API를 통한 wardrobe 열기 시도...")
            
            // 1. default wardrobe 가져오기
            val defaultWardrobe: Wardrobe? = WardrobeSettings.getWardrobe("default")
            
            if (defaultWardrobe == null) {
                plugin.logger.warning("'default' wardrobe를 찾을 수 없습니다!")
                return false
            }
            
            plugin.logger.info("'default' wardrobe를 성공적으로 가져왔습니다")
            
            // 2. 권한 확인 (config.yml에 따르면 "hmccosmetics.wardrobe.default")
            val requiredPermission = "hmccosmetics.wardrobe.default"
            if (!player.hasPermission(requiredPermission)) {
                plugin.logger.info("플레이어 ${player.name}가 권한 '$requiredPermission'을 가지고 있지 않습니다")
                // 권한이 없어도 일단 시도해보기 (서버 설정에 따라 다를 수 있음)
            }
            
            // 3. 이미 wardrobe에 있는지 확인
            if (cosmeticUser.isInWardrobe) {
                plugin.logger.info("플레이어가 이미 wardrobe에 있습니다")
                player.sendMessage("§a✨ 옷장이 이미 열려있습니다! ✨")
                return true
            }
            
            // 4. wardrobe 입장 (true = 거리 체크 우회)
            plugin.logger.info("enterWardrobe() 호출...")
            cosmeticUser.enterWardrobe(defaultWardrobe, true)
            
            // 5. 잠깐 기다린 후 상태 확인
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (cosmeticUser.isInWardrobe) {
                    player.sendMessage("§a✨ 실제 옷장 환경이 열렸습니다! ✨")
                    plugin.logger.info("실제 wardrobe API로 성공적으로 wardrobe에 입장했습니다!")
                } else {
                    plugin.logger.warning("enterWardrobe() 호출했지만 wardrobe 상태가 활성화되지 않았습니다")
                }
            }, 5L)
            
            return true
            
        } catch (e: Exception) {
            plugin.logger.warning("실제 Wardrobe API 시도 실패: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 간단한 명령어를 통해 옷장/메뉴를 엽니다
     */
    private fun tryExecuteSimpleCommand(player: Player): Boolean {
        return try {
            plugin.logger.info("간단한 명령어를 통한 옷장 열기를 시도합니다...")
            
            // 가장 간단하고 가능성이 높은 명령어들
            val simpleCommands = listOf(
                "cosmetics",        // 기본 명령어
                "hmccosmetics",     // 기본 명령어
                "cosmetics menu",   // 메뉴 열기
                "hmccosmetics menu" // 메뉴 열기
            )
            
            for (command in simpleCommands) {
                try {
                    plugin.logger.info("명령어 시도: /$command")
                    val result = plugin.server.dispatchCommand(player, command)
                    if (result) {
                        // 성공한 경우 메시지 출력
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            player.sendMessage("§a✨ 옷장이 열렸습니다! ✨")
                        }, 3L)
                        plugin.logger.info("명령어 '/$command'로 옷장을 성공적으로 열었습니다.")
                        return true
                    } else {
                        plugin.logger.info("명령어 '/$command' 실행됨, 하지만 결과는 false")
                    }
                } catch (e: Exception) {
                    plugin.logger.info("명령어 '/$command' 실행 중 오류: ${e.message}")
                    continue
                }
            }
            
            plugin.logger.warning("모든 간단한 명령어 시도가 실패했습니다.")
            false
        } catch (e: Exception) {
            plugin.logger.warning("간단한 명령어를 통한 옷장 열기 실패: ${e.message}")
            false
        }
    }

    /**
     * Menu API를 통해 메뉴를 여는 방법을 시도합니다
     */
    private fun tryOpenMenuWithAPI(player: Player, cosmeticUser: CosmeticUser): Boolean {
        return try {
            plugin.logger.info("Menu API를 통한 메뉴 열기 시도...")
            
            // 가능한 메뉴 이름들 시도
            val menuNames = listOf("default", "main", "wardrobe", "cosmetics", "menu")
            
            for (menuName in menuNames) {
                try {
                    plugin.logger.info("메뉴 찾기 시도: $menuName")
                    val menu: Menu? = HMCCosmeticsAPI.getMenu(menuName)
                    
                    if (menu != null) {
                        plugin.logger.info("메뉴 찾음: $menuName - ${menu.id}")
                        
                        // 메뉴 열기 시도
                        menu.openMenu(cosmeticUser)
                        player.sendMessage("§a✨ 코스메틱 메뉴가 열렸습니다! ✨")
                        plugin.logger.info("Menu API로 메뉴를 성공적으로 열었습니다: $menuName")
                        return true
                    } else {
                        plugin.logger.info("메뉴를 찾을 수 없음: $menuName")
                    }
                } catch (e: Exception) {
                    plugin.logger.info("메뉴 '$menuName' 열기 실패: ${e.message}")
                    continue
                }
            }
            
            plugin.logger.warning("모든 메뉴 시도가 실패했습니다.")
            false
        } catch (e: Exception) {
            plugin.logger.warning("Menu API를 통한 메뉴 열기 실패: ${e.message}")
            false
        }
    }
    
    /**
     * 월드가 로드될 때 옷장 위치의 월드를 설정합니다
     */
    fun setWardrobeWorld(worldName: String) {
        val world = plugin.server.getWorld(worldName)
        if (world != null) {
            wardrobeLocation.world = world
            plugin.logger.info("옷장 위치가 월드 '$worldName'으로 설정되었습니다.")
        } else {
            plugin.logger.warning("월드 '$worldName'을 찾을 수 없습니다.")
        }
    }
    
    /**
     * 플러그인 비활성화 시 정리 작업
     */
    fun cleanup() {
        playersInRange.clear()
    }
} 
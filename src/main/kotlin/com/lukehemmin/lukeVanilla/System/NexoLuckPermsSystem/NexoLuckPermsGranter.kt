package com.lukehemmin.lukeVanilla.System.NexoLuckPermsSystem

import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.data.DataMutateResult
import net.luckperms.api.node.Node
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.plugin.java.JavaPlugin

/**
 * Nexo 커스텀 아이템을 우클릭하면 특정 권한을 지급하는 시스템
 */
class NexoLuckPermsGranter(private val plugin: JavaPlugin) : Listener {
    
    private val luckPerms: LuckPerms by lazy { LuckPermsProvider.get() }
    
    // Nexo 아이템 ID와 지급할 권한의 매핑
    private val itemPermissionMap = mapOf(
        "plny_summer2025cosmetics_duck_floatie" to "cosmetics.plny_summer2025cosmetics_cosmetics_duck_floatie",
        "plny_summer2025cosmetics_surfboard" to "cosmetics.plny_summer2025cosmetics_cosmetics_surfboard",
        "plny_summer2025cosmetics_umbrella" to "cosmetics.plny_summer2025cosmetics_cosmetics_umbrella",
        "plny_little_seagull_set_wings" to "cosmetics.plny_little_seagull_set_cosmetics_wings",
        "plny_little_seagull_set_hat" to "cosmetics.plny_little_seagull_set_cosmetics_hat"
    )
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // 우클릭만 처리
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        
        val player = event.player
        val item = event.item ?: return
        
        // Nexo 아이템인지 확인
        val nexoItemId = NexoItems.idFromItem(item)
        if (nexoItemId == null) {
            return
        }
        
        // 매핑된 권한이 있는지 확인
        val permission = itemPermissionMap[nexoItemId]
        if (permission == null) {
            return
        }
        
        // 이미 권한이 있는지 확인
        if (player.hasPermission(permission)) {
            val itemName = getItemDisplayName(item, nexoItemId)
            player.sendMessage(
                Component.text("이미 ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(itemName).color(NamedTextColor.GOLD))
                    .append(Component.text(" 권한을 소유하고 있습니다!").color(NamedTextColor.YELLOW))
            )
            return
        }
        
        // 권한 지급
        grantPermissionToPlayer(player.uniqueId.toString(), permission) { success ->
            if (success) {
                // 아이템 제거
                if (item.amount <= 1) {
                    player.inventory.setItemInMainHand(null)
                } else {
                    item.amount = item.amount - 1
                    player.inventory.setItemInMainHand(item)
                }
                
                val itemName = getItemDisplayName(item, nexoItemId)
                player.sendMessage(
                    Component.text("권한이 성공적으로 지급되었습니다!")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text("\n아이템: ").color(NamedTextColor.YELLOW))
                        .append(Component.text(itemName).color(NamedTextColor.AQUA))
                )
                
                // 권한 적용을 위해 플레이어 데이터 새로고침
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    player.recalculatePermissions()
                }, 1L)
                
            } else {
                player.sendMessage(
                    Component.text("권한 지급 중 오류가 발생했습니다.")
                        .color(NamedTextColor.RED)
                )
            }
        }
        
        // 이벤트 취소 (아이템 사용 방지)
        event.isCancelled = true
    }
    
    /**
     * 플레이어에게 권한을 지급합니다
     */
    private fun grantPermissionToPlayer(playerUuid: String, permission: String, callback: (Boolean) -> Unit) {
        // 비동기로 권한 처리 - LuckPerms API 5.2 방식
        val uuid = java.util.UUID.fromString(playerUuid)
        
        luckPerms.userManager.loadUser(uuid).thenAccept { user ->
            if (user != null) {
                try {
                    // 권한 노드 생성 (영구 권한)
                    val node = Node.builder(permission).build()
                    
                    // 권한 추가 - LuckPerms 5.2 방식 (DataMutateResult 반환)
                    val dataResult = user.data().add(node)
                    
                    // 권한 추가 결과 확인
                    if (!dataResult.wasSuccessful()) {
                        throw Exception("권한 노드 추가 실패: 이미 존재하거나 실패")
                    }
                    
                    // 변경사항 저장
                    luckPerms.userManager.saveUser(user).thenRun {
                        // 메인 스레드에서 성공 콜백 실행
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            callback(true)
                        })
                    }.exceptionally { saveException ->
                        plugin.logger.warning("권한 저장 중 오류 발생: ${saveException.message}")
                        saveException.printStackTrace()
                        
                        // 메인 스레드에서 실패 콜백 실행
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            callback(false)
                        })
                        null
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("권한 노드 추가 중 오류 발생: ${e.message}")
                    e.printStackTrace()
                    
                    // 메인 스레드에서 실패 콜백 실행
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        callback(false)
                    })
                }
            } else {
                plugin.logger.warning("사용자를 찾을 수 없습니다: $playerUuid")
                
                // 메인 스레드에서 실패 콜백 실행
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(false)
                })
            }
        }.exceptionally { loadException ->
            plugin.logger.warning("사용자 로드 중 오류 발생: ${loadException.message}")
            loadException.printStackTrace()
            
            // 메인 스레드에서 실패 콜백 실행
            plugin.server.scheduler.runTask(plugin, Runnable {
                callback(false)
            })
            null
        }
    }
    
    /**
     * 아이템의 디스플레이 이름 가져오기
     */
    private fun getItemDisplayName(item: org.bukkit.inventory.ItemStack, nexoItemId: String): String {
        // 먼저 ItemStack의 디스플레이 이름 확인
        val meta = item.itemMeta
        if (meta != null && meta.hasDisplayName()) {
            return meta.displayName
        }
        
        // Nexo ItemBuilder에서 이름 가져오기 시도
        val itemBuilder = NexoItems.itemFromId(nexoItemId)
        if (itemBuilder != null) {
            val nexoItem = itemBuilder.build()
            val nexoMeta = nexoItem.itemMeta
            if (nexoMeta != null && nexoMeta.hasDisplayName()) {
                return nexoMeta.displayName
            }
        }
        
        // 마지막으로 Nexo 아이템 ID 반환 (포맷팅)
        return nexoItemId.replace("_", " ").split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
    
    /**
     * 시스템을 등록합니다
     */
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.logger.info("NexoLuckPermsGranter 시스템이 활성화되었습니다.")
        plugin.logger.info("등록된 아이템 개수: ${itemPermissionMap.size}")
        
        // 등록된 아이템 목록 로그
        itemPermissionMap.forEach { (itemId, permission) ->
            plugin.logger.info("  - $itemId -> $permission")
        }
    }
} 
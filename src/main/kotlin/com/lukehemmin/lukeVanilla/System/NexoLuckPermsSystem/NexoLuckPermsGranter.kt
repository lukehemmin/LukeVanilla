package com.lukehemmin.lukeVanilla.System.NexoLuckPermsSystem

import com.nexomc.nexo.api.NexoItems
import net.luckperms.api.LuckPerms
import net.luckperms.api.model.user.User
import net.luckperms.api.node.Node
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class NexoLuckPermsGranter(private val plugin: JavaPlugin) : Listener {

    private val luckPerms: LuckPerms? = plugin.server.servicesManager.load(LuckPerms::class.java)
    
    // 아이템-권한 매핑
    private val itemPermissions = mapOf(
        "plny_summer2025cosmetics_duck_floatie" to "cosmetics.plny_summer2025cosmetics_cosmetics_duck_floatie",
        "plny_summer2025cosmetics_surfboard" to "cosmetics.plny_summer2025cosmetics_cosmetics_surfboard",
        "plny_summer2025cosmetics_umbrella" to "cosmetics.plny_summer2025cosmetics_cosmetics_umbrella",
        "plny_little_seagull_set_wings" to "cosmetics.plny_little_seagull_set_cosmetics_wings",
        "plny_little_seagull_set_hat" to "cosmetics.plny_little_seagull_set_cosmetics_hat"
    )

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // LuckPerms가 없으면 리턴
        if (luckPerms == null) return
        
        // 우클릭만 처리
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = event.item ?: return
        val nexoItemId = NexoItems.idFromItem(item) ?: return
        val permission = itemPermissions[nexoItemId] ?: return

        event.isCancelled = true

        // 이미 권한이 있는지 확인
        val user = luckPerms.userManager.getUser(player.uniqueId) ?: return
        val queryOptions = luckPerms.getPlayerAdapter(Player::class.java).getQueryOptions(player)
        
        if (user.cachedData.getPermissionData(queryOptions).checkPermission(permission).asBoolean()) {
            player.sendMessage("${ChatColor.YELLOW}이미 해당 권한을 보유하고 있습니다!")
            return
        }

        // 아이템 소모
        val heldItem = player.inventory.itemInMainHand
        if (!heldItem.isSimilar(item) || heldItem.amount <= 0) {
            player.sendMessage("${ChatColor.RED}아이템 소모에 실패했습니다!")
            return
        }

        if (heldItem.amount == 1) {
            player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        } else {
            heldItem.amount -= 1
        }

        // 권한 지급
        val node = Node.builder(permission).build()
        
        // 아이템 이름 가져오기
        val itemName = getItemDisplayName(item, nexoItemId)
        
        luckPerms.userManager.modifyUser(player.uniqueId) { user: User ->
            val result = user.data().add(node)
            
            plugin.server.scheduler.runTask(plugin) { _ ->
                if (result.wasSuccessful()) {
                    player.sendMessage("${ChatColor.GREEN}권한이 성공적으로 지급되었습니다!")
                    player.sendMessage("${ChatColor.YELLOW}아이템: ${ChatColor.WHITE}$itemName")
                } else {
                    player.sendMessage("${ChatColor.RED}권한 지급에 실패했습니다!")
                }
            }
        }
    }
    
    /**
     * 아이템의 디스플레이 이름 가져오기
     */
    private fun getItemDisplayName(item: ItemStack, nexoItemId: String): String {
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
} 
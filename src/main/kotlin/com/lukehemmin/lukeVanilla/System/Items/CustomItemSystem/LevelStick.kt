package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.floor

class LevelStick : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        val nexoId = NexoItems.idFromItem(item) ?: return
        if (nexoId != "experience_stick") return

        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK &&
            event.action != Action.LEFT_CLICK_AIR &&
            event.action != Action.LEFT_CLICK_BLOCK) return

        event.isCancelled = true
        convertExperienceToBottles(player)
    }

    private fun convertExperienceToBottles(player: Player) {
        val totalExp = getTotalExperience(player)
        // 경험치병 1개당 평균 7경험치를 주므로
        val bottleCount = floor(totalExp / 7.0).toInt()

        if (bottleCount <= 0) return

        val expBottle = ItemStack(Material.EXPERIENCE_BOTTLE, 1)
        val remainingBottles = giveOrDropItems(player, expBottle, bottleCount)

        player.level = 0
        player.exp = 0f

        player.sendMessage("§a${bottleCount - remainingBottles}개의 경험치 병을 받았습니다.")
    }

    private fun getTotalExperience(player: Player): Int {
        var totalExp = 0
        val level = player.level

        // 레벨별 필요 경험치 계산
        for (i in 0 until level) {
            totalExp += when {
                i < 16 -> 2 * i + 7
                i < 31 -> 5 * i - 38
                else -> 9 * i - 158
            }
        }

        // 현재 레벨의 부분 경험치 추가
        val levelExp = when {
            level < 16 -> 2 * level + 7
            level < 31 -> 5 * level - 38
            else -> 9 * level - 158
        }

        // 현재 진행 중인 레벨의 경험치 추가
        totalExp += (player.exp * levelExp).toInt()

        return totalExp
    }

    private fun giveOrDropItems(player: Player, item: ItemStack, amount: Int): Int {
        var remaining = amount
        val maxStackSize = item.maxStackSize

        while (remaining > 0) {
            val stackAmount = if (remaining > maxStackSize) maxStackSize else remaining
            val stack = item.clone()
            stack.amount = stackAmount

            if (player.inventory.firstEmpty() == -1) {
                player.world.dropItem(player.location, stack)
            } else {
                player.inventory.addItem(stack)
            }

            remaining -= stackAmount
        }

        return remaining
    }
}
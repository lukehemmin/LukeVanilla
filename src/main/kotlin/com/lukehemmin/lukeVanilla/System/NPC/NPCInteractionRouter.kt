package com.lukehemmin.lukeVanilla.System.NPC

import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.concurrent.ConcurrentHashMap

/**
 * 동적 NPC 이벤트 라우터
 *
 * 여러 시스템에서 공용으로 사용하는 NPC 클릭 이벤트를 중앙에서 관리합니다.
 * 각 시스템은 자신이 담당할 NPC ID와 실행할 로직을 이 라우터에 등록(register)합니다.
 *
 * 작동 원리:
 * 1. 이벤트 우선순위를 LOWEST로 설정하여 가장 먼저 이벤트를 수신합니다.
 * 2. 등록된 NPC ID인 경우, 등록된 로직(action)을 실행합니다.
 * 3. 이벤트를 취소(cancel)하여 이후의 다른 리스너들이 중복 실행되는 것을 방지합니다.
 */
class NPCInteractionRouter : Listener {

    // NPC ID -> 실행할 행동(함수) 매핑
    // 동시성 문제를 방지하기 위해 ConcurrentHashMap 사용
    private val actions = ConcurrentHashMap<Int, (Player) -> Unit>()

    /**
     * 특정 NPC에 대한 상호작용 로직을 등록합니다.
     * 이미 등록된 ID라면 새로운 로직으로 덮어씌워집니다.
     *
     * @param npcId NPC의 ID (Citizens)
     * @param action 해당 NPC 클릭 시 실행할 함수
     */
    fun register(npcId: Int, action: (Player) -> Unit) {
        actions[npcId] = action
    }

    /**
     * 특정 NPC에 대한 상호작용 등록을 해제합니다.
     *
     * @param npcId 등록을 해제할 NPC ID
     */
    fun unregister(npcId: Int) {
        actions.remove(npcId)
    }

    /**
     * 모든 등록을 초기화합니다. (플러그인 리로드/종료 시 사용)
     */
    fun clear() {
        actions.clear()
    }

    /**
     * 현재 등록된 NPC가 있는지 확인합니다.
     */
    fun hasRegistration(npcId: Int): Boolean {
        return actions.containsKey(npcId)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val npcId = event.npc.id
        val action = actions[npcId]

        if (action != null) {
            // 등록된 로직 실행
            action(event.clicker)

            // 이벤트를 취소하여 기존의 개별 리스너들이 실행되지 않도록 함
            // (기존 리스너들은 보통 ignoreCancelled = true 또는 isCancelled 체크를 수행함)
            event.isCancelled = true
        }
    }
}

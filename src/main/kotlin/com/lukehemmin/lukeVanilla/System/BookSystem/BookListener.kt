package com.lukehemmin.lukeVanilla.System.BookSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEditBookEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import java.util.logging.Logger

/**
 * 책 작성/수정을 감지하는 이벤트 리스너
 */
class BookListener(
    private val plugin: Main,
    private val bookRepository: BookRepository,
    private val logger: Logger
) : Listener {
    
    private val bookItemManager = BookItemManager(plugin, logger)

    /**
     * 플레이어가 책을 편집할 때 발생하는 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)  
    fun onPlayerEditBook(event: PlayerEditBookEvent) {
        val player = event.player
        val bookMeta = event.newBookMeta
        val item = player.inventory.itemInMainHand // 편집 중인 아이템
        
        try {
            // 책 내용이 비어있으면 저장하지 않음
            if (bookMeta.pages.isEmpty()) {
                return
            }

            // 현재 시즌 정보 가져오기 (설정에서)
            val currentSeason = plugin.config.getString("book_system.current_season", "Season1")
            
            // 마인크래프트 책 정보 생성
            val bookInfo = MinecraftBookInfo(
                title = bookMeta.title ?: "제목 없음",
                pages = bookMeta.pages,
                author = bookMeta.author,
                generation = bookMeta.generation?.ordinal
            )

            // 기존 책 ID 확인 (편집 전 아이템에서)
            val existingBookId = bookItemManager.getBookId(item)
            val existingOwner = bookItemManager.getBookOwner(item)
            val playerUuid = player.uniqueId.toString()

            // 비동기로 데이터베이스에 저장/업데이트
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                try {
                    if (existingBookId != null && existingOwner == playerUuid) {
                        // 기존 책 업데이트
                        val success = bookRepository.updateExistingBook(existingBookId, bookInfo, playerUuid)
                        
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (success) {
                                // 편집된 아이템의 메타데이터에 NBT 태그 업데이트
                                val updatedMeta = bookItemManager.incrementVersionInMeta(bookMeta, existingBookId, playerUuid)
                                event.newBookMeta = updatedMeta
                                
                                // 플레이어 인벤토리의 아이템도 업데이트 (1틱 후)
                                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                    val currentItem = player.inventory.itemInMainHand
                                    if (bookItemManager.isBookItem(currentItem)) {
                                        bookItemManager.incrementVersion(currentItem)
                                    }
                                }, 1L)
                                
                                val newVersion = bookItemManager.getVersionFromMeta(updatedMeta)
                                player.sendMessage("§a[책 시스템] §f책이 업데이트되었습니다! §7(ID: $existingBookId, v$newVersion)")
                                logger.info("[BookSystem] 플레이어 ${player.name}의 책이 업데이트되었습니다. (ID: $existingBookId)")
                            } else {
                                player.sendMessage("§c[책 시스템] §f책 업데이트 중 오류가 발생했습니다.")
                            }
                        })
                    } else {
                        // 새로운 책 저장
                        val bookId = bookRepository.saveBook(bookInfo, playerUuid, currentSeason)
                        
                        if (bookId != null) {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                // 편집된 아이템의 메타데이터에 NBT 태그 추가
                                val updatedMeta = bookItemManager.setBookIdInMeta(bookMeta, bookId, playerUuid)
                                event.newBookMeta = updatedMeta
                                
                                // 플레이어 인벤토리의 아이템도 업데이트 (1틱 후)
                                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                    val currentItem = player.inventory.itemInMainHand
                                    if (bookItemManager.isBookItem(currentItem)) {
                                        bookItemManager.setBookId(currentItem, bookId, playerUuid)
                                    }
                                }, 1L)
                                
                                // 간단한 성공 알림 (자연스럽게)
                                player.sendMessage("§a[책 시스템] §f책이 저장되었습니다! §7(ID: $bookId)")
                                player.sendMessage("§7이제 이 책을 계속 편집하면 자동으로 업데이트됩니다.")
                                
                                logger.info("[BookSystem] 플레이어 ${player.name}의 새 책이 저장되었습니다. (ID: $bookId)")
                            })
                        } else {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                player.sendMessage("§c[책 시스템] §f책 저장 중 오류가 발생했습니다.")
                                logger.warning("[BookSystem] 플레이어 ${player.name}의 책 저장에 실패했습니다.")
                            })
                        }
                    }
                } catch (e: Exception) {
                    logger.severe("[BookSystem] 책 저장/업데이트 중 예외 발생: ${e.message}")
                    e.printStackTrace()
                    
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage("§c[책 시스템] §f책 처리 중 오류가 발생했습니다.")
                    })
                }
            })
            
        } catch (e: Exception) {
            logger.severe("[BookSystem] 책 편집 이벤트 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 플레이어가 아이템을 우클릭할 때 (책 읽기 감지용)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // 우클릭이 아니면 무시
        if (!event.action.name.contains("RIGHT_CLICK")) {
            return
        }

        try {
            when (item.type) {
                Material.WRITTEN_BOOK -> {
                    // 서명된 책을 읽을 때
                    handleBookRead(player, item, true)
                }
                Material.WRITABLE_BOOK -> {
                    // 책과 깃펜을 읽을 때
                    handleBookRead(player, item, false)
                }
                else -> return
            }
        } catch (e: Exception) {
            logger.severe("[BookSystem] 책 읽기 이벤트 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 책 읽기 처리
     */
    private fun handleBookRead(player: Player, item: ItemStack, isSigned: Boolean) {
        val bookMeta = item.itemMeta as? BookMeta ?: return
        
        // 빈 책이면 무시
        if (bookMeta.pages.isEmpty()) {
            return
        }

        // 책 정보 로깅 (선택적)
        if (plugin.config.getBoolean("book_system.log_book_reads", false)) {
            logger.info("[BookSystem] 플레이어 ${player.name}이 책을 읽었습니다: ${bookMeta.title ?: "제목 없음"}")
        }
        
        // 향후 확장: 책 읽기 통계, 추천 시스템 등을 여기에 구현할 수 있음
    }

    /**
     * 책 내용을 안전하게 가져오기
     */
    private fun safeGetBookContent(bookMeta: BookMeta): List<String> {
        return try {
            bookMeta.pages.map { page ->
                // 페이지 내용을 안전하게 정제
                page.replace("\\n", "\n").take(256) // 페이지당 최대 256자 제한
            }
        } catch (e: Exception) {
            logger.warning("[BookSystem] 책 내용 파싱 중 오류: ${e.message}")
            listOf("내용을 읽을 수 없습니다.")
        }
    }

    /**
     * 책 제목을 안전하게 가져오기
     */
    private fun safeGetBookTitle(bookMeta: BookMeta): String {
        return try {
            bookMeta.title?.take(64) ?: "제목 없음" // 제목 최대 64자 제한
        } catch (e: Exception) {
            logger.warning("[BookSystem] 책 제목 파싱 중 오류: ${e.message}")
            "제목 없음"
        }
    }

    /**
     * 플레이어에게 책 저장 완료 알림을 보내는 메소드
     */
    private fun notifyBookSaved(player: Player, bookId: Long, title: String) {
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪              §f책이 저장되었습니다!              §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f제목: §e$title")
        player.sendMessage("§a▪ §f책 ID: §e$bookId")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f명령어:")
        player.sendMessage("§a▪   §e/책 목록 §f- 내 책 목록 보기")
        player.sendMessage("§a▪   §e/책 공개 $bookId §f- 이 책을 공개하기")
        player.sendMessage("§a▪   §e/책 웹사이트 §f- 웹에서 책 관리하기")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
    }

    /**
     * 플레이어에게 책 저장 실패 알림을 보내는 메소드
     */
    private fun notifyBookSaveFailed(player: Player) {
        player.sendMessage("")
        player.sendMessage("§c▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§c▪              §f책 저장에 실패했습니다!            §c▪")
        player.sendMessage("§c▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§c▪")
        player.sendMessage("§c▪ §f잠시 후 다시 시도해주세요.")
        player.sendMessage("§c▪ §f문제가 지속되면 관리자에게 문의해주세요.")
        player.sendMessage("§c▪")
        player.sendMessage("§c▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
    }
}
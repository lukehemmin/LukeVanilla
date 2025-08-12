package com.lukehemmin.lukeVanilla.System.BookSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.logging.Logger

/**
 * 책 시스템 인게임 명령어 처리기
 */
class BookCommand(
    private val plugin: Main,
    private val bookRepository: BookRepository,
    private val sessionManager: BookSessionManager,
    private val logger: Logger
) : CommandExecutor, TabCompleter {

    private val bookItemManager = BookItemManager(plugin, logger)
    private val externalDomain = plugin.config.getString("book_system.external_domain", "localhost:9595") ?: "localhost:9595"
    private val externalProtocol = plugin.config.getString("book_system.external_protocol", "http") ?: "http"

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (args.isEmpty()) {
            sendHelpMessage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "목록", "list" -> handleListCommand(sender, args)
            "공개", "public" -> handlePublicCommand(sender, args)
            "비공개", "private" -> handlePrivateCommand(sender, args)
            "삭제", "delete" -> handleDeleteCommand(sender, args)
            "정보", "info" -> handleInfoCommand(sender, args)
            "웹사이트", "web", "website" -> handleWebsiteCommand(sender)
            "토큰", "token" -> handleTokenCommand(sender)
            "도움말", "help" -> { sendHelpMessage(sender); true }
            "통계", "stats" -> handleStatsCommand(sender)
            "시즌", "season" -> handleSeasonCommand(sender, args)
            else -> { sendHelpMessage(sender); true }
        }
    }

    /**
     * 도움말 메시지 표시
     */
    private fun sendHelpMessage(player: Player) {
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪                §e책 시스템 명령어               §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §e/책 목록 [페이지] §f- 내 책 목록 보기")
        player.sendMessage("§a▪ §e/책 공개 <책ID> §f- 책을 공개로 설정")
        player.sendMessage("§a▪ §e/책 비공개 <책ID> §f- 책을 비공개로 설정")
        player.sendMessage("§a▪ §e/책 삭제 <책ID> §f- 책 삭제")
        player.sendMessage("§a▪ §e/책 정보 <책ID> §f- 책 상세 정보 보기")
        player.sendMessage("§a▪ §e/책 웹사이트 §f- 웹에서 책 관리하기")
        player.sendMessage("§a▪ §e/책 토큰 §f- 웹 인증 토큰 생성")
        player.sendMessage("§a▪ §e/책 통계 §f- 책 시스템 통계 보기")
        player.sendMessage("§a▪ §e/책 시즌 [시즌명] §f- 현재 시즌 확인/변경 (관리자)")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §7💡 팁: 책과 깃펜으로 작성한 내용은 자동 저장됩니다!")
        player.sendMessage("§a▪ §7같은 책을 계속 편집하면 업데이트되고 중복 저장되지 않아요.")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
    }

    /**
     * 책 목록 명령어 처리
     */
    private fun handleListCommand(player: Player, args: Array<out String>): Boolean {
        val page = if (args.size > 1) {
            args[1].toIntOrNull() ?: 1
        } else 1

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val (books, totalCount) = bookRepository.getPlayerBooks(
                    playerUuid = player.uniqueId.toString(),
                    page = page,
                    size = 10
                )

                plugin.server.scheduler.runTask(plugin, Runnable {
                    displayBookList(player, books, page, totalCount)
                })
            } catch (e: Exception) {
                logger.severe("[BookCommand] 책 목록 조회 중 예외 발생: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c[책 시스템] §f책 목록을 불러오는 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }

    /**
     * 책 공개 설정 명령어 처리
     */
    private fun handlePublicCommand(player: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            player.sendMessage("§c[책 시스템] §f사용법: /책 공개 <책ID>")
            return true
        }

        val bookId = args[1].toLongOrNull()
        if (bookId == null) {
            player.sendMessage("§c[책 시스템] §f올바른 책 ID를 입력해주세요.")
            return true
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                if (!bookRepository.isBookOwner(bookId, player.uniqueId.toString())) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage("§c[책 시스템] §f해당 책의 소유자가 아니거나 존재하지 않는 책입니다.")
                    })
                    return@Runnable
                }

                val success = bookRepository.setBookPublic(bookId, true, player.uniqueId.toString())
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage("§a[책 시스템] §f책 ID §e$bookId§f이(가) 공개로 설정되었습니다!")
                        player.sendMessage("§a[책 시스템] §f이제 다른 플레이어들이 웹사이트에서 이 책을 볼 수 있습니다.")
                    } else {
                        player.sendMessage("§c[책 시스템] §f책 공개 설정 중 오류가 발생했습니다.")
                    }
                })
            } catch (e: Exception) {
                logger.severe("[BookCommand] 책 공개 설정 중 예외 발생: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c[책 시스템] §f책 공개 설정 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }

    /**
     * 책 비공개 설정 명령어 처리
     */
    private fun handlePrivateCommand(player: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            player.sendMessage("§c[책 시스템] §f사용법: /책 비공개 <책ID>")
            return true
        }

        val bookId = args[1].toLongOrNull()
        if (bookId == null) {
            player.sendMessage("§c[책 시스템] §f올바른 책 ID를 입력해주세요.")
            return true
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                if (!bookRepository.isBookOwner(bookId, player.uniqueId.toString())) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage("§c[책 시스템] §f해당 책의 소유자가 아니거나 존재하지 않는 책입니다.")
                    })
                    return@Runnable
                }

                val success = bookRepository.setBookPublic(bookId, false, player.uniqueId.toString())
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage("§a[책 시스템] §f책 ID §e$bookId§f이(가) 비공개로 설정되었습니다!")
                    } else {
                        player.sendMessage("§c[책 시스템] §f책 비공개 설정 중 오류가 발생했습니다.")
                    }
                })
            } catch (e: Exception) {
                logger.severe("[BookCommand] 책 비공개 설정 중 예외 발생: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c[책 시스템] §f책 비공개 설정 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }

    /**
     * 책 삭제 명령어 처리
     */
    private fun handleDeleteCommand(player: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            player.sendMessage("§c[책 시스템] §f사용법: /책 삭제 <책ID>")
            return true
        }

        val bookId = args[1].toLongOrNull()
        if (bookId == null) {
            player.sendMessage("§c[책 시스템] §f올바른 책 ID를 입력해주세요.")
            return true
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val success = bookRepository.deleteBook(bookId, player.uniqueId.toString())
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success) {
                        player.sendMessage("§a[책 시스템] §f책 ID §e$bookId§f이(가) 삭제되었습니다.")
                    } else {
                        player.sendMessage("§c[책 시스템] §f해당 책의 소유자가 아니거나 존재하지 않는 책입니다.")
                    }
                })
            } catch (e: Exception) {
                logger.severe("[BookCommand] 책 삭제 중 예외 발생: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c[책 시스템] §f책 삭제 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }

    /**
     * 책 정보 명령어 처리
     */
    private fun handleInfoCommand(player: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            player.sendMessage("§c[책 시스템] §f사용법: /책 정보 <책ID>")
            return true
        }

        val bookId = args[1].toLongOrNull()
        if (bookId == null) {
            player.sendMessage("§c[책 시스템] §f올바른 책 ID를 입력해주세요.")
            return true
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val book = bookRepository.getBook(bookId)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (book != null) {
                        displayBookInfo(player, book)
                    } else {
                        player.sendMessage("§c[책 시스템] §f존재하지 않는 책 ID입니다.")
                    }
                })
            } catch (e: Exception) {
                logger.severe("[BookCommand] 책 정보 조회 중 예외 발생: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c[책 시스템] §f책 정보를 불러오는 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }

    /**
     * 웹사이트 접속 명령어 처리
     */
    private fun handleWebsiteCommand(player: Player): Boolean {
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪              §f책 시스템 웹사이트               §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f웹 주소: §e$externalProtocol://$externalDomain")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f로그인을 위해서는 먼저 §e/책 토큰§f 명령어로")
        player.sendMessage("§a▪ §f인증 토큰을 생성한 후 웹사이트에서 입력하세요.")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
        return true
    }

    /**
     * 토큰 생성 명령어 처리
     */
    private fun handleTokenCommand(player: Player): Boolean {
        try {
            val authCode = sessionManager.generateAuthCode(player.uniqueId.toString(), player.name)
            
            player.sendMessage("")
            player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
            player.sendMessage("§a▪              §f웹 인증 토큰 생성               §a▪")
            player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f인증 코드: §e§l$authCode")
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f이 코드는 §c5분 후 만료§f됩니다.")
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f📱 자동 로그인 링크:")
            
            // 클릭 가능한 자동 로그인 링크 생성
            val autoLoginUrl = "$externalProtocol://$externalDomain?code=$authCode"
            
            // ComponentBuilder를 사용하여 클릭 가능한 링크 생성
            val linkComponent = net.md_5.bungee.api.chat.ComponentBuilder("§a▪")
                .append("  🔗 클릭하여 자동 로그인")
                .color(net.md_5.bungee.api.ChatColor.AQUA)
                .underlined(true)
                .event(net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, 
                    autoLoginUrl
                ))
                .event(net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    arrayOf(net.md_5.bungee.api.chat.TextComponent("클릭하면 브라우저에서 자동으로 로그인됩니다!"))
                ))
                .create()
            
            // 메시지 전송
            player.spigot().sendMessage(*linkComponent)
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f또는 수동으로 코드 입력:")
            player.sendMessage("§a▪ §e$externalProtocol://$externalDomain")
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
            player.sendMessage("")
            
            logger.info("[BookCommand] 플레이어 ${player.name}의 인증 코드 생성: $authCode")
        } catch (e: Exception) {
            logger.severe("[BookCommand] 토큰 생성 중 예외 발생: ${e.message}")
            player.sendMessage("§c[책 시스템] §f인증 토큰 생성 중 오류가 발생했습니다.")
        }
        return true
    }

    /**
     * 통계 명령어 처리
     */
    private fun handleStatsCommand(player: Player): Boolean {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val (books, totalCount) = bookRepository.getPlayerBooks(player.uniqueId.toString(), 1, 1)
                val (publicBooks, publicCount) = bookRepository.getPlayerBooks(
                    player.uniqueId.toString(), 1, 1, publicOnly = true
                )
                val seasonStats = bookRepository.getBookStatsBySeason()
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    displayStats(player, totalCount, publicCount, seasonStats)
                })
            } catch (e: Exception) {
                logger.severe("[BookCommand] 통계 조회 중 예외 발생: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage("§c[책 시스템] §f통계를 불러오는 중 오류가 발생했습니다.")
                })
            }
        })
        return true
    }

    /**
     * 시즌 관리 명령어 처리
     */
    private fun handleSeasonCommand(player: Player, args: Array<out String>): Boolean {
        if (args.size == 1) {
            // 현재 시즌 정보 표시
            val currentSeason = plugin.config.getString("book_system.current_season", "Season1")
            
            player.sendMessage("")
            player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
            player.sendMessage("§a▪              §f현재 시즌 정보               §a▪")
            player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f현재 시즌: §e$currentSeason")
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f새로운 책들은 이 시즌으로 분류됩니다.")
            
            if (player.hasPermission("lukevanilla.admin") || player.isOp) {
                player.sendMessage("§a▪")
                player.sendMessage("§a▪ §f시즌 변경: §e/책 시즌 <새시즌명>")
            }
            
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
            player.sendMessage("")
            
            return true
        }
        
        // 시즌 변경 (관리자만)
        if (!player.hasPermission("lukevanilla.admin") && !player.isOp) {
            player.sendMessage("§c[책 시스템] §f시즌을 변경할 권한이 없습니다.")
            return true
        }
        
        val newSeason = args[1]
        
        // 시즌명 유효성 검사
        if (newSeason.length < 2 || newSeason.length > 20) {
            player.sendMessage("§c[책 시스템] §f시즌명은 2-20자 이내로 입력해주세요.")
            return true
        }
        
        if (!newSeason.matches(Regex("[a-zA-Z0-9가-힣_-]+"))) {
            player.sendMessage("§c[책 시스템] §f시즌명은 한글, 영문, 숫자, _, - 만 사용 가능합니다.")
            return true
        }
        
        val oldSeason = plugin.config.getString("book_system.current_season", "Season1")
        
        // 설정 변경
        plugin.config.set("book_system.current_season", newSeason)
        plugin.saveConfig()
        
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪              §f시즌이 변경되었습니다!              §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f이전 시즌: §7$oldSeason")
        player.sendMessage("§a▪ §f새로운 시즌: §e$newSeason")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f이제 새로 작성되는 책들은 §e$newSeason§f으로")
        player.sendMessage("§a▪ §f분류됩니다.")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
        
        // 서버 전체에 시즌 변경 알림
        plugin.server.broadcastMessage("§a[책 시스템] §f시즌이 §e$newSeason§f으로 변경되었습니다!")
        
        logger.info("[BookCommand] 관리자 ${player.name}이 시즌을 '$oldSeason'에서 '$newSeason'으로 변경했습니다.")
        
        return true
    }

    /**
     * 책 목록 표시
     */
    private fun displayBookList(player: Player, books: List<BookData>, page: Int, totalCount: Long) {
        val totalPages = ((totalCount - 1) / 10 + 1).toInt()
        
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪           §f내 책 목록 (${page}/${totalPages}페이지)          §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        
        if (books.isEmpty()) {
            player.sendMessage("§a▪")
            player.sendMessage("§a▪ §f저장된 책이 없습니다.")
            player.sendMessage("§a▪ §f책과 깃펜으로 책을 작성해보세요!")
            player.sendMessage("§a▪")
        } else {
            books.forEach { book ->
                val statusColor = if (book.isPublic) "§a" else "§7"
                val status = if (book.isPublic) "공개" else "비공개"
                val signedText = if (book.isSigned) " §e[서명됨]" else ""
                
                player.sendMessage("§a▪")
                player.sendMessage("§a▪ §f[§e${book.id}§f] §e${book.title}$signedText")
                player.sendMessage("§a▪ §f상태: $statusColor$status §f| 페이지: §e${book.pageCount}§f | 작성: §7${book.createdAt.substring(0, 10)}")
            }
            player.sendMessage("§a▪")
            
            // 페이지 네비게이션
            if (totalPages > 1) {
                val prevPage = if (page > 1) page - 1 else 1
                val nextPage = if (page < totalPages) page + 1 else totalPages
                player.sendMessage("§a▪ §f이전: §e/책 목록 $prevPage §f| 다음: §e/책 목록 $nextPage")
                player.sendMessage("§a▪")
            }
        }
        
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
    }

    /**
     * 책 상세 정보 표시
     */
    private fun displayBookInfo(player: Player, book: BookData) {
        val statusColor = if (book.isPublic) "§a" else "§7"
        val status = if (book.isPublic) "공개" else "비공개"
        val signedText = if (book.isSigned) "서명됨" else "미서명"
        
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪              §f책 상세 정보 (ID: ${book.id})          §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f제목: §e${book.title}")
        player.sendMessage("§a▪ §f상태: $statusColor$status")
        player.sendMessage("§a▪ §f서명: §e$signedText")
        player.sendMessage("§a▪ §f페이지 수: §e${book.pageCount}")
        player.sendMessage("§a▪ §f시즌: §e${book.season ?: "정보 없음"}")
        player.sendMessage("§a▪ §f작성일: §7${book.createdAt.substring(0, 16).replace("T", " ")}")
        player.sendMessage("§a▪ §f수정일: §7${book.updatedAt.substring(0, 16).replace("T", " ")}")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
    }

    /**
     * 통계 정보 표시
     */
    private fun displayStats(player: Player, totalBooks: Long, publicBooks: Long, seasonStats: Map<String, Int>) {
        player.sendMessage("")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪              §f내 책 시스템 통계               §a▪")
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("§a▪")
        player.sendMessage("§a▪ §f총 작성한 책: §e${totalBooks}권")
        player.sendMessage("§a▪ §f공개한 책: §e${publicBooks}권")
        player.sendMessage("§a▪ §f비공개 책: §e${totalBooks - publicBooks}권")
        player.sendMessage("§a▪")
        
        if (seasonStats.isNotEmpty()) {
            player.sendMessage("§a▪ §f시즌별 통계:")
            seasonStats.forEach { (season, count) ->
                player.sendMessage("§a▪   §e$season§f: §e${count}권")
            }
            player.sendMessage("§a▪")
        }
        
        player.sendMessage("§a▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪")
        player.sendMessage("")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (sender !is Player) return null

        return when (args.size) {
            1 -> {
                val commands = listOf("목록", "공개", "비공개", "삭제", "정보", "웹사이트", "토큰", "통계", "시즌", "도움말")
                commands.filter { it.startsWith(args[0], true) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "공개", "비공개", "삭제", "정보" -> {
                        // 실제로는 플레이어의 책 ID 목록을 가져와서 자동완성할 수 있음
                        listOf("<책ID>")
                    }
                    "시즌", "season" -> {
                        if (sender.hasPermission("lukevanilla.admin") || sender.isOp) {
                            listOf("<새시즌명>", "Season2", "Season3", "Winter2024", "Spring2025")
                        } else null
                    }
                    else -> null
                }
            }
            else -> null
        }
    }
}
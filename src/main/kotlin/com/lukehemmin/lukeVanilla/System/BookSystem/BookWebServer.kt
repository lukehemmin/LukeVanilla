package com.lukehemmin.lukeVanilla.System.BookSystem

import com.lukehemmin.lukeVanilla.Main
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
// import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.logging.Logger
import java.util.concurrent.TimeUnit

/**
 * 책 시스템 웹서버 관리자
 */
class BookWebServer(
    private val plugin: Main,
    private val bookRepository: BookRepository,
    private val sessionManager: BookSessionManager,
    private val logger: Logger
) {

    private var server: NettyApplicationEngine? = null
    private val bookAPI = BookAPI(bookRepository, sessionManager, logger)
    
    // 설정값들
    private val port = plugin.config.getInt("book_system.web_port", 9595)
    private val host = plugin.config.getString("book_system.web_host", "127.0.0.1") ?: "127.0.0.1"
    private val enableCors = plugin.config.getBoolean("book_system.enable_cors", true)
    private val allowedOrigins = plugin.config.getStringList("book_system.allowed_origins").ifEmpty { 
        listOf("http://localhost:9595") 
    }
    private val externalDomain = plugin.config.getString("book_system.external_domain", "localhost:9595") ?: "localhost:9595"
    private val externalProtocol = plugin.config.getString("book_system.external_protocol", "http") ?: "http"
    private val webContentPath = File(plugin.dataFolder, "web").path

    /**
     * 웹서버 시작
     */
    fun start() {
        try {
            // 웹 컨텐츠 폴더 생성
            setupWebContent()
            
            server = embeddedServer(Netty, port = port, host = host) { module() }
                .start(wait = false)
            
            logger.info("[BookWebServer] 웹서버가 $host:$port 에서 시작되었습니다.")
            logger.info("[BookWebServer] 내부 주소: http://$host:$port")
            logger.info("[BookWebServer] 외부 접속 주소: $externalProtocol://$externalDomain")
            
        } catch (e: Exception) {
            logger.severe("[BookWebServer] 웹서버 시작 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 웹서버 중지
     */
    fun stop() {
        try {
            server?.stop(1, 5, TimeUnit.SECONDS)
            logger.info("[BookWebServer] 웹서버가 정상적으로 종료되었습니다.")
        } catch (e: Exception) {
            logger.severe("[BookWebServer] 웹서버 종료 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Ktor 애플리케이션 모듈 설정
     */
    private fun Application.module() {
        // JSON 직렬화 설정
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }

        // CORS 설정
        if (enableCors) {
            install(CORS) {
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.AccessControlAllowOrigin)
                allowCredentials = true
                
                // 허용된 오리진만 설정
                allowedOrigins.forEach { origin ->
                    allowHost(origin.removePrefix("http://").removePrefix("https://"))
                }
            }
        }

        // 기본 헤더 설정
        install(DefaultHeaders) {
            header("X-Engine", "Ktor")
            header("X-Book-System", "LukeVanilla")
        }

        // 로깅 설정 (임시 비활성화)
        // install(CallLogging)

        // 예외 처리 설정
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                logger.severe("[BookWebServer] 예외 발생: ${cause.message}")
                cause.printStackTrace()
                
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<String>(
                        success = false,
                        error = "서버 내부 오류가 발생했습니다."
                    )
                )
            }
            
            status(HttpStatusCode.NotFound) { call, _ ->
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<String>(
                        success = false,
                        error = "요청한 리소스를 찾을 수 없습니다."
                    )
                )
            }
            
            status(HttpStatusCode.Unauthorized) { call, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<String>(
                        success = false,
                        error = "SESSION_EXPIRED",
                        message = "세션이 만료되었습니다. 다시 로그인해주세요."
                    )
                )
            }
        }

        // 인증 설정
        install(Authentication) {
            bearer("book-auth") {
                realm = "Book System"
                authenticate { tokenCredential ->
                    try {
                        val session = sessionManager.validateToken(tokenCredential.token)
                        if (session != null) {
                            BookAPI.BookSessionPrincipal(session)
                        } else {
                            logger.info("[BookWebServer] 유효하지 않은 토큰으로 인증 시도: ${tokenCredential.token.take(10)}...")
                            null
                        }
                    } catch (e: Exception) {
                        logger.warning("[BookWebServer] 토큰 검증 중 예외 발생: ${e.message}")
                        null
                    }
                }
            }
        }

        // 라우팅 설정
        routing {
            // 정적 파일 서빙 (HTML, CSS, JS 등)
            staticFiles("/", File(webContentPath)) {
                default("index.html")
            }

            // API 라우팅
            bookAPI.configureRouting(this)

            // 헬스체크 엔드포인트
            get("/health") {
                call.respond(mapOf(
                    "status" to "ok",
                    "timestamp" to System.currentTimeMillis(),
                    "system" to "BookSystem"
                ))
            }

            // 시스템 정보 엔드포인트 (관리자용)
            get("/admin/info") {
                // 실제로는 관리자 권한 체크 추가 필요
                val sessionStats = sessionManager.getSessionStats()
                val systemInfo = mapOf(
                    "server" to mapOf(
                        "host" to host,
                        "port" to port,
                        "startTime" to System.currentTimeMillis()
                    ),
                    "sessions" to sessionStats,
                    "plugin" to mapOf(
                        "name" to plugin.name,
                        "version" to plugin.description.version
                    )
                )
                call.respond(systemInfo)
            }
        }
    }

    /**
     * 웹 컨텐츠 폴더 및 기본 파일들 설정
     * 매번 새로운 버전으로 자동 업데이트
     */
    private fun setupWebContent() {
        val webDir = File(plugin.dataFolder, "web")
        
        // 기존 웹 폴더가 있으면 삭제하고 새로 생성
        if (webDir.exists()) {
            logger.info("[BookWebServer] 기존 웹 컨텐츠 폴더를 삭제하고 새로 생성합니다...")
            webDir.deleteRecursively()
        }
        
        webDir.mkdirs()

        // 기본 index.html 파일 생성 (항상 최신 버전으로)
        val indexFile = File(webDir, "index.html")
        indexFile.writeText(generateDefaultIndexHtml())

        // 기본 CSS 파일 생성 (항상 최신 버전으로)
        val cssDir = File(webDir, "css")
        cssDir.mkdirs()
        val cssFile = File(cssDir, "style.css")
        cssFile.writeText(generateDefaultCSS())

        // 기본 JavaScript 파일 생성 (항상 최신 버전으로)
        val jsDir = File(webDir, "js")
        jsDir.mkdirs()
        val jsFile = File(jsDir, "app.js")
        jsFile.writeText(generateDefaultJS())

        logger.info("[BookWebServer] 웹 컨텐츠가 최신 버전으로 업데이트되었습니다: ${webDir.absolutePath}")
    }

    /**
     * 기본 HTML 파일 생성 - 새로운 UI 적용
     */
    private fun generateDefaultIndexHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="ko">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>마인크래프트 도서관 - LukeVanilla</title>
            <link rel="stylesheet" href="/css/style.css">
        </head>
        <body>
            <div class="container">
                <!-- 헤더 -->
                <header class="header">
                    <h1>📚 마인크래프트 도서관</h1>
                    <p>플레이어들이 작성한 이야기를 모아보는 곳</p>
                </header>
                
                <!-- 네비게이션 -->
                <nav class="nav">
                    <button id="homeBtn" class="nav-btn active">🏠 홈</button>
                    <button id="publicBooksBtn" class="nav-btn">📖 공개 책</button>
                    <button id="myBooksBtn" class="nav-btn" style="display: none;">📚 내 책</button>
                    <button id="loginBtn" class="nav-btn">🔐 로그인</button>
                    <button id="logoutBtn" class="nav-btn" style="display: none;">👋 로그아웃</button>
                </nav>
                
                <!-- 홈 페이지 -->
                <div id="homePage" class="page active">
                    <!-- 통계 카드 -->
                    <section class="stats">
                        <div class="stat-card books">
                            <span class="icon">📚</span>
                            <div class="number" id="totalBooksCount">-</div>
                            <div class="label">총 책 수</div>
                        </div>
                        <div class="stat-card authors">
                            <span class="icon">👥</span>
                            <div class="number" id="totalAuthorsCount">-</div>
                            <div class="label">작성자 수</div>
                        </div>
                    </section>
                    
                    <!-- 로그인 알림 (로그인하지 않은 경우에만 표시) -->
                    <div id="loginAlert" class="alert" style="display: none;">
                        <div class="alert-title">
                            <span>🔐</span>
                            로그인하여 더 많은 기능을 이용하세요
                        </div>
                        <div class="alert-content">
                            마인크래프트에서 <strong>/책 토큰</strong> 명령어를 사용하여 인증 코드를 받은 후 로그인하세요.
                        </div>
                    </div>
                    
                    <!-- 최근 공개 책 미리보기 -->
                    <section class="recent-books">
                        <h2>🆕 최근 공개된 책들</h2>
                        <div id="recentBooksContainer" class="books-grid">
                            <div class="loading">최근 책 목록을 불러오는 중...</div>
                        </div>
                    </section>
                </div>
                
                <!-- 로그인 페이지 -->
                <div id="loginPage" class="page">
                    <div class="login-container">
                        <h2>🔐 로그인</h2>
                        <p>마인크래프트에서 <code>/책 토큰</code> 명령어를 입력하여 인증 코드를 받으세요.</p>
                        
                        <form id="loginForm">
                            <div class="form-group">
                                <label for="authCode">인증 코드 (6자리 숫자)</label>
                                <input type="text" id="authCode" placeholder="예: 123456" maxlength="6" required>
                            </div>
                            <button type="submit">로그인</button>
                        </form>
                        
                        <div id="loginMessage"></div>
                    </div>
                </div>
                
                <!-- 공개 책 페이지 -->
                <div id="publicBooksPage" class="page">
                    <!-- 검색 -->
                    <section class="search-section">
                        <div class="search-bar">
                            <input type="text" id="searchInput" placeholder="책 제목, 내용, 작성자 검색...">
                            <button class="search-btn" id="searchBtn">🔍 검색</button>
                        </div>
                    </section>
                    
                    <h2>📚 공개 책 목록</h2>
                    <div id="publicBooksContainer" class="books-grid">
                        <div class="loading">공개 책 목록을 불러오는 중...</div>
                    </div>
                </div>
                
                <!-- 내 책 페이지 -->
                <div id="myBooksPage" class="page">
                    <h2>📝 내 책 목록</h2>
                    <div id="myBooksContainer" class="books-grid">
                        <div class="loading">내 책 목록을 불러오는 중...</div>
                    </div>
                </div>
                
                <!-- 푸터 -->
                <footer class="footer">
                    © 2024 LukeVanilla 책 시스템 | Made with ❤️
                </footer>
            </div>
            
            <!-- 책 모달 -->
            <div class="book-modal" id="bookModal">
                <div class="book-page" id="bookPage">
                    <!-- 페이지 표시 -->
                    <div class="page-indicator" id="pageIndicator">1쪽 중 1쪽</div>
                    
                    <!-- 책 제목 -->
                    <h1 class="modal-book-title" id="modalBookTitle">책 제목</h1>
                    
                    <!-- 메타 정보 -->
                    <div class="modal-book-meta" id="modalBookMeta">
                        <span class="author">작성자</span>
                        <span class="divider">|</span>
                        <span class="date">날짜</span>
                        <span class="divider">|</span>
                        <span class="status">공개</span>
                    </div>
                    
                    <!-- 책 내용 -->
                    <div class="modal-book-content" id="modalBookContent">
                        <p>책 내용을 불러오는 중...</p>
                    </div>
                    
                    <!-- 내비게이션 -->
                    <div class="book-nav">
                        <button class="nav-btn-book" id="prevBtn">◀ 이전</button>
                        <button class="nav-btn-book" id="nextBtn">다음 ▶</button>
                    </div>
                    
                    <!-- 닫기 버튼 -->
                    <button class="close-btn" id="closeBtn">✕</button>
                </div>
            </div>

            <script src="/js/app.js"></script>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * 기본 CSS 파일 생성 - 새로운 UI 스타일 적용
     */
    private fun generateDefaultCSS(): String {
        return """
        @import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&family=JetBrains+Mono:wght@400;500&display=swap');
        
        :root {
            --mc-primary: #5865f2;
            --mc-secondary: #57f287;
            --mc-accent: #ffa500;
            --mc-gold: #ffcc02;
            --mc-bg-dark: #1e2124;
            --mc-bg-medium: #2f3136;
            --mc-bg-light: #36393f;
            --mc-text-primary: #ffffff;
            --mc-text-secondary: #b9bbbe;
            --mc-text-muted: #72767d;
            --mc-border: #4f5660;
            --mc-success: #57f287;
            --mc-warning: #fee75c;
            --mc-danger: #ed4245;
            --mc-grass: #7cb342;
            --mc-dirt: #8d6e63;
        }
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Noto Sans KR', sans-serif;
            font-size: 16px;
            background: linear-gradient(135deg, var(--mc-bg-dark) 0%, var(--mc-bg-medium) 100%);
            min-height: 100vh;
            color: var(--mc-text-primary);
            line-height: 1.6;
        }
        
        /* 메인 컨테이너 */
        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 24px;
        }
        
        /* 헤더 */
        .header {
            text-align: center;
            margin-bottom: 40px;
            padding: 40px 24px;
            background: linear-gradient(135deg, var(--mc-primary) 0%, var(--mc-secondary) 100%);
            border-radius: 16px;
            box-shadow: 
                0 8px 32px rgba(88, 101, 242, 0.3),
                0 0 0 1px rgba(255, 255, 255, 0.1);
            position: relative;
            overflow: hidden;
        }
        
        .header::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: 
                radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
                radial-gradient(circle at 80% 80%, rgba(255, 255, 255, 0.05) 0%, transparent 50%);
            pointer-events: none;
        }
        
        .header h1 {
            font-size: 3rem;
            font-weight: 700;
            margin-bottom: 12px;
            text-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
            position: relative;
        }
        
        .header p {
            font-size: 1.2rem;
            opacity: 0.9;
            position: relative;
        }
        
        /* 네비게이션 */
        .nav {
            display: flex;
            justify-content: center;
            gap: 16px;
            margin-bottom: 40px;
            flex-wrap: wrap;
        }
        
        .nav-btn {
            padding: 14px 28px;
            background: var(--mc-bg-light);
            border: 1px solid var(--mc-border);
            border-radius: 8px;
            color: var(--mc-text-primary);
            text-decoration: none;
            font-size: 1rem;
            font-weight: 500;
            transition: all 0.2s ease;
            cursor: pointer;
            position: relative;
            overflow: hidden;
        }
        
        .nav-btn::before {
            content: '';
            position: absolute;
            top: 0;
            left: -100%;
            width: 100%;
            height: 100%;
            background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
            transition: left 0.5s ease;
        }
        
        .nav-btn:hover {
            background: var(--mc-primary);
            border-color: var(--mc-primary);
            transform: translateY(-2px);
            box-shadow: 0 8px 24px rgba(88, 101, 242, 0.4);
        }
        
        .nav-btn:hover::before {
            left: 100%;
        }
        
        .nav-btn.active {
            background: linear-gradient(135deg, var(--mc-primary), var(--mc-secondary));
            border-color: transparent;
            color: white;
        }
        
        /* 페이지 표시/숨김 */
        .page {
            display: none;
        }
        
        .page.active {
            display: block;
        }
        
        /* 통계 카드 */
        .stats {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 24px;
            margin-bottom: 40px;
        }
        
        .stat-card {
            background: var(--mc-bg-light);
            border: 1px solid var(--mc-border);
            border-radius: 12px;
            padding: 20px;
            text-align: center;
            transition: all 0.3s ease;
            position: relative;
            overflow: hidden;
        }
        
        .stat-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 4px;
            background: linear-gradient(90deg, var(--mc-primary), var(--mc-secondary), var(--mc-accent));
        }
        
        .stat-card:hover {
            transform: translateY(-8px);
            box-shadow: 0 16px 40px rgba(0, 0, 0, 0.3);
            border-color: var(--mc-primary);
        }
        
        .stat-card .icon {
            font-size: 2.5rem;
            margin-bottom: 12px;
            display: block;
        }
        
        .stat-card.books .icon { color: var(--mc-accent); }
        .stat-card.authors .icon { color: var(--mc-success); }
        
        .stat-card .number {
            font-size: 2.5rem;
            font-weight: 700;
            color: var(--mc-text-primary);
            margin-bottom: 6px;
            font-family: 'JetBrains Mono', monospace;
        }
        
        .stat-card .label {
            font-size: 1rem;
            color: var(--mc-text-secondary);
            font-weight: 500;
        }
        
        /* 알림 */
        .alert {
            background: linear-gradient(135deg, rgba(87, 242, 135, 0.1), rgba(88, 101, 242, 0.1));
            border: 1px solid var(--mc-success);
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 32px;
            position: relative;
        }
        
        .alert-title {
            font-size: 1.2rem;
            font-weight: 600;
            color: var(--mc-success);
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        .alert-content {
            color: var(--mc-text-secondary);
            font-size: 1rem;
        }
        
        /* 검색 영역 */
        .search-section {
            background: var(--mc-bg-light);
            border: 1px solid var(--mc-border);
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 32px;
        }
        
        .search-bar {
            display: flex;
            gap: 12px;
            align-items: center;
            flex-wrap: wrap;
        }
        
        .search-bar input {
            flex: 1;
            min-width: 300px;
            padding: 14px 20px;
            background: var(--mc-bg-dark);
            border: 1px solid var(--mc-border);
            border-radius: 8px;
            color: var(--mc-text-primary);
            font-size: 1rem;
            transition: border-color 0.2s ease;
        }
        
        .search-bar input:focus {
            outline: none;
            border-color: var(--mc-primary);
            box-shadow: 0 0 0 3px rgba(88, 101, 242, 0.2);
        }
        
        .search-bar input::placeholder {
            color: var(--mc-text-muted);
        }
        
        .search-btn {
            padding: 14px 24px;
            background: var(--mc-primary);
            border: none;
            border-radius: 8px;
            color: white;
            font-size: 1rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        
        .search-btn:hover {
            background: #4752c4;
            transform: translateY(-1px);
        }
        
        /* 책 목록 */
        .books-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
            gap: 24px;
            margin-bottom: 40px;
        }
        
        .book-card {
            background: var(--mc-bg-light);
            border: 1px solid var(--mc-border);
            border-radius: 16px;
            padding: 24px;
            transition: all 0.3s ease;
            position: relative;
            overflow: hidden;
            cursor: pointer;
        }
        
        .book-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            width: 4px;
            height: 100%;
            background: linear-gradient(to bottom, var(--mc-accent), var(--mc-primary));
        }
        
        .book-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
            border-color: var(--mc-primary);
        }
        
        .book-title {
            font-size: 1.4rem;
            font-weight: 600;
            color: var(--mc-text-primary);
            margin-bottom: 12px;
            line-height: 1.4;
        }
        
        .book-meta {
            display: flex;
            gap: 16px;
            margin-bottom: 16px;
            font-size: 0.9rem;
            color: var(--mc-text-muted);
            flex-wrap: wrap;
        }
        
        .book-meta span {
            display: flex;
            align-items: center;
            gap: 4px;
        }
        
        .book-preview {
            color: var(--mc-text-secondary);
            font-size: 1rem;
            line-height: 1.6;
            margin-bottom: 16px;
            height: 80px;
            overflow: hidden;
            display: -webkit-box;
            -webkit-line-clamp: 4;
            -webkit-box-orient: vertical;
        }
        
        .book-tags {
            display: flex;
            gap: 8px;
            margin-bottom: 20px;
            flex-wrap: wrap;
        }
        
        .book-tag {
            background: rgba(88, 101, 242, 0.2);
            color: var(--mc-primary);
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 500;
            border: 1px solid rgba(88, 101, 242, 0.3);
        }
        
        .read-btn {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, var(--mc-primary), #4752c4);
            border: none;
            border-radius: 8px;
            color: white;
            font-size: 1rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
            position: relative;
            overflow: hidden;
        }
        
        .read-btn::before {
            content: '';
            position: absolute;
            top: 0;
            left: -100%;
            width: 100%;
            height: 100%;
            background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
            transition: left 0.5s ease;
        }
        
        .read-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 24px rgba(88, 101, 242, 0.4);
        }
        
        .read-btn:hover::before {
            left: 100%;
        }
        
        /* 책 액션 버튼들 */
        .book-actions {
            display: flex;
            gap: 8px;
            margin-top: 16px;
            justify-content: flex-end;
        }
        
        .action-btn {
            padding: 8px 16px;
            border: none;
            border-radius: 6px;
            font-size: 0.9rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            gap: 4px;
        }
        
        .public-btn {
            background: var(--mc-success);
            color: white;
        }
        
        .public-btn:hover {
            background: #3eb049;
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(87, 242, 135, 0.3);
        }
        
        .private-btn {
            background: var(--mc-text-muted);
            color: white;
        }
        
        .private-btn:hover {
            background: #5a6269;
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(114, 118, 125, 0.3);
        }
        
        /* 로그인 컨테이너 */
        .login-container {
            max-width: 500px;
            margin: 2rem auto;
            background: var(--mc-bg-light);
            border: 1px solid var(--mc-border);
            border-radius: 16px;
            padding: 40px;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
        }
        
        .login-container h2 {
            text-align: center;
            color: var(--mc-text-primary);
            margin-bottom: 1rem;
            font-size: 1.8rem;
        }
        
        .login-container p {
            text-align: center;
            color: var(--mc-text-secondary);
            margin-bottom: 2rem;
        }
        
        .form-group {
            margin-bottom: 1.5rem;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 0.5rem;
            color: var(--mc-text-primary);
            font-weight: 500;
        }
        
        .form-group input {
            width: 100%;
            padding: 14px;
            background: var(--mc-bg-dark);
            border: 1px solid var(--mc-border);
            border-radius: 8px;
            color: var(--mc-text-primary);
            font-size: 1rem;
            transition: border-color 0.2s ease;
        }
        
        .form-group input:focus {
            outline: none;
            border-color: var(--mc-primary);
            box-shadow: 0 0 0 3px rgba(88, 101, 242, 0.2);
        }
        
        .form-group input::placeholder {
            color: var(--mc-text-muted);
        }
        
        button[type="submit"] {
            width: 100%;
            background: linear-gradient(135deg, var(--mc-primary), #4752c4);
            color: white;
            border: none;
            padding: 14px;
            border-radius: 8px;
            font-size: 1rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        
        button[type="submit"]:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 24px rgba(88, 101, 242, 0.4);
        }
        
        /* 책 모달 */
        .book-modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.7);
            backdrop-filter: blur(3px);
            z-index: 1000;
            animation: fadeIn 0.3s ease;
        }
        
        .book-modal.show {
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 20px;
        }
        
        .book-page {
            background: #ffffff;
            width: 100%;
            max-width: 700px;
            max-height: 85vh;
            border-radius: 12px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
            animation: slideIn 0.4s ease;
            position: relative;
            display: flex;
            flex-direction: column;
        }
        
        /* 책 제목 */
        .modal-book-title {
            font-family: 'Noto Sans KR', sans-serif;
            font-size: 1.8rem;
            font-weight: 700;
            color: #333;
            text-align: center;
            margin: 0;
            padding: 30px 40px 20px;
            border-bottom: 2px solid #f0f0f0;
            background: #fafafa;
            border-radius: 12px 12px 0 0;
            flex-shrink: 0;
        }
        
        /* 메타 정보 */
        .modal-book-meta {
            text-align: center;
            padding: 15px 40px;
            background: #f8f9fa;
            color: #666;
            font-size: 0.9rem;
            border-bottom: 1px solid #e9ecef;
            display: flex;
            justify-content: center;
            gap: 15px;
            flex-wrap: wrap;
            flex-shrink: 0;
        }
        
        .modal-book-meta .divider {
            color: #999;
        }
        
        /* 페이지 표시 */
        .page-indicator {
            position: absolute;
            top: 35px;
            right: 40px;
            background: rgba(0, 0, 0, 0.7);
            color: white;
            padding: 5px 12px;
            border-radius: 15px;
            font-size: 0.8rem;
            font-weight: 500;
            z-index: 10;
        }
        
        /* 책 내용 */
        .modal-book-content {
            font-family: 'Noto Sans KR', sans-serif;
            font-size: 1.1rem;
            line-height: 1.8;
            color: #333;
            padding: 30px 40px;
            min-height: 300px;
            flex-grow: 1;
            overflow-y: auto;
        }
        
        .modal-book-content p {
            margin-bottom: 1.2em;
            text-align: justify;
        }
        
        /* 내비게이션 */
        .book-nav {
            display: flex;
            justify-content: center;
            gap: 15px;
            padding: 20px;
            background: #f8f9fa;
            border-top: 1px solid #e9ecef;
            border-radius: 0 0 12px 12px;
            flex-shrink: 0;
        }
        
        .nav-btn-book {
            padding: 12px 20px;
            background: var(--mc-primary);
            border: none;
            border-radius: 8px;
            color: white;
            font-family: 'Noto Sans KR', sans-serif;
            font-size: 0.9rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
            box-shadow: 0 2px 8px rgba(88, 101, 242, 0.3);
        }
        
        .nav-btn-book:hover {
            background: #4752c4;
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(88, 101, 242, 0.4);
        }
        
        .nav-btn-book:disabled {
            opacity: 0.5;
            cursor: not-allowed;
            transform: none;
            background: #ccc;
        }
        
        .nav-btn-book:disabled:hover {
            background: #ccc;
            transform: none;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }
        
        /* 닫기 버튼 */
        .close-btn {
            position: absolute;
            top: 10px;
            right: 10px;
            width: 35px;
            height: 35px;
            background: #ff4757;
            border: none;
            border-radius: 50%;
            color: white;
            font-size: 1.1rem;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 2px 8px rgba(255, 71, 87, 0.4);
            z-index: 20;
        }
        
        .close-btn:hover {
            background: #ff3742;
            transform: scale(1.1);
            box-shadow: 0 4px 12px rgba(255, 71, 87, 0.5);
        }
        
        /* 푸터 */
        .footer {
            text-align: center;
            padding: 40px 0;
            margin-top: 60px;
            border-top: 1px solid var(--mc-border);
            color: var(--mc-text-muted);
            font-size: 0.9rem;
        }
        
        /* 로딩 */
        .loading {
            text-align: center;
            padding: 2rem;
            color: var(--mc-text-secondary);
        }
        
        /* 애니메이션 */
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        
        @keyframes slideIn {
            from { 
                opacity: 0;
                transform: scale(0.8) translateY(50px);
            }
            to { 
                opacity: 1;
                transform: scale(1) translateY(0);
            }
        }
        
        /* 반응형 */
        @media (max-width: 768px) {
            .container {
                padding: 16px;
            }
            
            .header {
                padding: 24px 16px;
            }
            
            .header h1 {
                font-size: 2.2rem;
            }
            
            .nav {
                flex-direction: column;
                align-items: center;
            }
            
            .stats {
                grid-template-columns: 1fr;
            }
            
            .books-grid {
                grid-template-columns: 1fr;
            }
            
            .search-bar {
                flex-direction: column;
            }
            
            .search-bar input {
                min-width: 100%;
            }
            
            /* 모달 반응형 */
            .book-page {
                max-width: 90vw;
                max-height: 90vh;
                margin: 20px;
            }
            
            .modal-book-title {
                font-size: 1.5rem;
                padding: 20px 25px 15px;
            }
            
            .modal-book-content {
                padding: 20px 25px;
                font-size: 1rem;
            }
            
            .modal-book-meta {
                padding: 10px 25px;
                font-size: 0.8rem;
                gap: 10px;
            }
            
            .nav-btn-book {
                padding: 10px 16px;
                font-size: 0.8rem;
            }
            
            .book-nav {
                padding: 15px;
                gap: 10px;
            }
        }
        
        /* 스크롤바 커스텀 */
        ::-webkit-scrollbar {
            width: 8px;
        }
        
        ::-webkit-scrollbar-track {
            background: var(--mc-bg-dark);
        }
        
        ::-webkit-scrollbar-thumb {
            background: var(--mc-primary);
            border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb:hover {
            background: #4752c4;
        }
        """.trimIndent()
    }

    /**
     * 기본 JavaScript 파일 생성 - 새로운 UI 구조 적용
     */
    private fun generateDefaultJS(): String {
        return """
        class BookSystem {
            constructor() {
                this.currentToken = localStorage.getItem('bookToken');
                this.currentPage = 'home';
                this.currentBook = null;
                this.currentBookPages = [];
                this.currentPageIndex = 0;
                this.init();
            }

            init() {
                this.setupEventListeners();
                this.checkUrlParams(); // URL 파라미터 확인
                this.checkAuth();
                this.showPage('home');
                this.loadPublicStats();
                this.loadRecentBooks();
            }

            // URL 파라미터에서 인증코드 확인
            checkUrlParams() {
                const urlParams = new URLSearchParams(window.location.search);
                const authCode = urlParams.get('code');
                
                if (authCode && authCode.length === 6) {
                    // URL에서 code 파라미터 제거 (깔끔하게)
                    const url = new URL(window.location);
                    url.searchParams.delete('code');
                    window.history.replaceState({}, document.title, url.pathname);
                    
                    // 자동 로그인 시도
                    this.autoLogin(authCode);
                }
            }

            // 자동 로그인 함수
            async autoLogin(authCode) {
                try {
                    const response = await fetch('/api/auth', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ authCode })
                    });

                    const result = await response.json();

                    if (result.success) {
                        this.currentToken = result.data.token;
                        localStorage.setItem('bookToken', this.currentToken);
                        
                        // 성공 알림
                        this.showNotification('🎉 자동 로그인 완료!', 'success');
                        
                        this.checkAuth();
                        this.showPage('myBooks'); // 내 책 페이지로 이동
                    } else {
                        this.showNotification('❌ ' + result.error, 'error');
                    }
                } catch (error) {
                    console.error('Auto login error:', error);
                    this.showNotification('❌ 자동 로그인 중 오류가 발생했습니다.', 'error');
                }
            }

            // 알림 표시 함수
            showNotification(message, type = 'info') {
                const notification = document.createElement('div');
                notification.className = 'notification notification-' + type;
                notification.textContent = message;
                notification.style.cssText = `
                    position: fixed;
                    top: 20px;
                    right: 20px;
                    background: var(--mc-primary);
                    color: white;
                    padding: 15px 20px;
                    border-radius: 8px;
                    z-index: 10000;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    font-weight: 500;
                `;
                
                if (type === 'error') {
                    notification.style.background = 'var(--mc-danger)';
                } else if (type === 'success') {
                    notification.style.background = 'var(--mc-success)';
                }
                
                document.body.appendChild(notification);
                
                // 3초 후 제거
                setTimeout(() => {
                    if (notification.parentNode) {
                        notification.parentNode.removeChild(notification);
                    }
                }, 3000);
            }

            // API 호출 래퍼 (세션 만료 자동 처리)
            async apiCall(url, options = {}) {
                try {
                    const response = await fetch(url, options);
                    const result = await response.json();
                    
                    // 세션 만료 감지
                    if (!result.success && result.error === 'SESSION_EXPIRED') {
                        this.handleSessionExpired();
                        return null;
                    }
                    
                    return { response, result };
                } catch (error) {
                    console.error('API call error:', error);
                    return null;
                }
            }

            // 세션 만료 처리
            handleSessionExpired() {
                this.currentToken = null;
                localStorage.removeItem('bookToken');
                this.checkAuth();
                this.showPage('home');
                this.showNotification('⏱️ 세션이 만료되었습니다. 다시 로그인해주세요.', 'error');
            }

            setupEventListeners() {
                // 네비게이션 버튼들
                document.getElementById('homeBtn').addEventListener('click', () => this.showPage('home'));
                document.getElementById('publicBooksBtn').addEventListener('click', () => this.showPage('publicBooks'));
                document.getElementById('myBooksBtn').addEventListener('click', () => this.showPage('myBooks'));
                document.getElementById('loginBtn').addEventListener('click', () => this.showPage('login'));
                document.getElementById('logoutBtn').addEventListener('click', () => this.logout());

                // 로그인 폼
                document.getElementById('loginForm').addEventListener('submit', (e) => this.handleLogin(e));

                // 검색
                const searchBtn = document.getElementById('searchBtn');
                if (searchBtn) {
                    searchBtn.addEventListener('click', () => this.handleSearch());
                }
                const searchInput = document.getElementById('searchInput');
                if (searchInput) {
                    searchInput.addEventListener('keypress', (e) => {
                        if (e.key === 'Enter') this.handleSearch();
                    });
                }

                // 모달 관련
                document.getElementById('closeBtn').addEventListener('click', () => this.closeModal());
                document.getElementById('prevBtn').addEventListener('click', () => this.prevPage());
                document.getElementById('nextBtn').addEventListener('click', () => this.nextPage());
                
                // 모달 배경 클릭으로 닫기
                document.getElementById('bookModal').addEventListener('click', (e) => {
                    if (e.target.id === 'bookModal') this.closeModal();
                });

                // 키보드 단축키
                document.addEventListener('keydown', (e) => {
                    if (document.getElementById('bookModal').classList.contains('show')) {
                        if (e.key === 'ArrowLeft') this.prevPage();
                        else if (e.key === 'ArrowRight') this.nextPage();
                        else if (e.key === 'Escape') this.closeModal();
                    }
                });
            }

            checkAuth() {
                const isLoggedIn = !!this.currentToken;
                document.getElementById('loginBtn').style.display = isLoggedIn ? 'none' : 'block';
                document.getElementById('logoutBtn').style.display = isLoggedIn ? 'block' : 'none';
                document.getElementById('myBooksBtn').style.display = isLoggedIn ? 'block' : 'none';
                
                // 로그인 알림 메시지 표시/숨김 처리
                const loginAlert = document.getElementById('loginAlert');
                if (loginAlert) {
                    loginAlert.style.display = isLoggedIn ? 'none' : 'block';
                }
            }

            showPage(pageName) {
                // 모든 페이지 숨기기
                document.querySelectorAll('.page').forEach(page => page.classList.remove('active'));
                document.querySelectorAll('.nav-btn').forEach(btn => btn.classList.remove('active'));

                // 선택된 페이지 보이기
                document.getElementById(pageName + 'Page').classList.add('active');
                document.getElementById(pageName + 'Btn').classList.add('active');

                this.currentPage = pageName;

                // 페이지별 데이터 로드
                switch(pageName) {
                    case 'home':
                        this.loadPublicStats();
                        this.loadRecentBooks();
                        break;
                    case 'publicBooks':
                        this.loadPublicBooks();
                        break;
                    case 'myBooks':
                        if (this.currentToken) {
                            this.loadMyBooks();
                        }
                        break;
                }
            }

            async handleLogin(e) {
                e.preventDefault();
                const authCode = document.getElementById('authCode').value;
                const messageDiv = document.getElementById('loginMessage');

                if (!authCode || authCode.length !== 6) {
                    messageDiv.innerHTML = '<div style="color: var(--mc-danger);">6자리 인증 코드를 입력해주세요.</div>';
                    return;
                }

                try {
                    const response = await fetch('/api/auth', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ authCode })
                    });

                    const result = await response.json();

                    if (result.success) {
                        this.currentToken = result.data.token;
                        localStorage.setItem('bookToken', this.currentToken);
                        messageDiv.innerHTML = '<div style="color: var(--mc-success);">로그인 성공!</div>';
                        
                        setTimeout(() => {
                            this.checkAuth();
                            this.showPage('myBooks');
                        }, 1000);
                    } else {
                        messageDiv.innerHTML = '<div style="color: var(--mc-danger);">' + result.error + '</div>';
                    }
                } catch (error) {
                    messageDiv.innerHTML = '<div style="color: var(--mc-danger);">로그인 중 오류가 발생했습니다.</div>';
                    console.error('Login error:', error);
                }
            }

            logout() {
                if (this.currentToken) {
                    fetch('/api/user/logout', {
                        method: 'POST',
                        headers: { 'Authorization': 'Bearer ' + this.currentToken }
                    });
                }

                this.currentToken = null;
                localStorage.removeItem('bookToken');
                this.checkAuth();
                this.showPage('home');
            }

            async loadPublicStats() {
                try {
                    const response = await fetch('/api/stats');
                    const result = await response.json();

                    if (result.success) {
                        const stats = result.data;
                        const totalBooksElement = document.getElementById('totalBooksCount');
                        const totalAuthorsElement = document.getElementById('totalAuthorsCount');
                        
                        if (totalBooksElement) {
                            totalBooksElement.textContent = stats.totalPublicBooks || 0;
                        }
                        if (totalAuthorsElement) {
                            totalAuthorsElement.textContent = stats.uniqueAuthorsCount || 0;
                        }
                    }
                } catch (error) {
                    console.error('Stats loading error:', error);
                    const totalBooksElement = document.getElementById('totalBooksCount');
                    const totalAuthorsElement = document.getElementById('totalAuthorsCount');
                    if (totalBooksElement) totalBooksElement.textContent = '?';
                    if (totalAuthorsElement) totalAuthorsElement.textContent = '?';
                }
            }

            async loadRecentBooks() {
                const container = document.getElementById('recentBooksContainer');
                if (!container) return;
                
                container.innerHTML = '<div class="loading">최근 책 목록을 불러오는 중...</div>';

                try {
                    const response = await fetch('/api/books/public?page=1&size=6');
                    const result = await response.json();

                    if (result.success) {
                        this.renderBooks(container, result.data.books, false);
                    } else {
                        container.innerHTML = '<div class="loading">최근 책을 불러올 수 없습니다.</div>';
                    }
                } catch (error) {
                    console.error('Recent books loading error:', error);
                    container.innerHTML = '<div class="loading">오류가 발생했습니다.</div>';
                }
            }

            async loadPublicBooks() {
                const container = document.getElementById('publicBooksContainer');
                container.innerHTML = '<div class="loading">공개 책 목록을 불러오는 중...</div>';

                try {
                    const response = await fetch('/api/books/public?page=1&size=20');
                    const result = await response.json();

                    if (result.success) {
                        this.renderBooks(container, result.data.books, false);
                    } else {
                        container.innerHTML = '<div class="loading">공개 책을 불러올 수 없습니다.</div>';
                    }
                } catch (error) {
                    console.error('Public books loading error:', error);
                    container.innerHTML = '<div class="loading">오류가 발생했습니다.</div>';
                }
            }

            async loadMyBooks() {
                const container = document.getElementById('myBooksContainer');
                container.innerHTML = '<div class="loading">내 책 목록을 불러오는 중...</div>';

                const apiResult = await this.apiCall('/api/user/books?page=1&size=20', {
                    headers: { 'Authorization': 'Bearer ' + this.currentToken }
                });

                if (apiResult && apiResult.result.success) {
                    this.renderBooks(container, apiResult.result.data.books, true);
                } else if (apiResult) {
                    container.innerHTML = '<div class="loading">내 책을 불러올 수 없습니다.</div>';
                } else {
                    container.innerHTML = '<div class="loading">오류가 발생했습니다.</div>';
                }
            }

            async handleSearch() {
                const searchInput = document.getElementById('searchInput');
                const query = searchInput ? searchInput.value.trim() : '';
                
                if (!query) {
                    this.loadPublicBooks();
                    return;
                }

                const container = document.getElementById('publicBooksContainer');
                container.innerHTML = '<div class="loading">검색 중...</div>';

                try {
                    const response = await fetch('/api/books/public?page=1&size=20&search=' + encodeURIComponent(query));
                    const result = await response.json();

                    if (result.success) {
                        this.renderBooks(container, result.data.books, false);
                    } else {
                        container.innerHTML = '<div class="loading">검색 결과가 없습니다.</div>';
                    }
                } catch (error) {
                    console.error('Search error:', error);
                    container.innerHTML = '<div class="loading">검색 중 오류가 발생했습니다.</div>';
                }
            }

            renderBooks(container, books, isMyBooks) {
                if (books.length === 0) {
                    container.innerHTML = '<div class="loading">저장된 책이 없습니다.</div>';
                    return;
                }

                const booksHtml = books.map(function(book) {
                    const content = JSON.parse(book.content);
                    const preview = (content.pages[0] && content.pages[0].content) ? 
                        content.pages[0].content.substring(0, 100) + '...' : '내용 미리보기 없음';
                    
                    // 페이지 수 안전하게 처리 - 더 확실하게
                    const pageCount = book.pageCount || (content.pages ? content.pages.length : 1);
                    
                    const statusBadge = isMyBooks ? 
                        '<span>👤 ' + (book.isPublic ? '공개' : '비공개') + '</span>' : '<span>👤 작성자</span>';
                    
                    // 플레이어 이름 표시 (내 책에서만)
                    const playerNameBadge = isMyBooks && book.playerName ? 
                        '<span>👤 ' + book.playerName + '</span>' : statusBadge;

                    // 공개/비공개 토글 버튼 (내 책에서만)
                    const toggleButtons = isMyBooks ? 
                        '<div class="book-actions">' +
                            (book.isPublic ? 
                                '<button class="action-btn private-btn" onclick="event.stopPropagation(); bookSystem.toggleBookVisibility(' + book.id + ', false)">🔒 비공개로</button>' :
                                '<button class="action-btn public-btn" onclick="event.stopPropagation(); bookSystem.toggleBookVisibility(' + book.id + ', true)">🌍 공개하기</button>') +
                        '</div>' : '';

                    return '<article class="book-card" onclick="bookSystem.showBook(' + book.id + ', ' + isMyBooks + ')">' +
                            '<h3 class="book-title">' + book.title + '</h3>' +
                            '<div class="book-meta">' +
                                '<span>📄 ' + pageCount + '페이지</span>' +
                                '<span>📅 ' + book.createdAt.substring(0, 10) + '</span>' +
                                playerNameBadge +
                                (isMyBooks ? '<span>🔒 ' + (book.isPublic ? '공개' : '비공개') + '</span>' : '') +
                            '</div>' +
                            '<p class="book-preview">' + preview + '</p>' +
                            toggleButtons +
                        '</article>';
                }).join('');

                container.innerHTML = booksHtml;
            }

            async showBook(bookId, isMyBook) {
                const url = isMyBook ? 
                    '/api/user/books/' + bookId : 
                    '/api/books/public/' + bookId;
                const headers = isMyBook && this.currentToken ? 
                    { 'Authorization': 'Bearer ' + this.currentToken } : {};

                const apiResult = await this.apiCall(url, { headers });
                
                if (apiResult && apiResult.result.success) {
                    this.currentBook = apiResult.result.data;
                    const content = JSON.parse(this.currentBook.content);
                    this.currentBookPages = content.pages;
                    this.currentPageIndex = 0;
                    
                    this.updateBookModal();
                    document.getElementById('bookModal').classList.add('show');
                    document.body.style.overflow = 'hidden';
                } else {
                    this.showNotification('❌ 책을 불러올 수 없습니다.', 'error');
                }
            }

            updateBookModal() {
                if (!this.currentBook || !this.currentBookPages) return;

                document.getElementById('modalBookTitle').textContent = this.currentBook.title;
                
                // 플레이어 이름 표시 (있는 경우)
                const authorText = this.currentBook.playerName ? this.currentBook.playerName : '작성자';
                
                document.getElementById('modalBookMeta').innerHTML = 
                    '<span class="author">' + authorText + '</span>' +
                    '<span class="divider">|</span>' +
                    '<span class="date">' + this.currentBook.createdAt.substring(0, 10) + '</span>' +
                    '<span class="divider">|</span>' +
                    '<span class="status">' + (this.currentBook.isPublic ? '공개' : '비공개') + '</span>';
                document.getElementById('pageIndicator').textContent = this.currentBookPages.length + '쪽 중 ' + (this.currentPageIndex + 1) + '쪽';
                
                const currentPage = this.currentBookPages[this.currentPageIndex];
                // 줄바꿈 처리 (\n을 <br>로 변환)
                const formattedContent = currentPage.content.replace(/\n/g, '<br>');
                document.getElementById('modalBookContent').innerHTML = '<p>' + formattedContent + '</p>';
                
                // 버튼 상태 업데이트
                document.getElementById('prevBtn').disabled = this.currentPageIndex === 0;
                document.getElementById('nextBtn').disabled = this.currentPageIndex === this.currentBookPages.length - 1;
            }

            nextPage() {
                if (this.currentPageIndex < this.currentBookPages.length - 1) {
                    this.currentPageIndex++;
                    this.updateBookModal();
                }
            }

            prevPage() {
                if (this.currentPageIndex > 0) {
                    this.currentPageIndex--;
                    this.updateBookModal();
                }
            }

            closeModal() {
                document.getElementById('bookModal').classList.remove('show');
                document.body.style.overflow = 'auto';
                this.currentBook = null;
                this.currentBookPages = [];
                this.currentPageIndex = 0;
            }

            async toggleBookVisibility(bookId, makePublic) {
                const endpoint = makePublic ? '/api/user/books/' + bookId + '/public' : '/api/user/books/' + bookId + '/private';
                
                const apiResult = await this.apiCall(endpoint, {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + this.currentToken }
                });

                if (apiResult && apiResult.result.success) {
                    this.showNotification(
                        makePublic ? '📖 책이 공개되었습니다!' : '🔒 책이 비공개되었습니다!', 
                        'success'
                    );
                    this.loadMyBooks(); // 목록 새로고침
                } else {
                    this.showNotification('❌ 설정 변경에 실패했습니다.', 'error');
                }
            }
        }

        // 페이지 로드 시 BookSystem 초기화
        const bookSystem = new BookSystem();
        """.trimIndent()
    }

    /**
     * 서버 상태 확인
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * 서버 정보 가져오기
     */
    fun getServerInfo(): Map<String, Any> {
        return mapOf(
            "internal_host" to host,
            "internal_port" to port,
            "external_domain" to externalDomain,
            "external_protocol" to externalProtocol,
            "external_url" to "$externalProtocol://$externalDomain",
            "running" to isRunning(),
            "contentPath" to webContentPath,
            "allowed_origins" to allowedOrigins
        )
    }
    
    /**
     * 외부 접속 주소 반환
     */
    fun getExternalUrl(): String {
        return "$externalProtocol://$externalDomain"
    }
}
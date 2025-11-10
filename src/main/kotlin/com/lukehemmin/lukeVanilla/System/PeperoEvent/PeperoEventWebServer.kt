package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.Main
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.logging.Logger
import java.util.concurrent.TimeUnit

/**
 * 빼빼로 이벤트 웹서버
 */
class PeperoEventWebServer(
    private val plugin: Main,
    private val repository: PeperoEventRepository,
    private val logger: Logger
) {
    private var server: NettyApplicationEngine? = null

    private val port = plugin.config.getInt("pepero_event.web_port", 9696)
    private val host = plugin.config.getString("pepero_event.web_host", "0.0.0.0") ?: "0.0.0.0"
    private val enableCors = plugin.config.getBoolean("pepero_event.enable_cors", true)
    private val allowedOrigins = plugin.config.getStringList("pepero_event.allowed_origins").ifEmpty {
        listOf("https://peperoday2025.lukehemmin.com", "http://localhost:9696")
    }
    private val webContentPath = File(plugin.dataFolder, "pepero_web").path

    fun start() {
        try {
            setupWebContent()

            server = embeddedServer(Netty, port = port, host = host) { module() }
                .start(wait = false)

            logger.info("[PeperoEventWebServer] 웹서버가 $host:$port 에서 시작되었습니다.")
        } catch (e: Exception) {
            logger.severe("[PeperoEventWebServer] 웹서버 시작 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            server?.stop(1, 5, TimeUnit.SECONDS)
            logger.info("[PeperoEventWebServer] 웹서버가 정상 종료되었습니다.")
        } catch (e: Exception) {
            logger.severe("[PeperoEventWebServer] 웹서버 종료 중 오류: ${e.message}")
        }
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }

        if (enableCors) {
            install(CORS) {
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowCredentials = true
                allowedOrigins.forEach { origin ->
                    allowHost(origin.removePrefix("http://").removePrefix("https://").split("/").first())
                }
            }
        }

        install(DefaultHeaders) {
            header("X-Engine", "Ktor")
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                logger.severe("[PeperoEventWebServer] 오류: ${cause.message}")
                cause.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Nothing>(success = false, error = "서버 오류가 발생했습니다."))
            }
        }

        routing {
            staticFiles("/", File(webContentPath)) {
                default("index.html")
            }

            // 플레이어 검색 API
            post("/api/search") {
                val request = call.receive<SearchRequest>()
                val results = repository.searchPlayers(request.keyword)
                call.respond(ApiResponse(success = true, data = results))
            }

            // 투표 제출 API
            post("/api/vote") {
                val request = call.receive<VoteRequest>()
                val success = repository.submitVote(
                    token = request.token,
                    votedUuid = request.votedUuid,
                    votedName = request.votedName,
                    anonymousMessage = request.message
                )
                if (success) {
                    call.respond(ApiResponse<Nothing>(success = true, message = "투표가 완료되었습니다!"))
                } else {
                    call.respond(ApiResponse<Nothing>(success = false, error = "이미 사용된 토큰이거나 잘못된 요청입니다."))
                }
            }

            // 토큰 검증 API
            get("/api/verify/{token}") {
                val token = call.parameters["token"] ?: ""
                val participation = repository.getParticipationByToken(token)
                if (participation != null && !participation.tokenUsed) {
                    call.respond(ApiResponse<Map<String, Boolean>>(success = true, data = mapOf("valid" to true)))
                } else {
                    call.respond(ApiResponse<Nothing>(success = false, error = "유효하지 않은 토큰입니다."))
                }
            }

            // 헬스체크
            get("/health") {
                call.respond(mapOf("status" to "ok", "service" to "PeperoEvent"))
            }
        }
    }

    private fun setupWebContent() {
        val webDir = File(plugin.dataFolder, "pepero_web")
        if (webDir.exists()) {
            webDir.deleteRecursively()
        }
        webDir.mkdirs()

        File(webDir, "index.html").writeText(generateIndexHtml())
        File(webDir, "info.html").writeText(generateInfoHtml())

        val cssDir = File(webDir, "css")
        cssDir.mkdirs()
        File(cssDir, "style.css").writeText(generateCSS())

        val jsDir = File(webDir, "js")
        jsDir.mkdirs()
        File(jsDir, "app.js").writeText(generateJS())

        logger.info("[PeperoEventWebServer] 웹 컨텐츠 생성 완료: ${webDir.absolutePath}")
    }

    private fun generateIndexHtml(): String {
        return """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>빼빼로 데이 2025 이벤트</title>
    <link rel="stylesheet" href="/css/style.css">
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>🍫 빼빼로 데이 2025</h1>
            <p>소중한 친구에게 빼빼로를 선물해보세요!</p>
        </header>

        <!-- 단계 표시 -->
        <div class="steps">
            <div class="step active" id="step1-indicator">
                <div class="step-number">1</div>
                <div class="step-label">유저 선택</div>
            </div>
            <div class="step" id="step2-indicator">
                <div class="step-number">2</div>
                <div class="step-label">메시지 작성</div>
            </div>
            <div class="step" id="step3-indicator">
                <div class="step-number">3</div>
                <div class="step-label">완료</div>
            </div>
        </div>

        <!-- 1단계: 유저 검색 및 선택 -->
        <div id="step1" class="step-content active">
            <h2>선물할 유저를 선택하세요</h2>
            <div class="search-box">
                <input type="text" id="searchInput" placeholder="유저 이름 또는 칭호로 검색...">
                <button id="searchBtn" class="btn btn-primary">🔍 검색</button>
            </div>
            <div id="searchResults" class="search-results"></div>
            <div class="button-group">
                <button id="nextToStep2" class="btn btn-primary" disabled>다음</button>
            </div>
        </div>

        <!-- 2단계: 익명 메시지 작성 -->
        <div id="step2" class="step-content">
            <h2>익명 메시지를 작성하세요 (선택사항)</h2>
            <p class="selected-user" id="selectedUserName"></p>
            <textarea id="messageInput" placeholder="익명으로 전달할 메시지를 입력하세요... (선택사항)" rows="8"></textarea>
            <p class="info-text">※ 메시지는 지금 바로 전달되지 않아요.</p>
            <div class="button-group">
                <button id="backToStep1" class="btn btn-secondary">이전</button>
                <button id="skipMessage" class="btn btn-secondary">건너뛰기</button>
                <button id="submitVote" class="btn btn-primary">다음</button>
            </div>
        </div>

        <!-- 3단계: 완료 -->
        <div id="step3" class="step-content">
            <div class="completion">
                <h2>🎉 이벤트 참여 완료!</h2>
                <p class="completion-message">
                    이벤트에 참여해주셔서 감사해요!<br>
                    곧 다가오는 <strong>11월 11일</strong>에 서버에 들어오셔서<br>
                    빼빼로 아이템을 받아가세요!
                </p>
                <a href="/info.html" class="btn btn-primary">정책 확인하기</a>
            </div>
        </div>

        <footer class="footer">
            <a href="/info.html" target="_blank">개인정보 및 익명 메시지 관리 정책</a>
        </footer>
    </div>

    <script>
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('t');
        if (!token) {
            document.body.innerHTML = '<div class="container"><div class="error">유효하지 않은 접근입니다.</div></div>';
        }
    </script>
    <script src="/js/app.js"></script>
</body>
</html>
        """.trimIndent()
    }

    private fun generateInfoHtml(): String {
        return """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>빼빼로 데이 이벤트 정책</title>
    <link rel="stylesheet" href="/css/style.css">
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>📋 이벤트 정책</h1>
        </header>

        <div class="content">
            <section class="policy-section">
                <h2>🔒 개인정보 보호</h2>
                <p>
                    해당 이벤트를 통해 수집된 모든 정보는 이벤트 종료 후 자동으로 삭제됩니다.<br>
                    익명 메시지는 메시지를 받는 사람을 제외한 다른 사람이 내용에 접근할 수 없으며,<br>
                    내용의 보안이 철저히 유지됩니다.
                </p>
            </section>

            <section class="policy-section">
                <h2>💌 익명 메시지 전달</h2>
                <p>
                    작성하신 익명 메시지는 <strong>11월 12일 12시</strong>에 자동으로 해당 유저에게 전달됩니다.<br>
                    메시지는 디스코드 DM을 통해 전달되며, 대상 유저만 확인할 수 있습니다.
                </p>
            </section>

            <section class="policy-section">
                <h2>🏆 득표 결과</h2>
                <p>
                    이벤트 종료 후, 제일 많은 득표를 얻은 상위 1~3명의 유저 정보만 보존됩니다.<br>
                    보존되는 정보: 유저 이름, 디스코드 ID, 득표 수<br>
                    나머지 모든 투표 및 메시지 데이터는 자동으로 삭제됩니다.
                </p>
            </section>

            <section class="policy-section">
                <h2>🎁 아이템 수령</h2>
                <p>
                    <strong>11월 11일 00:00 ~ 23:59 (KST)</strong> 동안 서버에 접속하시면<br>
                    빼빼로 아이템을 받을 수 있습니다.<br>
                    해당 시간 이후에는 수령이 불가능하니 유의해주세요!
                </p>
            </section>
        </div>

        <footer class="footer">
            <a href="javascript:window.close()">창 닫기</a>
        </footer>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun generateCSS(): String {
        return """
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap');

:root {
    --primary: #ff6b6b;
    --secondary: #4ecdc4;
    --dark: #2d3436;
    --light: #dfe6e9;
    --success: #00b894;
    --warning: #fdcb6e;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Noto Sans KR', sans-serif;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    min-height: 100vh;
    padding: 20px;
}

.container {
    max-width: 800px;
    margin: 0 auto;
    background: white;
    border-radius: 20px;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    overflow: hidden;
}

.header {
    background: linear-gradient(135deg, var(--primary), var(--secondary));
    color: white;
    padding: 40px;
    text-align: center;
}

.header h1 {
    font-size: 2.5rem;
    margin-bottom: 10px;
}

.steps {
    display: flex;
    justify-content: space-around;
    padding: 30px 20px;
    background: #f8f9fa;
}

.step {
    text-align: center;
    opacity: 0.4;
    transition: opacity 0.3s;
}

.step.active {
    opacity: 1;
}

.step-number {
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background: var(--light);
    color: var(--dark);
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 10px;
    font-weight: bold;
    font-size: 1.2rem;
}

.step.active .step-number {
    background: var(--primary);
    color: white;
}

.step-content {
    display: none;
    padding: 40px;
}

.step-content.active {
    display: block;
}

.search-box {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
}

.search-box input {
    flex: 1;
    padding: 15px;
    border: 2px solid var(--light);
    border-radius: 10px;
    font-size: 1rem;
}

.search-box input:focus {
    outline: none;
    border-color: var(--primary);
}

.btn {
    padding: 15px 30px;
    border: none;
    border-radius: 10px;
    font-size: 1rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.3s;
}

.btn-primary {
    background: var(--primary);
    color: white;
}

.btn-primary:hover {
    background: #ee5a52;
    transform: translateY(-2px);
}

.btn-primary:disabled {
    background: var(--light);
    cursor: not-allowed;
    transform: none;
}

.btn-secondary {
    background: var(--light);
    color: var(--dark);
}

.btn-secondary:hover {
    background: #c9d1d5;
}

.search-results {
    max-height: 400px;
    overflow-y: auto;
    margin-bottom: 20px;
}

.user-item {
    padding: 15px;
    border: 2px solid var(--light);
    border-radius: 10px;
    margin-bottom: 10px;
    cursor: pointer;
    transition: all 0.3s;
}

.user-item:hover {
    border-color: var(--primary);
    background: #fff5f5;
}

.user-item.selected {
    border-color: var(--primary);
    background: #ffe0e0;
}

.user-name {
    font-weight: 600;
    font-size: 1.1rem;
    margin-bottom: 5px;
}

.user-tag {
    color: var(--secondary);
    font-size: 0.9rem;
}

textarea {
    width: 100%;
    padding: 15px;
    border: 2px solid var(--light);
    border-radius: 10px;
    font-family: inherit;
    font-size: 1rem;
    resize: vertical;
    margin-bottom: 10px;
}

textarea:focus {
    outline: none;
    border-color: var(--primary);
}

.info-text {
    color: var(--warning);
    font-size: 0.9rem;
    margin-bottom: 20px;
}

.button-group {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
}

.completion {
    text-align: center;
    padding: 40px 20px;
}

.completion h2 {
    color: var(--success);
    font-size: 2rem;
    margin-bottom: 20px;
}

.completion-message {
    font-size: 1.1rem;
    line-height: 1.8;
    margin-bottom: 30px;
}

.footer {
    padding: 20px;
    text-align: center;
    background: #f8f9fa;
    border-top: 1px solid var(--light);
}

.footer a {
    color: var(--primary);
    text-decoration: none;
}

.footer a:hover {
    text-decoration: underline;
}

.error {
    padding: 40px;
    text-align: center;
    color: var(--primary);
    font-size: 1.2rem;
}

.selected-user {
    font-size: 1.2rem;
    font-weight: 600;
    color: var(--primary);
    margin-bottom: 20px;
}

.content {
    padding: 40px;
}

.policy-section {
    margin-bottom: 30px;
}

.policy-section h2 {
    color: var(--primary);
    margin-bottom: 15px;
}

.policy-section p {
    line-height: 1.8;
    color: var(--dark);
}
        """.trimIndent()
    }

    private fun generateJS(): String {
        return """
const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('t');

let currentStep = 1;
let selectedUser = null;

// 단계 전환 함수
function showStep(step) {
    // 단계 표시 업데이트
    for (let i = 1; i <= 3; i++) {
        const stepElement = document.getElementById('step' + i);
        const indicatorElement = document.getElementById('step' + i + '-indicator');
        if (stepElement && indicatorElement) {
            if (i === step) {
                stepElement.classList.add('active');
                indicatorElement.classList.add('active');
            } else {
                stepElement.classList.remove('active');
                indicatorElement.classList.remove('active');
            }
        }
    }
    currentStep = step;
}

// 유저 검색
document.getElementById('searchBtn')?.addEventListener('click', async () => {
    const keyword = document.getElementById('searchInput').value.trim();
    if (!keyword) {
        alert('검색어를 입력하세요.');
        return;
    }

    try {
        const response = await fetch('/api/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ keyword })
        });

        const result = await response.json();
        if (result.success) {
            displaySearchResults(result.data);
        } else {
            alert('검색 실패: ' + result.error);
        }
    } catch (error) {
        console.error('검색 오류:', error);
        alert('검색 중 오류가 발생했습니다.');
    }
});

function displaySearchResults(users) {
    const container = document.getElementById('searchResults');
    if (users.length === 0) {
        container.innerHTML = '<p class="info-text">검색 결과가 없습니다.</p>';
        return;
    }

    container.innerHTML = users.map(user => {
        let html = '<div class="user-item" data-uuid="' + user.uuid + '" data-name="' + user.nickname + '">';
        html += '<div class="user-name">' + user.nickname + '</div>';
        if (user.tag) {
            html += '<div class="user-tag">' + user.tag + '</div>';
        }
        html += '</div>';
        return html;
    }).join('');

    // 유저 선택 이벤트
    document.querySelectorAll('.user-item').forEach(item => {
        item.addEventListener('click', () => {
            document.querySelectorAll('.user-item').forEach(i => i.classList.remove('selected'));
            item.classList.add('selected');
            selectedUser = {
                uuid: item.getAttribute('data-uuid'),
                name: item.getAttribute('data-name')
            };
            document.getElementById('nextToStep2').disabled = false;
        });
    });
}

// 2단계로 이동
document.getElementById('nextToStep2')?.addEventListener('click', () => {
    if (!selectedUser) {
        alert('유저를 선택하세요.');
        return;
    }
    document.getElementById('selectedUserName').textContent = selectedUser.name + ' 님에게 보낼 메시지';
    showStep(2);
});

// 1단계로 돌아가기
document.getElementById('backToStep1')?.addEventListener('click', () => {
    showStep(1);
});

// 건너뛰기
document.getElementById('skipMessage')?.addEventListener('click', async () => {
    await submitVote(null);
});

// 투표 제출
document.getElementById('submitVote')?.addEventListener('click', async () => {
    const message = document.getElementById('messageInput').value.trim();
    await submitVote(message || null);
});

async function submitVote(message) {
    if (!selectedUser) return;

    try {
        const response = await fetch('/api/vote', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                token,
                votedUuid: selectedUser.uuid,
                votedName: selectedUser.name,
                message
            })
        });

        const result = await response.json();
        if (result.success) {
            showStep(3);
        } else {
            alert('투표 실패: ' + result.error);
        }
    } catch (error) {
        console.error('투표 오류:', error);
        alert('투표 중 오류가 발생했습니다.');
    }
}
        """.trimIndent()
    }

    @Serializable
    data class ApiResponse<T>(
        val success: Boolean,
        val data: T? = null,
        val message: String? = null,
        val error: String? = null
    )

    @Serializable
    data class SearchRequest(val keyword: String)

    @Serializable
    data class VoteRequest(
        val token: String,
        val votedUuid: String,
        val votedName: String,
        val message: String?
    )
}

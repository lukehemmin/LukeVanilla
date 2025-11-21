# 📚 LukeVanilla 책 시스템 - nginx 프록시 설정 가이드

## 🎯 목표
마인크래프트 서버의 책 시스템을 `book.mine.lukehemmin.com` 도메인으로 외부 접속 가능하게 설정

## 🔧 설정 단계

### 1. 마인크래프트 서버 설정 (config.yml)

```yaml
book_system:
  # 웹서버 내부 설정 (nginx가 접근할 주소)
  web_host: "127.0.0.1"
  web_port: 9090
  
  # 외부 도메인 설정 (사용자가 접속할 주소)
  external_domain: "book.mine.lukehemmin.com"
  external_protocol: "https"
  
  # CORS 설정 (보안을 위해 허용된 도메인만)
  enable_cors: true
  allowed_origins:
    - "https://book.mine.lukehemmin.com"
    - "http://localhost:9090"  # 개발용
```

### 2. DNS 설정

도메인 관리 페이지에서 A 레코드 추가:
```
book.mine.lukehemmin.com  →  [서버 IP 주소]
```

### 3. nginx 설정

1. **nginx 설치** (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install nginx
```

2. **사이트 설정 파일 생성**:
```bash
sudo nano /etc/nginx/sites-available/book.mine.lukehemmin.com
```

3. **설정 파일 내용** (`nginx-book-proxy.conf` 파일 참조):
   - HTTP → HTTPS 리다이렉트
   - SSL 인증서 설정  
   - 내부 포트 9090으로 프록시
   - 보안 헤더 추가
   - 정적 파일 캐싱

4. **사이트 활성화**:
```bash
sudo ln -s /etc/nginx/sites-available/book.mine.lukehemmin.com /etc/nginx/sites-enabled/
```

5. **설정 테스트**:
```bash
sudo nginx -t
```

### 4. SSL 인증서 발급 (Let's Encrypt)

1. **certbot 설치**:
```bash
sudo apt install certbot python3-certbot-nginx
```

2. **인증서 발급**:
```bash
sudo certbot --nginx -d book.mine.lukehemmin.com
```

3. **자동 갱신 설정**:
```bash
sudo crontab -e
# 다음 줄 추가:
0 12 * * * /usr/bin/certbot renew --quiet
```

### 5. 방화벽 설정

```bash
sudo ufw allow 'Nginx Full'
sudo ufw allow 9090  # 마인크래프트 서버 내부 포트 (선택사항)
```

### 6. nginx 시작/재시작

```bash
sudo systemctl restart nginx
sudo systemctl enable nginx
```

## 📊 동작 흐름

```
사용자 브라우저
       ↓ (HTTPS 요청)
   book.mine.lukehemmin.com:443
       ↓ (nginx reverse proxy)
   127.0.0.1:9090 (마인크래프트 서버 BookSystem)
```

## 🔍 테스트 방법

1. **마인크래프트 서버 시작** 후 로그 확인:
   ```
   [BookWebServer] 웹서버가 127.0.0.1:9090 에서 시작되었습니다.
   [BookWebServer] 내부 주소: http://127.0.0.1:9090
   [BookWebServer] 외부 접속 주소: https://book.mine.lukehemmin.com
   ```

2. **브라우저에서 접속**:
   - `https://book.mine.lukehemmin.com` 접속
   - 책 시스템 홈페이지 확인

3. **인게임 테스트**:
   ```
   /책 웹사이트
   /책 토큰
   ```

## 🛠️ 트러블슈팅

### nginx 502 Bad Gateway
- 마인크래프트 서버가 9090 포트에서 실행 중인지 확인
- 방화벽에서 9090 포트 허용 확인

### SSL 인증서 오류
- 도메인 DNS 설정 확인 (A 레코드)
- certbot 재실행: `sudo certbot --nginx -d book.mine.lukehemmin.com`

### CORS 오류  
- config.yml의 `allowed_origins`에 정확한 도메인 추가
- 마인크래프트 서버 재시작

### 로그 확인
```bash
# nginx 로그
sudo tail -f /var/log/nginx/book.mine.lukehemmin.com.access.log
sudo tail -f /var/log/nginx/book.mine.lukehemmin.com.error.log

# 마인크래프트 서버 로그
tail -f logs/latest.log
```

## 🎉 완료!

설정이 완료되면:
- ✅ `https://book.mine.lukehemmin.com`으로 접속 가능
- ✅ 인게임에서 `/책 토큰`으로 인증코드 생성
- ✅ 웹에서 안전한 로그인 및 책 관리
- ✅ SSL 인증서로 보안 강화
- ✅ nginx를 통한 성능 최적화

---

## 📝 포트 변경 옵션

기본 9090 포트 외에 다른 포트 사용 가능:

### 자주 사용되는 포트들:
- **9090** (현재 설정) - 일반적인 웹 애플리케이션
- **8081** - 대안 웹 포트
- **3000** - Node.js 개발 서버 스타일
- **4000** - 다른 웹 서비스와 충돌 방지
- **7777** - 게임 서버 관련 포트

### 포트 변경 시:
1. `config.yml`의 `web_port` 수정
2. `nginx-book-proxy.conf`의 `proxy_pass` 포트 수정
3. 방화벽 설정 업데이트
4. 마인크래프트 서버 & nginx 재시작
-- 차단 우회 시도 추적 테이블
-- 차단된 계정이 다른 계정으로 우회 시도한 이력 저장

CREATE TABLE IF NOT EXISTS Banned_Account_Relations (
    relation_id INT AUTO_INCREMENT PRIMARY KEY,

    -- 기존 차단된 계정 정보
    original_uuid VARCHAR(36) NOT NULL COMMENT '기존 차단된 마인크래프트 UUID',
    original_nickname VARCHAR(16) COMMENT '기존 차단된 마인크래프트 닉네임',
    original_discord_id VARCHAR(20) COMMENT '기존 차단된 디스코드 ID',
    original_ban_reason TEXT COMMENT '기존 차단 사유',

    -- 우회 시도한 새 계정 정보
    new_uuid VARCHAR(36) NOT NULL COMMENT '우회 시도한 새 마인크래프트 UUID',
    new_nickname VARCHAR(16) COMMENT '우회 시도한 새 마인크래프트 닉네임',
    new_discord_id VARCHAR(20) COMMENT '우회 시도한 새 디스코드 ID',

    -- 공통 정보
    shared_ip VARCHAR(45) NOT NULL COMMENT '공통으로 사용된 차단된 IP',

    -- 메타 정보
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '우회 시도 감지 시간',
    ban_reason TEXT COMMENT '새 계정 차단 사유',

    -- 인덱스
    INDEX idx_original_uuid (original_uuid),
    INDEX idx_new_uuid (new_uuid),
    INDEX idx_shared_ip (shared_ip),
    INDEX idx_detected_at (detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='차단 우회 시도 추적';

-- ==========================================
-- LukeVanilla 토지 시스템 DB 마이그레이션
-- 목적: system_type 컬럼 추가로 시스템 구분 명확화
-- ==========================================

-- 1. system_type 컬럼 추가
ALTER TABLE myland_claims 
ADD COLUMN system_type ENUM('MYLAND', 'ADVANCED', 'FARM_VILLAGE') NULL
AFTER claim_type;

-- 2. 기존 데이터 마이그레이션
-- MyLand 시스템 데이터 (resource_type IS NULL)
UPDATE myland_claims 
SET system_type = 'MYLAND' 
WHERE resource_type IS NULL AND system_type IS NULL;

-- AdvancedLandClaiming 시스템 데이터 (resource_type IS NOT NULL)
UPDATE myland_claims 
SET system_type = 'ADVANCED' 
WHERE resource_type IS NOT NULL AND system_type IS NULL;

-- FarmVillage 시스템 데이터 (claim_type = 'FARM_VILLAGE')
UPDATE myland_claims 
SET system_type = 'FARM_VILLAGE' 
WHERE claim_type = 'FARM_VILLAGE' AND system_type IS NULL;

-- 3. system_type을 NOT NULL로 변경 (데이터 마이그레이션 후)
-- 주의: 모든 데이터가 마이그레이션되었는지 확인 후 실행
-- SELECT COUNT(*) FROM myland_claims WHERE system_type IS NULL;
-- 위 쿼리 결과가 0이면 안전하게 진행 가능

ALTER TABLE myland_claims 
MODIFY COLUMN system_type ENUM('MYLAND', 'ADVANCED', 'FARM_VILLAGE') NOT NULL;

-- 4. 인덱스 추가 (성능 최적화)
CREATE INDEX idx_system_type ON myland_claims(system_type);
CREATE INDEX idx_system_world_chunk ON myland_claims(system_type, world, chunk_x, chunk_z);

-- 5. 마이그레이션 검증 쿼리
SELECT 
    system_type,
    COUNT(*) as count,
    COUNT(CASE WHEN resource_type IS NULL THEN 1 END) as resource_null_count,
    COUNT(CASE WHEN resource_type IS NOT NULL THEN 1 END) as resource_not_null_count
FROM myland_claims
GROUP BY system_type;

-- 예상 결과:
-- MYLAND: resource_null_count = count, resource_not_null_count = 0
-- ADVANCED: resource_null_count = 0, resource_not_null_count = count
-- FARM_VILLAGE: claim_type = 'FARM_VILLAGE'

-- ==========================================
-- 롤백 스크립트 (문제 발생 시 사용)
-- ==========================================
/*
-- 인덱스 제거
DROP INDEX idx_system_world_chunk ON myland_claims;
DROP INDEX idx_system_type ON myland_claims;

-- system_type 컬럼 제거
ALTER TABLE myland_claims DROP COLUMN system_type;
*/

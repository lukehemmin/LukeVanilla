# 토지 시스템 DB 마이그레이션 가이드

## 개요
토지 시스템의 데이터 구분을 명확히 하기 위해 `system_type` 컬럼을 추가합니다.

## 실행 전 백업
```bash
mysqldump -u root -p lukevanilla myland_claims > myland_claims_backup_$(date +%Y%m%d).sql
```

## 마이그레이션 실행
```bash
mysql -u root -p lukevanilla < migration_system_type.sql
```

## 단계별 실행 (권장)
안전을 위해 단계별로 실행하는 것을 권장합니다.

### 1단계: 컬럼 추가
```sql
ALTER TABLE myland_claims 
ADD COLUMN system_type ENUM('MYLAND', 'ADVANCED', 'FARM_VILLAGE') NULL
AFTER claim_type;
```

### 2단계: 데이터 마이그레이션
```sql
-- MyLand
UPDATE myland_claims 
SET system_type = 'MYLAND' 
WHERE resource_type IS NULL AND system_type IS NULL;

-- AdvancedLandClaiming
UPDATE myland_claims 
SET system_type = 'ADVANCED' 
WHERE resource_type IS NOT NULL AND system_type IS NULL;

-- FarmVillage
UPDATE myland_claims 
SET system_type = 'FARM_VILLAGE' 
WHERE claim_type = 'FARM_VILLAGE' AND system_type IS NULL;
```

### 3단계: 검증
```sql
-- NULL 값 확인 (0이어야 함)
SELECT COUNT(*) FROM myland_claims WHERE system_type IS NULL;

-- 분포 확인
SELECT 
    system_type,
    COUNT(*) as count,
    COUNT(CASE WHEN resource_type IS NULL THEN 1 END) as resource_null,
    COUNT(CASE WHEN resource_type IS NOT NULL THEN 1 END) as resource_not_null
FROM myland_claims
GROUP BY system_type;
```

### 4단계: NOT NULL 제약 추가
```sql
ALTER TABLE myland_claims 
MODIFY COLUMN system_type ENUM('MYLAND', 'ADVANCED', 'FARM_VILLAGE') NOT NULL;
```

### 5단계: 인덱스 추가
```sql
CREATE INDEX idx_system_type ON myland_claims(system_type);
CREATE INDEX idx_system_world_chunk ON myland_claims(system_type, world, chunk_x, chunk_z);
```

## 코드 수정 사항

마이그레이션 후 다음 파일의 쿼리를 수정해야 합니다:

### LandData.kt
```kotlin
// 변경 전
val query = "SELECT world, chunk_x, chunk_z, owner_uuid FROM myland_claims WHERE resource_type IS NULL"

// 변경 후
val query = "SELECT world, chunk_x, chunk_z, owner_uuid FROM myland_claims WHERE system_type = 'MYLAND'"
```

### AdvancedLandData.kt
```kotlin
// 변경 전
private const val ADVANCED_FILTER = "resource_type IS NOT NULL"

// 변경 후
private const val ADVANCED_FILTER = "system_type = 'ADVANCED'"
```

## 롤백 방법
```sql
-- 인덱스 제거
DROP INDEX idx_system_world_chunk ON myland_claims;
DROP INDEX idx_system_type ON myland_claims;

-- 컬럼 제거
ALTER TABLE myland_claims DROP COLUMN system_type;

-- 백업 복원 (필요시)
mysql -u root -p lukevanilla < myland_claims_backup_YYYYMMDD.sql
```

## 주의사항
1. **반드시 백업 먼저 수행**
2. 서버 점검 시간에 실행
3. 단계별로 실행하고 각 단계 검증
4. 테스트 서버에서 먼저 테스트
5. 마이그레이션 중 서버 중지 권장

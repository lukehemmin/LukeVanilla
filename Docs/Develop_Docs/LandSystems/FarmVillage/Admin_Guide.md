# FarmVillage 시스템 관리자 가이드

## 1. 개요
FarmVillage 시스템을 관리하는 관리자를 위한 완전한 가이드입니다. 마을 생성부터 일상적인 관리까지 모든 과정을 다룹니다.

## 2. 시스템 이해

### 2.1 FarmVillage란?
- **관리자 지정 농사마을**: 플레이어가 아닌 관리자가 생성하고 관리
- **MyLand 기반**: 기존 MyLand 클레이밍 시스템 위에 구축
- **번호 체계**: 각 마을은 고유 번호로 식별 (1번, 2번, 3번...)
- **공동 농업**: 여러 플레이어가 함께 농업 활동 수행

### 2.2 일반 마을과의 차이점
| 구분 | FarmVillage | AdvancedLand 마을 |
|------|-------------|-------------------|
| 생성 주체 | 관리자 | 플레이어 |
| 관리 방식 | 중앙 집중형 | 자율형 |
| 목적 | 농업 활동 | 일반 건축/커뮤니티 |
| 권한 관리 | LuckPerms 연동 | 자체 권한 시스템 |

## 3. 기본 명령어

### 3.1 마을 관리 명령어

#### 마을 생성
```
/농사마을 생성 <번호> <이름>
/farmvillage create <번호> <이름>
```

**예시:**
```
/농사마을 생성 1 벼농사마을
/농사마을 생성 2 밀농사마을
/farmvillage create 3 VegetableFarm
```

**주의사항:**
- 번호는 중복될 수 없음
- 마을 이름은 고유해야 함
- 번호는 1부터 시작하는 것을 권장

#### 마을 삭제
```
/농사마을 삭제 <번호>
/farmvillage delete <번호>
```

**예시:**
```
/농사마을 삭제 1
/farmvillage delete 1
```

**주의사항:**
- 삭제 시 모든 멤버 정보도 함께 삭제됨
- 토지 할당은 해제되지만 MyLand 클레이밍은 유지됨
- 되돌릴 수 없으므로 신중하게 결정

#### 마을 정보 조회
```
/농사마을 정보 <번호>
/농사마을 목록
/farmvillage info <번호>
/farmvillage list
```

**예시:**
```
/농사마을 정보 1
/농사마을 목록
```

### 3.2 멤버 관리 명령어

#### 멤버 추가
```
/농사마을 멤버추가 <번호> <플레이어>
/farmvillage addmember <번호> <플레이어>
```

**예시:**
```
/농사마을 멤버추가 1 steve
/농사마을 멤버추가 1 alex
```

#### 멤버 제거
```
/농사마을 멤버제거 <번호> <플레이어>
/farmvillage removemember <번호> <플레이어>
```

**예시:**
```
/농사마을 멤버제거 1 steve
```

### 3.3 토지 관리 명령어

#### 토지 할당
```
/농사마을 토지할당 <번호>
/farmvillage assignland <번호>
```

**사용법:**
1. 할당하고 싶은 청크에 서기
2. 명령어 실행
3. 해당 청크가 농사마을 토지로 지정됨

**전제조건:**
- 해당 청크가 이미 MyLand로 클레이밍되어 있어야 함
- 클레이밍되지 않은 땅은 할당 불가

#### 토지 할당 해제
```
/농사마을 토지해제
/farmvillage removeland
```

**사용법:**
1. 해제하고 싶은 청크에 서기
2. 명령어 실행
3. 농사마을 할당이 해제됨 (MyLand 클레이밍은 유지)

### 3.4 권한 관리 명령어

#### 권한 동기화
```
/농사마을 권한동기화 <번호>
/farmvillage syncperms <번호>
```

**언제 사용하나요?**
- LuckPerms 그룹을 수동으로 생성한 후
- 멤버 추가 후 권한이 제대로 적용되지 않을 때
- 권한 시스템에 문제가 있을 때

## 4. 마을 생성 및 설정 가이드

### 4.1 새 농사마을 생성 과정

#### 단계 1: 계획 수립
1. **마을 번호 결정**
   - 기존 마을과 중복되지 않는 번호 선택
   - 일반적으로 1번부터 순차적으로 할당

2. **마을 이름 결정**
   - 농업 테마에 맞는 이름 선택
   - 예: "벼농사마을", "밀농사마을", "채소농장"

3. **위치 선정**
   - 농업에 적합한 평지 지역
   - 물 접근이 용이한 곳
   - 플레이어들이 접근하기 쉬운 위치

#### 단계 2: 토지 준비
1. **토지 클레이밍**
   ```
   # 관리자 계정으로 각 청크에서 실행
   /땅 클레임
   ```

2. **농업 인프라 구축**
   - 물 공급 시설
   - 기본 농업 도구 저장소
   - 작물 저장 창고

#### 단계 3: 마을 생성
```
/농사마을 생성 1 벼농사마을
```

#### 단계 4: 토지 할당
각 청크에서 다음 명령어 실행:
```
/농사마을 토지할당 1
```

#### 단계 5: LuckPerms 그룹 생성
```bash
# LuckPerms 그룹 생성
/lp creategroup farmvillage.member.1

# 기본 권한 설정
/lp group farmvillage.member.1 permission set some.farm.permission true
```

#### 단계 6: 권한 동기화
```
/농사마을 권한동기화 1
```

### 4.2 마을 설정 체크리스트

#### 생성 직후 확인사항
- [ ] 마을 번호가 올바르게 할당되었는가?
- [ ] 마을 이름이 정확한가?
- [ ] 토지가 모두 할당되었는가?
- [ ] LuckPerms 그룹이 생성되었는가?
- [ ] 권한 동기화가 완료되었는가?

#### 운영 중 주기적 확인사항
- [ ] 멤버들이 농업 활동을 하고 있는가?
- [ ] 토지가 적절히 활용되고 있는가?
- [ ] 멤버 간 갈등은 없는가?
- [ ] 추가 토지가 필요한가?

## 5. 멤버 관리

### 5.1 멤버 추가 과정

#### 기본 멤버 추가
```
/농사마을 멤버추가 1 steve
```

실행 시 자동으로:
1. 데이터베이스에 멤버 정보 저장
2. LuckPerms 그룹에 플레이어 추가
3. 농사마을 권한 부여

#### 멤버 추가 후 확인
```bash
# LuckPerms에서 권한 확인
/lp user steve permission check farmvillage.member.1

# 농사마을 정보에서 멤버 확인
/농사마을 정보 1
```

### 5.2 멤버 제거 과정

#### 기본 멤버 제거
```
/농사마을 멤버제거 1 steve
```

실행 시 자동으로:
1. 데이터베이스에서 멤버 정보 삭제
2. LuckPerms 그룹에서 플레이어 제거
3. 농사마을 권한 제거

#### 멤버 제거 후 확인
```bash
# 권한이 제거되었는지 확인
/lp user steve permission check farmvillage.member.1

# 마을 정보에서 멤버가 제거되었는지 확인
/농사마을 정보 1
```

### 5.3 문제 있는 멤버 관리

#### 비활성 멤버 정리
```bash
# 1. 멤버 목록 확인
/농사마을 정보 1

# 2. 오랫동안 접속하지 않은 플레이어 확인
/seen <플레이어>

# 3. 필요시 멤버 제거
/농사마을 멤버제거 1 <플레이어>
```

#### 권한 남용 멤버 처리
```bash
# 1. 즉시 멤버 제거
/농사마을 멤버제거 1 <플레이어>

# 2. 필요시 추가 제재 (서버 전체 벤 등)
/ban <플레이어> 농사마을 규칙 위반
```

## 6. 토지 관리

### 6.1 토지 할당 전략

#### 효율적인 토지 할당
1. **연속된 청크 우선**
   - 농업 효율성을 위해 인접한 청크들을 할당
   - 멤버들이 협업하기 쉬운 구조

2. **용도별 구역 설정**
   - 작물별 구역 분리 (벼농사 구역, 밀농사 구역 등)
   - 공용 시설 구역 별도 설정

3. **확장성 고려**
   - 미래 확장을 위한 인접 청크 확보
   - 신규 멤버를 위한 여유 공간

#### 토지 할당 예시
```bash
# 벼농사마을 토지 할당 예시
/농사마을 토지할당 1    # 청크 (0, 0)
/농사마을 토지할당 1    # 청크 (0, 1)
/농사마을 토지할당 1    # 청크 (1, 0)
/농사마을 토지할당 1    # 청크 (1, 1)
```

### 6.2 토지 사용 모니터링

#### 정기적인 토지 점검
```bash
# 1. 각 농사마을 방문
/tp @s <좌표>

# 2. 토지 사용 현황 확인
# - 작물이 심어져 있는가?
# - 농업 시설이 적절히 설치되어 있는가?
# - 방치된 구역은 없는가?

# 3. 필요시 멤버와 소통
/msg <플레이어> 농사마을 토지 사용에 대해 문의드립니다.
```

#### 비효율적 토지 사용 해결
1. **멤버 교육**
   - 효율적인 농업 방법 안내
   - 농사마을 규칙 재안내

2. **토지 재할당**
   - 사용하지 않는 토지의 할당 해제
   - 활발한 멤버에게 우선 할당

3. **추가 지원**
   - 농업 도구 제공
   - 기초 자원 지원

## 7. LuckPerms 연동 관리

### 7.1 권한 그룹 설정

#### 농사마을별 그룹 생성
```bash
# 각 농사마을별로 그룹 생성
/lp creategroup farmvillage.member.1
/lp creategroup farmvillage.member.2
/lp creategroup farmvillage.member.3
```

#### 권한 설정 예시
```bash
# 농업 관련 권한 부여
/lp group farmvillage.member.1 permission set worldguard.region.bypass.farmvillage1 true
/lp group farmvillage.member.1 permission set essentials.warp.farm1 true
/lp group farmvillage.member.1 permission set mcmmo.skills.herbalism true

# 특별 권한 부여 (예: 동물 번식 등)
/lp group farmvillage.member.1 permission set minecraft.command.tp false
/lp group farmvillage.member.1 permission set some.farm.special.permission true
```

### 7.2 권한 문제 해결

#### 권한이 적용되지 않는 경우
```bash
# 1. 그룹 존재 확인
/lp listgroups

# 2. 플레이어의 그룹 확인
/lp user <플레이어> info

# 3. 권한 동기화 재실행
/농사마을 권한동기화 <번호>

# 4. LuckPerms 권한 직접 확인
/lp user <플레이어> permission check farmvillage.member.<번호>
```

#### 중복 권한 문제
```bash
# 플레이어가 여러 농사마을 그룹에 속한 경우
/lp user <플레이어> parent remove farmvillage.member.1
/lp user <플레이어> parent add farmvillage.member.2
```

## 8. 문제 해결 가이드

### 8.1 일반적인 문제들

#### 마을 생성이 안 되는 경우
**증상:** `/농사마을 생성` 명령어가 실행되지 않음

**해결 방법:**
1. 권한 확인
   ```bash
   /lp user <관리자> permission check farmvillage.admin
   ```

2. 번호 중복 확인
   ```bash
   /농사마을 목록
   ```

3. 데이터베이스 연결 확인
   ```bash
   /농사마을 데이터베이스테스트
   ```

#### 토지 할당이 안 되는 경우
**증상:** 토지 할당 명령어가 실패함

**해결 방법:**
1. MyLand 클레이밍 확인
   ```bash
   /땅 정보
   ```

2. 이미 할당된 토지인지 확인
   ```bash
   /농사마을 토지정보
   ```

3. 마을 존재 여부 확인
   ```bash
   /농사마을 정보 <번호>
   ```

#### 멤버 권한이 작동하지 않는 경우
**증상:** 멤버가 농사마을에서 작업할 수 없음

**해결 방법:**
1. LuckPerms 그룹 확인
   ```bash
   /lp group farmvillage.member.<번호> info
   ```

2. 플레이어의 그룹 소속 확인
   ```bash
   /lp user <플레이어> info
   ```

3. 권한 동기화 재실행
   ```bash
   /농사마을 권한동기화 <번호>
   ```

### 8.2 데이터베이스 관련 문제

#### 데이터 불일치 문제
```sql
-- 데이터베이스에서 직접 확인
SELECT * FROM farm_villages WHERE village_number = 1;
SELECT * FROM farm_village_members WHERE village_number = 1;
SELECT * FROM farm_village_lands WHERE village_number = 1;
```

#### 데이터 복구
```sql
-- 백업에서 복구
mysql -u root -p lukevanilla < backup_farmvillage.sql

-- 또는 특정 테이블만 복구
mysql -u root -p lukevanilla -e "
  DELETE FROM farm_villages WHERE village_number = 1;
  INSERT INTO farm_villages (village_number, village_name, created_by) 
  VALUES (1, '벼농사마을', 'admin_uuid');
"
```

## 9. 모니터링 및 통계

### 9.1 농사마을 활동 모니터링

#### 일일 체크리스트
- [ ] 각 마을 방문하여 활동 확인
- [ ] 신규 멤버 요청 검토
- [ ] 문제 신고 처리
- [ ] 토지 사용 현황 점검

#### 주간 체크리스트
- [ ] 비활성 멤버 정리
- [ ] 토지 효율성 검토
- [ ] 농업 인프라 점검 및 보수
- [ ] 멤버 피드백 수집

#### 월간 체크리스트
- [ ] 농사마을 전체 성과 평가
- [ ] 신규 농사마을 필요성 검토
- [ ] 시스템 업데이트 및 개선
- [ ] 백업 및 보안 점검

### 9.2 통계 수집

#### 기본 통계 확인
```sql
-- 전체 농사마을 수
SELECT COUNT(*) as total_villages FROM farm_villages WHERE is_active = true;

-- 전체 멤버 수
SELECT COUNT(*) as total_members FROM farm_village_members;

-- 마을별 멤버 수
SELECT v.village_name, COUNT(m.member_uuid) as member_count
FROM farm_villages v
LEFT JOIN farm_village_members m ON v.village_number = m.village_number
WHERE v.is_active = true
GROUP BY v.village_number, v.village_name
ORDER BY member_count DESC;

-- 마을별 토지 수
SELECT v.village_name, COUNT(l.id) as land_count
FROM farm_villages v
LEFT JOIN farm_village_lands l ON v.village_number = l.village_number
WHERE v.is_active = true
GROUP BY v.village_number, v.village_name
ORDER BY land_count DESC;
```

#### 활동 통계 분석
```sql
-- 최근 생성된 마을
SELECT village_name, created_at 
FROM farm_villages 
WHERE is_active = true 
ORDER BY created_at DESC 
LIMIT 5;

-- 최근 추가된 멤버
SELECT v.village_name, m.member_name, m.joined_at
FROM farm_village_members m
JOIN farm_villages v ON m.village_number = v.village_number
ORDER BY m.joined_at DESC
LIMIT 10;
```

## 10. 확장 및 최적화

### 10.1 시스템 확장

#### 새로운 농사마을 추가 시 고려사항
1. **서버 성능**: 너무 많은 마을은 성능에 영향
2. **관리 부담**: 관리자가 감당할 수 있는 수준
3. **플레이어 수요**: 실제 수요가 있는지 확인
4. **지역 분산**: 서버 전체에 균형있게 배치

#### 기능 확장 아이디어
- 마을별 특화 작물 시스템
- 농업 레벨 및 경험치 시스템
- 마을 간 교역 시스템
- 계절별 농업 이벤트

### 10.2 성능 최적화

#### 데이터베이스 최적화
```sql
-- 인덱스 확인 및 생성
SHOW INDEX FROM farm_villages;
SHOW INDEX FROM farm_village_members;
SHOW INDEX FROM farm_village_lands;

-- 성능이 느린 쿼리 최적화
EXPLAIN SELECT * FROM farm_village_members WHERE village_number = 1;
```

#### 캐싱 전략
- 농사마을 정보 캐싱 (자주 변경되지 않음)
- 멤버 목록 캐싱 (변경 시 캐시 무효화)
- LuckPerms 권한 캐싱 활용

## 11. 보안 관리

### 11.1 권한 보안

#### 관리자 권한 관리
```bash
# 필요한 관리자에게만 권한 부여
/lp user <관리자> permission set farmvillage.admin true

# 임시 권한 부여 (일정 시간 후 자동 제거)
/lp user <임시관리자> permission settemp farmvillage.admin true 24h
```

#### 로그 모니터링
```bash
# 중요한 관리 명령어 로그 확인
grep "farmvillage.*create\|farmvillage.*delete" logs/latest.log
grep "농사마을.*생성\|농사마을.*삭제" logs/latest.log
```

### 11.2 데이터 보안

#### 정기 백업
```bash
#!/bin/bash
# farmvillage_backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
mysqldump -u minecraft -p'password' lukevanilla \
  farm_villages farm_village_members farm_village_lands \
  > "/backup/farmvillage_$DATE.sql"

# 30일 이상 된 백업 파일 삭제
find /backup -name "farmvillage_*.sql" -mtime +30 -delete
```

#### 데이터 무결성 검사
```sql
-- 고아 멤버 데이터 확인 (마을이 없는 멤버)
SELECT m.* FROM farm_village_members m
LEFT JOIN farm_villages v ON m.village_number = v.village_number
WHERE v.village_number IS NULL;

-- 고아 토지 데이터 확인 (마을이 없는 토지)
SELECT l.* FROM farm_village_lands l
LEFT JOIN farm_villages v ON l.village_number = v.village_number
WHERE v.village_number IS NULL;
```

이 관리자 가이드를 통해 FarmVillage 시스템을 효과적으로 운영하고 관리할 수 있습니다.
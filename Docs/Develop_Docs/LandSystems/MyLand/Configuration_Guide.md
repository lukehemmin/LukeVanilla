# MyLand 시스템 설정 가이드

## 1. 개요
MyLand 시스템의 모든 설정 옵션과 구성 방법을 설명합니다.

## 2. 기본 설정 파일 (config.yml)

### 2.1 전체 설정 예시
```yaml
# MyLand 시스템 설정
land:
  # 클레이밍 가능 구역 설정
  claimable-area:
    enabled: true                # 클레이밍 구역 제한 활성화
    min-x: -5000                # 최소 X 좌표
    max-x: 5000                 # 최대 X 좌표
    min-z: -5000                # 최소 Z 좌표
    max-z: 5000                 # 최대 Z 좌표
    worlds:                     # 클레이밍 가능한 월드 목록
      - "world"
      - "world_nether"
      - "world_the_end"
  
  # 클레이밍 제한 설정
  limits:
    max-claims: 50              # 플레이어당 최대 클레이밍 수 (-1: 무제한)
    enable-limit: true          # 클레이밍 제한 활성화
  
  # 보호 설정
  protection:
    block-break: true           # 블록 파괴 보호
    block-place: true           # 블록 설치 보호
    interact: true              # 상호작용 보호
    pvp: false                  # PvP 보호 (false = PvP 허용)
    explosion: false            # 폭발 보호 (false = 폭발 방지)
    fire-spread: false          # 화재 확산 방지
    water-flow: true            # 물 흐름 허용
    lava-flow: true             # 용암 흐름 허용
    
  # 상호작용 가능한 블록 설정
  interaction:
    containers:                 # 컨테이너 블록들
      - "CHEST"
      - "TRAPPED_CHEST"
      - "ENDER_CHEST"
      - "SHULKER_BOX"
      - "BARREL"
      - "HOPPER"
      - "DROPPER"
      - "DISPENSER"
      - "FURNACE"
      - "BLAST_FURNACE"
      - "SMOKER"
      - "BREWING_STAND"
      - "ENCHANTING_TABLE"
      - "ANVIL"
      - "CHIPPED_ANVIL"
      - "DAMAGED_ANVIL"
    
    redstone-devices:           # 레드스톤 장치들
      - "LEVER"
      - "STONE_BUTTON"
      - "OAK_BUTTON"
      - "SPRUCE_BUTTON"
      - "BIRCH_BUTTON"
      - "JUNGLE_BUTTON"
      - "ACACIA_BUTTON"
      - "DARK_OAK_BUTTON"
      - "CRIMSON_BUTTON"
      - "WARPED_BUTTON"
      - "STONE_PRESSURE_PLATE"
      - "OAK_PRESSURE_PLATE"
      - "SPRUCE_PRESSURE_PLATE"
      - "BIRCH_PRESSURE_PLATE"
      - "JUNGLE_PRESSURE_PLATE"
      - "ACACIA_PRESSURE_PLATE"
      - "DARK_OAK_PRESSURE_PLATE"
      - "CRIMSON_PRESSURE_PLATE"
      - "WARPED_PRESSURE_PLATE"
      - "LIGHT_WEIGHTED_PRESSURE_PLATE"
      - "HEAVY_WEIGHTED_PRESSURE_PLATE"
      - "REDSTONE_TORCH"
      - "REDSTONE_WALL_TORCH"
      - "COMPARATOR"
      - "REPEATER"
    
    doors:                      # 문 및 게이트
      - "OAK_DOOR"
      - "SPRUCE_DOOR"
      - "BIRCH_DOOR"
      - "JUNGLE_DOOR"
      - "ACACIA_DOOR"
      - "DARK_OAK_DOOR"
      - "CRIMSON_DOOR"
      - "WARPED_DOOR"
      - "IRON_DOOR"
      - "OAK_FENCE_GATE"
      - "SPRUCE_FENCE_GATE"
      - "BIRCH_FENCE_GATE"
      - "JUNGLE_FENCE_GATE"
      - "ACACIA_FENCE_GATE"
      - "DARK_OAK_FENCE_GATE"
      - "CRIMSON_FENCE_GATE"
      - "WARPED_FENCE_GATE"
      - "OAK_TRAPDOOR"
      - "SPRUCE_TRAPDOOR"
      - "BIRCH_TRAPDOOR"
      - "JUNGLE_TRAPDOOR"
      - "ACACIA_TRAPDOOR"
      - "DARK_OAK_TRAPDOOR"
      - "CRIMSON_TRAPDOOR"
      - "WARPED_TRAPDOOR"
      - "IRON_TRAPDOOR"

  # 메시지 설정
  messages:
    prefix: "&6[MyLand]&f "     # 메시지 접두사
    
    # 성공 메시지
    success:
      claim: "&a성공적으로 클레이밍했습니다!"
      unclaim: "&a클레이밍을 해제했습니다!"
      add-member: "&a{player}님을 친구로 추가했습니다!"
      remove-member: "&a{player}님을 친구에서 제거했습니다!"
      
    # 오류 메시지
    error:
      already-claimed: "&c이미 클레이밍된 청크입니다!"
      not-claimed: "&c클레이밍되지 않은 청크입니다!"
      not-owner: "&c이 땅의 소유자만 사용할 수 있습니다!"
      not-in-area: "&c이 구역에서는 클레이밍할 수 없습니다!"
      max-claims: "&c최대 클레이밍 수에 도달했습니다! (최대: {max}개)"
      already-member: "&c이미 친구로 등록된 플레이어입니다!"
      not-member: "&c친구로 등록되지 않은 플레이어입니다!"
      cannot-add-owner: "&c땅 소유자는 친구로 추가할 수 없습니다!"
      player-not-found: "&c플레이어를 찾을 수 없습니다!"
      database-error: "&c데이터베이스 오류가 발생했습니다!"
      no-permission: "&c권한이 없습니다!"
      
    # 보호 메시지
    protection:
      block-break: "&c이 땅에서는 블록을 파괴할 수 없습니다!"
      block-place: "&c이 땅에서는 블록을 설치할 수 없습니다!"
      interact: "&c이 땅에서는 상호작용할 수 없습니다!"
      
    # 정보 메시지
    info:
      chunk-info: |
        &6=== 땅 정보 ===
        &7소유자: &f{owner}
        &7청크: &f({x}, {z})
        &7월드: &f{world}
        &7클레이밍 시각: &f{date}
        &7멤버: &f{members}
      
      player-claims: |
        &6=== 내 땅 목록 ===
        &7총 {count}개의 땅을 소유하고 있습니다.
        {claims}
      
      member-list: |
        &6=== 친구 목록 ===
        &7이 땅의 친구: &f{members}

  # 데이터베이스 설정 (선택사항 - 기본값은 Main에서 설정)
  database:
    use-custom: false           # 커스텀 데이터베이스 설정 사용
    host: "localhost"
    port: 3306
    name: "myland"
    user: "root"
    password: ""
    
  # 성능 설정
  performance:
    cache-size: 1000            # 캐시 크기
    cache-expire-time: 300      # 캐시 만료 시간 (초)
    batch-size: 100             # 배치 처리 크기
    
  # 로깅 설정
  logging:
    enable-debug: false         # 디버그 로그 활성화
    log-claims: true            # 클레이밍 로그
    log-member-changes: true    # 멤버 변경 로그
    log-protection: false       # 보호 이벤트 로그 (스팸 주의)
```

## 3. 세부 설정 가이드

### 3.1 클레이밍 구역 설정

#### 기본 사각형 구역
```yaml
land:
  claimable-area:
    enabled: true
    min-x: -2000
    max-x: 2000
    min-z: -2000
    max-z: 2000
    worlds:
      - "world"
```

#### 무제한 구역 (모든 곳에서 클레이밍 가능)
```yaml
land:
  claimable-area:
    enabled: false
```

#### 다중 월드 지원
```yaml
land:
  claimable-area:
    enabled: true
    min-x: -5000
    max-x: 5000
    min-z: -5000
    max-z: 5000
    worlds:
      - "world"           # 오버월드
      - "world_nether"    # 네더
      - "world_the_end"   # 엔드
      - "custom_world"    # 커스텀 월드
```

### 3.2 클레이밍 제한 설정

#### 제한 없음
```yaml
land:
  limits:
    max-claims: -1
    enable-limit: false
```

#### 플레이어당 제한
```yaml
land:
  limits:
    max-claims: 20      # 플레이어당 최대 20개 청크
    enable-limit: true
```

### 3.3 보호 설정 커스터마이징

#### 최대 보호 (권장)
```yaml
land:
  protection:
    block-break: true
    block-place: true
    interact: true
    pvp: false          # PvP 방지
    explosion: false    # 폭발 방지
    fire-spread: false  # 화재 확산 방지
    water-flow: true    # 물 흐름 허용
    lava-flow: true     # 용암 흐름 허용
```

#### PvP 서버용 설정
```yaml
land:
  protection:
    block-break: true
    block-place: true
    interact: true
    pvp: true           # PvP 허용
    explosion: true     # 폭발 허용
    fire-spread: true   # 화재 확산 허용
    water-flow: true
    lava-flow: true
```

#### 건축 서버용 설정
```yaml
land:
  protection:
    block-break: true
    block-place: true
    interact: false     # 상호작용 허용 (레버, 버튼 등)
    pvp: false
    explosion: false
    fire-spread: false
    water-flow: true
    lava-flow: false    # 용암 흐름 방지 (건축물 보호)
```

### 3.4 메시지 커스터마이징

#### 한국어 메시지
```yaml
land:
  messages:
    prefix: "&6[내땅]&f "
    
    success:
      claim: "&a땅을 성공적으로 차지했습니다!"
      unclaim: "&a땅 소유권을 포기했습니다!"
      add-member: "&a{player}님을 친구로 추가했습니다!"
      remove-member: "&a{player}님을 친구에서 제거했습니다!"
```

#### 영어 메시지
```yaml
land:
  messages:
    prefix: "&6[MyLand]&f "
    
    success:
      claim: "&aSuccessfully claimed this chunk!"
      unclaim: "&aSuccessfully unclaimed this chunk!"
      add-member: "&aAdded {player} as a friend!"
      remove-member: "&aRemoved {player} from friends!"
```

#### 사용자 정의 메시지 포맷
```yaml
land:
  messages:
    info:
      chunk-info: |
        &6====== &lLAND INFO &r&6======
        &e▶ &7Owner: &b{owner}
        &e▶ &7Location: &b({x}, {z}) in {world}
        &e▶ &7Claimed: &b{date}
        &e▶ &7Friends: &b{members}
        &6========================
```

### 3.5 상호작용 블록 커스터마이징

#### 최소한의 상호작용 허용
```yaml
land:
  interaction:
    containers:
      - "CHEST"
      - "ENDER_CHEST"
    
    redstone-devices:
      - "LEVER"
      - "STONE_BUTTON"
    
    doors:
      - "OAK_DOOR"
      - "IRON_DOOR"
```

#### 모든 상호작용 허용
```yaml
land:
  protection:
    interact: false     # 상호작용 보호 비활성화
```

### 3.6 성능 최적화 설정

#### 고성능 서버용
```yaml
land:
  performance:
    cache-size: 5000        # 더 큰 캐시
    cache-expire-time: 600  # 더 긴 캐시 시간
    batch-size: 500         # 더 큰 배치 크기
```

#### 저사양 서버용
```yaml
land:
  performance:
    cache-size: 500         # 작은 캐시
    cache-expire-time: 60   # 짧은 캐시 시간
    batch-size: 50          # 작은 배치 크기
```

## 4. 권한 설정 (plugin.yml)

### 4.1 기본 권한 구조
```yaml
permissions:
  myland.*:
    description: "모든 MyLand 권한"
    default: false
    children:
      myland.use: true
      myland.admin: true
      
  myland.use:
    description: "기본 MyLand 사용 권한"
    default: true
    children:
      myland.claim: true
      myland.unclaim: true
      myland.info: true
      myland.list: true
      myland.member: true
      
  myland.claim:
    description: "땅 클레이밍 권한"
    default: true
    
  myland.unclaim:
    description: "땅 클레이밍 해제 권한"
    default: true
    
  myland.info:
    description: "땅 정보 조회 권한"
    default: true
    
  myland.list:
    description: "땅 목록 조회 권한"
    default: true
    
  myland.member:
    description: "친구 관리 권한"
    default: true
    children:
      myland.member.add: true
      myland.member.remove: true
      myland.member.list: true
      
  myland.member.add:
    description: "친구 추가 권한"
    default: true
    
  myland.member.remove:
    description: "친구 제거 권한"
    default: true
    
  myland.member.list:
    description: "친구 목록 권한"
    default: true
    
  myland.admin:
    description: "관리자 권한"
    default: op
    children:
      myland.admin.bypass: true
      myland.admin.reload: true
      myland.admin.force: true
      
  myland.admin.bypass:
    description: "보호 무시 권한"
    default: op
    
  myland.admin.reload:
    description: "설정 리로드 권한"
    default: op
    
  myland.admin.force:
    description: "강제 실행 권한"
    default: op
```

### 4.2 LuckPerms 설정 예시

#### 기본 플레이어 그룹
```
/lp group default permission set myland.use true
/lp group default permission set myland.claim true
/lp group default permission set myland.unclaim true
/lp group default permission set myland.info true
/lp group default permission set myland.list true
/lp group default permission set myland.member true
```

#### VIP 플레이어 그룹 (더 많은 클레이밍)
```
/lp group vip permission set myland.use true
/lp group vip meta set max-claims 100
```

#### 관리자 그룹
```
/lp group admin permission set myland.admin true
```

## 5. 데이터베이스 설정

### 5.1 MySQL 설정 (권장)
```yaml
# Main 플러그인의 config.yml에서 설정
database:
  host: "localhost"
  port: 3306
  name: "lukevanilla"
  user: "minecraft"
  password: "secure_password"
  
  # 커넥션 풀 설정
  pool:
    maximum-pool-size: 10
    minimum-idle: 5
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

### 5.2 SQLite 설정 (테스트용)
```yaml
database:
  type: "sqlite"
  file: "plugins/LukeVanilla/data.db"
```

## 6. 설정 적용 및 관리

### 6.1 설정 리로드
```bash
# 게임 내에서
/땅 리로드

# 또는 서버 콘솔에서
lukereload myland
```

### 6.2 설정 검증
```bash
# 설정 파일 문법 검사
/땅 설정확인

# 데이터베이스 연결 테스트
/땅 데이터베이스테스트
```

### 6.3 설정 백업
```bash
# 설정 파일 백업
cp config.yml config.yml.backup.$(date +%Y%m%d_%H%M%S)

# 데이터베이스 백업
mysqldump -u minecraft -p lukevanilla > backup_$(date +%Y%m%d_%H%M%S).sql
```

## 7. 고급 설정

### 7.1 조건부 설정
```yaml
# 월드별 다른 설정 (플러그인에서 구현 필요)
land:
  world-specific:
    world:
      max-claims: 50
      protection:
        pvp: false
    world_pvp:
      max-claims: 20
      protection:
        pvp: true
        explosion: true
```

### 7.2 시간 기반 설정
```yaml
# 특정 시간대에 다른 규칙 적용 (플러그인에서 구현 필요)
land:
  time-based:
    night-protection:
      enabled: true
      start-time: 18000  # 밤 시작
      end-time: 6000     # 낮 시작
      protection:
        pvp: false       # 밤에는 PvP 방지
```

### 7.3 이벤트 기반 설정
```yaml
# 이벤트 중 다른 규칙 적용
land:
  events:
    war-mode:
      enabled: false
      protection:
        pvp: true
        explosion: true
        block-break: false  # 블록은 보호
```

## 8. 문제 해결

### 8.1 일반적인 설정 문제

#### 설정이 적용되지 않음
```bash
# 1. 설정 파일 문법 확인
# 2. 서버 재시작 또는 리로드
# 3. 권한 확인
/lp user <플레이어> permission check myland.use
```

#### 클레이밍이 안 됨
```yaml
# config.yml 확인 사항
land:
  claimable-area:
    enabled: true  # false로 되어있지 않은지 확인
    worlds:
      - "world"    # 현재 월드가 목록에 있는지 확인
```

#### 보호가 작동하지 않음
```yaml
# 보호 설정 확인
land:
  protection:
    block-break: true  # false로 되어있으면 보호 안 됨
    block-place: true
    interact: true
```

### 8.2 성능 문제

#### 서버 랙 발생
```yaml
# 성능 설정 조정
land:
  performance:
    cache-size: 500      # 캐시 크기 줄이기
    batch-size: 50       # 배치 크기 줄이기
  
  logging:
    log-protection: false  # 보호 로그 비활성화
```

#### 메모리 사용량 증가
```yaml
land:
  performance:
    cache-expire-time: 60  # 캐시 만료 시간 단축
    cache-size: 300        # 캐시 크기 줄이기
```

## 9. 모니터링 및 유지보수

### 9.1 로그 모니터링
```bash
# 중요한 로그 패턴
tail -f logs/latest.log | grep "MyLand"
tail -f logs/latest.log | grep "CLAIM"
tail -f logs/latest.log | grep "ERROR"
```

### 9.2 데이터베이스 모니터링
```sql
-- 클레이밍 통계
SELECT COUNT(*) as total_claims FROM land_claims;
SELECT owner_name, COUNT(*) as claim_count FROM land_claims GROUP BY owner_uuid ORDER BY claim_count DESC LIMIT 10;

-- 멤버 통계
SELECT COUNT(*) as total_members FROM land_members;

-- 이력 통계
SELECT action_type, COUNT(*) as action_count FROM land_history GROUP BY action_type;
```

### 9.3 정기 백업 스크립트
```bash
#!/bin/bash
# backup_myland.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backup/myland/$DATE"
mkdir -p $BACKUP_DIR

# 설정 파일 백업
cp /server/plugins/LukeVanilla/config.yml $BACKUP_DIR/

# 데이터베이스 백업
mysqldump -u minecraft -p'password' lukevanilla land_claims land_members land_history > $BACKUP_DIR/database.sql

# 7일 이상 된 백업 삭제
find /backup/myland -type d -mtime +7 -exec rm -rf {} \;
```

이 설정 가이드를 통해 MyLand 시스템을 운영 환경에 맞게 최적화할 수 있습니다.
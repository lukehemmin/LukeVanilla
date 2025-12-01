# Nexo 아이템 크래프팅 문제 해결 가이드

## 문제 설명
Nexo 커스텀 아이템을 제작할 때 기본 아이템(예: 네더라이트 검)으로 변환되는 문제가 발생할 수 있습니다. 이는 주로 아이템 생성 과정에서 Nexo의 NBT 데이터가 손실되어 발생합니다.

## 해결책

### 1. StatsSystem과 Nexo의 충돌 방지
이 문제는 StatsSystem이 메타데이터를 추가하는 과정에서 Nexo 아이템의 중요한 NBT 데이터를 덮어쓰기 때문에 발생합니다. 이를 해결하기 위해 다음 단계를 수정했습니다:

- `ItemStatsListener.kt`에서 Nexo 아이템을 감지하고 처리하는 코드 개선
- NexoAPI를 직접 사용하여 아이템 확인 및 생성

### 2. 디버깅 방법
문제가 계속 발생하는 경우 다음 단계로 디버깅할 수 있습니다:

1. StatsSystem의 로그 활성화:
   ```kotlin
   // StatsSystem.kt에서
   var isLoggingEnabled: Boolean = true
   ```

2. 콘솔 로그 확인:
   - `[StatsSystem] Nexo 아이템 레시피 감지: {아이템ID}` - Nexo 아이템 감지
   - `[StatsSystem] Nexo API로 아이템 감지: {아이템ID}` - API를 통한 확인
   - `[StatsSystem] Nexo 아이템 수정 완료: {아이템ID}` - 수정 성공

### 3. 제작 과정 확인
크래프팅 과정에서 Nexo 아이템이 어떻게 처리되는지 확인:

1. 작업대에서 Nexo 아이템을 제작합니다.
2. 시스템은 Nexo 아이템을 감지하고 메타데이터 설정을 건너뜁니다.
3. 제작 후 1틱 후에 인벤토리에서 새로 생성된 아이템을 올바른 Nexo 아이템으로 교체합니다.

## NexoAPI 활용 방법

```java
// Nexo-ItemID에서 ItemBuilder 가져오기
ItemBuilder builder = NexoItems.itemFromId(itemID);

// ItemStack에서 Nexo-ItemID 가져오기
String itemId = NexoItems.idFromItem(itemstack);

// ItemBuilder에서 아이템 생성 (리플렉션 활용)
Method createMethod = builder.getClass().getMethod("create");
ItemStack newItem = (ItemStack) createMethod.invoke(builder);
```

## 문제 해결 팁
- 로그를 확인하여 어느 단계에서 문제가 발생하는지 파악합니다.
- 작업대를 닫고 다시 열어보세요.
- 서버를 재시작하고 다시 시도해보세요.
- Nexo 플러그인이 최신 버전인지 확인하세요.

## 관리자를 위한 추가 정보
Nexo API 문서가 제한적인 경우 리플렉션을 사용하여 필요한 메서드에 접근해야 할 수 있습니다.
아래는 리플렉션 코드 예제입니다:

```kotlin
// ItemBuilder에서 create 메서드 찾기
val builder = NexoItems.itemFromId(id)
val createMethod = builder.javaClass.getMethod("create")
val item = createMethod.invoke(builder) as ItemStack
```

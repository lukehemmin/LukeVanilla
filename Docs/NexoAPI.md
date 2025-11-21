# Nexo API 사용 가이드

## 저장소 및 의존성

```groovy
repositories {
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    compileOnly("com.nexomc:nexo:1.1.0")
}
```

## 플러그인에 Nexo 지원 추가하기

### 저장소 및 의존성 정보

위의 코드 블록에서 저장소와 의존성 정보를 확인할 수 있습니다.

모든 메소드와 자세한 기능 설명, 파라미터에 대한 정보는 실제 클래스에서 찾을 수 있습니다. IDE에서 클래스를 열어 전체 목록을 확인하세요.

## 사용 예제

Nexo는 ItemBuilder 클래스를 중심으로 구축되어 아이템을 쉽게 생성할 수 있게 해줍니다. 플러그인이 시작될 때 각 유형의 아이템에 대한 빌더를 생성하기 위해 구성을 분석합니다. 각 빌더는 아이템스택을 생성하는 데 사용할 수 있습니다.

### NexoItems 클래스:

#### Nexo-ItemID에서 ItemBuilder 가져오기

```java
NexoItems.itemFromId(itemID);
```

#### ItemStack에서 Nexo-ItemID 가져오기

아이템스택이 NexoItem인지 확인하는 데 사용할 수 있습니다(Nexo-ItemID가 존재하지 않으면 null을 반환합니다).

```java
NexoItems.idFromItem(itemstack);
```

### 커스텀 블록 및 가구

#### NexoBlock 배치하기

주어진 위치에 NexoBlock 배치:

```java
NexoBlocks.place(itemID, location)
```

주어진 위치에 NexoFurniture 배치, 회전을 위해 플레이어를 선택적으로 설정:

```java
NexoFurniture.place(itemID, location, @Nullable player)
```

### 메카닉(Mechanics):

Nexo를 사용하면 플러그인에 자체 메카닉을 추가할 수 있습니다. 새로운 메카닉을 추가하거나 기존 메카닉을 확장할 수 있습니다.

Java와 Kotlin 모두를 위한 예제 저장소는 [여기](https://github.com/nexomc-community/example-mechanic)에서 찾을 수 있습니다.

onEnable 메소드나 원하는 곳에서 메카닉을 등록할 수 있습니다. 이렇게 하면 Nexo가 자체 메카닉을 등록할 때 해당 메카닉이 등록되고 아이템에 대해 분석됩니다.

메카닉은 속성과 메소드가 있는 Mechanic 클래스로 구성됩니다. MechanicFactory는 글로벌 Mechanic 속성에 대한 구문 분석 메소드와 아이템 -> 메카닉 연결로 구성됩니다.

**NexoMechanicsRegisteredEvent** - Nexo가 메카닉을 로드/리로드할 때 호출됩니다.

**NexoItemsLoadedEvent** - Nexo가 NexoItems 로드/리로드를 마쳤을 때 호출됩니다.

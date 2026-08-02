# Cobblemon Monitor 확장 설계

이 문서는 현재 순수 클라이언트 구현과 향후 서버 보조 모드 설치를 고려한
Cobblemon Monitor의 식별 정보 및 확장 계획을 기록한다.

## 핵심 결정

모든 감지 이벤트는 단순히 "무언가 발생했다"고 처리하지 않는다.
Dimension과 BlockPos를 포함한 위치 식별자를 이벤트 키로 사용한다.

```text
EventKey = dimension + block position + event type
```

따라서 여러 목장이나 여러 스낵이 동시에 존재해도 서로 다른 이벤트로 구분한다.

## 목장 감시 대상 지정

목장을 자동으로 전부 감시하는 모드와 사용자가 지정한 목장만 감시하는 모드를
분리한다. 기본값은 사용자가 등록한 목장만 감시하는 선택 모드로 한다.

클라이언트 명령어 예시:

```text
/cobble-monitor pasture add <x> <y> <z>
/cobble-monitor pasture add ~ ~ ~
/cobble-monitor pasture add looking
/cobble-monitor pasture remove <x> <y> <z>
/cobble-monitor pasture list
/cobble-monitor pasture clear
/cobble-monitor pasture mode selected
/cobble-monitor pasture mode nearby
```

동작 규칙:

- 좌표를 지정하면 현재 차원과 함께 저장한다.
- `looking`은 플레이어가 바라보는 블록이 실제 Cobblemon Pasture인지 확인한 뒤 등록한다.
- 등록되지 않은 블록은 알 감시 대상이 아니다.
- `selected` 모드에서는 등록한 목장만 감시한다.
- `nearby` 모드에서는 현재 로드된 주변 목장을 감시할 수 있지만, 명시적으로
  선택한 목장과 혼동하지 않도록 별도 모드로 둔다.
- `list`는 차원, 좌표, 현재 로드 여부, 마지막으로 확인한 알 상태를 표시한다.
- `remove`와 `clear`는 클라이언트 설정만 변경하며 월드나 목장 블록에는 영향을 주지 않는다.

설정 구조 예시:

```json
{
  "pastureMonitorMode": "selected",
  "monitoredPastures": [
    {
      "dimension": "minecraft:overworld",
      "x": 120,
      "y": 65,
      "z": -318
    }
  ]
}
```

명령어는 Fabric Client Command API로 등록하여 서버 설치 없이 사용할 수 있게 한다.
서버 명령어로 만들지 않으므로 Vanilla 서버에서도 명령어 등록을 요구하지 않는다.

## Pasture Egg

### 현재 클라이언트에서 확인 가능한 정보

Cobbreeding 2.2.2는 Pasture Block에 다음 BlockState를 추가한다.

```text
HAS_EGG
BREEDING_ACTIVATED
```

`HAS_EGG`는 해당 목장에 알이 하나 이상 있는지만 나타내는 Boolean이다.

```text
false -> true: 알이 하나 이상 생김
true -> true: 알이 추가되어도 변화 없음
true -> false: 알이 모두 없어짐
```

따라서 다음 정보는 `HAS_EGG`만으로는 알 수 없다.

- 현재 알의 정확한 개수
- 새로 생성된 알이 몇 번째 슬롯인지
- 각 알의 포켓몬 종족
- 두 부모 포켓몬의 정보

Pasture의 위치는 BlockPos로 식별할 수 있지만, `HAS_EGG`만으로 여러 알을 각각
구분할 수는 없다.

### 클라이언트 우선 구현 방침

1. 로드된 Pasture BlockEntity를 위치별로 추적한다.
2. 최초 로드 상태는 기준값으로만 저장한다.
3. 이후 `HAS_EGG false -> true` 변화를 감지한다.
4. 클라이언트에 Pasture 인벤토리 NBT가 동기화되어 있으면 알 ItemStack의
   Cobbreeding `POKEMON_PROPERTIES`를 읽는다.
5. 종족을 확인할 수 있을 때만 종족 정보를 외부 알림에 포함한다.
6. 종족을 확인할 수 없으면 잘못된 종족을 추측하지 않고 로그에 원인을 남긴다.

예상 메타데이터:

```text
Pasture Position: dimension, x, y, z
Egg Species: 확인 가능한 경우에만
Egg Count: 동기화된 인벤토리를 읽을 수 있는 경우에만
Metadata Source: BlockState / BlockEntity NBT
```

현재 분석에서는 Cobbreeding의 실시간 알 생성용 S2C 인벤토리 패킷이나 전용
알 생성 이벤트를 확인하지 못했다. 따라서 순수 클라이언트만으로 모든 알에 대해
포켓몬 종족을 반드시 보장하는 것은 현재 API 구조상 불가능하다.

엄격 모드에서는 종족 정보를 읽지 못한 알에 대해 외부 알림을 보내지 않고,
다음 로그만 남기는 방안을 사용한다.

```text
Pasture egg detected, but egg species was not synchronized to the client
```

이렇게 해야 알 수 없는 포켓몬을 잘못 알리는 문제가 없다.

## Snack Consumed

### 이벤트 식별

Cobblemon은 성공적인 Poke Snack 처리 시 다음 S2C 패킷을 보낸다.

```text
cobblemon:poke_snack_block_particles
```

패킷에는 다음 정보가 있다.

- Snack BlockPos
- 영향을 받은 Pokemon 위치

따라서 Snack BlockPos를 기준으로 어떤 스낵인지 식별할 수 있다.

### Snack BlockEntity 정보

Cobblemon `PokeSnackBlockEntity`는 다음 정보를 저장한다.

- `PlacedBy`: 설치한 플레이어 UUID
- `FoodColour`
- `BaitEffects`
- `Ingredients`
- `AmountSpawned`

클라이언트가 해당 BlockEntity 데이터를 가지고 있으면 다음 우선순위로 알림을
구성한다.

1. 설치자 닉네임
2. 설치자 UUID
3. 스낵 효과
4. 스낵 재료
5. Snack BlockPos
6. 영향을 받은 Pokemon의 Species, Level, Shiny, Gender, UUID

설치자 닉네임을 클라이언트의 플레이어 목록에서 찾지 못하면 UUID를 사용한다.

예시:

```text
🍪 Snack Consumed
Owner: PlayerName
Effects: Shiny Boost, Dragon Spawn Boost
Snack Position: overworld 120 65 -318
Estimated Pokemon: Gengar
Level: 43
Shiny: false
Gender: Female
```

패킷 자체에는 Pokemon UUID와 스낵 효과가 없으므로, 패킷 수신 후 BlockEntity와
주변 PokemonEntity를 별도로 확인한다. Pokemon을 찾는 검색은 패킷이 수신된
경우에만 짧게 실행하며 매 Tick 전체 엔티티 순회를 하지 않는다.

## Discord Embed 알림

Discord Webhook은 긴 메타데이터를 일반 문자열 하나로 보내지 않고 Embed 카드로
구성한다. ntfy는 모바일 알림 목록에서 빠르게 읽을 수 있도록 짧은 평문을 사용한다.

공통 Embed 구조:

```json
{
  "username": "Cobble Monitor",
  "embeds": [
    {
      "title": "🍪 Snack Consumed",
      "description": "A wild Pokemon consumed a Poke Snack.",
      "color": 16753920,
      "fields": [
        {"name": "Pokemon", "value": "Gengar", "inline": true},
        {"name": "Level", "value": "43", "inline": true},
        {"name": "Shiny", "value": "false", "inline": true},
        {"name": "Owner", "value": "PlayerName", "inline": true},
        {"name": "Effects", "value": "Shiny Boost", "inline": false},
        {"name": "Position", "value": "overworld 120, 65, -318", "inline": false}
      ],
      "footer": {"text": "Cobble Monitor"},
      "timestamp": "2026-08-03T00:00:00Z"
    }
  ]
}
```

이벤트별 기본 표현:

| 이벤트 | 제목 | 기본 색상 | 주요 필드 |
|---|---|---:|---|
| Night | 🌙 Night Started | 남색 | 시간, 차원 |
| Pasture Egg | 🥚 Pasture Egg Created | 초록색 | 종족, 알 개수, 목장 위치 |
| Snack | 🍪 Snack Consumed | 주황색 | 포켓몬, 레벨, Shiny, 설치자, 효과, 스낵 위치 |

메타데이터를 확인하지 못한 경우에는 임의의 값을 넣지 않는다. 예를 들어 포켓몬을
위치 기반으로 찾은 경우에는 `Estimated Pokemon`을 별도 필드로 표시한다.

Embed 생성은 NotificationService에서 이벤트 타입별 Formatter로 분리한다.

```text
Event Metadata
    -> NotificationFormatter
        -> DiscordEmbedPayload
        -> ntfyPlainTextPayload
```

Discord 필드 값은 길이 제한을 적용하고, 외부 서비스 요청 실패가 게임 스레드에
영향을 주지 않도록 기존 비동기 HTTP 전송 구조를 유지한다.

## 서버 확장 시 해결되는 문제

향후 서버에도 보조 모드를 설치할 수 있게 되면 다음 전용 Payload를 추가한다.

### PastureEggCreatedPayload

```text
dimension
pasturePosition
eggId
eggSlot
eggCount
species
form
parentA
parentB
createdAt
```

서버가 알이 생성되는 정확한 시점에 해당 Payload를 해당 목장을 볼 수 있는
클라이언트 또는 전체 플레이어에게 전송한다. 이 방식이면 `HAS_EGG`의 한계인
알 개수와 개별 알 식별 문제를 해결할 수 있다.

### SnackConsumedPayload

```text
dimension
snackPosition
placedByUuid
placedByName
ingredients
effects
pokemonUuid
species
level
shiny
gender
pokemonPosition
consumedAt
```

서버의 `POKE_SNACK_SPAWN_POKEMON_POST` 이벤트를 직접 구독하면 추정 포켓몬이
아닌 정확한 PokemonEntity를 전달할 수 있다. 또한 플레이어 거리 밖에서 발생한
소비도 서버가 정책에 따라 전송할 수 있다.

## 단계별 개발 계획

### Phase 1: 순수 클라이언트

- 목장 위치별 `HAS_EGG` 변화 감지
- 가능한 경우에만 동기화된 알 종족·개수 표시
- Snack 패킷 감지
- Snack BlockEntity의 설치자·효과·재료 표시
- 주변 PokemonEntity는 패킷 발생 시에만 짧게 조회
- 불확실한 데이터는 `Estimated` 또는 `Unavailable`로 표시

### Phase 2: 클라이언트 안정화

- 알 BlockEntity NBT 동기화 여부를 실제 서버에서 검증
- 동기화되지 않는 경우 엄격 모드에서 알림 보류
- 목장·스낵별 중복 방지 키 추가
- 차원 변경, 청크 언로드, 재접속 시 캐시 정리

### Phase 3: 선택적 서버 보조 모드

- Cobblemon/Cobbreeding 서버 이벤트 구독
- 전용 Fabric CustomPayload 추가
- 개별 알 ID와 정확한 개수 전송
- 정확한 Snack 설치자·효과·Pokemon 메타데이터 전송
- 클라이언트가 서버 확장 모드를 감지하면 Payload 방식을 우선 사용

## 절대 지키지 않을 것

- `HAS_EGG`만으로 알 개수나 종족을 추측하지 않는다.
- Snack BlockPos 없이 여러 스낵을 하나의 이벤트로 합치지 않는다.
- 서버 이벤트를 클라이언트 이벤트인 것처럼 가정하지 않는다.
- 확인하지 못한 종족·부모 포켓몬 정보를 만들어 알리지 않는다.
- 서버 확장 Payload가 없는 상태에서 서버 전체 이벤트 감지를 보장한다고
  설명하지 않는다.

# Cobble Monitor 사용자 매뉴얼

## 1. 모드 역할

Cobble Monitor는 Fabric 1.21.1 클라이언트 전용 모드입니다.

현재 감시 기능:

- Minecraft 밤 시작
- 지정한 Cobblemon 목장의 알 생성
- Cobblemon Poke Snack 소비
- Discord Webhook Embed 알림
- ntfy 모바일 알림

서버에는 Cobble Monitor를 설치할 필요가 없습니다. 다만 Cobblemon 및 Cobbreeding
기능을 감시하려면 해당 모드가 서버와 클라이언트 양쪽에 정상적으로 설치되어
있어야 합니다.

## 2. 설치

1. Fabric Loader 0.16.5 이상과 Fabric API를 설치한다.
2. `cobble-monitor-1.5.1.jar`를 클라이언트의 `mods` 폴더에 넣는다.
3. 기존 `nightnotifier` JAR이 있으면 중복 실행을 막기 위해 제거한다.
4. Cobblemon 기능을 사용할 경우 Cobblemon 1.7.3을 설치한다.
5. 목장 알을 감시할 경우 Cobbreeding 2.2.2도 설치한다.

`sources.jar`는 개발자용 소스 코드이므로 `mods` 폴더에 넣지 않는다.

## 3. 설정 파일

첫 실행 후 다음 파일이 생성된다.

```text
.minecraft/config/cobble-monitor.json
```

주요 설정:

```json
{
  "enableDiscord": true,
  "discordWebhook": "",
  "enableNtfy": false,
  "ntfyTopic": "",
  "nightTime": 13000,
  "resetTime": 1000,
  "useGameLanguageMessages": true,
  "events": {
    "night": false,
    "day": false,
    "pastureEgg": true,
    "snackConsumed": true
  },
  "messages": {
    "day": "☀️ Minecraft에서 낮이 시작되었습니다.",
    "night": "🌙 Minecraft에서 밤이 시작되었습니다.",
    "pastureEgg": "🥚 Pasture Egg Created",
    "snackConsumed": "🍪 Snack Consumed"
  },
  "pastureMonitorMode": "selected",
  "monitoredPastures": []
}
```

설정을 수정한 뒤 게임을 재시작할 필요 없이 다음 명령어를 실행한다.

```text
/cobble-monitor reload
```

## 디버깅 명령어

알림이 오지 않을 때 다음 명령어로 감지와 HTTP 전송을 분리해서 확인할 수 있다.

```text
/cobble-monitor debug status
/cobble-monitor debug notify night
/cobble-monitor debug notify day
/cobble-monitor debug pasture
/cobble-monitor debug pasture looking
/cobble-monitor debug snack
/cobble-monitor debug notify pasture
/cobble-monitor debug notify snack
```

`debug status`는 Webhook URL과 ntfy topic의 실제 값을 표시하지 않는다. 클라이언트
`latest.log`에서 `Night detected`, `Day detected`, `Discord notification sent`,
`ntfy notification sent`, `Failed to send notification` 로그를 확인한다.

`debug pasture`는 저장된 모든 감시 대상 목장의 좌표, 클라이언트 청크 로드 여부,
`has_egg`, BlockEntity, 부모 데이터 진단을 출력한다. 조준 중인 블록만 확인하려면
`debug pasture looking`을 사용한다. `debug snack`은 Cobblemon 스낵 패킷 수신 횟수·중복 제거·대기열을 보여준다. `debug notify snack`은
Webhook 전송만 시험하며 실제 스낵 패킷 수신 여부는 `debug snack`으로 확인한다.
목장 외형의 빈 공간이 조준 좌표로 잡히는 경우에는 같은 X/Z에서 위·아래 2블록까지
목장 블록을 찾아 아래쪽 기준 좌표로 보정한다.

밤/낮 감시는 게임을 재시작하지 않고 다음 명령어로 즉시 켜고 끌 수 있다.

```text
/cobble-monitor config event night on
/cobble-monitor config event night off
/cobble-monitor config event day on
/cobble-monitor config event day off
```

새로 생성되는 설정에서는 낮/밤 알림이 기본 비활성화되어 있다. 원하는 항목만 위
명령어의 `on`으로 켜면 된다.

밤/낮 알림은 Overworld에서만 발생한다. 네더, 엔드, 레이드굴 등 다른 차원에서는
시간을 감시하지 않는다. Overworld로 돌아오면 현재 시간을 다시 확인하여 낮/밤에
해당하는 알림을 전송한다.

## 4. Discord Webhook 설정

1. Discord 서버에서 알림을 받을 채널을 연다.
2. 채널 설정 → 연동 → 웹후크로 이동한다.
3. 새 웹후크를 만들고 URL을 복사한다.
4. `discordWebhook`에 URL을 입력한다.
5. `enableDiscord`를 `true`로 설정한다.
6. `/cobble-monitor reload`를 실행한다.

명령어로도 설정할 수 있다.

```text
/cobble-monitor config discord <url>
/cobble-monitor config discord clear
/cobble-monitor config ntfy <topic>
/cobble-monitor config ntfy clear
```

URL은 채팅 기록이나 스트리밍 화면에 노출될 수 있으므로, 개인 클라이언트에서만 사용한다.

Discord 알림은 일반 텍스트가 아니라 Embed 카드로 전송된다.

- 밤: 남색 카드
- 목장 알: 초록색 카드
- Snack: 주황색 카드

목장 위치, 등록자, 포켓몬, 레벨, Shiny, Gender 등이 각각의 필드로 표시된다.
Snack 알림은 읽기 어려운 원시 효과·재료·패킷 정보는 제외하며, Discord에는 소비한
포켓몬의 일반/이로치 픽셀 스프라이트가 썸네일로 표시된다. 접속 중 탭 목록에서
확인한 플레이어의 UUID와 닉네임은 로컬 설정에 캐시되므로, 이후 해당 플레이어가
오프라인이어도 스낵 설치자로 표시할 수 있다.

Webhook URL은 비밀번호처럼 취급한다. URL을 공개 저장소나 채팅에 올리지 않는다.

## 5. ntfy 설정

1. ntfy 앱 또는 웹에서 사용할 topic 이름을 정한다.
2. `ntfyTopic`에 topic 이름만 입력한다.
3. `enableNtfy`를 `true`로 설정한다.
4. `/cobble-monitor reload`를 실행한다.

Cobble Monitor는 다음 주소로 POST한다.

```text
https://ntfy.sh/<topic>
```

ntfy는 모바일 알림 목록에 맞춰 제목과 간단한 메타데이터를 평문으로 전송한다.

## 6. 인게임 명령어

### 전체 도움말

```text
/cobble-monitor help
```

또는 루트 명령어만 입력해도 기본 도움말이 표시된다.

```text
/cobble-monitor
```

### 세부 도움말

```text
/cobble-monitor help pasture
/cobble-monitor help notifications
/cobble-monitor help config
```

### 목장 등록

가장 쉬운 방법은 목장 블록을 바라본 뒤 실행하는 것이다.

```text
/cobble-monitor pasture add looking
```

좌표를 직접 지정할 수도 있다. 좌표는 현재 차원 기준이다.

```text
/cobble-monitor pasture add 120 65 -318
```

명령어를 실행한 플레이어의 UUID와 닉네임은 해당 감시 대상의 `registeredBy`로
저장된다. 이는 목장을 설치한 사람을 의미하는 것이 아니라 감시 명령어를 등록한
사람을 의미한다.

### 목장 목록 확인

```text
/cobble-monitor pasture list
/cobble-monitor pasture inspect
```

각 목장은 차원과 BlockPos로 구분된다. 따라서 여러 목장을 동시에 등록해도 서로
섞이지 않는다.

### 목장 등록 해제

현재 차원에서 해당 좌표의 감시를 해제한다.

```text
/cobble-monitor pasture remove 120 65 -318
```

모든 목장 등록을 지우려면 다음을 사용한다.

```text
/cobble-monitor pasture clear
```

이 명령어들은 설정 목록만 변경하며 실제 Minecraft 블록이나 월드 데이터를
삭제하지 않는다.

## 7. 이벤트별 동작

### 밤 시작

월드 시간이 `nightTime` 이상이 되면 해당 밤에 한 번만 알림을 보낸다.
다음 날 `resetTime` 이하에서 상태를 초기화한다. 기본값 `1000`에서는 `/time set day`도 정상적으로 다음 밤 알림을 준비한다.

### 목장 알

등록된 목장만 감시한다.

```text
HAS_EGG: false -> true
```

변화가 발생하면 알림을 시도한다. 클라이언트에서 알 ItemStack의 포켓몬 종족을
읽을 수 있을 때만 종족을 Embed에 포함한다. 종족 데이터를 확인할 수 없으면
추측하지 않고 로그에 원인을 남긴다.

`HAS_EGG`는 Boolean이므로 이미 `true`인 상태에서 두 번째 알이 추가되는 변화와
현재 알의 정확한 개수는 순수 클라이언트에서 항상 확인할 수 없다.

### Snack 소비

Cobblemon의 Snack 소비 S2C 패킷을 감지한다. 패킷의 Snack BlockPos로 스낵을
구분하고, 가능한 경우 다음 정보를 읽는다.

- 설치자 닉네임 또는 UUID
- 스낵 효과
- 재료
- 영향을 받은 포켓몬

패킷에는 포켓몬 UUID가 없으므로 포켓몬은 패킷 위치 주변에서 짧게 조회한다.
이렇게 찾은 포켓몬은 Embed에 `Estimated Pokemon`으로 표시된다.

## 8. 로그와 문제 해결

로그 파일은 일반 Fabric 로그 위치에서 확인할 수 있다.

주요 로그:

```text
Cobble Monitor initialized
Night detected
Pasture detected using BlockState
Snack detected using Packet
Discord notification sent
ntfy notification sent
Failed to send notification
```

확인 순서:

1. JAR이 `mods` 폴더에 있는지 확인한다.
2. 서버가 아닌 클라이언트에 설치했는지 확인한다.
3. `config/cobble-monitor.json`의 Webhook 또는 topic을 확인한다.
4. `/cobble-monitor reload`를 실행한다.
5. `/cobble-monitor help`로 명령어가 등록됐는지 확인한다.
6. 목장 알은 먼저 `/cobble-monitor pasture list`로 좌표가 등록됐는지 확인한다.
7. 서버와 클라이언트의 Cobblemon 버전이 1.7.3인지 확인한다.

HTTP 오류가 발생해도 요청은 비동기로 처리되며 Minecraft 게임 스레드를 멈추지
않는다.

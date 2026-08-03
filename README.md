# Cobble Monitor

Fabric 1.21.1용 순수 클라이언트 모드입니다. 월드 밤 시작, 선택한 Cobblemon 목장의 알 생성, Poke Snack 소비를 Discord Webhook과 ntfy로 알립니다.

전체 설치 절차와 명령어 설명은 [상세 사용자 매뉴얼](docs/user-manual.md)을 참고하세요.

## 요구 사항

- Minecraft 1.21.1
- Fabric Loader 0.16.5 이상
- Fabric API
- Java 21
- Cobblemon 기능을 사용할 경우 Cobblemon 1.7.3
- Pasture Egg 기능을 사용할 경우 Cobbreeding 2.2.2

## 설정

첫 실행 시 `.minecraft/config/cobble-monitor.json`이 자동 생성됩니다.

```json
{
  "enableDiscord": true,
  "discordWebhook": "",
  "enableNtfy": false,
  "ntfyTopic": "",
  "nightTime": 13000,
  "resetTime": 1000,
  "events": {
    "night": true,
    "day": true,
    "legendarySpawn": true,
    "shinySpawn": true,
    "pastureEgg": true,
    "snackConsumed": true
  },
  "messages": {
    "day": "☀️ Minecraft에서 낮이 시작되었습니다.",
    "night": "🌙 Minecraft에서 밤이 시작되었습니다.",
    "legendarySpawn": "⭐ Legendary Spawn",
    "shinySpawn": "✨ Shiny Spawn",
    "pastureEgg": "🥚 Pasture Egg Created",
    "snackConsumed": "🍪 Snack Consumed"
  },
  "pastureMonitorMode": "selected",
  "monitoredPastures": []
}
```

Discord Webhook URL 또는 ntfy topic을 입력하고 해당 기능을 활성화하면 됩니다. Discord는 이벤트별 Embed 카드로 전송하고 ntfy는 짧은 평문으로 전송합니다.

## 목장 감시 명령어

클라이언트 명령어이므로 서버 설치 없이 사용할 수 있습니다.

```text
/cobble-monitor pasture add looking
/cobble-monitor pasture add <x> <y> <z>
/cobble-monitor pasture remove <x> <y> <z>
/cobble-monitor pasture list
/cobble-monitor pasture inspect
/cobble-monitor pasture clear
/cobble-monitor config discord <url>
/cobble-monitor config discord clear
/cobble-monitor config ntfy <topic>
/cobble-monitor config ntfy clear
/cobble-monitor config event night <on|off>
/cobble-monitor config event day <on|off>
/cobble-monitor debug status
/cobble-monitor debug notify <night|day>
/cobble-monitor reload
```

등록된 목장은 차원과 좌표로 구분되며, 명령어를 실행한 플레이어의 UUID와 닉네임이 `registeredBy` 정보로 저장됩니다.

게임 안에서 사용법을 확인하려면 다음 명령어를 실행합니다.

```text
/cobble-monitor help
/cobble-monitor help pasture
/cobble-monitor help notifications
/cobble-monitor help config
```

## English

Cobble Monitor is a client-side Fabric mod for Minecraft 1.21.1. It monitors
Minecraft night time, selected Cobblemon pastures, and Cobblemon Poke Snack
consumption, then sends notifications to Discord Webhooks and/or ntfy.

### Installation

1. Install Fabric Loader, Fabric API, and Java 21.
2. Install `Cobblemon-fabric-1.7.3+1.21.1.jar` for Cobblemon features.
3. Install `Cobbreeding-fabric-2.2.2.jar` for pasture egg monitoring.
4. Put `cobble-monitor-1.1.1.jar` in the instance `mods` folder.
5. Do not put the `sources.jar` file in the `mods` folder.

The mod is client-side only. It does not need to be installed on the server.

### Configuration

The configuration file is created automatically at:

```text
.minecraft/config/cobble-monitor.json
```

You can configure Discord and ntfy directly in the file, or use these client
commands without restarting the game:

```text
/cobble-monitor config discord <webhook-url>
/cobble-monitor config discord clear
/cobble-monitor config ntfy <topic>
/cobble-monitor config ntfy clear
```

If you edit the file manually, apply the changes with:

```text
/cobble-monitor reload
```

Webhook URLs are saved locally and should never be committed to GitHub or
shared in screenshots and streams.

Night and day monitoring can be toggled immediately with:

```text
/cobble-monitor config event night on
/cobble-monitor config event night off
/cobble-monitor config event day on
/cobble-monitor config event day off
```

The default day notification threshold is `resetTime` (1000), and the default
night notification threshold is `nightTime` (13000).
Day/night notifications are sent only in the Overworld; Nether, End, and other
dimensions do not trigger these alerts. When returning to the Overworld, the
current time is checked and the matching day/night notification may be sent.

### Pasture monitoring

Look at a Cobblemon pasture and run:

```text
/cobble-monitor pasture inspect
/cobble-monitor pasture add looking
```

`inspect` displays the dimension, coordinates, and monitoring status.
Use `/cobble-monitor pasture list` to view all registered pastures.

### Snack monitoring

Poke Snack monitoring is automatic. No snack coordinates or registration are
required. To enable or disable it, change `events.snackConsumed` in the config
file and run `/cobble-monitor reload`.

Use `/cobble-monitor help` in-game for the complete command list.

### Debugging

Use the following commands to separate event detection issues from HTTP delivery issues:

```text
/cobble-monitor debug status
/cobble-monitor debug notify night
/cobble-monitor debug notify day
```

`debug status` never displays the actual Webhook URL or ntfy topic. Check the
client `latest.log` for `Night detected`, `Day detected`, `Discord notification
sent`, `ntfy notification sent`, or `Failed to send notification`.

## Snack monitoring commands

Snack monitoring is automatic. No snack coordinate or snack registration is required.

```text
/cobble-monitor help notifications
```

To disable snack notifications, set `events.snackConsumed` to `false` in
`config/cobble-monitor.json`, then run:

```text
/cobble-monitor reload
```

Set it back to `true` and run the same reload command to enable snack notifications again.

## 빌드

```text
gradlew build -PcobblemonJar="C:/path/to/Cobblemon-fabric-1.7.3+1.21.1.jar" -PcobbreedingJar="C:/path/to/Cobbreeding-fabric-2.2.2.jar"
```

생성된 JAR는 `build/libs/`에 있습니다. 이 모드는 클라이언트 전용이므로 서버에는 설치하지 않습니다.

## 동작

클라이언트 틱에서 월드 시간을 읽고 `nightTime` 이상이면서 해당 밤에 아직 알림을 보내지 않았을 때만 알림을 예약합니다. 목장 감시는 등록된 좌표만 확인하며, Snack 감시는 Cobblemon의 전용 S2C 패킷이 수신될 때만 주변 포켓몬을 짧게 조회합니다. HTTP 요청은 Java 21 `HttpClient.sendAsync()`로 실행되므로 게임 스레드를 블로킹하지 않습니다.

# Minecraft Night Notifier

Fabric 1.21.1용 순수 클라이언트 모드입니다. 서버에 모드를 설치하지 않아도 월드 시간이 밤에 진입할 때 Discord Webhook과 ntfy로 푸시 알림을 보냅니다.

## 요구 사항

- Minecraft 1.21.1
- Fabric Loader 0.16.5 이상
- Fabric API
- Java 21

## 설정

첫 실행 시 `.minecraft/config/nightnotifier.json`이 자동 생성됩니다.

```json
{
  "enableDiscord": true,
  "discordWebhook": "",
  "enableNtfy": false,
  "ntfyTopic": "",
  "nightTime": 13000,
  "resetTime": 1000,
  "message": "🌙 Minecraft에서 밤이 시작되었습니다."
}
```

Discord Webhook URL 또는 ntfy topic을 입력하고 해당 기능을 활성화하면 됩니다. 두 기능을 동시에 사용할 수도 있습니다.

## 빌드

```text
gradlew build
```

생성된 JAR는 `build/libs/`에 있습니다. 이 모드는 클라이언트 전용이므로 서버에는 설치하지 않습니다.

## 동작

클라이언트 틱에서 월드 시간을 읽고 `nightTime` 이상이면서 해당 밤에 아직 알림을 보내지 않았을 때만 알림을 예약합니다. HTTP 요청은 Java 21 `HttpClient.sendAsync()`로 실행되므로 게임 스레드를 블로킹하지 않습니다.

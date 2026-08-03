# Cobble Monitor — Cobblemon Discord Webhook & ntfy Notifications

Cobble Monitor is a **client-side Minecraft Fabric 1.21.1 mod** for Cobblemon
players who want Discord Webhook or ntfy push notifications without installing
anything on their server. It works on vanilla-compatible servers and is designed
for Cobblemon modpacks such as Cobbleverse.

**English documentation is primary.** Korean documentation remains available
below.

- [Full user manual (English)](docs/user-manual.en.md)
- [사용자 매뉴얼 (한국어)](docs/user-manual.md)
- [Cobblemon monitoring roadmap](docs/cobblemon-monitor-roadmap.md)
- [Reddit post draft](docs/reddit-post.md)
- [GitHub discovery checklist](docs/github-discovery-checklist.md)

## Features

- Discord Webhook embeds and ntfy mobile notifications
- Asynchronous Java 21 `HttpClient.sendAsync()` delivery — no blocking game thread
- Configurable Overworld-only day and night alerts
- Selected Cobblemon pasture egg monitoring with no server-side installation
- Pasture egg species from synced egg data, with safe parent-based inference when
  the egg inventory is not synced
- Poke Snack consumption alerts from Cobblemon's client packet
- Pokemon pixel-sprite thumbnails in Discord embeds, including shiny Snack targets
- Offline snack placer names cached locally after the client has seen them
- In-game configuration, help, diagnostics, and manual notification tests

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.5+
- Fabric API
- Java 21
- Cobblemon 1.7.3 for Cobblemon features
- Cobbreeding 2.2.2 for pasture egg monitoring

## Install

1. Install Fabric Loader and Fabric API.
2. Install Cobblemon, and Cobbreeding if you use pasture egg monitoring.
3. Put `cobble-monitor-1.4.0.jar` in the instance `mods` folder.
4. Do **not** put `cobble-monitor-1.4.0-sources.jar` in `mods`.
5. Start the game once. The config is created at
   `.minecraft/config/cobble-monitor.json`.

Remove older Cobble Monitor or Night Notifier JARs so only one current version
is installed.

## Quick setup

Set a Discord Webhook in-game — no restart or `/reload` is needed afterward:

```text
/cobble-monitor config discord <webhook-url>
```

Optional ntfy setup:

```text
/cobble-monitor config ntfy <topic>
```

Day and night alerts are disabled by default. Enable only the alerts you want:

```text
/cobble-monitor config event night on
/cobble-monitor config event day on
```

Use `/cobble-monitor help` in-game for the full command list.

## Example configuration

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
    "day": "☀️ Minecraft day has started.",
    "night": "🌙 Minecraft night has started.",
    "pastureEgg": "🥚 Pasture Egg Created",
    "snackConsumed": "🍪 Snack Consumed"
  }
}
```

`useGameLanguageMessages` is enabled by default. With Minecraft set to Korean,
the embed title and description are Korean; other languages receive English.
Set it to `false` to use the strings under `messages` unchanged.

## Cobblemon pasture egg monitoring

Register only the pastures you care about; the mod never scans every pasture or
every Pokemon entity each tick.

```text
/cobble-monitor pasture inspect
/cobble-monitor pasture add looking
/cobble-monitor pasture list
/cobble-monitor pasture remove <x> <y> <z>
```

Both halves of Cobblemon's two-block pasture resolve to its bottom BlockEntity,
including the model's small air-gap raycast case.

When an egg appears, Cobble Monitor first reads synced egg metadata. If that is
unavailable, it reads the pasture's tethered Pokemon and calls Cobbreeding's own
possible-egg calculation. A single result is shown as an inferred species with a
Discord sprite thumbnail. Multiple valid results are listed as candidates rather
than guessing a potentially wrong Pokemon.

### Important client-side limitation

Pasture monitoring is limited to chunks currently sent to the monitoring
client. If the pasture is outside **your** client render/tracking distance, this
mod cannot see its `HAS_EGG`, inventory, or parent data and cannot notify in
real time. Another player standing near the pasture may keep it active on the
server, but their chunk updates are not sent to your distant client.

Returning to a loaded pasture can detect a remembered `false → true` egg state
in the same client world, but this is not a replacement for continuous remote
monitoring. Disconnecting or changing client worlds resets the baseline to
avoid duplicate alerts. Distance-independent alerts require the optional future
server companion described in the roadmap.

## Poke Snack monitoring

Snack monitoring is automatic and does not require snack coordinates or manual
registration. Cobble Monitor listens for Cobblemon's Snack client packet and
performs a short, local nearest-Pokemon lookup only when that packet arrives.

Discord embeds show the Pokemon species, level, shiny state, gender, position,
and a normal or shiny pixel-sprite thumbnail. The snack placer is resolved from
the current player list and then from a local UUID-to-name cache, so previously
seen players can still be named while offline.

## Diagnostics

```text
/cobble-monitor debug status
/cobble-monitor debug pasture
/cobble-monitor debug snack
/cobble-monitor debug notify <night|day|pasture|snack>
```

`debug pasture` reports the resolved pasture position, `has_egg`, synced egg
metadata, parent species, and possible egg species. `debug notify` tests only
the configured Discord/ntfy delivery; it does not simulate a Cobblemon packet.

## Search terms

Minecraft Fabric 1.21.1, Cobblemon mod, Cobbleverse, Discord Webhook,
Discord notifications, ntfy, Poke Snack monitor, pasture egg notifier,
Cobbreeding, client-side Minecraft mod, Pokemon spawn and event notifications.

## License

MIT

# Cobble Monitor — Cobblemon Discord Webhook & ntfy Notifications

Cobble Monitor is a **client-side Minecraft Fabric 1.21.1 mod** for Cobblemon
players who want Discord Webhook or ntfy push notifications without installing
anything on their server. It works on vanilla-compatible servers and is designed
for Cobblemon modpacks such as Cobbleverse.

**English documentation is primary.** Korean documentation remains available
below.

- [Full user manual (English)](docs/user-manual.en.md)
- [사용자 매뉴얼 (한국어)](docs/user-manual.md)
- [Working context for contributors](CLAUDE.md) — build setup, architecture, conventions
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
- Regional and alternate form sprites (Alolan, Galarian, Hisuian, Paldean, Mega,
  Gigantamax) resolved through Pokemon Showdown when PokeAPI cannot address the form
- Shiny and high-IV Cobbreeding eggs outlined in any container screen, with a total
  for the whole container including slots scrolled out of view
- Remaining Poke Snack count in Snack alerts
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
3. Put `cobble-monitor-1.8.2.jar` in the instance `mods` folder.
4. Do **not** put `cobble-monitor-1.8.2-sources.jar` in `mods`.
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

When the normal pasture BlockEntity does not include parent data, open that
monitored pasture's Cobblemon GUI once. Cobble Monitor caches the parent species
from the GUI's `OpenPasturePacket` and stores it in the config, so it survives a
relog or world change and opening each pasture once is enough. While that GUI
is open, adding or removing a Pokemon updates the cache immediately. For a
confirmed egg, a Ditto + one species or
same-species GUI cache can produce a conservative inferred egg species and
Discord sprite. The inferred form is retained when Cobbreeding returns it, so
regional forms use their matching Cobblemon texture; other combinations display
parents without guessing.
Cobblemon species and form names follow the active Minecraft language in both
fields and dynamic Discord titles.

When no parent data is available yet, the egg alert still sends and includes a
short reminder to open that pasture once for detailed parent and inferred-species
information. New egg alerts intentionally trigger only after `HAS_EGG` changes
from `false` to `true`; a pasture already holding an egg when monitoring begins
becomes the baseline rather than producing a duplicate alert.

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

## Egg highlighting

Open any container and Cobbreeding eggs worth keeping are outlined in place: gold
for a shiny, green for an average IV at or above `eggHighlight.minAverageIv`
(default 25). No command, no registration, and no per-container setup — the
outlines are drawn on the container screen itself, so a vanilla chest,
a Sophisticated Storage chest, a shulker box, and your own inventory all behave
the same.

A line above the screen totals the whole container:

```text
★ 1   ◆ 3   ↕ 2
```

`★` counts shiny eggs, `◆` counts high-IV eggs, and `↕` counts how many of those
are currently scrolled out of view. The totals cover every slot, so a 108-slot
Sophisticated chest does not have to be scrolled through to know what is in it.
Your own inventory slots are excluded from the totals.

Because outlines are drawn from each slot's live position, they stay correct while
scrolling, after sorting, and after you move an egg by hand. This reads the egg's
own `cobbreeding:pokemon_properties` component, which travels with the item stack,
so it needs no server-side installation. A server that enables Cobbreeding's
`eggEncryptionEnabled` replaces that component with ciphertext keyed by a
server-only file; eggs are then simply never outlined.

Like everything else client-side, this works only while the container's screen is
open — a client is never sent container contents otherwise.

Set `eggHighlight.enabled` to `false` to turn it off.

## Poke Snack monitoring

Snack monitoring is automatic and does not require snack coordinates or manual
registration. Cobble Monitor listens for Cobblemon's Snack client packet and
performs a short, local nearest-Pokemon lookup only when that packet arrives.

Discord embeds show the Pokemon species, level, shiny state, gender, position,
form, and a matching normal or shiny thumbnail. Base forms use PokeAPI sprites;
regional and alternate forms use a Pokemon Showdown sprite, and any remaining form
falls back to its Cobblemon 1.7.3 texture. The snack placer is resolved from
the current player list and then from a local UUID-to-name cache, so previously
seen players can still be named while offline.
When a nearby Pokemon is resolved, its species and form are also placed in the
Discord Embed title for quick scanning; the estimated-detection field remains.

Each alert also reports how many Poke Snacks remain on the eaten block, and states
explicitly when the last Poke Snack has been consumed.

## Diagnostics

```text
/cobble-monitor debug status
/cobble-monitor debug pasture
/cobble-monitor debug pasture looking
/cobble-monitor debug snack
/cobble-monitor debug notify <night|day|pasture|snack>
```

`debug pasture` inspects every persisted monitoring target directly: configured
coordinates, current dimension, client chunk availability, resolved base,
`HAS_EGG`, BlockEntity, local egg stacks, and parent-data diagnostics.
It also displays the most recent `OpenPasturePacket` and GUI-cache association.
`debug pasture looking` is the separate crosshair-only diagnostic. `debug
notify` tests only the configured Discord/ntfy delivery; it does not simulate a
Cobblemon packet.
Pasture notification embeds intentionally omit Egg Count because Cobbreeding
does not reliably synchronize that inventory to a pure client mod. Exact counts
remain a future server-companion feature.

## Search terms

Minecraft Fabric 1.21.1, Cobblemon mod, Cobbleverse, Discord Webhook,
Discord notifications, ntfy, Poke Snack monitor, pasture egg notifier,
Cobbreeding, client-side Minecraft mod, Pokemon spawn and event notifications.

## License

MIT

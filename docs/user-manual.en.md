# Cobble Monitor User Manual

Cobble Monitor is a client-only Fabric mod. Install it only on the Minecraft
client; the server does not need the mod.

## Install

1. Install Fabric Loader 0.16.5+, Fabric API, and Java 21 for Minecraft 1.21.1.
2. Install Cobblemon 1.7.3 for Cobblemon features.
3. Install Cobbreeding 2.2.2 for pasture egg monitoring.
4. Install `cobble-monitor-1.4.1.jar` in the instance `mods` folder.
5. Do not install the `-sources.jar` file.

The first launch creates `.minecraft/config/cobble-monitor.json`.

## Webhook and ntfy setup

```text
/cobble-monitor config discord <webhook-url>
/cobble-monitor config discord clear
/cobble-monitor config ntfy <topic>
/cobble-monitor config ntfy clear
```

The Discord command saves and enables the URL immediately. No reload is needed.
Use `/cobble-monitor reload` only after editing the JSON file manually.

## Day and night alerts

Both alerts are off in a newly created config. They run only in the Overworld.

```text
/cobble-monitor config event night on
/cobble-monitor config event night off
/cobble-monitor config event day on
/cobble-monitor config event day off
```

The default night threshold is `13000`; the reset/day threshold is `1000`.
Changing dimensions does not trigger an alert. When returning to the Overworld,
the current time is evaluated once.

## Message language

`useGameLanguageMessages` defaults to `true`.

- Minecraft language `ko_kr` (or another Korean locale): Korean title and
  description.
- Other Minecraft languages: English title and description.

Set it to `false` to use the literal strings in the config's `messages` object.

## Pasture egg monitoring

Register a pasture by looking at either half of it:

```text
/cobble-monitor pasture inspect
/cobble-monitor pasture add looking
/cobble-monitor pasture add <x> <y> <z>
/cobble-monitor pasture list
/cobble-monitor pasture remove <x> <y> <z>
/cobble-monitor pasture clear
```

Only registered pastures are checked. Cobble Monitor normalizes a two-block
pasture to its bottom BlockEntity position.

### Important: render-distance limitation

This is a client-side monitor. A pasture can be inspected only when its chunk
and BlockEntity are currently sent to the monitoring client. Being near the
pasture on another player's client does not send its live state to you.

If you leave the pasture beyond your client render/tracking distance, no
real-time egg detection is possible. Returning in the same client world can
still trigger an alert when the last known state was `HAS_EGG=false` and the
loaded state is now `true`. Disconnecting or changing client worlds resets that
baseline to prevent duplicate alerts. See the roadmap for the planned optional
server-side companion that will address remote monitoring.

For a new egg, the alert follows this order:

1. Read the exact species from synced egg data.
2. If unavailable, use Cobbreeding's own possible-egg calculation with the
   pasture's tethered Pokemon.
3. If exactly one species is possible, show it as `inferred from parents` and
   add its Discord pixel-sprite thumbnail.
4. If several species are possible, list candidates and do not choose a sprite.

`/cobble-monitor debug pasture` inspects all saved pasture registrations rather
than the crosshair. It shows configured coordinates, chunk availability,
resolved base, `HAS_EGG`, BlockEntity, local egg stacks, parent species,
possible results, tethered-entry count, and resolved Pokemon count. Use
`/cobble-monitor debug pasture looking` only for crosshair diagnostics. Pasture
notification embeds omit Egg Count because the client cannot reliably receive
the egg inventory; exact counts are reserved for the future server companion.

## Poke Snack monitoring

Snack monitoring is enabled by default and automatic. It listens for the
Cobblemon Snack S2C packet, then briefly checks near the packet position for the
most likely Pokemon. The alert identifies the result as estimated when that
association cannot be exact.

The embed includes the placer name when available. Names observed in the client
player list are stored in `playerNameCache`, allowing an offline player to be
identified later. The cache is local to this Minecraft client and does not query
any external account or server history service.

## Diagnostics

```text
/cobble-monitor help
/cobble-monitor debug status
/cobble-monitor debug pasture
/cobble-monitor debug pasture looking
/cobble-monitor debug snack
/cobble-monitor debug notify night
/cobble-monitor debug notify day
/cobble-monitor debug notify pasture
/cobble-monitor debug notify snack
```

Check `latest.log` for `Night detected`, `Pasture detected using BlockState`,
`Snack detected using Packet`, `Discord notification sent`, or
`Failed to send notification`.

# Cobble Monitor Roadmap

This document records the current client-only limits and the planned optional
server companion. It is intentionally based on APIs verified in Cobblemon 1.7.3
and Cobbreeding 2.2.2; it does not assume undocumented events exist.

## Current client-only implementation

Cobble Monitor runs entirely on the Minecraft client and needs no server mod.

- Day and night: vanilla client world time, Overworld only.
- Pasture eggs: `HAS_EGG` transition on explicitly registered Cobblemon
  pastures, plus synced egg data when available.
- Pasture species fallback: the pasture BlockEntity's tethered Pokemon are
  passed to Cobbreeding's own possible-egg calculation. A single candidate is
  reported as inferred; multiple candidates are not guessed.
- Poke Snacks: Cobblemon's Snack S2C packet, followed by a short local lookup
  around the packet position only when the packet arrives.

The client never broadly scans all Pokemon entities every tick.

## Event identity and de-duplication

Events should always retain their source location.

```text
EventKey = dimension + block position + event type
```

This prevents separate pastures or Snacks from being accidentally merged into
one notification.

## Pasture registration

Pastures are opt-in and stored in the client config.

```text
/cobble-monitor pasture add looking
/cobble-monitor pasture add <x> <y> <z>
/cobble-monitor pasture remove <x> <y> <z>
/cobble-monitor pasture list
/cobble-monitor pasture clear
```

The two-block Cobblemon pasture is normalized to its bottom BlockEntity
position. A registration retains the registering player's UUID and known name
for display only; it does not claim ownership of the block.

## Client-only limitation: remote pastures

The client can read a pasture only while the server sends that chunk and its
BlockEntity to *that client*. The code deliberately skips unloaded chunks.

- A different player near the pasture can keep the server chunk ticking, but a
  distant monitoring client still receives no live pasture data.
- A server-forceloaded chunk likewise does not send remote BlockEntity updates
  to a vanilla client that is not tracking the chunk.
- If no server-side player or chunk-loading mechanism keeps the chunk active,
  breeding may not progress at all because the server does not tick it.

When the monitoring client remains in the same client world, a remembered
`HAS_EGG=false` followed by returning to `HAS_EGG=true` can still notify on
return. After disconnecting or changing client worlds, the first observed state
is only a baseline to avoid false duplicate notifications.

## Planned: optional server-side remote pasture monitoring

### Goal

Allow a registered pasture to be observed independently of a monitoring
client's render distance, dimension, or connection state — but only when an
optional Fabric server companion is installed.

### Implementation direction

1. Investigate Cobbreeding/Cobblemon's actual server event or authoritative
   pasture/egg state hook for the installed versions.
2. Use that hook in preference to world polling.
3. Send a Fabric CustomPayload only to subscribed Cobble Monitor clients.
4. Let each client perform its existing Discord Webhook / ntfy delivery.
5. Keep the current client-only fallback untouched when the server companion is
   absent.

### Proposed payload

```text
PastureEggCreatedPayload
  dimension
  pasturePosition
  stablePastureKey
  createdAt
  eggCount                 # when server-authoritative data exposes it
  exactSpecies
  form
  parentA
  parentB
  inferenceSource          # server event | inventory | breeding calculation
  serverAuthoritative=true
```

### Server-side rules

- Do not force-load chunks solely for monitoring by default. If that becomes an
  option, make it explicit to server operators because it has a resource cost.
- De-duplicate with `dimension + pasture position + server event identity`.
- Preserve privacy: send alerts only to clients that registered that pasture,
  unless an administrator intentionally enables shared notifications.
- Report `serverAuthoritative=true` in diagnostics so users can distinguish a
  remote server event from local chunk-based observation.

### Migration and tests

1. Reuse existing client config pasture coordinates and registrations.
2. Add a server-companion handshake and subscription registration.
3. Test with the monitor beyond render distance, in another dimension,
   disconnected/reconnected, and while another player keeps the pasture active.
4. Test no-companion servers to ensure the mod never promises remote coverage.

## Planned server-side Snack upgrade

If Cobblemon exposes a stable server-side Snack-consumption event, prefer it to
client packet inference. The server payload should include the actual consuming
Pokemon UUID, species, level, shiny state, gender, snack position, placer UUID,
placer name, effects, and timestamp. This removes the current estimated-nearby
Pokemon fallback for compatible servers.

## Non-goals

- Do not invent Cobblemon or Cobbreeding events that have not been verified.
- Do not claim exact egg counts or identities from `HAS_EGG` alone.
- Do not use continuous global entity scans as a substitute for a missing API.
- Do not make server installation mandatory for current client-side features.

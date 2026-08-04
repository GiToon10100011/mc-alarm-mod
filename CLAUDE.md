# Cobble Monitor — working context

Client-side Minecraft **Fabric 1.21.1** mod. It watches Cobblemon events and pushes
notifications to a **Discord Webhook** and/or **ntfy**. Nothing is installed on the
server; it works on vanilla-compatible servers and Cobblemon modpacks.

This file is the fast path to productivity after a fresh clone. `README.md` and
`docs/user-manual*.md` are written for *players*; this file is for whoever is
*changing the code*.

---

## 1. Development setup — read this first

**A fresh clone cannot compile.** `PastureEggNotifier` and `SnackMonitorProvider`
import `com.cobblemon.*` and `ludichat.cobbreeding.*`, but those jars are
deliberately **not** in the repository and **not** on any public Maven the build
uses. `build.gradle` pulls them from local Gradle properties:

```groovy
if (project.hasProperty('cobblemonJar'))  { modCompileOnly files(project.property('cobblemonJar'))  }
if (project.hasProperty('cobbreedingJar')) { modCompileOnly files(project.property('cobbreedingJar')) }
```

They are `modCompileOnly` — compile-time only, never bundled into the released jar.

### Get the jars

| Dependency | Version the code targets | Where to get it |
|---|---|---|
| Cobblemon | **1.7.3+1.21.1** (Fabric) | Modrinth/CurseForge, or the `mods/` folder of any Cobblemon 1.7.3 instance |
| Cobbreeding | **2.2.2** (Fabric) | Modrinth/CurseForge, or the same `mods/` folder |

The exact filenames seen in practice are `Cobblemon-fabric-1.7.3+1.21.1.jar` and
`Cobbreeding-fabric-2.2.2.jar`.

### Wire them up (device-portable way)

Put the paths in your **user-level** Gradle properties so every clone on that
machine picks them up automatically, and nothing machine-specific ever lands in
the repo:

```properties
# ~/.gradle/gradle.properties   (Windows: C:\Users\<you>\.gradle\gradle.properties)
cobblemonJar=C:/path/to/Cobblemon-fabric-1.7.3+1.21.1.jar
cobbreedingJar=C:/path/to/Cobbreeding-fabric-2.2.2.jar
```

Do **not** add them to the repo's `gradle.properties` — that file is tracked.

Alternatively pass them per-invocation:

```bash
./gradlew build -PcobblemonJar=/abs/path/Cobblemon.jar -PcobbreedingJar=/abs/path/Cobbreeding.jar
```

### Build

```bash
./gradlew build          # produces build/libs/cobble-monitor-<version>.jar
./gradlew compileJava    # fast syntax/type check
```

Expected, harmless warnings: three `unknown enum constant DeprecationLevel.WARNING`
(Kotlin metadata in Cobblemon that javac cannot read) and one deprecated-API note
in `PastureEggNotifier`. There are **no tests** — `:test` is `NO-SOURCE`.

Once the user-level file is in place, plain `./gradlew build` works with no flags.

### If the build fails on a fresh clone

Without the two properties the build *configures* fine and fails only at
`:compileJava`, with this distinctive pair of symptoms:

```
error: Mixin has no targets                    (×5, one per Cobblemon-targeting mixin)
error: package com.cobblemon.mod.common.* does not exist
```

That means the setup above was skipped — **not** that the code is broken. The
`Mixin has no targets` lines come from the mixin annotation processor failing to
resolve the Cobblemon handler classes, and they disappear once the jars are wired
up.

---

## 2. Architecture

Java 21, single Fabric client entrypoint, ~2,530 lines across 13 files. No DI, no
framework — plain objects wired in `onInitializeClient`.

| File | Role |
|---|---|
| `CobbleMonitorClient` | Entrypoint. Tick loop, day/night detection, **static bridge methods the mixins call** |
| `NotificationService` | Discord embed + ntfy delivery, KO/EN localization, **all sprite URL construction** |
| `SnackMonitorProvider` | Snack packet → pending queue → nearby-Pokemon resolution → notify |
| `PastureEggNotifier` | `has_egg` blockstate transitions, pasture GUI parent cache, egg species inference |
| `CobblemonText` | Species/form name localization through the active game language |
| `ConfigManager` | JSON config, legacy `nightnotifier.json` migration, player-name cache |
| `CobbleMonitorCommands` | `/cobble-monitor` client commands (Brigadier) |
| `mixin/*` (6 files) | Observers on Cobblemon handlers |

### Optional-integration pattern

Everything Cobblemon-related is gated three ways, and all three must be preserved:

1. `FabricLoader.isModLoaded("cobblemon")` / `("cobbreeding")` before constructing
   the providers in `onInitializeClient`.
2. `"required": false` in `cobble-monitor.mixins.json`, so mixins silently no-op
   when the target classes are absent.
3. `modCompileOnly` dependencies, so the shipped jar never contains Cobblemon code.

The mod must stay usable with Cobblemon absent (day/night notifications still work).

### Mixin rule: observe, never replace

All six mixins are `@Inject(method = "...", at = @At("HEAD"))` with no cancellation.
They read a packet and hand it to a static method on `CobbleMonitorClient`. Cobblemon's
own handler then runs untouched.

**Do not** convert these to `@Redirect`, `@Overwrite`, or cancelling injections.
Breaking Cobblemon's pasture GUI or snack particles would be far worse than losing
a notification.

---

## 3. Event flows

### Day / night
Vanilla client world time in `CobbleMonitorClient.onClientTick`. **Overworld only.**
Edge-triggered by `nightNotified` / `dayNotified` flags, reset on world change.
Handles time rollback (`/time set`) via `movedFromNightToDay`. Off by default.

### Poke Snack
```
PokeSnackBlockParticlesHandler.handle  (mixin)
  → CobbleMonitorClient.handleSnackPacket
  → SnackMonitorProvider.handlePacket      dedup within 5 ticks, capture `bites`
  → PendingSnack queued
  → tick(): search 5-block box near entityPos for nearest PokemonEntity, up to 6 ticks
  → sendNotification()
```
The Pokemon is an **estimate** — the packet carries only positions, not an entity id.
That is why the embed says "Estimated Pokemon (near packet position)". Keep that
honesty; don't present it as authoritative.

### Pasture egg
Polls the `has_egg` blockstate of **explicitly registered** pastures only
(`/cobble-monitor pasture add`), on a `false → true` edge. Egg species comes from,
in order: synced egg NBT → Cobbreeding's own `getPossibleEggs` calculation on the
tethered Pokemon → species cached from `OpenPasturePacket` when the player last
opened that pasture's GUI.

Inference is deliberately conservative: it only guesses when the GUI cache shows
**exactly two** parents forming a valid pair (one non-Ditto + Ditto, or same-species
same-form). Anything else lists candidates rather than guessing. Preserve that.

---

## 4. Sprite resolution — three ordered layers

All in `NotificationService.cobblemonSpriteUrl()`. **The order is the design.**

| # | Layer | When | Source |
|---|---|---|---|
| 1 | **PokeAPI** | form has no aspects (base form) | `raw.githubusercontent.com/PokeAPI/sprites` `[shiny/]{dex}.png` |
| 2 | **Pokemon Showdown** | every aspect maps in `SHOWDOWN_FORM_SUFFIXES` | `play.pokemonshowdown.com/sprites/gen5[-shiny]/{id}-{suffix}.png` |
| 3 | **Cobblemon texture** | anything else | GitLab `cable-mc/cobblemon` raw, tag `1.7.3` |

Why layer 2 exists: PokeAPI's endpoint is keyed by **Pokedex number alone** and
physically cannot address a form — Hisuian Zorua and base Zorua share `570.png`.
Layer 3 serves the Cobblemon *3D model texture atlas*, which is a UV-unwrapped
skin, not a sprite; it renders as an unrecognizable smear in a Discord thumbnail,
and 404s outright for multi-aspect names like `mega_x`.

`showdownFormSpriteUrl` returns `""` unless **every** aspect on the form is
recognized, so unknown aspects fall through to layer 3 with byte-identical
behavior. The suffix map is a `LinkedHashMap` because **iteration order is
significant** — Showdown appends a regional name before a breed name
(`paldean` + `blaze-breed` → `paldeablaze`). One hardcoded exception: Showdown has
no `tauros-paldea`, only `tauros-paldeacombat`.

### Verifying a change to layer 2

Every generated URL was checked live, not assumed. Reproduce it like this:

1. Extract `data/cobblemon/species/**/*.json` from the Cobblemon jar (1,025 files)
   and collect each form's `aspects` array.
2. Call the private `showdownFormSpriteUrl` reflectively against the **compiled**
   class in `build/classes/java/main` (classpath also needs slf4j-api and gson from
   `~/.gradle/caches`) so you test shipped code, not a reimplementation.
3. HTTP HEAD every produced URL in both normal and shiny variants.

Current state: 366 forms carry aspects; 134 produce a Showdown URL; **all 268
resulting URLs return 200**; the other 232 fall through unchanged.

---

## 5. Non-obvious invariants

**Packet arrives before the block update.** `PokeSnackBlockEntity.affectSpawn` sends
`PokeSnackBlockParticlesPacket` to nearby players and *then* calls
`PokeSnackBlock.eat()`. So the client blockstate at packet-receipt time is the
**pre-bite** value. `SnackMonitorProvider` captures `bites` in `handlePacket`, not in
`sendNotification`, because a notification can fire on the very first tick and would
otherwise race the block update. Don't "simplify" this by reading the state later.

**Poke Snack math.** `PokeSnackBlock` has an `IntProperty bites` of range **0–8**;
`eat()` removes the block once a bite would exceed 8. So a snack holds **nine**
servings and `remaining = 8 - bites`.

**Reserved metadata keys.** `NotificationService` treats two map keys as internal,
never as embed fields:
- `_discordThumbnailUrl` (`DISCORD_THUMBNAIL_URL`)
- `_discordTitle` (`DISCORD_TITLE`) — overrides the embed title

`sendDiscord` **removes** them from the shared map; `sendNtfy` **skips** them by
name. Both paths are required: `sendNtfy` runs on the same map and would otherwise
print the raw key when Discord is disabled. If you add a third internal key, update
`sendNtfy`'s skip condition too.

**Blockstate properties are read by name, not by class.** Both `readHasEgg` and
`readRemainingSnacks` iterate `state.getProperties()` matching `"has_egg"` /
`"bites"`, rather than referencing Cobblemon's Kotlin `Companion` property objects.
This keeps the integration loose. Follow it for new properties.

**Reflection for Kotlin generics.** `readPossibleEggSpecies` calls Cobbreeding's
`getPossibleEggs` reflectively because its Java signature leaks Kotlin `Pair`,
which would drag a Kotlin dependency into this mod. Intentional — don't "clean up".

**Everything network is async.** All delivery goes through
`HttpClient.sendAsync()`. Never block the game thread on a notification, and never
add a synchronous HTTP call to a sprite-resolution path (that's why layer 2 uses a
pre-verified table instead of probing URLs at runtime).

**Config is user-owned.** `config/` is gitignored. `ConfigManager.normalize()`
backfills every field, so adding a config field is safe for existing users.
Webhook URLs are secrets — never log them, and the diagnostics commands only ever
report `configured=true/false`.

---

## 6. Conventions

**Commits** — Conventional-Commit prefixes, one purpose each:
`feat(scope):`, `fix(scope):`, `docs:`, and `release: <summary> in <version>` for
the version-bump commit. Keep feature changes and release metadata in *separate*
commits.

**Release process** (the repo has 24 tags; releases started at v1.8.0):

1. Bump `mod_version` in `gradle.properties`.
2. Add a `## <version>` section at the top of `CHANGELOG.md`.
3. Update the `cobble-monitor-<version>.jar` filename references in `README.md`
   and `docs/user-manual.md`.
4. Commit as `release: ... in <version>`.
5. `git tag -a v<version>` on that commit, `git push origin main --follow-tags`.
6. Build from the **tagged, clean** worktree and attach
   `cobble-monitor-<version>.jar` (main jar only — the README warns players not to
   put `-sources.jar` in `mods/`) to a GitHub Release via `gh release create`.

`fabric.mod.json` takes its version from `gradle.properties` through
`processResources` — never edit it by hand.

**Docs** — English is primary (`README.md`, `docs/user-manual.en.md`); Korean
(`docs/user-manual.md`) is maintained alongside. Update both for user-visible
changes. Code, comments, and commit messages are English.

**Scope discipline.** This project is maintained as small additive changes on top
of intentional existing code. Prefer the smallest change; extend rather than
replace; don't refactor unrelated code, rename files, or migrate APIs without
asking first.

---

## 7. Known issues / unfinished work

- **`Events.legendarySpawn` and `Events.shinySpawn` are dead config.** They default
  to `true` and are written into every user's `cobble-monitor.json`, plus matching
  `Messages` entries — but there is no `EventType` for them and no detection code
  anywhere. Users reasonably expect alerts that can never fire. Best candidate for
  the next change: either implement or remove them.
- **`SnackMonitorProvider.recentPackets` can retain stale entries across worlds.**
  `tick()` prunes with `currentTick - value > 40`, but `world.getTime()` resets on
  world change, so entries stamped with a large tick never match. Keys are
  dimension-scoped so no incorrect dedup can result — cosmetic memory only.
- **`COBBLEMON_TEXTURE_BASE_URL` is pinned to the literal `1.7.3` GitLab tag** and
  will rot when that tag moves or is removed. Layer 2 reduced how often it's
  reached, but it's still the fallback for ~232 forms.
- **Remote pasture monitoring is impossible client-side.** A client only sees a
  pasture while the server sends it that chunk. `docs/cobblemon-monitor-roadmap.md`
  specifies the optional server companion — and explicitly requires it to be a
  **separate** `cobble-monitor-server` project, not a module added here.

---

## 8. Quick reference

```text
/cobble-monitor help [pasture|notifications|config]
/cobble-monitor config discord <url> | discord clear
/cobble-monitor config ntfy <topic>  | ntfy clear
/cobble-monitor config event <night|day> <on|off>
/cobble-monitor pasture add looking | add <x> <y> <z> | remove <x> <y> <z>
/cobble-monitor pasture list | inspect | clear
/cobble-monitor debug status | pasture [looking] | snack
/cobble-monitor debug notify <night|day|pasture|snack>
/cobble-monitor reload
```

Config lives at `.minecraft/config/cobble-monitor.json`. `debug notify` only tests
webhook delivery — it does **not** prove event detection works; use `debug snack` /
`debug pasture` for that.

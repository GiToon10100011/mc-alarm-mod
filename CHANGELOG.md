# Changelog

## 1.8.0

- Added a Pokemon Showdown sprite fallback for regional and alternate forms that PokeAPI's Pokedex-number endpoint cannot address, covering Alolan, Galarian, Hisuian, Paldean, Mega, and Gigantamax forms. PokeAPI remains the primary lookup and the Cobblemon texture remains the final fallback.
- Added the remaining Poke Snack count to Snack notifications, including an explicit message when the last Poke Snack is consumed.
- Fixed the internal Discord title key appearing in ntfy messages when Discord is disabled.

## 1.7.0

- Localize Cobblemon species and form names in Snack and inferred pasture egg notifications using the active game language.
- Show the inferred pasture egg Pokemon in the Discord Embed title.

## 1.6.3

- Show the detected Pokemon (and form when available) in the Discord Snack Embed title.

## 1.6.2

- Preserve verified Cobblemon parent forms when inferring pasture egg species, and show the inferred egg form with its matching thumbnail.

## 1.6.1

- Update a monitored pasture's cached parents immediately from Cobblemon's verified PokemonPastured and PokemonUnpastured GUI packets.

## 1.6.0

- Added Cobblemon form metadata to Snack notifications.
- Use the verified Cobblemon 1.7.3 texture for non-base Snack forms, including Hisuian Zorua and shiny variants.

## 1.5.4

- Fixed Discord egg thumbnails by carrying the exact inferred Cobblemon Species through to PokeAPI sprite URL generation.

## 1.5.3

- Retain OpenPasturePacket species identifiers so Ditto and same-species parent pairs reliably infer an egg species and Discord sprite.

## 1.5.2

- Removed implementation-oriented `HAS_EGG` transition wording from the pasture registration message.

## 1.5.1

- Explain the one-time pasture GUI synchronization step when registering a monitoring target.
- Add a Discord/ntfy metadata hint when an egg alert has no locally available parent data.

## 1.5.0

- Cache parent species from Cobblemon's OpenPasturePacket after opening a monitored pasture GUI.
- Added OpenPasturePacket diagnostics and conservative parent-based egg inference from that GUI cache.

## 1.4.3

- Changed `debug pasture` to inspect persisted monitoring targets instead of the crosshair.
- Added `debug pasture looking` for explicit crosshair diagnostics.

## 1.4.2

- Removed the unreliable Egg Count field from pasture notification embeds; exact counts remain a server-side roadmap feature.

## 1.4.1

- Show an unavailable pasture egg count when the client receives `HAS_EGG` but not the egg inventory contents.
- Added tethered-entry and resolved-parent diagnostics for pasture debugging.

## 1.4.0

- Made English the primary README and documentation language.
- Added Minecraft-language-aware English/Korean notification titles and descriptions.
- Added a Reddit post draft and GitHub search-discovery checklist.

## 1.3.0

- Infer a pasture egg's species from Cobbreeding's actual possible-egg calculation when egg NBT is not synchronized.
- Show detected pasture parents and possible egg species in pasture diagnostics.
- Add the inferred or exact pasture egg Pokemon sprite as a Discord thumbnail.

## 1.2.4

- Disabled day and night alerts by default for newly created configurations.

## 1.2.3

- Persist client-observed player UUID-to-name mappings for readable offline snack placer names.

## 1.2.2

- Simplified Poke Snack embeds by removing raw effects, ingredient, and packet-source data.
- Resolve snack placer UUIDs through the client player list and show a friendly fallback when unavailable.
- Added a Discord thumbnail using the consumed Pokemon's standard or shiny sprite.

## 1.2.1

- Added a narrow vertical fallback for pasture command raycast misses.

## 1.2.0

- Normalized both halves of Cobblemon pastures to the bottom BlockEntity position.
- Added pasture and snack runtime diagnostics plus manual delivery tests.
- Preserved pasture egg alerts when the client has not synchronized egg species metadata.

## 1.1.2

- Fixed the `resetTime` boundary so `/time set day` resets the next night notification correctly.

## 1.1.1

- Added in-game debug status diagnostics.
- Added manual night/day notification test commands.
- Added guidance for checking client logs and separating detection from delivery issues.

## 1.1.0

- Added configurable day notifications alongside night notifications.
- Restricted day/night monitoring to the Overworld.
- Added in-game configuration commands for Discord, ntfy, and event toggles.
- Added pasture inspection and improved Overworld re-entry time checks.
- Added English README documentation.

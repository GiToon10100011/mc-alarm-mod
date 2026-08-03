# Changelog

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

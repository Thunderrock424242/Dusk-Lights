# Dusk Lights (Fabric 1.20.1)

DuskLights adds immersive, automated lighting to Minecraft while keeping a vanilla look.

## Core gameplay concept

- Craft a **Day/Night Sensor** copy of a vanilla light source for player builds:
  - **Day/Night Sensor Torch** = `Torch + Daylight Detector`
  - **Day/Night Sensor Lantern** = `Lantern + Daylight Detector`
- Place those sensor items to create lights that are automatically linked to the day/night cycle.
- Linked lights transition smoothly around dusk and dawn instead of hard toggling.
- Naturally generated structures are auto-linked when chunks are generated.
- The system is data-driven so modded light blocks can opt in via `dusklights:daylight_linkable`.
- Modded lights are runtime-discovered and normal placed items are auto-linked (no custom linked item required in most cases).

## How it works

- The mod tracks linked block positions in world saved data.
- Every few ticks, linked lights are updated based on time-of-day brightness from config.
- For blocks with compatible state properties (`lit`, `light`/`level`/`power`), brightness is applied directly.
- For vanilla torches, a normalization fallback is used.
- For non-directly-controllable blocks, an invisible `minecraft:light` helper block above the source provides brightness.

## Project layout

- `src/main/java` - Fabric mod code.
- `src/main/resources` - Fabric metadata, assets, recipes, and tags.
- Compatibility API docs for mod developers: `docs/MOD_COMPAT_API.md`.

## Useful Gradle tasks

From the repository root:

- `./gradlew runClient` - run Fabric client in dev.
- `./gradlew runServer` - run Fabric dedicated server in dev.
- `./gradlew build` - build the mod jars.

## Configuration

Runtime light timing is configured in `config/dusklights.json`.

Set `autoCompatDiscovery` (default `true`) to enable/disable runtime scanning of modded lights for auto-compat linking.

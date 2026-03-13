# Dusk Lights (Fabric 1.20.1)

DuskLights adds immersive, automated lighting to Minecraft while keeping a vanilla look.

## Core gameplay concept

- Use empty-hand right click on a compatible light to toggle its day/night sensor on or off.
- Sensor-enabled lights are automatically linked to the day/night cycle.
- Linked lights stay fully off in daytime, ramp up over 20 seconds at sunset, then stay on through the night.
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

## Natural structure light linking

Natural lights (like torches/lanterns in generated structures) are discovered dynamically.
When a chunk loads, DuskLights queues it for scanning and processes scans gradually during server ticks to avoid heavy world-join spikes.

## Debug command

Server operators can force all linked lights for debugging:

- `/dusklightsdebug lights on` - force linked lights fully on.
- `/dusklightsdebug lights off` - force linked lights fully off.
- `/dusklightsdebug lights auto` - return to normal day/night behavior.
- `/dusklightsdebug lights` - view current debug mode.

## Useful Gradle tasks

From the repository root:

- `./gradlew runClient` - run Fabric client in dev.
- `./gradlew runServer` - run Fabric dedicated server in dev.
- `./gradlew build` - build the mod jars.

## Configuration

Runtime light timing is configured in `config/dusklights.json`.

Set `autoCompatDiscovery` (default `true`) to enable/disable runtime scanning of modded lights for auto-compat linking.

You can also manually register additional block ids through config with `manualCompatBlockIds`:

```json
{
  "autoCompatDiscovery": false,
  "manualCompatBlockIds": [
    "some_mod:bronze_torch",
    "another_mod:wall_brazier"
  ]
}
```

This lets pack authors hardcode compat without requiring those mods to ship a DuskLights integration.

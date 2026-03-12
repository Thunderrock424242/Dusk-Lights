# Dusk Lights (Fabric 1.20.1)

DuskLights adds immersive, automated lighting to Minecraft while keeping a vanilla look.

## Core gameplay concept

- Right-click a placed light source with a **Daylight Sensor** (`minecraft:daylight_detector`) to link it to the day/night cycle.
- Linked lights should not hard-toggle instantly: they should transition in and out smoothly around dusk and dawn for a softer atmosphere.
- Villages and naturally generated buildings that spawn with torches/lanterns should be considered auto-linked by default.
- Player-built structures require intentional setup:
  - either right-click placed lights with a Daylight Sensor,
  - or craft a sensor-linked copy (`Linked Torch`: `Torch + Daylight Sensor`).
- The system is data-driven so modded light blocks can opt in via `dusklights:daylight_linkable`.

## Project layout

- `src/main/java` - Fabric mod code.
- `src/main/resources` - Fabric metadata, assets, recipes, and tags.

## Useful Gradle tasks

From the repository root:

- `./gradlew runClient` - run Fabric client in dev.
- `./gradlew runServer` - run Fabric dedicated server in dev.
- `./gradlew build` - build the mod jars.

## Configuration

Project and mod metadata (version, mod id, loader versions, etc.) is configured in `gradle.properties`.

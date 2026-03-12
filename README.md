# Dusk Lights (Multiloader)

DuskLights adds immersive, automated lighting to Minecraft while keeping a vanilla look.

## Core gameplay concept

- Right-click a placed light source with a **Daylight Sensor** (`minecraft:daylight_detector`) to link it to the day/night cycle.
- Linked lights should not hard-toggle instantly: they should transition in and out smoothly around dusk and dawn for a softer atmosphere.
- Villages and naturally generated buildings that spawn with torches/lanterns should be considered auto-linked by default.
- Player-built structures require intentional setup:
  - either right-click placed lights with a Daylight Sensor,
  - or craft a "sensor-linked" version of the light from the original light + Daylight Sensor.
- The system is intended to be data-driven so it can support **modded light blocks** (not only vanilla torches/lanterns).

## Design goals

- Preserve vanilla aesthetics.
- Make settlements feel alive at sunset/sunrise.
- Reduce manual light management for roads, villages, and bases.
- Keep compatibility broad by relying on tags/properties instead of hard-coded block lists where possible.

## Project layout

- `common/` - shared Java code and resources used by all loaders.
- `fabric/` - Fabric loader bootstrap + Fabric metadata.
- `forge/` - Forge loader bootstrap + `mods.toml`.

## Useful Gradle tasks

From the repository root:

- `./gradlew :fabric:runClient` - run Fabric client in dev.
- `./gradlew :fabric:runServer` - run Fabric dedicated server in dev.
- `./gradlew :forge:runClient` - run Forge client in dev.
- `./gradlew :forge:runServer` - run Forge dedicated server in dev.
- `./gradlew runFabricClient` / `runFabricServer` - root aliases for Fabric runs.
- `./gradlew runForgeClient` / `runForgeServer` - root aliases for Forge runs.
- `./gradlew build` - build all subprojects.

### IntelliJ run config note

If you hit errors like `ClassNotFoundException: net.fabricmc.loader.launch.knot.KnotClient`
while trying to run Forge, you are usually launching the wrong loader setup from your IDE.

- Use Gradle run tasks (`:forge:runClient`, `:forge:runServer`, `:fabric:runClient`, `:fabric:runServer`) or the root aliases.
- Avoid plain "Application" run configs for loader mains unless you know the exact classpath for that loader.

## Configuration

Project and mod metadata (version, mod id, loader versions, etc.) is configured in `gradle.properties`.

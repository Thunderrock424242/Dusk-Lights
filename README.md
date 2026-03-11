# Dusk Lights (Multiloader)

This repository is now set up as a **multiloader Minecraft mod project** targeting:

- **Fabric** (`/fabric`)
- **Forge** (`/forge`)
- Shared code in **Common** (`/common`)

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

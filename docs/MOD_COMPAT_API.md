# DuskLights Mod Compatibility API

This API allows other mods to plug custom light block behavior into DuskLights.

## Integration paths

You have three ways to integrate with DuskLights:

1. **Mod-side API/tag integration (recommended):** your mod ships a `dusklights:daylight_linkable` tag entry and optional `DuskLightsCompatApi` handler.
2. **Pack/server-side manual compat:** set `manualCompatBlockIds` in `config/dusklights.json` to force specific block ids to be treated as linkable.
3. **Automatic discovery:** leave `autoCompatDiscovery=true` to let DuskLights discover torch-like blocks at runtime.

## 1) Mark your block as linkable

Add your block id to the `dusklights:daylight_linkable` block tag.

```json
{
  "replace": false,
  "values": [
    "yourmod:your_light_block"
  ]
}
```

Place that JSON in your mod at:

`data/dusklights/tags/blocks/daylight_linkable.json`

## 2) Register a compatibility handler

If your block needs custom logic (instead of DuskLights' built-in fallback), register a handler during mod initialization.

```java
package com.example.yourmod;

import com.thunder.dusklights.api.DuskLightsCompatApi;
import net.minecraft.resources.ResourceLocation;

public final class YourMod {
    public void onInitialize() {
        DuskLightsCompatApi.registerHandler(
                new ResourceLocation("yourmod", "custom_lamp"),
                (level, pos, state, brightness) -> {
                    if (!state.is(YourBlocks.CUSTOM_LAMP)) {
                        return false; // not ours, let other handlers/default logic run
                    }

                    // Example: map dusk brightness to your own property/state.
                    level.setBlock(
                            pos,
                            state.setValue(CustomLampBlock.POWER, Math.min(4, brightness / 4)),
                            3
                    );

                    return true; // handled
                }
        );
    }
}
```

## Handler behavior

- Handlers execute in registration order.
- Return `true` when your handler fully applied the update.
- Return `false` to let other handlers or DuskLights fallback behavior run.
- Handler ids must be unique (`modid:path`).
- If a handler throws an exception, DuskLights logs it and continues processing.

## Fallback behavior (if no handler handles a block)

DuskLights will attempt one of these defaults:

1. Update an integer property whose name contains `light`, `level`, or `power`.
2. Update a boolean `lit` property.
3. Place/remove an invisible `minecraft:light` block above the source block.

For many simple light blocks, only adding the block tag is enough.


## Auto-link behavior for modded items

DuskLights performs a runtime discovery pass over loaded mod blocks and auto-links **torch-like** blocks (actual torch classes or ids containing `torch`) when players place the normal block item.
This avoids broad false positives from unrelated mod blocks that merely expose `power`/`level` properties.
You can still explicitly opt in with `dusklights:daylight_linkable` for guaranteed behavior on non-torch lights.
This means you usually do not need a separate `linked_*` item for torch compat.

If DuskLights cannot control a tagged block (for example, no compatible state property and helper light placement is blocked),
it logs a compat error that includes the owning mod id and asks users to report it to that mod.


### Config toggles

Server owners can disable runtime discovery via `config/dusklights.json` by setting `autoCompatDiscovery` to `false` (default is `true`).
When disabled, only explicitly tagged blocks, manually configured block ids, and existing compat handlers are used.

You can also force linkability for specific block ids:

```json
{
  "manualCompatBlockIds": [
    "yourmod:custom_torch",
    "yourmod:custom_wall_torch"
  ]
}
```

This is useful when a mod does not ship DuskLights integration but you still want predictable behavior in a modpack.

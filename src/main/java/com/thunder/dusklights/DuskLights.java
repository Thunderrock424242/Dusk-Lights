package com.thunder.dusklights;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DuskLights implements ModInitializer {
    public static final String MOD_ID = "dusklights";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item LINKED_TORCH = registerItem("linked_torch",
            new LinkedTorchItem(
                    Blocks.TORCH,
                    Blocks.WALL_TORCH,
                    new Item.Properties()
                            .stacksTo(64)
            )
    );

    public static final Item LINKED_LANTERN = registerItem("linked_lantern",
            new LinkedLanternItem(
                    Blocks.LANTERN,
                    new Item.Properties()
                            .stacksTo(64)
            )
    );

    private static boolean initialized;

    public DuskLights() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("Initializing {}", MOD_ID);
        DuskLightsConfig.Values config = DuskLightsConfig.get();
        LOGGER.info("Loaded dusk config: sunsetStartTick={}, sunsetRampMinutes={}, sunriseStartTick={}, sunriseRampMinutes={}",
                config.sunsetStartTick, config.sunsetRampMinutes, config.sunriseStartTick, config.sunriseRampMinutes);

        ServerTickEvents.END_WORLD_TICK.register(DuskLightsLogic::tickServerLevel);
    }

    @Override
    public void onInitialize() {
        init();
    }

    private static Item registerItem(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, path), item);
    }
}

package com.dusklights;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DuskLights {
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

    private static boolean initialized;

    private DuskLights() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("Initializing {}", MOD_ID);
    }

    private static Item registerItem(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, path), item);
    }
}

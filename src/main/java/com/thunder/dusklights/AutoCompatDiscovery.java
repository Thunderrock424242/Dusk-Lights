package com.thunder.dusklights;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoCompatDiscovery {
    private static final Set<Block> DISCOVERED_LINKABLE_BLOCKS = ConcurrentHashMap.newKeySet();
    private static volatile boolean discovered;

    private AutoCompatDiscovery() {
    }

    public static synchronized void discoverModdedLights() {
        if (discovered) {
            return;
        }

        int discoveredCount = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || "minecraft".equals(id.getNamespace()) || DuskLights.MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            BlockState state = block.defaultBlockState();
            if (!looksLikeLightSource(state)) {
                continue;
            }

            DISCOVERED_LINKABLE_BLOCKS.add(block);
            discoveredCount++;
        }

        discovered = true;
        DuskLights.LOGGER.info("Auto-discovered {} modded light blocks for DuskLights compat linking.", discoveredCount);
    }

    public static boolean isDiscoveredLinkable(BlockState state) {
        return DISCOVERED_LINKABLE_BLOCKS.contains(state.getBlock());
    }

    private static boolean looksLikeLightSource(BlockState state) {
        if (state.getLightEmission() > 0) {
            return true;
        }

        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty) {
                String name = integerProperty.getName();
                if (name.contains("light") || name.contains("level") || name.contains("power")) {
                    return true;
                }
            }

            if (property instanceof BooleanProperty booleanProperty && "lit".equals(booleanProperty.getName())) {
                return true;
            }
        }

        return false;
    }
}

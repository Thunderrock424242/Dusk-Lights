package com.thunder.dusklights;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class AutoCompatDiscovery {
    private static final Pattern TORCH_NAME_PATTERN = Pattern.compile("(^|_)torches?($|_)");
    private static final Set<Block> DISCOVERED_LINKABLE_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<Block> MANUAL_LINKABLE_BLOCKS = ConcurrentHashMap.newKeySet();
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
            if (!isTorchCandidate(block, id, state)) {
                continue;
            }

            DISCOVERED_LINKABLE_BLOCKS.add(block);
            discoveredCount++;
        }

        discovered = true;
        DuskLights.LOGGER.info("Auto-discovered {} modded torch blocks for DuskLights compat linking.", discoveredCount);
    }

    public static boolean isDiscoveredLinkable(BlockState state) {
        Block block = state.getBlock();
        return DISCOVERED_LINKABLE_BLOCKS.contains(block) || MANUAL_LINKABLE_BLOCKS.contains(block);
    }

    public static synchronized void registerConfiguredLinkableBlocks(Iterable<String> blockIds) {
        for (String blockId : blockIds) {
            if (blockId == null || blockId.isBlank()) {
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(blockId.trim());
            if (id == null) {
                DuskLights.LOGGER.warn("Skipping invalid manual compat block id '{}'", blockId);
                continue;
            }

            registerManualLinkableBlock(id);
        }
    }

    public static synchronized void registerManualLinkableBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            DuskLights.LOGGER.warn("Skipping manual compat block '{}': block is not registered", id);
            return;
        }

        registerManualLinkableBlock(BuiltInRegistries.BLOCK.get(id), id);
    }

    public static synchronized void registerManualLinkableBlock(Block block, ResourceLocation id) {
        if (MANUAL_LINKABLE_BLOCKS.add(block)) {
            DuskLights.LOGGER.info("Registered manual DuskLights compat block: {}", id);
        }
    }

    private static boolean isTorchCandidate(Block block, ResourceLocation id, BlockState state) {
        if (!isTorchLikeBlock(block, id)) {
            return false;
        }

        return state.getLightEmission() > 0 || hasControllableLightProperty(state);
    }

    private static boolean isTorchLikeBlock(Block block, ResourceLocation id) {
        if (block instanceof TorchBlock || block instanceof WallTorchBlock) {
            return true;
        }

        String path = id.getPath();
        return TORCH_NAME_PATTERN.matcher(path).find();
    }

    private static boolean hasControllableLightProperty(BlockState state) {
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

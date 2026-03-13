package com.thunder.dusklights;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DuskLights implements ModInitializer {
    public static final String MOD_ID = "dusklights";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block LINKED_TORCH_BLOCK = registerBlock("linked_torch",
            new TorchBlock(
                    BlockBehaviour.Properties.copy(Blocks.TORCH),
                    ParticleTypes.FLAME
            )
    );

    public static final Block LINKED_WALL_TORCH_BLOCK = registerBlock("linked_wall_torch",
            new WallTorchBlock(
                    BlockBehaviour.Properties.copy(Blocks.WALL_TORCH),
                    ParticleTypes.FLAME
            )
    );

    public static final Item LINKED_TORCH = registerItem("linked_torch",
            new LinkedTorchItem(
                    LINKED_TORCH_BLOCK,
                    LINKED_WALL_TORCH_BLOCK,
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
        LOGGER.info("Loaded dusk config: sunsetStartTick={}, sunsetRampMinutes={}, sunriseStartTick={}, sunriseRampMinutes={}, autoCompatDiscovery={}, manualCompatBlockIds={}",
                config.sunsetStartTick, config.sunsetRampMinutes, config.sunriseStartTick, config.sunriseRampMinutes,
                config.autoCompatDiscovery, config.manualCompatBlockIds.size());

        AutoCompatDiscovery.registerConfiguredLinkableBlocks(config.manualCompatBlockIds);

        if (config.autoCompatDiscovery) {
            AutoCompatDiscovery.discoverModdedLights();
        } else {
            LOGGER.info("Auto compat discovery is disabled by config.");
        }

        registerCreativeTabEntries();

        ServerTickEvents.END_WORLD_TICK.register(DuskLightsLogic::tickServerLevel);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                return net.minecraft.world.InteractionResult.PASS;
            }

            if (!(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                return net.minecraft.world.InteractionResult.PASS;
            }

            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) {
                return net.minecraft.world.InteractionResult.PASS;
            }

            if (!DuskLightsLogic.isLinkableState(blockItem.getBlock().defaultBlockState())) {
                return net.minecraft.world.InteractionResult.PASS;
            }

            net.minecraft.world.item.context.UseOnContext useOnContext = new net.minecraft.world.item.context.UseOnContext(player, hand, hitResult);
            net.minecraft.world.item.context.BlockPlaceContext placeContext = new net.minecraft.world.item.context.BlockPlaceContext(useOnContext);
            net.minecraft.core.BlockPos placedPos = placeContext.replacingClickedOnBlock()
                    ? placeContext.getClickedPos()
                    : placeContext.getClickedPos().relative(placeContext.getClickedFace());

            LinkedLightsSavedData.get(serverLevel).addLinked(placedPos.immutable());
            return net.minecraft.world.InteractionResult.PASS;
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> DuskLightsLogic.handleChunkLoad(level, chunk.getPos()));
    }

    @Override
    public void onInitialize() {
        init();
    }

    private static Item registerItem(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, path), item);
    }

    private static void registerCreativeTabEntries() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(LINKED_TORCH);
            entries.accept(LINKED_LANTERN);
        });
    }

    private static Block registerBlock(String path, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MOD_ID, path), block);
    }
}

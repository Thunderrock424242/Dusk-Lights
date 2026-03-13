package com.thunder.dusklights;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;

public final class DuskLights implements ModInitializer {
    public static final String MOD_ID = "dusklights";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
        LOGGER.info("Loaded dusk config: sunsetStartTick={}, sunsetRampMinutes={}, sunriseStartTick={}, sunriseRampMinutes={}, autoCompatDiscovery={}, defaultSensorEnabled={}, manualCompatBlockIds={}",
                config.sunsetStartTick, config.sunsetRampMinutes, config.sunriseStartTick, config.sunriseRampMinutes,
                config.autoCompatDiscovery, config.defaultSensorEnabled, config.manualCompatBlockIds.size());

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
                return InteractionResult.PASS;
            }

            if (!(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            net.minecraft.core.BlockPos clickedPos = hitResult.getBlockPos();

            if (held.isEmpty()) {
                net.minecraft.world.level.block.state.BlockState clickedState = serverLevel.getBlockState(clickedPos);
                if (!DuskLightsLogic.isLinkableState(clickedState)) {
                    return InteractionResult.PASS;
                }

                boolean linked = LinkedLightsSavedData.get(serverLevel).toggleLinked(clickedPos.immutable());
                if (!linked) {
                    DuskLightsLogic.removeAuxiliaryLightForSource(serverLevel, clickedPos);
                }

                player.displayClientMessage(Component.translatable(linked
                        ? "message.dusklights.sensor_enabled"
                        : "message.dusklights.sensor_disabled"), true);
                return InteractionResult.SUCCESS;
            }

            if (!(held.getItem() instanceof BlockItem blockItem)) {
                return InteractionResult.PASS;
            }

            if (!DuskLightsLogic.isLinkableState(blockItem.getBlock().defaultBlockState())) {
                return InteractionResult.PASS;
            }

            if (!DuskLightsConfig.get().defaultSensorEnabled) {
                return InteractionResult.PASS;
            }

            UseOnContext useOnContext = new UseOnContext(player, hand, hitResult);
            BlockPlaceContext placeContext = new BlockPlaceContext(useOnContext);
            net.minecraft.core.BlockPos placedPos = placeContext.replacingClickedOnBlock()
                    ? placeContext.getClickedPos()
                    : placeContext.getClickedPos().relative(placeContext.getClickedFace());

            net.minecraft.core.BlockPos linkedPos = placedPos.immutable();
            LinkedLightsSavedData.get(serverLevel).addLinked(linkedPos);
            DuskLightsLogic.refreshLinkedLight(serverLevel, linkedPos);
            return InteractionResult.PASS;
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> DuskLightsLogic.handleChunkLoad(level, chunk.getPos()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("dusklightsdebug")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("lights")
                                .then(literal("on").executes(context -> {
                                    DuskLightsLogic.setDebugLightsEnabled(true);
                                    context.getSource().sendSuccess(() -> Component.translatable("command.dusklights.debug.lights", "on"), true);
                                    return 1;
                                }))
                                .then(literal("off").executes(context -> {
                                    DuskLightsLogic.setDebugLightsEnabled(false);
                                    context.getSource().sendSuccess(() -> Component.translatable("command.dusklights.debug.lights", "off"), true);
                                    return 1;
                                }))
                                .then(literal("auto").executes(context -> {
                                    DuskLightsLogic.setDebugLightsEnabled(null);
                                    context.getSource().sendSuccess(() -> Component.translatable("command.dusklights.debug.lights", "auto"), true);
                                    return 1;
                                }))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.translatable("command.dusklights.debug.current", DuskLightsLogic.getDebugLightsMode()), false);
                                    return 1;
                                }))
        ));
    }

    @Override
    public void onInitialize() {
        init();
    }

    private static Item registerItem(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, path), item);
    }

    private static void registerCreativeTabEntries() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(LINKED_LANTERN));
    }
}

package com.thunder.dusklights;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;

@Mod(DuskLights.MOD_ID)
public final class DuskLights {
    public static final String MOD_ID = "dusklights";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean initialized;

    public DuskLights() {
        init();
        NeoForge.EVENT_BUS.register(NeoForgeHandlers.class);
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("Initializing {}", MOD_ID);
        DuskLightsConfig.Values config = DuskLightsConfig.get();
        LOGGER.info("Loaded dusk config: sunsetStartTick={}, sunsetRampMinutes={}, sunriseStartTick={}, sunriseRampMinutes={}, autoCompatDiscovery={}, defaultSensorEnabled={}, manualCompatBlockIds={}, dynamicTorchesEnabled={}, torchRainBrightnessMultiplier={}, torchStormFlickerChance={}, torchWarmupSeconds={}, torchUnderwaterSputterSeconds={}",
                config.sunsetStartTick, config.sunsetRampMinutes, config.sunriseStartTick, config.sunriseRampMinutes,
                config.autoCompatDiscovery, config.defaultSensorEnabled, config.manualCompatBlockIds.size(),
                config.dynamicTorchesEnabled, config.torchRainBrightnessMultiplier, config.torchStormFlickerChance,
                config.torchWarmupSeconds, config.torchUnderwaterSputterSeconds);

        AutoCompatDiscovery.registerConfiguredLinkableBlocks(config.manualCompatBlockIds);

        if (config.autoCompatDiscovery) {
            AutoCompatDiscovery.discoverModdedLights();
        } else {
            LOGGER.info("Auto compat discovery is disabled by config.");
        }
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class NeoForgeHandlers {
        private NeoForgeHandlers() {
        }

        @SubscribeEvent
        public static void onLevelTick(LevelTickEvent.Post event) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                DuskLightsLogic.tickServerLevel(serverLevel);
            }
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
                return;
            }

            Player player = event.getEntity();
            var held = player.getItemInHand(event.getHand());
            var clickedPos = event.getPos();

            if (held.isEmpty()) {
                BlockState clickedState = serverLevel.getBlockState(clickedPos);
                if (!DuskLightsLogic.isLinkableState(clickedState)) {
                    return;
                }

                boolean linked = LinkedLightsSavedData.get(serverLevel).toggleLinked(clickedPos.immutable());
                if (!linked) {
                    DuskLightsLogic.removeAuxiliaryLightForSource(serverLevel, clickedPos);
                }

                player.displayClientMessage(Component.translatable(linked
                        ? "message.dusklights.sensor_enabled"
                        : "message.dusklights.sensor_disabled"), true);

                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
                return;
            }

            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            if (!DuskLightsConfig.get().defaultSensorEnabled) {
                return;
            }

            if (!DuskLightsLogic.isLinkableState(event.getPlacedBlock())) {
                return;
            }

            var linkedPos = event.getPos().immutable();
            LinkedLightsSavedData.get(serverLevel).addLinked(linkedPos);
            DuskLightsLogic.registerPlayerPlacedLight(serverLevel, linkedPos, event.getPlacedBlock());
            DuskLightsLogic.refreshLinkedLight(serverLevel, linkedPos);
        }

        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load event) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                DuskLightsLogic.handleChunkLoad(serverLevel, event.getChunk().getPos());
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(
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
            );
        }
    }
}

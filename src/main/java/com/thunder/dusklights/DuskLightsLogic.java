package com.thunder.dusklights;

import com.thunder.dusklights.api.DuskLightsCompatApi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DuskLightsLogic {
    private static final TagKey<Block> DAYLIGHT_LINKABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(DuskLights.MOD_ID, "daylight_linkable"));

    private static final int UPDATE_INTERVAL_TICKS = 1;
    private static final int DAY_LENGTH_TICKS = 24000;
    private static final int CHUNK_SCANS_PER_TICK = 1;
    private static final Set<ResourceLocation> LOGGED_COMPAT_FAILURE_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<LightQueryContext> LIGHT_QUERY_CONTEXT = new ThreadLocal<>();
    private static Integer debugForcedBrightness;

    private DuskLightsLogic() {
    }

    public static TagKey<Block> daylightLinkableTag() {
        return DAYLIGHT_LINKABLE;
    }

    public static void tickServerLevel(ServerLevel level) {
        LinkedLightsSavedData data = LinkedLightsSavedData.get(level);
        processPendingChunkScans(level, data);

        if (level.getGameTime() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        if (data.getLinkedLightPositions().isEmpty()) {
            return;
        }

        List<BlockPos> stalePositions = new ArrayList<>();

        for (Long packed : data.getLinkedLightPositions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!refreshLinkedLight(level, pos)) {
                stalePositions.add(pos);
            }
        }

        for (BlockPos stalePos : stalePositions) {
            data.removeLinked(stalePos);
        }
    }

    public static boolean refreshLinkedLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return true;
        }

        BlockState state = level.getBlockState(pos);
        if (!isLinkableState(state)) {
            removeAuxiliaryLight(level, pos);
            return false;
        }

        applyBrightness(level, pos, state, calculateBrightness(level));
        return true;
    }

    public static void handleChunkLoad(ServerLevel level, ChunkPos chunkPos) {
        LinkedLightsSavedData data = LinkedLightsSavedData.get(level);
        data.queueChunkScan(chunkPos.toLong());
    }

    private static void processPendingChunkScans(ServerLevel level, LinkedLightsSavedData data) {
        if (!DuskLightsConfig.get().defaultSensorEnabled) {
            return;
        }

        for (int i = 0; i < CHUNK_SCANS_PER_TICK; i++) {
            Long packedChunkPos = data.pollPendingChunkScan();
            if (packedChunkPos == null) {
                return;
            }

            if (data.isChunkScanned(packedChunkPos)) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(packedChunkPos);
            BlockPos chunkOrigin = new BlockPos(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ());
            if (!level.isLoaded(chunkOrigin)) {
                data.queueChunkScan(packedChunkPos);
                continue;
            }

            scanChunkForNaturalLinkableLights(level, chunkPos, data);
            data.markChunkScanned(packedChunkPos);
            DuskLights.LOGGER.debug("Scanned chunk {} for natural linkable lights", chunkPos);
        }
    }

    private static void scanChunkForNaturalLinkableLights(ServerLevel level, ChunkPos chunkPos, LinkedLightsSavedData data) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minY; y < maxY; y++) {
                    cursor.set(x, y, z);
                    if (isLinkableState(level.getBlockState(cursor))) {
                        data.addLinked(cursor.immutable());
                    }
                }
            }
        }
    }


    public static boolean isLinkableState(BlockState state) {
        return state.is(DAYLIGHT_LINKABLE) || AutoCompatDiscovery.isDiscoveredLinkable(state);
    }

    public static void setDebugLightsEnabled(Boolean enabled) {
        if (enabled == null) {
            debugForcedBrightness = null;
            return;
        }

        debugForcedBrightness = enabled ? 15 : 0;
    }

    public static String getDebugLightsMode() {
        if (debugForcedBrightness == null) {
            return "auto";
        }

        return debugForcedBrightness > 0 ? "on" : "off";
    }


    public static int getTargetBrightness(Level level) {
        return calculateBrightness(level);
    }

    public static void pushLightQueryContext(BlockGetter getter, BlockPos pos) {
        LIGHT_QUERY_CONTEXT.set(new LightQueryContext(getter, pos.immutable()));
    }

    public static void popLightQueryContext() {
        LIGHT_QUERY_CONTEXT.remove();
    }

    public static Integer getForcedLightEmissionForLinkedState(BlockState state) {
        LightQueryContext context = LIGHT_QUERY_CONTEXT.get();
        if (context == null || !(context.getter() instanceof ServerLevel serverLevel)) {
            return null;
        }

        if (!isLinkableState(state) || !LinkedLightsSavedData.get(serverLevel).isLinked(context.pos())) {
            return null;
        }

        return getTargetBrightness(serverLevel);
    }

    private static int calculateBrightness(Level level) {
        if (debugForcedBrightness != null) {
            return debugForcedBrightness;
        }

        DuskLightsConfig.Values config = DuskLightsConfig.get();

        int timeOfDay = Math.floorMod((int) level.getDayTime(), DAY_LENGTH_TICKS);
        int sunsetDurationTicks = minutesToTicks(config.sunsetRampMinutes);
        int sunsetStart = config.sunsetStartTick;
        int sunsetEnd = wrapTick(sunsetStart + sunsetDurationTicks);

        int sunriseStart = config.sunriseStartTick;

        float minAtSunset = config.sunsetMinimumBrightness / 15.0F;
        float brightness;

        if (isWithinTimeWindow(timeOfDay, sunsetStart, sunsetEnd)) {
            float progress = normalizedProgress(timeOfDay, sunsetStart, sunsetDurationTicks);
            float eased = smoothstep(progress);
            brightness = minAtSunset + (1.0F - minAtSunset) * eased;
        } else if (isWithinTimeWindow(timeOfDay, sunsetEnd, sunriseStart)) {
            brightness = 1.0F;
        } else {
            brightness = 0.0F;
        }

        return Math.max(0, Math.min(15, Math.round(brightness * 15.0F)));
    }

    private static boolean isWithinTimeWindow(int value, int start, int end) {
        if (start <= end) {
            return value >= start && value <= end;
        }

        return value >= start || value <= end;
    }

    private static int wrapTick(int tick) {
        return Math.floorMod(tick, DAY_LENGTH_TICKS);
    }

    private static float normalizedProgress(int value, int start, int durationTicks) {
        int clampedDuration = Math.max(1, Math.min(durationTicks, DAY_LENGTH_TICKS - 1));
        int elapsed = Math.floorMod(value - start, DAY_LENGTH_TICKS);
        return Math.max(0.0F, Math.min(1.0F, elapsed / (float) clampedDuration));
    }

    private static int minutesToTicks(double minutes) {
        return Math.max(1, (int) Math.round(minutes * 60.0D * 20.0D));
    }

    private static float smoothstep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void applyBrightness(ServerLevel level, BlockPos pos, BlockState state, int brightness) {
        if (DuskLightsCompatApi.applyRegisteredHandlers(level, pos, state, brightness)) {
            return;
        }

        BlockState updatedState = tryApplyLightLevel(state, brightness);

        if (updatedState != state) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
            return;
        }

        List<BlockPos> helperLightPositions = getHelperLightPositions(pos, state);
        if (brightness <= 0) {
            for (BlockPos helperPos : helperLightPositions) {
                if (level.getBlockState(helperPos).is(Blocks.LIGHT)) {
                    level.removeBlock(helperPos, false);
                }
            }
            return;
        }

        BlockPos selectedPosition = null;
        for (BlockPos helperPos : helperLightPositions) {
            BlockState helperState = level.getBlockState(helperPos);
            if (helperState.is(Blocks.LIGHT)) {
                selectedPosition = helperPos;
                break;
            }

            if (helperState.isAir() && selectedPosition == null) {
                selectedPosition = helperPos;
            }
        }

        if (selectedPosition != null) {
            level.setBlock(selectedPosition, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), Block.UPDATE_CLIENTS);

            for (BlockPos helperPos : helperLightPositions) {
                if (!helperPos.equals(selectedPosition) && level.getBlockState(helperPos).is(Blocks.LIGHT)) {
                    level.removeBlock(helperPos, false);
                }
            }
            return;
        }

        logCompatFailure(state, pos, "Unable to apply brightness with fallback (blocked helper light position). Report to the owning mod for compatibility support.");
    }

    private static List<BlockPos> getHelperLightPositions(BlockPos sourcePos, BlockState sourceState) {
        List<BlockPos> candidates = new ArrayList<>(3);

        if (sourceState.hasProperty(WallTorchBlock.FACING)) {
            candidates.add(sourcePos.relative(sourceState.getValue(WallTorchBlock.FACING)));
        }

        candidates.add(sourcePos.above());
        candidates.add(sourcePos);
        return candidates;
    }

    private static BlockState tryApplyLightLevel(BlockState state, int brightness) {
        if (!hasBrightnessControlProperty(state)) {
            return state;
        }

        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty) {
                String name = integerProperty.getName();
                if (!(name.contains("light") || name.contains("level") || name.contains("power"))) {
                    continue;
                }

                int min = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
                int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
                return state.setValue(integerProperty, Math.max(min, Math.min(max, brightness)));
            }

            if (property instanceof BooleanProperty booleanProperty && "lit".equals(booleanProperty.getName())) {
                return state.setValue(booleanProperty, brightness > 0);
            }
        }

        return state;
    }

    private static boolean hasBrightnessControlProperty(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty) {
                String name = integerProperty.getName();
                if (name.contains("light") || name.contains("level") || name.contains("power")) {
                    return true;
                }
                continue;
            }

            if (property instanceof BooleanProperty booleanProperty && "lit".equals(booleanProperty.getName())) {
                return true;
            }
        }

        return false;
    }


    private record LightQueryContext(BlockGetter getter, BlockPos pos) {
    }

    private static void logCompatFailure(BlockState state, BlockPos pos, String reason) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null || !LOGGED_COMPAT_FAILURE_BLOCKS.add(blockId)) {
            return;
        }

        DuskLights.LOGGER.error("[Compat] {} Block={} at {}. This is not a DuskLights fault; report it to mod '{}'.",
                reason, blockId, pos, blockId.getNamespace());
    }


    public static void removeAuxiliaryLightForSource(ServerLevel level, BlockPos sourcePos) {
        removeAuxiliaryLight(level, sourcePos);
    }

    private static void removeAuxiliaryLight(ServerLevel level, BlockPos sourcePos) {
        BlockState sourceState = level.getBlockState(sourcePos);
        for (BlockPos helperPos : getHelperLightPositions(sourcePos, sourceState)) {
            if (level.getBlockState(helperPos).is(Blocks.LIGHT)) {
                level.removeBlock(helperPos, false);
            }
        }
    }
}

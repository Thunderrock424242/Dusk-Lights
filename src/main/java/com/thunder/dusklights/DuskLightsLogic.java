package com.thunder.dusklights;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;

public final class DuskLightsLogic {
    private static final TagKey<Block> DAYLIGHT_LINKABLE = TagKey.create(Registries.BLOCK,
            new ResourceLocation(DuskLights.MOD_ID, "daylight_linkable"));

    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final int DAY_LENGTH_TICKS = 24000;

    private DuskLightsLogic() {
    }

    public static TagKey<Block> daylightLinkableTag() {
        return DAYLIGHT_LINKABLE;
    }

    public static void tickServerLevel(ServerLevel level) {
        if (level.getGameTime() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        LinkedLightsSavedData data = LinkedLightsSavedData.get(level);
        if (data.getLinkedLightPositions().isEmpty()) {
            return;
        }

        int brightness = calculateBrightness(level);
        List<BlockPos> stalePositions = new ArrayList<>();

        for (Long packed : data.getLinkedLightPositions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.is(DAYLIGHT_LINKABLE)) {
                stalePositions.add(pos);
                removeAuxiliaryLight(level, pos);
                continue;
            }

            applyBrightness(level, pos, state, brightness);
        }

        for (BlockPos stalePos : stalePositions) {
            data.removeLinked(stalePos);
        }
    }

    public static void handleChunkLoad(ServerLevel level, ChunkPos chunkPos) {
        LinkedLightsSavedData data = LinkedLightsSavedData.get(level);
        if (!data.markChunkScanned(chunkPos.toLong())) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minY; y < maxY; y++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(DAYLIGHT_LINKABLE)) {
                        data.addLinked(cursor.immutable());
                    }
                }
            }
        }

        DuskLights.LOGGER.debug("Scanned chunk {} for natural linkable lights", chunkPos);
    }

    private static int calculateBrightness(Level level) {
        DuskLightsConfig.Values config = DuskLightsConfig.get();

        int timeOfDay = Math.floorMod((int) level.getDayTime(), DAY_LENGTH_TICKS);
        int sunsetDurationTicks = minutesToTicks(config.sunsetRampMinutes);
        int sunriseDurationTicks = minutesToTicks(config.sunriseRampMinutes);

        int sunsetStart = config.sunsetStartTick;
        int sunsetEnd = wrapTick(sunsetStart + sunsetDurationTicks);

        int sunriseStart = config.sunriseStartTick;
        int sunriseEnd = wrapTick(sunriseStart + sunriseDurationTicks);

        float minAtSunset = config.sunsetMinimumBrightness / 15.0F;
        float brightness;

        if (!isWithinTimeWindow(timeOfDay, sunsetStart, sunriseEnd)) {
            brightness = 0.0F;
        } else if (isWithinTimeWindow(timeOfDay, sunsetStart, sunsetEnd)) {
            float progress = normalizedProgress(timeOfDay, sunsetStart, sunsetDurationTicks);
            float eased = smoothstep(progress);
            brightness = minAtSunset + (1.0F - minAtSunset) * eased;
        } else if (isWithinTimeWindow(timeOfDay, sunriseStart, sunriseEnd)) {
            float progress = normalizedProgress(timeOfDay, sunriseStart, sunriseDurationTicks);
            float eased = smoothstep(progress);
            brightness = 1.0F - eased;
        } else {
            brightness = 1.0F;
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
        BlockState updatedState = tryApplyLightLevel(state, brightness);

        if (updatedState != state) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
            return;
        }

        BlockState normalizedVanillaTorchState = normalizeVanillaTorchState(state);
        if (normalizedVanillaTorchState != state) {
            BlockState normalizedUpdatedState = tryApplyLightLevel(normalizedVanillaTorchState, brightness);
            if (normalizedUpdatedState != normalizedVanillaTorchState) {
                level.setBlock(pos, normalizedUpdatedState, Block.UPDATE_CLIENTS);
                return;
            }
        }

        BlockPos lightPos = pos.above();
        if (brightness <= 0) {
            if (level.getBlockState(lightPos).is(Blocks.LIGHT)) {
                level.removeBlock(lightPos, false);
            }
            return;
        }

        BlockState lightState = level.getBlockState(lightPos);
        if (lightState.isAir() || lightState.is(Blocks.LIGHT)) {
            level.setBlock(lightPos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), Block.UPDATE_CLIENTS);
        }
    }
    private static BlockState normalizeVanillaTorchState(BlockState state) {
        if (state.is(Blocks.TORCH)) {
            return Blocks.REDSTONE_TORCH.defaultBlockState();
        }

        if (state.is(Blocks.WALL_TORCH)) {
            return Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.RedstoneWallTorchBlock.FACING,
                            state.getValue(net.minecraft.world.level.block.WallTorchBlock.FACING));
        }

        return state;
    }

    private static BlockState tryApplyLightLevel(BlockState state, int brightness) {
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

    private static void removeAuxiliaryLight(ServerLevel level, BlockPos sourcePos) {
        BlockPos lightPos = sourcePos.above();
        if (level.getBlockState(lightPos).is(Blocks.LIGHT)) {
            level.removeBlock(lightPos, false);
        }
    }
}

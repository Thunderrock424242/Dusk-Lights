package com.dusklights;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DuskLightsLogic {
    private static final TagKey<Block> DAYLIGHT_LINKABLE = TagKey.create(Registries.BLOCK,
            new ResourceLocation(DuskLights.MOD_ID, "daylight_linkable"));

    private static final int UPDATE_INTERVAL_TICKS = 5;

    private DuskLightsLogic() {
    }

    public static void handleLightLinkUse(ServerLevel level, Player player, InteractionHand hand, BlockPos pos) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.DAYLIGHT_DETECTOR)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.is(DAYLIGHT_LINKABLE)) {
            return;
        }

        LinkedLightsSavedData data = LinkedLightsSavedData.get(level);
        boolean linked = data.toggleLinked(pos.immutable());

        if (linked) {
            player.displayClientMessage(Component.translatable("message.dusklights.linked"), true);
        } else {
            removeAuxiliaryLight(level, pos);
            player.displayClientMessage(Component.translatable("message.dusklights.unlinked"), true);
        }
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
        Iterator<Long> iterator = data.getLinkedLightPositions().iterator();
        List<BlockPos> stalePositions = new ArrayList<>();

        while (iterator.hasNext()) {
            BlockPos pos = BlockPos.of(iterator.next());
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
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(DAYLIGHT_LINKABLE)) {
                        data.addLinked(cursor.immutable());
                    }
                }
            }
        }

        DuskLights.LOGGER.debug("Scanned chunk {} for natural linkable lights", chunkPos);
    }

    private static int calculateBrightness(Level level) {
        float angle = level.getTimeOfDay(1.0F);
        float cos = (float) Math.cos(angle * (Math.PI * 2));
        float normalizedNight = (1.0F - cos) * 0.5F;
        float smooth = normalizedNight * normalizedNight * (3.0F - 2.0F * normalizedNight);
        return Math.max(0, Math.min(15, Math.round(smooth * 15.0F)));
    }

    private static void applyBrightness(ServerLevel level, BlockPos pos, BlockState state, int brightness) {
        BlockState updatedState = tryApplyLightLevel(state, brightness);

        if (updatedState != state) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
        }

        if (updatedState == state) {
            BlockPos lightPos = pos.above();
            if (brightness <= 0) {
                if (level.getBlockState(lightPos).is(Blocks.LIGHT)) {
                    level.removeBlock(lightPos, false);
                }
                return;
            }

            BlockState lightState = level.getBlockState(lightPos);
            if (lightState.isAir() || lightState.is(Blocks.LIGHT)) {
                level.setBlock(lightPos, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, brightness), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static BlockState tryApplyLightLevel(BlockState state, int brightness) {
        for (var property : state.getProperties()) {
            if (!(property instanceof IntegerProperty integerProperty)) {
                continue;
            }

            String propertyName = integerProperty.getName();
            boolean seemsLikeLightProperty = propertyName.contains("light") || propertyName.contains("level") || propertyName.contains("power");
            if (!seemsLikeLightProperty) {
                continue;
            }

            int minValue = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
            int maxValue = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
            int clamped = Math.max(minValue, Math.min(maxValue, brightness));
            return state.setValue(integerProperty, clamped);
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

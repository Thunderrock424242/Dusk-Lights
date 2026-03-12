package com.dusklights;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class LinkedTorchItem extends StandingAndWallBlockItem {
    public LinkedTorchItem(Block standingBlock, Block wallBlock, Properties properties) {
        super(standingBlock, wallBlock, properties, net.minecraft.core.Direction.DOWN);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) {
            return result;
        }

        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return result;
        }

        BlockPos placedPos = context.replacingClickedOnBlock()
                ? context.getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace());

        if (serverLevel.getBlockState(placedPos).is(DuskLightsLogic.daylightLinkableTag())) {
            LinkedLightsSavedData.get(serverLevel).addLinked(placedPos.immutable());
        }

        return result;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return super.getDefaultInstance();
    }
}

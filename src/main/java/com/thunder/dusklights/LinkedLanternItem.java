package com.thunder.dusklights;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class LinkedLanternItem extends BlockItem {
    public LinkedLanternItem(Block block, Properties properties) {
        super(block, properties);
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

        if (DuskLightsConfig.get().defaultSensorEnabled
                && serverLevel.getBlockState(placedPos).is(DuskLightsLogic.daylightLinkableTag())) {
            LinkedLightsSavedData.get(serverLevel).addLinked(placedPos.immutable());
        }

        return result;
    }
}

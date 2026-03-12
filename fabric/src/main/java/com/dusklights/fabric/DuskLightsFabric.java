package com.dusklights.fabric;

import com.dusklights.DuskLights;
import com.dusklights.DuskLightsLogic;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;

public final class DuskLightsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        DuskLights.init();

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }

            DuskLightsLogic.handleLightLinkUse((ServerLevel) level, player, hand, hitResult.getBlockPos());
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_WORLD_TICK.register(DuskLightsLogic::tickServerLevel);

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> DuskLightsLogic.handleChunkLoad(world, chunk.getPos()));
    }
}

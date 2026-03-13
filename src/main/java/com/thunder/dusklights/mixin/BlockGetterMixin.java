package com.thunder.dusklights.mixin;

import com.thunder.dusklights.DuskLightsLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockGetter.class)
public interface BlockGetterMixin {
    @Inject(method = "getLightEmission", at = @At("HEAD"))
    private void dusklights$beginLightEmissionLookup(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        DuskLightsLogic.pushLightQueryContext((BlockGetter) (Object) this, pos);
    }

    @Inject(method = "getLightEmission", at = @At("RETURN"))
    private void dusklights$endLightEmissionLookup(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        DuskLightsLogic.popLightQueryContext();
    }
}

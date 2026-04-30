package com.scarasol.extractioncities.mixin;

import com.scarasol.extractioncities.server.level.DynamicDimensionSeeds;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalLong;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void extractioncities$getDynamicDimensionSeed(CallbackInfoReturnable<Long> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        OptionalLong seed = DynamicDimensionSeeds.get(level.dimension());
        if (seed.isPresent()) {
            cir.setReturnValue(seed.getAsLong());
        }
    }
}

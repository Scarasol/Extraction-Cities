package com.scarasol.extractioncities.mixin.tlc;

import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import mcjty.lostcities.setup.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Config.class, remap = false)
public abstract class ConfigMixin {
    @Inject(method = "getProfileForDimension", at = @At("HEAD"), cancellable = true)
    private static void extractioncities$getDynamicDimensionProfile(ResourceKey<Level> dimension, CallbackInfoReturnable<String> callbackInfo) {
        String profile = TlcCompat.profileForDynamicDimension(dimension);
        if (profile != null) {
            callbackInfo.setReturnValue(profile);
        }
    }
}

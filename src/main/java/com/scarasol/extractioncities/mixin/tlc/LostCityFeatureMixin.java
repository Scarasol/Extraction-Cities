package com.scarasol.extractioncities.mixin.tlc;

import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import mcjty.lostcities.worldgen.LostCityFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LostCityFeature.class, remap = false)
public abstract class LostCityFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void extractioncities$skipBiomePlacedFeatureForDynamicDimensions(FeaturePlaceContext<NoneFeatureConfiguration> context, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (TlcCompat.shouldSkipBiomePlacedFeature(context.level())) {
            callbackInfo.setReturnValue(false);
        }
    }
}

package com.scarasol.extractioncities.mixin.tlc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.worldgen.DefaultDimensionInfo;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DefaultDimensionInfo.class, remap = false)
public abstract class DefaultDimensionInfoMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lmcjty/lostcities/config/LostCityProfile;getWorldStyle()Ljava/lang/String;"))
    private String extractioncities$useDynamicDimensionWorldStyle(
            LostCityProfile instance,
            Operation<String> original,
            WorldGenLevel world,
            LostCityProfile profile,
            LostCityProfile outsideProfile
    ) {
        return TlcCompat.worldStyleForDynamicDimension(world.getLevel().dimension())
                .orElseGet(() -> original.call(instance));
    }
}

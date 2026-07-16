package com.scarasol.extractioncities.mixin.tlc;

import com.scarasol.extractioncities.compat.tlc.TlcBuildingOverrides;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BuildingInfo.class, remap = false)
public abstract class BuildingInfoMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void extractioncities$enforceDynamicBuildingOverride(ChunkCoord key, IDimensionInfo provider, CallbackInfo callbackInfo) {
        TlcBuildingOverrides.enforceBuildingInfo((BuildingInfo) (Object) this);
    }
}

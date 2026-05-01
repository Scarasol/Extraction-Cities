package com.scarasol.extractioncities.mixin;

import com.scarasol.extractioncities.compat.ModCompat;
import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void extractioncities$generateDynamicLostCities(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo callbackInfo) {
        if (ModCompat.isLoadTlc()) {
            TlcCompat.placeLostCityFeature(level, (ChunkGenerator) (Object) this, chunk);
        }
    }
}

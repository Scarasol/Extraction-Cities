package com.scarasol.extractioncities.mixin;

import com.scarasol.extractioncities.compat.ModCompat;
import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void extractioncities$generateDynamicLostCities(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo callbackInfo) {
        if (ModCompat.isLoadTlc()) {
            TlcCompat.placeLostCityFeature(level, (ChunkGenerator) (Object) this, chunk);
        }
    }

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void extractioncities$filterDynamicStructures(
            StructureSet.StructureSelectionEntry entry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!DynamicDimensionManager.shouldGenerateStructure(structureManager, entry.structure().value())) {
            callbackInfo.setReturnValue(false);
        }
    }
}

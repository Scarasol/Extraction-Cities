package com.scarasol.extractioncities.mixin;

import com.mojang.datafixers.util.Either;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ChunkStatus.class)
public abstract class ChunkStatusMixin {
    @WrapOperation(
            method = "lambda$static$2",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;generateStructures()Z")
    )
    private static boolean extractioncities$useDynamicStructureSetting(
            WorldOptions options,
            Operation<Boolean> original,
            ChunkStatus status,
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ThreadedLevelLightEngine lightEngine,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter,
            List<ChunkAccess> chunks,
            ChunkAccess chunk
    ) {
        return DynamicDimensionManager.shouldGenerateStructures(level, original.call(options));
    }
}

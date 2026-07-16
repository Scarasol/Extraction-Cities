package com.scarasol.extractioncities.mixin;

import com.mojang.datafixers.util.Either;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ChunkStatus.class)
public abstract class ChunkStatusMixin {
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void extractioncities$generateDynamicStructureStarts(
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ThreadedLevelLightEngine lightEngine,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter,
            List<ChunkAccess> chunks,
            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> callbackInfo
    ) {
        ChunkStatus status = (ChunkStatus) (Object) this;
        if (status != ChunkStatus.STRUCTURE_STARTS) {
            return;
        }

        ChunkAccess chunk = chunks.get(chunks.size() / 2);
        boolean fallback = level.getServer().getWorldData().worldGenOptions().generateStructures();
        if (DynamicDimensionManager.shouldGenerateStructures(level, fallback)) {
            generator.createStructures(level.registryAccess(), level.getChunkSource().getGeneratorState(), level.structureManager(), chunk, structureTemplateManager);
        }

        level.onStructureStartsAvailable(chunk);
        if (chunk instanceof ProtoChunk protoChunk && !protoChunk.getStatus().isOrAfter(status)) {
            protoChunk.setStatus(status);
        }
        callbackInfo.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
    }
}

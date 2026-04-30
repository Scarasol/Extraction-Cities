package com.scarasol.extractioncities.world.level.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;

public record DynamicDimensionRecord(
        ResourceLocation id,
        ResourceLocation dimensionType,
        ResourceLocation generator,
        @Nullable ResourceLocation biome,
        DynamicDimensionStorageMode storage,
        long seed,
        boolean generateStructures,
        @Nullable GameType gameMode,
        @Nullable BlockPos teleportPoint,
        boolean allowRespawn
) {
}
